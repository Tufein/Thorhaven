package nl.thorhaven.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Finite original zone tests. A software-success response never proves visible physical output. */
final class RgbDiagnostics {
  static final String[] MODES = {"zones", "channels", "left1", "left2", "right1", "right2"};
  static final int STEP_MS = 2000, CHANNEL = 25;

  static String text(Context c, String nl, String en) {
    return Language.isEnglish(c) ? en : nl;
  }

  static long duration(String mode) {
    if ("channels".equals(mode)) return 24000;
    for (String candidate : MODES) if (candidate.equals(mode)) return 8000;
    throw new IllegalArgumentException("Invalid RGB diagnostic mode");
  }

  static int zone(String mode, long elapsed) {
    duration(mode);
    if ("zones".equals(mode)) return (int) (elapsed / STEP_MS);
    if ("channels".equals(mode)) return (int) (elapsed / (STEP_MS * 3));
    for (int n = 2; n < MODES.length; n++) if (MODES[n].equals(mode)) return n - 2;
    throw new IllegalArgumentException("Invalid RGB diagnostic mode");
  }

  static RgbEngine.Frame frame(String mode, long elapsed) {
    long limit = duration(mode);
    if (elapsed < 0 || elapsed >= limit) return new RgbEngine.Frame(false, 0, false, 0);
    int selected = zone(mode, elapsed);
    int color = CHANNEL << 16 | CHANNEL << 8 | CHANNEL;
    if ("channels".equals(mode)) color = CHANNEL << (16 - (int) (elapsed / STEP_MS % 3) * 8);
    return new RgbEngine.Frame(
        selected < 2,
        selected == 0 ? color : 0,
        selected == 1 ? color : 0,
        selected >= 2,
        selected == 2 ? color : 0,
        selected == 3 ? color : 0);
  }

  static String zoneName(Context c, int index) {
    String[] nl = {"Linker zone 1", "Linker zone 2", "Rechter zone 1", "Rechter zone 2"};
    String[] en = {"Left zone 1", "Left zone 2", "Right zone 1", "Right zone 2"};
    if (index < 0 || index > 3) return text(c, "Geen actieve zone", "No active zone");
    return text(c, nl[index], en[index]);
  }

  static String progress(Context c, String mode, long elapsed) {
    long limit = duration(mode);
    if (elapsed >= limit) return text(c, "RGB-test klaar", "RGB test finished");
    String result =
        text(c, "RGB-zonetest · ", "RGB zone test · ")
            + zoneName(c, zone(mode, Math.max(0, elapsed)));
    if ("channels".equals(mode)) {
      int channel = (int) (Math.max(0, elapsed) / STEP_MS % 3);
      result +=
          " · "
              + text(
                  c,
                  new String[] {"Rood", "Groen", "Blauw"}[channel],
                  new String[] {"Red", "Green", "Blue"}[channel]);
    }
    return result
        + " · "
        + Math.max(0, (limit - elapsed + 999) / 1000)
        + text(c, " s resterend", " s remaining");
  }

  static String name(Context c, String mode) {
    if ("zones".equals(mode))
      return text(c, "Alle vier zones · 8 seconden", "All four zones · 8 seconds");
    if ("channels".equals(mode))
      return text(
          c,
          "Rood, groen en blauw per zone · 24 seconden",
          "Red, green and blue per zone · 24 seconds");
    return zoneName(c, zone(mode, 0)) + text(c, " · 8 seconden", " · 8 seconds");
  }

  static android.app.AlertDialog open(MainActivity a) {
    Handler handler = new Handler(Looper.getMainLooper());
    long[] ownedGeneration = {-1};
    boolean[] cleaned = {false};
    Runnable[] refresh = {null};
    Runnable cleanup =
        () -> {
          if (cleaned[0]) return;
          cleaned[0] = true;
          if (refresh[0] != null) handler.removeCallbacks(refresh[0]);
          if (ownedGeneration[0] >= 0 && ownedGeneration[0] == RgbService.requestGeneration)
            RgbService.stopDiagnostic(a);
        };
    LinearLayout content =
        new LinearLayout(a) {
          @Override
          protected void onDetachedFromWindow() {
            cleanup.run();
            super.onDetachedFromWindow();
          }
        };
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 8));
    content.addView(
        Ui.text(
            a,
            text(
                a,
                "Experimenteel: elke stick heeft twee adresseerbare zones. De test verlicht één"
                    + " zone tegelijk op ongeveer 10% sterkte en herstelt daarna de"
                    + " AYN-instellingen. Een test stopt ook wanneer de schermen uitgaan."
                    + " Controleer zelf welke fysieke zone reageert; een geslaagde schrijfopdracht"
                    + " is geen bewijs dat het licht werkt.",
                "Experimental: each stick has two addressable zones. The test lights one zone at a"
                    + " time at about 10% intensity, then restores AYN settings. It also stops when"
                    + " the screens turn off. Check which physical zone responds; a successful"
                    + " write does not prove that the light works."),
            13,
            Ui.MUTED));
    TextView status = Ui.text(a, RgbService.status, 14, Ui.ACCENT);
    content.addView(status);
    Runnable update =
        new Runnable() {
          public void run() {
            if (cleaned[0]) return;
            status.setText(RgbService.status);
            handler.postDelayed(this, 500);
          }
        };
    refresh[0] = update;
    for (String mode : MODES)
      content.addView(
          Ui.button(
              a,
              name(a, mode),
              () -> {
                if (cleaned[0]) return;
                if (RgbService.startDiagnostic(a, mode))
                  ownedGeneration[0] = RgbService.requestGeneration;
              }));
    content.addView(
        Ui.button(
            a,
            text(a, "Stoppen en AYN herstellen", "Stop and restore AYN"),
            () -> RgbService.stop(a)));
    content.addView(
        Ui.text(
            a,
            text(
                a,
                "Stop eerst een actieve RGB-sessie. Herstel een onderbroken sessie voordat je een"
                    + " test start. Andere RGB-apps of stock animaties kunnen testkleuren"
                    + " overschrijven. Sluiten stopt een lopende zonetest.",
                "Stop an active RGB session first. Restore an interrupted session before starting a"
                    + " test. Other RGB apps or stock animations may overwrite test colors. Closing"
                    + " this dialog stops a running zone test."),
            13,
            Ui.MUTED));
    ScrollView scroll = new ScrollView(a);
    scroll.addView(content);
    android.app.AlertDialog dialog =
        new android.app.AlertDialog(a) {
          @Override
          protected void onStop() {
            // onDismiss is queued; cancel synchronously even before the first view attachment.
            cleanup.run();
            super.onStop();
          }
        };
    dialog.setTitle(text(a, "RGB-zones testen · experimenteel", "Test RGB zones · experimental"));
    dialog.setView(scroll);
    dialog.setButton(
        android.content.DialogInterface.BUTTON_POSITIVE,
        text(a, "Sluiten", "Close"),
        (android.content.DialogInterface.OnClickListener) null);
    dialog.setOnShowListener(
        d -> {
          if (!cleaned[0]) handler.post(update);
        });
    dialog.setOnDismissListener(d -> cleanup.run());
    dialog.show();
    return dialog;
  }

  private RgbDiagnostics() {}
}
