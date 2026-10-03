package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

final class OfflineGuides {
  static final ExecutorService worker = Executors.newSingleThreadExecutor();

  static SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("thorhaven-guides", 0);
  }

  static void valid(String pkg) throws IOException {
    if (pkg == null || !pkg.matches("[A-Za-z0-9_.]{1,200}")) throw new IOException("Ongeldige app");
  }

  static File file(Context c, String pkg) throws Exception {
    valid(pkg);
    byte[] digest =
        MessageDigest.getInstance("SHA-256").digest(pkg.getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder();
    for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
    File folder = new File(c.getFilesDir(), "guides");
    if (!folder.exists() && !folder.mkdirs()) throw new IOException("Geen opslag voor gidsen");
    return new File(folder, hex + ".guide");
  }

  static JSONObject meta(Context c, String pkg) {
    try {
      return new JSONObject(prefs(c).getString(pkg, "{}"));
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  static boolean exists(Context c, String pkg) {
    try {
      return meta(c, pkg).has("kind") && file(c, pkg).isFile();
    } catch (Exception e) {
      return false;
    }
  }

  static synchronized String importStream(Context c, String pkg, String name, InputStream in)
      throws Exception {
    File destination = file(c, pkg),
        temp = new File(destination.getParentFile(), destination.getName() + ".tmp");
    boolean installed = false;
    File backup = new File(destination.getParentFile(), destination.getName() + ".bak");
    String oldMeta = prefs(c).getString(pkg, null);
    boolean movedOld = false, renamedNew = false;
    try {
      int total = 0;
      try (OutputStream out = new FileOutputStream(temp)) {
        byte[] bytes = new byte[8192];
        int n;
        while ((n = in.read(bytes)) != -1) {
          total += n;
          if (total > 16 * 1024 * 1024)
            throw new IOException("Maximaal 16 MB voor PDF, 2 MB voor tekst");
          out.write(bytes, 0, n);
        }
      }
      if (total == 0) throw new IOException("Leeg bestand");
      byte[] header = new byte[5];
      try (InputStream check = new FileInputStream(temp)) {
        check.read(header);
      }
      String kind;
      int pages = 0;
      if (Arrays.equals(header, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
        kind = "pdf";
        try (ParcelFileDescriptor fd =
                ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer pdf = new PdfRenderer(fd)) {
          pages = pdf.getPageCount();
          if (pages < 1 || pages > 3000)
            throw new IOException("PDF bevat geen bruikbaar aantal pagina's");
        }
      } else if (isImage(header)) {
        android.graphics.BitmapFactory.Options options =
            new android.graphics.BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(temp.getAbsolutePath(), options);
        if (options.outWidth < 1
            || options.outHeight < 1
            || options.outWidth > 16000
            || options.outHeight > 16000
            || (long) options.outWidth * options.outHeight > 64000000)
          throw new IOException("Image dimensions exceed 64 megapixels or 16,000 pixels");
        kind = "image";
      } else {
        kind = "text";
        if (total > 2 * 1024 * 1024) throw new IOException("Tekstgids maximaal 2 MB");
        byte[] text = read(temp);
        String decoded =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(text))
                .toString();
        if (decoded.indexOf('\0') >= 0) throw new IOException("Dit is geen tekstgids of PDF");
      }
      String title =
          name == null || name.trim().isEmpty()
              ? "Offline gids"
              : name.substring(0, Math.min(180, name.length()));
      JSONObject info =
          new JSONObject()
              .put("name", title)
              .put("kind", kind)
              .put("pages", pages)
              .put("page", 0)
              .put("scroll", 0);
      // Atomic replacement after validation: a rejected import leaves the prior guide intact.
      if (destination.exists()) {
        android.system.Os.rename(destination.getAbsolutePath(), backup.getAbsolutePath());
        movedOld = true;
      }
      android.system.Os.rename(temp.getAbsolutePath(), destination.getAbsolutePath());
      renamedNew = true;
      if (!prefs(c).edit().putString(pkg, info.toString()).commit())
        throw new IOException("Gidsgegevens konden niet worden opgeslagen");
      installed = true;
      backup.delete();
      return title;
    } finally {
      if (!installed) {
        temp.delete();
        if (renamedNew) destination.delete();
        if (movedOld) {
          destination.delete();
          android.system.Os.rename(backup.getAbsolutePath(), destination.getAbsolutePath());
        }
        if (oldMeta == null) prefs(c).edit().remove(pkg).commit();
        else prefs(c).edit().putString(pkg, oldMeta).commit();
      }
    }
  }

  static byte[] read(File f) throws Exception {
    try (InputStream in = new FileInputStream(f);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) {
        if (out.size() + n > 16 * 1024 * 1024) throw new IOException("Gids te groot");
        out.write(b, 0, n);
      }
      return out.toByteArray();
    }
  }

  static void importUri(MainActivity a, String pkg, Uri uri) {
    final String owner = a.guideImportOwner;
    worker.execute(
        () -> {
          String message;
          try {
            String name = "Offline gids";
            try (android.database.Cursor cursor =
                a.getContentResolver()
                    .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
              if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
            }
            try (InputStream in = a.getContentResolver().openInputStream(uri)) {
              if (in == null) throw new IOException("Bestand niet leesbaar");
              message = "Offline opgeslagen: " + importStream(a, pkg, name, in);

              if (owner != null && !owner.isEmpty() && !owner.equals(pkg)) {
                JSONObject m = meta(a, pkg).put("owner", owner);
                prefs(a).edit().putString(pkg, m.toString()).commit();
                Store.prefs(a).edit().putString("guideActive:" + owner, pkg).commit();
              }
            }
          } catch (Exception e) {
            message = "Gids niet opgeslagen: " + e.getMessage();
          }
          String finalMessage = message;
          a.handler.post(
              () -> {
                if (a.isDestroyed()) return;
                Ui.toast(a, finalMessage);
                a.render();
              });
        });
  }

  static void remember(Context c, String pkg, String key, int value) {
    JSONObject m = meta(c, pkg);
    if (!m.has("kind")) return;
    try {
      m.put(key, Math.max(0, value));
      prefs(c).edit().putString(pkg, m.toString()).apply();
    } catch (Exception ignored) {
    }
  }

  static boolean isImage(byte[] h) {
    return h.length >= 3
        && ((h[0] == (byte) 137 && h[1] == 80 && h[2] == 78)
            || (h[0] == (byte) 255 && h[1] == (byte) 216 && h[2] == (byte) 255));
  }

  static String owner(Context c, String id) {
    return meta(c, id).optString("owner", id);
  }

  static String active(Context c, String pkg) {
    if (meta(c, pkg).has("owner")) return pkg;
    String id = Store.prefs(c).getString("guideActive:" + pkg, pkg);
    return exists(c, id) && owner(c, id).equals(pkg) ? id : pkg;
  }

  static void open(Context c, String pkg) {
    pkg = active(c, pkg);
    if (!exists(c, pkg)) {
      Ui.toast(c, "Importeer eerst een PDF- of tekstgids via Gidsen.");
      return;
    }
    if (ThorService.instance != null) {
      ThorService.instance.showGuide(pkg);
      return;
    }
    int display = Store.screen(c, true);
    if (display < 0) display = Store.screen(c, false);
    try {
      c.startActivity(
          new Intent(c, GuideActivity.class)
              .putExtra("pkg", pkg)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
          ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle());
    } catch (Exception e) {
      Ui.toast(c, "Gids kon niet op dit scherm worden geopend.");
    }
  }

  static void remove(Context c, String pkg) {
    try {
      file(c, pkg).delete();
      prefs(c).edit().remove(pkg).commit();
    } catch (Exception ignored) {
    }
  }
}
