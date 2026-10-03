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
    GuideTools.library(a, library);
  }

  static void guide(MainActivity a, String pkg) {
    String active = OfflineGuides.active(a, pkg);
    JSONObject meta = OfflineGuides.meta(a, active);
    LinearLayout card =
        a.card(
            Store.name(a, pkg),
            OfflineGuides.exists(a, active)
                ? (meta.optString("kind").equals("pdf")
                    ? meta.optInt("pages") + " pagina's"
                    : meta.optString("kind").equals("image") ? "kaart / afbeelding" : "tekstgids")
                : "Nog geen offline gids voor deze app.");
    card.addView(
        Ui.button(
            a,
            "PDF / tekst / afbeelding toevoegen",
            () -> {
              selected = pkg;
              a.importGuide(pkg + ".doc" + java.util.UUID.randomUUID().toString().replace("-", ""));
            }));
    for (GuideTools.Entry entry : GuideTools.entries(a))
      if (entry.owner.equals(pkg)) GuideTools.documentRow(a, card, entry.id, false);
    ExtraFeatures.checklist(a, card, pkg);
    ExtraFeatures.profile(a, card, pkg);
    card.addView(Ui.button(a, "Online gids in mijn browser", () -> Store.guide(a, pkg)));
  }
}
