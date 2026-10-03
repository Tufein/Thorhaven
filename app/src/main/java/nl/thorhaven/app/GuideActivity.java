package nl.thorhaven.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.*;

public class GuideActivity extends Activity {
  GuidePane pane;

  public void onCreate(Bundle b) {
    super.onCreate(b);
    String pkg = getIntent().getStringExtra("pkg");
    if (!OfflineGuides.exists(this, pkg)) {
      finish();
      return;
    }
    pane = new GuidePane(this, pkg, this::finish);
    setContentView(pane);
  }

  protected void onDestroy() {
    if (pane != null) pane.close();
    super.onDestroy();
  }

  public boolean dispatchKeyEvent(KeyEvent e) {
    if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B) {
      finish();
      return true;
    }
    return super.dispatchKeyEvent(e);
  }
}
