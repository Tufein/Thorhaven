package nl.thorhaven.app;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.util.SparseArray;
import android.view.*;
import android.view.accessibility.*;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Real Android consent, projection, PNG and silent-MP4 checks on an isolated dual-display device.
 */
final class ScreenCaptureRuntimeChecks {
  static final int GREEN = Color.rgb(29, 150, 70), BLUE = Color.rgb(35, 80, 200);

  static void run(SmokeInstrumentation t, Context c, MainActivity a) throws Exception {
    Set<String> baseline = new HashSet<>();
    int pngCount = 0, videoCount = 0;
    for (File f : ScreenLab.files(c)) {
      baseline.add(f.getName());
      if (f.getName().endsWith(".png")) pngCount++;
      else videoCount++;
    }
    File[] existing = ScreenLab.directory(c).listFiles();
    if (existing != null) for (File f : existing) baseline.add(f.getName());
    if (pngCount >= 6 || videoCount >= 3)
      throw new Exception(
          "Capture runtime QA needs an isolated folder with a free PNG/video slot; existing"
              + " captures are preserved.");
    UiAutomation automation =
        t.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    AccessibilityServiceInfo originalInfo = automation.getServiceInfo();
    int originalFlags = originalInfo.flags;
    originalInfo.flags |=
        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
    automation.setServiceInfo(originalInfo);
    SharedPreferences prefs = c.getSharedPreferences("thorhaven-screen-lab", Context.MODE_PRIVATE);
    boolean hadNotificationChoice = prefs.contains("notificationAsked");
    boolean previousNotificationChoice = prefs.getBoolean("notificationAsked", false);
    final Pattern scene = new Pattern(a);
    ScreenLabActivity lab = null;
    ScreenLabService service = null;
    try {
      int bottom = Store.screen(c, true);
      t.check(
          bottom >= 0
              && bottom != Display.DEFAULT_DISPLAY
              && a.getDisplay().getDisplayId() == Display.DEFAULT_DISPLAY,
          "Capture runtime fixture has a primary source screen and a separate bottom screen");
      ActivityOptions options = ActivityOptions.makeBasic();
      options.setLaunchDisplayId(bottom);
      lab =
          (ScreenLabActivity)
              t.startActivitySync(
                  new Intent(c, ScreenLabActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                  options.toBundle());
      final ScreenLabActivity screen = lab;
      t.check(
          lab != null && lab.getDisplay().getDisplayId() == bottom,
          "Real Screen Lab activity opens on the assigned bottom display");
      awaitMain(
          t, 8000, () -> screen.service != null && screen.bound, "Screen Lab service did not bind");
      service = lab.service;
      final ScreenLabService capture = service;
      t.runOnMainSync(() -> screen.requestCapture(false));
      consent(automation, "denied preview", false);
      awaitMain(
          t,
          5000,
          () -> !screen.choosingCapture && released(capture),
          "Denied consent left capture resources active");
      t.check(true, "Cancelling real Android capture consent leaves no projection resources");
      t.runOnMainSync(() -> screen.requestCapture(false));
      t.runOnMainSync(() -> ScreenLabService.stopActive(c));
      consent(automation, "cancelled pending consent");
      awaitMain(
          t,
          5000,
          () -> !screen.choosingCapture && released(capture),
          "Late approval restarted an emergency-cancelled request");
      t.check(true, "Late Android consent cannot restart a request cancelled by emergency stop");
      t.runOnMainSync(() -> screen.requestCapture(false));
      consent(automation, "live preview");
      t.check(true, "Live preview starts through the real Android screen-sharing consent dialog");
      awaitMain(
          t,
          12000,
          () ->
              "mirror".equals(capture.mode)
                  && capture.projection != null
                  && capture.display != null
                  && capture.reader != null
                  && capture.frame != null,
          "Projection produced no live frame; inspect Screen Lab status and device capture"
              + " support");
      t.runOnMainSync(() -> ScreenLabService.stopActive(c));
      awaitMain(
          t,
          5000,
          () -> released(capture),
          "Emergency stop failed while Screen Lab remained bound");
      t.check(
          screen.bound && !screen.isDestroyed(),
          "Emergency stop releases projection while the Screen Lab activity stays bound");
      t.runOnMainSync(() -> screen.requestCapture(false));
      consent(automation, "preview after emergency stop");
      awaitMain(
          t,
          12000,
          () -> capture.frame != null && capture.projection != null,
          "Fresh consent did not restart preview after emergency stop");
      t.check(true, "Fresh consent can restart a bound preview after emergency stop");
      t.runOnMainSync(
          () ->
              capture.onStartCommand(
                  new Intent(c, ScreenLabService.class)
                      .setAction(ScreenLabService.START)
                      .putExtra("epoch", "cancelled-old-request")
                      .putExtra("consent", new Intent())
                      .putExtra("result", Activity.RESULT_OK),
                  0,
                  9901));
      t.check(
          capture.projection != null && "mirror".equals(capture.mode),
          "An already queued obsolete capture start cannot interrupt a newer active preview");
      t.runOnMainSync(() -> a.setContentView(scene));
      awaitMain(
          t,
          10000,
          () -> actualPixels(capture.frame),
          "Live frame did not contain the test pattern from the primary screen");
      t.check(true, "Real MediaProjection/ImageReader frames contain primary-screen test pixels");
      final int[] bounds = new int[2];
      RectF crop = new RectF(.25f, .2f, .75f, .8f);
      t.runOnMainSync(
          () -> {
            Rect r = ScreenLab.pixels(crop, capture.frame.getWidth(), capture.frame.getHeight());
            bounds[0] = r.width();
            bounds[1] = r.height();
            capture.screenshot(crop);
          });
      File png = awaitFile(c, baseline, ".png", 10000);
      Bitmap image = BitmapFactory.decodeFile(png.getAbsolutePath());
      try {
        t.check(
            image != null && image.getWidth() == bounds[0] && image.getHeight() == bounds[1],
            "Real screenshot PNG uses the selected crop dimensions");
        byte[] signature = new byte[8];
        try (InputStream in = new FileInputStream(png)) {
          if (in.read(signature) != 8) throw new IOException("Incomplete PNG");
        }
        t.check(
            png.length() > 64
                && Arrays.equals(signature, new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10})
                && near(image.getPixel(image.getWidth() / 4, image.getHeight() / 2), GREEN)
                && near(image.getPixel(image.getWidth() * 3 / 4, image.getHeight() / 2), BLUE),
            "Exported crop contains valid PNG bytes and the actual captured screen pixels");
      } finally {
        if (image != null) image.recycle();
      }
      awaitMain(t, 5000, () -> !capture.saving, "Screenshot worker did not finish");
      t.runOnMainSync(() -> capture.projection.stop());
      awaitMain(
          t, 5000, () -> released(capture), "Preview stop did not release projection resources");
      t.check(
          true,
          "Android projection stop callback releases projection, virtual display, reader and"
              + " frame");
      t.runOnMainSync(() -> screen.requestCapture(true));
      consent(automation, "silent recording");
      t.check(
          true,
          "Recording obtains fresh Android consent after the previous projection was stopped");
      awaitMain(
          t,
          12000,
          () ->
              "record".equals(capture.mode)
                  && capture.recordingStarted
                  && capture.recorder != null
                  && capture.projection != null
                  && capture.display != null,
          "Silent recording did not start; inspect MediaRecorder/encoder support and Screen Lab"
              + " status");
      final int[] videoSize = new int[2];
      final int[] expectedVideoSize = new int[2];
      t.runOnMainSync(
          () -> {
            videoSize[0] = capture.recordWidth;
            videoSize[1] = capture.recordHeight;
            Rect primary = primaryBounds(c);
            int[] expected = ScreenLab.size(primary.width(), primary.height(), true);
            expectedVideoSize[0] = expected[0];
            expectedVideoSize[1] = expected[1];
            a.setContentView(scene);
          });
      String recordingDiagnostic = diagnostic(t, c, capture);
      // A valid recorder must remain active until the test closes its controlling activity.
      // Do not silently accept a one-frame MP4 finalized by an unexpected initial resize.
      long recordingUntil = SystemClock.elapsedRealtime() + 2800;
      while (SystemClock.elapsedRealtime() < recordingUntil) {
        boolean[] recordingActive = {false};
        t.runOnMainSync(
            () ->
                recordingActive[0] =
                    "record".equals(capture.mode)
                        && capture.recordingStarted
                        && capture.projection != null
                        && capture.recorder != null);
        if (!recordingActive[0])
          throw new Exception(
              "Recording ended before the lab was closed: "
                  + diagnostic(t, c, capture)
                  + "; completedVideoDuration="
                  + completedDuration(c, baseline));
        Thread.sleep(100);
      }
      // Closing the actual activity must finalize its active recorder and stop projection.
      t.runOnMainSync(screen::finish);
      awaitMain(
          t,
          7000,
          () -> screen.isDestroyed() && released(capture),
          "Closing Screen Lab did not finalize the recording and release capture resources");
      File mp4 = awaitFile(c, baseline, ".mp4", 8000);
      MediaMetadataRetriever metadata = new MediaMetadataRetriever();
      try {
        metadata.setDataSource(mp4.getAbsolutePath());
        t.check(
            mp4.length() > 1024
                && Arrays.equals(videoSize, expectedVideoSize)
                && "yes"
                    .equals(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                && Integer.toString(videoSize[0])
                    .equals(
                        metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
                && Integer.toString(videoSize[1])
                    .equals(
                        metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)),
            "Closing active recording produces a playable MP4 with correct primary-screen video"
                + " dimensions "
                + Arrays.toString(expectedVideoSize)
                + "; "
                + recordingDiagnostic);
        long duration =
            Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        t.check(
            duration >= 1500 && duration <= 12000,
            "Recorded MP4 contains the expected short real video duration; actual="
                + duration
                + " ms; "
                + recordingDiagnostic
                + "; final="
                + diagnostic(t, c, capture));
        String audio = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO);
        MediaExtractor tracks = new MediaExtractor();
        boolean hasAudio = false, hasVideo = false;
        try {
          tracks.setDataSource(mp4.getAbsolutePath());
          for (int i = 0; i < tracks.getTrackCount(); i++) {
            String mime = tracks.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) hasAudio = true;
            if (mime != null && mime.startsWith("video/")) hasVideo = true;
          }
        } finally {
          tracks.release();
        }
        t.check(
            !"yes".equals(audio) && !hasAudio && hasVideo,
            "Silent recording has a video track and no audio track");
      } finally {
        metadata.release();
      }
      t.check(
          screen.isDestroyed() && released(capture),
          "Closing Screen Lab stops active recording and leaves no projection/recorder/frame"
              + " resources");
    } finally {
      final ScreenLabActivity closing = lab;
      final ScreenLabService stopping = service;
      t.runOnMainSync(
          () -> {
            scene.active = false;
            scene.removeCallbacks(scene.tick);
            if (stopping != null) stopping.stopCapture("Runtime QA cleanup");
            if (closing != null && !closing.isFinishing()) closing.finish();
            a.render();
          });
      // Only files generated by this test may be removed, including interrupted .part files.
      File[] generated = ScreenLab.directory(c).listFiles();
      if (generated != null)
        for (File f : generated) {
          String name = f.getName();
          if (!baseline.contains(name) && (ScreenLab.validName(name) || name.endsWith(".part")))
            f.delete();
        }
      SharedPreferences.Editor edit = prefs.edit();
      if (hadNotificationChoice) edit.putBoolean("notificationAsked", previousNotificationChoice);
      else edit.remove("notificationAsked");
      edit.commit();
      AccessibilityServiceInfo info = automation.getServiceInfo();
      info.flags = originalFlags;
      automation.setServiceInfo(info);
    }
  }

  static boolean released(ScreenLabService s) {
    return "idle".equals(s.mode)
        && s.projection == null
        && s.display == null
        && s.reader == null
        && s.recorder == null
        && s.frame == null
        && s.recording == null
        && !s.recordingStarted
        && s.timeout == null;
  }

  static Rect primaryBounds(Context c) {
    Display primary =
        c.getSystemService(android.hardware.display.DisplayManager.class)
            .getDisplay(Display.DEFAULT_DISPLAY);
    return c.getApplicationContext()
        .createDisplayContext(primary)
        .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        .getSystemService(WindowManager.class)
        .getMaximumWindowMetrics()
        .getBounds();
  }

  static String diagnostic(SmokeInstrumentation t, Context c, ScreenLabService capture) {
    final String[] result = {""};
    t.runOnMainSync(
        () -> {
          Display primary =
              c.getSystemService(android.hardware.display.DisplayManager.class)
                  .getDisplay(Display.DEFAULT_DISPLAY);
          android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
          primary.getRealMetrics(metrics);
          Rect actual = primaryBounds(c);
          result[0] =
              "mode="
                  + capture.mode
                  + ", status="
                  + capture.status
                  + ", recorder="
                  + capture.recordWidth
                  + "x"
                  + capture.recordHeight
                  + ", primaryMetrics="
                  + metrics.widthPixels
                  + "x"
                  + metrics.heightPixels
                  + ", explicitPrimaryBounds="
                  + actual.width()
                  + "x"
                  + actual.height()
                  + ", rotation="
                  + primary.getRotation();
        });
    return result[0];
  }

  static String completedDuration(Context c, Set<String> baseline) {
    for (File f : ScreenLab.files(c))
      if (!baseline.contains(f.getName()) && f.getName().endsWith(".mp4")) {
        MediaMetadataRetriever video = new MediaMetadataRetriever();
        try {
          video.setDataSource(f.getAbsolutePath());
          return video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) + " ms";
        } catch (Exception e) {
          return "unreadable: " + e.getClass().getSimpleName();
        } finally {
          try {
            video.release();
          } catch (Exception ignored) {
          }
        }
      }
    return "not finalized";
  }

  static boolean near(int actual, int expected) {
    return Math.abs(Color.red(actual) - Color.red(expected)) < 20
        && Math.abs(Color.green(actual) - Color.green(expected)) < 20
        && Math.abs(Color.blue(actual) - Color.blue(expected)) < 20;
  }

  static boolean actualPixels(Bitmap frame) {
    return frame != null
        && !frame.isRecycled()
        && frame.getWidth() > 8
        && frame.getHeight() > 8
        && near(frame.getPixel(frame.getWidth() / 4, frame.getHeight() / 2), GREEN)
        && near(frame.getPixel(frame.getWidth() * 3 / 4, frame.getHeight() / 2), BLUE);
  }

  static void awaitMain(
      SmokeInstrumentation t, int timeout, BooleanSupplier condition, String failure)
      throws Exception {
    long until = SystemClock.elapsedRealtime() + timeout;
    while (SystemClock.elapsedRealtime() < until) {
      boolean[] ready = {false};
      t.runOnMainSync(() -> ready[0] = condition.getAsBoolean());
      if (ready[0]) return;
      Thread.sleep(100);
    }
    throw new Exception(failure + " (bounded wait " + timeout + " ms)");
  }

  static File awaitFile(Context c, Set<String> before, String suffix, int timeout)
      throws Exception {
    long until = SystemClock.elapsedRealtime() + timeout;
    while (SystemClock.elapsedRealtime() < until) {
      for (File f : ScreenLab.files(c))
        if (!before.contains(f.getName()) && f.getName().endsWith(suffix) && f.length() > 0)
          return f;
      Thread.sleep(100);
    }
    throw new Exception(
        "Capture produced no completed "
            + suffix
            + " file; inspect Screen Lab status/encoder logs");
  }

  static void consent(UiAutomation automation, String phase) throws Exception {
    consent(automation, phase, true);
  }

  static void consent(UiAutomation automation, String phase, boolean approve) throws Exception {
    long until = SystemClock.elapsedRealtime() + 20000;
    String last = "No interactive windows";
    while (SystemClock.elapsedRealtime() < until) {
      StringBuilder seen = new StringBuilder();
      SparseArray<List<AccessibilityWindowInfo>> all = automation.getWindowsOnAllDisplays();
      for (int i = 0; i < all.size(); i++)
        for (AccessibilityWindowInfo w : all.valueAt(i)) {
          AccessibilityNodeInfo root = w.getRoot();
          if (root == null) continue;
          try {
            StringBuilder text = new StringBuilder();
            collect(root, text, 0);
            String labels = text.toString();
            if (seen.length() < 1600)
              seen.append("display ").append(all.keyAt(i)).append(": ").append(labels).append("; ");
            String pkg = root.getPackageName() == null ? "" : root.getPackageName().toString();
            if (!labels.toLowerCase(Locale.ROOT).contains("thorhaven")) continue;
            if (pkg.contains("systemui")
                && click(
                    root,
                    new HashSet<>(
                        approve
                            ? Arrays.asList(
                                "start now",
                                "nu starten",
                                "start",
                                "start recording",
                                "start sharing",
                                "nu opnemen")
                            : Arrays.asList("cancel", "annuleren", "not now", "niet nu")),
                    0)) return;
            if (pkg.contains("permissioncontroller"))
              click(root, new HashSet<>(Arrays.asList("allow", "toestaan")), 0);
          } finally {
            root.recycle();
          }
        }
      last = seen.toString();
      Thread.sleep(200);
    }
    throw new Exception(
        "Could not approve actual Android capture consent for "
            + phase
            + " within 20 s. Visible dialog labels: "
            + last);
  }

  static void collect(AccessibilityNodeInfo n, StringBuilder text, int depth) {
    if (depth > 15 || text.length() > 1500) return;
    if (n.getText() != null) text.append(n.getText()).append(" | ");
    for (int i = 0; i < n.getChildCount(); i++) {
      AccessibilityNodeInfo child = n.getChild(i);
      if (child != null)
        try {
          collect(child, text, depth + 1);
        } finally {
          child.recycle();
        }
    }
  }

  static boolean click(AccessibilityNodeInfo n, Set<String> labels, int depth) {
    if (depth > 15) return false;
    String text = n.getText() == null ? "" : n.getText().toString().trim().toLowerCase(Locale.ROOT);
    if (labels.contains(text) && n.isEnabled()) {
      if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
      AccessibilityNodeInfo parent = n.getParent();
      if (parent != null)
        try {
          if (parent.isClickable() && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            return true;
        } finally {
          parent.recycle();
        }
    }
    for (int i = 0; i < n.getChildCount(); i++) {
      AccessibilityNodeInfo child = n.getChild(i);
      if (child != null)
        try {
          if (click(child, labels, depth + 1)) return true;
        } finally {
          child.recycle();
        }
    }
    return false;
  }

  static final class Pattern extends View {
    final Paint paint = new Paint();
    final MainActivity host;
    boolean active = true;
    final Runnable tick = this::repaint;

    void repaint() {
      if (active) {
        // MainActivity can redraw when a capture virtual display is added.
        if (getParent() == null) host.setContentView(this);
        invalidate();
      }
    }

    Pattern(MainActivity c) {
      super(c);
      host = c;
    }

    @Override
    protected void onDraw(Canvas canvas) {
      canvas.drawColor(GREEN);
      paint.setColor(BLUE);
      canvas.drawRect(getWidth() / 2f, 0, getWidth(), getHeight(), paint);
      paint.setColor(Color.WHITE);
      int left = (int) ((SystemClock.elapsedRealtime() / 10) % Math.max(1, getWidth()));
      canvas.drawRect(
          left, getHeight() * .9f, Math.min(getWidth(), left + 60), getHeight() * .95f, paint);
      if (active) postDelayed(tick, 100);
    }
  }
}
