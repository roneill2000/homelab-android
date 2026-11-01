package lan.homelab.app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;

// Fetches /api/widget through LiveState (Net: the one cleartext address, short timeouts). A failure
// (away from home, server down) keeps the last good values and marks the widget "Away"; it never writes zeros.
public class WidgetUpdateWorker extends Worker {
    public WidgetUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

    static Constraints constraints() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        SharedPreferences p = LiveState.prefs(c);
        JSONObject d = LiveState.fetch(c);  // caches it and clears "away" on success
        if (d == null) p.edit().putBoolean("away", true).apply();
        // Problem alerts come from ntfy (instant, works away from home); the app's own 15-minute check is off unless
        // the "app_alerts" preference is set (v1.6, 30 Oct 2026).
        else if (p.getBoolean("app_alerts", false)) Alerts.process(c, d);
        HealthWidgetProvider.render(c);
        return Result.success();
    }
}
