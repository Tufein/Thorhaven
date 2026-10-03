package nl.thorhaven.app;

import android.content.*;
import android.net.*;
import android.os.*;
import java.io.*;
import java.security.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.json.*;

final class Controls {
  static final ExecutorService worker = Executors.newSingleThreadExecutor();
  static final Handler main = new Handler(Looper.getMainLooper());
  static final ScheduledExecutorService pulse = Executors.newSingleThreadScheduledExecutor();
  static LocalSocket socket;
  static BufferedReader in;
  static BufferedWriter out;
  static volatile boolean active;
  static volatile String padStatus = "Remapping uit";
  static volatile String currentProfile = "";
  static String sha, source;

  static {
    pulse.scheduleWithFixedDelay(
        () -> {
          synchronized (Controls.class) {
            if (socket != null)
              try {
                String status = send("STATUS");
                active = status.equals("OK actief");
                padStatus =
                    active
                        ? "Remapping actief"
                        : "Remapping gestopt · oorspronkelijke controller vrij";
              } catch (Exception e) {
                close();
              }
          }
        },
        1,
        1,
        TimeUnit.SECONDS);
  }

  interface Job {
    String run() throws Exception;
  }

  static void async(Context c, Job job, Consumer<JSONObject> done) {
    worker.execute(
        () -> {
          JSONObject response;
          try {
            response = new JSONObject(job.run());
          } catch (Exception e) {
            response = error(e.getMessage());
          }
          JSONObject finalResult = response;
          main.post(() -> done.accept(finalResult));
        });
  }

  static JSONObject error(String message) {
    JSONObject j = new JSONObject();
    try {
      j.put("error", message == null ? "Bewerking mislukt" : message);
    } catch (Exception ignored) {
    }
    return j;
  }

  static String device(JSONObject q) throws Exception {
    IThorBridge remote = Bridge.remote;
    return remote != null ? remote.deviceCall(q.toString()) : DeviceControl.call(q.toString());
  }

  static void status(Context c, Consumer<JSONObject> cb) {
    async(c, () -> device(new JSONObject().put("op", "status")), cb);
  }

  static void apply(Context c, JSONObject values, Consumer<JSONObject> cb) {
    async(
        c,
        () -> {
          JSONObject captured =
              new JSONObject(device(new JSONObject().put("op", "capture").put("values", values)));
          if (captured.has("error")) return captured.toString();
          JSONObject before = new JSONObject(), after = new JSONObject();
          String saved = Store.prefs(c).getString("hw:snapshot", "");
          if (!saved.isEmpty()) {
            JSONObject old = new JSONObject(saved);
            before = old.getJSONObject("before");
            after = old.getJSONObject("after");
          }
          java.util.Iterator<String> keys = captured.keys();
          while (keys.hasNext()) {
            String k = keys.next();
            if (!before.has(k)) before.put(k, captured.get(k));
            after.put(k, values.get(k));
          }
          if (!Store.prefs(c)
              .edit()
              .putString(
                  "hw:snapshot",
                  new JSONObject().put("before", before).put("after", after).toString())
              .commit())
            throw new IOException("Herstelgegevens konden niet worden opgeslagen; niets gewijzigd");
          String response = device(new JSONObject().put("op", "apply").put("values", values));
          if (new JSONObject(response).has("error")
              && !new JSONObject(response).optString("error").contains("herstel mislukt")) {
            if (saved.isEmpty()) Store.prefs(c).edit().remove("hw:snapshot").commit();
            else Store.prefs(c).edit().putString("hw:snapshot", saved).commit();
          }
          return response;
        },
        cb);
  }

  static void restore(Context c, Consumer<JSONObject> cb) {
    async(
        c,
        () -> {
          String saved = Store.prefs(c).getString("hw:snapshot", "");
          if (saved.isEmpty())
            return new JSONObject().put("message", "Geen instellingen om te herstellen").toString();
          String result =
              device(new JSONObject().put("op", "restore").put("snapshot", new JSONObject(saved)));
          if (!new JSONObject(result).has("error"))
            Store.prefs(c).edit().remove("hw:snapshot").commit();
          return result;
        },
        cb);
  }

  static void prepare(Context c) throws Exception {
    File folder = c.getExternalFilesDir(null);
    if (folder == null) throw new IOException("Opslag voor invoerhelper niet beschikbaar");
    File f = new File(folder, "thorpad");
    try (InputStream input = c.getAssets().open("native/arm64-v8a/thorpad");
        OutputStream output = new FileOutputStream(f)) {
      byte[] b = new byte[8192];
      int n;
      while ((n = input.read(b)) != -1) output.write(b, 0, n);
    }
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream input = new FileInputStream(f)) {
      byte[] b = new byte[8192];
      int n;
      while ((n = input.read(b)) != -1) digest.update(b, 0, n);
    }
    StringBuilder hex = new StringBuilder();
    for (byte b : digest.digest())
      hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
    sha = hex.toString();
    source = f.getAbsolutePath();
  }

  static JSONObject nativeRequest(Context c, String op) throws Exception {
    prepare(c);
    return new JSONObject()
        .put("op", op)
        .put("source", source)
        .put("sha", sha)
        .put("uid", android.os.Process.myUid());
  }

  static void probe(Context c, Consumer<JSONObject> cb) {
    async(
        c,
        () -> {
          if (active)
            throw new IOException("Stop remapping voordat je opnieuw naar controllers zoekt");
          return device(nativeRequest(c, "probe"));
        },
        cb);
  }

  static synchronized String send(String command) throws Exception {
    if (socket == null) throw new IOException("Geen controllersessie");
    out.write(command + "\n");
    out.flush();
    String line = in.readLine();
    if (line == null || line.length() > 4096 || !line.startsWith("OK"))
      throw new IOException(line == null ? "Invoerverbinding verbroken" : line);
    return line;
  }

  static synchronized void close() {
    try {
      if (socket != null) socket.close();
    } catch (Exception ignored) {
    }
    socket = null;
    in = null;
    out = null;
    active = false;
    padStatus = "Remapping uit";
    currentProfile = "";
  }

  static void stop(Context c) {
    worker.execute(
        () -> {
          synchronized (Controls.class) {
            try {
              if (socket != null) send("QUIT");
            } catch (Exception ignored) {
            }
            close();
          }
          main.post(() -> Ui.toast(c, "Originele controller hersteld"));
        });
  }

  static void start(Context c, int event, String profile, Consumer<JSONObject> cb) {
    async(
        c,
        () -> {
          if (ThorService.instance == null)
            throw new IOException(
                "Activeer eerst de Thorhaven-toegankelijkheidsservice bij Instellen");
          synchronized (Controls.class) {
            if (socket != null) {
              try {
                send("QUIT");
              } finally {
                close();
              }
            }
            byte[] bytes = new byte[16];
            new SecureRandom().nextBytes(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            String nonce = hex.toString();
            JSONObject request = nativeRequest(c, "start").put("nonce", nonce);
            JSONObject response = new JSONObject(device(request));
            if (response.has("error")) return response.toString();
            LocalSocket candidate = null;
            for (int i = 0; i < 20; i++) {
              try {
                candidate = new LocalSocket();
                candidate.connect(
                    new LocalSocketAddress(
                        "thorhaven.pad." + android.os.Process.myUid() + "." + nonce,
                        LocalSocketAddress.Namespace.ABSTRACT));
                break;
              } catch (IOException e) {
                if (candidate != null) candidate.close();
                candidate = null;
                Thread.sleep(100);
              }
            }
            if (candidate == null)
              throw new IOException(
                  "Invoerhelper niet bereikbaar. Firmware of roottoegang ondersteunt deze"
                      + " invoerlaag niet.");
            socket = candidate;
            socket.setSoTimeout(2000);
            in =
                new BufferedReader(
                    new InputStreamReader(
                        socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            out =
                new BufferedWriter(
                    new OutputStreamWriter(
                        socket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
            try {
              send("AUTH " + nonce);
              send("START " + event);
              send(PadProfile.command(load(c, profile)));
              active = true;
              currentProfile = profile;
              padStatus = "Remapping actief";
            } catch (Exception e) {
              try {
                send("QUIT");
              } catch (Exception ignored) {
              }
              close();
              throw e;
            }
          }
          return new JSONObject()
              .put(
                  "message",
                  "Remapping actief. Select + Start drie seconden vasthouden is de noodstop.")
              .toString();
        },
        cb);
  }

  static JSONObject load(Context c, String pkg) throws Exception {
    String value = Store.prefs(c).getString("mapping:" + pkg, "");
    if (value.isEmpty() && !pkg.equals("global"))
      value = Store.prefs(c).getString("mapping:global", "");
    return value.isEmpty() ? PadProfile.defaults() : PadProfile.parse(value);
  }

  static void save(Context c, String pkg, JSONObject profile) throws Exception {
    PadProfile.parse(profile.toString());
    Store.prefs(c).edit().putString("mapping:" + pkg, profile.toString()).commit();
    if (active) foreground(c, currentProfile);
  }

  static void foreground(Context c, String pkg) {
    if (!active) return;
    worker.execute(
        () -> {
          synchronized (Controls.class) {
            if (!active) return;
            try {
              send(PadProfile.command(load(c, pkg)));
              currentProfile = pkg;
            } catch (Exception e) {
              try {
                send("QUIT");
              } catch (Exception ignored) {
              }
              close();
              main.post(() -> Ui.toast(c, "Remapping gestopt: " + e.getMessage()));
            }
          }
        });
  }
}
