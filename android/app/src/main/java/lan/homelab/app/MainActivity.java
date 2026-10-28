package lan.homelab.app;

import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.WindowManager;
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
    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Set<String> PAGES = new HashSet<>(Arrays.asList(
        "overview", "services", "ai", "media", "network", "fleet", "storage", "infra", "updates"));
    private volatile boolean webReady = false;
    private String pendingJs = null;

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
                if (v != null) v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
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

        @JavascriptInterface
        public void setBackEnabled(boolean enabled) {
            runOnUiThread(() -> {
                if (backCallback != null) {
                    backCallback.setEnabled(enabled);
                }
            });
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

        // 2. Request 120Hz display refresh rate directly without altering screen resolution
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.preferredRefreshRate = 120.0f;
            window.setAttributes(params);
        }

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
                public void onPageLoaded(WebView view) {
                    injectIconStyleFix(view);
                }

                @Override
                public void onPageStarted(WebView view) {
                    injectIconStyleFix(view);
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
            public void handleOnBackStarted(@NonNull BackEventCompat backEvent) {
                WebView view = getBridge() != null ? getBridge().getWebView() : null;
                if (view != null) {
                    view.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('predictiveback', { detail: { phase: 'start', progress: " +
                        backEvent.getProgress() + ", edge: " + backEvent.getSwipeEdge() + " } }));",
                        null
                    );
                }
            }

            @Override
            public void handleOnBackProgressed(@NonNull BackEventCompat backEvent) {
                WebView view = getBridge() != null ? getBridge().getWebView() : null;
                if (view != null) {
                    view.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('predictiveback', { detail: { phase: 'progress', progress: " +
                        backEvent.getProgress() + ", edge: " + backEvent.getSwipeEdge() + " } }));",
                        null
                    );
                }
            }

            @Override
            public void handleOnBackCancelled() {
                WebView view = getBridge() != null ? getBridge().getWebView() : null;
                if (view != null) {
                    view.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('predictiveback', { detail: { phase: 'cancel' } }));",
                        null
                    );
                }
            }

            @Override
            public void handleOnBackPressed() {
                WebView view = getBridge() != null ? getBridge().getWebView() : null;
                if (view != null) {
                    view.evaluateJavascript(
                        "(function() {" +
                        "  var modals = document.querySelectorAll('.m3-search-view, .m3-dialog, .m3-nav-drawer.is-modal.open, .m3-xrail.is-modal, .m3-scrim');" +
                        "  var open = Array.prototype.some.call(modals, function(m) {" +
                        "    return window.getComputedStyle(m).display !== 'none' && m.getClientRects().length > 0;" +
                        "  });" +
                        "  var hasOverlay = (typeof window.__hasOpenOverlay === 'function' && window.__hasOpenOverlay()) || open;" +
                        "  window.dispatchEvent(new CustomEvent('predictiveback', { detail: { phase: 'commit' } }));" +
                        "  if (hasOverlay) {" +
                        "    return;" +
                        "  }" +
                        "  if (window.history.length > 1 && window.location.hash !== '' && window.location.hash !== '#/' && window.location.hash !== '#/overview') {" +
                        "    window.history.back();" +
                        "    return;" +
                        "  }" +
                        "  if (window.__navigateOverview) {" +
                        "    window.__navigateOverview();" +
                        "  }" +
                        "})()",
                        null
                    );
                }
            }
        };

        getOnBackPressedDispatcher().addCallback(this, backCallback);

        // The splash stays until the page reports ready(); never longer than 3 s (server down, offline page).
        new Handler(Looper.getMainLooper()).postDelayed(this::hideSplash, 3000);
        handleLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleLink(intent);
    }

    // Launcher shortcuts and the widget send homelab://view/<page> or homelab://action/wake-pc. Only allowlisted
    // constants reach JavaScript; anything else is ignored.
    private void handleLink(Intent intent) {
        Uri uri = intent != null ? intent.getData() : null;
        if (uri == null || !"homelab".equals(uri.getScheme())) return;
        String kind = uri.getHost(), arg = uri.getLastPathSegment();
        String js = null;
        if ("view".equals(kind) && arg != null && PAGES.contains(arg)) {
            js = "window.__homelabOpen&&window.__homelabOpen('" + arg + "')";
        } else if ("action".equals(kind) && "wake-pc".equals(arg)) {
            js = "window.__homelabShortcut&&window.__homelabShortcut('wake-pc')";
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
    }

    private void applySystemBars(String statusHex, String navHex, boolean lightIcons) {
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor(statusHex));
        w.setNavigationBarColor(Color.parseColor(navHex));
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(w, w.getDecorView());
        if (c != null) {
            c.setAppearanceLightStatusBars(lightIcons);
            c.setAppearanceLightNavigationBars(lightIcons);
        }
    }

    private void injectIconStyleFix(WebView webView) {
        if (webView == null) return;
        String css = ".material-symbols-rounded { overflow: visible !important; width: auto !important; height: auto !important; min-width: 1em !important; } " +
                     ".m3-nav-indicator { overflow: visible !important; } " +
                     ".m3-nav-indicator .material-symbols-rounded { overflow: visible !important; display: inline-flex !important; align-items: center !important; justify-content: center !important; } " +
                     ".m3-nav-bar__item:last-child .m3-nav-indicator .material-symbols-rounded { transform: translateY(-2px) !important; }";

        String js = "(function() {" +
                    "  if (document.getElementById('agy-icon-fix')) return;" +
                    "  var style = document.createElement('style');" +
                    "  style.id = 'agy-icon-fix';" +
                    "  style.textContent = '" + css + "';" +
                    "  (document.head || document.documentElement).appendChild(style);" +
                    "})();";

        webView.post(() -> webView.evaluateJavascript(js, null));
    }
}
