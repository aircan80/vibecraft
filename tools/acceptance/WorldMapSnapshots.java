import dev.wowforever.app.db.Database;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.WorldMapCatalog;
import dev.wowforever.app.model.WorldMapRegion;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.pages.WorldMapPage;
import dev.wowforever.app.ui.theme.Fonts;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.theme.Tokens;
import dev.wowforever.app.ui.theme.WowForeverDarkLaf;
import dev.wowforever.app.ui.theme.WowForeverLightLaf;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;

/** Final-jar, candidate-database map captures. Repository reads and image decoding stay off the EDT. */
public final class WorldMapSnapshots {
  public static void main(String[] args) throws Exception {
    if (args.length != 2 || !Boolean.getBoolean("java.awt.headless"))
      throw new IllegalArgumentException("Use -Djava.awt.headless=true DATABASE EMPTY_OUTPUT_DIRECTORY");
    Path out = Path.of(args[1]); Files.createDirectories(out);
    try (var contents = Files.list(out)) {
      if (contents.findAny().isPresent()) throw new IllegalArgumentException("Output directory must be empty");
    }
    WorldMapCatalog catalog;
    Map<Long, BufferedImage> art = new HashMap<>();
    List<Double> samples = new ArrayList<>();
    try (var db = Database.open(Path.of(args[0]))) {
      var repos = Repositories.of(db);
      catalog = repos.worldMaps().catalog();
      for (long id : new long[]{947, 1414, 1415}) {
        var map = repos.zones().mapByUiMap(id).orElseThrow();
        art.put(id, ImageIO.read(new ByteArrayInputStream(map.jpeg())));
      }
      // Warm database catalog and Java2D before measuring repeated load/decode/render passes.
      for (int i = 0; i < 35; i++) {
        long started = System.nanoTime();
        var hierarchy = repos.worldMaps().catalog();
        var map = repos.zones().mapByUiMap(1415).orElseThrow();
        var image = ImageIO.read(new ByteArrayInputStream(map.jpeg()));
        SwingUtilities.invokeAndWait(() -> render(new WorldMapPage(new ViewTarget.WorldMap(1415), hierarchy,
            image, ignored -> {}, ignored -> {}, () -> {}, () -> {}, () -> {}), 1280, 800));
        if (i >= 5) samples.add((System.nanoTime() - started) / 1_000_000.0);
      }
    }
    List<String> report = new ArrayList<>();
    report.add("Map hierarchy source: " + catalog.sourceBuild());
    report.add("Regions: " + catalog.regions().size());
    samples.sort(Double::compareTo);
    double p95 = samples.get((int) Math.ceil(samples.size() * .95) - 1);
    report.add(String.format(java.util.Locale.ROOT, "Eastern Kingdoms cached catalog + database art read + decode + panel construction/render p95: %.2f ms (%d samples)", p95, samples.size()));
    SwingUtilities.invokeAndWait(() -> {
      try {
        Fonts.install();
        RepaintManager.currentManager(null).setDoubleBufferingEnabled(false);
        for (boolean dark : new boolean[]{true, false}) {
          if (dark) { WowForeverDarkLaf.setup(); Theme.setCurrent(Tokens.DARK); }
          else { WowForeverLightLaf.setup(); Theme.setCurrent(Tokens.PARCHMENT); }
          for (int width : new int[]{1280, 420}) for (long id : new long[]{947, 1414, 1415}) {
            var page = new WorldMapPage(new ViewTarget.WorldMap(id), catalog, art.get(id),
                ignored -> {}, ignored -> {}, () -> {}, () -> {}, () -> {});
            var capture = render(page, width, width == 420 ? 720 : 900);
            if (width == 420) assertCompactLayout(page);
            ImageIO.write(capture, "png",
                out.resolve((dark ? "dark" : "light") + "-" + id + "-" + width + ".png").toFile());
          }
        }
        List<WorldMapRegion> opened = new ArrayList<>();
        List<Long> focused = new ArrayList<>();
        var root = new WorldMapPage(new ViewTarget.WorldMap(947), catalog, art.get(947), opened::add,
            focused::add, () -> {}, () -> {}, () -> {});
        enter(buttons(root).stream().filter(b -> b.getText().equals("Eastern Kingdoms")).findFirst().orElseThrow());
        if (opened.getLast().uiMapId() != 1415 || focused.getLast() != 1415)
          throw new AssertionError("Eastern Kingdoms keyboard route/focus failed");
        var continent = new WorldMapPage(new ViewTarget.WorldMap(1415), catalog, art.get(1415), opened::add,
            focused::add, () -> {}, () -> {}, () -> {});
        enter(buttons(continent).stream().filter(b -> b.getText().equals("Westfall")).findFirst().orElseThrow());
        if (opened.getLast().zoneId() == null || opened.getLast().zoneId() != 40 || focused.getLast() != opened.getLast().uiMapId())
          throw new AssertionError("Westfall keyboard route/focus failed");
        report.add("Enter bindings: Azeroth → Eastern Kingdoms (1415) → Westfall (zone 40), retaining focused child: passed");
        report.add("Headless captures do not verify native Tab focus, pointer hover, or actual frame history. Those remain interactive checks.");
      } catch (Exception failure) { throw new RuntimeException(failure); }
    });
    Files.write(out.resolve("report.txt"), report);
    report.forEach(System.out::println);
    if (p95 >= 300) throw new AssertionError("Map p95 exceeds 300 ms");
  }

  private static void assertCompactLayout(Container page) {
    var scroll = descendants(page).stream().filter(javax.swing.JScrollPane.class::isInstance)
        .map(javax.swing.JScrollPane.class::cast).findFirst().orElseThrow();
    int viewportWidth = scroll.getViewport().getWidth();
    if (scroll.getViewport().getView().getWidth() != viewportWidth || scroll.getHorizontalScrollBar().isVisible())
      throw new AssertionError("Compact map exceeds viewport or requires horizontal scrolling");
    for (JButton button : buttons(page)) {
      if (!button.getParent().getClass().getSimpleName().equals("RegionCanvas")) continue;
      if (button.getParent().getWidth() != viewportWidth || button.getX() < 0
          || button.getX() + button.getWidth() > viewportWidth || button.getY() < 0
          || button.getY() + button.getHeight() > button.getParent().getHeight())
        throw new AssertionError("Compact region is outside the scaled map: " + button.getText());
    }
  }
  private static List<Component> descendants(Container parent) {
    List<Component> result = new ArrayList<>();
    for (Component child : parent.getComponents()) {
      result.add(child);
      if (child instanceof Container nested) result.addAll(descendants(nested));
    }
    return result;
  }

  private static void enter(JButton button) {
    Object key = button.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("ENTER"));
    button.getActionMap().get(key).actionPerformed(new ActionEvent(button, 0, ""));
  }
  private static BufferedImage render(JComponent page, int width, int height) {
    page.setSize(width, height); layout(page); layout(page);
    var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics(); page.printAll(graphics); graphics.dispose(); return image;
  }
  private static void layout(Container c) {
    c.doLayout();
    for (Component child : c.getComponents()) if (child instanceof Container nested) layout(nested);
  }
  private static List<JButton> buttons(Container c) {
    List<JButton> result = new ArrayList<>();
    for (Component child : c.getComponents()) {
      if (child instanceof JButton b) result.add(b);
      if (child instanceof Container nested) result.addAll(buttons(nested));
    }
    return result;
  }
}
