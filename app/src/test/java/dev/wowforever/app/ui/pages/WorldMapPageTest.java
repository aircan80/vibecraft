package dev.wowforever.app.ui.pages;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.LevelBand;
import dev.wowforever.app.model.WorldMapCatalog;
import dev.wowforever.app.model.WorldMapRegion;
import dev.wowforever.app.nav.ViewTarget;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class WorldMapPageTest {
  private static WorldMapRegion region(long id, String name, Long zone, boolean placed) {
    return new WorldMapRegion(1415, id, name, .1, .2, .5, .6, zone, false, placed,
        Optional.of(new LevelBand(10, 20)), 98, 2);
  }

  @Test
  void compactViewportScalesTheWholeCanvasWithoutHorizontalScrolling() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var edge = new WorldMapRegion(1415, 1, "Eastern edge", .8, .1, .99, .9, 40L, true,
          true, Optional.empty(), 0, 0);
      var page = new WorldMapPage(new ViewTarget.WorldMap(1415), new WorldMapCatalog(List.of(edge)),
          new java.awt.image.BufferedImage(1002, 668, java.awt.image.BufferedImage.TYPE_INT_RGB),
          ignored -> {}, ignored -> {}, () -> {}, () -> {}, () -> {});
      page.setSize(420, 720);
      for (int i = 0; i < 3; i++) Tables.layOut(page);
      var canvas = descendants(page).stream().filter(WorldMapPage.RegionCanvas.class::isInstance)
          .map(WorldMapPage.RegionCanvas.class::cast).findFirst().orElseThrow();
      var scroll = descendants(page).stream().filter(javax.swing.JScrollPane.class::isInstance)
          .map(javax.swing.JScrollPane.class::cast).findFirst().orElseThrow();
      int viewportWidth = scroll.getViewport().getWidth();
      assertThat(canvas.getWidth()).isEqualTo(viewportWidth).isLessThan(420);
      assertThat(scroll.getViewport().getView().getWidth()).isEqualTo(viewportWidth);
      assertThat(scroll.getHorizontalScrollBar().isVisible()).isFalse();
      for (Component child : canvas.getComponents()) {
        assertThat(child.getX()).isGreaterThanOrEqualTo(0);
        assertThat(child.getX() + child.getWidth()).isLessThanOrEqualTo(viewportWidth);
        assertThat(child.getY()).isGreaterThanOrEqualTo(0);
        assertThat(child.getY() + child.getHeight()).isLessThanOrEqualTo(canvas.getHeight());
      }
    });
  }

  @Test
  void passiveLabelsDoNotOverlapAndHoverKeepsTheFullName() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var westfall = new WorldMapRegion(1415, 1436, "Westfall", .1, .1, .3, .4, 40L, true,
          true, Optional.empty(), 0, 0);
      var duskwood = new WorldMapRegion(1415, 1431, "Duskwood", .12, .1, .32, .4, 10L, true,
          true, Optional.empty(), 0, 0);
      var page = new WorldMapPage(new ViewTarget.WorldMap(1415), new WorldMapCatalog(List.of(westfall, duskwood)),
          null, ignored -> {}, ignored -> {}, () -> {}, () -> {}, () -> {});
      page.setSize(1280, 900);
      for (int i = 0; i < 3; i++) Tables.layOut(page);
      var canvas = descendants(page).stream().filter(WorldMapPage.RegionCanvas.class::isInstance)
          .map(WorldMapPage.RegionCanvas.class::cast).findFirst().orElseThrow();
      assertThat(canvas.labelPlates()).hasSize(1);
      var duskButton = buttons(canvas).stream().filter(b -> b.getText().equals("Duskwood")).findFirst().orElseThrow();
      duskButton.getModel().setRollover(true);
      assertThat(canvas.labelPlates()).extracting(WorldMapPage.RegionCanvas.LabelPlate::text).contains("Duskwood");
      var plates = canvas.labelPlates();
      for (int i = 0; i < plates.size(); i++) for (int j = i + 1; j < plates.size(); j++)
        assertThat(plates.get(i).bounds().intersects(plates.get(j).bounds())).isFalse();
    });
  }

  private static List<Component> descendants(Container parent) {
    List<Component> result = new ArrayList<>();
    for (Component child : parent.getComponents()) {
      result.add(child);
      if (child instanceof Container container) result.addAll(descendants(container));
    }
    return result;
  }

  @Test
  void narrowLabelsKeepTheirBeginningAndFitTheRegion() {
    var image = new java.awt.image.BufferedImage(100, 30, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 11));
    var metrics = graphics.getFontMetrics();
    String label = WorldMapPage.fitLabel("Westfall", metrics, metrics.stringWidth("West…"));
    assertThat(label).startsWith("West").endsWith("…");
    assertThat(metrics.stringWidth(label)).isLessThanOrEqualTo(metrics.stringWidth("West…"));
    assertThat(WorldMapPage.fitLabel("Westfall", metrics, 200)).isEqualTo("Westfall");
    assertThat(WorldMapPage.fitLabel("Westfall", metrics, 0)).isEmpty();
    graphics.dispose();
  }

  @Test
  void keyboardEnterOpensKnownZoneAndRetainsRegionFocus() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var westfall = region(1436, "Westfall", 40L, true);
      List<WorldMapRegion> opened = new ArrayList<>();
      List<Long> focused = new ArrayList<>();
      var page = new WorldMapPage(new ViewTarget.WorldMap(1415), new WorldMapCatalog(List.of(westfall)),
          null, opened::add, focused::add, () -> {}, () -> {}, () -> {});
      JButton button = buttons(page).stream().filter(b -> b.getText().equals("Westfall")).findFirst().orElseThrow();
      assertThat(button.isFocusable()).isTrue();
      assertThat(button.getAccessibleContext().getAccessibleName()).contains("Westfall", "Levels 10–20");
      assertThat(button.getToolTipText()).contains("98 quests", "2 instances", "No map art");
      Object action = button.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("ENTER"));
      button.getActionMap().get(action).actionPerformed(new ActionEvent(button, 0, ""));
      assertThat(opened).containsExactly(westfall);
      assertThat(focused).containsExactly(1436L);
    });
  }

  @Test
  void unavailableAndUnplacedRegionsRemainDiscoverableWithoutInventedPosition() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var island = region(2521, "Zephras Isle", null, false);
      List<WorldMapRegion> opened = new ArrayList<>();
      var page = new WorldMapPage(new ViewTarget.WorldMap(1415), new WorldMapCatalog(List.of(island)),
          null, opened::add, ignored -> {}, () -> {}, () -> {}, () -> {});
      JButton button = buttons(page).stream().filter(b -> b.getText().startsWith("Zephras Isle")).findFirst().orElseThrow();
      assertThat(button.isFocusable()).isTrue();
      assertThat(button.getText()).contains("Position unavailable");
      assertThat(button.getToolTipText()).contains("Not in the database yet").doesNotContain("quests");
      button.doClick();
      assertThat(opened).isEmpty();
    });
  }

  @Test
  void backspaceGoesUpAndEscapeClearsFocusedRegion() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      AtomicInteger up = new AtomicInteger();
      List<Long> focused = new ArrayList<>();
      var page = new WorldMapPage(new ViewTarget.WorldMap(1415), new WorldMapCatalog(List.of()),
          null, ignored -> {}, focused::add, () -> {}, up::incrementAndGet, () -> {});
      for (String stroke : List.of("BACK_SPACE", "ESCAPE")) {
        Object key = page.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke(stroke));
        page.getActionMap().get(key).actionPerformed(new ActionEvent(page, 0, ""));
      }
      assertThat(up.get()).isEqualTo(1);
      assertThat(focused).containsExactly((Long) null);
    });
  }

  private static List<JButton> buttons(Container parent) {
    List<JButton> result = new ArrayList<>();
    for (Component child : parent.getComponents()) {
      if (child instanceof JButton button) result.add(button);
      if (child instanceof Container container) result.addAll(buttons(container));
    }
    return result;
  }
}
