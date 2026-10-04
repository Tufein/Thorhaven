package nl.thorhaven.app;

import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.view.KeyEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

final class ExperimentToolsChecks {
  static void run(SmokeInstrumentation t, Context c, MainActivity a) throws Exception {
    Store.prefs(c).edit().putString("notes:qa.secret", "PRIVATE_NOTE_SENTINEL").commit();
    JSONObject report = ExperimentTools.report(c);
    t.check(
        report.getString("format").equals("thorhaven-diagnostics") && report.getInt("schema") == 1,
        "Diagnostic report has a versioned format");
    t.check(
        report.getJSONObject("app").getLong("versionCode")
                == c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode()
            && report
                .getJSONObject("app")
                .getString("version")
                .equals(c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName),
        "Diagnostic report identifies the actual installed version");
    t.check(
        report.getJSONArray("displays").length() >= 2 && report.has("assignedBottom"),
        "Diagnostic report includes real dual-display discovery and assignment");
    t.check(
        !report.toString().contains("PRIVATE_NOTE_SENTINEL")
            && !report.has("preferences")
            && !report.has("tokens"),
        "Diagnostic export excludes note content, preference dumps and tokens");
    t.check(
        report.getJSONObject("automaticBackup").length() == 2
            && !report.toString().contains("content://"),
        "Diagnostic report excludes backup-folder URIs");
    t.check(
        ExperimentTools.mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            && ExperimentTools.mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            && ExperimentTools.mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            && !ExperimentTools.mediaKey(KeyEvent.KEYCODE_HOME),
        "Media actions accept only previous, next and play/pause");
    boolean bad = false;
    try {
      ExperimentTools.sendMedia(c, KeyEvent.KEYCODE_HOME);
    } catch (IllegalArgumentException e) {
      bad = true;
    }
    t.check(bad, "Invalid media action is rejected before dispatch");
    JSONArray sessions =
        new JSONArray()
            .put(
                new JSONObject()
                    .put("date", 0)
                    .put("duration", 120000)
                    .put(
                        "samples",
                        new JSONArray()
                            .put(
                                new JSONObject()
                                    .put("ms", 0)
                                    .put("level", 88)
                                    .put("charging", false))
                            .put(
                                new JSONObject()
                                    .put("ms", 60000)
                                    .put("level", 87)
                                    .put("charging", true))))
            .put(
                new JSONObject()
                    .put("date", 1000)
                    .put("duration", 2000)
                    .put("samples", new JSONArray()));
    ExtraFeatures.save(c, "sessions", sessions.toString());
    String csv = ExperimentTools.sessionsCsv(c);
    t.check(
        csv.contains("1970-01-01T00:00:00Z,120,60,87,true\r\n")
            && csv.contains("1970-01-01T00:00:01Z,2,,,\r\n"),
        "Session CSV preserves UTC time, sampled battery and empty sessions");
    t.check(
        csv.split("\r\n").length == 4,
        "CSV has one row per recorded sample plus an empty-session row");
    sessions.getJSONObject(0).getJSONArray("samples").getJSONObject(0).put("level", 999);
    Store.prefs(c).edit().putString("sessions", sessions.toString()).commit();
    bad = false;
    try {
      ExperimentTools.sessionsCsv(c);
    } catch (Exception e) {
      bad = true;
    }
    t.check(bad, "Session CSV refuses invalid battery data");
    Store.prefs(c).edit().putString("sessions", "[]").commit();
    Uri tree = DocumentsContract.buildTreeDocumentUri("nl.thorhaven.backup.test", "root");
    Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, "root");
    Uri destination =
        DocumentsContract.createDocument(
            c.getContentResolver(), parent, "application/zip", "thorhaven-auto-0000000000006.zip");
    try {
      ExperimentTools.write(c, destination, 48);
      String json;
      try (InputStream in = c.getContentResolver().openInputStream(destination)) {
        json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
      t.check(
          new JSONObject(json).getString("format").equals("thorhaven-diagnostics"),
          "Diagnostic JSON writes through a real document provider");
      ExperimentTools.write(c, destination, 49);
      String text;
      try (InputStream in = c.getContentResolver().openInputStream(destination)) {
        text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
      t.check(
          text.equals(
              "session_started_utc,elapsed_seconds,sample_elapsed_seconds,battery_percent,charging\r\n"),
          "CSV export replaces destination contents without stale JSON bytes");
      bad = false;
      try {
        ExperimentTools.write(c, destination, 99);
      } catch (IllegalArgumentException e) {
        bad = true;
      }
      t.check(bad, "Unknown report export is rejected");
    } finally {
      DocumentsContract.deleteDocument(c.getContentResolver(), destination);
    }
    t.runOnMainSync(
        () -> {
          ExperimentTools.page(a);
          ExperimentTools.media(a, Ui.col(a));
        });
    t.check(true, "Experimental launcher and media card construct in English");
  }
}
