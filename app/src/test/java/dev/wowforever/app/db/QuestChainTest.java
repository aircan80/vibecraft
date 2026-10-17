package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.QuestChain;
import dev.wowforever.app.model.QuestChain.Connector;
import dev.wowforever.app.model.QuestChain.Step;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link QuestRepository#chain} on small quest graphs written row by row. */
class QuestChainTest {
  @TempDir Path tmp;
  private SchemaDb schema;
  private Database db;

  @BeforeEach
  void createSchema() throws Exception {
    schema = SchemaDb.create(tmp);
  }

  @AfterEach
  void close() throws Exception {
    if (db != null) {
      db.close();
    }
    schema.close();
  }

  @Test
  void linearChainRunsFromTheFirstPrerequisiteToTheLastUnlock() throws Exception {
    schema.quest(1, "First", 10).quest(2, "Second", 11).quest(3, "Third", 12)
        .relation(1, 2, "next").relation(2, 3, "next")
        .relation(2, 1, "pre_single").relation(3, 2, "pre_single");
    QuestChain middle = chain(2);
    assertThat(rows(middle)).containsExactly("1:REQUIRES_ONE:-1", "2:-:0", "3:UNLOCKS:1");
    assertThat(middle.currentIndex()).isEqualTo(1);
    assertThat(middle.current()).map(Step::questId).hasValue(2L);
    assertThat(middle.current()).map(Step::name).hasValue("Second");
    assertThat(middle.current()).map(Step::level).hasValue(11);
    assertThat(middle.truncated()).isFalse();
    assertThat(ids(middle.spine())).containsExactly(1L, 2L, 3L);
    assertThat(middle.spineIndex()).isEqualTo(1);
    assertThat(middle.previous()).map(Step::questId).hasValue(1L);
    assertThat(middle.next()).map(Step::questId).hasValue(3L);
    assertThat(middle.standalone()).isFalse();

    QuestChain first = chain(1);
    assertThat(rows(first)).containsExactly("1:-:0", "2:UNLOCKS:1", "3:UNLOCKS:2");
    assertThat(ids(first.spine())).containsExactly(1L, 2L, 3L);
    assertThat(first.spineIndex()).isZero();
    assertThat(first.previous()).isEmpty();

    assertThat(rows(chain(3))).containsExactly("1:REQUIRES_ONE:-2", "2:REQUIRES_ONE:-1", "3:-:0");
  }

  @Test
  void nextIsAHintThatUnlocksForwardButIsNotAPrerequisite() throws Exception {
    // 1 names 2 as its next quest, but 2 does not require 1
    schema.quest(1, "Hint", 10).quest(2, "Target", 11).relation(1, 2, "next");
    assertThat(rows(chain(1))).containsExactly("1:-:0", "2:UNLOCKS:1");
    assertThat(rows(chain(2))).containsExactly("2:-:0");
    assertThat(chain(2).standalone()).isTrue();
  }

  @Test
  void unlocksListEachQuestOnce() throws Exception {
    // 2 is both the next quest of 1 and requires 1; 3 is a child of 1 and names 1 as its parent
    schema.quest(1, "Root", 10).quest(2, "Follow-up", 11).quest(3, "Child", 12)
        .relation(1, 2, "next").relation(2, 1, "pre_single")
        .relation(1, 3, "child").relation(3, 1, "parent");
    assertThat(rows(chain(1))).containsExactly("1:-:0", "2:UNLOCKS:1", "3:UNLOCKS:1");
  }

  @Test
  void requiresAllOfAndOneOfNestBackward() throws Exception {
    // 10 requires all of 11 and 12; 12 requires one of 13 and 14
    schema.quest(10, "Goal", 20).quest(11, "Left", 15).quest(12, "Right", 16).quest(13, "Way A", 13)
        .quest(14, "Way B", 14)
        .relation(10, 11, "pre_group").relation(10, 12, "pre_group")
        .relation(12, 13, "pre_single").relation(12, 14, "pre_single");
    QuestChain goal = chain(10);
    assertThat(rows(goal)).containsExactly(
        "11:REQUIRES_ALL:-1", "13:REQUIRES_ONE:-2", "14:REQUIRES_ONE:-2", "12:REQUIRES_ALL:-1", "10:-:0");
    assertThat(goal.currentIndex()).isEqualTo(4);
    // without next hints the spine takes the highest-level prerequisite
    assertThat(ids(goal.spine())).containsExactly(14L, 12L, 10L);
  }

  @Test
  void spinePrefersThePrerequisiteWhoseNextHintNamesTheQuest() throws Exception {
    schema.quest(10, "Goal", 20).quest(11, "Main line", 15).quest(12, "Side", 16)
        .relation(10, 11, "pre_group").relation(10, 12, "pre_group").relation(11, 10, "next");
    assertThat(ids(chain(10).spine())).containsExactly(11L, 10L);
  }

  @Test
  void exclusiveAlternativesSitBesideTheirQuest() throws Exception {
    // 20 and 21 are exclusive to each other and both lead into 22 (the Pamela pattern)
    schema.quest(20, "Little", 50).quest(21, "Sister", 50).quest(22, "Doll", 50)
        .relation(20, 21, "exclusive").relation(21, 20, "exclusive")
        .relation(20, 22, "breadcrumb_for").relation(21, 22, "breadcrumb_for")
        .relation(20, 22, "next").relation(21, 22, "next")
        .relation(22, 20, "breadcrumb").relation(22, 21, "breadcrumb");
    QuestChain little = chain(20);
    assertThat(rows(little)).containsExactly("20:-:0", "21:EXCLUSIVE:0", "22:UNLOCKS:1");
    assertThat(little.currentIndex()).isZero();
    assertThat(ids(little.spine())).containsExactly(20L, 22L);
  }

  @Test
  void exclusiveDeclaredOnTheOtherSideStillBranches() throws Exception {
    schema.quest(30, "Pick", 5).quest(32, "Other pick", 5).relation(32, 30, "exclusive");
    assertThat(rows(chain(30))).containsExactly("30:-:0", "32:EXCLUSIVE:0");
  }

  @Test
  void exclusivesOfOtherStepsAreNotPartOfTheChain() throws Exception {
    // 31 requires 30, which is exclusive to 32; 31 unlocks 33, which is exclusive to 34
    schema.quest(30, "Pick", 5).quest(31, "Middle", 6).quest(32, "Other pick", 5).quest(33, "Then", 7)
        .quest(34, "Other then", 7)
        .relation(31, 30, "pre_single").relation(32, 30, "exclusive").relation(30, 32, "exclusive")
        .relation(33, 31, "pre_single").relation(33, 34, "exclusive").relation(34, 33, "exclusive");
    QuestChain middle = chain(31);
    assertThat(rows(middle)).containsExactly("30:REQUIRES_ONE:-1", "31:-:0", "33:UNLOCKS:1");
    assertThat(ids(middle.spine())).containsExactly(30L, 31L, 33L);
  }

  @Test
  void breadcrumbsAreOptionalLeadIns() throws Exception {
    schema.quest(20, "Little", 50).quest(21, "Sister", 50).quest(22, "Doll", 50)
        .relation(20, 21, "exclusive").relation(21, 20, "exclusive")
        .relation(20, 22, "breadcrumb_for").relation(21, 22, "breadcrumb_for")
        .relation(22, 20, "breadcrumb").relation(22, 21, "breadcrumb");
    QuestChain doll = chain(22);
    assertThat(rows(doll)).containsExactly("20:LEAD_IN:-1", "21:LEAD_IN:-1", "22:-:0");
    // a lead-in joins the spine when the quest has no prerequisite
    assertThat(ids(doll.spine())).containsExactly(20L, 22L);
  }

  @Test
  void breadcrumbDeclaredOnOneSideStillLeadsIn() throws Exception {
    schema.quest(1, "Lead", 10).quest(2, "Target", 12).quest(3, "Required", 11)
        .relation(1, 2, "breadcrumb_for").relation(2, 3, "pre_single");
    QuestChain target = chain(2);
    assertThat(rows(target)).containsExactly("3:REQUIRES_ONE:-1", "1:LEAD_IN:-1", "2:-:0");
    // a prerequisite beats a lead-in on the spine
    assertThat(ids(target.spine())).containsExactly(3L, 2L);
    assertThat(rows(chain(1))).containsExactly("1:-:0", "2:UNLOCKS:1");
  }

  @Test
  void parentQuestIsDuringAndStaysOffTheSpine() throws Exception {
    schema.quest(50, "Parent", 30).quest(51, "Child", 30).relation(50, 51, "child").relation(51, 50, "parent");
    QuestChain child = chain(51);
    assertThat(rows(child)).containsExactly("50:DURING:-1", "51:-:0");
    assertThat(ids(child.spine())).containsExactly(51L);
    assertThat(rows(chain(50))).containsExactly("50:-:0", "51:UNLOCKS:1");
  }

  @Test
  void cycleStopsWithALoopStep() throws Exception {
    // 30 and 31 require each other; 40 names itself as its next quest
    schema.quest(30, "Chicken", 10).quest(31, "Egg", 10).quest(40, "Again", 10)
        .relation(30, 31, "pre_single").relation(31, 30, "pre_single").relation(40, 40, "next");
    QuestChain chicken = chain(30);
    assertThat(rows(chicken)).containsExactly("30:REQUIRES_ONE:-2:loop", "31:REQUIRES_ONE:-1", "30:-:0");
    assertThat(chicken.currentIndex()).isEqualTo(2);
    assertThat(chicken.steps().getFirst().loop()).isTrue();
    assertThat(chicken.steps().getFirst().name()).isEqualTo("Chicken");
    assertThat(chicken.truncated()).isFalse();
    assertThat(ids(chicken.spine())).containsExactly(31L, 30L);

    QuestChain again = chain(40);
    assertThat(rows(again)).containsExactly("40:-:0", "40:UNLOCKS:1:loop");
    assertThat(ids(again.spine())).containsExactly(40L);
  }

  @Test
  void missingTargetIsAStepWithoutAName() throws Exception {
    schema.quest(40, "Known", 10).relation(40, 41, "pre_single").relation(40, 42, "next");
    QuestChain known = chain(40);
    assertThat(rows(known)).containsExactly("41:REQUIRES_ONE:-1", "40:-:0", "42:UNLOCKS:1");
    Step missing = known.steps().getFirst();
    assertThat(missing.name()).isNull();
    assertThat(missing.level()).isNull();
    assertThat(missing.zoneName()).isNull();
    assertThat(missing.displayName()).isEqualTo("Unknown quest #41");
    assertThat(ids(known.spine())).containsExactly(41L, 40L, 42L);
  }

  @Test
  void unknownQuestHasAnEmptyChain() throws Exception {
    // 99 is not a quest, even though a relation row names it
    schema.quest(98, "Known", 10).relation(98, 99, "next");
    QuestChain none = chain(99);
    assertThat(none).isEqualTo(QuestChain.EMPTY);
    assertThat(none.steps()).isEmpty();
    assertThat(none.currentIndex()).isEqualTo(-1);
    assertThat(none.spine()).isEmpty();
    assertThat(none.truncated()).isFalse();
    assertThat(none.isEmpty()).isTrue();
    assertThat(none.current()).isEmpty();
    assertThat(none.standalone()).isFalse();
    assertThat(none.spineIndex()).isEqualTo(-1);
    assertThat(none.previous()).isEmpty();
    assertThat(none.next()).isEmpty();
  }

  @Test
  void depthCapStopsAtFiftyStepsEitherWay() throws Exception {
    for (long id = 100; id < 160; id++) {
      schema.quest(id, "Step " + id, 10);
      if (id > 100) {
        schema.relation(id, id - 1, "pre_single");
      }
    }
    QuestChain last = chain(159);
    assertThat(last.steps()).hasSize(QuestChain.MAX_DEPTH + 1);
    assertThat(last.steps().getFirst().questId()).isEqualTo(109);
    assertThat(last.steps().getFirst().depth()).isEqualTo(-QuestChain.MAX_DEPTH);
    assertThat(last.steps()).noneMatch(Step::loop);
    assertThat(last.spine()).hasSize(QuestChain.MAX_DEPTH + 1);
    assertThat(last.truncated()).isTrue();

    QuestChain first = chain(100);
    assertThat(first.steps()).hasSize(QuestChain.MAX_DEPTH + 1);
    assertThat(first.steps().getLast().questId()).isEqualTo(150);
    assertThat(first.steps().getLast().depth()).isEqualTo(QuestChain.MAX_DEPTH);
    assertThat(first.truncated()).isTrue();

    // 51 steps back from 150 reach 100 exactly: nothing is left beyond the cap
    QuestChain exact = chain(150);
    assertThat(exact.steps().getFirst().questId()).isEqualTo(100);
    assertThat(exact.steps().getFirst().depth()).isEqualTo(-QuestChain.MAX_DEPTH);
    assertThat(exact.truncated()).isFalse();
    // from the middle both ends are less than 50 steps away
    QuestChain middle = chain(130);
    assertThat(middle.steps()).hasSize(60);
    assertThat(middle.truncated()).isFalse();
  }

  @Test
  void standaloneQuestHasOnlyItself() throws Exception {
    schema.quest(60, "Alone", 10).sql("INSERT INTO zone (id, name) VALUES (12, 'Elwynn Forest')")
        .sql("UPDATE quest SET zone_or_sort = 12 WHERE id = 60");
    QuestChain alone = chain(60);
    assertThat(rows(alone)).containsExactly("60:-:0");
    assertThat(alone.current()).map(Step::zoneName).hasValue("Elwynn Forest");
    assertThat(alone.standalone()).isTrue();
    assertThat(alone.spineIndex()).isZero();
    assertThat(alone.next()).isEmpty();
  }

  @Test
  void connectorsCarryTheSpecLabels() {
    assertThat(Connector.REQUIRES_ALL.label()).isEqualTo("Requires all of:");
    assertThat(Connector.REQUIRES_ONE.label()).isEqualTo("Requires one of:");
    assertThat(Connector.EXCLUSIVE.label()).isEqualTo("Choose one (exclusive):");
    assertThat(Connector.LEAD_IN.label()).isEqualTo("Optional lead-in:");
    assertThat(Connector.UNLOCKS.label()).isEqualTo("Unlocks:");
    assertThat(Connector.DURING.label()).isEqualTo("During:");
  }

  // ---------------------------------------------------------------------------------------------

  private QuestChain chain(long id) throws Exception {
    if (db == null) {
      db = schema.open();
    }
    return new QuestRepository(db).chain(id);
  }

  /** {@code id:connector:depth}, {@code -} for no connector, {@code :loop} appended for loop steps. */
  private static List<String> rows(QuestChain chain) {
    return chain.steps().stream()
        .map(s -> s.questId() + ":" + (s.connector() == null ? "-" : s.connector().name()) + ":" + s.depth()
            + (s.loop() ? ":loop" : ""))
        .toList();
  }

  private static List<Long> ids(List<Step> steps) {
    return steps.stream().map(Step::questId).toList();
  }
}
