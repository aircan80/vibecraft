package dev.wowforever.app;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.db.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Named acceptance examples use the exact bundled projection, including knowledge-state changes from hotfixes. */
@Tag("shipped-data")
class ShippedV041DataTest extends ShippedDataSupport {
  @Test void everyNativeItemHasAPageAndEveryCurrentKnownStatRecordHasACard() {
    assertThat(scalar("SELECT count(*) FROM client_table_row r JOIN client_table_dataset d ON d.snapshot_id=r.snapshot_id LEFT JOIN item i ON i.id=CAST(r.row_id AS INTEGER) WHERE d.build='1.60.1.70450' AND r.table_name='Item' AND i.id IS NULL")).isZero();
    assertThat(scalar("SELECT count(*) FROM client_table_row r JOIN client_table_dataset d ON d.snapshot_id=r.snapshot_id JOIN item i ON i.id=CAST(r.row_id AS INTEGER) WHERE d.build='1.60.1.70450' AND r.table_name='ItemSparse' AND i.has_client_data=0")).isZero();
    assertThat(scalar("SELECT count(*) FROM item_tooltip_line WHERE text LIKE '%Stat #%'")).isZero();
  }
  @Test void hiddenMetadataAndHotfixRevelationRemainDifferentFromClassicValues() {
    assertThat(column("SELECT state||':'||source FROM field_knowledge WHERE entity='item' AND id='286978' AND field='stats'")).containsExactly("KNOWN:blizzard-hotfix");
    assertThat(column("SELECT state FROM field_knowledge WHERE entity='item' AND id='287012' AND field='stats'")).containsExactly("HIDDEN_BY_BLIZZARD");
    assertThat(scalar("SELECT inventory_type FROM item WHERE id=287012")).isEqualTo(10);
    assertThat(scalar("SELECT item_set_id FROM item WHERE id=287012")).isEqualTo(2135);
    assertThat(column("SELECT client_source FROM item WHERE id=16806")).containsExactly("era");
    assertThat(column("SELECT state FROM field_knowledge WHERE entity='item' AND id='16806' AND field='classic_stats'")).containsExactly("KNOWN");
    assertThat(scalar("SELECT count(*) FROM item i JOIN field_knowledge k ON k.entity='item' AND k.id=CAST(i.id AS TEXT) AND k.field='stats' WHERE i.client_source='hotfix' AND k.source<>'blizzard-hotfix'")).isZero();
  }
  @Test void nativeRacesCampingAndReportedDiscoveryRetainRealIdentities() {
    var races=new RaceRepository(db).load();
    for(int id:new int[]{95,96})assertThat(races.races()).filteredOn(r->r.id()==id).singleElement().satisfies(r->{assertThat(r.classes()).hasSize(5);assertThat(r.racials()).hasSize(4);});
    assertThat(new CampingRepository(db).load().camps()).hasSize(36);
    assertThat(new ForeverContentRepository(db).discoveries()).filteredOn(d->d.mapId()==2981).singleElement().satisfies(d->
        assertThat(d.encounters()).anySatisfy(e->{assertThat(e.name()).isEqualTo("The Wild King");assertThat(e.state()).isEqualTo("Reported");}));
    var set=new ItemContentRepository(db).load("set",2133);
    assertThat(set.entries()).filteredOn(e->e.target().equals("item")).extracting(ItemContentRepository.Entry::id)
        .containsExactlyInAnyOrder(286978L,286980L,271207L,271214L,286979L);
  }
  @Test void travelAtlasAndSourceHistoryAreBundledWithTheirProvenance() {
    assertThat(scalar("SELECT count(*) FROM instance_map")).isEqualTo(7);
    assertThat(scalar("SELECT count(*) FROM instance_map_legend")).isEqualTo(50);
    assertThat(scalar("SELECT count(*) FROM map_flight_node WHERE zone_id=40 AND npc_id=523")).isPositive();
    assertThat(scalar("SELECT count(*) FROM hotfix_item_fact")).isEqualTo(4864);
    assertThat(scalar("SELECT count(*) FROM questie_refresh_change WHERE status='conflict'")).isZero();
    assertThat(scalar("SELECT count(*) FROM source_archive_file")).isGreaterThan(500);
  }
}
