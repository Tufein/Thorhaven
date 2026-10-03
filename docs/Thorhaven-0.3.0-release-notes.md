# Offline guides, battery statistics and controller presets — 0.3.0 Preview

This update adds local game-guide overlays, charging-aware battery history, controller presets and a touch-only quick panel. It also adds current app-pair capture and fixes L1/R1 navigation across all ten pages.

An independently written Android companion for the **AYN Thor**, inspired by [Thor-Wayfinder](https://github.com/Thor-Wayfinder/thor-wayfinder) and the Thor community. **Experimental pre-release; the app interface is currently Dutch.**

## What changed

- **Offline game guides:** import your own PDF, UTF-8 text or Markdown per app. Read on the bottom display, including an accessibility overlay above dual-screen games. Saved reading position, PDF page navigation and zoom.
- **Battery and app-time statistics:** local screen-off drain history, a duration-weighted average for eligible sessions of at least three hours, and approximate foreground app time. Charging and reboot checks prevent misleading sessions from entering the average.
- **Touch-only quick panel:** keep controller focus with the game, show/hide individual cards, and access recent apps, favorites, saved pairs and notes.
- **Black bottom-screen curtain:** non-focusable black overlay with double-tap or three-finger restoration. **It does not power off the physical display or put the device to sleep.**
- **Controller presets and profile copying:** Xbox/default, Nintendo button swaps, PC menu and WASD presets. Saving a preset does not activate remapping.
- **Five-second stick rest check:** offers a bounded dead-zone recommendation that you can save globally or per app.
- **Save the current app pair:** capture two visible apps from the quick panel through Shizuku without closing them.
- **Navigation fix:** L1/R1 now cycle correctly through all ten pages.

## Included features

Launch apps on either display; favorites and app profiles; app pairs; local notes and browser guide links; validated JSON settings backup/import; optional Select shortcuts; controller diagnostics and keyboard; Shizuku live task movement/swap; ARM64 evdev/uinput button mapping, D-pad keyboard mapping, stick settings and per-app controller profiles; emergency stop and session watchdog; firmware-dependent performance, fan, CPU-cap and joystick RGB controls with recovery snapshots.

## Install or update

Download **Thorhaven-0.3.0-preview.apk**, transfer it to the Thor and open it in Android Files. Requires Android 11 or later. Check top/bottom assignments under **Instellen → Schermen**.

The APK uses the same signing certificate as previews 0.1 and 0.2. **Install over the existing version to keep your data; do not uninstall first.** Accessibility enables overlays, shortcuts and local measurements. Shizuku enables live task operations; native input and firmware controls require supported root access. See the attached English guide for setup and shortcuts.

## Validation and known limits

**93 automated checks passed** (83 Android/integration checks and 10 native self-tests) on an Android 15 ARM64 emulator with two displays. Real accessibility overlays and Linux uinput events were exercised. An update from the signed 0.2 APK preserved a note and profile; document-picker import was also verified manually.

**Not yet tested on a physical AYN Thor.** Fan/CPU/RGB validation uses an in-memory firmware fixture. Battery arithmetic is tested with controlled inputs; screen-off does not prove deep sleep. Live task movement can reload apps. A virtual controller may appear as an additional player. No claim is made that every firmware, emulator or Wayfinder feature is supported.

Macros/turbo, mouse/trackpad, gyro, analog-trigger reassignment, independent display brightness, recording and physical panel power-off are not implemented. Guide files/bookmarks and measurements are excluded from the settings backup. No Internet permission, ads or trackers.

## Release files

- Signed installable APK.
- Complete source archive, including Gradle wrapper, ARM64 input helper, native sources, tests and licenses. Signing keys and local build caches are excluded.
- English installation and feature guide, release notes and example offline guide.
- Test results, verification metadata and SHA-256 checksums.
- Screenshot of the offline-guide page.

Original application code is **MIT licensed**. Third-party notices are included. Wayfinder is an inspiration reference; no Wayfinder source code or artwork is distributed.

Community references: [battery monitoring discussion](https://www.reddit.com/r/AynThor/comments/1sbhwkr/battery_monitoring_apps/), [SleepManager session statistics](https://www.reddit.com/r/AynThor/comments/1wmjit9/sleepmanager_update_thor_closedlid_protection/), and [Wayfinder usability discussion](https://www.reddit.com/r/AynThor/comments/1wsiwyo/wayfinder_is_a_necessity/).
