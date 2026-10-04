package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.media.AudioManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.json.*;

/** User data, parser boundaries and activity ownership checks shared by API 30 and 35. */
final class V1ReadinessChecks {
  final SmokeInstrumentation t;
  final Context c;

  V1ReadinessChecks(SmokeInstrumentation t, Context c) {
    this.t = t;
    this.c = c;
  }

  interface Action {
    void run() throws Exception;
  }

  boolean rejects(Action action) {
    try {
      action.run();
      return false;
    } catch (Exception expected) {
      return true;
    }
  }

  void run(MainActivity owner) throws Exception {
    String original = Store.backup(c).toString();
    try {
      Store.prefs(c).edit().putString("notes:com.android.settings", "Keep € 日本語").commit();
      String[] malformed = {
        "{\"schema\":1.0,\"data\":{}}",
        "{\"schema\":\"1\",\"data\":{}}",
        "{\"schema\":1,\"data\":{},\"extra\":0}",
        "{\"schema\":1,\"data\":{}} trailing",
        "{schema:1,data:{}}",
        "{\"schema\":1,\"schema\":1,\"data\":{}}",
        "{\"schema\":1,\"data\":{\"top\":1.5}}",
        "{\"schema\":1,\"data\":{\"volume:com.android.settings\":37.9}}",
        "{\"schema\":1,\"data\":{\"keyboardSize\":48.1}}",
        "{\"schema\":1,\"data\":{\"favorite:../../outside\":true}}",
        "{\"schema\":1,\"data\":{\"top\":999999999999999}}",
        "{\"schema\":1,\"data\":{\"top\":01}}"
      };
      for (int i = 0; i < malformed.length; i++) {
        String bad = malformed[i];
        t.check(
            rejects(() -> Store.restore(c, bad))
                && Store.prefs(c).getString("notes:com.android.settings", "").equals("Keep € 日本語"),
            "Malformed settings case " + (i + 1) + " is rejected without changing existing notes");
      }
      t.check(
          rejects(
              () -> SettingsBackup.read(new ByteArrayInputStream(new byte[] {(byte) 0xc3, 0x28}))),
          "Invalid UTF-8 backup bytes are rejected");
      t.check(
          rejects(
              () ->
                  SettingsBackup.read(
                      new ByteArrayInputStream(new byte[SettingsBackup.MAX_BYTES + 1]))),
          "Settings provider input is bounded before parsing");
      t.check(
          SettingsBackup.read(new ByteArrayInputStream("€ 日本語".getBytes(StandardCharsets.UTF_8)))
              .equals("€ 日本語"),
          "UTF-8 document reading preserves non-ASCII user content");
      t.check(
          rejects(
              () ->
                  CompleteBackup.validateStats(
                      new JSONObject().put("time:com.android.settings", 1.9))),
          "Fractional saved usage measurements are rejected");
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      CompleteBackup.write(c, output);
      byte[] archive = output.toByteArray();
      try (CompleteBackup.Prepared p =
          CompleteBackup.prepare(c, new ByteArrayInputStream(archive))) {
        t.check(
            p.manifest
                .getJSONObject("settings")
                .getJSONObject("data")
                .getString("notes:com.android.settings")
                .equals("Keep € 日本語"),
            "Complete ZIP staging preserves Unicode notes");
      }
      t.check(
          rejects(
              () ->
                  CompleteBackup.prepare(
                      c, new ByteArrayInputStream(Arrays.copyOf(archive, archive.length - 22)))),
          "ZIP missing its end directory is rejected before restore");
      byte[] changed = archive.clone();
      for (int i = 0; i < changed.length - 46; i++)
        if (changed[i] == 0x50
            && changed[i + 1] == 0x4b
            && changed[i + 2] == 1
            && changed[i + 3] == 2) {
          changed[i + 16] ^= 1;
          break;
        }
      t.check(
          rejects(() -> CompleteBackup.prepare(c, new ByteArrayInputStream(changed))),
          "ZIP central checksum mismatch is rejected");
      int volume =
          c.getSystemService(AudioManager.class).getStreamVolume(AudioManager.STREAM_MUSIC);
      final boolean[] launched = {true};
      t.runOnMainSync(
          () ->
              launched[0] = Store.launchChecked(c, "nl.thorhaven.missing", Store.screen(c, false)));
      t.check(
          !launched[0]
              && volume
                  == c.getSystemService(AudioManager.class)
                      .getStreamVolume(AudioManager.STREAM_MUSIC),
          "Unavailable launch has no music-volume side effect");
      String last = Store.prefs(c).getString("last", "");
      JSONObject pair =
          new JSONObject()
              .put("name", "Unavailable pair")
              .put("top", "nl.thorhaven.missing")
              .put("bottom", "com.android.settings");
      t.runOnMainSync(() -> Store.openPair(c, pair));
      Thread.sleep(500);
      t.check(
          last.equals(Store.prefs(c).getString("last", "")),
          "Pair preflight refuses both launches when one app is absent");
      t.check(
          Store.launchIntent(c, "com.android.settings", Integer.MAX_VALUE) == null,
          "Removed or unknown displays cannot launch an app");
      JSONObject cancellablePair =
          new JSONObject()
              .put("name", "Cancelled pair")
              .put("top", c.getPackageName())
              .put("bottom", "com.android.settings");
      t.runOnMainSync(
          () -> {
            Store.openPair(owner, cancellablePair);
            Store.cancelPendingPair(owner);
          });
      Thread.sleep(550);
      t.check(
          Store.prefs(c).getString("last", "").equals("com.android.settings"),
          "Cancelling an owner prevents its delayed upper app launch");
      t.runOnMainSync(
          () -> {
            Store.openPair(owner, cancellablePair);
            Store.openPair(owner, pair);
          });
      Thread.sleep(550);
      t.check(
          Store.prefs(c).getString("last", "").equals("com.android.settings"),
          "A newer unavailable pair supersedes and cancels an older delayed launch");
      t.check(
          rejects(() -> ExtraFeatures.validateGuide(new JSONObject().put("pages", 1.5)))
              && rejects(() -> ExtraFeatures.validateGuide(new JSONObject().put("scroll", "100")))
              && rejects(
                  () ->
                      ExtraFeatures.validateGuide(
                          new JSONObject()
                              .put("pages", 2)
                              .put(
                                  "bookmarks",
                                  new JSONArray()
                                      .put(
                                          new JSONObject()
                                              .put("name", "Fractional")
                                              .put("page", 0.5))))),
          "Guide pages, reading positions and bookmarks reject coerced integers");
      final AlertDialog[] replacement = {null};
      // Pair launch checks can leave the original owner's task behind another application.
      // Exercise the note controls through a fresh foreground window on every SDK.
      MainActivity noteOwner =
          (MainActivity)
              t.startActivitySync(
                  new Intent(c, MainActivity.class)
                      .addFlags(
                          Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
      t.waitForIdleSync();
      t.runOnMainSync(
          () -> {
            noteOwner.notes("com.android.settings");
            noteOwner.notes("com.android.settings");
            replacement[0] = noteOwner.noteDialog;
          });
      t.waitForIdleSync();
      t.check(
          noteOwner.noteDialog == replacement[0] && noteOwner.noteEditor != null,
          "An older note-dialog dismissal cannot clear a replacement editor");
      draftRecreation(noteOwner);
      t.runOnMainSync(() -> noteOwner.notes("com.android.settings"));
      t.check(
          noteOwner.noteDialog == null && noteOwner.noteEditor == null,
          "A destroyed note owner cannot open another dialog or edit saved notes");
    } finally {
      Store.prefs(c).edit().clear().commit();
      Store.restore(c, original);
    }
  }

  void draftRecreation(MainActivity owner) throws Exception {
    String draft = "Unsaved draft € 日本語 · maps";
    t.runOnMainSync(
        () -> {
          owner.notes("com.android.settings");
          owner.noteEditor.setText(draft);
        });
    Instrumentation.ActivityMonitor monitor =
        t.addMonitor(MainActivity.class.getName(), null, false);
    t.runOnMainSync(owner::recreate);
    Activity recreated = monitor.waitForActivityWithTimeout(10000);
    t.removeMonitor(monitor);
    t.waitForIdleSync();
    t.check(recreated instanceof MainActivity, "Notes editor activity recreates successfully");
    MainActivity fresh = (MainActivity) recreated;
    final boolean[] restored = {false};
    t.runOnMainSync(
        () ->
            restored[0] =
                fresh.noteEditor != null && draft.equals(fresh.noteEditor.getText().toString()));
    t.check(restored[0], "Unsaved Unicode note draft is restored after activity recreation");
    t.check(
        Store.prefs(c).getString("notes:com.android.settings", "").equals("Keep € 日本語"),
        "Restoring a draft does not silently overwrite the saved note");
    t.runOnMainSync(fresh::finish);
  }
}
