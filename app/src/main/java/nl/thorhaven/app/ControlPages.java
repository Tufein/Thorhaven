package nl.thorhaven.app;

import android.app.*;
import android.widget.*;
import org.json.*;

final class ControlPages {
  static String profile = "global";
  static JSONObject hardware;

  static void result(MainActivity a, JSONObject r) {
    Ui.toast(a, r.optString("error", r.optString("message", "Controle voltooid")));
  }

  static void choice(
      MainActivity a,
      LinearLayout parent,
      String label,
      String[] names,
      int selected,
      java.util.function.IntConsumer callback) {
    parent.addView(
        Ui.button(
            a,
            label + " · " + names[Math.max(0, Math.min(selected, names.length - 1))],
            () ->
                new Ui.Dialog(a)
                    .setTitle(label)
                    .setSingleChoiceItems(
                        names,
                        selected,
                        (d, i) -> {
                          callback.accept(i);
                          d.dismiss();
                        })
                    .setNegativeButton("Annuleren", null)
                    .show()));
  }

  static void mapping(MainActivity a) {
    a.heading(
        "Controller-mapping",
        "Profielen per app. Activeer alleen de ingebouwde controller die je zelf kiest.");
    LinearLayout state =
        a.card(
            Controls.padStatus,
            "Roottoegang en de toegankelijkheidsservice zijn nodig. De virtuele controller kan in"
                + " een emulator als een extra speler verschijnen; kies daar Thorhaven Controller"
                + " als invoer.");
    state.addView(
        Ui.button(
            a,
            "Noodstop · originele controller terug",
            () -> {
              Controls.stop(a);
              a.handler.postDelayed(a::render, 300);
            }));
    state.addView(
        Ui.text(
            a,
            "Hardware-noodstop: houd de fysieke Select + Start drie seconden vast. Remapping start"
                + " nooit automatisch na een herstart.",
            13,
            Ui.MUTED));
    state.addView(
        Ui.button(
            a,
            "Zoek beschikbare controllers",
            () ->
                Controls.probe(
                    a,
                    r -> {
                      if (r.has("error")) {
                        result(a, r);
                        return;
                      }
                      JSONArray devices = r.optJSONArray("devices");
                      if (devices == null || devices.length() == 0) {
                        Ui.toast(a, "Geen leesbare gamepad gevonden. Controleer roottoegang.");
                        return;
                      }
                      String[] names = new String[devices.length()];
                      for (int i = 0; i < names.length; i++) {
                        JSONObject d = devices.optJSONObject(i);
                        names[i] =
                            d.optString("name")
                                + " · event"
                                + d.optInt("event")
                                + (d.optInt("bus") == 5 ? " · Bluetooth" : "");
                      }
                      new Ui.Dialog(a)
                          .setTitle("Kies de ingebouwde Thor-controller")
                          .setItems(
                              names,
                              (dialog, i) -> {
                                JSONObject d = devices.optJSONObject(i);
                                new Ui.Dialog(a)
                                    .setTitle("Remapping activeren?")
                                    .setMessage(
                                        names[i]
                                            + " wordt exclusief door Thorhaven verwerkt totdat je"
                                            + " stopt. Kies de ingebouwde controller; Bluetooth- en"
                                            + " USB-controllers blijven vrij zolang je die niet"
                                            + " kiest.")
                                    .setPositiveButton(
                                        "Activeren",
                                        (confirm, n) ->
                                            Controls.start(
                                                a,
                                                d.optInt("event"),
                                                profile,
                                                response -> {
                                                  result(a, response);
                                                  a.render();
                                                }))
                                    .setNegativeButton("Annuleren", null)
                                    .show();
                              })
                          .setNegativeButton("Annuleren", null)
                          .show();
                    })));
    LinearLayout presets =
        a.card(
            "Snel beginnen",
            "Een preset vervangt en bewaart dit controllerprofiel. Je kunt daarna iedere knop"
                + " aanpassen.");
    presets.addView(
        Ui.button(
            a,
            "Kies controllerpreset",
            () ->
                new Ui.Dialog(a)
                    .setTitle("Preset voor dit profiel")
                    .setItems(
                        new String[] {
                          "Xbox / standaard",
                          "Nintendo · A/B en X/Y wisselen",
                          "PC-menu · pijlen, Enter, Escape",
                          "PC WASD · D-pad, spatie, Escape"
                        },
                        (d, index) -> {
                          try {
                            Controls.save(a, profile, PadProfile.preset(index));
                            a.render();
                          } catch (Exception e) {
                            Ui.toast(a, e.getMessage());
                          }
                        })
                    .setNegativeButton("Annuleren", null)
                    .show()));
    presets.addView(
        Ui.button(
            a,
            "Kopieer profiel van een app",
            () ->
                a.pick(
                    "Profiel kopiëren van…",
                    app -> {
                      try {
                        Controls.save(a, profile, Controls.load(a, app.pkg));
                        a.render();
                        Ui.toast(a, "Profiel gekopieerd");
                      } catch (Exception e) {
                        Ui.toast(a, e.getMessage());
                      }
                    })));
    String title = profile.equals("global") ? "Standaard voor alle apps" : Store.name(a, profile);
    LinearLayout editor =
        a.card(
            title,
            "Een app zonder eigen profiel gebruikt het standaardprofiel. Bij een appwissel kiest de"
                + " toegankelijkheidsservice het bijbehorende profiel.");
    editor.addView(
        Ui.button(
            a,
            "Kies app-profiel",
            () ->
                a.pick(
                    "Controllerprofiel voor…",
                    app -> {
                      profile = app.pkg;
                      a.render();
                    })));
    editor.addView(
        Ui.button(
            a,
            "Standaardprofiel bewerken",
            () -> {
              profile = "global";
              a.render();
            }));
    try {
      JSONObject p = Controls.load(a, profile);
      String editing = profile;
      String[] targetNames = new String[PadProfile.LABELS.length + PadProfile.EXTRA_LABELS.length];
      int[] targetCodes = new int[targetNames.length];
      System.arraycopy(PadProfile.LABELS, 0, targetNames, 0, PadProfile.LABELS.length);
      System.arraycopy(
          PadProfile.EXTRA_LABELS,
          0,
          targetNames,
          PadProfile.LABELS.length,
          PadProfile.EXTRA_LABELS.length);
      System.arraycopy(PadProfile.CODES, 0, targetCodes, 0, PadProfile.CODES.length);
      System.arraycopy(
          PadProfile.EXTRA, 0, targetCodes, PadProfile.CODES.length, PadProfile.EXTRA.length);
      JSONArray buttons = p.getJSONArray("buttons");
      for (int i = 0; i < PadProfile.CODES.length; i++) {
        final int index = i;
        int current = 0;
        for (int t = 0; t < targetCodes.length; t++)
          if (targetCodes[t] == buttons.getInt(i)) current = t;
        Button button = Ui.button(a, PadProfile.LABELS[i] + " → " + targetNames[current], () -> {});
        button.setOnClickListener(
            v ->
                new Ui.Dialog(a)
                    .setTitle(PadProfile.LABELS[index] + " omzetten naar…")
                    .setItems(
                        targetNames,
                        (d, t) -> {
                          try {
                            buttons.put(index, targetCodes[t]);
                            button.setText(PadProfile.LABELS[index] + " → " + targetNames[t]);
                          } catch (Exception ignored) {
                          }
                        })
                    .setNegativeButton("Annuleren", null)
                    .show());
        editor.addView(button);
      }
      Ui.seek(a, editor, "Dode zone links (%)", 40, p.getInt("left"), v -> put(p, "left", v));
      Ui.seek(a, editor, "Dode zone rechts (%)", 40, p.getInt("right"), v -> put(p, "right", v));
      String[] invert = {
        "Links horizontaal omkeren",
        "Links verticaal omkeren",
        "Rechts horizontaal omkeren",
        "Rechts verticaal omkeren"
      };
      for (int i = 0; i < 4; i++) {
        final int bit = 1 << i;
        Switch s = new Switch(a);
        s.setText(Language.text(a, invert[i]));
        s.setTextColor(Ui.TEXT);
        s.setMinHeight(Ui.dp(a, 48));
        s.setChecked((p.getInt("mask") & bit) != 0);
        s.setOnCheckedChangeListener(
            (b, on) -> put(p, "mask", on ? p.optInt("mask") | bit : p.optInt("mask") & ~bit));
        editor.addView(s);
      }
      Switch swap = new Switch(a);
      swap.setText(Language.text(a, "Linker- en rechterstick wisselen"));
      swap.setTextColor(Ui.TEXT);
      swap.setMinHeight(Ui.dp(a, 48));
      swap.setChecked(p.getInt("swap") == 1);
      swap.setOnCheckedChangeListener((b, on) -> put(p, "swap", on ? 1 : 0));
      editor.addView(swap);
      RadioGroup curves = new RadioGroup(a);
      String[] names = {
        "Lineaire gevoeligheid", "Fijner rond het midden", "Sneller rond het midden"
      };
      for (int i = 0; i < 3; i++) {
        RadioButton b = new RadioButton(a);
        b.setId(100 + i);
        b.setText(Language.text(a, names[i]));
        b.setTextColor(Ui.TEXT);
        curves.addView(b);
      }
      curves.check(100 + p.getInt("curve"));
      curves.setOnCheckedChangeListener((g, id) -> put(p, "curve", id - 100));
      editor.addView(curves);
      editor.addView(
          Ui.button(
              a,
              "Profiel opslaan",
              () -> {
                try {
                  Controls.save(a, editing, p);
                  Ui.toast(a, "Controllerprofiel opgeslagen");
                } catch (Exception e) {
                  Ui.toast(a, e.getMessage());
                }
              }));
      editor.addView(
          Ui.button(
              a,
              "Profiel terug naar standaard",
              () -> {
                Store.prefs(a).edit().remove("mapping:" + editing).commit();
                Controls.foreground(a, Controls.currentProfile);
                a.render();
              }));
    } catch (Exception e) {
      editor.addView(Ui.text(a, "Profiel niet leesbaar: " + e.getMessage(), 14, Ui.MUTED));
    }
  }

  static void put(JSONObject j, String key, Object value) {
    try {
      j.put(key, value);
    } catch (Exception ignored) {
    }
  }

  static void system(MainActivity a) {
    a.content.addView(Ui.button(a, "RGB Studio · kleuren en effecten", () -> a.go("RGB Studio")));
    a.heading("Thor-systeem", "Prestatiemodus, ventilator, CPU-limieten en joystickverlichting.");
    LinearLayout access =
        a.card(
            "AYN-firmware controleren",
            "De app toont alleen instellingen die jouw Thor-firmware aanbiedt. Toegang verloopt via"
                + " de AYN-rootservice of Shizuku in rootmodus.");
    access.addView(
        Ui.button(
            a,
            "Lees beschikbare systeemregelaars",
            () ->
                Controls.status(
                    a,
                    r -> {
                      if (r.has("error")) {
                        hardware = null;
                        result(a, r);
                      } else hardware = r;
                      a.render();
                    })));
    access.addView(
        Ui.button(
            a,
            "Herstel mijn eerdere instellingen",
            () ->
                Controls.restore(
                    a,
                    r -> {
                      result(a, r);
                      hardware = null;
                      a.render();
                    })));
    if (hardware == null) {
      access.addView(
          Ui.text(a, "Tik op controleren om de actuele mogelijkheden te laden.", 14, Ui.MUTED));
      return;
    }
    JSONObject settings = hardware.optJSONObject("settings");
    if (settings == null) return;
    LinearLayout performance =
        a.card(
            hardware.optString("model", "AYN Thor"),
            "Wijzigingen worden teruggelezen. De herstelknop bewaart de waarden van vóór jouw"
                + " eerste wijziging; instellingen die je elders hebt veranderd, worden"
                + " overgeslagen.");
    if (settings.has("performance_mode")) {
      String[] names = {"Standaard", "Prestaties", "Hoge prestaties"};
      choice(
          a,
          performance,
          "Prestatiemodus",
          names,
          Integer.parseInt(settings.optString("performance_mode", "0")),
          i -> {
            JSONObject patch = new JSONObject();
            put(patch, "performance_mode", "" + i);
            if (i == 2 && settings.has("fan_mode")) put(patch, "fan_mode", "4");
            apply(a, patch);
          });
    }
    if (settings.has("fan_mode")) {
      String[] names = {"Stil", "Smart", "Sport"};
      String[] values = {"1", "4", "5"};
      int current =
          settings.optString("fan_mode").equals("1")
              ? 0
              : settings.optString("fan_mode").equals("5") ? 2 : 1;
      choice(
          a,
          performance,
          "Ventilator",
          names,
          current,
          i -> {
            JSONObject patch = new JSONObject();
            put(patch, "fan_mode", values[i]);
            apply(a, patch);
          });
    }
    JSONArray clusters = hardware.optJSONArray("clusters");
    if (clusters != null)
      for (int n = 0; n < clusters.length(); n++) {
        JSONObject cluster = clusters.optJSONObject(n);
        JSONArray f = cluster.optJSONArray("frequencies");
        if (f == null || f.length() == 0) continue;
        String[] names = new String[f.length()];
        int chosen = 0;
        for (int i = 0; i < f.length(); i++) {
          names[i] = (f.optInt(i) / 1000) + " MHz";
          if (f.optInt(i) == cluster.optInt("max")) chosen = i;
        }
        int policy = cluster.optInt("policy");
        choice(
            a,
            performance,
            "CPU-cluster " + policy + " · maximum",
            names,
            chosen,
            i -> {
              JSONObject patch = new JSONObject();
              put(patch, "cpu:" + policy, "" + f.optInt(i));
              apply(a, patch);
            });
      }
    if (clusters == null || clusters.length() == 0)
      performance.addView(
          Ui.text(
              a,
              "Deze firmware publiceert geen instelbare CPU-frequentielijst. De standaard"
                  + " prestatiemodi blijven beschikbaar als de firmware die aanbiedt.",
              13,
              Ui.MUTED));
    if (settings.has("joystick_led_light_picker_color")
        || settings.has("joystick_light_enabled")
        || settings.has("led_light_brightness_percent")) {
      LinearLayout rgb =
          a.card(
              "Joystickverlichting",
              "Kies een vaste lichteffectstand in de AYN-instellingen als een animatie jouw gekozen"
                  + " kleur overschrijft. Teruglezen bevestigt de instelling; de fysieke"
                  + " verlichting is alleen op de Thor te controleren.");
      if (settings.has("joystick_light_enabled")) {
        Switch enabled = new Switch(a);
        enabled.setText(Language.text(a, "Joystickverlichting aan"));
        enabled.setTextColor(Ui.TEXT);
        enabled.setMinHeight(Ui.dp(a, 48));
        enabled.setChecked(settings.optString("joystick_light_enabled").equals("1,1"));
        rgb.addView(enabled);
        rgb.addView(
            Ui.button(
                a,
                "Lichtstand toepassen",
                () -> {
                  JSONObject patch = new JSONObject();
                  put(patch, "joystick_light_enabled", enabled.isChecked() ? "1,1" : "0,0");
                  apply(a, patch);
                }));
      }
      if (settings.has("joystick_led_light_picker_color")) {
        String[] colors = settings.optString("joystick_led_light_picker_color").split(",");
        EditText left = Ui.input(a, "Linker kleur #RRGGBB"),
            right = Ui.input(a, "Rechter kleur #RRGGBB");
        left.setText(
            colors.length > 0 && colors[0].length() == 9
                ? "#" + colors[0].substring(3)
                : "#62E5C0");
        right.setText(
            colors.length > 1 && colors[1].length() == 9
                ? "#" + colors[1].substring(3)
                : "#62E5C0");
        rgb.addView(left);
        rgb.addView(right);
        rgb.addView(
            Ui.button(
                a,
                "Kleuren toepassen",
                () -> {
                  String l = left.getText().toString().trim(),
                      r = right.getText().toString().trim();
                  if (!l.matches("#[A-Fa-f0-9]{6}") || !r.matches("#[A-Fa-f0-9]{6}")) {
                    Ui.toast(a, "Gebruik een kleur zoals #62E5C0");
                    return;
                  }
                  JSONObject patch = new JSONObject();
                  put(
                      patch,
                      "joystick_led_light_picker_color",
                      "#FF"
                          + l.substring(1).toUpperCase(java.util.Locale.ROOT)
                          + ",#FF"
                          + r.substring(1).toUpperCase(java.util.Locale.ROOT));
                  apply(a, patch);
                }));
      }
      if (settings.has("led_light_brightness_percent")) {
        int[] value = {
          Math.round(Float.parseFloat(settings.optString("led_light_brightness_percent")) * 100)
        };
        Ui.seek(a, rgb, "Lichtsterkte (%)", 100, value[0], v -> value[0] = v);
        rgb.addView(
            Ui.button(
                a,
                "Lichtsterkte toepassen",
                () -> {
                  JSONObject patch = new JSONObject();
                  put(patch, "led_light_brightness_percent", Float.toString(value[0] / 100f));
                  apply(a, patch);
                }));
      }
    }
  }

  static void apply(MainActivity a, JSONObject patch) {
    if (RgbService.instance != null
        && (patch.has("joystick_light_enabled")
            || patch.has("joystick_led_light_picker_color")
            || patch.has("led_light_brightness_percent"))) {
      Ui.toast(a, "Stop eerst RGB Studio voordat je de AYN-lichtinstellingen wijzigt.");
      return;
    }
    if (hardware != null
        && hardware.optJSONObject("settings") != null
        && hardware.optJSONObject("settings").has("is_quick_set_performance_and_fan_enable")
        && (patch.has("performance_mode") || patch.has("fan_mode")))
      put(patch, "is_quick_set_performance_and_fan_enable", "1");
    Controls.apply(
        a,
        patch,
        r -> {
          result(a, r);
          Controls.status(
              a,
              next -> {
                hardware = next.has("error") ? null : next;
                a.render();
              });
        });
  }
}
