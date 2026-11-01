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

// Quick Settings: snooze homelab alerts (the dashboard's Quick actions -> Snooze alerts). Lit = snoozed, with the time
// left. Tap snoozes for 2 hours, or ends a running snooze. The state is read from GET /api/maintenance each time the
// panel opens and after every tap; every alert sender honours it (alerts/SENDERS.md). Unlock first: it silences alerts.
public class SnoozeAlertsTile extends TileService {
    static final int MINUTES = 120;

    @Override
    public void onStartListening() {
        render(null);
        new Thread(() -> { fetch(this); post(null); }, "tile-snooze-state").start();
    }

    @Override
    public void onClick() {
        if (isLocked()) unlockAndRun(this::toggle); else toggle();
    }

    private void toggle() {
        render("Checking…");
        new Thread(() -> {
            if (!fetch(this)) { toast(Net.unreachable(this)); post(null); return; }
            boolean snoozed = secondsLeft(prefs(this), System.currentTimeMillis()) > 0;
            post(snoozed ? "Resuming…" : "Snoozing…");
            String msg;
            try {
                String body = Net.postForBody("/api/action/maintenance", "{\"minutes\":" + (snoozed ? 0 : MINUTES) + "}");
                JSONObject r = body == null ? null : new JSONObject(body);
                if (r != null && r.optBoolean("ok")) {
                    store(this, r);
                    msg = snoozed ? "Alerts are back on." : "Alerts snoozed for 2 hours.";
                } else msg = "The dashboard refused the request.";
            } catch (Exception e) { msg = Net.unreachable(this); }
            toast(msg);
            post(null);
        }, "tile-snooze").start();
    }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("homelab_snooze", Context.MODE_PRIVATE); }

    // Reads the snooze state; false when the dashboard can't be reached.
    static boolean fetch(Context c) {
        try { store(c, new JSONObject(Net.get("/api/maintenance", 4096))); return true; }
        catch (Exception e) { return false; }
    }

    // Keeps the seconds left as the server counted them (its own clock), plus when we heard it, so a phone clock that
    // is off doesn't skew the countdown.
    static void store(Context c, JSONObject d) {
        double until = d.optDouble("until", 0), now = d.optDouble("now", 0);
        long left = d.optBoolean("active") && until > now && now > 0 ? (long) (until - now) : 0;
        prefs(c).edit().putLong("left_s", left).putLong("at_ms", System.currentTimeMillis()).apply();
    }

    static long secondsLeft(SharedPreferences p, long nowMs) {
        long left = p.getLong("left_s", 0), at = p.getLong("at_ms", 0);
        return at == 0 ? 0 : Math.max(0, left - (nowMs - at) / 1000);
    }

    // "1 h 40 min left" / "25 min left" (rounded up, never "0 min").
    static String leftText(long seconds) {
        long min = Math.max(1, (seconds + 59) / 60);
        return (min >= 60 ? (min / 60) + " h" + (min % 60 > 0 ? " " + (min % 60) + " min" : "") : min + " min") + " left";
    }

    private void post(String override) { new Handler(Looper.getMainLooper()).post(() -> render(override)); }

    private void toast(String m) { new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show()); }

    private void render(String override) {
        Tile t = getQsTile();
        if (t == null) return;
        SharedPreferences p = prefs(this);
        boolean known = p.getLong("at_ms", 0) > 0;
        long left = secondsLeft(p, System.currentTimeMillis());
        t.setLabel(getString(R.string.tile_snooze));
        t.setState(left > 0 ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        String sub = override != null ? override : left > 0 ? "Snoozed · " + leftText(left) : known ? "Alerts on" : "Tap to snooze 2 h";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) t.setSubtitle(sub);
        else t.setLabel("Alerts · " + sub);
        t.updateTile();
    }
}
