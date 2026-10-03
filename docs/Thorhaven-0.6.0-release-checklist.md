# 0.6 Preview publication checks

- [x] Final signed release build and release lint succeed.
- [x] 102 existing integration, 43 previous tool, 75 new feature, 12 real capture and 10 native checks pass.
- [x] Actual fresh Android consent, main-screen preview pixels, cropped PNG, silent MP4 and capture cleanup are verified on the emulator.
- [x] Distinct display sizes are verified from a lower-screen context; recording and pointer bounds use the real main display.
- [x] Signing certificate matches all earlier previews; Android version code is 6.
- [x] Released 0.5 and final 0.6 actually render the same preserved notes and guide after updating.
- [x] No Internet/microphone permission or debug-only provider/tests are packaged in the release.
- [x] Preference/metadata imports and privileged input use bounded validation; experimental tools have explicit stops and limits.
- [x] Plain-English README, user guide, change descriptions and test limits are included.

Publication is verified in the attached JSON: commit/tag, APK/source/docs/checksums, pre-release and non-draft state, asset sizes/digests and a public APK download compared with the signed local file.

If an optional tool misbehaves, stop or disable it and preserve a complete backup. Original hardware settings can be restored through the recovery button. Corrective builds must retain the signing key and increase the Android version code. Earlier previews and source tags remain available; uninstalling is not needed for an update.

No production backend or stakeholder messaging is involved. Physical AYN Thor validation remains outstanding and is stated in the release description.
