package lan.homelab.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

// Live download progress while you're away from the app. The page starts it (AndroidBridge.watchDownloads) when an
// RdtClient download is running; it polls /api/downloads every 5 s, shows one ongoing notification with a progress
// bar, and stops itself once nothing has been active for two polls, leaving a "finished" notification.
public class DownloadProgressService extends Service {
    static final int ID = 4100, DONE_ID = 4101;
    private volatile boolean running = false;
    private final Set<String> seen = new LinkedHashSet<>();

    static void start(Context c) {
        try { ContextCompat.startForegroundService(c, new Intent(c, DownloadProgressService.class)); } catch (Exception ignored) { }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) return START_NOT_STICKY;
        running = true;
        Notification first = build("Checking downloads…", null, -1, true);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0;
        ServiceCompat.startForeground(this, ID, first, type);
        new Thread(this::loop, "download-progress").start();
        return START_NOT_STICKY;
    }

    // Android 15 limits data-sync services; when the system says time is up, stop cleanly.
    @Override
    public void onTimeout(int startId) { running = false; }

    @Override
    public void onDestroy() { running = false; super.onDestroy(); }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void loop() {
        int idle = 0, failures = 0;
        while (running) {
            try {
                JSONArray items = new JSONObject(Net.get("/api/downloads", 131072)).optJSONArray("items");
                failures = 0;
                List<JSONObject> active = new ArrayList<>();
                for (int i = 0; items != null && i < items.length(); i++) {
                    JSONObject t = items.optJSONObject(i);
                    if (t == null) continue;
                    double p = t.optDouble("progress", 1);
                    String state = t.optString("state", "");
                    if (p < 1 && !state.toLowerCase(Locale.ROOT).contains("error")) active.add(t);
                }
                if (active.isEmpty()) {
                    if (++idle >= 2) break;
                } else {
                    idle = 0;
                    for (JSONObject t : active) seen.add(t.optString("name", "Download"));
                    show(active);
                }
            } catch (Exception e) {
                if (++failures >= 12) break; // a minute without the dashboard (left home): give up quietly
            }
            try { Thread.sleep(5000); } catch (InterruptedException e) { break; }
        }
        finish(idle >= 2);
    }

    private void show(List<JSONObject> active) {
        String title, text;
        int pct;
        if (active.size() == 1) {
            JSONObject t = active.get(0);
            title = t.optString("name", "Download");
            pct = (int) Math.floor(t.optDouble("progress", 0) * 100);
            text = stage(t) + " · " + pct + "%" + speed(t.optDouble("dlspeed", 0));
        } else {
            double sum = 0, bps = 0;
            for (JSONObject t : active) { sum += t.optDouble("progress", 0); bps += t.optDouble("dlspeed", 0); }
            pct = (int) Math.floor(sum / active.size() * 100);
            title = active.size() + " downloads";
            text = active.get(0).optString("name", "") + " and " + (active.size() - 1) + " more" + speed(bps);
        }
        NotificationManagerCompat nm = NotificationManagerCompat.from(this);
        try { nm.notify(ID, build(title, text, pct, true)); } catch (SecurityException ignored) { }
    }

    private void finish(boolean completed) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        if (completed && !seen.isEmpty()) {
            String first = seen.iterator().next();
            String title = seen.size() == 1 ? "Download finished" : seen.size() + " downloads finished";
            String text = seen.size() == 1 ? first : first + " and " + (seen.size() - 1) + " more";
            NotificationCompat.Builder b = new NotificationCompat.Builder(this, Alerts.CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_widget_download).setContentTitle(title).setContentText(text)
                .setContentIntent(openMedia()).setAutoCancel(true);
            try { NotificationManagerCompat.from(this).notify(DONE_ID, b.build()); } catch (SecurityException ignored) { }
        }
        stopSelf();
    }

    private Notification build(String title, String text, int pct, boolean ongoing) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, Alerts.CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_widget_download)
            .setContentTitle(title)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openMedia());
        if (text != null) b.setContentText(text);
        if (pct < 0) b.setProgress(0, 0, true); else b.setProgress(100, Math.max(0, Math.min(100, pct)), false);
        return b.build();
    }

    private PendingIntent openMedia() {
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse("homelab://view/media"), this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, ID, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static String stage(JSONObject t) {
        String s = t.optString("stage", "");
        if ("debrid".equals(s)) return "At debrid";
        if ("finishing".equals(s)) return "Finishing";
        return "Downloading";
    }

    private static String speed(double bps) {
        return bps > 0 ? String.format(Locale.ROOT, " · %.1f MB/s", bps / 1e6) : "";
    }
}
