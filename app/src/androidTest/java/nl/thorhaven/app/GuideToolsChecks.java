package nl.thorhaven.app;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Real files, metadata transactions, exported bytes and rendered reader controls for 0.6. */
final class GuideToolsChecks {
  interface Attempt {
    void run() throws Exception;
  }

  static boolean rejects(Attempt action) {
    try {
      action.run();
      return false;
    } catch (Exception expected) {
      return true;
    }
  }

  static void run(SmokeInstrumentation t, Context c, MainActivity a) throws Exception {
    String owner = "example.v6.documents",
        textId = owner + ".docText",
        pdfId = owner + ".docPdf",
        imageId = owner + ".docMap";
    boolean notesBefore = Store.prefs(c).getBoolean("guideNotes", false);
    String languageBefore = Store.prefs(c).getString("language", "nl");
    GuidePane[] pane = {null};
    File destination = new File(c.getFilesDir(), "v6-export-check.txt");
    try {
      byte[] textBytes = "Notities — Apple apple 日本語\nSecond line".getBytes(StandardCharsets.UTF_8);
      OfflineGuides.importStream(c, textId, "walkthrough.md", new ByteArrayInputStream(textBytes));
      JSONObject text = OfflineGuides.meta(c, textId).put("owner", owner).put("scroll", 123);
      GuideTools.save(c, textId, text);
      ByteArrayOutputStream pdfBytes = new ByteArrayOutputStream();
      PdfDocument pdfDocument = new PdfDocument();
      try {
        for (int i = 1; i <= 2; i++) {
          PdfDocument.Page p =
              pdfDocument.startPage(new PdfDocument.PageInfo.Builder(200, 120, i).create());
          p.getCanvas().drawColor(i == 1 ? Color.WHITE : Color.LTGRAY);
          pdfDocument.finishPage(p);
        }
        pdfDocument.writeTo(pdfBytes);
      } finally {
        pdfDocument.close();
      }
      OfflineGuides.importStream(
          c, pdfId, "Dungeon.PDF", new ByteArrayInputStream(pdfBytes.toByteArray()));
      JSONObject pdf =
          OfflineGuides.meta(c, pdfId)
              .put("owner", owner)
              .put("page", 1)
              .put(
                  "bookmarks",
                  new JSONArray()
                      .put(new JSONObject().put("name", "Chest").put("page", 0))
                      .put(new JSONObject().put("name", "Exit").put("page", 1)));
      GuideTools.save(c, pdfId, pdf);
      Bitmap map = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888);
      map.eraseColor(Color.GREEN);
      ByteArrayOutputStream png = new ByteArrayOutputStream();
      map.compress(Bitmap.CompressFormat.PNG, 100, png);
      map.recycle();
      OfflineGuides.importStream(
          c, imageId, "Town.png", new ByteArrayInputStream(png.toByteArray()));
      JSONObject image =
          OfflineGuides.meta(c, imageId)
              .put("owner", owner)
              .put(
                  "markers",
                  new JSONArray()
                      .put(new JSONObject().put("name", "Shop").put("x", .25).put("y", .75))
                      .put(new JSONObject().put("name", "Inn").put("x", .5).put("y", .5)));
      GuideTools.save(c, imageId, image);
      GuideTools.rename(c, pdfId, "  Chest map 日本語  ");
      JSONObject renamed = OfflineGuides.meta(c, pdfId);
      t.check(
          renamed.getString("name").equals("Chest map 日本語")
              && renamed.getString("owner").equals(owner)
              && renamed.getInt("page") == 1
              && renamed.getJSONArray("bookmarks").length() == 2,
          "Document rename retains owner, reading position and individual bookmarks");
      t.check(
          Arrays.equals(pdfBytes.toByteArray(), OfflineGuides.read(OfflineGuides.file(c, pdfId))),
          "Document rename preserves the original PDF bytes");
      String baseline = renamed.toString();
      t.check(
          rejects(() -> GuideTools.rename(c, pdfId, "  "))
              && rejects(() -> GuideTools.rename(c, pdfId, "bad\nname"))
              && rejects(
                  () -> GuideTools.rename(c, pdfId, String.join("", Collections.nCopies(181, "x"))))
              && OfflineGuides.meta(c, pdfId).toString().equals(baseline),
          "Blank, control-character and oversized names leave existing document metadata intact");
      List<GuideTools.Entry> entries = GuideTools.entries(c);
      t.check(
          GuideTools.filter(entries, "CHEST MAP", "pdf").size() == 1
              && GuideTools.filter(entries, "documents", "image").size() == 1
              && GuideTools.filter(entries, "CHEST MAP", "text").isEmpty(),
          "Library filtering combines case-insensitive document/app search with document type");
      GuideTools.reading(c, textId, "textSize", 24);
      GuideTools.reading(c, pdfId, "zoom", 150);
      GuideTools.reading(c, imageId, "zoom", 200);
      t.check(
          rejects(() -> GuideTools.reading(c, textId, "textSize", 31))
              && rejects(() -> GuideTools.reading(c, pdfId, "zoom", 101))
              && rejects(() -> GuideTools.reading(c, textId, "unexpected", 20))
              && OfflineGuides.meta(c, textId).getInt("textSize") == 24
              && OfflineGuides.meta(c, pdfId).getInt("zoom") == 150,
          "Reader settings reject out-of-range values and unknown keys without losing prior"
              + " settings");
      JSONObject invalid =
          new JSONObject(OfflineGuides.meta(c, textId).toString()).put("textSize", "20");
      t.check(
          rejects(() -> ExtraFeatures.validateGuide(invalid)),
          "Backup metadata validation rejects reader sizes encoded as strings");
      invalid.put("textSize", 20.5);
      t.check(
          rejects(() -> ExtraFeatures.validateGuide(invalid)),
          "Backup metadata validation rejects fractional text sizes");
      GuideTools.changeEntry(c, pdfId, "bookmarks", 0, "Notities", 1, false);
      t.check(
          OfflineGuides.meta(c, pdfId).getJSONArray("bookmarks").getJSONObject(0).getInt("page")
                  == 1
              && OfflineGuides.meta(c, pdfId)
                  .getJSONArray("bookmarks")
                  .getJSONObject(1)
                  .getString("name")
                  .equals("Exit"),
          "Editing one PDF bookmark preserves the remaining entries");
      String bookmarkBaseline = OfflineGuides.meta(c, pdfId).toString();
      t.check(
          rejects(() -> GuideTools.changeEntry(c, pdfId, "bookmarks", 0, "bad page", 2, false))
              && OfflineGuides.meta(c, pdfId).toString().equals(bookmarkBaseline),
          "Invalid bookmark page cannot replace existing bookmark metadata");
      GuideTools.changeEntry(c, pdfId, "bookmarks", 1, "", null, true);
      t.check(
          OfflineGuides.meta(c, pdfId).getJSONArray("bookmarks").length() == 1,
          "Individual bookmark deletion preserves the selected bookmark");
      GuideTools.changeEntry(c, imageId, "markers", 0, "Notities", null, false);
      JSONObject marker = OfflineGuides.meta(c, imageId).getJSONArray("markers").getJSONObject(0);
      t.check(
          marker.getString("name").equals("Notities")
              && marker.getDouble("x") == .25
              && marker.getDouble("y") == .75,
          "Marker rename retains its original normalized map coordinates");
      GuideTools.changeEntry(c, imageId, "markers", 1, "", null, true);
      t.check(
          OfflineGuides.meta(c, imageId).getJSONArray("markers").length() == 1
              && rejects(
                  () -> GuideTools.changeEntry(c, imageId, "markers", 9, "missing", null, false)),
          "Individual marker deletion keeps unrelated markers and rejects stale indices");
      ByteArrayOutputStream exported = new ByteArrayOutputStream();
      GuideTools.exportDocument(c, textId, exported);
      t.check(
          Arrays.equals(textBytes, exported.toByteArray()),
          "Individual export copies original UTF-8 document bytes exactly");
      t.check(
          rejects(
                  () ->
                      GuideTools.exportDocument(
                          c,
                          textId,
                          new OutputStream() {
                            public void write(int b) throws IOException {
                              throw new IOException("Full destination");
                            }
                          }))
              && Arrays.equals(textBytes, OfflineGuides.read(OfflineGuides.file(c, textId))),
          "Failed export does not alter the original document");
      Intent exportIntent = GuideTools.exportIntent(c, textId);
      Intent mapIntent = GuideTools.exportIntent(c, imageId);
      t.check(
          exportIntent.getAction().equals(Intent.ACTION_CREATE_DOCUMENT)
              && exportIntent.getStringExtra(Intent.EXTRA_TITLE).equals("walkthrough.md")
              && exportIntent.getType().equals("text/markdown")
              && mapIntent.getType().equals("image/png"),
          "Original export uses Android's file picker with matching names and MIME types");
      c.getSharedPreferences("thorhaven-guide-export", 0)
          .edit()
          .putString("pending", textId)
          .commit();
      t.runOnMainSync(() -> GuideTools.onExportResult(a, Activity.RESULT_CANCELED, null));
      t.check(
          !c.getSharedPreferences("thorhaven-guide-export", 0).contains("pending"),
          "Canceling the export picker clears the pending document without writing a file");
      c.getSharedPreferences("thorhaven-guide-export", 0)
          .edit()
          .putString("pending", textId)
          .commit();
      t.runOnMainSync(
          () ->
              GuideTools.onExportResult(
                  a, Activity.RESULT_OK, new Intent().setData(Uri.fromFile(destination))));
      OfflineGuides.worker.submit(() -> {}).get(10, java.util.concurrent.TimeUnit.SECONDS);
      t.check(
          Arrays.equals(textBytes, OfflineGuides.read(destination)),
          "Export result handler writes the selected destination asynchronously");
      ByteArrayOutputStream backup = new ByteArrayOutputStream();
      CompleteBackup.write(c, backup);
      GuideTools.reading(c, textId, "textSize", 12);
      GuideTools.reading(c, pdfId, "zoom", 100);
      GuideTools.rename(c, imageId, "Changed");
      try (CompleteBackup.Prepared p =
          CompleteBackup.prepare(c, new ByteArrayInputStream(backup.toByteArray()))) {
        CompleteBackup.restore(c, p);
      }
      t.check(
          OfflineGuides.meta(c, textId).getInt("textSize") == 24
              && OfflineGuides.meta(c, pdfId).getInt("zoom") == 150
              && OfflineGuides.meta(c, imageId).getString("name").equals("Town.png")
              && OfflineGuides.meta(c, imageId)
                  .getJSONArray("markers")
                  .getJSONObject(0)
                  .getString("name")
                  .equals("Notities"),
          "Complete ZIP backup restores reader preferences, document names and edited markers");
      Store.prefs(c).edit().putBoolean("guideNotes", false).putString("language", "en").commit();
      t.runOnMainSync(
          () -> {
            pane[0] = new GuidePane(a, textId, () -> {});
            a.content.addView(pane[0], new LinearLayout.LayoutParams(-1, 600));
          });
      for (int i = 0; i < 50 && pane[0].documentText.isEmpty(); i++) Thread.sleep(100);
      t.check(
          pane[0].documentText.equals(new String(textBytes, StandardCharsets.UTF_8))
              && Math.round(
                      pane[0].guideText.getTextSize()
                          / c.getResources().getDisplayMetrics().scaledDensity)
                  == 24,
          "Rendered text reader restores document size and keeps user content untranslated");
      t.runOnMainSync(() -> pane[0].find("apple"));
      t.check(
          pane[0]
                  .guideText
                  .getText()
                  .toString()
                  .equals(new String(textBytes, StandardCharsets.UTF_8))
              && pane[0].searchAt > 0,
          "Search highlighting preserves the original document text after scale restoration");
      t.runOnMainSync(
          () -> {
            pane[0].close();
            a.content.removeView(pane[0]);
            pane[0] = new GuidePane(a, pdfId, () -> {});
            a.content.addView(pane[0], new LinearLayout.LayoutParams(-1, 600));
          });
      for (int i = 0; i < 50 && pane[0].bitmap == null; i++) Thread.sleep(100);
      t.check(
          pane[0].bitmap != null && pane[0].zoom == 1.5f && pane[0].page == 1,
          "Rendered PDF reader restores preferred zoom and reading page");
      String[] bookmarkLabel = {""};
      t.runOnMainSync(
          () -> {
            pane[0].bookmarks();
            bookmarkLabel[0] = pane[0].pageDialog.getListView().getAdapter().getItem(0).toString();
            pane[0].pageDialog.dismiss();
          });
      t.check(
          bookmarkLabel[0].equals("Notities · 2"),
          "Bookmark chooser leaves user names untranslated in English UI");
      t.runOnMainSync(
          () -> {
            pane[0].close();
            a.content.removeView(pane[0]);
            pane[0] = new GuidePane(a, imageId, () -> {});
            a.content.addView(pane[0], new LinearLayout.LayoutParams(-1, 600));
          });
      for (int i = 0; i < 50 && pane[0].bitmap == null; i++) Thread.sleep(100);
      String[] markerLabel = {""};
      t.runOnMainSync(
          () -> {
            pane[0].markers();
            markerLabel[0] = pane[0].pageDialog.getListView().getAdapter().getItem(0).toString();
            pane[0].pageDialog.dismiss();
          });
      t.check(
          pane[0].zoom == 2 && markerLabel[0].equals("Notities"),
          "Map reader restores preferred zoom and leaves marker names untranslated");
      boolean[] libraryRendered = {false};
      t.runOnMainSync(
          () -> {
            LinearLayout container = Ui.col(a);
            String old = GuideTools.libraryQuery;
            int oldKind = GuideTools.libraryKind;
            GuideTools.libraryQuery = "Chest map";
            GuideTools.libraryKind = 1;
            GuideTools.library(a, container);
            LinearLayout rows = (LinearLayout) container.getChildAt(3);
            libraryRendered[0] =
                ((TextView) rows.getChildAt(0)).getText().toString().contains("Chest map 日本語");
            GuideTools.libraryQuery = old;
            GuideTools.libraryKind = oldKind;
          });
      t.check(
          libraryRendered[0],
          "Searchable library renders matching document names as raw user text");
    } finally {
      t.runOnMainSync(
          () -> {
            if (pane[0] != null) {
              pane[0].close();
              a.content.removeView(pane[0]);
            }
          });
      OfflineGuides.remove(c, textId);
      OfflineGuides.remove(c, pdfId);
      OfflineGuides.remove(c, imageId);
      Store.prefs(c)
          .edit()
          .putBoolean("guideNotes", notesBefore)
          .putString("language", languageBefore)
          .commit();
      c.getSharedPreferences("thorhaven-guide-export", 0).edit().clear().commit();
      destination.delete();
    }
  }
}
