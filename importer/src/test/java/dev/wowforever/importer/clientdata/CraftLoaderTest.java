package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.report.ImportReport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The crafts of the client-data fixture (build 1.60.1.70425): Heavy Leather (20649, Leatherworking), Sulfuron Hammer
 * (21161, Blacksmithing), Eye of Sulfuras (21160, no SkillLineAbility row), Coarse Dynamite (3931, Engineering, a
 * yield range), Transmute: Air to Fire (17559, Alchemy, taught by Recipe: Transmute Air to Fire 13482) and Prowler
 * Steak (1225758, Cooking, taught by Recipe: Prowler Steak 238645 and a second recipe 250394). Enchant Weapon -
 * Dismantle (435481) has reagents and a skill row but creates nothing.
 */
class CraftLoaderTest {
  private static Crafts crafts;
  private static ImportReport report;
  private static Map<Long, Craft> bySpell;

  @BeforeAll
  static void load() {
    report = new ImportReport();
    crafts = CraftLoader.load(new ClientDataSource(Fixtures.clientDataRoot()), report);
    bySpell = crafts.crafts().stream().collect(Collectors.toMap(Craft::spellId, Function.identity()));
  }

  @Test
  void everySpellThatCreatesAnItemIsACraftInSpellOrder() {
    assertThat(crafts.crafts()).extracting(Craft::spellId)
        .containsExactly(3931L, 17559L, 20649L, 21160L, 21161L, 1225758L);
    assertThat(report.get(CraftLoader.CRAFTS)).isEqualTo(6);
  }

  @Test
  void createdItemSkillAndRanks() {
    assertThat(bySpell.get(20649L)).isEqualTo(new Craft(20649, "Heavy Leather", 4234, 1, 1, 165L, "Leatherworking",
        1, 125, 135, null));
    assertThat(bySpell.get(21161L)).isEqualTo(new Craft(21161, "Sulfuron Hammer", 17193, 1, 1, 164L,
        "Blacksmithing", 1, 325, 350, null));
  }

  @Test
  void quantityIsTheBasePointsSpreadByTheVariance() {
    Craft prowler = bySpell.get(1225758L);
    assertThat(prowler.quantityMin()).isEqualTo(2);
    assertThat(prowler.quantityMax()).isEqualTo(2);
    // Coarse Dynamite: EffectBasePointsF 2, Variance 1, so 2 * (1 -/+ 1/2)
    Craft dynamite = bySpell.get(3931L);
    assertThat(dynamite.createdItemId()).isEqualTo(4365);
    assertThat(dynamite.quantityMin()).isEqualTo(1);
    assertThat(dynamite.quantityMax()).isEqualTo(3);
    assertThat(dynamite.skillName()).isEqualTo("Engineering");
    // Transmute: Air to Fire stores EffectBasePointsF 0: a Create Item effect makes at least one
    assertThat(bySpell.get(17559L).quantityMin()).isEqualTo(1);
    assertThat(bySpell.get(17559L).quantityMax()).isEqualTo(1);
  }

  @Test
  void reagentsInSlotOrderWithoutEmptySlots() {
    assertThat(crafts.reagentsOf(20649)).containsExactly(new CraftReagent(20649, 2319, 5));
    assertThat(crafts.reagentsOf(21161)).containsExactly(new CraftReagent(21161, 17203, 8),
        new CraftReagent(21161, 11371, 20), new CraftReagent(21161, 12360, 50), new CraftReagent(21161, 7078, 25),
        new CraftReagent(21161, 11382, 10), new CraftReagent(21161, 17011, 10), new CraftReagent(21161, 17010, 10));
    assertThat(crafts.reagents()).extracting(CraftReagent::spellId).doesNotContain(435481L, 1226808L);
    assertThat(report.get(CraftLoader.REAGENTS)).isEqualTo(15).isEqualTo(crafts.reagents().size());
  }

  @Test
  void aCraftWithoutASkillLineAbilityRowKeepsNullSkillFields() {
    assertThat(bySpell.get(21160L)).isEqualTo(new Craft(21160, "Eye of Sulfuras", 17182, 1, 1, null, null, null,
        null, null, null));
    assertThat(crafts.reagentsOf(21160)).extracting(CraftReagent::itemId).containsExactly(17193L, 17204L);
    assertThat(report.get(CraftLoader.NO_SKILL)).isEqualTo(1);
  }

  @Test
  void aRecipeItemLinksTheCraftItTeachesEveryItemKept() {
    assertThat(bySpell.get(17559L).recipeItemId()).isEqualTo(13482L);
    assertThat(bySpell.get(1225758L).recipeItemId()).as("the lowest of 238645 and 250394").isEqualTo(238645L);
    assertThat(crafts.recipeItems()).containsExactly(new CraftRecipeItem(17559, 13482),
        new CraftRecipeItem(1225758, 238645), new CraftRecipeItem(1225758, 250394));
    assertThat(report.get(CraftLoader.RECIPE_ITEMS)).isEqualTo(3);
    assertThat(bySpell.get(1225758L).skillName()).isEqualTo("Cooking");
    assertThat(report.get(CraftLoader.RECIPES)).as("crafts with at least one recipe item").isEqualTo(2);
    assertThat(report.notes()).as("every recipe item is kept, nothing to note")
        .noneMatch(n -> n.startsWith(CraftLoader.RECIPES));
  }

  @Test
  void aSpellThatCreatesNothingIsSkipped() {
    assertThat(bySpell).doesNotContainKey(435481L);
  }

  @Test
  void aLearnSpellThatTeachesACraftLinksTheItemAsItsRecipe(@TempDir Path tmp) throws Exception {
    // item 900 uses spell 800, whose Effect 36 (Learn Spell) teaches craft 700
    Path csv = Files.createDirectories(tmp.resolve("csv"));
    write(csv, "SpellEffect", "ID,EffectIndex,DifficultyID,Effect,EffectItemType,EffectBasePointsF,Variance,"
        + "EffectTriggerSpell,SpellID\n1,0,0,24,50,1,0,0,700\n2,0,0,36,0,0,0,700,800\n3,0,0,24,0,1,0,0,701\n");
    write(csv, "SpellName", "ID,Name_lang\n700,Craft\n");
    write(csv, "SkillLine", "DisplayName_lang,ID\nTailoring,197\n");
    write(csv, "SkillLineAbility", "ID,SkillLine,Spell,MinSkillLineRank,TrivialSkillLineRankLow,"
        + "TrivialSkillLineRankHigh\n1,197,700,40,60,80\n");
    write(csv, "SpellReagents", "ID,SpellID," + slots("Reagent_") + "," + slots("ReagentCount_") + "\n"
        + "1,700,60,60,61,0,0,0,0,0,2,3,0,0,0,0,0,0\n");
    write(csv, "ItemEffect", "ID,TriggerType,SpellID\n10,0,800\n");
    write(csv, "ItemXItemEffect", "ID,ItemEffectID,ItemID\n1,10,900\n");
    ImportReport r = new ImportReport();
    Crafts c = CraftLoader.load(new ClientDataSource(tmp), r);

    assertThat(c.crafts()).containsExactly(new Craft(700, "Craft", 50, 1, 1, 197L, "Tailoring", 40, 60, 80, 900L));
    assertThat(c.reagents()).as("a repeated reagent adds up, one with count 0 is skipped")
        .containsExactly(new CraftReagent(700, 60, 5));
    assertThat(r.get(CraftLoader.RECIPES)).isEqualTo(1);
    assertThat(c.recipeItems()).containsExactly(new CraftRecipeItem(700, 900));
    assertThat(r.get(CraftLoader.RECIPE_ITEMS)).isEqualTo(1);
    assertThat(r.get(CraftLoader.NO_SKILL)).isZero();
  }

  @Test
  void aMissingSpellEffectTableFailsNamingIt(@TempDir Path tmp) throws Exception {
    Path csv = Files.createDirectories(tmp.resolve("csv"));
    write(csv, "SkillLineAbility", ABILITY_HEADER);
    write(csv, "SpellReagents", REAGENT_HEADER);
    assertThatThrownBy(() -> CraftLoader.load(new ClientDataSource(tmp), new ImportReport()))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("SpellEffect");
  }

  @Test
  void withoutTheCraftingTablesCraftingIsSkippedAndNoted(@TempDir Path tmp) throws Exception {
    // a client-data directory converted before SpellReagents and SkillLineAbility joined the required tables
    Path csv = copyFixtureCsv(tmp, "SkillLineAbility.csv", "SpellReagents.csv");
    ImportReport r = new ImportReport();
    Crafts c = CraftLoader.load(new ClientDataSource(tmp), r);

    assertThat(c).isEqualTo(Crafts.EMPTY);
    assertThat(r.get(CraftLoader.SKIPPED)).isEqualTo(1);
    assertThat(r.counters()).containsEntry(CraftLoader.CRAFTS, 0L);
    assertThat(r.notes()).anyMatch(n -> n.startsWith(CraftLoader.SKIPPED + ": ") && n.contains("SkillLineAbility")
        && n.contains("SpellReagents"));
    assertThat(Files.exists(csv.resolve("SpellEffect.csv"))).isTrue();
  }

  @Test
  void oneMissingCraftingTableIsEnoughToSkipAndIsNamed(@TempDir Path tmp) throws Exception {
    copyFixtureCsv(tmp, "SpellReagents.csv");
    ImportReport r = new ImportReport();
    assertThat(CraftLoader.load(new ClientDataSource(tmp), r)).isEqualTo(Crafts.EMPTY);
    assertThat(r.get(CraftLoader.SKIPPED)).isEqualTo(1);
    assertThat(r.notes()).anyMatch(n -> n.startsWith(CraftLoader.SKIPPED + ": ") && n.contains("SpellReagents")
        && !n.contains("SkillLineAbility"));
  }

  @Test
  void theFixtureIsNotSkippedAndHasNoDroppedRows() {
    assertThat(report.get(CraftLoader.SKIPPED)).isZero();
    assertThat(report.counters()).containsEntry(CraftLoader.EXTRA_CREATES, 0L)
        .containsEntry(CraftLoader.ROWS_WITHOUT_SPELL, 0L);
  }

  @Test
  void aSecondCreateItemRowIsCountedAndTheDifficulty0LowestIndexRowWins(@TempDir Path tmp) throws Exception {
    Path csv = Files.createDirectories(tmp.resolve("csv"));
    // spell 710: index 1 (item 51), a difficulty 1 row at index 0 (item 52), then difficulty 0 index 0 (item 50)
    write(csv, "SpellEffect", EFFECT_HEADER + "1,1,0,24,51,1,0,0,710\n2,0,1,24,52,1,0,0,710\n"
        + "3,0,0,24,50,1,0,0,710\n4,0,0,24,60,2,0,0,711\n");
    writeRest(csv, "", "", "", "");
    ImportReport r = new ImportReport();
    Crafts c = CraftLoader.load(new ClientDataSource(tmp), r);

    assertThat(c.crafts()).extracting(Craft::spellId, Craft::createdItemId)
        .containsExactly(org.assertj.core.groups.Tuple.tuple(710L, 50L), org.assertj.core.groups.Tuple.tuple(711L, 60L));
    assertThat(r.get(CraftLoader.EXTRA_CREATES)).isEqualTo(2);
    assertThat(r.notes()).anyMatch(n -> n.startsWith(CraftLoader.EXTRA_CREATES + ": ") && n.contains("710")
        && !n.contains("711"));
  }

  @Test
  void rowsWithoutASpellAreCounted(@TempDir Path tmp) throws Exception {
    Path csv = Files.createDirectories(tmp.resolve("csv"));
    write(csv, "SpellEffect", EFFECT_HEADER + "1,0,0,24,50,1,0,0,700\n2,0,0,24,51,1,0,0,\n");
    writeRest(csv, "1,197,,1,10,20\n2,197,700,1,10,20\n",
        "1,,60,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0\n2,700,61,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0\n", "", "");
    ImportReport r = new ImportReport();
    Crafts c = CraftLoader.load(new ClientDataSource(tmp), r);

    assertThat(c.crafts()).extracting(Craft::spellId).containsExactly(700L);
    assertThat(c.reagents()).containsExactly(new CraftReagent(700, 61, 2));
    assertThat(r.get(CraftLoader.ROWS_WITHOUT_SPELL)).as("one row each of SpellEffect, SkillLineAbility and"
        + " SpellReagents").isEqualTo(3);
    assertThat(r.notes()).anyMatch(n -> n.startsWith(CraftLoader.ROWS_WITHOUT_SPELL + ": ")
        && n.contains("SpellEffect 1") && n.contains("SkillLineAbility 1") && n.contains("SpellReagents 1"));
  }

  @Test
  void anItemTeachingTwoCraftsAndALearnEffectNamingALearnSpell(@TempDir Path tmp) throws Exception {
    Path csv = Files.createDirectories(tmp.resolve("csv"));
    // crafts 700, 701 and 702; spell 801 is no craft but its Effect 36 teaches 702
    write(csv, "SpellEffect", EFFECT_HEADER + "1,0,0,24,50,1,0,0,700\n2,0,0,24,51,1,0,0,701\n"
        + "3,0,0,24,52,1,0,0,702\n4,0,0,36,0,0,0,702,801\n");
    // item 901 teaches 700 and 701 (two trigger 6 effects); item 902's trigger 6 effect names learn spell 801
    writeRest(csv, "", "", "10,6,700\n11,6,701\n12,6,801\n", "1,10,901\n2,11,901\n3,12,902\n");
    ImportReport r = new ImportReport();
    Crafts c = CraftLoader.load(new ClientDataSource(tmp), r);

    assertThat(c.recipeItems()).containsExactly(new CraftRecipeItem(700, 901), new CraftRecipeItem(701, 901),
        new CraftRecipeItem(702, 902));
    assertThat(c.crafts()).extracting(Craft::recipeItemId).containsExactly(901L, 901L, 902L);
    assertThat(r.get(CraftLoader.RECIPES)).isEqualTo(3);
    assertThat(r.get(CraftLoader.RECIPE_ITEMS)).isEqualTo(3);
  }

  private static final String EFFECT_HEADER = "ID,EffectIndex,DifficultyID,Effect,EffectItemType,EffectBasePointsF,"
      + "Variance,EffectTriggerSpell,SpellID\n";
  private static final String ABILITY_HEADER = "ID,SkillLine,Spell,MinSkillLineRank,TrivialSkillLineRankLow,"
      + "TrivialSkillLineRankHigh\n";
  private static final String REAGENT_HEADER = "ID,SpellID," + slots("Reagent_") + "," + slots("ReagentCount_") + "\n";

  /** Every table but SpellEffect, with the given rows (header kept) for the four that tests vary. */
  private static void writeRest(Path csv, String abilities, String reagents, String itemEffects, String links)
      throws Exception {
    write(csv, "SpellName", "ID,Name_lang\n");
    write(csv, "SkillLine", "DisplayName_lang,ID\nTailoring,197\n");
    write(csv, "SkillLineAbility", ABILITY_HEADER + abilities);
    write(csv, "SpellReagents", REAGENT_HEADER + reagents);
    write(csv, "ItemEffect", "ID,TriggerType,SpellID\n" + itemEffects);
    write(csv, "ItemXItemEffect", "ID,ItemEffectID,ItemID\n" + links);
  }

  /** The fixture's CSVs copied to {@code tmp/csv}, without {@code omit}. */
  private static Path copyFixtureCsv(Path tmp, String... omit) throws Exception {
    Path csv = Files.createDirectories(tmp.resolve("csv"));
    List<String> skip = List.of(omit);
    try (var files = Files.list(Fixtures.clientDataRoot().resolve("csv"))) {
      for (Path f : (Iterable<Path>) files::iterator) {
        if (!skip.contains(f.getFileName().toString())) {
          Files.copy(f, csv.resolve(f.getFileName().toString()));
        }
      }
    }
    return csv;
  }

  private static String slots(String prefix) {
    return java.util.stream.IntStream.range(0, 8).mapToObj(i -> prefix + i).collect(Collectors.joining(","));
  }

  private static void write(Path csv, String table, String text) throws Exception {
    Files.writeString(csv.resolve(table + ".csv"), text, StandardCharsets.UTF_8);
  }

  @Test
  void reagentsOfAnUnknownSpellIsEmpty() {
    assertThat(crafts.reagentsOf(1)).isEqualTo(List.of());
  }
}
