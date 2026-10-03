# Thorhaven 0.6 Preview — User guide

Thorhaven is a local companion for the AYN Thor. Launch apps on either screen, keep documents and notes below your game, and use the optional control tools.

## Install or update

Download `Thorhaven-0.6.0-preview.apk` from the GitHub pre-release and open it in Android Files. Allow Files to install apps if Android asks. Install over your previous Thorhaven version to keep your data; do not uninstall first. Versions 0.1 through 0.6 use the same signing certificate.

Open Settings, choose Dutch or English, and check the top and bottom display assignments. Basic app launching and document tools do not need root.

## Documents, maps and bookmarks

1. Open **Guides** and choose the Android game or emulator app.
2. Select **Add PDF / text / image**. Each import adds a separate document; existing guides stay available.
3. Choose a document in that app's library to open it. Quick-panel guide actions reopen the most recently selected document.
4. For text or Markdown, enter a word and select **Find next**. Search ignores letter case and wraps to the beginning. Markdown is displayed as text.
5. For a PDF, select **Add bookmark**, name the current page, then use **Bookmarks** to return to it. Choose an entry under **Bookmarks** and select **Delete** to remove it, leaving the document intact.
6. For a PNG or JPEG map, select **Zoom** to cycle between 100%, 150% and 200%. Scroll to move around the image. Long-press the map and name a marker. Choose an entry under **Markers** and select **Delete** to remove that marker.

PDFs and images are limited to 16 MB each. Text and Markdown use UTF-8 and are limited to 2 MB. Images are limited to 64 megapixels and 16,000 pixels per dimension, and are downsampled for viewing. Complete backups include up to 64 documents, 128 MB of document data and 1 MB of metadata.

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

Manual complete ZIP export/import is still available under Settings → Complete backup. The older JSON format saves settings, profiles, checklists and completed session records, but not document files or PDF/map metadata. Backups contain personal data. Import merges matching settings/documents and replaces the older app-time/screen-off history. Use the complete ZIP format when moving your library.

## Automatic fan and performance profiles

Create a profile from **Apps → Profile** or the selected app's **Guides** tools. Choose a performance mode and Quiet, Smart or Sport fan mode. High performance requires Smart or Sport. Turning the fan off is not offered.

Enable **Automatic hardware profiles → Enable for this session** in Extra tools. This requires the accessibility service and root Shizuku or compatible Thor AYN PServer firmware. Settings are applied when the app gains focus. Switching to another app restores the prior hardware values before applying that app's profile. Thorhaven utility windows and Android system UI do not trigger profile changes.

Values are validated and read back. Unsupported firmware or an apply/restore error disables further automation and shows the error. Use **Restore original hardware settings** to retry restoration if needed. Restore manual hardware changes before enabling automation, so they are not mixed with an automatic session. Automation is disabled after process/device restart until you enable it again; any pending hardware recovery snapshot remains available through the restore button. Killing the process cannot guarantee immediate restoration.

These controls are experimental and need testing on an actual AYN Thor. The automated checks use simulated hardware responses; they do not establish physical fan operation or thermal behavior.

## Existing shortcuts and access

Custom Select combinations, the controller keyboard, app pairs, black bottom-screen mode and native remapping remain available. See [the 0.4 guide](Thorhaven-0.4.0-guide.md) for their setup and default shortcuts.

The black-screen feature is an overlay, not physical display power-off. When native remapping is active, hold the original **Select + Start for three seconds** to stop it. If an emulator sees an additional player, select **Thorhaven Controller** as its input device.

## Privacy and troubleshooting

Thorhaven has no Internet permission, trackers or ads. It stores documents, notes, profiles and measurements locally. Online guide links open in your own browser. The optional accessibility service observes foreground package names and controller buttons, not screen text.

For a revoked folder grant, choose the backup folder again. For touch controls, check display assignment, root access and emulator key handling. For automatic hardware profiles, check firmware support and the saved recovery state. Report your app version, Thor model, firmware and reproduction steps in a GitHub issue. Check shared logs for personal information first.
