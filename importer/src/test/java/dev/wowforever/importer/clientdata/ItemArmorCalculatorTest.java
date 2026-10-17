package dev.wowforever.importer.clientdata;

import static dev.wowforever.importer.clientdata.ClientItems.forever;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.report.ImportReport;
import org.junit.jupiter.api.Test;

class ItemArmorCalculatorTest {
  private static final ScalingTables T = ClientItems.fixtureTables();

  private static int armor(long id) {
    return ItemArmorCalculator.armor(ClientItems.one(id), T, new ImportReport()).orElseThrow().armor();
  }

  @Test
  void plateClothLeatherAndMail() {
    assertThat(armor(12640)).as("Lionheart Helm: 3619.31 * 1.2 * 0.13").isEqualTo(565);
    assertThat(armor(19682)).as("Bloodvine Vest: 521 * 1.1 * 0.16").isEqualTo(92);
    assertThat(armor(19683)).as("Bloodvine Leggings: 521 * 1.1 * 0.14").isEqualTo(80);
    assertThat(armor(19684)).as("Bloodvine Boots: 521 * 1.1 * 0.11").isEqualTo(63);
    assertThat(armor(1306)).as("Wolfmane Wristguards, leather").isEqualTo(34);
    assertThat(armor(1282)).as("Sparkmetal Coif, mail").isEqualTo(168);
    assertThat(armor(2902)).as("Cloak of the Faith, cloth back").isEqualTo(21);
  }

  @Test
  void bonusArmorIsNotPartOfTheBaseValue() {
    assertThat(armor(7919)).as("Heavy Mithril Gauntlet: base 188, the +80 is stat 50").isEqualTo(188);
    assertThat(ItemArmorCalculator.armor(ClientItems.one(15855), T, new ImportReport()))
        .as("Ring of Protection has only bonus armour").isEmpty();
  }

  @Test
  void shieldsUseTheShieldTableAndTheirBlockIsUnverified() {
    ImportReport report = new ImportReport();
    ItemArmor a = ItemArmorCalculator.armor(ClientItems.one(19321), T, report).orElseThrow();
    assertThat(a.armor()).isEqualTo(2291);
    assertThat(a.block()).as("the client tables hold no shield block value").isNull();
    assertThat(armor(2916)).as("Gold Lion Shield").isEqualTo(645);
    assertThat(report.get("clientdata.unverified.block")).isEqualTo(1);
    assertThat(report.get("clientdata.unverified.armor")).isZero();
  }

  @Test
  void aRobeUsesTheChestLocation() {
    assertThat(ItemArmorCalculator.armor(forever(4, 1, 20, 3, 65, null, 0.0, null), T, new ImportReport())
        .orElseThrow().armor()).isEqualTo(92);
  }

  @Test
  void eraRowsCarryAbsoluteArmor() {
    ItemArmor a = ItemArmorCalculator.armor(ClientItems.one(16809), T, new ImportReport()).orElseThrow();
    assertThat(a.armor()).as("Felheart Robes, Era Resistances_0").isEqualTo(102);
    assertThat(a.block()).isNull();
  }

  @Test
  void itemsWithoutArmorHaveNone() {
    ImportReport report = new ImportReport();
    assertThat(ItemArmorCalculator.armor(ClientItems.one(19019), T, report)).as("a weapon").isEmpty();
    assertThat(ItemArmorCalculator.armor(ClientItems.one(4549), T, report)).as("a ring").isEmpty();
    assertThat(ItemArmorCalculator.armor(forever(4, 1, 4, 2, 61, null, 0.0, null), T, report))
        .as("a shirt: location modifier 0").isEmpty();
    assertThat(report.get("clientdata.unverified.armor")).isZero();
  }

  @Test
  void aMissingTableRowIsUnverified() {
    ImportReport report = new ImportReport();
    assertThat(ItemArmorCalculator.armor(forever(4, 4, 1, 4, 3, null, 0.0, null), T, report)).isEmpty();
    assertThat(ItemArmorCalculator.armor(forever(4, 6, 14, 4, 3, null, 0.0, null), T, report)).isEmpty();
    assertThat(report.get("clientdata.unverified.armor")).isEqualTo(2);
  }
}
