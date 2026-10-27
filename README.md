# Homelab Android App

[![Release](https://img.shields.io/badge/release-v1.0.0-blue.svg)](release/Homelab-release.apk)
[![Platform](https://img.shields.io/badge/platform-Android%2014%2B%20(API%2034--35)-green.svg)](release/Homelab-release.apk)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Signed](https://img.shields.io/badge/signatures-v1%20%7C%20v2%20%7C%20v3-brightgreen.svg)](release/Homelab-release.apk)

The official native Android wrapper for the [Homelab Command Center](https://github.com/roneill2000/homelab-dashboard) (`http://192.168.0.2`). Built on Capacitor 6 and optimized for high-refresh-rate Google Pixel devices (including Pixel 11 Pro XL).

---

## 📥 Download Release APK

| Package | Version | Size | Download Link |
| :--- | :--- | :--- | :--- |
| **Homelab Release APK** | `v1.0.0` (code `1`) | 3.38 MB | [**Download `Homelab-release.apk`**](release/Homelab-release.apk?raw=true) |

Direct URL:  
`https://github.com/roneill2000/homelab-android/raw/main/release/Homelab-release.apk`

### Integrity & Signature Verification

- **SHA-256 Checksum:**  
  `b43b2a172b915e852f0960ed47c8bfd17931aa91ab8a4496740eca377c2b348a`
- **Signing Schemes:** Verified using v1 (JAR), v2 (APK Signature Scheme v2), and v3 (APK Signature Scheme v3).
- **Certificate SHA-256 Fingerprint:**  
  `f41ed07dd0d78bf08f9850b703b3e003c76ec2f92809d7604340e8e60b963104`

Verify the downloaded APK locally using `apksigner`:
```bash
apksigner verify --verbose --print-certs Homelab-release.apk
```

---

## 📲 Sideloading & Installation

### Option 1: Direct Download on Phone
1. Open Chrome or your browser on your Android device and navigate to:  
   `https://github.com/roneill2000/homelab-android/raw/main/release/Homelab-release.apk`
2. Download the APK and tap the completed download notification or open it via the **Files** app.
3. If prompted, toggle **Allow from this source** in *Install unknown apps*.
4. Tap **Install** (or **Update**).

### Option 2: Install via ADB
With your Android phone connected via USB or wireless debugging:
```bash
adb install -r release/Homelab-release.apk
```

---

## ✨ Features & Display Optimizations

- **120Hz High-Refresh Native Pipeline:** Requests a 120.0 Hz display refresh rate (`preferredRefreshRate = 120.0f`) on supported LTPO OLED displays without altering screen resolution.
- **Zero-Copy GPU Surface Rendering:** Configured with `webView.setLayerType(View.LAYER_TYPE_NONE, null)` so Chromium's internal Vulkan/Skia compositor renders directly to the window surface without intermediate Android View raster caching.
- **True Edge-to-Edge with Safe Insets:**
  - Status bar and camera cutout insets are managed natively via `WindowCompat.setDecorFitsSystemWindows(window, true)` and `ViewCompat.setOnApplyWindowInsetsListener`.
  - Bottom padding is preserved at `0` natively so the edge-to-edge WebView transparently passes `env(safe-area-inset-bottom)` to the web dashboard's bottom navigation bar without double-spacing.
  - Status and navigation bars are themed to `#091613` with light icons.
- **Pixel-Perfect Adaptive Launcher Icon:**
  - Full-bleed background `#1f4d45` (`ic_launcher_background.xml`).
  - Standalone house logo mark scaled to a 62.8% safe zone (68×68 dp centered within 108×108 dp) across all density buckets (`mdpi` to `xxxhdpi`), eliminating squishing or clipping on circular Pixel launcher masks.
- **Modal-Aware Hardware Back Button Navigation:**
  - Evaluates active Material 3 in-page dialogs and bottom sheets (`.m3-search-view`, `.m3-dialog`, `.m3-nav-drawer.is-modal.open`, `.m3-xrail.is-modal`, `.m3-scrim`).
  - Dismisses active dialogs seamlessly on Back press by dispatching `Escape` into the WebView before popping browser history or exiting the application.
- **Network Security Configuration:**
  - Scoped cleartext traffic strictly permitted only to `192.168.0.2` via `network_security_config.xml`. All other non-HTTPS endpoints remain blocked.

---

## 🛠️ Building from Source

### Prerequisites
- JDK 17 or newer
- Android SDK with Build Tools `35.0.0` and Android Platform `35`
- Node.js 20+ and npm

### 1. Clone & Install Dependencies
```bash
git clone https://github.com/roneill2000/homelab-android.git
cd homelab-android
npm install
```

### 2. Configure Keystore Properties
Copy the example properties file and provide your keystore credentials:
```bash
cp android/app/keystore.properties.example android/app/keystore.properties
```
Edit `android/app/keystore.properties`:
```properties
storeFile=/path/to/homelab-release.keystore
storePassword=YOUR_STORE_PASSWORD
keyAlias=homelab
keyPassword=YOUR_KEY_PASSWORD
```

### 3. Build Signed Release APK
```bash
cd android
./gradlew assembleRelease
```
The output APK will be located at:
```
android/app/build/outputs/apk/release/app-release.apk
```

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
