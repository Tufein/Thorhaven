package nl.thorhaven.app;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import org.json.*;

/** Explicit session timer and sampled battery graph; no wake locks or extrapolated readings. */
final class SessionStats {
  static int boot(Context c) {
    return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
  }

  static SharedPreferences pending(Context c) {
    return c.getSharedPreferences("thorhaven-session", 0);
  }

  static boolean active(Context c) {
    SharedPreferences p = pending(c);
    if (p.contains("start") && p.getInt("boot", -2) != boot(c)) {
      p.edit().clear().commit();
      return false;
    }
    return p.contains("start");
  }

  static void start(Context c) {
    if (active(c)) return;
    pending(c)
        .edit()
        .putLong("start", SystemClock.elapsedRealtime())
        .putLong("date", System.currentTimeMillis())
        .putInt("boot", boot(c))
        .putString("samples", "[]")
        .commit();
    sample(c);
  }

  static void sample(Context c) {
    if (!active(c)) return;
    try {
      SharedPreferences p = pending(c);
      JSONArray rows = new JSONArray(p.getString("samples", "[]"));
      long time = SystemClock.elapsedRealtime() - p.getLong("start", 0);
      if (rows.length() > 0 && time - rows.optJSONObject(rows.length() - 1).optLong("ms") < 60000)
        return;
      int level = PlayStats.level(c);
      if (level < 0) return;
      if (rows.length() >= 240)
        rows.remove(1); // retain the beginning, then the most recent four hours of samples
      rows.put(
          new JSONObject()
              .put("ms", time)
              .put("level", level)
              .put("charging", PlayStats.plugged(c)));
      p.edit().putString("samples", rows.toString()).commit();
    } catch (Exception ignored) {
    }
  }

  static void stop(Context c) {
    if (!active(c)) return;
    sample(c);
    try {
      SharedPreferences p = pending(c);
      JSONArray rows = ExtraFeatures.array(c, "sessions");
      rows.put(
          new JSONObject()
              .put("date", p.getLong("date", 0))
              .put("duration", SystemClock.elapsedRealtime() - p.getLong("start", 0))
              .put("samples", new JSONArray(p.getString("samples", "[]"))));
      while (rows.length() > 30) rows.remove(0);
      ExtraFeatures.save(c, "sessions", rows.toString());
      p.edit().clear().commit();
    } catch (Exception e) {
      Ui.toast(c, e.getMessage());
    }
  }

  static void validate(String s) throws Exception {
    JSONArray rows = new JSONArray(s);
    if (rows.length() > 30) throw new Exception("Maximum 30 play sessions");
    for (int i = 0; i < rows.length(); i++) {
      JSONObject row = rows.getJSONObject(i);
      long duration = row.getLong("duration");
      if (row.getLong("date") < 0 || duration < 0 || duration > 365L * 24 * 3600 * 1000)
        throw new Exception("Invalid session time");
      JSONArray samples = row.getJSONArray("samples");
      if (samples.length() > 240) throw new Exception("Too many battery samples");
      long last = -1;
      for (int j = 0; j < samples.length(); j++) {
        JSONObject x = samples.getJSONObject(j);
        long ms = x.getLong("ms");
        int level = x.getInt("level");
        if (ms < last || ms < 0 || ms > duration || level < 0 || level > 100)
          throw new Exception("Invalid battery sample");
        x.getBoolean("charging");
        last = ms;
      }
    }
  }

  static void page(MainActivity a) {
    sample(a);
    LinearLayout l =
        Ui.card(
            a,
            a.content,
            "Speelsessies & batterijgrafiek",
            "Start een timer voor je speelsessie. De verstreken tijd telt pauzes en tijd met"
                + " uitgeschakeld scherm mee. Metingen worden opgeslagen wanneer de service actief"
                + " is; ontbrekende metingen worden niet ingevuld.");
    boolean on = active(a);
    if (on)
      l.addView(
          Ui.text(
              a,
              "Current session: "
                  + (SystemClock.elapsedRealtime() - pending(a).getLong("start", 0)) / 60000
                  + " min",
              16,
              Ui.TEXT));
    l.addView(
        Ui.button(
            a,
            on ? "Sessie stoppen en bewaren" : "Sessie starten",
            () -> {
              if (on) stop(a);
              else start(a);
              a.render();
            }));
    JSONArray rows = ExtraFeatures.array(a, "sessions");
    JSONArray samples;
    try {
      samples =
          on
              ? new JSONArray(pending(a).getString("samples", "[]"))
              : rows.length() > 0
                  ? rows.getJSONObject(rows.length() - 1).getJSONArray("samples")
                  : new JSONArray();
    } catch (Exception e) {
      samples = new JSONArray();
    }
    l.addView(new Graph(a, samples), new LinearLayout.LayoutParams(-1, Ui.dp(a, 180)));
    for (int i = rows.length() - 1; i >= Math.max(0, rows.length() - 10); i--) {
      JSONObject x = rows.optJSONObject(i);
      if (x != null)
        l.addView(
            Ui.text(
                a,
                new java.text.SimpleDateFormat("MMM d HH:mm", java.util.Locale.getDefault())
                        .format(new java.util.Date(x.optLong("date")))
                    + " · "
                    + x.optLong("duration") / 60000
                    + " min",
                14,
                Ui.MUTED));
    }
    l.addView(
        Ui.button(
            a,
            "Opgeslagen sessies wissen",
            () ->
                new Ui.Dialog(a)
                    .setTitle("Sessiegeschiedenis wissen?")
                    .setPositiveButton(
                        "Wissen",
                        (d, w) -> {
                          Store.prefs(a).edit().remove("sessions").commit();
                          a.render();
                        })
                    .setNegativeButton("Annuleren", null)
                    .show()));
  }

  static final class Graph extends View {
    final JSONArray samples;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    Graph(Context c, JSONArray s) {
      super(c);
      samples = s;
      setContentDescription("Battery graph from 0 to 100 percent over the sampled session");
    }

    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      float left = Ui.dp(getContext(), 40),
          top = Ui.dp(getContext(), 12),
          bottom = getHeight() - Ui.dp(getContext(), 32),
          width = getWidth() - left - Ui.dp(getContext(), 12);
      paint.setTextSize(Ui.dp(getContext(), 12));
      paint.setStrokeWidth(Ui.dp(getContext(), 2));
      paint.setColor(Ui.MUTED);
      canvas.drawText("100%", 0, top + 12, paint);
      canvas.drawText("0%", 0, bottom, paint);
      canvas.drawLine(left, top, left, bottom, paint);
      canvas.drawLine(left, bottom, getWidth(), bottom, paint);
      if (samples.length() == 0) {
        canvas.drawText("No battery samples yet", left + 8, top + 40, paint);
        return;
      }
      long end = Math.max(1, samples.optJSONObject(samples.length() - 1).optLong("ms"));
      float px = 0, py = 0;
      long previous = -1;
      for (int i = 0; i < samples.length(); i++) {
        JSONObject x = samples.optJSONObject(i);
        long ms = x.optLong("ms");
        float nx = left + width * ms / end, ny = bottom - (bottom - top) * x.optInt("level") / 100f;
        paint.setColor(x.optBoolean("charging") ? Color.YELLOW : Ui.ACCENT);
        if (previous >= 0 && ms - previous <= 90000) canvas.drawLine(px, py, nx, ny, paint);
        canvas.drawCircle(nx, ny, 3, paint);
        px = nx;
        py = ny;
        previous = ms;
      }
      paint.setColor(Ui.MUTED);
      canvas.drawText("0 min", left, getHeight() - 5, paint);
      canvas.drawText(
          end / 60000 + " min", Math.max(left, getWidth() - 70), getHeight() - 5, paint);
    }
  }
}
