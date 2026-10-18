package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.*;
import dev.wowforever.app.ui.*;
import dev.wowforever.app.ui.detail.DetailPanels;
import dev.wowforever.app.ui.widgets.MapLayer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class JournalMapInspectorTest {
  private DetailPanels.LoadedZone loaded() {
    var detail = FixtureDb.repositories().zones().detail(40).orElseThrow();
    var travel = new TravelMapData(List.of(new TravelMapData.Entrance(new EntityRef(EntityKind.INSTANCE, 3),
        "The Deadmines", 38, 77, false, false, true, "Dungeon · Levels 10–17")),
        List.of(new TravelMapData.FlightNode(4, "Sentinel Hill", 56, 52, TravelMapData.Faction.ALLIANCE,
            new EntityRef(EntityKind.NPC, 523), List.of("Stormwind"))), List.of(), "Fixture travel source");
    return new DetailPanels.LoadedZone(detail, new BufferedImage(1002, 668, BufferedImage.TYPE_INT_RGB), List.of(),
        List.of(new GeographyStep("Azeroth", 947, GeographyStep.Kind.MAP),
            new GeographyStep("Eastern Kingdoms", 1415, GeographyStep.Kind.MAP),
            new GeographyStep("Westfall", 40, GeographyStep.Kind.ZONE)), travel);
  }

  @Test void selectingRealMarkerInspectsWithoutChangingHistoryAndOpenPreservesMapParent() throws Exception {
    var loaded = loaded();
    SwingUtilities.invokeAndWait(() -> {
      NavigationHistory history = new NavigationHistory();
      var parent = new ViewTarget.WorldMap(1415, 1436L);
      var zone = new ViewTarget.Entity(new EntityRef(EntityKind.ZONE, 40));
      history.push(parent); history.push(zone);
      List<String> copied = new ArrayList<>();
      var page = page(loaded, history::push, new ArrayList<>(), copied);
      page.mapPanel().mapView().setSize(1002, 668);
      var at = page.mapPanel().mapView().projection().toComponent(38, 77);
      var event = new MouseEvent(page.mapPanel().mapView(), MouseEvent.MOUSE_CLICKED, 0, 0,
          (int) at.x, (int) at.y, 1, false, MouseEvent.BUTTON1);
      for (var listener : page.mapPanel().mapView().getMouseListeners()) listener.mouseClicked(event);
      assertThat(page.inspector().selected().name()).isEqualTo("The Deadmines");
      assertThat(page.inspector().selected().facts()).contains("Dungeon", "Exit lands here");
      assertThat(history.size()).isEqualTo(2);
      button(page.inspector(), "Copy /way").doClick();
      assertThat(copied).containsExactly("/way Westfall 38.0 77.0");
      button(page.inspector(), "Open record →").doClick();
      assertThat(history.current()).contains(new ViewTarget.Entity(new EntityRef(EntityKind.INSTANCE, 3)));
      assertThat(history.back()).contains(zone);
      assertThat(history.back()).contains(parent);
    });
  }

  @Test void inspectorControlsUseRealMapLayerAndAsyncQuestModelsAndStateRestoresSelection() throws Exception {
    var loaded = loaded();
    SwingUtilities.invokeAndWait(() -> {
      List<Runnable> work = new ArrayList<>();
      var first = page(loaded, ignored -> {}, work, new ArrayList<>());
      button(first.inspector(), "The Deadmines").doClick();
      var entrance = descendants(first.inspector()).stream().filter(JToggleButton.class::isInstance)
          .map(JToggleButton.class::cast).filter(b -> b.getText().equals("Dungeon entrances")).findFirst().orElseThrow();
      entrance.doClick();
      var layer = MapLayer.REGISTERED.stream().filter(l -> l.id().equals(MapLayer.ENTRANCES)).findFirst().orElseThrow();
      assertThat(first.mapPanel().mapView().isLayerVisible(layer)).isFalse();
      var quests = descendants(first.inspector()).stream().filter(JCheckBox.class::isInstance)
          .map(JCheckBox.class::cast).filter(b -> b.getText().equals("Quest givers")).findFirst().orElseThrow();
      quests.doClick();
      assertThat(first.mapPanel().questsToggle().isSelected()).isTrue();
      assertThat(work).hasSize(1);
      Object saved = first.saveState();
      var restored = page(loaded, ignored -> {}, new ArrayList<>(), new ArrayList<>());
      restored.restoreState(saved);
      assertThat(restored.inspector().selected().name()).isEqualTo("The Deadmines");
      assertThat(restored.mapPanel().mapView().isLayerVisible(layer)).isFalse();
    });
  }

  @Test void worldDestinationInspectorUsesActualCatalogAndFocusCallbackBeforeNavigation() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var region = new WorldMapRegion(1415, 1436, "Westfall", .2, .3, .4, .5, 40L, true, true,
          Optional.of(new LevelBand(10, 20)), 98, 2);
      NavigationHistory history = new NavigationHistory(); history.push(new ViewTarget.WorldMap(1415));
      var page = new JournalWorldMapPage(new ViewTarget.WorldMap(1415), new WorldMapCatalog(List.of(region)),
          new BufferedImage(1002, 668, BufferedImage.TYPE_INT_RGB),
          child -> history.push(new ViewTarget.Entity(new EntityRef(EntityKind.ZONE, child.zoneId()))),
          child -> history.replaceCurrent(new ViewTarget.WorldMap(1415, child)), () -> {}, () -> {}, () -> {});
      button(page.inspector(), "Westfall").doClick();
      assertThat(page.inspector().selected().facts()).contains("Levels 10–20", "98 quests");
      assertThat(descendants(page.inspector())).noneMatch(c -> c instanceof JButton b && b.getText().equals("Copy /way"));
      button(page.inspector(), "Open record →").doClick();
      assertThat(history.back()).contains(new ViewTarget.WorldMap(1415, 1436L));
    });
  }

  @Test void compactInspectorMovesBelowFullWidthMapAndDesktopInspectorMovesRight() throws Exception {
    var loaded = loaded();
    SwingUtilities.invokeAndWait(() -> {
      for (int width : new int[]{420, 1280}) {
        var page = page(loaded, ignored -> {}, new ArrayList<>(), new ArrayList<>());
        page.setSize(width, 900);
        for (int i = 0; i < 5; i++) layout(page);
        var map = page.mapPanel(); var inspector = page.inspector();
        assertThat(map.getWidth()).isGreaterThan(width == 420 ? 300 : 500);
        if (width == 420) {
          assertThat(inspector.getY()).isGreaterThanOrEqualTo(map.getY() + map.getHeight());
          assertThat(inspector.getWidth()).isEqualTo(map.getWidth());
        } else assertThat(inspector.getX()).isGreaterThanOrEqualTo(map.getX() + map.getWidth());
      }
    });
  }

  @Test void legendStaysBelowMapAndOutOfInspectorAtBothWidths() throws Exception {
    var loaded = loaded();
    SwingUtilities.invokeAndWait(() -> {
      for (int width : new int[]{420, 1280}) {
        var page = page(loaded, ignored -> {}, new ArrayList<>(), new ArrayList<>());
        page.setSize(width, 900);
        for (int i = 0; i < 8; i++) layout(page);
        var map = page.mapPanel();
        assertThat(SwingUtilities.isDescendingFrom(map.legend(), page.inspector())).isFalse();
        assertThat(map.legend().isVisible()).isTrue();
        Point footer = SwingUtilities.convertPoint(map.legend(), 0, 0, map);
        assertThat(footer.y).isGreaterThanOrEqualTo(map.mapView().getY() + map.mapView().getHeight());
        assertThat(map.legend().getHeight()).isGreaterThan(0);
        for (Component entry : map.legend().getComponents()) {
          assertThat(entry.getFont().getSize2D()).isGreaterThanOrEqualTo(12f);
          assertThat(entry.getY() + entry.getHeight()).isLessThanOrEqualTo(map.legend().getHeight());
        }
        assertThat(descendants(page.inspector())).noneMatch(c -> c instanceof JLabel label
            && label.getText().equals("Layers & legend"));
      }
    });
  }

  private JournalZonePage page(DetailPanels.LoadedZone loaded, Navigator nav, List<Runnable> work, List<String> copied) {
    return new JournalZonePage(loaded, FixtureDb.repositories(), nav, work::add, StatusSink.NONE,
        BooleanSetting.inMemory(false), BooleanSetting.inMemory(false), copied::add);
  }
  private static JButton button(Container parent, String text) {
    return descendants(parent).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
        .filter(b -> b.getText().equals(text)).findFirst().orElseThrow();
  }
  private static List<Component> descendants(Container parent) {
    List<Component> result = new ArrayList<>();
    for (Component child : parent.getComponents()) { result.add(child); if (child instanceof Container c) result.addAll(descendants(c)); }
    return result;
  }
  private static void layout(Container parent) {
    parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container c) layout(c);
  }
}
