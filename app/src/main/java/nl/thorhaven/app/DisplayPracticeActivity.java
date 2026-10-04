package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.*;
import android.widget.*;

/**
 * An own-app display test, bounded to fifteen seconds; never captures or changes system settings.
 */
public class DisplayPracticeActivity extends Activity implements DisplayManager.DisplayListener {
  static final long DURATION = 15000;
  private static final String TARGET_DISPLAY = "practiceDisplay";
  static java.lang.ref.WeakReference<DisplayPracticeActivity> current =
      new java.lang.ref.WeakReference<>(null);
  final Handler handler = new Handler(Looper.getMainLooper());
  final Runnable close = this::finish;
  long deadline;
  int launchedDisplay = -1;
  boolean registered;
  TextView information;

  static boolean open(Context c, int display) {
    if (!SetupTools.displayExists(c, display)) {
      Ui.toast(
          c,
          SetupTools.text(
              c, "Dit scherm is niet meer beschikbaar.", "This display is no longer available."));
      return false;
    }
    try {
      Intent intent =
          new Intent(c, DisplayPracticeActivity.class)
              .putExtra(TARGET_DISPLAY, display)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
      if (!c.getSystemService(ActivityManager.class)
          .isActivityStartAllowedOnDisplay(c, display, intent)) return false;
      c.startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle());
      return true;
    } catch (RuntimeException denied) {
      Ui.toast(
          c,
          SetupTools.text(
              c,
              "Android kon de schermtest niet openen.",
              "Android could not open the display test."));
      return false;
    }
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    DisplayPracticeActivity previous = current.get();
    if (previous != null && previous != this && !previous.isFinishing()) previous.finish();
    current = new java.lang.ref.WeakReference<>(this);
    Display launched = getDisplay();
    if (launched == null || !launched.isValid()) {
      finish();
      return;
    }
    // Android may recreate a removed-display task on the primary display. Keep its original
    // target so that migration cannot silently turn this into a different screen's practice.
    launchedDisplay =
        state != null && state.containsKey(TARGET_DISPLAY)
            ? state.getInt(TARGET_DISPLAY)
            : getIntent().getIntExtra(TARGET_DISPLAY, launched.getDisplayId());
    if (launched.getDisplayId() != launchedDisplay
        || !SetupTools.displayExists(this, launchedDisplay)) {
      finish();
      return;
    }
    deadline =
        state == null
            ? SystemClock.elapsedRealtime() + DURATION
            : state.getLong("deadline", SystemClock.elapsedRealtime());
    LinearLayout content = Ui.col(this);
    content.setPadding(Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24));
    content.setBackgroundColor(Ui.BG);
    content.addView(
        Ui.title(
            this, SetupTools.text(this, "THORHAVEN · SCHERMTEST", "THORHAVEN · DISPLAY TEST"), 24));
    information = Ui.rawText(this, "", 20, Ui.ACCENT);
    content.addView(information);
    content.addView(
        Ui.text(
            this,
            SetupTools.text(
                this,
                "Dit is alleen een venster van Thorhaven. Onthoud welk fysiek scherm je ziet en"
                    + " wijs daarna Boven of Onder toe. De test sluit binnen 15 seconden; er wordt"
                    + " niets opgenomen.",
                "This is only a Thorhaven window. Note which physical screen you see, then assign"
                    + " Top or Bottom. The test closes within 15 seconds; nothing is recorded."),
            16,
            Ui.TEXT));
    content.addView(
        Ui.button(this, SetupTools.text(this, "Test sluiten", "Close test"), this::finish));
    ScrollView scroll = new ScrollView(this);
    scroll.addView(content);
    setContentView(scroll);
    getSystemService(DisplayManager.class).registerDisplayListener(this, handler);
    registered = true;
    refresh();
    handler.postDelayed(close, Math.max(0, deadline - SystemClock.elapsedRealtime()));
  }

  void refresh() {
    Display actual = getDisplay();
    if (actual == null || !actual.isValid() || actual.getDisplayId() != launchedDisplay) {
      finish();
      return;
    }
    String label =
        SetupTools.text(this, "Android-scherm ", "Android display ")
            + actual.getDisplayId()
            + "\n"
            + actual.getName();
    try {
      android.util.DisplayMetrics m = Store.displayMetrics(this, actual);
      label += "\n" + m.widthPixels + " × " + m.heightPixels;
    } catch (RuntimeException ignored) {
    }
    label +=
        "\n"
            + (actual.getDisplayId() == Display.DEFAULT_DISPLAY
                ? SetupTools.text(
                    this,
                    "Android-hoofdscherm · bron voor Screen Lab",
                    "Android primary · source for Screen Lab")
                : SetupTools.text(this, "Extra openbaar scherm", "Additional public display"));
    information.setText(label);
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    state.putLong("deadline", deadline);
    state.putInt(TARGET_DISPLAY, launchedDisplay);
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    if (registered) getSystemService(DisplayManager.class).unregisterDisplayListener(this);
    registered = false;
    if (current.get() == this) current.clear();
    super.onDestroy();
  }

  @Override
  public void onDisplayAdded(int id) {}

  @Override
  public void onDisplayChanged(int id) {
    Display actual = getDisplay();
    if (id == launchedDisplay || actual == null || actual.getDisplayId() != launchedDisplay)
      refresh();
  }

  @Override
  public void onDisplayRemoved(int id) {
    Display actual = getDisplay();
    if (id == launchedDisplay || actual == null || actual.getDisplayId() != launchedDisplay)
      finish();
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent e) {
    if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B) {
      finish();
      return true;
    }
    return super.dispatchKeyEvent(e);
  }
}
