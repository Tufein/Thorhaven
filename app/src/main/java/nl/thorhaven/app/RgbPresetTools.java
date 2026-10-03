package nl.thorhaven.app;

import android.content.Context;
import java.io.IOException;
import java.util.*;
import org.json.*;

/** Original transactional preset operations. Saving never starts a lighting session. */
final class RgbPresetTools {
  static JSONObject require(JSONObject settings, String id) throws Exception {
    if (id == null || id.isEmpty()) throw new IOException("Select an existing RGB preset");
    JSONArray list = settings.getJSONArray("presets");
    for (int i = 0; i < list.length(); i++) {
      JSONObject preset = list.getJSONObject(i);
      if (id.equals(preset.getString("id"))) return preset;
    }
    throw new IOException("RGB preset no longer exists");
  }

  static JSONObject preset(Context c, String id) throws Exception {
    return new JSONObject(require(RgbSettings.load(c), id).toString());
  }

  static void edit(Context c, String id, JSONObject profile) throws Exception {
    RgbSettings.validateProfile(profile);
    JSONObject detached = new JSONObject(profile.toString());
    RgbSettings.update(c, settings -> require(settings, id).put("profile", detached));
  }

  static String duplicate(Context c, String id, String name) throws Exception {
    final String[] created = {null};
    RgbSettings.update(
        c,
        settings -> {
          JSONObject source = require(settings, id);
          created[0] = append(settings, name, source.getJSONObject("profile"));
        });
    return created[0];
  }

  static String add(Context c, String name, JSONObject profile) throws Exception {
    RgbSettings.validateProfile(profile);
    JSONObject detached = new JSONObject(profile.toString());
    final String[] created = {null};
    RgbSettings.update(c, settings -> created[0] = append(settings, name, detached));
    return created[0];
  }

  static String append(JSONObject settings, String name, JSONObject profile) throws Exception {
    if (settings.getJSONArray("presets").length() >= 20)
      throw new IOException("You can save up to 20 RGB presets");
    if (name == null) throw new IOException("Enter an RGB preset name");
    String id;
    do {
      id = "p" + UUID.randomUUID().toString().replace("-", "");
    } while (contains(settings, id));
    settings
        .getJSONArray("presets")
        .put(
            new JSONObject()
                .put("id", id)
                .put("name", name.trim())
                .put("profile", new JSONObject(profile.toString())));
    // Validate the whole candidate before exposing it to another caller or saving it.
    RgbSettings.validateSettings(settings);
    return id;
  }

  static boolean contains(JSONObject settings, String id) throws Exception {
    JSONArray list = settings.getJSONArray("presets");
    for (int i = 0; i < list.length(); i++)
      if (id.equals(list.getJSONObject(i).getString("id"))) return true;
    return false;
  }

  static void rename(Context c, String id, String name) throws Exception {
    if (name == null) throw new IOException("Enter an RGB preset name");
    RgbSettings.update(c, settings -> require(settings, id).put("name", name.trim()));
  }

  static void useGlobal(Context c, String id) throws Exception {
    RgbSettings.update(
        c,
        settings ->
            settings.put(
                "profile",
                new JSONObject(require(settings, id).getJSONObject("profile").toString())));
  }

  static void updateFromGlobal(Context c, String id) throws Exception {
    RgbSettings.update(
        c,
        settings ->
            require(settings, id)
                .put("profile", new JSONObject(settings.getJSONObject("profile").toString())));
  }

  static void remove(Context c, String id) throws Exception {
    RgbSettings.update(
        c,
        settings -> {
          require(settings, id);
          JSONArray list = settings.getJSONArray("presets");
          for (int i = list.length() - 1; i >= 0; i--)
            if (id.equals(list.getJSONObject(i).getString("id"))) list.remove(i);
          JSONObject apps = settings.getJSONObject("apps");
          for (Iterator<String> it = apps.keys(); it.hasNext(); )
            if (id.equals(apps.getString(it.next()))) it.remove();
        });
  }

  static void assign(Context c, String pkg, String id) throws Exception {
    if (!RgbSettings.packageName(pkg)) throw new IOException("Select a valid Android app");
    if (id == null) throw new IOException("Select an RGB preset");
    RgbSettings.update(
        c,
        settings -> {
          if (id.isEmpty()) settings.getJSONObject("apps").remove(pkg);
          else {
            require(settings, id);
            settings.getJSONObject("apps").put(pkg, id);
          }
        });
  }

  static int assignedApps(JSONObject settings, String id) {
    int count = 0;
    JSONObject apps = settings.optJSONObject("apps");
    if (apps == null) return count;
    for (Iterator<String> it = apps.keys(); it.hasNext(); )
      if (id.equals(apps.optString(it.next()))) count++;
    return count;
  }

  static List<Store.App> matchingApps(List<Store.App> installed, String query) {
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    List<Store.App> found = new ArrayList<>();
    for (Store.App app : installed)
      if (needle.isEmpty()
          || app.name.toLowerCase(Locale.ROOT).contains(needle)
          || app.pkg.toLowerCase(Locale.ROOT).contains(needle)) found.add(app);
    return found;
  }

  private RgbPresetTools() {}
}
