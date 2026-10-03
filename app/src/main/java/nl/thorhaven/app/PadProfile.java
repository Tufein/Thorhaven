package nl.thorhaven.app;

import org.json.*;

final class PadProfile {
  static final int[] CODES = {
    304, 305, 307, 308, 310, 311, 312, 313, 314, 315, 316, 317, 318, 544, 545, 546, 547
  };
  static final String[] LABELS = {
    "A",
    "B",
    "X",
    "Y",
    "L1",
    "R1",
    "L2",
    "R2",
    "Select",
    "Start",
    "Home",
    "L3",
    "R3",
    "D-pad omhoog",
    "D-pad omlaag",
    "D-pad links",
    "D-pad rechts"
  };
  static final int[] EXTRA = {0, 1, 28, 57, 103, 108, 105, 106, 17, 30, 31, 32};
  static final String[] EXTRA_LABELS = {
    "Uitgeschakeld",
    "Escape",
    "Enter",
    "Spatie",
    "Pijl omhoog",
    "Pijl omlaag",
    "Pijl links",
    "Pijl rechts",
    "W",
    "A (toets)",
    "S",
    "D"
  };

  static JSONObject defaults() throws JSONException {
    return new JSONObject()
        .put("left", 10)
        .put("right", 10)
        .put("mask", 0)
        .put("swap", 0)
        .put("curve", 0)
        .put("buttons", new JSONArray(CODES));
  }

  static JSONObject preset(int index) throws Exception {
    JSONObject p = defaults();
    JSONArray b = p.getJSONArray("buttons");
    if (index == 1) {
      b.put(0, 305).put(1, 304).put(2, 308).put(3, 307);
    } else if (index == 2) {
      b.put(0, 28).put(1, 1).put(13, 103).put(14, 108).put(15, 105).put(16, 106);
    } else if (index == 3) {
      b.put(0, 57).put(1, 1).put(9, 28).put(13, 17).put(14, 31).put(15, 30).put(16, 32);
    } else if (index != 0) throw new Exception("Onbekende preset");
    return p;
  }

  static JSONObject parse(String text) throws Exception {
    JSONObject p = new JSONObject(text);
    if (p.length() != 6) throw new Exception("Ongeldig controllerprofiel");
    for (String key : new String[] {"left", "right", "mask", "swap", "curve"}) {
      Object o = p.get(key);
      if (!(o instanceof Integer)) throw new Exception("Ongeldige controllerwaarde");
      int v = p.getInt(key),
          max =
              key.equals("left") || key.equals("right")
                  ? 40
                  : key.equals("mask") ? 15 : key.equals("swap") ? 1 : 2;
      if (v < 0 || v > max) throw new Exception("Controllerwaarde buiten bereik");
    }
    JSONArray b = p.getJSONArray("buttons");
    if (b.length() != CODES.length) throw new Exception("Ongeldig aantal knoppen");
    for (int i = 0; i < b.length(); i++) {
      if (!(b.get(i) instanceof Integer) || !DeviceControlTarget(b.getInt(i)))
        throw new Exception("Ongeldige knop");
    }
    return p;
  }

  static boolean DeviceControlTarget(int n) {
    for (int b : CODES) if (b == n) return true;
    for (int b : EXTRA) if (b == n) return true;
    return false;
  }

  static String command(JSONObject p) throws Exception {
    parse(p.toString());
    StringBuilder s = new StringBuilder("CONFIG");
    for (String k : new String[] {"left", "right", "mask", "swap", "curve"})
      s.append(' ').append(p.getInt(k));
    JSONArray b = p.getJSONArray("buttons");
    for (int i = 0; i < b.length(); i++) s.append(' ').append(b.getInt(i));
    return s.toString();
  }
}
