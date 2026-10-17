package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.tuple;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.QuestPin;
import dev.wowforever.app.model.QuestRef;
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

  @Test
  void focusDimsGiversAndHidesOtherObjectives() {
    var view = new MapView(new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB), opened::add);
    view.setSize(100, 100);
    EntityRef ref = new EntityRef(EntityKind.NPC, 1);
    view.setPins(List.of(Pin.giver(ref, "Giver", 50, 50, true),
        Pin.objective(ref, "Hidden", 20, 20, PinKind.KILL, 1),
        Pin.objective(ref, "Shown", 80, 80, PinKind.KILL, 2)));
    view.setFocus(2);
    assertThat(view.visiblePins()).hasSize(2).noneMatch(p -> p.number() == 1);
    BufferedImage image = painted(view);
    assertThat(image.getRGB(57, 50) >>> 24).isBetween(126, 130);
  }

  @Test
  void questPageGiverIsAGoldDisc() {
    Theme saved = Theme.current();
    try {
      for (Theme theme : new Theme[] {dev.wowforever.app.ui.theme.Tokens.DARK,
          dev.wowforever.app.ui.theme.Tokens.PARCHMENT}) {
        Theme.setCurrent(theme);
        BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        PinGlyphs.paintDisc(g, PinGlyphs.font(new java.awt.Font("SansSerif", 0, 12), true),
            PinKind.QUEST_START, 25, 25);
        g.dispose();
        assertThat(image.getRGB(32, 25) & 0xFFFFFF).isEqualTo(0xFFD100);
        assertThat(paintedNear(image, 25, 25, 0x0C1424)).isTrue();
      }
    } finally { Theme.setCurrent(saved); }
  }

  @Test
  void objectiveShapes() {
    for (PinKind kind : List.of(PinKind.KILL, PinKind.COLLECT, PinKind.INTERACT, PinKind.EXPLORE)) {
      BufferedImage image = new BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      PinGlyphs.paintObjective(g, new java.awt.Font("SansSerif", java.awt.Font.BOLD, 10), kind, 3, 30, 30, 9);
      g.dispose();
      boolean outline = false, halo = false, fill = false;
      for (int x = 15; x < 45; x++) for (int y = 15; y < 45; y++) {
        fill |= image.getRGB(x, y) == MapView.colour(kind).getRGB();
        outline |= image.getRGB(x, y) == PinGlyphs.OUTLINE.getRGB();
        halo |= image.getRGB(x, y) == PinGlyphs.HALO.getRGB();
      }
      assertThat(fill).as(kind + " fill").isTrue();
      assertThat(outline).as(kind + " outline").isTrue();
      assertThat(halo).as(kind + " halo").isTrue();
      if (kind == PinKind.EXPLORE) assertThat(image.getRGB(30, 30) >>> 24).isZero();
      if (kind == PinKind.COLLECT) assertThat(image.getRGB(22, 22) >>> 24).isZero();
    }
    BufferedImage three = new BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB);
    BufferedImage eight = new BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB);
    Graphics2D a = three.createGraphics(), b = eight.createGraphics();
    java.awt.Font font = new java.awt.Font("SansSerif", java.awt.Font.BOLD, 10);
    PinGlyphs.paintObjective(a, font, PinKind.KILL, 3, 30, 30, 9);
    PinGlyphs.paintObjective(b, font, PinKind.KILL, 8, 30, 30, 9);
    a.dispose(); b.dispose();
    assertThat(three.getRGB(20, 20, 20, 20, null, 0, 20))
        .isNotEqualTo(eight.getRGB(20, 20, 20, 20, null, 0, 20));
  }

  @Test
  void cursorCoordinatesInvertTheProjection() {
    java.util.Random random = new java.util.Random(8);
    for (double scale : new double[] {1, 1.5}) {
      for (int width : new int[] {640, 1002}) {
        Projection projection = new Projection(1002, 668, width, scale);
        for (int i = 0; i < 20; i++) {
          double x = random.nextDouble() * 100, y = random.nextDouble() * 100;
          Point2D.Double point = projection.toComponent(x, y);
          var coordinates = projection.toMap(Math.round(point.x), Math.round(point.y)).orElseThrow();
          assertThat(coordinates.x).isCloseTo(x, org.assertj.core.data.Offset.offset(0.2));
          assertThat(coordinates.y).isCloseTo(y, org.assertj.core.data.Offset.offset(0.2));
        }
      }
    }
  }

  @Test
  void pointerBelowTheImageHasNoCoordinates() {
    var view = natural(List.of());
    view.setSize(1002, 800);
    int below = view.projection().drawnHeight() + 5;
    assertThat(view.coordinatesAt(20, below)).isEmpty();
    view.dispatchEvent(mouse(view, MouseEvent.MOUSE_MOVED, 20, below));
    assertThat(view.coordinatesText()).isEmpty();
    assertThat(view.contextMenuAt(20, below).getComponentCount()).isZero();
  }

  @Test
  void rightClickCopiesWay() {
    var view = natural(List.of());
    view.setZoneName("Elwynn Forest");
    List<String> copied = new ArrayList<>();
    view.setClipboard(copied::add);
    var point = view.projection().toComponent(42.1, 65.9);
    JMenuItem item = (JMenuItem) view.contextMenuAt((int)Math.round(point.x), (int)Math.round(point.y)).getComponent(0);
    assertThat(item.getText()).isEqualTo("Copy /way Elwynn Forest 42.1 65.9");
    item.doClick();
    assertThat(copied).containsExactly("/way Elwynn Forest 42.1 65.9");
  }

  @Test
  void objectivePinsClusterPerObjective() {
    EntityRef ref = new EntityRef(EntityKind.NPC, 1);
    var view = natural(List.of(Pin.objective(ref, "A", 50, 50, PinKind.KILL, 1),
        Pin.objective(ref, "A", 50.1, 50, PinKind.KILL, 1),
        Pin.objective(ref, "B", 50, 50, PinKind.KILL, 2),
        Pin.giver(ref, "Giver", 50, 50, true)));
    assertThat(view.clusters()).extracting(Cluster::count).containsExactly(1, 2, 1);
    assertThat(view.clusterAt(501, 334).kind()).isEqualTo(PinKind.QUEST_START);
    view.setFocus(2);
    assertThat(view.visiblePins()).hasSize(2).noneMatch(p -> p.number() == 1);
  }

  @Test
  void zoneCaptionShown() {
    var view = natural(List.of(pin(1, "A", 10, 10, PinKind.NPC),
        pin(2, "B", 20, 20, PinKind.NPC), pin(3, "C", 30, 30, PinKind.NPC)));
    view.setZoneName("Westfall");
    assertThat(view.getAccessibleContext().getAccessibleDescription()).isEqualTo("Westfall, 3 pins");
    BufferedImage painted = new BufferedImage(1002, 668, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = painted.createGraphics(); view.paint(g); g.dispose();
    assertThat(painted.getRGB(10, 10) & 0xFFFFFF).isEqualTo(0x0C1424);
  }

  @Test
  void theMapAsksForALayoutWhenItsHeightNoLongerFollowsItsWidthWithoutBouncingAScrollbar() {
    var v = new MapView(westfall, opened::add);
    int narrow = 379; // the page with its vertical scrollbar
    int wide = 396; // and without
    assertThat(v.heightFor(wide)).isGreaterThan(v.heightFor(narrow));
    assertThat(v.needsLayout(narrow, v.heightFor(westfall.getWidth()))).as("first measured at the image's width")
        .isTrue();
    assertThat(v.needsLayout(narrow, v.heightFor(narrow))).as("its height follows its width").isFalse();
    assertThat(v.needsLayout(wide, v.heightFor(narrow))).as("shorter than its width wants").isTrue();
    // that layout brought the scrollbar back: asking again would take it away and so on for ever
    assertThat(v.needsLayout(narrow, v.heightFor(wide))).as("the bounce").isFalse();
    assertThat(v.needsLayout(300, v.heightFor(narrow))).as("then the window narrows").isTrue();
    assertThat(v.needsLayout(300, v.heightFor(300))).isFalse();
    assertThat(v.needsLayout(250, v.heightFor(300))).as("and narrows again").isTrue();
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
  void clickingASharedGiverPresentsBothRolesBeforeNavigating() {
    List<JPopupMenu> shown = new ArrayList<>();
    EntityRef gryan = new EntityRef(EntityKind.NPC, 234);
    var view = new MapView(westfall, opened::add, (menu, point) -> shown.add(menu));
    view.setSize(1002, 668);
    view.setPins(List.of(Pin.giver(gryan, "Gryan — Given by", 50, 50, true),
        Pin.giver(gryan, "Gryan — Turned in to", 50, 50, false)));
    view.dispatchEvent(mouse(view, MouseEvent.MOUSE_CLICKED, 501, 334));
    assertThat(opened).isEmpty();
    assertThat(shown).hasSize(1);
    JPopupMenu menu = shown.getFirst();
    assertThat(menu.getComponentCount()).isEqualTo(2);
    assertThat(((JMenuItem) menu.getComponent(0)).getText()).isEqualTo("Gryan — Given by");
    assertThat(((JMenuItem) menu.getComponent(1)).getText()).isEqualTo("Gryan — Turned in to");
    ((JMenuItem) menu.getComponent(1)).doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(gryan));
  }

  @Test
  void clickingDuplicateSpawnsOfOneGiverRoleStillNavigatesDirectly() {
    List<JPopupMenu> shown = new ArrayList<>();
    EntityRef gryan = new EntityRef(EntityKind.NPC, 234);
    var view = new MapView(westfall, opened::add, (menu, point) -> shown.add(menu));
    view.setSize(1002, 668);
    view.setPins(List.of(Pin.giver(gryan, "Gryan — Given by", 50, 50, true),
        Pin.giver(gryan, "Gryan — Given by", 50.1, 50, true)));
    view.dispatchEvent(mouse(view, MouseEvent.MOUSE_CLICKED, 501, 334));
    assertThat(shown).isEmpty();
    assertThat(opened).containsExactly(new ViewTarget.Entity(gryan));
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

  private static final EntityRef DUGHAN = new EntityRef(EntityKind.NPC, 240);
  private static final QuestRef HOGGER = new QuestRef(176, "Wanted: Hogger", 11, true);
  private static final QuestRef BUNDLE = new QuestRef(5, "A Bundle of Trouble", 5, true);
  private static final QuestRef REPORT = new QuestRef(9, "Report to Gryan", 10, false);

  private static Pin questPin(double x, double y, QuestRef... quests) {
    return Pin.of(new QuestPin(DUGHAN, "Marshal Dughan", x, y, List.of(quests)));
  }

  private static BufferedImage painted(MapView v) {
    BufferedImage canvas = new BufferedImage(v.getWidth(), v.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = canvas.createGraphics();
    v.paint(g);
    g.dispose();
    return canvas;
  }

  /**
   * Whether a pixel within 2 px of {@code x}, {@code y} is {@code rgb}, give or take {@value #BLEND} per channel (a
   * glyph's stem may cover no pixel whole, so its pixels blend a little with the outline).
   */
  private static boolean paintedNear(BufferedImage canvas, int x, int y, int rgb) {
    for (int dx = -2; dx <= 2; dx++) {
      for (int dy = -2; dy <= 2; dy++) {
        int p = canvas.getRGB(x + dx, y + dy);
        boolean close = true;
        for (int shift = 0; shift <= 16; shift += 8) {
          close &= Math.abs((p >> shift & 0xFF) - (rgb >> shift & 0xFF)) <= BLEND;
        }
        if (close) {
          return true;
        }
      }
    }
    return false;
  }

  private static final int BLEND = 24;

  @Test
  void questPinsTakeTheirKindFromTheirQuestsStartsWinning() {
    assertThat(questPin(1, 2, HOGGER).kind()).isEqualTo(PinKind.QUEST_START);
    assertThat(questPin(1, 2, REPORT).kind()).isEqualTo(PinKind.QUEST_END);
    Pin both = questPin(1, 2, REPORT, HOGGER);
    assertThat(both.kind()).isEqualTo(PinKind.QUEST_START);
    assertThat(both.quests()).as("starts first").containsExactly(HOGGER, REPORT);
    assertThat(both.ref()).isEqualTo(DUGHAN);
    assertThat(both.name()).isEqualTo("Marshal Dughan");
    assertThat(both.emphasised()).isFalse();
    assertThat(PinKind.QUEST_START.quest()).isTrue();
    assertThat(PinKind.SELF.quest()).isFalse();
  }

  @Test
  void questGlyphsArePaintedInTheirColoursAtTheirPins() {
    var v = new MapView(westfall, opened::add);
    v.setSize(501, 334);
    v.setPins(List.of(questPin(50, 50, HOGGER), questPin(20, 20, REPORT)));
    BufferedImage canvas = painted(v);
    // 50 % is (250.5, 167), 20 % (100.2, 66.8)
    assertThat(paintedNear(canvas, 250, 167, 0xFFD100)).as("the yellow !").isTrue();
    assertThat(paintedNear(canvas, 100, 67, 0xB8B8B8)).as("the grey ?").isTrue();
    assertThat(paintedNear(canvas, 100, 67, 0xFFD100)).isFalse();
    assertThat(paintedNear(canvas, 400, 280, 0xFFD100)).as("nowhere else").isFalse();
  }

  @Test
  void questPinsClusterOnlyWithQuestPinsAndAreOnTop() {
    // the NPC at (501, 334), Dughan's pin 4 px and Gryan's 2 px right of it
    Pin gryan = Pin.of(new QuestPin(new EntityRef(EntityKind.NPC, 234), "Gryan Stoutmantle", 50.2, 50,
        List.of(BUNDLE)));
    var v = natural(List.of(pin(1, "Hogger", 50, 50, PinKind.NPC), questPin(50.4, 50, HOGGER), gryan));
    assertThat(v.clusters()).extracting(Cluster::count, Cluster::kind)
        .containsExactly(tuple(2, PinKind.QUEST_START), tuple(1, PinKind.NPC));
    assertThat(MapView.cluster(v.pins(), new Projection(1002, 668, 1002), 8)).hasSize(2);
    // the quest layer is hit first, even where the NPC's marker is nearer
    assertThat(v.clusterAt(501, 334).kind()).isEqualTo(PinKind.QUEST_START);
    assertThat(v.clusterAt(495, 334).kind()).as("10 px from the quest marker").isEqualTo(PinKind.NPC);
    // and painted over the NPC's dot
    BufferedImage canvas = painted(v);
    assertThat(paintedNear(canvas, (int) Math.round(0.504 * 1002), 334, 0xFFD100)).isTrue();
    // a cluster of quest pins lists its givers with their quests
    assertThat(v.getToolTipText(mouse(v, MouseEvent.MOUSE_MOVED, 503, 334))).isEqualTo(
        "<html>2 pins<br>Marshal Dughan — Wanted: Hogger (11)<br>Gryan Stoutmantle — A Bundle of Trouble (5)"
            + "</html>");
    JPopupMenu menu = v.menuFor(v.clusterAt(503, 334));
    assertThat(menu.getComponents()).extracting(item -> ((JMenuItem) item).getText()).containsExactly(
        "Wanted: Hogger (11)", "A Bundle of Trouble (5)", "Giver: Marshal Dughan", "Giver: Gryan Stoutmantle");
  }

  @Test
  void clickingAPinOfOneQuestOpensTheQuest() {
    var v = natural(List.of(questPin(50, 50, HOGGER)));
    assertThat(v.getToolTipText(mouse(v, MouseEvent.MOUSE_MOVED, 501, 334)))
        .isEqualTo("Marshal Dughan — Wanted: Hogger (11)");
    v.dispatchEvent(mouse(v, MouseEvent.MOUSE_CLICKED, 501, 334));
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(EntityKind.QUEST, 176)));
  }

  @Test
  void aPinOfSeveralQuestsListsThemAndItsGiverInAMenu() {
    var v = natural(List.of(questPin(50, 50, HOGGER, BUNDLE)));
    assertThat(v.getToolTipText(mouse(v, MouseEvent.MOUSE_MOVED, 501, 334)))
        .isEqualTo("Marshal Dughan — Wanted: Hogger (11), A Bundle of Trouble (5)");
    Cluster c = v.clusterAt(501, 334);
    JPopupMenu menu = v.menuFor(c);
    assertThat(menu.getComponents()).extracting(item -> ((JMenuItem) item).getText())
        .containsExactly("Wanted: Hogger (11)", "A Bundle of Trouble (5)", "Giver: Marshal Dughan");
    ((JMenuItem) menu.getComponent(1)).doClick();
    ((JMenuItem) menu.getComponent(2)).doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(EntityKind.QUEST, 5)),
        new ViewTarget.Entity(DUGHAN));
    // clicking the pin (headless: not showing, so no menu) opens nothing directly
    v.dispatchEvent(mouse(v, MouseEvent.MOUSE_CLICKED, 501, 334));
    assertThat(opened).hasSize(2);
  }

  @Test
  void aGiverThatStartsAndEndsQuestsListsItsStartsFirst() {
    var v = natural(List.of(questPin(50, 50, REPORT, HOGGER)));
    assertThat(v.getToolTipText(mouse(v, MouseEvent.MOUSE_MOVED, 501, 334))).isEqualTo(
        "<html>Marshal Dughan<br>Starts: Wanted: Hogger (11)<br>Ends: Report to Gryan (10)</html>");
    assertThat(v.menuFor(v.clusterAt(501, 334)).getComponents()).extracting(item -> ((JMenuItem) item).getText())
        .containsExactly("Wanted: Hogger (11)", "Report to Gryan (10)", "Giver: Marshal Dughan");
    BufferedImage canvas = painted(natural(v.pins()));
    assertThat(paintedNear(canvas, 501, 334, 0xFFD100)).as("a ! where starts and ends meet").isTrue();
  }

  @Test
  void aLongQuestListWrapsInItsTooltip() {
    List<QuestRef> many = new ArrayList<>();
    for (int i = 1; i <= 8; i++) {
      many.add(new QuestRef(i, "The Jasperlode Mine " + i, 10, true));
    }
    var v = natural(List.of(Pin.of(new QuestPin(DUGHAN, "Marshal Dughan", 50, 50, many))));
    String tip = v.getToolTipText(mouse(v, MouseEvent.MOUSE_MOVED, 501, 334));
    assertThat(tip).startsWith("<html><body style='width: ").endsWith("</body></html>")
        .contains("Marshal Dughan — The Jasperlode Mine 1 (10), ").contains("The Jasperlode Mine 8 (10)");
  }

  @Test
  void aMarkerOfManyGiversCapsItsTooltipAndItsGiverEntries() {
    List<Pin> givers = new ArrayList<>();
    for (int i = 1; i <= 15; i++) {
      givers.add(Pin.of(new QuestPin(new EntityRef(EntityKind.NPC, 1000 + i), "Giver " + i, 50, 50,
          List.of(new QuestRef(2000 + i, "Errand " + i, 10, true)))));
    }
    var v = natural(givers);
    String tip = v.getToolTipText(mouse(v, MouseEvent.MOUSE_MOVED, 501, 334));
    assertThat(tip.split("<br>")).hasSize(1 + QuestPinText.TOOLTIP_LINES + 1);
    assertThat(tip).startsWith("<html>15 pins<br>Giver 1 — Errand 1 (10)").endsWith("<br>+3 more</html>");
    List<String> items = new ArrayList<>();
    for (var item : v.menuFor(v.clusterAt(501, 334)).getComponents()) {
      items.add(((JMenuItem) item).getText());
    }
    assertThat(items).filteredOn(t -> t.startsWith("Errand")).hasSize(15);
    assertThat(items).filteredOn(t -> t.startsWith("Giver: ")).hasSize(MapView.GIVER_CAP);
    assertThat(items).last().as("the givers cut off").isEqualTo("…");
  }

  @Test
  void anEmphasisedGiverPinOpensTheGiverAndIsDrawnLarger() {
    Pin giver = Pin.giver(DUGHAN, "Marshal Dughan", 50, 50, true);
    assertThat(giver.kind()).isEqualTo(PinKind.QUEST_START);
    assertThat(giver.emphasised()).isTrue();
    assertThat(giver.quests()).isEmpty();
    assertThat(Pin.giver(DUGHAN, "Marshal Dughan", 50, 50, false).kind()).isEqualTo(PinKind.QUEST_END);
    var v = natural(List.of(giver));
    assertThat(v.getToolTipText(mouse(v, MouseEvent.MOUSE_MOVED, 501, 334))).isEqualTo("Marshal Dughan");
    v.dispatchEvent(mouse(v, MouseEvent.MOUSE_CLICKED, 501, 334));
    assertThat(opened).containsExactly(new ViewTarget.Entity(DUGHAN));
    Cluster large = v.clusters().get(0);
    Cluster plain = new Cluster(List.of(questPin(50, 50, HOGGER)), large.position());
    assertThat(v.markerBounds(large).contains(v.markerBounds(plain))).isTrue();
    assertThat(v.markerBounds(large).width).isGreaterThan(v.markerBounds(plain).width);
  }
}
