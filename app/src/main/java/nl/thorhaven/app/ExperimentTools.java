package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Optional tools and a privacy-conscious report for real-device troubleshooting. */
final class ExperimentTools {
  static void page(MainActivity a) {
    LinearLayout l =
        Ui.card(
            a,
            a.content,
            "Experimentele werkplaats",
            "Schermspiegeling, opname, macro's, trackpad en rapporten voor jouw apparaattests.");
    l.addView(Ui.button(a, "Werkplaats openen", () -> open(a)));
  }

  static void open(Context c) {
    c.startActivity(
        new Intent(c, MainActivity.class)
            .putExtra("pageKey", "Instellen")
            .putExtra("experiments", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  static void dialog(MainActivity a) {
    LinearLayout l = Ui.col(a);
    l.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 12));
    l.addView(
        Ui.text(
            a,
            "De experimentele functies zijn bedoeld voor tests op je Thor. Start één hulpmiddel"
                + " tegelijk. Android vraagt zelf toestemming voor schermopname; root-invoer"
                + " vereist Shizuku of AYN PServer.",
            14,
            Ui.MUTED));
    l.addView(Ui.button(a, "Schermspiegeling en opname", () -> ScreenLabActivity.open(a)));
    l.addView(Ui.button(a, "Macro's en trackpad", () -> ControlLab.open(a)));
    l.addView(Ui.button(a, "RGB Studio", () -> a.go("RGB Studio")));
    media(a, l);
    l.addView(Ui.title(a, "Rapporten exporteren", 18));
    l.addView(
        Ui.text(
            a,
            "Diagnostiek bevat app- en Androidversies, schermgegevens en toegang/status. Geen"
                + " notities, gidsen, macro-inhoud, tokens of toetsgeschiedenis. Controleer het"
                + " bestand voordat je het deelt.",
            13,
            Ui.MUTED));
    l.addView(
        Ui.button(
            a,
            "Diagnostiek als JSON exporteren",
            () -> document(a, 48, "application/json", "thorhaven-0.7-diagnostics.json")));
    l.addView(
        Ui.button(
            a,
            "Speelsessies als CSV exporteren",
            () -> document(a, 49, "text/csv", "thorhaven-play-sessions.csv")));
    ScrollView scroll = new ScrollView(a);
    scroll.addView(l);
    new Ui.Dialog(a)
        .setTitle("Experimentele werkplaats")
        .setView(scroll)
        .setPositiveButton("Sluiten", null)
        .show();
  }

  static void document(MainActivity a, int req, String mime, String name) {
    try {
      a.startActivityForResult(
          new Intent(Intent.ACTION_CREATE_DOCUMENT)
              .addCategory(Intent.CATEGORY_OPENABLE)
              .setType(mime)
              .putExtra(Intent.EXTRA_TITLE, name),
          req);
    } catch (RuntimeException e) {
      Ui.toast(a, "De Android-bestandskiezer is niet beschikbaar.");
    }
  }

  static void media(Context c, LinearLayout parent) {
    LinearLayout l =
        Ui.card(
            c,
            parent,
            "Mediabediening",
            "Vorige, afspelen/pauze en volgende voor de actieve mediasessie. De muziekapp bepaalt"
                + " welke acties worden ondersteund.");
    LinearLayout r = Ui.row(c);
    String[] labels = {"Vorige track", "Afspelen / pauze", "Volgende track"};
    int[] codes = {
      KeyEvent.KEYCODE_MEDIA_PREVIOUS,
      KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
      KeyEvent.KEYCODE_MEDIA_NEXT
    };
    for (int i = 0; i < codes.length; i++) {
      int key = codes[i];
      r.addView(
          Ui.button(c, labels[i], () -> sendMedia(c, key)),
          new LinearLayout.LayoutParams(0, -2, 1));
    }
    l.addView(r);
  }

  static boolean mediaKey(int key) {
    return key == KeyEvent.KEYCODE_MEDIA_PREVIOUS
        || key == KeyEvent.KEYCODE_MEDIA_NEXT
        || key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
  }

  static void sendMedia(Context c, int key) {
    if (!mediaKey(key)) throw new IllegalArgumentException("Unsupported media action");
    try {
      AudioManager manager = c.getSystemService(AudioManager.class);
      long time = SystemClock.uptimeMillis();
      manager.dispatchMediaKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, key, 0));
      manager.dispatchMediaKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_UP, key, 0));
    } catch (Exception e) {
      Ui.toast(c, "Mediabediening niet beschikbaar: " + e.getMessage());
    }
  }

  static JSONObject report(Context c) throws Exception {
    java.text.SimpleDateFormat date =
        new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.ROOT);
    date.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
    JSONObject j =
        new JSONObject()
            .put("format", "thorhaven-diagnostics")
            .put("schema", 1)
            .put("generatedUtc", date.format(new java.util.Date()));
    android.content.pm.PackageInfo own =
        c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
    j.put(
        "app",
        new JSONObject()
            .put("package", c.getPackageName())
            .put("version", own.versionName)
            .put("versionCode", own.getLongVersionCode()));
    j.put(
        "android",
        new JSONObject()
            .put("sdk", Build.VERSION.SDK_INT)
            .put("release", Build.VERSION.RELEASE)
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("fingerprint", Build.FINGERPRINT));
    JSONArray displays = new JSONArray();
    for (Display display : Store.displays(c)) {
      android.util.DisplayMetrics metrics = Store.displayMetrics(c, display);
      displays.put(
          new JSONObject()
              .put("id", display.getDisplayId())
              .put("name", display.getName())
              .put("width", metrics.widthPixels)
              .put("height", metrics.heightPixels)
              .put("densityDpi", metrics.densityDpi)
              .put("state", display.getState()));
    }
    j.put("displays", displays)
        .put("assignedTop", Store.screen(c, false))
        .put("assignedBottom", Store.screen(c, true));
    j.put(
        "access",
        new JSONObject()
            .put("accessibilityActive", ThorService.instance != null)
            .put("modifySettings", Settings.System.canWrite(c))
            .put("bridgeConnected", Bridge.remote != null)
            .put("nativeRemappingActive", Controls.active)
            .put("hardwareAutomationEnabled", HardwareAutomation.enabled));
    j.put(
        "rgb",
        new JSONObject()
            .put("active", RgbService.instance != null)
            .put("recoveryPending", RgbSession.recovery(c).contains("baseline")));
    j.put("hardwareRecoveryPending", Store.prefs(c).contains("hw:snapshot"));
    j.put(
        "automaticBackup",
        new JSONObject()
            .put("enabled", AutoBackup.prefs(c).getBoolean("enabled", false))
            .put(
                "jobScheduled",
                c.getSystemService(android.app.job.JobScheduler.class).getPendingJob(AutoBackup.JOB)
                    != null));
    j.put(
        "battery",
        new JSONObject().put("level", PlayStats.level(c)).put("charging", PlayStats.plugged(c)));
    int documents = 0;
    for (String id : OfflineGuides.prefs(c).getAll().keySet())
      if (OfflineGuides.exists(c, id)) documents++;
    j.put("documentCount", documents);
    j.put(
        "limitations",
        "Status snapshot only. Does not prove physical fan/cooling, capture, injected-input or ROM"
            + " compatibility.");
    return j;
  }

  static String sessionsCsv(Context c) throws Exception {
    JSONArray sessions = ExtraFeatures.array(c, "sessions");
    SessionStats.validate(sessions.toString());
    StringBuilder csv =
        new StringBuilder(
            "session_started_utc,elapsed_seconds,sample_elapsed_seconds,battery_percent,charging\r\n");
    java.text.SimpleDateFormat date =
        new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.ROOT);
    date.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
    for (int i = 0; i < sessions.length(); i++) {
      JSONObject row = sessions.getJSONObject(i);
      String common =
          date.format(new java.util.Date(row.getLong("date")))
              + ","
              + row.getLong("duration") / 1000
              + ",";
      JSONArray samples = row.getJSONArray("samples");
      if (samples.length() == 0) csv.append(common).append(",,\r\n");
      for (int n = 0; n < samples.length(); n++) {
        JSONObject sample = samples.getJSONObject(n);
        csv.append(common)
            .append(sample.getLong("ms") / 1000)
            .append(',')
            .append(sample.getInt("level"))
            .append(',')
            .append(sample.getBoolean("charging"))
            .append("\r\n");
      }
    }
    return csv.toString();
  }

  static void write(Context c, Uri uri, int request) throws Exception {
    if (request != 48 && request != 49) throw new IllegalArgumentException("Unsupported report");
    String body = request == 48 ? report(c).toString(2) : sessionsCsv(c);
    try (OutputStream out = c.getContentResolver().openOutputStream(uri, "wt")) {
      if (out == null) throw new IOException("Destination unavailable");
      out.write(body.getBytes(StandardCharsets.UTF_8));
    }
  }

  static void export(MainActivity a, Uri uri, int request) {
    OfflineGuides.worker.execute(
        () -> {
          String status;
          try {
            write(a, uri, request);
            status = "Rapport opgeslagen.";
          } catch (Exception e) {
            status = "Export mislukt: " + e.getMessage();
          }
          String result = status;
          a.handler.post(() -> Ui.toast(a, result));
        });
  }
}
