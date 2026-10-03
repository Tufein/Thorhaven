package nl.thorhaven.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;

/**
 * Serialized lighting session. Recovery restores actual AYN settings, not another app's animation.
 */
final class RgbSession {
  interface Backend {
    RgbHardware.Probe probe(Context c) throws Exception;

    void write(Context c, RgbEngine.Frame frame) throws Exception;

    void restore(Context c, JSONObject baseline) throws Exception;
  }

  static final Backend HARDWARE =
      new Backend() {
        public RgbHardware.Probe probe(Context c) throws Exception {
          return RgbHardware.probe(c);
        }

        public void write(Context c, RgbEngine.Frame f) throws Exception {
          RgbHardware.write(c, f);
        }

        public void restore(Context c, JSONObject b) throws Exception {
          RgbHardware.restore(c, b);
        }
      };
  final Context context;
  final Backend backend;
  final long started;
  final boolean direct;
  final JSONObject baseline;
  RgbEngine.Frame last;
  boolean closed, paused;
  String message = "RGB session ready";

  static SharedPreferences recovery(Context c) {
    return c.getSharedPreferences("rgb-recovery", 0);
  }

  RgbSession(Context c, Backend backend, long started) throws Exception {
    context = c.getApplicationContext();
    this.backend = backend;
    this.started = started;
    if (recovery(context).contains("baseline"))
      throw new Exception("Restore pending AYN lighting before starting a new session");
    RgbHardware.Probe p = backend.probe(context);
    if (!p.available || p.baseline == null) throw new Exception(p.message);
    direct = p.direct;
    baseline = new JSONObject(p.baseline.toString());
    RgbHardware.validateBaseline(baseline);
    if (!recovery(context).edit().putString("baseline", baseline.toString()).commit())
      throw new Exception("Could not save RGB recovery settings; lights were not changed");
    message = p.message;
  }

  static JSONObject selectProfile(JSONObject settings, String pkg) throws Exception {
    JSONObject apps = settings.getJSONObject("apps");
    String id = apps.optString(pkg, "");
    JSONArray presets = settings.getJSONArray("presets");
    for (int i = 0; i < presets.length(); i++) {
      JSONObject p = presets.getJSONObject(i);
      if (id.equals(p.getString("id"))) return p.getJSONObject("profile");
    }
    return settings.getJSONObject("profile");
  }

  /** A single tick; no catch-up frames, unbounded queue, wake lock or stored screen content. */
  boolean tick(
      JSONObject settings,
      String pkg,
      long now,
      boolean interactive,
      int battery,
      boolean charging,
      int screenLevel)
      throws Exception {
    if (closed) return false;
    RgbSettings.validateSettings(settings);
    long elapsed = Math.max(0, now - started);
    int minutes = settings.getInt("timerMinutes");
    if (minutes > 0 && elapsed >= minutes * 60000L) return false;
    paused = !interactive && settings.getBoolean("screenOffPause");
    RgbEngine.Frame next =
        paused
            ? new RgbEngine.Frame(false, 0, false, 0)
            : RgbEngine.render(
                selectProfile(settings, pkg), elapsed, battery, charging, screenLevel, settings);
    if (!next.equals(last)) {
      backend.write(context, next);
      last = next;
    }
    message =
        paused
            ? "RGB paused while the screens are off"
            : (pkg.isEmpty() || !settings.getJSONObject("apps").has(pkg)
                ? "RGB active · global profile"
                : "RGB active · " + Store.name(context, pkg));
    return true;
  }

  /** A finite diagnostic ignores profile edits and app focus and ends when the screen sleeps. */
  boolean diagnosticTick(String mode, long now, boolean interactive) throws Exception {
    if (closed) return false;
    long limit = RgbDiagnostics.duration(mode);
    long elapsed = Math.max(0, now - started);
    if (!interactive) {
      message =
          RgbDiagnostics.text(
              context,
              "RGB-zonetest gestopt omdat de schermen uit zijn",
              "RGB zone test stopped because the screens are off");
      return false;
    }
    if (elapsed >= limit) {
      message = RgbDiagnostics.text(context, "RGB-zonetest klaar", "RGB zone test finished");
      return false;
    }
    RgbEngine.Frame next = RgbDiagnostics.frame(mode, elapsed);
    if (!next.equals(last)) {
      backend.write(context, next);
      last = next;
    }
    paused = false;
    message = RgbDiagnostics.progress(context, mode, elapsed);
    return true;
  }

  boolean close(String reason) {
    if (closed) return !recovery(context).contains("baseline");
    closed = true;
    try {
      JSONObject restore = baseline;
      // We never write stock Settings.System. Respect changes made there during this session.
      try {
        RgbHardware.Probe now = backend.probe(context);
        if (now.baseline != null) {
          RgbHardware.validateBaseline(now.baseline);
          restore = now.baseline;
        }
      } catch (Exception ignored) {
        /* Saved actual baseline remains available for recovery. */
      }
      backend.restore(context, restore);
      if (!recovery(context).edit().remove("baseline").commit())
        throw new Exception("Could not clear the recovery record");
      message = reason + " · AYN lighting restored";
      return true;
    } catch (Exception e) {
      message = reason + " · restoration pending: " + e.getMessage();
      return false;
    }
  }
}
