package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.QuestChain;
import dev.wowforever.app.model.QuestChain.Step;
import dev.wowforever.app.model.QuestObjective;
import dev.wowforever.app.model.QuestRelation;
import dev.wowforever.app.model.QuestSourceItem;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FixtureDb.class)
class QuestRepositoryTest {
  private final Repositories repos = FixtureDb.repositories();

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
    assertThat(rows(repos.quests().chain(5142))).containsExactly("5142:-:0", "5601:EXCLUSIVE:0", "5149:UNLOCKS:1");
    assertThat(rows(repos.quests().chain(5149))).containsExactly("5142:LEAD_IN:-1", "5601:LEAD_IN:-1", "5149:-:0");
  }

  @Test
  void orbRequiresAllOfTwoAbsentQuests() {
    // 4964: preQuestGroup {4976, -4962}, exclusiveTo {4963}; all three are absent from the fixture
    QuestChain orb = repos.quests().chain(4964);
    assertThat(rows(orb)).containsExactly(
        "4962:REQUIRES_ALL:-1", "4976:REQUIRES_ALL:-1", "4964:-:0", "4963:EXCLUSIVE:0");
    assertThat(orb.currentIndex()).isEqualTo(2);
    assertThat(orb.current().name()).isEqualTo("The Completed Orb of Dar'Orahil");
  }

  private static List<String> rows(QuestChain chain) {
    return chain.steps().stream()
        .map(s -> s.questId() + ":" + (s.connector() == null ? "-" : s.connector().name()) + ":" + s.depth()
            + (s.loop() ? ":loop" : ""))
        .toList();
  }
}
