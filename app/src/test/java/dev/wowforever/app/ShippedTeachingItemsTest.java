package dev.wowforever.app;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.db.NativeTeachingItemRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("shipped-data")
class ShippedTeachingItemsTest extends ShippedDataSupport {
  @Test void exactNativeTeachingLinksRemainSeparateFromReportedAcquisition(){
    var repo=new NativeTeachingItemRepository(db);
    for(long[] pair:new long[][]{{2543,728},{2542,2697},{1252566,251526}}){var snapshot=repo.forSpell(pair[0]);assertThat(snapshot.build()).isEqualTo("1.60.1.70450");assertThat(snapshot.locale()).isEqualTo("enUS");assertThat(snapshot.items()).anySatisfy(item->{assertThat(item.itemId()).isEqualTo(pair[1]);assertThat(item.supports()).anySatisfy(support->{assertThat(support.relationship()).contains("Direct native");assertThat(support.evidence()).anyMatch(e->e.table().equals("ItemEffect")&&e.fields().get("TriggerType").equals("6"));});});}
    assertThat(scalar("SELECT count(*) FROM item_vendor WHERE item_id=728 AND npc_id=340")).isEqualTo(1);
    assertThat(scalar("SELECT count(*) FROM item_vendor WHERE item_id=251526")).isZero();
  }
}
