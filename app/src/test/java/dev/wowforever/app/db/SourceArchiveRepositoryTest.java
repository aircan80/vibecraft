package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceArchiveRepositoryTest {
  @TempDir Path dir;

  @Test void oldDatabaseReportsArchiveUnavailable() throws Exception {
    var file = dir.resolve("old.sqlite");
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + file)) { c.createStatement().execute("CREATE TABLE meta(key,value)"); }
    try (var db = Database.open(file)) {
      var result = new SourceArchiveRepository(db).catalog();
      assertThat(result.available()).isFalse();
      assertThat(result.snapshots()).isEmpty();
    }
  }

  @Test void keepsHistoryAndNullableMetadataWithoutReadingBlobs() throws Exception {
    var file = dir.resolve("archive.sqlite");
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + file); var sql = c.createStatement()) {
      sql.execute("CREATE TABLE source_archive_snapshot(id TEXT,source TEXT,version TEXT,product TEXT,build TEXT,locale TEXT,captured_at TEXT)");
      sql.execute("CREATE TABLE source_archive_file(snapshot_id TEXT,path TEXT,url TEXT,sha256 TEXT)");
      sql.execute("INSERT INTO source_archive_snapshot VALUES('old','questiedb','v1',NULL,NULL,NULL,'2026-01-01'),('new','questiedb','v2','forever','70450','enUS','2026-02-01')");
      sql.execute("CREATE TABLE meta(key TEXT,value TEXT)");
      sql.execute("CREATE TABLE source_ingestion_input(ingestion_id TEXT,role TEXT,snapshot_id TEXT)");
      sql.execute("CREATE TABLE source_projection(id INTEGER,ingestion_id TEXT,projected_at TEXT,transform_version TEXT)");
      sql.execute("INSERT INTO meta VALUES('source_ingestion_id','selected')");
      sql.execute("INSERT INTO source_ingestion_input VALUES('selected','quests','old'),('other','quests','new')");
      sql.execute("INSERT INTO source_projection VALUES(1,'selected','2026-03-01','rules1'),(2,'other','2026-04-01','rules2')");
      sql.execute("INSERT INTO source_archive_file VALUES('new','quest.lua','https://example.org/quest.lua','abc')");
    }
    try (var db = Database.open(file)) {
      var result = new SourceArchiveRepository(db).catalog();
      assertThat(result.available()).isTrue();
      assertThat(result.snapshots()).extracting(SourceArchiveRepository.Snapshot::id).containsExactly("new", "old");
      assertThat(result.snapshots().getFirst().files().getFirst().url()).isEqualTo("https://example.org/quest.lua");
      assertThat(result.snapshots().get(1).build()).isNull();
      assertThat(result.snapshots().get(1).files()).isEmpty();
      assertThat(result.activeRoles()).containsOnlyKeys("old");
      assertThat(result.activeRoles().get("old")).containsExactly("quests");
      assertThat(result.projection().ingestionId()).isEqualTo("selected");
      assertThat(result.projection().transformVersion()).isEqualTo("rules1");
    }
  }
}
