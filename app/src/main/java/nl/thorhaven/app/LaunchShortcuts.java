package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Icon;
import android.os.*;
import android.view.Display;
import android.widget.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Optional launcher requests; every execution resolves current settings rather than saved IDs. */
final class LaunchShortcuts {
  static final String EXTRA = "thorhaven.shortcut";
  static final ExecutorService worker = Executors.newSingleThreadExecutor();
  static final Handler main = new Handler(Looper.getMainLooper());
  static final java.util.concurrent.atomic.AtomicBoolean pinBusy =
      new java.util.concurrent.atomic.AtomicBoolean();

  static String text(Context c, String nl, String en) {
    return Language.isEnglish(c) ? en : nl;
  }

  static JSONObject appTarget(String pkg, String role) throws Exception {
    JSONObject q =
        new JSONObject().put("schema", 1).put("kind", "app").put("pkg", pkg).put("role", role);
    validate(q);
    return q;
  }

  static JSONObject pairTarget(String top, String bottom) throws Exception {
    JSONObject q =
        new JSONObject().put("schema", 1).put("kind", "pair").put("top", top).put("bottom", bottom);
    validate(q);
    return q;
  }

  static JSONObject guideTarget(String pkg) throws Exception {
    JSONObject q = new JSONObject().put("schema", 1).put("kind", "guide").put("pkg", pkg);
    validate(q);
    return q;
  }

  static void validate(JSONObject q) throws Exception {
    if (q == null || q.toString().length() > 1024) throw new IOException("Invalid shortcut");
    RgbSettings.integer(q, "schema", 1, 1);
    String kind = RgbSettings.string(q, "kind");
    if (kind.equals("app")) {
      RgbSettings.keys(q, "schema", "kind", "pkg", "role");
      OfflineGuides.valid(RgbSettings.string(q, "pkg"));
      if (!Arrays.asList("top", "bottom").contains(RgbSettings.string(q, "role")))
        throw new IOException("Invalid shortcut screen");
    } else if (kind.equals("pair")) {
      RgbSettings.keys(q, "schema", "kind", "top", "bottom");
      OfflineGuides.valid(RgbSettings.string(q, "top"));
      OfflineGuides.valid(RgbSettings.string(q, "bottom"));
      if (q.getString("top").equals(q.getString("bottom")))
        throw new IOException("A pair needs different apps");
    } else if (kind.equals("guide")) {
      RgbSettings.keys(q, "schema", "kind", "pkg");
      OfflineGuides.valid(RgbSettings.string(q, "pkg"));
    } else throw new IOException("Invalid shortcut kind");
  }

  static String stableId(JSONObject q) throws Exception {
    validate(q);
    String kind = q.getString("kind");
    String identity =
        kind.equals("pair")
            ? "pair\n" + q.getString("top") + "\n" + q.getString("bottom")
            : kind + "\n" + q.getString("pkg") + "\n" + q.optString("role", "");
    byte[] digest =
        MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
    StringBuilder id = new StringBuilder("thorhaven-");
    for (byte b : digest) id.append(String.format(Locale.ROOT, "%02x", b & 255));
    return id.toString();
  }

  static Intent intent(Context c, JSONObject target) throws Exception {
    validate(target);
    return new Intent(c, ShortcutActivity.class)
        .setAction(Intent.ACTION_VIEW)
        .putExtra(EXTRA, target.toString())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  }

  static int display(Context c, String role) throws Exception {
    if (!Arrays.asList("top", "bottom").contains(role))
      throw new IOException("Invalid shortcut screen");
    int id = Store.screen(c, role.equals("bottom"));
    for (Display d : Store.displays(c)) if (d.getDisplayId() == id) return id;
    throw new IOException(
        text(c, "Het gekozen scherm is niet beschikbaar.", "The selected screen is unavailable."));
  }

  static void launchable(Context c, String pkg, int display) throws Exception {
    if (Store.launchIntent(c, pkg, display) == null)
      throw new IOException(
          text(
              c,
              "Deze app of dit scherm is niet beschikbaar voor openen.",
              "This app or display is unavailable for launching."));
  }

  /** Returned labels are user content and must be displayed through raw UI helpers. */
  static JSONObject resolve(Context c, JSONObject target) throws Exception {
    validate(target);
    JSONObject result = new JSONObject(target.toString());
    String kind = target.getString("kind");
    if (kind.equals("app")) {
      int id = display(c, target.getString("role"));
      String pkg = target.getString("pkg");
      launchable(c, pkg, id);
      return result.put("display", id).put("label", Store.name(c, pkg));
    }
    if (kind.equals("guide")) {
      String pkg = target.getString("pkg");
      if (!OfflineGuides.exists(c, pkg))
        throw new IOException(
            text(
                c, "Deze offline gids bestaat niet meer.", "This offline guide no longer exists."));
      int id = Store.screen(c, true) >= 0 ? display(c, "bottom") : display(c, "top");
      return result
          .put("display", id)
          .put("label", OfflineGuides.meta(c, pkg).optString("name", Store.name(c, pkg)));
    }
    int top = display(c, "top"), bottom = display(c, "bottom");
    if (top == bottom)
      throw new IOException(
          text(
              c,
              "Een paar heeft twee verschillende schermen nodig.",
              "A pair needs two different screens."));
    JSONArray pairs = Store.pairs(c);
    for (int i = 0; i < pairs.length(); i++) {
      JSONObject p = pairs.optJSONObject(i);
      if (p != null
          && target.getString("top").equals(p.optString("top"))
          && target.getString("bottom").equals(p.optString("bottom"))) {
        String name = RgbSettings.string(p, "name");
        if (name.trim().isEmpty()
            || name.length() > 180
            || name.chars().anyMatch(Character::isISOControl))
          throw new IOException("Invalid pair name");
        launchable(c, target.getString("top"), top);
        launchable(c, target.getString("bottom"), bottom);
        return result
            .put("topDisplay", top)
            .put("bottomDisplay", bottom)
            .put("pair", new JSONObject(p.toString()))
            .put("label", name);
      }
    }
    throw new IOException(
        text(
            c,
            "Dit opgeslagen app-paar bestaat niet meer.",
            "This saved app pair no longer exists."));
  }

  static void execute(Context c, JSONObject target) throws Exception {
    JSONObject resolved = resolve(c, target);
    String kind = target.getString("kind");
    if (kind.equals("app")) {
      if (!Store.launchChecked(c, target.getString("pkg"), resolved.getInt("display")))
        throw new IOException(
            text(c, "De app kon niet worden geopend.", "The app could not open."));
    } else if (kind.equals("pair"))
      Store.openPair(c.getApplicationContext(), resolved.getJSONObject("pair"));
    else
      c.startActivity(
          new Intent(c, GuideActivity.class)
              .putExtra("pkg", target.getString("pkg"))
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
          ActivityOptions.makeBasic().setLaunchDisplayId(resolved.getInt("display")).toBundle());
  }

  static boolean supported(Context c) {
    try {
      ShortcutManager manager = c.getSystemService(ShortcutManager.class);
      return manager != null && manager.isRequestPinShortcutSupported();
    } catch (RuntimeException unavailable) {
      return false;
    }
  }

  static void pin(Context c, JSONObject target) {
    if (!(c instanceof Activity) || ((Activity) c).isFinishing() || ((Activity) c).isDestroyed())
      return;
    Activity owner = (Activity) c;
    if (!supported(c)) {
      Ui.toast(
          c,
          text(
              c,
              "Deze launcher ondersteunt geen vastgezette snelkoppelingen.",
              "This launcher does not support pinned shortcuts."));
      return;
    }
    final String payload;
    try {
      validate(target);
      payload = target.toString();
    } catch (Exception invalid) {
      Ui.toast(c, invalid.getMessage());
      return;
    }
    if (!pinBusy.compareAndSet(false, true)) {
      Ui.toast(
          c,
          text(
              c,
              "Een snelkoppelingsaanvraag is nog bezig.",
              "A shortcut request is still in progress."));
      return;
    }
    Context app = c.getApplicationContext();
    worker.execute(
        () -> {
          String message;
          try {
            if (owner.isFinishing() || owner.isDestroyed()) return;
            JSONObject q = StrictJson.object(payload, 1024), resolved = resolve(app, q);
            String label = resolved.getString("label");
            if (q.getString("kind").equals("app"))
              label +=
                  " · "
                      + text(
                          app,
                          q.getString("role").equals("top") ? "Boven" : "Onder",
                          q.getString("role").equals("top") ? "Top" : "Bottom");
            ShortcutInfo shortcut =
                new ShortcutInfo.Builder(app, stableId(q))
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(Icon.createWithResource(app, R.drawable.icon))
                    .setIntent(intent(app, q))
                    .build();
            if (owner.isFinishing() || owner.isDestroyed()) return;
            boolean requested =
                app.getSystemService(ShortcutManager.class).requestPinShortcut(shortcut, null);
            message =
                requested
                    ? text(
                        app,
                        "De launcher heeft de aanvraag ontvangen. Bevestig als daarom gevraagd"
                            + " wordt.",
                        "The launcher received the request. Confirm if prompted.")
                    : text(
                        app,
                        "De launcher heeft de aanvraag niet aangenomen.",
                        "The launcher did not accept the request.");
          } catch (Exception e) {
            message =
                text(app, "Snelkoppeling niet toegevoegd: ", "Shortcut was not added: ")
                    + e.getMessage();
          } finally {
            pinBusy.set(false);
          }
          String completed = message;
          main.post(
              () -> {
                if (!owner.isFinishing() && !owner.isDestroyed()) Ui.toast(owner, completed);
              });
        });
  }

  static void page(MainActivity a, LinearLayout parent) {
    LinearLayout card =
        Ui.card(
            a,
            parent,
            text(a, "Android-snelkoppelingen", "Android shortcuts"),
            text(
                a,
                "Voeg zelf een app, opgeslagen paar of offline gids toe aan je launcher."
                    + " Schermkeuzes worden bij openen opnieuw gecontroleerd. Sommige launchers"
                    + " ondersteunen dit niet.",
                "Add an app, saved pair or offline guide to your launcher. Screen choices are"
                    + " checked again when opened. Some launchers do not support this."));
    card.addView(
        Ui.button(
            a,
            text(a, "App-snelkoppeling toevoegen", "Add app shortcut"),
            () -> chooseApp(a, false)));
    card.addView(Ui.button(a, text(a, "App-paar toevoegen", "Add app pair"), () -> choosePair(a)));
    card.addView(
        Ui.button(a, text(a, "Offline gids toevoegen", "Add offline guide"), () -> chooseGuide(a)));
    card.addView(
        Ui.text(
            a,
            supported(a)
                ? text(a, "Launcher ondersteunt aanvragen", "Launcher supports requests")
                : text(
                    a,
                    "Deze launcher ondersteunt geen aanvragen",
                    "This launcher does not support requests"),
            12,
            Ui.MUTED));
  }

  static void chooseApp(MainActivity a, boolean guide) {
    List<Store.App> apps = Store.apps(a);
    if (guide) apps.removeIf(x -> !OfflineGuides.exists(a, x.pkg));
    if (apps.isEmpty()) {
      Ui.toast(
          a,
          text(
              a,
              "Geen geschikte apps of offline gidsen gevonden.",
              "No suitable apps or offline guides found."));
      return;
    }
    String[] names = new String[apps.size()];
    for (int i = 0; i < names.length; i++) names[i] = apps.get(i).name;
    new AlertDialog.Builder(a)
        .setTitle(
            text(
                a,
                guide ? "Offline gids kiezen" : "App kiezen",
                guide ? "Choose offline guide" : "Choose app"))
        .setItems(
            names,
            (dialog, index) -> {
              String pkg = apps.get(index).pkg;
              if (guide) {
                try {
                  pin(a, guideTarget(pkg));
                } catch (Exception e) {
                  Ui.toast(a, e.getMessage());
                }
              } else
                new AlertDialog.Builder(a)
                    .setTitle(text(a, "Scherm kiezen", "Choose screen"))
                    .setItems(
                        new String[] {text(a, "Boven", "Top"), text(a, "Onder", "Bottom")},
                        (d, role) -> {
                          try {
                            pin(a, appTarget(pkg, role == 0 ? "top" : "bottom"));
                          } catch (Exception e) {
                            Ui.toast(a, e.getMessage());
                          }
                        })
                    .setNegativeButton(text(a, "Annuleren", "Cancel"), null)
                    .show();
            })
        .setNegativeButton(text(a, "Annuleren", "Cancel"), null)
        .show();
  }

  static void choosePair(MainActivity a) {
    JSONArray pairs = Store.pairs(a);
    if (pairs.length() == 0) {
      Ui.toast(a, text(a, "Bewaar eerst een app-paar.", "Save an app pair first."));
      return;
    }
    String[] names = new String[pairs.length()];
    for (int i = 0; i < names.length; i++)
      names[i] =
          pairs.optJSONObject(i) == null ? "?" : pairs.optJSONObject(i).optString("name", "?");
    new AlertDialog.Builder(a)
        .setTitle(text(a, "Paar kiezen", "Choose pair"))
        .setItems(
            names,
            (d, index) -> {
              try {
                JSONObject p = pairs.getJSONObject(index);
                pin(a, pairTarget(p.getString("top"), p.getString("bottom")));
              } catch (Exception e) {
                Ui.toast(a, e.getMessage());
              }
            })
        .setNegativeButton(text(a, "Annuleren", "Cancel"), null)
        .show();
  }

  static void chooseGuide(MainActivity a) {
    List<String> packages = new ArrayList<>();
    for (String pkg : OfflineGuides.prefs(a).getAll().keySet())
      if (OfflineGuides.exists(a, pkg)) packages.add(pkg);
    packages.sort(
        (x, y) ->
            OfflineGuides.meta(a, x)
                .optString("name", x)
                .compareToIgnoreCase(OfflineGuides.meta(a, y).optString("name", y)));
    if (packages.isEmpty()) {
      Ui.toast(a, text(a, "Importeer eerst een offline gids.", "Import an offline guide first."));
      return;
    }
    String[] names = new String[packages.size()];
    for (int i = 0; i < names.length; i++)
      names[i] = OfflineGuides.meta(a, packages.get(i)).optString("name", packages.get(i));
    new AlertDialog.Builder(a)
        .setTitle(text(a, "Offline gids kiezen", "Choose offline guide"))
        .setItems(
            names,
            (d, index) -> {
              try {
                pin(a, guideTarget(packages.get(index)));
              } catch (Exception e) {
                Ui.toast(a, e.getMessage());
              }
            })
        .setNegativeButton(text(a, "Annuleren", "Cancel"), null)
        .show();
  }

  private LaunchShortcuts() {}
}
