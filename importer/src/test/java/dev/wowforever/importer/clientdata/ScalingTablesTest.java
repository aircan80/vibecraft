package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import dev.wowforever.importer.clientdata.ScalingTables.DamageKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScalingTablesTest {
  private static final ScalingTables T = ClientItems.fixtureTables();

  @TempDir Path tmp;

  @Test
  void slotGroupsFollowTheClassicBudgetTable() {
    // 0: head, shirt, chest, legs, two-hand, robe
    for (int inv : new int[] {1, 4, 5, 7, 17, 20}) {
      assertThat(ScalingTables.slotGroup(inv)).as("inventory type %d", inv).hasValue(0);
    }
    // 1: shoulders, waist, feet, hands, trinket
    for (int inv : new int[] {3, 6, 8, 10, 12}) {
      assertThat(ScalingTables.slotGroup(inv)).as("inventory type %d", inv).hasValue(1);
    }
    // 2: neck, wrists, finger, shield, cloak, held in off-hand
    for (int inv : new int[] {2, 9, 11, 14, 16, 23}) {
      assertThat(ScalingTables.slotGroup(inv)).as("inventory type %d", inv).hasValue(2);
    }
    // 3: one-hand, main hand, off hand
    for (int inv : new int[] {13, 21, 22}) {
      assertThat(ScalingTables.slotGroup(inv)).as("inventory type %d", inv).hasValue(3);
    }
    // 4: ranged (bow), thrown, ranged right (gun, crossbow, wand), relic
    for (int inv : new int[] {15, 25, 26, 28}) {
      assertThat(ScalingTables.slotGroup(inv)).as("inventory type %d", inv).hasValue(4);
    }
    for (int inv : new int[] {0, 18, 19, 24, 27}) {
      assertThat(ScalingTables.slotGroup(inv)).as("inventory type %d", inv).isEmpty();
    }
  }

  @Test
  void budgetColumnByQuality() {
    // RandPropPoints 61: Epic_0 45, Superior_0 35, Good_0 27
    assertThat(T.randPropBudget(61, 4, 1)).hasValue(45);
    assertThat(T.randPropBudget(61, 5, 1)).as("legendary uses Epic").hasValue(45);
    assertThat(T.randPropBudget(61, 3, 1)).hasValue(35);
    assertThat(T.randPropBudget(61, 2, 1)).hasValue(27);
    assertThat(T.randPropBudget(61, 1, 1)).as("common uses Good").hasValue(27);
    assertThat(T.randPropBudget(61, 0, 1)).as("poor uses Good").hasValue(27);
    // RandPropPoints 80: Epic_3 23
    assertThat(T.randPropBudget(80, 5, 13)).hasValue(23);
  }

  @Test
  void budgetIsEmptyWithoutARowOrASlotGroup() {
    assertThat(T.randPropBudget(3, 4, 1)).as("no RandPropPoints row 3 in the fixture").isEmpty();
    assertThat(T.randPropBudget(61, 4, 18)).as("bags have no budget").isEmpty();
  }

  @Test
  void dpsByKindLevelAndQuality() {
    assertThat(T.dps(DamageKind.ONE_HAND, 80, 5)).hasValue(61.84795379639);
    assertThat(T.dps(DamageKind.TWO_HAND, 76, 5)).hasValue(74.74600982666);
    assertThat(T.dps(DamageKind.RANGED, 78, 4)).hasValue(46.4979095459);
    assertThat(T.dps(DamageKind.WAND, 2, 0)).hasValue(1.32774853706);
    assertThat(T.dps(DamageKind.ONE_HAND, 3, 1)).isEmpty();
    assertThat(T.dps(DamageKind.ONE_HAND, 80, 7)).as("no Quality_7 column").isEmpty();
  }

  @Test
  void armorIsTotalTimesQualityTimesLocation() {
    // 3619.31 plate * 1.2 (epic) * 0.13 (head) = 564.61
    assertThat(T.armor(61, 4, 1, 4)).hasValue(565);
    // 521 cloth * 1.1 (rare) * 0.16 (chest) = 91.70
    assertThat(T.armor(65, 3, 5, 1)).hasValue(92);
    assertThat(T.armor(65, 3, 20, 1)).as("a robe uses the chest modifier").hasValue(92);
    assertThat(T.armor(65, 3, 4, 1)).as("a shirt has modifier 0").hasValue(0);
    assertThat(T.armor(3, 3, 5, 1)).as("no row for item level 3").isEmpty();
    assertThat(T.armor(65, 3, 5, 6)).as("not a cloth/leather/mail/plate material").isEmpty();
  }

  @Test
  void shieldArmorByLevelAndQuality() {
    assertThat(T.shieldArmor(60, 4)).hasValue(2291);
    assertThat(T.shieldArmor(3, 4)).isEmpty();
  }

  @Test
  void aMissingTableFailsNamingIt() throws IOException {
    Files.createDirectories(tmp.resolve("csv"));
    Files.writeString(tmp.resolve("csv/RandPropPoints.csv"), "ID,Epic_0\n1,1\n");
    assertThatThrownBy(() -> ScalingTables.load(new ClientDataSource(tmp)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("RandPropPoints");
  }

  @Test
  void dpsOffsetsAreExact() {
    assertThat(T.dps(DamageKind.AMMO, 54, 2).getAsDouble()).isCloseTo(16.9, offset(0.2));
  }
}
