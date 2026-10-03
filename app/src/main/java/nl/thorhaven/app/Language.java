package nl.thorhaven.app;

import android.content.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Local bilingual UI catalog. Stored user content is never changed. */
final class Language {
  static final Map<String, String> english = new HashMap<>();
  static Pattern pattern;

  static synchronized void load(Context c) {
    if (pattern != null) return;
    try (BufferedReader r =
        new BufferedReader(
            new InputStreamReader(c.getAssets().open("english.tsv"), StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) {
        String[] parts = line.split("\t", 2);
        if (parts.length == 2)
          english.put(parts[0].replace("\\n", "\n"), parts[1].replace("\\n", "\n"));
      }
    } catch (IOException ignored) {
    }
    List<String> keys = new ArrayList<>(english.keySet());
    keys.sort((a, b) -> Integer.compare(b.length(), a.length()));
    StringJoiner regex = new StringJoiner("|");
    for (String key : keys) regex.add(Pattern.quote(key));
    pattern = Pattern.compile(regex.length() == 0 ? "(?!)" : regex.toString());
  }

  static boolean isEnglish(Context c) {
    return Store.prefs(c).getString("language", "nl").equals("en");
  }

  static String text(Context c, String s) {
    if (s == null || !isEnglish(c)) return s;
    load(c);
    Matcher m = pattern.matcher(s);
    StringBuffer out = new StringBuffer();
    while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(english.get(m.group())));
    m.appendTail(out);
    return out.toString();
  }

  static String[] labels(Context c, String[] source) {
    String[] out = source.clone();
    for (int i = 0; i < out.length; i++) out[i] = text(c, out[i]);
    return out;
  }

  static void settings(MainActivity a, LinearLayout parent) {
    LinearLayout c =
        Ui.card(
            a,
            parent,
            "Taal / Language",
            "Nederlands of English. Je notities en gidsen blijven zoals je ze hebt geschreven.");
    c.addView(Ui.button(a, "Nederlands", () -> set(a, "nl")));
    c.addView(Ui.button(a, "English", () -> set(a, "en")));
  }

  static void set(MainActivity a, String language) {
    Store.prefs(a).edit().putString("language", language).commit();
    if (ThorService.instance != null) ThorService.instance.hidePanel();
    a.render();
  }
}
