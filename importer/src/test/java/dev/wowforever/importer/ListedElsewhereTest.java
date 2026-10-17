package dev.wowforever.importer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.drops.DropRate;
import dev.wowforever.importer.drops.DropRates;
import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.schema.MetaLoader;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaTable;
import org.junit.jupiter.api.Test;

/** Which (boss, item) rows a dropped AtlasLoot row survives through (Task 19): Questie's or VMaNGOS' listing. */
class ListedElsewhereTest {
  @Test
  void questieRatesVmangosRatesOrQuestieNpcDropsKeepAPair() {
    DropRates rates = new DropRates();
    rates.put(1, 100, new DropRate(5.0, "questie-wowhead"), 3);
    rates.put(2, 100, new DropRate(5.0, "vmangos"), 4);
    rates.put(3, 100, new DropRate(5.0, "atlasloot"), 5);
    EntityTable items = new EntityTable(EntityKind.ITEM,
        MetaLoader.load(Fixtures.questieDbRoot().resolve("src/meta/itemMeta.lua")));
    items.create(200);
    LuaTable drops = new LuaTable();
    drops.put(LuaNumber.of(1), LuaNumber.of(4));
    items.set(200, "npcDrops", drops);

    assertThat(ImportPipeline.listedElsewhere(rates, items, 1, 100)).as("Questie's rate").isTrue();
    assertThat(ImportPipeline.listedElsewhere(rates, items, 2, 100)).as("VMaNGOS' rate").isTrue();
    assertThat(ImportPipeline.listedElsewhere(rates, items, 3, 100)).as("AtlasLoot's own rate only").isFalse();
    assertThat(ImportPipeline.listedElsewhere(rates, items, 4, 200)).as("Questie's npcDrops").isTrue();
    assertThat(ImportPipeline.listedElsewhere(rates, items, 5, 200)).isFalse();
    assertThat(ImportPipeline.listedElsewhere(rates, items, 4, 999)).as("not a Questie item").isFalse();
  }
}
