package lan.homelab.app;

import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import androidx.activity.OnBackPressedCallback;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Disable native Android stretch overscroll so it never conflicts with dashboard pull-to-refresh
        if (bridge != null && bridge.getWebView() != null) {
            bridge.getWebView().setOverScrollMode(View.OVER_SCROLL_NEVER);
        }

        // Hardware Back button handling:
        // Navigates within the webview's internal history; exits the app only when at root.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView webView = bridge != null ? bridge.getWebView() : null;
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }
}
