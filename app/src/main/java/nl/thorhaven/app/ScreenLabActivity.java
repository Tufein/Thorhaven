package nl.thorhaven.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.media.projection.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Capture controls on the assigned lower screen. Closing the lab stops its capture. */
public class ScreenLabActivity extends Activity {
  static final int CONSENT = 610, EXPORT = 611, NOTIFICATION = 612;
  ScreenLabService service;
  boolean bound, bindingRegistered, recordRequested, choosingCapture, exporting, fullPreview;
  static final AtomicBoolean exportBusy = new AtomicBoolean();
  String exportName;
  String consentEpoch = "", notificationEpoch = "";
  RectF crop = new RectF(0, 0, 1, 1);
  TextView status;
  Preview preview;
  ScrollView tools;
  LinearLayout fileButtons;
  String filesFingerprint = "";

  public static void open(Context c) {
    try {
      ActivityOptions options = ActivityOptions.makeBasic();
      int bottom = Store.screen(c, true);
      if (bottom >= 0) options.setLaunchDisplayId(bottom);
      c.startActivity(
          new Intent(c, ScreenLabActivity.class)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
          options.toBundle());
    } catch (Exception e) {
      Ui.toast(c, "Screen Lab kan niet worden geopend");
    }
  }

  final ServiceConnection connection =
      new ServiceConnection() {
        public void onServiceConnected(ComponentName n, IBinder b) {
          bound = true;
          service = ((ScreenLabService.LocalBinder) b).service();
          service.changed = ScreenLabActivity.this::refresh;
          refresh();
        }

        public void onServiceDisconnected(ComponentName n) {
          bound = false;
          service = null;
          refresh();
        }
      };

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    if (b != null) {
      crop =
          ScreenLab.normalized(
              b.getFloat("left", 0),
              b.getFloat("top", 0),
              b.getFloat("right", 1),
              b.getFloat("bottom", 1));
      choosingCapture = b.getBoolean("choosing");
      consentEpoch = b.getString("consentEpoch", "");
      notificationEpoch = b.getString("notificationEpoch", "");
      recordRequested = b.getBoolean("record");
      exportName = b.getString("exportName");
      exporting = b.getBoolean("exporting");
    }
    build();
    bindingRegistered =
        bindService(new Intent(this, ScreenLabService.class), connection, BIND_AUTO_CREATE);
  }

  LinearLayout build() {
    LinearLayout root = Ui.col(this);
    root.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
    root.setBackgroundColor(Ui.BG);
    root.addView(Ui.title(this, "Screen Lab · experimenteel", 22));
    status = Ui.text(this, "Klaar voor schermdeling", 13, Ui.ACCENT);
    root.addView(status);
    preview = new Preview(this);
    preview.setContentDescription(Language.text(this, "Live schermvoorbeeld met uitsnede"));
    root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout always = Ui.row(this);
    always.addView(
        Ui.button(
            this,
            "Stop",
            () -> {
              if (service != null) service.stopCapture("Schermdeling gestopt");
            }),
        new LinearLayout.LayoutParams(0, -2, 1));
    always.addView(
        Ui.button(
            this,
            "Groot voorbeeld",
            () -> {
              fullPreview = !fullPreview;
              tools.setVisibility(fullPreview ? View.GONE : View.VISIBLE);
            }),
        new LinearLayout.LayoutParams(0, -2, 1));
    always.addView(
        Ui.button(this, "Sluiten", this::finish), new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(always);
    tools = new ScrollView(this);
    LinearLayout content = Ui.col(this);
    tools.addView(content);
    root.addView(tools, new LinearLayout.LayoutParams(-1, 0, 1));
    tools.setVisibility(fullPreview ? View.GONE : View.VISIBLE);
    content.addView(
        Ui.text(
            this,
            "Deel het hoofdscherm via Android. Beveiligde inhoud kan zwart blijven. Het live"
                + " voorbeeld is vertraagd en stuurt geen aanrakingen door.",
            13,
            Ui.MUTED));
    content.addView(Ui.button(this, "Live voorbeeld starten", () -> requestCapture(false)));
    content.addView(Ui.button(this, "Stille schermopname starten", () -> requestCapture(true)));
    content.addView(
        Ui.text(
            this,
            "Opnames bevatten het volledige hoofdscherm, zonder geluid of uitsnede. Maximaal 3"
                + " minuten of 96 MB. Een formaatwijziging stopt de opname. Sluiten stopt alle"
                + " schermdeling.",
            13,
            Ui.MUTED));
    LinearLayout cropping =
        Ui.card(
            this,
            content,
            "Uitsnede voor voorbeeld en schermafbeelding",
            "Grenzen zijn percentages van het gedeelde scherm.");
    Ui.seek(
        this,
        cropping,
        "Links",
        99,
        Math.round(crop.left * 100),
        p -> {
          crop = ScreenLab.normalized(p / 100f, crop.top, crop.right, crop.bottom);
          preview.invalidate();
        });
    Ui.seek(
        this,
        cropping,
        "Boven",
        99,
        Math.round(crop.top * 100),
        p -> {
          crop = ScreenLab.normalized(crop.left, p / 100f, crop.right, crop.bottom);
          preview.invalidate();
        });
    Ui.seek(
        this,
        cropping,
        "Rechts",
        100,
        Math.round(crop.right * 100),
        p -> {
          crop = ScreenLab.normalized(crop.left, crop.top, p / 100f, crop.bottom);
          preview.invalidate();
        });
    Ui.seek(
        this,
        cropping,
        "Onder",
        100,
        Math.round(crop.bottom * 100),
        p -> {
          crop = ScreenLab.normalized(crop.left, crop.top, crop.right, p / 100f);
          preview.invalidate();
        });
    cropping.addView(
        Ui.button(
            this,
            "Uitsnede herstellen",
            () -> {
              crop = new RectF(0, 0, 1, 1);
              build();
              refresh();
            }));
    cropping.addView(
        Ui.button(
            this,
            "Schermafbeelding van uitsnede",
            () -> {
              if (service != null) service.screenshot(crop);
            }));
    fileButtons =
        Ui.card(
            this,
            content,
            "Opgeslagen beelden",
            "De laatste 6 schermafbeeldingen en 3 opnames blijven lokaal. Exporteer wat je wilt"
                + " bewaren. Deze bestanden zitten niet in de instellingenback-up.");
    filesFingerprint = "";
    setContentView(root);
    return root;
  }

  void requestCapture(boolean record) {
    if (choosingCapture) return;
    if (service == null) {
      Ui.toast(this, "Screen Lab wordt nog gestart");
      return;
    }
    recordRequested = record;
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        && !getSharedPreferences("thorhaven-screen-lab", MODE_PRIVATE)
            .getBoolean("notificationAsked", false)) {
      getSharedPreferences("thorhaven-screen-lab", MODE_PRIVATE)
          .edit()
          .putBoolean("notificationAsked", true)
          .apply();
      notificationEpoch = ScreenLabService.requestEpoch;
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION);
      return;
    }
    service.stopCapture("Wachten op toestemming voor schermdeling");
    try {
      MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
      Intent permission =
          Build.VERSION.SDK_INT >= 34
              ? manager.createScreenCaptureIntent(
                  MediaProjectionConfig.createConfigForDefaultDisplay())
              : manager.createScreenCaptureIntent();
      choosingCapture = true;
      consentEpoch = ScreenLabService.requestEpoch;
      startActivityForResult(permission, CONSENT);
    } catch (Exception e) {
      choosingCapture = false;
      Ui.toast(this, "Schermdeling is niet beschikbaar op dit apparaat");
    }
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    if (request == NOTIFICATION && notificationEpoch.equals(ScreenLabService.requestEpoch))
      requestCapture(recordRequested);
  }

  void refresh() {
    if (status == null) return;
    if (service != null) status.setText(service.status);
    else status.setText("Klaar voor schermdeling");
    preview.invalidate();
    List<File> files = ScreenLab.files(this);
    StringBuilder signature = new StringBuilder();
    for (File f : files) signature.append(f.getName()).append(':').append(f.length()).append(';');
    if (filesFingerprint.equals(signature.toString())) return;
    filesFingerprint = signature.toString();
    while (fileButtons.getChildCount() > 2) fileButtons.removeViewAt(2);
    for (File f : files) {
      fileButtons.addView(
          Ui.rawText(this, f.getName() + " · " + (f.length() / 1024) + " KB", 12, Ui.MUTED));
      fileButtons.addView(Ui.button(this, "Exporteren", () -> export(f.getName())));
      fileButtons.addView(
          Ui.button(
              this,
              "Verwijderen",
              () -> {
                new Ui.Dialog(this)
                    .setTitle("Opgeslagen beeld verwijderen?")
                    .setMessage("Dit bestand wordt uit Screen Lab verwijderd.")
                    .setNegativeButton("Annuleren", null)
                    .setPositiveButton(
                        "Verwijderen",
                        (d, w) -> {
                          f.delete();
                          filesFingerprint = "";
                          refresh();
                        })
                    .show();
              }));
    }
  }

  void export(String name) {
    if (exporting || exportBusy.get()) {
      Ui.toast(this, "Export is nog bezig");
      return;
    }
    try {
      File source = ScreenLab.resolve(this, name);
      if (!source.isFile()) throw new IOException("Missing capture");
      exportName = name;
      exporting = true;
      startActivityForResult(
          new Intent(Intent.ACTION_CREATE_DOCUMENT)
              .addCategory(Intent.CATEGORY_OPENABLE)
              .setType(name.endsWith(".png") ? "image/png" : "video/mp4")
              .putExtra(Intent.EXTRA_TITLE, name),
          EXPORT);
    } catch (Exception e) {
      exporting = false;
      Ui.toast(this, "Export mislukt");
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == CONSENT) {
      choosingCapture = false;
      if (!consentEpoch.equals(ScreenLabService.requestEpoch)) {
        Ui.toast(
            this,
            Language.isEnglish(this)
                ? "Capture request cancelled. Start again for fresh consent."
                : "Schermdeelverzoek geannuleerd. Start opnieuw voor nieuwe toestemming.");
        return;
      }
      if (result != RESULT_OK || data == null) {
        Ui.toast(this, "Geen toestemming voor schermdeling");
        return;
      }
      try {
        startForegroundService(
            new Intent(this, ScreenLabService.class)
                .setAction(ScreenLabService.START)
                .putExtra("consent", data)
                .putExtra("epoch", consentEpoch)
                .putExtra("result", result)
                .putExtra("record", recordRequested));
      } catch (Exception e) {
        Ui.toast(this, "Schermdeling kon niet starten");
      }
    } else if (request == EXPORT) {
      exporting = false;
      if (result != RESULT_OK || data == null || data.getData() == null || exportName == null)
        return;
      if (!exportBusy.compareAndSet(false, true)) {
        Ui.toast(this, "Export is nog bezig");
        return;
      }
      Uri uri = data.getData();
      String name = exportName;
      exportName = null;
      // A single bounded export task; it owns no activity/view references while copying.
      Context app = getApplicationContext();
      new Thread(
              () -> {
                String message;
                try (InputStream in = new FileInputStream(ScreenLab.resolve(app, name));
                    OutputStream out = app.getContentResolver().openOutputStream(uri, "w")) {
                  if (out == null) throw new IOException("Destination unavailable");
                  byte[] buffer = new byte[32768];
                  int n;
                  long total = 0;
                  while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > ScreenLab.RECORD_BYTES + 1024 * 1024)
                      throw new IOException("Capture too large");
                    out.write(buffer, 0, n);
                  }
                  message = "Beeld geëxporteerd";
                } catch (Exception e) {
                  message = "Export mislukt";
                } finally {
                  exportBusy.set(false);
                }
                String completed = message;
                new Handler(Looper.getMainLooper()).post(() -> Ui.toast(app, completed));
              },
              "Thorhaven capture export")
          .start();
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putFloat("left", crop.left);
    out.putFloat("top", crop.top);
    out.putFloat("right", crop.right);
    out.putFloat("bottom", crop.bottom);
    out.putBoolean("choosing", choosingCapture);
    out.putString("consentEpoch", consentEpoch);
    out.putString("notificationEpoch", notificationEpoch);
    out.putBoolean("record", recordRequested);
    out.putBoolean("exporting", exporting);
    out.putString("exportName", exportName);
  }

  @Override
  protected void onDestroy() {
    if (service != null && service.changed != null) service.changed = null;
    if (!isChangingConfigurations()) {
      if (service != null) service.stopCapture("Schermdeling gestopt");
      else stopService(new Intent(this, ScreenLabService.class));
    }
    if (bindingRegistered) unbindService(connection);
    super.onDestroy();
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent e) {
    if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B) {
      finish();
      return true;
    }
    return super.dispatchKeyEvent(e);
  }

  final class Preview extends View {
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    Preview(Context c) {
      super(c);
      setMinimumHeight(Ui.dp(c, 100));
    }

    @Override
    protected void onDraw(Canvas canvas) {
      canvas.drawColor(Color.BLACK);
      Bitmap image = service == null ? null : service.frame;
      if (image == null || image.isRecycled()) {
        paint.setColor(Ui.MUTED);
        paint.setTextSize(Ui.dp(getContext(), 14));
        canvas.drawText(
            Language.text(getContext(), "Geen live beeld"),
            Ui.dp(getContext(), 12),
            Ui.dp(getContext(), 28),
            paint);
        return;
      }
      Rect src = ScreenLab.pixels(crop, image.getWidth(), image.getHeight());
      float scale = Math.min(getWidth() / (float) src.width(), getHeight() / (float) src.height());
      float w = src.width() * scale, h = src.height() * scale;
      RectF dst =
          new RectF(
              (getWidth() - w) / 2,
              (getHeight() - h) / 2,
              (getWidth() + w) / 2,
              (getHeight() + h) / 2);
      canvas.drawBitmap(image, src, dst, paint);
    }
  }
}
