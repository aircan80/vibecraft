package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.report.ImportReport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Crafting recipes from the client's spell tables (3A Task 8): which spell creates which item, from which reagents,
 * at which skill, and which item teaches it.
 *
 * <ul>
 *   <li>A craft is a spell with a {@code SpellEffect} row of {@code Effect} {@value #CREATE_ITEM} (Create Item) whose
 *       {@code EffectItemType} (the created item) is positive; a spell without one creates nothing and is skipped. When
 *       a spell has several, the row of {@code DifficultyID} 0 with the lowest {@code EffectIndex} is used.
 *   <li>Quantity: build 1.60.1.70425 stores the yield itself in {@code EffectBasePointsF} (Heavy Leather 1, Crafted
 *       Heavy Shot 200; there is no {@code EffectDieSides} column), spread by {@code Variance} like every effect value:
 *       {@code basePoints * (1 -/+ variance / 2)}, rounded (Coarse Dynamite: 2 and 1, so 1 to 3). A Create Item
 *       effect makes at least one item, so 0 (Transmute: Air to Fire) is 1.
 *   <li>Skill: the spell's {@code SkillLineAbility} row by {@code Spell} (the first in file order when there are
 *       several): {@code SkillLine}, {@code MinSkillLineRank}, {@code TrivialSkillLineRankLow} and
 *       {@code TrivialSkillLineRankHigh}, the skill's name from {@code SkillLine.DisplayName_lang}. A craft without one
 *       keeps null skill fields and is counted as {@value #NO_SKILL}.
 *   <li>Reagents: the spell's {@code SpellReagents} row, {@code Reagent_n} and {@code ReagentCount_n} for n 0 to 7 in
 *       slot order; a slot whose item or count is not positive is skipped (Season of Discovery engravings list a
 *       reagent with count 0), a repeated item adds up.
 *   <li>Recipe item: an item ({@code ItemXItemEffect} to {@code ItemEffect}) whose effect of {@code TriggerType} 6
 *       (learn) names the craft spell itself, which is how build 1.60.1.70425 links every recipe, pattern and
 *       formula, or whose effect of {@code TriggerType} 0 (use) or 6 names a spell with an {@code Effect}
 *       {@value #LEARN_SPELL} (Learn Spell) row teaching the craft ({@code EffectTriggerSpell}). Every such item is
 *       kept in {@link Crafts#recipeItems()} (40 crafts of build 1.60.1.70425 have several, e.g. a recipe and its
 *       later reissue); {@link Craft#recipeItemId()} is the lowest of them, for convenience.
 * </ul>
 *
 * <p>Counts {@value #CRAFTS}, {@value #NO_SKILL}, {@value #REAGENTS} (reagent rows), {@value #RECIPES} (crafts with
 * at least one recipe item) and {@value #RECIPE_ITEMS} (craft and recipe item pairs).
 */
public final class CraftLoader {
  public static final String CRAFTS = "clientdata.crafts";
  public static final String NO_SKILL = "clientdata.crafts.noSkill";
  public static final String REAGENTS = "clientdata.crafts.reagents";
  public static final String RECIPES = "clientdata.crafts.recipes";
  public static final String RECIPE_ITEMS = "clientdata.crafts.recipeItems";
  /** {@code SpellEffect.Effect}: Create Item. */
  static final int CREATE_ITEM = 24;
  /** {@code SpellEffect.Effect}: Learn Spell ({@code EffectTriggerSpell} is the taught spell). */
  static final int LEARN_SPELL = 36;
  static final int REAGENT_SLOTS = 8;

  static final List<String> EFFECT_COLUMNS = List.of("SpellID", "EffectIndex", "DifficultyID", "Effect",
      "EffectItemType", "EffectBasePointsF", "Variance", "EffectTriggerSpell");
  static final List<String> NAME_COLUMNS = List.of("ID", "Name_lang");
  static final List<String> SKILL_LINE_COLUMNS = List.of("ID", "DisplayName_lang");
  static final List<String> ABILITY_COLUMNS = List.of("Spell", "SkillLine", "MinSkillLineRank",
      "TrivialSkillLineRankLow", "TrivialSkillLineRankHigh");
  static final List<String> REAGENT_COLUMNS = Stream.concat(Stream.of("SpellID"), IntStream.range(0, REAGENT_SLOTS)
      .boxed().flatMap(i -> Stream.of("Reagent_" + i, "ReagentCount_" + i))).toList();
  static final List<String> ITEM_EFFECT_COLUMNS = List.of("ID", "TriggerType", "SpellID");
  static final List<String> LINK_COLUMNS = List.of("ItemID", "ItemEffectID");

  private CraftLoader() {}

  /**
   * The crafts of {@code src}.
   *
   * @throws IllegalStateException when a table read here is missing or lacks a column, naming both
   */
  public static Crafts load(ClientDataSource src, ImportReport report) {
    CsvTable effects = require(src.table("SpellEffect"), EFFECT_COLUMNS);
    Map<Long, CsvTable.Row> creates = new TreeMap<>();
    Map<Long, List<Long>> learns = new HashMap<>();
    for (CsvTable.Row r : effects.rows()) {
      OptionalLong spell = r.getLong("SpellID");
      if (spell.isEmpty()) {
        continue;
      }
      int effect = r.intOr("Effect", 0);
      if (effect == CREATE_ITEM && r.longOr("EffectItemType", 0) > 0) {
        creates.merge(spell.getAsLong(), r, (a, b) -> rank(b) < rank(a) ? b : a);
      } else if (effect == LEARN_SPELL && r.longOr("EffectTriggerSpell", 0) > 0) {
        learns.computeIfAbsent(spell.getAsLong(), k -> new ArrayList<>()).add(r.longOr("EffectTriggerSpell", 0));
      }
    }

    Map<Long, String> spellNames = names(require(src.table("SpellName"), NAME_COLUMNS), "Name_lang");
    Map<Long, String> skillNames = names(require(src.table("SkillLine"), SKILL_LINE_COLUMNS), "DisplayName_lang");
    Map<Long, CsvTable.Row> abilities = new HashMap<>();
    for (CsvTable.Row r : require(src.table("SkillLineAbility"), ABILITY_COLUMNS).rows()) {
      r.getLong("Spell").ifPresent(spell -> abilities.putIfAbsent(spell, r));
    }
    Map<Long, List<CsvTable.Row>> reagentRows =
        require(src.table("SpellReagents"), REAGENT_COLUMNS).groupByLong("SpellID");
    Map<Long, TreeSet<Long>> teachers = teachers(src, creates.keySet(), learns);

    List<Craft> crafts = new ArrayList<>();
    List<CraftReagent> reagents = new ArrayList<>();
    List<CraftRecipeItem> recipeItems = new ArrayList<>();
    long noSkill = 0;
    long recipes = 0;
    for (Map.Entry<Long, CsvTable.Row> e : creates.entrySet()) {
      long spell = e.getKey();
      CsvTable.Row create = e.getValue();
      double base = create.doubleOr("EffectBasePointsF", 0);
      double variance = Math.max(0, create.doubleOr("Variance", 0));
      int min = (int) Math.max(1, Math.round(base * (1 - variance / 2)));
      int max = (int) Math.max(min, Math.round(base * (1 + variance / 2)));
      CsvTable.Row ability = abilities.get(spell);
      Long skillLine = ability == null ? null : ability.longOr("SkillLine", 0);
      if (ability == null) {
        noSkill++;
      }
      TreeSet<Long> taughtBy = teachers.get(spell);
      Long recipe = taughtBy == null ? null : taughtBy.first();
      if (recipe != null) {
        recipes++;
        taughtBy.forEach(item -> recipeItems.add(new CraftRecipeItem(spell, item)));
      }
      crafts.add(new Craft(spell, spellNames.get(spell), create.longOr("EffectItemType", 0), min, max, skillLine,
          skillLine == null ? null : skillNames.get(skillLine), boxed(ability, "MinSkillLineRank"),
          boxed(ability, "TrivialSkillLineRankLow"), boxed(ability, "TrivialSkillLineRankHigh"), recipe));
      reagents.addAll(reagents(spell, reagentRows.getOrDefault(spell, List.of())));
    }
    report.count(CRAFTS, crafts.size());
    report.count(NO_SKILL, noSkill);
    report.count(REAGENTS, reagents.size());
    report.count(RECIPES, recipes);
    report.count(RECIPE_ITEMS, recipeItems.size());
    return new Crafts(crafts, reagents, recipeItems);
  }

  /** The items teaching each craft spell, by spell. */
  private static Map<Long, TreeSet<Long>> teachers(ClientDataSource src, Set<Long> crafts,
      Map<Long, List<Long>> learns) {
    Map<Long, List<Long>> itemsByEffect = new HashMap<>();
    for (CsvTable.Row link : require(src.table("ItemXItemEffect"), LINK_COLUMNS).rows()) {
      OptionalLong effect = link.getLong("ItemEffectID");
      OptionalLong item = link.getLong("ItemID");
      if (effect.isPresent() && item.isPresent() && item.getAsLong() > 0) {
        itemsByEffect.computeIfAbsent(effect.getAsLong(), k -> new ArrayList<>()).add(item.getAsLong());
      }
    }
    Map<Long, TreeSet<Long>> out = new HashMap<>();
    for (CsvTable.Row r : require(src.table("ItemEffect"), ITEM_EFFECT_COLUMNS).rows()) {
      int trigger = r.intOr("TriggerType", -1);
      if (trigger != ItemSpellEffect.USE && trigger != ItemSpellEffect.LEARN) {
        continue;
      }
      List<Long> items = itemsByEffect.get(r.longOr("ID", 0));
      if (items == null) {
        continue;
      }
      long spell = r.longOr("SpellID", 0);
      List<Long> taught = new ArrayList<>(learns.getOrDefault(spell, List.of()));
      if (trigger == ItemSpellEffect.LEARN) {
        taught.add(spell);
      }
      for (long t : taught) {
        if (crafts.contains(t)) {
          out.computeIfAbsent(t, k -> new TreeSet<>()).addAll(items);
        }
      }
    }
    return out;
  }

  /** The reagents of one {@code SpellReagents} row (rows of one spell in file order), repeated items added up. */
  private static List<CraftReagent> reagents(long spell, List<CsvTable.Row> rows) {
    Map<Long, Integer> counts = new LinkedHashMap<>();
    for (CsvTable.Row r : rows) {
      for (int i = 0; i < REAGENT_SLOTS; i++) {
        long item = r.longOr("Reagent_" + i, 0);
        int count = r.intOr("ReagentCount_" + i, 0);
        if (item > 0 && count > 0) {
          counts.merge(item, count, Integer::sum);
        }
      }
    }
    return counts.entrySet().stream().map(e -> new CraftReagent(spell, e.getKey(), e.getValue())).toList();
  }

  /** Orders the Create Item rows of one spell: difficulty 0 first, then the lowest effect index. */
  private static long rank(CsvTable.Row r) {
    return (r.intOr("DifficultyID", 0) == 0 ? 0L : 1L << 32) + r.intOr("EffectIndex", 0);
  }

  private static Integer boxed(CsvTable.Row r, String col) {
    if (r == null) {
      return null;
    }
    OptionalInt v = r.getInt(col);
    return v.isPresent() ? v.getAsInt() : null;
  }

  private static Map<Long, String> names(CsvTable t, String col) {
    Map<Long, String> out = new HashMap<>();
    for (CsvTable.Row r : t.rows()) {
      r.getLong("ID").ifPresent(id -> out.put(id, r.get(col)));
    }
    return out;
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
