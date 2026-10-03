package nl.thorhaven.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** A narrow Shizuku bridge: only inspect stacks and move a standard root task. */
public class SystemBridge extends IThorBridge.Stub {
  public SystemBridge() {}

  @Override
  public String deviceCall(String request) {
    return DeviceControl.call(request);
  }

  @Override
  public String currentApps(int topDisplay, int bottomDisplay) {
    try {
      if (topDisplay < 0 || bottomDisplay < 0 || topDisplay == bottomDisplay)
        throw new IOException("Wijs twee verschillende schermen toe.");
      String top = "", bottom = "";
      for (Task t : tasks()) {
        if (t.pkg.equals("nl.thorhaven.app")) continue;
        if (t.display == topDisplay && top.isEmpty()) top = t.pkg;
        if (t.display == bottomDisplay && bottom.isEmpty()) bottom = t.pkg;
      }
      if (top.isEmpty() || bottom.isEmpty() || top.equals(bottom))
        throw new IOException(
            "Open op beide schermen een verschillende app; startschermen tellen niet mee.");
      return new org.json.JSONObject().put("top", top).put("bottom", bottom).toString();
    } catch (Exception e) {
      return Controls.error(e.getMessage()).toString();
    }
  }

  @Override
  public void destroy() {
    System.exit(0);
  }

  static String command(String... args) throws Exception {
    Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
    ExecutorService io = Executors.newSingleThreadExecutor();
    Future<String> output =
        io.submit(
            () -> {
              ByteArrayOutputStream out = new ByteArrayOutputStream();
              try (InputStream in = p.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) {
                  out.write(buf, 0, n);
                  if (out.size() > 1_000_000) throw new IOException("Uitvoer te groot");
                }
              }
              return out.toString(StandardCharsets.UTF_8.name());
            });
    try {
      if (!p.waitFor(8, TimeUnit.SECONDS)) {
        p.destroyForcibly();
        throw new IOException("Systeemcommando duurt te lang");
      }
      String result = output.get(2, TimeUnit.SECONDS);
      if (p.exitValue() != 0 || result.contains("Exception") || result.contains("Error:"))
        throw new IOException(result.length() > 240 ? result.substring(0, 240) : result);
      return result;
    } finally {
      io.shutdownNow();
      p.destroy();
    }
  }

  static final class Task {
    int id, display;
    String pkg;

    Task(int i, int d, String p) {
      id = i;
      display = d;
      pkg = p;
    }
  }

  static List<Task> parse(String output) {
    List<Task> result = new ArrayList<>();
    Pattern sections =
        Pattern.compile(
            "(?:RootTask|Stack) id=(\\d+)[^\\n"
                + "]*displayId=(\\d+)[^\\n"
                + "]*\\n"
                + "(.*?)(?=(?:RootTask|Stack) id=|\\z)",
            Pattern.DOTALL);
    Matcher m = sections.matcher(output);
    while (m.find()) {
      String section = m.group(3);
      if (section.contains("activityType=home") || section.contains("activityType=recents"))
        continue;
      Matcher app =
          Pattern.compile(
                  "taskId=\\d+:[^\\n"
                      + "]*visible=true[^\\n"
                      + "]*topActivity=ComponentInfo\\{([A-Za-z0-9_.]+)/")
              .matcher(section);
      if (app.find())
        result.add(
            new Task(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), app.group(1)));
    }
    return result;
  }

  static List<Task> tasks() throws Exception {
    return parse(command("/system/bin/am", "stack", "list"));
  }

  static void move(Task t, int display) throws Exception {
    command(
        "/system/bin/am", "display", "move-stack", String.valueOf(t.id), String.valueOf(display));
    boolean found = false;
    for (Task x : tasks()) if (x.id == t.id && x.display == display) found = true;
    if (!found) throw new IOException("Android heeft de verplaatsing niet bevestigd");
  }

  @Override
  public synchronized String movePackage(String pkg, int display) {
    if (pkg == null || !pkg.matches("[A-Za-z0-9_.]{1,200}") || display < 0)
      return "Ongeldige app of schermkeuze.";
    try {
      for (Task task : tasks())
        if (task.pkg.equals(pkg)) {
          if (task.display == display) return "App staat al op dit scherm.";
          move(task, display);
          return "App live verplaatst naar scherm " + display + ".";
        }
      return "Geen zichtbare taak van deze app gevonden.";
    } catch (Exception e) {
      return "Verplaatsen geweigerd: " + e.getMessage();
    }
  }

  @Override
  public synchronized String swapScreens(int top, int bottom) {
    if (top < 0 || bottom < 0 || top == bottom) return "Kies twee verschillende schermen.";
    try {
      Task a = null, b = null;
      for (Task t : tasks()) {
        if (t.pkg.equals("nl.thorhaven.app")) continue;
        if (t.display == top && a == null) a = t;
        if (t.display == bottom && b == null) b = t;
      }
      if (a == null && b == null) return "Geen zichtbare apps om te wisselen.";
      if (a != null) move(a, bottom);
      try {
        if (b != null) move(b, top);
      } catch (Exception failure) {
        if (a != null)
          try {
            move(a, top);
          } catch (Exception ignored) {
          }
        throw failure;
      }
      return a != null && b != null
          ? "Apps op beide schermen gewisseld."
          : "App naar het andere scherm verplaatst.";
    } catch (Exception e) {
      return "Wisselen geweigerd: " + e.getMessage();
    }
  }
}
