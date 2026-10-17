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

  @Test void legacyBuildMetadataHasHonestRevisionAndReferenceLinks() throws Exception {
    var file = dir.resolve("legacy.sqlite");
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + file); var sql = c.createStatement()) {
      sql.execute("CREATE TABLE meta(key TEXT,value TEXT)");
      sql.execute("INSERT INTO meta VALUES('questiedb_commit','49e7dbca1f18eb3b8e57ee5d9562ac3e5c9adcc4'),"
          + "('atlasloot_commit','8e99341e4e779328460bf7684c0d5b22ce50ddf1'),"
          + "('client_product','wow_classic_beta'),('client_build','1.60.1.70450'),"
          + "('client_fallback_product','wow_classic_era'),('client_fallback_build','1.15.9.70003'),"
          + "('serverdb_commit','464179081673cfd240f7ffe7f0daf96bca0b5a70'),('serverdb_asset','db-sqlite-4641790.zip')");
    }
    try (var db = Database.open(file)) {
      var result = new SourceArchiveRepository(db).catalog();
      assertThat(result.available()).isFalse();
      assertThat(result.snapshots()).isEmpty();
      assertThat(result.projection()).isNull();
      assertThat(result.legacySources()).hasSize(5);
      assertThat(result.legacySources().getFirst().url()).isEqualTo(
          "https://github.com/Questie/QuestieDB/tree/49e7dbca1f18eb3b8e57ee5d9562ac3e5c9adcc4");
      assertThat(result.legacySources().get(3).version()).contains("wow_classic_era", "1.15.9.70003");
      assertThat(result.legacySources().get(3).linkType()).contains("not archived source evidence");
      assertThat(result.legacySources().get(4).version()).contains("db-sqlite-4641790.zip");
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
      sql.execute("INSERT INTO meta VALUES('source_ingestion_id','selected'),('source_projection_id','1')");
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
  private Path historyDatabase(String execution, boolean metadataColumns) throws Exception {
    var file = dir.resolve("history.sqlite");
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + file); var sql = c.createStatement()) {
      sql.execute("CREATE TABLE source_archive_snapshot(id TEXT,source TEXT,version TEXT,product TEXT,build TEXT,locale TEXT,captured_at TEXT)");
      sql.execute("CREATE TABLE source_archive_file(snapshot_id TEXT,path TEXT,url TEXT,sha256 TEXT)");
      sql.execute("INSERT INTO source_archive_snapshot VALUES('old','questiedb','v1','forever','70450','enUS','2026-01-01'),('new','questiedb','v2','forever','70451','enUS','2026-02-01')");
      sql.execute("CREATE TABLE meta(key TEXT,value TEXT)");
      sql.execute("INSERT INTO meta VALUES('source_ingestion_id','selected')");
      if (execution != null) try (var insert = c.prepareStatement("INSERT INTO meta VALUES('source_projection_id',?)")) {
        insert.setString(1, execution); insert.executeUpdate();
      }
      sql.execute("CREATE TABLE source_ingestion_input(ingestion_id TEXT,role TEXT,snapshot_id TEXT)");
      sql.execute("INSERT INTO source_ingestion_input VALUES('selected','quests','old'),('other','quests','new')");
      sql.execute("CREATE TABLE source_projection(id INTEGER,ingestion_id TEXT,projected_at TEXT,transform_version TEXT)");
      sql.execute("INSERT INTO source_projection VALUES(1,'selected','2026-03-01','rules1'),(2,'selected','2026-04-01','rules2'),(3,'other','2026-05-01','rules3')");
      if (metadataColumns) {
        sql.execute("ALTER TABLE source_projection ADD COLUMN schema_version INTEGER");
        sql.execute("ALTER TABLE source_projection ADD COLUMN build_id TEXT");
        sql.execute("ALTER TABLE source_projection ADD COLUMN validation_summary TEXT");
        sql.execute("UPDATE source_projection SET schema_version=4,build_id='app-build-1',validation_summary='integrity ok' WHERE id=1");
      }
    }
    return file;
  }

  @Test void identifiesExactOlderExecutionAndKeepsAllRunsAndTheirInputVersions() throws Exception {
    try (var db = Database.open(historyDatabase("1", true))) {
      var catalog = new SourceArchiveRepository(db).catalog();
      assertThat(catalog.history()).extracting(SourceArchiveRepository.ProjectionRun::id).containsExactly("3", "2", "1");
      assertThat(catalog.history().stream().filter(SourceArchiveRepository.ProjectionRun::active))
          .extracting(SourceArchiveRepository.ProjectionRun::id).containsExactly("1");
      assertThat(catalog.projection().transformVersion()).isEqualTo("rules1");
      var run = catalog.history().get(2);
      assertThat(run.schemaVersion()).isEqualTo("4");
      assertThat(run.buildId()).isEqualTo("app-build-1");
      assertThat(run.validationSummary()).isEqualTo("integrity ok");
      assertThat(run.inputs().getFirst().snapshot().version()).isEqualTo("v1");
      assertThat(catalog.history().getFirst().inputs().getFirst().snapshot().build()).isEqualTo("70451");
    }
  }

  @Test void olderIngestionOnlyMetadataNeverGuessesLatestRunAndOptionalColumnsRemainUnknown() throws Exception {
    try (var db = Database.open(historyDatabase(null, false))) {
      var catalog = new SourceArchiveRepository(db).catalog();
      assertThat(catalog.history()).hasSize(3).noneMatch(SourceArchiveRepository.ProjectionRun::active);
      assertThat(catalog.projection()).isNull();
      assertThat(catalog.activeRoles()).containsOnlyKeys("old");
      assertThat(catalog.historyNote()).contains("execution ID was not recorded");
      assertThat(catalog.history()).allSatisfy(run -> assertThat(run.schemaVersion()).isNull());
    }
  }

  @Test void inconsistentRunAndIngestionCannotClaimActiveBuildOrInputs() throws Exception {
    try (var db = Database.open(historyDatabase("3", true))) {
      var catalog = new SourceArchiveRepository(db).catalog();
      assertThat(catalog.history()).noneMatch(SourceArchiveRepository.ProjectionRun::active);
      assertThat(catalog.projection()).isNull();
      assertThat(catalog.activeRoles()).isEmpty();
      assertThat(catalog.historyNote()).contains("does not match");
    }
  }

  @Test void retainedBaselineKeepsEarlierProviderReferencesWithoutInventingArchives() throws Exception {
    Path file=historyDatabase("1",true);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("INSERT INTO source_archive_snapshot VALUES('base','legacy-app-database','derived','application','build','','now')");
      s.execute("INSERT INTO meta VALUES('questiedb_commit','49e7dbca1f18eb3b8e57ee5d9562ac3e5c9adcc4')");
      s.execute("INSERT INTO source_ingestion_input VALUES('selected','legacy-app','base')");
    }
    try(var db=Database.open(file)) {
      var catalog=new SourceArchiveRepository(db).catalog();
      assertThat(catalog.available()).isTrue();
      assertThat(catalog.legacySources()).hasSize(1);
      assertThat(catalog.legacySources().getFirst().source()).isEqualTo("QuestieDB");
      assertThat(catalog.snapshots()).hasSize(3);
    }
  }

  @Test void historicalBaselineDoesNotMisattributeNewFullImportMetadata() throws Exception {
    Path file=historyDatabase("1",true);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("INSERT INTO source_archive_snapshot VALUES('base','legacy-app-database','derived','application','build','','now')");
      s.execute("INSERT INTO meta VALUES('questiedb_commit','49e7dbca1f18eb3b8e57ee5d9562ac3e5c9adcc4')");
      s.execute("INSERT INTO source_ingestion_input VALUES('historical','legacy-app','base')");
    }
    try(var db=Database.open(file)) {
      assertThat(new SourceArchiveRepository(db).catalog().legacySources()).isEmpty();
    }
  }

}
