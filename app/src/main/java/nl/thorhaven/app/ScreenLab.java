package nl.thorhaven.app;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Bounded, local-only capture helpers. Screen capture is always consented through Android. */
final class ScreenLab {
  static final int PREVIEW_PIXELS = 518400;
  static final int RECORD_PIXELS = 921600;
  static final int RECORD_SECONDS = 180;
  static final long RECORD_BYTES = 96L * 1024 * 1024;

  static RectF normalized(float left, float top, float right, float bottom) {
    if (!Float.isFinite(left)
        || !Float.isFinite(top)
        || !Float.isFinite(right)
        || !Float.isFinite(bottom)) return new RectF(0, 0, 1, 1);
    left = Math.max(0, Math.min(.99f, left));
    top = Math.max(0, Math.min(.99f, top));
    right = Math.max(left + .01f, Math.min(1, right));
    bottom = Math.max(top + .01f, Math.min(1, bottom));
    return new RectF(left, top, right, bottom);
  }

  static Rect pixels(RectF crop, int width, int height) {
    if (width < 1 || height < 1) throw new IllegalArgumentException("Empty capture");
    RectF r = normalized(crop.left, crop.top, crop.right, crop.bottom);
    int left = Math.min(width - 1, Math.max(0, (int) Math.floor(r.left * width)));
    int top = Math.min(height - 1, Math.max(0, (int) Math.floor(r.top * height)));
    return new Rect(
        left,
        top,
        Math.min(width, Math.max(left + 1, (int) Math.ceil(r.right * width))),
        Math.min(height, Math.max(top + 1, (int) Math.ceil(r.bottom * height))));
  }

  static int[] size(int width, int height, boolean record) {
    if (width < 1 || height < 1) throw new IllegalArgumentException("Empty display");
    double scale =
        Math.min(
            1d,
            Math.min(
                (record ? 1280d : 960d) / Math.max(width, height),
                Math.sqrt((record ? RECORD_PIXELS : PREVIEW_PIXELS) / ((double) width * height))));
    int w = Math.max(2, (int) Math.floor(width * scale) & ~1);
    int h = Math.max(2, (int) Math.floor(height * scale) & ~1);
    return new int[] {w, h};
  }

  static ByteBuffer frameBuffer(ByteBuffer source, int rowStride, int width, int height) {
    long total = (long) rowStride * height;
    long content = (long) rowStride * (height - 1) + (long) width * 4;
    if (width < 1 || height < 1 || rowStride < (long) width * 4 || total > 4400000)
      throw new IllegalArgumentException("Invalid frame buffer");
    ByteBuffer input = source.duplicate();
    input.rewind();
    if (input.remaining() < content) throw new IllegalArgumentException("Truncated frame buffer");
    if (input.remaining() >= total) {
      input.limit((int) total);
      return input;
    }
    // Some ImageReader providers omit only the unused final-row padding.
    // Add zeros there, without reading beyond the provider's actual buffer.
    ByteBuffer padded = ByteBuffer.allocate((int) total);
    padded.put(input);
    padded.rewind();
    return padded;
  }

  static File directory(Context c) {
    File f = new File(c.getFilesDir(), "screen-lab");
    if (!f.isDirectory() && !f.mkdirs())
      throw new IllegalStateException("Capture folder unavailable");
    return f;
  }

  static boolean validName(String name) {
    return name != null && name.matches("(?:shot|record)-[0-9]+-[0-9]+\\.(?:png|mp4)");
  }

  static File resolve(Context c, String name) throws IOException {
    if (!validName(name)) throw new IOException("Invalid capture name");
    File parent = directory(c).getCanonicalFile();
    File out = new File(parent, name).getCanonicalFile();
    if (!parent.equals(out.getParentFile())) throw new IOException("Invalid capture path");
    return out;
  }

  static List<File> files(Context c) {
    File[] listed = directory(c).listFiles(f -> f.isFile() && validName(f.getName()));
    if (listed == null) return new ArrayList<>();
    Arrays.sort(listed, Comparator.comparingLong(File::lastModified).reversed());
    return new ArrayList<>(Arrays.asList(listed));
  }

  static void trim(Context c) {
    int png = 0, mp4 = 0;
    for (File f : files(c)) {
      boolean keep = f.getName().endsWith(".png") ? ++png <= 6 : ++mp4 <= 3;
      if (!keep) f.delete();
    }
  }

  private ScreenLab() {}
}
