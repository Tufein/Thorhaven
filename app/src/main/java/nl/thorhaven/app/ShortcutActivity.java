package nl.thorhaven.app;

import android.app.Activity;
import android.os.Bundle;

/** Narrow launcher trampoline: no file paths, privileges or capture tokens accepted. */
public class ShortcutActivity extends Activity {
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    try {
      if (!android.content.Intent.ACTION_VIEW.equals(getIntent().getAction()))
        throw new Exception("Invalid shortcut action");
      String payload = getIntent().getStringExtra(LaunchShortcuts.EXTRA);
      if (payload == null || payload.length() > 1024) throw new Exception("Invalid shortcut");
      LaunchShortcuts.execute(this, StrictJson.object(payload, 1024));
    } catch (Exception e) {
      Ui.toast(
          this,
          LaunchShortcuts.text(this, "Snelkoppeling niet beschikbaar: ", "Shortcut unavailable: ")
              + e.getMessage());
    } finally {
      finish();
    }
  }
}
