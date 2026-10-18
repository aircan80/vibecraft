package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.report.ImportReport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Item spell effects: {@code ItemXItemEffect(ItemID -> ItemEffectID)} to {@code ItemEffect(TriggerType, SpellID,
 * Charges, CoolDownMSec, CategoryCoolDownMSec)}, the spell's {@code Description_lang} formatted by
 * {@link SpellDescriptionFormatter}.
 *
 * <ul>
 *   <li>Triggers 0 (Use), 1 (Equip) and 2 (Chance on hit) become effects, ordered by {@code LegacySlotIndex}, then
 *       id. An effect whose spell has no description keeps its row with a null text.
 *   <li>Trigger 6 teaches its spell (recipes, patterns, formulae). The game shows the item's description as the Use
 *       line: the trigger-0 "Learning" effect without a description is replaced by one Use effect for the taught
 *       spell whose text is the item description formatted for that spell (the taught spell's own description when
 *       the item has none).
 *   <li>Other triggers (on pickup, on looted, ...) are skipped and counted as {@value #OTHER_TRIGGER}.
 * </ul>
 *
 * <p>The hotfix overlay's {@code ItemEffect.hotfix} rows replace Forever's {@code ItemEffect} rows of the same id
 * (Task 19), counted as {@value #HOTFIX_APPLIED}; the links stay Forever's {@code ItemXItemEffect}.
 *
 * <p>An item whose ItemSparse row is Era's ({@link ClientItem#ERA}, ruling 17) and that has no Forever effect takes
 * its effects from the Era fallback's {@code ItemEffect.era} when present, by {@code ParentItemID} (Classic Era has no
 * {@code ItemXItemEffect}); Forever's rows win whenever the item has any. A hotfix item ({@link ClientItem#HOTFIX})
 * is a Forever row and never takes Era effects: Forever moved the Era Equip bonuses into its stats, so they would
 * show twice (Task 19 review I1). Its skipped Era effects are counted as {@value #ERA_SKIPPED_ON_HOTFIX} (items) and
 * {@value #ERA_ROWS_SKIPPED_ON_HOTFIX} (rows). The spell text still comes from Forever's
 * spell tables: an Era effect whose spell has neither a Forever {@code Spell} nor {@code SpellName} row is dropped
 * and counted as {@value #ERA_UNRESOLVED}. An {@code ItemEffect.era} without {@code ParentItemID} (or another column
 * read here) is not fatal: it is counted as {@value #ERA_TABLE_UNUSABLE}, noted, and no Era effects are used.
 *
 * <p>Counts {@value #EFFECTS}, the effects returned.
 */
public final class ItemEffectLoader {
  public static final String EFFECTS = "clientdata.effects";
  public static final String OTHER_TRIGGER = "clientdata.effects.otherTrigger";
  public static final String ERA_UNRESOLVED = "clientdata.era.effectUnresolved";
  /** Hotfix items without Forever effects whose Era fallback effects were left out. */
  public static final String ERA_SKIPPED_ON_HOTFIX = "clientdata.era.effectsSkippedOnHotfixItems";
  /** The Era effect rows of those items. */
  public static final String ERA_ROWS_SKIPPED_ON_HOTFIX = "clientdata.era.effectRowsSkippedOnHotfixItems";
  /** Effects whose {@code ItemEffect} row is the hotfix overlay's. */
  public static final String HOTFIX_APPLIED = "hotfix.itemeffect.applied";
  public static final String HOTFIX_EFFECT = "ItemEffect" + ClientDataSource.HOTFIX_SUFFIX;
  /** 1, with a note, when {@code ItemEffect.era} lacks a column read here; absent otherwise. */
  public static final String ERA_TABLE_UNUSABLE = "clientdata.era.effectTableUnusable";
  /** The Era fallback's ItemEffect: Forever's columns plus {@code ParentItemID}, the item. */
  public static final String ERA_EFFECT = "ItemEffect" + ClientDataSource.ERA_SUFFIX;
  static final List<String> LINK_COLUMNS = List.of("ItemID", "ItemEffectID");
  static final List<String> EFFECT_COLUMNS = List.of("ID", "LegacySlotIndex", "TriggerType", "Charges", "CoolDownMSec",
      "CategoryCoolDownMSec", "SpellID");
  static final List<String> ERA_EFFECT_COLUMNS =
      Stream.concat(EFFECT_COLUMNS.stream(), Stream.of("ParentItemID")).toList();

  private ItemEffectLoader() {}

  /** The effects of every item in {@code items} that has one, by item id in id order. */
  public static Map<Long, List<ItemSpellEffect>> load(ClientDataSource src, Map<Long, ClientItem> items,
      SpellDescriptionFormatter fmt, ImportReport report) {
    Map<Long, List<CsvTable.Row>> links = require(src.table("ItemXItemEffect"), LINK_COLUMNS).groupByLong("ItemID");
    Map<Long, CsvTable.Row> effects = require(src.table("ItemEffect"), EFFECT_COLUMNS).indexByLong("ID");
    Map<Long, CsvTable.Row> hotfixEffects = src.optionalTable(HOTFIX_EFFECT)
        .map(t -> require(t, EFFECT_COLUMNS).indexByLong("ID"))
        .orElse(Map.of());
    Map<Long, List<CsvTable.Row>> eraEffects = src.optionalTable(ERA_EFFECT)
        .map(t -> eraEffectsByItem(t, report))
        .orElse(Map.of());
    Map<Long, List<ItemSpellEffect>> out = new LinkedHashMap<>();
    long total = 0;
    long other = 0;
    long eraUnresolved = 0;
    long eraSkippedItems = 0;
    long eraSkippedRows = 0;
    long hotfixApplied = 0;
    for (long id : new TreeSet<>(items.keySet())) {
      List<CsvTable.Row> rows = new ArrayList<>();
      for (CsvTable.Row link : links.getOrDefault(id, List.of())) {
        long effectId = link.longOr("ItemEffectID", 0);
        CsvTable.Row e = hotfixEffects.get(effectId);
        if (e != null) {
          hotfixApplied++;
        } else {
          e = effects.get(effectId);
        }
        if (e != null) {
          rows.add(e);
        }
      }
      String source = items.get(id).clientSource();
      if (rows.isEmpty() && ClientItem.HOTFIX.equals(source) && eraEffects.containsKey(id)) {
        eraSkippedItems++;
        eraSkippedRows += eraEffects.get(id).size();
      }
      if (rows.isEmpty() && ClientItem.ERA.equals(source)) {
        for (CsvTable.Row e : eraEffects.getOrDefault(id, List.of())) {
          if (fmt.tables().contains(e.longOr("SpellID", 0))) {
            rows.add(e);
          } else {
            eraUnresolved++;
          }
        }
      }
      if (rows.isEmpty()) {
        continue;
      }
      rows.sort(Comparator.comparingInt((CsvTable.Row r) -> r.intOr("LegacySlotIndex", 0))
          .thenComparingLong(r -> r.longOr("ID", 0)));
      CsvTable.Row learn = rows.stream().filter(r -> r.intOr("TriggerType", -1) == ItemSpellEffect.LEARN)
          .findFirst().orElse(null);
      List<ItemSpellEffect> list = new ArrayList<>();
      boolean learnPlaced = false;
      for (CsvTable.Row r : rows) {
        int trigger = r.intOr("TriggerType", -1);
        long spell = r.longOr("SpellID", 0);
        if (trigger == ItemSpellEffect.LEARN) {
          continue;
        }
        if (trigger < ItemSpellEffect.USE || trigger > ItemSpellEffect.CHANCE_ON_HIT) {
          other++;
          continue;
        }
        if (learn != null && !learnPlaced && trigger == ItemSpellEffect.USE
            && fmt.tables().description(spell).isEmpty()) {
          list.add(learned(items.get(id), learn, r, fmt));
          learnPlaced = true;
          continue;
        }
        SpellDescriptionFormatter.Formatted f = fmt.description(spell);
        list.add(new ItemSpellEffect(0, trigger, spell, f.text().isEmpty() ? null : f.text(), f.complete(),
            r.intOr("Charges", 0), cooldown(r), false, f.evidence()));
      }
      if (learn != null && !learnPlaced) {
        list.add(0, learned(items.get(id), learn, learn, fmt));
      }
      if (list.isEmpty()) {
        continue;
      }
      List<ItemSpellEffect> numbered = new ArrayList<>(list.size());
      for (int i = 0; i < list.size(); i++) {
        ItemSpellEffect e = list.get(i);
        numbered.add(new ItemSpellEffect(i, e.trigger(), e.spellId(), e.text(), e.complete(), e.charges(),
            e.cooldownMs(), e.fromItemDescription(), e.evidence()));
      }
      out.put(id, Collections.unmodifiableList(numbered));
      total += numbered.size();
    }
    report.count(EFFECTS, total);
    report.count(OTHER_TRIGGER, other);
    report.count(ERA_UNRESOLVED, eraUnresolved);
    report.count(ERA_SKIPPED_ON_HOTFIX, eraSkippedItems);
    report.count(ERA_ROWS_SKIPPED_ON_HOTFIX, eraSkippedRows);
    report.count(HOTFIX_APPLIED, hotfixApplied);
    return Collections.unmodifiableMap(out);
  }

  /**
   * The Era effect rows by {@code ParentItemID}; empty, counted as {@value #ERA_TABLE_UNUSABLE} and noted, when the
   * table lacks a column read here (another Era build may link effects differently), so the import goes on without
   * Era effects.
   */
  private static Map<Long, List<CsvTable.Row>> eraEffectsByItem(CsvTable t, ImportReport report) {
    List<String> missing = ERA_EFFECT_COLUMNS.stream().filter(c -> !t.hasColumn(c)).toList();
    if (!missing.isEmpty()) {
      report.count(ERA_TABLE_UNUSABLE, 1);
      report.note(ERA_TABLE_UNUSABLE + ": " + ERA_EFFECT + " (" + t.source() + ") has no column "
          + String.join(", ", missing) + "; Era-sourced items get no Era effects");
      return Map.of();
    }
    return t.groupByLong("ParentItemID");
  }

  /** The Use effect of an item that teaches {@code learn}'s spell; charges and cooldown from {@code use}. */
  private static ItemSpellEffect learned(ClientItem item, CsvTable.Row learn, CsvTable.Row use,
      SpellDescriptionFormatter fmt) {
    long taught = learn.longOr("SpellID", 0);
    String description = item == null ? null : item.description();
    boolean fromItem = description != null && !description.isBlank();
    SpellDescriptionFormatter.Formatted f = fromItem ? fmt.format(taught, description) : fmt.description(taught);
    return new ItemSpellEffect(0, ItemSpellEffect.USE, taught, f.text().isEmpty() ? null : f.text(), f.complete(),
        use.intOr("Charges", 0), cooldown(use), fromItem, f.evidence());
  }

  /** {@code CoolDownMSec} when positive, else {@code CategoryCoolDownMSec} when positive, else 0. */
  private static int cooldown(CsvTable.Row r) {
    int own = r.intOr("CoolDownMSec", 0);
    int category = r.intOr("CategoryCoolDownMSec", 0);
    return own > 0 ? own : Math.max(category, 0);
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
