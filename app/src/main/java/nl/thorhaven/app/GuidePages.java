package nl.thorhaven.app;

import android.widget.*;
import org.json.*;

final class GuidePages {
  static String selected = "";

  static void page(MainActivity a) {
    a.heading(
        "Offline gidsen",
        "Je eigen PDF-, tekst- en Markdown-gidsen op het onderste scherm, ook bovenop een game voor"
            + " beide schermen.");
    LinearLayout choose =
        a.card(
            "Voor welke app?", "Deze gidsen blijven op je Thor. Er is geen internettoegang nodig.");
    choose.addView(
        Ui.button(
            a,
            "Kies een app",
            () ->
                a.pick(
                    "Offline gids voor…",
                    app -> {
                      selected = app.pkg;
                      a.render();
                    })));
    String pkg = selected.isEmpty() ? Store.prefs(a).getString("last", "") : selected;
    if (!pkg.isEmpty()) guide(a, pkg);
    LinearLayout library =
        a.card(
            "Opgeslagen gidsen",
            "PDF maximaal 16 MB; UTF-8 tekst of Markdown maximaal 2 MB. PDF-bladwijzers en"
                + " leespositie worden lokaal onthouden.");
    int count = 0;
    for (String key : OfflineGuides.prefs(a).getAll().keySet())
      if (OfflineGuides.exists(a, key)) {
        count++;
        library.addView(
            Ui.button(
                a,
                Store.name(a, key) + " · " + OfflineGuides.meta(a, key).optString("name"),
                () -> {
                  selected = key;
                  a.render();
                }));
      }
    if (count == 0)
      library.addView(Ui.text(a, "Nog geen offline gidsen geïmporteerd.", 14, Ui.MUTED));
  }

  static void guide(MainActivity a, String pkg) {
    JSONObject meta = OfflineGuides.meta(a, pkg);
    LinearLayout card =
        a.card(
            Store.name(a, pkg),
            OfflineGuides.exists(a, pkg)
                ? meta.optString("name")
                    + " · "
                    + (meta.optString("kind").equals("pdf")
                        ? meta.optInt("pages") + " pagina's"
                        : "tekstgids")
                : "Nog geen offline gids voor deze app.");
    card.addView(Ui.button(a, "Importeer PDF / tekst / Markdown", () -> a.importGuide(pkg)));
    if (OfflineGuides.exists(a, pkg)) {
      card.addView(Ui.button(a, "Offline gids openen", () -> OfflineGuides.open(a, pkg)));
      card.addView(
          Ui.button(
              a,
              "Gids verwijderen",
              () ->
                  new Ui.Dialog(a)
                      .setTitle("Gids verwijderen?")
                      .setMessage("Alleen de lokale kopie en leespositie worden gewist.")
                      .setPositiveButton(
                          "Verwijderen",
                          (d, w) -> {
                            OfflineGuides.remove(a, pkg);
                            a.render();
                          })
                      .setNegativeButton("Annuleren", null)
                      .show()));
    }
    card.addView(Ui.button(a, "Online gids in mijn browser", () -> Store.guide(a, pkg)));
  }
}
