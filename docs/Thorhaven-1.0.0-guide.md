# Thorhaven 1.0 RC1 user guide

This candidate adds useful offline tools while keeping your existing Thorhaven data. Install the signed APK over the old version; do not uninstall first. For the remaining real-device checks, see [the Thor checklist](Thorhaven-1.0.0-device-checklist.md).

## Games: your own library and progress

Open **Games → Add game**. Enter a title, optionally choose an installed app, select Backlog/Playing now/Completed/Paused, add comma-separated labels and link an imported guide. The library searches titles, app identifiers and labels; Playing now and Favorites narrow the list.

Open a game card to see its details. From there, edit it, choose **Open app** to start its selected Android app on the configured top screen, open its offline guide, or add journal entries and tasks. Journal entries have timestamps; tasks can be checked off, edited or deleted. These records are separate for each game even when games use the same emulator. If you select an emulator, choose your ROM inside that emulator. Removing a game card also removes that card's journal and tasks after confirmation; it keeps the original guide.

The library supports up to 200 games, 100 journal entries and 100 tasks per game, with at most 2,000 journal entries and 2,000 tasks across the library. Each game can have eight labels. The combined data must fit within 300 KB of UTF-8 JSON. User titles and notes remain unchanged when switching languages. Save dialog edits before closing them.

## Sketchpad: maps and puzzle notes

Open **Sketchpad** and create a named page. Draw with touch or a stylus, choose a color and line width, toggle the grid, and use Undo/Redo. The drawing tools are below the canvas; scroll beside the canvas to reach them. Completed strokes save immediately. An unfinished stroke is cancelled when touch is cancelled, the active pointer disappears or the canvas closes.

Pages use normalized coordinates on a 3:2 canvas. Use **Export PNG** and choose a destination in Files. The 1,200 × 800 picture keeps the canvas proportions and is a flattened image; the editable drawing remains in Thorhaven. Export uses the saved page as it was when you opened the file picker. If a file-picker interruption leaves an export waiting, use **Cancel pending PNG export** and start a fresh export. A late result from the cancelled request cannot export a newer drawing.

The sketch book supports up to 20 pages, 500 strokes and 6,000 points across all pages, including strokes available for Redo. Each stroke has at most 512 points. The combined data must fit within 250 KB of UTF-8 JSON. The app explains when a limit is reached; Undo keeps a stroke for Redo, while clearing or deleting a page frees its stored points.

## Storage: inspect a selected folder

Open **Storage**, choose a folder through Android's picker and start a scan. The scan reads metadata only and reports file counts, known bytes, unknown sizes, extension totals, largest files, access errors and its timestamp. It is bounded to 10,000 entries, a depth of 12 and about two minutes, with a separate metadata limit. Reaching a limit or an inaccessible folder makes the report partial. Cancel keeps the previous completed report. A provider may finish its current request before accepting cancellation.

This is a folder inventory, not Android's full-device storage total. Android 11+ blocks some roots, Downloads trees and Android/data or Android/obb. Some providers do not report sizes. Select the actual ROM subfolder on internal storage or the microSD; reselect it if files move or access is revoked.

Export CSV to save the metadata report. Filenames and folder information can be personal; check the report before sharing. No existing ROM is deleted, moved or converted.

## Multi-disc M3U playlists

After a scan, select supported CUE/CHD/ISO/PBP/GDI files from one folder, in disc order. Adjust the order and preview the exact M3U text. For CD games, select a complete CUE definition rather than each BIN track. Your emulator must support the selected format and M3U disc swapping.

Choose **Create new M3U in this folder** and grant write access to the exact folder containing those discs. Thorhaven rechecks their names and identifiers, then creates a uniquely named new playlist. It refuses mixed folders, duplicate entries, guessed absolute paths, control characters and missing/changed files. Original discs remain unchanged. If the provider fails while writing, it may leave a new incomplete playlist; the app reports that error.

Existing M3U checking covers plain same-folder filenames, comments and missing references, up to 32 discs and 64 KB. Subfolder/absolute-path playlists and content URIs are deliberately outside this checker. A successful reference check does not verify disc contents or every emulator's M3U implementation.

## Setup and launcher shortcuts

Open **Setup → Check screens and access**. A display test opens Thorhaven's own test window on the selected display and closes within 15 seconds. If that display disappears or Android moves the test elsewhere, it closes rather than showing the wrong screen. Assign two different Top/Bottom roles, swap them, or disable Bottom when using one screen. Android display 0 is the primary capture source; it does not automatically mean the physical top screen.

The capability list shows current displays and optional access. Normal local tools need no root. The accessibility service adds controller chords, overlays and app detection. Modify-system-settings access enables brightness profiles. Shizuku enables eligible live app moves; supported root/firmware access is separate.

With a supporting Android launcher, choose an app, saved pair or offline guide and request a pinned shortcut. The launcher handles pin confirmation; an accepted request does not prove the shortcut was pinned. Each execution resolves current screen roles and current targets; deleting a target or disabling an app/pair screen produces a useful message. An offline guide uses Bottom when available and otherwise falls back to Top. Pinning is optional and is not available in every launcher.

**Lock device now** uses ordinary Android whole-device locking when accessibility reports it available. It does not independently power off a panel. **Stop Thorhaven actions** stops current overlays, capture, RGB and controller tools, cancels delayed pairs from every Thorhaven window, disables automatic app profiles and controller chords, and keeps recovery information. Re-enable the controls you want manually afterward. In-flight privileged commands may need to finish first.

## Backups and updates

Game cards, journals, tasks and editable sketches join the existing portable settings. A complete ZIP also includes original guides and their reading metadata. Folder grants, pending exports, storage reports, live sessions and hardware recovery stay device-local. Captures and ROMs are not included.

Settings JSON is limited to 2 MB; complete ZIP metadata is also limited to 2 MB, with up to 64 guides and 128 MB of guide data. Stricter validation rejects malformed/fractional fields, duplicate JSON keys and inconsistent/truncated ZIP directories before restore. Imports leave unrelated setting keys intact and replace the included values. If the backup includes the game library or sketch book, it replaces that whole collection; it does not combine individual cards or pages. Make a complete backup before testing a candidate.


## Existing companion and advanced tools

Thorhaven is a local companion for the AYN Thor. Launch apps on either screen, keep documents and notes below your game, and use the optional control tools.

## Install or update

Download `Thorhaven-1.0.0-rc1.apk` from the GitHub pre-release and open it in Android Files. Allow Files to install apps if Android asks. Install over your previous Thorhaven version to keep your data; do not uninstall first. Versions 0.1 through 1.0 RC1 use the same signing certificate.

Open Settings, choose Dutch or English, and check the top and bottom display assignments. Basic app launching and document tools do not need root.

## RGB Studio: a quick start

RGB Studio controls the two joystick lights independently. You can edit and preview styles before starting a hardware session.

1. Stop other lighting apps and AYN animated lighting so they do not overwrite the same lights.
2. Open **RGB Studio** from the navigation bar or quick panel.
3. Select **Check RGB access**. Supported stock firmware may allow direct access without root. Otherwise Thorhaven can use compatible AYN PServer or root-mode Shizuku. Missing lighting controls or missing AYN restore settings are reported as unsupported.
4. Choose a quick style, such as **Thorhaven**, **Ocean** or **Ember**, and view the two-ring preview.
5. Select **Start RGB**. Android may offer notification permission. The foreground service keeps the session running while you use another app. Allow notifications if you want its visible **Stop & restore** action.
6. To finish, select **Stop & restore AYN** in RGB Studio, use the quick-panel stop button or select **Stop & restore** in the notification.

Starting RGB never changes the stock AYN color, on/off or brightness setting values. It temporarily drives the hardware. Stopping writes the lights represented by the actual AYN settings, using current stock settings when readable and the saved start-of-session baseline otherwise. It does not restore another lighting app's previous animation.

### Colors, effects and brightness

Select **Edit colors and effects**. Each stick has its own on/off switch, main color, second color, brightness and effect. Enter colors as `#RRGGBB`, for example `#FF8000` for orange. The swatches are shortcuts; you can type any valid six-digit color.

Brightness ranges from 0 to 100%. Effect duration ranges from 2 to 20 seconds; the slider displays the actual number of seconds. A larger duration produces a slower effect. Select **Save** to update the global profile. If a session is already active, saved changes apply on its next update when it is using that global profile. Editor changes are only a preview until saved.

Use **Copy left to right** to give both sticks the left stick's current global style, or **Swap left and right** to exchange their global styles. These actions save the global profile and apply on the next update of an active global session.

| Effect | What it does |
| --- | --- |
| **Solid color** | Keeps the main color steady. |
| **Breathing** | Gradually dims and brightens the main color. |
| **Rainbow** | Moves through the color spectrum. |
| **Color cycle** | Blends back and forth between the main and second colors. |
| **Soft pulse** | A repeated rise and fall followed by a short dark interval. |
| **Battery level** | Uses red through yellow to green according to the measured battery level. It is black when no valid reading is available. |
| **Charging indicator** | Breathes while charging and blends the main/second colors according to battery level. It is black when unplugged or without a valid reading. |

The preview uses the same calculation as hardware output. The main preview shows the global profile. **Current profile selection** shows the app/profile routing when the page opened; use **Refresh selection** to reload it. Saved-preset and app editors preview the style you are editing. Previews are not physical color calibration. Normal profiles give both physical zones of each stick the same output; the new zone tests address them separately for short diagnostics. Screen-color sampling and audio-reactive lighting are not included.

### Quick styles and personal presets

The nine built-in quick styles are **Thorhaven, Aurora, Ocean, Ember, Retro, Neon, Battery, Charging** and **Off**. Selecting one replaces the global profile. During an active global session it applies on the next update; selecting a style does not start a stopped session. **Off** turns both lights off while that profile is running; **Stop & restore AYN** ends the session and returns the lights to stock settings.

Use **Save global profile as preset** to store your style with a name. Up to 20 personal presets are supported. Select a preset's name to open its details. Choose **Use globally** or **Use as global profile** to copy that style to the global profile. **Replace with global profile** replaces the saved style after confirmation.

**Edit** opens the left/right controls for that saved style directly. Saving keeps the global profile intact. **Duplicate** makes a separately editable copy with a new name. Rename and delete remain available; deletion also removes assignments referring to the preset. User-entered names are kept exactly as written after trimming leading/trailing spaces.

A style may be shared by multiple apps. Editing it changes the lighting for every app assigned to it. The editor shows its assignment count. Duplicate the style and reassign one app if you want a separate version. Saving or assigning styles never starts hardware control; a style already used by an active session changes on its next update.

### A different style for each app

Save a personal preset, then select **Assign app to RGB preset** under **RGB per app**. Search by Android app name or package, choose an app and choose a saved preset. Up to 64 assignments are supported. An app row shows its current style. Choose **Edit shared preset** to preview and adjust that assigned preset; shared-preset changes affect all its assigned apps.

Automatic switching needs Thorhaven's accessibility service and an already active RGB session. The foreground Android app selects its assigned preset; unassigned apps use the global profile. Removing an assignment returns that app to the global profile. This detects Android apps, not individual ROMs inside an emulator. Without the accessibility service, the global profile is used.

### Share just your RGB presets

Under the RGB sharing tools, choose **Export presets** and a destination in Android Files. The JSON file contains all your saved preset names and left/right styles. It excludes app names/packages, assignments, the global profile, session options, personal notes and recovery records. Save at least one preset before exporting.

Choose **Import presets**, select a Thorhaven RGB file and confirm **Add**. The file must contain 1–20 styles and be at most 64 KB. Import adds new preset IDs and keeps existing presets and assignments, even when names match. There must be enough room within the 20-preset limit for the entire file. Invalid files or capacity failures leave your settings intact. Imported styles are not assigned automatically and do not start an RGB session.

For a full transfer including app assignments and other settings, use the regular settings or complete ZIP backup instead.

### Test the four lighting zones (experimental)

Stop any active RGB session, then open **RGB diagnostics and zone test**. Stop other RGB controllers and AYN animations first. You can choose:

| Test | What it sends |
| --- | --- |
| **One zone** | A modest white light to left zone 1, left zone 2, right zone 1 or right zone 2 for 8 seconds. The other zones stay black. |
| **All four zones** | Each zone in turn for 2 seconds; 8 seconds total. |
| **RGB channels** | Red, green and blue on each zone, 2 seconds each; 24 seconds total. |

The test starts a foreground session with the same baseline/recovery safeguards as normal RGB. Its status identifies the current step. It ends automatically and restores current valid stock AYN lighting settings, falling back to the captured baseline. **Stop & restore** can end it sooner. Turning the screens off ends a diagnostic rather than resuming it later. App switching and normal effect settings do not change the test pattern.

Tests use numeric zone 1/2 addresses from the known Thor interface. Closing or stopping the chooser immediately cancels a test started there and requests stock restoration; controls from a closed chooser cannot restart it. Their physical positions and acceptance still need checking on your firmware. A successful command does not confirm a visible color. Check both sticks yourself and note which physical part responds to each step. Zone tests never save per-zone styles or change your preset settings. If restoration fails, use **Restore after interruption** before starting another session.

### Session behavior

| Option | Behavior |
| --- | --- |
| **Update speed** | Direct hardware access uses your choice of 2, 5 or 10 updates per second, with 5 selected by default. The root or AYN bridge is limited to 2 updates per second. Identical output is not repeatedly written. |
| **Dim RGB with system brightness** | Follows Android's system brightness value, rather than each display separately. Disabled by default. |
| **Extra dimming below 20% battery** | Reduces intensity when the measured level is below 20% and the device is not charging. Enabled by default. |
| **Pause when screens are off** | Writes black once while paused and avoids ongoing light writes. Resumes within the active session when the device becomes interactive. Enabled by default. |
| **Stop automatically after** | No timer, or 5, 15, 30 or 60 minutes. Time is measured from the session start and includes screen-off pauses. |

Options changed during a session take effect on the next update. Effects do not start on boot, after process restart or after a backup import. Normal app navigation does not stop the foreground RGB service; use a Stop action when finished. No wake lock or screen/audio capture is needed for RGB Studio.

### Restore after an interruption

Before its first hardware write, Thorhaven saves the actual AYN restore settings in a private recovery record. A partial write failure stops further animation and attempts restoration. An unsuccessful restore leaves a visible pending status.

If the app was interrupted before it could restore the lights, open RGB Studio and choose **Restore after interruption**. Thorhaven checks current valid AYN settings first and otherwise uses its saved baseline. It will not start another session while recovery remains pending. If recovery still fails, reconnect the required access and try again. Check the lights and stock settings on the Thor afterward.

Restoration is a command to supported firmware, not proof of physical output. Thorhaven cannot record or recreate an effect that another RGB app was running. Stock AYN effects may immediately write their own colors again. The older manual RGB controls under System are blocked while RGB Studio is active; use the session's Stop action before using those controls.

### Backups, compatibility and privacy

RGB presets, global behavior and app assignments are included in both JSON settings backups and complete ZIP backups. Imports validate these values and do not start a session. The active session and private hardware recovery record are not portable and are excluded from backups.

RGB Studio has no Internet, microphone or screen-capture access. It reads battery/system-brightness values and, for optional app switching, the package already observed by the accessibility service. Physical brightness and colors depend on the LED hardware. Use one controller at a time and test both sticks and zones on your actual Thor.

### RGB troubleshooting

- **The preview works but the lights do not:** select Check RGB access, verify this is supported Thor firmware and connect root Shizuku or the available AYN service if direct access is unavailable.
- **Colors flicker or revert:** stop Bifrost, Wayfinder, PULSE and stock animated lighting before trying again. These controllers can write the same hardware.
- **Charging mode is dark:** it deliberately stays black while unplugged or when no valid battery reading is available.
- **My app preset does not appear:** enable the accessibility service, save a preset, check the app assignment and start RGB. ROM names inside one emulator do not trigger separate assignments.
- **A new session is refused:** recover any pending AYN lighting state first.
- **The lights look different from the preview:** the preview is a calculation, not LED color calibration. Adjust the colors and brightness on the actual device.

The firmware check reflects known device interfaces: Wayfinder's [pinned lighting source](https://github.com/Thor-Wayfinder/thor-wayfinder/blob/305d3ad824e200c936fc270d044d9db3ecc800ee/app/src/main/java/app/wayfinder/lights/StickLights.kt) reports directly writable Thor light controls and stock writes after wake; Bifrost's [pinned controller](https://github.com/Pollux-MoonBench/Bifrost/blob/1baddf1644ff0d7edd1bd0f4ba02f7eb6c8e3cfa/app/src/main/java/com/moonbench/bifrost/tools/LedController.kt) describes both lighting zones and RGB-based dimming. These are interface references, not a guarantee for every firmware. Thorhaven's RGB implementation is independently written. Reference licenses and technical details are listed in the [developer guide](DEVELOPMENT.md#protocol-research-and-independent-implementation).

## Documents, maps and bookmarks

1. Open **Guides** and choose the Android game or emulator app.
2. Select **Add PDF / text / image**. Each import adds a separate document; existing guides stay available.
3. Choose a document in that app's library to open it. Quick-panel guide actions reopen the most recently selected document.
4. For text or Markdown, enter a word and select **Find next**. Search ignores letter case and wraps to the beginning. Markdown is displayed as text.
5. For a PDF, select **Add bookmark**, name the current page, then use **Bookmarks** to return to it. Choose an entry under **Bookmarks** and select **Delete** to remove it, leaving the document intact.
6. For a PNG or JPEG map, select **Zoom** to cycle between 100%, 150% and 200%. Scroll to move around the image. Long-press the map and name a marker. Choose an entry under **Markers** and select **Delete** to remove that marker.

PDFs and images are limited to 16 MB each. Text and Markdown use UTF-8 and are limited to 2 MB. Images are limited to 64 megapixels and 16,000 pixels per dimension, and are downsampled for viewing. Complete backups include up to 64 documents, 128 MB of document data and 2 MB of metadata.

PDF search is not included; text search works on text and Markdown guides. Bookmarks and map markers are included in complete ZIP backups.

Enable the accessibility service to show a guide above a game on the lower display. Without it, guides open in a separate activity. Settings → Notes beside guide enables the split reader and notes editor; save note edits explicitly.

## Library management and reading settings

The Guides page has a searchable document library. Search by document or app name and filter PDF, text or map/image files. **Document options** lets you rename a guide, export its original bytes through Android's file picker or remove the local copy. Renaming changes the display title, not the underlying file type or owner app.

The reader remembers text size from 12 to 30 sp. PDF zoom can be 100%, 150% or 200%; map zoom remains available. **Bookmarks** and **Markers** let you rename or delete individual entries. PDF bookmark pages can be changed using a 1-based page number. Names and original document text remain exactly as entered, regardless of the interface language. These settings and entries are included in complete ZIP backups.

## Checklists and named game profiles

Under Guides, select an app and find **Game checklist**. Add tasks, tick completed items and remove entries with ×. Up to 200 tasks are supported per app. Tasks belong to the selected Android app, so an emulator can have a shared list with game names in the task text.

**Named game profiles** store that app's saved volume, brightness, display and controller configuration. Save the app's normal settings first, then create a profile with a game name. Up to 30 profiles are supported per app. Select a profile manually when changing games inside an emulator. Thorhaven does not detect ROM names or automatically launch ROMs. Selecting a profile applies volume/brightness and the active remapping configuration; its display choice is used next time the app is launched.

## Arrange your panel and use touch buttons

Open **Settings → Extra tools → Open extra options**.

**Panel card order:** select ↑ beside a card to move it to the front. System volume, brightness, emergency stop and screen actions remain at the top. Card visibility and touch-only panel mode remain available in Settings.

**Touch control tiles:** choose a tap action for each of six tiles, then open the controls. The same controls are available from the quick panel. They appear on the lower display without taking controller focus. The target app must be active on the upper display.

Touch tiles require root Shizuku or compatible AYN PServer firmware. Tap mode sends short gamepad or keyboard presses. Input Lab adds short macros, bounded holds, finite turbo and a pointer pad as explained below. Game/emulator support for injected input varies; configure matching hotkeys in the emulator and check on your Thor. Home and AYN keys are not offered. Close the controls with their Close button.

## Experimental Input Lab

Open **Settings → Extra tools → Open extra options → Input Lab**. Set the hold duration, turbo count and pause, and create up to six named macros. Each macro contains 1–20 sequential key steps with an optional bounded hold and delay. The declared hold-plus-delay budget is at most ten seconds. It cannot send simultaneous key combinations or analog stick values.

Use **Check firmware support** to query the connected bridge. Bounded key holds require the firmware's `input` command to support `--duration`; unsupported firmware returns an error instead of silently substituting taps. A hold is at most 1.5 seconds. Turbo is off by default and requires deliberate opt-in. Each burst has 2–12 taps, separated by 100–500 ms; it never repeats indefinitely.

Open the lower-screen controls from **Experimental workshop → Macros and trackpad** or Input Lab. Choose tap, hold or turbo for the six buttons, or run a saved macro. Dragging the pad moves a target point in absolute coordinates on the assigned top display; tapping clicks at that point. Choose touch or mouse source, adjust sensitivity, tap to click and use the scroll controls for bounded vertical swipes. This is an experimental absolute pointer surface, not a relative desktop trackpad or analog gamepad.

**Stop all input actions** cancels queued macro steps and pointer moves. An already dispatched system action completes first, so stopping a hold may take up to its configured duration. Starting a new macro replaces the previous sequence. Closing the controls or destroying the accessibility service also stops queued input. Root Shizuku or compatible root AYN PServer is required. App, emulator and firmware support must be tested on your own device.

Input Lab configuration is included in settings/complete backups. Running sequences are never resumed after restart.

## Experimental Screen Lab

Open **Settings → Experimental workshop → Screen mirroring and recording**. The lab opens on the assigned bottom display when Android allows it. It captures Android's default/main display; it does not choose an arbitrary secondary display.

1. Select **Start live preview** and accept Android's screen-sharing prompt. Consent is requested for every session. On supported Android versions, optional notification permission gives you a notification with a Stop action; denying it does not block capture.
2. Adjust left, top, right and bottom percentages to choose the visible crop. **Large preview** hides the tools temporarily. The preview is capped at 5 fps, 960 pixels on its longest side and 518,400 pixels in total. It is for viewing and does not forward touches.
3. Select **Screenshot of crop** to save a PNG of the currently available frame and crop.
4. To record, stop the preview and select **Start silent recording**, then accept fresh capture consent. Recording captures the full main screen without audio or crop. It is capped at 3 minutes or 96 MB, 1280 pixels on its longest side and 921,600 pixels in total. A format/aspect change stops recording.
5. Select **Stop**, use the capture notification, or close Screen Lab to end sharing. Saved files appear below the controls. Export them with Android's file picker or delete them individually.

The lab retains the latest six screenshots and three recordings in private app storage. Export important files before creating more. Captures are excluded from complete settings backups. Protected apps may show black content; encoding support and performance vary. Mirroring and recording are separate modes and use additional device resources. Physical Thor behavior is experimental.

## Media controls and exported reports

The workshop and quick panel provide previous track, play/pause and next track for the active media session. The media app determines which actions work; no microphone access is needed.

**Export diagnostics as JSON** writes the actual installed app version, Android/firmware identity, detected/assigned displays, permission/bridge status, battery state, guide count and automatic-backup status. It excludes note and guide content, macro content, tokens, folder URIs and key history. It is a status snapshot, not proof that a firmware or emulator feature works. Review it before sharing.

**Export play sessions as CSV** saves completed sessions, their UTC start time, elapsed seconds and recorded battery samples. Empty sessions have an empty sample row. No missing readings are invented. Select a destination through Android's file picker. Reports are created only on request.

## Session timer and battery graph

Open **Battery → Play sessions & battery graph** and start a session. The timer measures elapsed time, including breaks and time with the screen off. Stop and save the session when finished. The last 30 sessions are kept locally.

Battery levels are sampled approximately once per minute while the accessibility service is running, and when the battery page is opened. The graph shows the current or most recently saved session. Yellow indicates charging; green indicates other readings. Gaps longer than 90 seconds are not joined. The graph does not infer consumption between missing samples. A pending session is discarded after a device reboot; completed sessions are included in backups. This is separate from the existing approximate foreground app-time and screen-off drain tools.

## Automatic local backups

In Extra tools, choose a backup folder using Android's folder picker. Android will schedule a daily backup when the device is idle and charging; it can defer the job. **Back up now** runs a backup immediately. This feature needs no Internet or cloud account.

Archives are validated after writing. Thorhaven keeps the latest seven archives that it successfully created and recorded in the selected folder. Other files are not deleted. A failed archive is removed when possible, and previous completed archives are preserved. The status shows completion, failure or scheduling state. Check it periodically, especially with removable storage. Selecting a different folder starts a separate retention history.

**Disable automatic backups** cancels future jobs. Existing archives remain. Folder permission and scheduling are local to this installation and are not transferred in a backup; choose a folder again after reinstalling or moving devices. Do not disable Thorhaven's Android background execution if you want scheduled jobs to run.

Manual complete ZIP export/import is available under Settings → Complete backup. JSON saves settings, profiles, checklists, completed session records, game cards and editable sketches, but not document files or PDF/map metadata. Backups contain personal data. Import replaces included settings and matching documents, and replaces the older app-time/screen-off history. Use the complete ZIP format when moving your guide library; the collection replacement rules above apply to both formats.

## Automatic fan and performance profiles

Create a profile from **Apps → Profile** or the selected app's **Guides** tools. Choose a performance mode and Quiet, Smart or Sport fan mode. High performance requires Smart or Sport. Turning the fan off is not offered.

Enable **Automatic hardware profiles → Enable for this session** in Extra tools. This requires the accessibility service and root Shizuku or compatible Thor AYN PServer firmware. Settings are applied when the app gains focus. Switching to another app restores the prior hardware values before applying that app's profile. Thorhaven utility windows and Android system UI do not trigger profile changes.

Values are validated and read back. Unsupported firmware or an apply/restore error disables further automation and shows the error. Use **Restore original hardware settings** to retry restoration if needed. Restore manual hardware changes before enabling automation, so they are not mixed with an automatic session. Automation is disabled after process/device restart until you enable it again; any pending hardware recovery snapshot remains available through the restore button. Killing the process cannot guarantee immediate restoration.

These controls are experimental and need testing on an actual AYN Thor. The automated checks use simulated hardware responses; they do not establish physical fan operation or thermal behavior.

## Existing shortcuts and access

Custom Select combinations, the controller keyboard, app pairs, black bottom-screen mode and native remapping remain available. See [the 0.4 guide](Thorhaven-0.4.0-guide.md) for their setup and default shortcuts.

The black-screen feature is an overlay, not physical display power-off. When native remapping is active, hold the original **Select + Start for three seconds** to stop it. If an emulator sees an additional player, select **Thorhaven Controller** as its input device.

## Privacy and troubleshooting

Thorhaven has no Internet permission, trackers or ads. It stores imported documents, notes, game cards, sketches, profiles and measurements locally. Online guide links open in your own browser. Android Files can offer a cloud provider, which may sync a folder or export destination you select. Thorhaven does not upload files itself. The optional accessibility service observes foreground package names and controller buttons, not screen text.

For a revoked folder grant, select the relevant scan, playlist-write or backup folder again. For touch controls, check display assignment, root access and emulator key handling. For automatic hardware profiles, check firmware support and the saved recovery state. Report your app version, Thor model, firmware and reproduction steps in a GitHub issue. Check shared logs for personal information first. The [verification report](Thorhaven-1.0.0-test-results.txt) records automated checks; the [Thor checklist](Thorhaven-1.0.0-device-checklist.md) covers physical-device feedback still needed for a stable 1.0.
