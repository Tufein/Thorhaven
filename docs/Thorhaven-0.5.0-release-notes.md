# Guide libraries, game tools & automatic backups — 0.5 Preview

This preview expands Thorhaven's lower-screen tools and adds optional automation while preserving existing guides and settings.

## New everyday tools

- **Multiple documents per app:** add several PDFs, UTF-8 text/Markdown guides and PNG/JPEG maps. Reopen the selected document from guide actions.
- **Find next in text guides:** case-insensitive search with wraparound and highlighted matches, including Unicode text.
- **Named PDF bookmarks:** save a name and page, jump from a list or clear the list.
- **Map viewing and markers:** bounded image import, scrolling, zoom and named markers placed by long press.
- **Game checklists:** add, complete and remove local tasks for each Android app.
- **Named game profiles:** manually select saved volume, brightness, display and controller settings for different games inside one emulator. ROM detection is not included.
- **Panel card order:** move app, settings, recent, favorite, pair and touch-control cards into your preferred order.
- **Play-session timer and battery graph:** save elapsed sessions and sampled battery levels, show charging readings and leave gaps where readings are missing.

## Optional automation and controls

- **Daily local backups:** choose a folder, let Android run while idle and charging, validate each archive and retain the last seven successfully recorded archives. A manual Back up now action and status are included. Timing is controlled by Android.
- **Six configurable touch tiles:** send short gamepad or keyboard actions to the upper display through root Shizuku or compatible AYN PServer. These are tap actions, without held buttons, analog sticks or macros. Emulator compatibility must be checked on-device.
- **Automatic fan/performance profiles:** session opt-in, per-app typed settings, serialized switching and original-value restoration. High performance requires Smart or Sport fan mode; fan-off is not offered. Firmware errors disable automation. Pending hardware recovery remains accessible after restart.

## Data and updates

The APK uses the same signing certificate as previous previews. Install over the previous version to preserve data. Existing one-document libraries remain valid. Complete ZIP backups include the new document metadata, named profiles, checklists, panel/tile preferences and completed sessions. Hardware recovery state, pending timers and backup-folder permissions are not transferred.

No Internet permission, ads, trackers or new third-party dependencies were added. Android's boot permission supports persisted backup jobs. The release remains a **pre-release**.

## Validation and limits

**155 automated checks passed:** 102 existing integration checks, 43 new feature checks and 10 native self-tests. A signed 0.4 → 0.5 update also preserved settings, Unicode notes and an existing guide. See the included test report for the scope. Android integration tests cover document import/search, map rendering, backup round trips, archive retention, job constraints, settings validation and UI construction. Existing dual-display, Shizuku and controller checks are retained. Hardware automation transitions use simulated responses.

Physical AYN Thor fan behavior and emulator acceptance of injected touch-tile input have not been independently validated. No analog touch gamepad, held touch buttons, ROM auto-detection, physical screen power-off, gyro or screen recording is included.
