# 0.5 Preview publication checks

- [x] Signed release builds and Android release lint passes.
- [x] Existing integration suite, new feature suite and native self-tests pass.
- [x] Signing certificate matches previous APK releases.
- [x] A forward 0.4 → 0.5 update preserves settings and a guide.
- [x] Debug-only storage provider and tests are absent from release APK.
- [x] Diff inspected for preference validation, bounded document import, typed privileged commands and recoverable hardware changes.
- [x] Plain-English README, installation guide, feature changes and test limits are included.
- [x] Release remains opt-in and experimental; automatic hardware changes require session enablement.

GitHub publication checks (recorded in the release verification JSON): upload APK/source/docs/checksums, confirm pre-release and non-draft state, compare asset digests and download the public APK for verification.

If a release issue is reported, disable the affected optional tool and retain the user's backup. Hardware changes can be restored using the recovery button. A corrective APK should retain the signing key and use a higher Android version code. Reverting source and rebuilding with a higher version code avoids requiring users to uninstall and lose data. Previous GitHub previews and source tags are retained.

No production backend, migration, monitoring service or stakeholder messaging is involved. Physical device testing remains outstanding and is stated in the release description.
