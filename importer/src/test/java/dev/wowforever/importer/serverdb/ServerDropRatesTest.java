package dev.wowforever.importer.serverdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.drops.DropRate;
import dev.wowforever.importer.drops.DropRateLoader;
import dev.wowforever.importer.drops.DropRates;
import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.schema.MetaLoader;
import dev.wowforever.importer.sqlite.ImportModel;
import dev.wowforever.importer.sqlite.SqliteWriter;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaTable;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongPredicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerDropRatesTest {
  private static final LongPredicate ALL_NPCS = id -> true;

  @TempDir Path tmp;

  private DropRates rates;
  private ImportReport report;

  private DropRates load(ServerDbFixture db) throws Exception {
    return load(db, ALL_NPCS);
  }

  private DropRates load(ServerDbFixture db, LongPredicate knownNpc) throws Exception {
    rates = new DropRates();
    report = new ImportReport();
    try (ServerDbSource source = ServerDbSource.open(db.write(tmp.resolve("db"))).orElseThrow()) {
      ServerDropRates.load(source, knownNpc, rates, report);
    }
    return rates;
  }

  private double pct(long npc, long item) {
    Optional<DropRate> r = rates.get(npc, item);
    assertThat(r).as("a rate for npc %d item %d", npc, item).isPresent();
    assertThat(r.get().source()).isEqualTo("vmangos");
    return r.get().percent();
  }

  @Test
  void aPositiveChanceIsTheDropPercentage() throws Exception {
    load(new ServerDbFixture().creature(100, 100).loot(100, 1, 25.5, 0, 1, 1));
    assertThat(pct(100, 1)).isEqualTo(25.5);
    assertThat(report.get("serverdb.loot.rows")).isEqualTo(1);
    assertThat(report.get("serverdb.rates")).isEqualTo(1);
  }

  @Test
  void aNegativeChanceIsAQuestOnlyDropAtItsAbsoluteValue() throws Exception {
    load(new ServerDbFixture().creature(448, 448).loot(448, 1931, -100, 0, 1, 1).loot(448, 2, -35, 0, 1, 1));
    assertThat(pct(448, 1931)).isEqualTo(100.0);
    assertThat(pct(448, 2)).isEqualTo(35.0);
    assertThat(report.get("serverdb.rates.questOnly")).isEqualTo(2);
  }

  @Test
  void creaturesReachTheirLootThroughLootIdAtTheNewestPatch() throws Exception {
    load(new ServerDbFixture()
        .creature(100, 100).loot(100, 1, 10, 0, 1, 1)
        .creature(101, 100) // shares 100's table
        .creature(102, 0) // no loot
        .creature(103, 0, 0).creature(103, 5, 100) // loot from patch 5 on
        .lootPatched(100, 2, 50, 0, 6) // removed after patch 6
        .lootPatched(100, 3, 60, 7, 10));
    assertThat(pct(101, 1)).isEqualTo(10.0);
    assertThat(pct(103, 1)).isEqualTo(10.0);
    assertThat(rates.get(102, 1)).isEmpty();
    assertThat(rates.get(100, 2)).as("a pre-1.12 row").isEmpty();
    assertThat(pct(100, 3)).isEqualTo(60.0);
    assertThat(report.get("serverdb.loot.rows")).isEqualTo(2);
  }

  @Test
  void aReferenceMultipliesItsChanceIntoTheReferencedRows() throws Exception {
    load(new ServerDbFixture().creature(100, 100)
        .lootRef(100, 500, 50, 0, 1)
        .ref(500, 10, 20, 0, 1, 1)
        .ref(500, 11, -40, 0, 1, 1));
    assertThat(pct(100, 10)).isCloseTo(10.0, within(1e-9));
    assertThat(pct(100, 11)).isCloseTo(20.0, within(1e-9));
    assertThat(rates.get(100, 500)).as("the reference row is not an item").isEmpty();
    assertThat(report.get("serverdb.reference.rows")).isEqualTo(2);
  }

  @Test
  void zeroChanceRowsShareWhatTheirGroupLeavesEqually() throws Exception {
    load(new ServerDbFixture().creature(100, 100)
        .lootRef(100, 501, 100, 0, 1)
        .ref(501, 20, 40, 1, 1, 1)
        .ref(501, 21, 0, 1, 1, 1)
        .ref(501, 22, 0, 1, 1, 1)
        // a group of the creature's own table, with a reference in it
        .loot(100, 30, 0, 2, 1, 1)
        .loot(100, 31, 0, 2, 1, 1)
        .loot(100, 32, 0, 2, 1, 1)
        .lootRef(100, 502, 70, 2, 1)
        .ref(502, 40, 100, 0, 1, 1));
    assertThat(pct(100, 20)).isCloseTo(40.0, within(1e-9));
    assertThat(pct(100, 21)).isCloseTo(30.0, within(1e-9));
    assertThat(pct(100, 22)).isCloseTo(30.0, within(1e-9));
    assertThat(pct(100, 30)).isCloseTo(10.0, within(1e-9));
    assertThat(pct(100, 40)).isCloseTo(70.0, within(1e-9));
  }

  @Test
  void aGroupWhoseExplicitChancesFillItLeavesNothingToShare() throws Exception {
    load(new ServerDbFixture().creature(100, 100)
        .loot(100, 1, 60, 1, 1, 1).loot(100, 2, 40, 1, 1, 1).loot(100, 3, 0, 1, 1, 1)
        .loot(100, 4, 0, 0, 1, 1)); // ungrouped zero chance never drops
    assertThat(pct(100, 1)).isEqualTo(60.0);
    assertThat(rates.get(100, 3)).isEmpty();
    assertThat(rates.get(100, 4)).isEmpty();
    assertThat(report.get("serverdb.zeroChance.ungrouped")).isEqualTo(1);
  }

  @Test
  void aReferenceRolledSeveralTimesGivesAtLeastOneDropMoreOften() throws Exception {
    load(new ServerDbFixture().creature(100, 100).lootRef(100, 500, 100, 0, 2).ref(500, 10, 50, 0, 1, 1));
    assertThat(pct(100, 10)).isCloseTo(75.0, within(1e-9));
  }

  @Test
  void anItemReachedTwiceCombinesAsIndependentRolls() throws Exception {
    load(new ServerDbFixture().creature(100, 100)
        .loot(100, 10, 50, 0, 1, 1).lootRef(100, 500, 100, 0, 1).ref(500, 10, 50, 0, 1, 1));
    assertThat(pct(100, 10)).isCloseTo(75.0, within(1e-9));
  }

  @Test
  void nestedReferencesResolveFiveLevelsDeepThenStop() throws Exception {
    ServerDbFixture db = new ServerDbFixture().creature(100, 100).lootRef(100, 601, 100, 0, 1);
    for (int level = 1; level <= 7; level++) {
      db.ref(600 + level, 700 + level, 100, 0, 1, 1).refRef(600 + level, 600 + level + 1, 100, 0, 1);
    }
    load(db);
    for (int level = 1; level <= 5; level++) {
      assertThat(pct(100, 700 + level)).as("level " + level).isEqualTo(100.0);
    }
    assertThat(rates.get(100, 706)).isEmpty();
    assertThat(report.get("serverdb.references.depthCapped")).isEqualTo(1);
  }

  @Test
  void aReferenceCycleStopsAtTheCap() throws Exception {
    load(new ServerDbFixture().creature(100, 100).lootRef(100, 800, 100, 0, 1)
        .ref(800, 1, 50, 0, 1, 1).refRef(800, 800, 100, 0, 1));
    assertThat(pct(100, 1)).isCloseTo(100 * (1 - Math.pow(0.5, 5)), within(1e-9));
    assertThat(report.get("serverdb.references.depthCapped")).isEqualTo(1);
  }

  @Test
  void aMissingReferenceIsCounted() throws Exception {
    load(new ServerDbFixture().creature(100, 100).lootRef(100, 999, 100, 0, 1).loot(100, 1, 5, 0, 1, 1));
    assertThat(pct(100, 1)).isEqualTo(5.0);
    assertThat(report.get("serverdb.references.missing")).isEqualTo(1);
  }

  @Test
  void creaturesMissingFromTheNpcTableAreCountedNotRated() throws Exception {
    load(new ServerDbFixture()
            .creature(100, 100).loot(100, 1, 5, 0, 1, 1)
            .creature(200, 200).loot(200, 1, 5, 0, 1, 1)
            .creature(300, 100)
            .creature(400, 0),
        id -> id == 100);
    assertThat(pct(100, 1)).isEqualTo(5.0);
    assertThat(rates.get(200, 1)).isEmpty();
    assertThat(rates.get(300, 1)).isEmpty();
    assertThat(report.get("serverdb.unmatched.npcs")).as("200 and 300; 400 has no loot").isEqualTo(2);
    assertThat(report.get("serverdb.creatures")).isEqualTo(3);
    assertThat(report.get("serverdb.rates")).isEqualTo(1);
  }

  @Test
  void vmangosRanksAfterQuestieCorrectionsAndBeforeEveryOtherSource() throws Exception {
    load(new ServerDbFixture().creature(100, 100)
        .loot(100, 1, 12, 0, 1, 1).loot(100, 2, 12, 0, 1, 1).loot(100, 3, 12, 0, 1, 1));
    rates.put(100, 1, new DropRate(60, "questie-correction"), DropRateLoader.QUESTIE_CORRECTION);
    rates.put(100, 1, new DropRate(50, "questie-wowhead"), DropRateLoader.QUESTIE_WOWHEAD);
    rates.put(100, 2, new DropRate(50, "questie-wowhead"), DropRateLoader.QUESTIE_WOWHEAD);
    rates.put(100, 2, new DropRate(40, "atlasloot"), DropRateLoader.ATLASLOOT);
    rates.putItem(3, new DropRate(30, "atlasloot-override"), DropRateLoader.ATLASLOOT_OVERRIDE);
    assertThat(rates.get(100, 1).orElseThrow().source()).isEqualTo("questie-correction");
    assertThat(rates.get(100, 2).orElseThrow()).isEqualTo(new DropRate(12, "vmangos"));
    assertThat(rates.get(100, 3).orElseThrow()).isEqualTo(new DropRate(12, "vmangos"));
    assertThat(rates.allSources(100, 1)).extracting(DropRate::source)
        .containsExactly("questie-correction", "vmangos", "questie-wowhead");
    assertThat(DropRateLoader.QUESTIE_CORRECTION).isLessThan(ServerDropRates.PRECEDENCE);
    assertThat(ServerDropRates.PRECEDENCE).isLessThan(DropRateLoader.ATLASLOOT_OVERRIDE);
  }

  @Test
  void aVmangosRateFarFromTheOthersIsFlaggedAsDisagreeing() throws Exception {
    load(new ServerDbFixture().creature(11520, 11520)
        .loot(11520, 14149, 18.0, 0, 1, 1).loot(11520, 14148, 40.0, 0, 1, 1));
    rates.put(11520, 14149, new DropRate(17.5, "questie-wowhead"), DropRateLoader.QUESTIE_WOWHEAD);
    rates.put(11520, 14148, new DropRate(10.0, "questie-wowhead"), DropRateLoader.QUESTIE_WOWHEAD);
    EntityTable items = new EntityTable(EntityKind.ITEM,
        MetaLoader.load(Fixtures.questieDbRoot().resolve("src/meta/itemMeta.lua")));
    for (long item : List.of(14149L, 14148L)) {
      items.create(item);
      items.set(item, "name", new LuaString("item " + item));
      LuaTable npcs = new LuaTable();
      npcs.put(LuaNumber.of(1), LuaNumber.of(11520));
      items.set(item, "npcDrops", npcs);
    }
    Map<EntityKind, EntityTable> tables = new EnumMap<>(EntityKind.class);
    tables.put(EntityKind.ITEM, items);
    ImportReport written = new ImportReport();
    Path out = tmp.resolve("t.sqlite");
    SqliteWriter.write(out, new ImportModel(tables, rates, new LinkedHashMap<>(), List.of(), new LinkedHashMap<>()),
        written);
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + out)) {
      assertThat(scalar(c, "select source_count || '/' || disagrees || '/' || pct_source from item_npc_drop"
          + " where item_id = 14149")).isEqualTo("2/0/vmangos");
      assertThat(scalar(c, "select source_count || '/' || disagrees || '/' || drop_pct from item_npc_drop"
          + " where item_id = 14148")).isEqualTo("2/1/40.0");
      assertThat(scalar(c, "select count(*) from drop_rate_source where source = 'vmangos'")).isEqualTo(2);
    }
    assertThat(written.get("provenance.sources.vmangos")).isEqualTo(2);
    assertThat(written.render()).contains("vmangos vs questie-wowhead: 1 disagreeing of 2 shared");
  }

  private static Object scalar(Connection c, String sql) throws Exception {
    try (ResultSet rs = c.createStatement().executeQuery(sql)) {
      assertThat(rs.next()).isTrue();
      return rs.getObject(1);
    }
  }
}
