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

  static JSONObject sendKey(Runner runner, JSONObject q) throws Exception {
    int code = q.getInt("code"), display = q.getInt("display");
    boolean allowed = false;
    for (int k : TouchControls.CODES) if (k == code) allowed = true;
    if (!allowed || display < 0 || display > 1000) throw new IOException("Invalid key action");
    String source = code >= 96 && code <= 110 ? "gamepad" : "keyboard";
    runner.run("input " + source + " -d " + display + " keyevent " + code);
    return new JSONObject().put("message", "Key sent");
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
