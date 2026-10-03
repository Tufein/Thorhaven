# Thorhaven

Native Android companion for the AYN Thor, written independently of Wayfinder. Preview 0.4.0. Dutch and English UI.

Download the signed APK from [GitHub Releases](https://github.com/Tufein/Thorhaven/releases). This is an experimental pre-release and has not been tested on a physical AYN Thor. MIT-licensed original application code.

Features: public display discovery and assignment; launch apps on chosen displays; app profiles for system music volume and brightness; favorites; app pairs; local notes and browser guides; JSON backup/validated import; AccessibilityService quick panel and optional Select shortcuts; controller diagnostics; an InputMethodService keyboard; optional Shizuku UserService for live root-task movement and swapping.

Added in 0.2: native evdev/uinput button remapping, D-pad-to-keyboard mapping, stick inversion/swap/dead zones/response curves, automatic per-app controller profiles, emergency stop and connection watchdog; firmware-probed stock performance/fan modes, CPU frequency caps and joystick RGB with persisted recovery snapshots. Root is needed for these additions, through root Shizuku or the firmware PServer binder. Hardware controls refuse non-Thor devices.

Not implemented: macros/turbo, gyro/mouse, analog-trigger reassignment, screen power, capture or per-ROM detection. Real AYN Thor firmware and controllers have not been tested.

Added in 0.3: private offline PDF/UTF-8/Markdown imports and an accessibility-overlay reader with page bookmarks/zoom; per-session standby drain and foreground app-time measurements; touch-only quick panel, recent apps and configurable cards; a non-focusable black bottom-screen curtain; face-button/keyboard presets, profile copy and a five-second stick rest measurement; saving the two live app tasks as a pair from the quick panel. The curtain does not power off the display or put the device to sleep. No extra Android permissions or dependencies were added.

## Latest changes: custom shortcuts, complete backups and bilingual UI

- Switch between Dutch and English without translating user documents or notes.
- Assign supported Select chords to apps, pairs, pages, overlays and display/system actions; disable individual actions or reset defaults.
- Export/import a bounded complete ZIP backup with guides, bookmarks and usage history, staged validation and crash-recoverable rollback.
- Type symbols, move the cursor with L1/R1 and choose keyboard key sizes; controller navigation follows the real row layout.
- Read an offline guide beside an editable notes column, with explicit saving.

See [the 0.4 change details](docs/Thorhaven-0.4.0-release-notes.md) and [the English guide](docs/Thorhaven-0.4.0-guide.md) for setup, validation and limitations.

## Build

Use JDK 21, Android SDK platform 36 and build tools 36.0.0. Set `ANDROID_HOME` or create `local.properties` with `sdk.dir=...`. Android Gradle Plugin 9.1.0 and the included Gradle 9.3.1 wrapper are used.

```
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. This build is signed with your local Android debug certificate.

For a release with your own signing key, set `THORHAVEN_KEYSTORE` and `THORHAVEN_KEYSTORE_PASSWORD` (alias `thorhaven`), then run `./gradlew :app:assembleRelease`. Without these variables, release uses the local debug certificate. The signing key used for the delivered APK is not included in the source archive; builds signed with another certificate cannot update that installed APK directly.

An optional `THORHAVEN_BUILD_ROOT` environment variable puts build products outside the source checkout. Minimum Android API 30; target API 33. The new input helper supports arm64-v8a, the Thor ABI. Kotlin runtime is added by AGP's built-in Kotlin support; the app itself is Java.

Rebuild the bundled ARM64 helper after native edits:

```
ANDROID_NDK_HOME=/path/to/ndk/27.2.12479018 ./native/build.sh
```

The prebuilt helper is included for app-only builds. The root process checks its SHA-256 before execution. The app has no Internet permission; helper communication uses a random abstract Unix socket, peer UID checks and a per-session token. Loss of heartbeat or socket closes releases the grabbed input device. Holding raw Select + Start for three seconds stops capture. The virtual pad may appear as a second player because the original device remains enumerated by Android; select Thorhaven Controller in the emulator. No firmware modules or stock mapping services are disabled.

## Architecture

- `Language` / `english.tsv`: local UI catalog; guide contents and note text use untranslated views.
- `Shortcuts`: bounded and validated Select-chord bindings; app/pair/page targets are checked before execution. Native emergency stop is independent.
- `CompleteBackup`: bounded ZIP extraction, content validation, private staging, recoverable file/preference transactions and startup recovery. In-flight measurements and hardware recovery snapshots are excluded from transfer.
- `KeyboardSettings` / `ThorKeyboard`: symbol rows, paired cursor events, key sizing and row-aware controller navigation.

- `OfflineGuides`, `GuidePane`, `GuideActivity`, `GuidePages`: validated atomic local import, bounded native PDF rendering, independent reader/overlay lifecycle and private bookmarks. No downloaded HTML, scripts or network permission.
- `PlayStats`: dynamically registered screen/battery events while accessibility is enabled; elapsed-time and boot-identity checks; charge-aware sleep history with duration-weighted averages from eligible sessions ≥ 3 hours. Screen-off is not a measurement of deep sleep. No wake lock, alarm, reboot receiver or radio control.
- `ScreenCover`: black accessibility overlay on the assigned bottom display, touch-to-restore, no controller focus and no panel-power commands.
- `DriftCheck`: observes stick rest peaks for five seconds and offers a bounded dead-zone recommendation. A moving stick is rejected; this is not a hardware diagnosis.
- `PanelActions`: snapshots two distinct visible non-home tasks through the typed Shizuku API, then asks for the pair name.
- `Controls` / `PadProfile` / `ControlPages`: asynchronous root control, authenticated native sessions, validated per-app mappings and firmware capability UI.
- `DeviceControl`: fixed key/path allowlists, available-frequency caps, readback, rollback and conditional restore. PServer parcel protocol is adapted with MIT attribution from OdinTools.
- `native/thorpad.cpp`: original Linux evdev/uinput helper; releases outputs before destroying the virtual pad and ungrabbing the source.
- `MainActivity`: responsive ten-page Android Views interface, profile editing and Android document-picker backup/import.
- `Store`: local preferences, public display enumeration, display-targeted launch, app discovery, profile application, notes and validated backups. Import is validated before any preference commit.
- `ThorService`: optional accessibility service with window-state events and key filtering; it does not retrieve view content. Panel is attached to an accessible target display using an accessibility overlay.
- `QuickPanel`: volume/brightness, foreground-app actions, favorites and app pairs.
- `ThorKeyboard`: optional input method with touch and controller navigation.
- `Bridge`: permission-gated Shizuku lifecycle and worker-thread Binder operations.
- `SystemBridge` / `IThorBridge`: narrowly scoped Shizuku user service. Uses fixed executable arguments for `am stack list` and `am display move-stack`; no user shell text is executed. Package names and display arguments are checked, home/recents tasks are excluded, commands are bounded, moves are verified, and the first half of a failed swap is rolled back when possible.

Shizuku API documentation: https://github.com/RikkaApps/Shizuku-API

## Device tests

`SmokeInstrumentation` is a dependency-free Android instrumentation suite. It needs a dedicated emulator/device with two public displays, Settings and Shizuku installed, Shizuku running and Thorhaven authorized. It exercises live task movement, so use a test environment. Install the app and test APK with matching signing certificates.

```
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w nl.thorhaven.app.test/nl.thorhaven.app.SmokeInstrumentation
```

For the extended suite, use a dedicated root-enabled ARM64 emulator and start the test-only `native/test-fixture.cpp` binary as root. It creates a virtual source gamepad and a command FIFO, and observes physical and remapped output. This fixture is not packaged in the application. Shizuku must run as root for native integration tests. The hardware tests use an in-memory runner; fan/CPU/RGB physical behavior remains untested.

The suite exercises stack filtering and argument validation, display enumeration, app discovery, Unicode backup round trips, atomic invalid-import rejection, unknown schema handling, disabled screen assignment, secondary-context panel creation, all ten UI pages, Shizuku connection, live moves, two-app swaps and repeated-move behavior. It restores its preference changes after success. Accessibility service may need toggling after instrumentation force-stops the target process.

See [the English guide](docs/Thorhaven-0.4.0-guide.md) for installation and limitations. Third-party licenses are in `THIRD_PARTY_NOTICES.txt` and bundled in `app/src/main/assets/licenses.txt`.

## 0.3 validation

The extended suite also imports Unicode text and a real generated two-page PDF, rejects invalid/oversized imports without replacing the existing guide, renders bookmarked pages, verifies all navigation wrapping, tests presets and charge/reboot-aware battery arithmetic, and attaches the real black curtain, touch-only panel and PDF reader to the secondary display. PDF page dialogs and controller B closing are exercised with a real bound accessibility service. Tests use UiAutomation with accessibility suppression disabled. Input-fixture writes drain their command echo so closing a pipe cannot discard test events.

Offline guides and measurements are not part of the JSON settings backup; copy the original guide documents separately. Settings backups include the bounded recent app list and panel-card preferences. The optional accessibility service tracks foreground package names and session durations locally. The native input layer and firmware-specific physical behavior remain untested on an actual AYN Thor.

## 0.4 validation

`V4FeatureChecks` covers UI language, unchanged user text, validated custom bindings, actual custom shortcut execution and release consumption, complete guide/settings/usage backup round trips, rejection of unsafe ZIP paths and invalid charging-session eligibility, rollback after a late transaction failure, side-by-side note saving, symbol input, paired cursor events and keyboard row/size bounds. The extended suite remains in `SmokeInstrumentation`; test fixtures are not packaged into the APK.

Complete ZIP backups include guide files and usage history, unlike the legacy settings-only JSON export. Both formats include the new language, shortcut and keyboard preferences. ZIP imports merge matching guide/settings entries and replace usage history. Limits: 64 guides, 128 MB guide data and 1 MB metadata. No Internet permission or new third-party dependency was added.
