package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.ItemFilter;
import dev.wowforever.app.model.EntityKind;
import java.nio.file.*;
import java.sql.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForeverItemProjectionTest {
  @TempDir Path temp;

  @Test void eraCardsAndFiltersStaySeparateFromForeverWithoutDeletingArchive() throws Exception {
    Path file=temp.resolve("scope.sqlite");Files.copy(FixtureDb.path(),file);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("UPDATE item SET client_source='era',has_client_data=1,item_level=66,required_level=60,quality=4,inventory_type=6,allowable_class=256 WHERE id=14149");
      s.execute("UPDATE item SET client_source='hotfix' WHERE id=2874");
      s.execute("INSERT INTO quest(id,name) VALUES(999999,'Boundary reward')");
      s.execute("INSERT INTO quest_reward_item VALUES(999999,14149)");
      s.execute("CREATE TABLE client_table_dataset(snapshot_id TEXT,product TEXT,build TEXT,locale TEXT)");
      s.execute("CREATE TABLE client_table_row(snapshot_id TEXT,table_name TEXT,row_id TEXT,payload TEXT,source_path TEXT,source_line INTEGER)");
      s.execute("INSERT INTO client_table_dataset VALUES('era-native','wow_classic_era','1.15.9.70003','enUS')");
      s.execute("INSERT INTO client_table_row VALUES('era-native','ItemSparse','14149','{\"Display_lang\":\"Genuine Classic name\",\"ItemLevel\":\"66\",\"RequiredLevel\":\"60\"}','Era/ItemSparse.csv',10)");
      s.execute("INSERT OR REPLACE INTO item_stat(item_id,ordinal,stat_id,value) VALUES(14149,999,125,777)");
    }
    try(var db=Database.open(file)) {
      var repo=new ItemRepository(db);var era=repo.detail(14149).orElseThrow();
      assertThat(era.card().lines()).isEmpty();
      assertThat(era.summary().name()).isNull();assertThat(era.summary().quality()).isNull();
      assertThat(era.summary().itemLevel()).isNull();assertThat(era.summary().requiredLevel()).isNull();
      assertThat(era.droppedBy()).isNotEmpty();
      assertThat(new NpcRepository(db).detail(11520).orElseThrow().drops()).filteredOn(i->i.item().id()==14149).singleElement().satisfies(i->{assertThat(i.inventoryType()).isNull();assertThat(i.allowableClass()).isNull();assertThat(i.itemName()).isNull();assertThat(i.itemLevel()).isNull();assertThat(i.quality()).isNull();});
      assertThat(new QuestRepository(db).detail(999999).orElseThrow().rewards()).singleElement().satisfies(i->{assertThat(i.item().id()).isEqualTo(14149);assertThat(i.itemLevel()).isNull();assertThat(i.quality()).isNull();});
      assertThat(repo.classicComparison(14149)).get().satisfies(i->{assertThat(i.name()).isEqualTo("Genuine Classic name");assertThat(i.itemLevel()).isEqualTo(66);assertThat(i.requiredLevel()).isEqualTo(60);assertThat(i.evidence()).contains("Era/ItemSparse.csv:10");});
      assertThat(repo.detail(2874).orElseThrow().card().lines()).isNotEmpty();
      assertThat(repo.list(ItemFilter.ALL.withStat(125))).noneMatch(i->i.id()==14149);
      assertThat(repo.list(ItemFilter.ALL.withLevels(66,66))).noneMatch(i->i.id()==14149);
      assertThat(repo.list(ItemFilter.ALL.withQuality(4))).noneMatch(i->i.id()==14149);
      assertThat(new ItemContentRepository(db).load("stat",125).entries()).noneMatch(i->i.id()==14149);
      assertThat(new SearchRepository(db).search("Subterranean",Optional.of(EntityKind.ITEM),100)).noneMatch(i->i.ref().id()==14149);
      assertThat(new FieldKnowledgeRepository(db).item(14149)).filteredOn(k->k.field().equals("item_level")).allSatisfy(k->assertThat(k.value()).isNull());
      String archivedName=db.read(c->Sql.one(c,"SELECT name FROM main.item WHERE id=14149",r->r.getString(1)).orElseThrow());
      assertThat(archivedName).isNotBlank();
      int archivedLevel=db.read(c->Sql.one(c,"SELECT item_level FROM main.item WHERE id=14149",r->r.getInt(1)).orElseThrow());
      assertThat(archivedLevel).isEqualTo(66);
    }
  }
  @Test void nativeForeverEquipmentSlotReachesLootIndependentlyFromAddonOrder() throws Exception {
    Path file=temp.resolve("known-loot.sqlite");Files.copy(FixtureDb.path(),file);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("UPDATE item SET client_source='hotfix',inventory_type=6,allowable_class=256 WHERE id=14149");
    }
    try(var db=Database.open(file)) {
      var rows=new NpcRepository(db).detail(11520).orElseThrow().drops();
      assertThat(rows).filteredOn(r->r.item().id()==14149).singleElement().satisfies(r->{
        assertThat(r.inventoryType()).isEqualTo(6);
        assertThat(r.allowableClass()).isEqualTo(256);
        assertThat(r.slot()).isNull();
        assertThat(new dev.wowforever.app.model.LootFilter(9,6).apply(java.util.List.of(r)).rows())
            .containsExactly(r);
      });
    }
  }

}
