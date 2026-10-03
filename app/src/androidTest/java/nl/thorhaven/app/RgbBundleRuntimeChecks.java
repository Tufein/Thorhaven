package nl.thorhaven.app;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.UiAutomation;
import android.content.*;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import org.json.*;

/**
 * Actual document-provider bytes and the production result/confirmation path, without a picker
 * bypass claim.
 */
final class RgbBundleRuntimeChecks {
  static void run(SmokeInstrumentation t, Context c, MainActivity previous) throws Exception {
    // The preceding editor suite changes focus between displays. A real picker result returns
    // to its foreground owner, so establish that same state for this result-handler test.
    MainActivity a =
        (MainActivity)
            t.startActivitySync(
                new Intent(c, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
                ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());
    JSONObject original = RgbSettings.load(c);
    String language = Store.prefs(c).getString("language", "nl");
    Uri uri = null;
    UiAutomation automation =
        t.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    try {
      Store.prefs(c).edit().putString("language", "en").commit();
      JSONObject saved = RgbSettings.defaults();
      RgbPresetTools.append(saved, "Portable Notities 日本語", RgbSettings.defaultProfile());
      String id = saved.getJSONArray("presets").getJSONObject(0).getString("id");
      saved.getJSONObject("apps").put("com.android.settings", id);
      RgbSettings.save(c, saved);
      Uri parent = DocumentsContract.buildDocumentUri("nl.thorhaven.backup.test", "root");
      uri =
          DocumentsContract.createDocument(
              c.getContentResolver(),
              parent,
              "application/json",
              "thorhaven-auto-" + System.currentTimeMillis() + ".zip");
      if (uri == null) throw new IOException("Could not create isolated RGB exchange document");
      final Uri document = uri;
      String expected = RgbPresetBundle.encode(saved);
      t.runOnMainSync(
          () ->
              a.onActivityResult(
                  RgbPresetBundle.EXPORT, Activity.RESULT_OK, new Intent().setData(document)));
      long deadline = SystemClock.elapsedRealtime() + 7000;
      String actual = "";
      while (SystemClock.elapsedRealtime() < deadline) {
        try (InputStream in = c.getContentResolver().openInputStream(document)) {
          actual = RgbPresetBundle.read(in);
        }
        if (actual.equals(expected)) break;
        Thread.sleep(50);
      }
      t.check(
          actual.equals(expected)
              && RgbPresetBundle.parse(actual).length() == 1
              && !actual.contains("com.android.settings")
              && !actual.contains(id),
          "Production export result writes exact Unicode styles-only bytes through Android"
              + " ContentResolver");
      String before = Store.prefs(c).getString(RgbSettings.KEY, "");
      t.runOnMainSync(
          () ->
              a.onActivityResult(
                  RgbPresetBundle.IMPORT, Activity.RESULT_OK, new Intent().setData(document)));
      click(automation, "Cancel");
      t.waitForIdleSync();
      t.check(
          before.equals(Store.prefs(c).getString(RgbSettings.KEY, "")),
          "Canceling the real RGB import confirmation preserves every existing preset and"
              + " assignment");
      t.runOnMainSync(
          () ->
              a.onActivityResult(
                  RgbPresetBundle.IMPORT, Activity.RESULT_OK, new Intent().setData(document)));
      // Change an unrelated option after reading, before confirmation: import must use the latest
      // snapshot.
      awaitDialog(automation);
      RgbSettings.update(c, current -> current.put("fps", 10));
      click(automation, "Add");
      deadline = SystemClock.elapsedRealtime() + 5000;
      JSONObject imported = RgbSettings.load(c);
      while (imported.getJSONArray("presets").length() != 2
          && SystemClock.elapsedRealtime() < deadline) {
        Thread.sleep(50);
        imported = RgbSettings.load(c);
      }
      t.check(
          imported.getJSONArray("presets").length() == 2
              && imported.getJSONArray("presets").getJSONObject(0).getString("id").equals(id)
              && !imported.getJSONArray("presets").getJSONObject(1).getString("id").equals(id)
              && imported.getJSONObject("apps").getString("com.android.settings").equals(id)
              && imported.getInt("fps") == 10
              && imported
                  .getJSONObject("profile")
                  .toString()
                  .equals(saved.getJSONObject("profile").toString())
              && RgbService.instance == null
              && !RgbService.requested,
          "Confirmed production RGB import adds a new ID against current settings without starting"
              + " hardware or replacing app routing");
    } finally {
      if (uri != null) DocumentsContract.deleteDocument(c.getContentResolver(), uri);
      RgbSettings.save(c, original);
      Store.prefs(c).edit().putString("language", language).commit();
      t.runOnMainSync(a::finish);
    }
  }

  static void awaitDialog(UiAutomation automation) throws Exception {
    long deadline = SystemClock.elapsedRealtime() + 7000;
    while (SystemClock.elapsedRealtime() < deadline) {
      AccessibilityNodeInfo root = automation.getRootInActiveWindow();
      if (root != null) {
        boolean found = !root.findAccessibilityNodeInfosByText("Add RGB presets?").isEmpty();
        root.recycle();
        if (found) return;
      }
      Thread.sleep(50);
    }
    throw new Exception("RGB import confirmation did not appear");
  }

  static void click(UiAutomation automation, String text) throws Exception {
    awaitDialog(automation);
    AccessibilityNodeInfo root = automation.getRootInActiveWindow();
    if (root != null) {
      try {
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
          if (text.equalsIgnoreCase(String.valueOf(node.getText()))
              && node.isClickable()
              && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
        }
      } finally {
        root.recycle();
      }
    }
    throw new Exception("Cannot select RGB import confirmation action: " + text);
  }

  private RgbBundleRuntimeChecks() {}
}
