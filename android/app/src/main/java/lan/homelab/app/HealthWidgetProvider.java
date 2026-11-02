package lan.homelab.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.widget.RemoteViews;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

// Home-screen widget: headline + key numbers from /api/widget (same rules as the dashboard). WidgetUpdateWorker
// refreshes it every 15 minutes (the WorkManager minimum) and on the refresh button; off the home network it keeps the
// last values, marked "Away", and never shows zeros for readings it didn't get.
public class HealthWidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH = "lan.homelab.app.WIDGET_REFRESH";
    static final String WORK = "homelab-widget";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        schedule(context);
        render(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        render(context);
    }

    @Override
    public void onEnabled(Context context) {
        schedule(context);
        refreshNow(context);
    }

    @Override
    public void onDisabled(Context context) {
        // The refresh keeps running without a widget: problem alerts and the Quick Settings tiles use it too.
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) refreshNow(context);
    }

    static void schedule(Context context) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(WidgetUpdateWorker.class, 15, TimeUnit.MINUTES)
            .setConstraints(WidgetUpdateWorker.constraints()).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req);
    }

    static void refreshNow(Context context) {
        WorkManager.getInstance(context).enqueue(new OneTimeWorkRequest.Builder(WidgetUpdateWorker.class).build());
    }

    static void render(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, HealthWidgetProvider.class));
        for (int id : ids) {
            RemoteViews views;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Map<SizeF, RemoteViews> sizes = new HashMap<>();
                sizes.put(new SizeF(110f, 40f), build(context, R.layout.widget_small));
                sizes.put(new SizeF(110f, 150f), build(context, R.layout.widget_compact));
                sizes.put(new SizeF(230f, 300f), build(context, R.layout.widget_full));
                views = new RemoteViews(sizes);
            } else {
                Bundle o = manager.getAppWidgetOptions(id);
                int w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH), h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
                views = build(context, h < 150 ? R.layout.widget_small : w >= 230 && h >= 300 ? R.layout.widget_full : R.layout.widget_compact);
            }
            manager.updateAppWidget(id, views);
        }
    }

    // Status first: a header that says whether anything needs attention, the gaming PC (GPU numbers only while it is
    // busy), then storage, internet, DNS, now playing, downloads and updates. A reading the dashboard didn't have is a
    // dash, never a zero.
    private static RemoteViews build(Context context, int layout) {
        RemoteViews v = new RemoteViews(context.getPackageName(), layout);
        SharedPreferences p = context.getSharedPreferences("homelab_widget", Context.MODE_PRIVATE);
        String raw = p.getString("json", null);
        long okAt = p.getLong("ok_at", 0);
        boolean away = p.getBoolean("away", false);
        JSONObject d = null;
        try { if (raw != null) d = new JSONObject(raw); } catch (Exception ignored) { }
        String time = okAt > 0 ? DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(okAt)) : "";
        int on = context.getColor(R.color.widget_on), variant = context.getColor(R.color.widget_on_variant),
            accent = context.getColor(R.color.widget_accent), warn = context.getColor(R.color.widget_warn),
            bad = context.getColor(R.color.widget_attention);

        // Header
        JSONArray probs = d != null ? d.optJSONArray("problems") : null;
        int nProbs = probs != null ? probs.length() : 0;
        String headline, sub;
        int icon = R.drawable.ic_widget_ok, iconColor = accent;
        if (d == null) {
            headline = away ? context.getString(R.string.widget_away_never) : context.getString(R.string.widget_loading);
            sub = "";
            iconColor = variant;
        } else if (away) {
            headline = context.getString(R.string.widget_away);
            sub = context.getString(R.string.widget_last_checked) + " " + time;
            iconColor = variant;
        } else if (nProbs > 0) {
            headline = nProbs == 1 ? probs.optString(0) : nProbs + " problems";
            StringBuilder b = new StringBuilder();
            for (int k = nProbs == 1 ? 1 : 0; k < nProbs; k++) b.append(b.length() > 0 ? " · " : "").append(probs.optString(k));
            sub = b.length() > 0 ? b.toString() : services(context, d, time);
            icon = R.drawable.ic_widget_alert;
            iconColor = bad;
        } else {
            headline = context.getString(R.string.widget_all_clear);
            sub = services(context, d, time);
        }
        v.setTextViewText(R.id.widget_headline, headline);
        v.setTextViewText(R.id.widget_sub, sub);
        v.setImageViewResource(R.id.widget_icon, icon);
        v.setInt(R.id.widget_icon, "setColorFilter", iconColor);
        v.setOnClickPendingIntent(R.id.widget_root, open(context, "view/overview", 0));
        if (layout == R.layout.widget_small) return v;
        Intent refresh = new Intent(context, HealthWidgetProvider.class).setAction(ACTION_REFRESH);
        v.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(context, 9, refresh, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

        // Gaming PC: one line when idle; GPU readings appear only while it generates or the GPU is busy (a game).
        JSONObject pc = d != null ? d.optJSONObject("pc") : null;
        JSONObject gpu = pc != null ? pc.optJSONObject("gpu") : null;
        String state = pc != null ? pc.optString("state", "unknown") : "unknown";
        int util = gpu != null && !gpu.isNull("utilization_pct") ? gpu.optInt("utilization_pct") : -1;
        boolean busy = "generating".equals(state) || util >= 20;
        String full = pc != null && !pc.isNull("model") ? ModelNames.label(pc.optString("model")) : null;
        // "Strata · Swift 1.5 IQ3_XXS" -> value "Swift 1.5", engine "Strata" for the detail line
        String engine = full != null && full.contains(" · ") ? full.substring(0, full.indexOf(" · ")) : null;
        String model = full == null ? null : (engine != null ? full.substring(full.indexOf(" · ") + 3) : full).replaceAll("\\s+(UD-)?I?Q\\d\\S*$", "");
        String pcValue;
        switch (state) {
            case "off": pcValue = context.getString(R.string.widget_asleep); break;
            case "idle": pcValue = busy ? "In use · GPU " + util + "%" : context.getString(R.string.widget_nothing_loaded); break;
            case "loading": pcValue = "Loading " + (model != null ? model : "a model"); break;
            case "generating": pcValue = (model != null ? model : "Generating") + (pc.has("tok_s") ? String.format(" · %.0f tok/s", pc.optDouble("tok_s")) : ""); break;
            case "model_loaded": pcValue = model != null ? model : "Model loaded"; break;
            default: pcValue = "—";
        }
        String pcSub = "";
        if (gpu != null && busy) {
            StringBuilder g = new StringBuilder();
            if (!gpu.isNull("power_w")) g.append(String.format("%.0f W", gpu.optDouble("power_w")));
            if (!gpu.isNull("temperature_c")) g.append(g.length() > 0 ? " · " : "").append(gpu.optInt("temperature_c")).append(" °C");
            if (util >= 0) g.append(g.length() > 0 ? " · " : "").append("GPU ").append(util).append("%");
            if (!gpu.isNull("vram_used_mib") && !gpu.isNull("vram_total_mib"))
                g.append(" · ").append(String.format("%.1f/%.0f GB", gpu.optDouble("vram_used_mib") / 1024, gpu.optDouble("vram_total_mib") / 1024));
            pcSub = g.toString();
        } else if ("model_loaded".equals(state)) {
            pcSub = (engine != null ? engine + " · " : "") + "Ready · GPU idle";
        } else if ("off".equals(state)) {
            pcSub = "Wake it from Quick Settings";
        }
        v.setTextViewText(R.id.value_pc, pcValue);
        v.setTextViewText(R.id.sub_pc, pcSub);
        v.setOnClickPendingIntent(R.id.cell_pc, open(context, "view/ai", 5));

        // Storage
        JSONObject st = d != null ? d.optJSONObject("storage") : null;
        String stValue = "—", stSub = "";
        boolean driveBad = st != null && st.has("drives_ok") && !st.isNull("drives_ok") && !st.optBoolean("drives_ok");
        if (st != null) {
            if (driveBad) stValue = context.getString(R.string.widget_drive_warning);
            else if (!st.isNull("array_pct")) stValue = "Array " + Math.round(st.optDouble("array_pct")) + "%";
            StringBuilder b = new StringBuilder();
            if (!st.isNull("cache_pct")) b.append("Cache ").append(Math.round(st.optDouble("cache_pct"))).append("%");
            stSub = b.toString();
        }
        cell(v, R.id.value_storage, R.id.sub_storage, R.id.icon_storage, stValue, stSub, driveBad ? bad : on, driveBad ? bad : accent);
        v.setOnClickPendingIntent(R.id.cell_storage, open(context, "view/storage", 6));

        // Internet (latest speed test) and DNS blocking
        JSONObject net = d != null ? d.optJSONObject("network") : null;
        String netValue = "—", netSub = "";
        if (net != null && !net.isNull("down_mbps")) {
            netValue = speed(net.optDouble("down_mbps"));
            StringBuilder b = new StringBuilder();
            if (!net.isNull("up_mbps")) b.append("↑ ").append(speed(net.optDouble("up_mbps")));
            netSub = b.toString();
        }
        cell(v, R.id.value_network, R.id.sub_network, R.id.icon_network, netValue, netSub, on, accent);
        v.setOnClickPendingIntent(R.id.cell_network, open(context, "view/network", 7));
        JSONObject dns = d != null ? d.optJSONObject("dns") : null;
        String dnsValue = "—", dnsSub = "";
        int dnsColor = on;
        if (dns != null && !dns.isNull("blocking")) {
            if (dns.optBoolean("blocking")) {
                dnsValue = context.getString(R.string.widget_protected);
                if (net != null && !net.isNull("dns_blocked_pct")) dnsSub = String.format("%.1f%% blocked", net.optDouble("dns_blocked_pct"));
            } else {
                dnsValue = context.getString(R.string.widget_paused);
                dnsColor = warn;
                if (!dns.isNull("seconds_left")) dnsSub = Math.max(1, Math.round(dns.optInt("seconds_left") / 60f)) + " min left";
            }
        }
        cell(v, R.id.value_dns, R.id.sub_dns, R.id.icon_dns, dnsValue, dnsSub, dnsColor, dnsColor == warn ? warn : accent);
        v.setOnClickPendingIntent(R.id.cell_dns, open(context, "view/network", 8));
        if (layout == R.layout.widget_compact) return v;

        // Now playing
        JSONObject media = d != null ? d.optJSONObject("media") : null;
        String mValue = "—", mSub = "";
        if (media != null) {
            JSONObject np = media.optJSONObject("now_playing");
            int streams = media.optInt("streams", 0);
            if (np == null || streams == 0) {
                mValue = context.getString(R.string.widget_idle);
                mSub = context.getString(R.string.widget_nothing_playing);
            } else {
                mValue = np.optString("title", "Playing");
                StringBuilder b = new StringBuilder();
                if (!np.isNull("episode")) b.append(np.optString("episode"));
                if (np.optBoolean("paused")) b.append(b.length() > 0 ? " · " : "").append("paused");
                if (!np.isNull("progress")) b.append(b.length() > 0 ? " · " : "").append(Math.round(np.optDouble("progress") * 100)).append("%");
                if (streams > 1) b.append(b.length() > 0 ? " · " : "").append("+").append(streams - 1).append(" more");
                mSub = b.toString();
            }
        }
        cell(v, R.id.value_media, R.id.sub_media, R.id.icon_media, mValue, mSub, on, accent);
        v.setOnClickPendingIntent(R.id.cell_media, open(context, "view/media", 1));

        // Downloads
        JSONObject dl = d != null ? d.optJSONObject("downloads") : null;
        String dlValue = "—", dlSub = "";
        if (dl != null && "active".equals(dl.optString("state"))) {
            dlValue = dl.isNull("speed") ? "Downloading" : dl.optString("speed");
            if (!dl.isNull("queue") && dl.optInt("queue") > 0) dlSub = dl.optInt("queue") + " " + context.getString(R.string.widget_queued);
        } else if (dl != null && "idle".equals(dl.optString("state"))) {
            dlValue = context.getString(R.string.widget_idle);
            dlSub = "Queue empty";
        }
        cell(v, R.id.value_downloads, R.id.sub_downloads, R.id.icon_downloads, dlValue, dlSub, on, accent);
        v.setOnClickPendingIntent(R.id.cell_downloads, open(context, "view/media", 3));

        // Updates
        JSONObject up = d != null ? d.optJSONObject("updates") : null;
        String uValue = "—", uSub = "";
        int uColor = on;
        if (up != null && !up.isNull("pending")) {
            int n = up.optInt("pending");
            uValue = n == 0 ? context.getString(R.string.widget_up_to_date) : n + " pending";
            if (n > 0) uColor = warn;
            if (!up.isNull("checked_at")) uSub = "Checked " + ago(System.currentTimeMillis() / 1000 - up.optLong("checked_at"));
        }
        cell(v, R.id.value_updates, R.id.sub_updates, R.id.icon_updates, uValue, uSub, uColor, uColor == warn ? warn : accent);
        v.setOnClickPendingIntent(R.id.cell_updates, open(context, "view/updates", 4));
        return v;
    }

    private static void cell(RemoteViews v, int value, int sub, int icon, String text, String detail, int color, int iconColor) {
        v.setTextViewText(value, text);
        v.setTextColor(value, color);
        v.setTextViewText(sub, detail);
        v.setInt(icon, "setColorFilter", iconColor);
    }

    private static String services(Context context, JSONObject d, String time) {
        StringBuilder b = new StringBuilder();
        if (!d.isNull("services_up") && !d.isNull("services_known"))
            b.append(d.optLong("services_up")).append("/").append(d.optLong("services_known")).append(" ").append(context.getString(R.string.widget_services_up));
        if (!time.isEmpty()) b.append(b.length() > 0 ? " · " : "").append(time);
        return b.toString();
    }

    private static String ago(long s) {
        return s < 3600 ? "just now" : s < 86400 ? (s / 3600) + "h ago" : (s / 86400) + "d ago";
    }

    private static String speed(double mbps) {
        return mbps >= 1000 ? String.format("%.2f Gbps", mbps / 1000) : String.format("%.0f Mbps", mbps);
    }

    private static PendingIntent open(Context context, String path, int code) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("homelab://" + path), context, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
