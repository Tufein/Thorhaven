package nl.thorhaven.app;

import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** RGB tests use generated inputs and recording hardware fixtures, never physical-light claims. */
final class RgbStudioChecks {
  interface Action {
    void run() throws Exception;
  }

  static boolean rejects(Action action) {
    try {
      action.run();
      return false;
    } catch (Exception expected) {
      return true;
    }
  }

  static JSONObject copy(JSONObject source) throws JSONException {
    return new JSONObject(source.toString());
  }

  static void run(SmokeInstrumentation t, Context c, MainActivity a) throws Exception {
    JSONObject defaults = RgbSettings.defaults();
    RgbSettings.validate(defaults.toString());
    t.check(
        defaults.getInt("schema") == 1, "RGB settings default to the supported portable schema");
    t.check(
        rejects(() -> RgbSettings.validate(copy(defaults).put("schema", 2).toString()))
            && rejects(
                () -> RgbSettings.validate(copy(defaults).put("path", "/sys/arbitrary").toString()))
            && rejects(() -> RgbSettings.validate(copy(defaults).put("shell", "id").toString())),
        "RGB settings reject unknown schemas and arbitrary hardware paths or commands");
    t.check(
        rejects(() -> RgbSettings.validate(defaults.toString() + " trailing"))
            && rejects(() -> RgbSettings.validate("[]")),
        "RGB import accepts one complete settings object and rejects trailing data or arrays");
    t.check(
        rejects(() -> RgbSettings.validate(copy(defaults).put("fps", "5").toString()))
            && rejects(() -> RgbSettings.validate(copy(defaults).put("fps", 1.5).toString()))
            && rejects(
                () -> RgbSettings.validate(copy(defaults).put("timerMinutes", -1).toString()))
            && rejects(
                () -> RgbSettings.validate(copy(defaults).put("timerMinutes", 100000).toString())),
        "RGB frame rate and session timer enforce numeric types and finite limits");
    t.check(
        rejects(() -> RgbSettings.validate(copy(defaults).put("followScreen", "true").toString()))
            && rejects(
                () -> RgbSettings.validate(copy(defaults).put("lowBatteryDim", 1).toString()))
            && rejects(
                () ->
                    RgbSettings.validate(copy(defaults).put("screenOffPause", "false").toString())),
        "RGB power options require booleans instead of coercing strings or numbers");
    JSONObject profile = defaults.getJSONObject("profile");
    t.check(
        rejects(
                () -> {
                  JSONObject bad = copy(profile);
                  bad.getJSONObject("left").put("enabled", "true");
                  RgbSettings.validateProfile(bad);
                })
            && rejects(
                () -> {
                  JSONObject bad = copy(profile);
                  bad.getJSONObject("right").put("brightness", 25.0);
                  RgbSettings.validateProfile(bad);
                })
            && rejects(
                () -> {
                  JSONObject bad = copy(profile);
                  bad.getJSONObject("left").put("speedMs", "6000");
                  RgbSettings.validateProfile(bad);
                }),
        "Both RGB zones require actual boolean switches and integer brightness/speed values");
    t.check(
        rejects(
                () -> {
                  JSONObject bad = copy(profile);
                  bad.getJSONObject("left").put("brightness", -1);
                  RgbSettings.validateProfile(bad);
                })
            && rejects(
                () -> {
                  JSONObject bad = copy(profile);
                  bad.getJSONObject("right").put("brightness", 101);
                  RgbSettings.validateProfile(bad);
                })
            && rejects(
                () -> {
                  JSONObject bad = copy(profile);
                  bad.getJSONObject("left").put("speedMs", 1999);
                  RgbSettings.validateProfile(bad);
                })
            && rejects(
                () -> {
                  JSONObject bad = copy(profile);
                  bad.getJSONObject("right").put("speedMs", 20001);
                  RgbSettings.validateProfile(bad);
                }),
        "RGB zones bound brightness to 0–100 and effect periods to 2–20 seconds");
    t.check(
        rejects(
                () -> {
                  JSONObject bad = copy(profile);
                  bad.getJSONObject("left").put("effect", "shell");
                  RgbSettings.validateProfile(bad);
                })
            && rejects(
                () -> {
                  JSONObject bad = copy(profile);
                  bad.getJSONObject("right").put("color", "#FFFFFF;id");
                  RgbSettings.validateProfile(bad);
                })
            && rejects(
                () -> {
                  JSONObject bad = copy(profile);
                  bad.getJSONObject("left").put("secondary", "NaN");
                  RgbSettings.validateProfile(bad);
                })
            && rejects(
                () -> {
                  JSONObject bad = copy(profile);
                  bad.getJSONObject("right").put("path", "/sys/foo");
                  RgbSettings.validateProfile(bad);
                }),
        "RGB profiles restrict effects and colors and reject arbitrary per-zone properties");
    t.check(
        rejects(
            () -> {
              JSONObject bad = copy(profile);
              bad.remove("right");
              RgbSettings.validateProfile(bad);
            }),
        "RGB profiles require both independent zones before they are saved");
    JSONObject portable = copy(defaults);
    JSONObject independent = copy(profile);
    independent
        .getJSONObject("left")
        .put("color", "#FF0000")
        .put("secondary", "#00FF00")
        .put("brightness", 50)
        .put("enabled", true);
    independent
        .getJSONObject("right")
        .put("color", "#0000FF")
        .put("secondary", "#00FFFF")
        .put("brightness", 100)
        .put("enabled", false);
    portable.put("profile", independent);
    JSONObject preset =
        new JSONObject()
            .put("id", "qa_pair")
            .put("name", "Notities 日本語")
            .put("profile", copy(independent));
    preset.getJSONObject("profile").getJSONObject("left").put("color", "#00FF00");
    portable.getJSONArray("presets").put(preset);
    portable.getJSONObject("apps").put("example.rgb.qa", "qa_pair");
    RgbSettings.validate(portable.toString());
    t.check(
        rejects(
            () ->
                RgbSettings.validate(
                    copy(portable)
                        .put("presets", new JSONArray().put(preset).put(preset))
                        .toString())),
        "RGB presets reject duplicate identifiers");
    t.check(
        rejects(
                () -> {
                  JSONObject bad = copy(portable);
                  bad.getJSONArray("presets").getJSONObject(0).put("id", "../escape");
                  RgbSettings.validate(bad.toString());
                })
            && rejects(
                () -> {
                  JSONObject bad = copy(portable);
                  bad.getJSONArray("presets").getJSONObject(0).put("name", "\ncommand");
                  RgbSettings.validate(bad.toString());
                })
            && rejects(
                () -> {
                  JSONObject bad = copy(portable);
                  bad.getJSONArray("presets").getJSONObject(0).put("name", " ");
                  RgbSettings.validate(bad.toString());
                }),
        "Preset identifiers and user names reject traversal, controls and empty names");
    t.check(
        rejects(
                () -> {
                  JSONObject bad = copy(portable);
                  bad.getJSONObject("apps").put("example.other", "missing");
                  RgbSettings.validate(bad.toString());
                })
            && rejects(
                () -> {
                  JSONObject bad = copy(portable);
                  bad.getJSONObject("apps").put("bad;command", "qa_pair");
                  RgbSettings.validate(bad.toString());
                })
            && rejects(
                () -> {
                  JSONObject bad = copy(portable);
                  bad.getJSONObject("apps").put("example.other", true);
                  RgbSettings.validate(bad.toString());
                }),
        "App RGB assignments require validated packages and references to existing preset IDs");
    JSONArray crowded = new JSONArray();
    for (int i = 0; i < 21; i++)
      crowded.put(
          new JSONObject()
              .put("id", "qa_" + i)
              .put("name", "Preset " + i)
              .put("profile", copy(profile)));
    JSONObject apps = new JSONObject();
    for (int i = 0; i < 65; i++) apps.put("example.game" + i, "qa_pair");
    t.check(
        rejects(() -> RgbSettings.validate(copy(portable).put("presets", crowded).toString()))
            && rejects(() -> RgbSettings.validate(copy(portable).put("apps", apps).toString())),
        "Portable RGB data limits presets to twenty and app assignments to sixty-four");
    RgbSettings.save(c, portable);
    String settingsBackup = Store.backup(c).toString();
    Store.prefs(c).edit().remove(RgbSettings.KEY).commit();
    Store.restore(c, settingsBackup);
    JSONObject restored = RgbSettings.load(c);
    t.check(
        restored.getJSONArray("presets").getJSONObject(0).getString("name").equals("Notities 日本語")
            && restored.getJSONObject("apps").getString("example.rgb.qa").equals("qa_pair")
            && !restored.getJSONObject("profile").getJSONObject("right").getBoolean("enabled"),
        "RGB settings backup preserves Unicode preset names, app assignments and separate zone"
            + " switches");
    String untouched = Store.prefs(c).getString(RgbSettings.KEY, "");
    JSONObject malformed = Store.backup(c);
    malformed
        .getJSONObject("data")
        .put("notes:example.rgb.qa", "would change")
        .put(RgbSettings.KEY, copy(portable).put("fps", "wrong").toString());
    String priorNote = Store.prefs(c).getString("notes:example.rgb.qa", "");
    t.check(
        rejects(() -> Store.restore(c, malformed.toString()))
            && untouched.equals(Store.prefs(c).getString(RgbSettings.KEY, ""))
            && priorNote.equals(Store.prefs(c).getString("notes:example.rgb.qa", "")),
        "Malformed RGB import rejects atomically without changing RGB data or neighboring notes");
    ByteArrayOutputStream full = new ByteArrayOutputStream();
    CompleteBackup.write(c, full);
    RgbSettings.save(c, defaults);
    try (CompleteBackup.Prepared prepared =
        CompleteBackup.prepare(c, new ByteArrayInputStream(full.toByteArray()))) {
      CompleteBackup.restore(c, prepared);
    }
    t.check(
        RgbSettings.load(c)
                .getJSONArray("presets")
                .getJSONObject(0)
                .getString("name")
                .equals("Notities 日本語")
            && RgbSettings.load(c)
                .getJSONObject("apps")
                .getString("example.rgb.qa")
                .equals("qa_pair"),
        "Complete ZIP backup includes portable RGB presets and app assignments");
    JSONObject selected = RgbSettings.profile(c, "example.rgb.qa");
    t.check(
        selected.getJSONObject("left").getString("color").equals("#00FF00")
            && !selected.getJSONObject("right").getBoolean("enabled"),
        "Per-app RGB selection resolves its assigned preset with independent zone settings");
    JSONObject fallback = RgbSettings.profile(c, "example.unassigned");
    t.check(
        fallback.getJSONObject("left").getString("color").equals("#FF0000"),
        "Unassigned apps use the global RGB profile");
    engine(t, defaults, independent);
    hardware(t);
    sessions(t, c, portable);
    ui(t, c, a, portable);
    services(t, c, portable);
  }

  static void engine(SmokeInstrumentation t, JSONObject defaults, JSONObject independent)
      throws Exception {
    JSONObject options = copy(defaults).put("lowBatteryDim", false).put("followScreen", false);
    RgbEngine.Frame f = RgbEngine.render(independent, 0, 80, false, 100, options);
    t.check(
        f.leftEnabled && !f.rightEnabled && f.left == 0x800000 && f.right == 0,
        "Shared RGB engine applies separate zone brightness and disables the other zone"
            + " completely");
    JSONObject both = copy(independent);
    both.getJSONObject("left").put("brightness", 100);
    both.getJSONObject("right").put("enabled", true).put("brightness", 100);
    f = RgbEngine.render(both, 0, 100, false, 50, copy(options).put("followScreen", true));
    t.check(
        f.left == 0x800000 && f.right == 0x000080,
        "Follow-screen option scales both independently colored zones using the measured screen"
            + " level");
    RgbEngine.Frame zero =
        RgbEngine.render(both, 0, 50, false, -50, copy(options).put("followScreen", true));
    RgbEngine.Frame high =
        RgbEngine.render(both, 0, 50, false, 250, copy(options).put("followScreen", true));
    t.check(
        zero.left == 0 && zero.right == 0 && high.left == 0xff0000 && high.right == 0x0000ff,
        "Screen-brightness scaling clamps out-of-range measurements instead of overflowing RGB"
            + " channels");
    RgbEngine.Frame low =
        RgbEngine.render(both, 0, 19, false, 100, copy(options).put("lowBatteryDim", true));
    RgbEngine.Frame above =
        RgbEngine.render(both, 0, 20, false, 100, copy(options).put("lowBatteryDim", true));
    RgbEngine.Frame plugged =
        RgbEngine.render(both, 0, 19, true, 100, copy(options).put("lowBatteryDim", true));
    t.check(
        low.left == 0x4d0000 && above.left == 0xff0000 && plugged.left == 0xff0000,
        "Low-battery dimming uses the actual below-20-percent threshold and stops dimming while"
            + " charging");
    JSONObject animation = copy(both);
    animation.getJSONObject("left").put("effect", "breathe").put("speedMs", 6000);
    int breathStart = RgbEngine.render(animation, 0, 100, false, 100, options).left;
    int breathPeak = RgbEngine.render(animation, 3000, 100, false, 100, options).left;
    int breathRepeat = RgbEngine.render(animation, 6000, 100, false, 100, options).left;
    t.check(
        breathStart == 0x260000 && breathPeak == 0xff0000 && breathRepeat == breathStart,
        "Breathing fades through a slow six-second period and repeats at its original brightness");
    animation.getJSONObject("left").put("effect", "cycle").put("secondary", "#00FF00");
    int cycleStart = RgbEngine.render(animation, 0, 100, false, 100, options).left;
    int cycleQuarter = RgbEngine.render(animation, 1500, 100, false, 100, options).left;
    int cyclePeak = RgbEngine.render(animation, 3000, 100, false, 100, options).left;
    int cycleRepeat = RgbEngine.render(animation, 6000, 100, false, 100, options).left;
    t.check(
        cycleStart == 0xff0000
            && Math.abs((cycleQuarter >> 16 & 255) - 128) <= 1
            && Math.abs((cycleQuarter >> 8 & 255) - 128) <= 1
            && cyclePeak == 0x00ff00
            && cycleRepeat == cycleStart,
        "Slow color cycle blends base and secondary colors and returns after a complete period");
    animation.getJSONObject("left").put("effect", "rainbow");
    int rainbowStart = RgbEngine.render(animation, 0, 100, false, 100, options).left;
    int rainbowHalf = RgbEngine.render(animation, 3000, 100, false, 100, options).left;
    int rainbowRepeat = RgbEngine.render(animation, 6000, 100, false, 100, options).left;
    t.check(
        rainbowStart == 0xff0000 && rainbowHalf == 0x00ffff && rainbowRepeat == rainbowStart,
        "Rainbow phase rotates the chosen base hue without changing its cycle period");
    animation.getJSONObject("left").put("effect", "pulse");
    t.check(
        RgbEngine.render(animation, 0, 100, false, 100, options).left == 0
            && RgbEngine.render(animation, 2100, 100, false, 100, options).left == 0xff0000
            && RgbEngine.render(animation, 5000, 100, false, 100, options).left == 0,
        "Pulse has a smooth bright midpoint and an explicit dark tail instead of a rapid flash"
            + " loop");
    animation.getJSONObject("left").put("effect", "battery");
    int empty = RgbEngine.render(animation, 0, 0, false, 100, options).left;
    int middle = RgbEngine.render(animation, 0, 50, false, 100, options).left;
    int full = RgbEngine.render(animation, 0, 100, false, 100, options).left;
    t.check(
        empty == 0xff0000 && middle == 0xffff00 && full == 0x00ff00,
        "Battery color follows actual empty, half-full and full measurements");
    t.check(
        RgbEngine.render(animation, 0, -1, false, 100, options).left == 0
            && RgbEngine.render(animation, 0, 101, false, 100, options).left == 0,
        "Unknown or invalid battery measurements produce a dark battery indicator");
    animation
        .getJSONObject("left")
        .put("effect", "charging")
        .put("color", "#FF0000")
        .put("secondary", "#0000FF");
    t.check(
        RgbEngine.render(animation, 3000, 0, true, 100, options).left == 0xff0000
            && RgbEngine.render(animation, 3000, 50, true, 100, options).left == 0x800080
            && RgbEngine.render(animation, 3000, 100, true, 100, options).left == 0x0000ff,
        "Charging color blends the chosen colors using the actual battery percentage");
    t.check(
        RgbEngine.render(animation, 3000, 50, false, 100, options).left == 0
            && RgbEngine.render(animation, 3000, -1, true, 100, options).left == 0,
        "Charging effect stays dark while unplugged or battery state is unknown");
    for (String effect : RgbSettings.EFFECTS) {
      animation.getJSONObject("left").put("effect", effect);
      animation.getJSONObject("right").put("effect", effect);
      for (long time : new long[] {-6000, -1, 0, 1500, 3000, 6000, Long.MAX_VALUE})
        for (int level : new int[] {-1, 0, 5, 15, 50, 100, 200}) {
          RgbEngine.Frame frame = RgbEngine.render(animation, time, level, true, 100, options);
          if (frame.left < 0 || frame.left > 0xffffff || frame.right < 0 || frame.right > 0xffffff)
            throw new Exception("RGB channel overflow for " + effect);
        }
    }
    t.check(
        true,
        "Every RGB effect remains within both-zone channel bounds across negative and very large"
            + " phases");
    JSONObject frame = new RgbEngine.Frame(true, 0xff0000, false, 0xff).toJson();
    t.check(
        rejects(() -> RgbEngine.Frame.fromJson(copy(frame).put("leftEnabled", "true")))
            && rejects(() -> RgbEngine.Frame.fromJson(copy(frame).put("left", 1.0)))
            && rejects(() -> RgbEngine.Frame.fromJson(copy(frame).put("right", 0x1000000)))
            && rejects(() -> RgbEngine.Frame.fromJson(copy(frame).put("node", "/sys/arbitrary"))),
        "Typed frame parser rejects coercions, channel overflow and arbitrary hardware properties");
    RgbEngine.Frame parsed = RgbEngine.Frame.fromJson(frame);
    t.check(
        parsed.equals(new RgbEngine.Frame(true, 0xff0000, false, 0))
            && parsed.right == 0
            && parsed.hashCode() == new RgbEngine.Frame(true, 0xff0000, false, 0).hashCode(),
        "Frame round-trip keeps disabled zones dark and supports deterministic change"
            + " deduplication");
  }

  static JSONObject baseline() throws Exception {
    return new JSONObject()
        .put("joystick_light_enabled", "1,0")
        .put("joystick_led_light_picker_color", "#FF123456,#FF654321")
        .put("led_light_brightness_percent", "0.5");
  }

  static final class FrameRunner implements DeviceControl.Runner {
    final JSONObject stock;
    final List<String> commands = new ArrayList<>();
    boolean nodes = true, partial, noCreate = true;

    FrameRunner() throws Exception {
      stock = baseline();
    }

    public String run(String command) throws Exception {
      commands.add(command);
      if (command.equals("dd --help")) return noCreate ? "nocreat notrunc" : "legacy dd";
      if (command.startsWith("settings get system ")) return stock.getString(command.substring(20));
      if (command.contains("THORHAVEN_RGB_NODES_OK")) return nodes ? "THORHAVEN_RGB_NODES_OK" : "";
      if (command.contains("THORHAVEN_RGB_FRAME_OK"))
        return partial ? "THORHAVEN_RGB_PARTIAL_2" : "THORHAVEN_RGB_FRAME_OK";
      throw new AssertionError("Unexpected RGB command: " + command);
    }
  }

  static void hardware(SmokeInstrumentation t) throws Exception {
    JSONObject original = baseline();
    RgbHardware.validateBaseline(original);
    JSONObject spaced =
        new JSONObject()
            .put(RgbHardware.ENABLED, " 1 , 0 ")
            .put(RgbHardware.COLOR, " #80112233 , #445566 ")
            .put(RgbHardware.BRIGHTNESS, " .5 ");
    RgbHardware.validateBaseline(spaced);
    RgbEngine.Frame stock = RgbHardware.stockFrame(spaced);
    t.check(
        stock.leftEnabled && !stock.rightEnabled && stock.left == 0x09111a && stock.right == 0,
        "Stock RGB recovery accepts supported color formats and whitespace and applies actual"
            + " enable/brightness values");
    t.check(
        rejects(
                () ->
                    RgbHardware.validateBaseline(copy(original).put(RgbHardware.ENABLED, "1;id,0")))
            && rejects(
                () ->
                    RgbHardware.validateBaseline(
                        copy(original).put(RgbHardware.COLOR, "#FFFFFFFF,$(id)")))
            && rejects(
                () ->
                    RgbHardware.validateBaseline(copy(original).put(RgbHardware.BRIGHTNESS, "NaN")))
            && rejects(
                () ->
                    RgbHardware.validateBaseline(copy(original).put(RgbHardware.BRIGHTNESS, "1.1")))
            && rejects(
                () -> RgbHardware.validateBaseline(copy(original).put("node", "/sys/escape"))),
        "Recovery baseline rejects executable values, non-finite brightness, out-of-range values"
            + " and unknown keys");
    t.check(
        rejects(
                () -> {
                  JSONObject q = copy(original);
                  q.remove(RgbHardware.COLOR);
                  RgbHardware.validateBaseline(q);
                })
            && rejects(
                () -> RgbHardware.validateBaseline(copy(original).put(RgbHardware.ENABLED, true))),
        "Recovery requires all three real stock settings as strings before a hardware write");
    FrameRunner runner = new FrameRunner();
    JSONObject capability = DeviceControl.rgbStatus(runner, "AYN Thor fixture");
    t.check(
        capability.getBoolean("available")
            && capability.getJSONObject("baseline").getString(RgbHardware.ENABLED).equals("1,0")
            && runner.commands.size() == 5
            && runner.commands.stream()
                .noneMatch(s -> s.contains("settings put") || s.contains(" > ")),
        "Typed RGB capability check probes all four nodes and reads the actual stock baseline"
            + " without changing it");
    String probe = runner.commands.get(0);
    for (String path : DeviceControl.RGB_PATHS)
      if (!probe.contains("test -f " + path) || !probe.contains("test -w " + path))
        throw new Exception("Missing RGB capability check " + path);
    t.check(
        true,
        "Both sticks require existing writable enable and brightness nodes before RGB capability is"
            + " reported");
    runner.commands.clear();
    JSONObject request =
        new JSONObject()
            .put("op", "rgbFrame")
            .put("frame", new RgbEngine.Frame(true, 0x112233, true, 0x445566).toJson());
    DeviceControl.rgbFrame(runner, request);
    String write = runner.commands.get(2);
    t.check(
        runner.commands.size() == 3
            && write.contains("'1-17:34:51:255'")
            && write.contains("'2-17:34:51:255'")
            && write.contains("'1-68:85:102:255'")
            && write.contains("'2-68:85:102:255'"),
        "RGB frame writes both physical zones of both sticks using their distinct selected colors");
    java.util.regex.Matcher paths =
        java.util.regex.Pattern.compile("dd of=(/sys/[A-Za-z0-9_/]+)").matcher(write);
    int writes = 0;
    while (paths.find()) {
      if (!Arrays.asList(DeviceControl.RGB_PATHS).contains(paths.group(1)))
        throw new Exception("Arbitrary RGB path");
      writes++;
    }
    t.check(
        writes == 6
            && !write.contains("settings put")
            && !write.contains("mkdir")
            && !write.contains("performance_mode")
            && !write.contains("fan_mode")
            && write.contains("conv=nocreat,notrunc")
            && !write.contains(" > /sys/"),
        "Typed RGB transport makes exactly six fixed-node writes without changing stock or"
            + " performance settings");
    runner.commands.clear();
    JSONObject off =
        new JSONObject()
            .put("op", "rgbFrame")
            .put("frame", new RgbEngine.Frame(false, 0xffffff, false, 0xffffff).toJson());
    DeviceControl.rgbFrame(runner, off);
    write = runner.commands.get(2);
    t.check(
        write.contains("'0' | dd of=" + DeviceControl.RGB_PATHS[0])
            && write.contains("'0' | dd of=" + DeviceControl.RGB_PATHS[2])
            && write.contains("'1-0:0:0:255'")
            && write.contains("'2-0:0:0:255'"),
        "All-off frame disables both sticks and writes black to every physical zone");
    runner.commands.clear();
    t.check(
        rejects(() -> DeviceControl.rgbFrame(runner, copy(request).put("path", "/sys/other")))
            && rejects(() -> DeviceControl.rgbFrame(runner, copy(request).put("op", "shell")))
            && rejects(
                () -> {
                  JSONObject q = copy(request);
                  q.getJSONObject("frame").put("leftEnabled", "true");
                  DeviceControl.rgbFrame(runner, q);
                })
            && runner.commands.isEmpty(),
        "Invalid RGB operations or frame types reject before any privileged command executes");
    runner.nodes = false;
    t.check(
        rejects(() -> DeviceControl.rgbFrame(runner, request)) && runner.commands.size() == 1,
        "Missing or unwritable RGB nodes refuse a frame before the first write");
    runner.nodes = true;
    runner.noCreate = false;
    runner.commands.clear();
    t.check(
        rejects(() -> DeviceControl.rgbFrame(runner, request)) && runner.commands.size() == 2,
        "Firmware without a no-create node writer refuses RGB changes before any node can be"
            + " created or truncated");
    runner.noCreate = true;
    runner.partial = true;
    runner.commands.clear();
    String failure = "";
    try {
      DeviceControl.rgbFrame(runner, request);
    } catch (Exception expected) {
      failure = expected.getMessage();
    }
    t.check(
        failure.contains("partial") && failure.contains("THORHAVEN_RGB_PARTIAL_2"),
        "A partial hardware write is surfaced with its failing stage instead of being reported as"
            + " success");
    t.check(
        rejects(() -> RgbHardware.writeNode("/data/local/tmp/rgb-arbitrary", "1")),
        "Direct writer rejects a path outside the fixed Thor RGB allowlist before opening any"
            + " file");
  }

  static class FakeBackend implements RgbSession.Backend {
    final Context c;
    JSONObject stock;
    JSONObject restored;
    final List<RgbEngine.Frame> frames = Collections.synchronizedList(new ArrayList<>());
    final List<String> threads = Collections.synchronizedList(new ArrayList<>());
    volatile boolean failWrite, failRestore, failProbe, available = true;
    volatile int probes, restorations;

    FakeBackend(Context c) throws Exception {
      this.c = c;
      stock = baseline();
    }

    public RgbHardware.Probe probe(Context context) throws Exception {
      probes++;
      if (failProbe) throw new IOException("Fixture probe failure");
      return new RgbHardware.Probe(available, available, "RGB recording fixture", copy(stock));
    }

    public void write(Context context, RgbEngine.Frame frame) throws Exception {
      if (!RgbSession.recovery(c).contains("baseline"))
        throw new AssertionError("RGB recovery must be saved before first write");
      threads.add(Thread.currentThread().getName());
      if (failWrite) throw new IOException("Fixture RGB write failure");
      frames.add(frame);
    }

    public void restore(Context context, JSONObject baseline) throws Exception {
      restorations++;
      threads.add(Thread.currentThread().getName());
      if (failRestore) throw new IOException("Fixture RGB restore failure");
      restored = copy(baseline);
    }
  }

  static void sessions(SmokeInstrumentation t, Context c, JSONObject portable) throws Exception {
    if (RgbSession.recovery(c).contains("baseline"))
      throw new Exception(
          "RGB QA requires no pending recovery record; restore device lighting before running v7"
              + " QA");
    FakeBackend backend = new FakeBackend(c);
    RgbSession session = new RgbSession(c, backend, 1000);
    try {
      t.check(
          backend.frames.isEmpty() && RgbSession.recovery(c).contains("baseline"),
          "RGB session persists validated recovery settings before sending its first frame");
      JSONObject backup = Store.backup(c);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      CompleteBackup.write(c, bytes);
      try (CompleteBackup.Prepared prepared =
          CompleteBackup.prepare(c, new ByteArrayInputStream(bytes.toByteArray()))) {
        t.check(
            !backup.toString().contains("rgb-recovery")
                && !backup.getJSONObject("data").has("baseline")
                && !prepared.manifest.toString().contains("rgb-recovery")
                && prepared
                    .manifest
                    .getJSONObject("settings")
                    .getJSONObject("data")
                    .has(RgbSettings.KEY),
            "Settings and complete backups include RGB profiles but exclude machine-specific"
                + " recovery/session state");
      }
      JSONObject options = copy(portable).put("lowBatteryDim", false);
      boolean continuing = session.tick(options, "", 1000, true, 100, false, 100);
      session.tick(options, "", 1100, true, 100, false, 100);
      t.check(
          continuing
              && backend.frames.size() == 1
              && backend.frames.get(0).left == 0x800000
              && backend.frames.get(0).right == 0,
          "Static RGB session deduplicates identical frames and keeps independent disabled zones"
              + " dark");
      session.tick(options, "example.rgb.qa", 1200, true, 100, false, 100);
      t.check(
          backend.frames.size() == 2 && backend.frames.get(1).left == 0x008000,
          "Foreground app selection switches the running session to its assigned preset");
      session.tick(options, "example.rgb.qa", 1300, false, 100, false, 100);
      session.tick(options, "example.rgb.qa", 2300, false, 100, false, 100);
      t.check(
          session.paused
              && backend.frames.size() == 3
              && !session.last.leftEnabled
              && !session.last.rightEnabled
              && session.last.left == 0
              && session.last.right == 0,
          "Screen-off pause sends one all-black disabled frame and avoids repeated off-screen"
              + " writes");
      session.tick(options, "example.rgb.qa", 2400, true, 100, false, 100);
      t.check(
          !session.paused && backend.frames.size() == 4 && session.last.left == 0x008000,
          "Screen-on resumes the selected RGB profile without restarting or losing its session");
      options.put("screenOffPause", false);
      session.tick(options, "", 2500, false, 100, false, 100);
      t.check(
          !session.paused && session.last.left == 0x800000,
          "Disabling screen-off pause explicitly lets the selected profile continue while screens"
              + " are off");
      options.put("timerMinutes", 5);
      int beforeExpiry = backend.frames.size();
      t.check(
          !session.tick(options, "", 301000, false, 100, false, 100)
              && backend.frames.size() == beforeExpiry,
          "Session timer expires during screen-off time without sending another RGB frame");
      backend.stock.put("joystick_light_enabled", "0,1");
      backend.stock.put("joystick_led_light_picker_color", "#FF102030,#FF405060");
      t.check(
          session.close("QA stopped")
              && session.closed
              && backend.restorations == 1
              && backend.restored.getString("joystick_light_enabled").equals("0,1")
              && !RgbSession.recovery(c).contains("baseline"),
          "Stopping restores current AYN settings changed during the session and clears its"
              + " recovery record");
      int stoppedFrames = backend.frames.size();
      t.check(
          !session.tick(portable, "", 310000, true, 100, false, 100)
              && session.close("QA repeat")
              && backend.restorations == 1
              && backend.frames.size() == stoppedFrames,
          "Closed RGB sessions never send new frames or restore twice");
    } finally {
      if (!session.closed) session.close("QA cleanup");
      RgbSession.recovery(c).edit().remove("baseline").commit();
    }
    FakeBackend unavailable = new FakeBackend(c);
    unavailable.available = false;
    t.check(
        rejects(() -> new RgbSession(c, unavailable, 0))
            && unavailable.frames.isEmpty()
            && !RgbSession.recovery(c).contains("baseline"),
        "Unsupported RGB hardware fails before journaling or changing any lights");
    FakeBackend invalid = new FakeBackend(c);
    invalid.stock.put("joystick_light_enabled", "1;id,0");
    t.check(
        rejects(() -> new RgbSession(c, invalid, 0))
            && !RgbSession.recovery(c).contains("baseline"),
        "Invalid stock RGB recovery settings reject before the session starts");
    FakeBackend broken = new FakeBackend(c);
    broken.failWrite = true;
    RgbSession failure = new RgbSession(c, broken, 0);
    try {
      t.check(
          rejects(() -> failure.tick(portable, "", 0, true, 50, false, 100))
              && failure.last == null
              && broken.frames.isEmpty()
              && failure.close("Write failed")
              && !RgbSession.recovery(c).contains("baseline"),
          "RGB write failures remain uncommitted and the saved stock baseline restores cleanly");
    } finally {
      if (!failure.closed) failure.close("QA cleanup");
      RgbSession.recovery(c).edit().remove("baseline").commit();
    }
    FakeBackend failedRestore = new FakeBackend(c);
    RgbSession pending = new RgbSession(c, failedRestore, 0);
    failedRestore.failRestore = true;
    try {
      t.check(
          !pending.close("Restore failed")
              && pending.closed
              && RgbSession.recovery(c).contains("baseline")
              && rejects(() -> new RgbSession(c, new FakeBackend(c), 0)),
          "Restoration failure retains recovery settings and blocks a new session from overwriting"
              + " them");
    } finally {
      RgbSession.recovery(c).edit().remove("baseline").commit();
    }
    FakeBackend lostProbe = new FakeBackend(c);
    RgbSession fallback = new RgbSession(c, lostProbe, 0);
    lostProbe.failProbe = true;
    try {
      t.check(
          fallback.close("Probe disconnected")
              && lostProbe.restored.toString().equals(baseline().toString()),
          "Stopping after a disconnected stock probe still restores the originally saved RGB"
              + " baseline");
    } finally {
      RgbSession.recovery(c).edit().remove("baseline").commit();
    }
  }

  static boolean buttonNamed(View view, String name) {
    if (view instanceof Button && ((Button) view).getText().toString().equals(name)) return true;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        if (buttonNamed(group.getChildAt(i), name)) return true;
    }
    return false;
  }

  static <T extends View> List<T> find(View root, Class<T> type) {
    List<T> out = new ArrayList<>();
    if (type.isInstance(root)) out.add(type.cast(root));
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) out.addAll(find(group.getChildAt(i), type));
    }
    return out;
  }

  static void ui(SmokeInstrumentation t, Context c, MainActivity a, JSONObject portable)
      throws Exception {
    RgbSettings.save(c, copy(portable));
    RgbStudio.savePreset(c, "Links 日本語");
    JSONArray saved = RgbSettings.load(c).getJSONArray("presets");
    String created = saved.getJSONObject(saved.length() - 1).getString("id");
    RgbStudio.assign(c, "example.ui.rgb", created);
    t.check(
        RgbSettings.load(c).getJSONObject("apps").getString("example.ui.rgb").equals(created),
        "UI helpers save a user preset and assign it using stable validated identifiers");
    RgbStudio.deletePreset(c, created);
    t.check(
        !RgbSettings.load(c).getJSONObject("apps").has("example.ui.rgb")
            && RgbSettings.load(c).getJSONArray("presets").length() == 1,
        "Deleting a preset also removes its app assignments while preserving other presets");
    String untouched = Store.prefs(c).getString(RgbSettings.KEY, "");
    t.check(
        rejects(() -> RgbStudio.assign(c, "example.ui.rgb", "missing"))
            && untouched.equals(Store.prefs(c).getString(RgbSettings.KEY, "")),
        "UI assignment helpers reject unknown presets without partially changing settings");
    for (int i = 0; i < RgbStudio.BUILTINS.length; i++)
      RgbSettings.validateProfile(RgbStudio.builtIn(i));
    JSONObject off = RgbStudio.builtIn(8);
    t.check(
        !off.getJSONObject("left").getBoolean("enabled")
            && !off.getJSONObject("right").getBoolean("enabled")
            && rejects(() -> RgbStudio.builtIn(-1)),
        "Built-in RGB styles all validate and the Off style disables both independent lights");
    JSONObject editable = copy(portable).getJSONObject("profile");
    final boolean[] sideChecks = {false};
    t.runOnMainSync(
        () -> {
          LinearLayout left = Ui.col(a), right = Ui.col(a);
          RgbStudio.sideEditor(a, left, editable.optJSONObject("left"), "Linker licht");
          RgbStudio.sideEditor(a, right, editable.optJSONObject("right"), "Rechter licht");
          List<Switch> leftSwitch = find(left, Switch.class),
              rightSwitch = find(right, Switch.class);
          List<SeekBar> leftSliders = find(left, SeekBar.class),
              rightSliders = find(right, SeekBar.class);
          leftSwitch.get(0).setChecked(false);
          rightSwitch.get(0).setChecked(true);
          sideChecks[0] =
              !editable.optJSONObject("left").optBoolean("enabled")
                  && editable.optJSONObject("right").optBoolean("enabled")
                  && leftSliders.get(0).getMax() == 100
                  && rightSliders.get(0).getMax() == 100
                  && leftSliders.get(0).getProgress() == 50
                  && rightSliders.get(0).getProgress() == 100
                  && find(left, EditText.class).get(0).getText().toString().equals("#FF0000")
                  && find(right, EditText.class).get(0).getText().toString().equals("#0000FF");
        });
    t.check(
        sideChecks[0],
        "RGB side editors independently control enable, color and brightness for both lights");
    MainActivity lower = null;
    try {
      android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
      options.setLaunchDisplayId(Store.screen(c, true));
      lower =
          (MainActivity)
              t.startActivitySync(
                  new Intent(c, MainActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
                  options.toBundle());
      final MainActivity bottom = lower;
      final boolean[] rendered = {false, false, false, false};
      int index = 0;
      for (String language : new String[] {"nl", "en"}) {
        Store.prefs(c).edit().putString("language", language).commit();
        final int offset = index;
        t.runOnMainSync(
            () -> {
              a.go("RGB Studio");
              bottom.go("RGB Studio");
              rendered[offset] = buttonNamed(a.getWindow().getDecorView(), "Notities 日本語");
              rendered[offset + 1] = buttonNamed(bottom.getWindow().getDecorView(), "Notities 日本語");
            });
        index += 2;
      }
      t.check(
          rendered[0]
              && rendered[1]
              && rendered[2]
              && rendered[3]
              && bottom.getDisplay().getDisplayId() == Store.screen(c, true)
              && Language.text(c, "Notities").equals("Notes"),
          "Dutch and English RGB pages render on both real displays and preserve exact user preset"
              + " names");
    } finally {
      if (lower != null) {
        MainActivity done = lower;
        t.runOnMainSync(done::finish);
      }
      t.runOnMainSync(() -> a.go("RGB Studio"));
    }
  }

  static void await(BooleanSupplier condition, int timeout, String message) throws Exception {
    long until = SystemClock.elapsedRealtime() + timeout;
    while (SystemClock.elapsedRealtime() < until) {
      if (condition.getAsBoolean()) return;
      Thread.sleep(50);
    }
    throw new Exception(message + " (bounded wait " + timeout + " ms)");
  }

  static boolean containsColor(List<RgbEngine.Frame> frames, int color) {
    synchronized (frames) {
      for (RgbEngine.Frame f : frames) if (f.left == color) return true;
    }
    return false;
  }

  static boolean latestFrameIs(List<RgbEngine.Frame> frames, RgbEngine.Frame expected) {
    synchronized (frames) {
      return !frames.isEmpty() && frames.get(frames.size() - 1).equals(expected);
    }
  }

  static void services(SmokeInstrumentation t, Context c, JSONObject portable) throws Exception {
    if (RgbService.instance != null)
      throw new Exception("RGB QA requires a stopped lighting service");
    RgbSession.Backend original = RgbService.backend;
    FakeBackend backend = new FakeBackend(c);
    try {
      RgbSettings.save(c, copy(portable).put("lowBatteryDim", false));
      RgbService.backend = backend;
      t.runOnMainSync(() -> RgbService.start(c));
      await(
          () -> RgbService.instance != null && backend.frames.size() >= 1,
          8000,
          "Injected RGB service did not start its first finite worker frame");
      RgbService active = RgbService.instance;
      t.check(
          active.worker != null
              && active.session != null
              && !active.session.closed
              && backend.threads.stream().allMatch(n -> n.contains("Thorhaven-RGB")),
          "Actual foreground RGB service performs validated frame work on its dedicated background"
              + " thread");
      RgbService.focus(c, "example.rgb.qa");
      await(
          () -> containsColor(backend.frames, 0x008000),
          5000,
          "Foreground RGB service did not use the assigned app preset");
      t.check(
          true,
          "Foreground app notification changes the actual running RGB service to its assigned"
              + " preset");
      RgbEngine.Frame global = new RgbEngine.Frame(true, 0x800000, false, 0);
      t.runOnMainSync(() -> RgbService.focus(c, ""));
      await(
          () -> active.foreground.isEmpty() && latestFrameIs(backend.frames, global),
          5000,
          "Clearing the foreground app did not restore the RGB service's global profile");
      t.check(
          active.foreground.isEmpty() && latestFrameIs(backend.frames, global),
          "Clearing foreground app uses the global profile in the actual running RGB service");
      int beforeStop = backend.frames.size();
      t.runOnMainSync(() -> RgbService.stop(c));
      await(
          () -> RgbService.instance == null && active.session.closed && backend.restorations == 1,
          7000,
          "Foreground RGB service did not stop and restore its saved baseline");
      Thread.sleep(700);
      t.check(
          backend.frames.size() == beforeStop && !RgbSession.recovery(c).contains("baseline"),
          "Stopping the real RGB service clears recovery state and prevents later queued frames");
      FakeBackend stopOnly = new FakeBackend(c);
      RgbService.backend = stopOnly;
      t.runOnMainSync(
          () ->
              c.startForegroundService(new Intent(c, RgbService.class).setAction(RgbService.STOP)));
      Thread.sleep(500);
      await(
          () -> RgbService.instance == null,
          5000,
          "Stop-only RGB intent did not close its foreground service");
      t.check(
          stopOnly.probes == 0 && stopOnly.frames.isEmpty() && stopOnly.restorations == 0,
          "Stop-only notification intent never creates a new hardware session or writes a frame");
      BlockingBackend blocked = new BlockingBackend(c);
      RgbService.backend = blocked;
      t.runOnMainSync(() -> RgbService.start(c));
      if (!blocked.entered.await(5, TimeUnit.SECONDS))
        throw new Exception("RGB worker did not enter the controlled in-flight write");
      RgbService inflight = RgbService.instance;
      t.runOnMainSync(() -> RgbService.stop(c));
      blocked.release.countDown();
      await(
          () -> RgbService.instance == null && blocked.restorations == 1,
          7000,
          "Stopping during an in-flight RGB frame failed to restore after that frame completed");
      Thread.sleep(700);
      t.check(
          inflight.session.closed
              && blocked.frames.size() == 1
              && !RgbSession.recovery(c).contains("baseline"),
          "In-flight stop waits for its bounded current write then restores once and cancels all"
              + " later frames");
      FakeBackend errors = new FakeBackend(c);
      errors.failWrite = true;
      RgbService.backend = errors;
      t.runOnMainSync(() -> RgbService.start(c));
      await(
          () -> errors.probes >= 1 && errors.restorations == 1 && RgbService.instance == null,
          7000,
          "RGB service write error did not stop and restore automatically");
      t.check(
          errors.frames.isEmpty() && !RgbSession.recovery(c).contains("baseline"),
          "Actual RGB service stops automatically after a write error and restores its recorded"
              + " stock settings");
      FakeBackend rapid = new FakeBackend(c);
      RgbService.backend = rapid;
      t.runOnMainSync(
          () -> {
            RgbService.start(c);
            RgbService.stop(c);
          });
      Thread.sleep(700);
      await(
          () -> RgbService.instance == null,
          5000,
          "Rapid RGB start/stop left a late foreground service active");
      t.check(
          rapid.frames.isEmpty()
              && rapid.probes == 0
              && !RgbSession.recovery(c).contains("baseline"),
          "Stop issued before service creation cancels a queued RGB start and prevents a late"
              + " hardware session");
    } finally {
      if (RgbService.instance != null) {
        t.runOnMainSync(() -> RgbService.stop(c));
        await(() -> RgbService.instance == null, 7000, "RGB QA service cleanup did not finish");
      }
      RgbService.backend = original;
      RgbSession.recovery(c).edit().remove("baseline").commit();
    }
  }

  static final class BlockingBackend extends FakeBackend {
    final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);

    BlockingBackend(Context c) throws Exception {
      super(c);
    }

    @Override
    public void write(Context c, RgbEngine.Frame frame) throws Exception {
      entered.countDown();
      if (!release.await(3, TimeUnit.SECONDS))
        throw new IOException("Fixture bounded-write timeout");
      super.write(c, frame);
    }
  }
}
