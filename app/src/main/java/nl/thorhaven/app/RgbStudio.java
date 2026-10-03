package nl.thorhaven.app;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Controller-friendly RGB editor. Preview and saved profiles work without lighting hardware. */
final class RgbStudio {
  static final String[] EFFECTS = {
    "solid", "breathe", "rainbow", "cycle", "pulse", "battery", "charging"
  };
  static final String[] EFFECT_NAMES = {
    "Vaste kleur",
    "Ademen",
    "Regenboog",
    "Kleurovergang",
    "Zachte puls",
    "Accuniveau",
    "Laadindicatie"
  };
  static final String[] BUILTINS = {
    "Thorhaven", "Aurora", "Ocean", "Ember", "Retro", "Neon", "Battery", "Charging", "Off"
  };

  interface Edit {
    void change(JSONObject settings) throws Exception;
  }

  static boolean change(Context c, Edit action) {
    try {
      RgbSettings.update(c, action::change);
      RgbService.notifySettings(c);
      return true;
    } catch (Exception e) {
      Ui.toast(c, e.getMessage());
      return false;
    }
  }

  static String words(Context c, String dutch, String english) {
    return Language.isEnglish(c) ? english : dutch;
  }

  interface Work {
    void run() throws Exception;
  }

  static boolean finishEdit(MainActivity a, Work work) {
    try {
      work.run();
      RgbService.notifySettings(a);
      a.render();
      return true;
    } catch (Exception e) {
      Ui.toast(a, e.getMessage());
      return false;
    }
  }

  static void open(Context c) {
    c.startActivity(
        new Intent(c, MainActivity.class)
            .putExtra("pageKey", "RGB Studio")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  static void page(MainActivity a) {
    a.heading("RGB Studio", "Twee joysticklichten. Jouw kleuren, effecten en gameprofielen.");
    JSONObject settings = RgbSettings.load(a);
    LinearLayout control = a.card("RGB-sessie", RgbService.status);
    control.addView(
        Ui.text(
            a,
            "Start bewust een sessie. De effecten blijven via een zichtbare Android-melding actief."
                + " Na stoppen worden de AYN-kleur-, aan/uit- en helderheidsinstellingen hersteld."
                + " Zet andere RGB-apps en AYN-animaties uit als ze de kleuren overschrijven.",
            13,
            Ui.MUTED));
    control.addView(
        Ui.button(
            a,
            "RGB starten",
            () -> {
              if (Build.VERSION.SDK_INT >= 33
                  && a.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                      != android.content.pm.PackageManager.PERMISSION_GRANTED)
                a.requestPermissions(
                    new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 71);
              RgbService.start(a);
              a.handler.postDelayed(a::render, 700);
            }));
    control.addView(
        Ui.button(
            a,
            "Stoppen en AYN herstellen",
            () -> {
              RgbService.stop(a);
              a.handler.postDelayed(a::render, 700);
            }));
    if (RgbSession.recovery(a).contains("baseline"))
      control.addView(
          Ui.button(
              a,
              "Herstellen na onderbreking",
              () -> {
                RgbService.recover(a);
                a.handler.postDelayed(a::render, 1000);
              }));
    control.addView(
        Ui.button(
            a,
            "RGB-toegang controleren",
            () -> {
              Controls.worker.execute(
                  () -> {
                    RgbHardware.Probe p = RgbHardware.probe(a.getApplicationContext());
                    a.handler.post(
                        () ->
                            new Ui.Dialog(a)
                                .setTitle("RGB-toegang")
                                .setMessage(
                                    p.message
                                        + "\n\n"
                                        + Language.text(
                                            a,
                                            "Controleert firmwaretoegang; de fysieke lichten en"
                                                + " beide zones moet je op je Thor testen."))
                                .setPositiveButton("Sluiten", null)
                                .show());
                  });
            }));
    control.addView(
        Ui.rawButton(
            a,
            words(a, "RGB-diagnostiek en zones testen", "RGB diagnostics and zone test"),
            () -> RgbDiagnostics.open(a)));
    selection(a, settings);
    LinearLayout preview =
        a.card(
            "Voorbeeld · globaal profiel",
            "Dit voorbeeld gebruikt dezelfde kleurenberekening als de lichten, zonder"
                + " hardwarewijzigingen.");
    preview.addView(
        new Preview(a, settings.optJSONObject("profile"), settings),
        new LinearLayout.LayoutParams(-1, Ui.dp(a, 170)));
    preview.addView(Ui.button(a, "Kleuren en effecten bewerken", () -> editor(a)));
    preview.addView(
        Ui.button(
            a,
            "Links naar rechts kopiëren",
            () -> {
              if (change(
                  a,
                  q ->
                      q.getJSONObject("profile")
                          .put(
                              "right",
                              new JSONObject(
                                  q.getJSONObject("profile").getJSONObject("left").toString()))))
                a.render();
            }));
    preview.addView(
        Ui.button(
            a,
            "Links en rechts omwisselen",
            () -> {
              if (change(
                  a,
                  q -> {
                    JSONObject p = q.getJSONObject("profile");
                    JSONObject l = p.getJSONObject("left"), r = p.getJSONObject("right");
                    p.put("left", r).put("right", l);
                  })) a.render();
            }));
    LinearLayout templates =
        a.card(
            "Snelle stijlen",
            "Kies een stijl als globaal profiel. Je kunt hem daarna aanpassen en met een eigen naam"
                + " bewaren.");
    for (int n = 0; n < BUILTINS.length; n++) {
      final int index = n;
      templates.addView(
          Ui.rawButton(
              a,
              BUILTINS[n],
              () -> {
                if (change(a, q -> q.put("profile", builtIn(index)))) a.render();
              }));
    }
    presets(a, settings);
    sharing(a);
    options(a, settings);
    assignments(a, settings);
  }

  static JSONObject builtIn(int index) throws Exception {
    if (index < 0 || index >= BUILTINS.length) throw new Exception("Invalid RGB style");
    JSONObject p = new JSONObject(RgbSettings.defaults().getJSONObject("profile").toString());
    JSONObject l = p.getJSONObject("left"), r = p.getJSONObject("right");
    switch (index) {
      case 1:
        l.put("effect", "rainbow");
        r.put("effect", "rainbow");
        break;
      case 2:
        l.put("color", "#00C2FF").put("secondary", "#2146E8").put("effect", "cycle");
        r.put("color", "#2146E8").put("secondary", "#00C2FF").put("effect", "cycle");
        break;
      case 3:
        l.put("color", "#FF7D25").put("effect", "breathe");
        r.put("color", "#FF3344").put("effect", "breathe");
        break;
      case 4:
        l.put("color", "#43FF6D");
        r.put("color", "#43FF6D");
        break;
      case 5:
        l.put("color", "#FF4FD8").put("effect", "breathe");
        r.put("color", "#3CDFFF").put("effect", "breathe");
        break;
      case 6:
        l.put("effect", "battery");
        r.put("effect", "battery");
        break;
      case 7:
        l.put("effect", "charging");
        r.put("effect", "charging");
        break;
      case 8:
        l.put("enabled", false);
        r.put("enabled", false);
        break;
      default:
        break;
    }
    RgbSettings.validateProfile(p);
    return p;
  }

  static void savePreset(Context c, String name) throws Exception {
    RgbSettings.update(c, q -> RgbPresetTools.append(q, name, q.getJSONObject("profile")));
  }

  static void deletePreset(Context c, String id) throws Exception {
    RgbPresetTools.remove(c, id);
  }

  static void assign(Context c, String pkg, String id) throws Exception {
    RgbPresetTools.assign(c, pkg, id);
  }

  static void presets(MainActivity a, JSONObject q) {
    LinearLayout box =
        a.card(
            "Mijn RGB-presets",
            "Bewaar maximaal 20 stijlen. App-koppelingen gebruiken een bewaarde preset.");
    JSONArray presets = q.optJSONArray("presets");
    for (int i = 0; i < presets.length(); i++) {
      JSONObject p = presets.optJSONObject(i);
      String id = p.optString("id");
      box.addView(Ui.rawButton(a, p.optString("name"), () -> presetDetails(a, id)));
      box.addView(Ui.rawText(a, profileSummary(a, p.optJSONObject("profile")), 12, Ui.MUTED));
      LinearLayout row = Ui.row(a);
      row.addView(
          Ui.rawButton(a, words(a, "Bewerken", "Edit"), () -> savedPresetEditor(a, id)),
          new LinearLayout.LayoutParams(0, -2, 1));
      row.addView(
          Ui.rawButton(a, words(a, "Dupliceren", "Duplicate"), () -> nameDialog(a, id, true)),
          new LinearLayout.LayoutParams(0, -2, 1));
      row.addView(
          Ui.rawButton(
              a,
              words(a, "Globaal gebruiken", "Use globally"),
              () -> finishEdit(a, () -> RgbPresetTools.useGlobal(a, id))),
          new LinearLayout.LayoutParams(0, -2, 1));
      box.addView(row);
    }
    box.addView(
        Ui.button(
            a,
            "Globaal profiel als preset bewaren",
            () -> {
              EditText name = Ui.input(a, "Naam van de preset");
              android.app.AlertDialog dialog =
                  new Ui.Dialog(a)
                      .setTitle("RGB-preset bewaren")
                      .setView(name)
                      .setPositiveButton("Opslaan", null)
                      .setNegativeButton("Annuleren", null)
                      .create();
              dialog.setOnShowListener(
                  v ->
                      dialog
                          .getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                          .setOnClickListener(
                              button -> {
                                if (finishEdit(a, () -> savePreset(a, name.getText().toString())))
                                  dialog.dismiss();
                              }));
              dialog.show();
            }));
    box.addView(
        Ui.text(
            a,
            "RGB-presets en app-koppelingen zitten in beide Thorhaven-back-ups. Import start geen"
                + " RGB-sessie.",
            13,
            Ui.MUTED));
  }

  static String profileSummary(Context c, JSONObject profile) {
    if (profile == null) return "";
    StringJoiner summary = new StringJoiner(" · ");
    for (String side : new String[] {"left", "right"}) {
      JSONObject p = profile.optJSONObject(side);
      if (p == null) continue;
      String label = side.equals("left") ? words(c, "Links", "Left") : words(c, "Rechts", "Right");
      int index = Arrays.asList(EFFECTS).indexOf(p.optString("effect"));
      String effect = index >= 0 ? Language.text(c, EFFECT_NAMES[index]) : "";
      summary.add(
          label
              + ": "
              + (!p.optBoolean("enabled")
                  ? words(c, "uit", "off")
                  : effect + " " + p.optInt("brightness") + "% " + p.optString("color")));
    }
    return summary.toString();
  }

  static void selection(MainActivity a, JSONObject settings) {
    String pkg =
        RgbService.instance != null
            ? RgbService.instance.foreground
            : ThorService.instance != null ? ThorService.instance.foreground : "";
    String id = settings.optJSONObject("apps").optString(pkg, "");
    JSONObject profile = settings.optJSONObject("profile");
    String name = words(a, "Globaal profiel", "Global profile");
    if (!id.isEmpty()) {
      try {
        JSONObject preset = RgbPresetTools.require(settings, id);
        profile = preset.getJSONObject("profile");
        name = preset.getString("name");
      } catch (Exception ignored) {
      }
    }
    LinearLayout box = a.card(words(a, "Huidige profielkeuze", "Current profile selection"), null);
    box.addView(
        Ui.rawText(
            a,
            (pkg.isEmpty()
                    ? words(a, "Geen herkende app", "No recognized app")
                    : Store.name(a, pkg))
                + " · "
                + name,
            15,
            Ui.TEXT));
    box.addView(Ui.rawText(a, profileSummary(a, profile), 12, Ui.MUTED));
    box.addView(
        new Preview(a, profile, settings), new LinearLayout.LayoutParams(-1, Ui.dp(a, 130)));
    box.addView(
        Ui.rawText(
            a,
            words(
                a,
                "Dit is de profielkeuze bij het openen van deze pagina. Start RGB om de lichten te"
                    + " bedienen; vernieuw na een appwissel.",
                "This shows the selection when this page opened. Start RGB to control the lights;"
                    + " refresh after switching apps."),
            12,
            Ui.MUTED));
    box.addView(
        Ui.rawButton(a, words(a, "Profielkeuze vernieuwen", "Refresh selection"), a::render));
  }

  static void sharing(MainActivity a) {
    LinearLayout box = a.card(words(a, "RGB-presets delen", "Share RGB presets"), null);
    box.addView(
        Ui.rawText(
            a,
            words(
                a,
                "Deel alleen je bewaarde presets. Globaal profiel, app-koppelingen en"
                    + " herstelgegevens worden niet meegestuurd.",
                "Share saved presets only. Your global profile, app assignments and recovery data"
                    + " are not included."),
            13,
            Ui.MUTED));
    box.addView(
        Ui.rawButton(
            a, words(a, "Presets exporteren", "Export presets"), () -> RgbPresetBundle.export(a)));
    box.addView(
        Ui.rawButton(
            a,
            words(a, "Presets importeren", "Import presets"),
            () -> RgbPresetBundle.importFile(a)));
  }

  static void nameDialog(MainActivity a, String id, boolean duplicate) {
    try {
      JSONObject preset = RgbPresetTools.preset(a, id);
      EditText name = Ui.input(a, "Naam van de preset");
      name.setText(preset.getString("name"));
      name.setFilters(
          new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(60)});
      android.app.AlertDialog dialog =
          new android.app.AlertDialog.Builder(a)
              .setTitle(
                  words(
                      a,
                      duplicate ? "RGB-preset dupliceren" : "RGB-preset hernoemen",
                      duplicate ? "Duplicate RGB preset" : "Rename RGB preset"))
              .setView(name)
              .setPositiveButton(Language.text(a, "Opslaan"), null)
              .setNegativeButton(Language.text(a, "Annuleren"), null)
              .create();
      dialog.setOnShowListener(
          v ->
              dialog
                  .getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                  .setOnClickListener(
                      button -> {
                        if (finishEdit(
                            a,
                            () -> {
                              if (duplicate)
                                RgbPresetTools.duplicate(a, id, name.getText().toString());
                              else RgbPresetTools.rename(a, id, name.getText().toString());
                            })) dialog.dismiss();
                      }));
      dialog.show();
      name.setSelectAllOnFocus(true);
    } catch (Exception e) {
      Ui.toast(a, e.getMessage());
    }
  }

  static void presetDetails(MainActivity a, String id) {
    try {
      JSONObject settings = RgbSettings.load(a), preset = RgbPresetTools.require(settings, id);
      LinearLayout box = Ui.col(a);
      box.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 8));
      box.addView(Ui.rawText(a, preset.getString("name"), 20, Ui.TEXT));
      box.addView(Ui.rawText(a, sharedMessage(a, settings, id), 13, Ui.MUTED));
      box.addView(
          new Preview(a, preset.getJSONObject("profile"), settings),
          new LinearLayout.LayoutParams(-1, Ui.dp(a, 150)));
      android.app.AlertDialog dialog =
          new android.app.AlertDialog.Builder(a)
              .setTitle(words(a, "Bewaarde RGB-preset", "Saved RGB preset"))
              .setView(box)
              .setNegativeButton(Language.text(a, "Sluiten"), null)
              .create();
      box.addView(
          Ui.rawButton(
              a,
              words(a, "Preset rechtstreeks bewerken", "Edit this preset directly"),
              () -> {
                dialog.dismiss();
                savedPresetEditor(a, id);
              }));
      box.addView(
          Ui.rawButton(
              a,
              words(a, "Dupliceren met eigen naam", "Duplicate with your own name"),
              () -> {
                dialog.dismiss();
                nameDialog(a, id, true);
              }));
      box.addView(
          Ui.rawButton(
              a,
              words(a, "Als globaal profiel gebruiken", "Use as global profile"),
              () -> {
                if (finishEdit(a, () -> RgbPresetTools.useGlobal(a, id))) dialog.dismiss();
              }));
      box.addView(
          Ui.rawButton(
              a,
              words(a, "Naam wijzigen", "Rename"),
              () -> {
                dialog.dismiss();
                nameDialog(a, id, false);
              }));
      box.addView(
          Ui.rawButton(
              a,
              words(a, "Vervangen door globaal profiel", "Replace with global profile"),
              () -> {
                dialog.dismiss();
                confirmShared(a, id, false);
              }));
      box.addView(
          Ui.rawButton(
              a,
              words(a, "Verwijderen", "Delete"),
              () -> {
                dialog.dismiss();
                confirmShared(a, id, true);
              }));
      ScrollView scroll = new ScrollView(a);
      scroll.addView(box);
      dialog.setView(scroll);
      dialog.show();
    } catch (Exception e) {
      Ui.toast(a, e.getMessage());
    }
  }

  static String sharedMessage(Context c, JSONObject settings, String id) {
    int count = RgbPresetTools.assignedApps(settings, id);
    return words(
        c,
        "Gedeelde preset voor "
            + count
            + " app-koppelingen. Bewerken wijzigt hun stijl en laat het globale profiel intact."
            + " Dupliceren maakt een onafhankelijke kopie. Opslaan start geen RGB-sessie.",
        "Shared preset for "
            + count
            + " app assignments. Editing changes their style and keeps the global profile intact."
            + " Duplicating makes an independent copy. Saving does not start RGB.");
  }

  static void confirmShared(MainActivity a, String id, boolean delete) {
    try {
      JSONObject settings = RgbSettings.load(a), preset = RgbPresetTools.require(settings, id);
      new android.app.AlertDialog.Builder(a)
          .setTitle(
              words(
                  a,
                  delete ? "Preset verwijderen?" : "Preset vervangen?",
                  delete ? "Delete preset?" : "Replace preset?"))
          .setMessage(
              preset.getString("name")
                  + "\n\n"
                  + (delete
                      ? words(
                          a,
                          "De app-koppelingen naar deze preset worden ook verwijderd.",
                          "App assignments to this preset are also removed.")
                      : sharedMessage(a, settings, id)))
          .setPositiveButton(
              words(a, delete ? "Verwijderen" : "Vervangen", delete ? "Delete" : "Replace"),
              (d, w) ->
                  finishEdit(
                      a,
                      () -> {
                        if (delete) RgbPresetTools.remove(a, id);
                        else RgbPresetTools.updateFromGlobal(a, id);
                      }))
          .setNegativeButton(Language.text(a, "Annuleren"), null)
          .show();
    } catch (Exception e) {
      Ui.toast(a, e.getMessage());
    }
  }

  static void options(MainActivity a, JSONObject q) {
    LinearLayout box =
        a.card(
            "Lichtgedrag",
            "De grenzen gelden voor beide lichten. Wijzigingen worden in een actieve sessie op de"
                + " volgende update gebruikt.");
    toggle(a, box, q, "followScreen", "RGB dimmen met systeemhelderheid");
    toggle(a, box, q, "lowBatteryDim", "Onder 20% accu extra dimmen");
    toggle(a, box, q, "screenOffPause", "Pauzeren wanneer de schermen uit zijn");
    choose(
        a,
        box,
        "Snelheid van updates",
        new String[] {
          "Zuinig · 2 per seconde", "Gebalanceerd · 5 per seconde", "Vloeiend · 10 per seconde"
        },
        q.optInt("fps") == 2 ? 0 : q.optInt("fps") == 10 ? 2 : 1,
        index -> {
          if (change(a, s -> s.put("fps", new int[] {2, 5, 10}[index]))) a.render();
        });
    int[] values = {0, 5, 15, 30, 60};
    int current = 0;
    for (int n = 0; n < values.length; n++) if (q.optInt("timerMinutes") == values[n]) current = n;
    choose(
        a,
        box,
        "Automatisch stoppen na",
        new String[] {"Geen timer", "5 minuten", "15 minuten", "30 minuten", "60 minuten"},
        current,
        index -> {
          if (change(a, s -> s.put("timerMinutes", values[index]))) a.render();
        });
    box.addView(
        Ui.text(
            a,
            "De timer telt vanaf de start van de sessie, ook tijdens schermpauze. Dimmen volgt"
                + " Androids systeemhelderheid, niet afzonderlijk de twee schermen. Via de root- of"
                + " AYN-koppeling zijn updates begrensd op 2 per seconde; directe toegang gebruikt"
                + " je keuze hierboven.",
            12,
            Ui.MUTED));
  }

  static void assignments(MainActivity a, JSONObject q) {
    LinearLayout box =
        a.card(
            "RGB per app",
            "Binnen een actieve RGB-sessie wisselt de optionele toegankelijkheidsservice"
                + " automatisch van preset. Apps zonder koppeling gebruiken het globale profiel.");
    if (ThorService.instance == null)
      box.addView(
          Ui.text(
              a,
              "Activeer de controller-service bij Instellen voor automatische app-herkenning.",
              13,
              Ui.MUTED));
    JSONObject apps = q.optJSONObject("apps");
    List<String> packages = new ArrayList<>();
    for (Iterator<String> it = apps.keys(); it.hasNext(); ) packages.add(it.next());
    Collections.sort(packages);
    for (String pkg : packages) {
      String id = apps.optString(pkg), label = id;
      JSONArray ps = q.optJSONArray("presets");
      for (int i = 0; i < ps.length(); i++)
        if (id.equals(ps.optJSONObject(i).optString("id")))
          label = ps.optJSONObject(i).optString("name");
      box.addView(Ui.rawText(a, Store.name(a, pkg) + " · " + label, 14, Ui.TEXT));
      box.addView(Ui.rawText(a, pkg, 12, Ui.MUTED));
      box.addView(
          Ui.rawButton(
              a,
              words(a, "Gedeelde preset bewerken", "Edit shared preset"),
              () -> savedPresetEditor(a, id)));
      box.addView(
          Ui.rawButton(
              a,
              words(a, "Andere preset kiezen", "Choose another preset"),
              () -> choosePreset(a, pkg)));
      box.addView(
          Ui.button(
              a,
              "Koppeling verwijderen",
              () -> {
                finishEdit(a, () -> assign(a, pkg, ""));
              }));
    }
    box.addView(Ui.button(a, "App aan RGB-preset koppelen", () -> appPicker(a)));
  }

  static void choosePreset(MainActivity a, String pkg) {
    JSONObject settings = RgbSettings.load(a);
    JSONArray presets = settings.optJSONArray("presets");
    if (presets.length() == 0) {
      Ui.toast(a, "Bewaar eerst een RGB-preset.");
      return;
    }
    LinearLayout box = Ui.col(a);
    box.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 8));
    box.addView(Ui.rawText(a, Store.name(a, pkg), 17, Ui.TEXT));
    box.addView(Ui.rawText(a, pkg, 12, Ui.MUTED));
    box.addView(
        Ui.rawText(
            a,
            words(
                a,
                "Kies een bewaarde preset. De koppeling start geen sessie; een actieve sessie"
                    + " gebruikt hem bij deze app.",
                "Choose a saved preset. Assigning does not start a session; an active session uses"
                    + " it for this app."),
            13,
            Ui.MUTED));
    ScrollView scroll = new ScrollView(a);
    scroll.addView(box);
    android.app.AlertDialog dialog =
        new android.app.AlertDialog.Builder(a)
            .setTitle(words(a, "Kies een RGB-preset", "Choose an RGB preset"))
            .setView(scroll)
            .setNegativeButton(Language.text(a, "Annuleren"), null)
            .create();
    String current = settings.optJSONObject("apps").optString(pkg, "");
    for (int i = 0; i < presets.length(); i++) {
      JSONObject preset = presets.optJSONObject(i);
      String id = preset.optString("id");
      String name = preset.optString("name");
      Button select =
          Ui.rawButton(
              a,
              name,
              () -> {
                if (finishEdit(a, () -> assign(a, pkg, id))) dialog.dismiss();
              });
      if (id.equals(current)) select.setTextColor(Ui.ACCENT);
      box.addView(select);
      if (id.equals(current))
        box.addView(
            Ui.rawText(a, words(a, "Huidige koppeling", "Current assignment"), 12, Ui.ACCENT));
      box.addView(Ui.rawText(a, profileSummary(a, preset.optJSONObject("profile")), 12, Ui.MUTED));
    }
    dialog.show();
  }

  static android.app.AlertDialog appPicker(MainActivity a) {
    if (RgbSettings.load(a).optJSONArray("presets").length() == 0) {
      Ui.toast(a, "Bewaar eerst een RGB-preset.");
      return null;
    }
    LinearLayout box = Ui.col(a);
    box.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 8));
    EditText search =
        Ui.input(a, words(a, "Zoek op appnaam of pakketnaam", "Search app name or package"));
    box.addView(search);
    TextView count = Ui.rawText(a, words(a, "Apps laden…", "Loading apps…"), 13, Ui.MUTED);
    box.addView(count);
    ListView list = new ListView(a);
    box.addView(list, new LinearLayout.LayoutParams(-1, Ui.dp(a, 320)));
    List<Store.App> installed = new ArrayList<>(), visible = new ArrayList<>();
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(a, android.R.layout.simple_list_item_1, new ArrayList<>());
    list.setAdapter(adapter);
    android.app.AlertDialog dialog =
        new android.app.AlertDialog.Builder(a)
            .setTitle(words(a, "App aan RGB-preset koppelen", "Assign app to RGB preset"))
            .setView(box)
            .setNegativeButton(Language.text(a, "Annuleren"), null)
            .create();
    final boolean[] loaded = {false};
    Runnable filter =
        () -> {
          visible.clear();
          visible.addAll(RgbPresetTools.matchingApps(installed, search.getText().toString()));
          adapter.setNotifyOnChange(false);
          adapter.clear();
          for (Store.App app : visible) adapter.add(app.name + "\n" + app.pkg);
          adapter.notifyDataSetChanged();
          count.setText(
              !loaded[0]
                  ? words(a, "Apps laden…", "Loading apps…")
                  : visible.isEmpty()
                      ? words(a, "Geen apps gevonden", "No matching apps")
                      : visible.size() + words(a, " apps gevonden", " apps found"));
        };
    search.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            filter.run();
          }

          public void afterTextChanged(android.text.Editable s) {}
        });
    list.setOnItemClickListener(
        (parent, view, position, itemId) -> {
          if (position < 0 || position >= visible.size()) return;
          String pkg = visible.get(position).pkg;
          dialog.dismiss();
          choosePreset(a, pkg);
        });
    dialog.show();
    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    Controls.worker.execute(
        () -> {
          try {
            List<Store.App> found = Store.apps(a.getApplicationContext());
            a.handler.post(
                () -> {
                  if (!dialog.isShowing() || a.isFinishing() || a.isDestroyed()) return;
                  installed.addAll(found);
                  loaded[0] = true;
                  filter.run();
                });
          } catch (RuntimeException e) {
            a.handler.post(
                () -> {
                  if (dialog.isShowing())
                    count.setText(
                        words(
                            a,
                            "Apps konden niet worden geladen. Sluit dit venster en probeer"
                                + " opnieuw.",
                            "Apps could not be loaded. Close this window and try again."));
                });
          }
        });
    return dialog;
  }

  static void toggle(MainActivity a, LinearLayout box, JSONObject q, String key, String label) {
    Switch value = new Switch(a);
    value.setText(Language.text(a, label));
    value.setTextColor(Ui.TEXT);
    value.setMinHeight(Ui.dp(a, 48));
    value.setChecked(q.optBoolean(key));
    value.setOnCheckedChangeListener(
        (v, checked) -> {
          if (!change(a, s -> s.put(key, checked))) value.setChecked(!checked);
        });
    box.addView(value);
  }

  static void choose(
      Context c,
      LinearLayout parent,
      String label,
      String[] values,
      int selected,
      java.util.function.IntConsumer onSelect) {
    Button button = Ui.button(c, label + " · " + values[selected], () -> {});
    final int[] current = {selected};
    button.setOnClickListener(
        v ->
            new Ui.Dialog(c)
                .setTitle(label)
                .setSingleChoiceItems(
                    Language.labels(c, values),
                    current[0],
                    (d, index) -> {
                      current[0] = index;
                      button.setText(label + " · " + values[index]);
                      d.dismiss();
                      onSelect.accept(index);
                    })
                .setNegativeButton("Annuleren", null)
                .show());
    parent.addView(button);
  }

  static void editor(MainActivity a) {
    try {
      JSONObject options = RgbSettings.load(a),
          profile = new JSONObject(options.getJSONObject("profile").toString());
      profileEditor(
          a,
          options,
          profile,
          words(a, "Globaal profiel bewerken", "Edit global profile"),
          words(a, "Globaal profiel", "Global profile"),
          words(
              a,
              "Opslaan wijzigt alleen het globale profiel. Bewaarde presets blijven intact.",
              "Saving changes only the global profile. Saved presets remain unchanged."),
          () -> RgbSettings.update(a, s -> s.put("profile", new JSONObject(profile.toString()))));
    } catch (Exception e) {
      Ui.toast(a, e.getMessage());
    }
  }

  static android.app.AlertDialog savedPresetEditor(MainActivity a, String id) {
    try {
      JSONObject options = RgbSettings.load(a), preset = RgbPresetTools.require(options, id);
      JSONObject profile = new JSONObject(preset.getJSONObject("profile").toString());
      return profileEditor(
          a,
          options,
          profile,
          words(a, "Bewaarde preset bewerken", "Edit saved preset"),
          preset.getString("name"),
          sharedMessage(a, options, id),
          () -> RgbPresetTools.edit(a, id, profile));
    } catch (Exception e) {
      Ui.toast(a, e.getMessage());
      return null;
    }
  }

  static android.app.AlertDialog profileEditor(
      MainActivity a,
      JSONObject options,
      JSONObject profile,
      String title,
      String name,
      String explanation,
      Work save)
      throws Exception {
    LinearLayout box = Ui.col(a);
    box.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 8));
    box.addView(Ui.rawText(a, name, 20, Ui.TEXT));
    box.addView(Ui.rawText(a, explanation, 13, Ui.MUTED));
    box.addView(new Preview(a, profile, options), new LinearLayout.LayoutParams(-1, Ui.dp(a, 150)));
    sideEditor(a, box, profile.getJSONObject("left"), "Linker licht");
    sideEditor(a, box, profile.getJSONObject("right"), "Rechter licht");
    ScrollView scroll = new ScrollView(a);
    scroll.addView(box);
    android.app.AlertDialog dialog =
        new android.app.AlertDialog.Builder(a)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(Language.text(a, "Opslaan"), null)
            .setNegativeButton(Language.text(a, "Annuleren"), null)
            .create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    button -> {
                      if (finishEdit(a, save)) dialog.dismiss();
                    }));
    dialog.show();
    return dialog;
  }

  static void sideEditor(MainActivity a, LinearLayout parent, JSONObject side, String title) {
    LinearLayout box =
        Ui.card(
            a,
            parent,
            title,
            "Een eigen aan/uit-stand, kleur, effect en helderheid voor dit licht.");
    Switch enabled = new Switch(a);
    enabled.setText(Language.text(a, "Dit licht aan"));
    enabled.setTextColor(Ui.TEXT);
    enabled.setMinHeight(Ui.dp(a, 48));
    enabled.setChecked(side.optBoolean("enabled"));
    enabled.setOnCheckedChangeListener((v, on) -> put(side, "enabled", on));
    box.addView(enabled);
    colorInput(a, box, side, "color", "Hoofdkleur · #RRGGBB");
    colorInput(a, box, side, "secondary", "Tweede kleur voor kleurovergang · #RRGGBB");
    Ui.seek(
        a,
        box,
        "Lichtsterkte (%)",
        100,
        side.optInt("brightness"),
        value -> put(side, "brightness", value));
    int effect = Arrays.asList(EFFECTS).indexOf(side.optString("effect"));
    choose(
        a,
        box,
        "Effect",
        EFFECT_NAMES,
        Math.max(0, effect),
        index -> {
          put(side, "effect", EFFECTS[index]);
        });
    TextView duration =
        Ui.text(a, "Effectduur (seconden) · " + side.optInt("speedMs") / 1000, 14, Ui.MUTED);
    box.addView(duration);
    SeekBar speed = new SeekBar(a);
    speed.setMin(2);
    speed.setMax(20);
    speed.setProgress(side.optInt("speedMs") / 1000);
    box.addView(speed, new LinearLayout.LayoutParams(-1, Ui.dp(a, 40)));
    speed.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar b, int value, boolean user) {
            duration.setText("Effectduur (seconden) · " + value);
            if (user) put(side, "speedMs", value * 1000);
          }

          public void onStartTrackingTouch(SeekBar b) {}

          public void onStopTrackingTouch(SeekBar b) {}
        });
    box.addView(
        Ui.text(
            a,
            "Accukleuren gebruiken de werkelijk gemeten accu; laadindicatie ademt alleen tijdens"
                + " laden.",
            12,
            Ui.MUTED));
  }

  static void colorInput(Context c, LinearLayout box, JSONObject side, String key, String hint) {
    EditText input = Ui.input(c, hint);
    input.setText(side.optString(key));
    input.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(7)});
    input.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            put(side, key, s.toString().toUpperCase(Locale.ROOT));
          }

          public void afterTextChanged(android.text.Editable s) {}
        });
    box.addView(Ui.text(c, hint, 13, Ui.MUTED));
    box.addView(input);
    String[] colors = {"#62E5C0", "#D56CEF", "#FF3344", "#FFB52E", "#3CBFFF", "#FFFFFF"};
    for (int start = 0; start < colors.length; start += 3) {
      LinearLayout row = Ui.row(c);
      for (int n = start; n < start + 3; n++) {
        String color = colors[n];
        Button b = Ui.rawButton(c, color, () -> input.setText(color));
        b.setTextColor(Color.parseColor(color));
        row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
      }
      box.addView(row);
    }
  }

  static void put(JSONObject q, String key, Object value) {
    try {
      q.put(key, value);
    } catch (JSONException ignored) {
    }
  }

  static final class Preview extends View {
    final JSONObject profile, options;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Handler handler = new Handler(Looper.getMainLooper());
    final long started = SystemClock.elapsedRealtime();
    final Runnable animate =
        new Runnable() {
          public void run() {
            invalidate();
            handler.postDelayed(this, 200);
          }
        };

    Preview(Context c, JSONObject p, JSONObject q) {
      super(c);
      profile = p;
      options = q;
      setContentDescription(Language.text(c, "Voorbeeld van de linker en rechter RGB-ring"));
    }

    @Override
    protected void onAttachedToWindow() {
      super.onAttachedToWindow();
      handler.post(animate);
    }

    @Override
    protected void onDetachedFromWindow() {
      handler.removeCallbacks(animate);
      super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      try {
        int screen =
            Math.max(
                0,
                Math.min(
                    100,
                    android.provider.Settings.System.getInt(
                            getContext().getContentResolver(),
                            android.provider.Settings.System.SCREEN_BRIGHTNESS,
                            -1)
                        * 100
                        / 255));
        RgbEngine.Frame frame =
            RgbEngine.render(
                profile,
                SystemClock.elapsedRealtime() - started,
                PlayStats.level(getContext()),
                PlayStats.plugged(getContext()),
                screen,
                options);
        float radius = Math.min(getWidth() / 6f, getHeight() / 3.5f), y = getHeight() * .42f;
        for (int n = 0; n < 2; n++) {
          float x = getWidth() * (n == 0 ? .27f : .73f);
          int color = n == 0 ? frame.left : frame.right;
          paint.setStyle(Paint.Style.STROKE);
          paint.setStrokeWidth(Ui.dp(getContext(), 12));
          paint.setColor(0xFF000000 | color);
          canvas.drawCircle(x, y, radius, paint);
          paint.setStrokeWidth(Ui.dp(getContext(), 1));
          paint.setColor(Ui.MUTED);
          canvas.drawCircle(x, y, radius + Ui.dp(getContext(), 8), paint);
          paint.setStyle(Paint.Style.FILL);
          paint.setColor(Ui.TEXT);
          paint.setTextSize(Ui.dp(getContext(), 14));
          paint.setTextAlign(Paint.Align.CENTER);
          canvas.drawText(
              Language.text(getContext(), n == 0 ? "Links" : "Rechts"),
              x,
              y + radius + Ui.dp(getContext(), 31),
              paint);
        }
      } catch (Exception ignored) {
        paint.setColor(Ui.MUTED);
        paint.setTextSize(Ui.dp(getContext(), 14));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(
            Language.text(getContext(), "Voer een geldige kleur in voor het voorbeeld."),
            getWidth() / 2f,
            getHeight() / 2f,
            paint);
      }
    }
  }
}
