package nl.thorhaven.app;

import java.io.IOException;
import java.util.HashSet;
import org.json.*;

/** Strict JSON syntax before Android's deliberately forgiving JSON parser. */
final class StrictJson {
  private final String text;
  private int pos, values;
  private int maxValues = 30000;

  private StrictJson(String text) {
    this.text = text;
  }

  static JSONObject object(String raw, int maxBytes) throws Exception {
    return object(raw, maxBytes, 30000);
  }

  static JSONObject object(String raw, int maxBytes, int maxValues) throws Exception {
    Object result = parse(raw, maxBytes, maxValues);
    if (!(result instanceof JSONObject)) throw new IOException("Expected JSON object");
    return (JSONObject) result;
  }

  static JSONArray array(String raw, int maxBytes) throws Exception {
    Object result = parse(raw, maxBytes, 30000);
    if (!(result instanceof JSONArray)) throw new IOException("Expected JSON array");
    return (JSONArray) result;
  }

  static long integer(JSONObject object, String key, long min, long max) throws Exception {
    Object value = object.get(key);
    if (!(value instanceof Integer) && !(value instanceof Long))
      throw new IOException("Expected integer: " + key);
    long number = ((Number) value).longValue();
    if (number < min || number > max) throw new IOException("Integer outside range: " + key);
    return number;
  }

  private static Object parse(String raw, int maxBytes, int maxValues) throws Exception {
    if (raw == null
        || raw.length() > maxBytes
        || raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes)
      throw new IOException("JSON document exceeds its size limit");
    StrictJson parser = new StrictJson(raw);
    parser.maxValues = maxValues;
    parser.value(0);
    parser.space();
    if (parser.pos != raw.length()) throw new IOException("Trailing JSON data");
    return new JSONTokener(raw).nextValue();
  }

  private void space() {
    while (pos < text.length() && " \t\r\n".indexOf(text.charAt(pos)) >= 0) pos++;
  }

  private void take(char ch) throws IOException {
    space();
    if (pos >= text.length() || text.charAt(pos++) != ch)
      throw new IOException("Invalid JSON syntax");
  }

  private boolean end(char ch) {
    space();
    if (pos < text.length() && text.charAt(pos) == ch) {
      pos++;
      return true;
    }
    return false;
  }

  private String string() throws Exception {
    space();
    int start = pos;
    take('"');
    while (pos < text.length()) {
      char ch = text.charAt(pos++);
      if (ch == '"') return (String) new JSONTokener(text.substring(start, pos)).nextValue();
      if (ch < 32) throw new IOException("Control character in JSON string");
      if (ch == '\\') {
        if (pos >= text.length()) throw new IOException("Incomplete JSON escape");
        char escape = text.charAt(pos++);
        if (escape == 'u') {
          for (int i = 0; i < 4; i++)
            if (pos >= text.length() || Character.digit(text.charAt(pos++), 16) < 0)
              throw new IOException("Invalid JSON Unicode escape");
        } else if ("\"\\/bfnrt".indexOf(escape) < 0) throw new IOException("Invalid JSON escape");
      }
    }
    throw new IOException("Unclosed JSON string");
  }

  private void value(int depth) throws Exception {
    if (depth > 32 || ++values > maxValues) throw new IOException("JSON document is too complex");
    space();
    if (pos >= text.length()) throw new IOException("Missing JSON value");
    char ch = text.charAt(pos);
    if (ch == '{') {
      pos++;
      HashSet<String> keys = new HashSet<>();
      if (end('}')) return;
      do {
        String key = string();
        if (!keys.add(key)) throw new IOException("Duplicate JSON key");
        take(':');
        value(depth + 1);
        if (end('}')) return;
        take(',');
      } while (true);
    } else if (ch == '[') {
      pos++;
      if (end(']')) return;
      do {
        value(depth + 1);
        if (end(']')) return;
        take(',');
      } while (true);
    } else if (ch == '"') string();
    else if (text.startsWith("true", pos)) pos += 4;
    else if (text.startsWith("false", pos)) pos += 5;
    else if (text.startsWith("null", pos)) pos += 4;
    else {
      int start = pos;
      while (pos < text.length() && "-+0123456789.eE".indexOf(text.charAt(pos)) >= 0) pos++;
      String number = text.substring(start, pos);
      if (!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
          || !Double.isFinite(Double.parseDouble(number)))
        throw new IOException("Invalid JSON number");
    }
  }
}
