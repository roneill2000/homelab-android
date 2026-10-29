package lan.homelab.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

// Quick Settings: pause DNS blocking for 5 minutes (both Pi-holes), or resume it if it's paused. On the lock screen
// it asks you to unlock first, since it changes what the whole network can reach.
public class PauseDnsTile extends TileService {
    @Override
    public void onStartListening() { render(null); }

    @Override
    public void onClick() {
        if (isLocked()) unlockAndRun(this::toggle); else toggle();
    }

    private void toggle() {
        boolean paused = paused(this);
        render(paused ? "Resuming…" : "Pausing…");
        new Thread(() -> {
            String msg;
            try {
                int code = Net.post("/api/action/pihole-toggle", paused ? "{\"enable\":true}" : "{\"enable\":false,\"timer\":300}");
                msg = code == 200 ? (paused ? "DNS blocking is back on." : "DNS blocking paused for 5 minutes.") : "The dashboard refused the request.";
                if (code == 200) markPaused(this, !paused);
            } catch (Exception e) { msg = "Can't reach your homelab."; }
            String m = msg;
            new Handler(Looper.getMainLooper()).post(() -> { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); render(null); });
            HealthWidgetProvider.refreshNow(this);
        }, "tile-dns").start();
    }

    private void render(String override) {
        Tile t = getQsTile();
        if (t == null) return;
        boolean paused = paused(this);
        t.setLabel(paused ? "Resume DNS" : "Pause DNS");
        t.setState(paused ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) t.setSubtitle(override != null ? override : paused ? "Blocking paused" : "5 minutes");
        t.updateTile();
    }

    static boolean paused(Context c) {
        SharedPreferences p = c.getSharedPreferences("homelab_widget", Context.MODE_PRIVATE);
        if (System.currentTimeMillis() < p.getLong("dns_paused_until", 0)) return true;
        try {
            JSONArray probs = new JSONObject(p.getString("json", "{}")).optJSONArray("problems");
            for (int i = 0; probs != null && i < probs.length(); i++) if (probs.optString(i).contains("DNS blocking paused")) return true;
        } catch (Exception ignored) { }
        return false;
    }

    static void markPaused(Context c, boolean paused) {
        c.getSharedPreferences("homelab_widget", Context.MODE_PRIVATE).edit()
            .putLong("dns_paused_until", paused ? System.currentTimeMillis() + 300_000 : 0).apply();
    }
}
