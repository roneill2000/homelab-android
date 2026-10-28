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
        String headline;
        boolean attention = false;
        if (d == null) {
            headline = away ? context.getString(R.string.widget_away_never) : context.getString(R.string.widget_loading);
        } else {
            headline = d.optString("headline", "—");
            attention = "attention".equals(d.optString("status"));
            if (away) headline = context.getString(R.string.widget_away) + " · " + headline;
        }
        v.setTextViewText(R.id.widget_headline, headline);
        v.setImageViewResource(R.id.widget_icon, attention ? R.drawable.ic_widget_alert : R.drawable.ic_widget_ok);
        int tint = context.getColor(attention ? R.color.widget_attention : R.color.widget_accent);
        v.setInt(R.id.widget_icon, "setColorFilter", tint);
        if (layout != R.layout.widget_small) {
            v.setTextViewText(R.id.value_services, d == null ? "—" : pair(d, "services_up", "services_known"));
            v.setTextViewText(R.id.value_dns, d == null ? "—" : dns(context, d.optString("dns", "unknown")));
            v.setTextViewText(R.id.widget_updated, okAt == 0 ? "" : (away ? context.getString(R.string.widget_last_checked) : context.getString(R.string.widget_updated))
                + " " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(okAt)));
            v.setOnClickPendingIntent(R.id.tile_services, open(context, "view/services", 1));
            v.setOnClickPendingIntent(R.id.tile_dns, open(context, "view/network", 2));
            Intent refresh = new Intent(context, HealthWidgetProvider.class).setAction(ACTION_REFRESH);
            v.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(context, 9, refresh, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        if (layout == R.layout.widget_large) {
            v.setTextViewText(R.id.value_devices, d == null ? "—" : pair(d, "devices_connected", "devices_total"));
            v.setTextViewText(R.id.value_logs, d == null || d.isNull("log_errors_24h") ? "—" : String.valueOf(d.optLong("log_errors_24h")));
            v.setOnClickPendingIntent(R.id.tile_devices, open(context, "view/network", 3));
            v.setOnClickPendingIntent(R.id.tile_logs, open(context, "view/infra", 4));
        }
        v.setOnClickPendingIntent(R.id.widget_root, open(context, "view/overview", 0));
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
