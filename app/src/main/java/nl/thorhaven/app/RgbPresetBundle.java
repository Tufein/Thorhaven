package nl.thorhaven.app;

import android.app.AlertDialog;
import android.content.*;
import android.net.Uri;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.UUID;
import org.json.*;

/** A small, validated styles-only exchange file. Never transfers apps or hardware recovery. */
final class RgbPresetBundle {
  static final int EXPORT = 115, IMPORT = 116, MAX_BYTES = 65536;
  static final String FORMAT = "thorhaven-rgb-presets";

  static String encode(JSONObject settings) throws Exception {
    RgbSettings.validateSettings(settings);
    JSONArray styles = new JSONArray(), presets = settings.getJSONArray("presets");
    for (int i = 0; i < presets.length(); i++) {
      JSONObject preset = presets.getJSONObject(i);
      styles.put(
          new JSONObject()
              .put("name", preset.getString("name"))
              .put("profile", new JSONObject(preset.getJSONObject("profile").toString())));
    }
    validateStyles(styles);
    String raw =
        new JSONObject().put("format", FORMAT).put("schema", 1).put("presets", styles).toString(2);
    parse(raw);
    return raw;
  }

  static JSONArray parse(String raw) throws Exception {
    if (raw == null
        || raw.isEmpty()
        || raw.length() > MAX_BYTES
        || raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
      throw new IOException("RGB preset file must be at most 64 KB");
    JSONTokener tokener = new JSONTokener(raw);
    Object value = tokener.nextValue();
    if (!(value instanceof JSONObject) || tokener.nextClean() != 0)
      throw new IOException("Invalid RGB preset document");
    JSONObject document = (JSONObject) value;
    RgbSettings.keys(document, "format", "schema", "presets");
    if (!FORMAT.equals(RgbSettings.string(document, "format")))
      throw new IOException("Not a Thorhaven RGB preset file");
    RgbSettings.integer(document, "schema", 1, 1);
    Object styles = document.get("presets");
    if (!(styles instanceof JSONArray)) throw new IOException("Invalid RGB preset list");
    validateStyles((JSONArray) styles);
    return new JSONArray(styles.toString());
  }

  static void validateStyles(JSONArray styles) throws Exception {
    if (styles == null || styles.length() < 1 || styles.length() > 20)
      throw new IOException("RGB preset files need 1 to 20 styles");
    JSONObject validation = RgbSettings.defaults();
    JSONArray presets = validation.getJSONArray("presets");
    for (int i = 0; i < styles.length(); i++) {
      Object raw = styles.get(i);
      if (!(raw instanceof JSONObject)) throw new IOException("Invalid RGB style");
      JSONObject style = (JSONObject) raw;
      RgbSettings.keys(style, "name", "profile");
      presets.put(
          new JSONObject()
              .put("id", "validate_" + i)
              .put("name", RgbSettings.string(style, "name"))
              .put("profile", RgbSettings.object(style, "profile")));
    }
    RgbSettings.validateSettings(validation);
  }

  /** Returns a new settings document only after validating the entire import and capacity. */
  static JSONObject merge(JSONObject settings, JSONArray styles) throws Exception {
    RgbSettings.validateSettings(settings);
    validateStyles(styles);
    JSONObject result = new JSONObject(settings.toString());
    JSONArray presets = result.getJSONArray("presets");
    if (presets.length() + styles.length() > 20)
      throw new IOException("Not enough space: at most 20 saved RGB presets");
    for (int i = 0; i < styles.length(); i++) {
      JSONObject style = styles.getJSONObject(i);
      String id;
      boolean duplicate;
      do {
        id = "rgb_" + UUID.randomUUID().toString().replace("-", "");
        duplicate = false;
        for (int n = 0; n < presets.length(); n++)
          duplicate |= presets.getJSONObject(n).getString("id").equals(id);
      } while (duplicate);
      presets.put(
          new JSONObject()
              .put("id", id)
              .put("name", style.getString("name"))
              .put("profile", new JSONObject(style.getJSONObject("profile").toString())));
    }
    RgbSettings.validateSettings(result);
    return result;
  }

  static String read(InputStream in) throws Exception {
    if (in == null) throw new IOException("Cannot open RGB preset file");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int count;
    while ((count = in.read(buffer)) != -1) {
      if (out.size() + count > MAX_BYTES) throw new IOException("RGB preset file exceeds 64 KB");
      out.write(buffer, 0, count);
    }
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(out.toByteArray()))
        .toString();
  }

  static String text(Context c, String nl, String en) {
    return Language.isEnglish(c) ? en : nl;
  }

  static void export(MainActivity a) {
    try {
      encode(RgbSettings.load(a)); // Validate before creating a destination file.
      a.startActivityForResult(
          new Intent(Intent.ACTION_CREATE_DOCUMENT)
              .addCategory(Intent.CATEGORY_OPENABLE)
              .setType("application/json")
              .putExtra(Intent.EXTRA_TITLE, "Thorhaven-RGB-presets.json"),
          EXPORT);
    } catch (Exception e) {
      Ui.toast(
          a,
          text(a, "Sla eerst minstens één RGB-stijl op.", "Save at least one RGB preset first."));
    }
  }

  static void importFile(MainActivity a) {
    try {
      a.startActivityForResult(
          new Intent(Intent.ACTION_OPEN_DOCUMENT)
              .addCategory(Intent.CATEGORY_OPENABLE)
              .setType("*/*")
              .putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/json", "text/plain"}),
          IMPORT);
    } catch (Exception e) {
      Ui.toast(a, text(a, "Bestandskiezer niet beschikbaar.", "File picker unavailable."));
    }
  }

  static void result(MainActivity a, int request, Uri uri) {
    if (request == EXPORT) {
      try {
        String raw = encode(RgbSettings.load(a));
        new Thread(
                () -> {
                  try (OutputStream out = a.getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new IOException("Cannot open destination");
                    out.write(raw.getBytes(StandardCharsets.UTF_8));
                    a.runOnUiThread(
                        () -> {
                          if (!a.isDestroyed())
                            Ui.toast(
                                a, text(a, "RGB-stijlen geëxporteerd.", "RGB presets exported."));
                        });
                  } catch (Exception e) {
                    failure(a);
                  }
                },
                "rgb-preset-export")
            .start();
      } catch (Exception e) {
        failure(a);
      }
      return;
    }
    new Thread(
            () -> {
              try (InputStream in = a.getContentResolver().openInputStream(uri)) {
                JSONArray styles = parse(read(in));
                a.runOnUiThread(
                    () -> {
                      if (a.isFinishing() || a.isDestroyed()) return;
                      new AlertDialog.Builder(a)
                          .setTitle(text(a, "RGB-stijlen toevoegen?", "Add RGB presets?"))
                          .setMessage(
                              styles.length()
                                  + text(
                                      a,
                                      " stijl(en). Bestaande stijlen, app-koppelingen en je globale"
                                          + " profiel blijven behouden. Dit start geen"
                                          + " verlichting.",
                                      " style(s). Existing presets, app assignments and your global"
                                          + " profile are kept. This does not start lighting."))
                          .setNegativeButton(text(a, "Annuleren", "Cancel"), null)
                          .setPositiveButton(
                              text(a, "Toevoegen", "Add"),
                              (d, w) -> {
                                try {
                                  // Read current settings at confirmation, rather than overwriting
                                  // edits made while reading.
                                  RgbSettings.update(
                                      a,
                                      current ->
                                          current.put(
                                              "presets",
                                              merge(current, styles).getJSONArray("presets")));
                                  a.render();
                                  Ui.toast(
                                      a, text(a, "RGB-stijlen toegevoegd.", "RGB presets added."));
                                } catch (Exception e) {
                                  Ui.toast(
                                      a,
                                      text(
                                          a,
                                          "Import mislukt. Controleer de vrije ruimte (maximaal 20"
                                              + " stijlen).",
                                          "Import failed. Check the available space (at most 20"
                                              + " presets)."));
                                }
                              })
                          .show();
                    });
              } catch (Exception e) {
                failure(a);
              }
            },
            "rgb-preset-import")
        .start();
  }

  static void failure(MainActivity a) {
    a.runOnUiThread(
        () -> {
          if (!a.isDestroyed())
            Ui.toast(
                a,
                text(
                    a,
                    "RGB-bestand kon niet worden gelezen of opgeslagen. Gebruik een geldig"
                        + " Thorhaven RGB-bestand van maximaal 64 KB.",
                    "Could not read or save the RGB file. Use a valid Thorhaven RGB preset file of"
                        + " at most 64 KB."));
        });
  }

  private RgbPresetBundle() {}
}
