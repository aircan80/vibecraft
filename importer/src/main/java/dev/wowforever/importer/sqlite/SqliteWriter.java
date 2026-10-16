package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.clientdata.ClientItem;
import dev.wowforever.importer.clientdata.ItemClassNames;
import dev.wowforever.importer.clientdata.ItemDamage;
import dev.wowforever.importer.clientdata.ItemStat;
import dev.wowforever.importer.clientdata.ItemValues;
import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.schema.EntitySchema;
import dev.wowforever.importer.zones.Zone;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Writes an {@link ImportModel} to a fresh SQLite file: schema, every row in one transaction with
 * batched prepared statements, the full-text search index, the secondary indexes, then ANALYZE and
 * VACUUM. A failed write deletes the partial file.
 */
public final class SqliteWriter {
  private static final String SCHEMA_RESOURCE = "/dev/wowforever/importer/schema.sql";
  /** Separates the table definitions in {@code schema.sql} from the indexes created after the bulk load. */
  private static final Pattern INDEX_MARKER = Pattern.compile("^-- @indexes\\s*$", Pattern.MULTILINE);
  private static final List<String> PRAGMAS = List.of(
      "PRAGMA page_size = 4096", "PRAGMA journal_mode = OFF", "PRAGMA synchronous = OFF", "PRAGMA temp_store = MEMORY");
  /** Every named entity, except {@code Zone #id} placeholder names, which only clutter search results. */
  private static final String SEARCH_INDEX = """
      INSERT INTO search_index(kind, id, name)
      SELECT kind, id, name FROM (
                SELECT 'item' AS kind, id, name FROM item WHERE name IS NOT NULL
      UNION ALL SELECT 'npc', id, name FROM npc WHERE name IS NOT NULL
      UNION ALL SELECT 'object', id, name FROM game_object WHERE name IS NOT NULL
      UNION ALL SELECT 'quest', id, name FROM quest WHERE name IS NOT NULL
      UNION ALL SELECT 'zone', id, name FROM zone WHERE name IS NOT NULL AND kind <> 'unknown'
      UNION ALL SELECT 'instance', id, name FROM instance WHERE name IS NOT NULL)
      WHERE name NOT LIKE 'Zone #%'""";

  private SqliteWriter() {}

  /** The {@code schema.sql} resource text (tables, then indexes after the marker line). */
  public static String schemaSql() {
    try (InputStream in = SqliteWriter.class.getResourceAsStream(SCHEMA_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException(SCHEMA_RESOURCE + " is missing from the classpath");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static void write(Path out, ImportModel model) throws IOException, SQLException {
    write(out, model, new ImportReport());
  }

  /** Writes the model, counting data oddities (nameless rows, unresolved values, unknown ids) into {@code report}. */
  public static void write(Path out, ImportModel model, ImportReport report) throws IOException, SQLException {
    write(out, model, report, Map.of(), null);
  }

  /**
   * Writes the model with client data: {@code clientItems} (by item id, from
   * {@link dev.wowforever.importer.clientdata.ItemSparseLoader}) are merged into the item rows, and
   * {@code classNames}, when not null, replace the static {@link ItemClasses} names. An empty map and null names
   * write exactly what {@link #write(Path, ImportModel, ImportReport)} writes.
   */
  public static void write(Path out, ImportModel model, ImportReport report, Map<Long, ClientItem> clientItems,
      ItemClassNames classNames) throws IOException, SQLException {
    write(out, model, report, clientItems, classNames, Map.of());
  }

  /**
   * Writes the model with client data and the computed {@code values} (from {@link ItemValues#compute}), which fill
   * {@code item_stat}, {@code item_damage} and {@code item_armor} for the ids that are item rows; an empty map leaves
   * those tables empty.
   */
  public static void write(Path out, ImportModel model, ImportReport report, Map<Long, ClientItem> clientItems,
      ItemClassNames classNames, Map<Long, ItemValues> values) throws IOException, SQLException {
    Path file = out.toAbsolutePath();
    Files.deleteIfExists(file);
    Files.createDirectories(file.getParent());
    try {
      writeFile(file, model, report, clientItems, classNames, values);
    } catch (SQLException | RuntimeException e) {
      Files.deleteIfExists(file);
      throw e;
    }
  }

  private static void writeFile(Path file, ImportModel model, ImportReport report, Map<Long, ClientItem> clientItems,
      ItemClassNames classNames, Map<Long, ItemValues> values) throws SQLException {
    String schema = schemaSql();
    Matcher m = INDEX_MARKER.matcher(schema);
    if (!m.find()) {
      throw new IllegalStateException("schema.sql lacks the '-- @indexes' marker line");
    }
    int marker = m.start();
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file)) {
      execute(c, PRAGMAS);
      execute(c, statements(schema.substring(0, marker)));
      c.setAutoCommit(false);

      writeMeta(c, model.meta());
      writeItemClasses(c, classNames);
      LuaColumns columns = new LuaColumns(report);
      EntityWriter entities = new EntityWriter(c, columns, report);
      NpcDrops drops = new NpcDrops();
      Map<Long, Long> spawnZones = new HashMap<>();
      entities.items(table(model, EntityKind.ITEM), drops, clientItems);
      entities.npcs(table(model, EntityKind.NPC), spawnZones);
      entities.objects(table(model, EntityKind.OBJECT), spawnZones);
      entities.quests(table(model, EntityKind.QUEST));
      EntityTable items = table(model, EntityKind.ITEM);
      writeItemValues(c, values, items);
      new AtlasWriter(c, report).write(model.instances(), model.zones()::containsKey, items::contains,
          model.dropRates(), drops);
      drops.write(c, model.dropRates());
      writeZones(c, model.zones(), spawnZones, report);

      execute(c, List.of(SEARCH_INDEX));
      execute(c, statements(schema.substring(marker)));
      c.commit();
      c.setAutoCommit(true);
      execute(c, List.of("ANALYZE", "VACUUM"));
    }
  }

  private static EntityTable table(ImportModel model, EntityKind kind) {
    EntityTable t = model.tables().get(kind);
    return t != null ? t : new EntityTable(kind, new EntitySchema(kind.metaEntity(), List.of(), List.of(), List.of()));
  }

  private static void writeMeta(Connection c, Map<String, String> meta) throws SQLException {
    try (Batch rows = new Batch(c, "INSERT INTO meta(key, value) VALUES (?, ?)")) {
      for (Map.Entry<String, String> e : meta.entrySet()) {
        rows.add(e.getKey(), e.getValue());
      }
    }
  }

  /** {@code item_stat} (ordinal = tooltip order), {@code item_damage} and {@code item_armor} for the item rows. */
  private static void writeItemValues(Connection c, Map<Long, ItemValues> values, EntityTable items)
      throws SQLException {
    try (Batch stats = new Batch(c, "INSERT INTO item_stat(item_id, ordinal, stat_id, value) VALUES (?, ?, ?, ?)");
        Batch damage = new Batch(c,
            "INSERT INTO item_damage(item_id, min, max, school, delay_ms, dps) VALUES (?, ?, ?, ?, ?, ?)");
        Batch armor = new Batch(c, "INSERT INTO item_armor(item_id, armor, block) VALUES (?, ?, ?)")) {
      for (Map.Entry<Long, ItemValues> e : new TreeMap<>(values).entrySet()) {
        long id = e.getKey();
        if (!items.contains(id)) {
          continue;
        }
        ItemValues v = e.getValue();
        int ordinal = 0;
        for (ItemStat s : v.stats()) {
          stats.add(id, ordinal++, s.statId(), s.value());
        }
        if (v.damage() != null) {
          ItemDamage d = v.damage();
          damage.add(id, d.min(), d.max(), d.school(), d.delayMs() > 0 ? d.delayMs() : null, d.dps());
        }
        if (v.armor() != null) {
          armor.add(id, v.armor().armor(), v.armor().block());
        }
      }
    }
  }

  /** The client's class names when given, else the static cmangos table. */
  private static void writeItemClasses(Connection c, ItemClassNames names) throws SQLException {
    Map<Integer, String> classNames = names != null ? names.classes() : ItemClasses.classes();
    Map<Integer, Map<Integer, String>> subclassNames = names != null ? names.subclasses() : ItemClasses.subclasses();
    try (Batch classes = new Batch(c, "INSERT INTO item_class(id, name) VALUES (?, ?)");
        Batch subclasses = new Batch(c, "INSERT INTO item_subclass(class_id, id, name) VALUES (?, ?, ?)")) {
      for (Map.Entry<Integer, String> e : classNames.entrySet()) {
        classes.add(e.getKey(), e.getValue());
      }
      for (Map.Entry<Integer, Map<Integer, String>> cls : subclassNames.entrySet()) {
        for (Map.Entry<Integer, String> sub : cls.getValue().entrySet()) {
          subclasses.add(cls.getKey(), sub.getKey(), sub.getValue());
        }
      }
    }
  }

  /**
   * The model's zones plus a {@code Zone #id} placeholder (kind {@code unknown}) for every zone id that
   * only spawns reference, so no spawn row is dropped and every spawn zone resolves.
   */
  private static void writeZones(Connection c, Map<Long, Zone> zones, Map<Long, Long> spawnZones, ImportReport report)
      throws SQLException {
    Map<Long, Long> unknown = new TreeMap<>(spawnZones.entrySet().stream()
        .filter(e -> !zones.containsKey(e.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    report.count("spawn.unknownZone", unknown.values().stream().mapToLong(Long::longValue).sum());
    report.count("zone.placeholders", unknown.size());
    try (Batch rows = new Batch(c, "INSERT INTO zone(id, name, parent_id, kind) VALUES (?, ?, ?, ?)")) {
      for (Zone z : zones.values()) {
        rows.add(z.id(), z.name(), z.parentId(), z.kind());
      }
      for (long id : unknown.keySet()) {
        rows.add(id, "Zone #" + id, null, "unknown");
      }
    }
  }

  /** Splits SQL text into statements: comment lines dropped, statements end with ';' at a line end. */
  static List<String> statements(String sql) {
    List<String> out = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String line : sql.split("\\R")) {
      String trimmed = line.strip();
      if (trimmed.isEmpty() || trimmed.startsWith("--")) {
        continue;
      }
      current.append(line).append('\n');
      if (trimmed.endsWith(";")) {
        out.add(current.toString().strip());
        current.setLength(0);
      }
    }
    if (!current.toString().isBlank()) {
      throw new IllegalStateException("schema.sql ends with an unterminated statement: " + current);
    }
    return out;
  }

  private static void execute(Connection c, List<String> sql) throws SQLException {
    try (Statement s = c.createStatement()) {
      for (String statement : sql) {
        s.execute(statement);
      }
    }
  }
}
