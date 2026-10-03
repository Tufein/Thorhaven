package nl.thorhaven.app;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.text.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Local document management. Reader preferences travel with each document in ZIP backups. */
final class GuideTools {
  static final int EXPORT_REQUEST = 47;
  static String libraryQuery = "";
  static int libraryKind;
  static final String[] KINDS = {"", "pdf", "text", "image"};

  static final class Entry {
    final String id, name, owner, app, kind;

    Entry(Context c, String id) {
      this.id = id;
      JSONObject m = OfflineGuides.meta(c, id);
      name = m.optString("name");
      owner = OfflineGuides.owner(c, id);
      app = Store.name(c, owner);
      kind = m.optString("kind");
    }
  }

  static List<Entry> entries(Context c) {
    List<Entry> rows = new ArrayList<>();
    for (String id : OfflineGuides.prefs(c).getAll().keySet())
      if (OfflineGuides.exists(c, id)) rows.add(new Entry(c, id));
    rows.sort(
        Comparator.comparing((Entry e) -> e.app.toLowerCase(Locale.ROOT))
            .thenComparing(e -> e.name.toLowerCase(Locale.ROOT))
            .thenComparing(e -> e.id));
    return rows;
  }

  static List<Entry> filter(List<Entry> entries, String query, String kind) {
    String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    List<Entry> result = new ArrayList<>();
    for (Entry e : entries)
      if ((kind.isEmpty() || kind.equals(e.kind))
          && (q.isEmpty()
              || (e.name + "\n" + e.app + "\n" + e.owner).toLowerCase(Locale.ROOT).contains(q)))
        result.add(e);
    return result;
  }

  static void library(MainActivity a, LinearLayout parent) {
    List<Entry> all = entries(a);
    EditText query = Ui.input(a, "Zoek op documentnaam of app");
    query.setText(libraryQuery);
    parent.addView(query);
    Spinner kinds = new Spinner(a);
    kinds.setAdapter(
        new ArrayAdapter<>(
            a,
            android.R.layout.simple_spinner_dropdown_item,
            Language.labels(
                a, new String[] {"Alle documenten", "PDF", "Tekst", "Kaarten / afbeeldingen"})));
    kinds.setSelection(Math.max(0, Math.min(3, libraryKind)));
    parent.addView(kinds);
    TextView count = Ui.text(a, "", 13, Ui.MUTED);
    parent.addView(count);
    LinearLayout rows = Ui.col(a);
    parent.addView(rows);
    Runnable update =
        () -> {
          libraryQuery = query.getText().toString();
          libraryKind = Math.max(0, kinds.getSelectedItemPosition());
          List<Entry> found = filter(all, libraryQuery, KINDS[libraryKind]);
          rows.removeAllViews();
          count.setText(found.size() + " / " + all.size() + " documenten");
          for (Entry e : found) documentRow(a, rows, e.id, true);
          if (found.isEmpty())
            rows.addView(
                Ui.text(
                    a,
                    all.isEmpty()
                        ? "Nog geen offline gidsen geïmporteerd."
                        : "Geen documenten gevonden.",
                    14,
                    Ui.MUTED));
        };
    query.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            update.run();
          }

          public void afterTextChanged(Editable s) {}
        });
    kinds.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> p, android.view.View v, int i, long id) {
            update.run();
          }

          public void onNothingSelected(AdapterView<?> p) {}
        });
    update.run();
  }

  static void documentRow(MainActivity a, LinearLayout parent, String id, boolean includeApp) {
    JSONObject m = OfflineGuides.meta(a, id);
    parent.addView(
        Ui.rawText(
            a,
            (includeApp ? Store.name(a, OfflineGuides.owner(a, id)) + " · " : "")
                + m.optString("name"),
            15,
            Ui.TEXT));
    LinearLayout actions = Ui.row(a);
    actions.addView(
        Ui.button(
            a,
            "Openen",
            () -> {
              GuidePages.selected = OfflineGuides.owner(a, id);
              Store.prefs(a).edit().putString("guideActive:" + GuidePages.selected, id).commit();
              OfflineGuides.open(a, id);
            }),
        new LinearLayout.LayoutParams(0, -2, 1));
    actions.addView(
        Ui.button(a, "Documentopties", () -> options(a, id)),
        new LinearLayout.LayoutParams(0, -2, 1));
    parent.addView(actions);
  }

  static void options(MainActivity a, String id) {
    new Ui.Dialog(a)
        .setTitle("Documentopties")
        .setItems(
            new String[] {"Naam wijzigen", "Origineel exporteren", "Verwijderen"},
            (d, w) -> {
              if (w == 0) {
                EditText name = Ui.input(a, "Naam van het document");
                name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(180)});
                name.setText(OfflineGuides.meta(a, id).optString("name"));
                new Ui.Dialog(a)
                    .setTitle("Naam wijzigen")
                    .setView(name)
                    .setPositiveButton(
                        "Opslaan",
                        (d2, w2) -> {
                          try {
                            rename(a, id, name.getText().toString());
                            a.render();
                          } catch (Exception e) {
                            Ui.toast(a, e.getMessage());
                          }
                        })
                    .setNegativeButton("Annuleren", null)
                    .show();
              } else if (w == 1) beginExport(a, id);
              else
                new Ui.Dialog(a)
                    .setTitle("Lokaal document verwijderen?")
                    .setPositiveButton(
                        "Verwijderen",
                        (d2, w2) -> {
                          OfflineGuides.remove(a, id);
                          a.render();
                        })
                    .setNegativeButton("Annuleren", null)
                    .show();
            })
        .setNegativeButton("Annuleren", null)
        .show();
  }

  static String title(String name, int max) throws IOException {
    String value = name == null ? "" : name.trim();
    if (value.isEmpty() || value.length() > max)
      throw new IOException("Gebruik 1 tot " + max + " tekens.");
    for (int i = 0; i < value.length(); i++)
      if (Character.isISOControl(value.charAt(i)))
        throw new IOException("De naam bevat ongeldige tekens.");
    return value;
  }

  static void rename(Context c, String id, String name) throws Exception {
    synchronized (OfflineGuides.class) {
      JSONObject m = require(c, id);
      m.put("name", title(name, 180));
      save(c, id, m);
    }
  }

  static JSONObject require(Context c, String id) throws Exception {
    OfflineGuides.valid(id);
    if (!OfflineGuides.exists(c, id)) throw new IOException("Document bestaat niet meer.");
    return OfflineGuides.meta(c, id);
  }

  static void save(Context c, String id, JSONObject m) throws Exception {
    ExtraFeatures.validateGuide(m);
    validateMeta(m);
    if (!OfflineGuides.prefs(c).edit().putString(id, m.toString()).commit())
      throw new IOException("Documentgegevens konden niet worden opgeslagen.");
  }

  static int integer(JSONObject m, String key) throws Exception {
    Object value = m.get(key);
    if (!(value instanceof Number)
        || !Double.isFinite(((Number) value).doubleValue())
        || ((Number) value).doubleValue() != ((Number) value).intValue())
      throw new IOException("Ongeldige leesinstelling.");
    return ((Number) value).intValue();
  }

  static void validateMeta(JSONObject m) throws Exception {
    if (m.has("textSize")) {
      int n = integer(m, "textSize");
      if (n < 12 || n > 30) throw new IOException("Tekstgrootte moet tussen 12 en 30 liggen.");
    }
    if (m.has("zoom")) {
      int n = integer(m, "zoom");
      if (n != 100 && n != 150 && n != 200) throw new IOException("Kies zoom 100, 150 of 200%.");
    }
  }

  static void reading(Context c, String id, String key, int value) throws Exception {
    if (!key.equals("textSize") && !key.equals("zoom"))
      throw new IOException("Ongeldige leesinstelling.");
    synchronized (OfflineGuides.class) {
      JSONObject m = require(c, id);
      m.put(key, value);
      save(c, id, m);
    }
  }

  static void changeEntry(
      Context c, String id, String key, int index, String name, Integer page, boolean remove)
      throws Exception {
    if (!key.equals("bookmarks") && !key.equals("markers"))
      throw new IOException("Ongeldige documentoptie.");
    synchronized (OfflineGuides.class) {
      JSONObject m = require(c, id);
      JSONArray rows = m.optJSONArray(key);
      if (rows == null || index < 0 || index >= rows.length())
        throw new IOException("Deze vermelding bestaat niet meer.");
      if (remove) rows.remove(index);
      else {
        JSONObject entry = rows.getJSONObject(index);
        entry.put("name", title(name, 100));
        if (key.equals("bookmarks") && page != null) entry.put("page", page);
      }
      save(c, id, m);
    }
  }

  static Intent exportIntent(Context c, String id) throws Exception {
    JSONObject m = require(c, id);
    String kind = m.getString("kind"), mime, extension;
    if (kind.equals("pdf")) {
      mime = "application/pdf";
      extension = ".pdf";
    } else if (kind.equals("image")) {
      byte[] header = new byte[3];
      try (InputStream in = new FileInputStream(OfflineGuides.file(c, id))) {
        in.read(header);
      }
      boolean png = header[0] == (byte) 137 && header[1] == 80;
      mime = png ? "image/png" : "image/jpeg";
      extension = png ? ".png" : ".jpg";
    } else {
      mime = "text/plain";
      extension = ".txt";
    }
    String filename =
        m.optString("name", "Thorhaven-guide").replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    if (filename.isEmpty() || filename.equals(".") || filename.equals(".."))
      filename = "Thorhaven-guide";
    String lower = filename.toLowerCase(Locale.ROOT);
    if (kind.equals("text") && lower.endsWith(".md")) mime = "text/markdown";
    else if (!lower.endsWith(extension) && !(kind.equals("image") && lower.endsWith(".jpeg")))
      filename += extension;
    return new Intent(Intent.ACTION_CREATE_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(mime)
        .putExtra(Intent.EXTRA_TITLE, filename);
  }

  static void beginExport(MainActivity a, String id) {
    // Read the original header off the UI thread; the file picker is always shown on the UI thread.
    OfflineGuides.worker.execute(
        () -> {
          try {
            Intent intent = exportIntent(a, id);
            a.handler.post(
                () -> {
                  if (a.isDestroyed()) return;
                  try {
                    if (!a.getSharedPreferences("thorhaven-guide-export", 0)
                        .edit()
                        .putString("pending", id)
                        .commit()) throw new IOException("Export kon niet worden gestart.");
                    a.startActivityForResult(intent, EXPORT_REQUEST);
                  } catch (Exception e) {
                    a.getSharedPreferences("thorhaven-guide-export", 0)
                        .edit()
                        .remove("pending")
                        .apply();
                    Ui.toast(a, e.getMessage());
                  }
                });
          } catch (Exception e) {
            a.handler.post(() -> Ui.toast(a, e.getMessage()));
          }
        });
  }

  static void exportDocument(Context c, String id, OutputStream out) throws Exception {
    synchronized (OfflineGuides.class) {
      require(c, id);
      File f = OfflineGuides.file(c, id);
      long bytes = f.length();
      if (bytes < 1 || bytes > 16 * 1024 * 1024)
        throw new IOException("Ongeldige documentgrootte.");
      try (InputStream in = new FileInputStream(f)) {
        byte[] b = new byte[8192];
        int n;
        long total = 0;
        while ((n = in.read(b)) != -1) {
          if ((total += n) > 16 * 1024 * 1024) throw new IOException("Document te groot.");
          out.write(b, 0, n);
        }
        if (total != bytes) throw new IOException("Document veranderde tijdens export.");
      }
      out.flush();
    }
  }

  static void onExportResult(MainActivity a, int result, Intent data) {
    SharedPreferences pending = a.getSharedPreferences("thorhaven-guide-export", 0);
    String id = pending.getString("pending", "");
    pending.edit().remove("pending").commit();
    if (result != Activity.RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    OfflineGuides.worker.execute(
        () -> {
          String message;
          try {
            require(a, id);
            try (OutputStream out = a.getContentResolver().openOutputStream(uri, "wt")) {
              if (out == null) throw new IOException("Bestand niet schrijfbaar.");
              exportDocument(a, id, out);
            }
            message = "Origineel document geëxporteerd.";
          } catch (Exception e) {
            message = "Export mislukt: " + e.getMessage();
          }
          String status = message;
          a.handler.post(
              () -> {
                if (!a.isDestroyed()) Ui.toast(a, status);
              });
        });
  }
}
