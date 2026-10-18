package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SystemsRepositoryTest {
  @TempDir Path temp;

  private Path database() throws Exception {
    Path file=temp.resolve("systems.sqlite");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("CREATE TABLE client_table_dataset(snapshot_id TEXT PRIMARY KEY,build TEXT)");
      s.execute("CREATE TABLE client_table_row(snapshot_id TEXT,table_name TEXT,row_id TEXT,payload TEXT,source_path TEXT,source_line INTEGER,PRIMARY KEY(snapshot_id,table_name,row_id))");
      s.execute("INSERT INTO client_table_dataset VALUES('native','1.60.1.70450'),('era','1.15.9.70003')");
      try(var insert=c.prepareStatement("INSERT INTO client_table_row VALUES(?,?,?,?,?,?)")) {
        String[][] rows={
          {"native","TraitSystem","10","{\"ID\":\"10\",\"Flags\":\"0\",\"Blank\":\"\",\"Absent\":null}"},
          {"native","TraitDefinition","100","{\"ID\":\"100\",\"SpellID\":\"200\",\"OverrideName_lang\":\"Rune of \\\"Light\\\"\"}"},
          {"native","Spell","200","{\"ID\":\"200\",\"Description_lang\":\"First line\\nSecond line\"}"},
          {"native","Spell","201","{\"ID\":\"201\",\"Description_lang\":\"Unrelated spell\"}"},
          {"native","SpellName","200","{\"ID\":\"200\",\"Name_lang\":\"Rune\"}"},
          {"era","Talent","900","{\"ID\":\"900\",\"SpellRank_0\":\"200\",\"SpellRank_1\":\"0\"}"},
          {"era","SkillLineAbility","901","{\"ID\":\"901\",\"Spell\":\"200\",\"ClassMask\":\"1\"}"}};
        int line=2;
        for(String[] row:rows) {
          for(int i=0;i<4;i++)insert.setString(i+1,row[i]);
          insert.setString(5,row[0]+"/"+row[1]+".csv");insert.setInt(6,line++);insert.executeUpdate();
        }
      }
    }
    return file;
  }

  @Test void payloadReaderKeepsNativeAndEraComparisonValuesAndSourceCoordinates() throws Exception {
    try(var db=Database.open(database())) {
      var catalog=new SystemsRepository(db).load();
      assertThat(catalog.available()).isTrue();
      assertThat(catalog.build()).isEqualTo("1.60.1.70450");
      assertThat(catalog.baselineBuild()).isEqualTo("1.15.9.70003");
      var system=catalog.row("TraitSystem",10).orElseThrow();
      assertThat(system.values()).containsEntry("Flags","0").containsEntry("Blank","").doesNotContainKey("Absent");
      assertThat(system.sourcePath()).isEqualTo("native/TraitSystem.csv");
      assertThat(system.sourceLine()).isEqualTo(2);
      assertThat(catalog.row("TraitDefinition",100).orElseThrow().name()).isEqualTo("Rune of \"Light\"");
      assertThat(catalog.row("Spell",200).orElseThrow().text("Description_lang")).isEqualTo("First line\nSecond line");
      assertThat(catalog.row("Spell",201)).isEmpty();
      assertThat(catalog.spellName(200)).isEqualTo("Rune");
      assertThat(catalog.baselineTables().get("Talent")).singleElement().satisfies(row -> {
        assertThat(row.text("SpellRank_0")).isEqualTo("200");
        assertThat(row.text("SpellRank_1")).isEqualTo("0");
        assertThat(row.sourcePath()).isEqualTo("era/Talent.csv");
      });
      assertThat(new SystemsRepository(db).load()).isSameAs(catalog);
    }
  }

  @Test void concurrentFirstVisitsShareOneSnapshotAcrossRepositoryInstances() throws Exception {
    try(var db=Database.open(database());var workers=Executors.newFixedThreadPool(2)) {
      CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
      var first=workers.submit(() -> {ready.countDown();go.await();return new SystemsRepository(db).load();});
      var second=workers.submit(() -> {ready.countDown();go.await();return new SystemsRepository(db).load();});
      assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();
      go.countDown();
      assertThat(first.get(5,TimeUnit.SECONDS)).isSameAs(second.get(5,TimeUnit.SECONDS));
    }
  }
}
