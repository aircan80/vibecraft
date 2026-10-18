package dev.wowforever.app.model;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;

class LootFilterTest {
  private LootRow row(long id, Integer mask, Integer equipmentSlot) {
    return new LootRow(new EntityRef(EntityKind.ITEM,id),"Item "+id,null,null,"reported",6,
        4,"Armor",1,false,0,null,null,null,mask,0).withInventoryType(equipmentSlot);
  }
  @Test void equipmentSlotNeverUsesAddonOrderingAndUnknownsAreAccountedOnce() {
    var known = row(1,256,6);
    var addonOrderOnly = row(2,256,null);
    var unknownBoth = row(3,null,null);
    var wrongClass = row(4,128,null);
    var wrongSlot = row(5,null,7);
    var result = new LootFilter(9,6).apply(List.of(known,addonOrderOnly,unknownBoth,wrongClass,wrongSlot));
    assertThat(result.rows()).containsExactly(known);
    assertThat(result.excluded()).isEqualTo(2);
    assertThat(result.unknown()).isEqualTo(2);
    assertThat(result.unknownClass()).isEqualTo(1);
    assertThat(result.unknownSlot()).isEqualTo(2);
  }
  @Test void allFiltersRetainUnknownsAndZeroIsNotMissing() {
    var unknown=row(1,null,null); var unrestricted=row(2,-1,6); var zero=row(3,0,0);
    assertThat(new LootFilter(null,null).apply(List.of(unknown,unrestricted,zero)).rows())
        .containsExactly(unknown,unrestricted,zero);
    assertThat(new LootFilter(9,6).apply(List.of(unrestricted,zero)).rows()).containsExactly(unrestricted);
    assertThat(new LootFilter(9,6).apply(List.of(zero)).excluded()).isEqualTo(1);
  }
  @Test void observationPreservesEquipmentSlot() {
    assertThat(row(1,256,6).withObservation(true).inventoryType()).isEqualTo(6);
  }
  @Test void semanticChestAndRangedChoicesIncludeEquivalentClientTypes() {
    assertThat(new LootFilter(null,5).apply(List.of(row(1,256,5),row(2,256,20),row(3,256,6))).rows())
        .extracting(r->r.item().id()).containsExactly(1L,2L);
    assertThat(new LootFilter(null,15).apply(List.of(row(1,256,15),row(2,256,26),row(3,256,25))).rows())
        .extracting(r->r.item().id()).containsExactly(1L,2L);
  }

}
