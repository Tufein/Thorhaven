package nl.thorhaven.app;

import android.content.Context;
import android.graphics.*;
import android.widget.LinearLayout;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Local capture math/storage/UI checks. Does not automate Android's sharing consent. */
final class ScreenLabChecks {
  static void run(SmokeInstrumentation test, Context c, MainActivity a) throws Exception {
    android.hardware.display.DisplayManager displays =
        c.getSystemService(android.hardware.display.DisplayManager.class);
    android.view.Display primary = displays.getDisplay(android.view.Display.DEFAULT_DISPLAY);
    android.view.Display lower = displays.getDisplay(Store.screen(c, true));
    Context lowerContext = c.createDisplayContext(lower);
    android.util.DisplayMetrics upperMetrics = Store.displayMetrics(lowerContext, primary);
    android.util.DisplayMetrics lowerMetrics = Store.displayMetrics(lowerContext, lower);
    test.check(
        upperMetrics.widthPixels == 1920
            && upperMetrics.heightPixels == 1080
            && lowerMetrics.widthPixels == 1240
            && lowerMetrics.heightPixels == 1080,
        "Display metrics from the lower context retain the fixture's distinct 1920×1080 and"
            + " 1240×1080 screens; actual top="
            + upperMetrics.widthPixels
            + "x"
            + upperMetrics.heightPixels
            + ", bottom="
            + lowerMetrics.widthPixels
            + "x"
            + lowerMetrics.heightPixels
            + ", lowerId="
            + lower.getDisplayId());
    RectF invalid = ScreenLab.normalized(Float.NaN, -5, Float.POSITIVE_INFINITY, .1f);
    test.check(
        invalid.equals(new RectF(0, 0, 1, 1)),
        "Screen Lab rejects non-finite crop coordinates with a full-screen fallback");
    RectF crop = ScreenLab.normalized(-5, .8f, .01f, .2f);
    test.check(
        crop.left == 0
            && crop.top == .8f
            && crop.right >= .01f
            && crop.bottom > crop.top
            && crop.bottom <= 1,
        "Screen Lab normalizes reversed and out-of-range crop boundaries");
    Rect exact = ScreenLab.pixels(new RectF(.25f, .25f, .75f, .75f), 1920, 1080);
    test.check(
        exact.equals(new Rect(480, 270, 1440, 810)),
        "Normalized Screen Lab crop maps to the expected pixels");
    Rect tiny = ScreenLab.pixels(new RectF(.99f, .99f, .99f, .99f), 1, 1);
    test.check(
        tiny.equals(new Rect(0, 0, 1, 1)), "Screen Lab crops retain at least one in-bounds pixel");
    boolean rejected = false;
    try {
      ScreenLab.pixels(new RectF(0, 0, 1, 1), 0, 100);
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    test.check(rejected, "Screen Lab rejects empty capture dimensions");
    for (boolean recording : new boolean[] {false, true}) {
      int limit = recording ? ScreenLab.RECORD_PIXELS : ScreenLab.PREVIEW_PIXELS;
      for (int[] wh : new int[][] {{1920, 1080}, {1080, 1920}, {8000, 8000}, {1, 1}}) {
        int[] size = ScreenLab.size(wh[0], wh[1], recording);
        if (size[0] < 2
            || size[1] < 2
            || size[0] % 2 != 0
            || size[1] % 2 != 0
            || (long) size[0] * size[1] > limit)
          throw new Exception("Unbounded capture size " + Arrays.toString(size));
      }
    }
    test.check(
        true, "Preview and recorder allocations are bounded for portrait and large displays");
    ByteBuffer sparse = ByteBuffer.allocateDirect(20);
    for (int i = 0; i < 20; i++) sparse.put((byte) (i + 1));
    sparse.position(3);
    ByteBuffer padded = ScreenLab.frameBuffer(sparse, 12, 2, 2);
    test.check(
        padded.remaining() == 24
            && padded.get(19) == 20
            && padded.get(20) == 0
            && padded.get(23) == 0
            && sparse.position() == 3,
        "ImageReader final-row padding is completed without changing source data or its cursor");
    rejected = false;
    try {
      ScreenLab.frameBuffer(ByteBuffer.allocate(19), 12, 2, 2);
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    test.check(
        rejected,
        "Screen Lab rejects a buffer missing real pixels instead of exporting partial images");
    test.check(
        ScreenLab.RECORD_SECONDS == 180 && ScreenLab.RECORD_BYTES == 96L * 1024 * 1024,
        "Experimental silent recording has explicit duration and file-size limits");
    rejected = false;
    try {
      ScreenLab.resolve(c, "../escape.mp4");
    } catch (IOException expected) {
      rejected = true;
    }
    test.check(
        rejected && !ScreenLab.validName("record-1-1.part"),
        "Capture export rejects traversal and unfinished recordings");
    File sample = ScreenLab.resolve(c, "shot-1-1.png");
    try {
      Bitmap image = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
      image.eraseColor(Color.BLUE);
      try (FileOutputStream out = new FileOutputStream(sample)) {
        if (!image.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("PNG");
      } finally {
        image.recycle();
      }
      Bitmap restored = BitmapFactory.decodeFile(sample.getAbsolutePath());
      test.check(
          restored != null
              && restored.getWidth() == 4
              && restored.getPixel(0, 0) == Color.BLUE
              && ScreenLab.files(c).stream()
                  .anyMatch(
                      f -> {
                        try {
                          return f.getCanonicalFile().equals(sample.getCanonicalFile());
                        } catch (IOException e) {
                          return false;
                        }
                      }),
          "Private capture files retain a valid PNG and appear in the export list");
      if (restored != null) restored.recycle();
    } finally {
      sample.delete();
    }
    test.runOnMainSync(
        () -> {
          ScreenLabActivity lab = new ScreenLabActivity();
          // build() normally calls setContentView, so test the real preview in a host activity.
          ScreenLabActivity.Preview preview = lab.new Preview(a);
          LinearLayout host = Ui.col(a);
          host.addView(preview, new LinearLayout.LayoutParams(640, 360));
          host.measure(
              android.view.View.MeasureSpec.makeMeasureSpec(
                  640, android.view.View.MeasureSpec.EXACTLY),
              android.view.View.MeasureSpec.makeMeasureSpec(
                  360, android.view.View.MeasureSpec.EXACTLY));
          host.layout(0, 0, 640, 360);
          Bitmap rendered = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888);
          try {
            preview.draw(new Canvas(rendered));
          } finally {
            rendered.recycle();
          }
        });
    test.check(
        true, "Screen Lab preview safely renders an idle capture without permission or a service");
  }
}
