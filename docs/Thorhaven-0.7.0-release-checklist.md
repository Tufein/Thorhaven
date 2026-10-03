# Thorhaven 0.7 Preview — release checks

This checklist records the signed 0.7 preview's verified implementation checks and remaining publication checks. Pre-publication evidence includes 317 passing automated checks, successful release build/lint and a signed 0.6 → 0.7 update. Implementation checks use code review, recorded transports or emulator tests as appropriate; they do not establish physical Thor lighting behavior. Published asset hashes and download results are recorded separately in the attached verification JSON.

## Implementation and compatibility

- [x] Release has version name `0.7.0-preview` and version code 7.
- [x] Release build and release lint complete successfully.
- [x] The signed APK installs over the released signed 0.6 APK without uninstalling or changing the app UID.
- [x] The forward install preserves English selection, an app volume setting, a displayed Unicode note and exact original guide bytes. Both versions actually render the seeded note and guide.
- [x] RGB Studio is reachable from navigation and the quick panel.
- [x] Color/effect preview works without Thor hardware or privileged access.
- [x] Both JSON and complete ZIP backup round trips preserve RGB presets, options and app assignments. The document suite separately covers complete ZIP reading-metadata restoration.
- [x] Imports do not start RGB and do not transfer active sessions or recovery records.

## RGB validation

- [x] Invalid schemas, fields, color formats, numeric ranges, duplicate preset identifiers and stale app assignments are rejected before saved settings change.
- [x] Engine checks cover all seven effects, independent colors, disabled sides, brightness bounds, battery/charging behavior and unknown battery readings.
- [x] Preview and hardware output use the same engine.
- [x] Recorded hardware requests use only the fixed two-sided enable/brightness nodes and bounded zone/channel values.
- [x] Direct-write guards require Thor identity and all necessary writable nodes; bridge requests have typed validation.
- [x] A missing or malformed stock AYN baseline prevents hardware writes.
- [x] Rate limits, timer behavior, deduplication, screen-off pause and cancellation are checked.
- [x] Global fallback, assigned app presets and clearing foreground selection are checked.
- [x] Stop and partial-write failure attempt restoration; failed recovery retains the journal and blocks new sessions.
- [x] Current valid stock settings take precedence during stop/recovery.
- [x] The old manual System RGB controls are blocked while Studio is active.
- [x] Real Android service checks cover start, foreground selection, stop-only commands, in-flight stop, error restoration and cancellation before service creation. The non-sticky restart policy and notification Stop action are reviewed.

## Documentation and assets

- [ ] README links point to the 0.7 APK, guide, notes and verification report.
- [x] Release title and English notes describe the actual RGB changes.
- [x] User guide explains conflicts, notification permission, firmware dependence and recovery.
- [x] Actual test counts replace the validation placeholder in release notes.
- [x] Validation notes distinguish deterministic/fake-transport checks from physical Thor testing.
- [x] APK inspection confirms no Internet/microphone permission, instrumentation or debug provider.
- [ ] Source archive is generated from the release commit and excludes signing secrets, local configuration and downloaded reference implementations.
- [ ] Checksums and verification JSON identify every published asset.
- [ ] Publish as a GitHub pre-release, confirm it is not a draft and verify the public APK download matches the local signed APK.

The unchecked packaging/publication items are completed by release verification and recorded in the attached JSON. They are not claimed as complete here before that evidence exists.

## Physical validation still required

No emulator or fake transport establishes actual LED color, both zones, direct-write permissions, PServer behavior, stock animated-light conflicts or restoration on every Thor firmware. Preserve that limitation in the release notes unless an actual device test is recorded. Existing input, performance, fan and thermal limitations also remain in effect.
