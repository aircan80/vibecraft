package dev.wowforever.importer.clientdata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Computed stats as tooltip lines, phrased the way Wowhead's Forever tooltips (the known-items oracle) print them.
 *
 * <ul>
 *   <li>Plain stats are white {@code +N Name} lines ({@code stat} kind, resistances the {@code resist} kind), in slot
 *       order; bonus armour (50) is "+N Armor"; mana and health regeneration (43, 46) are white "Restores +N mana per
 *       5 sec." lines.
 *   <li>Ratings and spell bonuses are green {@code equip} lines, in stat id order (as the oracle orders hit before
 *       critical strike): hit (31) and critical strike (32) as percentages at 10 and 14 rating per 1 % (the level-60
 *       conversion; Lionheart Helm and Bloodvine), spell power (45), healing (41, merged with spell damage 42 when
 *       both are present), attack power (38), the school spell damage ids 84 to 89 (holy, fire, nature, frost,
 *       shadow, arcane). The other ratings use the TBC-era wording with the rating itself, which no oracle item
 *       verifies.
 *   <li>The Forever ids that replace a Classic Equip spell ({@link StatNames}: weapon and profession skills, spell
 *       penetration, all resistances, slaying) are green {@code equip} lines worded like that Era spell's
 *       description, e.g. "Equip: Increased Fishing +5.".
 *   <li>Ids {@link StatNames} does not know stay {@code +N Stat #id} lines.
 * </ul>
 */
final class StatText {
  /** One line: {@code kind} is {@code stat}, {@code resist} or {@code equip}. */
  record Line(String kind, String text) {}

  private record Rated(int statId, String text) {}

  private static final Map<Integer, String> SCHOOLS = Map.of(84, "Holy", 85, "Fire", 86, "Nature", 87, "Frost",
      88, "Shadow", 89, "Arcane");
  private static final Map<Integer, String> RATINGS = Map.of(
      12, "Increases defense rating by %d.",
      13, "Increases your dodge rating by %d.",
      14, "Increases your parry rating by %d.",
      15, "Increases your shield block rating by %d.",
      35, "Improves your resilience rating by %d.",
      36, "Improves haste rating by %d.",
      37, "Increases your expertise rating by %d.",
      44, "Increases your armor penetration rating by %d.",
      47, "Increases spell penetration by %d.");
  /** Forever stats that replace a Classic Equip spell, worded like the Era spell (its {@code $s1} as %d). */
  private static final Map<Integer, String> ERA_EQUIP = Map.ofEntries(
      Map.entry(90, "Increased Two-handed Axes +%d."),
      Map.entry(91, "Increased Two-handed Maces +%d."),
      Map.entry(96, "Increased Daggers +%d."),
      Map.entry(103, "Increased Swords +%d."),
      Map.entry(112, "Herbalism +%d."),
      Map.entry(113, "Mining +%d."),
      Map.entry(117, "Increased Fishing +%d."),
      Map.entry(119, "Decreases the fire resistance of your spell targets by %d."),
      Map.entry(121, "Decreases the frost resistance of your spell targets by %d."),
      Map.entry(124, "+%d All Resistances."),
      Map.entry(127, "+%d Attack Power against Demons."),
      Map.entry(128, "+%d Attack Power against Undead."),
      Map.entry(131, "+%d Attack Power against Beasts."),
      Map.entry(135, "Increases damage done to Demons by magical spells and effects by up to %d."),
      Map.entry(136, "Increases damage done to Undead by magical spells and effects by up to %d."));
  static final double HIT_PER_PERCENT = 10;
  static final double CRIT_PER_PERCENT = 14;

  private StatText() {}

  /** The {@code stat} lines, then the {@code resist} lines, then the {@code equip} lines. */
  static List<Line> lines(List<ItemStat> stats) {
    Integer healing = first(stats, 41);
    Integer damage = first(stats, 42);
    List<Line> plain = new ArrayList<>();
    List<Line> resist = new ArrayList<>();
    List<Rated> equip = new ArrayList<>();
    for (ItemStat s : stats) {
      int id = s.statId();
      int v = s.value();
      if (StatNames.kind(id).equals(StatNames.RESIST)) {
        resist.add(new Line(StatNames.RESIST, signed(v) + " " + StatNames.name(id)));
        continue;
      }
      String rated = switch (id) {
        case 31 -> "Improves your chance to hit by " + percent(v / HIT_PER_PERCENT) + "%.";
        case 32 -> "Improves your chance to get a critical strike by " + percent(v / CRIT_PER_PERCENT) + "%.";
        case 45 -> "Increases damage and healing done by magical spells and effects by up to " + v + ".";
        case 41 -> damage != null
            ? "Increases healing done by up to " + v + " and damage done by up to " + damage
                + " for all magical spells and effects."
            : "Increases healing done by spells and effects by up to " + v + ".";
        case 42 -> healing != null ? "" : "Increases damage done by magical spells and effects by up to " + v + ".";
        case 38 -> signed(v) + " Attack Power.";
        default -> SCHOOLS.containsKey(id)
            ? "Increases damage done by " + SCHOOLS.get(id) + " spells and effects by up to " + v + "."
            : RATINGS.containsKey(id) ? String.format(RATINGS.get(id), v)
            : ERA_EQUIP.containsKey(id) ? String.format(ERA_EQUIP.get(id), v) : null;
      };
      if (rated != null) {
        if (!rated.isEmpty()) {
          equip.add(new Rated(id, "Equip: " + rated));
        }
        continue;
      }
      String text = switch (id) {
        case 43 -> "Restores " + signed(v) + " mana per 5 sec.";
        case 46 -> "Restores " + signed(v) + " health per 5 sec.";
        case 50 -> signed(v) + " Armor";
        default -> signed(v) + " " + StatNames.name(id);
      };
      plain.add(new Line(StatNames.STAT, text));
    }
    List<Line> out = new ArrayList<>(plain);
    out.addAll(resist);
    equip.sort(Comparator.comparingInt(Rated::statId));
    equip.forEach(r -> out.add(new Line("equip", r.text())));
    return out;
  }

  private static Integer first(List<ItemStat> stats, int id) {
    return stats.stream().filter(s -> s.statId() == id).map(ItemStat::value).findFirst().orElse(null);
  }

  private static String signed(int v) {
    return v < 0 ? "-" + Math.abs(v) : "+" + v;
  }

  private static String percent(double v) {
    return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).toPlainString();
  }
}
