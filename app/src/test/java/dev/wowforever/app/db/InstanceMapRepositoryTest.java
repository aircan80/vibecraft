package dev.wowforever.app.db;
import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class InstanceMapRepositoryTest {
 @TempDir Path temp;
 @Test void oldDatabasesHaveNoMapsAndPreloadedMapsSurviveConnectionClosure()throws Exception {
  Path file=temp.resolve("maps.sqlite");Files.copy(FixtureDb.path(),file);
  try(var db=Database.open(file)){assertThat(new InstanceMapRepository(db).load(2)).isEmpty();}
  try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()){
   s.execute("CREATE TABLE instance_map(instance_id,floor,label,jpeg,forever,ordinal)");s.execute("CREATE TABLE instance_map_legend(instance_id,floor,number,name,npc_id,ordinal)");
   s.execute("INSERT INTO instance_map VALUES(2,'A','Map A',x'010203',1,0)");s.execute("INSERT INTO instance_map_legend VALUES(2,'A','1','Exact NPC',639,0)");
  }
  java.util.List<dev.wowforever.app.model.InstanceFloorMap> maps;
  try(var db=Database.open(file)){maps=new InstanceMapRepository(db).load(2);}
  assertThat(maps).hasSize(1);assertThat(maps.getFirst().legend().getFirst().npcId()).isEqualTo(639L);
  byte[] copy=maps.getFirst().jpeg();copy[0]=9;assertThat(maps.getFirst().jpeg()).containsExactly(1,2,3);
 }
}
