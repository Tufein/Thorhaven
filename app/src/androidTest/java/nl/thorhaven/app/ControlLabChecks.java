package nl.thorhaven.app;

import android.content.*;
import java.util.*;
import org.json.*;

/** Input checks use a recording backend: they do not claim support on physical Thor firmware. */
final class ControlLabChecks {
  interface Action {
    void run() throws Exception;
  }

  static boolean rejects(Action action) {
    try {
      action.run();
      return false;
    } catch (Exception expected) {
      return true;
    }
  }

  static final class FakeClock implements ControlLab.Clock {
    long time;

    public long now() {
      return time;
    }

    public void pause(int ms) {
      time += ms;
    }
  }

  static JSONObject macro(JSONObject... steps) throws Exception {
    JSONArray a = new JSONArray();
    for (JSONObject step : steps) a.put(step);
    return new JSONObject().put("name", "Test 日本語").put("steps", a);
  }

  static void run(SmokeInstrumentation t, Context c, MainActivity a) throws Exception {
    JSONObject config = ControlLab.defaults();
    ControlLab.validate(config.toString());
    t.check(
        !config.getBoolean("turboEnabled") && config.getJSONArray("macros").length() == 0,
        "Control lab starts with turbo disabled and no automatic macros");
    JSONObject sequence = macro(ControlLab.step(135, 0, 150), ControlLab.step(66, 400, 0));
    config.getJSONArray("macros").put(sequence);
    ControlLab.validate(config.toString());
    String old = Store.prefs(c).getString("controlLab", null);
    try {
      ControlLab.save(c, config);
      t.check(
          ControlLab.config(c)
              .getJSONArray("macros")
              .getJSONObject(0)
              .getString("name")
              .equals("Test 日本語"),
          "Input macro settings preserve Unicode names");
      String backup = Store.backup(c).toString();
      Store.prefs(c).edit().remove("controlLab").commit();
      Store.restore(c, backup);
      t.check(
          ControlLab.config(c).getJSONArray("macros").length() == 1
              && ControlLab.config(c)
                  .getJSONArray("macros")
                  .getJSONObject(0)
                  .getString("name")
                  .equals("Test 日本語")
              && !ControlLab.config(c).getBoolean("turboEnabled"),
          "Input macro settings and explicit turbo choice survive the settings backup round-trip");
    } finally {
      if (old == null) Store.prefs(c).edit().remove("controlLab").commit();
      else Store.prefs(c).edit().putString("controlLab", old).commit();
    }
    t.check(
        rejects(
            () ->
                ControlLab.validate(
                    new JSONObject(config.toString()).put("shell", "id").toString())),
        "Input settings reject arbitrary operations before execution");
    t.check(
        rejects(
                () ->
                    ControlLab.validate(
                        new JSONObject(config.toString()).put("turboCount", 13).toString()))
            && rejects(
                () ->
                    ControlLab.validate(
                        new JSONObject(config.toString()).put("turboGap", 99).toString()))
            && rejects(
                () ->
                    ControlLab.validate(
                        new JSONObject(config.toString()).put("turboEnabled", "true").toString())),
        "Turbo requires an actual opt-in flag and finite bounded count and interval");
    t.check(
        rejects(() -> ControlLab.validateMacro(macro(ControlLab.step(3, 0, 0))))
            && rejects(() -> ControlLab.validateMacro(macro(ControlLab.step(96, 1501, 0))))
            && rejects(() -> ControlLab.validateMacro(macro(ControlLab.step(96, 1, 0)))),
        "Macros reject system Home and out-of-range key holds");
    JSONArray tooMany = new JSONArray();
    for (int i = 0; i < 21; i++) tooMany.put(ControlLab.step(96, 0, 0));
    JSONObject longMacro =
        macro(
            ControlLab.step(96, 1500, 1500),
            ControlLab.step(97, 1500, 1500),
            ControlLab.step(99, 1500, 1500),
            ControlLab.step(100, 1500, 1500));
    t.check(
        rejects(
                () ->
                    ControlLab.validateMacro(
                        new JSONObject().put("name", "Too many").put("steps", tooMany)))
            && rejects(() -> ControlLab.validateMacro(longMacro)),
        "Macros enforce twenty-step and ten-second declared budgets");
    List<JSONObject> sent = new ArrayList<>();
    FakeClock clock = new FakeClock();
    int done =
        ControlLab.execute(
            sequence,
            2,
            request -> {
              sent.add(request);
              return new JSONObject();
            },
            new ControlLab.Cancellation(),
            clock);
    t.check(
        done == 2
            && sent.size() == 2
            && sent.get(0).getInt("code") == 135
            && sent.get(1).getInt("duration") == 400
            && sent.get(1).getInt("display") == 2
            && clock.time == 150,
        "Macro executor sends validated ordered keys to the selected display with explicit delays");
    sent.clear();
    ControlLab.Cancellation cancelled = new ControlLab.Cancellation();
    done =
        ControlLab.execute(
            sequence,
            0,
            request -> {
              sent.add(request);
              cancelled.stop();
              return new JSONObject();
            },
            cancelled,
            new FakeClock());
    t.check(
        done == 1 && sent.size() == 1,
        "Stop cancels subsequent macro actions and pending delay after the current key completes");
    sent.clear();
    clock = new FakeClock();
    final FakeClock deadline = clock;
    done =
        ControlLab.execute(
            sequence,
            0,
            request -> {
              sent.add(request);
              deadline.time += 10001;
              return new JSONObject();
            },
            new ControlLab.Cancellation(),
            clock);
    t.check(
        done == 1 && sent.size() == 1,
        "Macro executor stops sending further actions after its wall-clock deadline");
    sent.clear();
    t.check(
        rejects(
                () ->
                    ControlLab.execute(
                        macro(ControlLab.step(96, 0, 0), ControlLab.step(3, 0, 0)),
                        0,
                        request -> {
                          sent.add(request);
                          return new JSONObject();
                        },
                        new ControlLab.Cancellation(),
                        new FakeClock()))
            && sent.isEmpty(),
        "Entire macro validates before its first privileged action");
    t.check(
        rejects(
            () ->
                ControlLab.execute(
                    sequence,
                    0,
                    request -> new JSONObject().put("error", "injection refused"),
                    new ControlLab.Cancellation(),
                    new FakeClock())),
        "Injection errors stop the macro instead of continuing silently");
    t.check(
        rejects(() -> ControlLab.burst(ControlLab.defaults(), 96)),
        "A turbo tile cannot run while turbo is disabled");
    JSONObject burst =
        ControlLab.burst(ControlLab.defaults().put("turboEnabled", true).put("turboCount", 12), 96);
    t.check(
        burst.getJSONArray("steps").length() == 12
            && burst.getJSONArray("steps").getJSONObject(11).getInt("wait") == 0,
        "Turbo is exactly the requested finite number of taps with no repeat loop");
    JSONObject hold = new JSONObject().put("code", 96).put("display", 2).put("duration", 400);
    List<String> commands = new ArrayList<>();
    DeviceControl.sendKey(
        command -> {
          commands.add(command);
          return command.equals("input help") ? "keyevent --duration" : "";
        },
        hold);
    t.check(
        commands.equals(
            Arrays.asList("input help", "input gamepad -d 2 keyevent --duration 400 96")),
        "Bounded holds probe runtime support and emit the actual --duration command");
    commands.clear();
    t.check(
        rejects(
                () ->
                    DeviceControl.sendKey(
                        command -> {
                          commands.add(command);
                          return "keyevent --longpress";
                        },
                        hold))
            && commands.size() == 1
            && commands.get(0).equals("input help"),
        "Unsupported firmware refuses holds without sending a misleading short tap");
    t.check(
        rejects(
                () ->
                    DeviceControl.keyCommand(
                        new JSONObject().put("code", 96).put("display", 0).put("duration", 0.5)))
            && rejects(
                () ->
                    DeviceControl.keyCommand(
                        new JSONObject().put("code", "96;id").put("display", 0))),
        "Typed key backend rejects fractional and command-shaped numeric values");
    JSONObject pointer =
        new JSONObject()
            .put("mode", "mouse")
            .put("action", "move")
            .put("display", 2)
            .put("width", 1920)
            .put("height", 1080)
            .put("x", 500)
            .put("y", 300);
    t.check(
        DeviceControl.pointerCommand(pointer).equals("input mouse -d 2 roll 500 300"),
        "Mouse pad uses a fixed typed position command on the selected display");
    JSONObject tap = new JSONObject(pointer.toString()).put("mode", "touch").put("action", "tap");
    JSONObject swipe =
        new JSONObject(tap.toString())
            .put("action", "swipe")
            .put("x2", 500)
            .put("y2", 500)
            .put("duration", 250);
    t.check(
        DeviceControl.pointerCommand(tap).equals("input touchscreen -d 2 tap 500 300")
            && DeviceControl.pointerCommand(swipe)
                .equals("input touchscreen -d 2 swipe 500 300 500 500 250"),
        "Touch pad clicks and scrolling emit complete bounded gestures");
    t.check(
        rejects(
                () ->
                    DeviceControl.pointerCommand(new JSONObject(pointer.toString()).put("x", 1920)))
            && rejects(
                () ->
                    DeviceControl.pointerCommand(
                        new JSONObject(pointer.toString()).put("action", "DOWN")))
            && rejects(
                () ->
                    DeviceControl.pointerCommand(
                        new JSONObject(pointer.toString()).put("mode", "mouse;id")))
            && rejects(
                () ->
                    DeviceControl.pointerCommand(
                        new JSONObject(swipe.toString()).put("duration", 1501))),
        "Pointer backend rejects out-of-display positions, persistent down events and arbitrary"
            + " sources");
    JSONArray tiles = TouchControls.defaults();
    tiles.getJSONObject(0).put("mode", "hold");
    tiles.getJSONObject(1).put("mode", "turbo");
    TouchControls.validate(tiles.toString());
    t.check(
        rejects(
            () -> {
              tiles.getJSONObject(0).put("mode", "forever");
              TouchControls.validate(tiles.toString());
            }),
        "Touch tiles accept finite hold/turbo modes and reject an indefinite mode");
    t.runOnMainSync(
        () -> {
          ControlLab.Cancellation token = new ControlLab.Cancellation();
          ControlLab.current = token;
          ControlLab.pendingPointer = pointer;
          ControlLab.stop();
          if (!token.cancelled || ControlLab.current != null || ControlLab.pendingPointer != null)
            throw new IllegalStateException("stop state");
          LinearLayoutHolder.render(a);
        });
    t.check(
        true,
        "Emergency stop clears queued pointer state and the lab settings render on the UI thread");
    String previousLab = Store.prefs(c).getString("controlLab", null);
    String previousLanguage = Store.prefs(c).getString("language", null);
    final boolean[] names = {false, false};
    try {
      JSONObject named = macro(ControlLab.step(96, 0, 0)).put("name", "Notities");
      ControlLab.save(c, ControlLab.defaults().put("macros", new JSONArray().put(named)));
      Store.prefs(c).edit().putString("language", "en").commit();
      t.runOnMainSync(
          () -> {
            android.widget.LinearLayout settingsView = new android.widget.LinearLayout(a);
            ControlLab.settings(a, settingsView);
            names[0] = hasExactButton(settingsView, "Notities");
            android.widget.LinearLayout overlayView = new android.widget.LinearLayout(a);
            ControlLab.decorate(a, overlayView, Store.screen(c, false));
            names[1] = hasExactButton(overlayView, "Notities");
          });
      t.check(
          Language.text(c, "Notities").equals("Notes") && names[0] && names[1],
          "User macro names stay exact in English settings and input overlays while interface"
              + " labels translate");
    } finally {
      android.content.SharedPreferences.Editor editor = Store.prefs(c).edit();
      if (previousLab == null) editor.remove("controlLab");
      else editor.putString("controlLab", previousLab);
      if (previousLanguage == null) editor.remove("language");
      else editor.putString("language", previousLanguage);
      editor.commit();
    }
    final int[] targetBounds = new int[2];
    t.runOnMainSync(
        () -> {
          android.hardware.display.DisplayManager displays =
              c.getSystemService(android.hardware.display.DisplayManager.class);
          android.view.Display lower = displays.getDisplay(Store.screen(c, true));
          Context lowerContext = c.createDisplayContext(lower);
          ControlLab.Pad pad = new ControlLab.Pad(lowerContext, Store.screen(c, false));
          targetBounds[0] = pad.bounds.x;
          targetBounds[1] = pad.bounds.y;
        });
    t.check(
        targetBounds[0] == 1920 && targetBounds[1] == 1080,
        "Pad hosted on the bottom display targets the actual 1920x1080 top display instead of"
            + " inheriting bottom bounds");
  }

  static boolean hasExactButton(android.view.View view, String label) {
    if (view instanceof android.widget.Button
        && ((android.widget.Button) view).getText().toString().equals(label)) return true;
    if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        if (hasExactButton(group.getChildAt(i), label)) return true;
    }
    return false;
  }

  static final class LinearLayoutHolder {
    static void render(MainActivity a) {
      ControlLab.settings(a, new android.widget.LinearLayout(a));
    }
  }
}
