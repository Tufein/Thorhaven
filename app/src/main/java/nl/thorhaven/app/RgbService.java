package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import org.json.*;

/** User-started foreground lighting only; no automatic restart after a process/device restart. */
public class RgbService extends Service {
  static volatile RgbService instance;
  static volatile String status = "RGB stopped · AYN controls are available";
  static volatile boolean recovering;
  static volatile boolean requested;
  static volatile boolean cancellationPending;
  static volatile long requestGeneration;
  static RgbSession.Backend backend = RgbSession.HARDWARE;
  static final int NOTIFICATION = 71;
  static final String CHANNEL = "thorhaven-rgb";
  static final String STOP = "nl.thorhaven.app.RGB_STOP";
  static final java.util.concurrent.ExecutorService recoveryWorker =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  HandlerThread thread;
  Handler worker;
  final Handler main = new Handler(Looper.getMainLooper());
  volatile boolean stopping;
  volatile String foreground = "";
  RgbSession session;

  static void start(Context c) {
    if (recovering || cancellationPending || (instance != null && instance.stopping)) {
      Ui.toast(c, "RGB-herstel is bezig. Probeer daarna opnieuw.");
      return;
    }
    try {
      requested = true;
      long generation = ++requestGeneration;
      c.startForegroundService(new Intent(c, RgbService.class).putExtra("generation", generation));
    } catch (RuntimeException e) {
      requested = false;
      Ui.toast(c, "RGB starten mislukt: " + e.getMessage());
    }
  }

  static void stop(Context c) {
    boolean pendingStart = requested;
    requested = false;
    ++requestGeneration;
    RgbService s = instance;
    if (s != null) s.finishSession("RGB stopped");
    else if (pendingStart) {
      cancellationPending = true;
      // A queued startForegroundService must reach startForeground even when Stop arrives first.
      // Deliver cancellation as a service command instead of removing the pending creation.
      try {
        c.startForegroundService(new Intent(c, RgbService.class).setAction(STOP));
      } catch (RuntimeException e) {
        cancellationPending = false;
        status = "RGB start was cancelled: " + e.getMessage();
      }
    }
  }

  static void focus(Context c, String pkg) {
    RgbService s = instance;
    if (s != null && pkg != null && (pkg.isEmpty() || RgbSettings.packageName(pkg)))
      s.foreground = pkg;
  }

  static void notifySettings(Context c) {
    // The bounded worker reads one current settings snapshot on its next tick.
  }

  static void recover(Context c) {
    if (instance != null || requested || cancellationPending || recovering) {
      Ui.toast(c, "Stop eerst de actieve RGB-sessie.");
      return;
    }
    Context app = c.getApplicationContext();
    recovering = true;
    status = "Restoring AYN lighting";
    recoveryWorker.execute(
        () -> {
          try {
            String saved = RgbSession.recovery(app).getString("baseline", "");
            if (saved.isEmpty()) throw new Exception("No pending RGB recovery settings");
            JSONObject original = new JSONObject(saved);
            RgbHardware.validateBaseline(original);
            try {
              RgbHardware.Probe current = RgbHardware.probe(app);
              if (current.baseline != null) {
                RgbHardware.validateBaseline(current.baseline);
                original = current.baseline;
              }
            } catch (Exception ignored) {
            }
            RgbHardware.restore(app, original);
            if (!RgbSession.recovery(app).edit().remove("baseline").commit())
              throw new Exception("Could not clear the recovery record");
            status = "AYN lighting restored";
          } catch (Exception e) {
            status = "RGB restoration pending: " + e.getMessage();
          } finally {
            recovering = false;
            new Handler(Looper.getMainLooper()).post(() -> Ui.toast(app, status));
          }
        });
  }

  @Override
  public void onCreate() {
    super.onCreate();
    instance = this;
    thread = new HandlerThread("Thorhaven-RGB");
    thread.start();
    worker = new Handler(thread.getLooper());
    NotificationManager manager = getSystemService(NotificationManager.class);
    manager.createNotificationChannel(
        new NotificationChannel(CHANNEL, "Thorhaven RGB", NotificationManager.IMPORTANCE_LOW));
    foreground = ThorService.instance != null ? ThorService.instance.foreground : "";
    try {
      startForeground(NOTIFICATION, notification("Starting RGB Studio"));
    } catch (RuntimeException e) {
      requested = false;
      stopping = true;
      status = "RGB unavailable: " + e.getMessage();
      if (instance == this) instance = null;
      thread.quitSafely();
      stopSelf();
    }
  }

  Notification notification(String message) {
    int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    Intent open = new Intent(this, MainActivity.class).putExtra("pageKey", "RGB Studio");
    PendingIntent page = PendingIntent.getActivity(this, 71, open, flags);
    PendingIntent stop =
        PendingIntent.getService(
            this, 72, new Intent(this, RgbService.class).setAction(STOP), flags);
    return new Notification.Builder(this, CHANNEL)
        .setSmallIcon(nl.thorhaven.app.R.drawable.icon)
        .setContentTitle("Thorhaven RGB Studio")
        .setContentText(message)
        .setContentIntent(page)
        .setOngoing(true)
        .addAction(new Notification.Action.Builder(null, "Stop & restore", stop).build())
        .build();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int id) {
    if (intent != null && STOP.equals(intent.getAction())) {
      requested = false;
      ++requestGeneration;
      finishSession("RGB stopped");
      return START_NOT_STICKY;
    }
    if (intent == null
        || !requested
        || intent.getLongExtra("generation", -1) != requestGeneration) {
      if (!requested) finishSession("RGB stopped");
      return START_NOT_STICKY;
    }
    final long generation = requestGeneration;
    worker.post(
        () -> {
          if (session != null || stopping || !requested || generation != requestGeneration) return;
          try {
            session = new RgbSession(this, backend, SystemClock.elapsedRealtime());
            tick.run();
          } catch (Exception e) {
            finishSession("RGB unavailable: " + e.getMessage());
          }
        });
    return START_NOT_STICKY;
  }

  final Runnable tick =
      () -> {
        if (stopping || session == null) return;
        try {
          JSONObject options = RgbSettings.load(this);
          boolean awake = getSystemService(PowerManager.class).isInteractive();
          int level = PlayStats.level(this);
          boolean charging = PlayStats.plugged(this);
          int brightness =
              Math.max(
                  0,
                  Math.min(
                      100,
                      Settings.System.getInt(
                              getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1)
                          * 100
                          / 255));
          if (!session.tick(
              options,
              foreground,
              SystemClock.elapsedRealtime(),
              awake,
              level,
              charging,
              brightness)) {
            finishSession("RGB timer finished");
            return;
          }
          boolean changed = !status.equals(session.message);
          status = session.message;
          if (changed)
            getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(status));
          int fps = session.direct ? options.getInt("fps") : 2;
          worker.postDelayed(this.tick, session.paused ? 1000 : 1000 / fps);
        } catch (Exception e) {
          finishSession("RGB stopped after an error: " + e.getMessage());
        }
      };

  void finishSession(String reason) {
    if (stopping) return;
    requested = false;
    stopping = true;
    status = "Stopping RGB · restoring AYN lighting";
    worker.removeCallbacks(tick);
    worker.post(
        () -> {
          if (session != null) {
            session.close(reason);
            status = session.message;
          } else status = reason;
          main.post(
              () -> {
                Ui.toast(this, status);
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
              });
        });
  }

  @Override
  public void onDestroy() {
    stopping = true;
    requested = false;
    cancellationPending = false;
    if (instance == this) instance = null;
    if (worker != null) {
      worker.removeCallbacks(tick);
      worker.post(
          () -> {
            if (session != null && !session.closed) {
              session.close("RGB service stopped");
              status = session.message;
            }
            thread.quitSafely();
          });
    }
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
