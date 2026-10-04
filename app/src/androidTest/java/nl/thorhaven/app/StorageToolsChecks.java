package nl.thorhaven.app;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Real Android SAF grants/provider bytes, plus deterministic playlist and cancellation checks. */
final class StorageToolsChecks {
  static final String AUTHORITY = "nl.thorhaven.storage.test";
  static final Uri CONTROL = Uri.parse("content://nl.thorhaven.storage.control.test");
  static final Uri TREE = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root");
  static final int READ = Intent.FLAG_GRANT_READ_URI_PERMISSION,
      WRITE = Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

  interface Action {
    void run() throws Exception;
  }

  static boolean rejects(Action action) {
    try {
      action.run();
      return false;
    } catch (Exception expected) {
      return true;
    }
  }

  static Bundle fixture(SmokeInstrumentation t, Context c, String method, String arg, int flags) {
    UiAutomation automation =
        t.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    automation.adoptShellPermissionIdentity("android.permission.DUMP");
    try {
      Bundle extras = new Bundle();
      extras.putInt("flags", flags);
      return c.getContentResolver().call(CONTROL, method, arg, extras);
    } finally {
      automation.dropShellPermissionIdentity();
    }
  }

  static void reset(SmokeInstrumentation t, Context c, String mode) {
    fixture(t, c, "fixtureReset", mode, 0);
  }

  static void grant(SmokeInstrumentation t, Context c, int flags) {
    fixture(t, c, "fixtureGrant", "root", flags);
  }

  static Bundle stats(SmokeInstrumentation t, Context c) {
    return fixture(t, c, "fixtureStats", null, 0);
  }

  static int persisted(Context c, Uri tree) {
    int flags = 0;
    for (UriPermission grant : c.getContentResolver().getPersistedUriPermissions()) {
      if (!grant.getUri().equals(tree)) continue;
      if (grant.isReadPermission()) flags |= READ;
      if (grant.isWritePermission()) flags |= WRITE;
    }
    return flags;
  }

  static StorageTools.Entry entry(StorageTools.Report r, String id) throws Exception {
    for (StorageTools.Entry e : r.files) if (e.id.equals(id)) return e;
    throw new Exception("Missing fixture document " + id);
  }

  static StorageTools.Entry artificial(String name, String id, String parent) {
    return new StorageTools.Entry(
        DocumentsContract.buildDocumentUriUsingTree(TREE, id),
        DocumentsContract.buildDocumentUriUsingTree(TREE, parent),
        id,
        parent,
        name,
        name,
        1);
  }

  static String bytes(Context c, Uri uri) throws Exception {
    try (InputStream in = c.getContentResolver().openInputStream(uri)) {
      return PlaylistTools.read(in, new StorageTools.Token());
    }
  }

  static boolean onMain(SmokeInstrumentation t, BooleanSupplier test) {
    final boolean[] value = {false};
    t.runOnMainSync(() -> value[0] = test.getAsBoolean());
    return value[0];
  }

  static void await(SmokeInstrumentation t, BooleanSupplier test, int millis, String failure)
      throws Exception {
    long end = SystemClock.elapsedRealtime() + millis;
    while (SystemClock.elapsedRealtime() < end) {
      if (onMain(t, test)) return;
      Thread.sleep(30);
    }
    throw new Exception(failure);
  }

  static boolean text(View root, String needle) {
    if (root instanceof TextView && ((TextView) root).getText().toString().contains(needle))
      return true;
    if (root instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) root;
      for (int n = 0; n < g.getChildCount(); n++) if (text(g.getChildAt(n), needle)) return true;
    }
    return false;
  }

  static Button button(View root, String label) {
    if (root instanceof Button && ((Button) root).getText().toString().equals(label))
      return (Button) root;
    if (root instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) root;
      for (int n = 0; n < g.getChildCount(); n++) {
        Button b = button(g.getChildAt(n), label);
        if (b != null) return b;
      }
    }
    return null;
  }

  static void click(SmokeInstrumentation t, String label) throws Exception {
    UiAutomation automation =
        t.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    long end = SystemClock.elapsedRealtime() + 5000;
    while (SystemClock.elapsedRealtime() < end) {
      AccessibilityNodeInfo root = automation.getRootInActiveWindow();
      if (root != null)
        try {
          for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(label)) {
            if (!label.equals(String.valueOf(node.getText()))) continue;
            AccessibilityNodeInfo target = node;
            while (target != null && !target.isClickable()) target = target.getParent();
            if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
          }
        } finally {
          root.recycle();
        }
      Thread.sleep(50);
    }
    throw new Exception("Cannot click real storage dialog choice: " + label);
  }

  static void run(SmokeInstrumentation t, Context c, MainActivity previous) throws Exception {
    String language = Store.prefs(c).getString("language", "nl");
    Map<String, ?> localBefore = new HashMap<>(StorageTools.local(c).getAll());
    MainActivity a = null, bottom = null;
    try {
      t.check(
          rejects(() -> c.getContentResolver().call(CONTROL, "fixtureStats", null, Bundle.EMPTY)),
          "Ordinary target app cannot access privileged storage fixture controls");
      fixture(t, c, "fixtureRevoke", "root", 0);
      reset(t, c, "normal");
      StorageTools.Report refused = StorageTools.scan(c, TREE, new StorageTools.Token());
      t.check(
          refused.files.isEmpty() && !refused.errors.isEmpty() && refused.knownBytes == 0,
          "Missing real SAF read grant yields a partial report, not invented device storage");
      grant(t, c, READ);
      t.check(
          c.checkUriPermission(TREE, android.os.Process.myPid(), android.os.Process.myUid(), READ)
                  == PackageManager.PERMISSION_GRANTED
              && c.checkUriPermission(
                      TREE, android.os.Process.myPid(), android.os.Process.myUid(), WRITE)
                  != PackageManager.PERMISSION_GRANTED,
          "Android holds a readable folder grant without silently gaining write access");
      StorageTools.Report report = StorageTools.scan(c, TREE, new StorageTools.Token());
      t.check(
          report.files.size() == 5
              && report.nodes == 6
              && report.folders == 2
              && report.knownBytes == 414
              && report.unknownSizes == 1
              && report.errors.isEmpty()
              && !report.limited
              && report.at > 0,
          "Real DocumentsProvider scan counts only selected files and preserves unknown sizes");
      t.check(
          report.extensions.get("chd").files == 2
              && report.extensions.get("chd").knownBytes == 100
              && report.extensions.get("chd").unknown == 1
              && report.largest.get(0).name.equals("Other.iso")
              && report.largest.stream().noneMatch(e -> e.bytes < 0),
          "Extension totals and largest-file order never turn unknown sizes into zero");
      StorageTools.Entry d1 = entry(report, "disc1"),
          d2 = entry(report, "disc2"),
          other = entry(report, "other"),
          old = entry(report, "playlist");
      List<StorageTools.Entry> order = Arrays.asList(d2, d1);
      t.check(
          PlaylistTools.encode(order).equals(d2.name + "\n" + d1.name + "\n")
              && PlaylistTools.names(order).get(0).equals(d2.name),
          "M3U emits exact Unicode relative names in the chosen disc order");
      t.check(
          PlaylistTools.disc("Game.CUE")
              && PlaylistTools.disc("Game.CHD")
              && PlaylistTools.disc("Game.ISO")
              && PlaylistTools.disc("Game.PBP")
              && PlaylistTools.disc("Game.GDI")
              && !PlaylistTools.disc("Track.bin"),
          "Disc choices allow documented playlist formats and exclude BIN tracks");
      t.check(
          rejects(() -> PlaylistTools.names(Arrays.asList(d1, d1)))
              && rejects(() -> PlaylistTools.names(Arrays.asList(d1, other)))
              && rejects(
                  () ->
                      PlaylistTools.names(
                          Arrays.asList(d1, artificial(d1.name, "different", "root"))))
              && rejects(() -> PlaylistTools.names(Collections.emptyList())),
          "Duplicate identities/names, mixed folders and empty disc selections are rejected");
      boolean badNames = true;
      for (String name :
          Arrays.asList(
              "../Disc.chd",
              "folder/Disc.chd",
              "folder\\Disc.chd",
              "#Disc.chd",
              "Disc\n.chd",
              "Disc\r.chd",
              "Disc\u0000.chd",
              "content:Disc.chd",
              " Disc.chd",
              "Disc.chd ",
              ".",
              "..")) badNames &= rejects(() -> PlaylistTools.filename(name));
      t.check(
          badNames,
          "M3U filenames reject traversal, absolute schemes, comments and control characters");
      List<StorageTools.Entry> many = new ArrayList<>();
      for (int n = 0; n < 33; n++) many.add(artificial("Disc" + n + ".chd", "d" + n, "root"));
      t.check(
          rejects(() -> PlaylistTools.names(many))
              && rejects(
                  () -> PlaylistTools.filename(String.join("", Collections.nCopies(241, "a")))),
          "Playlist selection and filenames have explicit finite bounds");
      PlaylistTools.Check check = PlaylistTools.checkExisting(c, old, new StorageTools.Token());
      t.check(
          check.references.size() == 2
              && check.missing.equals(Collections.singletonList("Missing (Disc 3).chd")),
          "Existing M3U is read through ContentResolver and detects an exact same-folder missing"
              + " disc");
      PlaylistTools.Check comments =
          PlaylistTools.check(
              "\uFEFF#EXTM3U\r\n" + d1.name + "\r\n\r\n" + d2.name + "\r\n",
              Arrays.asList(d1.name, d2.name));
      t.check(
          comments.references.equals(Arrays.asList(d1.name, d2.name)) && comments.missing.isEmpty(),
          "M3U checker accepts UTF-8 BOM, comments and CRLF without changing reference names");
      t.check(
          rejects(
                  () ->
                      PlaylistTools.check(d1.name + "\n" + d1.name, Collections.singleton(d1.name)))
              && rejects(() -> PlaylistTools.check("../Disc.chd", Collections.emptyList()))
              && rejects(() -> PlaylistTools.check("#empty", Collections.emptyList()))
              && rejects(
                  () -> PlaylistTools.check("Track.bin", Collections.singleton("Track.bin"))),
          "Existing playlist checker rejects duplicate, traversing, empty and unsupported"
              + " references");
      byte[] over = new byte[PlaylistTools.MAX_BYTES + 1];
      Arrays.fill(over, (byte) 'x');
      t.check(
          rejects(
                  () ->
                      PlaylistTools.read(new ByteArrayInputStream(over), new StorageTools.Token()))
              && rejects(
                  () ->
                      PlaylistTools.read(
                          new ByteArrayInputStream(new byte[] {(byte) 0xc3, 0x28}),
                          new StorageTools.Token())),
          "M3U input rejects oversized streams and malformed UTF-8");
      StorageTools.Token cancelled = new StorageTools.Token();
      cancelled.cancel();
      t.check(
          rejects(() -> StorageTools.scan(c, TREE, cancelled))
              && rejects(
                  () -> PlaylistTools.read(new ByteArrayInputStream(new byte[] {1}), cancelled))
              && cancelled.signal.isCanceled(),
          "Cancellation token reaches scan and playlist I/O before accepting data");
      t.check(
          StorageTools.csvCell("=SUM(1,2)").equals("\"'=SUM(1,2)\"")
              && StorageTools.csvCell("+file").contains("'+file")
              && StorageTools.csvCell("@file").contains("'@file")
              && StorageTools.csvCell("-file").contains("'-file")
              && StorageTools.csvCell("a\"b").equals("\"a\"\"b\""),
          "CSV protects formula-like cells and escapes quotes without modifying playlist names");
      String csv = StorageTools.csv(report);
      t.check(
          csv.contains("selected folder only")
              && csv.contains(d2.name + "\",,\"chd\"")
              && csv.contains("414,1,5,2,false,0"),
          "CSV marks folder-only scope, leaves unknown sizes blank and exports honest totals");
      t.check(
          rejects(() -> PlaylistTools.create(c, TREE, order, new StorageTools.Token()))
              && stats(t, c).getInt("creates") == 0,
          "Read-only scan grant cannot create an M3U and fails before provider side effects");
      grant(t, c, READ | WRITE);
      String original = bytes(c, old.uri);
      Uri first = PlaylistTools.create(c, TREE, order, new StorageTools.Token());
      Uri second = PlaylistTools.create(c, TREE, order, new StorageTools.Token());
      t.check(
          !first.equals(second)
              && bytes(c, first).equals(PlaylistTools.encode(order))
              && bytes(c, second).equals(PlaylistTools.encode(order))
              && bytes(c, old.uri).equals(original)
              && stats(t, c).getInt("creates") == 2
              && stats(t, c).getInt("writes") == 2,
          "Actual SAF M3U creation writes distinct new documents in order and preserves existing"
              + " playlist bytes");
      t.check(
          rejects(
                  () ->
                      PlaylistTools.create(
                          c,
                          DocumentsContract.buildTreeDocumentUri(AUTHORITY, "sub"),
                          order,
                          new StorageTools.Token()))
              && stats(t, c).getInt("creates") == 2,
          "Wrong-folder write URI is rejected before creating anything");
      reset(t, c, "alias");
      t.check(
          rejects(() -> PlaylistTools.create(c, TREE, order, new StorageTools.Token()))
              && stats(t, c).getInt("writes") == 0,
          "Provider returning an existing document identity cannot overwrite any existing file");
      reset(t, c, "renamed");
      t.check(
          rejects(() -> PlaylistTools.create(c, TREE, order, new StorageTools.Token()))
              && stats(t, c).getInt("writes") == 0,
          "Provider changing the requested new filename is refused before writing bytes");
      reset(t, c, "createRefuse");
      t.check(
          rejects(() -> PlaylistTools.create(c, TREE, order, new StorageTools.Token()))
              && stats(t, c).getInt("creates") == 0
              && stats(t, c).getInt("writes") == 0,
          "Provider creation refusal reports failure without any write attempt");
      reset(t, c, "writeRefuse");
      t.check(
          rejects(() -> PlaylistTools.create(c, TREE, order, new StorageTools.Token()))
              && stats(t, c).getInt("creates") == 1
              && stats(t, c).getInt("writes") == 1
              && bytes(c, old.uri).equals(original),
          "Provider write refusal can leave only a new incomplete file and preserves original"
              + " bytes");
      reset(t, c, "partial");
      StorageTools.Report partial = StorageTools.scan(c, TREE, new StorageTools.Token());
      t.check(
          partial.files.size() == 4
              && partial.knownBytes == 214
              && partial.unknownSizes == 1
              && !partial.errors.isEmpty(),
          "Denied subtree keeps useful measurements and labels the report partial");
      reset(t, c, "depth");
      StorageTools.Report depth = StorageTools.scan(c, TREE, new StorageTools.Token());
      t.check(
          depth.limited
              && depth.folders <= StorageTools.MAX_DEPTH + 3
              && depth.errors.stream().anyMatch(e -> e.startsWith("Depth limit")),
          "Deep provider hierarchy stops at the documented twelve-level bound");
      reset(t, c, "many");
      StorageTools.Report bounded = StorageTools.scan(c, TREE, new StorageTools.Token());
      t.check(
          bounded.nodes == StorageTools.MAX_NODES
              && bounded.files.size() <= StorageTools.MAX_NODES
              && bounded.limited
              && bounded.largest.size() <= 20,
          "Large folder caps visited entries and largest-file collection");
      reset(t, c, "cycle");
      StorageTools.Report cycle = StorageTools.scan(c, TREE, new StorageTools.Token());
      t.check(
          cycle.files.size() == 5 && cycle.folders == 2 && !cycle.errors.isEmpty(),
          "Repeated provider document identity cannot create a scan cycle");
      reset(t, c, "overflow");
      StorageTools.Report overflow = StorageTools.scan(c, TREE, new StorageTools.Token());
      t.check(
          overflow.knownBytes == 414
              && overflow.unknownSizes == 2
              && overflow.errors.stream().anyMatch(e -> e.startsWith("Size total overflow")),
          "Provider sizes cannot overflow known byte totals or silently become zero");
      reset(t, c, "badMetadata");
      StorageTools.Report metadata = StorageTools.scan(c, TREE, new StorageTools.Token());
      t.check(
          metadata.files.size() == 5 && !metadata.errors.isEmpty(),
          "Unbounded provider display names are rejected as partial metadata");
      reset(t, c, "longMetadata");
      StorageTools.Report textBound = StorageTools.scan(c, TREE, new StorageTools.Token());
      t.check(
          textBound.limited
              && textBound.nodes < StorageTools.MAX_NODES
              && textBound.metadataChars <= StorageTools.MAX_METADATA_CHARS
              && textBound.errors.stream().anyMatch(e -> e.startsWith("Name data limit"))
              && StorageTools.csv(textBound).length() < StorageTools.MAX_METADATA_CHARS * 3,
          "Aggregate long-name metadata stops safely before the node limit and bounds the CSV"
              + " buffer");
      fixture(t, c, "fixtureRevoke", "root", 0);
      StorageTools.Report revoked = StorageTools.scan(c, TREE, new StorageTools.Token());
      t.check(
          revoked.files.isEmpty()
              && !revoked.errors.isEmpty()
              && rejects(() -> PlaylistTools.checkExisting(c, old, new StorageTools.Token())),
          "Revoked Android URI grant invalidates both scan and existing-playlist access");
      reset(t, c, "normal");
      grant(t, c, READ);
      a =
          (MainActivity)
              t.startActivitySync(
                  new Intent(c, MainActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                      .putExtra("pageKey", "Opslag"),
                  ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());
      final MainActivity owner = a;
      Store.prefs(c).edit().putString("language", "en").commit();
      t.runOnMainSync(
          () -> {
            owner.render();
            owner.onActivityResult(
                StorageTools.READ_TREE,
                Activity.RESULT_OK,
                new Intent().setData(TREE).addFlags(READ));
          });
      await(
          t,
          () -> !StorageTools.state(owner).busy && StorageTools.state(owner).report != null,
          7000,
          "Production readable-folder result did not finish scanning");
      t.check(
          onMain(
              t,
              () ->
                  StorageTools.state(owner).report.files.size() == 5
                      && text(owner.getWindow().getDecorView(), "unknown sizes: 1")
                      && button(owner.getWindow().getDecorView(), "Add disc files") != null),
          "Actual folder result builds the visible English storage report and M3U actions");
      int priorWrites = stats(t, c).getInt("writes");
      t.runOnMainSync(
          () ->
              owner.onActivityResult(
                  StorageTools.CSV,
                  Activity.RESULT_OK,
                  new Intent()
                      .setData(Uri.parse("file:///not-a-document-provider.csv"))
                      .addFlags(WRITE)));
      t.check(
          !onMain(t, () -> StorageTools.state(owner).busy)
              && stats(t, c).getInt("writes") == priorWrites,
          "A non-SAF export result is rejected before scheduling file I/O");
      t.runOnMainSync(
          () -> button(owner.getWindow().getDecorView(), "Add disc files").performClick());
      click(t, d1.name);
      t.runOnMainSync(
          () -> button(owner.getWindow().getDecorView(), "Add disc files").performClick());
      click(t, d2.name);
      t.runOnMainSync(() -> button(owner.getWindow().getDecorView(), "Move down").performClick());
      t.check(
          onMain(
              t,
              () ->
                  StorageTools.state(owner).selected.size() == 2
                      && StorageTools.state(owner).selected.get(0).name.equals(d2.name)
                      && text(owner.getWindow().getDecorView(), d1.name)),
          "Real disc-selection dialogs and order button preserve raw Unicode filenames");
      int selected = StorageTools.state(owner).selected.size();
      t.runOnMainSync(
          () -> button(owner.getWindow().getDecorView(), "Add disc files").performClick());
      click(t, d1.name);
      t.check(
          onMain(t, () -> StorageTools.state(owner).selected.size() == selected),
          "Repeated dialog disc choice is rejected without corrupting the current order");
      t.runOnMainSync(
          () ->
              StorageTools.state(owner).pending =
                  new ArrayList<>(StorageTools.state(owner).selected));
      t.runOnMainSync(
          () ->
              owner.onActivityResult(
                  StorageTools.WRITE_TREE,
                  Activity.RESULT_OK,
                  new Intent().setData(TREE).addFlags(READ)));
      t.check(
          stats(t, c).getInt("creates") == 0 && !onMain(t, () -> StorageTools.state(owner).busy),
          "Production new-playlist result refuses a picker result without write permission");
      grant(t, c, READ | WRITE);
      t.runOnMainSync(
          () ->
              owner.onActivityResult(
                  StorageTools.WRITE_TREE,
                  Activity.RESULT_OK,
                  new Intent().setData(TREE).addFlags(READ | WRITE)));
      await(
          t,
          () -> !StorageTools.state(owner).busy,
          7000,
          "Production playlist write did not complete");
      t.check(
          stats(t, c).getInt("creates") == 1
              && stats(t, c).getInt("writes") == 1
              && onMain(t, () -> StorageTools.state(owner).message.startsWith("New M3U saved:")),
          "Production separate write-folder result saves only a new playlist through the background"
              + " worker");
      Uri output =
          DocumentsContract.createDocument(
              c.getContentResolver(),
              DocumentsContract.buildDocumentUriUsingTree(TREE, "root"),
              "text/csv",
              "Report-" + UUID.randomUUID() + ".csv");
      t.runOnMainSync(
          () ->
              owner.onActivityResult(
                  StorageTools.CSV,
                  Activity.RESULT_OK,
                  new Intent().setData(output).addFlags(WRITE)));
      await(t, () -> !StorageTools.state(owner).busy, 7000, "Production CSV result did not finish");
      t.check(
          bytes(c, output).equals(StorageTools.csv(StorageTools.state(owner).report)),
          "Production CSV result writes the exact report bytes through Android ContentResolver");
      StorageTools.Report retained = StorageTools.state(owner).report;
      reset(t, c, "slow");
      int priorQueries = stats(t, c).getInt("queries");
      t.runOnMainSync(() -> StorageTools.scan(owner, TREE));
      long queryDeadline = SystemClock.elapsedRealtime() + 3000;
      while (stats(t, c).getInt("queries") == priorQueries
          && SystemClock.elapsedRealtime() < queryDeadline) Thread.sleep(30);
      t.runOnMainSync(() -> StorageTools.cancel(owner));
      t.check(
          onMain(
              t,
              () ->
                  !StorageTools.state(owner).busy
                      && StorageTools.state(owner).report == retained
                      && StorageTools.state(owner).token.signal.isCanceled()),
          "Cancel task immediately keeps the previous visible report and signals real provider"
              + " I/O");
      CountDownLatch drained = new CountDownLatch(1);
      StorageTools.worker.execute(drained::countDown);
      t.check(
          drained.await(7, TimeUnit.SECONDS)
              && onMain(
                  t,
                  () ->
                      StorageTools.state(owner).report == retained
                          && StorageTools.state(owner).message.startsWith("Cancelled")),
          "Late provider completion after cancellation cannot replace the previous report or"
              + " status");
      reset(t, c, "partial");
      t.runOnMainSync(() -> StorageTools.scan(owner, TREE));
      await(t, () -> !StorageTools.state(owner).busy, 7000, "Partial UI scan did not finish");
      t.check(
          onMain(
              t,
              () ->
                  text(owner.getWindow().getDecorView(), "Partial: true")
                      && text(owner.getWindow().getDecorView(), "selected")),
          "Visible storage page marks refused-provider measurements as partial");
      Store.prefs(c).edit().putString("language", "nl").commit();
      t.runOnMainSync(owner::render);
      t.check(
          onMain(
              t,
              () ->
                  button(owner.getWindow().getDecorView(), "Schijfbestanden toevoegen") != null
                      && text(owner.getWindow().getDecorView(), "Onvolledig: true")),
          "Dutch storage page keeps the same partial measurements and usable disc actions");
      int display = Store.screen(c, true);
      if (display <= 0)
        throw new Exception("Storage lifecycle fixture requires an additional public display");
      bottom =
          (MainActivity)
              t.startActivitySync(
                  new Intent(c, MainActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                      .putExtra("pageKey", "Opslag"),
                  ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle());
      final MainActivity secondary = bottom;
      t.check(
          onMain(
              t,
              () ->
                  button(secondary.getWindow().getDecorView(), "Map kiezen en lezen") != null
                      && secondary.getDisplay().getDisplayId() == display),
          "Storage page constructs on the actual secondary Android display");
      reset(t, c, "slow");
      t.runOnMainSync(() -> StorageTools.scan(secondary, TREE));
      StorageTools.State removed = onMainState(t, secondary);
      t.runOnMainSync(secondary::finish);
      bottom = null;
      CountDownLatch closed = new CountDownLatch(1);
      StorageTools.worker.execute(closed::countDown);
      t.check(
          closed.await(7, TimeUnit.SECONDS)
              && onMain(t, () -> !StorageTools.states.containsKey(secondary))
              && removed.token.signal.isCanceled(),
          "Finishing the real secondary owner cancels its work and removes lifecycle state before"
              + " late callbacks");
      Uri subTree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "sub");
      fixture(t, c, "fixtureGrant", "sub", READ | WRITE);
      StorageTools.persist(c, TREE, READ, "tree");
      StorageTools.persist(c, TREE, READ | WRITE, "writeTree");
      StorageTools.persist(c, subTree, READ | WRITE, "writeTree");
      t.check(
          persisted(c, TREE) == READ
              && persisted(c, subTree) == (READ | WRITE)
              && StorageTools.local(c).getString("tree", "").equals(TREE.toString()),
          "Switching the writable folder preserves the genuine persisted read grant still used by"
              + " scanning");
      StorageTools.persist(c, TREE, READ | WRITE, "writeTree");
      StorageTools.persist(c, subTree, READ, "tree");
      t.check(
          persisted(c, TREE) == (READ | WRITE)
              && persisted(c, subTree) == READ
              && StorageTools.local(c).getString("writeTree", "").equals(TREE.toString()),
          "Switching the scan folder preserves both persisted rights still used by the writable"
              + " folder");
      StorageTools.persist(c, TREE, READ, "tree");
      t.check(
          StorageTools.local(c).getString("tree", "").equals(TREE.toString())
              && StorageTools.local(c).getString("writeTree", "").equals(TREE.toString())
              && !Store.backup(c).toString().contains(AUTHORITY),
          "SAF folder grant references stay device-local and are excluded from portable backups");
    } finally {
      final MainActivity first = a, second = bottom;
      t.runOnMainSync(
          () -> {
            if (second != null) second.finish();
            if (first != null) first.finish();
          });
      fixture(t, c, "fixtureRevoke", "root", 0);
      fixture(t, c, "fixtureRevoke", "sub", 0);
      Store.prefs(c).edit().putString("language", language).commit();
      SharedPreferences.Editor edit = StorageTools.local(c).edit().clear();
      for (Map.Entry<String, ?> e : localBefore.entrySet())
        if (e.getValue() instanceof String) edit.putString(e.getKey(), (String) e.getValue());
      edit.commit();
    }
  }

  static StorageTools.State onMainState(SmokeInstrumentation t, MainActivity a) {
    StorageTools.State[] state = {null};
    t.runOnMainSync(() -> state[0] = StorageTools.state(a));
    return state[0];
  }

  private StorageToolsChecks() {}
}
