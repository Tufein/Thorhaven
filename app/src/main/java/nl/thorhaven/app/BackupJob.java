package nl.thorhaven.app;

import android.app.job.*;

public class BackupJob extends JobService {
  public boolean onStartJob(JobParameters params) {
    if (!AutoBackup.prefs(this).getBoolean("enabled", false)) return false;
    OfflineGuides.worker.execute(
        () -> {
          AutoBackup.run(getApplicationContext());
          jobFinished(params, false);
        });
    return true;
  }

  public boolean onStopJob(JobParameters params) {
    return false;
  }
}
