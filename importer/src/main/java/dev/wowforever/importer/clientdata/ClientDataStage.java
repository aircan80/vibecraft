package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.zones.Zone;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaValue;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The importer's client-data stage: reads a converted client-data directory and returns everything the writer embeds.
 * In order: the source and its build lock, {@link ItemSparseLoader}, {@link ItemClassNames}, {@link ItemValues},
 * {@link ItemTooltips}, {@link IconLoader}, {@link ZoneMapLoader}, {@link UiAssetLoader}.
 *
 * <p>With {@code --skip-client-data} it returns {@link ClientDataBundle#EMPTY} (the writer then creates the client
 * tables empty, sets no {@code has_client_data} and writes no {@code client_*} meta keys) and counts
 * {@code clientdata.skipped}.
 *
 * <p>Besides the loaders' own counters it counts {@code clientdata.items.named} (items with a name after corrections),
 * {@code clientdata.items.named.matched} (those with an ItemSparse row; {@code clientdata.items.matched} also counts
 * nameless items) and {@code clientdata.coverage.pct}, the named matched items as a whole percentage of the named
 * items, rounded down. The coverage gate ({@link #coverageTooLow}) compares the exact ratio.
 *
 * <p>{@value #DISPUTED} counts the items of {@link #DISPUTED_ITEMS} that have client data, with a note naming each
 * one and the dispute. Their computed values are still written; the list only flags them for whoever reads the
 * report.
 */
public final class ClientDataStage {
  public static final String SKIPPED = "clientdata.skipped";
  public static final String MATCHED = "clientdata.items.matched";
  public static final String NAMED = "clientdata.items.named";
  public static final String NAMED_MATCHED = "clientdata.items.named.matched";
  public static final String COVERAGE_PCT = "clientdata.coverage.pct";
  /**
   * The default of {@code --coverage-min}: {@code --fail-on-unsupported} fails when fewer named items than this
   * percentage have an ItemSparse row.
   */
  public static final int DEFAULT_MIN_COVERAGE_PCT = 85;
  public static final String DISPUTED = "clientdata.disputed";
  /**
   * Items whose computed values a secondary source disputes, by id, with what differs. Add an item here when the
   * known-items oracle ({@code fixtures/clientdata/KnownItemsOracle.md}) drops a kind for it because no client table
   * produces the other source's value, and the computed value is kept.
   */
  static final Map<Long, String> DISPUTED_ITEMS = Map.of(
      19019L, "Thunderfury, Blessed Blade of the Windseeker: damage 82 - 153 from Forever's item level 80 one-hand"
          + " table (the Era row agrees and adds 16 - 30 Nature), Wowhead's Forever tooltip shows 44 - 115");

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
    long namedMatched = names.keySet().stream()
        .filter(id -> clientItems.containsKey(id) && clientItems.get(id).hasSparse()).count();
    report.count(NAMED, names.size());
    report.count(NAMED_MATCHED, namedMatched);
    report.count(COVERAGE_PCT, names.isEmpty() ? 100 : namedMatched * 100 / names.size());

    ItemClassNames classNames = ItemClassNames.load(source);
    Map<Long, ItemValues> values = ItemValues.compute(clientItems, ScalingTables.load(source), report);
    reportDisputed(clientItems, report);
    ItemTooltips tooltips = ItemTooltips.build(source, clientItems, values, names, report);
    Map<Long, byte[]> icons = IconLoader.load(source, clientItems.values(), report);
    ZoneMaps zoneMaps = ZoneMapLoader.load(source, questieDbRoot, zones, report);
    Map<String, byte[]> uiAssets = UiAssetLoader.load(source, report);
    return new ClientDataBundle(clientItems, classNames, values, tooltips, icons, zoneMaps, lock, uiAssets);
  }

  /**
   * True when client data was imported and {@code clientdata.items.named.matched} is below {@code minPct} percent of
   * {@code clientdata.items.named} (exact ratio, not the rounded {@code clientdata.coverage.pct}); false after
   * {@code --skip-client-data} or without named items.
   *
   * @param minPct the {@code --coverage-min} threshold, 0 to 100
   */
  public static boolean coverageTooLow(ImportReport report, int minPct) {
    long named = report.get(NAMED);
    if (report.get(SKIPPED) > 0 || named == 0) {
      return false;
    }
    return report.get(NAMED_MATCHED) * 100 < (long) minPct * named;
  }

  /**
   * Counts {@value #DISPUTED} (always emitted) as the {@link #DISPUTED_ITEMS} present in {@code clientItems}, and
   * notes them in id order.
   */
  static void reportDisputed(Map<Long, ClientItem> clientItems, ImportReport report) {
    List<Long> present = DISPUTED_ITEMS.keySet().stream().filter(clientItems::containsKey).sorted().toList();
    report.count(DISPUTED, present.size());
    if (!present.isEmpty()) {
      report.note(DISPUTED + ": " + present.stream().map(id -> id + " (" + DISPUTED_ITEMS.get(id) + ")")
          .collect(Collectors.joining("; ")));
    }
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
