package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeItemMetadataRepositoryTest {
  @TempDir Path temp;
  @Test void nativeSlotSetsAndEffectsSurviveWithoutStatsOrAName()throws Exception {
    Path file=temp.resolve("hidden.sqlite");Files.copy(FixtureDb.path(),file);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("INSERT INTO item(id,has_client_data) VALUES(900001,0)");
      s.execute("INSERT OR REPLACE INTO meta VALUES('client_build','1.60.1.70450')");
      s.execute("CREATE TABLE client_table_dataset(snapshot_id TEXT,product TEXT,build TEXT,locale TEXT)");
      s.execute("INSERT INTO client_table_dataset VALUES('native','wow_classic_beta','1.60.1.70450','enUS')");
      s.execute("CREATE TABLE client_table_row(snapshot_id TEXT,table_name TEXT,row_id TEXT,payload TEXT,source_path TEXT,source_line INTEGER)");
      insert(c,"Item","900001","{\"ID\":\"900001\",\"InventoryType\":\"12\",\"IconFileDataID\":\"0\"}",3);
      insert(c,"ItemSet","42","{\"ID\":\"42\",\"Name_lang\":\"Native set\",\"ItemID_0\":\"900001\"}",4);
      insert(c,"ItemXItemEffect","1","{\"ID\":\"1\",\"ItemID\":\"900001\",\"ItemEffectID\":\"10\"}",5);
      insert(c,"ItemEffect","10","{\"ID\":\"10\",\"SpellID\":\"20\",\"TriggerType\":\"1\"}",6);
      insert(c,"SpellName","20","{\"ID\":\"20\",\"Name_lang\":\"Native effect\"}",7);
      insert(c,"ItemXItemEffect","2","{\"ID\":\"2\",\"ItemID\":\"900002\",\"ItemEffectID\":\"11\"}",8);
    }
    try(var db=Database.open(file)) {
      var metadata=new NativeItemMetadataRepository(db).item(900001);
      assertThat(metadata.nativeRow()).isTrue();assertThat(metadata.inventoryType()).isEqualTo(12);assertThat(metadata.iconId()).isZero();
      assertThat(metadata.sets()).singleElement().satisfies(set->{assertThat(set.id()).isEqualTo(42);assertThat(set.name()).isEqualTo("Native set");assertThat(set.evidence()).contains("ItemSet.csv:4");});
      assertThat(metadata.effects()).singleElement().satisfies(effect->{assertThat(effect.spellId()).isEqualTo(20);assertThat(effect.name()).isEqualTo("Native effect");assertThat(effect.triggerType()).isEqualTo(1);});
      assertThat(metadata.evidence()).contains("Item.csv:3","native");
      assertThat(new NativeItemMetadataRepository(db).item(-1).available()).isFalse();
    }
  }
  private static void insert(Connection c,String table,String id,String payload,int line)throws Exception {
    try(var p=c.prepareStatement("INSERT INTO client_table_row VALUES('native',?,?,?,?,?)")) {
      p.setString(1,table);p.setString(2,id);p.setString(3,payload);p.setString(4,table+".csv");p.setInt(5,line);p.executeUpdate();
    }
  }
}
