package nl.thorhaven.app;

import android.app.job.*;
import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Opt-in daily idle job; folder grants stay local and are never restored from backups. */
final class AutoBackup {
  static final int JOB = 5705;

  static android.content.SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("thorhaven-auto-backup", 0);
  }

  static void page(MainActivity a) {
    LinearLayout l =
        Ui.card(
            a,
            a.content,
            "Automatische lokale back-ups",
            "Android maakt dagelijks een back-up als het apparaat niet wordt gebruikt en oplaadt."
                + " De laatste 7 volledige back-ups blijven bewaard. Het tijdstip kan variëren;"
                + " geen cloud- of netwerktoegang.");
    l.addView(Ui.text(a, prefs(a).getString("status", "No folder selected"), 13, Ui.MUTED));
    l.addView(
        Ui.button(
            a,
            "Back-upmap kiezen",
            () ->
                a.startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        .addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),
                    46)));
    l.addView(
        Ui.button(
            a,
            "Nu een back-up maken",
            () ->
                OfflineGuides.worker.execute(
                    () -> {
                      String result = run(a.getApplicationContext());
                      a.handler.post(
                          () -> {
                            Ui.toast(a, result);
                            a.render();
                          });
                    })));
    l.addView(
        Ui.button(
            a,
            "Automatische back-ups uitschakelen",
            () -> {
              a.getSystemService(JobScheduler.class).cancel(JOB);
              prefs(a)
                  .edit()
                  .putBoolean("enabled", false)
                  .putString("status", "Automatic backups disabled")
                  .commit();
              a.render();
            }));
  }

  static void choose(MainActivity a, Uri uri, int flags) {
    try {
      a.getContentResolver()
          .takePersistableUriPermission(
              uri,
              flags
                  & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                      | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
      if (!uri.toString().equals(prefs(a).getString("folder", "")))
        prefs(a).edit().remove("completed").commit();
      prefs(a).edit().putString("folder", uri.toString()).putBoolean("enabled", true).commit();
      int result = schedule(a);
      prefs(a)
          .edit()
          .putString(
              "status",
              result == JobScheduler.RESULT_SUCCESS
                  ? "Daily backup enabled · waiting for Android"
                  : "Android could not schedule backups")
          .commit();
      a.render();
    } catch (Exception e) {
      Ui.toast(a, e.getMessage());
    }
  }

  static int schedule(Context c) {
    return c.getSystemService(JobScheduler.class)
        .schedule(
            new JobInfo.Builder(JOB, new ComponentName(c, BackupJob.class))
                .setPeriodic(24L * 3600 * 1000)
                .setRequiresDeviceIdle(true)
                .setRequiresCharging(true)
                .setPersisted(true)
                .build());
  }

  static synchronized String run(Context c) {
    Uri destination = null;
    boolean verified = false;
    try {
      String folder = prefs(c).getString("folder", "");
      if (folder.isEmpty()) throw new IOException("Choose a folder first");
      Uri tree = Uri.parse(folder),
          parent =
              DocumentsContract.buildDocumentUriUsingTree(
                  tree, DocumentsContract.getTreeDocumentId(tree));
      String name =
          "thorhaven-auto-"
              + String.format(Locale.ROOT, "%013d", System.currentTimeMillis())
              + ".zip";
      destination =
          DocumentsContract.createDocument(c.getContentResolver(), parent, "application/zip", name);
      if (destination == null) throw new IOException("Folder refused backup");
      try (OutputStream out = c.getContentResolver().openOutputStream(destination, "w")) {
        if (out == null) throw new IOException("Backup file unavailable");
        CompleteBackup.write(c, out);
      }
      // Verify readability before removing older app-owned archives.
      try (InputStream in = c.getContentResolver().openInputStream(destination);
          CompleteBackup.Prepared staged = CompleteBackup.prepare(c, in)) {}
      verified = true;
      org.json.JSONArray completed = new org.json.JSONArray(prefs(c).getString("completed", "[]"));
      completed.put(destination.toString());
      if (!prefs(c).edit().putString("completed", completed.toString()).commit())
        throw new IOException("Cannot save archive history");
      while (completed.length() > 7) {
        Uri oldest = Uri.parse(completed.getString(0));
        // Only delete archives this app successfully created and validated in this same folder.
        if (!oldest.getAuthority().equals(tree.getAuthority())
            || !DocumentsContract.getTreeDocumentId(oldest)
                .equals(DocumentsContract.getTreeDocumentId(tree)))
          throw new IOException("Invalid archive history");
        try {
          DocumentsContract.deleteDocument(c.getContentResolver(), oldest);
        } catch (java.io.FileNotFoundException alreadyRemoved) {
        }
        completed.remove(0);
        prefs(c).edit().putString("completed", completed.toString()).commit();
      }
      String status =
          "Backup completed · "
              + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date());
      prefs(c).edit().putString("status", status).commit();
      return status;
    } catch (Exception e) {
      if (destination != null && !verified)
        try {
          DocumentsContract.deleteDocument(c.getContentResolver(), destination);
        } catch (Exception ignored) {
        }
      String status =
          (verified ? "Backup saved; cleanup failed: " : "Backup failed: ") + e.getMessage();
      prefs(c).edit().putString("status", status).commit();
      return status;
    }
  }
}
