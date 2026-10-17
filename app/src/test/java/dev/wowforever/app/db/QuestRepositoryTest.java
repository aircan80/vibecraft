package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.QuestChain;
import dev.wowforever.app.model.QuestChain.Step;
import dev.wowforever.app.model.QuestFilter;
import dev.wowforever.app.model.QuestGiver;
import dev.wowforever.app.model.QuestObjective;
import dev.wowforever.app.model.QuestRelation;
import dev.wowforever.app.model.QuestSourceItem;
import dev.wowforever.app.model.QuestSummary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FixtureDb.class)
class QuestRepositoryTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void involvingMergesGiverRoles(@TempDir Path tmp) throws Exception {
    Path copy = tmp.resolve("merged.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement st = conn.createStatement()) {
      st.executeUpdate("insert into quest_giver(quest_id,kind,entity_id,role) values (2,'npc',999999,'start'),(2,'npc',999999,'end')");
    }
    try (Database db = Database.open(copy)) {
      var result = Repositories.of(db).quests().involving(new dev.wowforever.app.model.EntityRef(EntityKind.NPC, 999999));
      assertThat(result).singleElement().satisfies(r -> {
        assertThat(r.quest().id()).isEqualTo(2);
        assertThat(r.roles()).containsExactlyInAnyOrder(
            dev.wowforever.app.model.QuestInvolvement.Role.STARTS, dev.wowforever.app.model.QuestInvolvement.Role.ENDS);
        assertThat(r.glyphs()).isEqualTo("! ?");
        assertThat(r.rolesText()).isEqualTo("Starts · Ends");
      });
    }
  }

  @Test
  void involvingMergesRepeatedItemSourcesAndSortsQuests(@TempDir Path tmp) throws Exception {
    Path copy = tmp.resolve("source-roles.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement st = conn.createStatement()) {
      st.executeUpdate("insert into item(id,name) values (99001,'Alpha item'),(99002,'Beta item')");
      st.executeUpdate("insert into quest(id,name,quest_level) values (99001,'Late',20),(99002,'Early',10)");
      st.executeUpdate("insert into quest_giver(quest_id,kind,entity_id,role) values (99001,'npc',999995,'start'),(99002,'npc',999995,'end')");
      st.executeUpdate("insert into quest_objective(quest_id,ordinal,kind,entity_id) values (99001,0,'creature',999995),(99001,1,'item',99001),(99001,2,'item',99002),(99001,3,'item',99001)");
      st.executeUpdate("insert into item_npc_drop(item_id,npc_id,origin) values (99001,999995,'questie'),(99002,999995,'questie')");
    }
    try (Database db = Database.open(copy)) {
      var rows = Repositories.of(db).quests().involving(new dev.wowforever.app.model.EntityRef(EntityKind.NPC, 999995));
      assertThat(rows).extracting(r -> r.quest().id()).containsExactly(99002L, 99001L);
      assertThat(rows.getLast().roles()).containsExactlyInAnyOrder(
          dev.wowforever.app.model.QuestInvolvement.Role.STARTS,
          dev.wowforever.app.model.QuestInvolvement.Role.OBJECTIVE,
          dev.wowforever.app.model.QuestInvolvement.Role.DROPS_OBJECTIVE_ITEM);
      assertThat(rows.getLast().viaItemName()).isEqualTo("Alpha item, Beta item");
      assertThat(rows.getLast().rolesText(EntityKind.NPC))
          .isEqualTo("Starts · Kill objective · Drops Alpha item, Beta item");
    }
  }

  @Test
  void involvingCapsOnlyAboveFiveHundred(@TempDir Path tmp) throws Exception {
    Path copy = tmp.resolve("involving.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement st = conn.createStatement()) {
      for (int i = 0; i < 500; i++) {
        st.executeUpdate("insert into quest(id,name,quest_level) values (" + (98000+i) + ",'Cap Quest " + i + "',10)");
        st.executeUpdate("insert into quest_giver(quest_id,kind,entity_id,role) values (" + (98000+i) + ",'npc',999999,'start'),(" + (98000+i) + ",'npc',999999,'end')");
      }
    }
    try (Database db = Database.open(copy)) {
      var result = Repositories.of(db).quests().involvingResult(new dev.wowforever.app.model.EntityRef(EntityKind.NPC, 999999));
      assertThat(result.rows()).hasSize(500);
      assertThat(result.capped()).isFalse();
    }
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement st = conn.createStatement()) {
      st.executeUpdate("insert into quest(id,name,quest_level) values (99000,'Overflow',11)");
      st.executeUpdate("insert into quest_giver(quest_id,kind,entity_id,role) values (99000,'npc',999999,'start')");
    }
    try (Database db = Database.open(copy)) {
      var quests = Repositories.of(db).quests();
      var ref = new dev.wowforever.app.model.EntityRef(EntityKind.NPC, 999999);
      assertThat(quests.involving(ref)).hasSize(500);
      assertThat(quests.involvingResult(ref).capped()).isTrue();
    }
  }

  @Test
  void questDetailHasGivers() {
    assertThat(repos.quests().detail(2).get().givers()).isNotEmpty();
  }

  @Test
  void startGiversComeFirstAndKnowTheirQuest() {
    var givers = repos.quests().detail(2).get().givers();
    assertThat(givers.get(0).role()).isEqualTo("start");
    assertThat(givers).allSatisfy(g -> assertThat(g.displayName()).isNotBlank());
  }

  @Test
  void missingQuestIsEmpty() {
    assertThat(repos.quests().detail(-1)).isEmpty();
  }

  @Test
  void itemObjectiveKeepsItsTarget() {
    // 176 Wanted: "Hogger": objectives {nil,nil,{{1931}}}
    assertThat(repos.quests().objectives(176))
        .containsExactly(new QuestObjective(176, 0, "item", 1931L, null, null, List.of()));
  }

  @Test
  void killCreditGroupIsOneOrdinalWithItsMembersInOrder() {
    // 452 Pyrewood Ambush: one killCredit group of 2060..2068, the base creature 2060 first
    List<QuestObjective> pyrewood = repos.quests().objectives(452);
    assertThat(pyrewood).hasSize(9).allSatisfy(o -> {
      assertThat(o.ordinal()).isZero();
      assertThat(o.kind()).isEqualTo("creature");
      assertThat(o.creditGroup()).isZero();
      assertThat(o.points()).isEmpty();
    });
    assertThat(pyrewood).extracting(QuestObjective::targetId)
        .containsExactly(2060L, 2061L, 2062L, 2063L, 2064L, 2065L, 2066L, 2067L, 2068L);
  }

  @Test
  void exploreObjectiveCarriesItsPoints() {
    // 5561 Kodo Roundup: a killCredit group, then the explore row with its Desolace point
    List<QuestObjective> kodo = repos.quests().objectives(5561);
    assertThat(kodo).extracting(QuestObjective::ordinal).containsExactly(0, 0, 0, 1);
    QuestObjective explore = kodo.getLast();
    assertThat(explore.kind()).isEqualTo("explore");
    assertThat(explore.targetId()).isNull();
    assertThat(explore.text()).isEqualTo("Lure the Kodos to Smeed Scrabblescrew.");
    assertThat(explore.points()).containsExactly(new QuestObjective.Point(405, 60.58, 62.0));
    assertThat(kodo.getFirst().points()).isEmpty();
  }

  @Test
  void unknownQuestHasAnEmptyChain() {
    // 604 is named by 599's next relation but is not in the fixture
    assertThat(repos.quests().chain(604)).isEqualTo(QuestChain.EMPTY);
  }

  @Test
  void questWithoutObjectivesOrRelationsIsEmpty() {
    assertThat(repos.quests().objectives(-1)).isEmpty();
    assertThat(repos.quests().relations(-1)).isEmpty();
    assertThat(repos.quests().sourceItems(-1)).isEmpty();
  }

  @Test
  void relationsAreTheQuestsOwnRowsByKind() {
    // 597 The Bloodsail Buccaneers: preQuestSingle {595}, nextQuestInChain 599
    assertThat(repos.quests().relations(597)).containsExactly(
        new QuestRelation(597, 599, "next"), new QuestRelation(597, 595, "pre_single"));
    // 5149 Pamela's Doll: breadcrumbs {5142, 5601}
    assertThat(repos.quests().relations(5149)).containsExactly(
        new QuestRelation(5149, 5142, "breadcrumb"), new QuestRelation(5149, 5601, "breadcrumb"));
  }

  @Test
  void sourceItemsMissingFromTheItemTableKeepTheirIds() {
    // 1136 Frostmaw: sourceItemId 5838, requiredSourceItems {5810}; neither item is in the fixture
    assertThat(repos.quests().sourceItems(1136)).containsExactly(
        new QuestSourceItem(5838, "provided", null, null, null),
        new QuestSourceItem(5810, "required", null, null, null));
    assertThat(repos.quests().sourceItems(5149)).extracting(QuestSourceItem::itemId)
        .containsExactly(12886L, 12887L, 12888L);
  }

  @Test
  void sourceItemsJoinTheItemsNameQualityAndIcon(@TempDir Path dir) throws Exception {
    try (SchemaDb schema = SchemaDb.create(dir)) {
      schema.quest(1, "Delivery", 10)
          .sql("INSERT INTO item (id, name, quality, icon_id) VALUES (7, 'Sealed Letter', 1, 133471)")
          .sql("INSERT INTO item (id, name, quality, icon_id) VALUES (9, 'Apple', 1, 133975)")
          .sql("INSERT INTO quest_source_item (quest_id, item_id, kind) VALUES (1, 7, 'provided')")
          .sql("INSERT INTO quest_source_item (quest_id, item_id, kind) VALUES (1, 8, 'required')")
          .sql("INSERT INTO quest_source_item (quest_id, item_id, kind) VALUES (1, 9, 'required')");
      try (Database db = schema.open()) {
        List<QuestSourceItem> items = new QuestRepository(db).sourceItems(1);
        // provided first; then the named items by name, unknown ones last
        assertThat(items).containsExactly(
            new QuestSourceItem(7, "provided", "Sealed Letter", 1, 133471L),
            new QuestSourceItem(9, "required", "Apple", 1, 133975L),
            new QuestSourceItem(8, "required", null, null, null));
        assertThat(items.get(2).displayName()).isEqualTo("Unknown item #8");
      }
    }
  }

  @Test
  void questListByZoneAndLevel() {
    assertThat(repos.quests().list(new QuestFilter(40L, null, null))).extracting(QuestSummary::name)
        .containsExactly("Captain Sander's Hidden Treasure");
    assertThat(repos.quests().list(new QuestFilter(null, 15, 16))).extracting(QuestSummary::id)
        .containsExactly(5691L, 5692L, 136L, 452L);
    assertThat(repos.quests().list(new QuestFilter(40L, 20, null))).isEmpty();
    assertThat(repos.quests().list(QuestFilter.ALL)).hasSize(26);
    assertThat(repos.quests().list(new QuestFilter(40L, null, null))).isEqualTo(repos.quests().byZone(40));
  }

  @Test
  void itemGiversCarryTheItemQualityAndIcon(@TempDir Path tmp) throws Exception {
    Path copy = tmp.resolve("db.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement st = conn.createStatement()) {
      st.executeUpdate("insert into quest_giver(quest_id, kind, entity_id, role) values (2, 'item', 17204, 'end')");
    }
    try (Database db = Database.open(copy)) {
      List<QuestGiver> givers = Repositories.of(db).quests().detail(2).orElseThrow().givers();
      assertThat(givers).filteredOn(g -> g.ref().id() == 17204).singleElement().satisfies(g -> {
        assertThat(g.name()).isEqualTo("Eye of Sulfuras");
        assertThat(g.quality()).isEqualTo(5);
        assertThat(g.iconId()).isEqualTo(134124L);
      });
      assertThat(givers).filteredOn(g -> g.ref().kind() == EntityKind.NPC).isNotEmpty()
          .allSatisfy(g -> {
            assertThat(g.quality()).isNull();
            assertThat(g.iconId()).isNull();
          });
    }
  }

  @Test
  void bloodsailChainRunsThroughTheMissingLastQuest() {
    // 595 -> 597 -> 599 -> 604; 604 is not in the fixture
    QuestChain chain = repos.quests().chain(599);
    assertThat(rows(chain)).containsExactly("595:REQUIRES_ONE:-2", "597:REQUIRES_ONE:-1", "599:-:0", "604:UNLOCKS:1");
    assertThat(chain.steps().getFirst().name()).isEqualTo("The Bloodsail Buccaneers");
    assertThat(chain.steps().getFirst().level()).isEqualTo(41);
    assertThat(chain.steps().getLast().name()).isNull();
    assertThat(chain.spine()).extracting(Step::questId).containsExactly(595L, 597L, 599L, 604L);
    assertThat(chain.spineIndex()).isEqualTo(2);
  }

  @Test
  void pamelaQuestsAreExclusiveLeadInsToTheDoll() {
    assertThat(rows(repos.quests().chain(5142))).containsExactly("5142:-:0", "5601:EXCLUSIVE:0", "5149:LEADS_TO:1");
    assertThat(rows(repos.quests().chain(5149))).containsExactly("5142:LEAD_IN:-1", "5601:LEAD_IN:-1", "5149:-:0");
  }

  @Test
  void orbRequiresAllOfTwoAbsentQuests() {
    // 4964: preQuestGroup {4976, -4962}, exclusiveTo {4963}; all three are absent from the fixture
    QuestChain orb = repos.quests().chain(4964);
    assertThat(rows(orb)).containsExactly(
        "4962:REQUIRES_ALL:-1", "4976:REQUIRES_ALL:-1", "4964:-:0", "4963:EXCLUSIVE:0");
    assertThat(orb.currentIndex()).isEqualTo(2);
    assertThat(orb.current()).map(Step::name).hasValue("The Completed Orb of Dar'Orahil");
  }

  private static List<String> rows(QuestChain chain) {
    return chain.steps().stream()
        .map(s -> s.questId() + ":" + (s.connector() == null ? "-" : s.connector().name()) + ":" + s.depth()
            + (s.loop() ? ":loop" : ""))
        .toList();
  }

  @Test
  void relatedItemsCarryTheItemQualityAndIcon(@TempDir Path tmp) throws Exception {
    Path copy = tmp.resolve("db.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement st = conn.createStatement()) {
      st.executeUpdate("insert into item_related_quest(item_id, quest_id) values (17204, 2), (1176, 2)");
    }
    try (Database db = Database.open(copy)) {
      List<ItemSource> related = Repositories.of(db).quests().detail(2).orElseThrow().relatedItems();
      assertThat(related).filteredOn(r -> r.ref().id() == 17204).singleElement().satisfies(r -> {
        assertThat(r.quality()).isEqualTo(5);
        assertThat(r.iconId()).isEqualTo(134124L);
      });
      assertThat(related).filteredOn(r -> r.ref().id() == 1176).singleElement()
          .satisfies(r -> assertThat(r.quality()).as("no client data").isNull());
    }
  }

  @Test
  void questLevelsOfZeroAndBelowAreUnknown(@TempDir Path tmp) throws Exception {
    Path copy = tmp.resolve("db.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement st = conn.createStatement()) {
      st.executeUpdate("insert into quest(id, name, quest_level, zone_or_sort) values "
          + "(97001, 'Scaling Quest', -1, 40), (97002, 'Level Zero Quest', 0, 40)");
    }
    try (Database db = Database.open(copy)) {
      QuestRepository quests = Repositories.of(db).quests();
      assertThat(quests.list(new QuestFilter(40L, null, null))).extracting(QuestSummary::id)
          .contains(97001L, 97002L);
      assertThat(quests.list(new QuestFilter(40L, null, 20))).extracting(QuestSummary::id).containsExactly(136L);
      assertThat(quests.list(new QuestFilter(40L, 1, null))).extracting(QuestSummary::id).containsExactly(136L);
    }
  }
}
