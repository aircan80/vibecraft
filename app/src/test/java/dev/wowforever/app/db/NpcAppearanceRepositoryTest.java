package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.app.model.NpcAppearance.State;
import java.nio.file.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NpcAppearanceRepositoryTest {
  @TempDir Path temp;
  private Path database(String name,String build)throws Exception {
    Path file=temp.resolve(name);Files.copy(FixtureDb.path(),file);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var p=c.prepareStatement("INSERT OR REPLACE INTO meta VALUES('client_build',?)")){p.setString(1,build);p.executeUpdate();}
    return file;
  }
  @Test void verifiedCatalogRetainsRealVariantsScalesTexturesAndSharedAssets()throws Exception {
    try(var db=Database.open(database("catalog.sqlite","1.60.1.70450"))) {
      var repo=new NpcAppearanceRepository(db);var squirrel=repo.resolve(2671);
      assertThat(squirrel.product()).isEqualTo("wow_classic_beta");assertThat(squirrel.build()).isEqualTo("1.60.1.70450");
      assertThat(squirrel.state()).isEqualTo(State.MAPPED);
      assertThat(squirrel.variants()).singleElement().satisfies(v->{
        assertThat(v.displayId()).isEqualTo(7937);assertThat(v.modelId()).isEqualTo(134);assertThat(v.modelFileDataId()).isEqualTo(126061);
        assertThat(v.textures()).containsExactly(126062L,0L,0L,0L);assertThat(v.displayScale()).isEqualTo(1.29999995232);
        assertThat(v.modelScale()).isEqualTo(1.0);assertThat(v.evidence()).extracting(e->e.table()).containsExactly("Creature","CreatureDisplayInfo","CreatureModelData");
        assertThat(v.evidence()).allSatisfy(e->{assertThat(e.sourceSha256()).hasSize(64);assertThat(e.sourceLine()).isGreaterThan(1);assertThat(e.payload()).isNotEmpty();});
      });
      assertThat(repo.resolve(10259).variants()).extracting(v->v.displayId()).containsExactly(9563L,9572L);
      assertThat(repo.resolve(7550).variants().getFirst().appearanceKey()).isEqualTo(repo.resolve(12419).variants().getFirst().appearanceKey());
      assertThat(repo.resolve(15698).state()).isEqualTo(State.UNSUPPORTED);
      assertThat(repo.resolve(15698).variants().getFirst().reason()).contains("Humanoid customization");
      assertThat(repo.resolve(639).state()).isEqualTo(State.UNRESOLVED);
    }
  }
  @Test void catalogNeverSubstitutesAnotherBuild()throws Exception {
    try(var db=Database.open(database("future.sqlite","1.60.1.70451"))) {
      var result=new NpcAppearanceRepository(db).resolve(2671);
      assertThat(result.state()).isEqualTo(State.UNRESOLVED);assertThat(result.variants()).isEmpty();
      assertThat(result.build()).isEqualTo("1.60.1.70451");
    }
  }
  @Test void nativeLookupKeepsEverySlotAndHonestUnsupportedAndMissingRecords()throws Exception {
    Path file=database("native.sqlite","1.60.1.70450");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("CREATE TABLE client_table_dataset(snapshot_id TEXT,product TEXT,build TEXT,locale TEXT)");
      s.execute("CREATE TABLE client_table_manifest(snapshot_id TEXT,table_name TEXT,sha256 TEXT)");
      s.execute("CREATE TABLE client_table_row(snapshot_id TEXT,table_name TEXT,row_id TEXT,payload TEXT,source_path TEXT,source_line INTEGER,PRIMARY KEY(snapshot_id,table_name,row_id))");
      s.execute("INSERT INTO client_table_dataset VALUES('f','wow_classic_beta','1.60.1.70450','enUS'),('e','wow_classic_era','1.60.1.70450','enUS')");
      s.execute("INSERT INTO client_table_manifest VALUES('f','Creature','creature-sha'),('f','CreatureDisplayInfo','display-sha'),('f','CreatureModelData','model-sha'),('f','CreatureDisplayInfoExtra','extra-sha'),('e','Creature','era-sha')");
      row(c,"f","Creature","900001","{\"Name_lang\":\"Native fixture\",\"DisplayID_0\":\"10\",\"DisplayID_1\":\"11\",\"DisplayID_2\":\"12\"}");
      row(c,"f","CreatureDisplayInfo","10","{\"ModelID\":\"20\",\"CreatureModelScale\":\"1.5\",\"CreatureModelAlpha\":\"255\",\"TextureVariationFileDataID_0\":\"8000\",\"TextureVariationFileDataID_2\":\"9000\"}");
      row(c,"f","CreatureDisplayInfo","11","{\"ModelID\":\"20\",\"CreatureModelScale\":\"1\",\"CreatureModelAlpha\":\"255\",\"ExtendedDisplayInfoID\":\"30\"}");
      row(c,"f","CreatureModelData","20","{\"FileDataID\":\"7000\",\"ModelScale\":\"2\"}");
      row(c,"f","CreatureDisplayInfoExtra","30","{\"DisplayRaceID\":\"1\"}");
      row(c,"e","Creature","900002","{\"DisplayID_0\":\"10\"}");
    }
    try(var db=Database.open(file)) {
      var repo=new NpcAppearanceRepository(db);var result=repo.resolve(900001);
      assertThat(result.state()).isEqualTo(State.PARTIAL);assertThat(result.catalogId()).isEqualTo("f");
      assertThat(result.variants()).extracting(v->v.displayId()).containsExactly(10L,11L,12L);
      assertThat(result.variants()).extracting(v->v.state()).containsExactly(State.MAPPED,State.UNSUPPORTED,State.UNRESOLVED);
      assertThat(result.variants().getFirst().textures()).containsExactly(8000L,0L,9000L,0L);
      assertThat(result.variants().getFirst().displayScale()).isEqualTo(1.5);assertThat(result.variants().getFirst().modelScale()).isEqualTo(2.0);
      assertThat(result.variants().get(1).evidence()).extracting(e->e.table()).contains("CreatureDisplayInfoExtra");
      assertThat(repo.resolve(900002).variants()).isEmpty();
    }
  }
  private static void row(Connection c,String snapshot,String table,String id,String json)throws Exception {
    try(var p=c.prepareStatement("INSERT INTO client_table_row VALUES(?,?,?,?,?,2)")){
      p.setString(1,snapshot);p.setString(2,table);p.setString(3,id);p.setString(4,json);p.setString(5,table+".csv");p.executeUpdate();
    }
  }
}
