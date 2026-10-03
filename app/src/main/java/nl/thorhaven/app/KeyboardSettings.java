package nl.thorhaven.app;

import android.widget.*;

final class KeyboardSettings {
  static void page(MainActivity a, LinearLayout parent) {
    LinearLayout c = Ui.card(a, parent, "Toetsenbordgrootte", null);
    String[] labels = {"Normaal", "Groot", "Extra groot"};
    int[] sizes = {40, 52, 64};
    for (int i = 0; i < sizes.length; i++) {
      final int size = sizes[i];
      c.addView(
          Ui.button(
              a,
              labels[i] + " · " + size,
              () -> {
                Store.prefs(a).edit().putInt("keyboardSize", size).apply();
                a.render();
              }));
    }
  }
}
