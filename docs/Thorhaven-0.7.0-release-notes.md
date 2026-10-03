# Independent joystick colors, effects & app presets — 0.7 Preview

Thorhaven 0.7 adds RGB Studio for the AYN Thor's two joystick lights. Choose a separate style for each stick, preview it, save named presets and switch lighting with your Android apps. The existing launching, guide, backup, Screen Lab and Input Lab features remain available.

## Colors and effects

- Independent left/right on/off, main and second colors, 0–100% brightness and 2–20-second effect duration.
- Copy the left stick's global style to the right, or swap the two global styles with one action.
- Seven effects: solid color, breathing, rainbow, color cycle, soft pulse, battery level and charging indicator.
- Nine built-in quick styles: Thorhaven, Aurora, Ocean, Ember, Retro, Neon, Battery, Charging and Off.
- A two-ring preview uses the same deterministic color calculation as hardware output. Previewing and saving profiles work without lighting hardware.
- Both physical zones of each stick receive that stick's output. Separate zone editing, screen-color capture and audio-reactive RGB are not included.

## Presets and app switching

Save up to 20 personal presets, update them from the global profile, rename them or delete them. Assign saved presets to up to 64 Android apps. Optional accessibility-based app switching works within an active RGB session; unassigned apps use the global profile. ROMs within one emulator are not detected individually.

Saving presets or assignments does not start hardware control. Selecting a style or saving the global editor updates an already active global session on its next tick. Presets, options and assignments are included in validated JSON and complete ZIP backups. Importing them never starts a session.

## Session controls

A user-started foreground service keeps effects running outside the editor and provides a Stop & restore notification action. Direct access uses your choice of 2, 5 or 10 updates per second, with 5 selected by default. Root/AYN bridge updates are capped at 2 per second. Unchanged output is deduplicated. Screen-off pause is enabled by default and writes black once instead of continuing light updates.

Optional dimming follows Android's system brightness value. Low-battery dimming reduces intensity below 20% when not charging. An optional timer stops after 5, 15, 30 or 60 minutes from session start, including screen-off pauses. No automatic boot/process restart or wake lock is used.

## Firmware access and recovery

RGB Studio checks the Thor lighting nodes and actual AYN settings before making changes. Direct access works without root only when the firmware permits all required writes. Compatible AYN PServer or root Shizuku provides a fallback. Fixed hardware nodes, strict channel bounds and serialized background writes limit the interface.

Before writing, Thorhaven saves the actual stock AYN color, on/off and master-brightness settings in a private recovery record. It does not change these stock setting values. Stop restores the light output represented by current valid stock settings when readable, or the saved baseline otherwise. Another RGB application's previous animation is not captured or resumed.

A partial write failure halts updates and attempts restoration. Failed restoration remains visible and blocks new sessions until Restore after interruption succeeds. Recovery records and active sessions are excluded from portable backups. The older manual System RGB controls are blocked while Studio is active.

Stop other RGB controllers and stock animated lighting before running a session. They may overwrite the same hardware and cause flicker or reverting colors. A successful write is not proof of the physical color or both zones working.

## Update and privacy

Install `Thorhaven-0.7.0-preview.apk` over the previous release. Version code 7 uses the same signing certificate as previews 0.1–0.6, allowing an update that retains data.

No Internet, microphone or RGB screen-capture permission and no new third-party dependency was added. The new foreground service declares its local lighting purpose. Optional notification permission exposes its Stop action. RGB Studio does not record images, audio or keystroke history.

## Validation

**317 automated checks passed:** 102 existing Android checks, 43 checks for the 0.5 features, 75 for the 0.6 features, 75 new RGB checks, 12 real screen-capture checks and 10 native helper self-tests. The release build and release lint also passed. The existing Android and capture checks ran against the final signed release.

The RGB suite tests the real Android foreground-service lifecycle with an injected recording backend: start, foreground app selection, global fallback, stop-only commands, in-flight stop, partial-error restoration and cancellation before service creation. It also checks the deterministic engine, validated fixed-node transport, presets and backups. These tests do not establish physical LED output.

A signed update from the released 0.6 APK to the final 0.7 APK succeeded without changing the app UID. Both versions actually displayed the seeded Unicode note and original guide. English language selection, the app volume setting and exact guide bytes remained intact. Reading-metadata backup round trips are covered separately by the document suite. See the [verification report](Thorhaven-0.7.0-test-results.txt).

Physical AYN Thor validation remains necessary for firmware access, physical colors, both lighting zones, stock-effect conflicts and recovery behavior. Emulator previews and recorded hardware transports do not establish actual LED output. This release remains a **pre-release**; RGB hardware behavior is experimental.
