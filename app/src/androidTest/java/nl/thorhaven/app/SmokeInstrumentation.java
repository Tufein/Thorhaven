package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

public class SmokeInstrumentation extends Instrumentation {
  boolean v5Only;
  boolean v6Only;
  boolean v7Only;
  boolean v8Only;
  boolean captureOnly;
  int passed;
  StringBuilder report = new StringBuilder();

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    v5Only = args != null && "true".equals(args.getString("v5"));
    v6Only = args != null && "true".equals(args.getString("v6"));
    v7Only = args != null && "true".equals(args.getString("v7"));
    v8Only = args != null && "true".equals(args.getString("v8"));
    captureOnly = args != null && "true".equals(args.getString("capture"));
    start();
  }

  void check(boolean good, String label) throws Exception {
    if (!good) throw new Exception(label);
    passed++;
    report.append("PASS ").append(label).append('\n');
  }

  @Override
  public void onStart() {
    if (v8Only) {
      v8();
      return;
    }
    if (v7Only) {
      v7();
      return;
    }
    if (captureOnly) {
      capture();
      return;
    }
    if (v6Only) {
      v6();
      return;
    }
    if (v5Only) {
      v5();
      return;
    }
    Bundle result = new Bundle();
    Context c = getTargetContext();
    try {
      JSONObject before = Store.backup(c);
      Store.prefs(c).edit().putString("language", "nl").commit();
      check(
          SystemBridge.parse(
                      "RootTask id=7 bounds=[0,0][1920,1080] displayId=0 userId=0\n"
                          + " configuration={activityType=standard}\n"
                          + " taskId=7: example.game/.Main visible=true"
                          + " topActivity=ComponentInfo{example.game/.Main}\n\n"
                          + "RootTask id=1 bounds=[] displayId=0 userId=0\n"
                          + " configuration={activityType=home}\n"
                          + " taskId=1: example.home/.Main visible=true"
                          + " topActivity=ComponentInfo{example.home/.Main}\n")
                  .size()
              == 1,
          "Stack parser excludes the home task");
      check(
          new SystemBridge().movePackage("bad;command", 2).startsWith("Ongeldige"),
          "Bridge rejects malformed package names before execution");
      check(Store.displays(c).size() >= 2, "Two public displays discovered");
      check(Store.screen(c, true) != Store.screen(c, false), "Independent display IDs selected");
      check(Store.apps(c).size() > 0, "Installed launchable apps discovered");
      Store.prefs(c)
          .edit()
          .putBoolean("favorite:com.android.settings", true)
          .putInt("volume:com.android.settings", 37)
          .putString("notes:com.android.settings", "Test € 日本語")
          .apply();
      String json = Store.backup(c).toString();
      Store.prefs(c).edit().remove("notes:com.android.settings").apply();
      Store.restore(c, json);
      check(
          Store.prefs(c).getString("notes:com.android.settings", "").equals("Test € 日本語"),
          "Backup round-trip preserves Unicode notes");
      check(
          Store.prefs(c).getInt("volume:com.android.settings", 0) == 37,
          "Backup round-trip preserves profile values");
      boolean rejected = false;
      try {
        Store.restore(
            c,
            "{\"schema\":1,\"data\":{\"notes:com.android.settings\":\"changed\",\"volume:com.android.settings\":999}}");
      } catch (Exception e) {
        rejected = true;
      }
      check(
          rejected
              && Store.prefs(c).getString("notes:com.android.settings", "").equals("Test € 日本語"),
          "Malformed import is rejected atomically");
      rejected = false;
      try {
        Store.restore(c, "{\"schema\":2,\"data\":{}}");
      } catch (Exception e) {
        rejected = true;
      }
      check(rejected, "Unknown backup schema is rejected");
      Store.prefs(c).edit().putInt("bottom", -1).apply();
      check(Store.screen(c, true) == -1, "Unassigned secondary display stays disabled");
      Store.prefs(c).edit().remove("bottom").apply();
      runOnMainSync(
          () -> {
            QuickPanel.build(
                c.createDisplayContext(
                    c.getSystemService(android.hardware.display.DisplayManager.class)
                        .getDisplay(Store.screen(c, true))),
                null);
          });
      check(true, "Quick panel builds in secondary-display context");
      Intent intent = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      Activity a = startActivitySync(intent);
      check(a != null, "Dashboard starts successfully");
      runOnMainSync(
          () -> {
            MainActivity m = (MainActivity) a;
            for (String page : MainActivity.PAGES) m.go(page);
          });
      check(true, "All ten pages render without exception");
      long deadline = System.currentTimeMillis() + 8000;
      while (Bridge.remote == null && System.currentTimeMillis() < deadline) Thread.sleep(100);
      check(
          Bridge.remote != null, "Shizuku user service connects with authorized shell privileges");
      runOnMainSync(() -> Store.launch(c, "com.android.settings", Store.screen(c, false)));
      Thread.sleep(800);
      String moved = Bridge.remote.movePackage("com.android.settings", Store.screen(c, true));
      check(
          moved.startsWith("App live verplaatst"),
          "An existing settings task moves live to the secondary display: " + moved);
      runOnMainSync(() -> Store.launch(c, "moe.shizuku.privileged.api", Store.screen(c, false)));
      Thread.sleep(800);
      String swapped = Bridge.remote.swapScreens(Store.screen(c, false), Store.screen(c, true));
      check(
          swapped.equals("Apps op beide schermen gewisseld."),
          "Two existing app tasks swap displays with Shizuku: " + swapped);
      String movedBack = Bridge.remote.movePackage("com.android.settings", Store.screen(c, false));
      check(
          movedBack.equals("App staat al op dit scherm."),
          "Repeated move to the same screen is a safe no-op");
      JSONObject currentPair =
          new JSONObject(Bridge.remote.currentApps(Store.screen(c, false), Store.screen(c, true)));
      check(
          currentPair.optString("top").equals("com.android.settings")
              && currentPair.optString("bottom").equals("moe.shizuku.privileged.api"),
          "Current app-pair capture reflects both real screen tasks");
      extended(c, (MainActivity) a);
      new NewFeatureChecks(this, c, (MainActivity) a).run();
      new V4FeatureChecks(this, c, (MainActivity) a).run();
      Store.prefs(c).edit().clear().commit();
      Store.restore(c, before.toString());
      result.putString("stream", report + "\n" + passed + " checks passed\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      result.putString("stream", report + "\nFAILED: " + e);
      finish(Activity.RESULT_CANCELED, result);
    }
  }

  static final class HardwareFixture implements DeviceControl.Runner {
    java.util.Map<String, String> settings = new java.util.HashMap<>(),
        files = new java.util.HashMap<>();
    boolean failFan;

    HardwareFixture() {
      settings.put("is_quick_set_performance_and_fan_enable", "0");
      settings.put("performance_mode", "0");
      settings.put("fan_mode", "4");
      settings.put("joystick_light_enabled", "1,1");
      settings.put("joystick_led_light_picker_color", "#FF00FF00,#FF00FF00");
      settings.put("led_light_brightness_percent", "0.5");
      String p = DeviceControl.cpuPath(0);
      files.put(p + "scaling_available_frequencies", "500000 1000000 2000000");
      files.put(p + "scaling_min_freq", "500000");
      files.put(p + "scaling_max_freq", "2000000");
      files.put(p + "cpuinfo_max_freq", "2000000");
    }

    public String run(String cmd) throws Exception {
      if (cmd.startsWith("settings get system "))
        return settings.getOrDefault(cmd.substring(20), "null");
      if (cmd.startsWith("settings put system ")) {
        String[] parts = cmd.substring(20).split(" ", 2);
        if (failFan && parts[0].equals("fan_mode")) {
          failFan = false;
          throw new Exception("Fixture fan failure");
        }
        settings.put(parts[0], parts[1].substring(1, parts[1].length() - 1));
        return "";
      }
      if (cmd.startsWith("cat ")) return files.getOrDefault(cmd.substring(4).split(" ")[0], "");
      if (cmd.startsWith("printf ")) {
        String[] parts = cmd.split(" ");
        files.put(parts[4], parts[2].replace("'", ""));
        return "";
      }
      throw new Exception("Unexpected fixture command: " + cmd);
    }
  }

  JSONObject await(java.util.function.Consumer<java.util.function.Consumer<JSONObject>> operation)
      throws Exception {
    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicReference<JSONObject> value =
        new java.util.concurrent.atomic.AtomicReference<>();
    operation.accept(
        r -> {
          value.set(r);
          latch.countDown();
        });
    if (!latch.await(15, java.util.concurrent.TimeUnit.SECONDS))
      throw new Exception("Async operation timed out");
    return value.get();
  }

  String shell(String command) throws Exception {
    try (android.os.ParcelFileDescriptor fd =
            getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                .executeShellCommand(command);
        java.io.InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) {
      java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
      byte[] b = new byte[4096];
      int n;
      while ((n = input.read(b)) != -1) output.write(b, 0, n);
      return output.toString("UTF-8");
    }
  }

  void event(int type, int code, int value) throws Exception {
    android.os.ParcelFileDescriptor[] pipes =
        getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            .executeShellCommandRw("tee /data/local/tmp/thorhaven-fixture.commands");
    try (java.io.OutputStream output =
        new android.os.ParcelFileDescriptor.AutoCloseOutputStream(pipes[1])) {
      output.write((type + " " + code + " " + value + "\n").getBytes("UTF-8"));
    }
    try (java.io.InputStream echo =
        new android.os.ParcelFileDescriptor.AutoCloseInputStream(pipes[0])) {
      byte[] bytes = new byte[128];
      while (echo.read(bytes) != -1) {}
    }
    Thread.sleep(150);
  }

  String fixtureLog(int start) throws Exception {
    String log = shell("cat /data/local/tmp/thorhaven-fixture.log");
    return log.substring(Math.min(start, log.length()));
  }

  void extended(Context c, MainActivity a) throws Exception {
    HardwareFixture f = new HardwareFixture();
    JSONObject changed =
        DeviceControl.apply(f, new JSONObject().put("performance_mode", "1").put("fan_mode", "5"));
    check(
        f.settings.get("performance_mode").equals("1") && f.settings.get("fan_mode").equals("5"),
        "Hardware transaction applies and reads back multiple settings");
    DeviceControl.restore(f, changed);
    check(
        f.settings.get("performance_mode").equals("0") && f.settings.get("fan_mode").equals("4"),
        "Hardware snapshot restores original settings");
    f.failFan = true;
    boolean rejected = false;
    try {
      DeviceControl.apply(f, new JSONObject().put("performance_mode", "1").put("fan_mode", "5"));
    } catch (Exception expected) {
      rejected = true;
    }
    check(
        rejected && f.settings.get("performance_mode").equals("0"),
        "Partial hardware failure rolls back earlier writes");
    rejected = false;
    try {
      DeviceControl.apply(f, new JSONObject().put("performance_mode", "2").put("fan_mode", "1"));
    } catch (Exception expected) {
      rejected = true;
    }
    check(
        rejected && f.settings.get("performance_mode").equals("0"),
        "High performance with quiet fan is rejected before writes");
    changed = DeviceControl.apply(f, new JSONObject().put("fan_mode", "5"));
    f.settings.put("fan_mode", "1");
    DeviceControl.restore(f, changed);
    check(
        f.settings.get("fan_mode").equals("1"),
        "Restore preserves settings changed outside Thorhaven");
    f.settings.put("fan_mode", "4");
    changed = DeviceControl.apply(f, new JSONObject().put("cpu:0", "1000000"));
    check(
        f.files.get(DeviceControl.cpuPath(0) + "scaling_max_freq").equals("1000000"),
        "CPU cap selects a published frequency");
    DeviceControl.restore(f, changed);
    check(
        f.files.get(DeviceControl.cpuPath(0) + "scaling_max_freq").equals("2000000"),
        "CPU cap restores previous frequency");
    rejected = false;
    try {
      DeviceControl.apply(f, new JSONObject().put("cpu:0", "9000000"));
    } catch (Exception expected) {
      rejected = true;
    }
    check(
        rejected && f.files.get(DeviceControl.cpuPath(0) + "scaling_max_freq").equals("2000000"),
        "Unavailable CPU frequencies rejected before writes");
    rejected = false;
    try {
      DeviceControl.apply(f, new JSONObject().put("unknown;touch /tmp/x", "1"));
    } catch (Exception expected) {
      rejected = true;
    }
    check(rejected, "Unknown hardware command is rejected");
    JSONObject profile = PadProfile.defaults();
    profile.getJSONArray("buttons").put(0, 305).put(1, 305).put(15, 28);
    profile.put("mask", 1);
    Controls.save(c, "global", profile);
    String backup = Store.backup(c).toString();
    Store.prefs(c).edit().remove("mapping:global").commit();
    Store.restore(c, backup);
    check(
        Controls.load(c, "global").getJSONArray("buttons").getInt(0) == 305,
        "Backup preserves validated mapping profiles");
    rejected = false;
    try {
      Store.restore(
          c,
          new JSONObject()
              .put("schema", 1)
              .put(
                  "data",
                  new JSONObject()
                      .put("notes:test", "bad")
                      .put(
                          "mapping:global",
                          profile.toString().replace("\"left\":10", "\"left\":99")))
              .toString());
    } catch (Exception expected) {
      rejected = true;
    }
    check(
        rejected && !Store.prefs(c).contains("notes:test"),
        "Invalid mapping import is rejected atomically");
    JSONObject device =
        new JSONObject(Bridge.remote.deviceCall(new JSONObject().put("op", "status").toString()));
    check(
        device.optString("error").contains("alleen beschikbaar"),
        "Hardware controls refuse non-Thor hardware");
    JSONObject probe = await(cb -> Controls.probe(c, cb));
    check(
        !probe.has("error") && probe.getInt("uid") == 0,
        "Native helper starts through root Shizuku with verified hash");
    int index = -1;
    JSONArray devices = probe.getJSONArray("devices");
    for (int i = 0; i < devices.length(); i++)
      if (devices.getJSONObject(i).getString("name").equals("Thorhaven Test Source"))
        index = devices.getJSONObject(i).getInt("event");
    check(index >= 0, "Real Linux uinput fixture discovered as gamepad");
    ThorService oldService = ThorService.instance;
    ThorService.instance = new ThorService();
    final int source = index;
    JSONObject started = await(cb -> Controls.start(c, source, "global", cb));
    check(
        !started.has("error") && Controls.active,
        "Authenticated app connects and grabs a real gamepad: " + started);
    Thread.sleep(500);
    int logStart = shell("cat /data/local/tmp/thorhaven-fixture.log").length();
    event(1, 304, 1);
    event(1, 305, 1);
    event(1, 304, 0);
    String log = fixtureLog(logStart);
    check(
        log.contains("1 305 1") && !log.contains("\n1 305 0") && !log.contains("\n1 304 1"),
        "A maps to B and shared target stays held until both sources release");
    event(1, 305, 0);
    log = fixtureLog(logStart);
    check(log.contains("1 305 0"), "Shared target releases without stuck button");
    event(3, 0, 32767);
    log = fixtureLog(logStart);
    check(log.contains("3 0 -32768"), "Native stick inversion transforms actual kernel events");
    event(3, 16, -1);
    event(3, 16, 0);
    log = fixtureLog(logStart);
    check(
        log.contains("1 28 1") && log.contains("1 28 0"),
        "Hat D-pad mapping emits keyboard Enter and releases it");
    event(1, 314, 1);
    event(1, 315, 1);
    Thread.sleep(3800);
    check(!Controls.active, "Raw Select plus Start emergency stop releases original device");
    Controls.stop(c);
    Thread.sleep(300);
    event(1, 314, 0);
    event(1, 315, 0);
    JSONObject identity = PadProfile.defaults();
    Controls.save(c, "com.android.settings", identity);
    started = await(cb -> Controls.start(c, source, "global", cb));
    check(!started.has("error"), "Remapping can be restarted after emergency stop");
    Controls.foreground(c, "com.android.settings");
    Thread.sleep(400);
    check(
        Controls.currentProfile.equals("com.android.settings"),
        "Foreground app selects its own mapping profile");
    event(1, 304, 1);
    event(1, 304, 0);
    log = fixtureLog(logStart);
    check(
        log.contains("\n1 304 1") && log.contains("\n1 304 0"),
        "Per-app identity mapping produces its own kernel events");
    synchronized (Controls.class) {
      Controls.close();
    }
    Thread.sleep(4200);
    event(1, 304, 1);
    event(1, 304, 0);
    log = fixtureLog(logStart);
    long observedDeadline = System.currentTimeMillis() + 2500;
    while (!log.contains("SRC 1 304 0") && System.currentTimeMillis() < observedDeadline) {
      Thread.sleep(100);
      log = fixtureLog(logStart);
    }
    check(
        log.contains("SRC 1 304 1") && log.contains("SRC 1 304 0"),
        "Connection loss watchdog releases the physical gamepad without app assistance");
    JSONObject request =
        Controls.nativeRequest(c, "start").put("nonce", "00000000000000000000000000000000");
    JSONObject startedHelper = new JSONObject(Controls.device(request));
    check(!startedHelper.has("error"), "Authentication test helper launches");
    Thread.sleep(300);
    android.net.LocalSocket denied = new android.net.LocalSocket();
    denied.connect(
        new android.net.LocalSocketAddress(
            "thorhaven.pad." + android.os.Process.myUid() + ".00000000000000000000000000000000",
            android.net.LocalSocketAddress.Namespace.ABSTRACT));
    denied.setSoTimeout(2000);
    denied.getOutputStream().write("AUTH wrong-token\n".getBytes("UTF-8"));
    check(
        denied.getInputStream().read() == -1,
        "Incorrect session token is rejected before device access");
    denied.close();
    String quietToken = "11111111111111111111111111111111";
    request = Controls.nativeRequest(c, "start").put("nonce", quietToken);
    startedHelper = new JSONObject(Controls.device(request));
    if (startedHelper.has("error")) throw new Exception(startedHelper.toString());
    Thread.sleep(300);
    android.net.LocalSocket quiet = new android.net.LocalSocket();
    quiet.connect(
        new android.net.LocalSocketAddress(
            "thorhaven.pad." + android.os.Process.myUid() + "." + quietToken,
            android.net.LocalSocketAddress.Namespace.ABSTRACT));
    quiet.setSoTimeout(6000);
    java.io.BufferedReader quietReply =
        new java.io.BufferedReader(new java.io.InputStreamReader(quiet.getInputStream(), "UTF-8"));
    quiet
        .getOutputStream()
        .write(("AUTH " + quietToken + "\nSTART " + source + "\n").getBytes("UTF-8"));
    if (!quietReply.readLine().startsWith("OK") || !quietReply.readLine().startsWith("OK"))
      throw new Exception("Heartbeat test cannot start");
    check(
        quietReply.readLine() == null,
        "Missing heartbeat closes the session and destroys the virtual controller");
    quiet.close();

    changed =
        DeviceControl.apply(
            f,
            new JSONObject()
                .put("joystick_led_light_picker_color", "#FF123456,#FF654321")
                .put("joystick_light_enabled", "0,0")
                .put("led_light_brightness_percent", "0.8"));
    check(
        f.settings.get("joystick_led_light_picker_color").equals("#FF123456,#FF654321")
            && f.settings.get("led_light_brightness_percent").equals("0.8"),
        "RGB color, enable and brightness round-trip through typed backend");
    DeviceControl.restore(f, changed);
    check(
        f.settings.get("led_light_brightness_percent").equals("0.5")
            && f.settings.get("joystick_light_enabled").equals("1,1"),
        "RGB snapshot restores the original values");
    JSONObject uiStatus = DeviceControl.status(f, "AYN Thor fixture");
    MainActivity uiActivity = a;
    runOnMainSync(
        () -> {
          ControlPages.hardware = uiStatus;
          uiActivity.go("Systeem");
          ControlPages.hardware = null;
        });
    check(true, "All supported system controls render with fixture capabilities");
    Controls.stop(c);
    Thread.sleep(300);
    ThorService.instance = oldService;
  }

  void rejects(String key, String value) throws Exception {
    boolean no = false;
    try {
      ExtraFeatures.validate(key, value);
    } catch (Exception e) {
      no = true;
    }
    check(no, "Reject malformed " + key);
  }

  void capture() {
    Bundle result = new Bundle();
    Context c = getTargetContext();
    MainActivity a = null;
    int code = 0;
    try {
      a =
          (MainActivity)
              startActivitySync(
                  new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      ScreenCaptureRuntimeChecks.run(this, c, a);
      result.putString("result", "PASS " + passed + " capture checks\n" + report);
    } catch (Throwable e) {
      code = 1;
      result.putString(
          "result",
          "FAIL after "
              + passed
              + " checks\n"
              + report
              + "\n"
              + android.util.Log.getStackTraceString(e));
    } finally {
      if (a != null) {
        MainActivity done = a;
        runOnMainSync(done::finish);
      }
    }
    finish(code, result);
  }

  void v6() {
    Bundle result = new Bundle();
    Context c = getTargetContext();
    String before = null;
    MainActivity a = null;
    int code = 0;
    try {
      before = Store.backup(c).toString();
      Store.prefs(c).edit().putString("language", "en").commit();
      a =
          (MainActivity)
              startActivitySync(
                  new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      ControlLabChecks.run(this, c, a);
      ScreenLabChecks.run(this, c, a);
      GuideToolsChecks.run(this, c, a);
      ExperimentToolsChecks.run(this, c, a);
      result.putString("result", "PASS " + passed + " checks\n" + report);
    } catch (Throwable e) {
      result.putString(
          "result",
          "FAIL after "
              + passed
              + " checks\n"
              + report
              + "\n"
              + android.util.Log.getStackTraceString(e));
      code = 1;
    } finally {
      ControlLab.stop();
      if (before != null)
        try {
          Store.restore(c, before);
        } catch (Exception ignored) {
        }
      if (a != null) {
        MainActivity done = a;
        runOnMainSync(done::finish);
      }
    }
    finish(code, result);
  }

  void v8() {
    Bundle result = new Bundle();
    Context c = getTargetContext();
    String before = null;
    MainActivity a = null;
    int code = 0;
    try {
      before = Store.backup(c).toString();
      a =
          (MainActivity)
              startActivitySync(
                  new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      RgbToolsChecks.run(this, c, a);
      RgbBundleRuntimeChecks.run(this, c, a);
      result.putString("result", "PASS " + passed + " RGB tools checks\n" + report);
    } catch (Throwable e) {
      code = 1;
      result.putString(
          "result",
          "FAIL after "
              + passed
              + " checks\n"
              + report
              + "\n"
              + android.util.Log.getStackTraceString(e));
    } finally {
      RgbService.stop(c);
      if (before != null)
        try {
          Store.restore(c, before);
        } catch (Exception ignored) {
        }
      if (a != null) {
        MainActivity done = a;
        runOnMainSync(done::finish);
      }
    }
    finish(code, result);
  }

  void v7() {
    Bundle result = new Bundle();
    Context c = getTargetContext();
    String before = null;
    MainActivity a = null;
    int code = 0;
    try {
      before = Store.backup(c).toString();
      a =
          (MainActivity)
              startActivitySync(
                  new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      RgbStudioChecks.run(this, c, a);
      result.putString("result", "PASS " + passed + " RGB checks\n" + report);
    } catch (Throwable e) {
      code = 1;
      result.putString(
          "result",
          "FAIL after "
              + passed
              + " checks\n"
              + report
              + "\n"
              + android.util.Log.getStackTraceString(e));
    } finally {
      RgbService.stop(c);
      if (before != null)
        try {
          Store.restore(c, before);
        } catch (Exception ignored) {
        }
      if (a != null) {
        MainActivity done = a;
        runOnMainSync(done::finish);
      }
    }
    finish(code, result);
  }

  void v5() {
    Bundle result = new Bundle();
    Context c = getTargetContext();
    try {
      AutoBackup.prefs(c).edit().clear().commit();
      File[] priorArchives = new File(c.getFilesDir(), "backup-provider").listFiles();
      if (priorArchives != null) for (File f : priorArchives) f.delete();
      String old = Store.backup(c).toString();
      Store.prefs(c).edit().putString("language", "en").commit();
      MainActivity a =
          (MainActivity)
              startActivitySync(
                  new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      String pkg = "qa.game", id = pkg + ".doc1", imageId = pkg + ".doc2", pdfId = pkg + ".doc3";
      OfflineGuides.importStream(
          c,
          pkg,
          "Original.txt",
          new ByteArrayInputStream("Original guide".getBytes(StandardCharsets.UTF_8)));
      OfflineGuides.importStream(
          c,
          id,
          "Walkthrough.md",
          new ByteArrayInputStream(
              "Één apple\nSecond apple\nİ dotted unicode".getBytes(StandardCharsets.UTF_8)));
      JSONObject meta = OfflineGuides.meta(c, id).put("owner", pkg);
      OfflineGuides.prefs(c).edit().putString(id, meta.toString()).commit();
      Store.prefs(c).edit().putString("guideActive:" + pkg, id).commit();
      check(
          OfflineGuides.active(c, pkg).equals(id) && OfflineGuides.exists(c, pkg),
          "Multiple guides preserve original document and selected guide");
      Bitmap b = Bitmap.createBitmap(80, 50, Bitmap.Config.ARGB_8888);
      b.eraseColor(Color.CYAN);
      ByteArrayOutputStream png = new ByteArrayOutputStream();
      b.compress(Bitmap.CompressFormat.PNG, 100, png);
      b.recycle();
      OfflineGuides.importStream(
          c, imageId, "Map.png", new ByteArrayInputStream(png.toByteArray()));
      JSONObject imageMeta =
          OfflineGuides.meta(c, imageId)
              .put("owner", pkg)
              .put(
                  "markers",
                  new JSONArray()
                      .put(new JSONObject().put("name", "Treasure").put("x", 0.3).put("y", 0.7)));
      ExtraFeatures.validateGuide(imageMeta);
      OfflineGuides.prefs(c).edit().putString(imageId, imageMeta.toString()).commit();
      check(
          OfflineGuides.meta(c, imageId).getString("kind").equals("image"),
          "PNG import validates dimensions and recognizes map");
      boolean bad = false;
      try {
        ExtraFeatures.validateGuide(
            new JSONObject()
                .put(
                    "markers",
                    new JSONArray()
                        .put(new JSONObject().put("name", "bad").put("x", 2).put("y", 0))));
      } catch (Exception e) {
        bad = true;
      }
      check(bad, "Map marker outside normalized coordinates rejected");
      ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
      android.graphics.pdf.PdfDocument pdf = new android.graphics.pdf.PdfDocument();
      try {
        for (int page = 0; page < 2; page++) {
          android.graphics.pdf.PdfDocument.Page pp =
              pdf.startPage(
                  new android.graphics.pdf.PdfDocument.PageInfo.Builder(300, 400, page + 1)
                      .create());
          pp.getCanvas().drawColor(Color.WHITE);
          pdf.finishPage(pp);
        }
        pdf.writeTo(pdfOut);
      } finally {
        pdf.close();
      }
      OfflineGuides.importStream(
          c, pdfId, "PDF chapters", new ByteArrayInputStream(pdfOut.toByteArray()));
      JSONObject pdfMeta =
          OfflineGuides.meta(c, pdfId)
              .put("owner", pkg)
              .put(
                  "bookmarks",
                  new JSONArray().put(new JSONObject().put("name", "Chapter two").put("page", 1)));
      ExtraFeatures.validateGuide(pdfMeta);
      OfflineGuides.prefs(c).edit().putString(pdfId, pdfMeta.toString()).commit();
      bad = false;
      try {
        ExtraFeatures.validateGuide(
            new JSONObject()
                .put("pages", 2)
                .put(
                    "bookmarks",
                    new JSONArray().put(new JSONObject().put("name", "Missing").put("page", 2))));
      } catch (Exception e) {
        bad = true;
      }
      check(bad, "Named PDF bookmark rejects page outside document");
      JSONArray tasks =
          new JSONArray().put(new JSONObject().put("text", "Collect € 日本語").put("done", true));
      ExtraFeatures.save(c, "checklist:" + pkg, tasks.toString());
      JSONArray profiles =
          new JSONArray()
              .put(
                  new JSONObject()
                      .put("name", "Adventure")
                      .put(
                          "data",
                          new JSONObject()
                              .put("volume", 23)
                              .put("brightness", -1)
                              .put("screen", Store.screen(c, false))));
      ExtraFeatures.validate("gameProfiles:" + pkg, profiles.toString());
      ExtraFeatures.save(c, "gameProfiles:" + pkg, profiles.toString());
      runOnMainSync(() -> ExtraFeatures.applyProfile(c, pkg, profiles.optJSONObject(0)));
      check(
          Store.prefs(c).getInt("volume:" + pkg, -1) == 23,
          "Named game profile applies stored app settings");
      rejects("gameProfiles:" + pkg, "[{\"name\":\"bad\",\"data\":{\"shell\":\"id\"}}]");
      rejects("hardwareProfile:" + pkg, "{\"performance_mode\":\"99\",\"fan_mode\":\"4\"}");
      rejects("guideActive:" + pkg, "../../escape");
      rejects("touchTiles", "[{\"code\":3,\"label\":\"Home\"}]");
      rejects("panelOrder", "[\"Favorieten\"]");
      final String[] sent = {""};
      DeviceControl.sendKey(
          command -> {
            sent[0] = command;
            return "";
          },
          new JSONObject().put("code", 96).put("display", 0));
      check(
          sent[0].equals("input gamepad -d 0 keyevent 96"),
          "Touch action uses fixed gamepad command and target display");
      bad = false;
      try {
        DeviceControl.sendKey(
            command -> {
              throw new AssertionError("Should not execute");
            },
            new JSONObject().put("code", 3).put("display", 0));
      } catch (Exception e) {
        bad = true;
      }
      check(bad, "Touch command rejects Home before privileged execution");
      rejects("hardwareProfile:" + pkg, "{\"performance_mode\":\"2\",\"fan_mode\":\"1\"}");
      runOnMainSync(
          () -> {
            try {
              checkHardwareAutomation(c);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      ExtraFeatures.save(c, "touchTiles", TouchControls.defaults().toString());
      JSONArray order = new JSONArray();
      for (int i = ExtraFeatures.CARDS.length - 1; i >= 0; i--) order.put(ExtraFeatures.CARDS[i]);
      ExtraFeatures.save(c, "panelOrder", order.toString());
      final GuidePane[] text = {null}, image = {null};
      runOnMainSync(
          () -> {
            text[0] = new GuidePane(a, id, () -> {});
            a.content.addView(text[0], new LinearLayout.LayoutParams(-1, 600));
          });
      Thread.sleep(700);
      runOnMainSync(
          () -> {
            text[0].find("apple");
          });
      check(text[0].searchAt > 0, "Text search locates first match");
      int first = text[0].searchAt;
      runOnMainSync(
          () -> {
            text[0].find("apple");
          });
      check(text[0].searchAt > first, "Find next advances within guide");
      runOnMainSync(() -> text[0].find("İ"));
      check(true, "Unicode search keeps source offsets valid");
      runOnMainSync(
          () -> {
            text[0].close();
            a.content.removeView(text[0]);
            image[0] = new GuidePane(a, imageId, () -> {});
            a.content.addView(image[0], new LinearLayout.LayoutParams(-1, 600));
          });
      Thread.sleep(700);
      check(image[0].bitmap != null, "Map reader decodes image asynchronously");
      runOnMainSync(
          () -> {
            image[0].zoom = 2;
            image[0].sizeImage();
          });
      check(image[0].image.getLayoutParams().width > 0, "Map zoom resizes image with aspect ratio");
      runOnMainSync(
          () -> {
            image[0].close();
            a.content.removeView(image[0]);
          });
      runOnMainSync(
          () -> {
            LinearLayout panel = QuickPanel.build(a, null);
            int previous = -1;
            for (int i = 0; i < order.length(); i++) {
              for (int j = 0; j < panel.getChildCount(); j++)
                if (order.optString(i).equals(panel.getChildAt(j).getTag())) {
                  if (j < previous) throw new AssertionError("Card order");
                  previous = j;
                }
            }
          });
      check(true, "Quick panel honors reordered card list");
      SessionStats.start(c);
      pendingBackdate(c);
      SessionStats.sample(c);
      SessionStats.stop(c);
      check(
          ExtraFeatures.array(c, "sessions").length() > 0,
          "Session timer saves bounded battery history");
      String backup = Store.backup(c).toString();
      Store.prefs(c).edit().remove("checklist:" + pkg).remove("sessions").commit();
      Store.restore(c, backup);
      check(
          ExtraFeatures.array(c, "checklist:" + pkg)
              .getJSONObject(0)
              .getString("text")
              .equals("Collect € 日本語"),
          "Settings backup preserves checklists and Unicode");
      ByteArrayOutputStream zip = new ByteArrayOutputStream();
      CompleteBackup.write(c, zip);
      OfflineGuides.remove(c, imageId);
      try (CompleteBackup.Prepared prepared =
          CompleteBackup.prepare(c, new ByteArrayInputStream(zip.toByteArray()))) {
        CompleteBackup.restore(c, prepared);
      }
      check(
          OfflineGuides.meta(c, imageId)
              .getJSONArray("markers")
              .getJSONObject(0)
              .getString("name")
              .equals("Treasure"),
          "Complete ZIP restores multiple guides and map markers");
      check(
          OfflineGuides.meta(c, pdfId)
              .getJSONArray("bookmarks")
              .getJSONObject(0)
              .getString("name")
              .equals("Chapter two"),
          "Complete backup preserves named PDF bookmarks");
      check(
          AutoBackup.schedule(c) == android.app.job.JobScheduler.RESULT_SUCCESS,
          "Android accepts persisted automatic backup job");
      android.app.job.JobInfo job =
          c.getSystemService(android.app.job.JobScheduler.class).getPendingJob(AutoBackup.JOB);
      check(
          job != null
              && job.isRequireCharging()
              && job.isRequireDeviceIdle()
              && job.isPersisted()
              && job.getIntervalMillis() == 24L * 3600 * 1000,
          "Backup job requires charging, idle state and daily period");
      c.getSystemService(android.app.job.JobScheduler.class).cancel(AutoBackup.JOB);
      Uri tree = DocumentsContract.buildTreeDocumentUri("nl.thorhaven.backup.test", "root");
      AutoBackup.prefs(c).edit().putString("folder", tree.toString()).commit();
      for (int i = 0; i < 9; i++) {
        String msg = AutoBackup.run(c);
        check(
            msg.startsWith("Backup completed"),
            "Automatic backup archive " + (i + 1) + " is validated: " + msg);
        Thread.sleep(5);
      }
      Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, "root");
      int count;
      try (android.database.Cursor cursor =
          c.getContentResolver()
              .query(
                  children,
                  new String[] {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                  },
                  null,
                  null,
                  null)) {
        count = cursor.getCount();
      }
      check(count == 7, "Automatic backup retention keeps seven archives");
      String savedOrder = Store.prefs(c).getString("panelOrder", "");
      Store.prefs(c).edit().putString("panelOrder", "[\"bad\"]").commit();
      String failed = AutoBackup.run(c);
      Store.prefs(c).edit().putString("panelOrder", savedOrder).commit();
      check(
          failed.startsWith("Backup failed"),
          "Invalid generated backup is rejected before retention pruning");
      try (android.database.Cursor cursor =
          c.getContentResolver()
              .query(
                  children,
                  new String[] {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                  },
                  null,
                  null,
                  null)) {
        check(
            cursor.getCount() == 7,
            "Failed backup removes partial archive and preserves previous seven");
      }
      runOnMainSync(
          () -> {
            for (String page : MainActivity.PAGES) a.go(page);
            a.go("Gidsen");
            GuidePages.selected = pkg;
            a.render();
          });
      check(true, "All pages and expanded guide tools render");
      OfflineGuides.remove(c, pkg);
      OfflineGuides.remove(c, id);
      OfflineGuides.remove(c, imageId);
      OfflineGuides.remove(c, pdfId);
      Store.prefs(c).edit().clear().commit();
      Store.restore(c, old);
      AutoBackup.prefs(c).edit().clear().commit();
      result.putString("stream", report + "\n" + passed + " checks passed\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      result.putString("stream", report + "\nFAILED: " + e);
      finish(Activity.RESULT_CANCELED, result);
    }
  }

  void checkHardwareAutomation(Context c) throws Exception {
    HardwareAutomation.Actions original = HardwareAutomation.actions;
    final java.util.List<String> calls = new java.util.ArrayList<>();
    final java.util.List<java.util.function.Consumer<JSONObject>> callbacks =
        new java.util.ArrayList<>();
    Store.prefs(c)
        .edit()
        .remove("hw:snapshot")
        .putString("hardwareProfile:qa.game", "{\"performance_mode\":\"1\",\"fan_mode\":\"4\"}")
        .putString("hardwareProfile:qa.other", "{\"performance_mode\":\"2\",\"fan_mode\":\"5\"}")
        .commit();
    HardwareAutomation.actions =
        new HardwareAutomation.Actions() {
          public void apply(
              Context context, JSONObject patch, java.util.function.Consumer<JSONObject> cb) {
            calls.add("apply:" + patch.optString("performance_mode"));
            callbacks.add(cb);
          }

          public void restore(Context context, java.util.function.Consumer<JSONObject> cb) {
            calls.add("restore");
            callbacks.add(cb);
          }
        };
    try {
      HardwareAutomation.enabled = true;
      HardwareAutomation.focus(c, "qa.game");
      HardwareAutomation.focus(c, "qa.other");
      check(calls.size() == 1, "Hardware automation serializes rapid foreground changes");
      callbacks.remove(0).accept(new JSONObject());
      check(
          calls.get(1).equals("restore"),
          "Hardware automation restores prior profile before switching");
      callbacks.remove(0).accept(new JSONObject());
      check(
          calls.get(2).equals("apply:2"),
          "Hardware automation applies latest requested profile after restore");
      callbacks.remove(0).accept(new JSONObject());
      HardwareAutomation.enabled = false;
      HardwareAutomation.focus(c, "");
      check(
          calls.get(3).equals("restore"),
          "Disabling automation requests original hardware restoration");
      callbacks.remove(0).accept(new JSONObject());
      HardwareAutomation.enabled = true;
      HardwareAutomation.focus(c, "qa.game");
      callbacks.remove(0).accept(Controls.error("Unsupported firmware"));
      check(
          !HardwareAutomation.enabled && !HardwareAutomation.owned,
          "Unsupported firmware disables automation without retry loop");
    } finally {
      HardwareAutomation.actions = original;
      HardwareAutomation.enabled = false;
      HardwareAutomation.owned = false;
      HardwareAutomation.busy = false;
      HardwareAutomation.desired = "";
      HardwareAutomation.applied = "";
    }
  }

  void pendingBackdate(Context c) {
    SessionStats.pending(c).edit().putLong("start", SystemClock.elapsedRealtime() - 65000).commit();
  }
}
