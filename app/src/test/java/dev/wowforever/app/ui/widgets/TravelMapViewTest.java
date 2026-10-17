package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.Route;
import dev.wowforever.app.model.TravelMapData;
import dev.wowforever.app.model.TravelMapData.Entrance;
import dev.wowforever.app.model.TravelMapData.Faction;
import dev.wowforever.app.model.TravelMapData.FlightNode;
import dev.wowforever.app.model.TravelMapData.FlightRoute;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.theme.Tokens;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JToggleButton;
import org.junit.jupiter.api.Test;

class TravelMapViewTest {
  private final List<ViewTarget> opened = new ArrayList<>();
  private static final EntityRef DEADMINES = new EntityRef(EntityKind.INSTANCE, 36);
  private static final EntityRef THOR = new EntityRef(EntityKind.NPC, 523);

  private TravelMapData data() {
    return new TravelMapData(List.of(
        new Entrance(DEADMINES, "The Deadmines", 40, 80, false, false, true,
            "The Deadmines · Dungeon · Levels 10–17")), List.of(
        new FlightNode(4, "Sentinel Hill", 50, 50, Faction.ALLIANCE, THOR, List.of("Stormwind", "Lakeshire")),
        new FlightNode(9, "Unmatched", 70, 20, Faction.NEUTRAL, null, List.of())), List.of(
        new FlightRoute(1, "Stormwind", List.of(new Route.Point(50, 50), new Route.Point(110, -10)))));
  }

  private MapView map() {
    var map = new MapView(new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB), opened::add);
    map.setSize(1000, 1000);
    map.setTravel(data());
    return map;
  }

  private static MapLayer layer(String id) {
    return MapLayer.REGISTERED.stream().filter(layer -> layer.id().equals(id)).findFirst().orElseThrow();
  }

  private static void click(MapView map, int x, int y) {
    var event = new MouseEvent(map, MouseEvent.MOUSE_CLICKED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1);
    for (var listener : map.getMouseListeners()) listener.mouseClicked(event);
  }

  @Test
  void nearbyEntranceAndExitNamesStayDistinctAndDoNotRepeatExitPrefix() {
    var image = new BufferedImage(420, 280, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    var entrance = data().entrances().getFirst();
    var exit = new Entrance(DEADMINES, "Exit of The Deadmines", 42, 85, false, true, false, "Recorded exit");
    assertThat(TravelMapPainter.entranceLabel(exit)).isEqualTo("Exit of The Deadmines");
    var first = TravelMapPainter.labelBounds(graphics.getFontMetrics(), entrance.name(), 180, 225, 420, 280, List.of());
    var second = TravelMapPainter.labelBounds(graphics.getFontMetrics(), exit.name(), 190, 235, 420, 280, List.of(first));
    assertThat(first.intersects(second)).isFalse();
    assertThat(second.x).isGreaterThanOrEqualTo(0);
    assertThat(second.y + second.height).isLessThanOrEqualTo(280);
    graphics.dispose();
  }

  @Test
  void compactLegendWrapsAllRegisteredTravelControlsAndSourceFooter() throws Exception {
    javax.swing.SwingUtilities.invokeAndWait(() -> {
      var data = data();
      String sources = "Travel: client 1.60.1.70450 and archived VMaNGOS Vanilla teleport records";
      var panel = new dev.wowforever.app.ui.pages.ZoneMapPanel(40,
          new BufferedImage(1000, 668, BufferedImage.TYPE_INT_RGB), List.of(),
          new dev.wowforever.app.ui.pages.ZoneMapPanel.PinSource() {
            public List<dev.wowforever.app.model.SpawnPin> npcs(long id, boolean bosses) { return List.of(); }
            public List<dev.wowforever.app.model.SpawnPin> objects(long id) { return List.of(); }
            public dev.wowforever.app.model.QuestPins questPins(long id) {
              return new dev.wowforever.app.model.QuestPins(List.of(), false);
            }
          }, Runnable::run, dev.wowforever.app.ui.StatusSink.NONE, ignored -> {},
          dev.wowforever.app.ui.BooleanSetting.inMemory(false), dev.wowforever.app.ui.BooleanSetting.inMemory(false),
          new TravelMapData(data.entrances(), data.flights(), data.routes(), sources));
      var root = new javax.swing.JPanel(new java.awt.BorderLayout());
      root.putClientProperty(dev.wowforever.app.ui.detail.CompactColumns.KEY, true);
      root.add(panel, java.awt.BorderLayout.NORTH);
      root.setSize(420, 720);
      for (int i = 0; i < 4; i++) layout(root);
      assertThat(panel.legend().texts()).contains("Entrances", "Flight paths", "Flight routes");
      for (var component : panel.legend().getComponents()) {
        assertThat(component.getX()).isGreaterThanOrEqualTo(0);
        assertThat(component.getX() + component.getWidth()).isLessThanOrEqualTo(panel.legend().getWidth());
        assertThat(component.getY() + component.getHeight()).isLessThanOrEqualTo(panel.legend().getHeight());
      }
      assertThat(descendants(panel)).anyMatch(component -> component instanceof javax.swing.JTextArea text
          && text.getText().equals(sources) && text.getWidth() <= 420);
    });
  }

  private static void layout(java.awt.Container parent) {
    parent.doLayout();
    for (var component : parent.getComponents()) if (component instanceof java.awt.Container nested) layout(nested);
  }

  private static List<java.awt.Component> descendants(java.awt.Container parent) {
    List<java.awt.Component> result = new ArrayList<>();
    for (var component : parent.getComponents()) {
      result.add(component);
      if (component instanceof java.awt.Container nested) result.addAll(descendants(nested));
    }
    return result;
  }

  @Test
  void travelLayersStartOnWhileRouteLinesStartOffAndLegendCanHideThem() {
    var map = map();
    assertThat(map.isLayerVisible(layer(MapLayer.ENTRANCES))).isTrue();
    assertThat(map.isLayerVisible(layer(MapLayer.FLIGHTS))).isTrue();
    assertThat(map.isLayerVisible(layer(MapLayer.FLIGHT_ROUTES))).isFalse();
    var legend = PinLegend.interactive(map);
    assertThat(legend.texts()).containsExactly("Entrances", "Flight paths", "Flight routes");
    ((JToggleButton) legend.getComponent(0)).doClick();
    assertThat(map.travelAt(400, 800)).isNull();
    assertThat(map.travelAt(500, 500).target()).isEqualTo(THOR);
  }

  @Test
  void travelPresetHidesSpawnPinsButKeepsBothTravelLayersAndQuestPresetHidesTravel() {
    var map = map();
    map.setPins(List.of(new MapView.Pin(THOR, "Thor", 20, 20, MapView.PinKind.NPC)));
    map.applyPreset(MapLayer.TRAVEL);
    assertThat(map.visiblePins()).isEmpty();
    assertThat(map.travelAt(400, 800).target()).isEqualTo(DEADMINES);
    assertThat(map.travelAt(500, 500).target()).isEqualTo(THOR);
    assertThat(map.isLayerVisible(layer(MapLayer.FLIGHT_ROUTES))).isFalse();
    map.applyPreset(MapLayer.BOSSES_AND_RARES);
    assertThat(map.travelAt(400, 800)).isNull();
    assertThat(map.travelAt(500, 500)).isNull();
  }

  @Test
  void entranceAndMatchedFlightMasterOpenTheirEntitiesWhileUnmatchedNodeOnlyHasTooltip() {
    var map = map();
    click(map, 400, 800);
    click(map, 500, 500);
    click(map, 700, 200);
    assertThat(opened).containsExactly(new ViewTarget.Entity(DEADMINES), new ViewTarget.Entity(THOR));
    assertThat(map.travelAt(400, 800).tooltip()).contains("Dungeon", "Levels 10–17", "Exit lands here");
    assertThat(map.travelAt(500, 500).tooltip()).contains("Alliance flight path", "Flies to: Stormwind, Lakeshire");
    assertThat(map.travelAt(700, 200).tooltip()).contains("Neutral flight path");
    assertThat(map.travelAt(700, 200).target()).isNull();
  }

  @Test
  void routeLeavingMapLabelsItsActualBoundaryIntersection() {
    var edge = TravelMapPainter.exitPoint(new Route.Point(50, 50), new Route.Point(110, -10)).orElseThrow();
    assertThat(edge.x).isCloseTo(100, within(.00001));
    assertThat(edge.y).isCloseTo(0, within(.00001));
    assertThat(TravelMapPainter.exitPoint(new Route.Point(10, 10), new Route.Point(20, 20))).isEmpty();
    assertThat(TravelMapPainter.exitPoint(new Route.Point(-10, -10), new Route.Point(-20, -20))).isEmpty();
  }

  @Test
  void entrancesExitsFactionsAndClippedRoutesPaintInBothThemesAtCompactWidth() {
    Theme saved = Theme.current();
    try {
      for (Theme theme : List.of(Tokens.DARK, Tokens.PARCHMENT)) {
        Theme.setCurrent(theme);
        var map = map();
        var original = data();
        map.setTravel(new TravelMapData(List.of(original.entrances().getFirst(),
            new Entrance(DEADMINES, "Raid wing", 10, 15, true, false, false, "Raid wing · Raid"),
            new Entrance(DEADMINES, "The Deadmines", 80, 70, false, true, false, "Exit of The Deadmines")),
            List.of(original.flights().getFirst(), original.flights().getLast(),
                new FlightNode(10, "Horde node", 30, 30, Faction.HORDE, null, List.of())), original.routes()));
        map.setLayerVisible(layer(MapLayer.FLIGHT_ROUTES), true);
        map.setSize(420, 420);
        var image = new BufferedImage(420, 420, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        map.paint(graphics); graphics.dispose();
        assertThat(image.getRGB(210, 210) & 0xffffff).isNotZero();
        assertThat(map.travelAt(168, 336).tooltip()).contains("The Deadmines");
      }
    } finally { Theme.setCurrent(saved); }
  }
}
