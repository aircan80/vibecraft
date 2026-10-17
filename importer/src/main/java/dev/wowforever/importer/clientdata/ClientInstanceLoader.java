package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.zones.Zone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Dungeons from the client's own tables (3B Task 17): the WoW: Forever dungeons that AtlasLoot, written for Classic,
 * does not know.
 *
 * <ul>
 *   <li>A candidate is a {@code Map} row of {@code InstanceType} 1 (dungeon) and {@code ExpansionID} 0, or a map that
 *       only {@code DungeonEncounter} names (no {@code Map} row; build 1.60.1.70450: 3002).
 *   <li>A candidate whose map id is the client map id of an AtlasLoot instance (AtlasLoot's {@code InstanceID}, such
 *       as 36 for The Deadmines) is AtlasLoot's, counted as {@value #ATLASLOOT} and not loaded: LFGDungeons lists the
 *       Classic dungeons too, and AtlasLoot's rows carry loot.
 *   <li>The others are loaded when the {@code Map}'s {@code MapName_lang} equals an {@code LFGDungeons.Name_lang}
 *       after both are lower-cased and a leading {@code "the "} is removed ({@link #joinKey}): Map "The Hall of
 *       Thanes" is LFGDungeons "Hall of Thanes". When several LFGDungeons rows share a key, the lowest {@code ID}
 *       wins. Candidates without a match (a test map, a Season of Discovery leftover, a map without a name) are
 *       counted as {@value #UNMATCHED} and noted.
 *   <li>{@link ClientInstance#tunedMinLevel()}: the {@code ContentTuning.MinLevelSquish} of the LFGDungeons row's
 *       {@code ContentTuningID}; null when either is 0 or the row is absent, counted as {@value #NO_TUNING}.
 *       {@code MaxLevelSquish} repeats the minimum and {@code LfgMinLevel}/{@code LfgMaxLevel} are 0 or inconsistent
 *       in build 1.60.1.70450, so neither is read.
 *   <li>Encounters: the {@code DungeonEncounter} rows of the map with {@code DifficultyID} 0, by {@code OrderIndex}
 *       then {@code ID}.
 * </ul>
 *
 * <p>{@code LFGDungeons}, {@code ContentTuning} and {@code DungeonEncounter} joined the fetched tables with this loader,
 * so they are optional client data, like the crafting tables of {@link CraftLoader}: without one of them nothing is
 * loaded, counted as {@value #SKIPPED} and noted with the missing tables. A table that is present must have every
 * column read here.
 *
 * <p>Counts {@value #INSTANCES}, {@value #ENCOUNTERS} (of the loaded instances), {@value #UNMATCHED},
 * {@value #NO_TUNING} and {@value #ATLASLOOT}.
 */
public final class ClientInstanceLoader {
  public static final String INSTANCES = "clientdata.instances";
  public static final String ENCOUNTERS = "clientdata.encounters";
  public static final String UNMATCHED = "clientdata.instances.unmatched";
  public static final String NO_TUNING = "clientdata.instances.noTuning";
  public static final String ATLASLOOT = "clientdata.instances.atlasloot";
  /** 1, with a note naming the missing tables, when the loader is skipped; absent otherwise. */
  public static final String SKIPPED = "clientdata.instances.skipped";
  /** The optional tables the loader needs ({@code Map} is a required table since 2A). */
  static final List<String> INSTANCE_TABLES = List.of("LFGDungeons", "ContentTuning", "DungeonEncounter");
  static final int DUNGEON = 1;
  private static final String ARTICLE = "the ";
  /** How many maps the unmatched note names. */
  private static final int NOTE_EXAMPLES = 25;

  static final List<String> MAP_COLUMNS = List.of("ID", "MapName_lang", "InstanceType", "ExpansionID", "AreaTableID");
  static final List<String> LFG_COLUMNS = List.of("ID", "Name_lang", "ContentTuningID");
  static final List<String> TUNING_COLUMNS = List.of("ID", "MinLevelSquish");
  static final List<String> ENCOUNTER_COLUMNS = List.of("ID", "Name_lang", "MapID", "DifficultyID", "OrderIndex");
  static final List<String> AREA_COLUMNS = List.of("ID", "AreaName_lang");

  private ClientInstanceLoader() {}

  /**
   * The client dungeons of {@code src} by map id; empty (counted as {@value #SKIPPED}) when it lacks a table of
   * {@link #INSTANCE_TABLES}.
   *
   * @param atlasLootMapIds the client map ids of the AtlasLoot instances (their {@code InstanceID}), not loaded again
   * @throws IllegalStateException when {@code Map} is missing, or a table read here lacks a column, naming both
   */
  public static List<ClientInstance> load(ClientDataSource src, Set<Long> atlasLootMapIds, ImportReport report) {
    List<String> missing = INSTANCE_TABLES.stream().filter(t -> !src.hasTable(t)).toList();
    if (!missing.isEmpty()) {
      report.count(SKIPPED, 1);
      report.count(INSTANCES, 0);
      report.note(SKIPPED + ": the client data has no " + String.join(", ", missing)
          + " (converted before the dungeon tables were fetched); no client dungeons imported. Fetch and convert"
          + " them with --tables " + String.join(",", missing));
      return List.of();
    }
    CsvTable maps = require(src.table("Map"), MAP_COLUMNS);
    Map<String, CsvTable.Row> lfgByKey = lfgByKey(require(src.table("LFGDungeons"), LFG_COLUMNS));
    Map<Long, CsvTable.Row> tuning = require(src.table("ContentTuning"), TUNING_COLUMNS).indexByLong("ID");
    Map<Long, List<ClientEncounter>> encounters =
        encounters(require(src.table("DungeonEncounter"), ENCOUNTER_COLUMNS));

    Map<Long, String> unmatched = new TreeMap<>();
    Set<Long> withMapRow = new TreeSet<>();
    List<ClientInstance> out = new ArrayList<>();
    long covered = 0;
    long noTuning = 0;
    for (CsvTable.Row m : maps.rows()) {
      OptionalLong id = m.getLong("ID");
      if (id.isEmpty()) {
        continue;
      }
      long mapId = id.getAsLong();
      withMapRow.add(mapId);
      if (m.intOr("InstanceType", -1) != DUNGEON || m.intOr("ExpansionID", -1) != 0) {
        continue;
      }
      if (atlasLootMapIds.contains(mapId)) {
        covered++;
        continue;
      }
      String name = m.get("MapName_lang");
      CsvTable.Row lfg = name.isBlank() ? null : lfgByKey.get(joinKey(name));
      if (lfg == null) {
        unmatched.put(mapId, name.isBlank() ? "no name" : name);
        continue;
      }
      Integer tuned = tunedMinLevel(lfg, tuning);
      if (tuned == null) {
        noTuning++;
      }
      long area = m.longOr("AreaTableID", 0);
      out.add(new ClientInstance(mapId, name, tuned, area > 0 ? (int) area : null,
          encounters.getOrDefault(mapId, List.of())));
    }
    for (long mapId : encounters.keySet()) {
      if (!withMapRow.contains(mapId) && !atlasLootMapIds.contains(mapId)) {
        unmatched.put(mapId, "no Map row");
      }
    }
    out.sort(Comparator.comparingLong(ClientInstance::mapId));

    report.count(INSTANCES, out.size());
    report.count(ENCOUNTERS, out.stream().mapToLong(i -> i.encounters().size()).sum());
    report.count(UNMATCHED, unmatched.size());
    report.count(NO_TUNING, noTuning);
    report.count(ATLASLOOT, covered);
    if (!unmatched.isEmpty()) {
      report.note(UNMATCHED + ": dungeon maps without an LFGDungeons row of their name, not imported: "
          + unmatched.entrySet().stream().limit(NOTE_EXAMPLES).map(e -> e.getKey() + " (" + e.getValue() + ")")
          .collect(Collectors.joining(", ")) + (unmatched.size() > NOTE_EXAMPLES ? ", ..." : ""));
    }
    return List.copyOf(out);
  }

  /**
   * The zone of each instance, by map id: the zone named like the {@code AreaTable} area of its
   * {@link ClientInstance#areaTableId()}, else the zone named like the instance itself; among zones of one name a
   * {@code dungeon} zone wins, then the lowest id. An instance without such a zone has no entry. In build
   * 1.60.1.70450 only City of Dalaran has an area (16544); Questie names the other three dungeons' zones after them.
   */
  public static Map<Long, Long> zoneIds(ClientDataSource src, List<ClientInstance> instances, Map<Long, Zone> zones) {
    Map<String, Zone> byName = new HashMap<>();
    Comparator<Zone> best = Comparator.comparing((Zone z) -> !"dungeon".equals(z.kind())).thenComparingLong(Zone::id);
    for (Zone z : zones.values()) {
      if (z.name() != null) {
        byName.merge(z.name(), z, (a, b) -> best.compare(a, b) <= 0 ? a : b);
      }
    }
    Map<Long, String> areaNames = instances.stream().anyMatch(i -> i.areaTableId() != null)
        ? areaNames(require(src.table("AreaTable"), AREA_COLUMNS)) : Map.of();
    Map<Long, Long> out = new LinkedHashMap<>();
    for (ClientInstance i : instances) {
      Optional.ofNullable(i.areaTableId()).map(a -> areaNames.get(a.longValue())).map(byName::get)
          .or(() -> Optional.ofNullable(byName.get(i.name())))
          .ifPresent(z -> out.put(i.mapId(), z.id()));
    }
    return out;
  }

  /** {@code name} lower-cased (root locale), without a leading {@code "the "}: the join key of Map and LFGDungeons. */
  static String joinKey(String name) {
    String key = name.strip().toLowerCase(Locale.ROOT);
    return key.startsWith(ARTICLE) ? key.substring(ARTICLE.length()).strip() : key;
  }

  private static Map<String, CsvTable.Row> lfgByKey(CsvTable lfg) {
    Map<String, CsvTable.Row> out = new HashMap<>();
    for (CsvTable.Row r : lfg.rows()) {
      String name = r.get("Name_lang");
      if (r.getLong("ID").isEmpty() || name.isBlank()) {
        continue;
      }
      out.merge(joinKey(name), r, (a, b) -> b.longOr("ID", 0) < a.longOr("ID", 0) ? b : a);
    }
    return out;
  }

  private static Integer tunedMinLevel(CsvTable.Row lfg, Map<Long, CsvTable.Row> tuning) {
    long id = lfg.longOr("ContentTuningID", 0);
    CsvTable.Row t = id > 0 ? tuning.get(id) : null;
    int level = t == null ? 0 : t.intOr("MinLevelSquish", 0);
    return level > 0 ? level : null;
  }

  private static Map<Long, List<ClientEncounter>> encounters(CsvTable table) {
    Map<Long, List<ClientEncounter>> out = new TreeMap<>();
    for (CsvTable.Row r : table.rows()) {
      OptionalLong map = r.getLong("MapID");
      OptionalLong id = r.getLong("ID");
      if (map.isEmpty() || id.isEmpty() || r.intOr("DifficultyID", 0) != 0) {
        continue;
      }
      out.computeIfAbsent(map.getAsLong(), k -> new ArrayList<>())
          .add(new ClientEncounter(id.getAsLong(), r.get("Name_lang"), r.intOr("OrderIndex", 0)));
    }
    Comparator<ClientEncounter> order =
        Comparator.comparingInt(ClientEncounter::orderIndex).thenComparingLong(ClientEncounter::id);
    out.values().forEach(l -> l.sort(order));
    return out;
  }

  private static Map<Long, String> areaNames(CsvTable areas) {
    Map<Long, String> out = new HashMap<>();
    for (CsvTable.Row r : areas.rows()) {
      r.getLong("ID").ifPresent(id -> out.putIfAbsent(id, r.get("AreaName_lang")));
    }
    return out;
  }

  private static CsvTable require(CsvTable t, List<String> columns) {
    for (String col : columns) {
      if (!t.hasColumn(col)) {
        throw new IllegalStateException("client table " + t.source() + " has no column " + col);
      }
    }
    return t;
  }
}
