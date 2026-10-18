package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.clientdata.BuildLock;
import dev.wowforever.importer.clientdata.ClientDataBundle;
import dev.wowforever.importer.clientdata.ClientItem;
import dev.wowforever.importer.clientdata.Craft;
import dev.wowforever.importer.clientdata.CraftReagent;
import dev.wowforever.importer.clientdata.CraftRecipeItem;
import dev.wowforever.importer.clientdata.Crafts;
import dev.wowforever.importer.clientdata.ItemClassNames;
import dev.wowforever.importer.clientdata.ItemDamage;
import dev.wowforever.importer.clientdata.ItemSetInfo;
import dev.wowforever.importer.clientdata.ItemSpellEffect;
import dev.wowforever.importer.clientdata.ItemStat;
import dev.wowforever.importer.clientdata.ItemTooltips;
import dev.wowforever.importer.clientdata.ItemValues;
import dev.wowforever.importer.clientdata.ZoneMaps;
import dev.wowforever.importer.clientdata.TooltipLine;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
  /**
   * The {@code schema.sql} generation, written as {@code meta.schema_version}: 1 the v0.1 tables, 2 the phase 2A
   * client data, 3 the phase 3A quest objectives, relations, source items and UI assets, 4 the client dungeons'
   * {@code instance} columns (3B Task 17). Changes are additive.
   */
  public static final int SCHEMA_VERSION = 4;
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
    write(out, model, report, clientItems, classNames, values, ItemTooltips.empty());
  }

  /**
   * Writes the model with client data, computed values and {@code tooltips} (from {@link ItemTooltips#build}):
   * {@code item_effect} and {@code item_tooltip_line} for the ids that are item rows, and every given set to
   * {@code item_set}, {@code item_set_item} and {@code item_set_bonus}.
   */
  public static void write(Path out, ImportModel model, ImportReport report, Map<Long, ClientItem> clientItems,
      ItemClassNames classNames, Map<Long, ItemValues> values, ItemTooltips tooltips)
      throws IOException, SQLException {
    write(out, model, report, new ClientDataBundle(clientItems, classNames, values, tooltips, Map.of(),
        ZoneMaps.EMPTY, null, Map.of()));
  }

  /**
   * Writes the model with everything the client-data stages produced, see {@link ClientDataBundle}. Icons and
   * zone maps fill {@code icon} and {@code zone_map} and set {@code zone.ui_map_id}, UI textures fill
   * {@code ui_asset}, crafts fill {@code craft}, {@code craft_reagent} and {@code craft_recipe_item}, client dungeons
   * follow the AtlasLoot instances in {@code instance} and {@code boss}; with a build lock the {@code client_*} meta
   * keys are written too.
   */
  public static void write(Path out, ImportModel model, ImportReport report, ClientDataBundle client)
      throws IOException, SQLException {
    Path file = out.toAbsolutePath();
    Files.deleteIfExists(file);
    Files.createDirectories(file.getParent());
    try {
      writeFile(file, model, report, client);
    } catch (SQLException | RuntimeException e) {
      Files.deleteIfExists(file);
      throw e;
    }
  }

  private static void writeFile(Path file, ImportModel model, ImportReport report, ClientDataBundle client)
      throws SQLException {
    Map<Long, ClientItem> clientItems = client.clientItems();
    ItemClassNames classNames = client.classNames();
    Map<Long, ItemValues> values = client.values();
    ItemTooltips tooltips = client.tooltips();
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
      writeMeta(c, Map.of("schema_version", String.valueOf(SCHEMA_VERSION)));
      writeItemClasses(c, classNames);
      LuaColumns columns = new LuaColumns(report);
      EntityWriter entities = new EntityWriter(c, columns, report);
      NpcDrops drops = new NpcDrops();
      DropProvenance provenance = new DropProvenance(model.dropRates());
      Map<Long, Long> spawnZones = new HashMap<>();
      entities.items(table(model, EntityKind.ITEM), drops, clientItems);
      entities.npcs(table(model, EntityKind.NPC), spawnZones);
      entities.objects(table(model, EntityKind.OBJECT), spawnZones);
      entities.quests(table(model, EntityKind.QUEST));
      EntityTable items = table(model, EntityKind.ITEM);
      writeItemValues(c, values, items);
      writeTooltips(c, tooltips, items);
      new AtlasWriter(c, report).write(model.instances(), model.zones()::containsKey, items::contains,
          model.dropRates(), drops, provenance);
      new ClientInstanceWriter(c).write(client.instances());
      drops.write(c, model.dropRates(), provenance);
      provenance.write(c, report);
      writeZones(c, model.zones(), spawnZones, client.zoneMaps().zoneToMap(), report);
      writeIcons(c, client.icons());
      writeZoneMaps(c, client.zoneMaps());
      writeUiAssets(c, client.uiAssets());
      writeCrafts(c, client.crafts());
      writeClientMeta(c, client);

      execute(c, List.of(SEARCH_INDEX));
      execute(c, statements(schema.substring(marker)));
      c.commit();
      c.setAutoCommit(true);
      execute(c, List.of("ANALYZE", "VACUUM"));
    }
  }

  private static void writeIcons(Connection c, Map<Long, byte[]> icons) throws SQLException {
    try (Batch rows = new Batch(c, "INSERT INTO icon(id, png) VALUES (?, ?)")) {
      for (Map.Entry<Long, byte[]> e : new TreeMap<>(icons).entrySet()) {
        rows.add(e.getKey(), e.getValue());
      }
    }
  }

  private static void writeUiAssets(Connection c, Map<String, byte[]> assets) throws SQLException {
    try (Batch rows = new Batch(c, "INSERT INTO ui_asset(name, png) VALUES (?, ?)")) {
      for (Map.Entry<String, byte[]> e : new TreeMap<>(assets).entrySet()) {
        rows.add(e.getKey(), e.getValue());
      }
    }
  }

  /** {@code craft}, {@code craft_reagent} (in the given slot order) and {@code craft_recipe_item}. */
  private static void writeCrafts(Connection c, Crafts crafts) throws SQLException {
    try (Batch rows = new Batch(c, "INSERT INTO craft(spell_id, spell_name, item_id, quantity_min, quantity_max,"
        + " skill_line_id, skill_name, min_skill, trivial_low, trivial_high, recipe_item_id)"
        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
      for (Craft k : crafts.crafts()) {
        rows.add(k.spellId(), k.spellName(), k.createdItemId(), k.quantityMin(), k.quantityMax(), k.skillLineId(),
            k.skillName(), k.minSkill(), k.trivialLow(), k.trivialHigh(), k.recipeItemId());
      }
    }
    try (Batch rows = new Batch(c, "INSERT INTO craft_reagent(spell_id, item_id, count) VALUES (?, ?, ?)")) {
      for (CraftReagent r : crafts.reagents()) {
        rows.add(r.spellId(), r.itemId(), r.count());
      }
    }
    try (Batch rows = new Batch(c, "INSERT INTO craft_recipe_item(spell_id, item_id) VALUES (?, ?)")) {
      for (CraftRecipeItem r : crafts.recipeItems()) {
        rows.add(r.spellId(), r.itemId());
      }
    }
  }

  private static void writeZoneMaps(Connection c, ZoneMaps maps) throws SQLException {
    try (Batch rows = new Batch(c, "INSERT INTO zone_map(ui_map_id, width, height, jpeg) VALUES (?, ?, ?, ?)")) {
      for (Map.Entry<Long, ZoneMaps.MapImage> e : new TreeMap<>(maps.maps()).entrySet()) {
        rows.add(e.getKey(), e.getValue().w(), e.getValue().h(), e.getValue().jpeg());
      }
    }
  }

  /** The client build the data came from and what was embedded; nothing without a build lock. */
  private static void writeClientMeta(Connection c, ClientDataBundle client) throws SQLException {
    BuildLock lock = client.lock();
    if (lock == null) {
      return;
    }
    Map<String, String> meta = new LinkedHashMap<>();
    meta.put("client_product", lock.product());
    meta.put("client_build", lock.build());
    meta.put("client_fetched_at", lock.fetchedAt());
    if (lock.fallbackProduct() != null) {
      meta.put("client_fallback_product", lock.fallbackProduct());
    }
    if (lock.fallbackBuild() != null) {
      meta.put("client_fallback_build", lock.fallbackBuild());
    }
    if (lock.hotfixSha256() != null) {
      meta.put("client_hotfix_sha256", lock.hotfixSha256());
      meta.put("client_hotfix_header_build", String.valueOf(lock.hotfixHeaderBuild()));
    }
    try (Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM item WHERE has_client_data = 1")) {
      meta.put("client_item_count", String.valueOf(rs.next() ? rs.getLong(1) : 0));
    }
    meta.put("icon_count", String.valueOf(client.icons().size()));
    meta.put("zone_map_count", String.valueOf(client.zoneMaps().maps().size()));
    writeMeta(c, meta);
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

  /** {@code item_effect} and {@code item_tooltip_line} for the item rows; every set with its pieces and bonuses. */
  private static void writeTooltips(Connection c, ItemTooltips tooltips, EntityTable items) throws SQLException {
    try (Batch effects = new Batch(c,
            "INSERT INTO item_effect(item_id, ordinal, trigger, spell_id, text) VALUES (?, ?, ?, ?, ?)");
        Batch sets = new Batch(c, "INSERT INTO item_set(id, name) VALUES (?, ?)");
        Batch pieces = new Batch(c, "INSERT INTO item_set_item(set_id, item_id) VALUES (?, ?)");
        Batch bonuses = new Batch(c,
            "INSERT INTO item_set_bonus(set_id, threshold, spell_id, text) VALUES (?, ?, ?, ?)");
        Batch lines = new Batch(c,
            "INSERT INTO item_tooltip_line(item_id, ordinal, kind, text, colour) VALUES (?, ?, ?, ?, ?)")) {
      for (Map.Entry<Long, List<ItemSpellEffect>> e : tooltips.effects().entrySet()) {
        if (items.contains(e.getKey())) {
          for (ItemSpellEffect x : e.getValue()) {
            effects.add(e.getKey(), x.ordinal(), x.trigger(), x.spellId(), x.text());
            SpellTextEvidenceWriter.write(c, "effect", e.getKey(), x.ordinal(), x.evidence());
          }
        }
      }
      for (ItemSetInfo s : tooltips.sets().values()) {
        sets.add(s.id(), s.name());
        for (long piece : s.itemIds()) {
          pieces.add(s.id(), piece);
        }
        for (ItemSetInfo.Bonus b : s.bonuses()) {
          bonuses.add(s.id(), b.threshold(), b.spellId(), b.text());
          SpellTextEvidenceWriter.write(c, "set_bonus", s.id(), b.threshold(), b.evidence());
        }
      }
      for (Map.Entry<Long, List<TooltipLine>> e : tooltips.lines().entrySet()) {
        if (items.contains(e.getKey())) {
          for (TooltipLine l : e.getValue()) {
            lines.add(e.getKey(), l.ordinal(), l.kind(), l.text(), l.colour());
            SpellTextEvidenceWriter.write(c, "tooltip", e.getKey(), l.ordinal(), l.evidence());
          }
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
  private static void writeZones(Connection c, Map<Long, Zone> zones, Map<Long, Long> spawnZones,
      Map<Long, Long> zoneMaps, ImportReport report)
      throws SQLException {
    Map<Long, Long> unknown = new TreeMap<>(spawnZones.entrySet().stream()
        .filter(e -> !zones.containsKey(e.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    report.count("spawn.unknownZone", unknown.values().stream().mapToLong(Long::longValue).sum());
    report.count("zone.placeholders", unknown.size());
    try (Batch rows = new Batch(c, "INSERT INTO zone(id, name, parent_id, kind, ui_map_id) VALUES (?, ?, ?, ?, ?)")) {
      for (Zone z : zones.values()) {
        rows.add(z.id(), z.name(), z.parentId(), z.kind(), zoneMaps.get(z.id()));
      }
      for (long id : unknown.keySet()) {
        rows.add(id, "Zone #" + id, null, "unknown", zoneMaps.get(id));
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
