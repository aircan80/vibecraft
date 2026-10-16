package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.report.ImportReport;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ItemSetLoaderTest {

  private static Map<Long, ItemSetInfo> load(ImportReport report, Set<Long> ids) {
    SpellDescriptionFormatter fmt = new SpellDescriptionFormatter(SpellTables.load(ClientItems.fixture()));
    return ItemSetLoader.load(ClientItems.fixture(), ids, fmt, report);
  }

  @Test
  void setsWithPiecesSkillAndBonusesInThresholdOrder() {
    ImportReport report = new ImportReport();
    Map<Long, ItemSetInfo> sets = load(report, Set.of(421L, 163L, 203L, 999L));

    assertThat(sets.keySet()).containsExactly(163L, 203L, 421L);
    assertThat(sets.get(421L)).isEqualTo(new ItemSetInfo(421, "Bloodvine Garb", java.util.List.of(19682L, 19683L,
        19684L), 197, 300, java.util.List.of(new ItemSetInfo.Bonus(3, 18382,
        "Improves your chance to get a critical strike with spells by 2.0%.", true))));

    ItemSetInfo scarlet = sets.get(163L);
    assertThat(scarlet.name()).isEqualTo("Chain of the Scarlet Crusade");
    assertThat(scarlet.itemIds()).containsExactly(10329L, 10332L, 10328L, 10331L, 10330L, 10333L);
    assertThat(scarlet.requiredSkill()).isNull();
    assertThat(scarlet.bonuses()).extracting(ItemSetInfo.Bonus::threshold).containsExactly(2, 3, 4, 5, 6);
    assertThat(scarlet.bonuses().get(0).text()).isEqualTo("+10 Shadow Resistance.");
    assertThat(scarlet.bonuses().get(1).text()).isEqualTo("+30 Attack Power against Undead.");
    assertThat(scarlet.bonuses().get(3).text()).isEqualTo("Improves your chance to hit by 1.0%.");
    // (4) the proc chance $h is SpellAuraOptions.ProcChance 15 of spell 1293674
    assertThat(scarlet.bonuses().get(2).text()).isEqualTo("Taking damage has a 15% chance to grant Enraging Light "
        + "for 10 sec, causing your melee attacks to deal 20 Holy damage. Deals 3 times as much damage to Undead.");
    assertThat(scarlet.bonuses().get(2).complete()).isTrue();

    assertThat(sets.get(203L).bonuses()).extracting(ItemSetInfo.Bonus::threshold).containsExactly(3, 5, 8);
    assertThat(sets.get(203L).itemIds()).hasSize(8);
    assertThat(report.get(ItemSetLoader.SETS)).isEqualTo(3);
  }

  @Test
  void noIdsNoSets() {
    ImportReport report = new ImportReport();
    assertThat(load(report, Set.of())).isEmpty();
    assertThat(report.get(ItemSetLoader.SETS)).isZero();
    assertThat(report.counters()).containsKey(ItemSetLoader.SETS);
  }
}
