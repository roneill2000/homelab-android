package lan.homelab.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import org.json.JSONObject;

// Quick Settings: wake the gaming PC (Wake-on-LAN through the dashboard). Shows the PC's last known state.
public class WakePcTile extends TileService {
    // Shows the PC's live state (read from the dashboard each time the panel opens). Waking is idempotent: an awake PC
    // is reported as on and is not sent another wake signal.
    @Override
    public void onStartListening() {
        render(null);
        new Thread(() -> { LiveState.fetch(this); post(null); }, "tile-wake-state").start();
    }

    // Like Pause DNS, waking the PC needs the phone unlocked: a tile works from the lock screen otherwise.
    @Override
    public void onClick() {
        if (isLocked()) unlockAndRun(this::wake); else wake();
    }

    private void wake() {
        render("Waking…");
        new Thread(() -> {
            String msg;
            try {
                String body = Net.postForBody("/api/action/wake-pc", "{}");
                msg = body == null ? "The dashboard refused the request."
                    : body.contains("\"already\": true") ? "The PC is already on." : "Wake signal sent. The PC takes about 30 s.";
            } catch (Exception e) { msg = "Can't reach your homelab."; }
            String m = msg;
            new Handler(Looper.getMainLooper()).post(() -> { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); render(null); });
            HealthWidgetProvider.refreshNow(this);
        }, "tile-wake").start();
    }

    private void post(String override) { new Handler(Looper.getMainLooper()).post(() -> render(override)); }

    private void render(String override) {
        Tile t = getQsTile();
        if (t == null) return;
        String state = pcState(this);
        boolean known = state != null && !"unknown".equals(state) && LiveState.fresh(this);
        boolean on = known && !"off".equals(state);
        t.setLabel("Gaming PC");
        t.setState(!known ? Tile.STATE_INACTIVE : on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        String sub = override != null ? override : !known ? "Tap to wake" : on ? "On" : "Asleep · tap to wake";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) t.setSubtitle(sub);
        else t.setLabel("PC · " + sub);
        t.updateTile();
    }

    static String pcState(Context c) {
        SharedPreferences p = c.getSharedPreferences("homelab_widget", Context.MODE_PRIVATE);
        try { return new JSONObject(p.getString("json", "{}")).optJSONObject("pc").optString("state", null); }
        catch (Exception e) { return null; }
    }
}
