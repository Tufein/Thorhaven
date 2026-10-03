package nl.thorhaven.app;

import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Typed vendor operations. The PServer parcel protocol is adapted from MIT OdinTools. */
final class DeviceControl {
  interface Runner {
    String run(String command) throws Exception;
  }

  static final String[] KEYS = {
    "is_quick_set_performance_and_fan_enable",
    "performance_mode",
    "fan_mode",
    "joystick_led_light_picker_color",
    "joystick_light_enabled",
    "led_light_brightness_percent"
  };

  static final String[] RGB_PATHS = {
    "/sys/class/sn3112l/led/enable", "/sys/class/sn3112l/led/brightness",
    "/sys/class/sn3112r/led/enable", "/sys/class/sn3112r/led/brightness"
  };

  static synchronized String call(String request) {
    try {
      JSONObject q = new JSONObject(request);
      String op = q.getString("op");
      Runner runner = root();
      if (op.equals("probe") || op.equals("start")) return nativeCall(runner, q);
      String model = runner.run("getprop ro.product.model").trim();
      String vendor = runner.run("getprop ro.vendor.retro.name").trim();
      if (!((model + " " + vendor).toLowerCase(Locale.ROOT).contains("thor")))
        throw new IOException("Deze systeemregelaars zijn alleen beschikbaar op een AYN Thor.");
      if (op.equals("key")) {
        return sendKey(runner, q).toString();
      }
      if (op.equals("inputStatus")) {
        String help = runner.run("input help");
        return new JSONObject()
            .put("holds", help.contains("--duration"))
            .put("mouse", help.contains("mouse") && help.contains("roll"))
            .put("message", "Invoermogelijkheden gecontroleerd")
            .toString();
      }
      if (op.equals("pointer")) return sendPointer(runner, q).toString();
      if (op.equals("rgbStatus")) return rgbStatus(runner, model).toString();
      if (op.equals("rgbFrame")) return rgbFrame(runner, q).toString();
      if (op.equals("status")) return status(runner, model).toString();
      if (op.equals("capture")) return capture(runner, q.getJSONObject("values")).toString();
      if (op.equals("apply")) return apply(runner, q.getJSONObject("values")).toString();
      if (op.equals("restore")) return restore(runner, q.getJSONObject("snapshot")).toString();
      throw new IOException("Onbekende bewerking");
    } catch (Exception e) {
      return Controls.error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
          .toString();
    }
  }

  static void rgbNodes(Runner r) throws Exception {
    StringBuilder command = new StringBuilder();
    for (String path : RGB_PATHS) {
      if (command.length() > 0) command.append(" && ");
      command.append("test -f ").append(path).append(" && test -w ").append(path);
    }
    command.append(" && printf '%s' THORHAVEN_RGB_NODES_OK");
    if (!r.run(command.toString()).trim().equals("THORHAVEN_RGB_NODES_OK"))
      throw new IOException("Both Thor LED enable and brightness nodes must exist and be writable");
    String ddHelp = r.run("dd --help");
    if (!ddHelp.contains("nocreat") || !ddHelp.contains("notrunc"))
      throw new IOException("Firmware lacks a no-create RGB node writer");
  }

  static JSONObject rgbStatus(Runner r, String model) throws Exception {
    rgbNodes(r);
    JSONObject baseline = new JSONObject();
    for (String key : new String[] {RgbHardware.ENABLED, RgbHardware.COLOR, RgbHardware.BRIGHTNESS})
      baseline.put(key, read(r, key));
    RgbHardware.validateBaseline(baseline);
    return new JSONObject()
        .put("available", true)
        .put("model", model)
        .put("baseline", baseline)
        .put("message", "Thor RGB capability and actual stock lighting checked");
  }

  static JSONObject rgbFrame(Runner r, JSONObject request) throws Exception {
    RgbSettings.keys(request, "op", "frame");
    if (!RgbSettings.string(request, "op").equals("rgbFrame"))
      throw new IOException("Invalid RGB operation");
    RgbEngine.Frame frame = RgbEngine.Frame.fromJson(RgbSettings.object(request, "frame"));
    // Validate all fields and every fixed node before the first write.
    rgbNodes(r);
    String[] values = rgbValues(frame);
    StringBuilder command = new StringBuilder();
    for (int i = 0; i < values.length; i++) {
      command
          .append("printf '%s\\n' ")
          .append(quote(values[i]))
          .append(" | dd of=")
          .append(RGB_PATHS[rgbPathIndex(i)])
          .append(" conv=nocreat,notrunc status=none")
          .append(" || { printf '%s' THORHAVEN_RGB_PARTIAL_")
          .append(i)
          .append("; exit 1; }; ");
    }
    command.append("printf '%s' THORHAVEN_RGB_FRAME_OK");
    String result = r.run(command.toString()).trim();
    if (!result.equals("THORHAVEN_RGB_FRAME_OK"))
      throw new IOException(
          "RGB frame write failed; state may be partial: "
              + result.substring(0, Math.min(160, result.length())));
    return new JSONObject().put("message", "Both zones of both Thor sticks updated");
  }

  static int rgbPathIndex(int write) {
    if (write < 0 || write > 5) throw new IllegalArgumentException("Invalid RGB write");
    return write < 3 ? (write == 0 ? 0 : 1) : (write == 3 ? 2 : 3);
  }

  static String[] rgbValues(RgbEngine.Frame frame) {
    String left =
        (frame.left >>> 16 & 255)
            + ":"
            + (frame.left >>> 8 & 255)
            + ":"
            + (frame.left & 255)
            + ":255";
    String left2 =
        (frame.left2 >>> 16 & 255)
            + ":"
            + (frame.left2 >>> 8 & 255)
            + ":"
            + (frame.left2 & 255)
            + ":255";
    String right =
        (frame.right >>> 16 & 255)
            + ":"
            + (frame.right >>> 8 & 255)
            + ":"
            + (frame.right & 255)
            + ":255";
    String right2 =
        (frame.right2 >>> 16 & 255)
            + ":"
            + (frame.right2 >>> 8 & 255)
            + ":"
            + (frame.right2 & 255)
            + ":255";
    return new String[] {
      frame.leftEnabled ? "1" : "0",
      "1-" + left,
      "2-" + left2,
      frame.rightEnabled ? "1" : "0",
      "1-" + right,
      "2-" + right2
    };
  }

  static JSONObject sendKey(Runner runner, JSONObject q) throws Exception {
    String command = keyCommand(q);
    int duration = q.optInt("duration", 0);
    if (duration > 0 && !runner.run("input help").contains("--duration"))
      throw new IOException(
          "Deze firmware ondersteunt geen begrensde knopdruk. Gebruik tikacties.");
    inputResult(runner.run(command));
    return new JSONObject()
        .put("message", duration > 0 ? "Begrensde knopdruk verstuurd" : "Key sent");
  }

  static String keyCommand(JSONObject q) throws Exception {
    int code = inputInt(q, "code", 0, 255), display = inputInt(q, "display", 0, 1000);
    if (!TouchControls.allowed(code)) throw new IOException("Invalid key action");
    int duration = q.has("duration") ? inputInt(q, "duration", 0, 1500) : 0;
    if (duration > 0 && duration < 50) throw new IOException("Invalid key duration");
    String source = code >= 96 && code <= 110 ? "gamepad" : "keyboard";
    return "input "
        + source
        + " -d "
        + display
        + " keyevent "
        + (duration == 0 ? "" : "--duration " + duration + " ")
        + code;
  }

  static int inputInt(JSONObject q, String key, int min, int max) throws Exception {
    Object raw = q.get(key);
    if (!(raw instanceof Number)) throw new IOException("Invalid numeric input");
    double d = ((Number) raw).doubleValue();
    if (!Double.isFinite(d) || d != Math.rint(d) || d < min || d > max)
      throw new IOException("Invalid numeric input");
    return (int) d;
  }

  static void inputResult(String result) throws Exception {
    if (result.contains("Exception")
        || result.contains("Error:")
        || result.contains("Unknown command")
        || result.contains("Invalid arguments")
        || result.contains("Usage:"))
      throw new IOException("Firmware heeft deze invoeractie geweigerd");
  }

  static JSONObject sendPointer(Runner runner, JSONObject q) throws Exception {
    inputResult(runner.run(pointerCommand(q)));
    return new JSONObject().put("message", "Aanwijzeractie verstuurd");
  }

  /** A complete bounded gesture, never an independently held DOWN or an arbitrary shell string. */
  static String pointerCommand(JSONObject q) throws Exception {
    int display = inputInt(q, "display", 0, 1000);
    int width = inputInt(q, "width", 1, 8192), height = inputInt(q, "height", 1, 8192);
    int x = inputInt(q, "x", 0, width - 1), y = inputInt(q, "y", 0, height - 1);
    String mode = q.getString("mode"), action = q.getString("action");
    if (!mode.equals("touch") && !mode.equals("mouse"))
      throw new IOException("Invalid pointer source");
    String prefix = "input " + (mode.equals("mouse") ? "mouse" : "touchscreen") + " -d " + display;
    if (action.equals("move") && mode.equals("mouse")) return prefix + " roll " + x + " " + y;
    if (action.equals("tap")) return prefix + " tap " + x + " " + y;
    if (!action.equals("swipe")) throw new IOException("Invalid pointer action");
    int x2 = inputInt(q, "x2", 0, width - 1), y2 = inputInt(q, "y2", 0, height - 1);
    int duration = inputInt(q, "duration", 80, 1500);
    return prefix + " swipe " + x + " " + y + " " + x2 + " " + y2 + " " + duration;
  }

  static Runner root() throws Exception {
    if (android.os.Process.myUid() == 0)
      return cmd -> SystemBridge.command("/system/bin/sh", "-c", cmd).trim();
    IBinder binder;
    try {
      binder =
          (IBinder)
              Class.forName("android.os.ServiceManager")
                  .getDeclaredMethod("getService", String.class)
                  .invoke(null, "PServerBinder");
    } catch (Exception e) {
      throw new IOException(
          "Roottoegang nodig: koppel Shizuku in rootmodus, of gebruik firmware met AYN PServer.");
    }
    if (binder == null)
      throw new IOException("Geen AYN-rootservice. Koppel Shizuku in rootmodus bij Instellen.");
    Runner r =
        cmd -> {
          Parcel data = Parcel.obtain(), reply = Parcel.obtain();
          try {
            data.writeStringArray(new String[] {cmd, "1"});
            if (!binder.transact(0, data, reply, 0))
              throw new IOException("AYN-rootservice weigert de aanvraag");
            byte[] bytes = reply.createByteArray();
            if (bytes == null || bytes.length > 1000000)
              throw new IOException("Geen geldig antwoord van AYN-rootservice");
            return new String(bytes, StandardCharsets.UTF_8).trim();
          } finally {
            data.recycle();
            reply.recycle();
          }
        };
    if (!r.run("id -u").equals("0")) throw new IOException("AYN-service biedt geen roottoegang");
    return r;
  }

  static String quote(String s) {
    return "'" + s.replace("'", "'\\''") + "'";
  }

  static boolean known(String key) {
    return Arrays.asList(KEYS).contains(key);
  }

  static String read(Runner r, String key) throws Exception {
    return r.run("settings get system " + key).trim();
  }

  static void write(Runner r, String key, String value) throws Exception {
    validate(key, value);
    r.run("settings put system " + key + " " + quote(value));
    if (!read(r, key).equals(value))
      throw new IOException("Firmware heeft " + key + " niet toegepast");
  }

  static void validate(String key, String value) throws Exception {
    if (!known(key)) throw new IOException("Onbekende systeeminstelling");
    boolean ok;
    switch (key) {
      case "is_quick_set_performance_and_fan_enable":
        ok = value.matches("[01]");
        break;
      case "performance_mode":
        ok = value.matches("[012]");
        break;
      case "fan_mode":
        ok = value.matches("[0145]");
        break;
      case "joystick_led_light_picker_color":
        ok = value.matches("#[A-Fa-f0-9]{8},#[A-Fa-f0-9]{8}");
        break;
      case "joystick_light_enabled":
        ok = value.matches("[01],[01]");
        break;
      default:
        float f = Float.parseFloat(value);
        ok = Float.isFinite(f) && f >= 0 && f <= 1;
    }
    if (!ok) throw new IOException("Ongeldige waarde voor " + key);
  }

  static String cpuPath(int policy) {
    if (policy < 0 || policy > 15) throw new IllegalArgumentException("CPU-cluster");
    return "/sys/devices/system/cpu/cpufreq/policy" + policy + "/";
  }

  static String file(Runner r, String path) throws Exception {
    return r.run("cat " + path + " 2>/dev/null || true").trim();
  }

  static JSONObject status(Runner r, String model) throws Exception {
    JSONObject s = new JSONObject().put("model", model), settings = new JSONObject();
    for (String k : KEYS) {
      String v = read(r, k);
      if (!v.equals("null") && !v.isEmpty()) {
        try {
          validate(k, v);
          settings.put(k, v);
        } catch (Exception unsupported) {
        }
      }
    }
    s.put("settings", settings);
    JSONArray clusters = new JSONArray();
    for (int i = 0; i < 16; i++) {
      String p = cpuPath(i), f = file(r, p + "scaling_available_frequencies");
      if (f.isEmpty()) continue;
      JSONArray values = new JSONArray();
      for (String n : f.split("\\s+")) values.put(Integer.parseInt(n));
      clusters.put(
          new JSONObject()
              .put("policy", i)
              .put("frequencies", values)
              .put("min", Integer.parseInt(file(r, p + "scaling_min_freq")))
              .put("max", Integer.parseInt(file(r, p + "scaling_max_freq"))));
    }
    return s.put("clusters", clusters);
  }

  static void cap(Runner r, int policy, int value) throws Exception {
    String p = cpuPath(policy), freq = file(r, p + "scaling_available_frequencies");
    boolean valid = false;
    for (String n : freq.split("\\s+")) if (n.equals(Integer.toString(value))) valid = true;
    int min = Integer.parseInt(file(r, p + "scaling_min_freq")),
        max = Integer.parseInt(file(r, p + "cpuinfo_max_freq"));
    if (!valid || value < min || value > max)
      throw new IOException("CPU-limiet valt buiten de beschikbare frequenties");
    r.run("printf '%s' " + quote(Integer.toString(value)) + " > " + p + "scaling_max_freq");
    if (!file(r, p + "scaling_max_freq").equals(Integer.toString(value)))
      throw new IOException("CPU-limiet geweigerd");
  }

  static JSONObject capture(Runner r, JSONObject values) throws Exception {
    JSONObject before = new JSONObject();
    // Validate the whole request before the first write, including every baseline.
    Iterator<String> it = values.keys();
    while (it.hasNext()) {
      String k = it.next(), v = values.getString(k);
      if (k.matches("cpu:[0-9]{1,2}")) {
        int policy = Integer.parseInt(k.substring(4));
        String current = file(r, cpuPath(policy) + "scaling_max_freq");
        Integer.parseInt(current);
        int cap = Integer.parseInt(v);
        String p = cpuPath(policy), frequencies = file(r, p + "scaling_available_frequencies");
        boolean available = false;
        for (String n : frequencies.split("\\s+")) if (n.equals(v)) available = true;
        if (!available
            || cap < Integer.parseInt(file(r, p + "scaling_min_freq"))
            || cap > Integer.parseInt(file(r, p + "cpuinfo_max_freq")))
          throw new IOException("CPU-limiet valt buiten beschikbare frequenties");
        before.put(k, current);
      } else {
        validate(k, v);
        if (k.equals("fan_mode") && v.equals("0"))
          throw new IOException("Ventilator uitschakelen wordt niet aangeboden");
        String current = read(r, k);
        validate(k, current);
        before.put(k, current);
      }
    }
    String perf = values.optString("performance_mode", read(r, "performance_mode")),
        fan = values.optString("fan_mode", read(r, "fan_mode"));
    if (perf.equals("2") && (fan.equals("0") || fan.equals("1")))
      throw new IOException("Hoge prestaties vereist Smart of Sport als fanstand");
    return before;
  }

  static JSONObject apply(Runner r, JSONObject values) throws Exception {
    JSONObject before = capture(r, values), after = new JSONObject();
    try {
      for (String k : KEYS)
        if (values.has(k)) {
          write(r, k, values.getString(k));
          after.put(k, values.getString(k));
        }
      Iterator<String> it = values.keys();
      while (it.hasNext()) {
        String k = it.next();
        if (k.startsWith("cpu:")) {
          cap(r, Integer.parseInt(k.substring(4)), Integer.parseInt(values.getString(k)));
          after.put(k, values.getString(k));
        }
      }
    } catch (Exception e) {
      try {
        java.util.Iterator<String> keys = before.keys();
        while (keys.hasNext()) {
          String k = keys.next();
          String old = before.getString(k);
          if (k.startsWith("cpu:")) {
            int policy = Integer.parseInt(k.substring(4));
            String current = file(r, cpuPath(policy) + "scaling_max_freq");
            if (!current.equals(old)) cap(r, policy, Integer.parseInt(old));
          } else if (!read(r, k).equals(old)) write(r, k, old);
        }
      } catch (Exception rollback) {
        throw new IOException(e.getMessage() + "; herstel mislukt: " + rollback.getMessage());
      }
      throw e;
    }
    return new JSONObject()
        .put("before", before)
        .put("after", after)
        .put("message", "Instellingen toegepast en teruggelezen");
  }

  static JSONObject restore(Runner r, JSONObject snapshot) throws Exception {
    JSONObject before = snapshot.getJSONObject("before"), after = snapshot.getJSONObject("after");
    int restored = 0, skipped = 0;
    Iterator<String> it = before.keys();
    while (it.hasNext()) {
      String k = it.next();
      if (!after.has(k)) continue;
      String old = before.getString(k), last = after.getString(k);
      if (k.matches("cpu:[0-9]{1,2}")) {
        int p = Integer.parseInt(k.substring(4));
        if (!file(r, cpuPath(p) + "scaling_max_freq").equals(last)) {
          skipped++;
          continue;
        }
        cap(r, p, Integer.parseInt(old));
      } else {
        validate(k, old);
        if (!read(r, k).equals(last)) {
          skipped++;
          continue;
        }
        write(r, k, old);
      }
      restored++;
    }
    return new JSONObject()
        .put(
            "message",
            restored
                + " instellingen hersteld; "
                + skipped
                + " elders gewijzigde instellingen overgeslagen");
  }

  static String nativeCall(Runner r, JSONObject q) throws Exception {
    int uid = q.getInt("uid");
    if (uid < 10000 || uid > 2000000) throw new IOException("Ongeldige app-identiteit");
    String source = q.getString("source"), sha = q.getString("sha");
    String prefix = "/storage/emulated/" + (uid / 100000) + "/Android/data/nl.thorhaven.app/files/";
    if (!source.equals(prefix + "thorpad") || !sha.matches("[a-f0-9]{64}"))
      throw new IOException("Ongeldige helperlocatie");
    String dir = "/data/local/tmp/thorhaven-" + uid, path = dir + "/thorpad";
    r.run(
        "mkdir -p "
            + dir
            + " && chmod 700 "
            + dir
            + " && cp "
            + quote(source)
            + " "
            + path
            + ".new && chmod 700 "
            + path
            + ".new && mv "
            + path
            + ".new "
            + path);
    if (!r.run("sha256sum " + path).startsWith(sha + " "))
      throw new IOException("Helpercontrole mislukt");
    if (q.getString("op").equals("probe")) return r.run(path + " --probe");
    String nonce = q.getString("nonce");
    if (!nonce.matches("[a-f0-9]{32}")) throw new IOException("Ongeldige sessie");
    r.run(
        "nohup "
            + path
            + " --serve "
            + uid
            + " "
            + nonce
            + " </dev/null >"
            + dir
            + "/session.log 2>&1 &");
    return new JSONObject().put("message", "Invoerlaag gestart").toString();
  }
}
