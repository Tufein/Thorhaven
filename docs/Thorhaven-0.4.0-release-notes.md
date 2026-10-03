# Custom shortcuts, complete backups and a bilingual interface — 0.4.0 Preview

This update makes Thorhaven easier to configure and preserves more of your local data. Choose Dutch or English, assign your own Select shortcuts, back up offline guides and reading positions, and use an expanded controller keyboard. It also adds notes beside the offline reader.

## What changed

- **Dutch / English language selection:** switch in Settings without changing your notes or guide content. Navigation, controls, dialogs and status labels use a local English catalog.
- **Custom Select shortcuts:** assign supported controller buttons to the quick panel, guide, notes, live move/swap, black curtain, volume/brightness, a chosen app on either display, a saved app pair or a Thorhaven page. Disable individual actions or restore defaults. Shortcuts remain opt-in; Home and the AYN button are not intercepted. Select remains a modifier while shortcuts are enabled.
- **Complete ZIP backup:** includes validated settings, notes, controller profiles, custom shortcuts, language and keyboard preferences, offline guide files, reading positions and battery/app-time history. Up to 64 guides and 128 MB of guide data, with bounded metadata. Unsafe paths, invalid guide content and inconsistent measurements are rejected before installation. A recovery journal restores prior files and preferences if installation fails or is interrupted. Existing JSON settings backup/import remains available.
- **Expanded controller keyboard:** dedicated symbol rows, including angle brackets and currency symbols; cursor buttons and L1/R1 cursor movement; three key sizes; navigation follows the actual keyboard rows. Symbol input, cursor key releases and multiline Enter behavior are supported.
- **Guide and notes side by side:** enable the option in Settings, open an offline guide and edit the app's notes beside it. Use Save to keep your changes. Editing notes needs keyboard focus, even when the quick panel normally uses touch-only mode.

## Install or update

Download **Thorhaven-0.4.0-preview.apk** and install over 0.3, 0.2 or 0.1 to keep your existing data. The signing certificate is unchanged. Requires Android 11 or newer; native remapping uses ARM64. Check display assignments under Settings → Displays.

The new functions do not require Internet access. Existing accessibility, Shizuku and root requirements remain: accessibility enables overlays and Select shortcuts, Shizuku enables live task operations, and supported root access is needed for native input or firmware controls. Saving a shortcut or preset does not automatically activate root remapping.

## Backup behavior

The ZIP contains personal notes, guide documents and usage history. Keep it somewhere appropriate. Imports replace matching settings and guides; unrelated guides remain. Usage history is replaced. Unfinished screen-off sessions and hardware recovery snapshots are deliberately not transferred between devices. Original guide files remain useful as independent copies.

## Validation and limitations

**112 automated checks passed**: 102 Android/integration checks and 10 native self-tests. The signed 0.3 → 0.4 update preserved saved Unicode text and preferences. See the attached test report and verification metadata for details. Validation uses an Android 15 ARM64 emulator with two public displays, real accessibility overlays and Linux uinput events. Firmware fan/CPU/RGB behavior uses an in-memory fixture. This build has not been independently tested on a physical AYN Thor.

The black curtain remains an overlay, not physical display power-off. Profiles remain per Android app, not per ROM. Touch-generated gamepad buttons, automatic fan/performance profiles and firmware controller-focus locking are not included in this release; the previous release's hardware controls remain manual. Macros/turbo, gyro/mouse, analog-trigger reassignment and recording are also not implemented.

## Included files

Signed APK, complete source archive, English guide and release notes, test report, verification metadata, SHA-256 checksums and an English-interface screenshot. Original application code is MIT licensed; dependency notices are included. Signing credentials and local build caches are excluded.
