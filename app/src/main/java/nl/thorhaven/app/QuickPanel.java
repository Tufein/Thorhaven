package nl.thorhaven.app;

import android.content.*;
import android.media.AudioManager;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;

final class QuickPanel {
  static LinearLayout build(Context c, Runnable close) {
    LinearLayout l = Ui.col(c);
    l.setBackgroundColor(Ui.BG);
    l.setPadding(Ui.dp(c, 18), Ui.dp(c, 12), Ui.dp(c, 18), Ui.dp(c, 18));
    LinearLayout header = Ui.row(c);
    header.addView(Ui.title(c, "Snelpaneel", 24), new LinearLayout.LayoutParams(0, -2, 1));
    if (close != null)
      header.addView(Ui.button(c, "Sluiten", close), new LinearLayout.LayoutParams(-2, -2));
    l.addView(header);
    l.addView(Ui.text(c, Store.battery(c), 13, Ui.MUTED));
    l.addView(Ui.button(c, "Controller noodstop", () -> Controls.stop(c)));
    l.addView(
        Ui.button(
            c,
            "Thor-systeemregelaars",
            () -> {
              if (close != null) close.run();
              c.startActivity(
                  new Intent(c, MainActivity.class)
                      .putExtra("page", "system")
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }));
    AudioManager a = c.getSystemService(AudioManager.class);
    int max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    Ui.seek(
        c,
        l,
        "Mediavolume",
        max,
        a.getStreamVolume(AudioManager.STREAM_MUSIC),
        v -> a.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0));
    if (Settings.System.canWrite(c)) {
      int value =
          Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
      Ui.seek(
          c,
          l,
          "Systeemhelderheid (%)",
          100,
          value * 100 / 255,
          v -> {
            Settings.System.putInt(
                c.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(
                c.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS,
                Math.max(8, v * 255 / 100));
          });
    } else
      l.addView(Ui.text(c, "Helderheid: activeer instellingstoegang in Thorhaven.", 12, Ui.MUTED));
    l.addView(
        Ui.button(
            c,
            "Onderste scherm zwart / herstellen",
            () -> {
              if (close != null) close.run();
              if (ThorService.instance != null && ThorService.instance.cover != null)
                ThorService.instance.cover.show();
              else
                Ui.toast(
                    c,
                    "Activeer de toegankelijkheidsservice. Dit is een zwarte OLED-overlay, geen"
                        + " schermuitschakeling.");
            }));
    l.addView(
        Ui.text(
            c, "Zwarte modus: dubbeltik of tik met drie vingers om terug te keren.", 12, Ui.MUTED));
    String pkg =
        ThorService.instance != null
            ? ThorService.instance.foreground
            : Store.prefs(c).getString("last", "");
    if (pkg.isEmpty() || pkg.equals(c.getPackageName())) pkg = Store.prefs(c).getString("last", "");
    final String active = pkg;
    if (!active.isEmpty()) {
      LinearLayout now = Ui.card(c, l, "Laatst actief", Store.name(c, active));
      LinearLayout r = Ui.row(c);
      r.addView(
          Ui.button(
              c,
              "Open boven",
              () -> {
                if (close != null) close.run();
                Store.launch(c, active, Store.screen(c, false));
              }),
          new LinearLayout.LayoutParams(0, -2, 1));
      r.addView(
          Ui.button(
              c,
              "Open onder",
              () -> {
                if (close != null) close.run();
                Store.launch(c, active, Store.screen(c, true));
              }),
          new LinearLayout.LayoutParams(0, -2, 1));
      now.addView(r);
      now.addView(
          Ui.button(
              c,
              "Live naar ander scherm (Shizuku)",
              () -> {
                int from =
                    ThorService.instance != null
                        ? ThorService.instance.foregroundDisplay
                        : Store.prefs(c).getInt("lastScreen", Store.screen(c, false));
                if (close != null) close.run();
                Bridge.move(
                    c,
                    active,
                    from == Store.screen(c, false)
                        ? Store.screen(c, true)
                        : Store.screen(c, false));
              }));
      now.addView(
          Ui.button(
              c,
              "Gids openen",
              () -> {
                if (close != null) close.run();
                Store.guide(c, active);
              }));
      if (OfflineGuides.exists(c, OfflineGuides.active(c, active)))
        now.addView(
            Ui.button(
                c,
                "Offline gids boven mijn game",
                () -> {
                  if (close != null) close.run();
                  OfflineGuides.open(c, active);
                }));
      String notes = Store.prefs(c).getString("notes:" + active, "");
      if (Store.prefs(c).getBoolean("panelNotes", true) && !notes.isEmpty())
        now.addView(Ui.rawText(c, notes, 14, Ui.TEXT));
    }
    l.addView(
        Ui.button(
            c,
            "Beide schermen wisselen (Shizuku)",
            () -> {
              if (close != null) close.run();
              Bridge.swap(c);
            }));
    l.addView(Ui.button(c, "Bewaar dit app-paar (Shizuku)", () -> PanelActions.savePair(c, close)));
    LinearLayout shortcuts =
        Ui.card(c, l, "Snel naar", "Android-instellingen openen op het paneelscherm.");
    String[] labels = {"Wifi", "Bluetooth", "Geluid", "Accu", "Instellingen"};
    String[] actions = {
      Settings.ACTION_WIFI_SETTINGS,
      Settings.ACTION_BLUETOOTH_SETTINGS,
      Settings.ACTION_SOUND_SETTINGS,
      Settings.ACTION_BATTERY_SAVER_SETTINGS,
      Settings.ACTION_SETTINGS
    };
    for (int n = 0; n < labels.length; n++) {
      final String action = actions[n];
      shortcuts.addView(
          Ui.button(
              c,
              labels[n],
              () -> {
                if (close != null) close.run();
                try {
                  Intent i = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                  c.startActivity(
                      i,
                      android.app.ActivityOptions.makeBasic()
                          .setLaunchDisplayId(c.getDisplay().getDisplayId())
                          .toBundle());
                } catch (Exception e) {
                  Ui.toast(c, "Instelling niet beschikbaar.");
                }
              }));
    }
    if (Store.prefs(c).getBoolean("panelRecents", true)) {
      LinearLayout recent =
          Ui.card(
              c, l, "Recente apps", "Via Thorhaven of de actieve toegankelijkheidsservice gezien.");
      JSONArray list = Store.recent(c);
      int shown = 0;
      for (int n = 0; n < list.length() && shown < 6; n++) {
        String p = list.optString(n);
        if (c.getPackageManager().getLaunchIntentForPackage(p) == null) continue;
        shown++;
        recent.addView(
            Ui.button(
                c,
                Store.name(c, p),
                () -> {
                  if (close != null) close.run();
                  Store.launch(c, p, Store.prefs(c).getInt("screen:" + p, Store.screen(c, false)));
                }));
      }
      if (shown == 0) recent.addView(Ui.text(c, "Open apps om ze hier te zien.", 13, Ui.MUTED));
    }
    LinearLayout favorites = Ui.card(c, l, "Favorieten", null);
    if (!Store.prefs(c).getBoolean("panelFavorites", true)) favorites.setVisibility(View.GONE);
    int count = 0;
    for (Store.App app : Store.apps(c))
      if (Store.prefs(c).getBoolean("favorite:" + app.pkg, false)) {
        count++;
        favorites.addView(
            Ui.button(
                c,
                app.name,
                () -> {
                  if (close != null) close.run();
                  Store.launch(
                      c,
                      app.pkg,
                      Store.prefs(c).getInt("screen:" + app.pkg, Store.screen(c, false)));
                }));
      }
    if (count == 0)
      favorites.addView(Ui.text(c, "Voeg favorieten toe via Apps → Profiel.", 12, Ui.MUTED));
    JSONArray arr = Store.pairs(c);
    if (Store.prefs(c).getBoolean("panelPairs", true) && arr.length() > 0) {
      LinearLayout pairs = Ui.card(c, l, "App-paren", null);
      for (int n = 0; n < arr.length(); n++) {
        try {
          JSONObject p = arr.getJSONObject(n);
          pairs.addView(
              Ui.button(
                  c,
                  p.getString("name"),
                  () -> {
                    if (close != null) close.run();
                    Store.openPair(c, p);
                  }));
        } catch (Exception ignored) {
        }
      }
    }
    LinearLayout touch =
        Ui.card(c, l, "Aanraakknoppen", "Tikacties voor de app op het bovenste scherm.");
    touch.addView(
        Ui.button(
            c,
            "Aanraakknoppen openen",
            () -> {
              if (close != null) close.run();
              TouchControls.show(c);
            }));
    for (int i = 0; i < l.getChildCount(); i++) {
      View child = l.getChildAt(i);
      if (child instanceof LinearLayout) {
        LinearLayout box = (LinearLayout) child;
        if (box.getChildCount() > 0 && box.getChildAt(0) instanceof TextView) {
          String title = ((TextView) box.getChildAt(0)).getText().toString();
          for (String key : ExtraFeatures.CARDS)
            if (title.equals(Language.text(c, key))) box.setTag(key);
        }
      }
    }
    ExtraFeatures.reorder(c, l);
    return l;
  }
}
