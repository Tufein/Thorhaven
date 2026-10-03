package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import org.json.*;

/** Finite key actions on the lower screen; never leaves an independently held key behind. */
final class TouchControls {
  static final int[] CODES = {
    96, 97, 99, 100, 102, 103, 104, 105, 106, 107, 108, 109, 19, 20, 21, 22, 131, 132, 133, 134,
    135, 136, 137, 138, 139, 140, 141, 142, 111, 66, 62, 61, 67, 51, 29, 47, 32
  };
  static final String[] LABELS = {
    "A",
    "B",
    "X",
    "Y",
    "L1",
    "R1",
    "L2",
    "R2",
    "L3",
    "R3",
    "Start",
    "Select",
    "Up",
    "Down",
    "Left",
    "Right",
    "F1",
    "F2",
    "F3",
    "F4",
    "F5",
    "F6",
    "F7",
    "F8",
    "F9",
    "F10",
    "F11",
    "F12",
    "Escape",
    "Enter",
    "Space",
    "Tab",
    "Backspace",
    "W",
    "A (keyboard)",
    "S",
    "D"
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

  static boolean allowed(int code) {
    for (int candidate : CODES) if (candidate == code) return true;
    return false;
  }

  static JSONArray config(Context c) {
    try {
      String value = Store.prefs(c).getString("touchTiles", defaults().toString());
      validate(value);
      return new JSONArray(value);
    } catch (Exception e) {
      return defaults();
    }
  }

  static void validate(String s) throws Exception {
    JSONArray a = new JSONArray(s);
    if (a.length() != 6) throw new Exception("Expected six touch tiles");
    for (int i = 0; i < a.length(); i++) {
      JSONObject x = a.getJSONObject(i);
      int code = DeviceControl.inputInt(x, "code", 0, 255);
      if (!allowed(code) || x.getString("label").isEmpty() || x.getString("label").length() > 30)
        throw new Exception("Invalid touch tile");
      if (!java.util.Arrays.asList("tap", "hold", "turbo").contains(x.optString("mode", "tap")))
        throw new Exception("Invalid touch tile mode");
      for (java.util.Iterator<String> it = x.keys(); it.hasNext(); ) {
        String key = it.next();
        if (!java.util.Arrays.asList("code", "label", "mode").contains(key))
          throw new Exception("Invalid touch tile property");
      }
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
                                  new JSONObject()
                                      .put("code", CODES[w])
                                      .put("label", LABELS[w])
                                      .put("mode", x.optString("mode", "tap")));
                              ExtraFeatures.save(a, "touchTiles", tiles.toString());
                              a.render();
                            } catch (Exception ignored) {
                            }
                          })
                      .show()));
      String mode = x.optString("mode", "tap");
      l.addView(
          Ui.button(
              a,
              "Actie · " + modeLabel(mode),
              () ->
                  new Ui.Dialog(a)
                      .setTitle("Aanraakactie kiezen")
                      .setItems(
                          new String[] {"Tik", "Begrensde knopdruk", "Turbo · eindige reeks"},
                          (d, w) -> {
                            try {
                              x.put("mode", new String[] {"tap", "hold", "turbo"}[w]);
                              ExtraFeatures.save(a, "touchTiles", tiles.toString());
                              a.render();
                            } catch (Exception e) {
                              Ui.toast(a, e.getMessage());
                            }
                          })
                      .show()));
    }
  }

  static void hide() {
    ControlLab.stop();
    if (overlay != null && window != null)
      try {
        window.removeView(overlay);
      } catch (Exception ignored) {
      }
    overlay = null;
    window = null;
  }

  static void show(Context c) {
    show(c, false);
  }

  static String modeLabel(String mode) {
    return mode.equals("hold")
        ? "Begrensde knopdruk"
        : mode.equals("turbo") ? "Turbo · eindige reeks" : "Tik";
  }

  static void show(Context c, boolean lab) {
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
      l.addView(Ui.title(local, lab ? "Invoerlaboratorium · experimenteel" : "Aanraakknoppen", 20));
      l.addView(
          Ui.text(
              local,
              "Root-Shizuku / AYN PServer nodig. Kies de doelapp op het bovenste scherm."
                  + " Ondersteuning verschilt per emulator. Tik, begrensde knopdruk of eindige"
                  + " turbo volgens je instellingen. Geen onbeperkt vasthouden.",
              13,
              Ui.MUTED));
      JSONArray tiles = config(c);
      for (int row = 0; row < 2; row++) {
        LinearLayout r = Ui.row(local);
        for (int col = 0; col < 3; col++) {
          JSONObject x = tiles.getJSONObject(row * 3 + col);
          int code = x.getInt("code");
          String mode = x.optString("mode", "tap");
          r.addView(
              Ui.button(
                  local,
                  x.getString("label") + (mode.equals("tap") ? "" : " · " + modeLabel(mode)),
                  () -> ControlLab.key(c, code, mode, target)),
              new LinearLayout.LayoutParams(0, Ui.dp(local, 70), 1));
        }
        l.addView(r);
      }
      if (lab) ControlLab.decorate(local, l, target);
      else {
        l.addView(Ui.button(local, "Pad en macro's openen", () -> show(c, true)));
        l.addView(Ui.button(local, "Alle invoeracties stoppen", ControlLab::stop));
      }
      l.addView(Ui.button(local, "Sluiten", TouchControls::hide));
      View content = l;
      if (lab) {
        ScrollView scroll = new ScrollView(local);
        scroll.addView(l);
        content = scroll;
      }
      WindowManager.LayoutParams lp =
          new WindowManager.LayoutParams(
              -1,
              lab ? -1 : -2,
              WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
              WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                  | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
              android.graphics.PixelFormat.TRANSLUCENT);
      lp.gravity = Gravity.BOTTOM;
      window.addView(content, lp);
      overlay = content;
    } catch (Exception e) {
      hide();
      Ui.toast(c, "Touch controls unavailable: " + e.getMessage());
    }
  }
}
