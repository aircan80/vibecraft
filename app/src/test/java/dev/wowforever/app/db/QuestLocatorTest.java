package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuestLocatorTest {
  @Test
  void unknownQuestIsEmpty() {
    assertThat(FixtureDb.repositories().questLocator().load(-1)).isEmpty();
  }

  @Test
  void offMapSpawnsDropOut(@TempDir Path temp) throws Exception {
    Path file = temp.resolve("fixture.sqlite");
    Files.copy(FixtureDb.path(), file);
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + file); var st = c.createStatement()) {
      st.executeUpdate("INSERT OR REPLACE INTO item_npc_drop(item_id,npc_id,origin) VALUES(1931,448,'questie')");
      st.executeUpdate("DELETE FROM npc_spawn WHERE npc_id = 448");
      st.executeUpdate("INSERT INTO npc_spawn(npc_id, zone_id, x, y) VALUES(448, 12, -1, -1)");
    }
    try (Database db = Database.open(file)) {
      var data = new QuestLocator(db).load(176).orElseThrow();
      assertThat(data.objectives().getFirst().sources()).filteredOn(s -> s.ref().kind() == EntityKind.NPC)
          .filteredOn(s -> s.ref().id() == 448).singleElement().satisfies(s -> assertThat(s.spawns()).isEmpty());
    }
  }
  @Test
  void escortWithoutPoints(@TempDir Path temp) throws Exception {
    Path file = temp.resolve("escort.sqlite");
    Files.copy(FixtureDb.path(), file);
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + file); var st = c.createStatement()) {
      st.executeUpdate("DELETE FROM quest_objective WHERE quest_id = 176");
      st.executeUpdate("DELETE FROM quest_objective_point WHERE quest_id = 176");
      st.executeUpdate("INSERT INTO quest_objective(quest_id,ordinal,kind,text)"
          + " VALUES(176,0,'explore','Escort the caravan')");
    }
    try (Database db = Database.open(file)) {
      var row = new QuestLocator(db).load(176).orElseThrow().objectives().getFirst();
      assertThat(row.verb()).isEqualTo(dev.wowforever.app.model.ObjectiveVerb.ESCORT);
      assertThat(row.sources()).isEmpty();
      assertThat(row.points()).isEmpty();
    }
  }

  @Test
  void sourcesSortByRateThenKindAndKeepUnknowns(@TempDir Path temp) throws Exception {
    Path file = temp.resolve("sources.sqlite");
    Files.copy(FixtureDb.path(), file);
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + file); var st = c.createStatement()) {
      st.executeUpdate("DELETE FROM item_npc_drop WHERE item_id = 1931");
      st.executeUpdate("DELETE FROM item_object_drop WHERE item_id = 1931");
      st.executeUpdate("INSERT INTO item_npc_drop(item_id,npc_id,drop_pct,pct_source,origin) VALUES"
          + "(1931,448,84,'questie-wowhead','questie'),(1931,999991,NULL,NULL,'questie'),"
          + "(1931,999992,90,'vmangos','questie')");
      st.executeUpdate("INSERT INTO item_object_drop(item_id,object_id) VALUES(1931,999993)");
    }
    try (Database db = Database.open(file)) {
      var sources = new QuestLocator(db).load(176).orElseThrow().objectives().getFirst().sources();
      assertThat(sources).extracting(s -> s.ref().id()).containsExactly(999992L,448L,999991L,999993L);
      assertThat(sources.getFirst().pctSource()).isEqualTo("vmangos");
      assertThat(sources.get(2).name()).isNull();
      assertThat(sources.get(2).spawns()).isEmpty();
    }
  }

  @Test
  void factsKeepRestrictionsAndSeparateSortsFromZones(@TempDir Path temp) throws Exception {
    Path file = temp.resolve("facts.sqlite");
    Files.copy(FixtureDb.path(), file);
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + file); var st = c.createStatement()) {
      st.executeUpdate("UPDATE quest SET zone_or_sort=-24,sort_label='Herbalism',required_races=77,"
          + "required_classes=8,repeatable=1 WHERE id=176");
    }
    try (Database db = Database.open(file)) {
      var facts = new QuestRepository(db).facts(176).orElseThrow();
      assertThat(facts.zoneId()).isNull();
      assertThat(facts.zoneName()).isNull();
      assertThat(facts.sortLabel()).isEqualTo("Herbalism");
      assertThat(facts.requiredRaces()).isEqualTo(77);
      assertThat(facts.requiredClasses()).isEqualTo(8);
      assertThat(facts.repeatable()).isTrue();
      assertThat(new QuestRepository(db).facts(-1)).isEmpty();
    }
  }
}
