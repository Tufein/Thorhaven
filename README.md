# Thorhaven

**Make the most of both screens on your AYN Thor.**

Thorhaven helps you launch apps on either screen, keep a game guide or your notes below your game, and adjust your controls from one place. Use the features you need and leave the others switched off.

**Android 11 or newer · Dutch and English · Free and open source**

[**Download the APK**](https://github.com/Tufein/Thorhaven/releases/download/v0.6.0-preview/Thorhaven-0.6.0-preview.apk) · [Release notes](https://github.com/Tufein/Thorhaven/releases/tag/v0.6.0-preview) · [User guide](docs/Thorhaven-0.6.0-guide.md)

> Version 0.6 is an early preview. Some features depend on your Thor's firmware and the apps you use. Start with the basic features before enabling advanced controller or hardware settings.

## What can it do?

| Feature | How it helps |
| --- | --- |
| **Apps on both screens** | Choose where an app opens, or launch two apps together as a saved pair. |
| **Guide libraries and maps** | Search your document library, rename or export originals, adjust reading size and edit bookmarks or map markers. |
| **Notes beside your guide** | Keep tips and progress notes next to the guide, then save your edits. |
| **Quick panel** | Reach volume, brightness, favorites and recent apps, and arrange your cards. |
| **Game checklists and profiles** | Track tasks and manually select settings for different games inside one emulator. |
| **Custom shortcuts** | Assign Select + button combinations to apps, guides, saved pairs and other actions. |
| **Controller tools** | Test buttons and sticks, choose presets and adjust button mappings or stick settings. |
| **Controller keyboard** | Type with the controller or touch, insert symbols and move the text cursor. |
| **Battery and play time** | View local screen-off measurements, estimated app time, a session timer and sampled battery graphs. |
| **Backups** | Export a complete ZIP or choose a folder for daily automatic local backups. |
| **Experimental input tools** | Configure touch buttons, short key macros, bounded turbo and an absolute touch/mouse pad with supported root access. |
| **Experimental Screen Lab** | Mirror the main screen below, choose a crop, save screenshots and record short silent MP4 clips. |
| **Media and reports** | Control an active music session, export completed session samples to CSV or save a diagnostic report. |
| **Optional hardware profiles** | Apply supported per-app fan/performance settings with session opt-in and recovery. |

Advanced options can also move running apps between screens and adjust supported performance, fan and joystick-light settings. These need extra setup; see **Optional setup** below.

## Install in a few steps

1. [Download the APK](https://github.com/Tufein/Thorhaven/releases/download/v0.6.0-preview/Thorhaven-0.6.0-preview.apk) on your Thor.
2. Open it in Android **Files**. If Android asks, allow that app to install the APK.
3. Open **Thorhaven**. In **Instellen / Settings**, choose **Nederlands** or **English**.
4. In **Settings → Displays**, check which screen is assigned as top and bottom.
5. Open **Apps**, choose an app and launch it on the screen you want.

**Already using Thorhaven?** Install the new APK over the existing version to keep your data. Do not uninstall first. The published 0.1–0.6 APKs use the same signing certificate.

## Try these first

- **Save an app pair:** combine your emulator on top with a music app or browser below, then reopen both with one action.
- **Add a guide:** go to **Guides**, select your game or emulator app and add PDF, text, Markdown or map images.
- **Customize the panel:** select touch-only mode to keep controller focus with your game, and choose which cards to show and their order under Extra tools.
- **Set a shortcut:** open **Settings → Custom shortcuts** and choose what a supported Select + button combination should do.
- **Make a backup:** use **Settings → Complete backup** to include your guides and reading positions. The older JSON backup saves settings only.

Guide imports support PDFs and PNG/JPEG images up to **16 MB**, or UTF-8 text and Markdown up to **2 MB**. Markdown is displayed as text. Complete backups support up to **64 guides** and **128 MB of guide data**.

## Optional setup

Basic app launching, saved pairs, notes and settings do not require root. Enable additional access only for the features you want.

| To use… | Enable… |
| --- | --- |
| The quick panel, guides over games, Select shortcuts and usage measurements | Thorhaven's **accessibility service**, through Settings. |
| The controller keyboard | **Thorhaven Controller Keyboard** in Android's keyboard settings. |
| Screen Lab mirroring and recording | Android's **screen capture consent** for each session; notifications are optional. |
| System brightness controls | Android permission to **modify system settings**. |
| Moving or swapping running apps | [Shizuku](https://shizuku.rikka.app/download/), then connect it in Thorhaven. |
| Input Lab, advanced controller remapping and supported hardware controls | The supported AYN root service, or **Shizuku running with root access**. |

Shizuku provides additional Android system access. Starting it through wireless debugging can support live app moves, but normally does not provide the root access needed for advanced input or hardware controls. The [user guide](docs/Thorhaven-0.6.0-guide.md) explains the setup and default shortcuts.

**Controller emergency stop:** while advanced remapping is active, hold the original **Select + Start for three seconds** to stop it. You can also stop remapping from the app or quick panel.

## What is new in 0.6?

- **Screen Lab:** main-screen preview on the lower display, crop controls, PNG screenshots and short silent MP4 recordings with Android consent.
- **Input Lab:** up to six short macros, firmware-dependent bounded key holds, finite turbo bursts and an absolute touch/mouse pad.
- **Better document tools:** search and filter the library, rename guides, export original files, adjust text size/PDF zoom and edit individual bookmarks or markers.
- **Media buttons and reports:** previous/play/pause/next, diagnostic JSON and play-session CSV exports.

Open **Settings → Experimental workshop** to try the new tools. Input Lab settings are under **Extra tools → Open extra options**. Enable only the tools you want to test.

[Read the full change details](docs/Thorhaven-0.6.0-release-notes.md).

<details>
<summary>See the English settings interface (0.4 example)</summary>

![Thorhaven settings showing language selection and keyboard sizes](https://github.com/Tufein/Thorhaven/releases/download/v0.4.0-preview/Thorhaven-0.4.0-English-settings.png)

</details>

## A few things to know

- The **black bottom-screen mode** places a black layer over the screen. It does not physically turn the display off. Double-tap or touch with three fingers to restore the picture.
- Moving a running app can cause it to reload, or the app may refuse to move.
- Named game profiles are **selected manually**. Thorhaven does not detect ROM names inside an emulator. Volume and brightness affect the system.
- The virtual controller may appear as another player. If needed, select **Thorhaven Controller** in your emulator.
- Screen-off battery measurements do not prove that the device entered deep sleep. Play-time figures are estimates.
- Input Lab needs compatible **root access**. Macros use sequential keys, not simultaneous combinations. Holds work only when the firmware supports Android's bounded-duration input option; turbo is a finite burst. The pad uses absolute screen coordinates, with an optional mouse source. Game/emulator acceptance varies.
- Automatic backups can be deferred by Android. Hardware automation is experimental, is disabled after restart and needs compatible Thor firmware.
- Screen Lab captures Android's **default/main display**, which may differ from a manually assigned top screen. The preview is limited to 5 frames per second and 960 pixels on its longest side. Recording captures the full main screen, without audio or crop, for up to 3 minutes or 96 MB. Protected content may stay black.
- Screen Lab retains six screenshots and three recordings locally. Export files you want to keep; captures are not included in complete settings backups.
- Gyro mapping, analog touch sticks, ROM detection and physical display power-off remain unavailable.

**242 automated checks passed**, including real screen-sharing consent, cropped PNG screenshots and silent MP4 recording on the emulator. A signed 0.5 → 0.6 update preserves existing settings, notes and guides. Physical AYN Thor validation is still needed, especially for firmware, input acceptance and capture performance. See the [test report](docs/Thorhaven-0.6.0-test-results.txt) for the exact scope and limits.

## Your data stays local

Thorhaven has **no Internet permission, ads or trackers**. Notes, profiles, guides and measurements stay on your device. Online guide links open in your own browser.

The optional accessibility service uses foreground app names and controller buttons. It does not read screen text or save a keystroke history. Backup files contain your personal notes, guides and usage data, so store them somewhere appropriate. Screen Lab asks Android for capture consent each time and uses a foreground service while sharing. Captures can include visible personal information. Diagnostic reports include firmware and display information but omit notes, guide contents, macro contents, tokens and key history; review any file before sharing it.

## Help and feedback

Check the [user guide](docs/Thorhaven-0.6.0-guide.md) for setup and troubleshooting. If something does not work, [open an issue](https://github.com/Tufein/Thorhaven/issues) and include your Thor model, Android/firmware version, Thorhaven version and the steps that caused the problem. Share logs only after checking them for personal information.

## For developers

The full source is available in this repository and with each release. Build instructions, code structure and test setup are in the [developer guide](docs/DEVELOPMENT.md).

## Credits and license

Thorhaven is an independently written project inspired by [Thor-Wayfinder](https://github.com/Thor-Wayfinder/thor-wayfinder) and the AYN Thor community. No Wayfinder source code or artwork is included. The AYN service protocol adaptation credits OdinTools in the third-party notices.

Original application code is released under the [MIT license](LICENSE.txt). Dependency licenses and attributions are listed in [Third-party notices](THIRD_PARTY_NOTICES.txt).
