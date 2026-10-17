package dev.wowforever.importer.allthethings;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AllTheThingsParserTest {
  private AllTheThingsParser.Result parse(String text) { return AllTheThingsParser.parse(Map.of("fixture.lua", text)); }
  @Test void nativeForeverSleepingBagRowsKeepFactionAlternativesAndFourSingleSteps() throws Exception {
    String text;
    try (var in = getClass().getResourceAsStream("/fixtures/allthethings/sleeping-bag.lua")) {
      text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    var result = parse(text);
    assertThat(result.issues()).isEmpty();
    assertThat(result.quests()).hasSize(7);
    assertThat(result.links()).extracting(l -> l.questId()+":"+l.relatedId()+":"+l.kind()).containsExactlyInAnyOrder(
        "79192:79008:pre_single", "79192:79007:pre_single", "79980:79192:pre_single", "79974:79980:pre_single",
        "79975:79974:pre_single", "79976:79975:pre_single");
    assertThat(result.links()).allSatisfy(l -> { assertThat(l.evidence()).contains("AllTheThings Forever;", "sourceQuests=").doesNotContain(AllTheThingsSource.PIN); assertThat(l.sourceLine()).isPositive(); });
  }
  @Test void pluralThresholdDistinguishesAllAnyAndUnsupportedKOfN() {
    var result = parse("q(1,{});q(2,{});q(3,{});q(4,{sourceQuests={1,2}});"
        +"q(5,{sourceQuests={1,2},sourceQuestNumRequired=1});q(6,{sourceQuests={1,2,3},sqreq=2})");
    assertThat(result.links()).extracting(l -> l.questId()+":"+l.kind()).containsExactlyInAnyOrder(
        "4:pre_group","4:pre_group","5:pre_single","5:pre_single");
    assertThat(result.issues()).anySatisfy(i -> assertThat(i.detail()).contains("Unrepresentable"));
  }
  @Test void unresolvedPluralFactionOrMissingReferenceIsNotPromotedAsAll() {
    var result = parse("q(1,{races=ALLIANCE_ONLY});q(2,{});q(3,{sourceQuests={1,2}});q(4,{sourceQuests={100,101}})");
    assertThat(result.links()).isEmpty();
    assertThat(result.issues()).hasSize(2);
  }
  @Test void unknownTimelineAndDynamicConditionsRemainVisibleWithoutBecomingFacts() {
    var result = parse("q(1,{sourceQuest=2,timeline={TIMELINE.REMOVED_1_60_2}});q(2,{sourceQuest=3,races=UNKNOWN});"
        +"q(3,{sourceQuest=compute()});q(4,{sourceQuest=5,lockCriteria={1,2}})");
    assertThat(result.quests()).hasSize(4).allSatisfy(q -> assertThat(q.supported()).isFalse());
    assertThat(result.links()).isEmpty(); assertThat(result.issues()).hasSize(4);
  }
  @Test void preprocessorUsesOnlyDeclaredForeverContextAndDoesNotGuessUnknownBranches() {
    var result = parse("-- #IF FOREVER\nq(1,{sourceQuest=2})\n-- #ELSE\nq(3,{sourceQuest=4})\n-- #ENDIF\n"
        +"-- #IF SOMETHING_UNKNOWN\nq(5,{sourceQuest=6})\n-- #ELSE\nq(7,{sourceQuest=8})\n-- #ENDIF");
    assertThat(result.links()).extracting(AllTheThingsParser.QuestLink::questId).containsExactly(1L);
    assertThat(result.links().getFirst().sourceLine()).isEqualTo(2);
    assertThat(result.issues()).hasSize(1);
  }
  @Test void unsupportedElseIfNeverLeaksItsQuests() {
    var result = parse("-- #IF SOD\nq(1,{sourceQuest=2})\n-- #ELSEIF UNKNOWN\nq(3,{sourceQuest=4})\n-- #ENDIF");
    assertThat(result.links()).isEmpty(); assertThat(result.issues()).isNotEmpty();
  }
  @Test void functionDefinitionsAndControlFlowAreNotExecuted() {
    var result = parse("function make() return q(1,{sourceQuest=2}) end\nif flag then q(3,{sourceQuest=4}) end\nq(5,{sourceQuest=6})");
    assertThat(result.links()).extracting(AllTheThingsParser.QuestLink::questId).containsExactly(5L);
    assertThat(result.issues()).isNotEmpty();
  }
  @Test void inheritedOrDynamicContainerRestrictionsDoNotBecomeUnconditionalFacts() {
    var result = parse("n(QUESTS,{races=HORDE_ONLY,groups={q(1,{sourceQuest=2})}});"
        + "unknownWrapper(q(3,{sourceQuest=4})); n(QUESTS,{q(5,{sourceQuest=6})}); q(9,q(10,{sourceQuest=11}))");
    assertThat(result.links()).extracting(AllTheThingsParser.QuestLink::questId).containsExactly(5L);
    assertThat(result.issues()).hasSize(4);
  }
  @Test void conflictingVariantsAndDuplicateFieldsAreNotUnioned() {
    var result = parse("q(1,{sourceQuest=2});q(1,{sourceQuest=3});q(4,{sourceQuest=2,sourceQuest=3})");
    assertThat(result.links()).isEmpty(); assertThat(result.issues()).isNotEmpty();
  }
  @Test void knownUnsupportedPrerequisiteDoesNotBecomeMandatoryEvenForSingleReference() {
    var result = parse("q(1,{isBreadcrumb=true});q(2,{sourceQuest=1});"
        + "q(3,{timeline={TIMELINE.REMOVED_1_60_2}});q(4,{sourceQuest=3});q(5,{sourceQuest=99})");
    assertThat(result.links()).extracting(AllTheThingsParser.QuestLink::questId).containsExactly(5L);
    assertThat(result.issues()).anySatisfy(i -> assertThat(i.detail()).contains("Known prerequisite"));
  }
  @Test void futureEligibilityFieldsAreRetainedAsUnsupportedRatherThanIgnored() {
    var result = parse("q(1,{sourceQuest=2,newFutureGate=true})");
    assertThat(result.links()).isEmpty();
    assertThat(result.quests().getFirst().detail()).contains("Unsupported quest field newFutureGate");
  }
  @Test void replayUsesArchivedManifestInsteadOfNewerCurrentFileList(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
    String path = AllTheThingsSource.FOREVER_ROOT + "zones/old-selection.lua";
    java.nio.file.Files.createDirectories(root.resolve(path).getParent());
    java.nio.file.Files.writeString(root.resolve(path), "q(1,{sourceQuest=2})");
    assertThat(AllTheThingsSource.load(root, List.of(path)).links()).hasSize(1);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> AllTheThingsSource.load(root,
        List.of(AllTheThingsSource.FOREVER_ROOT + "../../../../escape.lua"))).isInstanceOf(java.io.IOException.class);
  }
  @Test void dynamicQuestFieldKeysCannotBypassEligibilityGuards() {
    var result = parse("q(2,{sourceQuest=1,[computeConditionKey()]=true});q(3,{sourceQuest=1,arbitraryFields()})");
    assertThat(result.links()).isEmpty();
    assertThat(result.quests()).hasSize(2).allSatisfy(q -> assertThat(q.supported()).isFalse());
    assertThat(result.issues()).allSatisfy(i -> assertThat(i.detail()).contains("quest field key"));
  }
  @Test void unknownInheritedContainerFieldsCannotHideEligibilityRestrictions() {
    var result = parse("n(QUESTS,{[computeConditionKey()]=true,groups={q(2,{sourceQuest=1})}});"
        +"n(QUESTS,{futureGate=true,groups={q(3,{sourceQuest=1})}})");
    assertThat(result.links()).isEmpty();
    assertThat(result.quests()).hasSize(2).allSatisfy(q -> assertThat(q.supported()).isFalse());
  }
  @Test void hiddenQuestRowsStayInspectableWithoutBeingPromoted() {
    var result = parse("root(ROOTS.HiddenQuestTriggers,{q(98289,{timeline={TIMELINE.ADDED_1_60_1}})})");
    assertThat(result.quests()).hasSize(1);
    assertThat(result.quests().getFirst().supported()).isFalse();
    assertThat(result.links()).isEmpty();
    assertThat(AllTheThingsSource.isQuestInput(AllTheThingsSource.FOREVER_ROOT+".hqt/hidden quest triggers.lua")).isTrue();
  }
  @Test void fullManifestIncludesInactiveRawInputsLicenseAndRuntimeEvidence() {
    assertThat(AllTheThingsSource.paths()).hasSize(242).contains("LICENSE", "src/Classes/Quests.lua",
        ".contrib/.db/shared/functions/Shortcuts.lua", ".contrib/.db/forever/.config/forever.config");
    assertThat(AllTheThingsSource.isQuestInput(".contrib/.db/forever/zzOLD/test.lua")).isFalse();
    assertThat(AllTheThingsSource.isQuestInput(".contrib/.db/forever/zones/test.lua")).isTrue();
  }
}
