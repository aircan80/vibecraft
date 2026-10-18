package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import java.sql.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeTeachingItemRepositoryTest {
  @TempDir Path temp;
  @Test void exactNativeDirectAndLearnSpellPathsDeduplicateItemsWithoutGuessingUseOrConditions()throws Exception {
    Path file=fixture();
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file)) {
      row(c,"f","SpellEffect","1",Map.of("SpellID","100","Effect","24","EffectItemType","10"));
      row(c,"f","SpellEffect","2",Map.of("SpellID","200","Effect","36","EffectTriggerSpell","100"));
      row(c,"f","ItemEffect","11",Map.of("ID","11","SpellID","100","TriggerType","6","PlayerConditionID","0","ChrSpecializationID","0"));
      row(c,"f","ItemEffect","12",Map.of("ID","12","SpellID","200","TriggerType","0","PlayerConditionID","900","ChrSpecializationID","2"));
      row(c,"f","ItemEffect","13",Map.of("ID","13","SpellID","100","TriggerType","0"));
      row(c,"f","ItemEffect","14",Map.of("ID","14","SpellID","100","TriggerType","6","PlayerConditionID","malformed"));
      row(c,"e","ItemEffect","15",Map.of("ID","15","SpellID","100","TriggerType","6"));
      row(c,"f","ItemEffect","16",Map.of("ID","16","SpellID","100","TriggerType","6"));
      row(c,"e","SpellEffect","3",Map.of("SpellID","300","Effect","36","EffectTriggerSpell","100"));
      row(c,"f","ItemEffect","17",Map.of("ID","17","SpellID","300","TriggerType","0"));
      row(c,"f","ItemXItemEffect","21",Map.of("ItemID","728","ItemEffectID","11"));
      row(c,"f","ItemXItemEffect","22",Map.of("ItemID","728","ItemEffectID","12"));
      row(c,"f","ItemXItemEffect","23",Map.of("ItemID","99","ItemEffectID","13"));
      row(c,"f","ItemXItemEffect","24",Map.of("ItemID","729","ItemEffectID","14"));
      row(c,"f","ItemXItemEffect","25",Map.of("ItemID","999","ItemEffectID","15"));
      row(c,"f","ItemXItemEffect","26",Map.of("ItemID","730","ItemEffectID","11"));
      row(c,"f","ItemXItemEffect","27",Map.of("ItemID","731","ItemEffectID","16"));
      row(c,"f","ItemXItemEffect","28",Map.of("ItemID","998","ItemEffectID","17"));
    }
    try(var db=Database.open(file)) {
      var repo=new NativeTeachingItemRepository(db);var snapshot=repo.forSpell(100);
      assertThat(snapshot.items()).extracting(NativeTeachingItemRepository.TeachingItem::itemId).containsExactly(728L,729L,730L,731L);
      assertThat(snapshot.items().getFirst().supports()).hasSize(2).anyMatch(s->s.relationship().contains("Direct")).anyMatch(s->s.relationship().contains("explicit Learn Spell"));
      assertThat(snapshot.items().getFirst().name()).isEqualTo("Native recipe");
      assertThat(snapshot.items().get(1).supports().getFirst().condition()).contains("missing or malformed","eligibility unknown");
      assertThat(snapshot.items().get(2).name()).isEqualTo("Item #730");
      assertThat(snapshot.items().get(3).supports().getFirst().condition()).contains("missing or malformed","eligibility unknown");
      assertThat(snapshot.items().getFirst().supports().get(1).condition()).contains("PlayerCondition #900","Specialization #2","not been evaluated");
      assertThat(snapshot.items().getFirst().supports().getFirst().condition()).contains("does not prove unrestricted");
      assertThat(snapshot.items().getFirst().supports().getFirst().evidence()).allMatch(e->e.path().startsWith("native/")&&e.line()==2);
      assertThat(repo.forSpell(100)).isSameAs(snapshot);
    }
  }
  @Test void missingLinksAreCoverageNotTrainerProofAndNonCreateSpellIsRejected()throws Exception {
    Path file=fixture();try(var c=DriverManager.getConnection("jdbc:sqlite:"+file)){row(c,"f","SpellEffect","1",Map.of("SpellID","100","Effect","24","EffectItemType","10"));}
    try(var db=Database.open(file)){var repo=new NativeTeachingItemRepository(db);assertThat(repo.forSpell(100).items()).isEmpty();assertThat(repo.forSpell(100).coverage()).contains("does not establish a trainer","automatic learning");assertThat(repo.forSpell(200).coverage()).contains("No native create-item");}
  }
  private Path fixture()throws Exception {
    Path file=temp.resolve(java.util.UUID.randomUUID()+".sqlite");try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()){
      s.execute("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)");s.execute("INSERT INTO meta VALUES('forever_diff_forever_snapshot_id','f')");s.execute("CREATE TABLE client_table_dataset(snapshot_id TEXT,product TEXT,build TEXT,locale TEXT)");s.execute("INSERT INTO client_table_dataset VALUES('f','wow_classic_beta','1.60.test','enUS'),('e','wow_classic_era','1.15.test','enUS')");s.execute("CREATE TABLE client_table_row(snapshot_id TEXT,table_name TEXT,row_id TEXT,payload TEXT,source_path TEXT,source_line INTEGER,PRIMARY KEY(snapshot_id,table_name,row_id))");s.execute("CREATE TABLE item(id INTEGER PRIMARY KEY,name TEXT,client_source TEXT)");s.execute("INSERT INTO item VALUES(728,'Native recipe','forever'),(729,'Second recipe','forever'),(730,'Era name must not leak','era')");}
    return file;
  }
  private static void row(Connection c,String snapshot,String table,String id,Map<String,String> cells)throws Exception{try(var p=c.prepareStatement("INSERT INTO client_table_row VALUES(?,?,?,?,?,2)")){p.setString(1,snapshot);p.setString(2,table);p.setString(3,id);p.setString(4,new com.google.gson.Gson().toJson(cells));p.setString(5,"native/"+table+".csv");p.executeUpdate();}}
}
