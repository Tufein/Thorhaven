package nl.thorhaven.app;

import android.app.*;
import android.content.Context;
import android.view.WindowManager;
import android.widget.EditText;
import org.json.*;

final class PanelActions {
  static void savePair(Context c, Runnable close) {
    if (Bridge.remote == null) {
      Ui.toast(c, "Koppel Shizuku bij Instellen om de huidige apps te lezen.");
      return;
    }
    Controls.async(
        c,
        () -> Bridge.remote.currentApps(Store.screen(c, false), Store.screen(c, true)),
        r -> {
          if (r.has("error")) {
            Ui.toast(c, r.optString("error"));
            return;
          }
          EditText name = Ui.input(c, "Naam voor dit app-paar");
          name.setText(
              Store.name(c, r.optString("top")) + " + " + Store.name(c, r.optString("bottom")));
          AlertDialog dialog =
              new AlertDialog.Builder(c)
                  .setTitle("Huidige apps als paar bewaren")
                  .setView(name)
                  .setPositiveButton(
                      "Bewaren",
                      (d, w) -> {
                        try {
                          r.put("name", name.getText().toString());
                          Store.pair(c, r);
                          Ui.toast(c, "App-paar bewaard");
                        } catch (Exception e) {
                          Ui.toast(c, "Bewaren mislukt.");
                        }
                      })
                  .setNegativeButton("Annuleren", null)
                  .create();
          if (!(c instanceof Activity))
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
          if (close != null) close.run();
          dialog.show();
        });
  }
}
