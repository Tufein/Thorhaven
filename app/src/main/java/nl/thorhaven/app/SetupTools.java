package nl.thorhaven.app;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Read-only capability dashboard until the user chooses an individual action or screen role. */
final class SetupTools {
  static final int NOTIFICATIONS = 121;

  static String text(Context c, String nl, String en) {
    return Language.isEnglish(c) ? en : nl;
  }

  static boolean live(Activity a) {
    return a != null && !a.isFinishing() && !a.isDestroyed();
  }

  static boolean displayExists(Context c, int id) {
    for (Display d : Store.displays(c)) if (d.getDisplayId() == id) return true;
    return false;
  }

  static boolean assign(Context c, boolean bottom, int id) {
    if (bottom && id == -1) return Store.prefs(c).edit().putInt("bottom", -1).commit();
    if (id < 0 || !displayExists(c, id) || id == Store.screen(c, !bottom)) return false;
    return Store.prefs(c).edit().putInt(bottom ? "bottom" : "top", id).commit();
  }

  static boolean swapRoles(Context c) {
    int top = Store.screen(c, false), bottom = Store.screen(c, true);
    if (top == bottom || !displayExists(c, top) || !displayExists(c, bottom)) return false;
    return Store.prefs(c).edit().putInt("top", bottom).putInt("bottom", top).commit();
  }

  static boolean lockAvailable() {
    ThorService service = ThorService.instance;
    if (service == null) return false;
    try {
      for (AccessibilityNodeInfo.AccessibilityAction action : service.getSystemActions())
        if (action.getId() == AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) return true;
    } catch (RuntimeException unavailable) {
    }
    return false;
  }

  /** Locks the whole Android device; never claims independent physical panel power control. */
  static boolean lockNow(Context c) {
    ThorService service = ThorService.instance;
    if (service == null || !lockAvailable()) return false;
    try {
      return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
    } catch (RuntimeException unavailable) {
      return false;
    }
  }

  static JSONObject capabilities(Context c) throws JSONException {
    JSONArray displays = new JSONArray();
    for (Display d : Store.displays(c)) {
      JSONObject q =
          new JSONObject()
              .put("id", d.getDisplayId())
              .put("name", d.getName())
              .put("primary", d.getDisplayId() == Display.DEFAULT_DISPLAY);
      try {
        android.util.DisplayMetrics m = Store.displayMetrics(c, d);
        q.put("width", m.widthPixels).put("height", m.heightPixels).put("densityDpi", m.densityDpi);
      } catch (RuntimeException unavailable) {
        q.put("measurementAvailable", false);
      }
      displays.put(q);
    }
    return new JSONObject()
        .put("schema", 1)
        .put("displays", displays)
        .put("top", Store.screen(c, false))
        .put("bottom", Store.screen(c, true))
        .put(
            "secondaryActivities",
            c.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS))
        .put("accessibility", ThorService.instance != null)
        .put("writeSettings", Settings.System.canWrite(c))
        .put(
            "notifications",
            c.getSystemService(NotificationManager.class).areNotificationsEnabled()
                && (Build.VERSION.SDK_INT < 33
                    || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED))
        .put("pinnedShortcuts", LaunchShortcuts.supported(c))
        .put("liveMove", Bridge.remote != null && Bridge.permitted())
        .put("lockDevice", lockAvailable())
        .put("hardwareRecovery", Store.prefs(c).contains("hw:snapshot"))
        .put("rgbRecovery", RgbSession.recovery(c).contains("baseline"));
  }

  static void page(MainActivity a, LinearLayout parent) {
    LinearLayout card =
        Ui.card(
            a,
            parent,
            text(a, "Aan de slag met jouw schermen", "Set up your screens"),
            text(
                a,
                "Controleer de schermen en kies alleen de extra toegang die je nodig hebt. Gewone"
                    + " appstarts, paren, notities en offline gidsen hebben geen root nodig.",
                "Check your screens and choose only the extra access you need. Ordinary app"
                    + " launches, pairs, notes and offline guides do not require root."));
    card.addView(
        Ui.button(
            a,
            text(a, "Schermen en toegang controleren", "Check screens and access"),
            () -> open(a)));
  }

  static AlertDialog open(MainActivity a) {
    if (!live(a)) return null;
    LinearLayout content = Ui.col(a);
    content.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 8));
    ScrollView scroll = new ScrollView(a);
    scroll.addView(content);
    AlertDialog dialog =
        new AlertDialog.Builder(a)
            .setTitle(text(a, "Jouw Thor instellen", "Set up your Thor"))
            .setView(scroll)
            .setPositiveButton(text(a, "Sluiten", "Close"), null)
            .create();
    Runnable[] refresh = {null};
    refresh[0] =
        () -> {
          if (!live(a)) return;
          content.removeAllViews();
          dashboard(a, content);
          content.addView(
              Ui.button(
                  a, text(a, "Status opnieuw controleren", "Check status again"), refresh[0]));
        };
    refresh[0].run();
    dialog.show();
    return dialog;
  }

  static void dashboard(MainActivity a, LinearLayout content) {
    content.addView(
        Ui.text(a, text(a, "1 · Herken de schermen", "1 · Identify the screens"), 18, Ui.TEXT));
    content.addView(
        Ui.text(
            a,
            text(
                a,
                "Android-hoofdscherm betekent scherm 0, niet automatisch jouw bovenste scherm."
                    + " Screen Lab deelt dat hoofdscherm. Test de schermen en wijs daarna Boven en"
                    + " Onder toe.",
                "Android's primary display means display 0, not automatically your upper screen."
                    + " Screen Lab shares that primary display. Test the displays, then assign Top"
                    + " and Bottom."),
            13,
            Ui.MUTED));
    for (Display d : Store.displays(a)) {
      final int id = d.getDisplayId();
      String size;
      try {
        android.util.DisplayMetrics m = Store.displayMetrics(a, d);
        size = m.widthPixels + " × " + m.heightPixels;
      } catch (RuntimeException unavailable) {
        size = text(a, "afmetingen onbekend", "dimensions unavailable");
      }
      String label =
          text(a, "Scherm ", "Display ")
              + id
              + " · "
              + size
              + (id == Display.DEFAULT_DISPLAY
                  ? text(a, " · Android-hoofdscherm", " · Android primary")
                  : text(a, " · extra scherm", " · additional display"));
      content.addView(Ui.rawText(a, label + "\n" + d.getName(), 13, Ui.TEXT));
      content.addView(
          Ui.button(
              a,
              text(a, "Test dit scherm · 15 seconden", "Test this display · 15 seconds"),
              () -> DisplayPracticeActivity.open(a, id)));
      LinearLayout row = Ui.row(a);
      row.addView(
          Ui.button(a, text(a, "Als Boven kiezen", "Assign Top"), () -> assignUi(a, false, id)),
          new LinearLayout.LayoutParams(0, -2, 1));
      row.addView(
          Ui.button(a, text(a, "Als Onder kiezen", "Assign Bottom"), () -> assignUi(a, true, id)),
          new LinearLayout.LayoutParams(0, -2, 1));
      content.addView(row);
    }
    content.addView(
        Ui.text(
            a,
            text(a, "Huidige keuze: Boven ", "Current assignment: Top ")
                + Store.screen(a, false)
                + text(a, " · Onder ", " · Bottom ")
                + Store.screen(a, true),
            13,
            Ui.ACCENT));
    content.addView(
        Ui.button(
            a,
            text(a, "Boven en Onder omwisselen", "Swap Top and Bottom"),
            () -> {
              if (swapRoles(a))
                Ui.toast(
                    a,
                    text(
                        a,
                        "Schermrollen omgewisseld. Controleer de status opnieuw.",
                        "Screen roles swapped. Check the status again."));
              else
                Ui.toast(
                    a,
                    text(
                        a,
                        "Wijs eerst twee verschillende beschikbare schermen toe.",
                        "Assign two different available displays first."));
            }));
    content.addView(
        Ui.button(
            a,
            text(a, "Zonder onderste scherm gebruiken", "Use without a bottom screen"),
            () -> assignUi(a, true, -1)));
    content.addView(
        Ui.text(
            a, text(a, "2 · Kies optionele toegang", "2 · Choose optional access"), 18, Ui.TEXT));
    content.addView(
        Ui.text(
            a,
            text(
                a,
                "Met de controller-service krijg je combinaties, overlays en app-herkenning. Alleen"
                    + " helderheidsprofielen hebben schrijftoegang tot instellingen nodig. Live"
                    + " verplaatsen gebruikt Shizuku. RGB en root-invoer controleren daarnaast"
                    + " echte Thor-firmware; toegang is geen bewijs dat alle hardware werkt.",
                "The controller service adds chords, overlays and app detection. Only brightness"
                    + " profiles need settings write access. Live moving uses Shizuku. RGB and"
                    + " privileged input also check actual Thor firmware; access does not prove all"
                    + " hardware works."),
            13,
            Ui.MUTED));
    try {
      JSONObject q = capabilities(a);
      String[] keys = {
        "secondaryActivities",
        "accessibility",
        "writeSettings",
        "notifications",
        "pinnedShortcuts",
        "liveMove",
        "lockDevice"
      };
      String[] nl = {
        "Apps op extra schermen",
        "Controller-service",
        "Helderheidstoegang",
        "Meldingen",
        "Launcher-snelkoppelingen",
        "Live verplaatsen",
        "Apparaat vergrendelen"
      };
      String[] en = {
        "Apps on additional displays",
        "Controller service",
        "Brightness access",
        "Notifications",
        "Launcher shortcuts",
        "Live moving",
        "Device locking"
      };
      for (int i = 0; i < keys.length; i++)
        content.addView(
            Ui.text(
                a,
                text(a, nl[i], en[i])
                    + " · "
                    + text(
                        a,
                        q.getBoolean(keys[i]) ? "beschikbaar" : "nog niet beschikbaar",
                        q.getBoolean(keys[i]) ? "available" : "not currently available"),
                13,
                Ui.MUTED));
      if (q.getBoolean("hardwareRecovery") || q.getBoolean("rgbRecovery"))
        content.addView(
            Ui.text(
                a,
                text(
                    a,
                    "Er staan herstelgegevens klaar. Herstel ze op Systeem of RGB Studio voordat je"
                        + " nieuwe hardwaretests start.",
                    "Recovery records are pending. Restore them in System or RGB Studio before"
                        + " starting new hardware tests."),
                13,
                Ui.ACCENT));
    } catch (Exception ignored) {
    }
    content.addView(
        Ui.button(
            a,
            text(a, "Controller-service instellen", "Set up controller service"),
            () -> settings(a, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
    content.addView(
        Ui.button(
            a,
            text(a, "Helderheidstoegang kiezen", "Choose brightness access"),
            () ->
                settings(
                    a,
                    new Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        android.net.Uri.parse("package:" + a.getPackageName())))));
    content.addView(
        Ui.button(
            a,
            text(a, "Meldingen toestaan", "Allow notifications"),
            () -> {
              if (!live(a)) return;
              if (Build.VERSION.SDK_INT >= 33
                  && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                      != PackageManager.PERMISSION_GRANTED)
                a.requestPermissions(
                    new String[] {Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATIONS);
              else
                Ui.toast(
                    a,
                    text(
                        a,
                        "Meldingen zijn al beschikbaar.",
                        "Notifications are already available."));
            }));
    content.addView(
        Ui.button(
            a,
            text(
                a,
                "Shizuku koppelen · alleen voor extra functies",
                "Connect Shizuku · optional extra features"),
            Bridge::request));
    content.addView(
        Ui.text(a, text(a, "3 · Handige veilige acties", "3 · Useful safe actions"), 18, Ui.TEXT));
    content.addView(
        Ui.text(
            a,
            text(
                a,
                "De zwarte schermlaag zet geen paneel fysiek uit. Voor AYN-controllerfocus: open"
                    + " het AYN-menu en kies de focusoptie; de naam verschilt per firmware."
                    + " Vergrendelen hieronder is de gewone Android-vergrendeling voor het hele"
                    + " apparaat.",
                "The black screen layer does not physically switch off a panel. For AYN controller"
                    + " focus, open the AYN menu and choose its focus option; the name varies by"
                    + " firmware. Lock below is Android's normal lock for the whole device."),
            13,
            Ui.MUTED));
    content.addView(
        Ui.button(
            a,
            text(a, "Apparaat nu vergrendelen", "Lock device now"),
            () -> {
              if (!lockNow(a))
                Ui.toast(
                    a,
                    text(
                        a,
                        "Android-vergrendeling is niet beschikbaar. Controleer de"
                            + " controller-service.",
                        "Android locking is unavailable. Check the controller service."));
            }));
    content.addView(
        Ui.text(
            a,
            text(
                a,
                "Stop schakelt Select-combinaties en automatische volume-/helderheidsprofielen uit."
                    + " Je kunt ze later zelf weer inschakelen. Het stopt de huidige hulpmiddelen;"
                    + " eerdere gewone volume-/helderheidswijzigingen blijven staan.",
                "Stop disables Select chords and automatic volume/brightness profiles. You can"
                    + " enable them again yourself. It stops current tools; earlier ordinary"
                    + " volume/brightness changes remain applied."),
            12,
            Ui.MUTED));
    content.addView(
        Ui.button(
            a,
            text(a, "Thorhaven-acties stoppen", "Stop Thorhaven actions"),
            () -> emergencyStop(a)));
    LaunchShortcuts.page(a, content);
  }

  static void assignUi(Activity a, boolean bottom, int id) {
    if (!live(a)) return;
    Ui.toast(
        a,
        assign(a, bottom, id)
            ? text(
                a,
                "Schermkeuze opgeslagen. Controleer de status opnieuw.",
                "Screen assignment saved. Check the status again.")
            : text(
                a,
                "Kies een beschikbaar scherm dat verschilt van de andere rol. Gebruik Omwisselen om"
                    + " rollen te wisselen.",
                "Choose an available display different from the other role. Use Swap to exchange"
                    + " roles."));
  }

  static void settings(Activity a, Intent intent) {
    if (!live(a)) return;
    try {
      a.startActivity(intent);
    } catch (RuntimeException unavailable) {
      Ui.toast(
          a,
          text(
              a,
              "Deze Android-instellingen zijn niet beschikbaar.",
              "These Android settings are unavailable."));
    }
  }

  static void emergencyStop(Context c) {
    Store.prefs(c).edit().putBoolean("combos", false).putBoolean("autoProfiles", false).commit();
    Store.cancelAllPendingPairs();
    TouchControls.hide();
    ControlLab.stop();
    ThorService service = ThorService.instance;
    if (service != null) {
      service.hidePanel();
      if (service.cover != null) service.cover.hide();
    }
    HardwareAutomation.enabled = false;
    HardwareAutomation.focus(c, "");
    Controls.stop(c);
    if (RgbService.instance != null || RgbService.requested) RgbService.stop(c);
    ScreenLabService.stopActive(c);
    Ui.toast(
        c,
        text(
            c,
            "Thorhaven-acties stoppen. Lopende opdrachten ronden eerst af; eventuele"
                + " herstelmeldingen blijven zichtbaar.",
            "Stopping Thorhaven actions. In-flight commands finish first; any recovery messages"
                + " remain available."));
  }

  private SetupTools() {}
}
