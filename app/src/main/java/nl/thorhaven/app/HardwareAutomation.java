package nl.thorhaven.app;

import android.content.Context;
import org.json.*;

/** Session opt-in; preserves existing manual recovery state and serializes changes. */
final class HardwareAutomation {
  interface Actions {
    void apply(Context c, JSONObject patch, java.util.function.Consumer<JSONObject> callback);

    void restore(Context c, java.util.function.Consumer<JSONObject> callback);
  }

  static Actions actions =
      new Actions() {
        public void apply(Context c, JSONObject patch, java.util.function.Consumer<JSONObject> cb) {
          Controls.apply(c, patch, cb);
        }

        public void restore(Context c, java.util.function.Consumer<JSONObject> cb) {
          Controls.restore(c, cb);
        }
      };
  static boolean enabled, busy, owned;
  static String desired = "", applied = "", status = "Automatic hardware profiles disabled";

  static void focus(Context c, String pkg) {
    desired = enabled ? pkg : "";
    step(c.getApplicationContext());
  }

  static void step(Context c) {
    if (busy) return;
    String target = desired;
    String config = Store.prefs(c).getString("hardwareProfile:" + target, "");
    if (!enabled) config = "";
    if (target.equals(applied) && owned && !config.isEmpty()) return;
    if (owned) {
      busy = true;
      actions.restore(
          c,
          r -> {
            busy = false;
            if (r.has("error")) {
              status = r.optString("error");
              enabled = false;
              desired = "";
              Ui.toast(c, status);
              return;
            }
            owned = false;
            applied = "";
            status = "Hardware settings restored";
            step(c);
          });
      return;
    }
    if (config.isEmpty()) return;
    if (Store.prefs(c).contains("hw:snapshot")) {
      status = "Restore manual hardware changes before enabling automatic profiles";
      enabled = false;
      Ui.toast(c, status);
      return;
    }
    try {
      JSONObject patch = new JSONObject(config);
      ExtraFeatures.validate("hardwareProfile:" + target, config);
      busy = true;
      actions.apply(
          c,
          patch,
          r -> {
            busy = false;
            if (r.has("error")) {
              status = r.optString("error");
              enabled = false;
              desired = "";
              Ui.toast(c, status);
              return;
            }
            owned = true;
            applied = target;
            status = "Hardware profile: " + Store.name(c, target);
            step(c);
          });
    } catch (Exception e) {
      status = e.getMessage();
      enabled = false;
    }
  }
}
