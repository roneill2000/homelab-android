package lan.homelab.app;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

// Small HTTP helpers for background work (widget, alerts, tiles, download progress). Only the home dashboard address,
// which is the one cleartext host the network security config allows; short timeouts so nothing hangs off-network.
final class Net {
    static final String BASE = "http://192.168.0.2";

    private Net() {}

    static String get(String path, int maxBytes) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE + path).openConnection();
        try {
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(6000);
            conn.setUseCaches(false);
            if (conn.getResponseCode() != 200) throw new Exception("HTTP " + conn.getResponseCode());
            return read(conn.getInputStream(), maxBytes);
        } finally {
            conn.disconnect();
        }
    }

    // Like post(), but returns the response body (null unless HTTP 200), for actions that report what they did.
    static String postForBody(String path, String json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE + path).openConnection();
        try {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Dashboard-Action", "1");
            conn.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = conn.getOutputStream()) { out.write(body); }
            return conn.getResponseCode() == 200 ? read(conn.getInputStream(), 4096) : null;
        } finally {
            conn.disconnect();
        }
    }

    // State-changing calls carry the same header the page sends; the dashboard refuses actions without it.
    static int post(String path, String json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE + path).openConnection();
        try {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Dashboard-Action", "1");
            conn.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = conn.getOutputStream()) { out.write(body); }
            return conn.getResponseCode();
        } finally {
            conn.disconnect();
        }
    }

    private static String read(InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            if (out.size() > maxBytes) throw new Exception("response too large");
        }
        return out.toString("UTF-8");
    }
}
