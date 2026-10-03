# Developer guide

Build instructions, implementation details and test setup for Thorhaven. For installation and everyday use, start with [the README](../README.md).

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

See [the English guide](Thorhaven-0.6.0-guide.md) for installation and limitations. Third-party licenses are in `THIRD_PARTY_NOTICES.txt` and bundled in `app/src/main/assets/licenses.txt`.

## 0.3 validation

The extended suite also imports Unicode text and a real generated two-page PDF, rejects invalid/oversized imports without replacing the existing guide, renders bookmarked pages, verifies all navigation wrapping, tests presets and charge/reboot-aware battery arithmetic, and attaches the real black curtain, touch-only panel and PDF reader to the secondary display. PDF page dialogs and controller B closing are exercised with a real bound accessibility service. Tests use UiAutomation with accessibility suppression disabled. Input-fixture writes drain their command echo so closing a pipe cannot discard test events.

Offline guides and measurements are not part of the JSON settings backup; copy the original guide documents separately. Settings backups include the bounded recent app list and panel-card preferences. The optional accessibility service tracks foreground package names and session durations locally. The native input layer and firmware-specific physical behavior remain untested on an actual AYN Thor.

## 0.4 validation

`V4FeatureChecks` covers UI language, unchanged user text, validated custom bindings, actual custom shortcut execution and release consumption, complete guide/settings/usage backup round trips, rejection of unsafe ZIP paths and invalid charging-session eligibility, rollback after a late transaction failure, side-by-side note saving, symbol input, paired cursor events and keyboard row/size bounds. The extended suite remains in `SmokeInstrumentation`; test fixtures are not packaged into the APK.

Complete ZIP backups include guide files and usage history, unlike the legacy settings-only JSON export. Both formats include the new language, shortcut and keyboard preferences. ZIP imports merge matching guide/settings entries and replace usage history. Limits: 64 guides, 128 MB guide data and 1 MB metadata. No Internet permission or new third-party dependency was added.

## 0.5 architecture and validation

`ExtraFeatures` validates portable tool preferences and document metadata. `SessionStats` uses elapsed time and boot identity, samples a bounded battery history without wake locks and transfers completed sessions only. `TouchControls` attaches a non-focusable lower-display overlay; its fixed key/display command is validated by `DeviceControl.sendKey`. Input is tap-only and privileged.

`HardwareAutomation` serializes apply/restore transitions, coalesces pending foreground changes, preserves manual recovery state and opts in per process session. The existing verified hardware transaction is reused. Its transition tests use an asynchronous fake transport; actual firmware behavior remains untested.

`AutoBackup` and `BackupJob` use Android's persisted daily idle/charging job and a document-tree grant. Every archive is prepared/validated before retention removes previously recorded app-owned archives. Folder grants and retention records are local. A debug-only document provider supports integration tests; neither that provider nor instrumentation is packaged in the release APK.

Run the new feature checks with a matching debug app and test APK, two displays and Android Settings installed:

```
adb shell am instrument -w -e v5 true nl.thorhaven.app.test/nl.thorhaven.app.SmokeInstrumentation
```

This suite exercises Unicode search, PNG decoding, named PDF bookmark bounds, complete multi-document/map backup, named profiles, checklists, panel order, restricted touch commands, hardware state transitions, daily job constraints, seven-archive retention and preservation after a failed backup. Use a dedicated emulator: tests modify temporary preferences and launch apps. The regular instrumentation mode runs the existing 0.1–0.4 regression suite.


## 0.6 architecture and validation

`ControlLab` validates bounded portable input settings, serializes sequences and coalesces pointer work. All privileged input passes through `DeviceControl`'s typed allowlist. Cancellation suppresses queued steps; a dispatched bounded system action must finish. Capability probing checks the connected Android `input` command before duration-based holds.

`ScreenLabActivity` opens on the selected lower display in its own single task. `ScreenLabService` creates one consented MediaProjection virtual display per session, has a foreground notification, and cleans up projection, readers, recorder and frames on stop. Preview frames and H.264 recording are bounded; private captures are exported individually through SAF and never added to settings backups.

`GuideTools` manages search, rename, original-file export, reader metadata and individual bookmarks/markers. Metadata validation is reused by complete backup imports. The pending document identifier stays in a separate, non-portable preference file; destination grants are not stored there. `ExperimentTools` exports an explicit status allowlist and validated sampled-session CSV; it does not dump preferences or personal document contents.

Run the 0.6 feature suite with matching debug/test APKs:

```
adb shell am instrument -w -e v6 true nl.thorhaven.app.test/nl.thorhaven.app.SmokeInstrumentation
```

`ControlLabChecks`, `ScreenLabChecks`, `GuideToolsChecks` and `ExperimentToolsChecks` cover limits, cancellation, validated commands, cropping, private retention, original-byte export, edited metadata backup, local reports and UI construction. The v5 and regular modes retain prior integration checks. Runtime MediaProjection consent and actual PNG/MP4 output are checked separately on the emulator; helper/crop tests alone do not establish capture support on physical Thor firmware.

Display sizing uses `Store.displayMetrics` with a bounded cache of application-owned display/window contexts and `WindowManager.getMaximumWindowMetrics`. Calling `Display.getRealMetrics` from a lower-screen context can apply that context's compatibility bounds on recent Android, even when the Display object names the main display; this caused early recorder shutdown and incorrect pointer bounds during runtime validation.

For real consent/capture QA on the dedicated dual-display fixture, install a matching test APK and run:

```
adb shell am instrument -w -e capture true nl.thorhaven.app.test/nl.thorhaven.app.SmokeInstrumentation
```

`ScreenCaptureRuntimeChecks` accepts actual Android sharing prompts, checks generated source pixels and cropped PNG data, requires the recorder to remain active until lab close, and verifies MP4 dimensions/duration and absence of audio. Use isolated capture storage with a free screenshot/recording slot; existing captures are preserved.

## 0.7 RGB architecture and validation

`RgbSettings` stores a validated, versioned `rgbStudio` preference. It contains a global two-sided profile, up to 20 named presets, up to 64 app assignments and the bounded session options. It rejects unknown fields, invalid colors/ranges and references to missing presets before committing. Both backup formats include this portable configuration; they do not start a service after import.

`RgbEngine` provides original deterministic calculations for solid, breathing, rainbow, cycle, pulse, battery and charging effects. Each side has its own enabled flag, colors, brightness and period. `RgbStudio.Preview` uses this engine without touching hardware. It previews the global/editor profile, not the active foreground app override.

`RgbHardware` uses only the fixed SN3112 left/right enable and brightness nodes. Direct access requires Thor identity and all four writable nodes. The typed backend can fall back to the existing AYN PServer or root-Shizuku transport. Each frame controls both zones on both sides with bounded integer channels; brightness is represented by scaled RGB values. Hardware writes and probes run off the UI thread. Command success is not a physical LED readback.

`RgbSession` captures validated actual stock AYN settings before the first write into the separate `rgb-recovery` preferences. It never modifies the stock color, enabled or brightness settings. On stop it prefers current valid stock settings when readable, otherwise the captured baseline. It cannot capture or restart another app's animation. A failed restore leaves the recovery record, preventing a new session until recovery succeeds. Recovery records are local to this device and excluded from portable backups and ordinary diagnostic exports.

`RgbService` runs one user-started foreground session on a serialized worker, with a notification Stop & restore action. It is `START_NOT_STICKY` and has no boot-start route. Direct updates are 2/5/10 Hz; the root/AYN bridge is capped at 2 Hz. Unchanged frames are deduplicated, screen-off pause sends black once and the timer uses elapsed time including pauses. Optional accessibility foreground events select saved app presets with a global fallback. Screen-brightness following uses Android's main system value, not per-display brightness. The RGB pipeline adds no wake lock, screen sampler, audio sampler or third-party dependency. A generation counter and explicit stop command cancel a queued foreground-service start while still allowing Android's foreground deadline to be fulfilled.

### Protocol research and independent implementation

Bifrost's pinned [LedController](https://github.com/Pollux-MoonBench/Bifrost/blob/1baddf1644ff0d7edd1bd0f4ba02f7eb6c8e3cfa/app/src/main/java/com/moonbench/bifrost/tools/LedController.kt) shows SN3112 left/right brightness nodes with zone-one/two RGB payloads and PServer transaction code 0. Its code comments explain that dimming requires RGB scaling because the fourth wire field is ignored. Its project is [GPL-3.0](https://github.com/Pollux-MoonBench/Bifrost/blob/1baddf1644ff0d7edd1bd0f4ba02f7eb6c8e3cfa/LICENSE).

Wayfinder's pinned [StickLights](https://github.com/Thor-Wayfinder/thor-wayfinder/blob/305d3ad824e200c936fc270d044d9db3ecc800ee/app/src/main/java/app/wayfinder/lights/StickLights.kt) identifies the enable nodes and stock keys `joystick_light_enabled`, `joystick_led_light_picker_color` and `led_light_brightness_percent`; it also reports stock writes after wake. Wayfinder is [PolyForm Strict 1.0.0](https://github.com/Thor-Wayfinder/thor-wayfinder/blob/305d3ad824e200c936fc270d044d9db3ecc800ee/LICENSE).

PULSE's pinned [RgbController](https://github.com/keiretrogaming/pulse/blob/0d2893e67cee0497e3fe624237679d104dd9c472/app/src/main/java/com/kei/pulse/data/RgbController.kt) corroborates the paired hex-color/enable settings and 0–1 master brightness on other vendor devices, while explicitly describing Thor verification as outstanding. [RootExec](https://github.com/keiretrogaming/pulse/blob/0d2893e67cee0497e3fe624237679d104dd9c472/app/src/main/java/com/kei/pulse/root/RootExec.kt) demonstrates synchronous byte-array command output through PServer. Its project is [GPL-2.0](https://github.com/keiretrogaming/pulse/blob/0d2893e67cee0497e3fe624237679d104dd9c472/LICENSE).

These sources informed interface facts only. Thorhaven's schema, engine, UI, lifecycle and validation are independently implemented; no reference source code, animation formula, color calibration or artwork is included. Treat direct-node/PServer behavior as firmware-dependent until tested on a physical Thor.

Run the RGB instrumentation entry point with `adb shell am instrument -w -e v7 true nl.thorhaven.app.test/nl.thorhaven.app.SmokeInstrumentation`. `RgbStudioChecks` checks strict schemas, bounds, preset/app references, backup round trips, deterministic effects, validated recorded transport, recovery and rendered Dutch/English UI with unchanged user names. It also starts the actual Android foreground service with an injected recording backend and checks foreground/global selection, stop-only commands, in-flight cancellation, write-error restoration and cancellation before service creation. Transport recording and injected backends do not prove physical LED output.

The final 0.7 validation passed 317 checks: 102 existing Android, 43 previous 0.5 features, 75 previous 0.6 features, 75 RGB, 12 real capture-runtime and 10 current native-helper self-tests. Existing Android and capture-runtime checks ran against the final signed release. The release build and `lintRelease` passed. A released signed 0.6 → final signed 0.7 install retained the app UID, English selection, an app volume setting, a displayed Unicode note and exact original guide bytes; both APKs actually rendered the seeded note and guide. Forward-install checks do not establish every reader-metadata field; complete ZIP metadata restoration is covered separately in the document suite. Full results are in [the 0.7 report](Thorhaven-0.7.0-test-results.txt).

Physical Thor verification remains outstanding for colors, both zones, direct-node permissions, PServer firmware behavior, stock-effect conflicts and actual restoration. Source hashes, published-asset checksums and the public APK download are recorded separately in the release's verification JSON after publication.


## 0.8 RGB tools

`RgbPresetTools` edits and duplicates saved styles against a current settings snapshot; the global profile and existing preset IDs remain intact. `RgbSettings.update` serializes read/mutate/validate/commit, preventing an editor from saving an obsolete dialog snapshot over newer settings. App assignment search uses raw labels/packages, and user names remain outside translation replacement.

`RgbPresetBundle` implements a styles-only format (`thorhaven-rgb-presets`, schema 1). Strict validation reuses the existing profile/name rules. Export excludes identifiers, app mappings and session data. Import validates all styles and capacity, assigns new unique IDs and commits the entire addition against current settings. SAF I/O runs off the UI thread; input bytes and UTF-8 decoding are bounded and checked.

The frame transport now supports distinct zone 1/2 values for each stick. Its four-argument constructor and legacy four-field JSON retain mirrored output; complete six-field frames add `left2` and `right2`. Mixed/unknown schemas fail before any writes. Fixed-node writes still use no-create operations. Normal effects remain two-sided profiles with mirrored physical zones.

`RgbDiagnostics` offers fixed, low-intensity patterns through `RgbService` and `RgbSession`. One-zone and sequential-zone tests last eight seconds; RGB channel tests last 24. They share the foreground-service deadline, serialized output, stock baseline and journal. Screen-off or timeout ends a diagnostic and restores stock output. No background resume, app-driven pattern changes, raw command input or arbitrary hardware path is introduced. The Shizuku user-service version is advanced so a previously cached process cannot retain an older frame parser.

Run `adb shell am instrument -w -e v8 true nl.thorhaven.app.test/nl.thorhaven.app.SmokeInstrumentation` for `RgbToolsChecks`. The old `v7` RGB mode remains available. All hardware tests use recording fixtures; actual LED output needs physical Thor validation. See [the 0.8 verification report](Thorhaven-0.8.0-test-results.txt) for final counts and signed-update evidence.
