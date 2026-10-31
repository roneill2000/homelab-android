package lan.homelab.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

// The Quick Settings tiles read the dashboard's /api/widget when shown and after a tap, so they show the real state
// (set from the dashboard or another phone too), not a guess kept since the last 15-minute widget refresh.
final class LiveState {
    private LiveState() {}

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("homelab_widget", Context.MODE_PRIVATE); }

    // Fetches and caches the widget summary; null when the dashboard can't be reached (away without WireGuard).
    static JSONObject fetch(Context c) {
        try {
            JSONObject d = new JSONObject(Net.get("/api/widget", 16384));
            if (!d.has("status")) return null;
            prefs(c).edit().putString("json", d.toString()).putLong("ok_at", System.currentTimeMillis()).putBoolean("away", false).apply();
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    static JSONObject cached(Context c) {
        try { return new JSONObject(prefs(c).getString("json", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    static boolean fresh(Context c) { return System.currentTimeMillis() - prefs(c).getLong("ok_at", 0) < 120_000; }
}
