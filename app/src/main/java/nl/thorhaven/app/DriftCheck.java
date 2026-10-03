package nl.thorhaven.app;

import android.app.AlertDialog;
import android.widget.TextView;

final class DriftCheck {
  static int suggested(float peak) {
    if (!Float.isFinite(peak) || peak < 0 || peak > .35f) return -1;
    return Math.min(40, Math.max(3, (int) Math.ceil(peak * 100) + 3));
  }

  static void start(MainActivity a, TextView status) {
    if (Controls.active) {
      Ui.toast(a, "Stop remapping voordat je de fysieke ruststand meet.");
      return;
    }
    if (a.controller == null || a.lastControllerMotion == 0) {
      Ui.toast(a, "Beweeg beide sticks even, laat los en start daarna de meting.");
      return;
    }
    MainActivity.ControllerView view = a.controller;
    float[] peak = {0, 0};
    long end = android.os.SystemClock.elapsedRealtime() + 5000;
    Runnable sample =
        new Runnable() {
          public void run() {
            if (a.isDestroyed() || a.controller != view) return;
            peak[0] = Math.max(peak[0], Math.max(Math.abs(view.lx), Math.abs(view.ly)));
            peak[1] = Math.max(peak[1], Math.max(Math.abs(view.rx), Math.abs(view.ry)));
            long remaining = end - android.os.SystemClock.elapsedRealtime();
            status.setText(
                "Laat beide sticks los · " + Math.max(0, (remaining + 999) / 1000) + " sec");
            if (remaining > 0) {
              a.handler.postDelayed(this, 50);
              return;
            }
            int left = suggested(peak[0]), right = suggested(peak[1]);
            if (left < 0 || right < 0) {
              status.setText(
                  "Te veel stickbeweging om een ruststand te bepalen. Meet opnieuw zonder de sticks"
                      + " aan te raken.");
              return;
            }
            status.setText(
                "Advies: links "
                    + left
                    + "%, rechts "
                    + right
                    + "% · gemeten piek + 3 procentpunt marge");
            new AlertDialog.Builder(a)
                .setTitle("Dode zone opslaan")
                .setMessage(
                    "Links "
                        + left
                        + "%, rechts "
                        + right
                        + "%. Dit is een rustmeting, geen hardwarediagnose.")
                .setPositiveButton("Standaardprofiel", (d, w) -> apply(a, "global", left, right))
                .setNeutralButton(
                    "Kies een app",
                    (d, w) -> a.pick("Dode zone voor…", app -> apply(a, app.pkg, left, right)))
                .setNegativeButton("Sluiten", null)
                .show();
          }
        };
    sample.run();
  }

  static void apply(MainActivity a, String pkg, int left, int right) {
    try {
      org.json.JSONObject p = Controls.load(a, pkg);
      p.put("left", left).put("right", right);
      Controls.save(a, pkg, p);
      Ui.toast(
          a,
          "Dode zones opgeslagen voor "
              + (pkg.equals("global") ? "alle apps" : Store.name(a, pkg)));
    } catch (Exception e) {
      Ui.toast(a, e.getMessage());
    }
  }
}
