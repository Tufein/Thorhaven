package nl.thorhaven.app;

import android.inputmethodservice.InputMethodService;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;

public class ThorKeyboard extends InputMethodService {
  final List<Button> keys = new ArrayList<>();
  boolean upper;
  int selected;

  @Override
  public boolean onEvaluateFullscreenMode() {
    return false;
  }

  @Override
  public View onCreateInputView() {
    return keyboard();
  }

  View keyboard() {
    keys.clear();
    LinearLayout root = Ui.col(this);
    root.setBackgroundColor(Ui.BG);
    root.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
    root.addView(Ui.text(this, "Thorhaven · D-pad: kies · A: typ · B: sluit", 12, Ui.MUTED));
    String[] rows = {"1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm.,?"};
    for (String chars : rows) {
      LinearLayout r = Ui.row(this);
      for (char ch : chars.toCharArray()) {
        String value = upper ? String.valueOf(ch).toUpperCase(Locale.ROOT) : String.valueOf(ch);
        Button b =
            Ui.button(
                this,
                value,
                () -> {
                  InputConnection ic = getCurrentInputConnection();
                  if (ic != null) ic.commitText(value, 1);
                });
        keys.add(b);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(Ui.dp(this, 38));
        r.addView(b, new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));
      }
      root.addView(r);
    }
    LinearLayout actions = Ui.row(this);
    add(
        actions,
        "Shift",
        () -> {
          upper = !upper;
          setInputView(keyboard());
        });
    add(
        actions,
        "Spatie",
        () -> {
          InputConnection ic = getCurrentInputConnection();
          if (ic != null) ic.commitText(" ", 1);
        });
    add(
        actions,
        "⌫",
        () -> {
          InputConnection ic = getCurrentInputConnection();
          if (ic != null) {
            CharSequence sel = ic.getSelectedText(0);
            if (sel != null && sel.length() > 0) ic.commitText("", 1);
            else ic.deleteSurroundingTextInCodePoints(1, 0);
          }
        });
    add(
        actions,
        "Enter",
        () -> {
          InputConnection ic = getCurrentInputConnection();
          EditorInfo info = getCurrentInputEditorInfo();
          if (ic != null && info != null) {
            int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
            if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED)
              ic.performEditorAction(action);
            else ic.commitText("\n", 1);
          }
        });
    add(actions, "Andere", () -> switchToNextInputMethod(false));
    root.addView(actions);
    selected = Math.min(selected, keys.size() - 1);
    highlight();
    return root;
  }

  void add(LinearLayout row, String label, Runnable action) {
    Button b = Ui.button(this, label, action);
    keys.add(b);
    row.addView(b, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
  }

  void vertical(int direction) {
    int[] starts = {0, 10, 20, 29, 38, 43};
    int row = 0;
    while (row < 4 && selected >= starts[row + 1]) row++;
    int col = selected - starts[row];
    int target = Math.max(0, Math.min(4, row + direction));
    selected = starts[target] + Math.min(col, starts[target + 1] - starts[target] - 1);
  }

  void highlight() {
    for (int i = 0; i < keys.size(); i++) {
      keys.get(i).setTextColor(i == selected ? Ui.BG : Ui.TEXT);
      keys.get(i).setBackground(Ui.bg(this, i == selected ? Ui.ACCENT : Ui.CARD));
    }
  }

  @Override
  public boolean onKeyDown(int code, KeyEvent e) {
    if (!isInputViewShown()) return super.onKeyDown(code, e);
    switch (code) {
      case KeyEvent.KEYCODE_DPAD_LEFT:
        selected = (selected + keys.size() - 1) % keys.size();
        break;
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        selected = (selected + 1) % keys.size();
        break;
      case KeyEvent.KEYCODE_DPAD_UP:
        vertical(-1);
        break;
      case KeyEvent.KEYCODE_DPAD_DOWN:
        vertical(1);
        break;
      case KeyEvent.KEYCODE_BUTTON_A:
        keys.get(selected).performClick();
        return true;
      case KeyEvent.KEYCODE_BUTTON_B:
        requestHideSelf(0);
        return true;
      default:
        return super.onKeyDown(code, e);
    }
    highlight();
    return true;
  }

  @Override
  public boolean onKeyUp(int code, KeyEvent e) {
    if (code == KeyEvent.KEYCODE_BUTTON_A
        || code == KeyEvent.KEYCODE_BUTTON_B
        || code >= KeyEvent.KEYCODE_DPAD_UP && code <= KeyEvent.KEYCODE_DPAD_RIGHT) return true;
    return super.onKeyUp(code, e);
  }
}
