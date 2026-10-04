package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.widget.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

/** User-selected SAF folders only. A report describes that folder, never device-wide storage. */
final class StorageTools {
  static final int READ_TREE = 160, WRITE_TREE = 161, CSV = 162;
  static final int MAX_DEPTH = 12, MAX_NODES = 10000, MAX_METADATA_CHARS = 2000000;
  static final long MAX_TIME = 120000;
  static final String LOCAL = "thorhaven-storage-local";
  static final ThreadPoolExecutor worker =
      new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1));
  static final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor();
  static final Handler main = new Handler(Looper.getMainLooper());
  static final Map<MainActivity, State> states = new WeakHashMap<>();

  static String words(Context c, String nl, String en) {
    return Language.isEnglish(c) ? en : nl;
  }

  static final class Token {
    final CancellationSignal signal = new CancellationSignal();
    final long started = SystemClock.elapsedRealtime();
    volatile boolean cancelled, timedOut;

    void cancel() {
      cancelled = true;
      signal.cancel();
    }

    void check() {
      if (SystemClock.elapsedRealtime() - started >= MAX_TIME) {
        timedOut = true;
        cancel();
      }
      if (cancelled || signal.isCanceled()) throw new OperationCanceledException();
    }
  }

  static final class Entry {
    final Uri uri, parentUri;
    final String id, parentId, name, relative;
    final long bytes;

    Entry(
        Uri uri,
        Uri parentUri,
        String id,
        String parentId,
        String name,
        String relative,
        long bytes) {
      this.uri = uri;
      this.parentUri = parentUri;
      this.id = id;
      this.parentId = parentId;
      this.name = name;
      this.relative = relative;
      this.bytes = bytes;
    }
  }

  static final class Group {
    int files, unknown;
    long knownBytes;
  }

  static final class Report {
    final Uri tree;
    final long at = System.currentTimeMillis();
    final List<Entry> files = new ArrayList<>(), largest = new ArrayList<>();
    final SortedMap<String, Group> extensions = new TreeMap<>();
    final List<String> errors = new ArrayList<>();
    int nodes, folders, unknownSizes, metadataChars;
    long knownBytes;
    boolean limited, cancelled;

    Report(Uri tree) {
      this.tree = tree;
    }

    void error(String message) {
      if (errors.size() < 50) errors.add(message);
    }
  }

  static final class State {
    Token token;
    Report report;
    List<Entry> selected = new ArrayList<>(), pending = new ArrayList<>();
    String message = "";
    boolean busy;
    int generation;
  }

  static State state(MainActivity a) {
    return states.computeIfAbsent(a, k -> new State());
  }

  static SharedPreferences local(Context c) {
    return c.getSharedPreferences(LOCAL, 0);
  }

  static void lifecyclecancel(MainActivity a) {
    State s = states.remove(a);
    if (s != null) {
      ++s.generation;
      if (s.token != null) s.token.cancel();
    }
  }

  static void cancel(MainActivity a) {
    State s = state(a);
    ++s.generation;
    if (s.token != null) s.token.cancel();
    s.busy = false;
    s.message =
        words(
            a,
            "Geannuleerd. Het vorige rapport blijft staan.",
            "Cancelled. The previous report is kept.");
    a.render();
  }

  interface Work {
    Object run(Context application, Token token) throws Exception;
  }

  static void submit(MainActivity a, String message, Work work) {
    State s = state(a);
    if (s.busy) {
      Ui.toast(a, words(a, "Annuleer eerst de lopende taak.", "Cancel the running task first."));
      return;
    }
    s.busy = true;
    s.message = message;
    Token token = new Token();
    s.token = token;
    int generation = ++s.generation;
    WeakReference<MainActivity> owner = new WeakReference<>(a);
    Context application = a.getApplicationContext();
    ScheduledFuture<?> deadline =
        deadlines.schedule(
            () -> {
              token.timedOut = true;
              token.cancel();
            },
            MAX_TIME,
            TimeUnit.MILLISECONDS);
    try {
      worker.execute(
          () -> {
            Object result = null;
            String error = null;
            try {
              token.check();
              result = work.run(application, token);
              token.check();
            } catch (Exception e) {
              error =
                  token.cancelled
                      ? (token.timedOut ? "Time limit reached" : "Cancelled")
                      : e.getMessage();
            } finally {
              deadline.cancel(false);
            }
            final Object complete = result;
            final String failure = error;
            main.post(
                () -> {
                  MainActivity current = owner.get();
                  if (current == null
                      || current.isDestroyed()
                      || current.isFinishing()
                      || states.get(current) != s
                      || s.generation != generation) return;
                  s.busy = false;
                  s.token = null;
                  if (failure != null)
                    s.message = words(current, "Niet voltooid: ", "Not completed: ") + failure;
                  else if (complete instanceof Report) {
                    s.report = (Report) complete;
                    s.selected.clear();
                    s.pending.clear();
                    s.message = words(current, "Mapscan voltooid.", "Folder scan completed.");
                  } else s.message = String.valueOf(complete);
                  if (current.page.equals("Opslag")) current.render();
                });
          });
    } catch (RejectedExecutionException occupied) {
      deadline.cancel(false);
      token.cancel();
      s.busy = false;
      s.token = null;
      s.message =
          words(
              a,
              "Een vorige provider-aanvraag loopt nog. Probeer het later opnieuw.",
              "A previous provider request is still running. Try again later.");
    }
    if (a.page.equals("Opslag")) a.render();
  }

  static String extension(String name) {
    int dot = name == null ? -1 : name.lastIndexOf('.');
    return dot < 0 || dot == name.length() - 1
        ? "(none)"
        : name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  static final class Folder {
    final String id, relative;
    final int depth;

    Folder(String id, String relative, int depth) {
      this.id = id;
      this.relative = relative;
      this.depth = depth;
    }
  }

  static Report scan(Context c, Uri tree, Token token) throws Exception {
    if (tree == null
        || !"content".equals(tree.getScheme())
        || !DocumentsContract.isTreeUri(tree)
        || tree.toString().length() > 4096)
      throw new IOException("Choose a folder through Android's picker");
    Report report = new Report(tree);
    Deque<Folder> queue = new ArrayDeque<>();
    queue.add(new Folder(DocumentsContract.getTreeDocumentId(tree), "", 0));
    Set<String> seen = new HashSet<>();
    seen.add(DocumentsContract.getTreeDocumentId(tree));
    while (!queue.isEmpty() && report.nodes < MAX_NODES) {
      token.check();
      Folder folder = queue.removeFirst();
      report.folders++;
      Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, folder.id);
      Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder.id);
      try (Cursor cursor =
          c.getContentResolver()
              .query(
                  children,
                  new String[] {
                    Document.COLUMN_DOCUMENT_ID,
                    Document.COLUMN_DISPLAY_NAME,
                    Document.COLUMN_MIME_TYPE,
                    Document.COLUMN_SIZE
                  },
                  null,
                  null,
                  null,
                  token.signal)) {
        if (cursor == null) throw new IOException("Provider returned no folder listing");
        while (cursor.moveToNext()) {
          token.check();
          if (report.nodes >= MAX_NODES) {
            report.limited = true;
            break;
          }
          report.nodes++;
          String id = cursor.getString(0), name = cursor.getString(1), mime = cursor.getString(2);
          if (id == null
              || id.isEmpty()
              || id.length() > 4096
              || name == null
              || name.length() > 1024) {
            report.error("Invalid document metadata");
            continue;
          }
          if (!seen.add(id)) {
            report.error("Repeated document identity: " + name);
            continue;
          }
          String relative = folder.relative.isEmpty() ? name : folder.relative + "/" + name;
          Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, id);
          long added =
              (long) id.length()
                  + name.length()
                  + relative.length()
                  + document.toString().length()
                  + parent.toString().length();
          if (added > MAX_METADATA_CHARS - report.metadataChars) {
            report.limited = true;
            report.error("Name data limit reached; report is partial");
            queue.clear();
            break;
          }
          report.metadataChars += (int) added;
          if (Document.MIME_TYPE_DIR.equals(mime)) {
            if (folder.depth >= MAX_DEPTH) {
              report.limited = true;
              report.error("Depth limit: " + relative);
            } else queue.addLast(new Folder(id, relative, folder.depth + 1));
            continue;
          }
          long size = -1;
          if (!cursor.isNull(3) && cursor.getType(3) == Cursor.FIELD_TYPE_INTEGER) {
            long measured = cursor.getLong(3);
            if (measured >= 0) size = measured;
          }
          Entry entry = new Entry(document, parent, id, folder.id, name, relative, size);
          report.files.add(entry);
          Group group = report.extensions.computeIfAbsent(extension(name), k -> new Group());
          group.files++;
          if (size < 0) {
            report.unknownSizes++;
            group.unknown++;
          } else if (size > Long.MAX_VALUE - report.knownBytes
              || size > Long.MAX_VALUE - group.knownBytes) {
            report.error("Size total overflow: " + relative);
            report.unknownSizes++;
            group.unknown++;
          } else {
            report.knownBytes += size;
            group.knownBytes += size;
            report.largest.add(entry);
          }
        }
      } catch (OperationCanceledException cancelled) {
        throw cancelled;
      } catch (Exception refused) {
        report.error(folder.relative + " · " + refused.getClass().getSimpleName());
      }
    }
    token.check();
    if (!queue.isEmpty()) report.limited = true;
    report.largest.sort((a, b) -> Long.compare(b.bytes, a.bytes));
    if (report.largest.size() > 20) report.largest.subList(20, report.largest.size()).clear();
    return report;
  }

  static String csvCell(String value) {
    if (value == null) value = "";
    if (!value.isEmpty()
        && ("=+-@".indexOf(value.charAt(0)) >= 0
            || value.charAt(0) == '\t'
            || value.charAt(0) == '\r')) value = "'" + value;
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }

  static String csv(Report report) {
    StringBuilder b =
        new StringBuilder("report_time,scope,relative_name,known_bytes,extension\r\n");
    for (Entry e : report.files)
      b.append(report.at)
          .append(',')
          .append(csvCell("selected folder only"))
          .append(',')
          .append(csvCell(e.relative))
          .append(',')
          .append(e.bytes < 0 ? "" : e.bytes)
          .append(',')
          .append(csvCell(extension(e.name)))
          .append("\r\n");
    b.append("\r\nknown_bytes,unknown_sizes,files,folders,partial,errors\r\n")
        .append(report.knownBytes)
        .append(',')
        .append(report.unknownSizes)
        .append(',')
        .append(report.files.size())
        .append(',')
        .append(report.folders)
        .append(',')
        .append(report.limited || !report.errors.isEmpty())
        .append(',')
        .append(report.errors.size())
        .append("\r\n");
    for (String error : report.errors) b.append(csvCell(error)).append("\r\n");
    return b.toString();
  }

  static void choose(MainActivity a, int request, Uri initial) {
    Intent i =
        new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    if (request == WRITE_TREE) i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    if (initial != null) i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial);
    try {
      a.startActivityForResult(i, request);
    } catch (RuntimeException unavailable) {
      Ui.toast(a, words(a, "Bestandskiezer niet beschikbaar.", "File picker unavailable."));
    }
  }

  static void scan(MainActivity a, Uri tree) {
    submit(
        a,
        words(a, "Map wordt gescand…", "Scanning folder…"),
        (application, token) -> scan(application, tree, token));
  }

  static boolean handlesResult(MainActivity a, int request, int result, Intent data) {
    if (request != READ_TREE && request != WRITE_TREE && request != CSV) return false;
    if (result != Activity.RESULT_OK || data == null || data.getData() == null) return true;
    Uri uri = data.getData();
    State s = state(a);
    int granted =
        data.getFlags()
            & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    try {
      if (!"content".equals(uri.getScheme()))
        throw new IOException("A document-provider URI is required");
      if (request == CSV) {
        Report snapshot = s.report;
        if (snapshot == null) throw new IOException("Scan a folder first");
        submit(
            a,
            words(a, "CSV wordt opgeslagen…", "Saving CSV…"),
            (application, token) -> {
              token.check();
              try (ParcelFileDescriptor fd =
                  application.getContentResolver().openFileDescriptor(uri, "w", token.signal)) {
                if (fd == null) throw new IOException("Provider refused CSV export");
                try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(fd)) {
                  out.write(csv(snapshot).getBytes(StandardCharsets.UTF_8));
                }
              }
              return words(application, "CSV opgeslagen.", "CSV saved.");
            });
        return true;
      }
      if (!DocumentsContract.isTreeUri(uri)
          || (granted & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0)
        throw new IOException("A readable folder grant is required");
      if (request == WRITE_TREE) {
        List<Entry> selected = Collections.unmodifiableList(new ArrayList<>(s.pending));
        PlaylistTools.names(selected);
        Entry first = selected.get(0);
        if ((granted & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0
            || !Objects.equals(uri.getAuthority(), first.parentUri.getAuthority())
            || !DocumentsContract.getTreeDocumentId(uri).equals(first.parentId))
          throw new IOException("Choose the exact disc folder and allow writing");
        // Keep scan access read-only. A write grant is obtained only for this explicit new-file
        // action.
        persist(a.getApplicationContext(), uri, granted, "writeTree");
        submit(
            a,
            words(a, "Nieuwe M3U wordt opgeslagen…", "Saving a new M3U…"),
            (application, token) -> {
              Uri saved = PlaylistTools.create(application, uri, selected, token);
              return words(application, "Nieuwe M3U opgeslagen: ", "New M3U saved: ")
                  + DocumentsContract.getDocumentId(saved);
            });
      } else {
        persist(a.getApplicationContext(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION, "tree");
        scan(a, uri);
      }
    } catch (Exception denied) {
      Ui.toast(a, words(a, "Niet voltooid: ", "Not completed: ") + denied.getMessage());
    }
    return true;
  }

  static synchronized void persist(Context c, Uri uri, int flags, String key) throws IOException {
    if (!key.equals("tree") && !key.equals("writeTree"))
      throw new IOException("Invalid local folder-grant purpose");
    try {
      c.getContentResolver().takePersistableUriPermission(uri, flags);
    } catch (SecurityException temporaryOnly) {
      /* Some providers offer session access only. */
    }
    String old = local(c).getString(key, "");
    if (!local(c).edit().putString(key, uri.toString()).commit())
      throw new IOException("Could not remember the selected folder");
    String otherKey = key.equals("tree") ? "writeTree" : "tree";
    int preserved =
        old.equals(local(c).getString(otherKey, ""))
            ? (otherKey.equals("tree")
                ? Intent.FLAG_GRANT_READ_URI_PERMISSION
                : Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            : 0;
    int release = flags & ~preserved;
    if (!old.isEmpty() && !old.equals(uri.toString()) && release != 0)
      try {
        c.getContentResolver().releasePersistableUriPermission(Uri.parse(old), release);
      } catch (RuntimeException ignored) {
      }
  }

  static void page(MainActivity a, LinearLayout parent) {
    State s = state(a);
    LinearLayout box =
        Ui.card(
            a,
            parent,
            words(a, "Opslag en M3U", "Storage and M3U"),
            words(
                a,
                "Kies zelf een map. Dit overzicht meet alleen die map en toont bekende"
                    + " bestandsgroottes; onbekende groottes tellen niet als nul. Er wordt niets"
                    + " verwijderd of verplaatst.",
                "Choose a folder. This report measures only that folder and shows known file sizes;"
                    + " unknown sizes are not counted as zero. Nothing is deleted or moved."));
    box.addView(
        Ui.rawButton(
            a,
            words(a, "Map kiezen en lezen", "Choose folder to read"),
            () -> choose(a, READ_TREE, null)));
    String saved = local(a).getString("tree", "");
    if (!saved.isEmpty())
      box.addView(
          Ui.rawButton(
              a,
              words(a, "Gekozen map opnieuw scannen", "Scan selected folder again"),
              () -> scan(a, Uri.parse(saved))));
    if (s.busy)
      box.addView(Ui.rawButton(a, words(a, "Taak annuleren", "Cancel task"), () -> cancel(a)));
    if (!s.message.isEmpty()) box.addView(Ui.rawText(a, s.message, 13, Ui.ACCENT));
    box.addView(
        Ui.rawText(
            a,
            words(
                a,
                "Grenzen: 12 mapniveaus en 10.000 vermeldingen. Na 2 minuten wordt annulering"
                    + " gevraagd; een provider kan zijn huidige aanvraag eerst afmaken. Verborgen"
                    + " of geweigerde bestanden kunnen ontbreken.",
                "Limits: 12 folder levels and 10,000 entries. Cancellation is requested after 2"
                    + " minutes; a provider may finish its current request first. Hidden or refused"
                    + " files may be absent."),
            12,
            Ui.MUTED));
    Report r = s.report;
    if (r == null) return;
    box.addView(
        Ui.rawText(
            a,
            DateFormat.getDateTimeInstance().format(new Date(r.at))
                + "\n"
                + words(a, "Bestanden: ", "Files: ")
                + r.files.size()
                + words(a, " · mappen: ", " · folders: ")
                + r.folders
                + "\n"
                + words(a, "Bekende bytes: ", "Known bytes: ")
                + r.knownBytes
                + words(a, " · onbekende groottes: ", " · unknown sizes: ")
                + r.unknownSizes
                + "\n"
                + words(a, "Onvolledig: ", "Partial: ")
                + (r.limited || !r.errors.isEmpty()),
            14,
            Ui.TEXT));
    int shown = 0;
    for (Map.Entry<String, Group> e : r.extensions.entrySet()) {
      if (++shown > 100) break;
      box.addView(
          Ui.rawText(
              a,
              e.getKey()
                  + " · "
                  + e.getValue().files
                  + " · "
                  + e.getValue().knownBytes
                  + words(a, " bytes bekend · onbekend: ", " known bytes · unknown: ")
                  + e.getValue().unknown,
              12,
              Ui.MUTED));
    }
    if (r.extensions.size() > 100)
      box.addView(
          Ui.rawText(
              a,
              words(
                  a,
                  "Maximaal 100 bestandstypen zichtbaar; CSV bevat alle bestanden.",
                  "Up to 100 file types shown; CSV includes all files."),
              12,
              Ui.MUTED));
    box.addView(
        Ui.rawButton(
            a,
            words(a, "CSV-rapport exporteren", "Export CSV report"),
            () -> {
              try {
                a.startActivityForResult(
                    new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("text/csv")
                        .putExtra(Intent.EXTRA_TITLE, "Thorhaven-folder-scan.csv"),
                    CSV);
              } catch (RuntimeException e) {
                Ui.toast(
                    a, words(a, "Bestandskiezer niet beschikbaar.", "File picker unavailable."));
              }
            }));
    box.addView(
        Ui.rawText(
            a, words(a, "Grootste bestanden (max. 20)", "Largest files (up to 20)"), 15, Ui.TEXT));
    for (Entry e : r.largest)
      box.addView(Ui.rawText(a, e.relative + " · " + e.bytes + " B", 12, Ui.MUTED));
    for (String error : r.errors) box.addView(Ui.rawText(a, error, 12, Ui.MUTED));
    playlistPage(a, parent, s, r);
  }

  static void playlistPage(MainActivity a, LinearLayout parent, State s, Report r) {
    LinearLayout box =
        Ui.card(
            a,
            parent,
            words(a, "M3U-hulp", "M3U assistant"),
            words(
                a,
                "Selecteer CUE/CHD/ISO/PBP/GDI-schijfbestanden uit één map en bepaal hun volgorde."
                    + " BIN-tracks zijn geen schijfkeuze. Bestandsnamen moeten overeenkomen met wat"
                    + " je emulator ziet; ondersteuning verschilt per core. Deze hulp test geen"
                    + " spelinhoud.",
                "Select CUE/CHD/ISO/PBP/GDI disc files from one folder and arrange their order. BIN"
                    + " tracks are not disc choices. Filenames must match what your emulator sees;"
                    + " support varies by core. This assistant does not validate game content."));
    box.addView(
        Ui.rawText(
            a,
            words(
                a,
                "Een PBP kan meerdere schijven al bevatten; dan is een M3U meestal niet nodig."
                    + " Gebruik alleen bestandsformaten die jouw emulator/core ondersteunt.",
                "A PBP can already contain multiple discs; an M3U is usually unnecessary in that"
                    + " case. Use only formats supported by your emulator/core."),
            12,
            Ui.MUTED));
    box.addView(
        Ui.rawButton(
            a, words(a, "Schijfbestanden toevoegen", "Add disc files"), () -> discPicker(a, s, r)));
    for (int n = 0; n < s.selected.size(); n++) {
      final int index = n;
      Entry e = s.selected.get(n);
      LinearLayout row = Ui.col(a);
      row.addView(Ui.rawText(a, (n + 1) + " · " + e.name, 13, Ui.TEXT));
      row.addView(
          Ui.rawButton(
              a,
              words(a, "Omhoog", "Move up"),
              () -> {
                if (index > 0) Collections.swap(s.selected, index, index - 1);
                a.render();
              }));
      row.addView(
          Ui.rawButton(
              a,
              words(a, "Omlaag", "Move down"),
              () -> {
                if (index + 1 < s.selected.size()) Collections.swap(s.selected, index, index + 1);
                a.render();
              }));
      row.addView(
          Ui.rawButton(
              a,
              words(a, "Uit selectie halen", "Remove from selection"),
              () -> {
                s.selected.remove(index);
                a.render();
              }));
      box.addView(row);
    }
    if (!s.selected.isEmpty()) {
      try {
        box.addView(
            Ui.rawText(a, words(a, "M3U-tekstvoorbeeld", "M3U text preview"), 14, Ui.ACCENT));
        box.addView(Ui.rawText(a, PlaylistTools.encode(s.selected), 13, Ui.TEXT));
      } catch (Exception invalid) {
        box.addView(Ui.rawText(a, invalid.getMessage(), 13, Ui.MUTED));
      }
    }
    if (!s.selected.isEmpty())
      box.addView(
          Ui.rawButton(
              a,
              words(a, "Nieuwe M3U in deze map maken", "Create new M3U in this folder"),
              () -> {
                try {
                  PlaylistTools.names(s.selected);
                  s.pending = new ArrayList<>(s.selected);
                  Ui.toast(
                      a,
                      words(
                          a,
                          "Kies nu precies de map met deze schijven en geef schrijftoegang.",
                          "Now choose the exact disc folder and allow writing."));
                  choose(a, WRITE_TREE, s.selected.get(0).parentUri);
                } catch (Exception e) {
                  Ui.toast(a, e.getMessage());
                }
              }));
    box.addView(
        Ui.rawButton(
            a,
            words(a, "Bestaande M3U controleren", "Check an existing M3U"),
            () -> {
              List<Entry> playlists = new ArrayList<>();
              for (Entry e : r.files) if (extension(e.name).equals("m3u")) playlists.add(e);
              if (playlists.isEmpty()) {
                Ui.toast(a, words(a, "Geen M3U in dit rapport.", "No M3U in this report."));
                return;
              }
              String[] labels = playlists.stream().map(e -> e.relative).toArray(String[]::new);
              new AlertDialog.Builder(a)
                  .setTitle(words(a, "M3U kiezen", "Choose M3U"))
                  .setItems(
                      labels,
                      (dialog, which) -> {
                        Entry selected = playlists.get(which);
                        submit(
                            a,
                            words(a, "M3U wordt gecontroleerd…", "Checking M3U…"),
                            (application, token) -> {
                              PlaylistTools.Check check =
                                  PlaylistTools.checkExisting(application, selected, token);
                              return words(application, "Verwijzingen: ", "References: ")
                                  + check.references.size()
                                  + words(application, " · ontbrekend: ", " · missing: ")
                                  + check.missing.size()
                                  + (check.missing.isEmpty()
                                      ? ""
                                      : "\n" + String.join("\n", check.missing));
                            });
                      })
                  .setNegativeButton(words(a, "Sluiten", "Close"), null)
                  .show();
            }));
  }

  static void discPicker(MainActivity a, State s, Report r) {
    List<Entry> candidates = new ArrayList<>();
    for (Entry e : r.files) if (PlaylistTools.disc(e.name)) candidates.add(e);
    if (candidates.isEmpty()) {
      Ui.toast(
          a,
          words(
              a,
              "Geen ondersteunde schijfbestanden in dit rapport.",
              "No supported disc files in this report."));
      return;
    }
    String[] labels = candidates.stream().map(e -> e.relative).toArray(String[]::new);
    new AlertDialog.Builder(a)
        .setTitle(words(a, "Schijf toevoegen", "Add disc"))
        .setItems(
            labels,
            (dialog, which) -> {
              List<Entry> next = new ArrayList<>(s.selected);
              next.add(candidates.get(which));
              try {
                PlaylistTools.names(next);
                s.selected = next;
                a.render();
              } catch (Exception e) {
                Ui.toast(a, e.getMessage());
              }
            })
        .setNegativeButton(words(a, "Sluiten", "Close"), null)
        .show();
  }

  private StorageTools() {}
}
