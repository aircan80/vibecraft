package dev.wowforever.importer.corrections;

import static dev.wowforever.importer.corrections.CorrectionTestSupport.arr;
import static dev.wowforever.importer.corrections.CorrectionTestSupport.itemsWith;
import static dev.wowforever.importer.corrections.CorrectionTestSupport.lua;
import static dev.wowforever.importer.corrections.CorrectionTestSupport.questsWith;
import static dev.wowforever.importer.corrections.CorrectionTestSupport.tableWith;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.report.Skip;
import dev.wowforever.lua.LuaNil;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValues;
import org.junit.jupiter.api.Test;

class CorrectionApplierTest {
  private final ImportReport report = new ImportReport();
  private final CorrectionApplier applier = new CorrectionApplier(report);

  private void apply(EntityTable table, String src, String layer) {
    applier.apply(table, (LuaTable) lua(src), layer);
  }

  @Test void replacesDeletesAndCreates() {
    EntityTable items = itemsWith(730);                         // name "Murloc Eye", npcDrops {1,2}
    apply(items, "{ [730] = { [itemKeys.npcDrops] = {9}, [itemKeys.vendors] = {} }, [999999] = { [itemKeys.name] = 'New' } }", "test");
    assertThat(arr(items.get(730, "npcDrops"))).containsExactly(9L); assertThat(items.get(730, "vendors")).isEqualTo(LuaNil.NIL);
    assertThat(items.get(999999, "name")).isEqualTo(new LuaString("New")); assertThat(report.get("created.ITEM")).isEqualTo(1);
  }
  @Test void createsMissingEntityWithPartialFields() {
    EntityTable items = itemsWith();
    apply(items, "{ [190179] = { [itemKeys.name] = \"Avelina's Heart\", [itemKeys.npcDrops_add] = {185333} } }", "traces");
    assertThat(items.get(190179, "itemLevel")).isEqualTo(LuaNil.NIL); assertThat(arr(items.get(190179, "npcDrops"))).containsExactly(185333L);
  }
  @Test void reportsUnresolvedValuesAndUnsupportedOps() {
    EntityTable quests = questsWith(2);
    apply(quests, "{ [2] = { [questKeys.objectives_add] = {{{123}}}, [questKeys.name] = UNKNOWN_SYMBOL } }", "x");
    assertThat(report.skips()).extracting(Skip::field).containsExactlyInAnyOrder("objectives", "name");
    assertThat(report.skipsOnUiFields()).as("objectives is a UI field since phase 3A").isEqualTo(2);
  }
  @Test void tableIntoScalarIsSkipped() { EntityTable items = itemsWith(730); apply(items, "{ [730] = { [itemKeys.itemLevel] = {1,2} } }", "x"); assertThat(report.skips()).hasSize(1); assertThat(items.get(730, "itemLevel")).isNotEqualTo(lua("{1,2}")); }

  @Test
  void countsReplacementsDeletionsOpsAndRows() {
    EntityTable items = itemsWith(730, 2874);
    apply(items, "{ [730] = { [itemKeys.itemLevel] = 12, [itemKeys.npcDrops] = {} }, [2874] = { [itemKeys.vendors_add] = {1} } }", "layer");
    assertThat(report.get("replaced")).isEqualTo(1);
    assertThat(report.get("deleted")).isEqualTo(1);
    assertThat(report.get("ops")).isEqualTo(1);
    assertThat(report.get("rows.layer")).isEqualTo(2);
    assertThat(report.get("created.ITEM")).isZero();
    assertThat(LuaValues.asLong(items.get(730, "itemLevel"))).hasValue(12);
    assertThat(report.skips()).isEmpty();
  }

  @Test
  void emptyTableDeletesEvenAScalarField() {
    EntityTable items = itemsWith(730);
    apply(items, "{ [730] = { [itemKeys.itemLevel] = {} } }", "x");
    assertThat(items.get(730, "itemLevel")).isEqualTo(LuaNil.NIL);
    assertThat(report.get("deleted")).isEqualTo(1);
  }

  @Test
  void nonIntegerEntityIdsAndNonTableRowsAreSkippedAsUiLosses() {
    EntityTable items = itemsWith(730);
    apply(items, "{ [1.5] = { [itemKeys.name] = 'x' }, ['abc'] = {}, [0] = {}, [730] = 5 }", "x");
    assertThat(report.skips()).extracting(Skip::field).containsOnly("<id>");
    assertThat(report.skips()).extracting(Skip::reason).containsExactlyInAnyOrder(
        "non-integer entity id", "non-integer entity id", "non-integer entity id", "row is not a table");
    assertThat(report.skipsOnUiFields()).isEqualTo(4);
    assertThat(items.size()).isEqualTo(1);
  }

  @Test
  void fieldIndexZeroIsSkippedNotThrown() {
    EntityTable items = itemsWith(730);
    apply(items, "{ [730] = { ['op:add:0'] = {1}, ['op:remove:-1'] = {2}, [itemKeys.itemLevel] = 7 } }", "x");
    assertThat(report.skips()).hasSize(2).allMatch(s -> s.reason().equals("unknown key"));
    assertThat(LuaValues.asLong(items.get(730, "itemLevel"))).hasValue(7);
  }

  @Test
  void unknownKeysAreSkipped() {
    EntityTable items = itemsWith(730);
    apply(items, "{ [730] = { [99] = 1, ['name'] = 'x', ['op:add:99'] = {1} } }", "x");
    assertThat(report.skips()).hasSize(3).allMatch(s -> s.reason().equals("unknown key"));
    assertThat(items.get(730, "name")).isEqualTo(new LuaString("Murloc Eye"));
  }

  @Test
  void tablesContainingUnresolvedValuesAreNotStored() {
    EntityTable items = itemsWith(730);
    apply(items, "{ [730] = { [itemKeys.npcDrops] = {1, NOPE}, [itemKeys.vendors_add] = {{NOPE2}} } }", "x");
    assertThat(report.skips()).extracting(Skip::field).containsExactlyInAnyOrder("npcDrops", "vendors");
    assertThat(report.skips()).extracting(Skip::reason).containsExactlyInAnyOrder("unknown name NOPE", "unknown name NOPE2");
    assertThat(arr(items.get(730, "npcDrops"))).hasSize(57);
  }

  @Test
  void scalarIntoTableFieldIsSkipped() {
    EntityTable items = itemsWith(730);
    apply(items, "{ [730] = { [itemKeys.npcDrops] = 5 } }", "x");
    assertThat(report.skips()).extracting(Skip::reason).containsExactly("scalar into table field");
    assertThat(items.get(730, "npcDrops")).isInstanceOf(LuaTable.class);
  }

  @Test
  void mismatchedScalarTypesAreStoredAndCountedAsCoerced() {
    EntityTable items = itemsWith(730);
    apply(items, "{ [730] = { [itemKeys.name] = 5, [itemKeys.itemLevel] = '12' } }", "x");
    assertThat(items.get(730, "name")).isEqualTo(LuaNumber.of(5));
    assertThat(items.get(730, "itemLevel")).isEqualTo(new LuaString("12"));
    assertThat(report.get("coerced")).isEqualTo(2);
    assertThat(report.get("replaced")).isEqualTo(2);
    assertThat(report.skips()).isEmpty();
  }

  @Test
  void opsOnScalarFieldsAndNonTableOperandsAreSkipped() {
    EntityTable items = itemsWith(730);
    apply(items, "{ [730] = { [itemKeys.itemLevel_add] = {1}, [itemKeys.vendors_add] = 7 } }", "x");
    assertThat(report.skips()).extracting(Skip::reason)
        .containsExactlyInAnyOrder("unsupported op on scalar field", "operand is not a table");
  }

  @Test
  void unsupportedShapeIsNamedInTheSkip() {
    EntityTable npcs = tableWith(EntityKind.NPC, 448);
    apply(npcs, "{ [448] = { [npcKeys.waypoints_add] = {[12] = {{{1,2}}}} } }", "x");
    assertThat(report.skips()).extracting(Skip::reason).containsExactly("unsupported op on shape WAYPOINTLIST");
    assertThat(report.skipsOnUiFields()).isZero();
  }

  @Test
  void malformedOperandsAndNonTableExistingValuesAreSkipped() {
    EntityTable items = itemsWith(730);
    items.set(730, "vendors", LuaNumber.of(3));
    apply(items, "{ [730] = { [itemKeys.npcDrops_add] = {nil, 5}, [itemKeys.vendors_add] = {1} } }", "x");
    assertThat(report.skips()).extracting(Skip::field).containsExactlyInAnyOrder("npcDrops", "vendors");
    assertThat(report.skips()).filteredOn(s -> s.field().equals("npcDrops")).allMatch(s -> s.reason().startsWith("malformed operand"));
    assertThat(report.skips()).filteredOn(s -> s.field().equals("vendors")).extracting(Skip::reason).containsExactly("existing value is not a table");
    assertThat(items.get(730, "vendors")).isEqualTo(LuaNumber.of(3));
  }

  @Test
  void emptyOperandDoesNothingAndDoesNotInspectTheExistingValue() {
    EntityTable items = itemsWith(730);
    items.set(730, "vendors", LuaNumber.of(3));
    apply(items, "{ [730] = { [itemKeys.vendors_add] = {}, [itemKeys.npcDrops_remove] = {} } }", "x");
    assertThat(report.skips()).isEmpty();
    assertThat(items.get(730, "vendors")).isEqualTo(LuaNumber.of(3));
    assertThat(arr(items.get(730, "npcDrops"))).hasSize(57);
    assertThat(report.get("ops")).isZero();
  }

  @Test
  void replaceAndOpsOnOneFieldApplyReplaceThenRemoveThenAdd() {
    EntityTable items = itemsWith(730);
    apply(items, "{ [730] = { [itemKeys.npcDrops_add] = {3, 1}, [itemKeys.npcDrops_remove] = {1, 2}, [itemKeys.npcDrops] = {1, 2, 5} } }", "x");
    assertThat(arr(items.get(730, "npcDrops"))).containsExactly(5L, 3L, 1L);
    assertThat(report.get("replace+op")).isEqualTo(1);
    assertThat(report.get("replaced")).isEqualTo(1);
    assertThat(report.get("ops")).isEqualTo(2);
  }

  @Test
  void skipsCarryLayerKindIdAndFieldName() {
    EntityTable quests = questsWith(2);
    apply(quests, "{ [2] = { [questKeys.requiredLevel] = {1,2} } }", "forever/foreverQuestFixes");
    assertThat(report.skips()).containsExactly(
        new Skip("forever/foreverQuestFixes", EntityKind.QUEST, 2, "requiredLevel", "table into scalar"));
  }
}
