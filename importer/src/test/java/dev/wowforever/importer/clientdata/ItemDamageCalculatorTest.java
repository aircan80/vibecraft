package dev.wowforever.importer.clientdata;

import static dev.wowforever.importer.clientdata.ClientItems.forever;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import dev.wowforever.importer.clientdata.ScalingTables.DamageKind;
import dev.wowforever.importer.report.ImportReport;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ItemDamageCalculatorTest {
  private static final ScalingTables T = ClientItems.fixtureTables();

  private static ItemDamage damage(long id) {
    return ItemDamageCalculator.damage(ClientItems.one(id), T, new ImportReport()).orElseThrow();
  }

  @Test
  void wornShortsword() {
    // one-hand, il 2, common: 1.01635 dps * 1.9 s = 1.931; min floor(1.931 * 0.7) = 1, max round(1.931 * 1.3) = 3
    ItemDamage d = damage(25);
    assertThat(d.min()).isEqualTo(1);
    assertThat(d.max()).isEqualTo(3);
    assertThat(d.school()).isZero();
    assertThat(d.delayMs()).isEqualTo(1900);
    assertThat(d.dps()).as("(1 + 3) / 2 / 1.9").isCloseTo(1.0526, offset(1e-4));
  }

  @Test
  void thunderfuryRoundsTheMaximumHalfUp() {
    // one-hand, il 80, legendary: 61.84795 * 1.9 = 117.511; min floor(82.258) = 82, max round(152.764) = 153
    ItemDamage d = damage(19019);
    assertThat(List.of(d.min(), d.max(), d.delayMs())).containsExactly(82, 153, 1900);
  }

  @Test
  void ashbringerUsesTheTwoHandTable() {
    // two-hand, il 76, legendary: 74.74601 * 3.0 = 224.238; variance 0.4: min floor(179.39) = 179, max 269
    ItemDamage d = damage(13262);
    assertThat(List.of(d.min(), d.max())).containsExactly(179, 269);
    assertThat(d.dps()).isCloseTo(74.667, offset(1e-3));
  }

  @Test
  void crossbowUsesTheRangedTable() {
    // ranged right, il 78, epic: 46.49791 * 2.9 = 134.844; min floor(107.875) = 107, max round(161.81) = 162
    ItemDamage d = damage(18837);
    assertThat(List.of(d.min(), d.max(), d.delayMs())).containsExactly(107, 162, 2900);
  }

  @Test
  void wandKeepsItsSchool() {
    ItemDamage d = damage(5248);
    assertThat(d.school()).as("DamageType 3, nature").isEqualTo(3);
    assertThat(List.of(d.min(), d.max(), d.delayMs())).containsExactly(27, 52, 1300);
  }

  @Test
  void ammoDamageIsPerSecondWithoutSpeed() {
    // Ice Threaded Arrow: ammo, il 54, uncommon; Wowhead "Adds 16.5 damage per second"
    ItemDamage d = damage(19316);
    assertThat(List.of(d.min(), d.max(), d.delayMs())).containsExactly(16, 17, 0);
    assertThat(d.dps()).isCloseTo(16.5, offset(1e-9));
  }

  @Test
  void eraRowsCarryAbsoluteDamage() {
    ItemDamage d = damage(5191); // Cruel Barb, Era: 30 - 57, 2.8 s
    assertThat(List.of(d.min(), d.max(), d.delayMs())).containsExactly(30, 57, 2800);
    assertThat(d.dps()).isCloseTo(15.536, offset(1e-3));
  }

  @Test
  void kindFromClassSubclassAndInventoryType() {
    assertThat(ItemDamageCalculator.kind(item(2, 7, 13, 0))).hasValue(DamageKind.ONE_HAND);
    assertThat(ItemDamageCalculator.kind(item(2, 15, 21, 0))).hasValue(DamageKind.ONE_HAND);
    assertThat(ItemDamageCalculator.kind(item(2, 13, 22, 0))).hasValue(DamageKind.ONE_HAND);
    assertThat(ItemDamageCalculator.kind(item(2, 8, 17, 0))).hasValue(DamageKind.TWO_HAND);
    assertThat(ItemDamageCalculator.kind(item(2, 10, 17, 0x200))).hasValue(DamageKind.TWO_HAND_CASTER);
    assertThat(ItemDamageCalculator.kind(item(2, 4, 21, 0x200))).hasValue(DamageKind.ONE_HAND_CASTER);
    assertThat(ItemDamageCalculator.kind(item(2, 2, 15, 0))).hasValue(DamageKind.RANGED);
    assertThat(ItemDamageCalculator.kind(item(2, 3, 26, 0))).hasValue(DamageKind.RANGED);
    assertThat(ItemDamageCalculator.kind(item(2, 18, 26, 0))).hasValue(DamageKind.RANGED);
    assertThat(ItemDamageCalculator.kind(item(2, 19, 26, 0))).hasValue(DamageKind.WAND);
    assertThat(ItemDamageCalculator.kind(item(2, 16, 25, 0))).hasValue(DamageKind.THROWN);
    assertThat(ItemDamageCalculator.kind(item(6, 2, 24, 0))).hasValue(DamageKind.AMMO);
    assertThat(ItemDamageCalculator.kind(item(4, 1, 5, 0))).as("armour").isEmpty();
    assertThat(ItemDamageCalculator.kind(item(2, 14, 0, 0))).as("a weapon that cannot be equipped").isEmpty();
  }

  @Test
  void foreverCasterWeaponsAreUnverified() {
    // Nimboya's Mystical Staff: Flags_4 0x100; Wowhead shows 48 - 73, the tables give 65 - 99
    ImportReport report = new ImportReport();
    assertThat(ItemDamageCalculator.damage(ClientItems.one(4134), T, report)).isEmpty();
    assertThat(report.get("clientdata.unverified.damage")).isEqualTo(1);
  }

  @Test
  void inconsistentInputsAreUnverified() {
    ImportReport report = new ImportReport();
    assertThat(ItemDamageCalculator.damage(forever(2, 7, 13, 2, 3, 2000, 0.6, null), T, report))
        .as("no ItemDamageOneHand row for item level 3").isEmpty();
    assertThat(ItemDamageCalculator.damage(forever(2, 7, 13, 2, 61, null, 0.6, null), T, report))
        .as("a weapon without a delay").isEmpty();
    assertThat(ItemDamageCalculator.damage(forever(2, 14, 0, 2, 61, 2000, 0.6, null), T, report))
        .as("a weapon of no known kind").isEmpty();
    assertThat(report.get("clientdata.unverified.damage")).isEqualTo(3);
  }

  @Test
  void nonWeaponsHaveNoDamageAndAreNotCounted() {
    ImportReport report = new ImportReport();
    assertThat(ItemDamageCalculator.damage(ClientItems.one(12640), T, report)).isEqualTo(Optional.empty());
    assertThat(report.get("clientdata.unverified.damage")).isZero();
  }

  @Test
  void minimumIsAtLeastOne() {
    // two-hand, il 1, poor: 0.98989 * 1.0 = 0.99; min floor(0.79) = 0 -> 1, max round(1.19) = 1
    ItemDamage d = ItemDamageCalculator.damage(forever(2, 8, 17, 0, 1, 1000, 0.4, null), T, new ImportReport())
        .orElseThrow();
    assertThat(List.of(d.min(), d.max())).containsExactly(1, 1);
  }

  private static ClientItem item(int classId, int subclassId, int inventoryType, int flags1) {
    return forever(classId, subclassId, inventoryType, 2, 61, 2000, 0.6, List.of(0, flags1, 0, 0, 0));
  }
}
