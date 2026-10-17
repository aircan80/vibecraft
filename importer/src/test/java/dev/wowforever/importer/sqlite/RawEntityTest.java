package dev.wowforever.importer.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.entity.RawEntityLoader;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.schema.MetaLoader;
import dev.wowforever.lua.LuaNil;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaUnresolved;
import dev.wowforever.lua.LuaValue;
import dev.wowforever.lua.ProblemCollector;
import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RawEntityTest {
  private final ImportReport report = new ImportReport();

  private static EntityTable fixtureQuests() {
    return RawEntityLoader.load(Fixtures.questieDbRoot().resolve("data/Forever/foreverQuestDB.lua"), EntityKind.QUEST,
        MetaLoader.load(Fixtures.questieDbRoot().resolve("src/meta/questMeta.lua")), new ProblemCollector());
  }

  private RawEntity quest(EntityTable quests, long id) {
    return new RawEntity(quests, quests.rows().get(id), new LuaColumns(report), report, new HashSet<>());
  }

  @Test
  void questNestedTables() {
    RawEntity hogger = quest(fixtureQuests(), 176); // Wanted: "Hogger"

    assertThat(hogger.name()).isEqualTo("Wanted:  \"Hogger\"");
    // startedBy {creatureStart nil, objectStart {68, 156561}}; finishedBy {{240}}
    assertThat(hogger.nested("startedBy", 1)).isEmpty();
    assertThat(hogger.nested("startedBy", 2)).containsExactly(LuaNumber.of(68), LuaNumber.of(156561));
    assertThat(hogger.nested("startedBy", 3)).isEmpty();
    assertThat(hogger.nested("finishedBy", 1)).containsExactly(LuaNumber.of(240));
    // objectives {nil, nil, {{1931}}}: positions are kept, holes read as nil
    assertThat(hogger.nested("objectives")).hasSize(3);
    assertThat(hogger.nested("objectives").subList(0, 2)).containsOnly(LuaNil.NIL);
    assertThat(hogger.nested("objectives", 1)).isEmpty();
    assertThat(hogger.nested("objectives", 3)).singleElement().isInstanceOf(LuaTable.class);
    assertThat(hogger.nested("objectives", 3, 1)).containsExactly(LuaNumber.of(1931));
    assertThat(hogger.nested("objectives", 3, 1, 1)).as("a scalar leaf").containsExactly(LuaNumber.of(1931));
    assertThat(hogger.nested("objectives", 5)).as("an index past the end").isEmpty();
    assertThat(hogger.nested("objectivesText")).containsExactly(
        new LuaString("Slay the gnoll Hogger and bring his Huge Gnoll Claw to Marshal Dughan."));
    assertThat(hogger.nested("reputationReward", 1)).containsExactly(LuaNumber.of(72), LuaNumber.of(250));
    assertThat(hogger.nested("zoneOrSort")).containsExactly(LuaNumber.of(12));
    assertThat(hogger.nested("triggerEnd")).isEmpty();
    assertThat(hogger.nested("sourceItemId")).isEmpty();
    assertThat(hogger.nestedTable("objectives", 3)).isPresent();
    assertThat(hogger.nestedTable("triggerEnd", 2)).isEmpty();
    assertThat(report.counters()).isEmpty();
  }

  @Test
  void steppingThroughAScalarIsABadValue() {
    RawEntity hogger = quest(fixtureQuests(), 176);

    assertThat(hogger.nested("zoneOrSort", 1)).isEmpty();
    assertThat(hogger.nested("objectives", 3, 1, 1, 1)).isEmpty();
    assertThat(hogger.nestedTable("objectives", 3, 1, 1)).isEmpty();

    assertThat(report.get("badValue.quest.zoneOrSort")).isEqualTo(1);
    assertThat(report.get("badValue.quest.objectives")).isEqualTo(2);
  }

  @Test
  void triggerEndZonesAreReachableAsATable() {
    EntityTable quests = fixtureQuests();
    LuaTable zones = new LuaTable();
    zones.put(LuaNumber.of(40), table(table(new LuaNumber(56.2), new LuaNumber(40.1))));
    zones.put(LuaNumber.of(12), table(table(new LuaNumber(30.0), new LuaNumber(60.5))));
    quests.set(176, "triggerEnd", table(new LuaString("Scout the Westfall lighthouse"), zones));

    RawEntity q = quest(quests, 176);

    assertThat(q.nested("triggerEnd", 1)).containsExactly(new LuaString("Scout the Westfall lighthouse"));
    assertThat(q.nestedTable("triggerEnd", 2).orElseThrow().entries()).extracting(Map.Entry::getKey)
        .containsExactlyInAnyOrder(LuaNumber.of(40), LuaNumber.of(12));
    assertThat(q.nested("triggerEnd", 2, 40)).as("one zone's coordinate pairs").hasSize(1);
    assertThat(q.nested("triggerEnd", 2, 40, 1)).containsExactly(new LuaNumber(56.2), new LuaNumber(40.1));
    assertThat(q.nested("triggerEnd", 2, 1)).as("no zone 1").isEmpty();
  }

  @Test
  void anUnresolvedFieldReadsAsEmptyAndIsCounted() {
    EntityTable quests = fixtureQuests();
    quests.set(176, "preQuestSingle", table(new LuaUnresolved("unknown enum", 7)));

    assertThat(quest(quests, 176).nested("preQuestSingle")).isEmpty();
    assertThat(report.get("unresolved.quest.preQuestSingle")).isEqualTo(1);
  }

  private static LuaTable table(LuaValue... values) {
    LuaTable t = new LuaTable();
    for (int i = 0; i < values.length; i++) {
      t.put(LuaNumber.of(i + 1), values[i]);
    }
    return t;
  }
}
