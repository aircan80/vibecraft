package dev.wowforever.app.ui.pages;

import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static dev.wowforever.app.ui.pages.Tables.column;
import static dev.wowforever.app.ui.pages.Tables.columnNames;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.ObjectSummary;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.model.ZoneDetail;
import dev.wowforever.app.model.ZoneSummary;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
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
  void headerLinksTheParentZoneAndAMapAreaIsReserved() {
    List<ViewTarget> opened = new ArrayList<>();
    var p = new ZonePage(repos.zones().detail(1581).get(), opened::add);
    assertThat(p.header().title()).isEqualTo("The Deadmines");
    assertThat(p.header().subtitle()).isEqualTo("Zone #1581 · Dungeon · Level 21 · Westfall");
    p.header().links().get(0).doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(ZONE, 40)));
    assertThat(p.mapPlaceholder().getPreferredSize().height).isPositive();
    assertThat(p.mapPlaceholder().getComponentCount()).isZero();
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
}
