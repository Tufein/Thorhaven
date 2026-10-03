package nl.thorhaven.app;

public class ThorApp extends android.app.Application {
  @Override
  public void onCreate() {
    super.onCreate();
    Bridge.init(this);
  }
}
