package nl.thorhaven.app;

import android.content.*;
import android.net.Uri;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Bounded ZIP backup with validated staging and crash-recoverable rollback. */
final class CompleteBackup {
  static final long LIMIT = 128L * 1024 * 1024;
  // ZIP headers and DEFLATE can expand incompressible data. This allowance applies only to
  // the input archive; extracted guide and metadata limits remain unchanged.
  static final long ARCHIVE_LIMIT = LIMIT + SettingsBackup.MAX_BYTES + 1024 * 1024;
  static final String[] PREFS = {"thorhaven", "thorhaven-guides", "thorhaven-stats"};

  static void page(MainActivity a, LinearLayout parent) {
    LinearLayout c =
        Ui.card(
            a,
            parent,
            "Volledige back-up",
            "Inclusief gidsen, leesposities en speeltijd. Het ZIP-bestand bevat je persoonlijke"
                + " gegevens.");
    c.addView(
        Ui.button(
            a,
            "Volledige back-up exporteren",
            () ->
                a.startActivityForResult(
                    new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .setType("application/zip")
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .putExtra(Intent.EXTRA_TITLE, "thorhaven-complete-backup.zip"),
                    44)));
    c.addView(
        Ui.button(
            a,
            "Volledige back-up importeren",
            () ->
                a.startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .setType("*/*")
                        .putExtra(
                            Intent.EXTRA_MIME_TYPES,
                            new String[] {"application/zip", "application/octet-stream"})
                        .addCategory(Intent.CATEGORY_OPENABLE),
                    45)));
  }

  static JSONObject stats(Context c) throws Exception {
    JSONObject data = new JSONObject();
    for (Map.Entry<String, ?> e : c.getSharedPreferences("thorhaven-stats", 0).getAll().entrySet())
      if (e.getKey().equals("sleeps") || e.getKey().startsWith("time:"))
        data.put(e.getKey(), e.getValue());
    validateStats(data);
    return data;
  }

  static void validateStats(JSONObject data) throws Exception {
    if (data.length() > 3000) throw new IOException("Too many measurements");
    for (Iterator<String> it = data.keys(); it.hasNext(); ) {
      String k = it.next();
      if (k.startsWith("time:")) {
        OfflineGuides.valid(k.substring(5));
        Object v = data.get(k);
        StrictJson.integer(data, k, 0, 10L * 365 * 24 * 3600 * 1000);
      } else if (k.equals("sleeps")) {
        JSONArray rows = StrictJson.array(RgbSettings.string(data, k), 100000);
        if (rows.length() > 30) throw new IOException("Too many sessions");
        for (int i = 0; i < rows.length(); i++) {
          JSONObject r = rows.getJSONObject(i);
          RgbSettings.keys(r, "duration", "drop", "charged", "ended", "eligible");
          long duration = StrictJson.integer(r, "duration", 300000, 7L * 24 * 3600 * 1000);
          int drop = RgbSettings.integer(r, "drop", -100, 100);
          boolean charged = RgbSettings.bool(r, "charged");
          if (duration < 300000
              || duration > 7L * 24 * 3600 * 1000
              || drop < -100
              || drop > 100
              || StrictJson.integer(r, "ended", 0, Long.MAX_VALUE) < 0
              || RgbSettings.bool(r, "eligible")
                  != (!charged && drop >= 0 && duration >= 3L * 3600 * 1000))
            throw new IOException("Invalid battery session");
        }
      } else throw new IOException("Invalid measurement key");
    }
  }

  static synchronized void write(Context c, OutputStream output) throws Exception {
    synchronized (OfflineGuides.class) {
      JSONObject settings = Store.backup(c);
      Store.restore(c, settings.toString(), false);
      JSONObject manifest =
          new JSONObject()
              .put("format", "thorhaven-complete")
              .put("schema", 1)
              .put("settings", settings)
              .put("stats", stats(c));
      JSONArray guides = new JSONArray();
      List<File> files = new ArrayList<>();
      long total = 0;
      for (String pkg : OfflineGuides.prefs(c).getAll().keySet())
        if (OfflineGuides.exists(c, pkg)) {
          if (files.size() >= 64) throw new IOException("Complete backup supports up to 64 guides");
          File f = OfflineGuides.file(c, pkg);
          total += f.length();
          if (total > LIMIT) throw new IOException("Complete backup exceeds 128 MB");
          ExtraFeatures.validateGuide(OfflineGuides.meta(c, pkg));
          guides.put(
              new JSONObject()
                  .put("pkg", pkg)
                  .put("entry", "guides/" + f.getName())
                  .put("meta", OfflineGuides.meta(c, pkg)));
          files.add(f);
        }
      manifest.put("guides", guides);
      StrictJson.object(manifest.toString(), SettingsBackup.MAX_BYTES, 100000);
      byte[] json = manifest.toString().getBytes(StandardCharsets.UTF_8);
      if (json.length > SettingsBackup.MAX_BYTES) throw new IOException("Metadata exceeds 2 MB");
      try (ZipOutputStream zip = new ZipOutputStream(output)) {
        zip.putNextEntry(new ZipEntry("manifest.json"));
        zip.write(json);
        zip.closeEntry();
        for (File f : files) {
          zip.putNextEntry(new ZipEntry("guides/" + f.getName()));
          try (InputStream in = new FileInputStream(f)) {
            copy(in, zip, 16L * 1024 * 1024);
          }
          zip.closeEntry();
        }
      }
    }
  }

  static long copy(InputStream in, OutputStream out, long max) throws IOException {
    byte[] b = new byte[8192];
    long size = 0;
    int n;
    while ((n = in.read(b)) != -1) {
      size += n;
      if (size > max) throw new IOException("Backup entry too large");
      out.write(b, 0, n);
    }
    return size;
  }

  static final class Prepared implements AutoCloseable {
    final Context context;
    final File dir;
    final JSONObject manifest;
    final String prefsSuffix;

    Prepared(Context c, File d, JSONObject m, String suffix) {
      context = c;
      dir = d;
      manifest = m;
      prefsSuffix = suffix;
    }

    public void close() {
      erase(dir);
      context.getSharedPreferences("thorhaven-guides" + prefsSuffix, 0).edit().clear().commit();
    }
  }

  static Prepared prepare(Context c, InputStream input) throws Exception {
    File dir = new File(c.getCacheDir(), "backup-" + UUID.randomUUID());
    if (!dir.mkdirs()) throw new IOException("Cannot stage backup");
    String suffix = "-" + dir.getName();
    try {
      Set<String> names = new HashSet<>();
      long total = 0;
      File archive = new File(dir, "archive.zip");
      try (InputStream source = input;
          OutputStream out = new FileOutputStream(archive)) {
        copy(source, out, ARCHIVE_LIMIT);
      }
      try (ZipInputStream zip = new ZipInputStream(new FileInputStream(archive))) {
        ZipEntry e;
        while ((e = zip.getNextEntry()) != null) {
          String n = e.getName();
          if (!n.equals("manifest.json") && !n.matches("guides/[a-f0-9]{64}\\.guide"))
            throw new IOException("Unknown or unsafe ZIP path");
          if (e.isDirectory() || !names.add(n) || names.size() > 65)
            throw new IOException("Invalid ZIP entry");
          File file = new File(dir, n);
          file.getParentFile().mkdirs();
          try (OutputStream out = new FileOutputStream(file)) {
            total +=
                copy(
                    zip,
                    out,
                    n.equals("manifest.json") ? SettingsBackup.MAX_BYTES : 16L * 1024 * 1024);
          }
          if (total > LIMIT + SettingsBackup.MAX_BYTES)
            throw new IOException("Backup exceeds 128 MB");
          zip.closeEntry();
        }
      }
      // A local-entry stream alone can accept a ZIP truncated before its central directory.
      try (ZipFile central = new ZipFile(archive)) {
        Set<String> directoryNames = new HashSet<>();
        Enumeration<? extends ZipEntry> entries = central.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          if (!directoryNames.add(entry.getName()) || !names.contains(entry.getName()))
            throw new IOException("Inconsistent ZIP directory");
          File staged = new File(dir, entry.getName());
          if (entry.getSize() != staged.length()) throw new IOException("Inconsistent ZIP size");
          CRC32 crc = new CRC32();
          try (InputStream in = new FileInputStream(staged)) {
            byte[] bytes = new byte[8192];
            int n;
            while ((n = in.read(bytes)) != -1) crc.update(bytes, 0, n);
          }
          if (crc.getValue() != entry.getCrc()) throw new IOException("Inconsistent ZIP checksum");
        }
        if (!directoryNames.equals(names)) throw new IOException("Missing ZIP directory entries");
      }
      File mf = new File(dir, "manifest.json");
      if (!mf.isFile()) throw new IOException("Missing backup manifest");
      JSONObject m;
      try (InputStream manifestInput = new FileInputStream(mf)) {
        m = StrictJson.object(SettingsBackup.read(manifestInput), SettingsBackup.MAX_BYTES, 100000);
      }
      RgbSettings.keys(m, "format", "schema", "settings", "stats", "guides");
      if (!RgbSettings.string(m, "format").equals("thorhaven-complete")
          || RgbSettings.integer(m, "schema", 1, 1) != 1)
        throw new IOException("Unknown complete backup format");
      Store.restore(c, m.getJSONObject("settings").toString(), false);
      validateStats(m.getJSONObject("stats"));
      JSONArray guides = m.getJSONArray("guides");
      if (guides.length() > 64 || names.size() != guides.length() + 1)
        throw new IOException("Unexpected guide entries");
      Set<String> packages = new HashSet<>(), referenced = new HashSet<>();
      Context staged =
          new ContextWrapper(c) {
            public File getFilesDir() {
              return new File(dir, "validated");
            }

            public SharedPreferences getSharedPreferences(String n, int mode) {
              return super.getSharedPreferences(n + suffix, mode);
            }
          };
      for (int i = 0; i < guides.length(); i++) {
        JSONObject g = guides.getJSONObject(i), meta = g.getJSONObject("meta");
        String pkg = g.getString("pkg"), entry = g.getString("entry");
        OfflineGuides.valid(pkg);
        if (!packages.add(pkg)
            || !referenced.add(entry)
            || !names.contains(entry)
            || !entry.equals("guides/" + OfflineGuides.file(c, pkg).getName()))
          throw new IOException("Invalid guide identity");
        try (InputStream in = new FileInputStream(new File(dir, entry))) {
          OfflineGuides.importStream(staged, pkg, meta.getString("name"), in);
        }
        ExtraFeatures.validateGuide(meta);
        JSONObject actual = OfflineGuides.meta(staged, pkg);
        if (!actual.getString("kind").equals(meta.getString("kind"))
            || actual.getInt("pages") != meta.getInt("pages")
            || meta.getString("name").length() > 180
            || meta.getInt("page") < 0
            || meta.getInt("page") >= Math.max(1, actual.getInt("pages"))
            || meta.getInt("scroll") < 0
            || meta.getInt("scroll") > 10000000) throw new IOException("Invalid reading position");
      }
      return new Prepared(c, dir, m, suffix);
    } catch (Exception e) {
      erase(dir);
      c.getSharedPreferences("thorhaven-guides" + suffix, 0).edit().clear().commit();
      throw e;
    }
  }

  static void replacePrefs(Context c, String name, JSONObject values) throws Exception {
    SharedPreferences.Editor ed = c.getSharedPreferences(name, 0).edit().clear();
    for (Iterator<String> it = values.keys(); it.hasNext(); ) {
      String k = it.next();
      Object v = values.get(k);
      if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
      else if (v instanceof Number && (k.startsWith("time:") || k.equals("sleep:start")))
        ed.putLong(k, ((Number) v).longValue());
      else if (v instanceof Integer) ed.putInt(k, (Integer) v);
      else if (v instanceof Long) ed.putLong(k, (Long) v);
      else if (v instanceof Number) ed.putFloat(k, ((Number) v).floatValue());
      else if (v instanceof String) ed.putString(k, (String) v);
      else throw new IOException("Unsupported preference type");
    }
    if (!ed.commit()) throw new IOException("Could not save backup data");
  }

  static File journal(Context c) {
    return new File(c.getFilesDir(), "backup-rollback");
  }

  static synchronized void restore(Context c, Prepared p) throws Exception {
    synchronized (OfflineGuides.class) {
      recover(c);
      File journal = journal(c);
      if (!journal.mkdirs()) throw new IOException("Cannot prepare recovery journal");
      JSONObject rollback = new JSONObject(), prefs = new JSONObject();
      for (String name : PREFS)
        prefs.put(name, new JSONObject(c.getSharedPreferences(name, 0).getAll()));
      rollback.put("prefs", prefs);
      JSONArray guides = p.manifest.getJSONArray("guides"), oldFiles = new JSONArray();
      for (int i = 0; i < guides.length(); i++) {
        String pkg = guides.getJSONObject(i).getString("pkg");
        File file = OfflineGuides.file(c, pkg);
        oldFiles.put(new JSONObject().put("pkg", pkg).put("existed", file.isFile()));
        if (file.isFile())
          try (InputStream in = new FileInputStream(file);
              OutputStream out = new FileOutputStream(new File(journal, file.getName()))) {
            copy(in, out, 16L * 1024 * 1024);
          }
      }
      rollback.put("files", oldFiles);
      try (FileOutputStream out = new FileOutputStream(new File(journal, "journal.tmp"))) {
        out.write(rollback.toString().getBytes(StandardCharsets.UTF_8));
        out.getFD().sync();
      }
      android.system.Os.rename(
          new File(journal, "journal.tmp").getPath(), new File(journal, "journal.json").getPath());
      try {
        for (int i = 0; i < guides.length(); i++) {
          JSONObject g = guides.getJSONObject(i);
          String pkg = g.getString("pkg");
          File destination = OfflineGuides.file(c, pkg);
          android.system.Os.rename(
              new File(p.dir, "validated/guides/" + destination.getName()).getPath(),
              destination.getPath());
          if (!OfflineGuides.prefs(c)
              .edit()
              .putString(pkg, g.getJSONObject("meta").toString())
              .commit()) throw new IOException("Guide metadata write failed");
        }
        Store.restore(c, p.manifest.getJSONObject("settings").toString());
        replacePrefs(c, "thorhaven-stats", p.manifest.getJSONObject("stats"));
        if (!new File(journal, "journal.json").delete())
          throw new IOException("Could not finish backup transaction");
        erase(journal);
      } catch (Exception e) {
        recover(c);
        throw e;
      }
    }
  }

  static synchronized void recover(Context c) throws Exception {
    File dir = journal(c), file = new File(dir, "journal.json");
    if (!file.isFile()) {
      erase(dir);
      return;
    }
    JSONObject j = new JSONObject(new String(OfflineGuides.read(file), StandardCharsets.UTF_8));
    JSONArray files = j.getJSONArray("files");
    for (int i = 0; i < files.length(); i++) {
      JSONObject row = files.getJSONObject(i);
      File target = OfflineGuides.file(c, row.getString("pkg"));
      if (row.getBoolean("existed")) {
        File old = new File(dir, target.getName());
        if (old.exists())
          try (InputStream in = new FileInputStream(old);
              OutputStream out = new FileOutputStream(target)) {
            copy(in, out, 16L * 1024 * 1024);
          }
      } else target.delete();
    }
    JSONObject prefs = j.getJSONObject("prefs");
    for (String name : PREFS) replacePrefs(c, name, prefs.getJSONObject(name));
    if (!file.delete()) throw new IOException("Recovery journal could not be cleared");
    erase(dir);
  }

  static void erase(File f) {
    if (f.isDirectory()) {
      File[] files = f.listFiles();
      if (files != null) for (File p : files) erase(p);
    }
    f.delete();
  }

  static void exportUri(MainActivity a, Uri uri) {
    Ui.toast(a, "Bezig met back-up…");
    if (ThorService.instance != null && ThorService.instance.stats != null)
      ThorService.instance.stats.flush();
    OfflineGuides.worker.execute(
        () -> {
          String result;
          try (OutputStream out = a.getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IOException("Cannot open destination");
            write(a, out);
            result = "Back-up opgeslagen.";
          } catch (Exception e) {
            result = "Backup failed: " + e.getMessage();
          }
          String message = result;
          a.handler.post(() -> Ui.toast(a, message));
        });
  }

  static void importUri(MainActivity a, Uri uri) {
    Ui.toast(a, "Bezig met back-up…");
    if (ThorService.instance != null) {
      ThorService.instance.hidePanel();
      if (ThorService.instance.stats != null) ThorService.instance.stats.flush();
    }
    Context app = a.getApplicationContext();
    java.lang.ref.WeakReference<MainActivity> owner = new java.lang.ref.WeakReference<>(a);
    android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    OfflineGuides.worker.execute(
        () -> {
          String result;
          try {
            InputStream in = app.getContentResolver().openInputStream(uri);
            if (in == null) throw new IOException("Cannot read backup");
            try (Prepared prepared = prepare(app, in)) {
              restore(app, prepared);
            }
            result = "Back-up geïmporteerd.";
          } catch (Exception e) {
            result = "Backup failed: " + e.getMessage();
          }
          String message = result;
          main.post(
              () -> {
                MainActivity activity = owner.get();
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
                activity.render();
                Ui.toast(activity, message);
              });
        });
  }
}
