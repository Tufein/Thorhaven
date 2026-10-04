package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Bounded vector notes. Completed strokes and all tools survive view/activity recreation. */
final class SketchPad {
  static final String KEY = "sketchBook";
  static final int MAX_BYTES = 250000, MAX_PAGES = 20, MAX_STROKES = 500, MAX_POINTS = 6000;
  static final int POINTS_PER_STROKE = 512, PNG_EXPORT_REQUEST = 151;
  static final int REQUEST_FIRST = 2000, REQUEST_LAST = 32760;
  static final int PNG_WIDTH = 1200, PNG_HEIGHT = 800;
  static final String LOCAL_PREFS = "thorhaven-sketch-local", PENDING = "pendingPng";
  static final ExecutorService worker = Executors.newSingleThreadExecutor();
  static final int[] COLORS = {0x1b263b, 0x1565c0, 0x2e7d32, 0xc62828, 0x6a1b9a, 0xe65100};

  interface Update {
    void apply(JSONObject root) throws Exception;
  }

  static String words(Context c, String nl, String en) {
    return GameLibrary.words(c, nl, en);
  }

  static JSONObject defaults() {
    try {
      return new JSONObject().put("schema", 1).put("active", "").put("pages", new JSONArray());
    } catch (JSONException e) {
      throw new AssertionError(e);
    }
  }

  static void validate(String json) throws Exception {
    validateRoot(StrictJson.object(json, MAX_BYTES));
  }

  static void validateRoot(JSONObject root) throws Exception {
    GameLibrary.keys(root, "schema", "active", "pages");
    GameLibrary.integer(root, "schema", 1, 1);
    String active = GameLibrary.string(root, "active", 32, true, false);
    if (!active.isEmpty()) GameLibrary.id(active);
    JSONArray pages = GameLibrary.array(root, "pages", MAX_PAGES);
    Set<String> ids = new HashSet<>();
    int points = 0, strokes = 0;
    for (int i = 0; i < pages.length(); i++) {
      JSONObject page = GameLibrary.object(pages, i);
      GameLibrary.keys(page, "id", "name", "grid", "color", "width", "strokes", "redo");
      GameLibrary.unique(ids, GameLibrary.string(page, "id", 32, false, false));
      GameLibrary.string(page, "name", 120, false, false);
      GameLibrary.bool(page, "grid");
      GameLibrary.integer(page, "color", 0, 0xffffff);
      GameLibrary.integer(page, "width", 1, 16);
      for (String key : new String[] {"strokes", "redo"}) {
        JSONArray list = GameLibrary.array(page, key, MAX_STROKES);
        strokes += list.length();
        for (int j = 0; j < list.length(); j++)
          points += validateStroke(GameLibrary.object(list, j));
      }
    }
    if ((pages.length() == 0 && !active.isEmpty()) || (pages.length() > 0 && !ids.contains(active)))
      throw new IOException("Invalid selected sketch page");
    if (strokes > MAX_STROKES || points > MAX_POINTS) throw new IOException("Sketch book is full");
  }

  static int validateStroke(JSONObject stroke) throws Exception {
    GameLibrary.keys(stroke, "color", "width", "points");
    GameLibrary.integer(stroke, "color", 0, 0xffffff);
    GameLibrary.integer(stroke, "width", 1, 16);
    JSONArray points = GameLibrary.array(stroke, "points", POINTS_PER_STROKE);
    if (points.length() == 0) throw new IOException("Empty sketch stroke");
    for (int i = 0; i < points.length(); i++) {
      Object item = points.get(i);
      if (!(item instanceof JSONArray) || ((JSONArray) item).length() != 2)
        throw new IOException("Invalid sketch point");
      JSONArray point = (JSONArray) item;
      for (int j = 0; j < 2; j++) {
        Object value = point.get(j);
        if (!(value instanceof Integer) && !(value instanceof Long))
          throw new IOException("Invalid sketch coordinate");
        long number = ((Number) value).longValue();
        if (number < 0 || number > 10000) throw new IOException("Sketch coordinate outside canvas");
      }
    }
    return points.length();
  }

  static JSONObject snapshot(Context c) throws Exception {
    String json = Store.prefs(c).getString(KEY, defaults().toString());
    validate(json);
    return new JSONObject(json);
  }

  static synchronized void update(Context c, Update change) throws Exception {
    JSONObject latest = snapshot(c);
    change.apply(latest);
    String json = latest.toString();
    validate(json);
    if (!Store.prefs(c).edit().putString(KEY, json).commit())
      throw new IOException("Could not save sketch book");
  }

  static JSONObject require(JSONObject root, String pageId) throws Exception {
    GameLibrary.id(pageId);
    JSONArray pages = root.getJSONArray("pages");
    for (int i = 0; i < pages.length(); i++)
      if (pages.getJSONObject(i).getString("id").equals(pageId)) return pages.getJSONObject(i);
    throw new IOException("Sketch page no longer exists");
  }

  static JSONObject page(Context c, String pageId) throws Exception {
    return require(snapshot(c), pageId);
  }

  static String add(Context c, String name) throws Exception {
    String pageId = GameLibrary.newId();
    JSONObject page =
        new JSONObject()
            .put("id", pageId)
            .put("name", name)
            .put("grid", false)
            .put("color", COLORS[0])
            .put("width", 3)
            .put("strokes", new JSONArray())
            .put("redo", new JSONArray());
    update(
        c,
        root -> {
          root.getJSONArray("pages").put(page);
          root.put("active", pageId);
        });
    return pageId;
  }

  static void select(Context c, String pageId) throws Exception {
    update(
        c,
        root -> {
          require(root, pageId);
          root.put("active", pageId);
        });
  }

  static void rename(Context c, String pageId, String name) throws Exception {
    update(c, root -> require(root, pageId).put("name", name));
  }

  static void remove(Context c, String pageId) throws Exception {
    update(
        c,
        root -> {
          require(root, pageId);
          JSONArray old = root.getJSONArray("pages"), kept = new JSONArray();
          for (int i = 0; i < old.length(); i++)
            if (!old.getJSONObject(i).getString("id").equals(pageId)) kept.put(old.get(i));
          root.put("pages", kept);
          if (root.getString("active").equals(pageId))
            root.put("active", kept.length() == 0 ? "" : kept.getJSONObject(0).getString("id"));
        });
  }

  static void tools(Context c, String pageId, Integer color, Integer width, Boolean grid)
      throws Exception {
    update(
        c,
        root -> {
          JSONObject page = require(root, pageId);
          if (color != null) page.put("color", color);
          if (width != null) page.put("width", width);
          if (grid != null) page.put("grid", grid);
        });
  }

  static void stroke(Context c, String pageId, JSONObject stroke) throws Exception {
    validateStroke(stroke);
    JSONObject detached = GameLibrary.copy(stroke);
    update(
        c,
        root -> {
          JSONObject page = require(root, pageId);
          page.getJSONArray("strokes").put(detached);
          page.put("redo", new JSONArray());
        });
  }

  static void history(Context c, String pageId, boolean redo) throws Exception {
    update(
        c,
        root -> {
          JSONObject page = require(root, pageId);
          String from = redo ? "redo" : "strokes", to = redo ? "strokes" : "redo";
          JSONArray source = page.getJSONArray(from),
              destination = page.getJSONArray(to),
              kept = new JSONArray();
          if (source.length() == 0) return;
          for (int i = 0; i < source.length() - 1; i++) kept.put(source.get(i));
          destination.put(source.get(source.length() - 1));
          page.put(from, kept);
        });
  }

  static void clear(Context c, String pageId) throws Exception {
    update(
        c,
        root -> require(root, pageId).put("strokes", new JSONArray()).put("redo", new JSONArray()));
  }

  static void validatePage(JSONObject page) throws Exception {
    JSONObject root = defaults().put("active", page.get("id"));
    root.getJSONArray("pages").put(GameLibrary.copy(page));
    validate(root.toString());
  }

  static void draw(Canvas canvas, JSONObject page, int width, int height) throws Exception {
    canvas.drawColor(Color.rgb(246, 248, 250));
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    if (page.getBoolean("grid")) {
      paint.setColor(Color.rgb(207, 216, 220));
      paint.setStrokeWidth(1);
      for (int i = 1; i < 10; i++) {
        canvas.drawLine(width * i / 10f, 0, width * i / 10f, height, paint);
        canvas.drawLine(0, height * i / 10f, width, height * i / 10f, paint);
      }
    }
    JSONArray strokes = page.getJSONArray("strokes");
    for (int i = 0; i < strokes.length(); i++)
      drawStroke(canvas, strokes.getJSONObject(i), width, height, paint);
  }

  static void drawStroke(Canvas canvas, JSONObject stroke, int width, int height, Paint paint)
      throws Exception {
    paint.setColor(0xff000000 | stroke.getInt("color"));
    paint.setStrokeWidth(Math.max(1f, stroke.getInt("width") * Math.min(width, height) / 300f));
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
    JSONArray points = stroke.getJSONArray("points");
    if (points.length() == 1) {
      JSONArray point = points.getJSONArray(0);
      paint.setStyle(Paint.Style.FILL);
      canvas.drawCircle(
          point.getInt(0) * (width - 1) / 10000f,
          point.getInt(1) * (height - 1) / 10000f,
          paint.getStrokeWidth() / 2,
          paint);
    } else {
      paint.setStyle(Paint.Style.STROKE);
      Path path = new Path();
      for (int i = 0; i < points.length(); i++) {
        JSONArray point = points.getJSONArray(i);
        float x = point.getInt(0) * (width - 1) / 10000f,
            y = point.getInt(1) * (height - 1) / 10000f;
        if (i == 0) path.moveTo(x, y);
        else path.lineTo(x, y);
      }
      canvas.drawPath(path, paint);
    }
    paint.setStyle(Paint.Style.FILL);
  }

  static Bitmap render(JSONObject page, int width, int height) throws Exception {
    validatePage(page);
    if (width < 16
        || height < 16
        || width > 1920
        || height > 1080
        || (long) width * height > 2073600) throw new IOException("PNG dimensions outside limits");
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    try {
      draw(new Canvas(bitmap), page, width, height);
      return bitmap;
    } catch (Exception | Error e) {
      bitmap.recycle();
      throw e;
    }
  }

  static void writePng(JSONObject page, OutputStream out) throws Exception {
    Bitmap bitmap = render(page, PNG_WIDTH, PNG_HEIGHT);
    try {
      if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
        throw new IOException("Could not encode PNG");
      out.flush();
    } finally {
      bitmap.recycle();
    }
  }

  static void exportPng(Context c, Uri uri, JSONObject page) throws Exception {
    validatePage(page);
    if (uri == null
        || !"content".equals(uri.getScheme())
        || uri.getAuthority() == null
        || uri.getAuthority().isEmpty())
      throw new IOException("Choose a PNG destination using Android Files");
    try (OutputStream out = c.getContentResolver().openOutputStream(uri, "wt")) {
      if (out == null) throw new IOException("Export destination unavailable");
      writePng(page, out);
    }
  }

  static SharedPreferences local(Context c) {
    return c.getSharedPreferences(LOCAL_PREFS, 0);
  }

  static synchronized void cancelExport(Context c) throws IOException {
    if (!local(c).edit().remove(PENDING).commit())
      throw new IOException("Could not cancel pending PNG export");
  }

  static boolean handlesRequest(int request) {
    return request >= REQUEST_FIRST && request <= REQUEST_LAST;
  }

  static synchronized String prepareExport(Context c, String pageId) throws Exception {
    JSONObject current = page(c, pageId);
    validatePage(current);
    if (local(c).contains(PENDING))
      throw new IOException("A PNG export is already awaiting the file picker");
    String token = GameLibrary.newId();
    JSONObject wrapper = new JSONObject().put("token", token).put("page", current);
    if (!local(c).edit().putString(PENDING, wrapper.toString()).commit())
      throw new IOException("Could not prepare PNG export");
    return token;
  }

  /** A late result can consume only the snapshot that its Activity originally requested. */
  static synchronized JSONObject consumeExport(Context c, String token) throws Exception {
    if (token == null || token.isEmpty()) return null;
    String raw = local(c).getString(PENDING, null);
    if (raw == null) return null;
    JSONObject wrapper = StrictJson.object(raw, MAX_BYTES + 256);
    GameLibrary.keys(wrapper, "token", "page");
    String actual = GameLibrary.string(wrapper, "token", 32, false, false);
    GameLibrary.id(actual);
    if (!actual.equals(token)) return null;
    Object value = wrapper.get("page");
    if (!(value instanceof JSONObject)) throw new IOException("Invalid pending PNG snapshot");
    JSONObject page = (JSONObject) value;
    validatePage(page);
    cancelExport(c);
    return page;
  }

  static void beginExport(Activity a, String pageId) {
    GameLibrary.attempt(
        a,
        () -> {
          if (!(a instanceof MainActivity))
            throw new IOException("Open the sketch pad in Thorhaven to export");
          MainActivity owner = (MainActivity) a;
          if (owner.sketchExportRequest >= REQUEST_LAST)
            throw new IOException("Close and reopen Thorhaven before starting another PNG export");
          String token = prepareExport(a, pageId);
          owner.sketchExportToken = token;
          int request = ++owner.sketchExportRequest;
          try {
            String name = page(a, pageId).getString("name").replaceAll("[\\\\/:*?\"<>|]", "_");
            a.startActivityForResult(
                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_TITLE, name + ".png"),
                request);
          } catch (Exception e) {
            owner.sketchExportToken = "";
            consumeExport(a, token);
            throw e;
          }
        });
  }

  static void onActivityResult(Activity a, int request, int result, Intent data) {
    if (!(a instanceof MainActivity)) return;
    MainActivity owner = (MainActivity) a;
    if (request != owner.sketchExportRequest) return;
    String token = owner.sketchExportToken;
    owner.sketchExportToken = "";
    JSONObject page;
    try {
      page = consumeExport(a, token);
    } catch (Exception e) {
      Ui.toast(a, e.getMessage());
      return;
    }
    if (result != Activity.RESULT_OK || data == null || data.getData() == null || page == null)
      return;
    Uri destination = data.getData();
    Context context = a.getApplicationContext();
    Handler main = new Handler(Looper.getMainLooper());
    worker.execute(
        () -> {
          String message;
          try {
            exportPng(context, destination, page);
            message =
                words(
                    context,
                    "PNG opgeslagen. Het tekenboek blijft bewerkbaar in Thorhaven.",
                    "PNG saved. The sketch book remains editable in Thorhaven.");
          } catch (Exception e) {
            message = words(context, "PNG niet opgeslagen: ", "PNG not saved: ") + e.getMessage();
          }
          String finalMessage = message;
          main.post(() -> Ui.toast(context, finalMessage));
        });
  }

  static void page(Activity a, LinearLayout parent) {
    LinearLayout card =
        Ui.card(
            a,
            parent,
            words(a, "Tekenblok", "Sketch pad"),
            words(
                a,
                "Teken je eigen kaart of puzzelnotitie. Afgeronde lijnen worden automatisch"
                    + " bewaard; PNG-export maakt een afbeelding.",
                "Draw your own map or puzzle note. Completed strokes save automatically; PNG export"
                    + " creates an image."));
    LinearLayout body = Ui.col(a);
    card.addView(body);
    Runnable refresh =
        () -> {
          if (!GameLibrary.alive(a)) return;
          body.removeAllViews();
          try {
            JSONObject root = snapshot(a);
            if (local(a).contains(PENDING))
              body.addView(
                  Ui.rawButton(
                      a,
                      words(a, "PNG-export annuleren", "Cancel pending PNG export"),
                      () ->
                          GameLibrary.attempt(
                              a,
                              () -> {
                                cancelExport(a);
                                Ui.toast(
                                    a,
                                    words(
                                        a,
                                        "Exportaanvraag geannuleerd. Je kunt opnieuw exporteren.",
                                        "Export request canceled. You can export again."));
                                GameLibrary.refreshChildren(card);
                              })));
            body.addView(
                Ui.rawButton(
                    a,
                    words(a, "Nieuwe tekening", "New sketch"),
                    () -> nameDialog(a, null, () -> GameLibrary.refreshChildren(card))));
            JSONArray pages = root.getJSONArray("pages");
            if (pages.length() == 0) {
              body.addView(
                  Ui.rawText(
                      a,
                      words(a, "Maak een tekening om te beginnen.", "Create a sketch to begin."),
                      14,
                      Ui.MUTED));
              return;
            }
            Spinner selection = new Spinner(a);
            String[] names = new String[pages.length()];
            int activeIndex = 0;
            for (int i = 0; i < pages.length(); i++) {
              names[i] = pages.getJSONObject(i).getString("name");
              if (pages.getJSONObject(i).getString("id").equals(root.getString("active")))
                activeIndex = i;
            }
            selection.setAdapter(
                new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, names));
            selection.setSelection(activeIndex);
            body.addView(selection);
            String pageId = root.getString("active");
            JSONObject page = require(root, pageId);
            SketchCanvas canvas = new SketchCanvas(a, pageId, () -> {});
            body.addView(canvas, new LinearLayout.LayoutParams(-1, -2));
            int[] previousPage = {activeIndex};
            selection.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                  public void onItemSelected(AdapterView<?> p, View v, int index, long id) {
                    if (index == previousPage[0]) return;
                    previousPage[0] = index;
                    try {
                      String chosen = pages.getJSONObject(index).getString("id");
                      if (!chosen.equals(pageId)) {
                        select(a, chosen);
                        GameLibrary.refreshChildren(card);
                      }
                    } catch (Exception e) {
                      Ui.toast(a, e.getMessage());
                    }
                  }

                  public void onNothingSelected(AdapterView<?> p) {}
                });
            body.addView(Ui.rawText(a, words(a, "Kleur", "Color"), 14, Ui.MUTED));
            Spinner colors = new Spinner(a);
            String[] colorNames = {
              words(a, "Donker", "Dark"),
              words(a, "Blauw", "Blue"),
              words(a, "Groen", "Green"),
              words(a, "Rood", "Red"),
              words(a, "Paars", "Purple"),
              words(a, "Oranje", "Orange")
            };
            colors.setAdapter(
                new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, colorNames));
            int selected = -1;
            for (int i = 0; i < COLORS.length; i++)
              if (COLORS[i] == page.getInt("color")) selected = i;
            if (selected < 0) {
              String[] withCustom = Arrays.copyOf(colorNames, colorNames.length + 1);
              withCustom[colorNames.length] =
                  words(a, "Eigen kleur", "Custom color")
                      + " #"
                      + String.format(Locale.ROOT, "%06X", page.getInt("color"));
              colors.setAdapter(
                  new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, withCustom));
              selected = colorNames.length;
            }
            colors.setSelection(selected);
            body.addView(colors);
            int[] previousColor = {selected};
            colors.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                  public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
                    if (i == previousColor[0]) return;
                    previousColor[0] = i;
                    if (i < COLORS.length)
                      GameLibrary.attempt(
                          a,
                          () -> {
                            if (page(a, pageId).getInt("color") != COLORS[i])
                              tools(a, pageId, COLORS[i], null, null);
                            canvas.reload();
                          });
                  }

                  public void onNothingSelected(AdapterView<?> p) {}
                });
            body.addView(Ui.rawText(a, words(a, "Lijndikte", "Stroke width"), 14, Ui.MUTED));
            Spinner widths = new Spinner(a);
            String[] widthLabels = new String[16];
            for (int i = 0; i < widthLabels.length; i++) widthLabels[i] = Integer.toString(i + 1);
            widths.setAdapter(
                new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, widthLabels));
            widths.setSelection(page.getInt("width") - 1);
            body.addView(widths);
            int[] previousWidth = {page.getInt("width") - 1};
            widths.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                  public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
                    if (i == previousWidth[0]) return;
                    previousWidth[0] = i;
                    GameLibrary.attempt(
                        a,
                        () -> {
                          if (page(a, pageId).getInt("width") != i + 1)
                            tools(a, pageId, null, i + 1, null);
                          canvas.reload();
                        });
                  }

                  public void onNothingSelected(AdapterView<?> p) {}
                });
            CheckBox grid = new CheckBox(a);
            grid.setText(words(a, "Raster tonen", "Show grid"));
            grid.setTextColor(Ui.TEXT);
            grid.setChecked(page.getBoolean("grid"));
            body.addView(grid);
            grid.setOnCheckedChangeListener(
                (button, checked) ->
                    GameLibrary.attempt(
                        a,
                        () -> {
                          tools(a, pageId, null, null, checked);
                          canvas.reload();
                        }));
            body.addView(
                Ui.rawButton(
                    a,
                    words(a, "Ongedaan maken", "Undo"),
                    () ->
                        GameLibrary.attempt(
                            a,
                            () -> {
                              canvas.cancelStroke();
                              history(a, pageId, false);
                              canvas.reload();
                            })));
            body.addView(
                Ui.rawButton(
                    a,
                    words(a, "Opnieuw uitvoeren", "Redo"),
                    () ->
                        GameLibrary.attempt(
                            a,
                            () -> {
                              canvas.cancelStroke();
                              history(a, pageId, true);
                              canvas.reload();
                            })));
            body.addView(
                Ui.rawButton(
                    a,
                    words(a, "Naam wijzigen", "Rename"),
                    () -> nameDialog(a, pageId, () -> GameLibrary.refreshChildren(card))));
            body.addView(
                Ui.rawButton(
                    a, words(a, "PNG exporteren", "Export PNG"), () -> beginExport(a, pageId)));
            body.addView(
                Ui.rawButton(
                    a,
                    words(a, "Lijnen wissen", "Clear strokes"),
                    () ->
                        GameLibrary.confirm(
                            a,
                            "Alle lijnen van deze tekening wissen?",
                            "Clear all strokes on this sketch?",
                            () -> {
                              canvas.cancelStroke();
                              clear(a, pageId);
                              canvas.reload();
                            })));
            body.addView(
                Ui.rawButton(
                    a,
                    words(a, "Tekening verwijderen", "Delete sketch"),
                    () ->
                        GameLibrary.confirm(
                            a,
                            "Deze tekening verwijderen?",
                            "Delete this sketch?",
                            () -> {
                              canvas.cancelStroke();
                              remove(a, pageId);
                              GameLibrary.refreshChildren(card);
                            })));
            body.addView(
                Ui.rawText(
                    a,
                    words(
                        a,
                        "Maximaal 20 tekeningen, 500 lijnen en 6000 punten in het hele tekenboek."
                            + " Een onderbroken lijn wordt niet bewaard.",
                        "Up to 20 sketches, 500 strokes and 6000 points across the whole book. An"
                            + " interrupted stroke is not saved."),
                    13,
                    Ui.MUTED));
          } catch (Exception e) {
            body.addView(
                Ui.rawText(
                    a,
                    words(a, "Tekenboek niet beschikbaar: ", "Sketch book unavailable: ")
                        + e.getMessage(),
                    14,
                    Ui.MUTED));
          }
        };
    body.setTag(refresh);
    refresh.run();
  }

  static AlertDialog nameDialog(Activity a, String pageId, Runnable changed) {
    try {
      EditText name = GameLibrary.input(a, "Naam van de tekening", "Sketch name", 120, false);
      if (pageId != null) name.setText(page(a, pageId).getString("name"));
      AlertDialog dialog =
          new AlertDialog.Builder(a)
              .setTitle(
                  words(
                      a,
                      pageId == null ? "Nieuwe tekening" : "Naam wijzigen",
                      pageId == null ? "New sketch" : "Rename"))
              .setView(name)
              .setNegativeButton(words(a, "Annuleren", "Cancel"), null)
              .setPositiveButton(words(a, "Opslaan", "Save"), null)
              .create();
      dialog.setOnShowListener(
          d ->
              dialog
                  .getButton(AlertDialog.BUTTON_POSITIVE)
                  .setOnClickListener(
                      v -> {
                        try {
                          if (pageId == null) add(a, name.getText().toString());
                          else rename(a, pageId, name.getText().toString());
                          dialog.dismiss();
                          changed.run();
                        } catch (Exception e) {
                          name.setError(
                              words(a, "Niet opgeslagen: ", "Not saved: ") + e.getMessage());
                        }
                      }));
      dialog.show();
      return dialog;
    } catch (Exception e) {
      Ui.toast(a, e.getMessage());
      return null;
    }
  }

  static final class SketchCanvas extends View {
    final String pageId;
    final Runnable changed;
    JSONObject saved, draft;
    int pointer = -1;
    boolean truncated;

    SketchCanvas(Context c, String pageId, Runnable changed) throws Exception {
      super(c);
      this.pageId = pageId;
      this.changed = changed;
      setFocusable(true);
      setClickable(true);
      setContentDescription(
          words(
              c, "Tekenoppervlak voor kaart- of puzzelnotities", "Canvas for map or puzzle notes"));
      saved = page(c, pageId);
    }

    void reload() throws Exception {
      saved = page(getContext(), pageId);
      invalidate();
    }

    void cancelStroke() {
      pointer = -1;
      draft = null;
      truncated = false;
      if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
      invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
      cancelStroke();
      super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
      int width = resolveSize(Ui.dp(getContext(), 360), widthSpec);
      setMeasuredDimension(width, Math.round(width * 2f / 3f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      try {
        SketchPad.draw(canvas, saved, getWidth(), getHeight());
        if (draft != null)
          drawStroke(canvas, draft, getWidth(), getHeight(), new Paint(Paint.ANTI_ALIAS_FLAG));
      } catch (Exception ignored) {
        canvas.drawColor(Color.rgb(246, 248, 250));
      }
    }

    void point(float x, float y) throws Exception {
      if (getWidth() < 2 || getHeight() < 2) return;
      JSONArray points = draft.getJSONArray("points");
      int px = Math.round(Math.max(0, Math.min(getWidth() - 1, x)) * 10000 / (getWidth() - 1)),
          py = Math.round(Math.max(0, Math.min(getHeight() - 1, y)) * 10000 / (getHeight() - 1));
      if (points.length() > 0) {
        JSONArray last = points.getJSONArray(points.length() - 1);
        if (last.getInt(0) == px && last.getInt(1) == py) return;
      }
      if (points.length() >= POINTS_PER_STROKE) {
        truncated = true;
        return;
      }
      points.put(new JSONArray().put(px).put(py));
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
      int action = event.getActionMasked();
      try {
        if (action == android.view.MotionEvent.ACTION_DOWN) {
          cancelStroke();
          reload();
          pointer = event.getPointerId(0);
          draft =
              new JSONObject()
                  .put("color", saved.getInt("color"))
                  .put("width", saved.getInt("width"))
                  .put("points", new JSONArray());
          if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
          point(event.getX(), event.getY());
        } else if (action == android.view.MotionEvent.ACTION_CANCEL) {
          cancelStroke();
          return true;
        } else if (draft != null) {
          int index = event.findPointerIndex(pointer);
          if (index < 0) {
            cancelStroke();
            return true;
          }
          if (action == android.view.MotionEvent.ACTION_MOVE) {
            for (int h = 0; h < event.getHistorySize(); h++)
              point(event.getHistoricalX(index, h), event.getHistoricalY(index, h));
            point(event.getX(index), event.getY(index));
          } else if (action == android.view.MotionEvent.ACTION_UP
              || (action == android.view.MotionEvent.ACTION_POINTER_UP
                  && event.getPointerId(event.getActionIndex()) == pointer)) {
            point(event.getX(index), event.getY(index));
            if (draft.getJSONArray("points").length() > 0) stroke(getContext(), pageId, draft);
            boolean capped = truncated;
            cancelStroke();
            reload();
            changed.run();
            performClick();
            if (capped)
              Ui.toast(
                  getContext(),
                  words(
                      getContext(),
                      "Deze lijn bereikte 512 punten; begin een nieuwe lijn.",
                      "This stroke reached 512 points; start a new stroke."));
          }
        }
        invalidate();
        return true;
      } catch (Exception e) {
        cancelStroke();
        try {
          reload();
        } catch (Exception ignored) {
        }
        Ui.toast(
            getContext(),
            words(getContext(), "Lijn niet opgeslagen: ", "Stroke not saved: ") + e.getMessage());
        return true;
      }
    }

    @Override
    public boolean performClick() {
      super.performClick();
      return true;
    }
  }
}
