package dev.wowforever.app;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.app.db.InstanceRepository;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Actual addon inputs, canonical coverage and the limitations of reported loot. */
@Tag("shipped-data")
class ShippedForeverLootDataTest extends ShippedDataSupport {
  @Test void fourDungeonsRetainClientEncountersAndReceiveReportedLootWithoutRates() {
    var counts=Map.of(2959L,26,2998L,12,2999L,27,3065L,13);
    for(var entry:counts.entrySet()) {
      long instance=longs("SELECT id FROM instance WHERE client_map_id="+entry.getKey()).getFirst();
      var detail=repos.instances().detail(instance).orElseThrow();
      assertThat(detail.summary().lootState()).isEqualTo("reported");
      assertThat(scalar("SELECT count(*) FROM boss_loot bl JOIN boss b ON b.id=bl.boss_id WHERE b.instance_id="+instance)).isEqualTo(entry.getValue().longValue());
      for(var boss:detail.bosses())for(var row:repos.instances().bossLoot(boss.id(),"NORMAL")) {
        assertThat(row.dropPct()).isNull();assertThat(row.observedInGame()).isFalse();
        assertThat(row.displayOrigin()).endsWith(" (reported)");
      }
    }
    assertThat(scalar("SELECT count(*) FROM boss b JOIN instance i ON i.id=b.instance_id WHERE i.source='client' AND b.is_extra_list=0")).isEqualTo(24);
    assertThat(scalar("SELECT count(*) FROM boss b JOIN instance i ON i.id=b.instance_id WHERE i.source='client' AND b.npc_id IS NOT NULL")).isZero();
    assertThat(scalar("SELECT count(*) FROM atlasloot_forever_fact")).isEqualTo(143);
    assertThat(scalar("SELECT count(*) FROM(SELECT boss_id,item_id,difficulty FROM atlasloot_forever_fact GROUP BY boss_id,item_id,difficulty HAVING count(DISTINCT provider)=2)")).isEqualTo(65);
    assertThat(scalar("SELECT count(*) FROM atlasloot_forever_fact WHERE claimed_npc_id IS NOT NULL")).isPositive();
    assertThat(rows("SELECT b.name FROM boss b JOIN instance i ON i.id=b.instance_id WHERE i.source='client' AND b.is_extra_list=1"))
        .extracting(r -> r[0]).containsExactlyInAnyOrder("Magmatus","The Baron","Arcanic Enigma","Trash");
    assertThat(rows("SELECT b.name FROM boss b JOIN instance i ON i.id=b.instance_id WHERE i.source='client' AND b.is_extra_list=0 AND NOT EXISTS(SELECT 1 FROM boss_loot bl WHERE bl.boss_id=b.id)"))
        .extracting(r -> r[0]).containsExactlyInAnyOrder("Mana Elemental","Mana Wraith","Highland Horror","The Butcher","Infurnus");
  }
  @Test void eachSupportingItemHasItsExactArchivedDefinitionAndPinnedZip() {
    var catalog=repos.sourceArchive().catalog();
    var pins=Map.of("atlasloot-forever","ee8a6d68eb14c1ac38372485f03d9e44921e3b7feb1d5728e85db90fc97bc090",
        "atlasloot-continued-forever","8eaee376a19b26da4a50f4f7f7304f9f41b3eadfd0bd38c4fd98f67495fa82bd");
    Map<String,List<String>> lines=new HashMap<>();
    for(var source:catalog.snapshots())if(pins.containsKey(source.source())) {
      assertThat(source.files()).hasSize(4);
      assertThat(source.files()).filteredOn(f -> f.path().endsWith(".zip")).singleElement()
          .satisfies(f -> assertThat(f.sha256()).isEqualTo(pins.get(source.source())));
      assertThat(source.files()).anySatisfy(f -> assertThat(f.path()).endsWith("/LICENSE"));
      if(source.source().equals("atlasloot-continued-forever"))assertThat(source.files()).allSatisfy(f -> assertThat(f.url()).isNullOrEmpty());
      db.read(c -> {
        try(var p=c.prepareStatement("SELECT f.path,b.bytes FROM source_archive_file f JOIN source_archive_blob b ON b.sha256=f.sha256 WHERE f.snapshot_id=?")) {
          p.setString(1,source.id());try(var r=p.executeQuery()){while(r.next())if(r.getString(1).endsWith(".lua"))
            lines.put(source.id()+":"+r.getString(1),new String(r.getBytes(2),StandardCharsets.UTF_8).lines().toList());}
        }
        return null;
      });
    }
    assertThat(lines).hasSize(2);
    for(Object[] row:rows("SELECT snapshot_id,source_path,source_line,item_id,state FROM atlasloot_forever_fact")) {
      var source=lines.get(row[0]+":"+row[1]);int line=((Number)row[2]).intValue();
      assertThat(source).isNotNull();assertThat(line).isBetween(1,source.size());
      assertThat(source.get(line-1)).containsPattern("\\{\\s*\\d+\\s*,\\s*"+row[3]+"\\s*\\}");
      assertThat(row[4]).isEqualTo("reported");
    }
    assertThat(catalog.history()).filteredOn(r -> r.active()).singleElement().satisfies(r -> {
      assertThat(r.inputs()).extracting(i -> i.role()).contains("allthethings","configuration","legacy-app",
          "forever-loot-atlasloot-forever","forever-loot-atlasloot-continued-forever");
      assertThat(r.inputs()).anySatisfy(i -> assertThat(i.role()).startsWith("world-map-"));
    });
  }
}
