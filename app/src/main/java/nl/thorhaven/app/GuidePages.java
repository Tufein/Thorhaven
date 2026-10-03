package nl.thorhaven.app;

import android.widget.*;
import org.json.*;

final class GuidePages {
  static String selected = "";

  static void page(MainActivity a) {
    a.heading(
        "Offline gidsen",
        "Je eigen gidsen en kaarten op het onderste scherm, ook bovenop een game voor"
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
            "PDF / afbeelding maximaal 16 MB; UTF-8 tekst of Markdown maximaal 2 MB. Bladwijzers en"
                + " leespositie worden lokaal onthouden.");
    int count = 0;
    for (String key : OfflineGuides.prefs(a).getAll().keySet())
      if (OfflineGuides.exists(a, key)) {
        count++;
        library.addView(
            Ui.button(
                a,
                Store.name(a, OfflineGuides.owner(a, key))
                    + " · "
                    + OfflineGuides.meta(a, key).optString("name"),
                () -> {
                  selected = OfflineGuides.owner(a, key);
                  Store.prefs(a).edit().putString("guideActive:" + selected, key).commit();
                  a.render();
                }));
      }
    if (count == 0)
      library.addView(Ui.text(a, "Nog geen offline gidsen geïmporteerd.", 14, Ui.MUTED));
  }

  static void guide(MainActivity a, String pkg) {
    String active = OfflineGuides.active(a, pkg);
    JSONObject meta = OfflineGuides.meta(a, active);
    LinearLayout card =
        a.card(
            Store.name(a, pkg),
            OfflineGuides.exists(a, active)
                ? meta.optString("name")
                    + " · "
                    + (meta.optString("kind").equals("pdf")
                        ? meta.optInt("pages") + " pagina's"
                        : meta.optString("kind").equals("image")
                            ? "kaart / afbeelding"
                            : "tekstgids")
                : "Nog geen offline gids voor deze app.");
    card.addView(
        Ui.button(
            a,
            "PDF / tekst / afbeelding toevoegen",
            () -> {
              selected = pkg;
              a.importGuide(pkg + ".doc" + java.util.UUID.randomUUID().toString().replace("-", ""));
            }));
    for (String id : OfflineGuides.prefs(a).getAll().keySet()) {
      if (!OfflineGuides.exists(a, id) || !OfflineGuides.owner(a, id).equals(pkg)) continue;
      LinearLayout row = Ui.row(a);
      row.addView(
          Ui.button(
              a,
              OfflineGuides.meta(a, id).optString("name"),
              () -> {
                Store.prefs(a).edit().putString("guideActive:" + pkg, id).commit();
                OfflineGuides.open(a, id);
              }),
          new LinearLayout.LayoutParams(0, -2, 1));
      row.addView(
          Ui.button(
              a,
              "Verwijderen",
              () ->
                  new Ui.Dialog(a)
                      .setTitle("Lokaal document verwijderen?")
                      .setPositiveButton(
                          "Verwijderen",
                          (d, w) -> {
                            OfflineGuides.remove(a, id);
                            a.render();
                          })
                      .setNegativeButton("Annuleren", null)
                      .show()));
      card.addView(row);
    }
    ExtraFeatures.checklist(a, card, pkg);
    ExtraFeatures.profile(a, card, pkg);
    card.addView(Ui.button(a, "Online gids in mijn browser", () -> Store.guide(a, pkg)));
  }
}
