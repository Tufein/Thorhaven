# Thorhaven 0.4.0 — AYN Thor preview

Thorhaven is an independently written native Android companion inspired by Thor-Wayfinder and discussions on r/AynThor. The interface supports Dutch and English. Original application code is MIT licensed; dependency licenses are included. No Wayfinder source code or artwork is included.

## Installation and updates

1. Download `Thorhaven-0.4.0-preview.apk` to your Thor and open it in Android Files. Allow installation from that app if Android asks.
2. Open Thorhaven and select **Instellen → Schermen** to check the top and bottom display assignments. Android display IDs are discovered automatically and need not be 0 and 1.
3. Use **Apps → Boven / Onder** to launch an app on the selected display. **Profiel** sets its preferred display, favorite status, media volume, system brightness and guide link.

Requires Android 11 or newer; the native input helper is ARM64. This APK uses the same signing certificate as previews 0.1, 0.2 and 0.3. Install over the previous version to retain settings; do not uninstall first. Builds signed with a different key cannot directly update this APK.

## New setup options

In **Instellen / Settings**, select **Nederlands** or **English**. This changes interface labels without translating your documents or notes. Choose **Keyboard size**: Normal, Large or Extra large. Reopen the keyboard after changing its size.

Under **Custom shortcuts**, select a Select + button row, choose an action and, where applicable, select an app, saved pair or page. Changes are saved immediately. Use **Restore default shortcuts** to reset bindings. Enable Select shortcuts through the accessibility/controller settings to use them. Disabling an individual action passes its second button through; Select itself remains intercepted while shortcuts are enabled. Home and AYN are not offered. The native three-second Select + Start emergency stop remains independent of these bindings.

**Complete backup** creates a ZIP including offline guides and reading positions as well as settings, notes and usage history. Import asks before replacing matching data. Limits are 64 guides, 128 MB of guide data and 1 MB of metadata. Guide files and usage records are validated before replacement. A local recovery journal restores the previous data if the transaction fails or is interrupted. Unrelated guides remain; usage history is replaced. Unfinished screen-off sessions and hardware recovery state are excluded. JSON settings-only backups remain available with their previous behavior.

Enable **Notes beside guide** to show an editable notes column in the offline reader. Press **Save** to persist edits; unsaved edits are not automatically retained when closing. This reader needs keyboard focus to edit notes.

The expanded keyboard offers **Symbols / Letters**, touch cursor arrows and L1/R1 cursor movement. It includes `<`, `>`, brackets, quotes and currency symbols. Three key sizes and all rows support controller navigation.

## Displays, profiles and quick panel

Launch apps on either display, save two different apps as a pair, manage favorites and local notes, and export/import validated JSON settings. Volume and brightness are global system settings. Profiles are per Android app, not per individual ROM. Applying a profile does not automatically restore the previous volume or brightness when the game closes.

Enable the optional Thorhaven accessibility service through **Instellen → Controller & snelpaneel → Service instellen**. If your firmware restricts sideloaded accessibility services, Android App info may offer **Allow restricted settings**. This service receives foreground package names, controller keys and screen/battery events; it does not retrieve screen text or keep keystroke history.

Under **Instellen → Snelpaneel tijdens gamen**, select **Alleen aanraken** for touch-only operation that keeps controller focus with the game. Configure recent-app, favorite, pair and note cards separately. A focusable panel may capture controller input until closed. Recent apps reflect launches and accessibility observations, not Android's complete task history.

Enable **Select-combinaties inschakelen / Enable Select shortcuts** only if you want shortcuts. The table shows defaults; the custom editor can replace them:

| Shortcut | Action |
| --- | --- |
| Select + Start | Open/close quick panel |
| Select + X | Move the active app to the other display; Shizuku required |
| Select + Y | Notes |
| Select + A | Offline guide for the last active app |
| Select + B | Toggle the black bottom-screen curtain |
| Select + L1 / R1 | Lower/raise media volume |
| Select + D-pad Up / Down | Raise/lower system brightness |

Select acts as an intercepted modifier while shortcuts are enabled. Disable them to use Select normally in games. Home and the AYN button are not remapped by this feature. Firmware may intercept some keys first; printed Xbox/Nintendo labels may differ from Android key codes. L1/R1 navigate all ten main pages.

## Offline guides

Select **Gidsen → Kies een app → Importeer PDF / tekst / Markdown**, then choose a document in Android's file picker. PDFs are limited to 16 MB; text and Markdown to 2 MB. Text must be valid UTF-8. Markdown is displayed as source text. Imports are copied into private local storage; invalid imports preserve the existing guide.

**Offline gids openen** opens on the bottom display. With accessibility enabled, the reader overlays the game, including games using both displays. Without it, a regular activity is used and may pause the underlying app. PDFs support page navigation, page selection and zoom with horizontal scrolling. PDF page and text scroll position are saved locally. Close the reader with **Sluiten**.

Guide files and bookmarks are excluded from the JSON settings backup. Keep original documents separately. Browser guide links open in your browser; Thorhaven does not download web pages.

## Battery, app time and black-screen curtain

**Accu** shows battery status and locally collected screen-off measurements and approximate active app time while the accessibility service is running. Refresh with **Vernieuwen**. The duration-weighted drain average includes only sessions of at least three hours without observed charging or a rising battery level. Short sessions remain separate; reboot invalidates an unfinished session. Up to 30 measurements are retained.

Screen-off does not prove deep sleep. Thorhaven does not disable radios or other apps and does not predict guaranteed battery life. App-time estimates pause when the screen is off or Thorhaven is foreground. Measurements can be cleared on the Accu page and are excluded from settings backups.

**Onderste scherm zwart / herstellen** covers the assigned bottom display with a black, non-focusable accessibility overlay. Double-tap or touch with three fingers to restore it. Select + B also works when shortcuts are enabled. This does not power off the physical display or put the device to sleep; energy savings have not been measured on real hardware.

## Controller tools and mapping

**Controller** displays buttons, sticks and triggers. **Meet stickdrift** asks you to move both sticks, release them and measure five seconds at rest. It proposes the measured peak plus three percentage points as a dead zone. Save globally or for one app; excessive movement is rejected. Stop remapping first. This is a rest measurement, not a hardware diagnosis.

**Mapping → Kies controllerpreset** offers Xbox/default, Nintendo A/B and X/Y swaps, PC menu arrows/Enter/Escape and PC WASD/Space/Escape. Copy another app's profile if useful. Saving a preset does not activate remapping. Keyboard presets require a game supporting keyboard input.

Native evdev/uinput remapping requires the firmware's supported AYN root service or **root-mode Shizuku**. Wireless-debugging Shizuku alone normally lacks the required input-device access. Enable accessibility, configure and save a profile, discover controllers, explicitly select the built-in controller and activate mapping. Set **Thorhaven Controller** as the input device in your emulator if necessary; Android still enumerates the original device, so the virtual pad may appear as another player.

Mapping supports buttons, D-pad-to-keyboard, stick inversion/swap, dead zones and response curves. Profiles can follow the foreground app. Stop through the UI or hold the original **Select + Start for three seconds**. A connection/heartbeat watchdog releases capture on session loss. No firmware modules are installed and no stock mapping service is disabled.

## Shizuku and current app pairs

Install Shizuku from its [official download page](https://shizuku.rikka.app/download/) and follow its [setup guide](https://shizuku.rikka.app/guide/setup/). Authorize Thorhaven via **Instellen → Live schermwissels → Shizuku koppelen**. Live move/swap operates on existing Android tasks and verifies the result. Apps may reload, recreate their activity or refuse a display change; exact game-state preservation is not guaranteed.

Open the quick panel over two distinct apps and select **Bewaar dit app-paar** to save their display assignments under a chosen name. This uses Shizuku and excludes home/recents and Thorhaven itself. It does not terminate the apps.

## Firmware controls

**Systeem → Lees beschikbare systeemregelaars** discovers supported Thor settings through root access. Controls refuse non-Thor hardware and expose only available firmware capabilities: stock performance/fan modes, published CPU frequency caps and joystick RGB color/brightness.

High performance with a quiet or disabled fan is rejected. CPU caps use published frequencies within the hardware limits; no overclocking is offered. Before changes, recovery values are saved locally; writes are read back and partial failures trigger rollback attempts. **Herstel mijn eerdere instellingen** restores original values unless they have since been changed outside Thorhaven. These controls are manual and global, not automatic per-app settings. Stock performance modes may override CPU caps.

Readback validates stored settings, not actual fan speed, CPU behavior or lighting. Hardware behavior depends on firmware and has not been validated on a physical AYN Thor.

## Keyboard, privacy and limitations

Enable **Thorhaven Controller Keyboard** in Instellen, then select it as your input method. D-pad selects a key, A types and B closes the keyboard. Touch, Shift, Space, Delete and Enter are available. Android may require **Show on-screen keyboard** with a physical controller connected. This is a text-field keyboard with symbol mode and cursor controls, not a PC keyboard/trackpad on the other display.

Thorhaven has no Internet permission, advertisements or trackers. Browser links use the browser's network connection. Settings exports include notes, profiles, recent app names and panel preferences; they exclude private guide documents, bookmarks, battery/app-time records and hardware recovery data. The root helper uses local authenticated socket communication and verifies the packaged helper hash.

Not implemented: macros/turbo, mouse/trackpad, gyro mapping, analog-trigger reassignment, per-ROM profiles, independent per-display brightness, physical screen power-off or recording.

## Validation

See `Thorhaven-0.4.0-test-results.txt` for the completed automated checks. Tests used an Android 15 ARM64 emulator with 1920×1080 and 1240×1080 public displays. Real accessibility overlays and Linux uinput events were exercised. Battery calculations used controlled inputs; fan/CPU/RGB used an in-memory firmware fixture. The previous update from the signed 0.2 APK preserved a saved note and controller profile. Android file-picker import and opening a Unicode example guide were also checked manually.

**This preview has not been tested on a physical AYN Thor.** Verify display assignments, controller behavior and your emulators on the device before relying on it during play.
