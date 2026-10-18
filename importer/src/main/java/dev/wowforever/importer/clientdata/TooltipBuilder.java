package dev.wowforever.importer.clientdata;

import static dev.wowforever.importer.clientdata.TooltipLine.EQUIP_GREEN;
import static dev.wowforever.importer.clientdata.TooltipLine.FLAVOUR_GOLD;
import static dev.wowforever.importer.clientdata.TooltipLine.MUTED;
import static dev.wowforever.importer.clientdata.TooltipLine.SET_GREY;
import static dev.wowforever.importer.clientdata.TooltipLine.WHITE;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Renders an item's tooltip as {@link TooltipLine}s in the game's order (spec 4.3). Kinds, in order:
 * {@code name, binding, unique, begins_quest, slot, damage, damage_extra, speed, dps, armor, block, stat, resist,
 * durability, req_class, req_race, req_level, req_skill, req_rep, use, equip, proc, set_name, set_item, set_bonus,
 * charges, duration, flavour, item_level, sell_price, source_note}; a set's own skill requirement is a
 * {@code req_skill} line right under its {@code set_name}, as the game shows it.
 *
 * <p>Colours: the name in its quality colour; Use, Equip and Chance on hit green; flavour text and the set name gold;
 * set pieces and set bonuses grey (the game greys the pieces and bonuses a character lacks, and no character is
 * known here, so the count is always 0); item level, sell price and the Era note muted; everything else white.
 *
 * <p>Inputs the client data lacks are left out: shield block (no table holds it), durability for Forever rows (its
 * {@code ItemSparse} has no {@code MaxDurability}), the school of an Era row's second damage range.
 */
public final class TooltipBuilder {
  /** {@code Flags_0} bit: "Unique-Equipped". */
  static final int UNIQUE_EQUIPPED = 0x80000;
  /** Cooldowns up to this are shared category cooldowns (food, drink, elixirs) the game does not print. */
  static final int SHOWN_COOLDOWN_MIN_MS = 3000;
  /** The footer of an Era-sourced item (backlog decision 20, pm-data-sources.md). */
  public static final String SOURCE_NOTE = "Classic Era values. Forever stats are still hidden.";
  /** The footer of a hotfix item without the snapshot's build and date ({@link HotfixInfo#footer} has them). */
  public static final String HOTFIX_NOTE = "Forever stats revealed by a Blizzard hotfix";
  /** Every kind, in tooltip order. */
  public static final List<String> KINDS = List.of("name", "binding", "unique", "begins_quest", "slot", "damage",
      "damage_extra", "speed", "dps", "armor", "block", "stat", "resist", "durability", "req_class", "req_race",
      "req_level", "req_skill", "req_rep", "use", "equip", "proc", "set_name", "set_item", "set_bonus", "charges",
      "duration", "flavour", "item_level", "sell_price", "source_note");
  private static final List<String> SCHOOLS = List.of("", "Holy", "Fire", "Nature", "Frost", "Shadow", "Arcane");
  private static final List<String> STANDINGS = List.of("Hated", "Hostile", "Unfriendly", "Neutral", "Friendly",
      "Honored", "Revered", "Exalted");
  /** Armour subclasses whose name the slot line shows: cloth, leather, mail, plate, shield, libram, idol, totem. */
  private static final Set<Integer> NAMED_ARMOR = Set.of(1, 2, 3, 4, 6, 7, 8, 9);
  private static final int WEAPON = 2;
  private static final int ARMOR = 4;
  private static final int PROJECTILE = 6;
  private static final int CONTAINER = 1;
  private static final int QUIVER = 11;
  private static final int BACK = 16;

  private TooltipBuilder() {}

  /**
   * One item's tooltip.
   *
   * @param stats   computed stats in slot order (empty for none)
   * @param damage  null for none
   * @param armor   null for none
   * @param effects the item's effects (empty for none)
   * @param set     the item's set, or null
   */
  public static List<TooltipLine> build(ClientItem item, List<ItemStat> stats, ItemDamage damage, ItemArmor armor,
      List<ItemSpellEffect> effects, ItemSetInfo set, TooltipNames names) {
    return build(item, stats, damage, armor, effects, set, names, HOTFIX_NOTE);
  }

  /**
   * One item's tooltip, a hotfix item's footer being {@code hotfixFooter} (the snapshot's {@link HotfixInfo#footer}).
   * The footer ({@code source_note}, muted) names where the stats come from: a hotfix item's says the hotfix
   * revealed them, an Era item's that Forever's are still hidden; a Forever item has none.
   */
  public static List<TooltipLine> build(ClientItem item, List<ItemStat> stats, ItemDamage damage, ItemArmor armor,
      List<ItemSpellEffect> effects, ItemSetInfo set, TooltipNames names, String hotfixFooter) {
    Lines out = new Lines();
    ClientItem.Details d = item.details();
    out.add("name", item.name() != null ? item.name() : names.item(item.id()), TooltipLine.qualityColour(item.quality()));
    BindingNames.name(item.bonding()).ifPresent(t -> out.add("binding", t, WHITE));
    if (d.maxCount() != null) {
      out.add("unique", d.maxCount() == 1 ? "Unique" : "Unique (" + d.maxCount() + ")", WHITE);
    } else if (!item.flags().isEmpty() && (item.flags().get(0) & UNIQUE_EQUIPPED) != 0) {
      out.add("unique", "Unique-Equipped", WHITE);
    }
    if (item.startQuestId() != null) {
      out.add("begins_quest", "This Item Begins a Quest", WHITE);
    }
    slot(item, names, out);

    if (damage != null && damage.delayMs() > 0) {
      String school = damage.school() > 0 && damage.school() < SCHOOLS.size() ? SCHOOLS.get(damage.school()) + " " : "";
      out.add("damage", damage.min() + " - " + damage.max() + " " + school + "Damage", WHITE);
      ClientItem.Absolute a = item.absolute();
      if (a != null && a.extraMaxDamage() != null) {
        out.add("damage_extra", "+" + a.extraMinDamage() + " - " + a.extraMaxDamage() + " Damage", WHITE);
      }
      out.add("speed", String.format(Locale.ROOT, "Speed %.2f", damage.delayMs() / 1000.0), WHITE);
      out.add("dps", String.format(Locale.ROOT, "(%.2f damage per second)", damage.dps()), WHITE);
    } else if (damage != null) {
      out.add("dps", "Adds " + SpellDescriptionFormatter.number(damage.dps()) + " damage per second", WHITE);
    }
    if (armor != null && armor.armor() > 0) {
      out.add("armor", armor.armor() + " Armor", WHITE);
    }
    if (armor != null && armor.block() != null && armor.block() > 0) {
      out.add("block", armor.block() + " Block", WHITE);
    }
    List<StatText.Line> statLines = StatText.lines(stats);
    statLines.stream().filter(l -> !l.kind().equals("equip")).forEach(l -> out.add(l.kind(), l.text(), WHITE));
    if (d.maxDurability() != null) {
      out.add("durability", "Durability " + d.maxDurability() + " / " + d.maxDurability(), WHITE);
    }

    names.classRace().classes(item.allowableClass()).ifPresent(t -> out.add("req_class", t, WHITE));
    names.classRace().races(item.allowableRace(), d.allowableRaceHigh()).ifPresent(t -> out.add("req_race", t, WHITE));
    if (item.requiredLevel() != null && item.requiredLevel() > 1) {
      out.add("req_level", "Requires Level " + item.requiredLevel(), WHITE);
    }
    if (item.requiredSkill() != null) {
      out.add("req_skill", skill(item.requiredSkill(), item.requiredSkillRank(), names), WHITE);
    }
    if (d.minFactionId() != null) {
      String standing = d.minReputation() != null && d.minReputation() >= 0 && d.minReputation() < STANDINGS.size()
          ? " - " + STANDINGS.get(d.minReputation()) : "";
      out.add("req_rep", "Requires " + names.faction(d.minFactionId()).orElse("Faction #" + d.minFactionId())
          + standing, WHITE);
    }

    for (ItemSpellEffect e : effects) {
      if (e.trigger() == ItemSpellEffect.USE && e.text() != null) {
        out.add("use", "Use: " + e.text() + cooldown(e.cooldownMs()), EQUIP_GREEN, e.evidence());
      }
    }
    statLines.stream().filter(l -> l.kind().equals("equip")).forEach(l -> out.add("equip", l.text(), EQUIP_GREEN));
    for (ItemSpellEffect e : effects) {
      if (e.trigger() == ItemSpellEffect.EQUIP && e.text() != null) {
        out.add("equip", "Equip: " + e.text() + cooldown(e.cooldownMs()), EQUIP_GREEN, e.evidence());
      }
    }
    for (ItemSpellEffect e : effects) {
      if (e.trigger() == ItemSpellEffect.CHANCE_ON_HIT && e.text() != null) {
        out.add("proc", "Chance on hit: " + e.text(), EQUIP_GREEN, e.evidence());
      }
    }

    if (set != null) {
      out.add("set_name", set.name() + " (0/" + set.itemIds().size() + ")", FLAVOUR_GOLD);
      if (set.requiredSkill() != null) {
        out.add("req_skill", skill(set.requiredSkill(), set.requiredSkillRank(), names), WHITE);
      }
      for (long piece : set.itemIds()) {
        out.add("set_item", names.item(piece), SET_GREY);
      }
      for (ItemSetInfo.Bonus b : set.bonuses()) {
        if (b.text() != null) {
          out.add("set_bonus", "(" + b.threshold() + ") Set: " + b.text(), SET_GREY, b.evidence());
        }
      }
    }

    effects.stream().filter(e -> Math.abs(e.charges()) > 1).findFirst()
        .ifPresent(e -> out.add("charges", Math.abs(e.charges()) + " Charges", WHITE));
    if (d.durationInInventory() != null) {
      out.add("duration", "Duration: " + SpellDescriptionFormatter.duration(d.durationInInventory() * 1000L), WHITE);
    }
    boolean descriptionUsed = effects.stream().anyMatch(ItemSpellEffect::fromItemDescription);
    if (item.description() != null && !item.description().isBlank() && !descriptionUsed) {
      out.add("flavour", "\"" + item.description().strip() + "\"", FLAVOUR_GOLD);
    }
    if (item.itemLevel() != null && item.itemLevel() > 0) {
      out.add("item_level", "Item Level " + item.itemLevel(), MUTED);
    }
    if (item.sellPrice() != null && item.sellPrice() > 0) {
      out.add("sell_price", "Sell Price: " + money(item.sellPrice()), MUTED);
    }
    if (ClientItem.ERA.equals(item.clientSource())) {
      out.add("source_note", SOURCE_NOTE, MUTED);
    } else if (ClientItem.HOTFIX.equals(item.clientSource())) {
      out.add("source_note", hotfixFooter, MUTED);
    }
    return out.lines;
  }

  /**
   * The slot line: the slot name and, for weapons, armour of a material, shields, relics and ammunition, the
   * subclass, tab-separated; a bag's "16 Slot Bag" instead.
   */
  private static void slot(ClientItem item, TooltipNames names, Lines out) {
    Integer cls = item.classId();
    Integer sub = item.subclassId();
    Integer slots = item.details().containerSlots();
    if (slots != null && cls != null && (cls == CONTAINER || cls == QUIVER)) {
      out.add("slot", slots + " Slot " + names.subclass(cls, sub).orElse("Bag"), WHITE);
      return;
    }
    InventoryTypeNames.name(item.inventoryType()).ifPresent(left -> {
      String right = null;
      if (cls != null && (cls == WEAPON || cls == PROJECTILE)) {
        right = names.subclass(cls, sub).orElse(null);
      } else if (cls != null && cls == ARMOR && sub != null && NAMED_ARMOR.contains(sub)
          && !Integer.valueOf(BACK).equals(item.inventoryType())) {
        right = names.subclass(cls, sub).orElse(null);
      }
      out.add("slot", right == null ? left : left + "\t" + right, WHITE);
    });
  }

  private static String skill(int skill, Integer rank, TooltipNames names) {
    String name = names.skill(skill).orElse("Skill #" + skill);
    return "Requires " + name + (rank != null && rank > 0 ? " (" + rank + ")" : "");
  }

  /** " (7 Min Cooldown)", or empty for none or a shared category cooldown of a few seconds. */
  static String cooldown(int ms) {
    if (ms <= SHOWN_COOLDOWN_MIN_MS) {
      return "";
    }
    return " (" + cooldownTime(ms) + " Cooldown)";
  }

  /** "30 Sec", "7 Min", "1 Min 30 Sec", "1 Hour", "2 Hours", "1 Day". */
  static String cooldownTime(long ms) {
    long s = ms / 1000;
    if (s % 86_400 == 0) {
      return plural(s / 86_400, "Day");
    }
    if (s % 3_600 == 0) {
      return plural(s / 3_600, "Hour");
    }
    List<String> parts = new ArrayList<>();
    if (s >= 3_600) {
      parts.add(plural(s / 3_600, "Hour"));
      s %= 3_600;
    }
    if (s >= 60) {
      parts.add(s / 60 + " Min");
    }
    if (s % 60 > 0) {
      parts.add(s % 60 + " Sec");
    }
    return String.join(" ", parts);
  }

  private static String plural(long n, String unit) {
    return n + " " + unit + (n == 1 ? "" : "s");
  }

  /** Copper as "1g 2s 3c", leaving out zero denominations ("52c", "1g"). */
  public static String money(long copper) {
    List<String> parts = new ArrayList<>();
    if (copper >= 10_000) {
      parts.add(copper / 10_000 + "g");
    }
    if (copper / 100 % 100 > 0) {
      parts.add(copper / 100 % 100 + "s");
    }
    if (copper % 100 > 0 || copper == 0) {
      parts.add(copper % 100 + "c");
    }
    return String.join(" ", parts);
  }

  private static final class Lines {
    final List<TooltipLine> lines = new ArrayList<>();

    void add(String kind, String text, String colour, SpellTextEvidence evidence) {
      lines.add(new TooltipLine(lines.size(), kind, text, colour, evidence));
    }

    void add(String kind, String text, String colour) {
      lines.add(new TooltipLine(lines.size(), kind, text, colour));
    }
  }
}
