package nl.thorhaven.app;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.WeakHashMap;

/** Slow document providers never run on the activity's UI thread. */
final class SettingsBackup {
  static final int MAX_BYTES = 2_000_000;
  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static final WeakHashMap<MainActivity, Job> JOBS = new WeakHashMap<>();

  static final class Job {
    final Context app;
    final WeakReference<MainActivity> owner;
    volatile boolean cancelled;

    Job(MainActivity activity) {
      app = activity.getApplicationContext();
      owner = new WeakReference<>(activity);
    }

    void show(Runnable action) {
      MAIN.post(
          () -> {
            MainActivity activity = owner.get();
            if (!cancelled
                && activity != null
                && !activity.isFinishing()
                && !activity.isDestroyed()) action.run();
          });
    }
  }

  static void cancel(MainActivity activity) {
    Job job = JOBS.remove(activity);
    if (job != null) job.cancelled = true;
  }

  static String read(InputStream in) throws Exception {
    if (in == null) throw new IOException("Cannot open backup");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int count;
    while ((count = in.read(buffer)) != -1) {
      if (out.size() + count > MAX_BYTES) throw new IOException("Backup exceeds 2 MB");
      out.write(buffer, 0, count);
    }
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(out.toByteArray()))
        .toString();
  }

  static void result(MainActivity activity, int request, Uri uri) {
    if (uri == null || !"content".equals(uri.getScheme())) {
      Ui.toast(activity, "Kies een document met de Android-bestandskiezer.");
      return;
    }
    cancel(activity);
    Job job = new Job(activity);
    JOBS.put(activity, job);
    Ui.toast(
        activity, request == 41 ? "Back-up wordt opgeslagen…" : "Back-up wordt gecontroleerd…");
    new Thread(
            () -> {
              try {
                if (request == 41) {
                  String json = Store.backup(job.app).toString(2);
                  Store.restore(job.app, json, false);
                  if (job.cancelled) return;
                  try (OutputStream out =
                      job.app.getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new IOException("Cannot write backup");
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                  }
                  job.show(() -> Ui.toast(job.app, "Back-up opgeslagen."));
                } else {
                  String json;
                  try (InputStream in = job.app.getContentResolver().openInputStream(uri)) {
                    json = read(in);
                  }
                  Store.restore(job.app, json, false);
                  job.show(
                      () -> {
                        MainActivity owner = job.owner.get();
                        if (owner == null) return;
                        new Ui.Dialog(owner)
                            .setTitle("Back-up importeren?")
                            .setMessage(
                                "Instellingen in deze back-up overschrijven overeenkomstige"
                                    + " instellingen. Je andere gegevens blijven staan.")
                            .setPositiveButton("Importeren", (dialog, button) -> commit(job, json))
                            .setNegativeButton(
                                "Annuleren", (dialog, button) -> job.cancelled = true)
                            .show();
                      });
                }
              } catch (Exception error) {
                job.show(() -> Ui.toast(job.app, "Back-up mislukt: " + error.getMessage()));
              }
            },
            "thorhaven-settings-document")
        .start();
  }

  private static void commit(Job job, String json) {
    new Thread(
            () -> {
              try {
                if (job.cancelled) return;
                Store.restore(job.app, json);
                job.show(
                    () -> {
                      MainActivity owner = job.owner.get();
                      if (owner != null) owner.render();
                      Ui.toast(job.app, "Back-up geïmporteerd.");
                    });
              } catch (Exception error) {
                job.show(() -> Ui.toast(job.app, "Ongeldige back-up: " + error.getMessage()));
              }
            },
            "thorhaven-settings-commit")
        .start();
  }
}
