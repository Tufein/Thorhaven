package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.os.*;
import android.view.*;
import java.io.*;
import org.json.*;

final class NewFeatureChecks {
  final SmokeInstrumentation test;
  final Context c;
  final MainActivity activity;

  NewFeatureChecks(SmokeInstrumentation t, Context c, MainActivity a) {
    test = t;
    this.c = c;
    activity = a;
  }

  void check(boolean pass, String label) throws Exception {
    test.check(pass, label);
  }

  static java.util.List<View> allViews(View root) {
    java.util.List<View> found = new java.util.ArrayList<>();
    found.add(root);
    if (root instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) found.addAll(allViews(group.getChildAt(i)));
    }
    return found;
  }

  void run() throws Exception {
    check(
        PadProfile.preset(1).getJSONArray("buttons").getInt(0) == 305
            && PadProfile.preset(1).getJSONArray("buttons").getInt(2) == 308,
        "Nintendo preset swaps both face-button pairs");
    check(
        PadProfile.preset(2).getJSONArray("buttons").getInt(13) == 103
            && PadProfile.preset(2).getJSONArray("buttons").getInt(0) == 28,
        "PC-menu preset uses arrows and Enter");
    check(
        PadProfile.preset(3).getJSONArray("buttons").getInt(13) == 17,
        "WASD preset uses Linux keyboard targets");
    check(
        DriftCheck.suggested(.02f) == 5 && DriftCheck.suggested(0) == 3,
        "Drift recommendation includes a three-point rest margin");
    check(
        DriftCheck.suggested(.8f) == -1 && DriftCheck.suggested(Float.NaN) == -1,
        "Moving or invalid sticks do not produce a calibration recommendation");
    for (int i = 0; i < 20; i++) Store.recordRecent(c, "example.app" + i);
    Store.recordRecent(c, "example.app5");
    JSONArray recent = Store.recent(c);
    check(
        recent.length() == 12 && recent.getString(0).equals("example.app5"),
        "Recent list is bounded and moves repeated apps to the front");
    JSONObject backup = Store.backup(c);
    Store.prefs(c).edit().remove("recent").commit();
    Store.restore(c, backup.toString());
    check(
        Store.recent(c).getString(0).equals("example.app5"),
        "Recent apps round-trip through backup without launching them");
    Store.prefs(c).edit().putBoolean("panelTouchOnly", true).commit();
    check(
        (ThorService.panelFlags(c) & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0,
        "Touch-only panel explicitly leaves controller focus to the game");
    Store.prefs(c).edit().putBoolean("panelTouchOnly", false).commit();
    check(
        (ThorService.panelFlags(c) & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0,
        "Controller panel can take focus when requested");
    class Clock implements PlayStats.Clock {
      long elapsed = 1000;
      int boot = 7;

      public long now() {
        return elapsed;
      }

      public int boot() {
        return boot;
      }
    }
    c.getSharedPreferences("thorhaven-stats", 0).edit().clear().commit();
    Clock clock = new Clock();
    PlayStats stats = new PlayStats(c, clock);
    stats.beginSleep(80, false);
    clock.elapsed += 4 * 3600000L;
    JSONObject row = stats.endSleep(72, false);
    check(
        row.optBoolean("eligible") && row.getInt("drop") == 8,
        "Uncharged four-hour screen-off session records measured battery loss");
    check(
        Math.abs(PlayStats.average(PlayStats.history(c)) - 2) < .001,
        "Standby average uses elapsed time and percentage-point loss");
    stats.beginSleep(72, false);
    clock.elapsed += 10 * 60000;
    row = stats.endSleep(70, false);
    check(
        !row.optBoolean("eligible") && Math.abs(PlayStats.average(PlayStats.history(c)) - 2) < .001,
        "Short sleep sessions do not distort the long-session average");
    stats.beginSleep(70, false);
    stats.receiver.onReceive(
        c, new Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PLUGGED, 2));
    clock.elapsed += 4 * 3600000L;
    row = stats.endSleep(65, false);
    check(
        row.getBoolean("charged") && !row.getBoolean("eligible"),
        "A charger connected during screen-off excludes the whole measurement");
    stats.beginSleep(65, false);
    clock.elapsed += 4 * 3600000L;
    clock.boot++;
    check(
        stats.endSleep(60, false) == null,
        "A reboot invalidates an unfinished elapsed-time sleep measurement");
    stats.focus("example.play");
    clock.elapsed += 30000;
    stats.flush();
    stats.focus(c.getPackageName());
    clock.elapsed += 10000;
    stats.flush();
    check(
        stats.prefs.getLong("time:example.play", 0) == 30000,
        "Active app-time pauses while Thorhaven is foreground");
    c.getSharedPreferences("thorhaven-stats", 0).edit().clear().commit();
    String textPkg = "example.guide.text", pdfPkg = "example.guide.pdf";
    String text = "Thorhaven testgids € 日本語\nEen offline tip.\n";
    OfflineGuides.importStream(
        c, textPkg, "Gids.txt", new ByteArrayInputStream(text.getBytes("UTF-8")));
    check(
        OfflineGuides.exists(c, textPkg)
            && new String(OfflineGuides.read(OfflineGuides.file(c, textPkg)), "UTF-8").equals(text),
        "UTF-8 guide is copied into private local storage");
    boolean rejected = false;
    try {
      OfflineGuides.importStream(
          c, textPkg, "Bad.dat", new ByteArrayInputStream(new byte[] {0, 1, 2, 3}));
    } catch (Exception expected) {
      rejected = true;
    }
    check(
        rejected
            && new String(OfflineGuides.read(OfflineGuides.file(c, textPkg)), "UTF-8").equals(text),
        "Rejected binary import preserves the earlier offline guide");
    rejected = false;
    try {
      OfflineGuides.file(c, "../../outside");
    } catch (Exception expected) {
      rejected = true;
    }
    check(rejected, "Guide identifiers cannot traverse storage paths");
    rejected = false;
    try {
      OfflineGuides.importStream(
          c,
          textPkg,
          "Huge.txt",
          new InputStream() {
            int remaining = 2 * 1024 * 1024 + 1;

            public int read() {
              return remaining-- > 0 ? 'a' : -1;
            }

            public int read(byte[] b, int start, int length) {
              if (remaining <= 0) return -1;
              int count = Math.min(length, remaining);
              java.util.Arrays.fill(b, start, start + count, (byte) 'a');
              remaining -= count;
              return count;
            }
          });
    } catch (Exception expected) {
      rejected = true;
    }
    check(
        rejected && OfflineGuides.meta(c, textPkg).getString("name").equals("Gids.txt"),
        "Oversized text import preserves existing guide metadata");
    ByteArrayOutputStream pdfBytes = new ByteArrayOutputStream();
    PdfDocument document = new PdfDocument();
    try {
      for (int p = 1; p <= 2; p++) {
        PdfDocument.Page page =
            document.startPage(new PdfDocument.PageInfo.Builder(600, 800, p).create());
        Paint ink = new Paint();
        ink.setColor(Color.BLACK);
        ink.setTextSize(28);
        page.getCanvas().drawText("Thorhaven gids pagina " + p, 40, 80, ink);
        document.finishPage(page);
      }
      document.writeTo(pdfBytes);
    } finally {
      document.close();
    }
    OfflineGuides.importStream(
        c, pdfPkg, "Test.pdf", new ByteArrayInputStream(pdfBytes.toByteArray()));
    check(
        OfflineGuides.meta(c, pdfPkg).getInt("pages") == 2,
        "Real PDF is parsed and counted before local installation");
    OfflineGuides.remember(c, pdfPkg, "page", 1);
    check(
        OfflineGuides.meta(c, pdfPkg).getInt("page") == 1,
        "PDF page bookmark survives reader creation");
    final GuidePane[] panes = new GuidePane[2];
    test.runOnMainSync(
        () -> {
          panes[0] = new GuidePane(activity, textPkg, () -> {});
          panes[1] = new GuidePane(activity, pdfPkg, () -> {});
          activity.go("Controller");
        });
    Thread.sleep(700);
    check(
        panes[0].status.getText().toString().startsWith("Offline tekst"),
        "Local text reader loads without network access");
    long deadline = System.currentTimeMillis() + 5000;
    while (panes[1].bitmap == null && System.currentTimeMillis() < deadline) Thread.sleep(50);
    check(
        panes[1].bitmap != null && panes[1].page == 1,
        "PDF reader renders the bookmarked second page");
    test.runOnMainSync(
        () -> {
          panes[0].close();
          panes[1].close();
        });
    test.runOnMainSync(
        () -> {
          activity.go("Overzicht");
          activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_L1));
        });
    check(activity.page.equals("Instellen"), "L1 wraps from first page to the final page");
    test.runOnMainSync(
        () ->
            activity.dispatchKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_R1)));
    check(activity.page.equals("Overzicht"), "R1 wraps through the full ten-page navigation");
    // Instrumentation force-stops the target; reset stale binding state before enabling.
    test.shell("settings delete secure enabled_accessibility_services");
    test.shell("settings put secure accessibility_enabled 0");
    Thread.sleep(300);
    // Verify real accessibility overlays, not just their view builders.
    test.shell(
        "settings put secure enabled_accessibility_services"
            + " nl.thorhaven.app/nl.thorhaven.app.ThorService");
    test.shell("settings put secure accessibility_enabled 1");
    deadline = System.currentTimeMillis() + 8000;
    while ((ThorService.instance == null || ThorService.instance.cover == null)
        && System.currentTimeMillis() < deadline) Thread.sleep(100);
    check(
        ThorService.instance != null && ThorService.instance.cover != null,
        "Real accessibility service binds for second-display overlays");
    ThorService service = ThorService.instance;
    test.runOnMainSync(() -> service.cover.show());
    check(
        service.cover.shown() && service.cover.display == Store.screen(c, true),
        "Black OLED curtain attaches only to the assigned bottom display");
    test.runOnMainSync(() -> service.cover.hide());
    check(
        !service.cover.shown(),
        "Black curtain restores the underlying screen without power commands");
    Store.prefs(c).edit().putBoolean("panelTouchOnly", true).commit();
    test.runOnMainSync(service::showPanel);
    check(
        service.panel != null
            && (((WindowManager.LayoutParams) service.panel.getLayoutParams()).flags
                    & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                != 0,
        "Real touch-only quick panel keeps its window non-focusable");
    test.runOnMainSync(service::hidePanel);
    test.runOnMainSync(() -> service.showGuide(pdfPkg));
    check(
        service.panel != null && service.guidePane != null,
        "PDF guide attaches as an accessibility overlay over the second display");
    Thread.sleep(500);
    check(service.guidePane.bitmap != null, "Real guide overlay renders its PDF page");
    test.runOnMainSync(
        () -> {
          for (android.view.View v : allViews(service.guidePane))
            if (v instanceof android.widget.Button
                && ((android.widget.Button) v).getText().toString().equals("Pagina"))
              v.performClick();
        });
    check(
        service.guidePane.pageDialog != null && service.guidePane.pageDialog.isShowing(),
        "PDF page dialog opens above a real accessibility overlay");
    test.runOnMainSync(service::hidePanel);
    check(
        service.panel == null && service.guidePane == null,
        "Closing guide overlay releases the reader and window");
    Store.prefs(c).edit().putBoolean("panelTouchOnly", false).commit();
    test.runOnMainSync(service::showPanel);
    test.runOnMainSync(
        () -> service.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)));
    check(service.panel == null, "Controller B closes a focusable panel even with combos disabled");
    test.runOnMainSync(
        () -> service.onKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B)));
    OfflineGuides.remove(c, textPkg);
    OfflineGuides.remove(c, pdfPkg);
    check(!OfflineGuides.exists(c, pdfPkg), "Removing a guide clears only its private copy");
  }
}
