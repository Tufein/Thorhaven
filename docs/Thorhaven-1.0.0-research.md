# Research behind the 1.0 release candidate

Researched on 3 October 2026. Original community posts identify useful workflows; they are examples, not a representative survey or proof that a firmware interface works. Android documentation defines the implementation limits. Thorhaven is independently implemented and includes no Wayfinder code or artwork.

| Community need | Original source | Result in Thorhaven |
| --- | --- | --- |
| A smaller currently-playing list rather than an overwhelming collection | [Backlog discussion](https://www.reddit.com/r/AynThor/comments/1t48mro/how_to_deal_with_backlog/), [library discussion](https://www.reddit.com/r/AynThor/comments/1w0avcj/how_many_games_do_you_have_on_your_thor/) | Manual game cards, Playing now, status and favorite filters, searchable labels. |
| Remember progress and unopened collectibles without spoilers | [Notes companion request](https://www.reddit.com/r/AynThor/comments/1uk2lmh/notes_companion/) | Separate journals and checklists for each game, even when games share an emulator. |
| Draw puzzle solutions and maps beside the game | [Drawing discussion](https://www.reddit.com/r/AynThor/comments/1p5j47q/drawingsketching_on_the_thor/), [creative bottom-screen uses](https://www.reddit.com/r/AynThor/comments/1wi1uul/creative_uses_of_the_bottom_screen/) | Named offline sketch pages with grid, colors, undo/redo and PNG export. |
| Understand storage use and organize multiple discs | [Storage question](https://www.reddit.com/r/AynThor/comments/1uroc8e/ayn_thor_full_storage/), [multi-disc discussion](https://www.reddit.com/r/AynThor/comments/1rzob4r/how_to_handle_multidisc_games_on_cocoonuse_m3u/) | Read-only inventory of a chosen folder; ordered same-folder M3U creation and reference checking. |
| Make dual-screen setup and routine launching easier | [Two-screen app management](https://www.reddit.com/r/AynThor/comments/1rp7zs2/how_do_you_guys_manage_apps_with_the_two_screens/), [Wayfinder](https://github.com/Thor-Wayfinder/thor-wayfinder) | Capability dashboard, bounded display practice, role assignments and optional launcher shortcuts. |

## Primary implementation references

- [Android Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files): users select a document or folder; permissions can become unavailable after a move, deletion or grant revocation. Android 11 limits selectable storage roots, Downloads trees and Android/data or Android/obb. A selected-folder inventory cannot describe every private app container or full-device usage.
- [DocumentsContract](https://developer.android.com/reference/android/provider/DocumentsContract): document URIs and identifiers remain provider-owned. Thorhaven does not turn content URIs into guessed filesystem paths. M3U creation requests write access to the exact disc folder, rechecks filenames and creates a new document.
- [Libretro multi-disc documentation source](https://github.com/libretro/docs/blob/master/docs/library/beetle_psx_hw.md#multiple-disk-games): ordered disc filenames form an M3U; CUE/CHD references are documented. Emulator and frontend support varies. BIN tracks are not interchangeable with complete CUE disc definitions. Thorhaven checks same-folder names, not disc contents or every emulator's parser.
- [Android custom drawing](https://developer.android.com/develop/ui/views/layout/custom-views/custom-drawing) and [MotionEvent](https://developer.android.com/reference/android/view/MotionEvent#ACTION_CANCEL): sketch strokes use normalized coordinates, bounded data and explicit cancellation on pointer loss or view detachment.
- [Pinned launcher shortcuts](https://developer.android.com/develop/ui/compose/system/shortcuts/creating-shortcuts): Android's ShortcutManager lets a supporting launcher request user confirmation. A requested pin is not proof that a shortcut has been pinned.
- [Accessibility global actions](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#performGlobalAction(int)): the optional Lock device action checks current availability and the returned success value. It locks the whole device, not an individual panel.
- [MediaProjection](https://developer.android.com/media/grow/media-projection): each capture session needs Android consent. Cancelling consent, Android stopping a projection and stopping a still-bound Screen Lab are runtime test cases.

## What this candidate deliberately promises

Game cards are manual records. Opening one shows its progress; **Open app** starts the selected Android app, where the user chooses a ROM if it is an emulator. Storage tools read only the selected folder and clearly distinguish unknown sizes and partial results. Sketch pages are local drawings, with a saved model and a separate flattened PNG export. Basic tools need no root and introduce no Internet permission. These choices are Thorhaven's interpretation of the community workflows, rather than behavior promised by those sources.

Existing RGB, privileged input and hardware profiles remain experimental. Firmware interface checks and emulator fixtures cannot establish physical LED color, stick behavior, lid behavior or performance on a real Thor. Ambient RGB, ROM compression and automatic lid-based power control are outside this candidate's scope.
