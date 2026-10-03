package nl.thorhaven.app;

import android.content.*;
import android.os.*;
import android.provider.Settings;
import java.util.*;
import org.json.*;

/** Local screen-event measurements. No wake locks, alarms, radios or background app control. */
final class PlayStats {
  interface Clock {
    long now();

    int boot();
  }

  final Context context;
  final SharedPreferences prefs;
  final Clock clock;
  final Handler handler = new Handler(Looper.getMainLooper());
  String active = "";
  long last;
  boolean started;
  final Runnable tick =
      new Runnable() {
        public void run() {
          flush();
          if (started) handler.postDelayed(this, 15000);
        }
      };
  final BroadcastReceiver receiver =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          String action = i.getAction();
          if (Intent.ACTION_SCREEN_OFF.equals(action)) {
            focus("");
            beginSleep(level(c), plugged(c));
          } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
            endSleep(level(c), plugged(c));
          } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)
              && prefs.contains("sleep:start")
              && i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0)
            prefs.edit().putBoolean("sleep:charged", true).apply();
        }
      };

  PlayStats(Context c) {
    this(
        c,
        new Clock() {
          public long now() {
            return SystemClock.elapsedRealtime();
          }

          public int boot() {
            try {
              return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
            } catch (SecurityException unsupported) {
              return -1;
            }
          }
        });
  }

  PlayStats(Context c, Clock clock) {
    context = c;
    this.clock = clock;
    prefs = c.getSharedPreferences("thorhaven-stats", 0);
    last = clock.now();
  }

  void start() {
    if (started) return;
    started = true;
    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_SCREEN_OFF);
    filter.addAction(Intent.ACTION_SCREEN_ON);
    filter.addAction(Intent.ACTION_BATTERY_CHANGED);
    context.registerReceiver(receiver, filter);
    if (context.getSystemService(PowerManager.class).isInteractive())
      endSleep(level(context), plugged(context));
    handler.postDelayed(tick, 15000);
  }

  void stop() {
    if (!started) return;
    focus("");
    started = false;
    handler.removeCallbacks(tick);
    context.unregisterReceiver(receiver);
  }

  void focus(String pkg) {
    flush();
    active = pkg.equals(context.getPackageName()) ? "" : pkg;
    last = clock.now();
  }

  void flush() {
    long now = clock.now(), delta = now - last;
    last = now;
    if (!active.isEmpty() && delta > 0 && delta < 60000) {
      long old = prefs.getLong("time:" + active, 0);
      prefs.edit().putLong("time:" + active, old + delta).apply();
    }
  }

  void beginSleep(int level, boolean charging) {
    if (prefs.contains("sleep:start")) return;
    prefs
        .edit()
        .putLong("sleep:start", clock.now())
        .putInt("sleep:boot", clock.boot())
        .putInt("sleep:level", level)
        .putBoolean("sleep:charged", charging)
        .commit();
  }

  JSONObject endSleep(int level, boolean charging) {
    if (!prefs.contains("sleep:start")) return null;
    long duration = clock.now() - prefs.getLong("sleep:start", 0);
    int boot = prefs.getInt("sleep:boot", -2), initial = prefs.getInt("sleep:level", -1);
    boolean charged = charging || prefs.getBoolean("sleep:charged", false);
    prefs
        .edit()
        .remove("sleep:start")
        .remove("sleep:boot")
        .remove("sleep:level")
        .remove("sleep:charged")
        .commit();
    if (boot < 0
        || boot != clock.boot()
        || duration < 300000
        || duration > 7L * 24 * 60 * 60 * 1000
        || initial < 0
        || level < 0) return null;
    JSONObject row = new JSONObject();
    try {
      row.put("duration", duration)
          .put("drop", initial - level)
          .put("charged", charged)
          .put("ended", System.currentTimeMillis())
          .put("eligible", !charged && initial >= level && duration >= 3L * 60 * 60 * 1000);
      JSONArray all = history(context), next = new JSONArray();
      next.put(row);
      for (int n = 0; n < Math.min(all.length(), 29); n++) next.put(all.get(n));
      prefs.edit().putString("sleeps", next.toString()).commit();
    } catch (Exception ignored) {
    }
    return row;
  }

  static JSONArray history(Context c) {
    try {
      return new JSONArray(c.getSharedPreferences("thorhaven-stats", 0).getString("sleeps", "[]"));
    } catch (Exception e) {
      return new JSONArray();
    }
  }

  static double average(JSONArray history) {
    long duration = 0;
    int drop = 0;
    for (int i = 0; i < history.length(); i++) {
      JSONObject row = history.optJSONObject(i);
      if (row != null && row.optBoolean("eligible")) {
        duration += row.optLong("duration");
        drop += row.optInt("drop");
      }
    }
    return duration == 0 ? Double.NaN : drop * 3600000.0 / duration;
  }

  static int level(Context c) {
    Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    if (i == null) return -1;
    return i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        * 100
        / Math.max(1, i.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
  }

  static boolean plugged(Context c) {
    Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    return i != null && i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
  }

  static String duration(long ms) {
    long minutes = Math.max(0, ms / 60000);
    return minutes >= 60 ? (minutes / 60) + " u " + (minutes % 60) + " min" : minutes + " min";
  }

  static void page(MainActivity a) {
    a.heading(
        "Accu & speeltijd",
        "Lokale metingen tijdens de toegankelijkheidsservice. Scherm uit is geen bewijs van diepe"
            + " slaap.");
    android.widget.LinearLayout current = a.card("Nu", Store.battery(a));
    BatteryManager b = a.getSystemService(BatteryManager.class);
    long micro = b.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
    current.addView(
        Ui.text(
            a,
            micro == Long.MIN_VALUE
                ? "Stroommeting niet beschikbaar"
                : "Gemeten stroom: "
                    + String.format(Locale.ROOT, "%.0f mA", micro / 1000.0)
                    + " · teken volgt de firmware",
            14,
            Ui.MUTED));
    current.addView(Ui.button(a, "Vernieuwen", a::render));
    JSONArray rows = history(a);
    double avg = average(rows);
    android.widget.LinearLayout sleep =
        a.card(
            "Tijdens scherm-uit",
            Double.isNaN(avg)
                ? "Nog geen bruikbare meting van minstens 3 uur zonder laden."
                : String.format(
                    Locale.ROOT,
                    "Gemiddeld %.2f procentpunt per uur · sessies ≥ 3 uur, zonder laden",
                    avg));
    if (ThorService.instance == null)
      sleep.addView(
          Ui.text(
              a,
              "Activeer de toegankelijkheidsservice om nieuwe metingen te verzamelen.",
              14,
              Ui.MUTED));
    for (int i = 0; i < Math.min(rows.length(), 10); i++) {
      JSONObject row = rows.optJSONObject(i);
      if (row == null) continue;
      String text =
          duration(row.optLong("duration"))
              + " · "
              + row.optInt("drop")
              + " procentpunt"
              + (row.optBoolean("charged")
                  ? " · lader aangesloten, uitgesloten"
                  : row.optBoolean("eligible") ? " · telt mee" : " · uitgesloten van gemiddelde");
      sleep.addView(Ui.text(a, text, 14, Ui.TEXT));
    }
    android.widget.LinearLayout time =
        a.card(
            "Actieve app-tijd",
            "Schatting vanaf inschakelen van de service. Pauzeert bij scherm-uit en terwijl"
                + " Thorhaven zelf op de voorgrond staat.");
    if (ThorService.instance != null && ThorService.instance.stats != null)
      ThorService.instance.stats.flush();
    List<Map.Entry<String, ?>> entries =
        new ArrayList<>(a.getSharedPreferences("thorhaven-stats", 0).getAll().entrySet());
    entries.removeIf(e -> !e.getKey().startsWith("time:") || !(e.getValue() instanceof Long));
    entries.sort((x, y) -> Long.compare((Long) y.getValue(), (Long) x.getValue()));
    for (int i = 0; i < Math.min(entries.size(), 15); i++) {
      Map.Entry<String, ?> e = entries.get(i);
      time.addView(
          Ui.text(
              a,
              Store.name(a, e.getKey().substring(5)) + " · " + duration((Long) e.getValue()),
              14,
              Ui.TEXT));
    }
    time.addView(
        Ui.button(
            a,
            "Wis mijn metingen",
            () ->
                new android.app.AlertDialog.Builder(a)
                    .setTitle("Metingen wissen?")
                    .setMessage("Slaaphistorie en actieve app-tijd worden gewist.")
                    .setPositiveButton(
                        "Wissen",
                        (d, w) -> {
                          a.getSharedPreferences("thorhaven-stats", 0).edit().clear().commit();
                          a.render();
                        })
                    .setNegativeButton("Annuleren", null)
                    .show()));
  }
}
