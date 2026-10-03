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
  static volatile String requestedDiagnostic = "";
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
  volatile String diagnosticMode = "";
  RgbSession session;
  boolean screenReceiverRegistered;
  final BroadcastReceiver screenReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
          if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())
              && (!diagnosticMode.isEmpty() || !requestedDiagnostic.isEmpty()))
            finishSession(
                RgbDiagnostics.text(
                    c,
                    "RGB-zonetest gestopt omdat de schermen uit zijn",
                    "RGB zone test stopped because the screens are off"));
        }
      };

  static synchronized void start(Context c) {
    if (!requestedDiagnostic.isEmpty()
        || (instance != null && !instance.diagnosticMode.isEmpty())) {
      Ui.toast(
          c,
          RgbDiagnostics.text(
              c,
              "Stop eerst de zonetest voordat je RGB start.",
              "Stop the zone test before starting RGB."));
      return;
    }
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

  static synchronized boolean startDiagnostic(Context c, String mode) {
    try {
      RgbDiagnostics.duration(mode);
    } catch (IllegalArgumentException invalid) {
      Ui.toast(c, invalid.getMessage());
      return false;
    }
    if (instance != null
        || requested
        || recovering
        || cancellationPending
        || RgbSession.recovery(c).contains("baseline")) {
      Ui.toast(
          c,
          RgbDiagnostics.text(
              c,
              "Stop en herstel eerst de bestaande RGB-sessie voordat je zones test.",
              "Stop and restore the existing RGB session before testing zones."));
      return false;
    }
    try {
      requested = true;
      requestedDiagnostic = mode;
      long generation = ++requestGeneration;
      c.startForegroundService(
          new Intent(c, RgbService.class)
              .putExtra("generation", generation)
              .putExtra("diagnostic", mode));
      return true;
    } catch (RuntimeException e) {
      requested = false;
      requestedDiagnostic = "";
      status =
          RgbDiagnostics.text(
                  c, "RGB-zonetest starten mislukt: ", "RGB zone test could not start: ")
              + e.getMessage();
      Ui.toast(c, status);
      return false;
    }
  }

  static synchronized void stopDiagnostic(Context c) {
    RgbService s = instance;
    if (!requestedDiagnostic.isEmpty() || (s != null && !s.diagnosticMode.isEmpty())) stop(c);
  }

  static synchronized void stop(Context c) {
    boolean pendingStart = requested;
    requested = false;
    requestedDiagnostic = "";
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

  static synchronized void recover(Context c) {
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
      IntentFilter screenFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
      if (Build.VERSION.SDK_INT >= 33)
        registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED);
      else registerReceiver(screenReceiver, screenFilter);
      screenReceiverRegistered = true;
    } catch (RuntimeException e) {
      requested = false;
      requestedDiagnostic = "";
      stopping = true;
      status = "RGB unavailable: " + e.getMessage();
      // onDestroy owns the instance/pending-cancellation cleanup, even if FGS setup fails.
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
      requestedDiagnostic = "";
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
    final String mode =
        intent.getStringExtra("diagnostic") == null ? "" : intent.getStringExtra("diagnostic");
    if (!mode.isEmpty()) {
      try {
        RgbDiagnostics.duration(mode);
      } catch (IllegalArgumentException invalid) {
        finishSession("Invalid RGB diagnostic mode");
        return START_NOT_STICKY;
      }
    }
    worker.post(
        () -> {
          if (session != null || stopping || !requested || generation != requestGeneration) return;
          try {
            diagnosticMode = mode;
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
          boolean awake = getSystemService(PowerManager.class).isInteractive();
          if (!diagnosticMode.isEmpty()) {
            if (!session.diagnosticTick(diagnosticMode, SystemClock.elapsedRealtime(), awake)) {
              finishSession(session.message);
              return;
            }
            boolean changed = !status.equals(session.message);
            status = session.message;
            if (changed)
              getSystemService(NotificationManager.class)
                  .notify(NOTIFICATION, notification(status));
            worker.postDelayed(this.tick, 500);
            return;
          }
          JSONObject options = RgbSettings.load(this);
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

  synchronized void finishSession(String reason) {
    if (stopping) return;
    requested = false;
    requestedDiagnostic = "";
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
    if (screenReceiverRegistered) {
      try {
        unregisterReceiver(screenReceiver);
      } catch (IllegalArgumentException ignored) {
      }
      screenReceiverRegistered = false;
    }
    stopping = true;
    final boolean ownedInstance = instance == this;
    if (ownedInstance) {
      requested = false;
      requestedDiagnostic = "";
      // Keep start/recovery excluded until the old worker has finished restoring hardware.
      cancellationPending = true;
      instance = null;
    }
    if (worker != null) {
      worker.removeCallbacks(tick);
      boolean accepted =
          worker.post(
              () -> {
                try {
                  if (session != null && !session.closed) {
                    session.close("RGB service stopped");
                    status = session.message;
                  }
                } finally {
                  thread.quitSafely();
                  main.post(
                      () -> {
                        if (ownedInstance && instance == null && !requested)
                          cancellationPending = false;
                      });
                }
              });
      if (!accepted && ownedInstance) cancellationPending = false;
    } else if (ownedInstance) cancellationPending = false;
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
