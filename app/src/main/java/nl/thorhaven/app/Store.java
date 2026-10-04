package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import java.util.*;
import org.json.*;

final class Store {
  static android.content.SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("thorhaven", 0);
  }

  static final class App {
    final String pkg, name;
    final android.graphics.drawable.Drawable icon;

    App(String p, String n, android.graphics.drawable.Drawable i) {
      pkg = p;
      name = n;
      icon = i;
    }
  }

  static List<App> apps(Context c) {
    List<App> a = new ArrayList<>();
    Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    Set<String> seen = new HashSet<>();
    for (ResolveInfo r : c.getPackageManager().queryIntentActivities(i, 0)) {
      String p = r.activityInfo.packageName;
      if (!p.equals(c.getPackageName()) && seen.add(p))
        a.add(
            new App(
                p,
                r.loadLabel(c.getPackageManager()).toString(),
                r.loadIcon(c.getPackageManager())));
    }
    a.sort((x, y) -> x.name.compareToIgnoreCase(y.name));
    return a;
  }

  static List<Display> displays(Context c) {
    List<Display> out = new ArrayList<>();
    for (Display d : c.getSystemService(DisplayManager.class).getDisplays())
      if (d.isValid() && (d.getFlags() & Display.FLAG_PRIVATE) == 0) out.add(d);
    out.sort(Comparator.comparingInt(Display::getDisplayId));
    return out;
  }

  static final Map<Integer, Context> measurementContexts = new LinkedHashMap<>();

  /**
   * Measure an actual display area, independently of the caller's activity compatibility bounds.
   */
  static synchronized android.util.DisplayMetrics displayMetrics(Context c, Display display) {
    if (!display.isValid()) throw new IllegalStateException("Display unavailable");
    measurementContexts.entrySet().removeIf(e -> !e.getValue().getDisplay().isValid());
    Context visual = measurementContexts.get(display.getDisplayId());
    if (visual == null) {
      // A display context alone is not a UI context. No window is attached and no overlay
      // permission is needed just to measure the display area's configuration.
      visual =
          c.getApplicationContext()
              .createDisplayContext(display)
              .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
      if (measurementContexts.size() >= 4)
        measurementContexts.remove(measurementContexts.keySet().iterator().next());
      measurementContexts.put(display.getDisplayId(), visual);
    }
    android.util.DisplayMetrics result = new android.util.DisplayMetrics();
    result.setTo(visual.getResources().getDisplayMetrics());
    result.densityDpi = Math.max(72, visual.getResources().getConfiguration().densityDpi);
    result.density = result.densityDpi / 160f;
    android.graphics.Rect bounds =
        visual.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
    if (bounds.width() < 1 || bounds.height() < 1)
      throw new IllegalStateException("Display bounds unavailable");
    result.widthPixels = bounds.width();
    result.heightPixels = bounds.height();
    return result;
  }

  static int screen(Context c, boolean bottom) {
    List<Display> d = displays(c);
    int fallback = bottom ? (d.size() > 1 ? d.get(1).getDisplayId() : -1) : 0;
    int id = prefs(c).getInt(bottom ? "bottom" : "top", fallback);
    if (id == -1) return -1;
    for (Display x : d) if (x.getDisplayId() == id) return id;
    return fallback;
  }

  static String name(Context c, String pkg) {
    try {
      return c.getPackageManager()
          .getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0))
          .toString();
    } catch (Exception e) {
      return pkg;
    }
  }

  static String battery(Context c) {
    Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    if (b == null) return "Accu onbekend";
    int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0),
        scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
    float temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
    return (level * 100 / Math.max(1, scale))
        + "%  ·  "
        + temp
        + " °C"
        + (b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0 ? "  ·  laden" : "");
  }

  static Intent launchIntent(Context c, String pkg, int display) {
    if (pkg == null || !pkg.matches("[A-Za-z0-9_.]{1,200}") || display < 0) return null;
    Display target = c.getSystemService(DisplayManager.class).getDisplay(display);
    if (target == null || !target.isValid() || (target.getFlags() & Display.FLAG_PRIVATE) != 0)
      return null;
    Intent intent = c.getPackageManager().getLaunchIntentForPackage(pkg);
    if (intent == null) return null;
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      return c.getSystemService(ActivityManager.class)
              .isActivityStartAllowedOnDisplay(c, display, intent)
          ? intent
          : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  static void launch(Context c, String pkg, int display) {
    launchChecked(c, pkg, display);
  }

  static boolean launchChecked(Context c, String pkg, int display) {
    Intent intent = launchIntent(c, pkg, display);
    if (intent == null) {
      Ui.toast(
          c,
          display < 0
              ? "Geen tweede scherm gevonden. Kijk bij Instellen → Schermen."
              : "App of scherm niet beschikbaar. Controleer je schermkeuze en geïnstalleerde"
                  + " apps.");
      return false;
    }
    try {
      c.startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle());
    } catch (RuntimeException e) {
      Ui.toast(c, "Openen op scherm " + display + " geweigerd: " + e.getClass().getSimpleName());
      return false;
    }
    try {
      apply(c, pkg);
    } catch (RuntimeException e) {
      Ui.toast(c, "App geopend; volume of helderheid kon niet worden toegepast.");
    }
    recordRecent(c, pkg);
    prefs(c).edit().putString("last", pkg).putInt("lastScreen", display).apply();
    return true;
  }

  static void apply(Context c, String pkg) {
    android.content.SharedPreferences p = prefs(c);
    int volume = p.getInt("volume:" + pkg, -1);
    if (volume >= 0) {
      AudioManager a = c.getSystemService(AudioManager.class);
      a.setStreamVolume(
          AudioManager.STREAM_MUSIC,
          volume * a.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 100,
          0);
    }
    int bright = p.getInt("brightness:" + pkg, -1);
    if (bright >= 0 && Settings.System.canWrite(c)) {
      Settings.System.putInt(
          c.getContentResolver(),
          Settings.System.SCREEN_BRIGHTNESS_MODE,
          Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
      Settings.System.putInt(
          c.getContentResolver(),
          Settings.System.SCREEN_BRIGHTNESS,
          Math.max(8, bright * 255 / 100));
    }
  }

  static JSONObject backup(Context c) throws JSONException {
    JSONObject root = new JSONObject(), data = new JSONObject();
    root.put("schema", 1);
    for (Map.Entry<String, ?> e : prefs(c).getAll().entrySet()) {
      String k = e.getKey();
      if (validKey(k)) data.put(k, e.getValue());
    }
    root.put("data", data);
    return root;
  }

  static boolean validKey(String k) {
    return ExtraFeatures.key(k)
        || k.equals("language")
        || k.equals("shortcuts")
        || k.equals("keyboardSize")
        || k.equals("guideNotes")
        || k.equals("top")
        || k.equals("bottom")
        || k.equals("combos")
        || k.equals("keepAwake")
        || k.equals("autoProfiles")
        || k.equals("panelTouchOnly")
        || k.equals("panelRecents")
        || k.equals("panelFavorites")
        || k.equals("panelPairs")
        || k.equals("panelNotes")
        || k.equals("recent")
        || k.equals("pairs")
        || k.startsWith("screen:")
        || k.startsWith("volume:")
        || k.startsWith("brightness:")
        || k.startsWith("favorite:")
        || k.startsWith("notes:")
        || k.startsWith("guide:")
        || k.matches("mapping:(global|[A-Za-z0-9_.]+)");
  }

  static void restore(Context c, String s) throws Exception {
    restore(c, s, true);
  }

  static void restore(Context c, String s, boolean commit) throws Exception {
    JSONObject root = StrictJson.object(s, SettingsBackup.MAX_BYTES);
    RgbSettings.keys(root, "schema", "data");
    RgbSettings.integer(root, "schema", 1, 1);
    JSONObject data = root.getJSONObject("data");
    if (data.length() > 3000) throw new Exception("Te veel instellingen");
    android.content.SharedPreferences.Editor ed = prefs(c).edit();
    for (Iterator<String> it = data.keys(); it.hasNext(); ) {
      String k = it.next();
      Object v = data.get(k);
      if (!validKey(k) || k.length() > 220) throw new Exception("Onbekende instelling");
      for (String prefix :
          new String[] {"screen:", "volume:", "brightness:", "favorite:", "notes:", "guide:"})
        if (k.startsWith(prefix) && !k.substring(prefix.length()).matches("[A-Za-z0-9_.]{1,200}"))
          throw new Exception("Ongeldig pakket");
      if (ExtraFeatures.key(k)) {
        ExtraFeatures.validate(k, v);
        ed.putString(k, (String) v);
      } else if (k.equals("language")) {
        if (!(v instanceof String) || (!v.equals("nl") && !v.equals("en")))
          throw new Exception("Invalid language");
        ed.putString(k, (String) v);
      } else if (k.equals("shortcuts")) {
        if (!(v instanceof String)) throw new Exception("Invalid shortcuts");
        Shortcuts.validate((String) v);
        ed.putString(k, (String) v);
      } else if (k.equals("keyboardSize")) {
        ed.putInt(k, RgbSettings.integer(data, k, 40, 64));
      } else if (k.startsWith("mapping:")) {
        if (!(v instanceof String)) throw new Exception("Ongeldig controllerprofiel");
        PadProfile.parse((String) v);
        ed.putString(k, (String) v);
      } else if (k.equals("recent")) {
        if (!(v instanceof String)) throw new Exception("Ongeldige recente apps");
        JSONArray list = StrictJson.array((String) v, 100000);
        if (list.length() > 12) throw new Exception("Te veel recente apps");
        for (int n = 0; n < list.length(); n++)
          if (!(list.get(n) instanceof String)
              || !list.getString(n).matches("[A-Za-z0-9_.]{1,200}"))
            throw new Exception("Ongeldig pakket");
        ed.putString(k, (String) v);
      } else if (k.equals("pairs")) {
        if (!(v instanceof String)) throw new Exception("Ongeldige app-paren");
        JSONArray arr = StrictJson.array((String) v, 100000);
        if (arr.length() > 100) throw new Exception("Te veel app-paren");
        for (int n = 0; n < arr.length(); n++) {
          JSONObject pair = arr.getJSONObject(n);
          RgbSettings.keys(pair, "top", "bottom", "name");
          if (!RgbSettings.string(pair, "top").matches("[A-Za-z0-9_.]{1,200}")
              || !RgbSettings.string(pair, "bottom").matches("[A-Za-z0-9_.]{1,200}"))
            throw new Exception("Ongeldig pakket");
          if (RgbSettings.string(pair, "name").length() > 500)
            throw new Exception("App-paarnaam te lang");
        }
        ed.putString(k, (String) v);
      } else if (k.equals("top") || k.equals("bottom") || k.startsWith("screen:")) {
        ed.putInt(k, RgbSettings.integer(data, k, -1, Integer.MAX_VALUE));
      } else if (k.startsWith("volume:") || k.startsWith("brightness:")) {
        ed.putInt(k, RgbSettings.integer(data, k, -1, 100));
      } else if (k.startsWith("notes:") || k.startsWith("guide:")) {
        if (!(v instanceof String) || ((String) v).length() > 50000)
          throw new Exception("Ongeldige tekst");
        ed.putString(k, (String) v);
      } else {
        if (!(v instanceof Boolean)) throw new Exception("Ongeldige schakelaar");
        ed.putBoolean(k, (Boolean) v);
      }
    }
    if (commit && !ed.commit()) throw new Exception("Could not save settings");
  }

  static JSONArray recent(Context c) {
    try {
      return new JSONArray(prefs(c).getString("recent", "[]"));
    } catch (Exception e) {
      return new JSONArray();
    }
  }

  static void recordRecent(Context c, String pkg) {
    if (pkg == null || pkg.equals(c.getPackageName()) || !pkg.matches("[A-Za-z0-9_.]{1,200}"))
      return;
    JSONArray old = recent(c), list = new JSONArray();
    list.put(pkg);
    for (int i = 0; i < old.length() && list.length() < 12; i++) {
      String p = old.optString(i);
      if (!p.equals(pkg) && p.matches("[A-Za-z0-9_.]{1,200}")) list.put(p);
    }
    prefs(c).edit().putString("recent", list.toString()).apply();
  }

  static JSONArray pairs(Context c) {
    try {
      return new JSONArray(prefs(c).getString("pairs", "[]"));
    } catch (Exception e) {
      return new JSONArray();
    }
  }

  static void pair(Context c, JSONObject pair) {
    JSONArray a = pairs(c);
    a.put(pair);
    try {
      restore(
          c,
          new JSONObject()
              .put("schema", 1)
              .put("data", new JSONObject().put("pairs", a.toString()))
              .toString(),
          false);
      if (!prefs(c).edit().putString("pairs", a.toString()).commit())
        throw new Exception("Could not save pair");
    } catch (Exception e) {
      Ui.toast(c, "App-paar niet opgeslagen: " + e.getMessage());
    }
  }

  private static final Handler pairHandler = new Handler(Looper.getMainLooper());
  private static long pairGeneration;
  private static java.lang.ref.WeakReference<Context> pairOwner =
      new java.lang.ref.WeakReference<>(null);

  static void cancelPendingPair(Context owner) {
    if (pairOwner.get() == owner) {
      cancelAllPendingPairs();
    }
  }

  /** Explicit global Stop; destroying one window still cancels only that window's pair. */
  static void cancelAllPendingPairs() {
    pairGeneration++;
    pairHandler.removeCallbacksAndMessages(null);
    pairOwner.clear();
  }

  static void openPair(Context c, JSONObject p) {
    pairGeneration++;
    pairHandler.removeCallbacksAndMessages(null);
    pairOwner.clear();
    int bottom = screen(c, true), top = screen(c, false);
    if (bottom < 0 || top < 0 || bottom == top) {
      Ui.toast(c, "Een app-paar heeft twee verschillende schermen nodig.");
      return;
    }
    try {
      String lower = p.getString("bottom"), upper = p.getString("top");
      if (launchIntent(c, lower, bottom) == null || launchIntent(c, upper, top) == null) {
        Ui.toast(c, "App-paar niet beschikbaar. Controleer beide apps en schermen.");
        return;
      }
      if (!launchChecked(c, lower, bottom)) return;
      Context app = c.getApplicationContext();
      long generation = pairGeneration;
      pairOwner = new java.lang.ref.WeakReference<>(c);
      pairHandler.postDelayed(
          () -> {
            if (generation != pairGeneration) return;
            pairOwner.clear();
            if (top != screen(app, false) || bottom != screen(app, true)) return;
            launchChecked(app, upper, top);
          },
          350);
    } catch (Exception e) {
      Ui.toast(c, "Ongeldig app-paar.");
    }
  }

  static void guide(Context c, String pkg) {
    String url = prefs(c).getString("guide:" + pkg, "").trim();
    if (url.isEmpty())
      url = "https://duckduckgo.com/?q=" + android.net.Uri.encode(name(c, pkg) + " game guide");
    android.net.Uri uri = android.net.Uri.parse(url);
    if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
      Ui.toast(c, "Gebruik een http- of https-link.");
      return;
    }
    try {
      Intent i = new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      int display = screen(c, true);
      c.startActivity(
          i,
          ActivityOptions.makeBasic()
              .setLaunchDisplayId(display >= 0 ? display : screen(c, false))
              .toBundle());
    } catch (Exception e) {
      Ui.toast(c, "Geen browser beschikbaar op dit scherm.");
    }
  }
}
