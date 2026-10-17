package dev.wowforever.importer.entity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.schema.EntitySchema;
import dev.wowforever.importer.schema.MetaLoader;
import dev.wowforever.lua.LuaNil;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValues;
import dev.wowforever.lua.Problem;
import dev.wowforever.lua.ProblemCollector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RawEntityLoaderTest {

  private static EntitySchema schema(String meta) {
    return MetaLoader.load(Fixtures.questieDbRoot().resolve("src/meta/" + meta + "Meta.lua"));
  }

  private static EntitySchema itemSchema() {
    return schema("item");
  }

  private static EntitySchema npcSchema() {
    return schema("npc");
  }

  @Test
  void loadsItems() {
    EntityTable t = RawEntityLoader.load(Fixtures.questieDbRoot().resolve("data/Forever/foreverItemDB.lua"), EntityKind.ITEM, itemSchema(), new ProblemCollector());
    assertThat(t.get(14149, "name")).isEqualTo(new LuaString("Subterranean Cape"));
    assertThat(((LuaTable) t.get(14149, "npcDrops")).array()).contains(LuaNumber.of(11520));
    assertThat(t.get(25, "npcDrops")).isEqualTo(LuaNil.NIL);
    assertThat(LuaValues.asLong(t.get(25, "itemLevel"))).hasValue(2);
  }

  @Test
  void loadsNpcSpawns() {
    EntityTable t = RawEntityLoader.load(Fixtures.questieDbRoot().resolve("data/Forever/foreverNpcDB.lua"), EntityKind.NPC, npcSchema(), new ProblemCollector());
    LuaTable spawns = (LuaTable) t.get(448, "spawns");   // Hogger
    assertThat(spawns.get(12)).isInstanceOf(LuaTable.class);
  }

  @Test
  void loadsEveryFixtureRowWithoutProblems() {
    ProblemCollector problems = new ProblemCollector();
    Path data = Fixtures.questieDbRoot().resolve("data/Forever");
    EntityTable items = RawEntityLoader.load(data.resolve("foreverItemDB.lua"), EntityKind.ITEM, itemSchema(), problems);
    EntityTable npcs = RawEntityLoader.load(data.resolve("foreverNpcDB.lua"), EntityKind.NPC, npcSchema(), problems);
    EntityTable objects = RawEntityLoader.load(data.resolve("foreverObjectDB.lua"), EntityKind.OBJECT, schema("object"), problems);
    EntityTable quests = RawEntityLoader.load(data.resolve("foreverQuestDB.lua"), EntityKind.QUEST, schema("quest"), problems);

    assertThat(problems.problems()).isEmpty();
    assertThat(items.rows().keySet()).containsExactly(25L, 730L, 2874L, 5191L, 14145L, 14148L, 14149L, 16800L, 17204L, 19019L,
        19682L);
    assertThat(npcs.rows().keySet()).containsExactly(448L, 639L, 1259L, 11502L, 11518L, 11520L, 12118L);
    assertThat(objects.size()).isEqualTo(2);
    assertThat(quests.get(2, "name")).isEqualTo(new LuaString("Sharptalon's Claw"));
    assertThat(npcs.contains(185333)).isFalse();
  }

  @Test
  void reportsFieldsBeyondFieldCountAndNonTableRows(@TempDir Path tmp) throws IOException {
    Path db = tmp.resolve("foreverObjectDB.lua");
    Files.writeString(db, """
        QuestieDB.objectData = [[return {
        [1] = {"Chest",nil,nil,nil,12,nil,nil,"extra"},
        [2] = "not a row",
        }]]
        """);
    ProblemCollector problems = new ProblemCollector();

    EntityTable t = RawEntityLoader.load(db, EntityKind.OBJECT, schema("object"), problems);

    assertThat(t.rows().keySet()).containsExactly(1L);
    assertThat(LuaValues.asLong(t.get(1, "zoneID"))).hasValue(12);
    assertThat(problems.problems()).extracting(Problem::message).containsExactlyInAnyOrder(
        "OBJECT 1 has field 8 beyond fieldCount 7",
        "OBJECT 2 is \"not a row\", not a table");
    assertThat(problems.problems()).allMatch(p -> p.file().startsWith("foreverObjectDB.lua"));
  }

  @Test
  void entityTableCreatesNilFilledRowsAndSetsFields() {
    EntityTable t = new EntityTable(EntityKind.ITEM, itemSchema());
    assertThat(t.contains(190179)).isFalse();

    t.create(190179);
    t.set(190179, "name", new LuaString("Avelina's Heart"));

    assertThat(t.contains(190179)).isTrue();
    assertThat(t.size()).isEqualTo(1);
    assertThat(t.rows().get(190179L)).hasSize(16);
    assertThat(t.get(190179, "name")).isEqualTo(new LuaString("Avelina's Heart"));
    assertThat(t.get(190179, "itemLevel")).isEqualTo(LuaNil.NIL);
  }
}
