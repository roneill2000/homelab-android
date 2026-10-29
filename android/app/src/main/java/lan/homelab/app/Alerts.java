package lan.homelab.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

// Native problem alerts. Each widget refresh (every 15 minutes, plus app opens and the refresh button) compares the
// dashboard's problem list with the last one: a new problem posts one notification that opens the right page, and a
// problem that clears removes its notification. Routine log errors are left to the nightly review, not notified.
final class Alerts {
    static final String CHANNEL = "alerts";
    static final String CHANNEL_PROGRESS = "download_progress";
    static final String CHANNEL_DONE = "downloads_done";

    private Alerts() {}

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel alerts = new NotificationChannel(CHANNEL, "Homelab problems", NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("A service down, a failed backup, a drive warning or DNS blocking paused");
        NotificationChannel progress = new NotificationChannel(CHANNEL_PROGRESS, "Download progress", NotificationManager.IMPORTANCE_LOW);
        progress.setDescription("Live progress while a download runs");
        progress.setShowBadge(false);
        NotificationChannel done = new NotificationChannel(CHANNEL_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(alerts);
        nm.createNotificationChannel(progress);
        nm.createNotificationChannel(done);
    }

    // Stable identity for a problem whose wording carries a count ("2 services down" and "3 services down" are one alert).
    static String key(String p) {
        String s = p.toLowerCase();
        if (s.contains("log error")) return null;
        if (s.contains(" down")) return "services-down";
        if (s.contains("dns")) return "dns";
        if (s.contains("drive")) return "drive";
        if (s.contains("cloud backup")) return "cloud";
        if (s.contains("backup job")) return "backup-job";
        if (s.contains("fresh readings")) return "stale";
        return s.replaceAll("[^a-z]+", "-");
    }

    static String page(String key) {
        switch (key) {
            case "services-down": return "services";
            case "dns": return "network";
            case "drive": return "storage";
            case "cloud": case "backup-job": return "infra";
            default: return "overview";
        }
    }

    static String detail(String key) {
        switch (key) {
            case "services-down": return "Some services aren't answering. Tap to see which.";
            case "dns": return "Ads and trackers aren't being blocked right now.";
            case "drive": return "A drive reported a SMART warning. Tap for details.";
            case "cloud": return "The off-site Google Drive backup needs a look.";
            case "backup-job": return "Last night's Proxmox backup job didn't succeed.";
            case "stale": return "The dashboard stopped getting fresh readings.";
            default: return "Tap to open the dashboard.";
        }
    }

    static void process(Context c, JSONObject widget) {
        JSONArray list = widget.optJSONArray("problems");
        if (list == null) return;
        SharedPreferences prefs = c.getSharedPreferences("homelab_alerts", Context.MODE_PRIVATE);
        Set<String> before = new HashSet<>(prefs.getStringSet("active", new HashSet<>()));
        Set<String> now = new HashSet<>();
        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        for (int i = 0; i < list.length(); i++) {
            String text = list.optString(i, "");
            String k = key(text);
            if (k == null || text.isEmpty()) continue;
            now.add(k);
            if (!before.contains(k)) notify(c, nm, k, text);
        }
        for (String k : before) if (!now.contains(k)) nm.cancel(k.hashCode());
        prefs.edit().putStringSet("active", now).apply();
    }

    private static void notify(Context c, NotificationManagerCompat nm, String key, String title) {
        if (!nm.areNotificationsEnabled()) return;
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse("homelab://view/" + page(key)), c, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(c, key.hashCode(), open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_widget_alert)
            .setContentTitle(title)
            .setContentText(detail(key))
            .setStyle(new NotificationCompat.BigTextStyle().bigText(detail(key)))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tap)
            .setAutoCancel(true);
        try { nm.notify(key.hashCode(), b.build()); } catch (SecurityException ignored) { }
    }
}
