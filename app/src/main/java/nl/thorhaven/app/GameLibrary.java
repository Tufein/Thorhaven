package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;
import org.json.*;

/** Manual, portable game cards. Every mutation reloads and validates the latest collection. */
final class GameLibrary {
  static final String KEY = "gameLibrary";
  static final int MAX_BYTES = 300000, MAX_GAMES = 200, MAX_JOURNAL = 100, MAX_TASKS = 100;
  static final long MAX_TIME = 4102444800000L;
  static final String[] STATUSES = {"backlog", "playing", "completed", "paused"};

  interface Update {
    void apply(JSONObject root) throws Exception;
  }

  interface Action {
    void run() throws Exception;
  }

  static String words(Context c, String nl, String en) {
    return Language.isEnglish(c) ? en : nl;
  }

  static JSONObject defaults() {
    try {
      return new JSONObject().put("schema", 1).put("games", new JSONArray());
    } catch (JSONException e) {
      throw new AssertionError(e);
    }
  }

  static JSONObject copy(JSONObject object) throws JSONException {
    return new JSONObject(object.toString());
  }

  static String newId() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  static void id(String value) throws IOException {
    if (value == null || !value.matches("[a-f0-9]{32}"))
      throw new IOException("Invalid local entry ID");
  }

  static void keys(JSONObject object, String... names) throws IOException {
    Set<String> allowed = new HashSet<>(Arrays.asList(names));
    if (object.length() != allowed.size()) throw new IOException("Incomplete or unknown fields");
    for (Iterator<String> i = object.keys(); i.hasNext(); )
      if (!allowed.contains(i.next())) throw new IOException("Unknown field");
  }

  static long integer(JSONObject object, String key, long min, long max) throws Exception {
    Object value = object.get(key);
    if (!(value instanceof Integer) && !(value instanceof Long))
      throw new IOException("Invalid integer: " + key);
    long number = ((Number) value).longValue();
    if (number < min || number > max) throw new IOException("Integer outside limits: " + key);
    return number;
  }

  static boolean bool(JSONObject object, String key) throws Exception {
    Object value = object.get(key);
    if (!(value instanceof Boolean)) throw new IOException("Invalid switch: " + key);
    return (Boolean) value;
  }

  static String text(String value, int max, boolean empty, boolean multiline) throws IOException {
    if (value == null || value.length() > max || (!empty && value.trim().isEmpty()))
      throw new IOException("Text outside limits");
    for (int i = 0; i < value.length(); i++) {
      char x = value.charAt(i);
      if (Character.isISOControl(x) && !(multiline && (x == '\n' || x == '\r' || x == '\t')))
        throw new IOException("Invalid text character");
      if (Character.isHighSurrogate(x)) {
        if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i)))
          throw new IOException("Invalid Unicode text");
      } else if (Character.isLowSurrogate(x)) throw new IOException("Invalid Unicode text");
    }
    return value;
  }

  static String string(JSONObject object, String key, int max, boolean empty, boolean multiline)
      throws Exception {
    Object value = object.get(key);
    if (!(value instanceof String)) throw new IOException("Invalid text field: " + key);
    return text((String) value, max, empty, multiline);
  }

  static JSONArray array(JSONObject object, String key, int max) throws Exception {
    Object value = object.get(key);
    if (!(value instanceof JSONArray) || ((JSONArray) value).length() > max)
      throw new IOException("Invalid list: " + key);
    return (JSONArray) value;
  }

  static JSONObject object(JSONArray array, int at) throws Exception {
    Object value = array.get(at);
    if (!(value instanceof JSONObject)) throw new IOException("Invalid list entry");
    return (JSONObject) value;
  }

  static void validate(String json) throws Exception {
    validateRoot(StrictJson.object(json, MAX_BYTES));
  }

  static void validateRoot(JSONObject root) throws Exception {
    keys(root, "schema", "games");
    integer(root, "schema", 1, 1);
    JSONArray games = array(root, "games", MAX_GAMES);
    Set<String> ids = new HashSet<>();
    int totalNotes = 0, totalTasks = 0;
    for (int i = 0; i < games.length(); i++) {
      JSONObject game = object(games, i);
      keys(
          game,
          "id",
          "title",
          "app",
          "status",
          "favorite",
          "tags",
          "guide",
          "created",
          "updated",
          "journal",
          "tasks");
      unique(ids, string(game, "id", 32, false, false));
      string(game, "title", 160, false, false);
      String app = string(game, "app", 200, true, false);
      if (!app.isEmpty() && !app.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))
        throw new IOException("Invalid app package");
      if (!Arrays.asList(STATUSES).contains(string(game, "status", 20, false, false)))
        throw new IOException("Invalid game status");
      bool(game, "favorite");
      JSONArray tags = array(game, "tags", 8);
      Set<String> labels = new HashSet<>();
      for (int j = 0; j < tags.length(); j++) {
        if (!(tags.get(j) instanceof String)) throw new IOException("Invalid label");
        String tag = text((String) tags.get(j), 30, false, false);
        if (!labels.add(tag.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate label");
      }
      String guide = string(game, "guide", 200, true, false);
      if (!guide.isEmpty()) OfflineGuides.valid(guide);
      long created = integer(game, "created", 0, MAX_TIME);
      long updated = integer(game, "updated", created, MAX_TIME);
      JSONArray journal = array(game, "journal", MAX_JOURNAL);
      totalNotes += journal.length();
      for (int j = 0; j < journal.length(); j++) {
        JSONObject entry = object(journal, j);
        keys(entry, "id", "text", "at");
        unique(ids, string(entry, "id", 32, false, false));
        string(entry, "text", 2000, false, true);
        integer(entry, "at", created, updated);
      }
      JSONArray tasks = array(game, "tasks", MAX_TASKS);
      totalTasks += tasks.length();
      for (int j = 0; j < tasks.length(); j++) {
        JSONObject task = object(tasks, j);
        keys(task, "id", "text", "done");
        unique(ids, string(task, "id", 32, false, false));
        string(task, "text", 300, false, false);
        bool(task, "done");
      }
    }
    if (totalNotes > 2000 || totalTasks > 2000) throw new IOException("Too many game entries");
  }

  static void unique(Set<String> ids, String value) throws IOException {
    id(value);
    if (!ids.add(value)) throw new IOException("Duplicate local entry ID");
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
      throw new IOException("Could not save game library");
  }

  static JSONObject require(JSONObject root, String gameId) throws Exception {
    id(gameId);
    JSONArray games = root.getJSONArray("games");
    for (int i = 0; i < games.length(); i++)
      if (games.getJSONObject(i).getString("id").equals(gameId)) return games.getJSONObject(i);
    throw new IOException("Game card no longer exists");
  }

  static JSONObject game(Context c, String gameId) throws Exception {
    return require(snapshot(c), gameId);
  }

  static JSONArray tags(String raw) throws Exception {
    JSONArray result = new JSONArray();
    if (raw == null || raw.trim().isEmpty()) return result;
    for (String tag : raw.split(",", -1)) result.put(text(tag.trim(), 30, false, false));
    return result;
  }

  static long stamp(JSONObject game) throws Exception {
    long now = Math.max(game.getLong("updated"), System.currentTimeMillis());
    game.put("updated", now);
    return now;
  }

  static String add(
      Context c,
      String title,
      String app,
      String status,
      boolean favorite,
      JSONArray tags,
      String guide)
      throws Exception {
    String gameId = newId();
    long time = System.currentTimeMillis();
    JSONObject entry =
        new JSONObject()
            .put("id", gameId)
            .put("title", title)
            .put("app", app)
            .put("status", status)
            .put("favorite", favorite)
            .put("tags", new JSONArray(tags.toString()))
            .put("guide", guide)
            .put("created", time)
            .put("updated", time)
            .put("journal", new JSONArray())
            .put("tasks", new JSONArray());
    update(c, root -> root.getJSONArray("games").put(entry));
    return gameId;
  }

  static void edit(
      Context c,
      String gameId,
      String title,
      String app,
      String status,
      boolean favorite,
      JSONArray tags,
      String guide)
      throws Exception {
    update(
        c,
        root -> {
          JSONObject game = require(root, gameId);
          game.put("title", title)
              .put("app", app)
              .put("status", status)
              .put("favorite", favorite)
              .put("tags", new JSONArray(tags.toString()))
              .put("guide", guide);
          stamp(game);
        });
  }

  static void remove(Context c, String gameId) throws Exception {
    update(
        c,
        root -> {
          require(root, gameId);
          JSONArray source = root.getJSONArray("games"), kept = new JSONArray();
          for (int i = 0; i < source.length(); i++)
            if (!source.getJSONObject(i).getString("id").equals(gameId)) kept.put(source.get(i));
          root.put("games", kept);
        });
  }

  static String addJournal(Context c, String gameId, String note) throws Exception {
    String entryId = newId();
    update(
        c,
        root -> {
          JSONObject game = require(root, gameId);
          game.getJSONArray("journal")
              .put(new JSONObject().put("id", entryId).put("text", note).put("at", stamp(game)));
        });
    return entryId;
  }

  static String addTask(Context c, String gameId, String title) throws Exception {
    String entryId = newId();
    update(
        c,
        root -> {
          JSONObject game = require(root, gameId);
          game.getJSONArray("tasks")
              .put(new JSONObject().put("id", entryId).put("text", title).put("done", false));
          stamp(game);
        });
    return entryId;
  }

  static void entry(
      Context c,
      String gameId,
      String list,
      String entryId,
      String value,
      Boolean done,
      boolean remove)
      throws Exception {
    if (!list.equals("journal") && !list.equals("tasks"))
      throw new IOException("Invalid entry list");
    id(entryId);
    update(
        c,
        root -> {
          JSONObject game = require(root, gameId);
          JSONArray old = game.getJSONArray(list), kept = new JSONArray();
          boolean found = false;
          for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.getJSONObject(i);
            if (item.getString("id").equals(entryId)) {
              found = true;
              if (remove) continue;
              if (value != null) item.put("text", value);
              if (done != null) {
                if (!list.equals("tasks"))
                  throw new IOException("Journal entries have no checkbox");
                item.put("done", done);
              }
            }
            kept.put(item);
          }
          if (!found) throw new IOException("Entry no longer exists");
          game.put(list, kept);
          stamp(game);
        });
  }

  static List<JSONObject> filter(JSONObject root, String query, String status, boolean favorites)
      throws Exception {
    String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    List<JSONObject> out = new ArrayList<>();
    JSONArray games = root.getJSONArray("games");
    for (int i = 0; i < games.length(); i++) {
      JSONObject game = games.getJSONObject(i);
      if ((!status.isEmpty() && !status.equals(game.getString("status")))
          || (favorites && !game.getBoolean("favorite"))) continue;
      String haystack =
          game.getString("title")
              + "\n"
              + game.getString("app")
              + "\n"
              + game.getJSONArray("tags").toString();
      if (q.isEmpty() || haystack.toLowerCase(Locale.ROOT).contains(q)) out.add(copy(game));
    }
    out.sort(
        Comparator.comparing((JSONObject game) -> game.optString("title").toLowerCase(Locale.ROOT))
            .thenComparing(game -> game.optString("id")));
    return out;
  }

  static boolean alive(Activity a) {
    return !a.isFinishing() && !a.isDestroyed();
  }

  static void attempt(Activity a, Action action) {
    try {
      action.run();
    } catch (Exception e) {
      if (alive(a)) Ui.toast(a, words(a, "Niet opgeslagen: ", "Not saved: ") + e.getMessage());
    }
  }

  static String statusLabel(Context c, String status) {
    switch (status) {
      case "playing":
        return words(c, "Nu aan het spelen", "Playing now");
      case "completed":
        return words(c, "Uitgespeeld", "Completed");
      case "paused":
        return words(c, "Gepauzeerd", "Paused");
      default:
        return words(c, "Nog spelen", "Backlog");
    }
  }

  static EditText input(Context c, String nl, String en, int max, boolean multiline) {
    EditText value = Ui.input(c, words(c, nl, en));
    value.setFilters(new InputFilter[] {new InputFilter.LengthFilter(max)});
    if (multiline) {
      value.setSingleLine(false);
      value.setMinLines(3);
      value.setGravity(Gravity.TOP);
    }
    return value;
  }

  static ScrollView scroll(Context c, LinearLayout content) {
    ScrollView view = new ScrollView(c);
    view.addView(content);
    view.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
    return view;
  }

  static void page(Activity a, LinearLayout parent) {
    LinearLayout card =
        Ui.card(
            a,
            parent,
            words(a, "Spelbibliotheek", "Game library"),
            words(
                a,
                "Eigen spelkaarten, dagboek en taken. Open een spelkaart en gebruik App openen;"
                    + " kies je ROM in die app.",
                "Your game cards, journal and tasks. Open a game card, then choose Open app; select"
                    + " your ROM inside that app."));
    EditText query = input(a, "Zoek titel, app of label", "Search title, app or label", 160, false);
    card.addView(query);
    Spinner status = new Spinner(a);
    String[] labels = new String[5];
    labels[0] = words(a, "Alle spellen", "All games");
    for (int i = 0; i < 4; i++) labels[i + 1] = statusLabel(a, STATUSES[i]);
    status.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, labels));
    card.addView(status);
    CheckBox favorites = new CheckBox(a);
    favorites.setText(words(a, "Alleen favorieten", "Favorites only"));
    favorites.setTextColor(Ui.TEXT);
    card.addView(favorites);
    TextView count = Ui.rawText(a, "", 13, Ui.MUTED);
    card.addView(count);
    LinearLayout rows = Ui.col(a);
    Runnable refresh =
        () -> {
          if (!alive(a)) return;
          rows.removeAllViews();
          try {
            JSONObject latest = snapshot(a);
            int chosen = Math.max(0, status.getSelectedItemPosition());
            List<JSONObject> found =
                filter(
                    latest,
                    query.getText().toString(),
                    chosen == 0 ? "" : STATUSES[chosen - 1],
                    favorites.isChecked());
            count.setText(found.size() + " / " + latest.getJSONArray("games").length());
            for (JSONObject game : found) {
              String gameId = game.getString("id");
              rows.addView(
                  Ui.rawText(
                      a,
                      (game.getBoolean("favorite") ? "★ " : "")
                          + game.getString("title")
                          + " · "
                          + statusLabel(a, game.getString("status")),
                      16,
                      Ui.TEXT));
              rows.addView(
                  Ui.rawButton(
                      a,
                      words(a, "Spelkaart openen", "Open game card"),
                      () -> details(a, gameId, refreshPage(parent))));
            }
            if (found.isEmpty())
              rows.addView(
                  Ui.rawText(
                      a,
                      words(
                          a,
                          "Geen spellen gevonden. Voeg je eerste spelkaart toe.",
                          "No games found. Add your first game card."),
                      14,
                      Ui.MUTED));
          } catch (Exception e) {
            count.setText(
                words(a, "Bibliotheek niet beschikbaar: ", "Library unavailable: ")
                    + e.getMessage());
          }
        };
    card.addView(
        Ui.rawButton(a, words(a, "Spel toevoegen", "Add game"), () -> editor(a, null, refresh)));
    card.addView(
        Ui.rawButton(
            a, words(a, "Nu aan het spelen", "Playing now"), () -> status.setSelection(2)));
    card.addView(rows);
    query.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int n, int after) {}

          public void onTextChanged(CharSequence s, int st, int before, int n) {
            refresh.run();
          }

          public void afterTextChanged(Editable e) {}
        });
    status.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
            refresh.run();
          }

          public void onNothingSelected(AdapterView<?> p) {}
        });
    favorites.setOnCheckedChangeListener((button, checked) -> refresh.run());
    // Details refresh the current page without keeping an Activity in static state.
    rows.setTag(refresh);
    refresh.run();
  }

  static Runnable refreshPage(LinearLayout parent) {
    return () -> {
      if (!parent.isAttachedToWindow()) return;
      refreshChildren(parent);
    };
  }

  static void refreshChildren(View view) {
    if (view.getTag() instanceof Runnable) {
      ((Runnable) view.getTag()).run();
      return;
    }
    if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) refreshChildren(group.getChildAt(i));
    }
  }

  static AlertDialog editor(Activity a, String gameId, Runnable changed) {
    try {
      JSONObject game = gameId == null ? null : game(a, gameId);
      LinearLayout content = Ui.col(a);
      EditText title = input(a, "Speltitel", "Game title", 160, false);
      title.setText(game == null ? "" : game.getString("title"));
      content.addView(title);
      EditText labels =
          input(a, "Labels, gescheiden door komma's", "Labels, separated by commas", 254, false);
      if (game != null) {
        List<String> values = new ArrayList<>();
        JSONArray tags = game.getJSONArray("tags");
        for (int i = 0; i < tags.length(); i++) values.add(tags.getString(i));
        labels.setText(String.join(", ", values));
      }
      content.addView(labels);
      Spinner status = new Spinner(a);
      String[] states = new String[4];
      for (int i = 0; i < 4; i++) states[i] = statusLabel(a, STATUSES[i]);
      status.setAdapter(
          new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, states));
      status.setSelection(
          game == null ? 0 : Arrays.asList(STATUSES).indexOf(game.getString("status")));
      content.addView(status);
      CheckBox favorite = new CheckBox(a);
      favorite.setText(words(a, "Favoriet", "Favorite"));
      favorite.setTextColor(Ui.TEXT);
      favorite.setChecked(game != null && game.getBoolean("favorite"));
      content.addView(favorite);
      String[] app = {game == null ? "" : game.getString("app")};
      TextView appLabel = Ui.rawText(a, "", 14, Ui.MUTED);
      Runnable appText =
          () ->
              appLabel.setText(
                  app[0].isEmpty()
                      ? words(a, "Geen app gekozen", "No app selected")
                      : Store.name(a, app[0]) + " · " + app[0]);
      appText.run();
      content.addView(appLabel);
      content.addView(
          Ui.rawButton(
              a,
              words(a, "App kiezen", "Choose app"),
              () ->
                  appPicker(
                      a,
                      pkg -> {
                        app[0] = pkg;
                        appText.run();
                      })));
      content.addView(
          Ui.rawButton(
              a,
              words(a, "App ontkoppelen", "Unlink app"),
              () -> {
                app[0] = "";
                appText.run();
              }));
      String[] guide = {game == null ? "" : game.getString("guide")};
      TextView guideLabel = Ui.rawText(a, "", 14, Ui.MUTED);
      Runnable guideText =
          () ->
              guideLabel.setText(
                  guide[0].isEmpty()
                      ? words(a, "Geen document gekoppeld", "No document linked")
                      : OfflineGuides.meta(a, guide[0]).optString("name", guide[0]));
      guideText.run();
      content.addView(guideLabel);
      content.addView(
          Ui.rawButton(
              a,
              words(a, "Offline document kiezen", "Choose offline document"),
              () -> {
                List<GuideTools.Entry> all = GuideTools.entries(a);
                String[] names = new String[all.size() + 1];
                names[0] = words(a, "Geen document", "No document");
                for (int i = 0; i < all.size(); i++) names[i + 1] = all.get(i).name;
                new AlertDialog.Builder(a)
                    .setTitle(words(a, "Document koppelen", "Link document"))
                    .setItems(
                        names,
                        (d, which) -> {
                          guide[0] = which == 0 ? "" : all.get(which - 1).id;
                          guideText.run();
                        })
                    .setNegativeButton(words(a, "Annuleren", "Cancel"), null)
                    .show();
              }));
      AlertDialog dialog =
          new AlertDialog.Builder(a)
              .setTitle(
                  words(
                      a,
                      gameId == null ? "Spel toevoegen" : "Spel bewerken",
                      gameId == null ? "Add game" : "Edit game"))
              .setView(scroll(a, content))
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
                          String state = STATUSES[Math.max(0, status.getSelectedItemPosition())];
                          if (gameId == null)
                            add(
                                a,
                                title.getText().toString(),
                                app[0],
                                state,
                                favorite.isChecked(),
                                tags(labels.getText().toString()),
                                guide[0]);
                          else
                            edit(
                                a,
                                gameId,
                                title.getText().toString(),
                                app[0],
                                state,
                                favorite.isChecked(),
                                tags(labels.getText().toString()),
                                guide[0]);
                          dialog.dismiss();
                          changed.run();
                        } catch (Exception e) {
                          title.setError(
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

  static AlertDialog appPicker(Activity a, java.util.function.Consumer<String> chosen) {
    LinearLayout content = Ui.col(a);
    EditText search = input(a, "Zoek app of pakketnaam", "Search app or package", 160, false);
    content.addView(search);
    TextView status = Ui.rawText(a, words(a, "Apps laden…", "Loading apps…"), 13, Ui.MUTED);
    content.addView(status);
    LinearLayout rows = Ui.col(a);
    content.addView(rows);
    AlertDialog dialog =
        new AlertDialog.Builder(a)
            .setTitle(words(a, "App kiezen", "Choose app"))
            .setView(scroll(a, content))
            .setNegativeButton(words(a, "Annuleren", "Cancel"), null)
            .create();
    List<Store.App> all = new ArrayList<>();
    Runnable refresh =
        () -> {
          if (!dialog.isShowing() || !alive(a)) return;
          rows.removeAllViews();
          String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
          int count = 0;
          for (Store.App app : all) {
            if (!(app.name + "\n" + app.pkg).toLowerCase(Locale.ROOT).contains(q)) continue;
            count++;
            if (count > 100) continue;
            rows.addView(
                Ui.rawButton(
                    a,
                    app.name + "\n" + app.pkg,
                    () -> {
                      chosen.accept(app.pkg);
                      dialog.dismiss();
                    }));
          }
          status.setText(
              count > 100
                  ? words(a, "Eerste 100 van ", "First 100 of ")
                      + count
                      + words(a, " apps. Verfijn je zoekopdracht.", " apps. Refine your search.")
                  : count + " apps");
        };
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            refresh.run();
          }

          public void afterTextChanged(Editable e) {}
        });
    dialog.show();
    Context appContext = a.getApplicationContext();
    Handler main = new Handler(Looper.getMainLooper());
    Controls.worker.execute(
        () -> {
          try {
            List<Store.App> found = Store.apps(appContext);
            main.post(
                () -> {
                  if (dialog.isShowing() && alive(a)) {
                    all.addAll(found);
                    refresh.run();
                  }
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (dialog.isShowing() && alive(a)) status.setText(e.getMessage());
                });
          }
        });
    return dialog;
  }

  static AlertDialog details(Activity a, String gameId, Runnable changed) {
    try {
      JSONObject selected = game(a, gameId);
      LinearLayout content = Ui.col(a);
      TextView state = Ui.rawText(a, "", 14, Ui.MUTED);
      content.addView(state);
      LinearLayout entries = Ui.col(a);
      content.addView(entries);
      Runnable refresh =
          () -> {
            if (!alive(a)) return;
            entries.removeAllViews();
            try {
              JSONObject game = game(a, gameId);
              state.setText(
                  statusLabel(a, game.getString("status"))
                      + " · "
                      + game.getJSONArray("tags").toString());
              entries.addView(
                  Ui.rawButton(
                      a,
                      words(a, "Spel bewerken", "Edit game"),
                      () ->
                          editor(
                              a,
                              gameId,
                              () -> {
                                changed.run();
                                refreshChildren(content);
                              })));
              if (!game.getString("app").isEmpty()) {
                String pkg = game.getString("app");
                entries.addView(
                    Ui.rawButton(
                        a,
                        words(a, "App openen: ", "Open app: ") + Store.name(a, pkg),
                        () -> Store.launch(a, pkg, Store.screen(a, false))));
                entries.addView(
                    Ui.rawText(
                        a,
                        words(
                            a,
                            "Kies het spel of de ROM vervolgens in de app.",
                            "Choose the game or ROM inside the app afterwards."),
                        13,
                        Ui.MUTED));
              }
              if (!game.getString("guide").isEmpty()) {
                String guide = game.getString("guide");
                entries.addView(
                    Ui.rawButton(
                        a,
                        words(a, "Gekoppeld document openen", "Open linked document"),
                        () -> {
                          if (OfflineGuides.exists(a, guide)) OfflineGuides.open(a, guide);
                          else
                            Ui.toast(
                                a,
                                words(
                                    a,
                                    "Dit document is niet meer beschikbaar.",
                                    "This document is no longer available."));
                        }));
              }
              entries.addView(Ui.rawText(a, words(a, "Dagboek", "Journal"), 18, Ui.ACCENT));
              entries.addView(
                  Ui.rawButton(
                      a,
                      words(a, "Dagboeknotitie toevoegen", "Add journal entry"),
                      () ->
                          noteEditor(
                              a,
                              gameId,
                              "journal",
                              null,
                              () -> {
                                changed.run();
                                refreshChildren(content);
                              })));
              JSONArray journal = game.getJSONArray("journal");
              for (int i = journal.length() - 1; i >= 0; i--) {
                JSONObject note = journal.getJSONObject(i);
                String entryId = note.getString("id");
                entries.addView(
                    Ui.rawText(
                        a,
                        java.text.DateFormat.getDateTimeInstance()
                                .format(new Date(note.getLong("at")))
                            + "\n"
                            + note.getString("text"),
                        14,
                        Ui.TEXT));
                entries.addView(
                    Ui.rawButton(
                        a,
                        words(a, "Notitie bewerken", "Edit entry"),
                        () ->
                            noteEditor(
                                a,
                                gameId,
                                "journal",
                                entryId,
                                () -> {
                                  changed.run();
                                  refreshChildren(content);
                                })));
                entries.addView(
                    Ui.rawButton(
                        a,
                        words(a, "Notitie verwijderen", "Delete entry"),
                        () ->
                            confirm(
                                a,
                                "Notitie verwijderen?",
                                "Delete entry?",
                                () -> {
                                  entry(a, gameId, "journal", entryId, null, null, true);
                                  changed.run();
                                  refreshChildren(content);
                                })));
              }
              entries.addView(
                  Ui.rawText(
                      a, words(a, "Taken voor dit spel", "Tasks for this game"), 18, Ui.ACCENT));
              entries.addView(
                  Ui.rawButton(
                      a,
                      words(a, "Taak toevoegen", "Add task"),
                      () ->
                          noteEditor(
                              a,
                              gameId,
                              "tasks",
                              null,
                              () -> {
                                changed.run();
                                refreshChildren(content);
                              })));
              JSONArray tasks = game.getJSONArray("tasks");
              for (int i = 0; i < tasks.length(); i++) {
                JSONObject task = tasks.getJSONObject(i);
                String entryId = task.getString("id");
                CheckBox checkbox = new CheckBox(a);
                checkbox.setText(task.getString("text"));
                checkbox.setTextColor(Ui.TEXT);
                checkbox.setChecked(task.getBoolean("done"));
                checkbox.setOnCheckedChangeListener(
                    (button, checked) ->
                        attempt(
                            a,
                            () -> {
                              entry(a, gameId, "tasks", entryId, null, checked, false);
                              changed.run();
                              refreshChildren(content);
                            }));
                entries.addView(checkbox);
                entries.addView(
                    Ui.rawButton(
                        a,
                        words(a, "Taak bewerken", "Edit task"),
                        () ->
                            noteEditor(
                                a,
                                gameId,
                                "tasks",
                                entryId,
                                () -> {
                                  changed.run();
                                  refreshChildren(content);
                                })));
                entries.addView(
                    Ui.rawButton(
                        a,
                        words(a, "Taak verwijderen", "Delete task"),
                        () ->
                            confirm(
                                a,
                                "Taak verwijderen?",
                                "Delete task?",
                                () -> {
                                  entry(a, gameId, "tasks", entryId, null, null, true);
                                  changed.run();
                                  refreshChildren(content);
                                })));
              }
            } catch (Exception e) {
              state.setText(e.getMessage());
            }
          };
      entries.setTag(refresh);
      AlertDialog dialog =
          new AlertDialog.Builder(a)
              .setTitle(selected.getString("title"))
              .setView(scroll(a, content))
              .setNegativeButton(words(a, "Sluiten", "Close"), null)
              .setNeutralButton(words(a, "Spel verwijderen", "Delete game"), null)
              .create();
      dialog.setOnShowListener(
          d ->
              dialog
                  .getButton(AlertDialog.BUTTON_NEUTRAL)
                  .setOnClickListener(
                      v ->
                          confirm(
                              a,
                              "Spel, dagboek en taken verwijderen?",
                              "Delete game, journal and tasks?",
                              () -> {
                                remove(a, gameId);
                                dialog.dismiss();
                                changed.run();
                              })));
      dialog.show();
      refresh.run();
      return dialog;
    } catch (Exception e) {
      Ui.toast(a, e.getMessage());
      return null;
    }
  }

  static JSONObject find(JSONArray list, String entryId) throws Exception {
    for (int i = 0; i < list.length(); i++)
      if (list.getJSONObject(i).getString("id").equals(entryId)) return list.getJSONObject(i);
    throw new IOException("Entry no longer exists");
  }

  static AlertDialog noteEditor(
      Activity a, String gameId, String list, String entryId, Runnable changed) {
    try {
      boolean journal = list.equals("journal");
      EditText note =
          input(
              a,
              journal ? "Eigen notitie" : "Taak",
              journal ? "Your note" : "Task",
              journal ? 2000 : 300,
              journal);
      if (entryId != null)
        note.setText(find(game(a, gameId).getJSONArray(list), entryId).getString("text"));
      LinearLayout content = Ui.col(a);
      content.addView(note);
      AlertDialog dialog =
          new AlertDialog.Builder(a)
              .setTitle(
                  words(a, journal ? "Dagboeknotitie" : "Taak", journal ? "Journal entry" : "Task"))
              .setView(scroll(a, content))
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
                          if (entryId != null)
                            entry(a, gameId, list, entryId, note.getText().toString(), null, false);
                          else if (journal) addJournal(a, gameId, note.getText().toString());
                          else addTask(a, gameId, note.getText().toString());
                          dialog.dismiss();
                          changed.run();
                        } catch (Exception e) {
                          note.setError(
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

  static AlertDialog confirm(Activity a, String nl, String en, Action action) {
    return new AlertDialog.Builder(a)
        .setTitle(words(a, nl, en))
        .setNegativeButton(words(a, "Annuleren", "Cancel"), null)
        .setPositiveButton(words(a, "Verwijderen", "Delete"), (dialog, which) -> attempt(a, action))
        .show();
  }
}
