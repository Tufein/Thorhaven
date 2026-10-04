package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity implements DisplayManager.DisplayListener {
  LinearLayout content, nav, body;
  String page = "Overzicht", query = "";
  List<Store.App> apps;
  TextView testText;
  ControllerView controller;
  long lastControllerMotion;
  android.app.AlertDialog profileDialog;
  int backupMode;
  String guideImportOwner = "";
  String guideImportPackage = "";
  String noteDraftPackage = "", noteDraft = "";
  String sketchExportToken = "";
  int sketchExportRequest = 1999;
  AlertDialog noteDialog;
  EditText noteEditor;
  static final String[] PAGES = {
    "Overzicht",
    "Apps",
    "Paren",
    "Notities",
    "Gidsen",
    "Games",
    "Schetsblok",
    "Opslag",
    "Setup",
    "Accu",
    "Controller",
    "Mapping",
    "Systeem",
    "RGB Studio",
    "Instellen"
  };
  boolean initialized;
  final Handler handler = new Handler(Looper.getMainLooper());

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    apps = Store.apps(this);
    if (b != null) {
      page = b.getString("page", "Overzicht");
      query = b.getString("query", "");
      guideImportPackage = b.getString("guideImportPackage", "");
      guideImportOwner = b.getString("guideImportOwner", "");
      noteDraftPackage = b.getString("noteDraftPackage", "");
      noteDraft = b.getString("noteDraft", "");
      sketchExportToken = b.getString("sketchExportToken", "");
      sketchExportRequest = b.getInt("sketchExportRequest", 1999);
    }
    getSystemService(DisplayManager.class).registerDisplayListener(this, handler);
    if ("notes".equals(getIntent().getStringExtra("page"))) page = "Notities";
    if ("system".equals(getIntent().getStringExtra("page"))) page = "Systeem";
    if (Arrays.asList(PAGES).contains(getIntent().getStringExtra("pageKey")))
      page = getIntent().getStringExtra("pageKey");
    render();
    initialized = true;
    if (!noteDraftPackage.isEmpty())
      handler.post(
          () -> {
            if (!isFinishing() && !isDestroyed()) notes(noteDraftPackage);
          });
    if (getIntent().getBooleanExtra("experiments", false))
      handler.post(
          () -> {
            if (!isFinishing() && !isDestroyed()) ExperimentTools.dialog(this);
          });
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    if (noteEditor != null) noteDraft = noteEditor.getText().toString();
    b.putString("noteDraftPackage", noteDraftPackage);
    b.putString("noteDraft", noteDraft);
    b.putString("sketchExportToken", sketchExportToken);
    b.putInt("sketchExportRequest", sketchExportRequest);
    super.onSaveInstanceState(b);
    b.putString("page", page);
    b.putString("query", query);
    b.putString("guideImportPackage", guideImportPackage);
    b.putString("guideImportOwner", guideImportOwner);
  }

  @Override
  public void onNewIntent(Intent i) {
    super.onNewIntent(i);
    setIntent(i);
    if (Arrays.asList(PAGES).contains(i.getStringExtra("pageKey")))
      page = i.getStringExtra("pageKey");
    if ("notes".equals(i.getStringExtra("page"))) page = "Notities";
    if ("system".equals(i.getStringExtra("page"))) page = "Systeem";
    render();
    if (i.getBooleanExtra("experiments", false))
      handler.post(
          () -> {
            if (!isFinishing() && !isDestroyed()) ExperimentTools.dialog(this);
          });
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (initialized) render();
    if (Store.prefs(this).getBoolean("keepAwake", false))
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  @Override
  protected void onDestroy() {
    getSystemService(DisplayManager.class).unregisterDisplayListener(this);
    handler.removeCallbacksAndMessages(null);
    Store.cancelPendingPair(this);
    SettingsBackup.cancel(this);
    StorageTools.lifecyclecancel(this);
    if (noteDialog != null) noteDialog.dismiss();
    if (profileDialog != null) profileDialog.dismiss();
    super.onDestroy();
  }

  public void onDisplayAdded(int id) {
    render();
  }

  public void onDisplayRemoved(int id) {
    render();
  }

  public void onDisplayChanged(int id) {}

  void go(String s) {
    page = s;
    query = "";
    render();
  }

  void render() {
    if (isFinishing() || isDestroyed()) return;
    testText = null;
    controller = null;
    lastControllerMotion = 0;
    LinearLayout root = Ui.col(this);
    root.setBackgroundColor(Ui.BG);
    root.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), 0);
    LinearLayout head = Ui.row(this);
    TextView brand = Ui.title(this, "THORHAVEN", 20);
    brand.setTextColor(Ui.ACCENT);
    head.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
    TextView status = Ui.text(this, Store.battery(this), 12, Ui.MUTED);
    head.addView(status);
    root.addView(head);
    root.addView(Ui.text(this, "Twee schermen. Jouw speelplek.", 12, Ui.MUTED));
    body = Ui.row(this);
    body.setGravity(Gravity.TOP);
    root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
    boolean small = getResources().getConfiguration().screenWidthDp < 520;
    nav = small ? Ui.row(this) : Ui.col(this);
    String[] pages = PAGES;
    for (String s : pages) {
      Button b = Ui.button(this, s, () -> go(s));
      if (page.equals(s)) {
        b.setTextColor(Ui.ACCENT);
        b.setBackground(Ui.bg(this, Color.rgb(31, 65, 61)));
      }
      if (small) {
        nav.addView(b, new LinearLayout.LayoutParams(Ui.dp(this, 88), -2));
        b.setTextSize(10);
        b.setPadding(0, 0, 0, 0);
      } else nav.addView(b);
    }
    if (!small) {
      ScrollView menu = new ScrollView(this);
      menu.addView(nav);
      body.addView(menu, new LinearLayout.LayoutParams(Ui.dp(this, 130), -1));
    }
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    content = Ui.col(this);
    content.setPadding(Ui.dp(this, small ? 0 : 16), Ui.dp(this, 4), 0, Ui.dp(this, 18));
    scroll.addView(content);
    body.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
    if (small) {
      HorizontalScrollView tabs = new HorizontalScrollView(this);
      tabs.addView(nav);
      root.addView(tabs);
    }
    switch (page) {
      case "Apps":
        appsPage();
        break;
      case "Paren":
        pairsPage();
        break;
      case "Notities":
        notesPage();
        break;
      case "Controller":
        controllerPage();
        break;
      case "Gidsen":
        GuidePages.page(this);
        break;
      case "Games":
        GameLibrary.page(this, content);
        break;
      case "Schetsblok":
        SketchPad.page(this, content);
        break;
      case "Opslag":
        StorageTools.page(this, content);
        break;
      case "Setup":
        SetupTools.page(this, content);
        LaunchShortcuts.page(this, content);
        break;
      case "Accu":
        PlayStats.page(this);
        break;
      case "Mapping":
        ControlPages.mapping(this);
        break;
      case "RGB Studio":
        RgbStudio.page(this);
        break;
      case "Systeem":
        ControlPages.system(this);
        break;
      case "Instellen":
        settingsPage();
        break;
      default:
        home();
    }
    setContentView(root);
  }

  void heading(String title, String sub) {
    content.addView(Ui.title(this, title, 28));
    content.addView(Ui.text(this, sub, 14, Ui.MUTED));
  }

  LinearLayout card(String t, String s) {
    return Ui.card(this, content, t, s);
  }

  void home() {
    heading("Klaar voor je volgende game", "Kies een scherm, start een app of open je snelpaneel.");
    LinearLayout screens =
        card(
            "Je schermen",
            Store.displays(this).size() + " beschikbare schermen · automatische detectie");
    for (android.view.Display d : Store.displays(this)) {
      android.util.DisplayMetrics m = Store.displayMetrics(this, d);
      String label =
          d.getDisplayId() == Store.screen(this, false)
              ? "Boven"
              : d.getDisplayId() == Store.screen(this, true) ? "Onder" : "Extra";
      screens.addView(
          Ui.text(
              this,
              label
                  + " · "
                  + m.widthPixels
                  + " × "
                  + m.heightPixels
                  + " · "
                  + Math.round(d.getRefreshRate())
                  + " Hz",
              15,
              Ui.TEXT));
    }
    if (Store.screen(this, true) < 0)
      screens.addView(
          Ui.text(
              this,
              "Het tweede scherm verschijnt zodra Android het beschikbaar maakt.",
              13,
              Ui.MUTED));
    LinearLayout actions =
        card(
            "Binnen handbereik",
            "Snelpaneel werkt boven andere apps met de optionele controller-service.");
    actions.addView(
        Ui.button(
            this,
            "Snelpaneel openen",
            () -> {
              if (ThorService.instance != null) ThorService.instance.showPanel();
              else panelFallback();
            }));
    actions.addView(Ui.button(this, "Schermen live wisselen (Shizuku)", () -> Bridge.swap(this)));
    actions.addView(Ui.button(this, "Apps & profielen", () -> go("Apps")));
    actions.addView(
        Ui.rawButton(
            this,
            Language.isEnglish(this) ? "Playing now & game journal" : "Nu spelen & gamedagboek",
            () -> go("Games")));
    actions.addView(
        Ui.rawButton(
            this,
            Language.isEnglish(this) ? "Setup & screen practice" : "Setup & schermtest",
            () -> go("Setup")));
    actions.addView(
        Ui.button(
            this,
            "Thorhaven op het andere scherm",
            () -> {
              int current = getDisplay().getDisplayId(),
                  other =
                      current == Store.screen(this, false)
                          ? Store.screen(this, true)
                          : Store.screen(this, false);
              Store.launch(this, getPackageName(), other);
            }));
    LinearLayout favorites = card("Favorieten", "Je favoriete apps openen op hun gekozen scherm.");
    int count = 0;
    for (Store.App a : apps)
      if (Store.prefs(this).getBoolean("favorite:" + a.pkg, false)) {
        favorites.addView(
            Ui.button(
                this,
                a.name,
                () ->
                    Store.launch(
                        this,
                        a.pkg,
                        Store.prefs(this).getInt("screen:" + a.pkg, Store.screen(this, false)))));
        count++;
      }
    if (count == 0)
      favorites.addView(
          Ui.text(this, "Markeer apps als favoriet via Apps → Profiel.", 14, Ui.MUTED));
    LinearLayout combos =
        card(
            "Controllercombinaties",
            Store.prefs(this).getBoolean("combos", false) && ThorService.instance != null
                ? "Ingeschakeld · zolang Android de knoppen doorgeeft"
                : "Optioneel · inschakelen bij Instellen");
    combos.addView(
        Ui.text(
            this,
            "Select + Start   Snelpaneel\n"
                + "Select + X   Live naar ander scherm (Shizuku)\n"
                + "Select + Y   Notities\n"
                + "Select + L1 / R1   Volume lager / hoger\n"
                + "Select + D-pad omhoog / omlaag   Helderheid",
            14,
            Ui.TEXT));
    if (getResources().getConfiguration().screenWidthDp >= 820) {
      List<View> cards = new ArrayList<>();
      while (content.getChildCount() > 2) {
        View view = content.getChildAt(2);
        content.removeViewAt(2);
        cards.add(view);
      }
      for (int i = 0; i < cards.size(); i += 2) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);
        for (int j = i; j < Math.min(i + 2, cards.size()); j++) {
          LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
          lp.setMargins(j == i ? 0 : Ui.dp(this, 12), Ui.dp(this, 6), 0, Ui.dp(this, 6));
          row.addView(cards.get(j), lp);
        }
        content.addView(row);
      }
    }
  }

  void appsPage() {
    heading("Apps & profielen", "Schermvoorkeur, favorieten, volume en notities per app.");
    EditText search = Ui.input(this, "Zoek een app…");
    search.setText(query);
    content.addView(search);
    LinearLayout list = Ui.col(this);
    content.addView(list);
    drawApps(list);
    search.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            query = s.toString();
            drawApps(list);
          }

          public void afterTextChanged(android.text.Editable e) {}
        });
  }

  void drawApps(LinearLayout list) {
    list.removeAllViews();
    int count = 0;
    for (Store.App a : apps) {
      if (!a.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
          && !a.pkg.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
      count++;
      LinearLayout outer = Ui.card(this, list, null, null);
      LinearLayout r = Ui.row(this);
      ImageView icon = new ImageView(this);
      icon.setImageDrawable(a.icon);
      r.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
      LinearLayout labels = Ui.col(this);
      labels.setPadding(Ui.dp(this, 12), 0, 0, 0);
      labels.addView(
          Ui.title(
              this,
              (Store.prefs(this).getBoolean("favorite:" + a.pkg, false) ? "★  " : "") + a.name,
              17));
      int preferred = Store.prefs(this).getInt("screen:" + a.pkg, Store.screen(this, false));
      labels.addView(
          Ui.text(
              this,
              "Voorkeur: "
                  + (preferred == Store.screen(this, true) && preferred >= 0
                      ? "onderste scherm"
                      : "bovenste scherm"),
              12,
              Ui.MUTED));
      r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
      outer.addView(r);
      LinearLayout buttons = Ui.row(this);
      buttons.addView(
          Ui.button(this, "Boven", () -> Store.launch(this, a.pkg, Store.screen(this, false))),
          new LinearLayout.LayoutParams(0, -2, 1));
      buttons.addView(
          Ui.button(this, "Onder", () -> Store.launch(this, a.pkg, Store.screen(this, true))),
          new LinearLayout.LayoutParams(0, -2, 1));
      buttons.addView(
          Ui.button(this, "Profiel", () -> profile(a)), new LinearLayout.LayoutParams(0, -2, 1));
      outer.addView(buttons);
    }
    if (count == 0) list.addView(Ui.text(this, "Geen apps gevonden.", 15, Ui.MUTED));
  }

  void profile(Store.App a) {
    LinearLayout l = Ui.col(this);
    l.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
    Switch favorite = new Switch(this);
    favorite.setText(Language.text(this, "Favoriet"));
    favorite.setTextColor(Ui.TEXT);
    favorite.setChecked(Store.prefs(this).getBoolean("favorite:" + a.pkg, false));
    l.addView(favorite);
    l.addView(Ui.text(this, "Open standaard op", 14, Ui.MUTED));
    Spinner spinner = new Spinner(this);
    List<android.view.Display> displays = Store.displays(this);
    String[] names = new String[displays.size()];
    int selected = 0;
    for (int i = 0; i < names.length; i++) {
      int id = displays.get(i).getDisplayId();
      names[i] =
          (id == Store.screen(this, false)
                  ? "Boven"
                  : id == Store.screen(this, true) ? "Onder" : "Extra")
              + " · scherm "
              + id;
      if (id == Store.prefs(this).getInt("screen:" + a.pkg, Store.screen(this, false)))
        selected = i;
    }
    spinner.setAdapter(
        new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_dropdown_item, Language.labels(this, names)));
    spinner.setSelection(selected);
    l.addView(spinner);
    final int[] values = {
      Store.prefs(this).getInt("volume:" + a.pkg, -1),
      Store.prefs(this).getInt("brightness:" + a.pkg, -1)
    };
    Switch vol = new Switch(this);
    vol.setText(Language.text(this, "Volume toepassen"));
    vol.setTextColor(Ui.TEXT);
    vol.setChecked(values[0] >= 0);
    l.addView(vol);
    Ui.seek(this, l, "Mediavolume (%)", 100, values[0] >= 0 ? values[0] : 50, v -> values[0] = v);
    Switch bright = new Switch(this);
    bright.setText(Language.text(this, "Helderheid toepassen (systeem)"));
    bright.setTextColor(Ui.TEXT);
    bright.setChecked(values[1] >= 0);
    l.addView(bright);
    Ui.seek(this, l, "Helderheid (%)", 100, values[1] >= 0 ? values[1] : 50, v -> values[1] = v);
    l.addView(
        Ui.text(
            this,
            "Geldt bij starten via Thorhaven. Met automatische profielen ook als de app via een"
                + " andere launcher opent. Helderheid vereist instellingstoegang. Deze waarden"
                + " wijzigen het systeem en worden niet automatisch teruggezet.",
            12,
            Ui.MUTED));
    EditText guide = Ui.input(this, "Optionele gidslink (https://…)");
    guide.setText(Store.prefs(this).getString("guide:" + a.pkg, ""));
    l.addView(guide);
    ExtraFeatures.profile(this, l, a.pkg);
    ScrollView sc = new ScrollView(this);
    sc.addView(l);
    profileDialog =
        new Ui.Dialog(this)
            .setTitle(a.name)
            .setView(sc)
            .setPositiveButton(
                "Opslaan",
                (d, w) -> {
                  Store.prefs(this)
                      .edit()
                      .putBoolean("favorite:" + a.pkg, favorite.isChecked())
                      .putInt(
                          "screen:" + a.pkg,
                          displays.get(spinner.getSelectedItemPosition()).getDisplayId())
                      .putInt("volume:" + a.pkg, vol.isChecked() ? Math.max(0, values[0]) : -1)
                      .putInt(
                          "brightness:" + a.pkg, bright.isChecked() ? Math.max(0, values[1]) : -1)
                      .putString("guide:" + a.pkg, guide.getText().toString())
                      .apply();
                  render();
                })
            .setNeutralButton("Notities", (d, w) -> notes(a.pkg))
            .setNegativeButton("Annuleren", null)
            .create();
    profileDialog.show();
  }

  void pairsPage() {
    heading("App-paren", "Je game boven, een tweede app onder. Eén druk om beide te openen.");
    content.addView(Ui.button(this, "Nieuw app-paar", this::newPair));
    content.addView(
        Ui.text(
            this,
            "Tip: open het snelpaneel boven je twee apps en kies Bewaar dit app-paar.",
            14,
            Ui.MUTED));
    JSONArray pairs = Store.pairs(this);
    if (pairs.length() == 0)
      card(
          "Nog geen app-paren",
          "Combineer bijvoorbeeld een emulator met een browser of muziekapp.");
    for (int n = 0; n < pairs.length(); n++) {
      final int index = n;
      try {
        JSONObject p = pairs.getJSONObject(n);
        LinearLayout c =
            card(
                p.getString("name"),
                "Boven: "
                    + Store.name(this, p.getString("top"))
                    + "\nOnder: "
                    + Store.name(this, p.getString("bottom")));
        c.addView(Ui.button(this, "Samen openen", () -> Store.openPair(this, p)));
        c.addView(
            Ui.button(
                this,
                "Verwijderen",
                () -> {
                  JSONArray arr = Store.pairs(this);
                  arr.remove(index);
                  Store.prefs(this).edit().putString("pairs", arr.toString()).apply();
                  render();
                }));
      } catch (Exception e) {
        Ui.toast(this, "App-paar kan niet worden gelezen.");
      }
    }
  }

  void pick(String title, java.util.function.Consumer<Store.App> action) {
    String[] labels = new String[apps.size()];
    for (int i = 0; i < labels.length; i++) labels[i] = apps.get(i).name;
    new Ui.Dialog(this)
        .setTitle(title)
        .setItems(labels, (d, w) -> action.accept(apps.get(w)))
        .setNegativeButton("Annuleren", null)
        .show();
  }

  void newPair() {
    pick(
        "App voor het bovenste scherm",
        top ->
            pick(
                "App voor het onderste scherm",
                bottom -> {
                  if (top.pkg.equals(bottom.pkg)) {
                    Ui.toast(this, "Kies twee verschillende apps.");
                    return;
                  }
                  EditText e = Ui.input(this, "Naam van je app-paar");
                  e.setText(top.name + " + " + bottom.name);
                  new Ui.Dialog(this)
                      .setTitle("App-paar opslaan")
                      .setView(e)
                      .setPositiveButton(
                          "Opslaan",
                          (d, w) -> {
                            try {
                              Store.pair(
                                  this,
                                  new JSONObject()
                                      .put("name", e.getText().toString())
                                      .put("top", top.pkg)
                                      .put("bottom", bottom.pkg));
                              render();
                            } catch (Exception x) {
                              Ui.toast(this, "Opslaan mislukt.");
                            }
                          })
                      .setNegativeButton("Annuleren", null)
                      .show();
                }));
  }

  void importGuide(String pkg) {
    guideImportPackage = pkg;
    guideImportOwner = GuidePages.selected;
    startActivityForResult(
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*")
            .putExtra(
                Intent.EXTRA_MIME_TYPES,
                new String[] {
                  "application/pdf", "text/plain", "text/markdown", "image/png", "image/jpeg"
                })
            .addCategory(Intent.CATEGORY_OPENABLE),
        43);
  }

  void notesPage() {
    heading("Gidsen & notities", "Je voortgang, tips en gidslinks blijven lokaal op dit apparaat.");
    content.addView(
        Ui.button(this, "Kies een app", () -> pick("Notities voor…", a -> notes(a.pkg))));
    content.addView(Ui.button(this, "Mijn offline gidsen", () -> go("Gidsen")));
    String last = Store.prefs(this).getString("last", "");
    if (!last.isEmpty()) {
      LinearLayout c = card("Laatst geopend", Store.name(this, last));
      c.addView(Ui.button(this, "Notities openen", () -> notes(last)));
      c.addView(Ui.button(this, "Gids op onderste scherm", () -> Store.guide(this, last)));
    }
    for (Store.App a : apps)
      if (!Store.prefs(this).getString("notes:" + a.pkg, "").isEmpty()) {
        LinearLayout c = card(a.name, null);
        c.addView(
            Ui.rawText(this, Store.prefs(this).getString("notes:" + a.pkg, ""), 13, Ui.MUTED));
        c.addView(Ui.button(this, "Bewerken", () -> notes(a.pkg)));
      }
  }

  void notes(String pkg) {
    if (isFinishing() || isDestroyed()) return;
    if (noteDialog != null) noteDialog.dismiss();
    EditText e = Ui.input(this, "Schrijf je notities…");
    e.setSingleLine(false);
    e.setMinLines(4);
    e.setMaxLines(10);
    e.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(50000)});
    e.setGravity(Gravity.TOP);
    e.setText(
        pkg.equals(noteDraftPackage) ? noteDraft : Store.prefs(this).getString("notes:" + pkg, ""));
    noteDraftPackage = pkg;
    noteDraft = e.getText().toString();
    noteEditor = e;
    noteDialog =
        new AlertDialog.Builder(this)
            .setTitle(
                Store.name(this, pkg) + (Language.isEnglish(this) ? " · notes" : " · notities"))
            .setView(e)
            .setPositiveButton(
                Language.text(this, "Opslaan"),
                (d, w) -> {
                  Store.prefs(this)
                      .edit()
                      .putString("notes:" + pkg, e.getText().toString())
                      .apply();
                  render();
                })
            .setNeutralButton(
                Language.text(this, "Gids openen"),
                (d, w) -> {
                  Store.guide(this, pkg);
                })
            .setNegativeButton(Language.text(this, "Sluiten"), null)
            .create();
    AlertDialog ownedDialog = noteDialog;
    noteDialog.setOnDismissListener(
        d -> {
          if (noteDialog != ownedDialog) return;
          if (isChangingConfigurations()) noteDraft = e.getText().toString();
          else {
            noteDraftPackage = "";
            noteDraft = "";
          }
          noteEditor = null;
          noteDialog = null;
        });
    try {
      noteDialog.show();
    } catch (WindowManager.BadTokenException expiredWindow) {
      // Android may retire a window token while a deferred action still owns this Activity.
      // Keep the draft in memory, but never attach a dialog to a retired window.
      noteDialog = null;
      noteEditor = null;
    }
  }

  void controllerPage() {
    heading(
        "Controller-test",
        "Beweeg de sticks en druk op een knop. De test verandert je mapping niet.");
    TextView drift =
        Ui.text(this, "Beweeg beide sticks, laat los en meet daarna hun ruststand.", 14, Ui.MUTED);
    content.addView(
        Ui.button(this, "Meet stickdrift · 5 seconden", () -> DriftCheck.start(this, drift)));
    content.addView(drift);
    controller = new ControllerView(this);
    content.addView(controller, new LinearLayout.LayoutParams(-1, Ui.dp(this, 210)));
    testText = Ui.text(this, "Wacht op controller-invoer…", 15, Ui.TEXT);
    content.addView(testText);
    LinearLayout c =
        card(
            "Bediening van Thorhaven",
            "D-pad / stick: navigeren · A: selecteren · B: terug\n"
                + "L1 / R1: vorige / volgende pagina\n"
                + "Android-knopcodes kunnen afwijken van de gedrukte A/B/X/Y-labels.");
    c.addView(
        Ui.button(
            this,
            "Controller-toetsenbord kiezen",
            () -> getSystemService(InputMethodManager.class).showInputMethodPicker()));
    c.addView(
        Ui.text(
            this,
            "Het toetsenbord werkt in normale tekstvelden. Activeer het eerst bij Instellen."
                + " Globale knopcombinaties staan tijdens deze test uit.",
            13,
            Ui.MUTED));
  }

  void settingSwitch(LinearLayout l, String text, String key, boolean fallback) {
    Switch s = new Switch(this);
    s.setText(Language.text(this, text));
    s.setTextColor(Ui.TEXT);
    s.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
    s.setChecked(Store.prefs(this).getBoolean(key, fallback));
    s.setOnCheckedChangeListener(
        (b, checked) -> {
          Store.prefs(this).edit().putBoolean(key, checked).apply();
          if (key.equals("keepAwake")) {
            if (checked) getWindow().addFlags(128);
            else getWindow().clearFlags(128);
          }
        });
    l.addView(s);
  }

  void settingsPage() {
    heading("Instellen", "Kies zelf welke toegang en functies je inschakelt.");
    Language.settings(this, content);
    KeyboardSettings.page(this, content);
    Shortcuts.page(this, content);
    CompleteBackup.page(this, content);
    ExtraFeatures.settings(this);
    ExperimentTools.page(this);
    settingSwitch(content, "Notities naast gids", "guideNotes", false);
    LinearLayout panelSettings =
        card(
            "Snelpaneel tijdens gamen",
            "Met Alleen aanraken blijft controllerfocus bij je game. Sluiten doe je met aanraken of"
                + " Select + Start.");
    settingSwitch(
        panelSettings, "Alleen aanraken · controller blijft bij game", "panelTouchOnly", false);
    settingSwitch(panelSettings, "Recente apps in snelpaneel", "panelRecents", true);
    settingSwitch(panelSettings, "Favorieten in snelpaneel", "panelFavorites", true);
    settingSwitch(panelSettings, "App-paren in snelpaneel", "panelPairs", true);
    settingSwitch(panelSettings, "Notities in snelpaneel", "panelNotes", true);
    panelSettings.addView(
        Ui.button(
            this,
            "Mijn Select-combinaties bekijken",
            () ->
                new Ui.Dialog(this)
                    .setTitle("Select-combinaties")
                    .setMessage(
                        "Select + Start: snelpaneel\n"
                            + "Select + X: live verplaatsen\n"
                            + "Select + Y: notities\n"
                            + "Select + A: offline gids\n"
                            + "Select + B: zwarte onderste schermmodus\n"
                            + "Select + L1/R1: volume\n"
                            + "Select + D-pad op/neer: helderheid\n\n"
                            + "Bij actieve root-remapping: fysieke Select + Start drie seconden"
                            + " vasthouden = noodstop.")
                    .setPositiveButton("Sluiten", null)
                    .show()));
    LinearLayout c =
        card(
            "Controller & snelpaneel",
            ThorService.instance != null ? "Service actief" : "Service nog niet ingeschakeld");
    c.addView(
        Ui.text(
            this,
            "De optionele toegankelijkheidsservice ontvangt app-namen en controllerknoppen voor"
                + " combinaties, profielen, het snelpaneel en lokale accu-/tijdmetingen. Hij leest"
                + " geen schermtekst, bewaart geen toetsgeschiedenis en heeft geen"
                + " internettoegang.",
            13,
            Ui.MUTED));
    c.addView(
        Ui.button(
            this,
            "Service instellen",
            () ->
                new Ui.Dialog(this)
                    .setTitle("Controller-service inschakelen")
                    .setMessage(
                        "Android vraagt brede toegankelijkheidstoegang. Thorhaven gebruikt alleen"
                            + " app-herkenning, controllerknoppen en een paneel boven apps. Schakel"
                            + " in de volgende pagina uitsluitend Thorhaven in. Bij 'Beperkte"
                            + " instelling': App-info → menu → Beperkte instellingen toestaan.")
                    .setPositiveButton(
                        "Naar instellingen",
                        (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .setNegativeButton("Annuleren", null)
                    .show()));
    settingSwitch(c, "Select-combinaties inschakelen", "combos", false);
    settingSwitch(c, "Profielen automatisch toepassen", "autoProfiles", false);
    c.addView(
        Ui.text(
            this,
            "Select wordt bij combinaties ingeschakeld als modifier onderschept. Uitschakelen geeft"
                + " Select terug aan de game. Home en AYN worden niet overgenomen.",
            12,
            Ui.MUTED));
    c = card("Schermen", "Controleer welke Android-scherm-ID bij boven en onder hoort.");
    screenPick(c, "Boven", "top", Store.screen(this, false));
    screenPick(c, "Onder", "bottom", Store.screen(this, true));
    settingSwitch(c, "Thorhaven-scherm wakker houden", "keepAwake", false);
    c = card("Live schermwissels · Shizuku", Bridge.status());
    c.addView(
        Ui.text(
            this,
            "Optionele systeemtoegang om actieve apps te verplaatsen zonder ze opnieuw te starten."
                + " Installeer en start Shizuku, geef Thorhaven toestemming en koppel hieronder."
                + " Een game kan bij verplaatsen alsnog opnieuw laden of Android kan de actie"
                + " weigeren. Geen Wayfinder-systeemcode wordt gebruikt.",
            13,
            Ui.MUTED));
    c.addView(Ui.button(this, "Shizuku koppelen", Bridge::request));
    c.addView(
        Ui.button(
            this,
            "Shizuku openen",
            () -> {
              Intent i =
                  getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
              if (i != null) startActivity(i);
              else Ui.toast(this, "Installeer Shizuku via shizuku.rikka.app/download.");
            }));
    c =
        card(
            "Helderheid & toetsenbord",
            Settings.System.canWrite(this)
                ? "Helderheidstoegang actief"
                : "Helderheidstoegang optioneel");
    c.addView(
        Ui.button(
            this,
            "Helderheidstoegang instellen",
            () ->
                startActivity(
                    new Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + getPackageName())))));
    c.addView(
        Ui.button(
            this,
            "Controller-toetsenbord activeren",
            () -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))));
    c.addView(
        Ui.button(
            this,
            "Toetsenbord kiezen",
            () -> getSystemService(InputMethodManager.class).showInputMethodPicker()));
    c = card("Back-up", "Exporteer je profielen, favorieten, app-paren, notities en schermkeuze.");
    c.addView(
        Ui.button(
            this,
            "Back-up exporteren",
            () -> {
              backupMode = 1;
              Intent i =
                  new Intent(Intent.ACTION_CREATE_DOCUMENT)
                      .setType("application/json")
                      .addCategory(Intent.CATEGORY_OPENABLE)
                      .putExtra(Intent.EXTRA_TITLE, "thorhaven-backup.json");
              startActivityForResult(i, 41);
            }));
    c.addView(
        Ui.button(
            this,
            "Back-up importeren",
            () -> {
              backupMode = 2;
              startActivityForResult(
                  new Intent(Intent.ACTION_OPEN_DOCUMENT)
                      .setType("application/json")
                      .addCategory(Intent.CATEGORY_OPENABLE),
                  42);
            }));
    c =
        card(
            "Over Thorhaven",
            "Thorhaven "
                + BuildConfig.VERSION_NAME
                + " · eigen implementatie, geïnspireerd op Wayfinder.");
    c.addView(
        Ui.rawText(
            this,
            Language.isEnglish(this)
                ? "A local companion for both screens: apps and pairs, per-game journals and tasks,"
                    + " offline guides, sketches, folder inventories and M3U playlists, launcher"
                    + " shortcuts, controller tools, play statistics and backups.\n\n"
                    + "Optional RGB, privileged input, live app moves and Screen Lab need their"
                    + " own setup. Firmware behavior and physical hardware still need testing on"
                    + " a Thor. This is a release candidate for feedback.\n\n"
                    + "No ads, trackers or Internet permission. Online guides open in your"
                    + " browser. Android or another app may refuse a display launch."
                : "Een lokale hulp voor beide schermen: apps en paren, gamedagboeken en taken,"
                    + " offline gidsen, schetsen, mapoverzichten en M3U-afspeellijsten,"
                    + " launcher-snelkoppelingen, controller-tools, speelstatistieken en"
                    + " back-ups.\n\n"
                    + "Optionele RGB, root-invoer, live appwissels en Screen Lab vragen eigen"
                    + " instellingen. Firmware en fysieke hardware moeten nog op een Thor worden"
                    + " getest. Dit is een releasekandidaat voor feedback.\n\n"
                    + "Geen advertenties, trackers of internettoegang. Online gidsen openen in je"
                    + " browser. Android of een andere app kan een schermstart weigeren.",
            13,
            Ui.MUTED));
    c.addView(Ui.button(this, "Licenties bekijken", this::licenses));
  }

  void licenses() {
    try (java.io.InputStream in = getAssets().open("licenses.txt")) {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int n;
      while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
      TextView text = Ui.text(this, out.toString("UTF-8"), 12, Ui.TEXT);
      text.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
      ScrollView scroll = new ScrollView(this);
      scroll.addView(text);
      new Ui.Dialog(this)
          .setTitle("Licenties")
          .setView(scroll)
          .setPositiveButton("Sluiten", null)
          .show();
    } catch (Exception e) {
      Ui.toast(this, "Licenties kunnen niet worden geopend.");
    }
  }

  void screenPick(LinearLayout l, String label, String key, int selected) {
    l.addView(Ui.text(this, label + "ste scherm", 14, Ui.MUTED));
    List<android.view.Display> ds = Store.displays(this);
    List<String> labels = new ArrayList<>();
    labels.add("Niet toegewezen");
    int index = 0;
    for (int i = 0; i < ds.size(); i++) {
      android.util.DisplayMetrics m = Store.displayMetrics(this, ds.get(i));
      labels.add("ID " + ds.get(i).getDisplayId() + " · " + m.widthPixels + " × " + m.heightPixels);
      if (ds.get(i).getDisplayId() == selected) index = i + 1;
    }
    Spinner s = new Spinner(this);
    s.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            Language.labels(this, labels.toArray(new String[0]))));
    s.setSelection(index);
    l.addView(s);
    s.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(android.widget.AdapterView<?> a) {}

          public void onItemSelected(android.widget.AdapterView<?> a, View v, int pos, long id) {
            int value = pos == 0 ? -1 : ds.get(pos - 1).getDisplayId();
            Store.prefs(MainActivity.this).edit().putInt(key, value).apply();
          }
        });
  }

  void panelFallback() {
    LinearLayout l = QuickPanel.build(this, null);
    ScrollView s = new ScrollView(this);
    s.addView(l);
    new Ui.Dialog(this).setTitle("Snelpaneel").setView(s).setPositiveButton("Sluiten", null).show();
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent e) {
    int k = e.getKeyCode();
    if (page.equals("Controller")
        && e.getSource() != android.view.InputDevice.SOURCE_KEYBOARD
        && controller != null) {
      controller.key = k;
      controller.pressed = e.getAction() == KeyEvent.ACTION_DOWN;
      controller.invalidate();
      testText.setText(
          KeyEvent.keyCodeToString(k)
              + " · "
              + (e.getAction() == 0 ? "ingedrukt" : "losgelaten")
              + " · apparaat "
              + e.getDeviceId());
      if (k != KeyEvent.KEYCODE_BACK && k != KeyEvent.KEYCODE_BUTTON_B) return true;
    }
    if (e.getAction() == KeyEvent.ACTION_DOWN) {
      if (k == KeyEvent.KEYCODE_BUTTON_L1 || k == KeyEvent.KEYCODE_BUTTON_R1) {
        String[] p = PAGES;
        int i = Arrays.asList(p).indexOf(page);
        go(p[(i + (k == KeyEvent.KEYCODE_BUTTON_R1 ? 1 : p.length - 1)) % p.length]);
        return true;
      }
      if (k == KeyEvent.KEYCODE_BUTTON_A) {
        View v = getCurrentFocus();
        if (v != null) {
          v.performClick();
          return true;
        }
      }
      if (k == KeyEvent.KEYCODE_BUTTON_B) {
        if (!page.equals("Overzicht")) go("Overzicht");
        else super.onBackPressed();
        return true;
      }
    }
    return super.dispatchKeyEvent(e);
  }

  @Override
  public boolean onGenericMotionEvent(MotionEvent e) {
    if (page.equals("Controller")
        && controller != null
        && (e.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
      lastControllerMotion = android.os.SystemClock.elapsedRealtime();
      controller.lx = e.getAxisValue(MotionEvent.AXIS_X);
      controller.ly = e.getAxisValue(MotionEvent.AXIS_Y);
      InputDevice input = e.getDevice();
      boolean z = input == null || input.getMotionRange(MotionEvent.AXIS_Z) != null;
      controller.rx = e.getAxisValue(z ? MotionEvent.AXIS_Z : MotionEvent.AXIS_RX);
      controller.ry = e.getAxisValue(z ? MotionEvent.AXIS_RZ : MotionEvent.AXIS_RY);
      controller.invalidate();
      testText.setText(
          String.format(
              Locale.US,
              "Links %.2f / %.2f · Rechts %.2f / %.2f\nL2 %.2f · R2 %.2f",
              controller.lx,
              controller.ly,
              controller.rx,
              controller.ry,
              e.getAxisValue(MotionEvent.AXIS_LTRIGGER),
              e.getAxisValue(MotionEvent.AXIS_RTRIGGER)));
      return true;
    }
    return super.onGenericMotionEvent(e);
  }

  @Override
  protected void onActivityResult(int req, int result, Intent data) {
    super.onActivityResult(req, result, data);
    if (StorageTools.handlesResult(this, req, result, data)) return;
    if (SketchPad.handlesRequest(req)) {
      SketchPad.onActivityResult(this, req, result, data);
      return;
    }
    if (req == 47) {
      GuideTools.onExportResult(this, result, data);
      return;
    }
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    if (!"content".equals(uri.getScheme())) {
      Ui.toast(this, "Kies een document met de Android-bestandskiezer.");
      return;
    }
    if (req == RgbPresetBundle.EXPORT || req == RgbPresetBundle.IMPORT) {
      RgbPresetBundle.result(this, req, uri);
      return;
    }
    if (req == 48 || req == 49) {
      ExperimentTools.export(this, uri, req);
      return;
    }
    if (req == 46) {
      AutoBackup.choose(this, uri, data.getFlags());
      return;
    }
    if (req == 44) {
      CompleteBackup.exportUri(this, uri);
    } else if (req == 45) {
      new Ui.Dialog(this)
          .setTitle("Volledige back-up importeren?")
          .setMessage(
              "De back-up vervangt overeenkomstige instellingen, gidsen en lokale metingen. Andere"
                  + " gidsen blijven staan. Hardwareherstel en lopende slaapmetingen worden niet"
                  + " overgenomen.")
          .setPositiveButton("Importeren", (d, w) -> CompleteBackup.importUri(this, uri))
          .setNegativeButton("Annuleren", null)
          .show();
    } else if (req == 43) {
      if (!guideImportPackage.isEmpty()) OfflineGuides.importUri(this, guideImportPackage, uri);
    } else if (req == 41 || req == 42) {
      SettingsBackup.result(this, req, uri);
    }
  }

  static final class ControllerView extends View {
    float lx, ly, rx, ry;
    int key;
    boolean pressed;
    Paint paint = new Paint(3);

    ControllerView(Context c) {
      super(c);
      setBackground(Ui.bg(c, Ui.CARD));
    }

    protected void onDraw(Canvas c) {
      super.onDraw(c);
      float radius = Math.min(getWidth() / 6f, getHeight() / 3f);
      for (int n = 0; n < 2; n++) {
        float x = getWidth() * (n == 0 ? .28f : .72f), y = getHeight() * .48f;
        paint.setColor(Color.rgb(41, 57, 75));
        c.drawCircle(x, y, radius, paint);
        paint.setColor(Ui.MUTED);
        paint.setStrokeWidth(1);
        c.drawLine(x - radius, y, x + radius, y, paint);
        c.drawLine(x, y - radius, x, y + radius, paint);
        paint.setColor(Ui.ACCENT);
        c.drawCircle(
            x + (n == 0 ? lx : rx) * radius,
            y + (n == 0 ? ly : ry) * radius,
            Ui.dp(getContext(), 10),
            paint);
        paint.setColor(Ui.TEXT);
        paint.setTextSize(Ui.dp(getContext(), 13));
        c.drawText(
            Language.text(getContext(), n == 0 ? "LINKER STICK" : "RECHTER STICK"),
            x - radius,
            y + radius + Ui.dp(getContext(), 24),
            paint);
      }
      if (pressed) {
        paint.setColor(Ui.ACCENT);
        paint.setTextSize(Ui.dp(getContext(), 13));
        c.drawText(
            KeyEvent.keyCodeToString(key), Ui.dp(getContext(), 16), Ui.dp(getContext(), 24), paint);
      }
    }
  }
}
