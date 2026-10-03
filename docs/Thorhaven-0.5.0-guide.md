# Thorhaven 0.5 Preview — User guide

Thorhaven is a local companion for the AYN Thor. Launch apps on either screen, keep documents and notes below your game, and use the optional control tools.

## Install or update

Download `Thorhaven-0.5.0-preview.apk` from the GitHub pre-release and open it in Android Files. Allow Files to install apps if Android asks. Install over your previous Thorhaven version to keep your data; do not uninstall first. Versions 0.1 through 0.5 use the same signing certificate.

Open Settings, choose Dutch or English, and check the top and bottom display assignments. Basic app launching and document tools do not need root.

## Documents, maps and bookmarks

1. Open **Guides** and choose the Android game or emulator app.
2. Select **Add PDF / text / image**. Each import adds a separate document; existing guides stay available.
3. Choose a document in that app's library to open it. Quick-panel guide actions reopen the most recently selected document.
4. For text or Markdown, enter a word and select **Find next**. Search ignores letter case and wraps to the beginning. Markdown is displayed as text.
5. For a PDF, select **Add bookmark**, name the current page, then use **Bookmarks** to return to it. **Clear bookmarks** removes the named list, leaving the document intact.
6. For a PNG or JPEG map, select **Zoom** to cycle between 100%, 150% and 200%. Scroll to move around the image. Long-press the map and name a marker. **Clear markers** removes that map's saved markers.

PDFs and images are limited to 16 MB each. Text and Markdown use UTF-8 and are limited to 2 MB. Images are limited to 64 megapixels and 16,000 pixels per dimension, and are downsampled for viewing. Complete backups include up to 64 documents, 128 MB of document data and 1 MB of metadata.

PDF search is not included; text search works on text and Markdown guides. Bookmarks and map markers are included in complete ZIP backups.

Enable the accessibility service to show a guide above a game on the lower display. Without it, guides open in a separate activity. Settings → Notes beside guide enables the split reader and notes editor; save note edits explicitly.

## Checklists and named game profiles

Under Guides, select an app and find **Game checklist**. Add tasks, tick completed items and remove entries with ×. Up to 200 tasks are supported per app. Tasks belong to the selected Android app, so an emulator can have a shared list with game names in the task text.

**Named game profiles** store that app's saved volume, brightness, display and controller configuration. Save the app's normal settings first, then create a profile with a game name. Up to 30 profiles are supported per app. Select a profile manually when changing games inside an emulator. Thorhaven does not detect ROM names or automatically launch ROMs. Selecting a profile applies volume/brightness and the active remapping configuration; its display choice is used next time the app is launched.

## Arrange your panel and use touch buttons

Open **Settings → Extra tools → Open extra options**.

**Panel card order:** select ↑ beside a card to move it to the front. System volume, brightness, emergency stop and screen actions remain at the top. Card visibility and touch-only panel mode remain available in Settings.

**Touch control tiles:** choose a tap action for each of six tiles, then open the controls. The same controls are available from the quick panel. They appear on the lower display without taking controller focus. The target app must be active on the upper display.

Touch tiles require root Shizuku or compatible AYN PServer firmware. They send a short gamepad or keyboard press through a restricted system command. They do not support holding a button, simultaneous input, analog sticks, macros or automatic repeats. Game/emulator support for injected keys varies; configure matching hotkeys in the emulator and check on your Thor. Unsupported firmware reports an error. Home and AYN keys are not offered. Close the panel using its Close button.

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
