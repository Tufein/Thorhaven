# Storage and M3U: behavior and test plan

The Storage page measures a folder chosen through Android's file picker. It does not measure all device storage, discover ROMs outside that folder, delete files, or move files. Sizes reported as unknown stay unknown. A blocked subtree or a scan limit makes the report partial.

The scan visits at most 10,000 entries and 12 nested folder levels, displays up to 20 largest known-size files and 100 extension groups, and keeps at most 50 error messages. Stored name/path/identity/URI metadata is also capped at two million characters, with names capped at 1,024 and identities at 4,096 characters; oversized metadata makes the report partial. This prevents a folder full of very long names from producing an unbounded CSV buffer. Its worker has one active request and one waiting request. After two minutes it requests cancellation through `CancellationSignal`; a provider that ignores cancellation may finish its current query first. Canceling or closing the Activity prevents its eventual result from changing a newer page. Folder queries and CSV/M3U byte I/O run off the main thread. Android grant bookkeeping runs when the picker result is handled and preserves rights still needed by the other selected folder.

CSV describes that same folder snapshot, including its timestamp, known byte total, unknown-size count and partial status. Cells containing user filenames are quoted; a leading formula character receives an apostrophe so a spreadsheet does not interpret it as a formula. Raw filenames used in M3U files are preserved.

The M3U assistant selects at most 32 distinct CUE, CHD, ISO, PBP or GDI disc files from one exact folder. Support depends on the emulator/core. BIN tracks are not disc choices. A PBP may already contain multiple discs, so another playlist may be unnecessary. Existing playlists are read as strict UTF-8, limited to 64 KiB, and checked against fresh same-folder filenames. This checks references, not game content or emulator compatibility.

Creating an M3U requires a separate explicit writable-folder selection. The current parent, filenames and document IDs are checked again. A fresh UUID-based name is passed to `DocumentsContract.createDocument`; an existing identity or a changed name is rejected before writing. No existing file is opened for writing. If a provider refuses the new-file write, an incomplete new document may remain; Thorhaven does not delete it or alter original disc files. Folder URIs and Android grants are device-local, outside portable backups.

Android documents that a tree grant covers only the selected directory. On Android 11 and newer, the picker excludes certain roots and `Android/data` / `Android/obb`; the app does not bypass those restrictions. Providers can omit metadata or report an unknown size. These boundaries follow the [official Storage Access Framework guide](https://developer.android.com/training/data-storage/shared/documents-files). The same-folder playlist format and core-specific support are documented by [Beetle PSX](https://docs.libretro.com/library/beetle_psx/#multiple-disk-games), [Flycast](https://docs.libretro.com/library/flycast/#multiple-disc-games), and [PCSX ReARMed](https://docs.libretro.com/library/pcsx_rearmed/#extensions); ISO/PBP use remains conditional on the selected core.

## Automated Android coverage

`StorageToolsChecks.run` uses a DocumentsProvider declared only in the instrumentation APK. A separate DUMP-protected controller offers or revokes genuine Android URI grants; the target app cannot normally access those fixture controls. The production scan/export runs after that temporary shell identity has been dropped and the DocumentsProvider remains protected by `MANAGE_DOCUMENTS`. No real ROMs or user files are involved.

| Area | Covered cases |
| --- | --- |
| Scan | Read-only grant, missing/revoked access, nested folder refusal, unknown size, extension/largest sorting, repeated document IDs, invalid metadata, depth/node/aggregate-text bounds, byte overflow |
| Playlist parsing | Exact Unicode order, supported disc choices, duplicates, mixed folders, unsafe paths/control characters, comments/BOM/CRLF, missing references, malformed UTF-8, 64 KiB bound |
| Safe creation | No write grant, wrong folder, two unique exports with exact bytes, unchanged existing bytes, provider returning an existing identity, changed filename, creation/write refusal |
| Actual UI/result flows | MainActivity folder result, visible report, real disc dialogs, reorder button, duplicate choice rejection, separate write result, actual CSV bytes, English/Dutch UI, actual secondary display |
| Cancellation | Cancel while a provider query is running, ignored late callback, previous report retained, owner Activity finish, cancellation signal set, local grants excluded from backup |

The suite does not automate the system picker itself or prove microSD throughput, cloud-provider behavior, physical Thor display geometry, or emulator disc switching. Manually check the picker on both displays, decline its write grant, cancel each picker, choose a read-only/cloud folder, remove a granted SD card, and open exported playlists in the intended emulator/core. A physical Thor remains necessary for that last compatibility check.

## Isolated Android 11 / API 30 setup

Use an isolated AVD instead of changing the user's emulator. The following are setup examples; adapt the SDK paths and ABI to the host. Repeat with API 35 for current Android behavior. Test and target APKs must use compatible signing certificates.

```sh
sdkmanager 'system-images;android-30;google_apis;arm64-v8a'
avdmanager create avd --name thorhaven-api30-qa --package 'system-images;android-30;google_apis;arm64-v8a'
```

In this new AVD's `config.ini`, configure a second public display before starting it:

```ini
hw.display1.width=1240
hw.display1.height=1080
hw.display1.density=240
hw.display1.flag=1739
```

Start it on a dedicated port and establish the primary display dimensions:

```sh
emulator -avd thorhaven-api30-qa -port 5572 -no-snapshot
adb -s emulator-5572 wait-for-device
adb -s emulator-5572 shell wm size 1920x1080
adb -s emulator-5572 shell wm density 240
adb -s emulator-5572 shell dumpsys display
adb -s emulator-5572 install -r app-debug.apk
adb -s emulator-5572 install -r app-debug-androidTest.apk
adb -s emulator-5572 shell am instrument -w -e v1 true nl.thorhaven.app.test/nl.thorhaven.app.SmokeInstrumentation
```

Check the actual public display IDs in `dumpsys display`; do not assume the secondary ID is always 2. `Store.screen` selects the discovered display. Save the complete instrumentation output, API level, APK hash and signing certificate with the release evidence. A new-feature pass on API 30 does not imply that privileged Shizuku or hardware suites passed there.

For the broader 1.0 capability checks, pinning is a launcher-controlled request. Automated target validation and shortcut execution do not prove that a launcher accepted the pin or that its confirmation was completed. Manually request an app, pair and guide shortcut; accept and cancel the launcher confirmation; change screen roles; remove the saved pair or guide; and launch each shortcut again. Also test a fifteen-second display test when Android refuses an additional display launch, and stop Screen Lab immediately after its capture-consent result. Physical RGB, controller injection and firmware controls remain outside emulator hardware proof.
