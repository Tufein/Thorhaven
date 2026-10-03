package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.*;

final class V4FeatureChecks {
  final SmokeInstrumentation test;
  final Context c;
  final MainActivity a;

  V4FeatureChecks(SmokeInstrumentation t, Context c, MainActivity a) {
    test = t;
    this.c = c;
    this.a = a;
  }

  void check(boolean b, String s) throws Exception {
    test.check(b, s);
  }

  void run() throws Exception {
    Store.prefs(c).edit().putString("language", "en").commit();
    check(
        Language.text(c, "Overzicht").equals("Overview")
            && Language.text(c, "Offline gids openen").equals("Open offline guide"),
        "English catalog translates navigation and guide actions");
    final String[] dynamic = {""};
    test.runOnMainSync(
        () -> {
          TextView label = Ui.text(c, "Notities", 16, Ui.TEXT);
          label.setText("Profiel opgeslagen");
          dynamic[0] = label.getText().toString();
        });
    check(dynamic[0].equals("Profile saved"), "Dynamic status labels use the selected language");
    String userText = "Notities · een Nederlandse gids";
    final String[] raw = {""};
    test.runOnMainSync(() -> raw[0] = Ui.rawText(c, userText, 16, Ui.TEXT).getText().toString());
    check(raw[0].equals(userText), "User guide content is not translated");
    JSONObject binds = Shortcuts.defaults();
    binds.put("96", new JSONObject().put("action", "appBottom").put("pkg", "com.android.settings"));
    Shortcuts.validate(binds.toString());
    Store.prefs(c).edit().putString("shortcuts", binds.toString()).commit();
    String backup = Store.backup(c).toString();
    Store.prefs(c).edit().remove("shortcuts").remove("language").commit();
    Store.restore(c, backup);
    check(
        Shortcuts.binding(c, 96).getString("pkg").equals("com.android.settings")
            && Language.isEnglish(c),
        "Custom shortcuts and language survive settings backup");
    boolean rejected = false;
    try {
      Shortcuts.validate("{\"3\":{\"action\":\"panel\"}}");
    } catch (Exception e) {
      rejected = true;
    }
    check(rejected, "Shortcut editor cannot intercept Home or arbitrary keys");
    rejected = false;
    try {
      Shortcuts.validate("{\"96\":{\"action\":\"appTop\",\"pkg\":\"bad;command\"}}");
    } catch (Exception e) {
      rejected = true;
    }
    check(rejected, "Shortcut package targets are validated before execution");
    ThorService service = ThorService.instance;
    final boolean[] actionChecks = new boolean[3];
    test.runOnMainSync(
        () -> {
          try {
            JSONObject configured = Shortcuts.defaults();
            configured.put("96", new JSONObject().put("action", "panel"));
            configured.put("97", new JSONObject().put("action", "disabled"));
            Store.prefs(c)
                .edit()
                .putString("shortcuts", configured.toString())
                .putBoolean("combos", true)
                .commit();
            service.ownFocused = false;
            service.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_SELECT));
            actionChecks[0] =
                service.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A))
                    && service.panel != null;
            actionChecks[1] =
                service.onKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A));
            actionChecks[2] =
                !service.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B));
            service.onKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_SELECT));
            service.hidePanel();
            Store.prefs(c).edit().putBoolean("combos", false).commit();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    check(
        actionChecks[0],
        "Custom Select+A binding opens the real quick panel instead of the default guide action");
    check(actionChecks[1], "Executed custom shortcuts consume the matching key release");
    check(actionChecks[2], "A disabled custom action passes its second button through to the game");
    String pkg = "example.v4guide";
    OfflineGuides.importStream(
        c,
        pkg,
        "Sample €",
        new ByteArrayInputStream("Notities — 日本語".getBytes(StandardCharsets.UTF_8)));
    OfflineGuides.remember(c, pkg, "scroll", 123);
    Store.prefs(c).edit().putString("notes:" + pkg, "Saved note").commit();
    c.getSharedPreferences("thorhaven-stats", 0)
        .edit()
        .putLong("time:" + pkg, 123456)
        .putLong("sleep:start", 999)
        .commit();
    ByteArrayOutputStream zip = new ByteArrayOutputStream();
    CompleteBackup.write(c, zip);
    OfflineGuides.importStream(
        c, pkg, "Changed", new ByteArrayInputStream("changed".getBytes(StandardCharsets.UTF_8)));
    Store.prefs(c).edit().putString("notes:" + pkg, "changed").commit();
    c.getSharedPreferences("thorhaven-stats", 0).edit().putLong("time:" + pkg, 0).commit();
    try (CompleteBackup.Prepared p =
        CompleteBackup.prepare(c, new ByteArrayInputStream(zip.toByteArray()))) {
      CompleteBackup.restore(c, p);
    }
    check(
        new String(OfflineGuides.read(OfflineGuides.file(c, pkg)), StandardCharsets.UTF_8)
                .equals("Notities — 日本語")
            && OfflineGuides.meta(c, pkg).getInt("scroll") == 123,
        "Complete ZIP backup restores guide bytes and reading position");
    check(
        Store.prefs(c).getString("notes:" + pkg, "").equals("Saved note")
            && c.getSharedPreferences("thorhaven-stats", 0).getLong("time:" + pkg, 0) == 123456,
        "Complete ZIP backup restores notes and typed app-time values");
    check(
        !c.getSharedPreferences("thorhaven-stats", 0).contains("sleep:start")
            && !CompleteBackup.journal(c).exists(),
        "Completed restore excludes unfinished sleep state and clears its recovery journal");
    OfflineGuides.importStream(
        c,
        pkg,
        "Recovery baseline",
        new ByteArrayInputStream("baseline guide".getBytes(StandardCharsets.UTF_8)));
    Store.prefs(c).edit().putString("notes:" + pkg, "baseline note").commit();
    rejected = false;
    try (CompleteBackup.Prepared p =
        CompleteBackup.prepare(c, new ByteArrayInputStream(zip.toByteArray()))) {
      p.manifest.getJSONObject("settings").getJSONObject("data").put("volume:" + pkg, 999);
      try {
        CompleteBackup.restore(c, p);
      } catch (Exception expected) {
        rejected = true;
      }
    }
    check(
        rejected
            && new String(OfflineGuides.read(OfflineGuides.file(c, pkg)), StandardCharsets.UTF_8)
                .equals("baseline guide")
            && Store.prefs(c).getString("notes:" + pkg, "").equals("baseline note"),
        "A late transaction failure restores prior guide files and preference values");
    try (CompleteBackup.Prepared p =
        CompleteBackup.prepare(c, new ByteArrayInputStream(zip.toByteArray()))) {
      CompleteBackup.restore(c, p);
    }
    ByteArrayOutputStream unsafe = new ByteArrayOutputStream();
    try (ZipOutputStream out = new ZipOutputStream(unsafe)) {
      out.putNextEntry(new ZipEntry("../escape"));
      out.write(1);
      out.closeEntry();
    }
    rejected = false;
    try {
      CompleteBackup.prepare(c, new ByteArrayInputStream(unsafe.toByteArray()));
    } catch (Exception e) {
      rejected = true;
    }
    check(
        rejected && OfflineGuides.meta(c, pkg).getInt("scroll") == 123,
        "Unsafe ZIP paths are rejected without replacing existing guides");
    JSONObject badStats =
        new JSONObject()
            .put(
                "sleeps",
                "[{\"duration\":10800000,\"drop\":5,\"charged\":true,\"ended\":1,\"eligible\":true}]");
    rejected = false;
    try {
      CompleteBackup.validateStats(badStats);
    } catch (Exception e) {
      rejected = true;
    }
    check(rejected, "Imported battery averages cannot include charging sessions");
    test.runOnMainSync(
        () -> {
          Store.prefs(c).edit().putBoolean("guideNotes", true).commit();
          GuidePane pane = new GuidePane(a, pkg, () -> {});
          try {
            if (pane.notes == null) throw new IllegalStateException("missing notes");
            pane.notes.setText("Updated beside guide");
            pane.saveNotes.run();
          } finally {
            pane.close();
          }
        });
    check(
        Store.prefs(c).getString("notes:" + pkg, "").equals("Updated beside guide"),
        "Side-by-side guide editor saves notes for the correct app");
    final boolean[] keyboardChecks = new boolean[3];
    test.runOnMainSync(
        () -> {
          class Keyboard extends ThorKeyboard {
            final List<KeyEvent> cursorEvents = new ArrayList<>();
            final StringBuilder typed = new StringBuilder();

            Keyboard() {
              attachBaseContext(c);
            }

            public InputConnection getCurrentInputConnection() {
              return new BaseInputConnection(new EditText(c), false) {
                public boolean commitText(CharSequence value, int pos) {
                  typed.append(value);
                  return true;
                }

                public boolean sendKeyEvent(KeyEvent e) {
                  cursorEvents.add(e);
                  return true;
                }
              };
            }
          }
          Keyboard k = new Keyboard();
          k.symbols = true;
          View view = k.keyboard();
          keyboardChecks[0] =
              k.keys.stream().anyMatch(b -> b.getText().toString().equals("<"))
                  && k.keys.stream().anyMatch(b -> b.getText().toString().equals("€"));
          k.keys.stream()
              .filter(b -> b.getText().toString().equals("<"))
              .findFirst()
              .get()
              .performClick();
          k.cursor(-1);
          keyboardChecks[1] =
              k.typed.toString().equals("<")
                  && k.cursorEvents.size() == 2
                  && k.cursorEvents.get(1).getAction() == KeyEvent.ACTION_UP;
          for (int i = 0; i < k.keys.size(); i++) {
            k.selected = i;
            k.vertical(1);
            if (k.selected < 0 || k.selected >= k.keys.size())
              throw new IllegalStateException("navigation");
            k.vertical(-1);
          }
          Store.prefs(c).edit().putInt("keyboardSize", 64).commit();
          k.keyboard();
          keyboardChecks[2] = k.keys.get(0).getLayoutParams().height == Ui.dp(c, 64);
        });
    check(keyboardChecks[0], "Symbol keyboard includes angle brackets and currency characters");
    check(keyboardChecks[1], "Keyboard commits symbols and sends paired cursor key events");
    check(
        keyboardChecks[2], "Keyboard size preference and every-row navigation stay within bounds");
    Store.prefs(c)
        .edit()
        .putString("language", "nl")
        .putBoolean("guideNotes", false)
        .remove("shortcuts")
        .commit();
    OfflineGuides.remove(c, pkg);
    c.getSharedPreferences("thorhaven-stats", 0).edit().remove("time:" + pkg).commit();
  }
}
