package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.report.ImportReport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemEffectLoaderTest {

  private static Map<Long, List<ItemSpellEffect>> load(ImportReport report, long... ids) {
    Map<Long, ClientItem> items = ClientItems.load(ids);
    SpellDescriptionFormatter fmt = new SpellDescriptionFormatter(SpellTables.load(ClientItems.fixture()));
    return ItemEffectLoader.load(ClientItems.fixture(), items, fmt, report);
  }

  @Test
  void useEquipAndProcEffectsWithFormattedTextCooldownAndCharges() {
    ImportReport report = new ImportReport();
    Map<Long, List<ItemSpellEffect>> effects = load(report, 5079, 17783, 19019, 5613, 118, 25);

    assertThat(effects.get(5079L)).containsExactly(new ItemSpellEffect(0, ItemSpellEffect.USE, 1139,
        "Increases time between target's attacks by 20% and reduces their movement speed by 40% for 15 sec.", true,
        0, 420000, false));
    assertThat(effects.get(17783L)).singleElement().satisfies(e -> {
      assertThat(e.trigger()).isEqualTo(ItemSpellEffect.EQUIP);
      assertThat(e.text()).isEqualTo("When struck in combat inflicts 4 Nature damage to the attacker.");
    });
    assertThat(effects.get(19019L)).singleElement().satisfies(e -> {
      assertThat(e.trigger()).isEqualTo(ItemSpellEffect.CHANCE_ON_HIT);
      assertThat(e.spellId()).isEqualTo(21992);
    });
    assertThat(effects.get(5613L)).singleElement().satisfies(e -> {
      assertThat(e.charges()).isEqualTo(3);
      assertThat(e.cooldownMs()).isEqualTo(300000);
      assertThat(e.text()).isEqualTo("Purifies the friendly target, removing 1 disease effect and 1 poison effect.");
    });
    assertThat(effects.get(118L)).singleElement().satisfies(e -> {
      assertThat(e.text()).isEqualTo("Restores 70 to 90 health.");
      assertThat(e.cooldownMs()).as("the category cooldown when the item's own is unset").isEqualTo(120000);
      assertThat(e.charges()).isEqualTo(-1);
    });
    assertThat(effects).doesNotContainKey(25L);
    assertThat(report.get(ItemEffectLoader.EFFECTS)).as("one each; Worn Shortsword has none").isEqualTo(5);
    assertThat(report.get(ItemEffectLoader.OTHER_TRIGGER)).isZero();
  }

  @Test
  void aRecipeUsesItsDescriptionForTheTaughtSpell() {
    ImportReport report = new ImportReport();
    Map<Long, List<ItemSpellEffect>> effects = load(report, 13482, 215129);
    // 13482: Use "Learning" (402265, no description) + learn 17559; the Use line is the item description
    assertThat(effects.get(13482L)).containsExactly(new ItemSpellEffect(0, ItemSpellEffect.USE, 17559,
        "Teaches you how to transmute Essence of Air into Essence of Fire.", true, -1, 0, true));
    // 215129's description is itself a template ($439164s1 = 75 +/- 20 % = 60 to 90), formatted for the taught
    // spell; the trailing CRLF is trimmed
    assertThat(effects.get(215129L)).singleElement().satisfies(e -> {
      assertThat(e.spellId()).isEqualTo(435481);
      assertThat(e.text()).isEqualTo("Permanently enchant a Weapon to cause all spells and attacks to sometimes deal "
          + "60 to 90 additional damage to mechanical creatures.");
      assertThat(e.fromItemDescription()).isTrue();
    });
    assertThat(report.get(ItemEffectLoader.EFFECTS)).isEqualTo(2);
  }

  @Test
  void anEraSourcedItemTakesItsEffectsFromTheEraTableWithForeverSpellText() {
    ImportReport report = new ImportReport();
    Map<Long, List<ItemSpellEffect>> effects = load(report, 19137, 16800, 19019);
    // Onslaught Girdle: no Forever ItemSparse or effect rows; Era ItemEffect rows 102124 (slot 0) and 102125 (slot 1)
    assertThat(effects.get(19137L)).extracting(ItemSpellEffect::ordinal, ItemSpellEffect::trigger,
        ItemSpellEffect::spellId, ItemSpellEffect::text).containsExactly(
        tuple(0, ItemSpellEffect.EQUIP, 7597L,
            "Improves your chance to get a critical strike with melee attacks by 1.0%."),
        tuple(1, ItemSpellEffect.EQUIP, 15464L, "Improves your chance to hit by 1.0%."));
    assertThat(effects.get(16800L)).as("Arcanist Boots").extracting(ItemSpellEffect::spellId)
        .containsExactly(18384L, 9416L);
    assertThat(effects.get(19019L)).as("a Forever item is unchanged").singleElement()
        .satisfies(e -> assertThat(e.spellId()).isEqualTo(21992));
    assertThat(report.get(ItemEffectLoader.EFFECTS)).isEqualTo(5);
    assertThat(report.get(ItemEffectLoader.ERA_UNRESOLVED)).isZero();
  }

  @Test
  void withoutAnEraEffectTableEraSourcedItemsHaveNoEffects(@TempDir Path tmp) throws IOException {
    ClientDataSource src = fixtureCopy(tmp);
    Files.delete(tmp.resolve("csv/ItemEffect.era.csv"));
    ImportReport report = new ImportReport();
    Map<Long, List<ItemSpellEffect>> effects = load(src, report, 19137, 19019);
    assertThat(effects).containsOnlyKeys(19019L);
    assertThat(report.get(ItemEffectLoader.ERA_UNRESOLVED)).isZero();
  }

  @Test
  void foreverRowsWinAndEraSpellsMissingFromForeverAreCounted(@TempDir Path tmp) throws IOException {
    ClientDataSource src = fixtureCopy(tmp);
    Files.writeString(tmp.resolve("csv/ItemEffect.era.csv"),
        "900001,0,1,0,-1,-1,0,7597,0,0,17204\n" // Eye of Sulfuras: a Forever item, its Era rows are never read
            + "900002,0,1,0,-1,-1,0,7597,0,0,14145\n" // Cursed Felblade: Era-sourced, but Forever has its effect
            + "900003,2,1,0,-1,-1,0,99999999,0,0,19137\n", // a spell neither Forever Spell nor SpellName has
        StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    ImportReport report = new ImportReport();
    Map<Long, List<ItemSpellEffect>> effects = load(src, report, 17204, 14145, 19137);
    Map<Long, List<ItemSpellEffect>> plain = load(new ImportReport(), 17204, 14145, 19137);

    assertThat(effects.get(17204L)).isEqualTo(plain.get(17204L));
    assertThat(effects.get(14145L)).isEqualTo(plain.get(14145L)).singleElement()
        .satisfies(e -> assertThat(e.spellId()).isEqualTo(18381));
    assertThat(effects.get(19137L)).as("no line for the unresolved spell").isEqualTo(plain.get(19137L)).hasSize(2);
    assertThat(report.get(ItemEffectLoader.ERA_UNRESOLVED)).isEqualTo(1);
  }

  @Test
  void anEraEffectTableWithoutParentItemIdIsReportedNotFatal(@TempDir Path tmp) throws IOException {
    // another Era build could link effects through an ItemXItemEffect table instead of ItemEffect.ParentItemID
    ClientDataSource src = fixtureCopy(tmp);
    Files.writeString(tmp.resolve("csv/ItemEffect.era.csv"),
        "ID,LegacySlotIndex,TriggerType,Charges,CoolDownMSec,CategoryCoolDownMSec,SpellCategoryID,SpellID\n"
            + "102124,0,1,0,-1,-1,0,7597\n", StandardCharsets.UTF_8);
    ImportReport report = new ImportReport();
    Map<Long, List<ItemSpellEffect>> effects = load(src, report, 19137, 19019);

    assertThat(effects).as("no Era effects, Forever ones intact").containsOnlyKeys(19019L);
    assertThat(report.get(ItemEffectLoader.ERA_TABLE_UNUSABLE)).isEqualTo(1);
    assertThat(report.notes()).anySatisfy(n -> assertThat(n)
        .startsWith(ItemEffectLoader.ERA_TABLE_UNUSABLE + ": ItemEffect.era")
        .contains("no column ParentItemID"));
  }

  @Test
  void aUsableEraEffectTableReportsNoProblem() {
    ImportReport report = new ImportReport();
    load(report, 19137);
    assertThat(report.counters()).doesNotContainKey(ItemEffectLoader.ERA_TABLE_UNUSABLE);
  }

  private static Map<Long, List<ItemSpellEffect>> load(ClientDataSource src, ImportReport report, long... ids) {
    Map<Long, ClientItem> items = ItemSparseLoader.load(src,
        Arrays.stream(ids).boxed().collect(Collectors.toSet()), new ImportReport());
    return ItemEffectLoader.load(src, items, new SpellDescriptionFormatter(SpellTables.load(src)), report);
  }

  /** The client-data fixture copied into {@code tmp}, to add or remove rows. */
  private static ClientDataSource fixtureCopy(Path tmp) throws IOException {
    Path from = Fixtures.clientDataRoot();
    try (Stream<Path> files = Files.walk(from)) {
      for (Path p : files.toList()) {
        Path to = tmp.resolve(from.relativize(p).toString());
        if (Files.isDirectory(p)) {
          Files.createDirectories(to);
        } else {
          Files.copy(p, to);
        }
      }
    }
    return new ClientDataSource(tmp);
  }

  @Test
  void anEffectWithoutDescriptionKeepsItsRowWithNullText() {
    Map<Long, List<ItemSpellEffect>> effects = load(new ImportReport(), 17204, 6948);
    assertThat(effects.get(17204L)).singleElement().satisfies(e -> {
      assertThat(e.spellId()).isEqualTo(21160);
      assertThat(e.text()).startsWith("The Eye of Sulfuras can be combined");
    });
    assertThat(effects.get(6948L)).singleElement().satisfies(e -> {
      assertThat(e.text()).startsWith("Returns you to $z.");
      assertThat(e.complete()).isFalse();
    });
  }
}
