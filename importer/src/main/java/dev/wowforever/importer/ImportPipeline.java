package dev.wowforever.importer;

import dev.wowforever.importer.atlasloot.AtlasInstance;
import dev.wowforever.importer.atlasloot.AtlasLootBaseline;
import dev.wowforever.importer.atlasloot.AtlasLootLoader;
import dev.wowforever.importer.clientdata.ClientDataBundle;
import dev.wowforever.importer.clientdata.ClientDataStage;
import dev.wowforever.importer.corrections.CorrectionApplier;
import dev.wowforever.importer.corrections.CorrectionLayer;
import dev.wowforever.importer.corrections.CorrectionLayers;
import dev.wowforever.importer.corrections.CorrectionScope;
import dev.wowforever.importer.corrections.EnumLoader;
import dev.wowforever.importer.drops.DropRate;
import dev.wowforever.importer.drops.DropRateLoader;
import dev.wowforever.importer.drops.DropRates;
import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.entity.RawEntityLoader;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.schema.EntitySchema;
import dev.wowforever.importer.schema.MetaLoader;
import dev.wowforever.importer.serverdb.ServerDbStage;
import dev.wowforever.importer.serverdb.ServerDropRates;
import dev.wowforever.importer.source.SourceFetcher;
import dev.wowforever.importer.source.SourceRoots;
import dev.wowforever.importer.source.SourceSpec;
import dev.wowforever.importer.sqlite.ImportModel;
import dev.wowforever.importer.sqlite.SqliteWriter;
import dev.wowforever.importer.zones.Zone;
import dev.wowforever.importer.zones.ZoneLoader;
import dev.wowforever.lua.LuaTable;
import dev.wowforever.lua.LuaValue;
import dev.wowforever.lua.LuaValues;
import dev.wowforever.lua.Problem;
import dev.wowforever.lua.ProblemCollector;
import java.io.IOException;
import java.io.PrintStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs the whole import: sources, entities, corrections, drop rates (VMaNGOS ones included), zones, AtlasLoot, client
 * data, SQLite, report.
 */
public final class ImportPipeline {
  public static final String IMPORTER_VERSION = "0.1.0";
  private static final Set<String> RAID_CONTENT = Set.of("RAID20", "RAID40");
  private static final int PROBLEMS_NOTED_PER_STAGE = 20;

  private ImportPipeline() {}

  /** Runs the import and prints the rendered report to standard output. */
  public static ImportReport run(ImportOptions opts) throws IOException, SQLException {
    return run(opts, System.out);
  }

  /** Runs the import and prints the rendered report to {@code console}. */
  public static ImportReport run(ImportOptions opts, PrintStream console) throws IOException, SQLException {
    ImportReport report = new ImportReport();
    Map<String, ProblemCollector> problems = new LinkedHashMap<>();

    SourceSpec questieSpec = spec("questiedb");
    SourceSpec atlasSpec = spec("atlasloot");
    SourceFetcher fetcher = new SourceFetcher(opts.cacheDir(), HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build());
    SourceRoots roots = new SourceRoots(
        fetcher.resolve(questieSpec, opts.questieDbDir()), fetcher.resolve(atlasSpec, opts.atlasLootDir()));
    Path questieDb = roots.questieDb();

    Map<EntityKind, EntitySchema> schemas = new EnumMap<>(EntityKind.class);
    Map<EntityKind, EntityTable> tables = new EnumMap<>(EntityKind.class);
    ProblemCollector raw = stage(problems, "raw");
    for (EntityKind kind : EntityKind.values()) {
      EntitySchema schema = MetaLoader.load(
          questieDb.resolve("src/meta/" + kind.metaEntity().toLowerCase(Locale.ROOT) + "Meta.lua"));
      schemas.put(kind, schema);
      tables.put(kind, RawEntityLoader.load(
          questieDb.resolve("data/Forever/forever" + kind.metaEntity() + "DB.lua"), kind, schema, raw));
      report.count("raw." + kind.name().toLowerCase(Locale.ROOT), tables.get(kind).size());
    }

    LuaTable constants = EnumLoader.load(questieDb, stage(problems, "enums"));
    CorrectionScope.Bound bound = CorrectionScope.build(constants, schemas);
    CorrectionApplier applier = new CorrectionApplier(report);
    ProblemCollector corrections = stage(problems, "corrections");
    for (CorrectionLayer layer : CorrectionLayers.forever()) {
      applier.applyLayer(layer, questieDb, tables, bound, corrections);
    }

    DropRates rates = DropRateLoader.load(roots, atlasSpec.rateSource(), stage(problems, "droprates"));
    Map<String, String> serverMeta =
        ServerDbStage.run(opts.serverDbDir(), tables.get(EntityKind.NPC)::contains, rates, report);

    // AtlasLoot names instances after zones, and raid zones are the maps of AtlasLoot raid instances:
    // a first zone pass supplies the names, a second one (cheap) classifies the raids.
    Map<Long, String> areaNames = ZoneLoader.load(questieDb, constants, Set.of(), new ProblemCollector())
        .values().stream().collect(Collectors.toMap(Zone::id, Zone::name, (a, b) -> a));
    List<AtlasInstance> instances = AtlasLootLoader.load(roots.atlasLoot(), areaNames, stage(problems, "atlasloot"));
    if (atlasSpec.baseline() != null) {
      // the AtlasLoot pin replaced an earlier one (the Classic Forever fork, Hoizame's): keep the union (Task 19)
      List<AtlasInstance> baseline = AtlasLootLoader.load(fetcher.resolve(atlasSpec.baseline(), Optional.empty()),
          areaNames, stage(problems, "atlasloot.baseline"));
      EntityTable items = tables.get(EntityKind.ITEM);
      instances = AtlasLootBaseline.merge(instances, baseline,
          (npc, item) -> listedElsewhere(rates, items, npc, item), report);
    }
    Set<Long> raidAreaIds = instances.stream()
        .filter(i -> RAID_CONTENT.contains(i.contentType()) && i.mapId() != null)
        .map(AtlasInstance::mapId)
        .collect(Collectors.toSet());
    Map<Long, Zone> zones = ZoneLoader.load(questieDb, constants, raidAreaIds, stage(problems, "zones"));

    // the client's own dungeons fill in what AtlasLoot lacks: an AtlasLoot InstanceID is the client map id
    Set<Long> atlasLootMapIds = instances.stream().map(AtlasInstance::instanceId).filter(Objects::nonNull)
        .collect(Collectors.toSet());
    ClientDataBundle client =
        ClientDataStage.run(opts.clientData(), tables, zones, questieDb, atlasLootMapIds, report);

    Map<String, String> meta = meta(questieSpec.commit(), atlasSpec.pin(), Instant.now());
    if (atlasSpec.zip() != null) {
      meta.put("atlasloot_name", atlasSpec.zip().name());
      meta.put("atlasloot_version", atlasSpec.zip().version());
      meta.put("atlasloot_sha256", atlasSpec.zip().sha256());
    }
    if (atlasSpec.baseline() != null) {
      meta.put("atlasloot_baseline_commit", atlasSpec.baseline().commit());
    }
    meta.putAll(serverMeta);
    SqliteWriter.write(opts.out(), new ImportModel(tables, rates, zones, instances, meta), report, client);
    countRows(opts.out(), report);
    summariseProblems(problems, report);

    Files.createDirectories(opts.buildIdOut().toAbsolutePath().getParent());
    Files.writeString(opts.buildIdOut(), meta.get("build_id"), StandardCharsets.UTF_8);
    report.writeTo(opts.out().toAbsolutePath().getParent().resolve("import-report.txt"));
    console.print(report.render());
    console.flush();
    return report;
  }

  /** Questie's or VMaNGOS' rates, or Questie's {@code npcDrops}, list {@code npc} dropping {@code item}. */
  static boolean listedElsewhere(DropRates rates, EntityTable items, long npc, long item) {
    for (DropRate r : rates.allSources(npc, item)) {
      if (r.source().startsWith("questie") || r.source().equals(ServerDropRates.SOURCE)) {
        return true;
      }
    }
    if (items == null) {
      return false;
    }
    LuaValue drops = items.contains(item) ? items.get(item, "npcDrops") : null;
    if (drops instanceof LuaTable t) {
      for (LuaValue v : t.array()) {
        var id = LuaValues.asLong(v);
        if (id.isPresent() && id.getAsLong() == npc) {
          return true;
        }
      }
    }
    return false;
  }

  private static SourceSpec spec(String key) {
    return SourceSpec.load().stream().filter(s -> s.key().equals(key)).findFirst()
        .orElseThrow(() -> new IllegalStateException("sources.properties has no " + key));
  }

  private static ProblemCollector stage(Map<String, ProblemCollector> problems, String stage) {
    return problems.computeIfAbsent(stage, s -> new ProblemCollector());
  }

  /**
   * {@code build_id} is the SHA-256 of both source commits, the importer version, the schema text and
   * {@code built_at} (the build time to the millisecond), so every regeneration gets a new id and the app
   * re-extracts the database even when only the importer's output changed.
   */
  static Map<String, String> meta(String questieCommit, String atlasCommit, Instant builtAt) {
    String built = builtAt.truncatedTo(ChronoUnit.MILLIS).toString();
    Map<String, String> meta = new LinkedHashMap<>();
    meta.put("build_id", sha256(questieCommit + atlasCommit + IMPORTER_VERSION + SqliteWriter.schemaSql() + built));
    meta.put("built_at", built);
    meta.put("questiedb_commit", questieCommit);
    meta.put("atlasloot_commit", atlasCommit);
    meta.put("importer_version", IMPORTER_VERSION);
    return meta;
  }

  private static String sha256(String text) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  /**
   * {@code table.<table>} row counts for every table, the search index and the client-data tables included (those
   * are counted, as 0, after {@code --skip-client-data} too).
   */
  private static void countRows(Path db, ImportReport report) throws SQLException {
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
        Statement s = c.createStatement()) {
      List<String> tableNames = new ArrayList<>();
      try (ResultSet rs = s.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'"
          + " AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'search_index_%' ORDER BY name")) {
        while (rs.next()) {
          tableNames.add(rs.getString(1));
        }
      }
      for (String table : tableNames) {
        try (ResultSet rs = s.executeQuery("SELECT count(*) FROM " + table)) {
          rs.next();
          report.count("table." + table, rs.getLong(1));
        }
      }
    }
  }

  private static void summariseProblems(Map<String, ProblemCollector> problems, ImportReport report) {
    problems.forEach((stage, collector) -> {
      List<Problem> list = collector.problems();
      if (list.isEmpty()) {
        return;
      }
      report.count("problems." + stage, list.size());
      list.stream().limit(PROBLEMS_NOTED_PER_STAGE).forEach(p ->
          report.note("problems." + stage + ": " + p.file() + ":" + p.line() + " " + p.message()));
    });
  }
}
