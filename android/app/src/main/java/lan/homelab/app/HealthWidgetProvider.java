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
        WorkManager.getInstance(context).cancelUniqueWork(WORK);
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
                sizes.put(new SizeF(110f, 110f), build(context, R.layout.widget_medium));
                sizes.put(new SizeF(250f, 110f), build(context, R.layout.widget_large));
                views = new RemoteViews(sizes);
            } else {
                Bundle o = manager.getAppWidgetOptions(id);
                int w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH), h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
                views = build(context, h < 100 ? R.layout.widget_small : w >= 250 ? R.layout.widget_large : R.layout.widget_medium);
            }
            manager.updateAppWidget(id, views);
        }
    }

    private static RemoteViews build(Context context, int layout) {
        RemoteViews v = new RemoteViews(context.getPackageName(), layout);
        SharedPreferences p = context.getSharedPreferences("homelab_widget", Context.MODE_PRIVATE);
        String raw = p.getString("json", null);
        long okAt = p.getLong("ok_at", 0);
        boolean away = p.getBoolean("away", false);
        JSONObject d = null;
        try { if (raw != null) d = new JSONObject(raw); } catch (Exception ignored) { }
        // Headline: the gaming PC. Then a detail line (GPU readings or the model), downloads, and problems.
        String headline;
        String detail = "";
        boolean attention = false;
        if (d == null) {
            headline = away ? context.getString(R.string.widget_away_never) : context.getString(R.string.widget_loading);
        } else {
            headline = d.optString("headline", "—");
            JSONObject pc = d.optJSONObject("pc");
            JSONObject gpu = pc != null ? pc.optJSONObject("gpu") : null;
            StringBuilder sb = new StringBuilder();
            if (pc != null && !pc.isNull("model")) sb.append(pc.optString("model"));
            if (pc != null && pc.has("tok_s")) sb.append(sb.length() > 0 ? " · " : "").append(String.format("%.0f tok/s", pc.optDouble("tok_s")));
            if (gpu != null) {
                if (!gpu.isNull("power_w")) sb.append(sb.length() > 0 ? " · " : "").append(String.format("%.0f W", gpu.optDouble("power_w")));
                if (!gpu.isNull("temperature_c")) sb.append(" · ").append(gpu.optInt("temperature_c")).append(" °C");
                if (!gpu.isNull("utilization_pct")) sb.append(" · GPU ").append(gpu.optInt("utilization_pct")).append("%");
                if (!gpu.isNull("vram_used_mib") && !gpu.isNull("vram_total_mib"))
                    sb.append(" · ").append(String.format("%.1f/%.0f GB", gpu.optDouble("vram_used_mib") / 1024, gpu.optDouble("vram_total_mib") / 1024));
            }
            detail = sb.toString();
            if (away) {
                headline = context.getString(R.string.widget_away) + " · " + headline;
                long okAtLocal = p.getLong("ok_at", 0);
                if (okAtLocal > 0) detail = context.getString(R.string.widget_last_checked) + " " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(okAtLocal));
            }
            attention = d.optJSONArray("problems") != null && d.optJSONArray("problems").length() > 0;
        }
        v.setTextViewText(R.id.widget_headline, headline);
        boolean pcOn = d != null && d.optJSONObject("pc") != null && !"off".equals(d.optJSONObject("pc").optString("state"));
        v.setInt(R.id.widget_icon, "setColorFilter", context.getColor(attention ? R.color.widget_attention : pcOn ? R.color.widget_accent : R.color.widget_on_variant));
        if (layout != R.layout.widget_small) {
            v.setTextViewText(R.id.widget_detail, detail);
            JSONObject dl = d != null ? d.optJSONObject("downloads") : null;
            String dlText = "—";
            if (dl != null && "active".equals(dl.optString("state"))) {
                dlText = dl.isNull("speed") ? "" : dl.optString("speed");
                if (!dl.isNull("queue") && dl.optInt("queue") > 0) dlText += (dlText.isEmpty() ? "" : " · ") + dl.optInt("queue") + " " + context.getString(R.string.widget_queued);
            } else if (dl != null && "idle".equals(dl.optString("state"))) {
                dlText = context.getString(R.string.widget_idle);
            }
            v.setTextViewText(R.id.value_downloads, dlText);
            org.json.JSONArray probs = d != null ? d.optJSONArray("problems") : null;
            StringBuilder pt = new StringBuilder();
            if (probs != null) for (int k = 0; k < probs.length(); k++) pt.append(k > 0 ? " · " : "").append(probs.optString(k));
            v.setTextViewText(R.id.value_problems, d == null ? "—" : pt.length() > 0 ? pt.toString() : context.getString(R.string.widget_no_problems));
            v.setTextColor(R.id.value_problems, context.getColor(pt.length() > 0 ? R.color.widget_attention : R.color.widget_on));
            v.setInt(R.id.icon_problems, "setColorFilter", context.getColor(pt.length() > 0 ? R.color.widget_attention : R.color.widget_on_variant));
            v.setOnClickPendingIntent(R.id.row_downloads, open(context, "view/media", 1));
            v.setOnClickPendingIntent(R.id.row_problems, open(context, "view/overview", 2));
            Intent refresh = new Intent(context, HealthWidgetProvider.class).setAction(ACTION_REFRESH);
            v.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(context, 9, refresh, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        v.setOnClickPendingIntent(R.id.widget_root, open(context, "view/ai", 0));
        return v;
    }

    private static String pair(JSONObject d, String a, String b) {
        if (d.isNull(a) || d.isNull(b) || !d.has(a) || !d.has(b)) return "—";
        return d.optLong(a) + " / " + d.optLong(b);
    }

    private static String dns(Context c, String s) {
        switch (s) {
            case "protected": return c.getString(R.string.widget_dns_on);
            case "paused": return c.getString(R.string.widget_dns_paused);
            case "mixed": return c.getString(R.string.widget_dns_mixed);
            default: return "—";
        }
    }

    private static PendingIntent open(Context context, String path, int code) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("homelab://" + path), context, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
