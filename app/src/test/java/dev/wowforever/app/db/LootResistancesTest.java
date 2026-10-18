package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.LootRow;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LootResistancesTest {
  @TempDir Path temp;
  private LootRow row(long id) { return new LootRow(new EntityRef(EntityKind.ITEM,id),null,null,null,null,null); }
  @Test void onlyExplicitForeverNumbersAreKnownAndEraAndAbsentSchoolsStayUnknown() throws Exception {
    Path file=temp.resolve("resistance.sqlite");Files.copy(FixtureDb.path(),file);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("UPDATE item SET client_source='hotfix',has_client_data=1 WHERE id=14149");
      s.execute("UPDATE item SET client_source='era',has_client_data=1 WHERE id=14145");
      s.execute("DELETE FROM item_stat WHERE item_id IN (14149,14145)");
      s.execute("INSERT OR REPLACE INTO boss_loot(boss_id,item_id,difficulty) SELECT id,14149,'NORMAL' FROM boss ORDER BY id LIMIT 1");
      s.execute("INSERT INTO item_stat(item_id,ordinal,stat_id,value) VALUES(14149,1,51,0),(14149,2,52,12),(14145,1,51,99)");
    }
    try(var db=Database.open(file)) {
      var rows=db.read(c->LootResistances.attach(c,List.of(row(14149),row(14145),row(999999))));
      assertThat(rows.get(0).resistanceValues()).containsExactlyInAnyOrderEntriesOf(Map.of(51,0,52,12));
      assertThat(rows.get(0).resistanceValues()).doesNotContainKey(54);
      assertThat(rows.get(1).resistanceValues()).isEmpty();
      assertThat(rows.get(2).resistanceValues()).isEmpty();
      long bossId=db.read(c->Sql.one(c,"SELECT id FROM boss ORDER BY id LIMIT 1",r->r.getLong(1)).orElseThrow());
      assertThat(new InstanceRepository(db).bossLoot(bossId,"NORMAL"))
          .filteredOn(r->r.item().id()==14149).singleElement()
          .satisfies(r->assertThat(r.resistanceValues()).containsEntry(51,0).containsEntry(52,12));
    }
  }
  @Test void boundedIdChunksPreserveDuplicatesOrderingAndUnrelatedRows() throws Exception {
    try(var db=Database.open(FixtureDb.path())) {
      var input=new ArrayList<LootRow>();
      for(int n=0;n<905;n++) input.add(row(900000+n));
      input.add(row(900001));
      var rows=db.read(c->LootResistances.attach(c,input));
      assertThat(rows).extracting(r->r.item().id()).containsExactlyElementsOf(input.stream().map(r->r.item().id()).toList());
      assertThat(rows).allSatisfy(r->assertThat(r.resistanceValues()).isEmpty());
    }
  }
}
