package dev.wowforever.importer.corrections;

import static dev.wowforever.importer.corrections.CorrectionTestSupport.arr;
import static dev.wowforever.importer.corrections.CorrectionTestSupport.lua;
import static dev.wowforever.importer.schema.Shape.EXTRAOBJECTIVES;
import static dev.wowforever.importer.schema.Shape.IDARRAY;
import static dev.wowforever.importer.schema.Shape.OBJECTIVES;
import static dev.wowforever.importer.schema.Shape.PAIR;
import static dev.wowforever.importer.schema.Shape.PAIRS;
import static dev.wowforever.importer.schema.Shape.QUESTGIVERS;
import static dev.wowforever.importer.schema.Shape.SPAWNLIST;
import static dev.wowforever.importer.schema.Shape.STRINGARRAY;
import static dev.wowforever.importer.schema.Shape.TRIGGER;
import static dev.wowforever.importer.schema.Shape.WAYPOINTLIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wowforever.importer.schema.Shape;
import dev.wowforever.lua.LuaNil;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValue;
import dev.wowforever.lua.LuaValues;
import org.junit.jupiter.api.Test;

class TableOpsTest {
  @Test void idarrayAddAppendsAbsent() { assertThat(arr(TableOps.add(IDARRAY, lua("{1,2}"), (LuaTable) lua("{2,3}")).get())).containsExactly(1L, 2L, 3L); }
  @Test void idarrayRemoveDeletesAllMatches() { assertThat(arr(TableOps.remove(IDARRAY, lua("{1,2,2,3}"), (LuaTable) lua("{2}")).get())).containsExactly(1L, 3L); }
  @Test void nilExistingActsAsEmpty() { assertThat(arr(TableOps.add(IDARRAY, LuaNil.NIL, (LuaTable) lua("{5}")).get())).containsExactly(5L); }
  @Test void questgiversAddKeepsOtherGroups() { LuaTable r = (LuaTable) TableOps.add(QUESTGIVERS, lua("{{17079}}"), (LuaTable) lua("{[2] = {424005}}")).get(); assertThat(arr(r.get(1))).containsExactly(17079L); assertThat(arr(r.get(2))).containsExactly(424005L); }
  @Test void spawnlistRemoveMatchesTuplesIgnoringZeroPhase() { LuaTable r = (LuaTable) TableOps.remove(SPAWNLIST, lua("{[12] = {{45,60,0},{46,61}}}"), (LuaTable) lua("{[12] = {{45,60}}}")).get(); assertThat(((LuaTable) r.get(12)).arrayLength()).isEqualTo(1); }
  @Test void pairsUseWholeRecordEquality() { assertThat(((LuaTable) TableOps.remove(PAIRS, lua("{{72,100},{73,200}}"), (LuaTable) lua("{{72,999}}")).get()).arrayLength()).isEqualTo(2); }
  @Test void unsupportedShapeIsEmpty() { assertThat(TableOps.add(OBJECTIVES, LuaNil.NIL, (LuaTable) lua("{}"))).isEmpty(); }

  @Test
  void addKeepsExistingDuplicatesAndAppendsOperandValuesOnceInOperandOrder() {
    LuaValue r = TableOps.add(IDARRAY, lua("{1,1,2}"), (LuaTable) lua("{4,3,4,1}")).get();
    assertThat(arr(r)).containsExactly(1L, 1L, 2L, 4L, 3L);
  }

  @Test
  void removePreservesOrderOfSurvivors() {
    assertThat(arr(TableOps.remove(IDARRAY, lua("{5,4,3,2,1}"), (LuaTable) lua("{4,2,99}")).get()))
        .containsExactly(5L, 3L, 1L);
  }

  @Test
  void removeOfEveryElementLeavesAnEmptyList() {
    LuaTable r = (LuaTable) TableOps.remove(IDARRAY, lua("{7}"), (LuaTable) lua("{7}")).get();
    assertThat(r.isEmpty()).isTrue();
  }

  @Test
  void signedIdsAreDistinctValues() {
    assertThat(arr(TableOps.add(IDARRAY, lua("{7786}"), (LuaTable) lua("{-7786}")).get())).containsExactly(7786L, -7786L);
  }

  @Test
  void emptyOperandReturnsExistingUnchanged() {
    LuaValue existing = lua("{1,2}");
    assertThat(TableOps.add(IDARRAY, existing, new LuaTable())).containsSame(existing);
    assertThat(TableOps.remove(SPAWNLIST, existing, new LuaTable())).containsSame(existing);
    assertThat(TableOps.remove(QUESTGIVERS, LuaNil.NIL, new LuaTable())).containsSame(LuaNil.NIL);
  }

  @Test
  void resultsAreNewTablesAndExistingIsNotMutated() {
    LuaTable existing = (LuaTable) lua("{1,2}");
    LuaValue r = TableOps.add(IDARRAY, existing, (LuaTable) lua("{3}")).get();
    assertThat(r).isNotSameAs(existing);
    assertThat(arr(existing)).containsExactly(1L, 2L);

    LuaTable spawns = (LuaTable) lua("{[12] = {{1,2}}}");
    TableOps.add(SPAWNLIST, spawns, (LuaTable) lua("{[12] = {{3,4}}}"));
    assertThat(((LuaTable) spawns.get(12)).arrayLength()).isEqualTo(1);

    LuaTable givers = (LuaTable) lua("{{1},{2}}");
    TableOps.remove(QUESTGIVERS, givers, (LuaTable) lua("{{1}}"));
    assertThat(arr(givers.get(1))).containsExactly(1L);
  }

  @Test
  void stringarrayAddsAndRemovesWholeStrings() {
    LuaValue added = TableOps.add(STRINGARRAY, lua("{\"a\"}"), (LuaTable) lua("{\"b\", \"a\"}")).get();
    assertThat(((LuaTable) added).array()).containsExactly(new LuaString("a"), new LuaString("b"));
    LuaValue removed = TableOps.remove(STRINGARRAY, added, (LuaTable) lua("{\"a\"}")).get();
    assertThat(((LuaTable) removed).array()).containsExactly(new LuaString("b"));
  }

  @Test
  void pairsAddUsesDeepEquality() {
    LuaTable r = (LuaTable) TableOps.add(PAIRS, lua("{{72,100}}"), (LuaTable) lua("{{72,100},{72,200}}")).get();
    assertThat(r.arrayLength()).isEqualTo(2);
    assertThat(LuaValues.deepEquals(r.get(2), lua("{72,200}"))).isTrue();
    LuaTable removed = (LuaTable) TableOps.remove(PAIRS, r, (LuaTable) lua("{{72,100}}")).get();
    assertThat(removed.arrayLength()).isEqualTo(1);
    assertThat(LuaValues.deepEquals(removed.get(1), lua("{72,200}"))).isTrue();
  }

  @Test
  void questgiversRemoveLeavesAHoleForAnEmptiedGroupAndKeepsOthers() {
    LuaTable r = (LuaTable) TableOps.remove(QUESTGIVERS, lua("{{1},{2,3},{4}}"), (LuaTable) lua("{{1},{3}}")).get();
    assertThat(r.get(1)).isEqualTo(LuaNil.NIL);
    assertThat(arr(r.get(2))).containsExactly(2L);
    assertThat(arr(r.get(3))).containsExactly(4L);
  }

  @Test
  void questgiversAddIntoNilExistingFillsOnlySuppliedGroups() {
    LuaTable r = (LuaTable) TableOps.add(QUESTGIVERS, LuaNil.NIL, (LuaTable) lua("{[3] = {9}}")).get();
    assertThat(r.get(1)).isEqualTo(LuaNil.NIL);
    assertThat(r.get(2)).isEqualTo(LuaNil.NIL);
    assertThat(arr(r.get(3))).containsExactly(9L);
  }

  @Test
  void questgiversEmptyOperandGroupLeavesThatGroupAlone() {
    LuaTable r = (LuaTable) TableOps.add(QUESTGIVERS, lua("{{1},{}}"), (LuaTable) lua("{{2},{}}")).get();
    assertThat(arr(r.get(1))).containsExactly(1L, 2L);
    assertThat(r.get(2)).isInstanceOf(LuaTable.class);
  }

  @Test
  void questgiversRejectsUnknownGroups() {
    assertThatThrownBy(() -> TableOps.add(QUESTGIVERS, LuaNil.NIL, (LuaTable) lua("{[4] = {1}}")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void spawnlistAddAppendsPerZoneAndPreservesOtherZones() {
    LuaTable r = (LuaTable) TableOps.add(SPAWNLIST, lua("{[12] = {{1,2}}, [40] = {{5,5}}}"),
        (LuaTable) lua("{[12] = {{1,2,0},{3,4}}, [14] = {{-1,-1}}}")).get();
    assertThat(((LuaTable) r.get(12)).arrayLength()).isEqualTo(2);
    assertThat(LuaValues.deepEquals(((LuaTable) r.get(12)).get(2), lua("{3,4}"))).isTrue();
    assertThat(LuaValues.deepEquals(r.get(14), lua("{{-1,-1}}"))).isTrue();
    assertThat(LuaValues.deepEquals(r.get(40), lua("{{5,5}}"))).isTrue();
  }

  @Test
  void spawnlistRemoveDropsAnEmptiedZoneAndHonoursNonZeroPhase() {
    LuaTable r = (LuaTable) TableOps.remove(SPAWNLIST, lua("{[12] = {{-1,-1}}, [14] = {{1,2,5}}}"),
        (LuaTable) lua("{[12] = {{-1,-1}}, [14] = {{1,2}}}")).get();
    assertThat(r.get(12)).isEqualTo(LuaNil.NIL);
    assertThat(((LuaTable) r.get(14)).arrayLength()).isEqualTo(1);
  }

  @Test
  void spawnCoordinatesCompareNumerically() {
    LuaTable r = (LuaTable) TableOps.remove(SPAWNLIST, lua("{[12] = {{45.5,60}}}"), (LuaTable) lua("{[12] = {{45.50,60.0}}}")).get();
    assertThat(r.get(12)).isEqualTo(LuaNil.NIL);
  }

  @Test
  void listOperandsMustBeDenseAndOfTheShapesElementType() {
    assertThatThrownBy(() -> TableOps.add(IDARRAY, LuaNil.NIL, (LuaTable) lua("{nil, 5}")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TableOps.add(IDARRAY, LuaNil.NIL, (LuaTable) lua("{\"x\"}")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TableOps.add(STRINGARRAY, LuaNil.NIL, (LuaTable) lua("{5}")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TableOps.add(PAIRS, LuaNil.NIL, (LuaTable) lua("{72, 100}")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TableOps.add(SPAWNLIST, LuaNil.NIL, (LuaTable) lua("{[12] = {45, 60}}")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void everyUnsupportedShapeIsEmptyForBothOperations() {
    for (Shape s : new Shape[] {OBJECTIVES, WAYPOINTLIST, TRIGGER, EXTRAOBJECTIVES, PAIR}) {
      assertThat(TableOps.add(s, LuaNil.NIL, (LuaTable) lua("{1}"))).as(s.name()).isEmpty();
      assertThat(TableOps.remove(s, LuaNil.NIL, (LuaTable) lua("{1}"))).as(s.name()).isEmpty();
    }
  }
}
