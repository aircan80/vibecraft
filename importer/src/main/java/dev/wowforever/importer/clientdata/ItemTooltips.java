package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.report.ImportReport;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Item effects, the sets the items name and every item's rendered tooltip: what the writer stores in
 * {@code item_effect}, {@code item_set*} and {@code item_tooltip_line}.
 *
 * @param effects by item id (every client item with an effect)
 * @param sets    by set id (only sets some item names)
 * @param lines   by item id (every item with an {@code ItemSparse} row)
 */
public record ItemTooltips(Map<Long, List<ItemSpellEffect>> effects, Map<Long, ItemSetInfo> sets,
    Map<Long, List<TooltipLine>> lines) {
  public static final String LINES = "clientdata.tooltip.lines";
  public static final String ITEMS = "clientdata.tooltip.items";
  /** Prefix of {@code clientdata.tooltip.missingKind.<kind>}: 1 when the client data lacks the kind's input. */
  public static final String MISSING_KIND = "clientdata.tooltip.missingKind.";

  public ItemTooltips {
    effects = Collections.unmodifiableMap(new TreeMap<>(effects));
    sets = Collections.unmodifiableMap(new TreeMap<>(sets));
    lines = Collections.unmodifiableMap(new TreeMap<>(lines));
  }

  public static ItemTooltips empty() {
    return new ItemTooltips(Map.of(), Map.of(), Map.of());
  }

  /**
   * Loads effects and sets and renders the tooltip of every item with an {@code ItemSparse} row.
   *
   * @param values       computed stats, damage and armour by item id ({@link ItemValues#compute})
   * @param questieNames item names to fall back on for set pieces the client has no name for
   */
  public static ItemTooltips build(ClientDataSource src, Map<Long, ClientItem> items, Map<Long, ItemValues> values,
      Map<Long, String> questieNames, ImportReport report) {
    SpellDescriptionFormatter fmt = new SpellDescriptionFormatter(SpellTables.load(src));
    Map<Long, List<ItemSpellEffect>> effects = ItemEffectLoader.load(src, items, fmt, report);
    Set<Long> setIds = new TreeSet<>();
    for (ClientItem i : items.values()) {
      if (i.hasSparse() && i.itemSetId() != null) {
        setIds.add(i.itemSetId());
      }
    }
    Map<Long, ItemSetInfo> sets = ItemSetLoader.load(src, setIds, fmt, report);

    Map<Long, String> names = new HashMap<>(questieNames);
    Set<Long> pieces = new TreeSet<>();
    sets.values().forEach(s -> pieces.addAll(s.itemIds()));
    pieces.removeAll(items.keySet());
    for (Map<Long, ClientItem> source : List.of(items, ItemSparseLoader.load(src, pieces, new ImportReport()))) {
      source.forEach((id, i) -> {
        if (i.name() != null) {
          names.put(id, i.name());
        }
      });
    }
    TooltipNames tooltipNames = TooltipNames.load(src, names);

    Map<Long, List<TooltipLine>> lines = new LinkedHashMap<>();
    long count = 0;
    for (ClientItem item : new TreeMap<>(items).values()) {
      if (!item.hasSparse()) {
        continue;
      }
      ItemValues v = values.get(item.id());
      List<TooltipLine> tooltip = TooltipBuilder.build(item, v == null ? List.of() : v.stats(),
          v == null ? null : v.damage(), v == null ? null : v.armor(), effects.getOrDefault(item.id(), List.of()),
          item.itemSetId() == null ? null : sets.get(item.itemSetId()), tooltipNames);
      lines.put(item.id(), List.copyOf(tooltip));
      count += tooltip.size();
    }
    report.count(LINES, count);
    report.count(ITEMS, lines.size());
    report.count(MISSING_KIND + "durability",
        src.table(ItemSparseLoader.SPARSE).hasColumn(ItemSparseLoader.MAX_DURABILITY) ? 0 : 1);
    fmt.report(report);
    return new ItemTooltips(effects, sets, lines);
  }
}
