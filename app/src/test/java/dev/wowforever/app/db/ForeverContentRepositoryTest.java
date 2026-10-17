package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForeverContentRepositoryTest {
  @TempDir Path temp;
  @Test void achievementOnlyInstanceRemainsReportedAndNeverProducesLoot() throws Exception {
    Path file=temp.resolve("discovery.sqlite");Files.copy(FixtureDb.path(),file);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("CREATE TABLE client_table_dataset(snapshot_id TEXT,product TEXT,build TEXT,locale TEXT)");
      s.execute("CREATE TABLE client_table_row(snapshot_id TEXT,table_name TEXT,row_id TEXT,payload TEXT,source_path TEXT,source_line INTEGER)");
      s.execute("INSERT INTO client_table_dataset VALUES('f','wow_classic_beta','1.60.1.70450','enUS'),('e','wow_classic_era','1.15.9.70003','enUS')");
      insert(c,"Achievement","1","{\"Title_lang\":\"The Wild King kills (Hyjal Summit)\",\"Instance_ID\":\"2981\",\"Category\":\"14821\"}");
      insert(c,"Faction","2798","{\"Name_lang\":\"Darkspear Raiders\",\"Description_lang\":\"Client description\"}");
      insert(c,"Map","2997","{\"MapName_lang\":\"Darkspear Islands\",\"InstanceType\":\"3\"}");
      s.execute("INSERT INTO client_table_row VALUES('e','Map','2981','{\"MapName_lang\":\"Wrong Era value\",\"InstanceType\":\"2\"}','Era.csv',2)");
    }
    try(var db=Database.open(file)) {
      var repo=new ForeverContentRepository(db);
      assertThat(repo.discoveries()).singleElement().satisfies(d->{
        assertThat(d.mapId()).isEqualTo(2981);assertThat(d.name()).isEqualTo("Hyjal Summit");
        assertThat(d.encounters()).singleElement().satisfies(b->{
          assertThat(b.name()).isEqualTo("The Wild King");assertThat(b.state()).isEqualTo("Reported");
          assertThat(b.source()).contains("1.60.1.70450","Achievement.csv:2");
        });
      });
      assertThat(repo.pvp().battlegrounds()).singleElement().satisfies(b->assertThat(b.name()).isEqualTo("Darkspear Islands"));
      assertThat(repo.pvp().factions()).singleElement().satisfies(f->assertThat(f.id()).isEqualTo(2798));
      assertThat(repo.context().changes()).isEmpty();
    }
  }
  @Test void namedDifferencesUsePinnedRowsAndCacheAcrossRepositoryInstances() throws Exception {
    Path file=temp.resolve("named.sqlite");Files.copy(FixtureDb.path(),file);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var statement=c.createStatement()) {
      statement.execute("CREATE TABLE client_table_dataset(snapshot_id TEXT,product TEXT,build TEXT,locale TEXT)");
      statement.execute("CREATE TABLE client_table_row(snapshot_id TEXT,table_name TEXT,row_id TEXT,payload TEXT,source_path TEXT,source_line INTEGER)");
      statement.execute("CREATE TABLE forever_diff(kind TEXT,id TEXT,status TEXT,fields TEXT,era_snapshot_id TEXT,forever_snapshot_id TEXT)");
      statement.execute("INSERT INTO client_table_dataset VALUES('f','wow_classic_beta','1.60.1.70450','enUS'),('e','wow_classic_era','1.15.9.70003','enUS')");
      statement.execute("INSERT OR REPLACE INTO meta VALUES('forever_diff_era_snapshot_id','e'),('forever_diff_forever_snapshot_id','f')");
      statement.execute("INSERT INTO forever_diff VALUES('AreaTable','40','changed','[\"AreaName_lang\"]','e','f'),('ItemSet','2133','new','[]','e','f'),('Item','2874','changed','[]','e','f')");
      insert(c,"AreaTable","40","{\"AreaName_lang\":\"Westfall (client)\"}");
      insert(c,"ItemSet","2133","{\"Name_lang\":\"Rotmender’s Raiment\"}");
      insert(c,"Item","2874","{\"ID\":\"2874\"}");
    }
    var db=Database.open(file);
    var context=new ForeverContentRepository(db).context();
    assertThat(context.changes()).filteredOn(c->c.kind().equals("ItemSet")).singleElement()
        .satisfies(c->assertThat(c.displayName()).isEqualTo("Rotmender’s Raiment"));
    assertThat(context.changes()).filteredOn(c->c.kind().equals("AreaTable")).singleElement().satisfies(c->{
      assertThat(c.displayName()).isEqualTo("Westfall (client)");
      assertThat(c.ref()).isEqualTo(new dev.wowforever.app.model.EntityRef(dev.wowforever.app.model.EntityKind.ZONE,40));
    });
    db.close();
    assertThat(new ForeverContentRepository(db).context()).isSameAs(context);
  }

  private static void insert(Connection c,String table,String id,String json)throws SQLException {
    try(var p=c.prepareStatement("INSERT INTO client_table_row VALUES('f',?,?,?, ?,2)")){
      p.setString(1,table);p.setString(2,id);p.setString(3,json);p.setString(4,table+".csv");p.executeUpdate();
    }
  }
}
