package nl.thorhaven.app;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Instrumentation APK only: deterministic real SAF provider with permission and I/O failures. */
public class StorageTestProvider extends DocumentsProvider {
  static volatile StorageTestProvider instance;
  static final String AUTHORITY = "nl.thorhaven.storage.test";
  static final String[] COL = {
    Document.COLUMN_DOCUMENT_ID,
    Document.COLUMN_DISPLAY_NAME,
    Document.COLUMN_MIME_TYPE,
    Document.COLUMN_SIZE,
    Document.COLUMN_FLAGS
  };

  static final class Node {
    String id, parent, name, mime;
    Long size;

    Node(String id, String parent, String name, String mime, Long size) {
      this.id = id;
      this.parent = parent;
      this.name = name;
      this.mime = mime;
      this.size = size;
    }
  }

  final Map<String, Node> nodes = new LinkedHashMap<>();
  String mode = "normal";
  int creates, writes, queries;

  public boolean onCreate() {
    instance = this;
    reset("normal");
    return true;
  }

  File bytes(String id) {
    return new File(
        getContext().getFilesDir(), "storage-fixture-" + id.replaceAll("[^A-Za-z0-9]", "_"));
  }

  synchronized void reset(String variant) {
    mode = variant;
    nodes.clear();
    creates = 0;
    writes = 0;
    queries = 0;
    nodes.put("root", new Node("root", "", "Fixture folder", Document.MIME_TYPE_DIR, null));
    nodes.put(
        "disc1",
        new Node("disc1", "root", "Game 日本語 (Disc 1).chd", "application/octet-stream", 100L));
    nodes.put(
        "disc2",
        new Node("disc2", "root", "Game 日本語 (Disc 2).chd", "application/octet-stream", null));
    nodes.put("bin", new Node("bin", "root", "Track.bin", "application/octet-stream", 50L));
    nodes.put("sub", new Node("sub", "root", "Other folder", Document.MIME_TYPE_DIR, null));
    nodes.put("other", new Node("other", "sub", "Other.iso", "application/octet-stream", 200L));
    nodes.put("playlist", new Node("playlist", "root", "Existing.m3u", "audio/x-mpegurl", 64L));
    try (FileOutputStream out = new FileOutputStream(bytes("playlist"))) {
      out.write("Game 日本語 (Disc 1).chd\nMissing (Disc 3).chd\n".getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    if (variant.equals("depth")) {
      String parent = "root";
      for (int n = 0; n < 15; n++) {
        String id = "level" + n;
        nodes.put(id, new Node(id, parent, id, Document.MIME_TYPE_DIR, null));
        parent = id;
      }
    }
    if (variant.equals("many"))
      for (int n = 0; n < 10020; n++)
        nodes.put("n" + n, new Node("n" + n, "root", "File" + n + ".txt", "text/plain", 1L));
    if (variant.equals("cycle"))
      nodes.put("cycle", new Node("root", "sub", "Cycle", Document.MIME_TYPE_DIR, null));
    if (variant.equals("overflow"))
      nodes.put(
          "huge", new Node("huge", "root", "Huge.dat", "application/octet-stream", Long.MAX_VALUE));
    if (variant.equals("badMetadata"))
      nodes.put(
          "bad",
          new Node(
              "bad", "root", String.join("", Collections.nCopies(1025, "x")), "text/plain", 1L));
    if (variant.equals("longMetadata")) {
      String label = String.join("", Collections.nCopies(990, "x"));
      for (int n = 0; n < 3000; n++)
        nodes.put("long" + n, new Node("long" + n, "root", label + n + ".txt", "text/plain", 1L));
    }
  }

  synchronized Bundle fixture(String method, String arg, Bundle extras) {
    if (method.equals("fixtureReset")) {
      reset(arg);
      return Bundle.EMPTY;
    }
    if (method.equals("fixtureRevoke")) {
      getContext()
          .revokeUriPermission(
              DocumentsContract.buildTreeDocumentUri(AUTHORITY, arg == null ? "root" : arg),
              Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
      return Bundle.EMPTY;
    }
    if (method.equals("fixtureGrant")) {
      Uri tree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, arg == null ? "root" : arg);
      int flags = extras.getInt("flags");
      getContext()
          .grantUriPermission(
              "nl.thorhaven.app",
              tree,
              flags
                  | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                  | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
      return Bundle.EMPTY;
    }
    if (method.equals("fixtureStats")) {
      Bundle b = new Bundle();
      b.putInt("creates", creates);
      b.putInt("writes", writes);
      b.putInt("queries", queries);
      b.putString("mode", mode);
      return b;
    }
    throw new IllegalArgumentException("Unknown test fixture control");
  }

  Node require(String id) throws FileNotFoundException {
    Node n = nodes.get(id);
    if (n == null) throw new FileNotFoundException(id);
    return n;
  }

  void row(MatrixCursor cursor, Node n) {
    MatrixCursor.RowBuilder r = cursor.newRow();
    for (String col : cursor.getColumnNames()) {
      Object v = null;
      if (col.equals(Document.COLUMN_DOCUMENT_ID)) v = n.id;
      else if (col.equals(Document.COLUMN_DISPLAY_NAME)) v = n.name;
      else if (col.equals(Document.COLUMN_MIME_TYPE)) v = n.mime;
      else if (col.equals(Document.COLUMN_SIZE)) v = n.size;
      else if (col.equals(Document.COLUMN_FLAGS))
        v =
            Document.MIME_TYPE_DIR.equals(n.mime)
                ? Document.FLAG_DIR_SUPPORTS_CREATE
                : Document.FLAG_SUPPORTS_WRITE;
      r.add(v);
    }
  }

  @Override
  public Cursor queryRoots(String[] projection) {
    return new MatrixCursor(new String[] {"root_id", "document_id", "title", "flags"});
  }

  @Override
  public synchronized Cursor queryDocument(String id, String[] projection)
      throws FileNotFoundException {
    MatrixCursor c = new MatrixCursor(projection == null ? COL : projection);
    row(c, require(id));
    return c;
  }

  @Override
  public Cursor queryChildDocuments(String id, String[] projection, String sort)
      throws FileNotFoundException {
    return queryChildDocuments(id, projection, sort, null);
  }

  public Cursor queryChildDocuments(
      String id, String[] projection, String sort, CancellationSignal signal)
      throws FileNotFoundException {
    String selected;
    synchronized (this) {
      selected = mode;
      queries++;
      require(id);
    }
    if (selected.equals("refuse") || selected.equals("partial") && id.equals("sub"))
      throw new FileNotFoundException("Fixture folder refused");
    if (selected.equals("slow")) {
      long deadline = SystemClock.elapsedRealtime() + 5000;
      while (SystemClock.elapsedRealtime() < deadline) {
        if (signal != null) signal.throwIfCanceled();
        try {
          Thread.sleep(25);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    MatrixCursor c = new MatrixCursor(projection == null ? COL : projection);
    synchronized (this) {
      for (Node n : nodes.values()) if (n.parent.equals(id)) row(c, n);
    }
    return c;
  }

  @Override
  public synchronized String createDocument(String parent, String mime, String name)
      throws FileNotFoundException {
    require(parent);
    if (mode.equals("createRefuse")) throw new FileNotFoundException("Fixture creation refused");
    creates++;
    if (mode.equals("alias")) return "disc1";
    if (nodes.values().stream().anyMatch(n -> n.parent.equals(parent) && n.name.equals(name)))
      throw new FileNotFoundException("Exists");
    String id = "new" + UUID.randomUUID().toString().replace("-", "");
    nodes.put(
        id, new Node(id, parent, mode.equals("renamed") ? "Provider-changed.m3u" : name, mime, 0L));
    try {
      if (!bytes(id).createNewFile()) throw new IOException("Exists");
    } catch (IOException e) {
      throw new FileNotFoundException(e.getMessage());
    }
    return id;
  }

  @Override
  public synchronized ParcelFileDescriptor openDocument(
      String id, String openMode, CancellationSignal signal) throws FileNotFoundException {
    require(id);
    if (signal != null) signal.throwIfCanceled();
    if (openMode.contains("w")) {
      writes++;
      if (mode.equals("writeRefuse")) throw new FileNotFoundException("Fixture write refused");
    }
    return ParcelFileDescriptor.open(bytes(id), ParcelFileDescriptor.parseMode(openMode));
  }

  @Override
  public synchronized boolean isChildDocument(String parent, String child) {
    Node n = nodes.get(child);
    int limit = 0;
    while (n != null && limit++ < 20) {
      if (n.parent.equals(parent)) return true;
      n = nodes.get(n.parent);
    }
    return false;
  }
}
