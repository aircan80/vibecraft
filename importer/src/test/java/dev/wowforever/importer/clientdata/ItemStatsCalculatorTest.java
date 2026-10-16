package dev.wowforever.importer.clientdata;

import static dev.wowforever.importer.clientdata.ClientItems.forever;
import static dev.wowforever.importer.clientdata.ClientItems.share;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.report.ImportReport;
import org.junit.jupiter.api.Test;

class ItemStatsCalculatorTest {
  private static final ScalingTables T = ClientItems.fixtureTables();

  private static ItemStat s(int id, int value) {
    return new ItemStat(id, value);
  }

  @Test
  void lionheartHelmUsesTheEpicHeadBudget() {
    // il 61, epic, head: Epic_0 = 45; 45 * 0.4000 = 18, 45 * 0.6222 = 28.0, 45 * 0.4444 = 20.0
    ImportReport report = new ImportReport();
    assertThat(ItemStatsCalculator.stats(ClientItems.one(12640), T, report))
        .containsExactly(s(4, 18), s(32, 28), s(31, 20));
    assertThat(report.get("clientdata.unverified.stats")).isZero();
  }

  @Test
  void thunderfuryUsesTheOneHandBudgetAndKeepsResistancesInSlotOrder() {
    // il 80, legendary, one-hand: Epic_3 = 23; agility 2174, stamina 3478, fire 3478, nature 3913
    assertThat(ItemStatsCalculator.stats(ClientItems.one(19019), T, new ImportReport()))
        .containsExactly(s(3, 5), s(7, 8), s(51, 8), s(55, 9));
  }

  @Test
  void bloodvineBootsUseTheSecondSlotGroup() {
    // il 65, rare, feet: Superior_1 = 28; 28 * 0.5714 = 16.0, 28 * 0.3571 = 10.0, 28 * 0.6786 = 19.0
    assertThat(ItemStatsCalculator.stats(ClientItems.one(19684), T, new ImportReport()))
        .containsExactly(s(5, 16), s(31, 10), s(45, 19));
  }

  @Test
  void rangedWeaponsUseTheRangedSlotGroup() {
    // High Warlord's Crossbow: il 78, epic, ranged right: Epic_4 = 17; stamina 4118 -> 7, ranged AP 21176 -> 36
    assertThat(ItemStatsCalculator.stats(ClientItems.one(18837), T, new ImportReport()))
        .containsExactly(s(7, 7), s(39, 36));
  }

  @Test
  void bonusArmorAndBlockValueStayStats() {
    // Ring of Protection: il 60, rare, finger: Superior_2 = 19; stamina 4737 -> 9, bonus armour 78947 -> 150
    assertThat(ItemStatsCalculator.stats(ClientItems.one(15855), T, new ImportReport()))
        .containsExactly(s(7, 9), s(50, 150));
    // The Immovable Object: il 60, epic, shield: Epic_2 = 25; stamina 5556 -> 14, block value 10000 -> 25
    assertThat(ItemStatsCalculator.stats(ClientItems.one(19321), T, new ImportReport()))
        .containsExactly(s(7, 14), s(48, 25));
  }

  @Test
  void eraRowsCarryAbsoluteStatsThenResistancesInClientOrder() {
    // Arcanist Boots (Era): stamina 13, intellect 14, spirit 11; Resistances_5 (shadow) 10
    assertThat(ItemStatsCalculator.stats(ClientItems.one(16800), T, new ImportReport()))
        .containsExactly(s(7, 13), s(5, 14), s(6, 11), s(54, 10));
    // Felheart Gloves (Era): Resistances_2 (fire) 7
    assertThat(ItemStatsCalculator.stats(ClientItems.one(16805), T, new ImportReport()))
        .containsExactly(s(5, 15), s(6, 8), s(7, 18), s(51, 7));
  }

  @Test
  void zeroBudgetProducesNoStats() {
    ImportReport report = new ImportReport();
    assertThat(ItemStatsCalculator.stats(forever(4, 1, 5, 3, 65, null, 0.0, null, share(5, 0), share(7, 0)), T,
        report)).as("all shares zero").isEmpty();
    assertThat(report.get("clientdata.unverified.stats")).as("nothing to verify").isZero();
    assertThat(ItemStatsCalculator.stats(ClientItems.one(17204), T, report)).as("no stat slots").isEmpty();
  }

  @Test
  void aStatThatRoundsToZeroIsLeftOut() {
    // Good_2 at 61 is 15; 15 * 0.0300 = 0.45 rounds to 0
    assertThat(ItemStatsCalculator.stats(forever(4, 1, 9, 2, 61, null, 0.0, null, share(7, 300), share(5, 10000)),
        T, new ImportReport())).containsExactly(s(5, 15));
  }

  @Test
  void roundsHalfUp() {
    // Good_0 at 61 is 27; 27 * 0.5000 = 13.5 -> 14
    assertThat(ItemStatsCalculator.stats(forever(4, 1, 5, 2, 61, null, 0.0, null, share(7, 5000)), T,
        new ImportReport())).containsExactly(s(7, 14));
  }

  @Test
  void aMissingBudgetIsUnverifiedNotGuessed() {
    ImportReport report = new ImportReport();
    assertThat(ItemStatsCalculator.stats(forever(4, 1, 5, 3, 3, null, 0.0, null, share(7, 5000)), T, report))
        .as("no RandPropPoints row for item level 3").isEmpty();
    assertThat(ItemStatsCalculator.stats(forever(1, 0, 18, 3, 61, null, 0.0, null, share(7, 5000)), T, report))
        .as("a bag has no slot group").isEmpty();
    assertThat(report.get("clientdata.unverified.stats")).isEqualTo(2);
  }

  @Test
  void itemOnlyItemsHaveNoStats() {
    ClientItem iconOnly = new ClientItem(9, false, null, null, null, 4, 1, 5, null, null, null, null, 1L, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null);
    assertThat(ItemStatsCalculator.stats(iconOnly, T, new ImportReport())).isEmpty();
  }
}
