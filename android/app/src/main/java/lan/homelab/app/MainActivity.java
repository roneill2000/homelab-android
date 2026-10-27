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
            // LAYER_TYPE_NONE allows Chromium's internal hardware compositor to draw directly to the window surface
            // without intermediate Android View offscreen FBO blits.
            webView.setLayerType(View.LAYER_TYPE_NONE, null);

            WebSettings settings = webView.getSettings();
            // Disable offscreen pre-rastering on ultra-high-resolution displays to avoid CPU raster thrashing during transitions
            settings.setOffscreenPreRaster(false);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);

            // Disable stretch bounce overscroll
            webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

            WebView.setWebContentsDebuggingEnabled(true);
        }

        // 4. Inject GPU layer acceleration hints on page load to promote service cards & transitions to hardware GPU layers
        if (getBridge() != null) {
            getBridge().addWebViewListener(new WebViewListener() {
                @Override
                public void onPageLoaded(WebView view) {
                    super.onPageLoaded(view);
                    String js = "(function() {" +
                        "  if (document.getElementById('android-gpu-boost')) return;" +
                        "  var style = document.createElement('style');" +
                        "  style.id = 'android-gpu-boost';" +
                        "  style.textContent = `" +
                        "    ::view-transition-group(*), ::view-transition-old(*), ::view-transition-new(*) {" +
                        "      will-change: transform, opacity;" +
                        "      transform: translateZ(0);" +
                        "      backface-visibility: hidden;" +
                        "    }" +
                        "    .service-card, .service-group, .panel, .m3-card, .ov-hero {" +
                        "      will-change: transform, opacity;" +
                        "      transform: translateZ(0);" +
                        "      backface-visibility: hidden;" +
                        "    }" +
                        "  `;" +
                        "  document.head.appendChild(style);" +
                        "})();";
                    view.evaluateJavascript(js, null);
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
