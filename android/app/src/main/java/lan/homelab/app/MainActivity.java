package lan.homelab.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import org.json.JSONObject;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.provider.Settings;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.WebViewListener;

public class MainActivity extends BridgeActivity {

    private OnBackPressedCallback backCallback;
    // On screen right now: "Answer ready" notifications are only posted while you're in another app.
    static volatile boolean visible = false;
    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Set<String> PAGES = new HashSet<>(Arrays.asList(
        "overview", "services", "ai", "media", "network", "fleet", "storage", "infra", "updates"));
    private volatile boolean webReady = false;
    private String pendingJs = null;
    // Offline page: while it shows, a background check retries the dashboard every few seconds and at once when the
    // network changes (Wi-Fi joined, WireGuard on), then loads it. The page itself can't fetch the dashboard.
    private volatile boolean offline = false;
    private final Object probeLock = new Object();
    private Thread prober;
    private ConnectivityManager.NetworkCallback netCallback;
    // VPN apps tried in order by "Open VPN": WireGuard, then AmneziaWG (the VM also serves AmneziaWG on :443).
    private static final String[] VPN_APPS = {"com.wireguard.android", "org.amnezia.awg"};

    public class NativeBridge {
        @JavascriptInterface
        public void tick() {
            runOnUiThread(() -> {
                View v = getBridge() != null ? getBridge().getWebView() : null;
                if (v != null) v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            });
        }

        @JavascriptInterface
        public void tap() {
            runOnUiThread(() -> {
                View v = getBridge() != null ? getBridge().getWebView() : null;
                if (v != null) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            });
        }

        @JavascriptInterface
        public void snap() {
            runOnUiThread(() -> {
                View v = getBridge() != null ? getBridge().getWebView() : null;
                // CONFIRM exists from Android 11; older phones get the closest older effect instead of nothing.
                if (v != null) v.performHapticFeedback(Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
            });
        }

        // System bar colours from the page (theme-aware). Validated, applied, and remembered for the next cold start.
        @JavascriptInterface
        public void setSystemBars(String statusHex, String navHex, boolean lightIcons) {
            if (statusHex == null || navHex == null || !HEX.matcher(statusHex).matches() || !HEX.matcher(navHex).matches()) return;
            getSharedPreferences("homelab", MODE_PRIVATE).edit()
                .putString("status", statusHex).putString("nav", navHex).putBoolean("light", lightIcons).apply();
            runOnUiThread(() -> applySystemBars(statusHex, navHex, lightIcons));
        }

        // The dashboard has painted: hide the splash and run any deep link that arrived before the page was ready.
        @JavascriptInterface
        public void ready() {
            runOnUiThread(() -> {
                webReady = true;
                hideSplash();
                if (pendingJs != null) { runJs(pendingJs); pendingJs = null; }
            });
        }

        // Android 12+ wallpaper accent for the optional Material You colours; empty on older Android.
        @JavascriptInterface
        public String getWallpaperSeed() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return "";
            return String.format("#%06x", 0xFFFFFF & getColor(android.R.color.system_accent1_500));
        }

        // Android 14+ Material You roles (the exact colours other apps use), light and dark. Missing roles are left out
        // and the page generates them from the wallpaper seed. Empty on older Android.
        @JavascriptInterface
        public String getSystemColors() {
            if (Build.VERSION.SDK_INT < 34) return "";
            String[] modal = {"primary", "on_primary", "primary_container", "on_primary_container", "secondary", "on_secondary",
                "secondary_container", "on_secondary_container", "tertiary", "on_tertiary", "tertiary_container", "on_tertiary_container",
                "error", "on_error", "error_container", "on_error_container", "background", "on_background", "surface", "on_surface",
                "surface_variant", "on_surface_variant", "surface_container_lowest", "surface_container_low", "surface_container",
                "surface_container_high", "surface_container_highest", "surface_bright", "surface_dim", "outline", "outline_variant"};
            String[] fixed = {"primary_fixed", "primary_fixed_dim", "on_primary_fixed", "on_primary_fixed_variant", "secondary_fixed",
                "secondary_fixed_dim", "on_secondary_fixed", "on_secondary_fixed_variant", "tertiary_fixed", "tertiary_fixed_dim",
                "on_tertiary_fixed", "on_tertiary_fixed_variant"};
            try {
                JSONObject out = new JSONObject();
                for (String mode : new String[]{"light", "dark"}) {
                    JSONObject m = new JSONObject();
                    for (String r : modal) put(m, r, "system_" + r + "_" + mode);
                    for (String r : fixed) put(m, r, "system_" + r);
                    out.put(mode, m);
                }
                return out.toString();
            } catch (Exception e) {
                return "";
            }
        }

        private void put(JSONObject m, String role, String res) throws Exception {
            int id = getResources().getIdentifier(res, "color", "android");
            if (id != 0) m.put(role.replace("_", "-"), String.format("#%06x", 0xFFFFFF & getColor(id)));
        }

        // The page saw a running download: keep a live progress notification going after you leave the app.
        @JavascriptInterface
        public void watchDownloads() {
            DownloadProgressService.start(MainActivity.this);
        }

        @JavascriptInterface
        public void setBackEnabled(boolean enabled) {
            runOnUiThread(() -> {
                if (backCallback != null) {
                    backCallback.setEnabled(enabled);
                }
            });
        }

        // The Ask box sent a question (v1.9): follow it and notify when the answer is ready if you've left the app.
        @JavascriptInterface
        public void watchAnswer(String id) {
            Answers.watch(MainActivity.this, id);
        }

        // The offline page has shown: retry in the background until the dashboard answers.
        @JavascriptInterface
        public void offlineShown() {
            offline = true;
            startProbing();
        }

        // "Try again" on the offline page: check right now instead of waiting for the next round.
        @JavascriptInterface
        public void retryNow() {
            offline = true;
            startProbing();
            synchronized (probeLock) { probeLock.notifyAll(); }
        }

        // "vpn" when a VPN carries this phone's traffic, else "none" (the offline page words its advice by this).
        @JavascriptInterface
        public String vpnState() {
            return vpnActive() ? "vpn" : "none";
        }

        // Opens WireGuard (or AmneziaWG); without either, Android's VPN settings. Returns what was opened.
        @JavascriptInterface
        public String openVpn() {
            for (String pkg : VPN_APPS) {
                Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
                if (i != null) {
                    startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return pkg.contains("amnezia") ? "AmneziaWG" : "WireGuard";
                }
            }
            try { startActivity(new Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return "settings"; }
            catch (Exception e) { return ""; }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();

        // 1. Maintain top inset barrier so status bar and notch never overlay dashboard content
        // Leave bottom padding at 0 so WebView edge-to-edge passes env(safe-area-inset-bottom) to the dashboard navbar
        WindowCompat.setDecorFitsSystemWindows(window, true);

        View contentView = findViewById(android.R.id.content);
        if (contentView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, insets) -> {
                Insets barInsets = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout()
                );
                if (v.getPaddingTop() != barInsets.top || v.getPaddingLeft() != barInsets.left ||
                    v.getPaddingRight() != barInsets.right || v.getPaddingBottom() != 0) {
                    v.setPadding(barInsets.left, barInsets.top, barInsets.right, 0);
                }
                return insets;
            });
        }

        // System bars: the colours the page sent last time (light theme stays light on a cold start), dark pine by default.
        SharedPreferences prefs = getSharedPreferences("homelab", MODE_PRIVATE);
        applySystemBars(prefs.getString("status", "#091613"), prefs.getString("nav", "#091613"), prefs.getBoolean("light", false));

        // 3. Native performance configuration & Haptics bridge
        WebView webView = getBridge() != null ? getBridge().getWebView() : null;
        if (webView != null) {
            // LAYER_TYPE_NONE allows Chromium's internal GPU compositor to draw directly to the window surface
            webView.setLayerType(View.LAYER_TYPE_NONE, null);

            WebSettings settings = webView.getSettings();
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            // No HTTP cache: anything answering at 192.168.0.2 on another network could otherwise plant a cached
            // page or script (with its own long cache headers) that would then run inside the real dashboard at home.
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

            // Disable stretch bounce overscroll
            webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

            // Remote DevTools only in debuggable builds; a release APK should not expose the WebView over adb.
            WebView.setWebContentsDebuggingEnabled((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);

            // Expose tactile feedback and predictive back control directly to JavaScript
            NativeBridge bridge = new NativeBridge();
            webView.addJavascriptInterface(bridge, "AndroidHaptics");
            webView.addJavascriptInterface(bridge, "AndroidBridge");
        }

        Bridge bridge = getBridge();
        if (bridge != null) {
            bridge.addWebViewListener(new WebViewListener() {
                @Override
                public void onPageStarted(WebView view) {
                    // A new page (reload, or the offline error page) hasn't asked for Back yet: give Back to Android so
                    // it can always leave the app, and queue deep links until the page is ready again.
                    webReady = false;
                    if (backCallback != null) backCallback.setEnabled(false);
                }
            });
        }

        // 4. Hardware & Predictive Back handling:
        // Callback is dynamically enabled/disabled by the web app (via setBackEnabled).
        // When at the root (Overview with no modals), it is disabled so Android 14/15
        // executes smooth native Back-to-Home / Back-to-previous-task predictive window animations.
        // When an overlay or sub-page is active, it is enabled to preview modal collapse and navigate back.
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackStarted(@NonNull BackEventCompat e) {
                backEvent("{phase:'start',progress:" + e.getProgress() + ",edge:" + e.getSwipeEdge() + "}");
            }

            @Override
            public void handleOnBackProgressed(@NonNull BackEventCompat e) {
                backEvent("{phase:'progress',progress:" + e.getProgress() + ",edge:" + e.getSwipeEdge() + "}");
            }

            @Override
            public void handleOnBackCancelled() {
                backEvent("{phase:'cancel'}");
            }

            // The page owns the decision (close the open overlay, go back a page, or to Overview): window.__homelabBack.
            @Override
            public void handleOnBackPressed() {
                runJs("window.__homelabBack?window.__homelabBack():" + backEventJs("{phase:'commit'}"));
            }
        };

        getOnBackPressedDispatcher().addCallback(this, backCallback);

        // Network changes (Wi-Fi joined, WireGuard switched on or off) retry the offline page at once and refresh its advice.
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) {
            netCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(@NonNull Network n) { networkChanged(); }
                @Override public void onLost(@NonNull Network n) { networkChanged(); }
                @Override public void onCapabilitiesChanged(@NonNull Network n, @NonNull NetworkCapabilities c) { networkChanged(); }
            };
            try { cm.registerDefaultNetworkCallback(netCallback); } catch (Exception ignored) { netCallback = null; }
        }

        // Problem alerts and download progress: channels, the background refresh (alerts ride on it even without a
        // widget), and the Android 13+ notification permission, asked once.
        Alerts.channels(this);
        HealthWidgetProvider.schedule(this);
        HealthWidgetProvider.refreshNow(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !prefs.getBoolean("asked_notifications", false)) {
            prefs.edit().putBoolean("asked_notifications", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }

        // The splash stays until the page reports ready(); never longer than 3 s (server down, offline page).
        new Handler(Looper.getMainLooper()).postDelayed(this::hideSplash, 3000);
        // Only a fresh launch: an activity restored from Recents still carries the old intent (e.g. Wake PC).
        if (savedInstanceState == null) handleLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleLink(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        visible = true;
    }

    @Override
    public void onPause() {
        visible = false;
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        // No retrying while the app is in the background; onStart resumes it if the offline page still shows.
        stopProbing();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (offline) startProbing();
    }

    @Override
    public void onDestroy() {
        stopProbing();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null && netCallback != null) { try { cm.unregisterNetworkCallback(netCallback); } catch (Exception ignored) { } }
        super.onDestroy();
    }

    // Launcher shortcuts and the widget send homelab://view/<page> or homelab://action/wake-pc. Only allowlisted
    // constants reach JavaScript; anything else is ignored. Text shared from another app (Android share sheet) opens
    // the Ask box with it filled in; it is passed as a JSON string literal, never as code.
    private void handleLink(Intent intent) {
        boolean send = intent != null && Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType());
        // Text selection menu "Ask AI" (v1.9): the same as sharing the selected text.
        boolean select = intent != null && Intent.ACTION_PROCESS_TEXT.equals(intent.getAction());
        if (send || select) {
            CharSequence shared = select ? intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT) : intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (shared == null && send) shared = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
            String text = shared == null ? "" : shared.toString().trim();
            if (text.isEmpty()) return;
            if (text.length() > 3000) text = text.substring(0, 3000);
            String js = "window.__homelabAsk&&window.__homelabAsk(" + JSONObject.quote(text) + ")";
            if (webReady) runJs(js); else pendingJs = js;
            return;
        }
        Uri uri = intent != null ? intent.getData() : null;
        if (uri == null || !"homelab".equals(uri.getScheme())) return;
        String kind = uri.getHost(), arg = uri.getLastPathSegment();
        String js = null;
        if ("view".equals(kind) && arg != null && PAGES.contains(arg)) {
            js = "window.__homelabOpen&&window.__homelabOpen('" + arg + "')";
        } else if ("action".equals(kind) && "wake-pc".equals(arg)) {
            js = "window.__homelabShortcut&&window.__homelabShortcut('wake-pc')";
        } else if ("action".equals(kind) && "ask".equals(arg)) {
            js = "window.__homelabAsk&&window.__homelabAsk('')";  // launcher shortcut: an empty Ask box, keyboard up
        }
        if (js == null) return;
        if (webReady) runJs(js); else pendingJs = js;
    }

    private void runJs(String js) {
        WebView view = getBridge() != null ? getBridge().getWebView() : null;
        if (view != null) view.evaluateJavascript(js, null);
    }

    private void hideSplash() {
        runJs("window.Capacitor&&Capacitor.Plugins&&Capacitor.Plugins.SplashScreen&&Capacitor.Plugins.SplashScreen.hide({fadeOutDuration:200})");
        // Removing the splash resets the status bar icons to the theme's (light icons), undoing what the page asked for
        // (seen on Android 15: white icons on the light theme's light bar). Re-apply the page's choice once it is gone.
        new Handler(Looper.getMainLooper()).postDelayed(this::reapplySystemBars, 450);
    }

    private void reapplySystemBars() {
        SharedPreferences prefs = getSharedPreferences("homelab", MODE_PRIVATE);
        applySystemBars(prefs.getString("status", "#091613"), prefs.getString("nav", "#091613"), prefs.getBoolean("light", false));
    }

    // Android 15+ draws apps edge to edge and ignores the window's bar colours, so the strip behind the status bar is the
    // content view's top padding: paint that too, or light theme gets dark icons on the dark launch colour.
    @SuppressWarnings("deprecation")
    private void applySystemBars(String statusHex, String navHex, boolean lightIcons) {
        Window w = getWindow();
        View content = findViewById(android.R.id.content);
        if (content != null) content.setBackgroundColor(Color.parseColor(statusHex));
        w.setStatusBarColor(Color.parseColor(statusHex));
        w.setNavigationBarColor(Color.parseColor(navHex));
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(w, w.getDecorView());
        if (c != null) {
            c.setAppearanceLightStatusBars(lightIcons);
            c.setAppearanceLightNavigationBars(lightIcons);
        }
    }

    private void backEvent(String detail) { runJs(backEventJs(detail)); }

    private static String backEventJs(String detail) {
        return "window.dispatchEvent(new CustomEvent('predictiveback',{detail:" + detail + "}))";
    }

    private boolean vpnActive() {
        try {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            NetworkCapabilities c = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
            return c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
        } catch (Exception e) {
            return false;
        }
    }

    private void networkChanged() {
        if (!offline) return;
        runOnUiThread(() -> runJs("window.__offlineUpdate&&window.__offlineUpdate()"));
        synchronized (probeLock) { probeLock.notifyAll(); }
    }

    // One background thread at a time: check /api/widget, load the dashboard once it answers, else wait 5 s (or until
    // a network change or "Try again" wakes it).
    private synchronized void startProbing() {
        if (prober != null && prober.isAlive()) return;
        prober = new Thread(() -> {
            while (offline && !Thread.currentThread().isInterrupted()) {
                try {
                    Net.get("/api/widget", 16384);
                    runOnUiThread(() -> {
                        if (!offline) return;
                        offline = false;
                        WebView view = getBridge() != null ? getBridge().getWebView() : null;
                        if (view != null) view.loadUrl(Net.BASE + "/");
                    });
                    return;
                } catch (Exception ignored) { }
                synchronized (probeLock) {
                    try { probeLock.wait(5000); } catch (InterruptedException e) { return; }
                }
            }
        }, "offline-retry");
        prober.start();
    }

    private synchronized void stopProbing() {
        if (prober != null) prober.interrupt();
        prober = null;
    }
}
