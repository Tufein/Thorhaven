package nl.thorhaven.app;

public class ThorApp extends android.app.Application {
  @Override
  public void onCreate() {
    super.onCreate();
    try {
      CompleteBackup.recover(this);
    } catch (Exception e) {
      android.util.Log.e("Thorhaven", "Backup recovery could not complete", e);
    }
    Bridge.init(this);
  }
}
