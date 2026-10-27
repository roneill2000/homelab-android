# Homelab Android App

A lightweight Android app for the [Homelab Command Center](https://github.com/roneill2000/homelab-dashboard). It runs the dashboard full screen with 120Hz scrolling, edge-to-edge system bar insets, and proper hardware back button support.

<p align="center">
  <img src="screenshots/overview.png" width="30%" alt="Overview screen on phone" />
  &nbsp;
  <img src="screenshots/more-sheet.png" width="30%" alt="More destinations bottom sheet" />
  &nbsp;
  <img src="screenshots/updates.png" width="30%" alt="Updates view on phone" />
</p>

---

## Download & install

The signed release APK is in [`release/Homelab-release.apk`](release/Homelab-release.apk) (3.38 MB).

- **Direct download:** [Homelab-release.apk](https://github.com/roneill2000/homelab-android/raw/main/release/Homelab-release.apk)
- **Install via ADB:**
  ```sh
  adb install -r release/Homelab-release.apk
  ```
- **Direct on phone:** download the APK and open it. Allow installing unknown apps if prompted.

### Verification

- **SHA-256:** `cccf79aabde1fd4cbe81e06abdc08a0b929b94ddf57295a5f6b67e7136f59f39`
- **Signing:** signed with v1, v2, and v3 schemes (valid to 2054).

---

## Features

- **Full screen UI.** No browser address bars or navigation chrome.
- **120Hz display refresh.** Unlocks smooth high-refresh scrolling on supported phones.
- **Edge-to-edge layout.** Automatically fits around the camera cutout and gesture navigation bar without clipping content.
- **Back button support.** The back gesture closes open search menus, bottom sheets, and dialogs before backing out of a page.
- **Local network only.** Network traffic is restricted strictly to `192.168.0.2`.

---

## Building

Requires JDK 17+, Android SDK build-tools 35, and Node.js.

1. Install dependencies:
   ```sh
   npm install
   ```
2. Copy the keystore template:
   ```sh
   cp android/app/keystore.properties.example android/app/keystore.properties
   ```
3. Build release APK:
   ```sh
   cd android
   ./gradlew assembleRelease
   ```

---

## License

[MIT](LICENSE)
