package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.zones.Zone;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaValue;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The importer's client-data stage: reads a converted client-data directory and returns everything the writer embeds.
 * In order: the source and its build lock, {@link ItemSparseLoader}, {@link ItemClassNames}, {@link ItemValues},
 * {@link ItemTooltips}, {@link IconLoader}, {@link ZoneMapLoader}.
 *
 * <p>With {@code --skip-client-data} it returns {@link ClientDataBundle#EMPTY} (the writer then creates the client
 * tables empty, sets no {@code has_client_data} and writes no {@code client_*} meta keys) and counts
 * {@code clientdata.skipped}.
 *
 * <p>Besides the loaders' own counters it counts {@code clientdata.items.named} (items with a name after corrections)
 * and {@code clientdata.coverage.pct}, {@code clientdata.items.matched} as a whole percentage of the named items,
 * rounded down; the coverage gate ({@link #coverageTooLow}) compares the exact ratio. {@code clientdata.items.matched}
 * also counts nameless items, so {@code clientdata.items.named.matched} (named items with an ItemSparse row) is
 * reported beside it for information.
 */
public final class ClientDataStage {
  public static final String SKIPPED = "clientdata.skipped";
  public static final String MATCHED = "clientdata.items.matched";
  public static final String NAMED = "clientdata.items.named";
  public static final String NAMED_MATCHED = "clientdata.items.named.matched";
  public static final String COVERAGE_PCT = "clientdata.coverage.pct";
  /** {@code --fail-on-unsupported} fails when fewer named items than this percentage have an ItemSparse row. */
  public static final int MIN_COVERAGE_PCT = 85;

  private ClientDataStage() {}

  /**
   * Runs the stage.
   *
   * @param tables        the entity tables after corrections; the items' ids and names are read
   * @param zones         the imported zones, assigned UiMaps by {@link ZoneMapLoader}
   * @param questieDbRoot the QuestieDB checkout, for the area-to-UiMap overrides
   * @throws IllegalStateException when the directory lacks a table or column a loader reads, or its build lock is
   *     missing, malformed or stale ({@link ClientDataSource#lock})
   */
  public static ClientDataBundle run(ClientDataOptions opts, Map<EntityKind, EntityTable> tables,
      Map<Long, Zone> zones, Path questieDbRoot, ImportReport report) {
    Objects.requireNonNull(opts, "opts");
    if (opts.skip()) {
      report.count(SKIPPED, 1);
      return ClientDataBundle.EMPTY;
    }
    ClientDataSource source = new ClientDataSource(opts.dir().orElseThrow());
    BuildLock lock = source.lock();

    Map<Long, String> names = itemNames(tables.get(EntityKind.ITEM));
    Map<Long, ClientItem> clientItems =
        ItemSparseLoader.load(source, tables.get(EntityKind.ITEM).rows().keySet(), report);
    report.count(NAMED, names.size());
    report.count(NAMED_MATCHED, names.keySet().stream()
        .filter(id -> clientItems.containsKey(id) && clientItems.get(id).hasSparse()).count());
    report.count(COVERAGE_PCT, names.isEmpty() ? 100 : report.get(MATCHED) * 100 / names.size());

    ItemClassNames classNames = ItemClassNames.load(source);
    Map<Long, ItemValues> values = ItemValues.compute(clientItems, ScalingTables.load(source), report);
    ItemTooltips tooltips = ItemTooltips.build(source, clientItems, values, names, report);
    Map<Long, byte[]> icons = IconLoader.load(source, clientItems.values(), report);
    ZoneMaps zoneMaps = ZoneMapLoader.load(source, questieDbRoot, zones, report);
    return new ClientDataBundle(clientItems, classNames, values, tooltips, icons, zoneMaps, lock);
  }

  /**
   * True when client data was imported and {@code clientdata.items.matched} is below {@link #MIN_COVERAGE_PCT} percent
   * of {@code clientdata.items.named}; false after {@code --skip-client-data} or without named items.
   */
  public static boolean coverageTooLow(ImportReport report) {
    long named = report.get(NAMED);
    if (report.get(SKIPPED) > 0 || named == 0) {
      return false;
    }
    return report.get(MATCHED) * 100 < MIN_COVERAGE_PCT * named;
  }

  /** Item names by id, as the writer will store them: a string {@code name}; nil and unresolved values are absent. */
  static Map<Long, String> itemNames(EntityTable items) {
    Map<Long, String> names = new LinkedHashMap<>();
    for (long id : items.rows().keySet()) {
      LuaValue v = items.get(id, "name");
      if (v instanceof LuaString s && s.value() != null) {
        names.put(id, s.value());
      }
    }
    return names;
  }
}
