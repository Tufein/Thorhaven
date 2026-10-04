package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Real manual-card dialogs, isolated journals/tasks, vector gestures and encoded PNG output. */
final class GameWorkspaceChecks {
  static boolean rejects(GameLibrary.Action action) {
    try {
      action.run();
      return false;
    } catch (Exception expected) {
      return true;
    }
  }

  static void main(SmokeInstrumentation t, GameLibrary.Action action) throws Exception {
    Exception[] failure = {null};
    t.runOnMainSync(
        () -> {
          try {
            action.run();
          } catch (Exception e) {
            failure[0] = e;
          }
        });
    t.waitForIdleSync();
    if (failure[0] != null) throw failure[0];
  }

  static String repeat(String text, int count) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < count; i++) out.append(text);
    return out.toString();
  }

  static View named(View root, String label) {
    if (root instanceof Button && ((Button) root).getText().toString().equals(label)) return root;
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        View result = named(group.getChildAt(i), label);
        if (result != null) return result;
      }
    }
    return null;
  }

  static List<EditText> inputs(View root) {
    List<EditText> out = new ArrayList<>();
    if (root instanceof EditText) out.add((EditText) root);
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) out.addAll(inputs(group.getChildAt(i)));
    }
    return out;
  }

  static SketchPad.SketchCanvas canvas(View root) {
    if (root instanceof SketchPad.SketchCanvas) return (SketchPad.SketchCanvas) root;
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        SketchPad.SketchCanvas result = canvas(group.getChildAt(i));
        if (result != null) return result;
      }
    }
    return null;
  }

  static void click(View root, String label) throws Exception {
    View button = named(root, label);
    if (button == null) throw new IOException("Missing visible action: " + label);
    button.performClick();
  }

  static void run(SmokeInstrumentation t, Context c, MainActivity a) throws Exception {
    String beforeLibrary = Store.prefs(c).getString(GameLibrary.KEY, null),
        beforeSketch = Store.prefs(c).getString(SketchPad.KEY, null),
        beforeLanguage = Store.prefs(c).getString("language", null),
        beforePending = SketchPad.local(c).getString(SketchPad.PENDING, null);
    String beforeToken = a.sketchExportToken;
    int beforeRequest = a.sketchExportRequest;
    String guide = "qa.game." + GameLibrary.newId();
    List<AlertDialog> dialogs = new ArrayList<>();
    try {
      Store.prefs(c)
          .edit()
          .putString(GameLibrary.KEY, GameLibrary.defaults().toString())
          .putString(SketchPad.KEY, SketchPad.defaults().toString())
          .putString("language", "en")
          .commit();
      SketchPad.cancelExport(c);
      GameLibrary.validate(GameLibrary.defaults().toString());
      SketchPad.validate(SketchPad.defaults().toString());
      t.check(
          GameLibrary.snapshot(c).getJSONArray("games").length() == 0
              && SketchPad.snapshot(c).getJSONArray("pages").length() == 0,
          "New game and sketch models start empty with valid portable defaults");
      library(t, c, guide);
      libraryUi(t, c, a, dialogs);
      sketches(t, c);
      sketchUi(t, c, a, dialogs);
      exportOwnership(t, c, a);
      t.check(
          Store.backup(c).getJSONObject("data").has(GameLibrary.KEY)
              && Store.backup(c).getJSONObject("data").has(SketchPad.KEY),
          "Both new portable collections are included in the real settings backup");
      JSONObject backup = Store.backup(c);
      Store.restore(c, backup.toString(), false);
      t.check(
          backup
                  .getJSONObject("data")
                  .getString(GameLibrary.KEY)
                  .equals(Store.prefs(c).getString(GameLibrary.KEY, ""))
              && backup
                  .getJSONObject("data")
                  .getString(SketchPad.KEY)
                  .equals(Store.prefs(c).getString(SketchPad.KEY, "")),
          "Validated backup retains exact game and sketch model strings");
      JSONObject settingsData = backup.getJSONObject("data");
      t.check(
          !settingsData.has(SketchPad.PENDING) && !settingsData.has(SketchPad.LOCAL_PREFS),
          "Pending PNG destinations and private export snapshots are not portable settings");
    } finally {
      main(
          t,
          () -> {
            for (AlertDialog dialog : dialogs)
              if (dialog != null && dialog.isShowing()) dialog.dismiss();
            a.sketchExportToken = beforeToken;
            a.sketchExportRequest = beforeRequest;
          });
      Store.prefs(c)
          .edit()
          .putString(GameLibrary.KEY, beforeLibrary)
          .putString(SketchPad.KEY, beforeSketch)
          .putString("language", beforeLanguage)
          .commit();
      SketchPad.local(c).edit().putString(SketchPad.PENDING, beforePending).commit();
      OfflineGuides.remove(c, guide);
    }
  }

  static void library(SmokeInstrumentation t, Context c, String guide) throws Exception {
    byte[] original = "Manual 日本語\nKeep original bytes".getBytes(StandardCharsets.UTF_8);
    OfflineGuides.importStream(c, guide, "Original 日本語.txt", new ByteArrayInputStream(original));
    String first =
        GameLibrary.add(
            c,
            "Links 日本語",
            "com.example.emulator",
            "playing",
            true,
            GameLibrary.tags("RPG, puzzle"),
            guide);
    String second =
        GameLibrary.add(
            c, "Another game", "com.example.emulator", "backlog", false, new JSONArray(), "");
    String note = GameLibrary.addJournal(c, first, "Chest in north cave\nReturn later 日本語"),
        task = GameLibrary.addTask(c, first, "Return to the chest");
    t.check(
        GameLibrary.game(c, first).getJSONArray("journal").length() == 1
            && GameLibrary.game(c, second).getJSONArray("journal").length() == 0
            && GameLibrary.game(c, second).getJSONArray("tasks").length() == 0,
        "Two games using one emulator keep journals and checklists independent");
    t.check(
        GameLibrary.game(c, first).getString("title").equals("Links 日本語")
            && GameLibrary.find(GameLibrary.game(c, first).getJSONArray("journal"), note)
                .getString("text")
                .equals("Chest in north cave\nReturn later 日本語"),
        "Game titles and multiline Unicode journal text remain untranslated and intact");
    JSONObject stale = GameLibrary.game(c, first);
    GameLibrary.addJournal(c, first, "Newer note created while editor is open");
    GameLibrary.entry(c, first, "tasks", task, null, true, false);
    GameLibrary.edit(
        c,
        first,
        "Edited 日本語",
        stale.getString("app"),
        "paused",
        false,
        stale.getJSONArray("tags"),
        stale.getString("guide"));
    t.check(
        GameLibrary.game(c, first).getJSONArray("journal").length() == 2
            && GameLibrary.find(GameLibrary.game(c, first).getJSONArray("tasks"), task)
                .getBoolean("done"),
        "Saving stale card fields reloads the latest journal and completed tasks");
    long time =
        GameLibrary.find(GameLibrary.game(c, first).getJSONArray("journal"), note).getLong("at");
    GameLibrary.entry(c, first, "journal", note, "Edited note 日本語", null, false);
    t.check(
        GameLibrary.find(GameLibrary.game(c, first).getJSONArray("journal"), note).getLong("at")
            == time,
        "Editing an existing journal entry retains its original timestamp");
    t.check(
        GameLibrary.filter(GameLibrary.snapshot(c), "PUZZLE", "paused", false).size() == 1
            && GameLibrary.filter(GameLibrary.snapshot(c), "COM.EXAMPLE", "", false).size() == 2
            && GameLibrary.filter(GameLibrary.snapshot(c), "", "playing", false).isEmpty(),
        "Library search finds labels and package names case-insensitively and respects status"
            + " filters");
    t.check(
        GameLibrary.filter(GameLibrary.snapshot(c), "", "", true).isEmpty(),
        "Favorites filter uses the current saved flag after a card edit");
    JSONObject root = GameLibrary.snapshot(c);
    t.check(
        rejects(() -> GameLibrary.validate(GameLibrary.copy(root).put("extra", true).toString()))
            && rejects(() -> GameLibrary.validate(root.toString() + " false"))
            && rejects(() -> GameLibrary.validate("{\"schema\":1,\"schema\":1,\"games\":[]}"))
            && rejects(() -> GameLibrary.validate("{'schema':1,'games':[]}")),
        "Library imports reject unknown fields, trailing data, duplicate JSON keys and relaxed"
            + " syntax");
    t.check(
        rejects(
                () ->
                    GameLibrary.validate(root.toString().replace("\"schema\":1", "\"schema\":1.0")))
            && rejects(
                () -> GameLibrary.validate(GameLibrary.copy(root).put("schema", "1").toString())),
        "Library schema rejects decimal and string coercion");
    JSONObject bad = GameLibrary.copy(root);
    bad.getJSONArray("games").getJSONObject(0).put("favorite", "true");
    JSONObject badId = GameLibrary.copy(root);
    badId.getJSONArray("games").getJSONObject(0).put("id", "../escape");
    t.check(
        rejects(() -> GameLibrary.validate(bad.toString()))
            && rejects(() -> GameLibrary.validate(badId.toString())),
        "Library rejects coerced switches and unsafe local IDs");
    JSONObject duplicates = GameLibrary.copy(root);
    duplicates.getJSONArray("games").getJSONObject(1).put("id", first);
    t.check(
        rejects(() -> GameLibrary.validate(duplicates.toString())),
        "Duplicate game identities cannot be imported");
    JSONObject duplicateNote = GameLibrary.copy(root);
    duplicateNote
        .getJSONArray("games")
        .getJSONObject(0)
        .getJSONArray("journal")
        .getJSONObject(0)
        .put("id", first);
    t.check(
        rejects(() -> GameLibrary.validate(duplicateNote.toString())),
        "Journal and task IDs cannot collide with another entry identity");
    JSONObject unknownEntry = GameLibrary.copy(root);
    unknownEntry
        .getJSONArray("games")
        .getJSONObject(0)
        .getJSONArray("tasks")
        .getJSONObject(0)
        .put("path", "/private");
    t.check(
        rejects(() -> GameLibrary.validate(unknownEntry.toString())),
        "Nested checklist entries reject unknown fields");
    String unchanged = Store.prefs(c).getString(GameLibrary.KEY, "");
    t.check(
        rejects(
                () ->
                    GameLibrary.edit(
                        c,
                        first,
                        "",
                        "com.example.emulator",
                        "playing",
                        false,
                        new JSONArray(),
                        ""))
            && unchanged.equals(Store.prefs(c).getString(GameLibrary.KEY, "")),
        "Rejected blank-title edits leave the entire saved library unchanged");
    t.check(
        rejects(
                () ->
                    GameLibrary.add(
                        c, "Bad package", "content://rom", "backlog", false, new JSONArray(), ""))
            && rejects(
                () ->
                    GameLibrary.add(
                        c, "Bad tags", "", "backlog", false, GameLibrary.tags("RPG, rpg"), ""))
            && rejects(
                () -> GameLibrary.add(c, "Bad status", "", "unknown", false, new JSONArray(), ""))
            && unchanged.equals(Store.prefs(c).getString(GameLibrary.KEY, "")),
        "Invalid packages, duplicate labels and unsupported states fail transactionally");
    t.check(
        rejects(
                () ->
                    GameLibrary.update(
                        c,
                        latest -> {
                          latest.put("games", new JSONArray());
                          throw new IOException("Injected aborted edit");
                        }))
            && unchanged.equals(Store.prefs(c).getString(GameLibrary.KEY, "")),
        "An exception after mutating a detached collection cannot partially save it");
    JSONObject huge = GameLibrary.defaults();
    JSONObject hugeGame = GameLibrary.copy(root.getJSONArray("games").getJSONObject(0));
    hugeGame.put("journal", new JSONArray()).put("tasks", new JSONArray());
    huge.getJSONArray("games").put(hugeGame);
    for (int i = 0; i < 60; i++)
      hugeGame
          .getJSONArray("journal")
          .put(
              new JSONObject()
                  .put("id", GameLibrary.newId())
                  .put("text", repeat("日", 2000))
                  .put("at", hugeGame.getLong("created")));
    String hugeRaw = huge.toString();
    t.check(
        hugeRaw.length() < GameLibrary.MAX_BYTES
            && hugeRaw.getBytes(StandardCharsets.UTF_8).length > GameLibrary.MAX_BYTES
            && rejects(() -> GameLibrary.validate(hugeRaw)),
        "Library aggregate limit measures UTF-8 bytes rather than character count");
    JSONObject tooMany = GameLibrary.defaults();
    for (int i = 0; i <= GameLibrary.MAX_GAMES; i++) {
      JSONObject clone =
          GameLibrary.copy(hugeGame).put("id", GameLibrary.newId()).put("journal", new JSONArray());
      tooMany.getJSONArray("games").put(clone);
    }
    t.check(
        rejects(() -> GameLibrary.validate(tooMany.toString())),
        "Library rejects collections above the 200-card limit");
    Store.prefs(c).edit().putString(GameLibrary.KEY, "{broken").commit();
    t.check(
        rejects(() -> GameLibrary.addTask(c, first, "Do not overwrite"))
            && Store.prefs(c).getString(GameLibrary.KEY, "").equals("{broken"),
        "Unreadable saved library data is reported without replacing it with empty defaults");
    Store.prefs(c).edit().putString(GameLibrary.KEY, unchanged).commit();
    GameLibrary.entry(c, first, "tasks", task, null, null, true);
    t.check(
        GameLibrary.game(c, first).getJSONArray("tasks").length() == 0
            && GameLibrary.game(c, first).getJSONArray("journal").length() == 2,
        "Deleting one checklist entry keeps journal entries intact");
    GameLibrary.remove(c, first);
    t.check(
        GameLibrary.snapshot(c).getJSONArray("games").length() == 1
            && Arrays.equals(OfflineGuides.read(OfflineGuides.file(c, guide)), original),
        "Deleting a game card keeps other cards and the linked original document unchanged");
    t.check(
        rejects(() -> GameLibrary.entry(c, first, "tasks", task, null, true, false)),
        "Actions from a deleted game card cannot recreate orphan journal or checklist data");
  }

  static void libraryUi(
      SmokeInstrumentation t, Context c, MainActivity a, List<AlertDialog> dialogs)
      throws Exception {
    LinearLayout[] page = {null};
    AlertDialog[] editor = {null}, noteEditor = {null};
    main(
        t,
        () -> {
          page[0] = Ui.col(a);
          GameLibrary.page(a, page[0]);
          AlertDialog host =
              new AlertDialog.Builder(a)
                  .setTitle("Game library QA")
                  .setView(GameLibrary.scroll(a, page[0]))
                  .show();
          dialogs.add(host);
          editor[0] = GameLibrary.editor(a, null, () -> GameLibrary.refreshChildren(page[0]));
          dialogs.add(editor[0]);
          if (editor[0] == null) throw new IOException("No visible game editor");
        });
    main(
        t,
        () -> {
          if (!editor[0].getWindow().getDecorView().isShown())
            throw new IOException("Game editor did not render");
          inputs(editor[0].getWindow().getDecorView()).get(0).setText("Visible 日本語 game");
          editor[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
    JSONObject root = GameLibrary.snapshot(c);
    String gameId =
        root.getJSONArray("games")
            .getJSONObject(root.getJSONArray("games").length() - 1)
            .getString("id");
    t.check(
        GameLibrary.game(c, gameId).getString("title").equals("Visible 日本語 game")
            && !editor[0].isShowing(),
        "Visible English add-card dialog commits the exact Unicode name and closes after Save");
    main(
        t,
        () -> {
          editor[0] = GameLibrary.editor(a, gameId, () -> GameLibrary.refreshChildren(page[0]));
          dialogs.add(editor[0]);
        });
    main(
        t,
        () -> {
          inputs(editor[0].getWindow().getDecorView()).get(0).setText("");
          editor[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
    t.check(
        editor[0].isShowing()
            && GameLibrary.game(c, gameId).getString("title").equals("Visible 日本語 game"),
        "Invalid visible card Save keeps the editor open and preserves prior data");
    GameLibrary.addJournal(c, gameId, "Arrived while editor was open");
    main(
        t,
        () -> {
          inputs(editor[0].getWindow().getDecorView()).get(0).setText("Visible edited 日本語");
          editor[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick();
          noteEditor[0] = GameLibrary.noteEditor(a, gameId, "journal", null, () -> {});
          dialogs.add(noteEditor[0]);
        });
    main(
        t,
        () -> {
          if (noteEditor[0] == null || !noteEditor[0].getWindow().getDecorView().isShown())
            throw new IOException("Journal editor did not render");
          inputs(noteEditor[0].getWindow().getDecorView()).get(0).setText("Visible note\n日本語");
          noteEditor[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
    t.check(
        GameLibrary.game(c, gameId).getString("title").equals("Visible edited 日本語")
            && GameLibrary.game(c, gameId).getJSONArray("journal").length() == 2,
        "Visible card and journal editing preserves an entry added after the editor opened");
    main(
        t,
        () -> {
          AlertDialog cancel =
              GameLibrary.confirm(
                  a, "Spel verwijderen?", "Delete game?", () -> GameLibrary.remove(a, gameId));
          dialogs.add(cancel);
          cancel.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        });
    t.check(
        GameLibrary.game(c, gameId).getJSONArray("journal").length() == 2,
        "Canceling the visible deletion confirmation retains the card and its journal");
    main(
        t,
        () -> {
          AlertDialog deletion =
              GameLibrary.confirm(
                  a,
                  "Spel verwijderen?",
                  "Delete game?",
                  () -> {
                    GameLibrary.remove(a, gameId);
                    GameLibrary.refreshChildren(page[0]);
                  });
          dialogs.add(deletion);
          deletion.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
    t.check(
        rejects(() -> GameLibrary.game(c, gameId)),
        "Confirmed visible card deletion removes exactly the selected game");
    main(
        t,
        () -> {
          Store.prefs(c).edit().putString("language", "nl").commit();
          editor[0] = GameLibrary.editor(a, null, () -> {});
          dialogs.add(editor[0]);
        });
    t.check(
        editor[0].getButton(AlertDialog.BUTTON_POSITIVE).getText().toString().equals("Opslaan"),
        "Dutch game editor exposes localized actions without translating user-entered titles");
    main(
        t,
        () -> {
          editor[0].dismiss();
          Store.prefs(c).edit().putString("language", "en").commit();
          for (AlertDialog dialog : dialogs)
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
        });
  }

  static JSONObject line(int color) throws Exception {
    return new JSONObject()
        .put("color", color)
        .put("width", 6)
        .put(
            "points",
            new JSONArray()
                .put(new JSONArray().put(0).put(5000))
                .put(new JSONArray().put(10000).put(5000)));
  }

  static void sketches(SmokeInstrumentation t, Context c) throws Exception {
    String first = SketchPad.add(c, "Map 日本語"), second = SketchPad.add(c, "Puzzle");
    SketchPad.stroke(c, first, line(0xc62828));
    SketchPad.tools(c, first, 0x1565c0, 10, true);
    t.check(
        SketchPad.page(c, first).getJSONArray("strokes").length() == 1
            && SketchPad.page(c, second).getJSONArray("strokes").length() == 0,
        "Separate sketch pages keep drawing strokes independent");
    SketchPad.history(c, first, false);
    t.check(
        SketchPad.page(c, first).getJSONArray("strokes").length() == 0
            && SketchPad.page(c, first).getJSONArray("redo").length() == 1,
        "Undo stores the exact removed stroke in portable redo history");
    JSONObject rebuilt = new JSONObject(Store.prefs(c).getString(SketchPad.KEY, ""));
    SketchPad.validate(rebuilt.toString());
    t.check(
        SketchPad.require(rebuilt, first).getJSONArray("redo").length() == 1
            && SketchPad.require(rebuilt, first).getBoolean("grid")
            && SketchPad.require(rebuilt, first).getInt("width") == 10,
        "Rebuilding the saved model retains undo history, grid and selected drawing tools");
    SketchPad.history(c, first, true);
    t.check(
        SketchPad.page(c, first).getJSONArray("strokes").getJSONObject(0).getInt("color")
            == 0xc62828,
        "Redo restores original stroke color rather than using newly selected tools");
    SketchPad.history(c, first, false);
    SketchPad.stroke(c, first, line(0x2e7d32));
    t.check(
        SketchPad.page(c, first).getJSONArray("redo").length() == 0,
        "A new stroke after Undo clears stale redo history");
    String unchanged = Store.prefs(c).getString(SketchPad.KEY, "");
    t.check(
        rejects(() -> SketchPad.stroke(c, first, line(0xff0000).put("path", "/private")))
            && rejects(() -> SketchPad.stroke(c, first, line(0xff0000).put("color", -1)))
            && rejects(() -> SketchPad.stroke(c, first, line(0xff0000).put("width", "3")))
            && unchanged.equals(Store.prefs(c).getString(SketchPad.KEY, "")),
        "Unknown stroke fields, invalid colors and coerced widths leave the sketch book unchanged");
    JSONObject invalid = line(0);
    invalid.getJSONArray("points").getJSONArray(0).put(0, 0.5);
    JSONObject outside = line(0);
    outside.getJSONArray("points").getJSONArray(1).put(0, 10001);
    t.check(
        rejects(() -> SketchPad.stroke(c, first, invalid))
            && rejects(() -> SketchPad.stroke(c, first, outside)),
        "Sketch coordinates reject fractional values and points beyond normalized canvas bounds");
    JSONObject root = SketchPad.snapshot(c);
    t.check(
        rejects(() -> SketchPad.validate(root.toString() + "{}"))
            && rejects(
                () ->
                    SketchPad.validate("{\"schema\":1,\"schema\":1,\"active\":\"\",\"pages\":[]}"))
            && rejects(
                () ->
                    SketchPad.validate(
                        GameLibrary.copy(root).put("active", GameLibrary.newId()).toString())),
        "Sketch imports reject trailing data, duplicate JSON keys and missing active-page"
            + " references");
    JSONObject duplicate = GameLibrary.copy(root);
    duplicate.getJSONArray("pages").getJSONObject(1).put("id", first);
    t.check(
        rejects(() -> SketchPad.validate(duplicate.toString())),
        "Sketch pages cannot import duplicate stable identities");
    JSONObject overPoints = SketchPad.defaults();
    JSONObject page =
        GameLibrary.copy(SketchPad.page(c, first))
            .put("strokes", new JSONArray())
            .put("redo", new JSONArray());
    overPoints.put("active", first).getJSONArray("pages").put(page);
    for (int i = 0; i < 12; i++) {
      JSONArray points = new JSONArray();
      for (int j = 0; j < 501; j++) points.put(new JSONArray().put(j).put(j));
      page.getJSONArray("strokes")
          .put(new JSONObject().put("color", 0).put("width", 1).put("points", points));
    }
    t.check(
        rejects(() -> SketchPad.validate(overPoints.toString())),
        "Total stroke points are capped at 6000 across all pages and undo histories");
    String byteOverflow =
        "{\"schema\":1,\"active\":\"\",\"pages\":[],\"extra\":\"" + repeat("日", 90000) + "\"}";
    t.check(
        byteOverflow.length() < SketchPad.MAX_BYTES
            && byteOverflow.getBytes(StandardCharsets.UTF_8).length > SketchPad.MAX_BYTES
            && rejects(() -> SketchPad.validate(byteOverflow)),
        "Sketch import byte limit also rejects multibyte oversized input before parsing");
    Bitmap image = SketchPad.render(SketchPad.page(c, first), 300, 200);
    try {
      t.check(
          image.getPixel(150, 100) == (0xff000000 | 0x2e7d32)
              && image.getPixel(5, 5) == Color.rgb(246, 248, 250),
          "Actual Android renderer paints the expected stored stroke and opaque canvas background");
    } finally {
      image.recycle();
    }
    JSONObject circlePage =
        GameLibrary.copy(SketchPad.page(c, first))
            .put("grid", false)
            .put("strokes", new JSONArray());
    JSONArray circlePoints = new JSONArray();
    for (int i = 0; i <= 64; i++) {
      double angle = i * Math.PI * 2 / 64;
      circlePoints.put(
          new JSONArray()
              .put((int) Math.round(5000 + 1000 * Math.cos(angle)))
              .put((int) Math.round(5000 + 1500 * Math.sin(angle))));
    }
    circlePage
        .getJSONArray("strokes")
        .put(new JSONObject().put("color", 0).put("width", 1).put("points", circlePoints));
    Bitmap circle = SketchPad.render(circlePage, SketchPad.PNG_WIDTH, SketchPad.PNG_HEIGHT);
    try {
      int[] bounds = inkBounds(circle);
      t.check(
          Math.abs((bounds[2] - bounds[0]) - (bounds[3] - bounds[1])) <= 2,
          "A circle drawn on the 3:2 canvas remains circular in the 1200 by 800 PNG");
    } finally {
      circle.recycle();
    }
    t.check(
        rejects(() -> SketchPad.render(SketchPad.page(c, first), 10000, 10000)),
        "PNG rendering rejects excessive bitmap dimensions before allocation");
    SketchPad.select(c, first);
    SketchPad.remove(c, first);
    t.check(
        SketchPad.snapshot(c).getString("active").equals(second)
            && SketchPad.snapshot(c).getJSONArray("pages").length() == 1,
        "Deleting the active sketch chooses an existing page without orphaning selection state");
    SketchPad.clear(c, second);
  }

  static void event(SketchPad.SketchCanvas canvas, int action, float x, float y) {
    long time = SystemClock.uptimeMillis();
    MotionEvent event = MotionEvent.obtain(time, time, action, x, y, 0);
    try {
      canvas.onTouchEvent(event);
    } finally {
      event.recycle();
    }
  }

  static void sketchUi(SmokeInstrumentation t, Context c, MainActivity a, List<AlertDialog> dialogs)
      throws Exception {
    String pageId = SketchPad.add(c, "Visible map 日本語");
    SketchPad.SketchCanvas[] drawing = {null};
    LinearLayout[] page = {null};
    main(
        t,
        () -> {
          page[0] = Ui.col(a);
          SketchPad.page(a, page[0]);
          AlertDialog host =
              new AlertDialog.Builder(a)
                  .setTitle("Sketch pad QA")
                  .setView(GameLibrary.scroll(a, page[0]))
                  .show();
          dialogs.add(host);
          drawing[0] = canvas(page[0]);
          if (drawing[0] == null) throw new IOException("No visible sketch canvas");
        });
    main(
        t,
        () -> {
          if (!drawing[0].isShown() || drawing[0].getWidth() < 1 || drawing[0].getHeight() < 1)
            throw new IOException("Sketch canvas did not render");
          if (Math.abs(drawing[0].getMeasuredWidth() * 2 - drawing[0].getMeasuredHeight() * 3) > 1)
            throw new IOException("Visible canvas does not preserve the PNG aspect ratio");
          drawing[0].layout(0, 0, 300, 200);
          event(drawing[0], MotionEvent.ACTION_DOWN, 30, 50);
          event(drawing[0], MotionEvent.ACTION_MOVE, 270, 50);
          event(drawing[0], MotionEvent.ACTION_UP, 270, 50);
        });
    t.check(
        SketchPad.page(c, pageId).getJSONArray("strokes").length() == 1,
        "Real visible Canvas DOWN/MOVE/UP gestures save a bounded vector stroke");
    main(
        t,
        () -> {
          drawing[0].layout(0, 0, 300, 200);
          event(drawing[0], MotionEvent.ACTION_DOWN, 40, 120);
          event(drawing[0], MotionEvent.ACTION_MOVE, 280, 120);
          event(drawing[0], MotionEvent.ACTION_CANCEL, 280, 120);
        });
    t.check(
        SketchPad.page(c, pageId).getJSONArray("strokes").length() == 1
            && drawing[0].draft == null
            && drawing[0].pointer == -1,
        "ACTION_CANCEL discards the unfinished stroke and releases pointer ownership");
    main(t, () -> click(page[0], "Undo"));
    t.check(
        SketchPad.page(c, pageId).getJSONArray("strokes").length() == 0
            && SketchPad.page(c, pageId).getJSONArray("redo").length() == 1,
        "Visible Undo button moves the actual gesture into persisted redo history");
    main(t, () -> click(page[0], "Redo"));
    t.check(
        SketchPad.page(c, pageId).getJSONArray("strokes").length() == 1,
        "Visible Redo button restores the saved gesture");
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    SketchPad.writePng(SketchPad.page(c, pageId), png);
    byte[] bytes = png.toByteArray();
    Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    try {
      t.check(
          bytes.length > 32
              && bytes[0] == (byte) 137
              && bytes[1] == 80
              && bytes[2] == 78
              && bytes[3] == 71
              && decoded != null
              && decoded.getWidth() == SketchPad.PNG_WIDTH
              && decoded.getHeight() == SketchPad.PNG_HEIGHT,
          "Real PNG encoder produces decodable bytes at the documented 1200 by 800 export size");
      t.check(
          decoded.getPixel(600, 201) == (0xff000000 | SketchPad.COLORS[0])
              && decoded.getPixel(600, 600) == Color.rgb(246, 248, 250),
          "Exported PNG contains the actual visible gesture rather than a blank placeholder");
    } finally {
      if (decoded != null) decoded.recycle();
    }
    main(
        t,
        () -> {
          SketchPad.SketchCanvas recreated = new SketchPad.SketchCanvas(a, pageId, () -> {});
          recreated.layout(0, 0, 300, 200);
          Bitmap viewImage = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888);
          try {
            recreated.draw(new Canvas(viewImage));
            if (viewImage.getPixel(150, 50) != (0xff000000 | SketchPad.COLORS[0]))
              throw new IOException("Recreated canvas lost saved stroke");
          } finally {
            viewImage.recycle();
          }
        });
    t.check(true, "A newly created Android Canvas view renders the previously saved working model");
    main(
        t,
        () -> {
          event(drawing[0], MotionEvent.ACTION_DOWN, 30, 140);
          for (AlertDialog dialog : dialogs)
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
        });
    t.check(
        drawing[0].draft == null && SketchPad.page(c, pageId).getJSONArray("strokes").length() == 1,
        "Removing the visible Canvas cancels an in-progress stroke without saving partial drawing"
            + " data");
    String canceledToken = SketchPad.prepareExport(c, pageId);
    main(
        t,
        () -> {
          a.sketchExportToken = canceledToken;
          a.sketchExportRequest++;
          SketchPad.onActivityResult(a, a.sketchExportRequest, Activity.RESULT_CANCELED, null);
        });
    t.check(
        !SketchPad.local(c).contains(SketchPad.PENDING),
        "Canceled system file picker clears the recreation-safe pending PNG snapshot");
    String abandonedToken = SketchPad.prepareExport(c, pageId);
    Store.prefs(c).edit().putString(SketchPad.KEY, SketchPad.defaults().toString()).commit();
    main(
        t,
        () -> {
          page[0] = Ui.col(a);
          SketchPad.page(a, page[0]);
          AlertDialog host =
              new AlertDialog.Builder(a)
                  .setTitle("Pending PNG recovery QA")
                  .setView(GameLibrary.scroll(a, page[0]))
                  .show();
          dialogs.add(host);
          click(page[0], "Cancel pending PNG export");
        });
    t.check(
        !SketchPad.local(c).contains(SketchPad.PENDING),
        "Abandoned pending export can be canceled visibly even when no sketch pages remain");
    main(
        t,
        () -> {
          a.sketchExportToken = abandonedToken;
          a.sketchExportRequest++;
          SketchPad.onActivityResult(
              a,
              a.sketchExportRequest,
              Activity.RESULT_OK,
              new Intent().setData(Uri.parse("content://unused")));
        });
    t.check(
        SketchPad.snapshot(c).getJSONArray("pages").length() == 0,
        "A later result from the canceled export cannot recreate or export an old sketch snapshot");
    SketchPad.add(c, "Backup sketch 日本語");
  }

  static int[] inkBounds(Bitmap bitmap) {
    int left = bitmap.getWidth(), top = bitmap.getHeight(), right = -1, bottom = -1;
    int[] row = new int[bitmap.getWidth()];
    int background = Color.rgb(246, 248, 250);
    for (int y = 0; y < bitmap.getHeight(); y++) {
      bitmap.getPixels(row, 0, row.length, 0, y, row.length, 1);
      for (int x = 0; x < row.length; x++)
        if (row[x] != background) {
          left = Math.min(left, x);
          top = Math.min(top, y);
          right = Math.max(right, x);
          bottom = Math.max(bottom, y);
        }
    }
    return new int[] {left, top, right, bottom};
  }

  static byte[] readPng(Context c, Uri uri) throws Exception {
    try (InputStream in = c.getContentResolver().openInputStream(uri);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      if (in == null) throw new IOException("No exported PNG");
      CompleteBackup.copy(in, out, 8L * 1024 * 1024);
      return out.toByteArray();
    }
  }

  static void exportOwnership(SmokeInstrumentation t, Context c, MainActivity a) throws Exception {
    String pageA = SketchPad.add(c, "Export A"), pageB = SketchPad.add(c, "Export B 日本語");
    SketchPad.stroke(c, pageA, line(0xc62828));
    SketchPad.stroke(c, pageB, line(0x1565c0));
    String tokenA = SketchPad.prepareExport(c, pageA);
    int[] requestA = {0}, requestB = {0};
    main(
        t,
        () -> {
          a.sketchExportToken = tokenA;
          requestA[0] = ++a.sketchExportRequest;
        });
    SketchPad.cancelExport(c);
    String tokenB = SketchPad.prepareExport(c, pageB);
    String pendingB = SketchPad.local(c).getString(SketchPad.PENDING, "");
    t.check(
        SketchPad.consumeExport(c, tokenA) == null
            && pendingB.equals(SketchPad.local(c).getString(SketchPad.PENDING, "")),
        "An earlier Activity's export nonce cannot consume another Activity's new pending"
            + " snapshot");
    StorageToolsChecks.reset(t, c, "normal");
    StorageToolsChecks.grant(t, c, StorageToolsChecks.READ | StorageToolsChecks.WRITE);
    Uri folder = DocumentsContract.buildDocumentUriUsingTree(StorageToolsChecks.TREE, "root");
    Uri
        outputA =
            DocumentsContract.createDocument(
                c.getContentResolver(), folder, "image/png", "Old-export.png"),
        outputB =
            DocumentsContract.createDocument(
                c.getContentResolver(), folder, "image/png", "Current-export.png");
    if (outputA == null || outputB == null)
      throw new IOException("Could not create PNG fixture destinations");
    Bundle saved = new Bundle();
    main(
        t,
        () -> {
          a.sketchExportToken = tokenB;
          requestB[0] = ++a.sketchExportRequest;
          a.onSaveInstanceState(saved);
          SketchPad.onActivityResult(
              a, requestA[0], Activity.RESULT_OK, new Intent().setData(outputA));
        });
    t.check(
        requestA[0] != requestB[0]
            && a.sketchExportToken.equals(tokenB)
            && pendingB.equals(SketchPad.local(c).getString(SketchPad.PENDING, ""))
            && readPng(c, outputA).length == 0,
        "A late result after same-Activity Cancel and retry cannot clear or export the newer"
            + " request");
    t.check(
        saved.getString("sketchExportToken").equals(tokenB)
            && saved.getInt("sketchExportRequest") == requestB[0],
        "Activity saved state records both nonce and unique request ID for pending PNG ownership");
    main(
        t,
        () ->
            SketchPad.onActivityResult(
                a, requestB[0], Activity.RESULT_OK, new Intent().setData(outputB)));
    SketchPad.worker.submit(() -> {}).get(15, java.util.concurrent.TimeUnit.SECONDS);
    byte[] actual = readPng(c, outputB);
    Bitmap result = BitmapFactory.decodeByteArray(actual, 0, actual.length);
    try {
      t.check(
          !SketchPad.local(c).contains(SketchPad.PENDING)
              && a.sketchExportToken.isEmpty()
              && result != null
              && result.getPixel(600, 400) == (0xff000000 | 0x1565c0)
              && readPng(c, outputA).length == 0,
          "The current result writes only its blue snapshot through the real SAF provider and"
              + " leaves the old destination empty");
    } finally {
      if (result != null) result.recycle();
    }
    int requestBeforeLimit = a.sketchExportRequest;
    main(
        t,
        () -> {
          a.sketchExportRequest = SketchPad.REQUEST_LAST;
          SketchPad.beginExport(a, pageB);
        });
    t.check(
        !SketchPad.local(c).contains(SketchPad.PENDING) && a.sketchExportToken.isEmpty(),
        "Exhausting unique request IDs refuses another picker instead of reusing an abandoned"
            + " request code");
    main(t, () -> a.sketchExportRequest = requestBeforeLimit);
    File forbidden = new File(c.getFilesDir(), "qa-sketch-not-a-saf-destination.png");
    if (forbidden.exists() && !forbidden.delete())
      throw new IOException("Could not clear PNG guard fixture");
    t.check(
        rejects(() -> SketchPad.exportPng(c, Uri.fromFile(forbidden), SketchPad.page(c, pageB)))
            && rejects(
                () ->
                    SketchPad.exportPng(
                        c, Uri.parse("https://example.test/sketch.png"), SketchPad.page(c, pageB)))
            && rejects(
                () -> SketchPad.exportPng(c, Uri.parse("content:"), SketchPad.page(c, pageB)))
            && !forbidden.exists(),
        "PNG export refuses file, HTTP and incomplete content destinations before external I/O");
    String invalidToken = SketchPad.prepareExport(c, pageB);
    main(
        t,
        () -> {
          a.sketchExportToken = invalidToken;
          int invalidRequest = ++a.sketchExportRequest;
          SketchPad.onActivityResult(
              a, invalidRequest, Activity.RESULT_OK, new Intent().setData(Uri.fromFile(forbidden)));
        });
    SketchPad.worker.submit(() -> {}).get(15, java.util.concurrent.TimeUnit.SECONDS);
    t.check(
        !SketchPad.local(c).contains(SketchPad.PENDING)
            && a.sketchExportToken.isEmpty()
            && !forbidden.exists()
            && SketchPad.page(c, pageB).getJSONArray("strokes").length() == 1,
        "Invalid-destination results consume only their pending request and preserve the editable"
            + " drawing without creating a file");
    StorageToolsChecks.fixture(t, c, "fixtureRevoke", "root", 0);
  }
}
