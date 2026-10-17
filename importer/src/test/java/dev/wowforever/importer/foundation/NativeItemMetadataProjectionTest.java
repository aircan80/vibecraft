package dev.wowforever.importer.foundation;

import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NativeItemMetadataProjectionTest {
  static void row(Connection c,String table,String id,String payload,int line)throws Exception {
    try(var p=c.prepareStatement("INSERT INTO client_table_row VALUES ('f',?,?,?,?,?)")){p.setString(1,table);p.setString(2,id);p.setString(3,payload);p.setString(4,"tables/"+table+".csv");p.setInt(5,line);p.executeUpdate();}
  }
  @Test void seedsHiddenMetadataWithoutChangingExistingItemsAndRetainsAmbiguousSetsAndEffects()throws Exception {
    try(var c=DriverManager.getConnection("jdbc:sqlite::memory:");var s=c.createStatement()) {
      c.setAutoCommit(false);ClientDataset.initialize(c);
      s.execute("INSERT INTO client_table_dataset VALUES ('f','wow_classic_beta','1.60.1.70450','enUS')");
      s.execute("CREATE TABLE item(id INTEGER PRIMARY KEY,name TEXT,class_id INTEGER,subclass_id INTEGER,inventory_type INTEGER,icon_id INTEGER,item_set_id INTEGER,has_client_data INTEGER NOT NULL DEFAULT 0)");
      s.execute("INSERT INTO item VALUES (16806,'Existing authored name',9,8,7,6,5,1)");
      row(c,"Item","286978","{\"ID\":\"286978\",\"ClassID\":\"4\",\"SubclassID\":\"1\",\"InventoryType\":\"5\",\"IconFileDataID\":\"0\"}",2);
      row(c,"Item","16806","{\"ID\":\"16806\",\"ClassID\":\"4\",\"InventoryType\":\"6\"}",3);
      row(c,"Item","999","{\"ID\":\"999\",\"ClassID\":\"4\",\"InventoryType\":\"12\"}",4);
      row(c,"ItemSet","2133","{\"ID\":\"2133\",\"ItemID_0\":\"286978\",\"ItemID_1\":\"999\"}",2);
      row(c,"ItemSet","2135","{\"ID\":\"2135\",\"ItemID_0\":\"999\"}",3);
      row(c,"ItemModifiedAppearance","1","{\"ID\":\"1\",\"ItemID\":\"286978\",\"ItemAppearanceID\":\"22\"}",2);
      row(c,"ItemXItemEffect","1","{\"ID\":\"1\",\"ItemID\":\"999\",\"ItemEffectID\":\"7\"}",2);
      row(c,"ItemEffect","7","{\"ID\":\"7\",\"SpellID\":\"1292252\"}",2);
      assertThat(NativeItemMetadataProjection.project(c,"f")).isEqualTo(2);
      try(var r=s.executeQuery("SELECT name,class_id,inventory_type,icon_id,item_set_id,has_client_data FROM item WHERE id=286978")) {
        assertThat(r.getString(1)).isNull();assertThat(r.getInt(2)).isEqualTo(4);assertThat(r.getInt(3)).isEqualTo(5);assertThat(r.getInt(4)).isZero();assertThat(r.getInt(5)).isEqualTo(2133);assertThat(r.getInt(6)).isZero();
      }
      try(var r=s.executeQuery("SELECT * FROM item WHERE id=16806")){assertThat(r.getString("name")).isEqualTo("Existing authored name");assertThat(r.getInt("class_id")).isEqualTo(9);assertThat(r.getInt("has_client_data")).isEqualTo(1);}
      try(var r=s.executeQuery("SELECT item_set_id FROM item WHERE id=999")){assertThat(r.getObject(1)).isNull();}
      try(var r=s.executeQuery("SELECT count(*) FROM native_item_metadata_fact WHERE item_id=999 AND source_table='ItemSet'")){assertThat(r.getInt(1)).isEqualTo(2);}
      try(var r=s.executeQuery("SELECT count(*) FROM native_item_metadata_fact WHERE item_id=999 AND source_table='ItemEffect' AND json_extract(payload,'$.SpellID')='1292252'")){assertThat(r.getInt(1)).isEqualTo(1);}
      assertThat(NativeItemMetadataProjection.project(c,"f")).isZero();
    }
  }
}
