# Editable app lighting, shared presets & zone tests — 0.8 Preview

Thorhaven 0.8 expands RGB Studio with direct preset editing, easier app assignments, portable style files and short diagnostics for the Thor's four lighting zones. The earlier launcher, document, backup, controller and capture tools remain available.

## Edit styles where you use them

- Edit a saved preset's left/right colors, effects, brightness and timing without overwriting the global profile.
- Duplicate a saved preset to make an independent variant. Names remain user text in either interface language.
- Search installed apps by label or package when assigning a style.
- Preview and edit an assigned preset from its app row. The editor indicates how many apps share it, so changes to a shared style are clear.
- Settings stay on the existing schema. Saving a style does not start hardware control; an active session picks up edits on its next update.

## Share RGB styles separately

Export all saved presets through Android Files to a small JSON file. The format includes names and left/right lighting profiles only. It excludes app packages/assignments, the global profile, session options, notes and recovery records.

Import validates the entire file before adding anything. Each imported style gets a new ID, so existing presets and app assignments stay intact even when names match. Files contain 1–20 presets and are limited to 64 KB. The complete import must fit within the existing 20-preset limit; otherwise it is rejected without replacing data. Import does not start lighting or assign apps automatically. Full JSON/ZIP backups still transfer the regular portable settings.

## Experimental four-zone diagnostics

Choose a single left/right zone for eight seconds, walk through all four zones in eight seconds, or test red/green/blue on each zone over 24 seconds. The other zones receive black. Tests use modest output of 25 per active channel out of 255, rather than maximum brightness.

Each diagnostic is an explicitly started foreground session using the existing serialized worker and recovery journal. It cannot overlap another RGB session or a pending restore. It ignores app switching and normal effect options, ends at its fixed deadline or when the screens turn off, and restores actual AYN lighting settings. The notification and quick panel offer Stop & restore. Failed restoration retains the recovery record and blocks another session until recovery succeeds.

Numeric zone 1/2 addresses come from the known SN3112 firmware interface. Their physical placement and acceptance require device testing. Normal saved profiles still give both zones of each stick the same style; separate persistent zone profiles and screen-color/audio-reactive effects are not included.

## Update and privacy

Install **Thorhaven-0.8.0-preview.apk** over your existing version. Version code 8 uses the established signing certificate, preserving Android update compatibility. Existing RGB presets, app assignments and other user data remain on the same schema.

No new third-party dependency, Internet permission, audio access or RGB screen capture was added. Style-file access uses Android's file picker. Hardware recovery records stay local and are not exported. Diagnostic patterns are temporary and never saved as your normal profile.

## Validation

**389 automated checks passed**, including **72 new checks** for preset transactions, app editing, bundle validation, independent-zone transport and the real Android foreground-service lifecycle with a recording backend. Previous RGB and app suites were rerun, along with actual Android-consented screen capture and native-helper checks. The signed release build and release lint passed.

A forward install from the released signed 0.7 APK preserved the app UID, displayed Unicode note, original guide bytes, English selection, app volume setting, saved RGB preset and app assignment. The public APK download was checked against the signed local file. Full results are attached and available in [the verification report](Thorhaven-0.8.0-test-results.txt).

No physical AYN Thor was available. Recorded transport and emulator tests verify software behavior, not visible light colors, zone positions, writable firmware nodes, PServer support or physical restoration. Stop stock animations and other lighting controllers before using Thorhaven. Hardware features remain experimental in this **pre-release**.

## Files

The release includes the signed APK, source archive, English user guide, change notes, test report, an English screenshot, verification metadata and SHA-256 checksums. The source archive is generated from the release tag and excludes signing keys and local credentials.
