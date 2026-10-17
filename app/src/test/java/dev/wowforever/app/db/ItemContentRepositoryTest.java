package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemContentRepositoryTest {
  @TempDir Path temp;
  private Path fixture() throws Exception {
    Path file=temp.resolve("content.sqlite");Files.copy(FixtureDb.path(),file);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()){
      s.execute("CREATE TABLE client_table_dataset(snapshot_id TEXT,product TEXT,build TEXT,locale TEXT)");
      s.execute("INSERT INTO client_table_dataset VALUES('f','wow_classic_beta','1.60.1.70450','enUS'),('e','wow_classic_era','1.15.9.70003','enUS')");
      s.execute("CREATE TABLE client_table_row(snapshot_id TEXT,table_name TEXT,row_id TEXT,payload TEXT,source_path TEXT,source_line INTEGER)");
    }return file;
  }
  private void row(Path file,String snap,String table,String id,String json)throws Exception{
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var p=c.prepareStatement("INSERT INTO client_table_row VALUES(?,?,?,?,?,?)")){
      p.setString(1,snap);p.setString(2,table);p.setString(3,id);p.setString(4,json);p.setString(5,table+".csv");p.setInt(6,Integer.parseInt(id)+1);p.executeUpdate();
    }
  }
  @Test void comparesActualSetMembershipAndKeepsMissingSparseHidden()throws Exception{
    Path file=fixture();row(file,"f","ItemSet","2133","{\"ID\":\"2133\",\"Name_lang\":\"Rotmender's Raiment\",\"ItemID_0\":\"286978\",\"ItemID_1\":\"286979\",\"ItemID_2\":\"286980\",\"ItemID_3\":\"286981\",\"ItemID_4\":\"286982\"}");
    row(file,"e","ItemSet","2133","{\"ID\":\"2133\",\"Name_lang\":\"Classic fixture\",\"ItemID_0\":\"117\"}");row(file,"e","ItemSparse","117","{\"ID\":\"117\",\"Display_lang\":\"Classic apple\"}");
    try(var db=Database.open(file)){
      var page=new ItemContentRepository(db).load("set",2133);assertThat(page.entries()).hasSize(5).allMatch(e->e.state().equals("Hidden"));
      assertThat(page.classic()).singleElement().satisfies(e->assertThat(e.name()).isEqualTo("Classic apple"));assertThat(page.note()).contains("1.60.1.70450","1.15.9.70003");
    }
  }
  @Test void appearanceGroupsIdentityWithoutInventingMappings()throws Exception{
    Path file=fixture();row(file,"f","ItemModifiedAppearance","1","{\"ID\":\"1\",\"ItemID\":\"117\",\"ItemAppearanceID\":\"55\"}");row(file,"f","ItemModifiedAppearance","2","{\"ID\":\"2\",\"ItemID\":\"118\",\"ItemAppearanceID\":\"55\"}");row(file,"f","ItemModifiedAppearance","3","{\"ID\":\"3\",\"ItemID\":\"119\",\"ItemAppearanceID\":\"0\"}");
    try(var db=Database.open(file)){var repo=new ItemContentRepository(db);assertThat(repo.appearance(117).members()).singleElement().satisfies(e->assertThat(e.id()).isEqualTo(118));assertThat(repo.appearance(119).members()).isEmpty();}
  }
  @Test void comprehensionUsesSkillReferenceNotItemNameAndMissingCellStaysNull()throws Exception{
    Path file=fixture();row(file,"f","SkillLine","3012","{\"ID\":\"3012\",\"DisplayName_lang\":\"Comprehension\"}");row(file,"f","ItemSparse","1","{\"ID\":\"1\",\"Display_lang\":\"Unintelligible Scroll\",\"RequiredSkill\":\"3012\",\"RequiredSkillRank\":\"\"}");row(file,"f","ItemSparse","2","{\"ID\":\"2\",\"Display_lang\":\"Comprehension in name only\",\"RequiredSkill\":\"0\"}");
    try(var db=Database.open(file)){var page=new ItemContentRepository(db).load("comprehension",0);assertThat(page.entries()).singleElement().satisfies(e->{assertThat(e.id()).isEqualTo(1);assertThat(e.details()).contains("not yet discovered");});assertThat(db.read(c->ClientTableRows.read(c,"f","ItemSparse")).get(0).number("RequiredSkillRank")).isNull();}
  }
  @Test void hasStatSurvivesOtherFiltersAndMatchesStoredStat()throws Exception{
    Path file=fixture();try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()){s.execute("INSERT INTO item_stat(item_id,ordinal,stat_id,value) VALUES(117,99,128,7)");}
    var filter=dev.wowforever.app.model.ItemFilter.ALL.withStat(128).withLevels(null,null).withClass(null,null).withQuality(null);
    assertThat(filter.statId()).isEqualTo(128);
    try(var db=Database.open(file)){assertThat(new ItemRepository(db).list(filter)).extracting(e->e.id()).contains(117L);assertThat(new ItemRepository(db).list(filter.withStat(140))).noneMatch(e->e.id()==117);}
  }

  @Test void xpFoodRequiresNativeFoodClassAndAnExplicitXpReferenceChain()throws Exception {
    Path file=fixture();
    row(file,"f","SpellEffect","1","{\"ID\":\"1\",\"SpellID\":\"2000\",\"EffectAura\":\"200\"}");
    row(file,"f","Spell","2001","{\"ID\":\"2001\",\"Description_lang\":\"Conditional benefit $@spelldesc2000\"}");
    row(file,"f","ItemEffect","2","{\"ID\":\"2\",\"SpellID\":\"2001\"}");
    row(file,"f","ItemXItemEffect","3","{\"ID\":\"3\",\"ItemID\":\"117\",\"ItemEffectID\":\"2\"}");
    row(file,"f","Item","117","{\"ID\":\"117\",\"ClassID\":\"0\",\"SubclassID\":\"5\"}");
    row(file,"f","ItemSparse","117","{\"ID\":\"117\",\"Display_lang\":\"Fixture Food\"}");
    try(var db=Database.open(file)){var page=new ItemContentRepository(db).load("xp-food",0);assertThat(page.entries()).singleElement().satisfies(e->{assertThat(e.id()).isEqualTo(117);assertThat(e.details()).contains("conditions").doesNotContain("0%");});}
  }

  @Test void setChipsUseOnlyQualifiedDiffEvidenceAndBonusesSortByThreshold()throws Exception {
    Path file=fixture();
    row(file,"f","ItemSet","1","{\"ID\":\"1\",\"Name_lang\":\"Shared\",\"NewColumn\":\"0\"}");
    row(file,"e","ItemSet","1","{\"ID\":\"1\",\"Name_lang\":\"Shared\"}");
    row(file,"f","ItemSetSpell","4","{\"ID\":\"4\",\"ItemSetID\":\"1\",\"Threshold\":\"4\",\"SpellID\":\"40\"}");
    row(file,"f","ItemSetSpell","2","{\"ID\":\"2\",\"ItemSetID\":\"1\",\"Threshold\":\"2\",\"SpellID\":\"20\"}");
    try(var db=Database.open(file)){var repo=new ItemContentRepository(db);assertThat(repo.load("sets",0).entries()).singleElement().satisfies(e->assertThat(e.state()).isEqualTo("Known"));assertThat(repo.load("set",1).entries()).extracting(ItemContentRepository.Entry::name).containsExactly("(2) Set bonus","(4) Set bonus");}
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var statement=c.createStatement()) {
      statement.execute("CREATE TABLE forever_diff_coverage(kind TEXT,forever_snapshot_id TEXT,era_snapshot_id TEXT,state TEXT)");
      statement.execute("CREATE TABLE forever_diff(kind TEXT,id TEXT,status TEXT,forever_snapshot_id TEXT,era_snapshot_id TEXT)");
      statement.execute("INSERT INTO forever_diff_coverage VALUES('ItemSet','f','e','partial_schema')");
      statement.execute("INSERT INTO forever_diff VALUES('ItemSet','1','changed','f','e')");
    }
    try(var db=Database.open(file)){assertThat(new ItemContentRepository(db).load("sets",0).entries()).singleElement().satisfies(e->assertThat(e.state()).isEqualTo("Changed"));}
  }

  @Test void formattedSetBonusesKeepForeverAndEraSourcesSeparate()throws Exception {
    Path file=fixture();
    for(String snapshot:new String[]{"f","e"}) {
      row(file,snapshot,"ItemSet","1","{\"ID\":\"1\",\"Name_lang\":\"Fixture Set\"}");
      row(file,snapshot,"ItemSetSpell","2","{\"ID\":\"2\",\"ItemSetID\":\"1\",\"Threshold\":\"2\",\"SpellID\":\"20\"}");
      row(file,snapshot,"ItemSetSpell","4","{\"ID\":\"4\",\"ItemSetID\":\"1\",\"Threshold\":\"4\",\"SpellID\":\"40\"}");
      row(file,snapshot,"Spell","40","{\"ID\":\"40\",\"Description_lang\":\"Restores $s1 Mana.\"}");
    }
    row(file,"e","Spell","20","{\"ID\":\"20\",\"Description_lang\":\"True Classic bonus\"}");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var statement=c.createStatement()) {
      statement.execute("UPDATE item SET item_set_id=1,client_source='forever' WHERE id=117");
      statement.execute("INSERT INTO item(id,name,item_set_id,client_source) VALUES(118,'Classic fixture piece',1,'era') ON CONFLICT(id) DO UPDATE SET item_set_id=1,client_source='era'");
      statement.execute("INSERT INTO item(id,name,item_set_id,client_source) VALUES(999990,'Hotfix piece',1,'hotfix')");
      statement.execute("DELETE FROM item_tooltip_line WHERE item_id IN (117,118,999990) AND kind='set_bonus'");
      statement.execute("INSERT INTO item_tooltip_line(item_id,ordinal,kind,text) VALUES(117,990,'set_bonus','(2) Set: Forever bonus'),(118,990,'set_bonus','(2) Set: Forever text masquerading'),(999990,990,'set_bonus','(4) Set: Hotfix bonus')");
    }
    try(var db=Database.open(file)) {
      var page=new ItemContentRepository(db).load("set",1);
      assertThat(page.entries()).extracting(ItemContentRepository.Entry::details).containsExactly("Forever bonus","Hotfix bonus");
      assertThat(page.classic()).extracting(ItemContentRepository.Entry::details).containsExactly("True Classic bonus","Client formula (not resolved): Restores $s1 Mana. · Client formula contains unresolved values");
    }
  }
}
