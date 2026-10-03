package nl.thorhaven.app;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Bounded local tools, with all portable settings covered by complete backups. */
final class ExtraFeatures {
  static final String[] CARDS = {
    "Laatst actief", "Snel naar", "Recente apps", "Favorieten", "App-paren", "Aanraakknoppen"
  };

  static boolean key(String k) {
    return k.equals("controlLab")
        || k.equals("sessions")
        || k.equals("touchTiles")
        || k.equals("panelOrder")
        || k.startsWith("checklist:")
        || k.startsWith("gameProfiles:")
        || k.startsWith("guideActive:")
        || k.startsWith("hardwareProfile:");
  }

  static void validate(String k, Object v) throws Exception {
    if (!(v instanceof String) || ((String) v).length() > (k.equals("sessions") ? 750000 : 100000))
      throw new Exception("Invalid tool data");
    if (k.equals("controlLab")) {
      ControlLab.validate((String) v);
      return;
    }
    if (k.equals("sessions")) {
      SessionStats.validate((String) v);
      return;
    }
    if (k.equals("touchTiles")) {
      TouchControls.validate((String) v);
      return;
    }
    if (!k.equals("panelOrder")) OfflineGuides.valid(k.substring(k.indexOf(':') + 1));
    String s = (String) v;
    if (k.startsWith("guideActive:")) {
      OfflineGuides.valid(s);
      return;
    }
    if (k.equals("panelOrder")) {
      JSONArray a = new JSONArray(s);
      Set<String> seen = new HashSet<>();
      if (a.length() != CARDS.length) throw new Exception("Incomplete panel order");
      for (int i = 0; i < a.length(); i++)
        if (!Arrays.asList(CARDS).contains(a.getString(i)) || !seen.add(a.getString(i)))
          throw new Exception("Invalid panel order");
    } else if (k.startsWith("checklist:")) {
      JSONArray a = new JSONArray(s);
      if (a.length() > 200) throw new Exception("Maximum 200 tasks");
      for (int i = 0; i < a.length(); i++) {
        JSONObject x = a.getJSONObject(i);
        if (x.getString("text").length() > 300) throw new Exception("Task too long");
        x.getBoolean("done");
      }
    } else if (k.startsWith("gameProfiles:")) {
      JSONArray a = new JSONArray(s);
      if (a.length() > 30) throw new Exception("Maximum 30 game profiles per app");
      for (int i = 0; i < a.length(); i++) {
        JSONObject x = a.getJSONObject(i);
        if (x.getString("name").isEmpty() || x.getString("name").length() > 100)
          throw new Exception("Invalid profile name");
        JSONObject data = x.getJSONObject("data");
        for (Iterator<String> it = data.keys(); it.hasNext(); ) {
          String p = it.next();
          if (!Arrays.asList("volume", "brightness", "screen", "mapping").contains(p))
            throw new Exception("Invalid game profile");
          if (p.equals("mapping")) PadProfile.parse(data.getString(p));
          else if (p.equals("screen")) {
            if (data.getInt(p) < -1) throw new Exception("Invalid display");
          } else if (data.getInt(p) < -1 || data.getInt(p) > 100)
            throw new Exception("Invalid percentage");
        }
      }
    } else if (k.startsWith("hardwareProfile:")) {
      JSONObject x = new JSONObject(s);
      if (x.length() != 2) throw new Exception("Invalid hardware profile");
      DeviceControl.validate("performance_mode", x.getString("performance_mode"));
      DeviceControl.validate("fan_mode", x.getString("fan_mode"));
      if (x.getString("fan_mode").equals("0")
          || (x.getString("performance_mode").equals("2") && x.getString("fan_mode").equals("1")))
        throw new Exception("High performance requires Smart or Sport; fan off is unsupported");
    }
  }

  static void validateGuide(JSONObject m) throws Exception {
    GuideTools.validateMeta(m);
    if (m.has("owner")) OfflineGuides.valid(m.getString("owner"));
    JSONArray b = m.optJSONArray("bookmarks");
    if (b != null) {
      if (b.length() > 100) throw new Exception("Maximum 100 bookmarks");
      for (int i = 0; i < b.length(); i++) {
        JSONObject x = b.getJSONObject(i);
        if (x.getString("name").length() > 100
            || x.getInt("page") < 0
            || x.getInt("page") >= Math.max(1, m.optInt("pages", 1)))
          throw new Exception("Invalid bookmark");
      }
    }
    JSONArray markers = m.optJSONArray("markers");
    if (markers != null) {
      if (markers.length() > 100) throw new Exception("Maximum 100 map markers");
      for (int i = 0; i < markers.length(); i++) {
        JSONObject x = markers.getJSONObject(i);
        double px = x.getDouble("x"), py = x.getDouble("y");
        if (!Double.isFinite(px)
            || !Double.isFinite(py)
            || px < 0
            || px > 1
            || py < 0
            || py > 1
            || x.getString("name").length() > 100) throw new Exception("Invalid marker");
      }
    }
  }

  static JSONArray array(Context c, String k) {
    try {
      return new JSONArray(Store.prefs(c).getString(k, "[]"));
    } catch (Exception e) {
      return new JSONArray();
    }
  }

  static void save(Context c, String k, String s) {
    try {
      validate(k, s);
      if (!Store.prefs(c).edit().putString(k, s).commit()) throw new Exception("Could not save");
    } catch (Exception e) {
      Ui.toast(c, e.getMessage());
    }
  }

  static void checklist(MainActivity a, LinearLayout parent, String pkg) {
    LinearLayout box =
        Ui.card(
            a,
            parent,
            "Gamechecklist",
            "Taken en verzamelobjecten worden lokaal voor deze app opgeslagen.");
    JSONArray rows = array(a, "checklist:" + pkg);
    for (int i = 0; i < rows.length(); i++) {
      final int index = i;
      JSONObject x = rows.optJSONObject(i);
      if (x == null) continue;
      LinearLayout row = Ui.row(a);
      CheckBox check = new CheckBox(a);
      check.setText(x.optString("text"));
      check.setTextColor(Ui.TEXT);
      check.setChecked(x.optBoolean("done"));
      check.setOnCheckedChangeListener(
          (v, on) -> {
            try {
              x.put("done", on);
              save(a, "checklist:" + pkg, rows.toString());
            } catch (Exception ignored) {
            }
          });
      row.addView(check, new LinearLayout.LayoutParams(0, -2, 1));
      row.addView(
          Ui.button(
              a,
              "×",
              () -> {
                rows.remove(index);
                save(a, "checklist:" + pkg, rows.toString());
                a.render();
              }));
      box.addView(row);
    }
    box.addView(
        Ui.button(
            a,
            "Taak toevoegen",
            () -> {
              EditText input = Ui.input(a, "Taak of verzamelobject");
              new Ui.Dialog(a)
                  .setTitle("Taak toevoegen")
                  .setView(input)
                  .setPositiveButton(
                      "Opslaan",
                      (d, w) -> {
                        String t = input.getText().toString().trim();
                        if (t.isEmpty()) return;
                        try {
                          rows.put(new JSONObject().put("text", t).put("done", false));
                          save(a, "checklist:" + pkg, rows.toString());
                          a.render();
                        } catch (Exception e) {
                          Ui.toast(a, e.getMessage());
                        }
                      })
                  .setNegativeButton("Annuleren", null)
                  .show();
            }));
  }

  static void profile(MainActivity a, LinearLayout parent, String pkg) {
    LinearLayout box =
        Ui.card(
            a,
            parent,
            "Gameprofielen met naam",
            "Bewaar het volume, de helderheid, het scherm en de controllerinstellingen van deze"
                + " app. Kies handmatig een profiel wanneer je in een emulator van game wisselt.");
    JSONArray rows = array(a, "gameProfiles:" + pkg);
    for (int i = 0; i < rows.length(); i++) {
      final int index = i;
      JSONObject x = rows.optJSONObject(i);
      if (x == null) continue;
      LinearLayout row = Ui.row(a);
      row.addView(
          Ui.button(a, x.optString("name"), () -> applyProfile(a, pkg, x)),
          new LinearLayout.LayoutParams(0, -2, 1));
      row.addView(
          Ui.button(
              a,
              "Verwijderen",
              () ->
                  new Ui.Dialog(a)
                      .setTitle("Gameprofiel verwijderen?")
                      .setPositiveButton(
                          "Verwijderen",
                          (d, w) -> {
                            rows.remove(index);
                            save(a, "gameProfiles:" + pkg, rows.toString());
                            a.render();
                          })
                      .setNegativeButton("Annuleren", null)
                      .show()));
      box.addView(row);
    }
    box.addView(
        Ui.button(
            a,
            "Huidige instellingen als gameprofiel bewaren",
            () -> {
              EditText input = Ui.input(a, "Naam van de game");
              new Ui.Dialog(a)
                  .setTitle("Huidige appinstellingen bewaren")
                  .setView(input)
                  .setMessage(
                      "Sla eerst de appinstellingen op voordat je een gameprofiel met naam maakt.")
                  .setPositiveButton(
                      "Opslaan",
                      (d, w) -> {
                        try {
                          JSONObject data = new JSONObject();
                          for (String k : new String[] {"volume", "brightness", "screen"})
                            data.put(k, Store.prefs(a).getInt(k + ":" + pkg, -1));
                          if (Store.prefs(a).contains("mapping:" + pkg))
                            data.put("mapping", Store.prefs(a).getString("mapping:" + pkg, ""));
                          rows.put(
                              new JSONObject()
                                  .put("name", input.getText().toString().trim())
                                  .put("data", data));
                          save(a, "gameProfiles:" + pkg, rows.toString());
                          a.render();
                        } catch (Exception e) {
                          Ui.toast(a, e.getMessage());
                        }
                      })
                  .setNegativeButton("Annuleren", null)
                  .show();
            }));
    box.addView(
        Ui.button(
            a,
            "Automatisch ventilator- / prestatieprofiel",
            () -> {
              LinearLayout l = Ui.col(a);
              Spinner performance =
                  spinner(a, l, new String[] {"Standaard", "Prestaties", "Hoge prestaties"});
              Spinner fan = spinner(a, l, new String[] {"Stil", "Smart", "Sport"});
              JSONObject old;
              try {
                old = new JSONObject(Store.prefs(a).getString("hardwareProfile:" + pkg, "{}"));
              } catch (Exception e) {
                old = new JSONObject();
              }
              performance.setSelection(Math.max(0, Math.min(2, old.optInt("performance_mode", 0))));
              int[] modes = {1, 4, 5};
              for (int i = 0; i < 3; i++)
                if (modes[i] == old.optInt("fan_mode", 4)) fan.setSelection(i);
              new Ui.Dialog(a)
                  .setTitle("Automatisch hardwareprofiel")
                  .setMessage(
                      "Vereist de toegankelijkheidsservice en Thor-firmware met root-Shizuku of AYN"
                          + " PServer. Wordt toegepast wanneer deze app actief is en hersteld bij"
                          + " wisselen naar een andere app. Niet-ondersteunde firmware geeft een"
                          + " fout. Na herstart moet je dit opnieuw inschakelen.")
                  .setView(l)
                  .setPositiveButton(
                      "Opslaan",
                      (d, w) -> {
                        try {
                          save(
                              a,
                              "hardwareProfile:" + pkg,
                              new JSONObject()
                                  .put(
                                      "performance_mode",
                                      "" + performance.getSelectedItemPosition())
                                  .put("fan_mode", "" + modes[fan.getSelectedItemPosition()])
                                  .toString());
                        } catch (Exception ignored) {
                        }
                      })
                  .setNeutralButton(
                      "Verwijderen",
                      (d, w) -> Store.prefs(a).edit().remove("hardwareProfile:" + pkg).commit())
                  .setNegativeButton("Annuleren", null)
                  .show();
            }));
  }

  static Spinner spinner(Context a, LinearLayout l, String[] labels) {
    Spinner s = new Spinner(a);
    s.setAdapter(
        new ArrayAdapter<>(
            a, android.R.layout.simple_spinner_dropdown_item, Language.labels(a, labels)));
    l.addView(s);
    return s;
  }

  static void applyProfile(Context c, String pkg, JSONObject x) {
    try {
      JSONObject data = x.getJSONObject("data");
      SharedPreferences.Editor e = Store.prefs(c).edit();
      for (String k : new String[] {"volume", "brightness", "screen"})
        if (data.has(k)) e.putInt(k + ":" + pkg, data.getInt(k));
      if (data.has("mapping")) e.putString("mapping:" + pkg, data.getString("mapping"));
      else e.remove("mapping:" + pkg);
      e.commit();
      Store.apply(c, pkg);
      Controls.foreground(c, pkg);
      if (c instanceof MainActivity && ((MainActivity) c).profileDialog != null)
        ((MainActivity) c).profileDialog.dismiss();
      Ui.toast(c, "Profile selected: " + x.getString("name"));
    } catch (Exception e) {
      Ui.toast(c, e.getMessage());
    }
  }

  static boolean expanded;

  static void settings(MainActivity a) {
    LinearLayout summary =
        Ui.card(
            a,
            a.content,
            "Extra hulpmiddelen",
            "Paneelvolgorde, aanraakknoppen, automatische back-ups en hardwareprofielen.");
    summary.addView(
        Ui.button(
            a,
            expanded ? "Opties sluiten" : "Extra opties openen",
            () -> {
              expanded = !expanded;
              a.render();
            }));
    if (!expanded) return;
    LinearLayout order =
        Ui.card(
            a,
            a.content,
            "Volgorde van paneelkaarten",
            "Verplaats een kaart naar voren in het snelpaneel. Belangrijke systeemregelaars blijven"
                + " bovenaan.");
    JSONArray sorted = order(a);
    for (int i = 0; i < sorted.length(); i++) {
      final int index = i;
      String label = sorted.optString(i);
      order.addView(
          Ui.button(
              a,
              "↑ " + label,
              () -> {
                if (index == 0) return;
                JSONArray n = new JSONArray();
                n.put(sorted.optString(index));
                for (int j = 0; j < sorted.length(); j++)
                  if (j != index) n.put(sorted.optString(j));
                save(a, "panelOrder", n.toString());
                a.render();
              }));
    }
    LinearLayout touch =
        Ui.card(
            a,
            a.content,
            "Aanraakknoppen",
            "Zes instelbare tikknoppen op het onderste scherm. Vereist systeemtoegang. Geen"
                + " vasthouden, analoge sticks of macro's.");
    TouchControls.configure(a, touch);
    touch.addView(Ui.button(a, "Aanraakknoppen openen", () -> TouchControls.show(a)));
    ControlLab.settings(a, a.content);
    AutoBackup.page(a);
    LinearLayout hw =
        Ui.card(
            a,
            a.content,
            "Automatische hardwareprofielen",
            "Stel een profiel in via Apps → Profiel of Gidsen. Roottoegang en geschikte"
                + " Thor-firmware zijn nodig.");
    Switch enable = new Switch(a);
    enable.setText(Language.text(a, "Voor deze sessie inschakelen"));
    enable.setTextColor(Ui.TEXT);
    enable.setChecked(HardwareAutomation.enabled);
    enable.setOnCheckedChangeListener(
        (v, on) -> {
          HardwareAutomation.enabled = on;
          if (on && ThorService.instance != null)
            HardwareAutomation.focus(a, ThorService.instance.foreground);
          else HardwareAutomation.focus(a, "");
        });
    hw.addView(enable);
    hw.addView(Ui.text(a, HardwareAutomation.status, 13, Ui.MUTED));
    hw.addView(
        Ui.button(
            a,
            "Oorspronkelijke hardwareinstellingen herstellen",
            () -> {
              HardwareAutomation.enabled = false;
              if (HardwareAutomation.owned || HardwareAutomation.busy) {
                HardwareAutomation.focus(a, "");
                Ui.toast(a, "Restoring automatic hardware settings…");
                a.render();
              } else
                Controls.restore(
                    a,
                    r -> {
                      Ui.toast(a, r.optString("error", r.optString("message", "Restored")));
                      a.render();
                    });
            }));
  }

  static JSONArray order(Context c) {
    try {
      String s =
          Store.prefs(c).getString("panelOrder", new JSONArray(Arrays.asList(CARDS)).toString());
      validate("panelOrder", s);
      return new JSONArray(s);
    } catch (Exception e) {
      return new JSONArray(Arrays.asList(CARDS));
    }
  }

  static void reorder(Context c, LinearLayout parent) {
    Map<String, android.view.View> cards = new HashMap<>();
    for (int i = 0; i < parent.getChildCount(); i++) {
      android.view.View v = parent.getChildAt(i);
      if (v.getTag() instanceof String) cards.put((String) v.getTag(), v);
    }
    JSONArray order = order(c);
    for (int i = 0; i < order.length(); i++) {
      android.view.View v = cards.get(order.optString(i));
      if (v != null) {
        parent.removeView(v);
        parent.addView(v);
      }
    }
  }
}
