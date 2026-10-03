package nl.thorhaven.app;

import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

final class Ui {
  static final int BG = Color.rgb(11, 17, 26),
      CARD = Color.rgb(21, 31, 45),
      TEXT = Color.rgb(237, 244, 251),
      MUTED = Color.rgb(159, 177, 197),
      ACCENT = Color.rgb(98, 229, 192);

  static int dp(Context c, float v) {
    return Math.round(v * c.getResources().getDisplayMetrics().density);
  }

  static LinearLayout col(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  static LinearLayout row(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  static GradientDrawable bg(Context c, int color) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(c, 14));
    return d;
  }

  static TextView text(Context c, String s, int size, int color) {
    TextView t = new Label(c);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setPadding(0, dp(c, 4), 0, dp(c, 4));
    return t;
  }

  static TextView title(Context c, String s, int size) {
    TextView t = text(c, s, size, TEXT);
    t.setTypeface(null, Typeface.BOLD);
    return t;
  }

  static Button button(Context c, String s, Runnable action) {
    return styledButton(c, new Action(c), s, action);
  }

  static Button rawButton(Context c, String s, Runnable action) {
    return styledButton(c, new Button(c), s, action);
  }

  private static Button styledButton(Context c, Button b, String s, Runnable action) {
    b.setText(s);
    b.setTextSize(13);
    b.setAllCaps(false);
    b.setTextColor(TEXT);
    b.setMinHeight(dp(c, 48));
    b.setBackground(bg(c, CARD));
    b.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
    b.setOnFocusChangeListener((v, f) -> v.setBackground(bg(c, f ? Color.rgb(42, 88, 81) : CARD)));
    b.setOnClickListener(v -> action.run());
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, dp(c, 4), 0, dp(c, 4));
    b.setLayoutParams(lp);
    return b;
  }

  static LinearLayout card(Context c, LinearLayout parent, String title, String subtitle) {
    LinearLayout card = col(c);
    card.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
    card.setBackground(bg(c, CARD));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, dp(c, 6), 0, dp(c, 6));
    parent.addView(card, lp);
    if (title != null) card.addView(title(c, title, 19));
    if (subtitle != null) card.addView(text(c, subtitle, 13, MUTED));
    return card;
  }

  static void toast(Context c, String s) {
    Toast.makeText(c, Language.text(c, s), Toast.LENGTH_LONG).show();
  }

  static EditText input(Context c, String hint) {
    EditText e = new EditText(c);
    e.setTextColor(TEXT);
    e.setHintTextColor(MUTED);
    e.setHint(Language.text(c, hint));
    e.setTextSize(15);
    e.setSingleLine(true);
    return e;
  }

  static void seek(
      Context c,
      LinearLayout parent,
      String label,
      int max,
      int initial,
      java.util.function.IntConsumer action) {
    TextView t = text(c, label + " · " + initial, 14, MUTED);
    parent.addView(t);
    SeekBar s = new SeekBar(c);
    s.setMax(max);
    s.setProgress(initial);
    parent.addView(s, new LinearLayout.LayoutParams(-1, dp(c, 40)));
    s.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar b, int p, boolean user) {
            t.setText(label + " · " + p);
            if (user) action.accept(p);
          }

          public void onStartTrackingTouch(SeekBar b) {}

          public void onStopTrackingTouch(SeekBar b) {}
        });
  }

  static final class Label extends TextView {
    Label(Context c) {
      super(c);
    }

    public void setText(CharSequence text, BufferType type) {
      super.setText(Language.text(getContext(), text == null ? null : text.toString()), type);
    }
  }

  static final class Action extends Button {
    Action(Context c) {
      super(c);
    }

    public void setText(CharSequence text, BufferType type) {
      super.setText(Language.text(getContext(), text == null ? null : text.toString()), type);
    }
  }

  static TextView rawText(Context c, String s, int size, int color) {
    TextView t = new TextView(c);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setPadding(0, dp(c, 4), 0, dp(c, 4));
    return t;
  }

  static final class Dialog extends android.app.AlertDialog.Builder {
    final Context context;

    Dialog(Context c) {
      super(c);
      context = c;
    }

    public android.app.AlertDialog.Builder setTitle(CharSequence s) {
      return super.setTitle(Language.text(context, s.toString()));
    }

    public android.app.AlertDialog.Builder setMessage(CharSequence s) {
      return super.setMessage(Language.text(context, s.toString()));
    }

    public android.app.AlertDialog.Builder setPositiveButton(
        CharSequence s, DialogInterface.OnClickListener l) {
      return super.setPositiveButton(Language.text(context, s.toString()), l);
    }

    public android.app.AlertDialog.Builder setNegativeButton(
        CharSequence s, DialogInterface.OnClickListener l) {
      return super.setNegativeButton(Language.text(context, s.toString()), l);
    }

    public android.app.AlertDialog.Builder setNeutralButton(
        CharSequence s, DialogInterface.OnClickListener l) {
      return super.setNeutralButton(Language.text(context, s.toString()), l);
    }

    public android.app.AlertDialog.Builder setSingleChoiceItems(
        CharSequence[] items, int selected, DialogInterface.OnClickListener l) {
      CharSequence[] out = items.clone();
      for (int i = 0; i < out.length; i++) out[i] = Language.text(context, out[i].toString());
      return super.setSingleChoiceItems(out, selected, l);
    }

    public android.app.AlertDialog.Builder setItems(
        CharSequence[] items, DialogInterface.OnClickListener l) {
      CharSequence[] out = items.clone();
      for (int i = 0; i < out.length; i++) out[i] = Language.text(context, out[i].toString());
      return super.setItems(out, l);
    }
  }
}
