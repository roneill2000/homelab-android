# Changelog

All notable changes to the Homelab Android app. Versions match `versionName` in `android/app/build.gradle`.

## 1.9 — 2026-11-01

### Added
- **Answer ready notification.** After asking a question, leave the app: a notification with the model and the start of the answer appears when it is done and opens the Local AI page. Nothing is posted while the app is on screen.
- **Ask AI from anywhere.** "Ask AI" in the text selection menu and as a launcher shortcut (opens an empty Ask box).
- **AI engine tile** in Quick Settings: shows Strata, the loaded llama.cpp model, Nothing loaded or PC asleep. Tap loads Strata or unloads it to free the GPU (refused while a game runs).
- Friendly model names everywhere a model is shown.

## 1.8 — 2026-11-01

### Added
- **Share to Ask AI.** Share text from any app to Homelab: the Local AI page opens with it in the Ask box. Nothing is sent until you choose.
- **Snooze alerts tile.** Lit while snoozed with the time left; tap snoozes every homelab alert for 2 hours or ends the snooze.
- **Offline page.** Says whether your VPN is on, opens WireGuard (or AmneziaWG, else Android's VPN settings) and retries every 5 s and on every network change.

### Fixed
- Status and navigation bar colours on Android 15+, which ignores window bar colours.
- Splash no longer hangs when the app starts offline.
- Back is decided by the page, so sheets, dialogs and search close before a page is left.

## 1.7 — 2026-10-30

### Fixed
- Download progress service always starts in the foreground (no crash when a download starts as another ends).
- Back returns to Android after a reload or the offline page.
- Restored activities no longer replay Wake PC.
- DNS tile stays tappable while its state loads; the Wake PC tile reports a cancelled sleep.

## 1.6 — 2026-10-30

### Added
- ntfy alerts open the app on the matching page (`homelab://view/<page>`).
- DNS and Gaming PC tiles show the live state (On, Paused · N min left, Asleep).

### Changed
- The app's own 15-minute problem check is off by default (ntfy delivers alerts instantly).

## 1.5 — 2026-10-30

### Changed
- Wake PC tile requires an unlocked phone.
- Minimum Android version raised to 8.0 (API 26).

### Fixed
- Alerts found while notifications were off still show once allowed.
- Download service stops immediately on the Android 15 foreground-service timeout.

## 1.4 — 2026-10-29

### Added
- Problem alerts, Quick Settings tiles (Wake PC, Pause DNS), live download progress notification.
- Exact Material You colours from the system's colour roles on Android 14+.

## 1.3 — 2026-10-28
- Extra-large widget fills tall sizes.

## 1.2 — 2026-10-28
- Widget leads with the gaming PC, then downloads and problems.

## 1.1 — 2026-10-27
- Home-screen widget, launcher shortcuts, theme-matched system bars, splash until the dashboard has drawn.

## 1.0 — 2026-10-27
- First release: full-screen dashboard, edge-to-edge layout, predictive back, haptics, offline page, no WebView cache, hardened release build.
