package nl.thorhaven.app;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;
import org.json.*;

/**
 * The same local reader can be an activity or an accessibility overlay above a dual-screen game.
 */
final class GuidePane extends LinearLayout {
  final Context context;
  final String pkg;
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  final Handler main = new Handler(Looper.getMainLooper());
  TextView status;
  ImageView image;
  HorizontalScrollView horizontal;
  android.app.AlertDialog pageDialog;
  ScrollView scroll;
  Bitmap bitmap;
  EditText notes;
  Runnable saveNotes;
  volatile boolean closed;
  int generation, page, pages;
  float zoom = 1;

  GuidePane(Context c, String pkg, Runnable dismiss) {
    super(c);
    context = c;
    this.pkg = pkg;
    setOrientation(VERTICAL);
    setBackgroundColor(Ui.BG);
    setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
    JSONObject m = OfflineGuides.meta(c, pkg);
    LinearLayout head = Ui.row(c);
    head.addView(Ui.title(c, Store.name(c, pkg), 18), new LayoutParams(0, -2, 1));
    head.addView(Ui.button(c, "Sluiten", dismiss), new LayoutParams(-2, -2));
    addView(head);
    addView(Ui.rawText(c, m.optString("name", Language.text(c, "Offline gids")), 13, Ui.MUTED));
    scroll = new ScrollView(c);
    if (Store.prefs(c).getBoolean("guideNotes", false)) {
      LinearLayout panes = Ui.row(c);
      panes.setGravity(Gravity.TOP);
      panes.addView(scroll, new LayoutParams(0, -1, 1.5f));
      LinearLayout side = Ui.col(c);
      side.setPadding(Ui.dp(c, 12), 0, 0, 0);
      side.addView(Ui.title(c, "Notities", 16));
      notes = Ui.input(c, "Schrijf je notities…");
      notes.setSingleLine(false);
      notes.setGravity(Gravity.TOP);
      notes.setInputType(
          android.text.InputType.TYPE_CLASS_TEXT
              | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
      notes.setText(Store.prefs(c).getString("notes:" + pkg, ""));
      ScrollView noteScroll = new ScrollView(c);
      noteScroll.addView(notes);
      side.addView(noteScroll, new LayoutParams(-1, 0, 1));
      saveNotes =
          () -> {
            String value = notes.getText().toString();
            if (value.length() > 50000) Ui.toast(c, "Notes are limited to 50,000 characters.");
            else {
              Store.prefs(c).edit().putString("notes:" + pkg, value).commit();
              Ui.toast(c, "Notities opgeslagen.");
            }
          };
      side.addView(Ui.button(c, "Opslaan", saveNotes));
      panes.addView(side, new LayoutParams(0, -1, 1));
      addView(panes, new LayoutParams(-1, 0, 1));
    } else addView(scroll, new LayoutParams(-1, 0, 1));
    LinearLayout content = Ui.col(c);
    scroll.addView(content);
    status = Ui.text(c, "Gids laden…", 14, Ui.MUTED);
    content.addView(status);
    if (m.optString("kind").equals("pdf")) {
      image = new ImageView(c);
      image.setAdjustViewBounds(true);
      image.setScaleType(ImageView.ScaleType.FIT_CENTER);
      horizontal = new HorizontalScrollView(c);
      horizontal.addView(image, new android.widget.FrameLayout.LayoutParams(-2, -2));
      content.addView(horizontal, new LayoutParams(-1, -2));
      pages = m.optInt("pages", 1);
      page = Math.max(0, Math.min(pages - 1, m.optInt("page", 0)));
      LinearLayout controls = Ui.row(c);
      addButton(
          controls,
          "Vorige",
          () -> {
            if (page > 0) {
              page--;
              renderPdf();
            }
          });
      addButton(
          controls,
          "Pagina",
          () -> {
            EditText input = Ui.input(c, "Paginanummer");
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            pageDialog =
                new Ui.Dialog(c)
                    .setTitle("Ga naar pagina")
                    .setView(input)
                    .setPositiveButton(
                        "Openen",
                        (d, w) -> {
                          try {
                            int target = Integer.parseInt(input.getText().toString()) - 1;
                            if (target < 0 || target >= pages) throw new Exception();
                            page = target;
                            renderPdf();
                          } catch (Exception e) {
                            Ui.toast(c, "Kies een pagina van 1 tot " + pages);
                          }
                        })
                    .setNegativeButton("Annuleren", null)
                    .create();
            if (!(c instanceof android.app.Activity))
              pageDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
            pageDialog.show();
          });
      addButton(
          controls,
          "Volgende",
          () -> {
            if (page + 1 < pages) {
              page++;
              renderPdf();
            }
          });
      addButton(
          controls,
          "Zoom",
          () -> {
            zoom = zoom == 1 ? 1.5f : zoom == 1.5f ? 2 : 1;
            renderPdf();
          });
      addView(controls);
      renderPdf();
    } else {
      TextView text = Ui.rawText(c, "", 17, Ui.TEXT);
      text.setTextIsSelectable(true);
      content.addView(text);
      worker.execute(
          () -> {
            try {
              String decoded =
                  new String(
                      OfflineGuides.read(OfflineGuides.file(c, pkg)),
                      java.nio.charset.StandardCharsets.UTF_8);
              main.post(
                  () -> {
                    if (closed) return;
                    status.setText("Offline tekst · je leespositie blijft bewaard");
                    text.setText(decoded);
                    scroll.post(() -> scroll.scrollTo(0, m.optInt("scroll", 0)));
                  });
            } catch (Exception e) {
              main.post(
                  () -> {
                    if (!closed) status.setText("Gids niet leesbaar: " + e.getMessage());
                  });
            }
          });
      scroll.setOnScrollChangeListener(
          (v, x, y, oldx, oldy) -> OfflineGuides.remember(c, pkg, "scroll", y));
    }
  }

  void addButton(LinearLayout row, String label, Runnable action) {
    row.addView(Ui.button(context, label, action), new LayoutParams(0, -2, 1));
  }

  void renderPdf() {
    int request = ++generation, target = page;
    float scale = zoom;
    status.setText("Pagina " + (page + 1) + " van " + pages + " · " + Math.round(zoom * 100) + "%");
    OfflineGuides.remember(context, pkg, "page", page);
    worker.execute(
        () -> {
          Bitmap rendered = null;
          try {
            try (ParcelFileDescriptor fd =
                    ParcelFileDescriptor.open(
                        OfflineGuides.file(context, pkg), ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer pdf = new PdfRenderer(fd);
                PdfRenderer.Page p = pdf.openPage(target)) {
              int width =
                  Math.min(
                      1800, Math.max(640, context.getResources().getDisplayMetrics().widthPixels));
              double ratio = (double) p.getHeight() / Math.max(1, p.getWidth());
              int height = (int) Math.min(2600, Math.max(1, Math.round(width * ratio)));
              if (width * ratio > 2600) width = Math.max(1, (int) (height / ratio));
              rendered = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
              rendered.eraseColor(Color.WHITE);
              p.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            }
            Bitmap result = rendered;
            main.post(
                () -> {
                  if (closed || request != generation) {
                    result.recycle();
                    return;
                  }
                  bitmap = result;
                  image.setImageBitmap(result);
                  int available =
                      Math.max(
                          Ui.dp(context, 200),
                          (getWidth() > 0
                                  ? getWidth()
                                  : context.getResources().getDisplayMetrics().widthPixels)
                              - Ui.dp(context, 28));
                  image.setLayoutParams(
                      new android.widget.FrameLayout.LayoutParams(
                          (int) (available * scale),
                          (int)
                              (result.getHeight()
                                  * (available / (float) result.getWidth())
                                  * scale)));
                  scroll.scrollTo(0, 0);
                });
          } catch (Exception e) {
            if (rendered != null) rendered.recycle();
            main.post(
                () -> {
                  if (!closed && request == generation)
                    status.setText("PDF niet leesbaar: " + e.getMessage());
                });
          }
        });
  }

  void close() {
    if (pageDialog != null) pageDialog.dismiss();
    closed = true;
    generation++;
    worker.shutdown();
    if (image != null) image.setImageDrawable(null);
    bitmap = null;
  }
}
