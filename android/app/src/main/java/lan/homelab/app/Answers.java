package lan.homelab.app;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import org.json.JSONObject;

// "Answer ready": the dashboard's Ask box tells the app (AndroidBridge.watchAnswer) which question it just sent. This
// follows that job through GET /api/qwen and, if you're in another app when it finishes, posts one notification that
// opens the answer. Nothing is posted while the app is on screen, or for a job that was replaced or cancelled.
// It runs in the app's process: if Android ends the process while you're away, that question just isn't notified.
final class Answers {
    static final String CHANNEL = "answers";
    static final int ID = 4200;
    private static final long POLL_MS = 3000, MAX_MS = 20 * 60_000;
    private static volatile String watching = null;

    private Answers() {}

    static boolean validId(String id) { return id != null && id.matches("[A-Za-z0-9_-]{1,64}"); }

    static void watch(Context context, String id) {
        if (!validId(id)) return;
        Context c = context.getApplicationContext();
        boolean running = watching != null;
        watching = id;  // a newer question replaces the one being followed
        if (running) return;
        new Thread(() -> loop(c), "answer-watch").start();
    }

    private static void loop(Context c) {
        long until = System.currentTimeMillis() + MAX_MS;
        while (watching != null && System.currentTimeMillis() < until) {
            try { Thread.sleep(POLL_MS); } catch (InterruptedException e) { break; }
            String id = watching;
            JSONObject d;
            try { d = new JSONObject(Net.get("/api/qwen", 262144)); } catch (Exception e) { continue; }
            String status = d.optString("status"), jobId = d.optString("id", "");
            if (!id.equals(jobId)) { if (!"busy".equals(status)) { watching = null; } continue; }
            if ("complete".equals(status) || "failed".equals(status)) {
                if (!MainActivity.visible) notify(c, d, "complete".equals(status));
                if (id.equals(watching)) watching = null;
            }
        }
        watching = null;
    }

    static String preview(String answer) {
        String a = answer == null ? "" : answer.replaceAll("\\s+", " ").trim();
        return a.length() > 160 ? a.substring(0, 157).trim() + "…" : a;
    }

    private static void notify(Context c, JSONObject d, boolean ok) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        if (!nm.areNotificationsEnabled()) return;
        String model = d.optString("model", "");
        String text = ok ? preview(d.optString("answer", "")) : d.optString("error", "The model couldn't answer.");
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse("homelab://view/ai"), c, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile_ai)
            .setContentTitle(ok ? "Answer ready" + (model.isEmpty() ? "" : " · " + model) : "Your question failed")
            .setContentText(text)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(PendingIntent.getActivity(c, ID, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
            .setAutoCancel(true);
        try { nm.notify(ID, b.build()); } catch (SecurityException ignored) { }
    }
}
