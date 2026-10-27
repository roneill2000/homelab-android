package lan.homelab.app;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.WebViewListener;

public class MainActivity extends BridgeActivity {

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

        // Set dark background for status bar & navigation bar with light icons
        int darkColor = Color.parseColor("#091613");
        window.setStatusBarColor(darkColor);
        window.setNavigationBarColor(darkColor);
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
            insetsController.setAppearanceLightNavigationBars(false);
        }

        // 2. Request 120Hz display refresh rate directly without altering screen resolution
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.preferredRefreshRate = 120.0f;
            window.setAttributes(params);
        }

        // 3. Native performance configuration
        WebView webView = getBridge() != null ? getBridge().getWebView() : null;
        if (webView != null) {
            // LAYER_TYPE_NONE allows Chromium's internal GPU compositor to draw directly to the window surface
            webView.setLayerType(View.LAYER_TYPE_NONE, null);

            WebSettings settings = webView.getSettings();
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);

            // Disable stretch bounce overscroll
            webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

            WebView.setWebContentsDebuggingEnabled(true);
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

        // 4. Hardware Back button handling (modal-aware)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView view = getBridge() != null ? getBridge().getWebView() : null;
                if (view != null) {
                    view.evaluateJavascript(
                        // querySelector would return the first match in document order (often the drawer's hidden scrim),
                        // so check every candidate. Dispatch Escape on the focused element so it bubbles through dialog
                        // onKeyDown handlers up to window; an event dispatched on window never reaches them.
                        "(function() {" +
                        "  var modals = document.querySelectorAll('.m3-search-view, .m3-dialog, .m3-nav-drawer.is-modal.open, .m3-xrail.is-modal, .m3-scrim');" +
                        "  var open = Array.prototype.some.call(modals, function(m) {" +
                        "    return window.getComputedStyle(m).display !== 'none' && m.getClientRects().length > 0;" +
                        "  });" +
                        "  if (!open) return false;" +
                        "  var active = document.activeElement;" +
                        "  var target = (active && active !== document.body) ? active : (document.querySelector('[role=dialog]') || document);" +
                        "  target.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true }));" +
                        "  return true;" +
                        "})()",
                        value -> {
                            if (!"true".equals(value)) {
                                if (view.canGoBack()) {
                                    view.goBack();
                                } else {
                                    setEnabled(false);
                                    getOnBackPressedDispatcher().onBackPressed();
                                    setEnabled(true);
                                }
                            }
                        }
                    );
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
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
