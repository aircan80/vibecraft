package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import java.sql.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeRecipeGuideRepositoryTest {
  @TempDir Path temp;
  @Test void nativeCreateReagentsSkillsAndZeroRemainExactAndEraNeverFillsMissing()throws Exception {
    Path file=fixture();
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file)) {
      row(c,"f","SpellEffect","1",flat(Map.of("SpellID","100","Effect","24","EffectItemType","10","EffectBasePointsF","2","Variance","1","DifficultyID","0","EffectIndex","0")));
      row(c,"f","SpellName","100",Map.of("Name_lang","Native Craft"));
      row(c,"f","ItemSparse","10",Map.of("Display_lang","Native output"));
      row(c,"f","SpellReagents","20",Map.of("SpellID","100","Reagent_0","11","ReagentCount_0","0","Reagent_1","12","ReagentCount_1","3"));
      row(c,"e","ItemSparse","11",Map.of("Display_lang","Era name must not leak"));
      row(c,"f","ItemSparse","12",Map.of("Display_lang","Native reagent"));
      row(c,"f","SkillLineAbility","30",Map.of("Spell","100","SkillLine","185","MinSkillLineRank","0","TrivialSkillLineRankLow","25","TrivialSkillLineRankHigh","50"));
      row(c,"f","SkillLine","185",Map.of("DisplayName_lang","Cooking","CategoryID","9"));
      row(c,"e","SpellEffect","2",Map.of("SpellID","101","Effect","24","EffectItemType","99"));
    }
    try(var db=Database.open(file)) {
      var repo=new NativeRecipeGuideRepository(db);var snapshot=repo.forItem(10);var recipe=snapshot.recipes().getFirst();
      assertThat(snapshot.build()).isEqualTo("1.60.test");assertThat(recipe.name()).isEqualTo("Native Craft");
      assertThat(recipe.quantity()).isEqualTo("1–3");assertThat(recipe.ingredients()).containsExactly(new NativeRecipeGuideRepository.Ingredient(11,"Item #11",0L),new NativeRecipeGuideRepository.Ingredient(12,"Native reagent",3L));
      assertThat(recipe.skills().getFirst().rank()).isZero();assertThat(recipe.skills().getFirst().profession()).isTrue();assertThat(recipe.evidence()).allMatch(e->e.path().startsWith("native/"));
      assertThat(repo.forItem(10)).isSameAs(snapshot);assertThat(repo.forItem(99).available()).isFalse();
    }
  }
  @Test void missingQuantityAndReagentRecordsStayUnknown()throws Exception {
    Path file=fixture();try(var c=DriverManager.getConnection("jdbc:sqlite:"+file)){row(c,"f","SpellEffect","1",Map.of("SpellID","100","Effect","24","EffectItemType","10"));}
    try(var db=Database.open(file)){var recipe=new NativeRecipeGuideRepository(db).forItem(10).recipes().getFirst();assertThat(recipe.quantity()).isEqualTo("Unknown");assertThat(recipe.ingredients()).isEmpty();assertThat(recipe.reagentCoverage()).contains("unknown");assertThat(recipe.skills()).isEmpty();}
  }
  @Test void levelResourceAndCoefficientInputsNeverBecomeFixedYieldAndMissingIsNotZero()throws Exception {
    var fields=java.util.List.of("EffectRealPointsPerLevel","EffectPointsPerResource","Coefficient","EffectBonusCoefficient","BonusCoefficientFromAP","ResourceCoefficient","ScalingClass");
    Path file=fixture();
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file)) {
      int id=100;
      for(String field:fields){var cells=flat(Map.of("SpellID","100","Effect","24","EffectItemType",Integer.toString(id),"EffectBasePointsF","2","Variance","0"));cells.put(field,"2");row(c,"f","SpellEffect",Integer.toString(id++),cells);}
      var missing=flat(Map.of("SpellID","100","Effect","24","EffectItemType","200","EffectBasePointsF","2","Variance","0"));missing.remove("Coefficient");row(c,"f","SpellEffect","200",missing);
      row(c,"f","SpellEffect","201",flat(Map.of("SpellID","100","Effect","24","EffectItemType","201","EffectBasePointsF","2","Variance","0")));
    }
    try(var db=Database.open(file)){var repo=new NativeRecipeGuideRepository(db);for(int id=100;id<107;id++)assertThat(repo.forItem(id).recipes().getFirst().quantity()).as("context field %s",fields.get(id-100)).isEqualTo("Context-dependent");assertThat(repo.forItem(200).recipes().getFirst().quantity()).isEqualTo("Unknown");assertThat(repo.forItem(201).recipes().getFirst().quantity()).isEqualTo("2");}
    assertThat(new NativeRecipeGuideRepository.Skill(237,"Arcane",0L,0L,0L,7L).association()).isEqualTo("Class ability");
    assertThat(new NativeRecipeGuideRepository.Skill(237,"Arcane",null,null,null).profession()).isFalse();
  }
  private static java.util.HashMap<String,String> flat(Map<String,String> cells){var result=new java.util.HashMap<>(cells);for(String field:java.util.List.of("EffectRealPointsPerLevel","EffectPointsPerResource","Coefficient","EffectBonusCoefficient","BonusCoefficientFromAP","ResourceCoefficient","ScalingClass"))result.put(field,"0");return result;}
  private Path fixture()throws Exception {
    Path file=temp.resolve(java.util.UUID.randomUUID()+".sqlite");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)");s.execute("INSERT INTO meta VALUES('forever_diff_forever_snapshot_id','f')");
      s.execute("CREATE TABLE client_table_dataset(snapshot_id TEXT,product TEXT,build TEXT,locale TEXT)");s.execute("INSERT INTO client_table_dataset VALUES('f','wow_classic_beta','1.60.test','enUS'),('e','wow_classic_era','1.15.test','enUS')");
      s.execute("CREATE TABLE client_table_row(snapshot_id TEXT,table_name TEXT,row_id TEXT,payload TEXT,source_path TEXT,source_line INTEGER,PRIMARY KEY(snapshot_id,table_name,row_id))");
    }
    return file;
  }
  private static void row(Connection c,String snapshot,String table,String id,Map<String,String> cells)throws Exception {
    try(var p=c.prepareStatement("INSERT INTO client_table_row VALUES(?,?,?,?,?,?)")){p.setString(1,snapshot);p.setString(2,table);p.setString(3,id);p.setString(4,new com.google.gson.Gson().toJson(cells));p.setString(5,"native/"+table+".csv");p.setInt(6,2);p.executeUpdate();}
  }
}
