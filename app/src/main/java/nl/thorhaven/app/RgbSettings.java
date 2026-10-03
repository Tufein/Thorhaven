package nl.thorhaven.app;

import android.content.Context;
import java.io.IOException;
import java.util.*;
import org.json.*;

/** Portable RGB Studio settings. Saving a profile never starts a hardware session. */
final class RgbSettings {
  static final String KEY = "rgbStudio";
  static final String[] EFFECTS = {
    "solid", "breathe", "rainbow", "cycle", "pulse", "battery", "charging"
  };
  static final int MAX_BYTES = 65536;

  static JSONObject side(String color, String secondary) throws JSONException {
    return new JSONObject()
        .put("enabled", true)
        .put("color", color)
        .put("secondary", secondary)
        .put("brightness", 25)
        .put("effect", "solid")
        .put("speedMs", 6000);
  }

  static JSONObject defaultProfile() {
    try {
      return new JSONObject()
          .put("left", side("#62E5C0", "#D56CEF"))
          .put("right", side("#D56CEF", "#62E5C0"));
    } catch (JSONException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  static JSONObject defaults() {
    try {
      return new JSONObject()
          .put("schema", 1)
          .put("profile", defaultProfile())
          .put("presets", new JSONArray())
          .put("apps", new JSONObject())
          .put("followScreen", false)
          .put("lowBatteryDim", true)
          .put("screenOffPause", true)
          .put("timerMinutes", 0)
          .put("fps", 5);
    } catch (JSONException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  static JSONObject load(Context c) {
    String raw = Store.prefs(c).getString(KEY, "");
    if (raw == null || raw.isEmpty()) return defaults();
    try {
      validate(raw);
      return new JSONObject(raw);
    } catch (Exception invalid) {
      return defaults();
    }
  }

  interface Update {
    void apply(JSONObject settings) throws Exception;
  }

  /** Mutate one current, detached snapshot; invalid changes never reach preferences. */
  static synchronized void update(Context c, Update change) throws Exception {
    JSONObject settings = new JSONObject(load(c).toString());
    change.apply(settings);
    save(c, settings);
  }

  static synchronized void save(Context c, JSONObject settings) throws Exception {
    validateSettings(settings);
    String raw = settings.toString();
    validate(raw);
    if (!Store.prefs(c).edit().putString(KEY, raw).commit())
      throw new IOException("RGB settings could not be saved");
  }

  static void validate(String raw) throws Exception {
    if (raw == null || raw.length() > MAX_BYTES || raw.isEmpty())
      throw new IOException("Invalid RGB settings size");
    JSONTokener tokener = new JSONTokener(raw);
    Object parsed = tokener.nextValue();
    if (!(parsed instanceof JSONObject) || tokener.nextClean() != 0)
      throw new IOException("Invalid RGB settings document");
    validateSettings((JSONObject) parsed);
  }

  static void validateSettings(JSONObject settings) throws Exception {
    keys(
        settings,
        "schema",
        "profile",
        "presets",
        "apps",
        "followScreen",
        "lowBatteryDim",
        "screenOffPause",
        "timerMinutes",
        "fps");
    if (integer(settings, "schema", 1, 1) != 1) throw new IOException("Unknown RGB schema");
    validateProfile(object(settings, "profile"));
    bool(settings, "followScreen");
    bool(settings, "lowBatteryDim");
    bool(settings, "screenOffPause");
    int timer = integer(settings, "timerMinutes", 0, 60);
    if (!Arrays.asList(0, 5, 15, 30, 60).contains(timer))
      throw new IOException("Invalid RGB timer");
    int fps = integer(settings, "fps", 2, 10);
    if (!Arrays.asList(2, 5, 10).contains(fps)) throw new IOException("Invalid RGB update rate");
    Object rawPresets = settings.get("presets");
    if (!(rawPresets instanceof JSONArray)) throw new IOException("Invalid RGB presets");
    JSONArray presets = (JSONArray) rawPresets;
    if (presets.length() > 20) throw new IOException("Too many RGB presets");
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < presets.length(); i++) {
      Object value = presets.get(i);
      if (!(value instanceof JSONObject)) throw new IOException("Invalid RGB preset");
      JSONObject preset = (JSONObject) value;
      keys(preset, "id", "name", "profile");
      String id = string(preset, "id");
      if (!id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,39}") || !ids.add(id))
        throw new IOException("Invalid or duplicate RGB preset id");
      String name = string(preset, "name");
      if (!name.equals(name.trim())
          || name.isEmpty()
          || name.length() > 60
          || name.codePoints().anyMatch(Character::isISOControl))
        throw new IOException("Invalid RGB preset name");
      validateProfile(object(preset, "profile"));
    }
    JSONObject apps = object(settings, "apps");
    if (apps.length() > 64) throw new IOException("Too many RGB app assignments");
    for (Iterator<String> it = apps.keys(); it.hasNext(); ) {
      String pkg = it.next();
      if (!packageName(pkg) || !ids.contains(string(apps, pkg)))
        throw new IOException("Invalid RGB app assignment");
    }
    if (settings.toString().length() > MAX_BYTES) throw new IOException("RGB settings too large");
  }

  static void validateProfile(JSONObject profile) throws Exception {
    keys(profile, "left", "right");
    for (String name : new String[] {"left", "right"}) {
      JSONObject side = object(profile, name);
      keys(side, "enabled", "color", "secondary", "brightness", "effect", "speedMs");
      bool(side, "enabled");
      color(string(side, "color"));
      color(string(side, "secondary"));
      integer(side, "brightness", 0, 100);
      integer(side, "speedMs", 2000, 20000);
      if (!Arrays.asList(EFFECTS).contains(string(side, "effect")))
        throw new IOException("Unknown RGB effect");
    }
  }

  static JSONObject profile(Context c, String pkg) {
    JSONObject settings = load(c);
    try {
      String id = settings.getJSONObject("apps").optString(pkg == null ? "" : pkg, "");
      JSONArray presets = settings.getJSONArray("presets");
      for (int i = 0; i < presets.length(); i++) {
        JSONObject p = presets.getJSONObject(i);
        if (id.equals(p.getString("id")))
          return new JSONObject(p.getJSONObject("profile").toString());
      }
      return new JSONObject(settings.getJSONObject("profile").toString());
    } catch (JSONException impossible) {
      return defaultProfile();
    }
  }

  static JSONObject object(JSONObject parent, String key) throws Exception {
    Object raw = parent.get(key);
    if (!(raw instanceof JSONObject)) throw new IOException("Invalid RGB object: " + key);
    return (JSONObject) raw;
  }

  static void keys(JSONObject value, String... allowed) throws Exception {
    Set<String> expected = new HashSet<>(Arrays.asList(allowed));
    if (value == null || value.length() != expected.size())
      throw new IOException("Incomplete or unknown RGB fields");
    for (Iterator<String> it = value.keys(); it.hasNext(); )
      if (!expected.contains(it.next())) throw new IOException("Unknown RGB field");
  }

  static int integer(JSONObject value, String key, int min, int max) throws Exception {
    Object raw = value.get(key);
    if (!(raw instanceof Integer) && !(raw instanceof Long))
      throw new IOException("Invalid RGB integer: " + key);
    long number = ((Number) raw).longValue();
    if (number < min || number > max) throw new IOException("RGB integer out of range: " + key);
    return (int) number;
  }

  static boolean bool(JSONObject value, String key) throws Exception {
    Object raw = value.get(key);
    if (!(raw instanceof Boolean)) throw new IOException("Invalid RGB boolean: " + key);
    return (Boolean) raw;
  }

  static String string(JSONObject value, String key) throws Exception {
    Object raw = value.get(key);
    if (!(raw instanceof String)) throw new IOException("Invalid RGB text: " + key);
    return (String) raw;
  }

  static int color(String hex) throws IOException {
    if (hex == null || !hex.matches("#[a-fA-F0-9]{6}"))
      throw new IOException("RGB color must be #RRGGBB");
    return Integer.parseInt(hex.substring(1), 16);
  }

  static boolean packageName(String pkg) {
    return pkg != null
        && pkg.length() <= 200
        && pkg.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+");
  }

  private RgbSettings() {}
}
