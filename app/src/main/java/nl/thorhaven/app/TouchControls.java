package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import org.json.*;

/** Tap-only key actions through the existing typed privileged bridge. No held-key state. */
final class TouchControls {
  static final int[] CODES = {
    96, 97, 99, 100, 102, 103, 108, 109, 19, 20, 21, 22, 131, 132, 135, 111, 66, 62
  };
  static final String[] LABELS = {
    "A", "B", "X", "Y", "L1", "R1", "Start", "Select", "Up", "Down", "Left", "Right", "F1", "F2",
    "F5", "Escape", "Enter", "Space"
  };
  static View overlay;
  static WindowManager window;

  static JSONArray defaults() {
    JSONArray a = new JSONArray();
    try {
      for (int code : new int[] {96, 97, 99, 100, 131, 135})
        a.put(new JSONObject().put("code", code).put("label", LABELS[index(code)]));
    } catch (Exception ignored) {
    }
    return a;
  }

  static int index(int code) {
    for (int i = 0; i < CODES.length; i++) if (CODES[i] == code) return i;
    return 0;
  }

  static JSONArray config(Context c) {
    try {
      return new JSONArray(Store.prefs(c).getString("touchTiles", defaults().toString()));
    } catch (Exception e) {
      return defaults();
    }
  }

  static void validate(String s) throws Exception {
    JSONArray a = new JSONArray(s);
    if (a.length() != 6) throw new Exception("Expected six touch tiles");
    for (int i = 0; i < a.length(); i++) {
      JSONObject x = a.getJSONObject(i);
      boolean valid = false;
      for (int k : CODES) if (k == x.getInt("code")) valid = true;
      if (!valid || x.getString("label").length() > 30) throw new Exception("Invalid touch tile");
    }
  }

  static void configure(MainActivity a, LinearLayout l) {
    JSONArray tiles = config(a);
    for (int i = 0; i < tiles.length(); i++) {
      final int n = i;
      JSONObject x = tiles.optJSONObject(i);
      l.addView(
          Ui.button(
              a,
              "Tile " + (i + 1) + ": " + x.optString("label"),
              () ->
                  new Ui.Dialog(a)
                      .setTitle("Tikactie kiezen")
                      .setItems(
                          LABELS,
                          (d, w) -> {
                            try {
                              tiles.put(
                                  n,
                                  new JSONObject().put("code", CODES[w]).put("label", LABELS[w]));
                              ExtraFeatures.save(a, "touchTiles", tiles.toString());
                              a.render();
                            } catch (Exception ignored) {
                            }
                          })
                      .show()));
    }
  }

  static void hide() {
    if (overlay != null && window != null)
      try {
        window.removeView(overlay);
      } catch (Exception ignored) {
      }
    overlay = null;
    window = null;
  }

  static void show(Context c) {
    hide();
    ThorService s = ThorService.instance;
    if (s == null) {
      Ui.toast(c, "Enable the accessibility service first");
      return;
    }
    int id = Store.screen(c, true), target = Store.screen(c, false);
    Display display =
        c.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(id);
    if (display == null || id == target) {
      Ui.toast(c, "Assign separate top and bottom displays first");
      return;
    }
    try {
      Context local = s.createDisplayContext(display);
      window = local.getSystemService(WindowManager.class);
      LinearLayout l = Ui.col(local);
      l.setBackgroundColor(Ui.BG);
      l.setPadding(Ui.dp(local, 16), Ui.dp(local, 12), Ui.dp(local, 16), Ui.dp(local, 12));
      l.addView(Ui.title(local, "Aanraakknoppen · tikacties", 20));
      l.addView(
          Ui.text(
              local,
              "Root-Shizuku / AYN PServer nodig. Kies de doelapp op het bovenste scherm."
                  + " Ondersteuning verschilt per emulator. De knoppen geven een korte druk, geen"
                  + " vastgehouden gamepadknop.",
              13,
              Ui.MUTED));
      JSONArray tiles = config(c);
      for (int row = 0; row < 2; row++) {
        LinearLayout r = Ui.row(local);
        for (int col = 0; col < 3; col++) {
          JSONObject x = tiles.getJSONObject(row * 3 + col);
          int code = x.getInt("code");
          r.addView(
              Ui.button(
                  local,
                  x.getString("label"),
                  () ->
                      Controls.async(
                          c,
                          () ->
                              Controls.device(
                                  new JSONObject()
                                      .put("op", "key")
                                      .put("code", code)
                                      .put("display", target)),
                          result -> {
                            if (result.has("error")) Ui.toast(c, result.optString("error"));
                          })),
              new LinearLayout.LayoutParams(0, Ui.dp(local, 70), 1));
        }
        l.addView(r);
      }
      l.addView(Ui.button(local, "Sluiten", TouchControls::hide));
      WindowManager.LayoutParams lp =
          new WindowManager.LayoutParams(
              -1,
              -2,
              WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
              WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                  | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
              android.graphics.PixelFormat.TRANSLUCENT);
      lp.gravity = Gravity.BOTTOM;
      window.addView(l, lp);
      overlay = l;
    } catch (Exception e) {
      hide();
      Ui.toast(c, "Touch controls unavailable: " + e.getMessage());
    }
  }
}
