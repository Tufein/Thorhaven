# Thorhaven 1.0 RC1 — game journals, sketches and disc tools

This milestone candidate adds practical tools for the second screen: a personal game library, progress journals, a sketchpad, folder reports and multi-disc playlists. It also makes screen setup clearer and checks app launches, backups and capture cancellation more carefully.

**Build:** `1.0.0-rc1` · Android 11 or newer · Dutch and English · GitHub pre-release.

## New everyday tools

- **Game library:** create game cards with Backlog, Playing now, Completed or Paused status, favorites and searchable labels. Keep a separate timestamped journal and checklist for each game, even when several games use one emulator. Link an installed app and an imported guide. Opening a card shows its details; **Open app** launches the selected app, where you choose your ROM.
- **Sketchpad:** draw named maps or puzzle notes, choose colors and line widths, toggle a grid, and use Undo/Redo. Completed strokes save locally. Export a 1,200 × 800 PNG with the same proportions as the canvas while keeping the original drawing editable in Thorhaven.
- **Selected-folder storage reports:** choose a folder in Android Files and inspect file counts, known bytes, unknown sizes, extension totals and the largest files. Export a timestamped CSV. The scan is cancellable and bounded; denied folders and scan limits produce a partial report rather than a misleading device-wide total.
- **M3U assistant:** select distinct CUE/CHD/ISO/PBP/GDI disc files from one folder, arrange their order and preview the exact playlist text. Grant separate write access to create a uniquely named new M3U. Check existing same-folder playlists for missing references. Original disc files are not changed; emulator/core support varies.
- **Guided screen setup:** inspect available displays and optional access, run a fifteen-second test window, assign Top and Bottom, swap roles or use a single screen. Android's primary display is identified separately from its physical position.
- **Optional launcher shortcuts:** request shortcuts for an app, saved app pair or offline guide. Each shortcut checks current screen roles and current targets when opened. Your Android launcher controls support and confirmation.

## Reliability changes

- Unavailable app or display launches are rejected before applying volume or brightness profiles. Saved pairs check both targets first; delayed launches are cancelled when their dashboard closes or a newer pair replaces the request. **Stop Thorhaven actions** also cancels pending pairs from every Thorhaven window.
- Settings document reads/writes and complete ZIP restore run away from the UI thread. Backup validation rejects malformed types, duplicate JSON keys, unsafe entries and inconsistent or truncated ZIP directories before applying data. Game cards and editable sketches are included in portable settings; folder permissions, pending exports and hardware recovery remain local to the device.
- App-note drafts survive Activity recreation. A destroyed or expired dashboard cannot open another note editor, and an old dialog cannot clear the new editor's draft. Interrupted sketch strokes are discarded, and pending PNG exports can be cancelled visibly. Late file-picker results cannot consume a newer export request or send its drawing to an old destination.
- Emergency stop cancels outstanding Screen Lab consent requests and stale queued capture starts, so an old permission result cannot restart capture after stopping. A new session requires a fresh request.
- Switching a writable folder preserves the persisted read access still needed by the selected scan folder, and switching a scan folder preserves access still needed by a writable folder.
- Display practice keeps its original target through recreation. If Android moves the test onto another display after removal, the test closes rather than identifying the wrong screen.
- Closing or stopping the RGB diagnostic chooser immediately cancels a test started there and requests stock restoration. Controls belonging to the closed chooser cannot start lighting again.

## Installing and testing

Install the signed candidate APK over your existing Thorhaven installation to keep its data; do not uninstall first. A released signed 0.8 → final signed RC1 update retained the app UID, signing identity and seeded data in Android verification. The report records the data actually checked. Make a complete backup before testing a candidate.

The game library is bounded to 300 KB and the editable sketch book to 250 KB. Settings JSON and complete ZIP metadata each allow up to 2 MB; complete ZIPs also allow 64 guides and 128 MB of guide data. Import replaces an included game library or sketch book as a whole. ROMs, captures and folder grants are not transferred. Android Files controls the chosen export provider, which can offer cloud storage even though Thorhaven has no Internet permission.

Release verification passed **783 automated check executions**, including 185 new-feature checks and 17 actual capture checks on each of Android 11 and Android 15. The signed 0.8 → RC1 update preserves existing data. Release build and lint succeed (0 errors; 95 lint warnings).

See the attached [verification report](Thorhaven-1.0.0-test-results.txt) for actual test environments, results and limits. Automated Android checks use emulators and controlled document/hardware fixtures. They do not prove physical AYN Thor compatibility. RGB output, privileged input, vendor firmware, launcher pin confirmation and emulator disc switching still need device testing.

This is a pre-release candidate for community feedback. Please report your Thor model, Android/firmware version, the action you tried and the observed result. The [user guide](Thorhaven-1.0.0-guide.md) explains the features, and the [device checklist](Thorhaven-1.0.0-device-checklist.md) lists the remaining physical checks.
