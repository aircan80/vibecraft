import dev.wowforever.app.db.Database;
import dev.wowforever.app.db.DatabaseInstaller;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.ui.BooleanSetting;
import dev.wowforever.app.ui.StatusSink;
import dev.wowforever.app.ui.ThemeManager;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.detail.DetailPanels;
import dev.wowforever.app.ui.pages.NpcPage;
import dev.wowforever.app.ui.pages.QuestPage;
import dev.wowforever.app.ui.theme.Fonts;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.IconCache;
import dev.wowforever.app.ui.widgets.MapView;
import dev.wowforever.app.ui.widgets.Rail;
import dev.wowforever.app.ui.widgets.RailIcons;
import dev.wowforever.app.ui.widgets.RailTarget;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import javax.swing.JPanel;
import javax.swing.RepaintManager;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.text.View;
import javax.swing.SwingUtilities;

/** Standalone panel-only capture runner. Compile against the exact shaded app jar being reviewed. */
public final class RenderSnapshots {
  private static final int FULL_WIDTH = 1216;
  private static final int FULL_HEIGHT = 680;
  private static final int WARMUP = 5;
  private static final double LOAD_TARGET_MS = 300;
  private static final List<EntityRef> CASES = List.of(
      ref(EntityKind.QUEST, 176), ref(EntityKind.QUEST, 166), ref(EntityKind.QUEST, 6502),
      ref(EntityKind.QUEST, 79976),
      ref(EntityKind.QUEST, 7787), ref(EntityKind.QUEST, 7791), ref(EntityKind.NPC, 550),
      ref(EntityKind.NPC, 412), ref(EntityKind.ZONE, 40), ref(EntityKind.ZONE, 6170), ref(EntityKind.INSTANCE, 3));

  private record Timing(EntityRef ref, double p50, double p95, double max, List<Double> samples) {}
  private record Capture(String file, int width, int height, int mapCount, int routes, List<String> failures) {}
  private record Loaded(Repositories repos, RailIcons rail, List<EntityRef> cases, Map<EntityRef, Object> pages,
      List<Timing> timings, String build, String clientBuild) {}

  private RenderSnapshots() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 4) throw new IllegalArgumentException("Expected jar, output directory, temporary DB directory, repeats");
    int repeats = Integer.parseInt(args[3]);
    if (repeats < 20) throw new IllegalArgumentException("At least 20 measured loads per case are required");
    if (!Boolean.getBoolean("java.awt.headless")) throw new IllegalStateException("Use the headless shell runner");
    Path jar = Path.of(args[0]);
    Path output = Path.of(args[1]).toAbsolutePath();
    Files.createDirectories(output);
    try (var entries = Files.list(output)) {
      if (entries.findAny().isPresent()) throw new IllegalArgumentException("Output directory must be empty: " + output);
    }
    onEdt(() -> {
      Fonts.install();
      ThemeManager.apply(ThemeManager.Mode.DARK);
      RepaintManager.currentManager(null).setDoubleBufferingEnabled(false);
    });
    Loaded loaded = load(Path.of(args[2]), repeats);
    // load() has CLOSED the database. Any repository-dependent rendering now fails instead of querying on the EDT.
    List<Capture> captures = new ArrayList<>();
    for (ThemeManager.Mode mode : ThemeManager.Mode.values()) {
      String theme = mode == ThemeManager.Mode.DARK ? "slate" : "parchment";
      onEdt(() -> {
        ThemeManager.apply(mode);
        RailIcons.install(loaded.rail());
      });
      for (EntityRef entity : loaded.cases()) {
        for (boolean compact : new boolean[] {false, true}) {
          String name = entity.kind().dbKind() + "-" + entity.id() + "-" + theme
              + (compact ? "-compact" : "-full");
          onEdt(() -> captures.add(capturePage(loaded, entity, compact, output, name)));
        }
        if (entity.kind() == EntityKind.NPC) {
          String name = "npc-" + entity.id() + "-routes-" + theme;
          onEdt(() -> captures.add(captureRoutes(loaded, entity, output, name)));
        }
      }
      onEdt(() -> captures.add(captureRail(loaded.rail(), output, "rail-" + theme)));
    }
    writeReport(output, jar, loaded, captures, repeats);
    long failures = captures.stream().filter(c -> !c.failures().isEmpty()).count();
    System.out.printf(Locale.ROOT, "Captured %d panels; %d load cases; %d captures with geometry failures.%n",
        captures.size(), loaded.timings().size(), failures);
    for (Timing timing : loaded.timings()) {
      System.out.printf(Locale.ROOT, "%s %d: warm p95 %.2f ms (target <= %.0f ms; informational)%n",
          timing.ref().kind(), timing.ref().id(), timing.p95(), LOAD_TARGET_MS);
    }
    if (failures > 0) throw new IllegalStateException("See manifest.json for capture geometry failures");
  }

  private static Loaded load(Path directory, int repeats) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) throw new AssertionError("Database loading must run off the EDT");
    Path file = new DatabaseInstaller(directory).install();
    try (Database database = Database.open(file)) {
      Repositories repos = Repositories.of(database);
      IconCache cache = new IconCache(id -> {
        if (SwingUtilities.isEventDispatchThread()) throw new AssertionError("Icon query on the EDT");
        return repos.assets().icon(id);
      }, IconCache.CAPACITY, null); // paint-time misses stay empty: no asynchronous DB work after close
      onEdt(() -> IconCache.install(cache));
      RailIcons rail = RailIcons.load(repos.assets()::uiIcon);
      List<EntityRef> cases = new ArrayList<>(CASES);
      // Every new dungeon and the complete area breadcrumb path must appear in final acceptance.
      for(String key:List.of("client:2959","client:2998","client:2999","client:3065")) {
        cases.add(repos.instances().all().stream().filter(instance -> key.equals(instance.key()))
            .findFirst().orElseThrow(() -> new IllegalStateException("Missing client dungeon "+key)).ref());
      }
      Map<EntityRef, Object> pages = new LinkedHashMap<>();
      List<Timing> timings = new ArrayList<>();
      for (EntityRef entity : cases) {
        for (int i = 0; i < WARMUP; i++) detail(entity, repos);
        List<Double> samples = new ArrayList<>();
        for (int i = 0; i < repeats; i++) {
          long start = System.nanoTime();
          Object page = detail(entity, repos);
          samples.add((System.nanoTime() - start) / 1_000_000.0);
          pages.put(entity, page);
        }
        double[] sorted = samples.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        timings.add(new Timing(entity, percentile(sorted, 0.5), percentile(sorted, 0.95),
            sorted[sorted.length - 1], List.copyOf(samples)));
      }
      return new Loaded(repos, rail, List.copyOf(cases), Map.copyOf(pages), List.copyOf(timings),
          database.meta().getOrDefault("build_id", "unknown"),
          database.meta().getOrDefault("client_build", "unknown"));
    }
  }

  private static Object detail(EntityRef ref, Repositories repos) {
    return DetailPanels.load(ref, repos).orElseThrow(() -> new IllegalStateException("Missing acceptance entity " + ref));
  }

  private static JComponent page(Loaded loaded, EntityRef entity) {
    if (!SwingUtilities.isEventDispatchThread()) throw new AssertionError("Rendering must run on the EDT");
    JComponent page = DetailPanels.render(loaded.pages().get(entity), loaded.repos(), target -> {},
        task -> { throw new AssertionError("Unexpected asynchronous load during static capture"); },
        new StatusSink() {
          public void message(String text) {}
          public void fail(Exception failure) { throw new IllegalStateException("Unexpected page load failure", failure); }
        }, SourceFilter.ALL, BooleanSetting.inMemory(false));
    if (!(page instanceof JPanel)) throw new IllegalStateException("Only panel detail pages are captured");
    return page;
  }

  private static Capture capturePage(Loaded loaded, EntityRef entity, boolean compact, Path output, String name)
      throws Exception {
    JComponent page = page(loaded, entity);
    int width = compact ? 420 : FULL_WIDTH;
    int height = compact ? 720 : FULL_HEIGHT;
    JPanel root = root(page, compact, width, height);
    List<String> failures = new ArrayList<>();
    wrappedTextClips(root, failures);
    if (page instanceof QuestPage quest) {
      if (!inside(quest.header(), root)) failures.add("Quest header extends outside viewport");
      coreClips(quest.header(), root, failures, true);
      coreClips(quest.objectivesPanel(), root, failures, false);
      if (!compact) {
        for (String title : List.of("Given by", "Turned in to")) {
          var section = quest.section(title);
          if (section == null || section.table().rowCount() == 0) continue;
          var table = section.table().table();
          Rectangle row = SwingUtilities.convertRectangle(table, table.getCellRect(0, 0, true), root);
          if (!new Rectangle(0, 0, root.getWidth(), root.getHeight()).contains(row)) {
            failures.add("First " + title + " table row is below the fold or clipped: " + row);
          }
        }
      }
      if (quest.stepper().isVisible() && !inside(quest.stepper(), root)) failures.add("Stepper extends outside viewport");
      if (!inside(quest.mapPanel(), root)) failures.add("Quest map panel extends outside viewport");
      MapView map = quest.mapPanel().mapView();
      if (map == null) {
        if (!quest.mapPanel().noMapNote().isVisible()) failures.add("Missing map has no visible fallback note");
      } else {
        Rectangle visible = clippedBounds(map, root);
        for (MapView.Pin pin : map.visiblePins()) {
          if (!pin.kind().quest()) continue;
          var point = map.projection().toComponent(pin.x(), pin.y());
          var inRoot = SwingUtilities.convertPoint(map, (int) Math.round(point.x), (int) Math.round(point.y), root);
          if (!visible.contains(inRoot)) failures.add("Active giver pin outside visible map: " + pin.name());
        }
      }
      if (quest.mapPanel().banner().isVisible() && !inside(quest.mapPanel().banner(), root)) {
        failures.add("Other-zone banner extends outside viewport");
      }
    }
    for(var trail:descendants(root,dev.wowforever.app.ui.widgets.BreadcrumbTrail.class)) {
      if(!inside(trail,root))failures.add("Breadcrumb trail outside viewport");
      Rectangle bounds=new Rectangle(0,0,trail.getWidth(),trail.getHeight());
      for(Component part:trail.getComponents())if(!bounds.contains(part.getBounds()))
        failures.add("Breadcrumb segment clipped: "+part.getBounds());
    }
    List<MapView> maps = descendants(root, MapView.class);
    int routes = maps.stream().mapToInt(map -> map.routes().size()).sum();
    return save(root, output, name, maps.size(), routes, failures);
  }

  private static Capture captureRoutes(Loaded loaded, EntityRef entity, Path output, String name) throws Exception {
    NpcPage page = (NpcPage) page(loaded, entity);
    MapView map = page.spawnMap();
    if (map == null) throw new IllegalStateException("No route map for " + entity);
    JPanel root = root(map, false, 1002, 668);
    List<String> failures = map.routes().isEmpty() ? List.of("Expected patrol route is missing") : List.of();
    return save(root, output, name, 1, map.routes().size(), failures);
  }

  private static Capture captureRail(RailIcons icons, Path output, String name) throws Exception {
    Rail rail = new Rail();
    rail.setIcons(icons);
    rail.select(RailTarget.QUESTS);
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBackground(Theme.current().bgBase());
    panel.add(rail, BorderLayout.WEST);
    return save(root(panel, false, 64, 420), output, name, 0, 0, List.of());
  }

  private static JPanel root(JComponent page, boolean compact, int width, int height) {
    JPanel root = new JPanel(new BorderLayout());
    root.setBackground(Theme.current().bgBase());
    root.putClientProperty(CompactColumns.KEY, compact);
    root.add(page, BorderLayout.CENTER);
    root.setSize(width, height);
    prepareRenderers(root);
    for (int pass = 0; pass < 12; pass++) layout(root);
    // Text UI views can update wrapping preferences on first paint. Settle those normal deferred layouts.
    BufferedImage warm = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    for (int pass = 0; pass < 3; pass++) {
      Graphics2D graphics = warm.createGraphics();
      try { root.printAll(graphics); }
      finally { graphics.dispose(); }
      layout(root);
    }
    return root;
  }

  /** Renderer containers have no peers in headless mode; explicitly lay out their children like on-screen cells. */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void prepareRenderers(Component root) {
    for (JList list : descendants(root, JList.class)) {
      var original = list.getCellRenderer();
      if (original == null) continue;
      list.setCellRenderer((owner, value, index, selected, focus) -> {
        Component cell = original.getListCellRendererComponent(owner, value, index, selected, focus);
        Dimension preferred = cell.getPreferredSize();
        layoutRenderer(cell, owner.getWidth() > 0 ? owner.getWidth() : preferred.width, preferred.height);
        return cell;
      });
    }
    for (JTable table : descendants(root, JTable.class)) {
      for (int index = 0; index < table.getColumnCount(); index++) {
        var column = table.getColumnModel().getColumn(index);
        TableCellRenderer original = column.getCellRenderer();
        if (original == null) original = table.getDefaultRenderer(table.getColumnClass(index));
        TableCellRenderer renderer = original;
        column.setCellRenderer((owner, value, selected, focus, row, viewColumn) -> {
          Component cell = renderer.getTableCellRendererComponent(owner, value, selected, focus, row, viewColumn);
          Rectangle bounds = owner.getCellRect(row, viewColumn, true);
          layoutRenderer(cell, bounds.width, bounds.height);
          return cell;
        });
      }
    }
  }

  private static void layoutRenderer(Component cell, int width, int height) {
    cell.setSize(Math.max(1, width), Math.max(1, height));
    if (cell instanceof Container container) layout(container);
  }

  private static void layout(Container component) {
    component.doLayout();
    for (Component child : component.getComponents()) {
      if (child instanceof Container container) layout(container);
    }
  }

  private static Capture save(JPanel root, Path output, String name, int maps, int routes, List<String> failures)
      throws Exception {
    BufferedImage image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(Theme.current().bgBase());
      graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
      graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      root.printAll(graphics);
    } finally { graphics.dispose(); }
    String file = name + ".png";
    if (!ImageIO.write(image, "png", output.resolve(file).toFile())) throw new IllegalStateException("PNG writer unavailable");
    StringBuilder geometry = new StringBuilder("class\tx\ty\twidth\theight\tvisible\tclip_x\tclip_y\tclip_width\tclip_height\tparents\ttext\n");
    describe(root, root, geometry);
    Files.writeString(output.resolve(name + ".layout.tsv"), geometry, StandardCharsets.UTF_8);
    return new Capture(file, root.getWidth(), root.getHeight(), maps, routes, List.copyOf(failures));
  }

  private static Rectangle bounds(Component component, Component root) {
    return SwingUtilities.convertRectangle(component, new Rectangle(0, 0, component.getWidth(), component.getHeight()), root);
  }

  private static Rectangle clippedBounds(Component component, Component root) {
    Rectangle visible = bounds(component, root);
    for (Component parent = component.getParent(); parent != null; parent = parent.getParent()) {
      visible = visible.intersection(bounds(parent, root));
      if (parent == root) break;
    }
    return visible;
  }

  private static boolean inside(Component component, Component root) {
    Rectangle at = bounds(component, root);
    return visible(component, root) && at.width > 0 && at.height > 0
        && new Rectangle(0, 0, root.getWidth(), root.getHeight()).contains(at);
  }

  /** A wrapped text area can fit its ancestors yet still allocate fewer lines than its text view needs. */
  private static void wrappedTextClips(Component root, List<String> failures) {
    Rectangle viewport = new Rectangle(0, 0, root.getWidth(), root.getHeight());
    for (JTextArea area : descendants(root, JTextArea.class)) {
      Rectangle actual = bounds(area, root);
      if (!visible(area, root) || !area.getLineWrap() || area.getText().isBlank()
          || actual.width <= 0 || actual.height <= 0 || !viewport.intersects(actual)) continue;
      var insets = area.getInsets();
      float width = Math.max(1, area.getWidth() - insets.left - insets.right);
      View view = area.getUI().getRootView(area);
      view.setSize(width, Float.MAX_VALUE);
      int required = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS)) + insets.top + insets.bottom;
      view.setSize(width, Math.max(1, area.getHeight() - insets.top - insets.bottom));
      if (area.getHeight() + 1 < required) {
        failures.add("Wrapped text content clipped: allocated=" + area.getHeight() + ", required=" + required
            + "; " + area.getText().replace('\n', ' '));
      }
    }
  }

  /** Internal allocation clipping is a bug; ordinary offscreen content in the outer scroll pane is not. */
  private static void coreClips(Component core, Component root, List<String> failures, boolean includeOutside) {
    List<Component> candidates = new ArrayList<>();
    candidates.addAll(descendants(core, JLabel.class));
    candidates.addAll(descendants(core, AbstractButton.class));
    candidates.addAll(descendants(core, JTextArea.class));
    Rectangle viewport = new Rectangle(0, 0, root.getWidth(), root.getHeight());
    for (Component component : candidates) {
      if (!visible(component, root)) continue;
      String text = component instanceof JLabel label ? label.getText()
          : component instanceof AbstractButton button ? button.getText() : ((JTextArea) component).getText();
      if (text == null || text.isBlank()) continue;
      Rectangle actual = bounds(component, root);
      if (actual.width <= 0 || actual.height <= 0 || !includeOutside && !viewport.intersects(actual)) continue;
      Rectangle internal = new Rectangle(actual);
      for (Component parent = component.getParent(); parent != null && !(parent instanceof JViewport);
          parent = parent.getParent()) {
        internal = internal.intersection(bounds(parent, root));
        if (parent == root) break;
      }
      if (!actual.equals(internal)) {
        failures.add("Core field clipped by internal ancestor: " + text.replace('\n', ' ')
            + "; bounds=" + actual + "; internal=" + internal);
      }
    }
  }

  private static boolean visible(Component component, Component root) {
    for (Component at = component; at != null; at = at.getParent()) {
      if (!at.isVisible()) return false;
      if (at == root) return true;
    }
    return false;
  }

  private static void describe(Component component, Component root, StringBuilder output) {
    String text = component instanceof JLabel label ? label.getText()
        : component instanceof AbstractButton button ? button.getText()
        : component instanceof JTextArea area ? area.getText() : "";
    if (component instanceof MapView || text != null && !text.isBlank()) {
      Rectangle rectangle = bounds(component, root);
      output.append(component.getClass().getSimpleName()).append('\t').append(rectangle.x).append('\t')
          .append(rectangle.y).append('\t').append(rectangle.width).append('\t').append(rectangle.height)
          .append('\t').append(visible(component, root)).append('\t');
      Rectangle clip = clippedBounds(component, root);
      output.append(clip.x).append('\t').append(clip.y).append('\t').append(Math.max(0, clip.width)).append('\t')
          .append(Math.max(0, clip.height)).append('\t').append(parents(component, root)).append('\t')
          .append(text == null ? "" : text.replace("\t", " ").replace("\r", " ").replace("\n", " ")).append('\n');
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) describe(child, root, output);
    }
  }

  private static String parents(Component component, Component root) {
    List<String> parents = new ArrayList<>();
    for (Component parent = component.getParent(); parent != null; parent = parent.getParent()) {
      Rectangle rectangle = bounds(parent, root);
      parents.add(parent.getClass().getSimpleName() + "(" + rectangle.x + "," + rectangle.y + ","
          + rectangle.width + "," + rectangle.height + ")");
      if (parent == root) break;
    }
    return String.join(" > ", parents);
  }

  private static <T> List<T> descendants(Component component, Class<T> type) {
    List<T> out = new ArrayList<>();
    if (type.isInstance(component)) out.add(type.cast(component));
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) out.addAll(descendants(child, type));
    }
    return out;
  }

  private static void writeReport(Path output, Path jar, Loaded loaded, List<Capture> captures, int repeats)
      throws Exception {
    List<String> timingRows = new ArrayList<>();
    for (Timing timing : loaded.timings()) {
      timingRows.add("{\"kind\":" + json(timing.ref().kind().dbKind()) + ",\"id\":" + timing.ref().id()
          + ",\"warmups\":" + WARMUP + ",\"repeats\":" + repeats
          + ",\"p50_ms\":" + decimal(timing.p50()) + ",\"p95_ms\":" + decimal(timing.p95())
          + ",\"max_ms\":" + decimal(timing.max()) + ",\"target_ms\":" + LOAD_TARGET_MS
          + ",\"within_target\":" + (timing.p95() <= LOAD_TARGET_MS)
          + ",\"samples_ms\":[" + String.join(",", timing.samples().stream().map(RenderSnapshots::decimal).toList()) + "]}");
    }
    List<String> imageRows = captures.stream().map(c -> "{\"file\":" + json(c.file())
        + ",\"width\":" + c.width() + ",\"height\":" + c.height() + ",\"map_count\":" + c.mapCount()
        + ",\"route_count\":" + c.routes() + ",\"failures\":["
        + String.join(",", c.failures().stream().map(RenderSnapshots::json).toList()) + "]}").toList();
    String report = "{\n\"created_utc\":" + json(Instant.now().toString())
        + ",\n\"app_jar_sha256\":" + json(sha256(jar)) + ",\n\"database_build\":" + json(loaded.build())
        + ",\n\"client_build\":" + json(loaded.clientBuild())
        + ",\n\"database_closed_before_render\":true,\n\"ui_scale\":1,\n\"java_version\":" + json(System.getProperty("java.version"))
        + ",\n\"full_viewport\":\"1216x680 content within a 1280x800 window; master list collapsed\""
        + ",\n\"performance_note\":\"Warm DetailPanels.load only, including its normal decoding/cache work; nearest-rank p95; timing is informational and never a CI assertion. Run with no concurrent builds for release measurements.\""
        + ",\n\"capture_note\":\"JPanel-only headless captures, no JFrame/JDialog, real user preferences, runtime DB queries or network. Renderer child layout is initialized explicitly without peers; offscreen paint/layout passes settle text views. Geometry checks do not replace visual review or keyboard testing.\""
        + ",\n\"timings\":[\n" + String.join(",\n", timingRows) + "\n],\n\"captures\":[\n"
        + String.join(",\n", imageRows) + "\n]\n}\n";
    Files.writeString(output.resolve("manifest.json"), report, StandardCharsets.UTF_8);
  }

  private static String json(String value) {
    if (value == null) return "null";
    StringBuilder quoted = new StringBuilder("\"");
    for (char character : value.toCharArray()) {
      switch (character) {
        case '"' -> quoted.append("\\\"");
        case '\\' -> quoted.append("\\\\");
        case '\n' -> quoted.append("\\n");
        case '\r' -> quoted.append("\\r");
        case '\t' -> quoted.append("\\t");
        default -> {
          if (character < 32) quoted.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
          else quoted.append(character);
        }
      }
    }
    return quoted.append('"').toString();
  }

  private static String sha256(Path path) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream input = Files.newInputStream(path)) {
      byte[] buffer = new byte[65536];
      for (int count; (count = input.read(buffer)) >= 0;) digest.update(buffer, 0, count);
    }
    return java.util.HexFormat.of().formatHex(digest.digest());
  }

  private static double percentile(double[] sorted, double percentile) {
    return sorted[Math.max(0, (int) Math.ceil(percentile * sorted.length) - 1)];
  }

  private static String decimal(double value) { return String.format(Locale.ROOT, "%.3f", value); }
  private static EntityRef ref(EntityKind kind, long id) { return new EntityRef(kind, id); }

  @FunctionalInterface
  private interface EdtWork { void run() throws Exception; }

  private static void onEdt(EdtWork work) throws Exception {
    AtomicReference<Exception> failure = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      try { work.run(); }
      catch (Exception exception) { failure.set(exception); }
    });
    if (failure.get() != null) throw failure.get();
  }
}
