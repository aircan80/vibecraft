package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.zones.Zone;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The dungeons of the client-data fixture, whose Map, LFGDungeons, ContentTuning and DungeonEncounter rows come from
 * build 1.60.1.70450: the four Forever dungeons City of Dalaran (2959), Excavation Site: Wetlands (2998), Ruins of
 * Lordaeron (2999) and The Hall of Thanes (3065); Deadmines (36), which AtlasLoot covers (its InstanceID is 36); map
 * 3002, which only DungeonEncounter names; and LFGDungeons 1 "Wailing Caverns", whose map is not in the fixture.
 */
class ClientInstanceLoaderTest {
  /** The client map ids of the fixture's AtlasLoot instances (Ragefire Chasm, The Deadmines, Molten Core). */
  private static final Set<Long> ATLASLOOT_MAPS = Set.of(389L, 36L, 409L);
  private static List<ClientInstance> instances;
  private static ImportReport report;
  private static Map<Long, ClientInstance> byMap;

  @BeforeAll
  static void load() {
    report = new ImportReport();
    instances = ClientInstanceLoader.load(new ClientDataSource(Fixtures.clientDataRoot()), ATLASLOOT_MAPS, report);
    byMap = instances.stream().collect(Collectors.toMap(ClientInstance::mapId, Function.identity()));
  }

  @Test
  void fourDungeonsWithTheirEncountersInOrder() {
    assertThat(instances).extracting(ClientInstance::mapId).containsExactly(2959L, 2998L, 2999L, 3065L);
    ClientInstance dalaran = byMap.get(2959L);
    assertThat(dalaran.name()).isEqualTo("City of Dalaran");
    assertThat(dalaran.tunedMinLevel()).isEqualTo(28);
    assertThat(dalaran.areaTableId()).isEqualTo(16544);
    assertThat(dalaran.encounters()).hasSize(9);
    assertThat(dalaran.encounters().get(0).name()).isEqualTo("Arcane Anomaly");
    assertThat(dalaran.encounters().get(8).name()).isEqualTo("Mana Wraith");
    assertThat(byMap.get(2998L).encounters()).extracting(ClientEncounter::name, ClientEncounter::orderIndex)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("Saltspine", 0),
            org.assertj.core.groups.Tuple.tuple("Shadetooth", 1000),
            org.assertj.core.groups.Tuple.tuple("Highland Horror", 1500),
            org.assertj.core.groups.Tuple.tuple("Relic Guardian", 2000));
    assertThat(byMap.get(2998L).encounters().get(2).id()).isEqualTo(3644);
    assertThat(byMap.get(2998L).areaTableId()).as("AreaTableID 0 is no area").isNull();
    assertThat(byMap.get(3065L).tunedMinLevel()).isEqualTo(13);
    assertThat(byMap.get(2999L).tunedMinLevel()).isEqualTo(15);
    assertThat(byMap.get(2999L).encounters()).extracting(ClientEncounter::name).containsExactly("Witherfang",
        "The Abandoned", "The Butcher", "Rath'mael", "Lordaeron Captain", "Viktor the Vile", "Bjork");

    assertThat(report.get(ClientInstanceLoader.INSTANCES)).isEqualTo(4);
    assertThat(report.get(ClientInstanceLoader.ENCOUNTERS)).isEqualTo(24);
    assertThat(report.get(ClientInstanceLoader.NO_TUNING)).isZero();
    assertThat(report.get(ClientInstanceLoader.SKIPPED)).isZero();
  }

  @Test
  void theUnnamedMap3002IsCountedUnmatchedNotLoaded() {
    assertThat(byMap).doesNotContainKey(3002L);
    assertThat(report.get(ClientInstanceLoader.UNMATCHED)).isEqualTo(1);
    assertThat(report.notes()).anyMatch(n -> n.startsWith(ClientInstanceLoader.UNMATCHED + ": ")
        && n.contains("3002"));
  }

  @Test
  void aDungeonAtlasLootCoversIsNotLoadedAgain() {
    // Map 36 "Deadmines" matches LFGDungeons 5 "Deadmines", but AtlasLoot's The Deadmines has InstanceID 36
    assertThat(byMap).doesNotContainKey(36L);
    assertThat(report.get(ClientInstanceLoader.ATLASLOOT)).isEqualTo(1);

    ImportReport r = new ImportReport();
    List<ClientInstance> all = ClientInstanceLoader.load(new ClientDataSource(Fixtures.clientDataRoot()), Set.of(), r);
    assertThat(all).extracting(ClientInstance::mapId).containsExactly(36L, 2959L, 2998L, 2999L, 3065L);
    assertThat(all.get(0).tunedMinLevel()).isEqualTo(16);
    assertThat(all.get(0).encounters()).extracting(ClientEncounter::name).containsExactly("Rhahk'Zor", "Sneed",
        "Miner Johnson", "Gilnid", "Captain Greenskin", "Mr. Smite", "Cookie", "Edwin VanCleef");
  }

  @Test
  void theArticleDoesNotBreakTheJoin() {
    // Map "The Hall of Thanes" against LFGDungeons "Hall of Thanes"
    assertThat(byMap.get(3065L).name()).isEqualTo("The Hall of Thanes");
    assertThat(byMap.get(3065L).encounters()).extracting(ClientEncounter::name).first().isEqualTo("Faldrim Anvilmar");
    assertThat(ClientInstanceLoader.joinKey("The Hall of Thanes")).isEqualTo(ClientInstanceLoader.joinKey("Hall of"
        + " Thanes")).isEqualTo("hall of thanes");
    assertThat(ClientInstanceLoader.joinKey("Theramore")).isEqualTo("theramore");
  }

  @Test
  void missingTablesSkipTheLoader(@TempDir Path tmp) throws Exception {
    copyFixtureCsv(tmp, "DungeonEncounter.csv");
    ImportReport r = new ImportReport();
    assertThat(ClientInstanceLoader.load(new ClientDataSource(tmp), Set.of(), r)).isEmpty();
    assertThat(r.get(ClientInstanceLoader.SKIPPED)).isEqualTo(1);
    assertThat(r.counters()).containsEntry(ClientInstanceLoader.INSTANCES, 0L);
    assertThat(r.notes()).anyMatch(n -> n.startsWith(ClientInstanceLoader.SKIPPED + ": ")
        && n.contains("DungeonEncounter") && !n.contains("LFGDungeons"));
  }

  @Test
  void theFixtureIsNotSkipped() {
    assertThat(report.counters()).doesNotContainKey(ClientInstanceLoader.SKIPPED);
  }

  @Test
  void otherDifficultiesAreLeftOutAndNoTuningIsCountedAndTiesGoById(@TempDir Path tmp) throws Exception {
    Path csv = Files.createDirectories(tmp.resolve("csv"));
    write(csv, "Map", MAP_HEADER + "500,D,\"Test Keep\",1,0,0\n501,D,\"Raid Hall\",2,0,0\n"
        + "502,D,\"New Keep\",1,1,0\n503,D,\"Old Cave\",1,0,77\n");
    // 500 and 503 are named; 501 is a raid and 502 an expansion map, neither is a candidate
    write(csv, "LFGDungeons", "ID,Name_lang,ContentTuningID\n7,\"Test Keep\",900\n8,\"the old cave\",0\n"
        + "9,\"Raid Hall\",900\n10,\"New Keep\",900\n");
    write(csv, "ContentTuning", "ID,MinLevelSquish\n900,0\n");
    write(csv, "DungeonEncounter", "Name_lang,ID,MapID,DifficultyID,OrderIndex\nHeroic,12,500,1,0\nSecond,11,500,0,5\n"
        + "First,10,500,0,5\nOpener,13,500,0,0\n");
    ImportReport r = new ImportReport();
    List<ClientInstance> got = ClientInstanceLoader.load(new ClientDataSource(tmp), Set.of(), r);

    assertThat(got).extracting(ClientInstance::mapId).containsExactly(500L, 503L);
    assertThat(got.get(0).encounters()).extracting(ClientEncounter::name).containsExactly("Opener", "First", "Second");
    assertThat(got.get(0).tunedMinLevel()).as("MinLevelSquish 0 is no level").isNull();
    assertThat(got.get(1).tunedMinLevel()).as("ContentTuningID 0 is no tuning").isNull();
    assertThat(got.get(1).areaTableId()).isEqualTo(77);
    assertThat(got.get(1).encounters()).isEmpty();
    assertThat(r.get(ClientInstanceLoader.NO_TUNING)).isEqualTo(2);
    assertThat(r.get(ClientInstanceLoader.ENCOUNTERS)).isEqualTo(3);
    assertThat(r.get(ClientInstanceLoader.UNMATCHED)).isZero();
  }

  @Test
  void aTableWithoutAColumnReadHereFailsNamingIt(@TempDir Path tmp) throws Exception {
    Path csv = Files.createDirectories(tmp.resolve("csv"));
    write(csv, "Map", MAP_HEADER);
    write(csv, "LFGDungeons", "ID,Name_lang\n");
    write(csv, "ContentTuning", "ID,MinLevelSquish\n");
    write(csv, "DungeonEncounter", "Name_lang,ID,MapID,DifficultyID,OrderIndex\n");
    assertThatThrownBy(() -> ClientInstanceLoader.load(new ClientDataSource(tmp), Set.of(), new ImportReport()))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("LFGDungeons")
        .hasMessageContaining("ContentTuningID");
  }

  @Test
  void zonesByAreaNameThenByMapName() {
    Map<Long, Zone> zones = Map.of(
        16544L, new Zone(16544, "City of Dalaran", 36L, "dungeon"),
        16558L, new Zone(16558, "City of Dalaran Dalaran", null, "zone"),
        16919L, new Zone(16919, "The Hall of Thanes", 1537L, "dungeon"),
        20000L, new Zone(20000, "The Hall of Thanes", null, "zone"),
        16611L, new Zone(16611, "Ruins of Lordaeron", 1497L, "dungeon"));
    Map<Long, Long> zoneIds = ClientInstanceLoader.zoneIds(new ClientDataSource(Fixtures.clientDataRoot()),
        instances, zones);
    // 2959's AreaTableID 16544 is the area "City of Dalaran"; the others have none and match by the Map name, a
    // dungeon zone first; 2998 matches no zone
    assertThat(zoneIds).containsExactlyInAnyOrderEntriesOf(Map.of(2959L, 16544L, 2999L, 16611L, 3065L, 16919L));
    // the area's name comes first: area 16544 is "City of Dalaran", whatever the map is called
    assertThat(ClientInstanceLoader.zoneIds(new ClientDataSource(Fixtures.clientDataRoot()),
        List.of(new ClientInstance(1, "Ruins of Lordaeron", null, 16544, List.of())), zones))
        .containsExactly(Map.entry(1L, 16544L));
  }

  private static final String MAP_HEADER = "ID,Directory,MapName_lang,InstanceType,ExpansionID,AreaTableID\n";

  /** The fixture's CSVs copied to {@code tmp/csv}, without {@code omit}. */
  private static void copyFixtureCsv(Path tmp, String... omit) throws Exception {
    Path csv = Files.createDirectories(tmp.resolve("csv"));
    List<String> skip = List.of(omit);
    try (var files = Files.list(Fixtures.clientDataRoot().resolve("csv"))) {
      for (Path f : (Iterable<Path>) files::iterator) {
        if (!skip.contains(f.getFileName().toString())) {
          Files.copy(f, csv.resolve(f.getFileName().toString()));
        }
      }
    }
  }

  private static void write(Path csv, String table, String text) throws Exception {
    Files.writeString(csv.resolve(table + ".csv"), text, StandardCharsets.UTF_8);
  }
}
