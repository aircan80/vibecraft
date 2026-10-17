package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.SpawnPin;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.MapView.Cluster;
import dev.wowforever.app.ui.widgets.MapView.Pin;
import dev.wowforever.app.ui.widgets.MapView.PinKind;
import dev.wowforever.app.ui.widgets.MapView.Projection;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class MapViewTest {
  private final BufferedImage westfall =
      Images.decode(FixtureDb.repositories().zones().map(40).orElseThrow().jpeg()).orElseThrow();
  private final List<ViewTarget> opened = new ArrayList<>();

  private static Pin pin(long id, String name, double x, double y, PinKind kind) {
    return new Pin(new EntityRef(kind == PinKind.OBJECT ? EntityKind.OBJECT : EntityKind.NPC, id), name, x, y, kind);
  }

  /** A view of the Westfall map at its natural size (1002 x 668, one image pixel per component pixel). */
  private MapView natural(List<Pin> pins) {
    var v = new MapView(westfall, opened::add);
    v.setSize(1002, 668);
    v.setPins(pins);
    return v;
  }

  private static MouseEvent mouse(MapView v, int id, int x, int y) {
    return new MouseEvent(v, id, 0L, 0, x, y, 1, false, MouseEvent.BUTTON1);
  }

  @Test
  void holdsTheDecodedImage() {
    var v = new MapView(westfall, opened::add);
    assertThat(v.image()).isSameAs(westfall);
    assertThat(v.pins()).isEmpty();
  }

  @Test
  void needsAnImage() {
    assertThatNullPointerException().isThrownBy(() -> new MapView(null, opened::add));
  }

  @Test
  void projectionScalesToTheWidthKeepingTheAspectRatioWithoutUpscaling() {
    var half = new Projection(1002, 668, 501);
    assertThat(half.drawnWidth()).isEqualTo(501);
    assertThat(half.drawnHeight()).isEqualTo(334);
    assertThat(half.toComponent(50, 50)).isEqualTo(new Point2D.Double(250.5, 167));
    assertThat(half.toComponent(0, 100)).isEqualTo(new Point2D.Double(0, 334));
    var wide = new Projection(1002, 668, 3000);
    assertThat(wide.drawnWidth()).isEqualTo(1002);
    assertThat(wide.drawnHeight()).isEqualTo(668);
    assertThat(wide.toComponent(100, 100)).isEqualTo(new Point2D.Double(1002, 668));
    assertThat(new Projection(1002, 668, 0).drawnHeight()).isZero();
    var v = new MapView(westfall, opened::add);
    assertThat(v.heightFor(501)).isEqualTo(334);
    assertThat(v.heightFor(3000)).isEqualTo(668);
    assertThat(v.heightFor(420)).isEqualTo(280);
    v.setSize(501, 10);
    assertThat(v.getPreferredSize().height).isEqualTo(334);
    assertThat(v.projection()).isEqualTo(half);
  }

  @Test
  void paintsTheScaledImageAndThePins() {
    var v = new MapView(westfall, opened::add);
    v.setSize(501, 334);
    v.setPins(List.of(pin(1, "Boss", 50, 50, PinKind.BOSS)));
    BufferedImage canvas = new BufferedImage(501, 334, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = canvas.createGraphics();
    v.paint(g);
    g.dispose();
    assertThat(canvas.getRGB(10, 10) >>> 24).as("alpha of a map pixel").isEqualTo(0xFF);
    assertThat(canvas.getRGB(250, 167) & 0xFFFFFF).as("the boss pin is drawn in gold")
        .isEqualTo(Theme.current().accentGold().getRGB() & 0xFFFFFF);
  }

  @Test
  void pinsOfSpawnPointsTakeTheirKindFromTheRank() {
    EntityRef npc = new EntityRef(EntityKind.NPC, 1);
    assertThat(Pin.of(new SpawnPin(npc, "B", 3, 1, 2), false).kind()).isEqualTo(PinKind.BOSS);
    assertThat(Pin.of(new SpawnPin(npc, "R", 2, 1, 2), false).kind()).isEqualTo(PinKind.RARE);
    assertThat(Pin.of(new SpawnPin(npc, "R", 4, 1, 2), false).kind()).isEqualTo(PinKind.RARE);
    assertThat(Pin.of(new SpawnPin(npc, "E", 1, 1, 2), false).kind()).isEqualTo(PinKind.NPC);
    assertThat(Pin.of(new SpawnPin(npc, null, null, 1, 2), false))
        .isEqualTo(new Pin(npc, "Unknown NPC #1", 1, 2, PinKind.NPC));
    EntityRef object = new EntityRef(EntityKind.OBJECT, 7);
    assertThat(Pin.of(new SpawnPin(object, "Chest", null, 1, 2), false).kind()).isEqualTo(PinKind.OBJECT);
    assertThat(Pin.of(new SpawnPin(object, "Chest", null, 1, 2), true).kind()).isEqualTo(PinKind.SELF);
  }

  @Test
  void hitTestingFindsAPinWithinEightPixels() {
    var v = natural(List.of(pin(1, "Vultros", 50, 50, PinKind.RARE)));
    // 50 % of 1002 x 668 is (501, 334)
    assertThat(v.clusterAt(501, 334)).isNotNull();
    assertThat(v.clusterAt(509, 334)).as("8 px right").isNotNull();
    assertThat(v.clusterAt(501, 342)).as("8 px down").isNotNull();
    assertThat(v.clusterAt(510, 334)).as("9 px right").isNull();
    assertThat(v.clusterAt(507, 340)).as("8.5 px diagonally").isNull();
    // the same maths at half size: the pin is drawn at (250.5, 167)
    v.setSize(501, 334);
    assertThat(v.clusterAt(250, 167)).isNotNull();
    assertThat(v.clusterAt(501, 334)).isNull();
  }

  @Test
  void pinsWithinEightPixelsClusterIntoOneMarkerWithACount() {
    // 0.4 % of 1002 px is 4 px: A is 4 px from the boss B, C 11 px
    var v = natural(List.of(pin(1, "A", 50, 50, PinKind.NPC), pin(2, "B", 50.4, 50, PinKind.BOSS),
        pin(3, "C", 51.5, 50, PinKind.NPC)));
    assertThat(v.clusters()).extracting(Cluster::count).containsExactlyInAnyOrder(2, 1);
    // the cluster is drawn as its most important member (the boss), where the boss is
    Cluster pair = v.clusters().stream().filter(c -> c.count() == 2).findFirst().orElseThrow();
    assertThat(pair.kind()).isEqualTo(PinKind.BOSS);
    assertThat(pair.position().getX()).isCloseTo(0.504 * 1002, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(pair.position().getY()).isEqualTo(334);
    // at half the width all three lie within 8 px of the boss (C 5.5 px)
    v.setSize(501, 334);
    assertThat(v.clusters()).extracting(Cluster::count).containsExactly(3);
    assertThat(MapView.cluster(v.pins(), new Projection(1002, 668, 1002), 8)).hasSize(2);
  }

  @Test
  void theFilterHidesPlainNpcs() {
    var v = natural(List.of(pin(1, "Npc", 10, 10, PinKind.NPC), pin(2, "Boss", 30, 30, PinKind.BOSS),
        pin(3, "Rare", 50, 50, PinKind.RARE), pin(4, "Chest", 70, 70, PinKind.OBJECT),
        pin(5, "Self", 90, 90, PinKind.SELF)));
    assertThat(v.bossesAndRaresOnly()).isFalse();
    assertThat(v.visiblePins()).hasSize(5);
    v.setFilter(true);
    assertThat(v.bossesAndRaresOnly()).isTrue();
    assertThat(v.visiblePins()).extracting(Pin::name).containsExactly("Boss", "Rare", "Chest", "Self");
    assertThat(v.clusterAt(100, 67)).as("the hidden NPC is not hit").isNull();
    v.setFilter(false);
    assertThat(v.clusters()).hasSize(5);
  }

  @Test
  void hoverShowsTheNameAndTheCountOfACluster() {
    var v = natural(List.of(pin(1, "Hogger", 50, 50, PinKind.NPC), pin(1, "Hogger", 50.2, 50, PinKind.NPC),
        pin(2, "Thor", 20, 20, PinKind.NPC), pin(3, "Gryan", 20.3, 20, PinKind.NPC)));
    assertThat(v.getToolTipText(mouse(v, MouseEvent.MOUSE_MOVED, 501, 334))).isEqualTo("Hogger (2)");
    assertThat(v.getToolTipText(mouse(v, MouseEvent.MOUSE_MOVED, 200, 134)))
        .isEqualTo("<html>2 pins<br>Thor<br>Gryan</html>");
    assertThat(v.getToolTipText(mouse(v, MouseEvent.MOUSE_MOVED, 700, 600))).isNull();
    v.setPins(List.of(pin(4, "Foe <Reaper>", 50, 50, PinKind.RARE)));
    assertThat(v.getToolTipText(mouse(v, MouseEvent.MOUSE_MOVED, 501, 334))).isEqualTo("Foe <Reaper>");
    v.dispatchEvent(mouse(v, MouseEvent.MOUSE_MOVED, 501, 334));
    assertThat(v.hovered()).isNotNull();
    v.dispatchEvent(mouse(v, MouseEvent.MOUSE_MOVED, 700, 600));
    assertThat(v.hovered()).isNull();
  }

  @Test
  void clickingAPinOpensItsEntity() {
    var v = natural(List.of(pin(448, "Hogger", 50, 50, PinKind.NPC), pin(448, "Hogger", 50.2, 50, PinKind.NPC)));
    v.dispatchEvent(mouse(v, MouseEvent.MOUSE_CLICKED, 502, 335));
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(EntityKind.NPC, 448)));
    v.dispatchEvent(mouse(v, MouseEvent.MOUSE_CLICKED, 700, 600));
    assertThat(opened).hasSize(1);
  }

  @Test
  void thePagesOwnPinsOpenNothing() {
    var v = natural(List.of(pin(448, "Hogger", 50, 50, PinKind.SELF)));
    v.dispatchEvent(mouse(v, MouseEvent.MOUSE_CLICKED, 501, 334));
    assertThat(opened).isEmpty();
  }

  @Test
  void aClusterOfSeveralEntitiesListsThemInAMenu() {
    var v = natural(List.of(pin(1, "Thor", 20, 20, PinKind.NPC), pin(2, "Boss", 20.3, 20, PinKind.BOSS),
        pin(1, "Thor", 20.1, 20.1, PinKind.NPC)));
    Cluster c = v.clusterAt(200, 134);
    assertThat(c.count()).isEqualTo(3);
    JPopupMenu menu = v.menuFor(c);
    assertThat(menu.getComponents()).extracting(item -> ((JMenuItem) item).getText())
        .containsExactly("Boss", "Thor (2)");
    ((JMenuItem) menu.getComponent(1)).doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(EntityKind.NPC, 1)));
    // clicking the cluster (headless: not showing, so no menu) opens nothing directly
    v.dispatchEvent(mouse(v, MouseEvent.MOUSE_CLICKED, 200, 134));
    assertThat(opened).hasSize(1);
  }

  @Test
  void aMarkersBoundsHoldItsDotRingAndBadgeOnly() {
    List<Pin> twelve = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      twelve.add(pin(i, "P" + i, 50 + i * 0.01, 50, PinKind.NPC));
    }
    var v = natural(twelve);
    Cluster c = v.clusters().get(0);
    assertThat(c.count()).isEqualTo(12);
    var b = v.markerBounds(c);
    int x = (int) c.position().x;
    int y = (int) c.position().y;
    assertThat(b.contains(x, y)).isTrue();
    assertThat(b.contains(x - 7, y + 7)).as("the hover ring").isTrue();
    assertThat(b.contains(x + 15, y - 12)).as("the badge").isTrue();
    assertThat(b.width).isLessThan(60);
    assertThat(b.height).isLessThan(40);
    var single = v.markerBounds(new Cluster(List.of(twelve.get(0)), c.position()));
    assertThat(b.contains(single)).isTrue();
  }
}
