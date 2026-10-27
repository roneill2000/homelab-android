# Homelab Android App

A native Android wrapper for the [Homelab Command Center](https://github.com/roneill2000/homelab-dashboard). It runs the dashboard in a tuned WebView with edge-to-edge system insets, 120Hz display refresh, hardware back navigation, and a Pixel-tailored adaptive icon.

---

## Download & install

The signed release APK is in [`release/Homelab-release.apk`](release/Homelab-release.apk) (3.38 MB).

- **Direct download:** [Homelab-release.apk](https://github.com/roneill2000/homelab-android/raw/main/release/Homelab-release.apk)
- **Install via ADB:**
  ```sh
  adb install -r release/Homelab-release.apk
  ```
- **Direct on phone:** open the download link above in Chrome on your phone, open the finished file, and allow unknown apps when prompted.

### Verification

- **SHA-256:** `b43b2a172b915e852f0960ed47c8bfd17931aa91ab8a4496740eca377c2b348a`
- **Signing:** signed with v1 (JAR), v2, and v3 schemes (2048-bit RSA, valid to 2054).
- **Certificate SHA-256:** `f41ed07dd0d78bf08f9850b703b3e003c76ec2f92809d7604340e8e60b963104`

Verify the signature:
```sh
apksigner verify --verbose --print-certs release/Homelab-release.apk
```

---

## What it does

- **120Hz display refresh.** Requests 120Hz on phones with high-refresh displays so scroll motion and tab switches match the screen's refresh rate.
- **Edge-to-edge insets.** The top barrier respects status bar and camera cutouts so the clock never covers the header, while bottom insets pass through directly to the dashboard's `env(safe-area-inset-bottom)`.
- **Direct GPU compositing.** Sets `LAYER_TYPE_NONE` so Chromium's internal compositor draws straight to the window surface without unnecessary View layer re-rasterization.
- **Modal-aware back button.** Pressing the Android back gesture closes active search dialogs, the More sheet, or confirmation dialogs first instead of immediately navigating backward or closing the app.
- **Adaptive launcher icon.** 108×108 dp assets across densities with the mark centered within the 66% safe circle, preventing cutoff on Google Pixel launcher masks.
- **Scoped network security.** Cleartext HTTP traffic is locked strictly to `192.168.0.2` in `network_security_config.xml`. All other unencrypted traffic is rejected.

---

## Building

Requires JDK 17+, Android SDK build-tools 35, and Node.js 20+.

1. Install dependencies:
   ```sh
   npm install
   ```
2. Copy the keystore template:
   ```sh
   cp android/app/keystore.properties.example android/app/keystore.properties
   ```
   Fill in your keystore path, alias, and passwords.
3. Build the release APK:
   ```sh
   cd android
   ./gradlew assembleRelease
   ```
   The built APK is saved to `android/app/build/outputs/apk/release/app-release.apk`.

---

## License

[MIT](LICENSE)
