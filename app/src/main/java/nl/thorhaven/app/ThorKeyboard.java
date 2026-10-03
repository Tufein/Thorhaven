package nl.thorhaven.app;

import android.inputmethodservice.InputMethodService;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;

public class ThorKeyboard extends InputMethodService {
  final List<Button> keys = new ArrayList<>();
  final List<Integer> starts = new ArrayList<>();
  final Set<Integer> consumed = new HashSet<>();
  boolean upper, symbols;
  int selected;

  public boolean onEvaluateFullscreenMode() {
    return false;
  }

  public View onCreateInputView() {
    return keyboard();
  }

  View keyboard() {
    keys.clear();
    starts.clear();
    int height = Store.prefs(this).getInt("keyboardSize", 40);
    height = Math.max(40, Math.min(64, height));
    LinearLayout root = Ui.col(this);
    root.setBackgroundColor(Ui.BG);
    root.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
    root.addView(
        Ui.text(this, "Thorhaven · D-pad: kies · A: typ · B: sluit · L1/R1: cursor", 12, Ui.MUTED));
    String[] rows =
        symbols
            ? new String[] {"!@#$%^&*()", "[]{}<>_=+|", "\\/;:'\"`~€£", ".,?:-01234"}
            : new String[] {"1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm.,?"};
    for (String chars : rows) {
      LinearLayout row = Ui.row(this);
      starts.add(keys.size());
      for (char ch : chars.toCharArray()) {
        String value =
            upper && !symbols ? String.valueOf(ch).toUpperCase(Locale.ROOT) : String.valueOf(ch);
        add(row, value, () -> commit(value), height);
      }
      root.addView(row);
    }
    LinearLayout actions = Ui.row(this);
    starts.add(keys.size());
    add(
        actions,
        "Shift",
        () -> {
          upper = !upper;
          setInputView(keyboard());
        },
        height);
    add(actions, "Spatie", () -> commit(" "), height);
    add(actions, "⌫", this::delete, height);
    add(actions, "Enter", this::enter, height);
    add(actions, "Andere", () -> switchToNextInputMethod(false), height);
    root.addView(actions);
    actions = Ui.row(this);
    starts.add(keys.size());
    add(
        actions,
        symbols ? "Letters" : "Symbolen",
        () -> {
          symbols = !symbols;
          setInputView(keyboard());
        },
        height);
    add(actions, "◀", () -> cursor(-1), height);
    add(actions, "▶", () -> cursor(1), height);
    add(actions, "Sluiten", () -> requestHideSelf(0), height);
    root.addView(actions);
    starts.add(keys.size());
    selected = Math.min(selected, keys.size() - 1);
    highlight();
    return root;
  }

  void commit(String value) {
    InputConnection ic = getCurrentInputConnection();
    if (ic != null) ic.commitText(value, 1);
  }

  void delete() {
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    CharSequence text = ic.getSelectedText(0);
    if (text != null && text.length() > 0) ic.commitText("", 1);
    else ic.deleteSurroundingTextInCodePoints(1, 0);
  }

  void enter() {
    InputConnection ic = getCurrentInputConnection();
    EditorInfo info = getCurrentInputEditorInfo();
    if (ic == null || info == null) return;
    int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
    if ((info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
        && action != EditorInfo.IME_ACTION_NONE
        && action != EditorInfo.IME_ACTION_UNSPECIFIED) ic.performEditorAction(action);
    else ic.commitText("\n", 1);
  }

  void cursor(int direction) {
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    int key = direction < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
  }

  void add(LinearLayout row, String label, Runnable action, int height) {
    Button b = Ui.button(this, label, action);
    b.setPadding(0, 0, 0, 0);
    b.setMinHeight(Ui.dp(this, height));
    keys.add(b);
    row.addView(b, new LinearLayout.LayoutParams(0, Ui.dp(this, height), 1));
  }

  void vertical(int direction) {
    if (starts.size() < 2) return;
    int row = 0;
    while (row < starts.size() - 2 && selected >= starts.get(row + 1)) row++;
    int column = selected - starts.get(row);
    int target = Math.max(0, Math.min(starts.size() - 2, row + direction));
    selected =
        starts.get(target) + Math.min(column, starts.get(target + 1) - starts.get(target) - 1);
  }

  void highlight() {
    for (int i = 0; i < keys.size(); i++) {
      keys.get(i).setTextColor(i == selected ? Ui.BG : Ui.TEXT);
      keys.get(i).setBackground(Ui.bg(this, i == selected ? Ui.ACCENT : Ui.CARD));
    }
  }

  public boolean onKeyDown(int code, KeyEvent e) {
    if (!isInputViewShown() || keys.isEmpty()) return super.onKeyDown(code, e);
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
        break;
      case KeyEvent.KEYCODE_BUTTON_B:
        requestHideSelf(0);
        break;
      case KeyEvent.KEYCODE_BUTTON_L1:
        cursor(-1);
        break;
      case KeyEvent.KEYCODE_BUTTON_R1:
        cursor(1);
        break;
      default:
        return super.onKeyDown(code, e);
    }
    consumed.add(code);
    highlight();
    return true;
  }

  public boolean onKeyUp(int code, KeyEvent e) {
    if (consumed.remove(code)) return true;
    return super.onKeyUp(code, e);
  }

  public void onFinishInput() {
    consumed.clear();
    super.onFinishInput();
  }
}
