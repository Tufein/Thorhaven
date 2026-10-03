# Thorhaven

**Make the most of both screens on your AYN Thor.**

Thorhaven helps you launch apps on either screen, keep a game guide or your notes below your game, and adjust your controls from one place. Use the features you need and leave the others switched off.

**Android 11 or newer · Dutch and English · Free and open source**

[**Download the APK**](https://github.com/Tufein/Thorhaven/releases/download/v0.4.0-preview/Thorhaven-0.4.0-preview.apk) · [Release notes](https://github.com/Tufein/Thorhaven/releases/tag/v0.4.0-preview) · [User guide](docs/Thorhaven-0.4.0-guide.md)

> Version 0.4 is an early preview. Some features depend on your Thor's firmware and the apps you use. Start with the basic features before enabling advanced controller or hardware settings.

## What can it do?

| Feature | How it helps |
| --- | --- |
| **Apps on both screens** | Choose where an app opens, or launch two apps together as a saved pair. |
| **Offline guides** | Read your own PDF, text or Markdown guide on the bottom screen while playing. |
| **Notes beside your guide** | Keep tips and progress notes next to the guide, then save your edits. |
| **Quick panel** | Reach volume, brightness, favorites and recent apps without returning to the main app. |
| **Custom shortcuts** | Assign Select + button combinations to apps, guides, saved pairs and other actions. |
| **Controller tools** | Test buttons and sticks, choose presets and adjust button mappings or stick settings. |
| **Controller keyboard** | Type with the controller or touch, insert symbols and move the text cursor. |
| **Battery and play time** | View local screen-off battery measurements and estimated time spent in apps. |
| **Backups** | Save settings, notes, offline guides and reading positions in a complete ZIP backup. |

Advanced options can also move running apps between screens and adjust supported performance, fan and joystick-light settings. These need extra setup; see **Optional setup** below.

## Install in a few steps

1. [Download the APK](https://github.com/Tufein/Thorhaven/releases/download/v0.4.0-preview/Thorhaven-0.4.0-preview.apk) on your Thor.
2. Open it in Android **Files**. If Android asks, allow that app to install the APK.
3. Open **Thorhaven**. In **Instellen / Settings**, choose **Nederlands** or **English**.
4. In **Settings → Displays**, check which screen is assigned as top and bottom.
5. Open **Apps**, choose an app and launch it on the screen you want.

**Already using Thorhaven?** Install the new APK over the existing version to keep your data. Do not uninstall first. The published 0.1–0.4 APKs use the same signing certificate.

## Try these first

- **Save an app pair:** combine your emulator on top with a music app or browser below, then reopen both with one action.
- **Add a guide:** go to **Guides**, select your game or emulator app and import a PDF, text or Markdown file.
- **Customize the panel:** select touch-only mode to keep controller focus with your game, and choose which cards to show.
- **Set a shortcut:** open **Settings → Custom shortcuts** and choose what a supported Select + button combination should do.
- **Make a backup:** use **Settings → Complete backup** to include your guides and reading positions. The older JSON backup saves settings only.

Guide imports support PDFs up to **16 MB**, or UTF-8 text and Markdown up to **2 MB**. Markdown is displayed as text. Complete backups support up to **64 guides** and **128 MB of guide data**.

## Optional setup

Basic app launching, saved pairs, notes and settings do not require root. Enable additional access only for the features you want.

| To use… | Enable… |
| --- | --- |
| The quick panel, guides over games, Select shortcuts and usage measurements | Thorhaven's **accessibility service**, through Settings. |
| The controller keyboard | **Thorhaven Controller Keyboard** in Android's keyboard settings. |
| System brightness controls | Android permission to **modify system settings**. |
| Moving or swapping running apps | [Shizuku](https://shizuku.rikka.app/download/), then connect it in Thorhaven. |
| Advanced controller remapping and supported hardware controls | The supported AYN root service, or **Shizuku running with root access**. |

Shizuku provides additional Android system access. Starting it through wireless debugging can support live app moves, but normally does not provide the root access needed for advanced input or hardware controls. The [user guide](docs/Thorhaven-0.4.0-guide.md) explains the setup and default shortcuts.

**Controller emergency stop:** while advanced remapping is active, hold the original **Select + Start for three seconds** to stop it. You can also stop remapping from the app or quick panel.

## What is new in 0.4?

- Choose **Dutch or English**.
- Configure **your own Select shortcuts**.
- Make a **complete backup**, including guides, reading positions and usage history.
- Use **more keyboard symbols**, cursor controls and three key sizes.
- Read a **guide and edit notes side by side**.

[Read the full change details](docs/Thorhaven-0.4.0-release-notes.md).

<details>
<summary>See the English interface</summary>

![Thorhaven settings showing language selection and keyboard sizes](https://github.com/Tufein/Thorhaven/releases/download/v0.4.0-preview/Thorhaven-0.4.0-English-settings.png)

</details>

## A few things to know

- The **black bottom-screen mode** places a black layer over the screen. It does not physically turn the display off. Double-tap or touch with three fingers to restore the picture.
- Moving a running app can cause it to reload, or the app may refuse to move.
- Profiles belong to an **Android app**, not to individual ROMs inside an emulator. Volume and brightness settings apply to the system.
- The virtual controller may appear as another player. If needed, select **Thorhaven Controller** in your emulator.
- Screen-off battery measurements do not prove that the device entered deep sleep. Play-time figures are estimates.
- Macros, turbo, mouse/trackpad, gyro mapping, screen recording and physical display power-off are not included.

**112 automated checks passed** for this release, including two-screen behavior, controller input, backups and update preservation. Tests ran on an Android emulator; firmware-specific fan, CPU and lighting behavior was checked with simulated hardware responses. See the [test report](docs/Thorhaven-0.4.0-test-results.txt) for the scope and limits.

## Your data stays local

Thorhaven has **no Internet permission, ads or trackers**. Notes, profiles, guides and measurements stay on your device. Online guide links open in your own browser.

The optional accessibility service uses foreground app names and controller buttons. It does not read screen text or save a keystroke history. Backup files contain your personal notes, guides and usage data, so store them somewhere appropriate.

## Help and feedback

Check the [user guide](docs/Thorhaven-0.4.0-guide.md) for setup and troubleshooting. If something does not work, [open an issue](https://github.com/Tufein/Thorhaven/issues) and include your Thor model, Android/firmware version, Thorhaven version and the steps that caused the problem. Share logs only after checking them for personal information.

## For developers

The full source is available in this repository and with each release. Build instructions, code structure and test setup are in the [developer guide](docs/DEVELOPMENT.md).

## Credits and license

Thorhaven is an independently written project inspired by [Thor-Wayfinder](https://github.com/Thor-Wayfinder/thor-wayfinder) and the AYN Thor community. No Wayfinder source code or artwork is included. The AYN service protocol adaptation credits OdinTools in the third-party notices.

Original application code is released under the [MIT license](LICENSE.txt). Dependency licenses and attributions are listed in [Third-party notices](THIRD_PARTY_NOTICES.txt).
