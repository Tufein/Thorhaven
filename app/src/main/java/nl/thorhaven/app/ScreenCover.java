package nl.thorhaven.app;

import android.content.Context;
import android.graphics.Color;
import android.view.*;
import android.widget.FrameLayout;

/** Black OLED curtain only: does not change panel power or pause the app beneath it. */
final class ScreenCover {
  final ThorService service;
  WindowManager manager;
  FrameLayout view;
  int display = -1;

  ScreenCover(ThorService s) {
    service = s;
  }

  boolean shown() {
    return view != null;
  }

  void show() {
    if (shown()) {
      hide();
      return;
    }
    int bottom = Store.screen(service, true), top = Store.screen(service, false);
    if (bottom < 0 || bottom == top) {
      Ui.toast(service, "Wijs eerst twee verschillende schermen toe.");
      return;
    }
    Display d =
        service.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(bottom);
    if (d == null) return;
    try {
      Context c = service.createDisplayContext(d);
      manager = c.getSystemService(WindowManager.class);
      FrameLayout black = new FrameLayout(c);
      black.setBackgroundColor(Color.BLACK);
      black.setContentDescription("Zwarte OLED-modus. Dubbeltik om terug te keren.");
      GestureDetector gesture =
          new GestureDetector(
              c,
              new GestureDetector.SimpleOnGestureListener() {
                public boolean onDown(android.view.MotionEvent e) {
                  return true;
                }

                public boolean onDoubleTap(android.view.MotionEvent e) {
                  hide();
                  return true;
                }
              });
      black.setOnTouchListener(
          (v, e) -> {
            if (e.getPointerCount() >= 3) {
              hide();
              return true;
            }
            return gesture.onTouchEvent(e);
          });
      WindowManager.LayoutParams p =
          new WindowManager.LayoutParams(
              -1,
              -1,
              WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
              WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                  | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
              android.graphics.PixelFormat.OPAQUE);
      p.screenBrightness = 0f;
      p.gravity = Gravity.FILL;
      manager.addView(black, p);
      view = black;
      display = bottom;
    } catch (Exception e) {
      view = null;
      display = -1;
      Ui.toast(service, "Zwarte modus niet beschikbaar: " + e.getClass().getSimpleName());
    }
  }

  void hide() {
    if (view != null) {
      try {
        manager.removeViewImmediate(view);
      } catch (Exception ignored) {
      }
      view = null;
      display = -1;
    }
  }
}
