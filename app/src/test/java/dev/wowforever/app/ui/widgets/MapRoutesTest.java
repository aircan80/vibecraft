package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.Route;
import dev.wowforever.app.nav.ViewTarget;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MapRoutesTest {
  private static final EntityRef NPC = new EntityRef(EntityKind.NPC, 550);
  private final List<ViewTarget> opened = new ArrayList<>();

  private MapView map(boolean loop) {
    MapView map = new MapView(new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB), opened::add);
    map.setSize(200, 200);
    map.setRoutes(List.of(new MapView.RouteLine(NPC, "Defias Messenger", MapView.PinKind.SELF,
        List.of(new Route.Point(10, 10), new Route.Point(90, 10), new Route.Point(90, 90)), loop)));
    map.setLayerVisible(MapLayer.REGISTERED.stream().filter(l -> l.id().equals("routes")).findFirst().orElseThrow(), true);
    return map;
  }

  private static BufferedImage paint(MapView map) {
    BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try { map.paint(g); } finally { g.dispose(); }
    return image;
  }

  private static MouseEvent click(MapView map, int x, int y) {
    return new MouseEvent(map, MouseEvent.MOUSE_CLICKED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1);
  }

  @Test
  void routeDrawsDashedTwoPxLine() {
    BufferedImage painted = paint(map(false));
    int ink = 0, gaps = 0;
    for (int x = 30; x < 160; x++) {
      if ((painted.getRGB(x, 20) >>> 24) > 0) ink++; else gaps++;
    }
    assertThat(ink).isGreaterThan(30);
    assertThat(gaps).isGreaterThan(20);
    assertThat(painted.getRGB(40, 24) >>> 24).isZero();
  }

  @Test
  void nonLoopGetsHollowEndMarkerAndTooltip() {
    MapView map = map(false);
    assertThat(map.getToolTipText(click(map, 180, 180))).isEqualTo(MapView.END_TOOLTIP);
    BufferedImage painted = paint(map);
    assertThat(painted.getRGB(180, 180) >>> 24).isZero();
    assertThat(painted.getRGB(176, 180) >>> 24).isPositive();
  }

  @Test
  void loopHasClosingSegmentAndNoEndTooltip() {
    MapView map = map(true);
    assertThat(map.routeAt(100, 100)).isNotNull();
    assertThat(map.getToolTipText(click(map, 180, 180))).isEqualTo("Defias Messenger");
    BufferedImage painted = paint(map);
    assertThat(painted.getRGB(176, 180) >>> 24).isZero();
  }

  @Test
  void lineHitOpensNpcButPinWins() {
    MapView map = map(false);
    map.dispatchEvent(click(map, 80, 23));
    assertThat(opened).containsExactly(new ViewTarget.Entity(NPC));
    opened.clear();
    EntityRef other = new EntityRef(EntityKind.NPC, 99);
    map.setPins(List.of(new MapView.Pin(other, "Other NPC", 40, 10, MapView.PinKind.NPC)));
    map.dispatchEvent(click(map, 80, 20));
    assertThat(opened).containsExactly(new ViewTarget.Entity(other));
    assertThat(map.clusters()).hasSize(1);
  }

  @Test
  void openRouteEndingOnArrowIndexKeepsItsMarkerHollow() {
    MapView map = map(false);
    map.setRoutes(List.of(new MapView.RouteLine(NPC, "Stitches", MapView.PinKind.SELF,
        java.util.stream.IntStream.rangeClosed(0, 10)
            .mapToObj(i -> new Route.Point(10 + i * 8, 10 + i * 8)).toList(), false)));
    BufferedImage painted = paint(map);
    assertThat(painted.getRGB(180, 180) >>> 24).isZero();
    assertThat(map.getToolTipText(click(map, 180, 180))).isEqualTo(MapView.END_TOOLTIP);
  }

  @Test
  void cursorResetsAfterLeavingARouteOrTheMap() {
    MapView map = map(false);
    map.dispatchEvent(new MouseEvent(map, MouseEvent.MOUSE_MOVED, 0, 0, 80, 20, 0, false));
    assertThat(map.getCursor().getType()).isEqualTo(java.awt.Cursor.HAND_CURSOR);
    map.dispatchEvent(new MouseEvent(map, MouseEvent.MOUSE_MOVED, 0, 0, 80, 80, 0, false));
    assertThat(map.getCursor().getType()).isEqualTo(java.awt.Cursor.DEFAULT_CURSOR);
    map.dispatchEvent(new MouseEvent(map, MouseEvent.MOUSE_MOVED, 0, 0, 80, 20, 0, false));
    map.dispatchEvent(new MouseEvent(map, MouseEvent.MOUSE_EXITED, 0, 0, -1, -1, 0, false));
    assertThat(map.getCursor().getType()).isEqualTo(java.awt.Cursor.DEFAULT_CURSOR);
  }

  @Test
  void routesAreImmutableAndEmptyListsAreSafe() {
    MapView map = map(false);
    map.setRoutes(List.of());
    assertThat(map.routes()).isEmpty();
    assertThat(map.routeAt(40, 20)).isNull();
  }
}
