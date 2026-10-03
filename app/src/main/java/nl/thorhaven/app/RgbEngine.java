package nl.thorhaven.app;

import java.util.Objects;
import org.json.*;

/** Original deterministic animation math shared by the user preview and hardware worker. */
final class RgbEngine {
  static final class Frame {
    final boolean leftEnabled, rightEnabled;
    final int left, left2, right, right2;

    Frame(boolean leftEnabled, int left, boolean rightEnabled, int right) {
      this(leftEnabled, left, left, rightEnabled, right, right);
    }

    /** Zone one keeps the legacy names; ordinary effects mirror the color into zone two. */
    Frame(boolean leftEnabled, int left, int left2, boolean rightEnabled, int right, int right2) {
      if (left < 0
          || left > 0xffffff
          || left2 < 0
          || left2 > 0xffffff
          || right < 0
          || right > 0xffffff
          || right2 < 0
          || right2 > 0xffffff) throw new IllegalArgumentException("Invalid RGB frame");
      this.leftEnabled = leftEnabled;
      this.rightEnabled = rightEnabled;
      this.left = leftEnabled ? left : 0;
      this.left2 = leftEnabled ? left2 : 0;
      this.right = rightEnabled ? right : 0;
      this.right2 = rightEnabled ? right2 : 0;
    }

    JSONObject toJson() {
      try {
        JSONObject q =
            new JSONObject()
                .put("leftEnabled", leftEnabled)
                .put("rightEnabled", rightEnabled)
                .put("left", left)
                .put("right", right);
        if (left2 != left || right2 != right) q.put("left2", left2).put("right2", right2);
        return q;
      } catch (JSONException impossible) {
        throw new IllegalStateException(impossible);
      }
    }

    static Frame fromJson(JSONObject q) throws Exception {
      boolean zones = q.has("left2") || q.has("right2");
      if (zones)
        RgbSettings.keys(q, "leftEnabled", "rightEnabled", "left", "right", "left2", "right2");
      else RgbSettings.keys(q, "leftEnabled", "rightEnabled", "left", "right");
      return new Frame(
          RgbSettings.bool(q, "leftEnabled"),
          RgbSettings.integer(q, "left", 0, 0xffffff),
          zones
              ? RgbSettings.integer(q, "left2", 0, 0xffffff)
              : RgbSettings.integer(q, "left", 0, 0xffffff),
          RgbSettings.bool(q, "rightEnabled"),
          RgbSettings.integer(q, "right", 0, 0xffffff),
          zones
              ? RgbSettings.integer(q, "right2", 0, 0xffffff)
              : RgbSettings.integer(q, "right", 0, 0xffffff));
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Frame)) return false;
      Frame f = (Frame) other;
      return leftEnabled == f.leftEnabled
          && rightEnabled == f.rightEnabled
          && left == f.left
          && left2 == f.left2
          && right == f.right
          && right2 == f.right2;
    }

    @Override
    public int hashCode() {
      return Objects.hash(leftEnabled, rightEnabled, left, left2, right, right2);
    }
  }

  static Frame render(
      JSONObject profile,
      long elapsedMs,
      int batteryPercent,
      boolean charging,
      int screenLevel,
      JSONObject options) {
    try {
      RgbSettings.validateProfile(profile);
      boolean follow = RgbSettings.bool(options, "followScreen");
      boolean dim = RgbSettings.bool(options, "lowBatteryDim");
      double scale = follow ? clamp(screenLevel / 100d) : 1;
      if (dim && batteryPercent >= 0 && batteryPercent < 20 && !charging) scale *= .3;
      JSONObject left = profile.getJSONObject("left"), right = profile.getJSONObject("right");
      return new Frame(
          left.getBoolean("enabled"),
          side(left, elapsedMs, batteryPercent, charging, scale),
          right.getBoolean("enabled"),
          side(right, elapsedMs, batteryPercent, charging, scale));
    } catch (Exception invalid) {
      throw new IllegalArgumentException("Invalid RGB profile or options", invalid);
    }
  }

  static int side(JSONObject side, long elapsed, int battery, boolean charging, double global)
      throws Exception {
    int base = RgbSettings.color(side.getString("color"));
    int secondary = RgbSettings.color(side.getString("secondary"));
    int period = side.getInt("speedMs");
    double phase = Math.floorMod(elapsed, (long) period) / (double) period;
    double level = side.getInt("brightness") / 100d * global;
    String effect = side.getString("effect");
    switch (effect) {
      case "solid":
        break;
      case "breathe":
        level *= .15 + .85 * (.5 - .5 * Math.cos(phase * 2 * Math.PI));
        break;
      case "rainbow":
        base = hsv(hue(base) + phase * 360);
        break;
      case "cycle":
        base = mix(base, secondary, .5 - .5 * Math.cos(phase * 2 * Math.PI));
        break;
      case "pulse":
        level *= phase < .7 ? Math.pow(Math.sin(Math.PI * phase / .7), 2) : 0;
        break;
      case "battery":
        if (battery < 0 || battery > 100) return 0;
        base = hsv(battery * 1.2);
        break;
      case "charging":
        if (!charging || battery < 0 || battery > 100) return 0;
        base = mix(base, secondary, battery / 100d);
        level *= .25 + .75 * (.5 - .5 * Math.cos(phase * 2 * Math.PI));
        break;
      default:
        throw new IllegalArgumentException("Unknown RGB effect");
    }
    return scale(base, level);
  }

  static int scale(int rgb, double factor) {
    double safe = clamp(factor);
    return channel(rgb >>> 16, safe) << 16 | channel(rgb >>> 8, safe) << 8 | channel(rgb, safe);
  }

  static int channel(int value, double factor) {
    return Math.max(0, Math.min(255, (int) Math.round((value & 255) * factor)));
  }

  static int mix(int from, int to, double position) {
    double p = clamp(position);
    int red = (int) Math.round((from >>> 16 & 255) * (1 - p) + (to >>> 16 & 255) * p);
    int green = (int) Math.round((from >>> 8 & 255) * (1 - p) + (to >>> 8 & 255) * p);
    int blue = (int) Math.round((from & 255) * (1 - p) + (to & 255) * p);
    return red << 16 | green << 8 | blue;
  }

  static double hue(int rgb) {
    double r = (rgb >>> 16 & 255) / 255d, g = (rgb >>> 8 & 255) / 255d, b = (rgb & 255) / 255d;
    double high = Math.max(r, Math.max(g, b)),
        low = Math.min(r, Math.min(g, b)),
        range = high - low;
    if (range == 0) return 0;
    double sector =
        high == r ? (g - b) / range : high == g ? (b - r) / range + 2 : (r - g) / range + 4;
    return (sector * 60 + 360) % 360;
  }

  static int hsv(double hue) {
    double h = (hue % 360 + 360) % 360 / 60;
    double x = 1 - Math.abs(h % 2 - 1);
    double r = 0, g = 0, b = 0;
    if (h < 1) {
      r = 1;
      g = x;
    } else if (h < 2) {
      r = x;
      g = 1;
    } else if (h < 3) {
      g = 1;
      b = x;
    } else if (h < 4) {
      g = x;
      b = 1;
    } else if (h < 5) {
      r = x;
      b = 1;
    } else {
      r = 1;
      b = x;
    }
    return (int) Math.round(r * 255) << 16
        | (int) Math.round(g * 255) << 8
        | (int) Math.round(b * 255);
  }

  static double clamp(double value) {
    return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
  }

  private RgbEngine() {}
}
