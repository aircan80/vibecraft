package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.WorldMapCatalog;
import dev.wowforever.app.model.WorldMapRegion;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.pages.WorldMapPage;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class JournalWorldMapPageTest {
  @Test void unplacedDestinationsUseOnePageScrollAndRemainFullyReachableAtBothWidths() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var continent = new WorldMapRegion(947, 1415, "Eastern Kingdoms", .5, .1, .9, .9,
          null, true, true, Optional.empty(), 0, 0);
      var island = new WorldMapRegion(947, 2521, "Zephras Isle", 0, 0, 0, 0,
          2521L, true, false, Optional.empty(), 0, 0);
      var other = new WorldMapRegion(947, 2522, "Another unplaced destination", 0, 0, 0, 0,
          null, false, false, Optional.empty(), 0, 0);
      for (int width : new int[]{420, 1280}) {
        List<WorldMapRegion> opened = new ArrayList<>();
        List<Long> focused = new ArrayList<>();
        var page = new JournalWorldMapPage(new ViewTarget.WorldMap(947),
            new WorldMapCatalog(List.of(continent, island, other)),
            new BufferedImage(1002, 668, BufferedImage.TYPE_INT_RGB),
            opened::add, focused::add, () -> {}, () -> {}, () -> {});
        page.setSize(width, 420);
        for (int pass = 0; pass < 8; pass++) layout(page);
        var components = descendants(page);
        var scrolls = components.stream().filter(JScrollPane.class::isInstance).map(JScrollPane.class::cast).toList();
        assertThat(scrolls).as("one outer page scroll, never a nested map scroll").hasSize(1);
        var map = components.stream().filter(WorldMapPage.class::isInstance).map(WorldMapPage.class::cast)
            .findFirst().orElseThrow();
        for (String name : List.of("Zephras Isle", "Another unplaced destination")) {
          JButton button = descendants(map).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
              .filter(b -> b.getText().startsWith(name)).findFirst().orElseThrow();
          assertThat(button.getText()).contains("Position unavailable");
          JButton continentButton = descendants(map).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
              .filter(b -> b.getText().equals("Eastern Kingdoms")).findFirst().orElseThrow();
          assertThat(button.getY() + button.getHeight()).as("unplaced destination precedes map artwork")
              .isLessThanOrEqualTo(continentButton.getParent().getY());
          Rectangle inMap = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), map);
          assertThat(new Rectangle(0, 0, map.getWidth(), map.getHeight()).contains(inMap))
              .as(name + " fully inside measured map content at " + width).isTrue();
          Rectangle inPage = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(),
              scrolls.getFirst().getViewport().getView());
          ((javax.swing.JComponent) scrolls.getFirst().getViewport().getView()).scrollRectToVisible(inPage);
          assertThat(scrolls.getFirst().getViewport().getViewRect().contains(inPage))
              .as(name + " reachable using the outer page scroll").isTrue();
          button.doClick();
        }
        assertThat(opened).containsExactly(island);
        assertThat(focused).contains(2521L, 2522L);
      }
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
  private static void layout(Container parent) {
    parent.doLayout();
    for (Component child : parent.getComponents()) if (child instanceof Container nested) layout(nested);
  }
}
