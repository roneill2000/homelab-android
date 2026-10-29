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
    @Override
    public void onStartListening() { render(null); }

    @Override
    public void onClick() {
        render("Waking…");
        new Thread(() -> {
            String msg;
            try { msg = Net.post("/api/action/wake-pc", "{}") == 200 ? "Wake signal sent. The PC takes about 30 s." : "The dashboard refused the request."; }
            catch (Exception e) { msg = "Can't reach your homelab."; }
            String m = msg;
            new Handler(Looper.getMainLooper()).post(() -> { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); render(null); });
            HealthWidgetProvider.refreshNow(this);
        }, "tile-wake").start();
    }

    private void render(String override) {
        Tile t = getQsTile();
        if (t == null) return;
        String state = pcState(this);
        t.setLabel("Wake PC");
        boolean on = state != null && !"off".equals(state) && !"unknown".equals(state);
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            t.setSubtitle(override != null ? override : "off".equals(state) ? "Asleep or off" : on ? "On" : "Unknown");
        t.updateTile();
    }

    static String pcState(Context c) {
        SharedPreferences p = c.getSharedPreferences("homelab_widget", Context.MODE_PRIVATE);
        try { return new JSONObject(p.getString("json", "{}")).optJSONObject("pc").optString("state", null); }
        catch (Exception e) { return null; }
    }
}
