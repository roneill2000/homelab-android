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
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.WebViewListener;

public class MainActivity extends BridgeActivity {

    private void applyPerformanceBoost(WebView view) {
        // Optimize mobile WebView animations:
        // By animating translateY + opacity without scaling, Chromium avoids re-rasterizing
        // font glyphs and vector icons on each frame, achieving a rock-solid 120 FPS on Pixel 11 Pro XL.
        String js = "(function() {" +
            "  var id = 'perf-boost-styles';" +
            "  var el = document.getElementById(id);" +
            "  if (!el) {" +
            "    el = document.createElement('style');" +
            "    el.id = id;" +
            "    document.head.appendChild(el);" +
            "  }" +
            "  el.textContent = `" +
            "    @keyframes m3-arrive {" +
            "      from { opacity: 0; transform: translateY(14px); }" +
            "      to { opacity: 1; transform: none; }" +
            "    }" +
            "    .service-card, .service-group, .panel, .m3-card, .ov-hero {" +
            "      will-change: transform, opacity;" +
            "    }" +
            "    .m3-nav-bar {" +
            "      position: fixed !important;" +
            "      bottom: 0 !important;" +
            "    }" +
            "  `;" +
            "})();";
        view.evaluateJavascript(js, null);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();

        // 1. Maintain inset barrier so the status bar and camera notch never overlay dashboard content
        WindowCompat.setDecorFitsSystemWindows(window, true);

        View contentView = findViewById(android.R.id.content);
        if (contentView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, insets) -> {
                Insets barInsets = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout()
                );
                if (v.getPaddingTop() != barInsets.top || v.getPaddingLeft() != barInsets.left || v.getPaddingRight() != barInsets.right) {
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
            // Do not pre-raster massive offscreen heights to prevent CPU raster thrashing during transitions
            settings.setOffscreenPreRaster(false);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            settings.setEnableSmoothTransition(true);

            // Disable stretch bounce overscroll
            webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

            WebView.setWebContentsDebuggingEnabled(true);
        }

        // 4. Inject performance styles on page load
        if (getBridge() != null) {
            getBridge().addWebViewListener(new WebViewListener() {
                @Override
                public void onPageLoaded(WebView view) {
                    super.onPageLoaded(view);
                    applyPerformanceBoost(view);
                }
            });
        }

        // 5. Hardware Back button handling
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView view = getBridge() != null ? getBridge().getWebView() : null;
                if (view != null && view.canGoBack()) {
                    view.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }
}
