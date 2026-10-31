package lan.homelab.app;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import org.json.JSONObject;

// Quick Settings: DNS blocking (both Pi-holes). Lit = blocking on; dim = paused, with the minutes left. Tap pauses for
// 5 minutes or resumes. The state comes from the dashboard each time the panel opens and after every tap; the request
// sets an explicit state (on, or off for 300 s), so a repeated or late tap can never flip it the wrong way. On the lock
// screen it asks you to unlock first, since it changes what the whole network can reach.
public class PauseDnsTile extends TileService {
    private static final long OPTIMISTIC_MS = 20_000;  // the dashboard reads the Pi-holes every few seconds

    @Override
    public void onStartListening() {
        render(null);
        new Thread(() -> { LiveState.fetch(this); post(null); }, "tile-dns-state").start();
    }

    @Override
    public void onClick() {
        if (isLocked()) unlockAndRun(this::toggle); else toggle();
    }

    private void toggle() {
        render("Checking…");
        new Thread(() -> {
            if (LiveState.fetch(this) == null) { toast("Can't reach your homelab."); post(null); return; }
            Boolean blocking = blocking();
            if (blocking == null) { toast("DNS status is unknown right now."); post(null); return; }
            boolean pause = blocking;
            post(pause ? "Pausing…" : "Resuming…");
            String msg;
            try {
                int code = Net.post("/api/action/pihole-toggle", pause ? "{\"enable\":false,\"timer\":300}" : "{\"enable\":true}");
                if (code == 200) {
                    LiveState.prefs(this).edit().putBoolean("dns_set_blocking", !pause)
                        .putLong("dns_set_at", System.currentTimeMillis()).apply();
                    msg = pause ? "DNS blocking paused for 5 minutes." : "DNS blocking is back on.";
                } else msg = "The dashboard refused the request.";
            } catch (Exception e) { msg = "Can't reach your homelab."; }
            toast(msg);
            post(null);
            HealthWidgetProvider.refreshNow(this);
        }, "tile-dns").start();
    }

    // Blocking on (true), paused (false) or unknown (null). Right after a tap, the state just set wins until the
    // dashboard has had time to read it back from the Pi-holes.
    private Boolean blocking() {
        android.content.SharedPreferences p = LiveState.prefs(this);
        if (System.currentTimeMillis() - p.getLong("dns_set_at", 0) < OPTIMISTIC_MS) return p.getBoolean("dns_set_blocking", true);
        JSONObject dns = LiveState.cached(this).optJSONObject("dns");
        if (dns == null || dns.isNull("blocking") || !LiveState.fresh(this)) return null;
        return dns.optBoolean("blocking");
    }

    private long minutesLeft() {
        android.content.SharedPreferences p = LiveState.prefs(this);
        long since = System.currentTimeMillis() - p.getLong("dns_set_at", 0);
        if (since < OPTIMISTIC_MS) return Math.max(1, (300_000 - since + 59_999) / 60_000);
        JSONObject dns = LiveState.cached(this).optJSONObject("dns");
        long s = dns == null ? 0 : dns.optLong("seconds_left", 0);
        long age = (System.currentTimeMillis() - p.getLong("ok_at", 0)) / 1000;
        return s > 0 ? Math.max(1, (s - age + 59) / 60) : 0;
    }

    private void post(String override) { new Handler(Looper.getMainLooper()).post(() -> render(override)); }

    private void toast(String m) { new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show()); }

    private void render(String override) {
        Tile t = getQsTile();
        if (t == null) return;
        Boolean on = blocking();
        t.setLabel("DNS blocking");
        t.setState(on == null ? Tile.STATE_UNAVAILABLE : on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        String sub;
        if (override != null) sub = override;
        else if (on == null) sub = "Unknown";
        else if (on) sub = "On";
        else { long m = minutesLeft(); sub = m > 0 ? "Paused · " + m + " min left" : "Paused"; }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) t.setSubtitle(sub);
        else t.setLabel("DNS · " + sub);
        t.updateTile();
    }
}
