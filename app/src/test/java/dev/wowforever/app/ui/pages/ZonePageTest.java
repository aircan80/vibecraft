package dev.wowforever.app.ui.pages;

import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.OBJECT;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static dev.wowforever.app.ui.pages.Tables.column;
import static dev.wowforever.app.ui.pages.Tables.columnNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.db.SpawnFixtureDb;
import dev.wowforever.app.db.ZoneRepository;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.ObjectSummary;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.model.SpawnPin;
import dev.wowforever.app.model.ZoneDetail;
import dev.wowforever.app.model.ZoneSummary;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.StatusSink;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.widgets.Images;
import dev.wowforever.app.ui.widgets.MapView;
import dev.wowforever.app.ui.widgets.MapView.Pin;
import dev.wowforever.app.ui.widgets.MapView.PinKind;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class ZonePageTest {
  private final Repositories repos = FixtureDb.repositories();
  private static final ZoneSummary ELWYNN = new ZoneSummary(12, "Elwynn Forest", "zone", null, null);

  private static NpcSummary npc(long id, String name, Integer min, Integer max, Integer rank) {
    return new NpcSummary(id, name, null, min, max, rank, "Elwynn Forest", 12L);
  }

  private static ZoneDetail zone(List<NpcSummary> npcs, List<QuestSummary> quests) {
    return new ZoneDetail(ELWYNN, npcs, List.of(), quests, List.of(), List.of());
  }

  @Test
  void levelRangeSpansTheZoneNpcsIgnoringUnknownLevels() {
    var p = new ZonePage(zone(List.of(npc(1, "A", null, null, 0), npc(2, "B", 5, 7, 0), npc(3, "C", 12, null, 0),
        npc(4, "D", 0, 0, 0), npc(5, "E", null, 9, 0)), List.of()), t -> {});
    assertThat(p.header().subtitle()).isEqualTo("Zone #12 · Zone · Level 5–12");
    var none = new ZonePage(zone(List.of(npc(1, "A", null, null, 0)), List.of()), t -> {});
    assertThat(none.header().subtitle()).isEqualTo("Zone #12 · Zone");
  }

  @Test
  void bossesAndRaresComeFirstThenByName() {
    var p = new ZonePage(zone(List.of(npc(1, "Aaa", 5, 5, 0), npc(2, "Zed", 5, 5, 3), npc(3, "Bob", 5, 5, 4),
        npc(4, "Cat", 5, 5, 1), npc(5, "Dan", 5, 5, 2), npc(6, "Abe", 5, 5, null)), List.of()), t -> {});
    var npcs = p.section("NPCs").table().table();
    assertThat(column(npcs, 0)).containsExactly("Zed", "Dan", "Bob", "Cat", "Aaa", "Abe");
    // every NPC is in this zone: no Zone column; nobody has a title: no Title column
    assertThat(columnNames(npcs)).containsExactly("Name", "Level", "Rank");
  }

  @Test
  void questsAreSortedByLevelUnknownLast() {
    var p = new ZonePage(zone(List.of(), List.of(new QuestSummary(1, "A", null, null, "Elwynn Forest"),
        new QuestSummary(2, "B", 12, 10, "Elwynn Forest"), new QuestSummary(3, "C", 5, 1, "Elwynn Forest"),
        new QuestSummary(4, "Aa", 12, 10, "Elwynn Forest"))), t -> {});
    assertThat(column(p.section("Quests").table().table(), 0)).containsExactly("C", "Aa", "B", "A");
  }

  @Test
  void instancesAndSubZonesAreLinksAtTheTop() {
    List<ViewTarget> opened = new ArrayList<>();
    var p = new ZonePage(repos.zones().detail(40).get(), opened::add);
    assertThat(p.instanceLinks()).extracting(JButton::getText).containsExactly("The Deadmines");
    assertThat(p.subzoneLinks()).extracting(JButton::getText).containsExactly("The Deadmines");
    p.instanceLinks().get(0).doClick();
    p.subzoneLinks().get(0).doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(INSTANCE, 2)),
        new ViewTarget.Entity(new EntityRef(ZONE, 1581)));
    assertThat(p.sections()).extracting(SourceSection::title).containsExactly("Quests");
  }

  @Test
  void headerLinksTheParentZoneAndAZoneWithoutAMapHasNoMapArea() {
    List<ViewTarget> opened = new ArrayList<>();
    var p = new ZonePage(repos.zones().detail(1581).get(), opened::add);
    assertThat(p.header().title()).isEqualTo("The Deadmines");
    assertThat(p.header().subtitle()).isEqualTo("Zone #1581 · Dungeon · Level 21 · Westfall");
    p.header().links().get(0).doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(ZONE, 40)));
    assertThat(p.mapPanel()).isNull();
  }

  /** A pin source over the fixture with Westfall spawns that records each call. */
  private static final class FakePins implements ZoneMapPanel.PinSource {
    final ZoneRepository zones = SpawnFixtureDb.repositories().zones();
    final List<String> calls = new ArrayList<>();

    @Override
    public List<SpawnPin> npcs(long zoneId, boolean bossesAndRaresOnly) {
      calls.add("npcs " + zoneId + " " + bossesAndRaresOnly);
      return zones.npcSpawnsInZone(zoneId, bossesAndRaresOnly);
    }

    @Override
    public List<SpawnPin> objects(long zoneId) {
      calls.add("objects " + zoneId);
      return List.of(new SpawnPin(new EntityRef(OBJECT, 7), "Chest", null, 40, 40));
    }
  }

  private final ZoneRepository spawnZones = SpawnFixtureDb.repositories().zones();
  private final ZoneDetail westfall = spawnZones.detail(40).orElseThrow();
  private final BufferedImage westfallMap = Images.decode(westfall.map().orElseThrow().jpeg()).orElseThrow();

  /** The status messages and failures a page reports. */
  private static final class Status implements StatusSink {
    final List<String> messages = new ArrayList<>();
    final List<Exception> failures = new ArrayList<>();

    @Override
    public void message(String text) {
      messages.add(text);
    }

    @Override
    public void fail(Exception e) {
      failures.add(e);
    }
  }

  private final Status status = new Status();

  private ZonePage westfallPage(ZoneMapPanel.PinSource pins, Executor worker, List<ViewTarget> opened) {
    return new ZonePage(westfall, new ZoneMapPanel(40, westfallMap, spawnZones.npcSpawnsInZone(40, true), pins,
        worker, status, opened::add), opened::add);
  }

  @Test
  void mapShownWithPins() {
    var pins = new FakePins();
    List<ViewTarget> opened = new ArrayList<>();
    var p = westfallPage(pins, Runnable::run, opened);
    MapView map = p.mapPanel().mapView();
    assertThat(map.image()).isSameAs(westfallMap);
    // the map replaces the reserved area: below the header and link rows, above the sections
    List<Component> top = List.of(p.mapPanel().getParent().getComponents());
    assertThat(top.indexOf(p.mapPanel())).isGreaterThan(top.indexOf(p.header()))
        .isLessThan(top.indexOf(p.sections().get(0)));
    // by default the bosses and rares, loaded with the page
    assertThat(map.pins()).extracting(Pin::name, Pin::kind).containsExactlyInAnyOrder(
        tuple("Foe Reaper 4000", PinKind.RARE), tuple("Vultros", PinKind.RARE), tuple("Test Boss", PinKind.BOSS));
    assertThat(map.pins()).filteredOn(pin -> pin.name().equals("Vultros")).singleElement()
        .satisfies(pin -> assertThat(pin.x()).isEqualTo(61.2));
    assertThat(map.bossesAndRaresOnly()).isTrue();
    ZoneMapPanel panel = p.mapPanel();
    assertThat(List.of(panel.bossesToggle(), panel.allNpcsToggle(), panel.objectsToggle()))
        .extracting(JToggleButton::getText)
        .containsExactly("Bosses & rares", "All NPCs", "Objects");
    assertThat(p.mapPanel().bossesToggle().isSelected()).isTrue();
    assertThat(pins.calls).isEmpty();

    p.mapPanel().allNpcsToggle().doClick();
    assertThat(pins.calls).containsExactly("npcs 40 false");
    assertThat(status.messages).containsExactly(ZoneMapPanel.LOADING, "4 pins on the map");
    assertThat(p.mapPanel().bossesToggle().isSelected()).isFalse();
    assertThat(map.bossesAndRaresOnly()).isFalse();
    assertThat(map.visiblePins()).extracting(Pin::name)
        .containsExactlyInAnyOrder("Foe Reaper 4000", "Vultros", "Test Boss", "Thor");

    p.mapPanel().bossesToggle().doClick(); // filters the NPCs already loaded
    assertThat(map.visiblePins()).extracting(Pin::name).doesNotContain("Thor").hasSize(3);
    assertThat(pins.calls).hasSize(1);

    p.mapPanel().objectsToggle().doClick();
    assertThat(pins.calls).containsExactly("npcs 40 false", "objects 40");
    assertThat(map.visiblePins()).extracting(Pin::name, Pin::kind).contains(tuple("Chest", PinKind.OBJECT));
    p.mapPanel().objectsToggle().doClick();
    assertThat(map.visiblePins()).extracting(Pin::kind).doesNotContain(PinKind.OBJECT);
    p.mapPanel().objectsToggle().doClick();
    assertThat(pins.calls).as("loaded once").hasSize(2);

    // a click on a pin opens its NPC: Vultros is drawn at 61.2 %, 65.9 % of the map
    map.setSize(1002, 668);
    map.dispatchEvent(new MouseEvent(map, MouseEvent.MOUSE_CLICKED, 0L, 0, 613, 440, 1, false, MouseEvent.BUTTON1));
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(NPC, 900002)));
  }

  @Test
  void togglesLoadOnTheWorkerAndDropStaleResults() {
    var pins = new FakePins();
    List<Runnable> queued = new ArrayList<>();
    var p = westfallPage(pins, queued::add, new ArrayList<>());
    p.mapPanel().allNpcsToggle().doClick();
    p.mapPanel().objectsToggle().doClick();
    assertThat(pins.calls).as("nothing runs on the calling thread").isEmpty();
    assertThat(queued).hasSize(2);
    assertThat(p.mapPanel().mapView().pins()).hasSize(3);
    queued.get(0).run(); // superseded by the second load: dropped
    assertThat(p.mapPanel().mapView().pins()).hasSize(3);
    queued.get(1).run(); // loads both what the first would have and the objects
    assertThat(pins.calls).containsExactly("npcs 40 false", "npcs 40 false", "objects 40");
    assertThat(p.mapPanel().mapView().visiblePins()).extracting(Pin::name).contains("Thor", "Chest");
  }

  @Test
  void theCompactWindowCollapsesTheTogglesIntoAPinsMenu() {
    var pins = new FakePins();
    var p = westfallPage(pins, Runnable::run, new ArrayList<>());
    assertThat(p.mapPanel().bossesToggle().getParent().isVisible()).isTrue();
    assertThat(p.mapPanel().pinsMenuButton().isVisible()).isFalse();
    JPanel compact = new JPanel();
    compact.putClientProperty(CompactColumns.KEY, Boolean.TRUE);
    compact.add(p);
    assertThat(p.mapPanel().mapView()).isNotNull();
    assertThat(p.mapPanel().bossesToggle().getParent().isVisible()).isFalse();
    assertThat(p.mapPanel().pinsMenuButton().isVisible()).isTrue();
    assertThat(p.mapPanel().pinsMenuButton().getText()).isEqualTo("Pins \u25BE");
    assertThat(p.mapPanel().pinsMenu().getComponents()).extracting(c -> ((JMenuItem) c).getText())
        .containsExactly("Bosses & rares", "All NPCs", "Objects");
    ((JMenuItem) p.mapPanel().pinsMenu().getComponent(1)).doClick();
    assertThat(p.mapPanel().allNpcsToggle().isSelected()).isTrue();
    assertThat(((JMenuItem) p.mapPanel().pinsMenu().getComponent(0)).isSelected()).isFalse();
    assertThat(pins.calls).containsExactly("npcs 40 false");
    ((JMenuItem) p.mapPanel().pinsMenu().getComponent(2)).doClick();
    assertThat(p.mapPanel().objectsToggle().isSelected()).isTrue();
    assertThat(p.mapPanel().mapView().visiblePins()).extracting(Pin::name).contains("Thor", "Chest");
    compact.remove(p);
    assertThat(p.mapPanel().bossesToggle().getParent().isVisible()).isTrue();
    assertThat(p.mapPanel().pinsMenuButton().isVisible()).isFalse();
  }

  @Test
  void anEmptyZoneHasNoSectionsOrLinks() {
    var p = new ZonePage(new ZoneDetail(new ZoneSummary(5, null, null, 77L, null), List.of(), List.of(), List.of(),
        List.of(), List.of()), t -> {});
    assertThat(p.header().title()).isEqualTo("Unknown zone #5");
    assertThat(p.header().subtitle()).isEqualTo("Zone #5 · Zone · Unknown zone #77");
    assertThat(p.sections()).isEmpty();
    assertThat(p.instanceLinks()).isEmpty();
    assertThat(p.subzoneLinks()).isEmpty();
  }

  @Test
  void objectsHaveTheirOwnSection() {
    var d = new ZoneDetail(ELWYNN, List.of(), List.of(new ObjectSummary(7, "Chest", "Elwynn Forest", 12L)),
        List.of(), List.of(), List.of());
    var p = new ZonePage(d, t -> {});
    assertThat(p.sections()).extracting(SourceSection::title).containsExactly("Objects");
    assertThat(columnNames(p.section("Objects").table().table())).containsExactly("Name");
  }

  @Test
  void aFailedLoadIsReportedAndItsToggleGoesBack() {
    var failing = new ZoneMapPanel.PinSource() {
      @Override
      public List<SpawnPin> npcs(long zoneId, boolean bossesAndRaresOnly) {
        throw new IllegalStateException("database is locked");
      }

      @Override
      public List<SpawnPin> objects(long zoneId) {
        throw new IllegalStateException("no objects");
      }
    };
    List<Runnable> queued = new ArrayList<>();
    var p = westfallPage(failing, queued::add, new ArrayList<>());
    ZoneMapPanel panel = p.mapPanel();
    panel.allNpcsToggle().doClick();
    assertThat(status.messages).containsExactly(ZoneMapPanel.LOADING);
    assertThat(panel.allNpcsToggle().isSelected()).as("on while loading").isTrue();
    queued.remove(0).run();
    assertThat(status.failures).singleElement().satisfies(e -> assertThat(e).hasMessage("database is locked"));
    assertThat(panel.allNpcsToggle().isSelected()).isFalse();
    assertThat(panel.bossesToggle().isSelected()).isTrue();
    assertThat(panel.mapView().bossesAndRaresOnly()).isTrue();
    assertThat(panel.mapView().pins()).hasSize(3);
    assertThat(queued).as("going back loads nothing").isEmpty();

    panel.objectsToggle().doClick();
    queued.remove(0).run();
    assertThat(status.failures).hasSize(2);
    assertThat(panel.objectsToggle().isSelected()).isFalse();
    assertThat(panel.mapView().pins()).hasSize(3);

    // the same on the calling thread (the failure arrives while the toggle is still changing)
    var inline = westfallPage(failing, Runnable::run, new ArrayList<>()).mapPanel();
    inline.allNpcsToggle().doClick();
    assertThat(inline.bossesToggle().isSelected()).isTrue();
    assertThat(inline.mapView().bossesAndRaresOnly()).isTrue();
    assertThat(status.failures).hasSize(3);
  }
}
