import dev.wowforever.app.db.Database;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.*;
import dev.wowforever.app.ui.BooleanSetting;
import dev.wowforever.app.ui.StatusSink;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.pages.ZoneMapPanel;
import dev.wowforever.app.ui.theme.*;
import dev.wowforever.app.ui.widgets.MapLayer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Reviewed-jar travel captures against a supplied candidate database; does not modify database or preferences. */
public final class TravelMapSnapshots {
  record Loaded(long zoneId, String name, BufferedImage art, List<SpawnPin> pins, TravelMapData travel) {}
  private static final ZoneMapPanel.PinSource NO_QUERIES = new ZoneMapPanel.PinSource() {
    public List<SpawnPin> npcs(long zone, boolean bosses) { throw new AssertionError("Unexpected EDT pin query"); }
    public List<SpawnPin> objects(long zone) { throw new AssertionError("Unexpected EDT object query"); }
    public QuestPins questPins(long zone) { throw new AssertionError("Unexpected EDT quest query"); }
  };
  private static final StatusSink STATUS = new StatusSink() {
    public void message(String text) {}
    public void fail(Exception failure) { throw new AssertionError(failure); }
  };
  public static void main(String[] args) throws Exception {
    if (args.length != 2 || !Boolean.getBoolean("java.awt.headless"))
      throw new IllegalArgumentException("Use -Djava.awt.headless=true DATABASE EMPTY_OUTPUT_DIRECTORY");
    Path output = Path.of(args[1]); Files.createDirectories(output);
    try (var files = Files.list(output)) {
      if (files.findAny().isPresent()) throw new IllegalArgumentException("Output must be empty");
    }
    Map<Long, Loaded> loaded = new LinkedHashMap<>();
    List<String> report = new ArrayList<>();
    SwingUtilities.invokeAndWait(() -> { Fonts.install(); WowForeverDarkLaf.setup(); Theme.setCurrent(Tokens.DARK); });
    try (var db = Database.open(Path.of(args[0]))) {
      var repos = Repositories.of(db);
      for (long zone : new long[]{40, 46, 1519, 12}) loaded.put(zone, load(repos, zone));
      assertContent(loaded);
      for (long zone : new long[]{40, 46}) {
        List<Double> samples = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
          long started = System.nanoTime();
          Loaded data = load(repos, zone);
          SwingUtilities.invokeAndWait(() -> render(data, 1280, false));
          if (i >= 5) samples.add((System.nanoTime() - started) / 1_000_000.0);
        }
        samples.sort(Double::compareTo);
        double p95 = samples.get((int) Math.ceil(samples.size() * .95) - 1);
        report.add(String.format(java.util.Locale.ROOT,
            "%s query+decode+panel/render p95 %.2f ms (%d samples; informational, 300 ms target)", loaded.get(zone).name(), p95, samples.size()));
      }
    }
    SwingUtilities.invokeAndWait(() -> {
      try {
        RepaintManager.currentManager(null).setDoubleBufferingEnabled(false);
        for (boolean dark : new boolean[]{true, false}) {
          if (dark) { WowForeverDarkLaf.setup(); Theme.setCurrent(Tokens.DARK); }
          else { WowForeverLightLaf.setup(); Theme.setCurrent(Tokens.PARCHMENT); }
          for (Loaded data : loaded.values()) for (int width : new int[]{1280, 420}) {
            var image = render(data, width, width == 420);
            ImageIO.write(image, "png", output.resolve((dark ? "dark" : "light") + "-travel-" + data.zoneId() + "-" + width + ".png").toFile());
          }
        }
      } catch (Exception failure) { throw new RuntimeException(failure); }
    });
    for (Loaded data : loaded.values()) report.add(data.name() + ": entrances=" + data.travel().entrances().size()
        + ", nodes=" + data.travel().flights().size() + ", routes=" + data.travel().routes().size() + "; " + data.travel().sourceLabel());
    report.add("Passed: Westfall Deadmines/Thor 523, Stormwind flight node, Elwynn no entrances, default-on markers/default-off routes, Travel preset, bounded viewports and marker centers.");
    report.add("Headless rendering does not establish native hover, keyboard focus, or frame navigation acceptance.");
    Files.write(output.resolve("report.txt"), report); report.forEach(System.out::println);
  }
  private static Loaded load(Repositories repos, long id) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) throw new AssertionError("EDT database read");
    var zone = repos.zones().detail(id).orElseThrow();
    var map = zone.map().orElseThrow();
    return new Loaded(id, zone.summary().displayName(), ImageIO.read(new ByteArrayInputStream(map.jpeg())),
        repos.zones().npcSpawnsInZone(id, true), repos.travel().inZone(id));
  }
  private static void assertContent(Map<Long, Loaded> data) {
    if (data.get(40L).travel().entrances().stream().noneMatch(e -> !e.exit() && e.name().contains("Deadmines")))
      throw new AssertionError("Westfall lacks Deadmines entrance");
    if (data.get(40L).travel().flights().stream().noneMatch(n -> n.name().contains("Sentinel Hill")
        && n.flightMaster() != null && n.flightMaster().id() == 523)) throw new AssertionError("Sentinel Hill lacks Thor 523 link");
    if (data.get(1519L).travel().flights().isEmpty()) throw new AssertionError("Stormwind lacks flight path");
    if (!data.get(12L).travel().entrances().isEmpty()) throw new AssertionError("Elwynn has entrance pins");
    for (Loaded loaded : data.values()) {
      if (loaded.travel().sourceLabel().isBlank()) throw new AssertionError("Missing mixed-client travel source footer");
      for (var marker : loaded.travel().entrances()) if (!MapCoordinates.onMap(marker.x(), marker.y()))
        throw new AssertionError("Entrance outside map: " + marker.name());
      for (var marker : loaded.travel().flights()) if (!MapCoordinates.onMap(marker.x(), marker.y()))
        throw new AssertionError("Flight node outside map: " + marker.name());
    }
  }
  private static BufferedImage render(Loaded data, int width, boolean compact) {
    ZoneMapPanel panel = new ZoneMapPanel(data.zoneId(), data.art(), data.pins(), NO_QUERIES,
        task -> { throw new AssertionError("Unexpected lazy load"); }, STATUS, ignored -> {},
        BooleanSetting.inMemory(false), BooleanSetting.inMemory(false), data.travel());
    panel.mapView().setZoneName(data.name());
    for (MapLayer layer : MapLayer.REGISTERED) {
      if ((layer.id().equals(MapLayer.ENTRANCES) || layer.id().equals(MapLayer.FLIGHTS)) && !panel.mapView().isLayerVisible(layer))
        throw new AssertionError("Travel markers off by default");
      if (layer.id().equals(MapLayer.FLIGHT_ROUTES) && panel.mapView().isLayerVisible(layer))
        throw new AssertionError("Flight routes on by default");
    }
    panel.mapView().applyPreset(MapLayer.TRAVEL);
    for (MapLayer layer : MapLayer.REGISTERED) if (panel.mapView().isLayerVisible(layer) != layer.presets().contains(MapLayer.TRAVEL))
      throw new AssertionError("Travel preset differs from registry");
    JPanel root = new JPanel(new BorderLayout());
    root.putClientProperty(CompactColumns.KEY, compact);
    var body = new PageBody(); body.add(panel, BorderLayout.NORTH);
    var scroll = new JScrollPane(body); scroll.setBorder(BorderFactory.createEmptyBorder()); root.add(scroll);
    root.setSize(width, compact ? 720 : 900);
    for (int i = 0; i < 4; i++) layout(root);
    if (scroll.getHorizontalScrollBar().isVisible() || body.getWidth() != scroll.getViewport().getWidth())
      throw new AssertionError("Travel map requires horizontal scrolling");
    var projection = panel.mapView().projection();
    if (projection.drawnWidth() > scroll.getViewport().getWidth()) throw new AssertionError("Map wider than viewport");
    for (var entrance : data.travel().entrances()) assertMarker(projection, entrance.x(), entrance.y());
    for (var node : data.travel().flights()) assertMarker(projection, node.x(), node.y());
    var image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics(); root.printAll(graphics); graphics.dispose(); return image;
  }
  private static void assertMarker(dev.wowforever.app.ui.widgets.MapView.Projection p, double x, double y) {
    var at = p.toComponent(x, y);
    if (at.x < 0 || at.y < 0 || at.x > p.drawnWidth() || at.y > p.drawnHeight())
      throw new AssertionError("Marker center is outside map");
  }
  private static void layout(Container parent) {
    parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container nested) layout(nested);
  }
  private static final class PageBody extends JPanel implements Scrollable {
    PageBody() { super(new BorderLayout()); }
    public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
    public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height; }
    public boolean getScrollableTracksViewportWidth() { return true; }
    public boolean getScrollableTracksViewportHeight() { return false; }
  }
}
