package nl.thorhaven.app;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.concurrent.*;
import rikka.shizuku.Shizuku;

final class Bridge {
  static volatile IThorBridge remote;
  static Context app;
  static boolean binding;
  static final ExecutorService worker = Executors.newSingleThreadExecutor();
  static Shizuku.UserServiceArgs args;
  static final ServiceConnection connection =
      new ServiceConnection() {
        public void onServiceConnected(ComponentName n, IBinder binder) {
          remote = IThorBridge.Stub.asInterface(binder);
          binding = false;
          Ui.toast(app, "Live schermwissels verbonden.");
        }

        public void onServiceDisconnected(ComponentName n) {
          remote = null;
          binding = false;
        }
      };

  static void init(Context c) {
    app = c.getApplicationContext();
    args =
        new Shizuku.UserServiceArgs(new ComponentName(app, SystemBridge.class))
            .daemon(false)
            .processNameSuffix("displaybridge")
            .tag("thorhaven.displaybridge")
            .version(3)
            .debuggable(false);
    Shizuku.addBinderReceivedListenerSticky(Bridge::bind);
    Shizuku.addBinderDeadListener(
        () -> {
          remote = null;
          binding = false;
        });
    Shizuku.addRequestPermissionResultListener(
        (r, g) -> {
          if (r == 84 && g == PackageManager.PERMISSION_GRANTED) bind();
        });
  }

  static boolean permitted() {
    try {
      return Shizuku.pingBinder()
          && !Shizuku.isPreV11()
          && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    } catch (Exception e) {
      return false;
    }
  }

  static void bind() {
    if (binding || remote != null || !permitted()) return;
    try {
      binding = true;
      Shizuku.bindUserService(args, connection);
    } catch (Exception e) {
      binding = false;
      Ui.toast(app, "Shizuku-koppeling mislukt.");
    }
  }

  static String status() {
    if (remote != null && remote.asBinder().isBinderAlive())
      return "Verbonden · live verplaatsen beschikbaar";
    try {
      if (!Shizuku.pingBinder()) return "Shizuku niet actief";
      if (!permitted()) return "Shizuku-toestemming nog nodig";
      return "Shizuku actief · koppel de service";
    } catch (Exception e) {
      return "Shizuku niet actief";
    }
  }

  static void request() {
    try {
      if (!Shizuku.pingBinder()) {
        Ui.toast(app, "Installeer en start Shizuku eerst. Zie de handleiding.");
        return;
      }
      if (permitted()) bind();
      else Shizuku.requestPermission(84);
    } catch (Exception e) {
      Ui.toast(app, "Shizuku kon geen toestemming vragen.");
    }
  }

  static void move(Context c, String pkg, int display) {
    run(c, () -> remote.movePackage(pkg, display));
  }

  static void swap(Context c) {
    int top = Store.screen(c, false), bottom = Store.screen(c, true);
    run(c, () -> remote.swapScreens(top, bottom));
  }

  interface Operation {
    String go() throws Exception;
  }

  static void run(Context c, Operation op) {
    if (remote == null || !permitted()) {
      Ui.toast(c, "Live verplaatsen vereist de Shizuku-koppeling bij Instellen.");
      return;
    }
    worker.execute(
        () -> {
          String message;
          try {
            message = op.go();
          } catch (Exception e) {
            remote = null;
            message = "Shizuku-verbinding verbroken. Koppel opnieuw bij Instellen.";
          }
          final String text = message;
          new Handler(Looper.getMainLooper()).post(() -> Ui.toast(app, text));
        });
  }
}
