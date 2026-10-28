package lan.homelab.app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

// Fetches http://192.168.0.2/api/widget (cleartext is allowed for that address only) with short timeouts. A failure
// (away from home, server down) keeps the last good values and marks the widget "Away"; it never writes zeros.
public class WidgetUpdateWorker extends Worker {
    static final String URL_WIDGET = "http://192.168.0.2/api/widget";

    public WidgetUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

    static Constraints constraints() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        SharedPreferences p = c.getSharedPreferences("homelab_widget", Context.MODE_PRIVATE);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(URL_WIDGET).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setUseCaches(false);
            if (conn.getResponseCode() != 200) throw new Exception("HTTP " + conn.getResponseCode());
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > 16384) throw new Exception("response too large");
            }
            JSONObject d = new JSONObject(out.toString("UTF-8"));
            if (!d.has("status")) throw new Exception("unexpected response");
            p.edit().putString("json", d.toString()).putLong("ok_at", System.currentTimeMillis()).putBoolean("away", false).apply();
        } catch (Exception e) {
            p.edit().putBoolean("away", true).apply();
        } finally {
            if (conn != null) conn.disconnect();
        }
        HealthWidgetProvider.render(c);
        return Result.success();
    }
}
