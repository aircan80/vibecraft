package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.report.ImportReport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
