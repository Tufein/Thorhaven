package nl.thorhaven.app;

import android.accessibilityservice.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.*;
import java.util.*;

public class ThorService extends AccessibilityService {
  public static ThorService instance;
  String foreground = "";
  int foregroundDisplay = 0;
  boolean select, ownFocused;
  Set<Integer> consumed = new HashSet<>();
  View panel;
  GuidePane guidePane;
  ScreenCover cover;
  PlayStats stats;
  WindowManager wm;
  final Handler handler = new Handler(Looper.getMainLooper());
  final Runnable timeout =
      () -> {
        select = false;
        consumed.clear();
      };

  @Override
  protected void onServiceConnected() {
    instance = this;
    cover = new ScreenCover(this);
    stats = new PlayStats(this);
    stats.start();
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent e) {
    if (e.getPackageName() == null) return;
    String p = e.getPackageName().toString();
    ownFocused = p.equals(getPackageName());
    if (stats != null && !p.equals("com.android.systemui") && !p.equals("android")) stats.focus(p);
    if (p.equals(getPackageName()) || p.equals("com.android.systemui") || p.equals("android"))
      return;
    foregroundDisplay = Build.VERSION.SDK_INT >= 33 ? e.getDisplayId() : Display.DEFAULT_DISPLAY;
    if (!p.equals(foreground)) {
      foreground = p;
      Store.recordRecent(this, p);
      Controls.foreground(this, p);
      HardwareAutomation.focus(this, p);
      RgbService.focus(this, p);
      if (Store.prefs(this).getBoolean("autoProfiles", false)) Store.apply(this, p);
    }
  }

  @Override
  public void onInterrupt() {
    hidePanel();
    select = false;
    consumed.clear();
  }

  @Override
  public void onDestroy() {
    hidePanel();
    TouchControls.hide();
    ControlLab.stop();
    HardwareAutomation.enabled = false;
    HardwareAutomation.focus(this, "");
    if (cover != null) cover.hide();
    if (stats != null) stats.stop();
    Controls.stop(this);
    RgbService.focus(this, "");
    instance = null;
    handler.removeCallbacksAndMessages(null);
    super.onDestroy();
  }

  @Override
  protected boolean onKeyEvent(KeyEvent e) {
    int k = e.getKeyCode();
    if (e.getAction() == KeyEvent.ACTION_UP && consumed.remove(k)) return true;
    if (panel != null
        && !Store.prefs(this).getBoolean("panelTouchOnly", false)
        && !select
        && (k == KeyEvent.KEYCODE_BUTTON_B || k == KeyEvent.KEYCODE_BACK)) {
      if (e.getAction() == KeyEvent.ACTION_DOWN) {
        hidePanel();
        consumed.add(k);
      }
      return true;
    }
    if (!Store.prefs(this).getBoolean("combos", false)
        || (ownFocused && panel == null && (cover == null || !cover.shown()))) {
      select = false;
      consumed.clear();
      return false;
    }
    if (k == KeyEvent.KEYCODE_BUTTON_SELECT) {
      select = e.getAction() == KeyEvent.ACTION_DOWN;
      handler.removeCallbacks(timeout);
      if (select) handler.postDelayed(timeout, 5000);
      return true;
    }
    if (e.getAction() == KeyEvent.ACTION_UP && consumed.remove(k)) return true;
    if (!select) return false;
    if (e.getAction() != KeyEvent.ACTION_DOWN || e.getRepeatCount() != 0)
      return consumed.contains(k);
    if (!Shortcuts.run(this, k)) return false;
    consumed.add(k);
    return true;
  }

  static int panelFlags(Context c) {
    return WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        | (Store.prefs(c).getBoolean("panelTouchOnly", false)
            ? WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            : 0);
  }

  void showGuide(String pkg) {
    hidePanel();
    if (cover != null) cover.hide();
    int id = Store.screen(this, true);
    if (id < 0) id = Store.screen(this, false);
    Display d = getSystemService(android.hardware.display.DisplayManager.class).getDisplay(id);
    if (d == null) return;
    try {
      Context c = createDisplayContext(d);
      wm = c.getSystemService(WindowManager.class);
      GuidePane reader = new GuidePane(c, pkg, this::hidePanel);
      guidePane = reader;
      android.util.DisplayMetrics m = Store.displayMetrics(this, d);
      WindowManager.LayoutParams lp =
          new WindowManager.LayoutParams(
              (int) (m.widthPixels * .96),
              (int) (m.heightPixels * .94),
              WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
              Store.prefs(c).getBoolean("guideNotes", false)
                      || OfflineGuides.meta(c, pkg).optString("kind").equals("text")
                  ? WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                  : panelFlags(c),
              android.graphics.PixelFormat.TRANSLUCENT);
      lp.gravity = Gravity.CENTER;
      wm.addView(reader, lp);
      guidePane = reader;
      panel = reader;
    } catch (Exception e) {
      if (guidePane != null) guidePane.close();
      guidePane = null;
      panel = null;
      Ui.toast(this, "Gids-overlay niet beschikbaar: " + e.getClass().getSimpleName());
    }
  }

  void showPanel() {
    if (panel != null) {
      hidePanel();
      return;
    }
    if (cover != null) cover.hide();
    int id = Store.screen(this, true);
    if (id < 0) id = Store.screen(this, false);
    android.view.Display d =
        getSystemService(android.hardware.display.DisplayManager.class).getDisplay(id);
    if (d == null) {
      Ui.toast(this, "Geen toegankelijk scherm.");
      return;
    }
    try {
      Context c = createDisplayContext(d);
      wm = c.getSystemService(WindowManager.class);
      ScrollView scroll = new ScrollView(c);
      scroll.setBackground(Ui.bg(c, Ui.BG));
      scroll.addView(QuickPanel.build(c, this::hidePanel));
      android.util.DisplayMetrics m = Store.displayMetrics(this, d);
      WindowManager.LayoutParams lp =
          new WindowManager.LayoutParams(
              Math.min(Ui.dp(c, 580), (int) (m.widthPixels * .94)),
              (int) (m.heightPixels * .92),
              WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
              panelFlags(c),
              android.graphics.PixelFormat.TRANSLUCENT);
      lp.gravity = Gravity.CENTER;
      scroll.setOnKeyListener(
          (v, k, e) -> {
            if (e.getAction() == 0
                && (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_BUTTON_B)) {
              hidePanel();
              return true;
            }
            return false;
          });
      panel = scroll;
      wm.addView(panel, lp);
    } catch (Exception e) {
      panel = null;
      Ui.toast(this, "Paneel niet beschikbaar: " + e.getClass().getSimpleName());
    }
  }

  void hidePanel() {
    if (guidePane != null) {
      guidePane.close();
      guidePane = null;
    }
    if (panel != null && wm != null) {
      try {
        wm.removeViewImmediate(panel);
      } catch (Exception ignored) {
      }
      panel = null;
    }
  }
}
