package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.report.ImportReport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

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
 * Counts {@value #EFFECTS}, the effects returned.
 */
public final class ItemEffectLoader {
  public static final String EFFECTS = "clientdata.effects";
  public static final String OTHER_TRIGGER = "clientdata.effects.otherTrigger";
  static final List<String> LINK_COLUMNS = List.of("ItemID", "ItemEffectID");
  static final List<String> EFFECT_COLUMNS = List.of("ID", "LegacySlotIndex", "TriggerType", "Charges", "CoolDownMSec",
      "CategoryCoolDownMSec", "SpellID");

  private ItemEffectLoader() {}

  /** The effects of every item in {@code items} that has one, by item id in id order. */
  public static Map<Long, List<ItemSpellEffect>> load(ClientDataSource src, Map<Long, ClientItem> items,
      SpellDescriptionFormatter fmt, ImportReport report) {
    Map<Long, List<CsvTable.Row>> links = require(src.table("ItemXItemEffect"), LINK_COLUMNS).groupByLong("ItemID");
    Map<Long, CsvTable.Row> effects = require(src.table("ItemEffect"), EFFECT_COLUMNS).indexByLong("ID");
    Map<Long, List<ItemSpellEffect>> out = new LinkedHashMap<>();
    long total = 0;
    long other = 0;
    for (long id : new TreeSet<>(items.keySet())) {
      List<CsvTable.Row> rows = new ArrayList<>();
      for (CsvTable.Row link : links.getOrDefault(id, List.of())) {
        CsvTable.Row e = effects.get(link.longOr("ItemEffectID", 0));
        if (e != null) {
          rows.add(e);
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
            r.intOr("Charges", 0), cooldown(r), false));
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
            e.cooldownMs(), e.fromItemDescription()));
      }
      out.put(id, Collections.unmodifiableList(numbered));
      total += numbered.size();
    }
    report.count(EFFECTS, total);
    report.count(OTHER_TRIGGER, other);
    return Collections.unmodifiableMap(out);
  }

  /** The Use effect of an item that teaches {@code learn}'s spell; charges and cooldown from {@code use}. */
  private static ItemSpellEffect learned(ClientItem item, CsvTable.Row learn, CsvTable.Row use,
      SpellDescriptionFormatter fmt) {
    long taught = learn.longOr("SpellID", 0);
    String description = item == null ? null : item.description();
    boolean fromItem = description != null && !description.isBlank();
    SpellDescriptionFormatter.Formatted f = fromItem ? fmt.format(taught, description) : fmt.description(taught);
    return new ItemSpellEffect(0, ItemSpellEffect.USE, taught, f.text().isEmpty() ? null : f.text(), f.complete(),
        use.intOr("Charges", 0), cooldown(use), fromItem);
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
