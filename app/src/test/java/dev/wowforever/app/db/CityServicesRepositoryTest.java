package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CityServicesRepositoryTest {
  @TempDir Path temp;
  @Test void deduplicatesCityPlacementsAndKeepsDistrictsAndEvidenceSeparate() throws Exception {
    Path file=temp.resolve("city.sqlite");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()){
      s.execute("CREATE TABLE meta(key TEXT,value TEXT)");
      s.execute("CREATE TABLE npc(id INTEGER PRIMARY KEY,name TEXT)");
      s.execute("CREATE TABLE npc_spawn(npc_id INTEGER,zone_id INTEGER,x REAL,y REAL)");
      s.execute("CREATE INDEX idx_npc_spawn_zone ON npc_spawn(zone_id)");
      s.execute("CREATE TABLE item(id INTEGER PRIMARY KEY,name TEXT)");
      s.execute("CREATE TABLE item_vendor(item_id INTEGER,npc_id INTEGER,PRIMARY KEY(item_id,npc_id))");
      s.execute("CREATE INDEX idx_item_vendor_npc ON item_vendor(npc_id)");
      s.execute("CREATE TABLE quest(id INTEGER PRIMARY KEY,name TEXT)");
      s.execute("CREATE TABLE quest_giver(quest_id INTEGER,kind TEXT,entity_id INTEGER,role TEXT)");
      s.execute("CREATE INDEX idx_quest_giver_entity ON quest_giver(kind,entity_id)");
      s.execute("INSERT INTO npc VALUES(1,'A recorded person'),(2,'District vendor'),(3,'Bank trainer by name only'),(4,'Unplaced vendor')");
      s.execute("INSERT INTO npc_spawn VALUES(1,1519,20,30),(1,1519,20,30),(1,1519,21,31),(2,1617,10,10),(3,1519,12,13)");
      s.execute("INSERT INTO item VALUES(7,NULL),(8,'District stock')");
      s.execute("INSERT INTO item_vendor VALUES(7,1),(8,2),(8,4)");
      s.execute("INSERT INTO quest VALUES(9,'Real quest')");
      s.execute("INSERT INTO quest_giver VALUES(9,'npc',1,'start'),(9,'npc',1,'start'),(9,'npc',1,'end'),(9,'object',3,'start')");
    }
    try(var db=Database.open(file)){
      var snapshot=new CityServicesRepository(db).load(1519);
      assertThat(snapshot.people()).hasSize(1);assertThat(snapshot.more()).isFalse();
      var person=snapshot.people().getFirst();
      assertThat(person.id()).isEqualTo(1);assertThat(person.locations()).containsExactly("20.0, 30.0","21.0, 31.0");
      assertThat(person.stock()).containsExactly(new CityServicesRepository.Link(7,null,"stock"));
      assertThat(person.quests()).containsExactly(new CityServicesRepository.Link(9,"Real quest","end"),new CityServicesRepository.Link(9,"Real quest","start"));
      assertThat(person.questCount()).isEqualTo(2);
      assertThat(new CityServicesRepository(db).load(999).people()).isEmpty();
      assertThat(new CityServicesRepository(db).load(1617).people()).extracting(CityServicesRepository.Person::id).containsExactly(2L);
    }
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()){
      s.execute("WITH RECURSIVE ids(id) AS (VALUES(100) UNION ALL SELECT id+1 FROM ids WHERE id<180) INSERT INTO npc SELECT id,'Person '||id FROM ids");
      s.execute("INSERT INTO npc_spawn SELECT id,1519,10,20 FROM npc WHERE id>=100");
      s.execute("INSERT INTO item_vendor SELECT 7,id FROM npc WHERE id>=100");
      s.execute("WITH RECURSIVE ids(id) AS (VALUES(100) UNION ALL SELECT id+1 FROM ids WHERE id<115) INSERT INTO item SELECT id,'Stock '||id FROM ids");
      s.execute("INSERT INTO item_vendor SELECT id,1 FROM item WHERE id>=100");
    }
    try(var db=Database.open(file)){
      var snapshot=new CityServicesRepository(db).load(1519);
      assertThat(snapshot.people()).hasSize(CityServicesRepository.PEOPLE_LIMIT);assertThat(snapshot.more()).isTrue();
      var person=snapshot.people().getFirst();assertThat(person.stockCount()).isEqualTo(17);
      assertThat(person.stock()).hasSize(CityServicesRepository.LINKS_LIMIT);
    }
  }
}
