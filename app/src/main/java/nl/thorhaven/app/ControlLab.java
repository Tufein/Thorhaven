package nl.thorhaven.app;

import android.content.*;
import android.graphics.Point;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Experimental input tools. Every privileged action is typed, finite and explicitly started. */
final class ControlLab {
  static final int MAX_STEPS = 20, MAX_MACROS = 6, MAX_RUN_MS = 10000;
  static final ExecutorService worker = Executors.newSingleThreadExecutor();
  static final Handler main = new Handler(Looper.getMainLooper());
  static volatile Cancellation current;
  static volatile String state = "Invoerlaboratorium klaar";
  static JSONObject pendingPointer;
  static boolean pointerQueued;

  interface Backend {
    JSONObject send(JSONObject request) throws Exception;
  }

  interface Clock {
    long now();

    void pause(int ms) throws Exception;
  }

  static final class Cancellation {
    volatile boolean cancelled;

    void stop() {
      cancelled = true;
    }
  }

  static final Clock CLOCK =
      new Clock() {
        public long now() {
          return SystemClock.elapsedRealtime();
        }

        public void pause(int ms) throws Exception {
          Thread.sleep(ms);
        }
      };

  static JSONObject defaults() {
    try {
      return new JSONObject()
          .put("holdMs", 400)
          .put("turboCount", 6)
          .put("turboGap", 150)
          .put("turboEnabled", false)
          .put("pointerMode", "touch")
          .put("sensitivity", 100)
          .put("macros", new JSONArray());
    } catch (JSONException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  static JSONObject config(Context c) {
    try {
      String s = Store.prefs(c).getString("controlLab", defaults().toString());
      validate(s);
      return new JSONObject(s);
    } catch (Exception invalid) {
      return defaults();
    }
  }

  static void fields(JSONObject q, String... expected) throws Exception {
    Set<String> keys = new HashSet<>(Arrays.asList(expected));
    for (Iterator<String> i = q.keys(); i.hasNext(); )
      if (!keys.remove(i.next())) throw new IOException("Unknown input setting");
    if (!keys.isEmpty()) throw new IOException("Incomplete input settings");
  }

  static void validate(String s) throws Exception {
    if (s.length() > 40000) throw new IOException("Input settings too large");
    JSONObject q = new JSONObject(s);
    fields(
        q,
        "holdMs",
        "turboCount",
        "turboGap",
        "turboEnabled",
        "pointerMode",
        "sensitivity",
        "macros");
    DeviceControl.inputInt(q, "holdMs", 50, 1500);
    DeviceControl.inputInt(q, "turboCount", 2, 12);
    DeviceControl.inputInt(q, "turboGap", 100, 500);
    DeviceControl.inputInt(q, "sensitivity", 25, 300);
    if (!(q.get("turboEnabled") instanceof Boolean)) throw new IOException("Invalid turbo option");
    if (!Arrays.asList("touch", "mouse").contains(q.getString("pointerMode")))
      throw new IOException("Invalid pointer mode");
    JSONArray macros = q.getJSONArray("macros");
    if (macros.length() > MAX_MACROS) throw new IOException("Maximum six input macros");
    for (int i = 0; i < macros.length(); i++) validateMacro(macros.getJSONObject(i));
  }

  static void validateMacro(JSONObject macro) throws Exception {
    fields(macro, "name", "steps");
    String name = macro.getString("name");
    if (name.trim().isEmpty() || name.length() > 60 || name.matches("(?s).*[\\p{Cntrl}].*"))
      throw new IOException("Invalid macro name");
    JSONArray steps = macro.getJSONArray("steps");
    if (steps.length() == 0 || steps.length() > MAX_STEPS)
      throw new IOException("One to twenty macro steps required");
    int duration = 0;
    for (int i = 0; i < steps.length(); i++) {
      JSONObject step = steps.getJSONObject(i);
      fields(step, "code", "hold", "wait");
      int code = DeviceControl.inputInt(step, "code", 0, 255);
      int hold = DeviceControl.inputInt(step, "hold", 0, 1500);
      int wait = DeviceControl.inputInt(step, "wait", 0, 1500);
      if (!TouchControls.allowed(code) || (hold > 0 && hold < 50))
        throw new IOException("Invalid macro key");
      duration += hold + wait;
    }
    if (duration > MAX_RUN_MS) throw new IOException("Maximum ten seconds per macro");
  }

  static void save(Context c, JSONObject q) throws Exception {
    validate(q.toString());
    if (!Store.prefs(c).edit().putString("controlLab", q.toString()).commit())
      throw new IOException("Instellingen konden niet worden opgeslagen");
  }

  static JSONObject step(int code, int hold, int wait) throws JSONException {
    return new JSONObject().put("code", code).put("hold", hold).put("wait", wait);
  }

  static JSONObject burst(JSONObject settings, int code) throws Exception {
    validate(settings.toString());
    if (!settings.getBoolean("turboEnabled"))
      throw new IOException("Schakel turbo bewust in bij Invoerlaboratorium");
    JSONArray steps = new JSONArray();
    int count = settings.getInt("turboCount"), gap = settings.getInt("turboGap");
    for (int i = 0; i < count; i++) steps.put(step(code, 0, i + 1 == count ? 0 : gap));
    JSONObject macro = new JSONObject().put("name", "Turbo").put("steps", steps);
    validateMacro(macro);
    return macro;
  }

  /** Injectable execution for regression tests; validation finishes before any input is sent. */
  static int execute(
      JSONObject macro, int display, Backend backend, Cancellation cancel, Clock clock)
      throws Exception {
    validateMacro(macro);
    if (display < 0 || display > 1000) throw new IOException("Invalid target display");
    long started = clock.now();
    int completed = 0;
    JSONArray steps = macro.getJSONArray("steps");
    for (int i = 0; i < steps.length(); i++) {
      if (cancel.cancelled || clock.now() - started >= MAX_RUN_MS) break;
      JSONObject step = steps.getJSONObject(i);
      JSONObject request =
          new JSONObject()
              .put("op", "key")
              .put("code", step.getInt("code"))
              .put("display", display)
              .put("duration", step.getInt("hold"));
      JSONObject response = backend.send(request);
      if (response.has("error")) throw new IOException(response.optString("error"));
      completed++;
      int remaining = step.getInt("wait");
      while (remaining > 0 && !cancel.cancelled && clock.now() - started < MAX_RUN_MS) {
        int part = Math.min(remaining, 25);
        clock.pause(part);
        remaining -= part;
      }
    }
    return completed;
  }

  static synchronized void stop() {
    if (current != null) current.stop();
    current = null;
    pendingPointer = null;
    state = "Invoer gestopt";
  }

  static void run(Context c, JSONObject macro, int display) {
    try {
      validateMacro(macro);
      // Copy mutable editor data before moving it to the worker.
      JSONObject copy = new JSONObject(macro.toString());
      Cancellation token = new Cancellation();
      synchronized (ControlLab.class) {
        stop();
        current = token;
        state = "Invoer bezig";
      }
      worker.execute(
          () -> {
            String message;
            try {
              int sent =
                  execute(
                      copy,
                      display,
                      request -> new JSONObject(Controls.device(request)),
                      token,
                      CLOCK);
              message =
                  token.cancelled ? "Invoer gestopt" : "Invoer afgerond · " + sent + " stappen";
            } catch (Exception e) {
              message = "Invoer geweigerd: " + e.getMessage();
            }
            synchronized (ControlLab.class) {
              if (current == token) {
                current = null;
                state = message;
              }
            }
            String result = message;
            main.post(
                () -> {
                  if (!token.cancelled) Ui.toast(c, result);
                });
          });
    } catch (Exception e) {
      Ui.toast(c, e.getMessage());
    }
  }

  static void key(Context c, int code, String mode, int display) {
    try {
      JSONObject q = config(c);
      JSONObject macro =
          mode.equals("turbo")
              ? burst(q, code)
              : new JSONObject()
                  .put("name", "Tile")
                  .put(
                      "steps",
                      new JSONArray()
                          .put(step(code, mode.equals("hold") ? q.getInt("holdMs") : 0, 0)));
      run(c, macro, display);
    } catch (Exception e) {
      Ui.toast(c, e.getMessage());
    }
  }

  static void open(Context c) {
    TouchControls.show(c, true);
  }

  static void settings(MainActivity a, LinearLayout parent) {
    LinearLayout card =
        Ui.card(
            a,
            parent,
            "Invoerlaboratorium · experimenteel",
            "Korte macro's, begrensde knopdrukken, turbo en een aanraak-/muispad. Root-Shizuku of"
                + " AYN PServer nodig. Ondersteuning verschilt per app.");
    card.addView(
        Ui.text(
            a,
            "Een macro heeft maximaal 20 stappen en 10 seconden. Stop blokkeert volgende acties; de"
                + " lopende systeemactie wordt eerst afgerond. Een knopdruk duurt maximaal 1,5"
                + " seconde. Geen onbeperkt vasthouden of achtergrondturbo.",
            13,
            Ui.MUTED));
    JSONObject q = config(a);
    card.addView(
        Ui.button(
            a,
            "Knopduur · " + q.optInt("holdMs") + " ms",
            () -> choice(a, "Knopduur", new int[] {100, 250, 400, 750, 1000, 1500}, q, "holdMs")));
    card.addView(
        Ui.button(
            a,
            "Turbo-aantal · " + q.optInt("turboCount"),
            () -> choice(a, "Turbo-aantal", new int[] {2, 4, 6, 8, 12}, q, "turboCount")));
    card.addView(
        Ui.button(
            a,
            "Turbo-pauze · " + q.optInt("turboGap") + " ms",
            () -> choice(a, "Turbo-pauze", new int[] {100, 150, 250, 500}, q, "turboGap")));
    Switch enabled = new Switch(a);
    enabled.setText(Language.text(a, "Turbo bewust inschakelen · alleen eindige reeksen"));
    enabled.setTextColor(Ui.TEXT);
    enabled.setChecked(q.optBoolean("turboEnabled"));
    enabled.setOnCheckedChangeListener((b, checked) -> update(a, q, "turboEnabled", checked));
    card.addView(enabled);
    card.addView(
        Ui.button(
            a,
            "Padmodus · " + (q.optString("pointerMode").equals("mouse") ? "Muis" : "Aanraking"),
            () ->
                new Ui.Dialog(a)
                    .setTitle("Padmodus")
                    .setItems(
                        new String[] {"Aanraking", "Muis"},
                        (d, w) -> update(a, q, "pointerMode", w == 0 ? "touch" : "mouse"))
                    .show()));
    card.addView(
        Ui.button(
            a,
            "Padgevoeligheid · " + q.optInt("sensitivity") + "%",
            () ->
                choice(
                    a,
                    "Padgevoeligheid",
                    new int[] {25, 50, 75, 100, 150, 200, 300},
                    q,
                    "sensitivity")));
    card.addView(
        Ui.button(
            a,
            "Firmware-ondersteuning controleren",
            () ->
                Controls.async(
                    a,
                    () -> Controls.device(new JSONObject().put("op", "inputStatus")),
                    result -> {
                      if (a.isFinishing() || a.isDestroyed()) return;
                      new Ui.Dialog(a)
                          .setTitle("Invoermogelijkheden")
                          .setMessage(
                              result.has("error")
                                  ? result.optString("error")
                                  : "Begrensde knopdruk: "
                                      + (result.optBoolean("holds")
                                          ? "Ondersteund"
                                          : "Niet ondersteund")
                                      + "\nMuiscommando's: "
                                      + (result.optBoolean("mouse")
                                          ? "Beschikbaar"
                                          : "Niet bevestigd")
                                      + "\n"
                                      + "Een beschikbare opdracht bevestigt geen ondersteuning in"
                                      + " je game.")
                          .setPositiveButton("OK", null)
                          .show();
                    })));
    JSONArray macros = q.optJSONArray("macros");
    for (int i = 0; i < macros.length(); i++) {
      final int index = i;
      JSONObject macro = macros.optJSONObject(i);
      LinearLayout row = Ui.row(a);
      row.addView(
          Ui.rawButton(a, macro.optString("name"), () -> edit(a, index)),
          new LinearLayout.LayoutParams(0, -2, 1));
      row.addView(
          Ui.button(a, "Testen", () -> run(a, macro, Store.screen(a, false))),
          new LinearLayout.LayoutParams(-2, -2));
      card.addView(row);
    }
    if (macros.length() < MAX_MACROS)
      card.addView(Ui.button(a, "Macro toevoegen", () -> edit(a, -1)));
    card.addView(Ui.button(a, "Laboratorium openen op onderste scherm", () -> open(a)));
    card.addView(Ui.button(a, "Alle invoeracties stoppen", ControlLab::stop));
    card.addView(Ui.text(a, state, 13, Ui.MUTED));
  }

  static void update(MainActivity a, JSONObject q, String key, Object value) {
    try {
      q.put(key, value);
      save(a, q);
      a.render();
    } catch (Exception e) {
      Ui.toast(a, e.getMessage());
    }
  }

  static void choice(MainActivity a, String title, int[] values, JSONObject q, String key) {
    String[] labels = new String[values.length];
    for (int i = 0; i < labels.length; i++) labels[i] = Integer.toString(values[i]);
    new Ui.Dialog(a)
        .setTitle(title)
        .setItems(labels, (d, w) -> update(a, q, key, values[w]))
        .show();
  }

  static void edit(MainActivity a, int index) {
    try {
      JSONObject q = config(a);
      JSONArray macros = q.getJSONArray("macros");
      JSONObject macro =
          index < 0
              ? new JSONObject().put("name", "").put("steps", new JSONArray())
              : new JSONObject(macros.getJSONObject(index).toString());
      LinearLayout form = Ui.col(a);
      form.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 12));
      EditText name = Ui.input(a, "Naam van macro");
      name.setText(macro.optString("name"));
      form.addView(name);
      form.addView(
          Ui.text(
              a,
              "Elke stap geeft één knopdruk. Kies duur 0 voor een tik. Pas pauze na de stap aan."
                  + " Geen gelijktijdige knopcombinaties.",
              13,
              Ui.MUTED));
      LinearLayout rows = Ui.col(a);
      form.addView(rows);
      Runnable render = () -> renderSteps(a, rows, macro);
      render.run();
      form.addView(
          Ui.button(
              a,
              "Stap toevoegen",
              () -> {
                if (macro.optJSONArray("steps").length() >= MAX_STEPS) {
                  Ui.toast(a, "Maximaal 20 stappen");
                  return;
                }
                new Ui.Dialog(a)
                    .setTitle("Knop kiezen")
                    .setItems(
                        TouchControls.LABELS,
                        (d, w) -> {
                          try {
                            macro.getJSONArray("steps").put(step(TouchControls.CODES[w], 0, 150));
                            render.run();
                          } catch (Exception e) {
                            Ui.toast(a, e.getMessage());
                          }
                        })
                    .show();
              }));
      form.addView(
          Ui.button(
              a,
              "Laatste stap verwijderen",
              () -> {
                JSONArray steps = macro.optJSONArray("steps");
                if (steps.length() > 0) steps.remove(steps.length() - 1);
                render.run();
              }));
      ScrollView scroll = new ScrollView(a);
      scroll.addView(form);
      Ui.Dialog builder = new Ui.Dialog(a);
      builder.setTitle(index < 0 ? "Nieuwe macro" : "Macro bewerken");
      builder.setView(scroll);
      builder.setPositiveButton("Opslaan", null);
      builder.setNegativeButton("Annuleren", null);
      if (index >= 0)
        builder.setNeutralButton(
            "Verwijderen",
            (d, w) -> {
              macros.remove(index);
              try {
                save(a, q);
                a.render();
              } catch (Exception e) {
                Ui.toast(a, e.getMessage());
              }
            });
      android.app.AlertDialog dialog = builder.create();
      dialog.setOnShowListener(
          d ->
              dialog
                  .getButton(-1)
                  .setOnClickListener(
                      v -> {
                        try {
                          macro.put("name", name.getText().toString().trim());
                          validateMacro(macro);
                          if (index < 0) macros.put(macro);
                          else macros.put(index, macro);
                          save(a, q);
                          dialog.dismiss();
                          a.render();
                        } catch (Exception e) {
                          Ui.toast(a, e.getMessage());
                        }
                      }));
      dialog.show();
    } catch (Exception e) {
      Ui.toast(a, e.getMessage());
    }
  }

  static void renderSteps(MainActivity a, LinearLayout rows, JSONObject macro) {
    rows.removeAllViews();
    JSONArray steps = macro.optJSONArray("steps");
    for (int i = 0; i < steps.length(); i++) {
      JSONObject step = steps.optJSONObject(i);
      final int index = i;
      rows.addView(
          Ui.button(
              a,
              (i + 1)
                  + ". "
                  + TouchControls.LABELS[TouchControls.index(step.optInt("code"))]
                  + " · "
                  + step.optInt("hold")
                  + " ms · "
                  + step.optInt("wait")
                  + " ms",
              () -> {
                new Ui.Dialog(a)
                    .setTitle("Stap bewerken")
                    .setItems(
                        new String[] {"Knop kiezen", "Knopduur", "Pauze na stap"},
                        (d, selected) -> {
                          if (selected == 0)
                            new Ui.Dialog(a)
                                .setTitle("Knop kiezen")
                                .setItems(
                                    TouchControls.LABELS,
                                    (d2, w) -> {
                                      try {
                                        step.put("code", TouchControls.CODES[w]);
                                        renderSteps(a, rows, macro);
                                      } catch (Exception ignored) {
                                      }
                                    })
                                .show();
                          else {
                            int[] values =
                                selected == 1
                                    ? new int[] {0, 100, 250, 400, 750, 1000, 1500}
                                    : new int[] {0, 100, 150, 250, 500, 1000, 1500};
                            String[] labels = new String[values.length];
                            for (int j = 0; j < values.length; j++) labels[j] = values[j] + " ms";
                            new Ui.Dialog(a)
                                .setTitle(selected == 1 ? "Knopduur" : "Pauze na stap")
                                .setItems(
                                    labels,
                                    (d2, w) -> {
                                      try {
                                        steps
                                            .getJSONObject(index)
                                            .put(selected == 1 ? "hold" : "wait", values[w]);
                                        renderSteps(a, rows, macro);
                                      } catch (Exception ignored) {
                                      }
                                    })
                                .show();
                          }
                        })
                    .show();
              }));
    }
  }

  static void decorate(Context c, LinearLayout l, int display) {
    l.addView(
        Ui.text(
            c,
            "Stop beëindigt volgende acties. Lopende acties duren maximaal 1,5 seconde plus"
                + " systeemvertraging. Turbo stopt vanzelf. Knopduur werkt alleen als firmware"
                + " --duration ondersteunt.",
            12,
            Ui.MUTED));
    l.addView(Ui.button(c, "Alle invoeracties stoppen", ControlLab::stop));
    JSONArray macros = config(c).optJSONArray("macros");
    for (int i = 0; i < macros.length(); i++) {
      JSONObject macro = macros.optJSONObject(i);
      l.addView(Ui.rawButton(c, macro.optString("name"), () -> run(c, macro, display)));
    }
    l.addView(new Pad(c, display), new LinearLayout.LayoutParams(-1, Ui.dp(c, 180)));
  }

  /**
   * Coalesced pointer positions. Closing/stopping cancels pending positions and queued gestures.
   */
  static void pointer(Context c, JSONObject request, boolean movement) {
    try {
      DeviceControl.pointerCommand(request);
    } catch (Exception e) {
      Ui.toast(c, e.getMessage());
      return;
    }
    Cancellation token;
    synchronized (ControlLab.class) {
      if (!movement) stop();
      if (current == null) current = new Cancellation();
      token = current;
      if (movement) {
        pendingPointer = request;
        if (pointerQueued) return;
        pointerQueued = true;
      }
    }
    if (movement) {
      worker.execute(
          () -> {
            while (true) {
              JSONObject next;
              Cancellation nextToken;
              synchronized (ControlLab.class) {
                next = pendingPointer;
                pendingPointer = null;
                nextToken = current;
                if (next == null) {
                  pointerQueued = false;
                  return;
                }
              }
              if (nextToken == null || nextToken.cancelled) continue;
              try {
                JSONObject response = new JSONObject(Controls.device(next));
                if (response.has("error")) throw new IOException(response.optString("error"));
                Thread.sleep(100);
              } catch (Exception e) {
                main.post(
                    () -> {
                      if (!nextToken.cancelled) Ui.toast(c, "Invoer geweigerd: " + e.getMessage());
                    });
                synchronized (ControlLab.class) {
                  if (current == nextToken) {
                    nextToken.stop();
                    current = null;
                    pendingPointer = null;
                  }
                }
              }
            }
          });
    } else
      worker.execute(
          () -> {
            if (!token.cancelled)
              try {
                JSONObject response = new JSONObject(Controls.device(request));
                if (response.has("error")) throw new IOException(response.optString("error"));
              } catch (Exception e) {
                main.post(
                    () -> {
                      if (!token.cancelled) Ui.toast(c, "Invoer geweigerd: " + e.getMessage());
                    });
              } finally {
                synchronized (ControlLab.class) {
                  if (current == token) current = null;
                }
              }
          });
  }

  static final class Pad extends LinearLayout {
    final int display;
    final Point bounds = new Point();
    final Context c;
    final TextView status;
    int x, y, startX, startY;
    float fingerX, fingerY;
    boolean moved;
    long down;
    final JSONObject settings;

    Pad(Context c, int display) {
      super(c);
      this.c = c;
      this.display = display;
      settings = config(c);
      setOrientation(VERTICAL);
      setBackground(Ui.bg(c, Ui.CARD));
      Display target =
          c.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(display);
      if (target != null) {
        android.util.DisplayMetrics metrics = Store.displayMetrics(c, target);
        bounds.set(metrics.widthPixels, metrics.heightPixels);
      }
      bounds.x = Math.max(1, Math.min(8192, bounds.x));
      bounds.y = Math.max(1, Math.min(8192, bounds.y));
      x = bounds.x / 2;
      y = bounds.y / 2;
      status = Ui.text(c, "Pad · sleep om te richten · tik voor klik", 13, Ui.MUTED);
      addView(status);
      TextView surface = Ui.text(c, "Aanraak-/muispad · experimenteel", 16, Ui.TEXT);
      surface.setGravity(Gravity.CENTER);
      surface.setBackground(Ui.bg(c, 0xff20354b));
      addView(surface, new LayoutParams(-1, 0, 1));
      surface.setOnTouchListener(
          (v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
              stop();
              fingerX = e.getX();
              fingerY = e.getY();
              startX = x;
              startY = y;
              moved = false;
              down = SystemClock.elapsedRealtime();
              v.getParent().requestDisallowInterceptTouchEvent(true);
              return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
              float dx = e.getX() - fingerX, dy = e.getY() - fingerY;
              if (Math.abs(dx) + Math.abs(dy) > Ui.dp(c, 8)) moved = true;
              float sensitivity = settings.optInt("sensitivity", 100) / 100f;
              x =
                  clamp(
                      startX + Math.round(dx * bounds.x / Math.max(1, v.getWidth()) * sensitivity),
                      bounds.x);
              y =
                  clamp(
                      startY + Math.round(dy * bounds.y / Math.max(1, v.getHeight()) * sensitivity),
                      bounds.y);
              status.setText("Pad · " + x + ", " + y);
              if (settings.optString("pointerMode").equals("mouse")) send("move", x, y, 0);
              return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
              if (!moved && SystemClock.elapsedRealtime() - down < 600) send("tap", x, y, 0);
              v.getParent().requestDisallowInterceptTouchEvent(false);
              return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
              stop();
              v.getParent().requestDisallowInterceptTouchEvent(false);
              return true;
            }
            return true;
          });
      LinearLayout buttons = Ui.row(c);
      buttons.addView(Ui.button(c, "Klik", () -> send("tap", x, y, 0)), new LayoutParams(0, -2, 1));
      buttons.addView(
          Ui.button(c, "Omhoog scrollen", () -> scroll(-1)), new LayoutParams(0, -2, 1));
      buttons.addView(Ui.button(c, "Omlaag scrollen", () -> scroll(1)), new LayoutParams(0, -2, 1));
      addView(buttons);
    }

    static int clamp(int value, int bound) {
      return Math.max(0, Math.min(bound - 1, value));
    }

    void scroll(int direction) {
      // A finite touchscreen swipe for apps that do not expose a mouse wheel.
      int mid = bounds.y / 2, end = clamp(mid - direction * Math.max(1, bounds.y / 4), bounds.y);
      try {
        JSONObject q =
            base("touch", "swipe", bounds.x / 2, mid)
                .put("x2", bounds.x / 2)
                .put("y2", end)
                .put("duration", 250);
        pointer(c, q, false);
      } catch (Exception e) {
        Ui.toast(c, e.getMessage());
      }
    }

    JSONObject base(String mode, String action, int x, int y) throws JSONException {
      return new JSONObject()
          .put("op", "pointer")
          .put("mode", mode)
          .put("action", action)
          .put("display", display)
          .put("width", bounds.x)
          .put("height", bounds.y)
          .put("x", x)
          .put("y", y);
    }

    void send(String action, int x, int y, int duration) {
      try {
        pointer(c, base(settings.optString("pointerMode"), action, x, y), action.equals("move"));
      } catch (Exception e) {
        Ui.toast(c, e.getMessage());
      }
    }
  }
}
