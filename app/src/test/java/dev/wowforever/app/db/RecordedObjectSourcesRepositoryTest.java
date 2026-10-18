package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RecordedObjectSourcesRepositoryTest {
  @TempDir Path temp;
  public static void fixture(Path file) throws Exception {
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()){
      s.execute("CREATE TABLE meta(key TEXT,value TEXT)");s.execute("INSERT INTO meta VALUES('questiedb_commit','base'),('questiedb_refresh_commit','correction')");
      s.execute("CREATE TABLE game_object(id INTEGER PRIMARY KEY,name TEXT)");s.execute("CREATE TABLE zone(id INTEGER PRIMARY KEY,name TEXT)");
      s.execute("CREATE TABLE item_object_drop(item_id INTEGER,object_id INTEGER,PRIMARY KEY(item_id,object_id))");
      s.execute("CREATE TABLE object_spawn(object_id INTEGER,zone_id INTEGER,x REAL,y REAL)");s.execute("CREATE INDEX idx_object_spawn_object ON object_spawn(object_id)");s.execute("CREATE INDEX idx_object_spawn_zone ON object_spawn(zone_id)");
      s.execute("INSERT INTO zone VALUES(12,'Elwynn Forest')");s.execute("INSERT INTO game_object VALUES(1731,'Copper Vein'),(106318,'Battered Chest'),(999,'Unrelated')");
      s.execute("INSERT INTO item_object_drop VALUES(2770,1731),(2770,106318),(5,999)");
      s.execute("INSERT INTO object_spawn VALUES(1731,12,10,20),(1731,12,10,20),(1731,12,NULL,NULL),(1731,12,101,0),(1731,99,5,6),(106318,12,30,40),(999,12,50,60)");
    }
  }
  @Test void preservesObjectIdentityRawRowsMissingCoordinatesAndExactZoneScope() throws Exception {
    Path file=temp.resolve("sources.sqlite");fixture(file);
    try(var db=Database.open(file)){
      var repo=new RecordedObjectSourcesRepository(db);assertThat(repo.zones(2770,0).rows()).extracting(RecordedObjectSourcesRepository.Zone::id).containsExactly(12L,99L);
      var sources=repo.sources(2770,12,0);assertThat(sources.rows()).extracting(RecordedObjectSourcesRepository.Source::id).containsExactly(1731L,106318L);assertThat(sources.rows().getFirst().rawRows()).isEqualTo(4);
      var points=repo.points(2770,12,1731);assertThat(points.rawRows()).isEqualTo(4);assertThat(points.validRows()).isEqualTo(2);assertThat(points.rows()).containsExactly(new RecordedObjectSourcesRepository.Point(10,20));
      assertThat(repo.points(2770,12,106318).rows()).containsExactly(new RecordedObjectSourcesRepository.Point(30,40));assertThat(repo.points(2770,12,999).rawRows()).isZero();assertThat(repo.zones(0,0).rows()).isEmpty();
      assertThat(repo.provenance()).contains("base","correction","not per-point verification");
    }
  }
  @Test void capsPagesAndDistinctPointsWithSentinels() throws Exception {
    Path file=temp.resolve("caps.sqlite");fixture(file);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()){
      s.execute("WITH RECURSIVE ids(i) AS (VALUES(1) UNION ALL SELECT i+1 FROM ids WHERE i<260) INSERT INTO object_spawn SELECT 1731,12,i/10.0,1 FROM ids");
      s.execute("WITH RECURSIVE ids(i) AS (VALUES(200) UNION ALL SELECT i+1 FROM ids WHERE i<310) INSERT INTO item_object_drop SELECT 2770,i FROM ids");
      s.execute("INSERT INTO object_spawn SELECT object_id,12,1,1 FROM item_object_drop WHERE object_id BETWEEN 200 AND 310");
      s.execute("WITH RECURSIVE ids(i) AS (VALUES(200) UNION ALL SELECT i+1 FROM ids WHERE i<310) INSERT INTO object_spawn SELECT 1731,i,1,1 FROM ids");
      s.execute("CREATE TABLE collector_fact(category TEXT,entity_type TEXT,item_id INTEGER,entity_id INTEGER)");
      s.execute("INSERT INTO collector_fact VALUES('object-drop','object',2770,1731)");
    }
    try(var db=Database.open(file)){
      var repo=new RecordedObjectSourcesRepository(db);assertThat(repo.sources(2770,12,0).rows()).hasSize(100);assertThat(repo.sources(2770,12,0).more()).isTrue();assertThat(repo.sources(2770,12,100).rows()).hasSize(13);
      assertThat(repo.zones(2770,0).rows()).hasSize(100);assertThat(repo.zones(2770,0).more()).isTrue();
      assertThat(repo.zones(2770,100).rows()).hasSize(13);
      assertThat(repo.sources(2770,12,100).rows()).filteredOn(source->source.id()==1731).extracting(RecordedObjectSourcesRepository.Source::observed).containsExactly(true);
      var points=repo.points(2770,12,1731);assertThat(points.rows()).hasSize(250);assertThat(points.more()).isTrue();assertThat(points.rawRows()).isEqualTo(264);
    }
  }
}
