# Screen Lab, input experiments & better guide tools — 0.6 Preview

Thorhaven 0.6 adds optional screen capture and input experiments, improves everyday document management and provides local troubleshooting exports. Existing dual-screen launching, guides, profiles, backups and controller features remain available.

## Screen Lab

- Live preview of Android's main/default display on the assigned lower screen, subject to Android display-launch support.
- Adjustable preview crop, large-preview mode and cropped PNG screenshots.
- Short silent H.264/MP4 recordings of the full main screen. Recording does not use the preview crop or capture audio.
- Fresh Android capture consent for every session, a foreground notification with Stop when permitted, and Stop/Close controls in the lab.
- Bounded preview (5 fps, longest side 960 px) and recording (3 minutes/96 MB, longest side 1280 px). Private retention of six screenshots and three recordings, with individual SAF export/delete.

## Input Lab

- Up to six named macros, each with 1–20 sequential key steps and a ten-second declared hold/delay budget.
- Firmware-dependent bounded holds, at most 1.5 seconds. Unsupported duration input produces an error.
- Opt-in finite turbo bursts of 2–12 taps, with a 100–500 ms gap.
- An experimental absolute touch/mouse pad targeting the assigned top display, with click, sensitivity and bounded swipe-scroll controls.
- Stop and replacement cancellation for queued work. An already running system action completes before cancellation takes effect. Closing controls stops the sequence.

These input tools require compatible root Shizuku or AYN PServer. They do not provide simultaneous key chords, analog touch sticks, indefinite holds, background turbo or gyro mapping. Acceptance by games and emulators varies.

## Better guides and local exports

- Search/filter the document library, rename guides and export original files without changing their contents.
- Per-document text size (12–30 sp), PDF zoom and individual bookmark/marker rename/delete; bookmark page editing.
- Previous/play/pause/next media controls for the active media session.
- Diagnostic JSON with actual app/Android/display/access status, excluding personal document contents and secrets.
- Completed play-session CSV with UTC start times and recorded battery readings.

## Dual-screen correctness

Display dimensions are measured in each screen's own context. This avoids newer Android compatibility bounds from substituting the lower screen's dimensions for the main screen, which could immediately stop recording or misplace pointer targets. The same measurement is used by overlays, the display picker and diagnostic reports. Android 11/12 accessibility handling also avoids a newer-only display-event method.

## Updates and privacy

Install the signed APK over an existing Thorhaven installation to preserve data. Version code 6 uses the same signing certificate as all prior previews. Input Lab settings, reader size/zoom and edited guide metadata are included in validated backups. Active input/capture sessions are not restored; capture files and temporary export grants are excluded from complete settings backups.

No Internet permission, ads, trackers, microphone permission or new third-party dependency was added. Screen capture uses Android's foreground-service and capture-consent flow; notification permission is optional. Captures may contain visible personal information, and diagnostic files identify firmware/display configuration. Review exports before sharing.

## Validation and limits

**242 automated checks passed**, including 12 actual Android-consent/PNG/MP4 capture checks and a verified signed 0.5 → 0.6 update. The attached test report gives the exact scope and results. Validation runs on a dedicated Android 15 ARM64 emulator with two displays, root Shizuku and an input fixture. Physical AYN Thor performance, encoder support, injected-input acceptance and firmware behavior still require device testing. This release remains a **pre-release**, and the new labs are explicitly experimental.
