package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.hardware.display.*;
import android.media.ImageReader;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/**
 * Real display windows and current-target resolution; never pins a shortcut in the user's launcher.
 */
final class SetupLaunchChecks {
  interface Attempt {
    void run() throws Exception;
  }

  static boolean rejects(Attempt attempt) {
    try {
      attempt.run();
      return false;
    } catch (Exception expected) {
      return true;
    }
  }

  static boolean onMain(SmokeInstrumentation t, BooleanSupplier condition) {
    boolean[] result = {false};
    t.runOnMainSync(() -> result[0] = condition.getAsBoolean());
    return result[0];
  }

  static void awaitMain(SmokeInstrumentation t, BooleanSupplier condition, long timeout)
      throws Exception {
    long deadline = SystemClock.elapsedRealtime() + timeout;
    while (!onMain(t, condition)) {
      if (SystemClock.elapsedRealtime() >= deadline)
        throw new IOException("Display cleanup timed out");
      SystemClock.sleep(50);
    }
  }

  static void restore(SharedPreferences preferences, Map<String, ?> before, String... keys) {
    SharedPreferences.Editor edit = preferences.edit();
    for (String key : keys) {
      Object value = before.get(key);
      if (value == null) edit.remove(key);
      else if (value instanceof String) edit.putString(key, (String) value);
      else if (value instanceof Integer) edit.putInt(key, (Integer) value);
      else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
      else throw new IllegalStateException("Unexpected test preference type");
    }
    if (!edit.commit()) throw new IllegalStateException("Could not restore test preferences");
  }

  static void run(SmokeInstrumentation t, Context c, MainActivity a) throws Exception {
    Map<String, ?> before = Store.prefs(c).getAll();
    String guide = "nl.thorhaven.qa.setup." + UUID.randomUUID().toString().replace("-", "");
    try {
      List<Display> displays = Store.displays(c);
      t.check(displays.size() >= 2, "Setup fixture has two real public Android displays");
      int top = displays.get(0).getDisplayId(), bottom = displays.get(1).getDisplayId();
      Store.prefs(c)
          .edit()
          .putInt("top", top)
          .putInt("bottom", bottom)
          .putString("language", "en")
          .commit();
      schema(t, c);
      resolution(t, c, top, bottom, guide);
      assignments(t, c, top, bottom);
      ui(t, c, a);
      practice(t, c, bottom);
      emergency(t, c);
      multiOwnerPairStop(t, c);
    } finally {
      t.runOnMainSync(
          () -> {
            DisplayPracticeActivity current = DisplayPracticeActivity.current.get();
            if (current != null) current.finish();
          });
      OfflineGuides.file(c, guide).delete();
      OfflineGuides.prefs(c).edit().remove(guide).commit();
      restore(
          Store.prefs(c), before, "top", "bottom", "language", "pairs", "combos", "autoProfiles");
    }
  }

  static void schema(SmokeInstrumentation t, Context c) throws Exception {
    JSONObject app = LaunchShortcuts.appTarget("com.android.settings", "top");
    JSONObject pair = LaunchShortcuts.pairTarget("com.android.settings", "example.game");
    JSONObject guide = LaunchShortcuts.guideTarget("example.game");
    t.check(
        app.length() == 4 && pair.length() == 4 && guide.length() == 3,
        "Shortcut payloads contain only the narrow versioned app, pair or guide fields");
    for (JSONObject source : new JSONObject[] {app, pair, guide}) {
      t.check(
          rejects(
                  () ->
                      LaunchShortcuts.validate(
                          new JSONObject(source.toString()).put("schema", "1")))
              && rejects(
                  () ->
                      LaunchShortcuts.validate(
                          new JSONObject(source.toString()).put("schema", 1.0)))
              && rejects(
                  () ->
                      LaunchShortcuts.validate(new JSONObject(source.toString()).put("schema", 2)))
              && rejects(
                  () ->
                      LaunchShortcuts.validate(
                          new JSONObject(source.toString()).put("captureToken", "anything")))
              && rejects(
                  () ->
                      LaunchShortcuts.validate(
                          new JSONObject(source.toString()).put("display", 0))),
          "Shortcut "
              + source.getString("kind")
              + " rejects coerced schema, privileges and cached display IDs");
    }
    t.check(
        rejects(() -> LaunchShortcuts.appTarget("../../etc/passwd", "top"))
            && rejects(() -> LaunchShortcuts.appTarget("app;id", "top"))
            && rejects(() -> LaunchShortcuts.appTarget("com.android.settings", "primary"))
            && rejects(() -> LaunchShortcuts.pairTarget("example.game", "example.game"))
            && rejects(
                () -> LaunchShortcuts.validate(new JSONObject(app.toString()).put("pkg", true)))
            && rejects(
                () -> LaunchShortcuts.validate(new JSONObject(app.toString()).put("role", 0)))
            && rejects(
                () ->
                    LaunchShortcuts.validate(
                        new JSONObject(guide.toString()).put("kind", "shell"))),
        "Shortcut targets reject traversal, command text, same-app pairs and invalid field types");
    t.check(
        rejects(() -> StrictJson.object("{\"schema\":1,\"schema\":1}", 1024))
            && rejects(() -> StrictJson.object("{'schema':1}", 1024))
            && rejects(() -> StrictJson.object(app.toString() + " true", 1024))
            && rejects(() -> StrictJson.object("[1]", 1024)),
        "Exported shortcut parsing rejects duplicate keys, loose JSON and trailing payloads");
    JSONObject reordered =
        new JSONObject()
            .put("role", "top")
            .put("pkg", "com.android.settings")
            .put("kind", "app")
            .put("schema", 1);
    String appId = LaunchShortcuts.stableId(app);
    t.check(
        appId.equals(LaunchShortcuts.stableId(reordered))
            && appId.matches("thorhaven-[0-9a-f]{64}")
            && !appId.equals(
                LaunchShortcuts.stableId(
                    LaunchShortcuts.appTarget("com.android.settings", "bottom")))
            && !appId.equals(
                LaunchShortcuts.stableId(LaunchShortcuts.guideTarget("com.android.settings"))),
        "Stable shortcut IDs are independent of JSON order and distinguish screen roles and kinds");
    Intent intent = LaunchShortcuts.intent(c, app);
    JSONObject payload = StrictJson.object(intent.getStringExtra(LaunchShortcuts.EXTRA), 1024);
    t.check(
        intent.getComponent().getClassName().equals(ShortcutActivity.class.getName())
            && Intent.ACTION_VIEW.equals(intent.getAction())
            && payload.getString("role").equals("top")
            && !payload.has("display")
            && intent.getData() == null
            && intent.getClipData() == null,
        "Pinned shortcut intents address only the narrow app trampoline with a logical role");
  }

  static void resolution(SmokeInstrumentation t, Context c, int top, int bottom, String guide)
      throws Exception {
    JSONObject app = LaunchShortcuts.appTarget("com.android.settings", "top");
    t.check(
        LaunchShortcuts.resolve(c, app).getInt("display") == top,
        "App shortcuts resolve the current top assignment at execution");
    t.check(
        rejects(
                () ->
                    LaunchShortcuts.resolve(
                        c, LaunchShortcuts.appTarget("nl.thorhaven.qa.missing", "top")))
            && rejects(() -> LaunchShortcuts.display(c, "unknown")),
        "Missing apps and unknown logical display roles cannot execute");
    String second = null;
    for (Store.App candidate : Store.apps(c)) {
      if (!candidate.pkg.equals("com.android.settings")
          && Store.launchIntent(c, candidate.pkg, bottom) != null) {
        second = candidate.pkg;
        break;
      }
    }
    t.check(second != null, "Pair shortcut fixture has a second installed launchable app");
    JSONObject pair = LaunchShortcuts.pairTarget("com.android.settings", second);
    String pairId = LaunchShortcuts.stableId(pair);
    JSONObject saved =
        new JSONObject()
            .put("name", "Notities 日本語")
            .put("top", "com.android.settings")
            .put("bottom", second);
    Store.prefs(c).edit().putString("pairs", new JSONArray().put(saved).toString()).commit();
    JSONObject result = LaunchShortcuts.resolve(c, pair);
    t.check(
        result.getString("label").equals("Notities 日本語")
            && result.getInt("topDisplay") == top
            && result.getInt("bottomDisplay") == bottom,
        "Pair shortcut resolves current raw user name and both current display assignments");
    saved.put("name", "Gidsen • renamed");
    Store.prefs(c).edit().putString("pairs", new JSONArray().put(saved).toString()).commit();
    t.check(
        LaunchShortcuts.resolve(c, pair).getString("label").equals("Gidsen • renamed")
            && LaunchShortcuts.stableId(pair).equals(pairId),
        "Renaming a saved pair retains its shortcut identity and reads the new exact name");
    Store.prefs(c).edit().putString("pairs", "[]").commit();
    t.check(
        rejects(() -> LaunchShortcuts.resolve(c, pair)),
        "Deleted saved pairs cannot execute from old launcher shortcuts");
    saved.put("name", "Notities\ninvalid");
    Store.prefs(c).edit().putString("pairs", new JSONArray().put(saved).toString()).commit();
    t.check(
        rejects(() -> LaunchShortcuts.resolve(c, pair)),
        "Malformed stored pair names are rejected before launch");
    saved.put("name", "Notities 日本語");
    Store.prefs(c).edit().putString("pairs", new JSONArray().put(saved).toString()).commit();
    OfflineGuides.importStream(
        c,
        guide,
        "Notities 日本語",
        new ByteArrayInputStream("Private offline guide".getBytes(StandardCharsets.UTF_8)));
    JSONObject guideTarget = LaunchShortcuts.guideTarget(guide);
    JSONObject resolvedGuide = LaunchShortcuts.resolve(c, guideTarget);
    t.check(
        resolvedGuide.getString("label").equals("Notities 日本語")
            && resolvedGuide.getInt("display") == bottom
            && c.getPackageManager().getLaunchIntentForPackage(guide) == null,
        "Offline guide shortcuts retain raw names and work without an installed owner app");
    t.check(
        SetupTools.swapRoles(c)
            && LaunchShortcuts.resolve(c, app).getInt("display") == bottom
            && LaunchShortcuts.resolve(c, pair).getInt("topDisplay") == bottom
            && LaunchShortcuts.resolve(c, pair).getInt("bottomDisplay") == top
            && LaunchShortcuts.resolve(c, guideTarget).getInt("display") == top,
        "Already-created app, pair and guide payloads follow a later screen-role swap");
    SetupTools.swapRoles(c);
    Store.prefs(c).edit().putInt("bottom", -1).commit();
    t.check(
        LaunchShortcuts.resolve(c, guideTarget).getInt("display") == top
            && rejects(() -> LaunchShortcuts.resolve(c, pair)),
        "Guides fall back to Top with Bottom disabled while two-screen pairs are refused");
    Store.prefs(c).edit().putInt("bottom", bottom).commit();
    OfflineGuides.file(c, guide).delete();
    t.check(
        rejects(() -> LaunchShortcuts.resolve(c, guideTarget)),
        "Deleted guide bytes cannot execute despite stale metadata");
  }

  static void assignments(SmokeInstrumentation t, Context c, int top, int bottom) throws Exception {
    int savedTop = Store.prefs(c).getInt("top", -1),
        savedBottom = Store.prefs(c).getInt("bottom", -1);
    t.check(
        !SetupTools.assign(c, false, bottom)
            && !SetupTools.assign(c, true, top)
            && !SetupTools.assign(c, false, -1)
            && !SetupTools.assign(c, true, Integer.MAX_VALUE)
            && Store.prefs(c).getInt("top", -1) == savedTop
            && Store.prefs(c).getInt("bottom", -1) == savedBottom,
        "Invalid, missing and colliding role assignments preserve both existing choices");
    t.check(
        SetupTools.assign(c, true, -1) && Store.screen(c, true) == -1 && !SetupTools.swapRoles(c),
        "Bottom can be explicitly disabled and an incomplete assignment cannot swap");
    t.check(
        SetupTools.assign(c, true, bottom) && SetupTools.assign(c, false, top),
        "Existing public displays can be assigned and persisted without permission changes");
    Store.prefs(c)
        .edit()
        .putInt("top", Integer.MAX_VALUE)
        .putInt("bottom", Integer.MAX_VALUE)
        .commit();
    t.check(
        SetupTools.displayExists(c, LaunchShortcuts.display(c, "top"))
            && SetupTools.displayExists(c, LaunchShortcuts.display(c, "bottom")),
        "Stale stored display IDs resolve only to a currently detected public display");
    Store.prefs(c).edit().putInt("top", top).putInt("bottom", bottom).commit();
    JSONObject capabilities = SetupTools.capabilities(c);
    JSONArray ds = capabilities.getJSONArray("displays");
    boolean accurate = ds.length() == Store.displays(c).size();
    for (int i = 0; i < ds.length(); i++) {
      JSONObject d = ds.getJSONObject(i);
      accurate &=
          d.getBoolean("primary") == (d.getInt("id") == Display.DEFAULT_DISPLAY)
              && SetupTools.displayExists(c, d.getInt("id"))
              && d.getInt("width") > 0
              && d.getInt("height") > 0;
    }
    t.check(
        accurate
            && capabilities.getInt("top") == top
            && capabilities.getInt("bottom") == bottom
            && capabilities.getBoolean("notifications")
                == c.getSystemService(NotificationManager.class).areNotificationsEnabled()
            && capabilities.getBoolean("pinnedShortcuts") == LaunchShortcuts.supported(c),
        "Capability dashboard reports actual display roles, dimensions and current"
            + " launcher/notification status");
  }

  static void ui(SmokeInstrumentation t, Context c, MainActivity a) throws Exception {
    final boolean[] built = {true};
    for (String language : new String[] {"nl", "en"}) {
      Store.prefs(c).edit().putString("language", language).commit();
      t.runOnMainSync(
          () -> {
            LinearLayout layout = Ui.col(a);
            SetupTools.page(a, layout);
            LaunchShortcuts.page(a, layout);
            AlertDialog dialog = SetupTools.open(a);
            built[0] &=
                layout.getChildCount() >= 2
                    && dialog != null
                    && dialog.isShowing()
                    && RgbStudioChecks.find(dialog.getWindow().getDecorView(), Button.class).size()
                        >= 10;
            if (dialog != null) dialog.dismiss();
          });
    }
    t.check(
        built[0] && !LaunchShortcuts.pinBusy.get(),
        "Setup and optional shortcut controls render in Dutch and English without requesting a"
            + " pin");
    MainActivity owner =
        (MainActivity)
            t.startActivitySync(
                new Intent(c, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
                ActivityOptions.makeBasic().setLaunchDisplayId(Store.screen(c, true)).toBundle());
    AlertDialog[] dialog = {null};
    t.runOnMainSync(() -> dialog[0] = SetupTools.open(owner));
    t.check(
        dialog[0] != null && dialog[0].isShowing(),
        "Setup dashboard opens on a real secondary-screen Activity");
    t.runOnMainSync(
        () -> {
          dialog[0].dismiss();
          owner.finish();
        });
    awaitMain(t, owner::isDestroyed, 4000);
    t.check(
        onMain(
            t,
            () -> {
              LaunchShortcuts.pin(owner, new JSONObject());
              return SetupTools.open(owner) == null && !LaunchShortcuts.pinBusy.get();
            }),
        "Destroyed setup owners cannot show another dialog or begin a launcher pin request");
  }

  static void practice(SmokeInstrumentation t, Context c, int display) throws Exception {
    DisplayPracticeActivity timed =
        (DisplayPracticeActivity)
            t.startActivitySync(
                new Intent(c, DisplayPracticeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
                ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle());
    t.check(
        onMain(
            t,
            () ->
                timed.getDisplay().getDisplayId() == display
                    && timed.launchedDisplay == display
                    && timed.registered
                    && timed.handler.hasCallbacks(timed.close)),
        "Own-app display practice runs on the requested real display with a bounded close"
            + " callback");
    long deadline = timed.deadline;
    awaitMain(t, timed::isDestroyed, DisplayPracticeActivity.DURATION + 4000);
    t.check(
        SystemClock.elapsedRealtime() >= deadline
            && onMain(
                t,
                () ->
                    !timed.registered
                        && !timed.handler.hasCallbacks(timed.close)
                        && DisplayPracticeActivity.current.get() == null),
        "Real fifteen-second display-test timeout destroys its window and unregisters all"
            + " callbacks");
    // Test-only system display flag: AOSP DisplayManager defines TRUSTED as 1 << 10 from API30.
    // It is absent from the public SDK; no production class or manifest receives extra access.
    final int trustedDisplayFlag = 1 << 10;
    final String trustedPermission = "android.permission.ADD_TRUSTED_DISPLAY";
    final String overlaySetting = "overlay_display_devices";
    UiAutomation automation =
        t.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    Set<Integer> existingDisplays = new HashSet<>();
    for (Display existing : Store.displays(c)) existingDisplays.add(existing.getDisplayId());
    ImageReader reader = null;
    VirtualDisplay virtual = null;
    DisplayPracticeActivity moving = null;
    String previousOverlay = Settings.Global.getString(c.getContentResolver(), overlaySetting);
    boolean overlayChanged = false;
    try {
      final int id;
      if (c.getPackageManager().checkPermission(trustedPermission, "com.android.shell")
          == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        reader = ImageReader.newInstance(640, 480, PixelFormat.RGBA_8888, 2);
        automation.adoptShellPermissionIdentity(trustedPermission);
        try {
          virtual =
              c.getSystemService(DisplayManager.class)
                  .createVirtualDisplay(
                      "Thorhaven QA trusted own-content display",
                      640,
                      480,
                      160,
                      reader.getSurface(),
                      DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                          | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                          | trustedDisplayFlag);
        } finally {
          automation.dropShellPermissionIdentity();
        }
        if (virtual == null) throw new IOException("Could not create trusted QA display");
        id = virtual.getDisplay().getDisplayId();
      } else {
        // Older API30 Shell does not hold ADD_TRUSTED_DISPLAY. The system's developer overlay
        // adapter creates trusted own-content displays. Only an isolated emulator with no existing
        // overlay setting may use this fallback; its physical/emulator displays remain untouched.
        if (!Arrays.asList("ranchu", "goldfish").contains(Build.HARDWARE))
          throw new IOException("Display-removal fixture requires an isolated Android emulator");
        if (previousOverlay != null && !previousOverlay.isEmpty())
          throw new IOException("Existing developer overlay configuration must remain untouched");
        overlayChanged = true;
        writeOverlay(automation, c, "640x480/160,own_content_only");
        awaitMain(
            t,
            () ->
                Store.displays(c).stream()
                    .anyMatch(d -> !existingDisplays.contains(d.getDisplayId())),
            8000);
        Display added = null;
        for (Display candidate : Store.displays(c))
          if (!existingDisplays.contains(candidate.getDisplayId())) {
            if (added != null) throw new IOException("Unexpected additional QA display");
            added = candidate;
          }
        if (added == null) throw new IOException("Developer overlay did not create a display");
        id = added.getDisplayId();
      }
      t.check(
          SetupTools.displayExists(c, id) && !existingDisplays.contains(id),
          "Isolated trusted public QA display contains only its own windows and uses no capture"
              + " session");
      moving =
          (DisplayPracticeActivity)
              t.startActivitySync(
                  new Intent(c, DisplayPracticeActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
                  ActivityOptions.makeBasic().setLaunchDisplayId(id).toBundle());
      DisplayPracticeActivity tested = moving;
      t.check(
          onMain(
              t,
              () ->
                  tested.launchedDisplay == id
                      && tested.getDisplay().getDisplayId() == id
                      && tested.registered),
          "Practice opens a real own-app window on a newly detected public display");
      if (virtual != null) {
        virtual.release();
        virtual = null;
      } else {
        writeOverlay(automation, c, previousOverlay);
        overlayChanged = false;
      }
      awaitMain(t, tested::isDestroyed, 5000);
      t.waitForIdleSync();
      try {
        awaitMain(t, () -> DisplayPracticeActivity.current.get() == null, 5000);
      } catch (IOException timeout) {
        // Report the surviving target/actual display below instead of a generic wait failure.
      }
      t.waitForIdleSync();
      t.check(
          onMain(t, () -> !tested.registered && !tested.handler.hasCallbacks(tested.close)),
          "Removing a display destroys the original practice and clears its listener/timeout");
      String[] migration = new String[1];
      t.runOnMainSync(
          () -> {
            DisplayPracticeActivity active = DisplayPracticeActivity.current.get();
            migration[0] =
                active == null
                    ? "none"
                    : "target="
                        + active.launchedDisplay
                        + ", actual="
                        + (active.getDisplay() == null
                            ? "none"
                            : active.getDisplay().getDisplayId())
                        + ", finishing="
                        + active.isFinishing();
          });
      t.check(
          onMain(t, () -> DisplayPracticeActivity.current.get() == null),
          "Removed-display migration leaves no replacement practice window: " + migration[0]);
      t.check(
          onMain(
              t,
              () ->
                  !SetupTools.displayExists(c, id)
                      && !SetupTools.assign(c, true, id)
                      && !DisplayPracticeActivity.open(c, id)),
          "Removed display rejects later stale practice-open and role-assignment requests");
    } finally {
      DisplayPracticeActivity remaining = moving;
      if (remaining != null) t.runOnMainSync(remaining::finish);
      t.runOnMainSync(
          () -> {
            DisplayPracticeActivity active = DisplayPracticeActivity.current.get();
            if (active != null) active.finish();
          });
      if (virtual != null) virtual.release();
      if (overlayChanged) writeOverlay(automation, c, previousOverlay);
      if (reader != null) reader.close();
      awaitMain(
          t,
          () -> {
            Set<Integer> current = new HashSet<>();
            for (Display d : Store.displays(c)) current.add(d.getDisplayId());
            return current.equals(existingDisplays);
          },
          8000);
      t.check(
          Objects.equals(
              previousOverlay, Settings.Global.getString(c.getContentResolver(), overlaySetting)),
          "Display-removal fixture releases its resources and preserves existing screen"
              + " configuration");
    }
  }

  static void writeOverlay(UiAutomation automation, Context c, String value) throws IOException {
    automation.adoptShellPermissionIdentity("android.permission.WRITE_SECURE_SETTINGS");
    try {
      if (!Settings.Global.putString(c.getContentResolver(), "overlay_display_devices", value))
        throw new IOException("Could not restore developer-display test setting");
    } finally {
      automation.dropShellPermissionIdentity();
    }
  }

  static void emergency(SmokeInstrumentation t, Context c) throws Exception {
    boolean inactive =
        HardwareAutomation.owned == false
            && !HardwareAutomation.busy
            && RgbService.instance == null
            && !RgbService.requested
            && ScreenLabService.instance == null;
    t.check(
        inactive, "Emergency-stop fixture begins without owned hardware, RGB or capture sessions");
    boolean enabled = HardwareAutomation.enabled;
    Store.prefs(c).edit().putBoolean("combos", true).putBoolean("autoProfiles", true).commit();
    try {
      t.runOnMainSync(() -> SetupTools.emergencyStop(c));
      t.waitForIdleSync();
      t.check(
          !Store.prefs(c).getBoolean("combos", true)
              && !Store.prefs(c).getBoolean("autoProfiles", true)
              && !HardwareAutomation.enabled
              && RgbService.instance == null
              && !RgbService.requested
              && ScreenLabService.instance == null,
          "Emergency stop persists disabled chords/profiles and does not start an absent"
              + " RGB/capture helper");
    } finally {
      HardwareAutomation.enabled = enabled;
    }
  }

  static void multiOwnerPairStop(SmokeInstrumentation t, Context c) throws Exception {
    Map<String, ?> before = Store.prefs(c).getAll();
    boolean automationEnabled = HardwareAutomation.enabled;
    String ownPackage = c.getPackageName(), lowerPackage = "com.android.settings";
    String[] keys = {
      "last",
      "lastScreen",
      "recent",
      "combos",
      "autoProfiles",
      "volume:" + ownPackage,
      "brightness:" + ownPackage,
      "volume:" + lowerPackage,
      "brightness:" + lowerPackage
    };
    MainActivity first = null, second = null;
    Instrumentation.ActivityMonitor launches = null;
    try {
      // Real Activity owners and real preflight/Handler scheduling; interception confines this
      // cancellation test to launch attempts without opening an external app or moving our owners.
      first =
          (MainActivity)
              t.startActivitySync(
                  new Intent(c, MainActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
                  ActivityOptions.makeBasic()
                      .setLaunchDisplayId(Store.screen(c, false))
                      .toBundle());
      second =
          (MainActivity)
              t.startActivitySync(
                  new Intent(c, MainActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
                  ActivityOptions.makeBasic().setLaunchDisplayId(Store.screen(c, true)).toBundle());
      Store.prefs(c)
          .edit()
          .putInt("volume:" + ownPackage, -1)
          .putInt("brightness:" + ownPackage, -1)
          .putInt("volume:" + lowerPackage, -1)
          .putInt("brightness:" + lowerPackage, -1)
          .commit();
      JSONObject pair =
          new JSONObject()
              .put("name", "Multi-owner stop fixture")
              .put("top", ownPackage)
              .put("bottom", lowerPackage);
      MainActivity owner = first, other = second;
      IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
      filter.addCategory(Intent.CATEGORY_LAUNCHER);
      launches = t.addMonitor(filter, null, true);
      Instrumentation.ActivityMonitor observed = launches;
      t.runOnMainSync(
          () -> {
            Store.openPair(owner, pair);
            Store.cancelPendingPair(other);
          });
      t.check(
          observed.getHits() == 1,
          "Pair fixture attempts its lower target before the delayed upper launch");
      SystemClock.sleep(550);
      t.waitForIdleSync();
      t.check(
          observed.getHits() == 2,
          "Owner-only cancellation from another real window preserves the first owner's pair");
      t.runOnMainSync(
          () -> {
            Store.openPair(owner, pair);
            SetupTools.emergencyStop(other);
          });
      t.check(
          observed.getHits() == 3,
          "Global Stop fixture reaches the lower launch before cancelling from another window");
      SystemClock.sleep(550);
      t.waitForIdleSync();
      t.check(
          observed.getHits() == 3,
          "Emergency Stop from another real window prevents every delayed upper app launch");
    } finally {
      if (launches != null) t.removeMonitor(launches);
      MainActivity owner = first, other = second;
      t.runOnMainSync(
          () -> {
            Store.cancelAllPendingPairs();
            if (owner != null) owner.finish();
            if (other != null) other.finish();
          });
      if (first != null) awaitMain(t, first::isDestroyed, 4000);
      if (second != null) awaitMain(t, second::isDestroyed, 4000);
      restore(Store.prefs(c), before, keys);
      HardwareAutomation.enabled = automationEnabled;
    }
  }
}
