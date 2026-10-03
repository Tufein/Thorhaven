package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Optional Select chords. No delayed single presses or Home/AYN interception. */
final class Shortcuts {
  static final int[] KEYS = {108, 96, 97, 99, 100, 102, 103, 19, 20, 106, 107};
  static final String[] ACTIONS = {
    "disabled",
    "panel",
    "guide",
    "notes",
    "move",
    "swap",
    "black",
    "volumeDown",
    "volumeUp",
    "brightnessUp",
    "brightnessDown",
    "appTop",
    "appBottom",
    "pair",
    "page"
  };

  static JSONObject defaults() {
    JSONObject j = new JSONObject();
    String[] actions = {
      "panel",
      "guide",
      "black",
      "move",
      "notes",
      "volumeDown",
      "volumeUp",
      "brightnessUp",
      "brightnessDown",
      "disabled",
      "disabled"
    };
    try {
      for (int i = 0; i < KEYS.length; i++)
        j.put("" + KEYS[i], new JSONObject().put("action", actions[i]));
    } catch (Exception ignored) {
    }
    return j;
  }

  static JSONObject bindings(Context c) {
    String raw = Store.prefs(c).getString("shortcuts", "");
    if (raw.isEmpty()) return defaults();
    try {
      return validate(raw);
    } catch (Exception e) {
      return defaults();
    }
  }

  static JSONObject validate(String raw) throws Exception {
    JSONObject j = new JSONObject(raw);
    if (j.length() > KEYS.length) throw new Exception("Invalid shortcuts");
    for (Iterator<String> it = j.keys(); it.hasNext(); ) {
      String key = it.next();
      boolean allowed = false;
      for (int k : KEYS) if (key.equals("" + k)) allowed = true;
      if (!allowed) throw new Exception("Invalid shortcut key");
      JSONObject b = j.getJSONObject(key);
      String action = b.getString("action");
      if (!Arrays.asList(ACTIONS).contains(action)) throw new Exception("Invalid shortcut action");
      if (action.equals("appTop") || action.equals("appBottom"))
        OfflineGuides.valid(b.getString("pkg"));
      if (action.equals("pair")) {
        JSONObject p = b.getJSONObject("pair");
        OfflineGuides.valid(p.getString("top"));
        OfflineGuides.valid(p.getString("bottom"));
        if (p.getString("top").equals(p.getString("bottom")) || p.getString("name").length() > 180)
          throw new Exception("Invalid app pair");
      }
      if (action.equals("page") && !Arrays.asList(MainActivity.PAGES).contains(b.getString("page")))
        throw new Exception("Invalid page");
      if (b.length() > 3) throw new Exception("Invalid shortcut parameters");
    }
    return j;
  }

  static JSONObject binding(Context c, int key) {
    return bindings(c).optJSONObject("" + key);
  }

  static String label(Context c, String action) {
    String[] labels = {
      "Uitgeschakeld",
      "Snelpaneel",
      "Offline gids",
      "Notities",
      "Live verplaatsen",
      "Schermen wisselen",
      "Onderste scherm zwart",
      "Volume lager",
      "Volume hoger",
      "Helderheid hoger",
      "Helderheid lager",
      "App openen boven",
      "App openen onder",
      "App-paar openen",
      "Thorhaven-pagina"
    };
    int i = Arrays.asList(ACTIONS).indexOf(action);
    return Language.text(c, i < 0 ? action : labels[i]);
  }

  static void page(MainActivity a, LinearLayout parent) {
    LinearLayout card =
        Ui.card(
            a,
            parent,
            "Eigen sneltoetsen",
            "Select + knop. Uitgeschakelde acties laten de tweede knop door. Select blijft"
                + " onderschept zolang combinaties aan staan.");
    JSONObject bindings = bindings(a);
    for (int key : KEYS) {
      JSONObject b = bindings.optJSONObject("" + key);
      String suffix =
          b == null ? label(a, "disabled") : label(a, b.optString("action", "disabled"));
      if (b != null && b.has("pkg")) suffix += " · " + Store.name(a, b.optString("pkg"));
      if (b != null && b.has("page")) suffix += " · " + Language.text(a, b.optString("page"));
      card.addView(
          Ui.button(
              a,
              "Select + "
                  + KeyEvent.keyCodeToString(key)
                      .replace("KEYCODE_BUTTON_", "")
                      .replace("KEYCODE_DPAD_", "D-pad ")
                  + " · "
                  + suffix,
              () -> edit(a, key)));
    }
    card.addView(
        Ui.button(
            a,
            "Herstel standaardcombinaties",
            () -> {
              Store.prefs(a).edit().remove("shortcuts").apply();
              a.render();
            }));
  }

  static void edit(MainActivity a, int key) {
    String[] labels = new String[ACTIONS.length];
    for (int i = 0; i < labels.length; i++) labels[i] = label(a, ACTIONS[i]);
    new Ui.Dialog(a)
        .setTitle("Actie kiezen")
        .setItems(
            labels,
            (d, index) -> {
              String action = ACTIONS[index];
              JSONObject b = new JSONObject();
              try {
                b.put("action", action);
              } catch (Exception ignored) {
              }
              if (action.equals("appTop") || action.equals("appBottom")) {
                List<Store.App> apps = Store.apps(a);
                String[] names = new String[apps.size()];
                for (int i = 0; i < names.length; i++) names[i] = apps.get(i).name;
                new Ui.Dialog(a)
                    .setTitle("Kies een app")
                    .setItems(
                        names,
                        (x, i) -> {
                          try {
                            b.put("pkg", apps.get(i).pkg);
                            save(a, key, b);
                          } catch (Exception e) {
                            Ui.toast(a, e.getMessage());
                          }
                        })
                    .show();
              } else if (action.equals("pair")) {
                JSONArray pairs = Store.pairs(a);
                if (pairs.length() == 0) {
                  Ui.toast(a, "Maak eerst een app-paar.");
                  return;
                }
                String[] names = new String[pairs.length()];
                for (int i = 0; i < names.length; i++)
                  names[i] = pairs.optJSONObject(i).optString("name");
                new Ui.Dialog(a)
                    .setTitle("App-paar kiezen")
                    .setItems(
                        names,
                        (x, i) -> {
                          try {
                            b.put("pair", pairs.getJSONObject(i));
                            save(a, key, b);
                          } catch (Exception e) {
                            Ui.toast(a, e.getMessage());
                          }
                        })
                    .show();
              } else if (action.equals("page"))
                new Ui.Dialog(a)
                    .setTitle("Pagina kiezen")
                    .setItems(
                        MainActivity.PAGES,
                        (x, i) -> {
                          try {
                            b.put("page", MainActivity.PAGES[i]);
                            save(a, key, b);
                          } catch (Exception e) {
                            Ui.toast(a, e.getMessage());
                          }
                        })
                    .show();
              else save(a, key, b);
            })
        .setNegativeButton("Annuleren", null)
        .show();
  }

  static void save(MainActivity a, int key, JSONObject b) {
    try {
      JSONObject j = bindings(a);
      j.put("" + key, b);
      validate(j.toString());
      Store.prefs(a).edit().putString("shortcuts", j.toString()).commit();
      a.render();
    } catch (Exception e) {
      Ui.toast(a, e.getMessage());
    }
  }

  static boolean run(ThorService service, int key) {
    JSONObject b = binding(service, key);
    String action = b == null ? "disabled" : b.optString("action", "disabled");
    if (action.equals("disabled")) return false;
    String pkg =
        service.foreground.isEmpty()
            ? Store.prefs(service).getString("last", "")
            : service.foreground;
    try {
      switch (action) {
        case "panel":
          if (service.panel == null) service.showPanel();
          else service.hidePanel();
          break;
        case "guide":
          service.hidePanel();
          if (!pkg.isEmpty()) OfflineGuides.open(service, pkg);
          break;
        case "black":
          service.hidePanel();
          if (service.cover != null) service.cover.show();
          break;
        case "move":
          service.hidePanel();
          if (!pkg.isEmpty())
            Bridge.move(
                service,
                pkg,
                service.foregroundDisplay == Store.screen(service, false)
                    ? Store.screen(service, true)
                    : Store.screen(service, false));
          break;
        case "swap":
          service.hidePanel();
          Bridge.swap(service);
          break;
        case "notes":
          openPage(service, "Notities");
          break;
        case "page":
          openPage(service, b.getString("page"));
          break;
        case "appTop":
        case "appBottom":
          service.hidePanel();
          Store.launch(
              service, b.getString("pkg"), Store.screen(service, action.equals("appBottom")));
          break;
        case "pair":
          service.hidePanel();
          Store.openPair(service, b.getJSONObject("pair"));
          break;
        case "volumeDown":
        case "volumeUp":
          service
              .getSystemService(android.media.AudioManager.class)
              .adjustStreamVolume(
                  android.media.AudioManager.STREAM_MUSIC,
                  action.equals("volumeUp") ? 1 : -1,
                  android.media.AudioManager.FLAG_SHOW_UI);
          break;
        case "brightnessUp":
        case "brightnessDown":
          if (android.provider.Settings.System.canWrite(service)) {
            int value =
                android.provider.Settings.System.getInt(
                    service.getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS,
                    128);
            android.provider.Settings.System.putInt(
                service.getContentResolver(),
                android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                0);
            android.provider.Settings.System.putInt(
                service.getContentResolver(),
                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                Math.max(8, Math.min(255, value + (action.equals("brightnessUp") ? 20 : -20))));
          } else Ui.toast(service, "Activeer helderheidstoegang in Thorhaven.");
          break;
      }
    } catch (Exception e) {
      Ui.toast(service, "Sneltoets mislukt: " + e.getMessage());
    }
    return true;
  }

  static void openPage(ThorService s, String page) {
    s.hidePanel();
    int target = Store.screen(s, true);
    s.startActivity(
        new Intent(s, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("pageKey", page),
        ActivityOptions.makeBasic()
            .setLaunchDisplayId(target >= 0 ? target : Store.screen(s, false))
            .toBundle());
  }
}
