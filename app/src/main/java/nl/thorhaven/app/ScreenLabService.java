package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.Display;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/** One explicitly consented capture at a time; never restarts capture after process death. */
public class ScreenLabService extends Service {
  static final String START = "nl.thorhaven.app.SCREEN_LAB_START";
  static final String STOP = "nl.thorhaven.app.SCREEN_LAB_STOP";
  static final int NOTICE = 601;
  final Handler main = new Handler(Looper.getMainLooper());
  final IBinder binder = new LocalBinder();
  HandlerThread thread;
  Handler worker;
  MediaProjection projection;
  VirtualDisplay display;
  ImageReader reader;
  MediaRecorder recorder;
  File recording;
  boolean recordingStarted;
  Bitmap frame;
  String mode = "idle", status = "Klaar voor schermdeling";
  Runnable changed;
  int density;
  long lastFrame;
  boolean saving;
  int generation;
  final AtomicBoolean frameQueued = new AtomicBoolean();
  Runnable timeout;

  public final class LocalBinder extends Binder {
    ScreenLabService service() {
      return ScreenLabService.this;
    }
  }

  MediaProjection.Callback callback;

  boolean currentSession(MediaProjection source, int token) {
    return source != null && projection == source && generation == token;
  }

  MediaProjection.Callback callbackFor(MediaProjection source, int token) {
    return new MediaProjection.Callback() {
      @Override
      public void onStop() {
        if (currentSession(source, token)) stopCapture("Schermdeling gestopt door Android");
      }

      @Override
      public void onCapturedContentResize(int width, int height) {
        if (!currentSession(source, token) || display == null || width < 1 || height < 1) return;
        if ("record".equals(mode)) {
          // A recorder cannot renegotiate its encoder dimensions halfway through an MP4.
          // Android may issue the initial callback too; only stop on a genuine aspect change.
          int[] size = ScreenLab.size(width, height, true);
          if (size[0] != recordWidth || size[1] != recordHeight)
            stopCapture("Opname opgeslagen na formaatwijziging");
        } else if ("mirror".equals(mode)) {
          try {
            resizeMirror(width, height);
          } catch (Exception e) {
            stopCapture("Schermdeling kan niet worden aangepast");
          }
        }
      }
    };
  }

  int recordWidth, recordHeight;

  @Override
  public void onCreate() {
    super.onCreate();
    thread = new HandlerThread("Thorhaven capture", android.os.Process.THREAD_PRIORITY_BACKGROUND);
    thread.start();
    worker = new Handler(thread.getLooper());
    NotificationManager manager = getSystemService(NotificationManager.class);
    manager.createNotificationChannel(
        new NotificationChannel(
            "screen-lab", "Thorhaven Screen Lab", NotificationManager.IMPORTANCE_LOW));
    // Interrupted recordings are never exposed as finished videos.
    File[] abandoned = ScreenLab.directory(this).listFiles(f -> f.getName().endsWith(".part"));
    if (abandoned != null) for (File f : abandoned) f.delete();
  }

  @Override
  public IBinder onBind(Intent i) {
    return binder;
  }

  @Override
  public int onStartCommand(Intent i, int flags, int startId) {
    if (i == null || STOP.equals(i.getAction())) {
      stopCapture("Schermdeling gestopt");
      return START_NOT_STICKY;
    }
    if (!START.equals(i.getAction())) {
      stopSelf(startId);
      return START_NOT_STICKY;
    }
    Intent consent = i.getParcelableExtra("consent");
    boolean record = i.getBooleanExtra("record", false);
    if (consent == null
        || i.getIntExtra("result", Activity.RESULT_CANCELED) != Activity.RESULT_OK) {
      stopCapture("Geen toestemming voor schermdeling");
      return START_NOT_STICKY;
    }
    endCapture("Schermdeling starten", false);
    try {
      startForeground(NOTICE, notice(record), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
      projection =
          getSystemService(MediaProjectionManager.class)
              .getMediaProjection(Activity.RESULT_OK, consent);
      if (projection == null) throw new IllegalStateException("Capture unavailable");
      callback = callbackFor(projection, generation);
      projection.registerCallback(callback, main);
      Display primary = getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
      if (primary == null) throw new IllegalStateException("Main screen unavailable");
      // A service launched from the lower display can carry that display's app bounds.
      // Query WindowManager through a context explicitly scoped to the capture source;
      // Display.getRealMetrics may otherwise apply the lower window's compatibility bounds.
      DisplayMetrics metrics = Store.displayMetrics(this, primary);
      density = Math.max(72, metrics.densityDpi);
      int[] size = ScreenLab.size(metrics.widthPixels, metrics.heightPixels, record);
      if (record) startRecording(size[0], size[1]);
      else startMirror(size[0], size[1]);
      notifyChanged();
    } catch (Exception e) {
      stopCapture("Schermdeling mislukt: " + e.getClass().getSimpleName());
    }
    return START_NOT_STICKY;
  }

  Notification notice(boolean record) {
    Intent open = new Intent(this, ScreenLabActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    PendingIntent content =
        PendingIntent.getActivity(
            this, 601, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent stop =
        PendingIntent.getService(
            this,
            602,
            new Intent(this, ScreenLabService.class).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    return new Notification.Builder(this, "screen-lab")
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setOngoing(true)
        .setContentTitle(
            record
                ? Language.text(this, "Schermopname actief")
                : Language.text(this, "Schermdeling actief"))
        .setContentText(Language.text(this, "Tik om te openen of stop de schermdeling"))
        .setContentIntent(content)
        .addAction(new Notification.Action.Builder(null, Language.text(this, "Stop"), stop).build())
        .build();
  }

  void startMirror(int width, int height) {
    mode = "mirror";
    reader = makeReader(width, height);
    display =
        projection.createVirtualDisplay(
            "Thorhaven mirror",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.getSurface(),
            null,
            main);
    status = "Live voorbeeld · maximaal 5 beelden per seconde";
  }

  ImageReader makeReader(int width, int height) {
    ImageReader source = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
    int token = generation;
    source.setOnImageAvailableListener(
        r -> {
          boolean ownsSlot = false, posted = false;
          try (Image image = r.acquireLatestImage()) {
            if (image == null) return;
            long now = SystemClock.elapsedRealtime();
            if (now - lastFrame < 200) return;
            if (!frameQueued.compareAndSet(false, true)) return;
            ownsSlot = true;
            lastFrame = now;
            Image.Plane plane = image.getPlanes()[0];
            if (plane.getPixelStride() != 4) return;
            int w = image.getWidth(), h = image.getHeight();
            int padded = plane.getRowStride() / 4;
            // Defensive bound independent of the capture provider's advertised dimensions.
            if (w < 1 || h < 1 || padded < w || padded * (long) h > 1100000) return;
            ByteBuffer buffer =
                ScreenLab.frameBuffer(plane.getBuffer(), plane.getRowStride(), w, h);
            Bitmap raw = Bitmap.createBitmap(padded, h, Bitmap.Config.ARGB_8888);
            buffer.rewind();
            try {
              raw.copyPixelsFromBuffer(buffer);
            } catch (Exception e) {
              raw.recycle();
              return;
            }
            Bitmap next = raw;
            if (padded != w) {
              next = Bitmap.createBitmap(raw, 0, 0, w, h);
              raw.recycle();
            }
            final Bitmap ready = next;
            main.post(
                () -> {
                  frameQueued.set(false);
                  if (generation != token || !"mirror".equals(mode) || reader != source) {
                    ready.recycle();
                    return;
                  }
                  Bitmap old = frame;
                  frame = ready;
                  notifyChanged();
                  if (old != null && !old.isRecycled()) old.recycle();
                });
            posted = true;
          } catch (IllegalStateException | IllegalArgumentException e) {
            // The reader may close while an already queued frame callback is being delivered.
          } finally {
            if (ownsSlot && !posted) frameQueued.set(false);
          }
        },
        worker);
    return source;
  }

  void resizeMirror(int width, int height) {
    int[] size = ScreenLab.size(width, height, false);
    if (reader != null && reader.getWidth() == size[0] && reader.getHeight() == size[1]) return;
    ImageReader replacement = makeReader(size[0], size[1]);
    ImageReader old = reader;
    boolean swapped = false;
    try {
      display.resize(size[0], size[1], density);
      display.setSurface(replacement.getSurface());
      reader = replacement;
      swapped = true;
    } finally {
      if (swapped) {
        if (old != null) old.close();
      } else replacement.close();
    }
  }

  void startRecording(int width, int height) throws IOException {
    mode = "record";
    recordWidth = width;
    recordHeight = height;
    recording =
        new File(
            ScreenLab.directory(this),
            "record-" + System.currentTimeMillis() + "-" + SystemClock.elapsedRealtime() + ".part");
    recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
    recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
    recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
    recorder.setVideoSize(width, height);
    recorder.setVideoFrameRate(30);
    recorder.setVideoEncodingBitRate(3000000);
    recorder.setMaxDuration(ScreenLab.RECORD_SECONDS * 1000);
    recorder.setMaxFileSize(ScreenLab.RECORD_BYTES);
    recorder.setOutputFile(recording.getAbsolutePath());
    MediaProjection source = projection;
    int token = generation;
    recorder.setOnInfoListener(
        (r, what, extra) -> {
          if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED
              || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)
            main.post(
                () -> {
                  if (currentSession(source, token)) stopCapture("Opnamelimiet bereikt");
                });
        });
    recorder.setOnErrorListener(
        (r, what, extra) ->
            main.post(
                () -> {
                  if (currentSession(source, token)) stopCapture("Opname gestopt na encoderfout");
                }));
    recorder.prepare();
    display =
        projection.createVirtualDisplay(
            "Thorhaven recorder",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder.getSurface(),
            null,
            main);
    recorder.start();
    recordingStarted = true;
    status = "Opname actief · zonder geluid · maximaal 3 minuten";
    timeout =
        () -> {
          if (currentSession(source, token)) stopCapture("Opnamelimiet bereikt");
        };
    main.postDelayed(timeout, ScreenLab.RECORD_SECONDS * 1000L + 1000);
  }

  void screenshot(RectF crop) {
    if (saving || frame == null || frame.isRecycled() || !"mirror".equals(mode)) {
      status = "Start eerst het live voorbeeld voor een schermafbeelding";
      notifyChanged();
      return;
    }
    Rect bounds = ScreenLab.pixels(crop, frame.getWidth(), frame.getHeight());
    Bitmap snapshot =
        Bitmap.createBitmap(frame, bounds.left, bounds.top, bounds.width(), bounds.height());
    // createBitmap can return the source for a full-size crop; own a separate worker copy.
    if (snapshot == frame) snapshot = frame.copy(Bitmap.Config.ARGB_8888, false);
    if (snapshot == null) return;
    saving = true;
    Bitmap copy = snapshot;
    worker.post(
        () -> {
          File part = null;
          String message;
          try {
            String name =
                "shot-" + System.currentTimeMillis() + "-" + SystemClock.elapsedRealtime() + ".png";
            File output = ScreenLab.resolve(this, name);
            part = new File(output.getAbsolutePath() + ".part");
            try (FileOutputStream stream = new FileOutputStream(part)) {
              if (!copy.compress(Bitmap.CompressFormat.PNG, 100, stream))
                throw new IOException("PNG failed");
              stream.getFD().sync();
            }
            if (!part.renameTo(output)) throw new IOException("Save failed");
            ScreenLab.trim(this);
            message = "Schermafbeelding opgeslagen · kies Exporteren om deze te bewaren";
          } catch (Exception e) {
            if (part != null) part.delete();
            message = "Schermafbeelding opslaan mislukt";
          } finally {
            copy.recycle();
          }
          String finalMessage = message;
          main.post(
              () -> {
                saving = false;
                status = finalMessage;
                notifyChanged();
              });
        });
  }

  void stopCapture(String message) {
    endCapture(message, true);
  }

  void endCapture(String message, boolean endStarted) {
    generation++;
    if (timeout != null) main.removeCallbacks(timeout);
    timeout = null;
    mode = "idle";
    // Unregister before stopping; callback recursion must not finalize the recorder twice.
    MediaProjection p = projection;
    projection = null;
    if (p != null) {
      try {
        if (callback != null) p.unregisterCallback(callback);
      } catch (RuntimeException ignored) {
      }
      try {
        p.stop();
      } catch (RuntimeException ignored) {
      }
    }
    callback = null;
    if (display != null) {
      try {
        display.release();
      } catch (RuntimeException ignored) {
      }
      display = null;
    }
    if (reader != null) {
      try {
        reader.close();
      } catch (RuntimeException ignored) {
      }
      reader = null;
    }
    if (recorder != null) {
      boolean valid = recordingStarted;
      try {
        if (recordingStarted) recorder.stop();
      } catch (RuntimeException e) {
        valid = false;
      }
      try {
        recorder.reset();
      } catch (RuntimeException ignored) {
      }
      try {
        recorder.release();
      } catch (RuntimeException ignored) {
      }
      recorder = null;
      recordingStarted = false;
      if (recording != null) {
        File completed =
            new File(recording.getParentFile(), recording.getName().replace(".part", ".mp4"));
        if (valid && recording.length() > 0 && recording.renameTo(completed)) {
          message = "Opname opgeslagen · kies Exporteren om deze te bewaren";
          ScreenLab.trim(this);
        } else {
          recording.delete();
          message = "Opname bevatte nog geen bruikbare beelden";
        }
        recording = null;
      }
    }
    if (frame != null) {
      frame.recycle();
      frame = null;
    }
    status = message;
    stopForeground(STOP_FOREGROUND_REMOVE);
    if (endStarted) stopSelf();
    notifyChanged();
  }

  void notifyChanged() {
    if (changed != null) changed.run();
  }

  @Override
  public void onDestroy() {
    changed = null;
    stopCapture("Schermdeling gestopt");
    if (thread != null) thread.quitSafely();
    super.onDestroy();
  }
}
