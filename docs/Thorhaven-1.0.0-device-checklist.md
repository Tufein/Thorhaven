# Thorhaven 1.0 release candidate: check it on your Thor

Use the release APK, record its version, and note your Thor model, Android version and AYN firmware version. This checklist is for hardware and real-world behavior that emulator tests cannot establish. Please avoid private notes, account details or ROM contents in screenshots and logs.

## Basic sharing checklist

| Check | What to try | Expected result |
| --- | --- | --- |
| Update | Make a complete backup, then install the signed candidate over 0.8. Do not uninstall first. | Existing profiles, notes, guides and RGB styles remain available. |
| Screens | Open Setup, test each display, confirm Top/Bottom, and swap the roles once. | Each test appears on its selected physical screen and closes within 15 seconds. A test moved onto another display closes. |
| Launching | Launch an emulator on Top and music/guide app on Bottom; save and reopen a pair. | Apps open where Android allows them. Running apps can reload; use Shizuku only if you want a live move. |
| Games | Add two games using one emulator; give each different journal entries and tasks. | Their progress stays separate after restarting Thorhaven. Opening a card shows its details; Open app starts the chosen emulator. |
| Sketchpad | Draw a map with the controller idle, use undo/redo, change page and export PNG. Cancel a pending export and start a fresh one. | Completed strokes persist, cancelled strokes do not; the 1,200 × 800 image preserves the drawing's proportions. A cancelled picker cannot export a newer request. |
| Storage | Choose a small folder on the microSD; scan, export CSV, then reselect the folder after revoking access. | Counts describe that folder only, unknown sizes and partial results are labelled. No existing files change. |
| Multiple discs | Choose CUE or CHD discs in order; authorize their exact folder and create M3U. Load the new playlist in your emulator. | It contains the exact ordered filenames. Check actual disc swapping in that emulator separately. |
| Shortcuts | Request an app, pair or guide shortcut. If the launcher offers confirmation, accept it and check the actual home-screen icon. Change screen roles and use it again. | A supporting launcher pins the shortcut; execution follows current screen roles. Deleted targets produce a useful message. An accepted request alone does not prove it was pinned. |
| Backups | Export a complete ZIP, then restore it after changing a test note/game card/sketch. | Included data and original guides return. Included game/sketch collections replace their current versions. Folder grants, ROMs and captures are not portable. |
| Access denied | Decline optional notifications/accessibility/Shizuku and use Games, Notes, Sketchpad and Storage. | Basic local tools remain usable. |
| Escape | Open Setup and use Stop Thorhaven actions, including with two Thorhaven windows open; re-enable desired controls manually afterward. | Delayed pairs from either window are cancelled. Overlay/capture/control sessions stop or finish their in-flight command; pending recovery remains visible. |

## Optional experimental checks

- RGB: stop other lighting controllers, check firmware access, save a preset, assign an app, test each stick/zone briefly and Stop. Close the diagnostic chooser during a test and check restoration. Confirm actual color, light position and stock restoration. Repeat after a forced app stop and use recovery if offered.
- Input: use a disposable emulator setup, verify Select + Start held for three seconds stops remapping, and check every changed button/stick. Emulator player assignment and game acceptance vary.
- Screen Lab: consent to a preview, take a crop screenshot, cancel a fresh consent prompt, and record a short silent clip. Close the lab and check the clip. Test Android's own stop action too.
- Whole-device Lock: with accessibility enabled, choose Lock device now. Both-screen sleep/wake and lid behavior are firmware/device observations; this action is Android lock, not individual panel power-off.
- Fan/performance: only use supported firmware values, verify the actual behavior, then restore. Record heat, battery or recovery issues before sharing.

## Reporting a result

Create an issue with the candidate version, model/firmware, the feature and exact steps, expected and actual behavior, and whether it also happens with experimental tools disabled. Say which checks passed and which were skipped. A stable 1.0 awaits successful physical-device feedback.
