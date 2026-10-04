package nl.thorhaven.app;

import android.app.*;
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

/** Separate 0.8 suite: real Android service lifecycle, recording RGB transport fixtures. */
final class RgbToolsChecks {
  static JSONObject copy(JSONObject q) throws JSONException {
    return RgbStudioChecks.copy(q);
  }

  static boolean rejects(RgbStudioChecks.Action action) {
    return RgbStudioChecks.rejects(action);
  }

  static void await(BooleanSupplier condition, int timeout, String message) throws Exception {
    RgbStudioChecks.await(condition, timeout, message);
  }

  static void run(SmokeInstrumentation t, Context c, MainActivity a) throws Exception {
    if (RgbService.instance != null
        || RgbService.requested
        || RgbService.cancellationPending
        || RgbSession.recovery(c).contains("baseline"))
      throw new Exception("RGB tools QA requires stopped lighting and no pending recovery record");
    zones(t);
    presets(t, c);
    bundles(t, c);
    ui(t, c, a);
    diagnostics(t, c);
    services(t, c, a);
  }

  static void zones(SmokeInstrumentation t) throws Exception {
    RgbEngine.Frame old = new RgbEngine.Frame(true, 0x112233, true, 0x445566);
    t.check(
        old.left2 == old.left && old.right2 == old.right,
        "Legacy four-argument frames mirror each stick color into its second physical zone");
    JSONObject legacy = old.toJson();
    t.check(
        legacy.length() == 4
            && !legacy.has("left2")
            && !legacy.has("right2")
            && RgbEngine.Frame.fromJson(legacy).equals(old),
        "Mirrored frames retain the legacy four-field wire format and round-trip unchanged");
    RgbEngine.Frame split = new RgbEngine.Frame(true, 0x112233, 0x445566, true, 0x778899, 0xaabbcc);
    JSONObject wire = split.toJson();
    t.check(
        wire.length() == 6
            && wire.getInt("left2") == 0x445566
            && wire.getInt("right2") == 0xaabbcc
            && RgbEngine.Frame.fromJson(wire).equals(split),
        "Independent zone frames preserve all four exact RGB values through the six-field"
            + " transport");
    JSONObject mirroredSix = copy(wire).put("left2", split.left).put("right2", split.right);
    t.check(
        RgbEngine.Frame.fromJson(mirroredSix).equals(oldColors(split))
            && RgbEngine.Frame.fromJson(mirroredSix).toJson().length() == 4,
        "Explicit mirrored six-field input remains compatible with canonical legacy output");
    RgbEngine.Frame off = new RgbEngine.Frame(false, 0xffffff, 0x112233, false, 0x445566, 0xaabbcc);
    t.check(
        off.left == 0 && off.left2 == 0 && off.right == 0 && off.right2 == 0,
        "Disabling a stick always clears both of its physical zones");
    t.check(
        rejects(() -> new RgbEngine.Frame(true, 0, -1, true, 0, 0))
            && rejects(() -> new RgbEngine.Frame(true, 0, 0x1000000, true, 0, 0))
            && rejects(() -> new RgbEngine.Frame(true, 0, 0, true, 0, -1))
            && rejects(() -> new RgbEngine.Frame(false, 0, 0, false, 0, 0x1000000)),
        "Zone-two constructor values remain bounded RGB integers even for disabled sticks");
    t.check(
        rejects(() -> RgbEngine.Frame.fromJson(copy(wire).put("left2", 12.0)))
            && rejects(() -> RgbEngine.Frame.fromJson(copy(wire).put("right2", "12")))
            && rejects(() -> RgbEngine.Frame.fromJson(copy(wire).put("left2", true)))
            && rejects(() -> RgbEngine.Frame.fromJson(copy(wire).put("right2", JSONObject.NULL)))
            && rejects(() -> RgbEngine.Frame.fromJson(copy(wire).put("rightEnabled", 1))),
        "Independent-zone wire data rejects coerced numbers, strings, nulls and non-boolean"
            + " switches");
    t.check(
        rejects(
                () -> {
                  JSONObject q = copy(wire);
                  q.remove("left2");
                  RgbEngine.Frame.fromJson(q);
                })
            && rejects(
                () -> {
                  JSONObject q = copy(wire);
                  q.remove("right2");
                  RgbEngine.Frame.fromJson(q);
                })
            && rejects(() -> RgbEngine.Frame.fromJson(copy(legacy).put("left2", 0)))
            && rejects(() -> RgbEngine.Frame.fromJson(copy(wire).put("path", "/sys/other"))),
        "Zone transport requires complete legacy or complete extended fields and rejects arbitrary"
            + " fields");
    RgbEngine.Frame changed =
        new RgbEngine.Frame(true, split.left, split.left2 + 1, true, split.right, split.right2);
    t.check(
        !changed.equals(split)
            && changed.hashCode() != split.hashCode()
            && split.hashCode() == RgbEngine.Frame.fromJson(wire).hashCode(),
        "Frame deduplication notices a zone-two-only change and keeps equal frame hashes stable");
    JSONObject settings = RgbSettings.defaults();
    RgbSettings.validateSettings(settings);
    RgbEngine.Frame rendered =
        RgbEngine.render(settings.getJSONObject("profile"), 0, 100, false, 100, settings);
    t.check(
        settings.getInt("schema") == 1
            && rendered.left == rendered.left2
            && rendered.right == rendered.right2,
        "Existing schema-one saved styles continue rendering identically into both zones of each"
            + " stick");
    RgbStudioChecks.FrameRunner runner = new RgbStudioChecks.FrameRunner();
    JSONObject request = new JSONObject().put("op", "rgbFrame").put("frame", wire);
    DeviceControl.rgbFrame(runner, request);
    String command = runner.commands.get(2);
    t.check(
        command.contains("'1-17:34:51:255' | dd of=" + DeviceControl.RGB_PATHS[1])
            && command.contains("'2-68:85:102:255' | dd of=" + DeviceControl.RGB_PATHS[1])
            && command.contains("'1-119:136:153:255' | dd of=" + DeviceControl.RGB_PATHS[3])
            && command.contains("'2-170:187:204:255' | dd of=" + DeviceControl.RGB_PATHS[3]),
        "Typed zone transport routes every distinct zone-one and zone-two value to its fixed stick"
            + " node");
    java.util.regex.Matcher paths =
        java.util.regex.Pattern.compile("dd of=(/sys/[A-Za-z0-9_/]+)").matcher(command);
    int count = 0;
    while (paths.find()) {
      if (!Arrays.asList(DeviceControl.RGB_PATHS).contains(paths.group(1)))
        throw new Exception("Zone transport escaped its fixed node allowlist");
      count++;
    }
    t.check(
        count == 6
            && command.contains("conv=nocreat,notrunc")
            && !command.contains(" > /sys/")
            && !command.contains("settings put"),
        "Extended-zone writes retain exactly six existing-node writes and never change stock AYN"
            + " settings");
    runner.commands.clear();
    boolean rejectedBeforeCommand = true;
    for (String field : new String[] {"left", "left2", "right", "right2"})
      for (Object value : new Object[] {-1, 0x1000000, 12.0, "12", JSONObject.NULL, true}) {
        JSONObject bad = copy(request).put("frame", copy(wire).put(field, value));
        rejectedBeforeCommand &=
            rejects(() -> DeviceControl.rgbFrame(runner, bad)) && runner.commands.isEmpty();
      }
    for (String field : new String[] {"left2", "right2"}) {
      JSONObject incomplete = copy(wire);
      incomplete.remove(field);
      JSONObject bad = copy(request).put("frame", incomplete);
      rejectedBeforeCommand &=
          rejects(() -> DeviceControl.rgbFrame(runner, bad)) && runner.commands.isEmpty();
    }
    t.check(
        rejectedBeforeCommand
            && rejects(
                () ->
                    DeviceControl.rgbFrame(
                        runner, copy(request).put("frame", copy(wire).put("left2", "$(id)"))))
            && rejects(
                () -> DeviceControl.rgbFrame(runner, copy(request).put("node", "/sys/escape")))
            && runner.commands.isEmpty(),
        "Every RGB field rejects out-of-range/coerced values and incomplete or executable"
            + " extended-zone requests before any command runs");
    runner.partial = true;
    String error = "";
    try {
      DeviceControl.rgbFrame(runner, request);
    } catch (Exception expected) {
      error = expected.getMessage();
    }
    t.check(
        error.contains("partial") && error.contains("THORHAVEN_RGB_PARTIAL_2"),
        "Partial extended-zone writes report the failing transport stage instead of claiming"
            + " success");
    RgbEngine.Frame restored = RgbHardware.stockFrame(RgbStudioChecks.baseline());
    runner.partial = false;
    runner.commands.clear();
    DeviceControl.rgbFrame(
        runner, new JSONObject().put("op", "rgbFrame").put("frame", restored.toJson()));
    String stockWrite = runner.commands.get(2);
    t.check(
        restored.left == restored.left2
            && restored.right == restored.right2
            && stockWrite.contains("'1-9:26:43:255' | dd of=" + DeviceControl.RGB_PATHS[1])
            && stockWrite.contains("'2-9:26:43:255' | dd of=" + DeviceControl.RGB_PATHS[1])
            && stockWrite.contains("'1-0:0:0:255' | dd of=" + DeviceControl.RGB_PATHS[3])
            && stockWrite.contains("'2-0:0:0:255' | dd of=" + DeviceControl.RGB_PATHS[3])
            && stockWrite.split("dd of=", -1).length == 7
            && runner.commands.size() == 3,
        "Actual stock recovery brightness and disabled sticks generate matching zone-one/two colors"
            + " through the same six fixed writes");
  }

  static RgbEngine.Frame oldColors(RgbEngine.Frame f) {
    return new RgbEngine.Frame(f.leftEnabled, f.left, f.rightEnabled, f.right);
  }

  static JSONObject portable() throws Exception {
    JSONObject q = RgbSettings.defaults();
    q.put("lowBatteryDim", false);
    JSONObject source = RgbStudio.builtIn(1);
    q.getJSONArray("presets")
        .put(
            new JSONObject().put("id", "qa_source").put("name", "Links 日本語").put("profile", source))
        .put(
            new JSONObject()
                .put("id", "qa_other")
                .put("name", "Other style")
                .put("profile", RgbStudio.builtIn(2)));
    q.getJSONObject("apps")
        .put("example.qa.one", "qa_source")
        .put("example.qa.two", "qa_source")
        .put("example.qa.other", "qa_other");
    return q;
  }

  static void presets(SmokeInstrumentation t, Context c) throws Exception {
    JSONObject initial = portable();
    RgbSettings.save(c, initial);
    String global = initial.getJSONObject("profile").toString();
    String apps = initial.getJSONObject("apps").toString();
    String originalName = "Links 日本語";
    JSONObject edited = RgbStudio.builtIn(3);
    RgbPresetTools.edit(c, "qa_source", edited);
    JSONObject after = RgbSettings.load(c);
    t.check(
        after.getJSONObject("profile").toString().equals(global)
            && after.getJSONObject("apps").toString().equals(apps)
            && after.getInt("fps") == initial.getInt("fps")
            && after.getBoolean("lowBatteryDim") == initial.getBoolean("lowBatteryDim"),
        "Editing a stored preset leaves the global profile, app assignments and playback options"
            + " untouched");
    JSONObject p = RgbPresetTools.preset(c, "qa_source");
    t.check(
        p.getString("id").equals("qa_source")
            && p.getString("name").equals(originalName)
            && p.getJSONObject("profile").toString().equals(edited.toString())
            && RgbSession.selectProfile(after, "example.qa.one")
                .toString()
                .equals(edited.toString())
            && RgbSession.selectProfile(after, "example.qa.two")
                .toString()
                .equals(edited.toString()),
        "Shared preset edits preserve its exact name/id and immediately propagate through both"
            + " assigned app references");
    edited.getJSONObject("left").put("color", "#010203");
    p.getJSONObject("profile").getJSONObject("left").put("color", "#040506");
    t.check(
        !RgbPresetTools.preset(c, "qa_source")
                .getJSONObject("profile")
                .getJSONObject("left")
                .getString("color")
                .equals("#040506")
            && !RgbPresetTools.preset(c, "qa_source")
                .getJSONObject("profile")
                .getJSONObject("left")
                .getString("color")
                .equals("#010203"),
        "Preset edits and reads use deep copies so later caller mutations cannot alter saved"
            + " styles");
    String duplicate = RgbPresetTools.duplicate(c, "qa_source", "Notities 日本語");
    JSONObject duplicated = RgbPresetTools.preset(c, duplicate);
    t.check(
        !duplicate.equals("qa_source")
            && duplicate.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,39}")
            && duplicated.getString("name").equals("Notities 日本語")
            && duplicated
                .getJSONObject("profile")
                .toString()
                .equals(RgbPresetTools.preset(c, "qa_source").getJSONObject("profile").toString())
            && RgbSettings.load(c).getJSONObject("apps").toString().equals(apps),
        "Duplicating a preset creates a distinct valid id and exact user name without moving any"
            + " app assignment");
    RgbPresetTools.edit(c, duplicate, RgbStudio.builtIn(4));
    t.check(
        !RgbPresetTools.preset(c, duplicate)
            .getJSONObject("profile")
            .toString()
            .equals(RgbPresetTools.preset(c, "qa_source").getJSONObject("profile").toString()),
        "Editing a duplicate changes only that duplicate and leaves its source preset independent");
    String before = Store.prefs(c).getString(RgbSettings.KEY, "");
    JSONObject invalid = RgbStudio.builtIn(1);
    invalid.getJSONObject("right").put("brightness", 101);
    t.check(
        rejects(() -> RgbPresetTools.edit(c, "qa_source", invalid))
            && rejects(() -> RgbPresetTools.edit(c, "missing", RgbStudio.builtIn(1)))
            && rejects(() -> RgbPresetTools.duplicate(c, "missing", "Missing"))
            && before.equals(Store.prefs(c).getString(RgbSettings.KEY, "")),
        "Invalid or missing-id preset edits and copies reject atomically without changing stored"
            + " settings");
    RgbPresetTools.rename(c, duplicate, "  Links 日本語  ");
    t.check(
        RgbPresetTools.preset(c, duplicate).getString("name").equals("Links 日本語"),
        "User preset rename trims outer whitespace and preserves Unicode text exactly");
    String beforeName = Store.prefs(c).getString(RgbSettings.KEY, "");
    t.check(
        rejects(() -> RgbPresetTools.rename(c, duplicate, "bad\nname"))
            && rejects(
                () ->
                    RgbPresetTools.rename(
                        c, duplicate, new String(new char[61]).replace('\0', 'x')))
            && rejects(() -> RgbPresetTools.rename(c, "missing", "Valid"))
            && beforeName.equals(Store.prefs(c).getString(RgbSettings.KEY, "")),
        "Preset names reject control characters, more than 60 characters and missing ids"
            + " atomically");
    t.check(
        RgbPresetTools.assignedApps(RgbSettings.load(c), "qa_source") == 2
            && RgbPresetTools.assignedApps(RgbSettings.load(c), duplicate) == 0,
        "Shared-assignment counts distinguish source presets from unassigned duplicates");
    RgbPresetTools.updateFromGlobal(c, duplicate);
    t.check(
        RgbPresetTools.preset(c, duplicate).getJSONObject("profile").toString().equals(global)
            && RgbSettings.load(c).getJSONObject("profile").toString().equals(global),
        "Updating a stored preset from global copies the profile without changing the global"
            + " configuration");
    RgbPresetTools.useGlobal(c, "qa_source");
    t.check(
        RgbSettings.load(c)
                .getJSONObject("profile")
                .toString()
                .equals(RgbPresetTools.preset(c, "qa_source").getJSONObject("profile").toString())
            && RgbSettings.load(c).getJSONObject("apps").toString().equals(apps),
        "Applying a stored preset globally preserves every app-specific assignment");
    RgbSettings.save(c, portable());
    AtomicReference<Throwable> failed = new AtomicReference<>();
    CountDownLatch go = new CountDownLatch(1);
    Thread rename =
        new Thread(
            () -> {
              try {
                go.await();
                RgbPresetTools.rename(c, "qa_source", "Concurrent 日本語");
              } catch (Throwable e) {
                failed.set(e);
              }
            });
    Thread assign =
        new Thread(
            () -> {
              try {
                go.await();
                RgbPresetTools.assign(c, "example.qa.concurrent", "qa_other");
              } catch (Throwable e) {
                failed.set(e);
              }
            });
    rename.start();
    assign.start();
    go.countDown();
    rename.join(4000);
    assign.join(4000);
    t.check(
        !rename.isAlive()
            && !assign.isAlive()
            && failed.get() == null
            && RgbPresetTools.preset(c, "qa_source").getString("name").equals("Concurrent 日本語")
            && RgbSettings.load(c)
                .getJSONObject("apps")
                .getString("example.qa.concurrent")
                .equals("qa_other"),
        "Concurrent preset and app mutations are serialized without losing either committed"
            + " change");
    RgbPresetTools.remove(c, "qa_source");
    JSONObject removed = RgbSettings.load(c);
    t.check(
        !removed.getJSONObject("apps").has("example.qa.one")
            && !removed.getJSONObject("apps").has("example.qa.two")
            && removed.getJSONObject("apps").getString("example.qa.other").equals("qa_other")
            && RgbPresetTools.preset(c, "qa_other") != null,
        "Deleting a shared preset removes only its matching app assignments and preserves other"
            + " styles");
    JSONObject full = portable();
    for (int i = 2; i < 20; i++)
      full.getJSONArray("presets")
          .put(
              new JSONObject()
                  .put("id", "qa_" + i)
                  .put("name", "Style " + i)
                  .put("profile", RgbSettings.defaultProfile()));
    RgbSettings.save(c, full);
    String atLimit = Store.prefs(c).getString(RgbSettings.KEY, "");
    t.check(
        rejects(() -> RgbPresetTools.duplicate(c, "qa_source", "Overflow"))
            && rejects(() -> RgbPresetTools.add(c, "Overflow", RgbSettings.defaultProfile()))
            && atLimit.equals(Store.prefs(c).getString(RgbSettings.KEY, "")),
        "The 20-preset capacity rejects duplicate and add operations without partial saved"
            + " changes");
    JSONObject appLimit = portable();
    for (int i = appLimit.getJSONObject("apps").length(); i < 64; i++)
      appLimit.getJSONObject("apps").put("example.limit.p" + i, "qa_source");
    RgbSettings.save(c, appLimit);
    String sixtyFour = Store.prefs(c).getString(RgbSettings.KEY, "");
    t.check(
        rejects(() -> RgbPresetTools.assign(c, "example.limit.overflow", "qa_source"))
            && rejects(() -> RgbPresetTools.assign(c, "example.limit.good", "missing"))
            && sixtyFour.equals(Store.prefs(c).getString(RgbSettings.KEY, "")),
        "App assignment limits and missing preset references reject atomically");
    List<Store.App> list =
        Arrays.asList(
            new Store.App("example.alpha", "Mario 日本語", null),
            new Store.App("example.beta", "Zelda", null),
            new Store.App("com.gamma", "MARIO second", null));
    List<Store.App> labelMatches = RgbPresetTools.matchingApps(list, "mArIo");
    List<Store.App> packageMatches = RgbPresetTools.matchingApps(list, "EXAMPLE.");
    t.check(
        labelMatches.size() == 2
            && labelMatches.get(0) == list.get(0)
            && labelMatches.get(1) == list.get(2)
            && packageMatches.size() == 2
            && packageMatches.get(0) == list.get(0)
            && RgbPresetTools.matchingApps(list, "日本語").size() == 1
            && RgbPresetTools.matchingApps(list, "no-match").isEmpty(),
        "App search matches labels and package names case-insensitively while preserving source"
            + " order and Unicode");
    t.check(
        RgbService.instance == null
            && !RgbService.requested
            && !RgbSession.recovery(c).contains("baseline"),
        "Saving, editing, copying and assigning presets never starts hardware lighting or creates a"
            + " recovery journal");
    RgbSettings.save(c, portable());
  }

  static void bundles(SmokeInstrumentation t, Context c) throws Exception {
    JSONObject settings = portable();
    String source = settings.toString();
    String raw = RgbPresetBundle.encode(settings);
    JSONArray styles = RgbPresetBundle.parse(raw);
    JSONObject document = new JSONObject(raw);
    t.check(
        document.length() == 3
            && document.getString("format").equals("thorhaven-rgb-presets")
            && document.getInt("schema") == 1
            && styles.length() == 2
            && styles.getJSONObject(0).getString("name").equals("Links 日本語")
            && styles
                .getJSONObject(0)
                .getJSONObject("profile")
                .toString()
                .equals(
                    settings
                        .getJSONArray("presets")
                        .getJSONObject(0)
                        .getJSONObject("profile")
                        .toString()),
        "RGB-only bundle round-trips exact Unicode preset names and complete style profiles");
    t.check(
        styles.getJSONObject(0).length() == 2
            && !styles.getJSONObject(0).has("id")
            && !document.has("apps")
            && !document.has("profile")
            && !document.has("baseline")
            && !raw.contains("example.qa.one")
            && !raw.contains(RgbHardware.ENABLED)
            && !raw.contains("qa_source"),
        "Shared RGB files exclude preset ids, app packages, global settings and hardware recovery"
            + " data");
    JSONObject merged = RgbPresetBundle.merge(settings, styles);
    JSONArray all = merged.getJSONArray("presets");
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < all.length(); i++) ids.add(all.getJSONObject(i).getString("id"));
    t.check(
        all.length() == 4
            && ids.size() == 4
            && ids.contains("qa_source")
            && ids.contains("qa_other")
            && all.getJSONObject(2).getString("name").equals("Links 日本語")
            && merged
                .getJSONObject("apps")
                .toString()
                .equals(settings.getJSONObject("apps").toString())
            && merged
                .getJSONObject("profile")
                .toString()
                .equals(settings.getJSONObject("profile").toString())
            && merged.getInt("fps") == settings.getInt("fps")
            && settings.toString().equals(source),
        "Bundle merging adds unique imported ids while preserving existing styles, apps, global"
            + " options and its input document");
    styles.getJSONObject(0).getJSONObject("profile").getJSONObject("left").put("color", "#010203");
    merged.getJSONArray("presets").getJSONObject(0).put("name", "Changed result");
    t.check(
        !merged
                .getJSONArray("presets")
                .getJSONObject(2)
                .getJSONObject("profile")
                .getJSONObject("left")
                .getString("color")
                .equals("#010203")
            && settings.toString().equals(source),
        "Imported and existing bundle styles are deep copies without shared mutable objects");
    t.check(
        rejects(() -> RgbPresetBundle.parse(raw + " trailing"))
            && rejects(() -> RgbPresetBundle.parse("[]"))
            && rejects(() -> RgbPresetBundle.parse(copy(document).put("schema", "1").toString()))
            && rejects(() -> RgbPresetBundle.parse(copy(document).put("schema", 2).toString()))
            && rejects(
                () -> RgbPresetBundle.parse(copy(document).put("format", "other").toString()))
            && rejects(
                () ->
                    RgbPresetBundle.parse(copy(document).put("apps", new JSONObject()).toString())),
        "Bundle parser rejects trailing data, wrong root type, coerced/unsupported schemas, other"
            + " formats and extra fields");
    JSONObject malicious = copy(document);
    malicious.getJSONArray("presets").getJSONObject(0).put("path", "/sys/escape");
    JSONObject badColor = copy(document);
    badColor
        .getJSONArray("presets")
        .getJSONObject(1)
        .getJSONObject("profile")
        .getJSONObject("right")
        .put("color", "$(id)");
    t.check(
        rejects(() -> RgbPresetBundle.parse(malicious.toString()))
            && rejects(() -> RgbPresetBundle.parse(badColor.toString())),
        "RGB-only styles reject unknown executable fields and invalid colors before any import"
            + " mutation");
    JSONArray twentyOne = new JSONArray();
    for (int i = 0; i < 21; i++)
      twentyOne.put(copy(document.getJSONArray("presets").getJSONObject(0)));
    t.check(
        rejects(
                () ->
                    RgbPresetBundle.parse(
                        copy(document).put("presets", new JSONArray()).toString()))
            && rejects(
                () -> RgbPresetBundle.parse(copy(document).put("presets", twentyOne).toString()))
            && rejects(() -> RgbPresetBundle.encode(RgbSettings.defaults())),
        "Shared RGB files require one to twenty styles and never export an empty preset"
            + " collection");
    String oversizedChars = new String(new char[65537]).replace('\0', ' ');
    String oversizedUtf8 = raw + new String(new char[23000]).replace('\0', '日');
    t.check(
        rejects(() -> RgbPresetBundle.parse(oversizedChars))
            && rejects(() -> RgbPresetBundle.parse(oversizedUtf8))
            && rejects(() -> RgbPresetBundle.read(new ByteArrayInputStream(new byte[65537])))
            && rejects(
                () ->
                    RgbPresetBundle.read(new ByteArrayInputStream(new byte[] {(byte) 0xc3, 0x28})))
            && RgbPresetBundle.read(
                    new ByteArrayInputStream(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .equals(raw),
        "RGB bundle text and streams enforce the 64 KB byte limit and reject malformed UTF-8"
            + " without corrupting valid Unicode");
    JSONObject full = copy(settings);
    for (int i = 2; i < 20; i++)
      full.getJSONArray("presets")
          .put(
              new JSONObject()
                  .put("id", "bundle_" + i)
                  .put("name", "Style " + i)
                  .put("profile", RgbSettings.defaultProfile()));
    String fullBefore = full.toString();
    JSONArray valid = RgbPresetBundle.parse(raw);
    t.check(
        rejects(() -> RgbPresetBundle.merge(full, valid)) && full.toString().equals(fullBefore),
        "Bundle capacity is checked before merging and leaves a full destination document"
            + " unchanged");
    RgbSettings.save(c, settings);
    String prefsBefore = Store.prefs(c).getString(RgbSettings.KEY, "");
    JSONArray invalidAll = RgbPresetBundle.parse(raw);
    invalidAll
        .getJSONObject(1)
        .getJSONObject("profile")
        .getJSONObject("left")
        .put("brightness", false);
    t.check(
        rejects(
                () ->
                    RgbSettings.update(
                        c,
                        current ->
                            current.put(
                                "presets",
                                RgbPresetBundle.merge(current, invalidAll)
                                    .getJSONArray("presets"))))
            && prefsBefore.equals(Store.prefs(c).getString(RgbSettings.KEY, "")),
        "A malformed later bundle style rejects the complete transactional import without saving"
            + " earlier styles");
    RgbSettings.update(
        c,
        current ->
            current.put("presets", RgbPresetBundle.merge(current, valid).getJSONArray("presets")));
    t.check(
        RgbSettings.load(c).getJSONArray("presets").length() == 4
            && RgbSettings.load(c)
                .getJSONObject("apps")
                .toString()
                .equals(settings.getJSONObject("apps").toString())
            && RgbService.instance == null
            && !RgbService.requested
            && !RgbSession.recovery(c).contains("baseline"),
        "Committing a valid RGB-only import adds styles while preserving app assignments and never"
            + " starts lighting");
    RgbSettings.save(c, portable());
  }

  static void diagnostics(SmokeInstrumentation t, Context c) throws Exception {
    t.check(
        RgbDiagnostics.duration("zones") == 8000
            && RgbDiagnostics.duration("channels") == 24000
            && RgbDiagnostics.duration("left1") == 8000
            && RgbDiagnostics.duration("right2") == 8000,
        "Diagnostics have fixed eight-second zone tests and a finite 24-second channel test");
    boolean allZones = true;
    for (int zone = 0; zone < 4; zone++) {
      RgbEngine.Frame expected = diagnosticFrame(zone, 0x191919);
      allZones &=
          RgbDiagnostics.frame("zones", zone * 2000L).equals(expected)
              && RgbDiagnostics.frame("zones", zone * 2000L + 1999).equals(expected);
    }
    t.check(
        allZones,
        "All-zone diagnostic lights exactly one physical zone at a time for two seconds at bounded"
            + " intensity");
    boolean channels = true;
    int[] colors = {0x190000, 0x001900, 0x000019};
    for (int zone = 0; zone < 4; zone++)
      for (int channel = 0; channel < 3; channel++) {
        long elapsed = (zone * 3L + channel) * 2000;
        channels &=
            RgbDiagnostics.frame("channels", elapsed).equals(diagnosticFrame(zone, colors[channel]))
                && RgbDiagnostics.frame("channels", elapsed + 1999)
                    .equals(diagnosticFrame(zone, colors[channel]));
      }
    t.check(
        channels,
        "Channel diagnostic checks red, green and blue separately on every zone without rapid"
            + " flashes");
    boolean holds = true;
    String[] modes = {"left1", "left2", "right1", "right2"};
    for (int zone = 0; zone < modes.length; zone++)
      holds &=
          RgbDiagnostics.frame(modes[zone], 0).equals(diagnosticFrame(zone, 0x191919))
              && RgbDiagnostics.frame(modes[zone], 7999).equals(diagnosticFrame(zone, 0x191919));
    t.check(
        holds,
        "Individual zone tests keep the selected physical zone stable for their complete finite"
            + " duration");
    RgbEngine.Frame black = new RgbEngine.Frame(false, 0, false, 0);
    boolean outside = true;
    for (String mode : RgbDiagnostics.MODES)
      outside &=
          RgbDiagnostics.frame(mode, -1).equals(black)
              && RgbDiagnostics.frame(mode, RgbDiagnostics.duration(mode)).equals(black)
              && RgbDiagnostics.frame(mode, Long.MAX_VALUE).equals(black);
    t.check(
        outside
            && rejects(() -> RgbDiagnostics.duration("zones;id"))
            && rejects(() -> RgbDiagnostics.frame("shell", 0)),
        "Diagnostic rendering returns all-off outside its finite interval and rejects unknown"
            + " command-like modes");
    RgbStudioChecks.FakeBackend backend = new RgbStudioChecks.FakeBackend(c);
    RgbSession session = new RgbSession(c, backend, 1000);
    try {
      t.check(
          session.diagnosticTick("zones", 1000, true)
              && backend.frames.size() == 1
              && backend.frames.get(0).equals(diagnosticFrame(0, 0x191919))
              && RgbSession.recovery(c).contains("baseline"),
          "Diagnostic session journals real stock recovery before its first isolated-zone write");
      session.diagnosticTick("zones", 2999, true);
      session.diagnosticTick("zones", 3000, true);
      t.check(
          backend.frames.size() == 2 && backend.frames.get(1).equals(diagnosticFrame(1, 0x191919)),
          "Diagnostic ticks deduplicate steady frames and advance zone-two independently at the"
              + " next boundary");
      int written = backend.frames.size();
      t.check(
          !session.diagnosticTick("zones", 9000, true) && backend.frames.size() == written,
          "Expired diagnostic ticks return stop without writing any additional hardware frame");
      t.check(
          session.close("Diagnostic QA finished")
              && backend.restorations == 1
              && !RgbSession.recovery(c).contains("baseline")
              && !session.diagnosticTick("zones", 1000, true)
              && backend.frames.size() == written,
          "Finite diagnostic restoration clears its journal and closed sessions cannot restart"
              + " writes");
    } finally {
      session.close("Diagnostic QA cleanup");
    }
    RgbStudioChecks.FakeBackend sleeping = new RgbStudioChecks.FakeBackend(c);
    RgbSession sleepSession = new RgbSession(c, sleeping, 1000);
    try {
      sleepSession.diagnosticTick("channels", 1000, true);
      t.check(
          !sleepSession.diagnosticTick("channels", 1100, false)
              && sleeping.frames.size() == 1
              && sleepSession.message.equals(
                  RgbDiagnostics.text(
                      c,
                      "RGB-zonetest gestopt omdat de schermen uit zijn",
                      "RGB zone test stopped because the screens are off")),
          "Screen-off immediately requests diagnostic stop without writing a new animation or"
              + " blackout frame");
    } finally {
      sleepSession.close("Screen-off QA cleanup");
    }
    RgbStudioChecks.FakeBackend failed = new RgbStudioChecks.FakeBackend(c);
    RgbSession failing = new RgbSession(c, failed, 1000);
    try {
      failed.failWrite = true;
      t.check(
          rejects(() -> failing.diagnosticTick("left2", 1000, true))
              && failing.last == null
              && failed.frames.isEmpty()
              && RgbSession.recovery(c).contains("baseline"),
          "Failed diagnostic writes retain recovery and never mark an unwritten zone frame as"
              + " committed");
      failed.failRestore = true;
      t.check(
          !failing.close("Failed diagnostic") && RgbSession.recovery(c).contains("baseline"),
          "A failed diagnostic restoration keeps the original stock journal for explicit later"
              + " recovery");
    } finally {
      // This suite created this recording-fixture journal; do not remove any pre-existing device
      // journal.
      RgbSession.recovery(c).edit().remove("baseline").commit();
    }
  }

  static RgbEngine.Frame diagnosticFrame(int zone, int color) {
    return new RgbEngine.Frame(
        zone < 2,
        zone == 0 ? color : 0,
        zone == 1 ? color : 0,
        zone >= 2,
        zone == 2 ? color : 0,
        zone == 3 ? color : 0);
  }

  static void ui(SmokeInstrumentation t, Context c, MainActivity a) throws Exception {
    RgbSettings.save(c, portable());
    MainActivity lower = null;
    try {
      ActivityOptions options = ActivityOptions.makeBasic();
      options.setLaunchDisplayId(Store.screen(c, true));
      lower =
          (MainActivity)
              t.startActivitySync(
                  new Intent(c, MainActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
                  options.toBundle());
      MainActivity bottom = lower;
      final boolean[] built = {true};
      for (String language : new String[] {"nl", "en"}) {
        Store.prefs(c).edit().putString("language", language).commit();
        for (MainActivity owner : new MainActivity[] {a, bottom}) {
          String before = Store.prefs(c).getString(RgbSettings.KEY, "");
          t.runOnMainSync(
              () -> {
                owner.go("RGB Studio");
                AlertDialog dialog = RgbStudio.savedPresetEditor(owner, "qa_source");
                if (dialog == null) {
                  built[0] = false;
                  return;
                }
                View decor = dialog.getWindow().getDecorView();
                built[0] &=
                    RgbStudioChecks.find(decor, TextView.class).stream()
                            .anyMatch(view -> view.getText().toString().equals("Links 日本語"))
                        && RgbStudioChecks.find(decor, EditText.class).size() == 4
                        && RgbStudioChecks.find(decor, Switch.class).size() == 2;
                RgbStudioChecks.find(decor, EditText.class).get(0).setText("#010203");
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
              });
          built[0] &= before.equals(Store.prefs(c).getString(RgbSettings.KEY, ""));
        }
      }
      t.check(
          built[0] && bottom.getDisplay().getDisplayId() == Store.screen(c, true),
          "Saved-preset editor renders exact user names and independent controls in Dutch/English"
              + " on both real displays; Cancel saves nothing");
      String beforeInvalid = Store.prefs(c).getString(RgbSettings.KEY, "");
      AtomicReference<AlertDialog> opened = new AtomicReference<>();
      t.runOnMainSync(
          () -> {
            AlertDialog dialog = RgbStudio.savedPresetEditor(a, "qa_source");
            opened.set(dialog);
            RgbStudioChecks.find(dialog.getWindow().getDecorView(), EditText.class)
                .get(0)
                .setText("#BAD");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
          });
      AlertDialog edit = opened.get();
      t.check(
          edit.isShowing() && beforeInvalid.equals(Store.prefs(c).getString(RgbSettings.KEY, "")),
          "Invalid saved-preset editor input keeps the dialog open and does not alter persisted"
              + " settings");
      String global = RgbSettings.load(c).getJSONObject("profile").toString();
      String apps = RgbSettings.load(c).getJSONObject("apps").toString();
      t.runOnMainSync(
          () -> {
            RgbStudioChecks.find(edit.getWindow().getDecorView(), EditText.class)
                .get(0)
                .setText("#010203");
            edit.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
          });
      JSONObject saved = RgbSettings.load(c);
      t.check(
          !edit.isShowing()
              && saved.getJSONObject("profile").toString().equals(global)
              && saved.getJSONObject("apps").toString().equals(apps)
              && RgbPresetTools.preset(c, "qa_source")
                  .getJSONObject("profile")
                  .getJSONObject("left")
                  .getString("color")
                  .equals("#010203")
              && RgbSession.selectProfile(saved, "example.qa.two")
                  .getJSONObject("left")
                  .getString("color")
                  .equals("#010203"),
          "Saving the real preset editor commits only its selected preset and propagates through"
              + " existing app references");
    } finally {
      if (lower != null) {
        MainActivity done = lower;
        t.runOnMainSync(done::finish);
      }
      RgbSettings.save(c, portable());
      t.runOnMainSync(() -> a.go("RGB Studio"));
    }
  }

  static void screen(SmokeInstrumentation t, boolean awake) throws Exception {
    // Two fixed Android commands on the isolated emulator; no app data or arbitrary shell input.
    try (ParcelFileDescriptor fd =
        t.getUiAutomation()
            .executeShellCommand(awake ? "input keyevent 224" : "input keyevent 223")) {
      // PowerManager below confirms completion; this key event has no useful output.
    }
    Context c = t.getTargetContext();
    await(
        () -> c.getSystemService(PowerManager.class).isInteractive() == awake,
        5000,
        awake ? "Isolated RGB QA display did not wake" : "Isolated RGB QA display did not sleep");
  }

  static void services(SmokeInstrumentation t, Context c, MainActivity a) throws Exception {
    RgbSession.Backend original = RgbService.backend;
    try {
      screen(t, true);
      RgbStudioChecks.FakeBackend finite = new RgbStudioChecks.FakeBackend(c);
      RgbService.backend = finite;
      AtomicBoolean accepted = new AtomicBoolean();
      t.runOnMainSync(() -> accepted.set(RgbService.startDiagnostic(c, "left2")));
      await(
          () -> RgbService.instance != null && finite.frames.size() == 1,
          8000,
          "Actual diagnostic service did not start its first independent-zone frame");
      RgbService active = RgbService.instance;
      t.check(
          accepted.get()
              && active.diagnosticMode.equals("left2")
              && finite.frames.get(0).equals(diagnosticFrame(1, 0x191919))
              && RgbSession.recovery(c).contains("baseline")
              && finite.threads.stream().allMatch(name -> name.contains("Thorhaven-RGB")),
          "Actual diagnostic foreground service journals recovery and writes its independent zone"
              + " on the background worker");
      AtomicBoolean overlap = new AtomicBoolean(true);
      t.runOnMainSync(
          () -> {
            overlap.set(RgbService.startDiagnostic(c, "right1"));
            RgbService.start(c);
            RgbService.focus(c, "example.qa.one");
          });
      RgbPresetTools.edit(c, "qa_source", RgbStudio.builtIn(5));
      Thread.sleep(700);
      t.check(
          !overlap.get()
              && active.diagnosticMode.equals("left2")
              && finite.frames.size() == 1
              && finite.frames.get(0).equals(diagnosticFrame(1, 0x191919)),
          "Active diagnostics refuse overlapping starts and remain independent of normal RGB"
              + " starts, app focus and preset edits");
      long started = active.session.started;
      await(
          () ->
              RgbService.instance == null
                  && active.session.closed
                  && finite.restorations == 1
                  && !RgbService.cancellationPending,
          11000,
          "Eight-second diagnostic service did not expire and restore its stock baseline");
      t.check(
          SystemClock.elapsedRealtime() - started >= 8000
              && finite.frames.size() == 1
              && !RgbSession.recovery(c).contains("baseline")
              && finite.restored.toString().equals(finite.stock.toString()),
          "Actual finite diagnostic expires automatically, restores stock once and clears recovery"
              + " without a final extra frame");
      RgbStudioChecks.FakeBackend sleeping = new RgbStudioChecks.FakeBackend(c);
      RgbService.backend = sleeping;
      t.runOnMainSync(() -> RgbService.startDiagnostic(c, "channels"));
      await(
          () -> RgbService.instance != null && sleeping.frames.size() == 1,
          7000,
          "Screen-off diagnostic fixture did not begin");
      RgbService screenSession = RgbService.instance;
      int beforeSleep = sleeping.frames.size();
      screen(t, false);
      await(
          () ->
              RgbService.instance == null
                  && screenSession.session.closed
                  && sleeping.restorations == 1
                  && !RgbService.cancellationPending,
          7000,
          "Actual screen-off did not cancel and restore its diagnostic foreground service");
      screen(t, true);
      Thread.sleep(700);
      t.check(
          sleeping.frames.size() == beforeSleep
              && !RgbService.requested
              && RgbService.instance == null
              && !RgbSession.recovery(c).contains("baseline"),
          "Real Android screen-off cancels diagnostics, restores stock and does not restart when"
              + " the display wakes");
      RgbStudioChecks.FakeBackend recovery = new RgbStudioChecks.FakeBackend(c);
      RgbService.backend = recovery;
      RgbSession.recovery(c)
          .edit()
          .putString("baseline", RgbStudioChecks.baseline().toString())
          .commit();
      AtomicBoolean blocked = new AtomicBoolean(true);
      t.runOnMainSync(() -> blocked.set(RgbService.startDiagnostic(c, "zones")));
      Thread.sleep(300);
      t.check(
          !blocked.get()
              && recovery.probes == 0
              && recovery.frames.isEmpty()
              && !RgbService.requested
              && RgbService.instance == null
              && RgbSession.recovery(c).contains("baseline"),
          "Pending stock recovery prevents an actual diagnostic start without probing or replacing"
              + " its saved journal");
      RgbSession.recovery(c).edit().remove("baseline").commit();
      t.runOnMainSync(() -> blocked.set(RgbService.startDiagnostic(c, "zones;id")));
      t.check(
          !blocked.get() && recovery.probes == 0 && !RgbService.requested,
          "Actual diagnostic entry point rejects unknown modes before Android service creation");
      RgbStudioChecks.FakeBackend cancelled = new RgbStudioChecks.FakeBackend(c);
      RgbService.backend = cancelled;
      AtomicBoolean queued = new AtomicBoolean();
      t.runOnMainSync(
          () -> {
            queued.set(RgbService.startDiagnostic(c, "right2"));
            RgbService.stopDiagnostic(c);
          });
      Thread.sleep(700);
      await(
          () -> RgbService.instance == null && !RgbService.cancellationPending,
          7000,
          "Queued diagnostic cancellation did not complete Android foreground cleanup");
      t.check(
          queued.get()
              && cancelled.probes == 0
              && cancelled.frames.isEmpty()
              && !RgbService.requested
              && !RgbSession.recovery(c).contains("baseline"),
          "Stopping a queued diagnostic before service creation prevents a stock probe, journal or"
              + " lighting write");
      RgbStudioChecks.FakeBackend dialogCancelled = new RgbStudioChecks.FakeBackend(c);
      RgbService.backend = dialogCancelled;
      AtomicBoolean clicked = new AtomicBoolean();
      AtomicBoolean queuedFromDialog = new AtomicBoolean();
      AtomicBoolean cancelledSynchronously = new AtomicBoolean();
      AtomicBoolean staleClickIgnored = new AtomicBoolean();
      t.runOnMainSync(
          () -> {
            AlertDialog dialog = RgbDiagnostics.open(a);
            Button selected = null;
            for (Button button :
                RgbStudioChecks.find(dialog.getWindow().getDecorView(), Button.class)) {
              if (button.getText().toString().equals(RgbDiagnostics.name(a, "left1"))) {
                clicked.set(button.performClick());
                selected = button;
                break;
              }
            }
            queuedFromDialog.set(
                RgbService.requested && "left1".equals(RgbService.requestedDiagnostic));
            dialog.dismiss();
            cancelledSynchronously.set(
                !RgbService.requested && RgbService.requestedDiagnostic.isEmpty());
            long stoppedGeneration = RgbService.requestGeneration;
            if (selected != null) selected.performClick();
            staleClickIgnored.set(
                !RgbService.requested && RgbService.requestGeneration == stoppedGeneration);
          });
      t.check(
          queuedFromDialog.get() && cancelledSynchronously.get(),
          "Closing a just-created chooser cancels its accepted diagnostic synchronously before"
              + " service creation");
      t.check(
          staleClickIgnored.get(),
          "A retained button from a closed diagnostic chooser cannot queue a new lighting session");
      Thread.sleep(700);
      await(
          () -> RgbService.instance == null && !RgbService.cancellationPending,
          7000,
          "Closing the diagnostic chooser did not cancel its own queued foreground start");
      t.check(
          clicked.get()
              && dialogCancelled.probes == 0
              && dialogCancelled.frames.isEmpty()
              && !RgbService.requested
              && !RgbSession.recovery(c).contains("baseline"),
          "Closing the actual diagnostic chooser cancels its own queued test before Android creates"
              + " a lighting session");
      ownerFinish(t, c);
      RgbStudioChecks.BlockingBackend inflight = new RgbStudioChecks.BlockingBackend(c);
      RgbService.backend = inflight;
      t.runOnMainSync(() -> RgbService.startDiagnostic(c, "right2"));
      if (!inflight.entered.await(5, TimeUnit.SECONDS))
        throw new Exception("Diagnostic service did not enter the controlled in-flight zone write");
      RgbService writing = RgbService.instance;
      t.runOnMainSync(() -> RgbService.stopDiagnostic(c));
      inflight.release.countDown();
      await(
          () ->
              RgbService.instance == null
                  && writing.session.closed
                  && inflight.restorations == 1
                  && !RgbService.cancellationPending,
          7000,
          "In-flight diagnostic stop did not restore after its bounded current write completed");
      Thread.sleep(700);
      t.check(
          inflight.frames.size() == 1
              && inflight.frames.get(0).equals(diagnosticFrame(3, 0x191919))
              && !RgbSession.recovery(c).contains("baseline"),
          "In-flight diagnostic cancellation waits for its current zone write then restores once"
              + " and prevents later frames");
      RgbStudioChecks.FakeBackend writeError = new RgbStudioChecks.FakeBackend(c);
      writeError.failWrite = true;
      RgbService.backend = writeError;
      t.runOnMainSync(() -> RgbService.startDiagnostic(c, "zones"));
      await(
          () ->
              writeError.restorations == 1
                  && RgbService.instance == null
                  && !RgbService.cancellationPending,
          8000,
          "Diagnostic hardware-error path did not stop its Android service and restore stock");
      t.check(
          writeError.frames.isEmpty() && !RgbSession.recovery(c).contains("baseline"),
          "Actual diagnostic service stops after its first write error and restores stock without"
              + " claiming a committed frame");
    } finally {
      screen(t, true);
      if (RgbService.instance != null || RgbService.requested)
        t.runOnMainSync(() -> RgbService.stop(c));
      await(
          () -> RgbService.instance == null && !RgbService.cancellationPending,
          7000,
          "RGB tools QA could not finish its recording service cleanup");
      RgbService.backend = original;
      RgbSession.recovery(c).edit().remove("baseline").commit();
      RgbSettings.save(c, portable());
    }
  }

  static void ownerFinish(SmokeInstrumentation t, Context c) throws Exception {
    RgbStudioChecks.FakeBackend backend = new RgbStudioChecks.FakeBackend(c);
    RgbService.backend = backend;
    ActivityOptions options = ActivityOptions.makeBasic();
    options.setLaunchDisplayId(Store.screen(c, true));
    MainActivity owner =
        (MainActivity)
            t.startActivitySync(
                new Intent(c, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
                options.toBundle());
    try {
      AtomicReference<View> decor = new AtomicReference<>();
      AtomicBoolean clicked = new AtomicBoolean();
      t.runOnMainSync(
          () -> {
            AlertDialog dialog = RgbDiagnostics.open(owner);
            decor.set(dialog.getWindow().getDecorView());
            for (Button button : RgbStudioChecks.find(decor.get(), Button.class)) {
              if (button.getText().toString().equals(RgbDiagnostics.name(owner, "right1"))) {
                clicked.set(button.performClick());
                break;
              }
            }
          });
      await(
          () -> RgbService.instance != null && backend.frames.size() == 1,
          5000,
          "Owner-finish diagnostic fixture did not start its first zone frame");
      RgbService active = RgbService.instance;
      long started = active.session.started;
      if (SystemClock.elapsedRealtime() - started >= 3500)
        throw new Exception(
            "Owner-finish fixture started too late to distinguish cleanup from eight-second"
                + " expiry");
      t.runOnMainSync(owner::finish);
      await(
          () ->
              owner.isDestroyed()
                  && !decor.get().isAttachedToWindow()
                  && RgbService.instance == null
                  && active.session.closed
                  && backend.restorations == 1
                  && !RgbService.cancellationPending,
          3000,
          "Destroying the diagnostic chooser's owner did not detach the dialog and stop/restore its"
              + " lighting service");
      Thread.sleep(700);
      t.check(
          clicked.get()
              && SystemClock.elapsedRealtime() - started < 6500
              && backend.frames.size() == 1
              && backend.frames.get(0).equals(diagnosticFrame(2, 0x191919))
              && !RgbSession.recovery(c).contains("baseline")
              && RgbService.instance == null,
          "Finishing the real secondary-screen diagnostic owner detaches its dialog and restores"
              + " stock before natural expiry without later frames");
    } finally {
      if (!owner.isDestroyed()) t.runOnMainSync(owner::finish);
    }
  }

  private RgbToolsChecks() {}
}
