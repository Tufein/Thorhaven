# Screen setup, launch shortcuts and 1.0 readiness

Research checked on 3 October 2026. This records public product descriptions and Android API facts, not physical Thor testing. Original implementation was written for Thorhaven; no Wayfinder code was copied.

## Primary product references

- [Wayfinder repository and README](https://github.com/Thor-Wayfinder/thor-wayfinder): its own feature tour emphasizes choosing screen roles, practicing the setup and selecting features. Its firmware/PServer compatibility statements describe Wayfinder and do not prove Thorhaven compatibility.
- [Wayfinder 1.4](https://github.com/Thor-Wayfinder/thor-wayfinder/releases/tag/v1.4): simpler setup and selective feature enablement, with explicit coexistence guidance for other device controllers. This supports keeping setup guidance separate from automatic hardware manipulation.
- [Wayfinder 1.4.1](https://github.com/Thor-Wayfinder/thor-wayfinder/releases/tag/v1.4.1): GitHub identified this as the latest release when checked; published 2 October, built from `3a083bc`. It reports keyboard editing fixes and restoring selected saturation after startup. These are vendor/product claims, not independent verification.
- [Wayfinder changelog](https://github.com/Thor-Wayfinder/thor-wayfinder/blob/main/CHANGELOG.md): useful for current feature scope, read as documentation only.

## Original community questions

The following threads establish user needs and individual reports. They do not establish a universal firmware protocol or reliable behavior across Lite/Base/Pro/Max variants.

- [Screen switcher app](https://www.reddit.com/r/AynThor/comments/1so9487/screen_switcher_app/): manual app juggling and the difference between reopening an app and moving an existing task.
- [Move open app to other screen](https://www.reddit.com/r/AynThor/comments/1p5u6f8/move_open_app_to_other_screen/): repeated closing/reopening and firmware-dependent workarounds.
- [Is there a function like this anywhere?](https://www.reddit.com/r/AynThor/comments/1u159rv/is_there_a_function_like_this_anywhere/): users want to swap two running apps, with reports of duplicate/reopened activities.
- [Accidentally tap bottom screen](https://www.reddit.com/r/AynThor/comments/1ukc5bc/accidentally_tap_bottom_screen/): accidental touches and controller focus are distinct from physically powering off a panel.
- [Disable the active screen](https://www.reddit.com/r/AynThor/comments/1vi0qa3/is_there_a_way_to_disable_the_active_screen_for/): focus helpers and lower-screen touch prevention.
- [Change in Home button behavior](https://www.reddit.com/r/AynThor/comments/1vn5rb8/change_in_home_button_behavior/): firmware/menu behavior varies; do not guess vendor settings keys.
- [Best way to have a game guide on the bottom screen?](https://www.reddit.com/r/AynThor/comments/1roh9ar/best_way_to_have_a_game_guide_on_the_bottom_screen/): offline guides and remembering reading position.
- [What do you put on the bottom screen?](https://www.reddit.com/r/AynThor/comments/1ww1td1/what_do_you_put_on_the_bottom_screen/): guides, maps and notes as recurring lower-screen uses.
- [Map/HUD on the other screen](https://www.reddit.com/r/AynThor/comments/1u0o0kt/weird_question_but_is_there_any_way_i_can_play/): bounded manual crop presets are useful; this does not justify continuous ambient capture.
- [Wayfinder is a necessity](https://www.reddit.com/r/AynThor/comments/1wsiwyo/wayfinder_is_a_necessity/): owners value shortcuts while asking for lightweight feature selection and coexistence.

## Implemented screen and launch improvements

| Addition | Bounded implementation | Limitation |
| --- | --- | --- |
| Capability/setup dashboard | Fresh public display list, real dimensions, explicit Top/Bottom selection and a 15-second own-app test | Android display 0 is not a claim about physical position |
| Optional launcher shortcuts | User-requested app, saved-pair and offline-guide pins; strict payloads and current-target resolution | Launcher support and user confirmation vary |
| Safer handoff/pairs | Preflight both targets, cancel delayed stale launches and explain reopen versus live task move | Ordinary launches cannot promise retained game state |
| Touch/focus helpers | Bounded black/touch curtain, manual AYN focus instructions and public whole-device lock | A black overlay does not physically power off a panel; vendor focus remains manual |
| Companion tools | Per-game notes/checklists and linked guides; existing general session timer | Does not save or restore a game's internal state; the timer is not linked to a card |
| Existing manual capture crops | Reuse consented Screen Lab capture with bounded preview and recreation-safe crop selection | No saved named crop presets; fresh Android consent and secure-window limits remain |

## Verified Android boundaries

- [ActivityOptions.setLaunchDisplayId](https://developer.android.com/reference/android/app/ActivityOptions#setLaunchDisplayId(int)): display targeting is subject to Android start permission and secondary-activity support. Always check the current target before launch. Thorhaven's own test window identifies the actual display on which it runs.
- [ShortcutManager.requestPinShortcut](https://developer.android.com/reference/android/content/pm/ShortcutManager#requestPinShortcut(android.content.pm.ShortcutInfo,%20android.content.IntentSender)): check launcher support, initiate from a foreground owner, and request only after a user action. The call may block for seconds, so Thorhaven uses one serialized worker. An accepted request is not proof that a pin was created; user refusal may give no callback.
- [AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#GLOBAL_ACTION_LOCK_SCREEN): this is a whole-device Android action. `getSystemActions()` reports currently available actions; `performGlobalAction()` returns actual acceptance. It does not provide independent panel power control.
- [DisplayManager own-content virtual displays](https://developer.android.com/reference/android/hardware/display/DisplayManager#VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY): `PUBLIC | OWN_CONTENT_ONLY` avoids default-display mirroring. Tests create and release their own display and surface; they do not change emulator display configuration or acquire a capture session.
- [MediaProjection guide](https://developer.android.com/media/grow/media-projection): projection sessions require user consent, foreground-service lifecycle and stop/resize handling. Android token reuse and secure-window restrictions rule out silently starting ambient capture from a profile.
- [Processes and threads](https://developer.android.com/guide/components/processes-and-threads): byte limits do not bound slow document-provider latency; SAF work belongs on a worker rather than the Activity thread.

## New code and meaningful verification

`SetupTools` reports real optional access and pending recovery without probing privileged hardware. Assigning roles never changes system display settings. Notification status includes Android's actual notification enablement.

`LaunchShortcuts` uses exact version-1 payloads with logical roles, package identities and no saved display IDs. IDs are stable hashes independent of JSON order and renamed pair labels. An old shortcut rechecks the currently installed app, saved pair or real private guide file. Guide shortcuts work even when their owner app is absent. Names remain raw user content in Dutch and English.

`DisplayPracticeActivity` keeps one own test window, its original display ID and a saved deadline. It closes after fifteen seconds and on display removal, including when Android migrates its Activity to display 0. It removes its Handler callbacks and display listener when destroyed. There is no recording, wake lock or privileged command.

`SetupLaunchChecks.run` covers strict payload boundaries, stable identities, current roles after a swap, disabled/stale display IDs, deleted apps/pairs/guide bytes, raw Unicode names, Dutch/English rendering, destroyed owner guards, actual fifteen-second timeout, actual display removal and emergency-stop non-start behavior. It deliberately does not pin a shortcut in a user's launcher or claim physical panel testing. Build/runtime results are reported by the release coordinator separately.

## Independent release-risk findings and disposition

- Failed ordinary launches previously applied global volume/brightness before Android accepted the Activity. The coordinator moved those changes after successful launch and added a boolean checked launch API.
- App pairs previously held an Activity in an uncancellable delayed callback and did not preflight both sides. The coordinator added a shared generation, fresh target checks, application context callbacks and owner cancellation.
- Deferred MainActivity/ControlLab dialog callbacks could outlive their Activity. The coordinator owns the lifecycle guards and callback teardown.
- Legacy settings SAF I/O previously ran on the Activity thread. The coordinator added `SettingsBackup` worker routing. A small byte limit alone did not prevent provider stalls.
- Ordinary launch profiles intentionally change global volume/brightness; emergency Stop disables future automatic profiles but cannot promise to restore arbitrary earlier global changes. The new dashboard explains this explicitly.
- RGB diagnostics, input transport, stock AYN restore and vendor firmware compatibility still need physical-device checks. Emulator and deterministic fixture tests do not turn these experimental capabilities into verified support for every Thor.
- Run the complete regression suite against the final signed artifact before describing this as a 1.0 candidate. New tests are authored here; they are not asserted as passed without a coordinator runtime result.
