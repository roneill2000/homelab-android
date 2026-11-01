package lan.homelab.app;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import org.json.JSONObject;

// Quick Settings: which AI engine holds the gaming PC's GPU, and switch it (the dashboard's engine switch,
// POST /api/action/ai-engine). Strata loaded -> tap unloads it (the llama.cpp models load with the next question);
// otherwise -> tap loads Strata. The dashboard refuses while a game runs. Lit while a model is loaded. Unlock first.
public class AiEngineTile extends TileService {
    private static final long WAIT_MS = 150_000;
    private volatile String pending = null;

    @Override
    public void onStartListening() {
        render(null);
        new Thread(() -> { LiveState.fetch(this); post(null); }, "tile-ai-state").start();
    }

    @Override
    public void onClick() {
        if (pending != null) return;
        if (isLocked()) unlockAndRun(this::toggle); else toggle();
    }

    private void toggle() {
        render("Checking…");
        new Thread(() -> {
            JSONObject d = LiveState.fetch(this);
            if (d == null) { toast(Net.unreachable(this)); post(null); return; }
            JSONObject pc = d.optJSONObject("pc");
            if (pc == null || "off".equals(pc.optString("state"))) { toast("The gaming PC is asleep. Wake it first."); post(null); return; }
            boolean strata = ModelNames.isStrata(model(d));
            String target = strata ? "llama" : "strata";
            pending = target;
            post(strata ? "Unloading Strata…" : "Loading Strata…");
            String msg;
            try {
                String body = Net.postForBody("/api/action/ai-engine", "{\"engine\":\"" + target + "\"}");
                JSONObject r = body == null ? null : new JSONObject(body);
                if (r == null) msg = "The dashboard refused the request.";
                else if (!r.optBoolean("ok")) msg = r.optString("error", "The dashboard refused the request.");
                else msg = strata ? "Unloading Strata. The 27B loads with your next question." : "Loading Strata. This takes about 30 s.";
                if (r == null || !r.optBoolean("ok")) pending = null;
            } catch (Exception e) { msg = Net.unreachable(this); pending = null; }
            toast(msg);
            // Follow the switch until the GPU shows the target (or give up after 2.5 min).
            long until = System.currentTimeMillis() + WAIT_MS;
            while (pending != null && System.currentTimeMillis() < until) {
                try { Thread.sleep(5000); } catch (InterruptedException e) { break; }
                JSONObject now = LiveState.fetch(this);
                boolean nowStrata = now != null && ModelNames.isStrata(model(now));
                if (now != null && ("strata".equals(target) == nowStrata)) break;
                post("strata".equals(target) ? "Loading Strata…" : "Unloading Strata…");
            }
            pending = null;
            post(null);
            HealthWidgetProvider.render(this);
        }, "tile-ai").start();
    }

    static String model(JSONObject d) {
        JSONObject pc = d == null ? null : d.optJSONObject("pc");
        return pc == null || pc.isNull("model") ? null : pc.optString("model", null);
    }

    private void post(String override) { new Handler(Looper.getMainLooper()).post(() -> render(override)); }

    private void toast(String m) { new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show()); }

    private void render(String override) {
        Tile t = getQsTile();
        if (t == null) return;
        JSONObject d = LiveState.cached(this);
        JSONObject pc = d.optJSONObject("pc");
        boolean known = pc != null && LiveState.fresh(this);
        boolean off = known && "off".equals(pc.optString("state"));
        String id = model(d);
        t.setLabel("AI engine");
        t.setState(known && !off && id != null ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        String sub = override != null ? override : !known ? "Unknown" : off ? "PC asleep"
            : id == null ? "Nothing loaded · tap for Strata" : ModelNames.isStrata(id) ? "Strata" : ModelNames.label(id);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) t.setSubtitle(sub);
        else t.setLabel("AI · " + sub);
        t.updateTile();
    }
}
