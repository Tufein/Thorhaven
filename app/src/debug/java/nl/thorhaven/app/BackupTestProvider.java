package nl.thorhaven.app;

import android.database.*;
import android.os.*;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsProvider;
import java.io.*;

/** Test APK only: isolated local folder for actual backup job and retention verification. */
public class BackupTestProvider extends DocumentsProvider {
  File folder;

  public boolean onCreate() {
    folder = new File(getContext().getFilesDir(), "backup-provider");
    folder.mkdirs();
    return true;
  }

  File file(String id) throws FileNotFoundException {
    if (id.equals("root")) return folder;
    if (!id.matches("thorhaven-auto-[0-9]{13}\\.zip")) throw new FileNotFoundException();
    return new File(folder, id);
  }

  static final String[] COL = {
    Document.COLUMN_DOCUMENT_ID,
    Document.COLUMN_DISPLAY_NAME,
    Document.COLUMN_MIME_TYPE,
    Document.COLUMN_FLAGS,
    Document.COLUMN_SIZE
  };

  void row(MatrixCursor c, String id, File f) {
    c.newRow()
        .add(Document.COLUMN_DOCUMENT_ID, id)
        .add(Document.COLUMN_DISPLAY_NAME, id)
        .add(
            Document.COLUMN_MIME_TYPE,
            id.equals("root") ? Document.MIME_TYPE_DIR : "application/zip")
        .add(
            Document.COLUMN_FLAGS,
            id.equals("root")
                ? Document.FLAG_DIR_SUPPORTS_CREATE
                : Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_DELETE)
        .add(Document.COLUMN_SIZE, f.length());
  }

  public Cursor queryRoots(String[] p) {
    return new MatrixCursor(new String[] {"root_id", "document_id", "title", "flags"});
  }

  public Cursor queryDocument(String id, String[] p) throws FileNotFoundException {
    MatrixCursor c = new MatrixCursor(p == null ? COL : p);
    row(c, id, file(id));
    return c;
  }

  public Cursor queryChildDocuments(String id, String[] p, String sort)
      throws FileNotFoundException {
    MatrixCursor c = new MatrixCursor(p == null ? COL : p);
    File[] files = file(id).listFiles();
    if (files != null) for (File f : files) row(c, f.getName(), f);
    return c;
  }

  public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal)
      throws FileNotFoundException {
    return ParcelFileDescriptor.open(file(id), ParcelFileDescriptor.parseMode(mode));
  }

  public String createDocument(String parent, String mime, String name)
      throws FileNotFoundException {
    if (!parent.equals("root")) throw new FileNotFoundException();
    File f = file(name);
    try {
      if (!f.createNewFile()) throw new IOException("Exists");
      return name;
    } catch (IOException e) {
      throw new FileNotFoundException(e.getMessage());
    }
  }

  public void deleteDocument(String id) throws FileNotFoundException {
    if (id.equals("root") || !file(id).delete()) throw new FileNotFoundException();
  }

  public boolean isChildDocument(String parent, String child) {
    return parent.equals("root") && !child.equals("root");
  }
}
