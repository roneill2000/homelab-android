import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'lan.homelab.app',
  appName: 'Homelab',
  webDir: 'www',
  backgroundColor: '#091613',
  server: {
    url: 'http://192.168.0.2',
    cleartext: true,
    // Bundled page shown when the dashboard can't be reached (off home Wi-Fi without the VPN, or the server down).
    errorPath: 'error.html',
  },
  plugins: {
    // MainActivity owns Back (predictive back + modal handling). The App plugin's own always-on handler would
    // swallow Back at the root, so the app could never exit or play the system back-to-home animation.
    App: {
      disableBackButtonHandler: true,
    },
    SplashScreen: {
      // Hidden by the page once it has painted (AndroidBridge.ready); the plugin itself hides it after 3 s at the latest
      // (v1.8: the old JS-only fallback never fired on the offline page, which has no Capacitor JS, so the splash hung).
      launchShowDuration: 3000,
      launchAutoHide: true,
      backgroundColor: '#091613',
      androidSplashResourceName: 'splash',
      androidScaleType: 'CENTER_CROP',
      showSpinner: false,
    },
  },
};

export default config;
