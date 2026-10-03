package nl.thorhaven.app;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.system.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.*;

/**
 * Original fixed-node Thor transport. Calls block and belong on the lighting service worker. Stock
 * AYN settings are a real restore baseline, not a snapshot of another app's animation. The writer
 * never changes Settings.System and never uses the performance/fan hw:snapshot.
 */
final class RgbHardware {
  static final String ENABLED = "joystick_light_enabled";
  static final String COLOR = "joystick_led_light_picker_color";
  static final String BRIGHTNESS = "led_light_brightness_percent";
  static String lastMessage = "RGB hardware has not been checked";

  static final class Probe {
    final boolean available, direct;
    final String message;
    final JSONObject baseline;

    Probe(boolean available, boolean direct, String message, JSONObject baseline) {
      this.available = available;
      this.direct = direct;
      this.message = message;
      this.baseline = baseline;
    }
  }

  static boolean thor() {
    return ((Build.MODEL == null ? "" : Build.MODEL)
            + " "
            + (Build.DEVICE == null ? "" : Build.DEVICE)
            + " "
            + (Build.PRODUCT == null ? "" : Build.PRODUCT))
        .toLowerCase(Locale.ROOT)
        .contains("thor");
  }

  static boolean directAvailable() {
    if (!thor()) return false;
    for (String path : DeviceControl.RGB_PATHS) {
      File node = new File(path);
      if (!node.exists() || !node.isFile() || !node.canWrite()) return false;
    }
    return true;
  }

  static synchronized Probe probe(Context c) {
    boolean direct = directAvailable();
    if (direct) {
      try {
        JSONObject baseline = readBaseline(c);
        return new Probe(true, true, "Direct Thor RGB nodes available", baseline);
      } catch (Exception unavailableStockRead) {
        // A validated stock baseline is mandatory. Ask the privileged, typed backend
        // only when app-level Settings.System access is unavailable or malformed.
      }
    }
    try {
      JSONObject result = response(new JSONObject().put("op", "rgbStatus"));
      JSONObject baseline = result.getJSONObject("baseline");
      validateBaseline(baseline);
      if (!result.getBoolean("available")) throw new IOException("Thor RGB nodes unavailable");
      return new Probe(
          true,
          direct,
          direct
              ? "Direct RGB nodes; stock baseline read through bridge"
              : "Thor RGB nodes available through root or AYN bridge",
          baseline);
    } catch (Exception e) {
      return new Probe(false, false, message(e), null);
    }
  }

  static JSONObject readBaseline(Context c) throws Exception {
    String enabled = Settings.System.getString(c.getContentResolver(), ENABLED);
    String color = Settings.System.getString(c.getContentResolver(), COLOR);
    String brightness = Settings.System.getString(c.getContentResolver(), BRIGHTNESS);
    if (enabled == null || color == null || brightness == null)
      throw new IOException("AYN stock lighting settings are missing; no restore baseline");
    JSONObject baseline =
        new JSONObject()
            .put(ENABLED, enabled.trim())
            .put(COLOR, color.trim())
            .put(BRIGHTNESS, brightness.trim());
    validateBaseline(baseline);
    return baseline;
  }

  static void validateBaseline(JSONObject baseline) throws Exception {
    RgbSettings.keys(baseline, ENABLED, COLOR, BRIGHTNESS);
    String[] enabled = RgbSettings.string(baseline, ENABLED).trim().split(",", -1);
    if (enabled.length != 2
        || !enabled[0].trim().matches("[01]")
        || !enabled[1].trim().matches("[01]"))
      throw new IOException("Invalid AYN stock LED enable baseline");
    String[] colors = RgbSettings.string(baseline, COLOR).trim().split(",", -1);
    if (colors.length != 2) throw new IOException("Invalid AYN stock LED color baseline");
    for (String hex : colors)
      if (!hex.trim().matches("#(?:[A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})"))
        throw new IOException("Invalid AYN stock LED color baseline");
    String raw = RgbSettings.string(baseline, BRIGHTNESS).trim();
    if (raw.length() > 32 || !raw.matches("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))
      throw new IOException("Invalid AYN stock LED brightness baseline");
    double level;
    try {
      level = Double.parseDouble(raw);
    } catch (NumberFormatException e) {
      throw new IOException("Invalid AYN stock LED brightness baseline");
    }
    if (!Double.isFinite(level) || level < 0 || level > 1)
      throw new IOException("Invalid AYN stock LED brightness baseline");
  }

  static RgbEngine.Frame stockFrame(JSONObject baseline) throws Exception {
    validateBaseline(baseline);
    String[] enabled = baseline.getString(ENABLED).trim().split(",", -1);
    String[] colors = baseline.getString(COLOR).trim().split(",", -1);
    double brightness = Double.parseDouble(baseline.getString(BRIGHTNESS).trim());
    int left = stockColor(colors[0]), right = stockColor(colors[1]);
    return new RgbEngine.Frame(
        enabled[0].trim().equals("1"),
        RgbEngine.scale(left, brightness),
        enabled[1].trim().equals("1"),
        RgbEngine.scale(right, brightness));
  }

  static int stockColor(String hex) throws IOException {
    String value = hex.trim();
    if (!value.matches("#(?:[A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})"))
      throw new IOException("Invalid stock color");
    return Integer.parseInt(value.substring(value.length() - 6), 16);
  }

  static synchronized void write(Context c, RgbEngine.Frame frame) throws IOException {
    if (frame == null) throw new IOException("Missing RGB frame");
    try {
      RgbEngine.Frame.fromJson(frame.toJson());
      if (directAvailable()) writeDirect(frame);
      else response(new JSONObject().put("op", "rgbFrame").put("frame", frame.toJson()));
      lastMessage = "RGB frame applied";
    } catch (Exception e) {
      lastMessage = message(e);
      throw new IOException(lastMessage, e);
    }
  }

  static synchronized void restore(Context c, JSONObject baseline) throws IOException {
    try {
      // Build and validate the whole restore before any enable/color write.
      RgbEngine.Frame frame = stockFrame(baseline);
      write(c, frame);
      lastMessage = "AYN stock lighting restored from the captured baseline";
    } catch (Exception e) {
      lastMessage = "AYN lighting restore failed: " + message(e);
      throw new IOException(lastMessage, e);
    }
  }

  static void writeDirect(RgbEngine.Frame frame) throws IOException {
    if (!directAvailable()) throw new IOException("Direct Thor RGB capability disappeared");
    String[] values = DeviceControl.rgbValues(frame);
    int completed = 0;
    try {
      for (int i = 0; i < values.length; i++) {
        writeNode(DeviceControl.RGB_PATHS[DeviceControl.rgbPathIndex(i)], values[i]);
        completed++;
      }
    } catch (Exception e) {
      throw new IOException(
          "RGB write failed after "
              + completed
              + "/6 node writes; state may be partial: "
              + message(e),
          e);
    }
  }

  static void writeNode(String path, String text) throws Exception {
    if (!java.util.Arrays.asList(DeviceControl.RGB_PATHS).contains(path))
      throw new IOException("RGB writes only accept the fixed Thor LED nodes");
    // O_CREAT is deliberately absent. Even a race with a removed node cannot create a file.
    FileDescriptor fd = Os.open(path, OsConstants.O_WRONLY | OsConstants.O_CLOEXEC, 0);
    try {
      byte[] bytes = (text + "\n").getBytes(StandardCharsets.US_ASCII);
      int offset = 0;
      while (offset < bytes.length) {
        int written = Os.write(fd, bytes, offset, bytes.length - offset);
        if (written <= 0) throw new IOException("Short RGB node write");
        offset += written;
      }
    } finally {
      Os.close(fd);
    }
  }

  static JSONObject response(JSONObject request) throws Exception {
    JSONObject result = new JSONObject(Controls.device(request));
    if (result.has("error")) throw new IOException(result.getString("error"));
    return result;
  }

  static String message(Exception e) {
    return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
  }

  private RgbHardware() {}
}
