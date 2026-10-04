package nl.thorhaven.app;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Small same-folder playlists. Names are supplied by SAF, never constructed filesystem paths. */
final class PlaylistTools {
  static final int MAX_DISCS = 32, MAX_BYTES = 65536;
  static final Set<String> DISC_EXTENSIONS =
      new HashSet<>(Arrays.asList("cue", "chd", "iso", "pbp", "gdi"));

  static boolean disc(String name) {
    return name != null && DISC_EXTENSIONS.contains(StorageTools.extension(name));
  }

  static void filename(String name) throws IOException {
    if (name == null
        || name.isEmpty()
        || name.length() > 240
        || !name.equals(name.trim())
        || name.equals(".")
        || name.equals("..")
        || name.startsWith("#")
        || name.contains("/")
        || name.contains("\\")
        || name.contains(":")
        || name.codePoints().anyMatch(Character::isISOControl))
      throw new IOException(
          "Use an exact, plain same-folder filename without paths or control characters");
  }

  static List<String> names(List<StorageTools.Entry> selected) throws IOException {
    if (selected == null || selected.isEmpty() || selected.size() > MAX_DISCS)
      throw new IOException("Select 1–32 disc files from one folder");
    List<String> names = new ArrayList<>();
    Set<String> unique = new HashSet<>(), ids = new HashSet<>();
    String authority = selected.get(0).uri.getAuthority(), folder = selected.get(0).parentId;
    for (StorageTools.Entry e : selected) {
      filename(e.name);
      if (!disc(e.name)
          || !folder.equals(e.parentId)
          || !Objects.equals(authority, e.uri.getAuthority())
          || !unique.add(e.name)
          || !ids.add(e.id))
        throw new IOException(
            "Disc files must be distinct CUE/CHD/ISO/PBP/GDI files in the same folder");
      names.add(e.name);
    }
    return names;
  }

  static String encode(List<StorageTools.Entry> selected) throws IOException {
    String text = String.join("\n", names(selected)) + "\n";
    if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
      throw new IOException("Playlist exceeds 64 KB");
    return text;
  }

  static final class Check {
    final List<String> references, missing;

    Check(List<String> references, List<String> missing) {
      this.references = Collections.unmodifiableList(new ArrayList<>(references));
      this.missing = Collections.unmodifiableList(new ArrayList<>(missing));
    }
  }

  static Check check(String text, Collection<String> folderNames) throws IOException {
    if (text == null || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
      throw new IOException("Playlist must be at most 64 KB");
    if (text.startsWith("\uFEFF")) text = text.substring(1);
    List<String> references = new ArrayList<>(), missing = new ArrayList<>();
    Set<String> unique = new HashSet<>(), existing = new HashSet<>(folderNames);
    for (String line : text.split("\r?\n", -1)) {
      if (line.isEmpty() || line.startsWith("#")) continue;
      filename(line);
      if (!disc(line) || !unique.add(line) || references.size() >= MAX_DISCS)
        throw new IOException("Playlist needs distinct supported same-folder disc filenames");
      references.add(line);
      if (!existing.contains(line)) missing.add(line);
    }
    if (references.isEmpty()) throw new IOException("Playlist has no disc references");
    return new Check(references, missing);
  }

  static String read(InputStream in, StorageTools.Token token) throws Exception {
    if (in == null) throw new IOException("Cannot open playlist");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int n;
    while ((n = in.read(buffer)) != -1) {
      token.check();
      if (bytes.size() + n > MAX_BYTES) throw new IOException("Playlist exceeds 64 KB");
      bytes.write(buffer, 0, n);
    }
    token.check();
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes.toByteArray()))
        .toString();
  }

  static Check checkExisting(Context c, StorageTools.Entry playlist, StorageTools.Token token)
      throws Exception {
    if (!StorageTools.extension(playlist.name).equals("m3u"))
      throw new IOException("Select an M3U file");
    Set<String> siblings = folder(c, playlist.parentUri, token).keySet();
    try (ParcelFileDescriptor fd =
        c.getContentResolver().openFileDescriptor(playlist.uri, "r", token.signal)) {
      if (fd == null) throw new IOException("Provider refused playlist access");
      try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
        return check(read(in, token), siblings);
      }
    }
  }

  static Map<String, String> folder(Context c, Uri parent, StorageTools.Token token)
      throws Exception {
    Map<String, String> entries = new HashMap<>();
    Uri children =
        DocumentsContract.buildChildDocumentsUriUsingTree(
            parent, DocumentsContract.getDocumentId(parent));
    try (Cursor cursor =
        c.getContentResolver()
            .query(
                children,
                new String[] {
                  Document.COLUMN_DOCUMENT_ID,
                  Document.COLUMN_DISPLAY_NAME,
                  Document.COLUMN_MIME_TYPE
                },
                null,
                null,
                null,
                token.signal)) {
      if (cursor == null) throw new IOException("Provider refused folder listing");
      int count = 0;
      while (cursor.moveToNext()) {
        token.check();
        if (++count > StorageTools.MAX_NODES) throw new IOException("Folder has too many entries");
        if (Document.MIME_TYPE_DIR.equals(cursor.getString(2))) continue;
        String name = cursor.getString(1), id = cursor.getString(0);
        if (name == null || id == null || entries.put(name, id) != null)
          throw new IOException("Folder names are missing or ambiguous");
      }
    }
    return entries;
  }

  /**
   * Creates a new document only after current sibling names/IDs and exact write-folder grant match.
   */
  static Uri create(
      Context c, Uri writeTree, List<StorageTools.Entry> selected, StorageTools.Token token)
      throws Exception {
    String text = encode(selected);
    StorageTools.Entry first = selected.get(0);
    if (!DocumentsContract.isTreeUri(writeTree)
        || !Objects.equals(writeTree.getAuthority(), first.parentUri.getAuthority())
        || !DocumentsContract.getTreeDocumentId(writeTree).equals(first.parentId))
      throw new IOException("Choose the exact folder containing these disc files");
    if (c.checkUriPermission(
            writeTree,
            android.os.Process.myPid(),
            android.os.Process.myUid(),
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        != android.content.pm.PackageManager.PERMISSION_GRANTED)
      throw new SecurityException("A separate folder write grant is required");
    Uri parent = DocumentsContract.buildDocumentUriUsingTree(writeTree, first.parentId);
    Map<String, String> siblings = folder(c, parent, token);
    for (StorageTools.Entry e : selected)
      if (!e.id.equals(siblings.get(e.name)))
        throw new IOException("Disc names changed; scan the folder again");
    try (Cursor cursor =
        c.getContentResolver()
            .query(parent, new String[] {Document.COLUMN_FLAGS}, null, null, null, token.signal)) {
      if (cursor == null
          || !cursor.moveToFirst()
          || (cursor.getInt(0) & Document.FLAG_DIR_SUPPORTS_CREATE) == 0)
        throw new IOException("This provider cannot create new files in the selected folder");
    }
    String name = "Thorhaven-" + UUID.randomUUID().toString() + ".m3u";
    token.check();
    Uri created =
        DocumentsContract.createDocument(c.getContentResolver(), parent, "audio/x-mpegurl", name);
    if (created == null
        || !Objects.equals(created.getAuthority(), parent.getAuthority())
        || siblings.containsValue(DocumentsContract.getDocumentId(created)))
      throw new IOException(
          "Provider did not return a new playlist document; no bytes were written");
    try (Cursor cursor =
        c.getContentResolver()
            .query(
                created,
                new String[] {Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE},
                null,
                null,
                null,
                token.signal)) {
      if (cursor == null
          || !cursor.moveToFirst()
          || !name.equals(cursor.getString(0))
          || Document.MIME_TYPE_DIR.equals(cursor.getString(1)))
        throw new IOException("Provider changed the new file identity; no bytes were written");
    }
    token.check();
    try (ParcelFileDescriptor fd =
        c.getContentResolver().openFileDescriptor(created, "w", token.signal)) {
      if (fd == null) throw new IOException("Provider refused the new playlist file");
      try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(fd)) {
        out.write(text.getBytes(StandardCharsets.UTF_8));
      }
    } catch (Exception e) {
      throw new IOException(
          "The new playlist may be incomplete; original disc files were not changed", e);
    }
    return created;
  }

  private PlaylistTools() {}
}
