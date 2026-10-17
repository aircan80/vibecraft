package dev.wowforever.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import dev.wowforever.importer.allthethings.AllTheThingsSource;
import dev.wowforever.importer.archive.SourceArchive;
import dev.wowforever.importer.clientdata.ClientDataOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyAttWorkflowTest {
  @TempDir Path tmp;

  @Test void capturesExactPrearchiveBaselineWithoutChangingExistingAppRowsAndRecaptureIsIdempotent() throws Exception {
    Path database = baseline();
    byte[] original = Files.readAllBytes(database);
    Path att = source();
    var options = options(database, att);
    String first = LegacyAttWorkflow.ingest(options);
    String second = LegacyAttWorkflow.ingest(options);
    assertThat(second).isEqualTo(first);
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + database)) {
      assertThat(SourceArchive.snapshots(c)).hasSize(3);
      String base = scalar(c, "SELECT snapshot_id FROM source_ingestion_input WHERE role='legacy-app'");
      var stored = SourceArchive.snapshot(c, base);
      assertThat(stored.metadata().source()).isEqualTo("legacy-app-database");
      assertThat(stored.metadata().version()).contains("derived app baseline");
      assertThat(SourceArchive.artifacts(c, base)).singleElement().satisfies(a -> {
        assertThat(a.path()).isEqualTo("base.sqlite");
        assertThat(a.bytes()).isEqualTo(original);
      });
      assertThat(scalar(c, "SELECT name FROM item WHERE id=1")).isEqualTo("Preserved item");
      assertThat(scalar(c, "SELECT value FROM meta WHERE key='build_id'")).isEqualTo("original-build");
      assertThat(scalar(c, "SELECT count(*) FROM source_ingestion")).isEqualTo("1");
      String attSnapshot = scalar(c, "SELECT snapshot_id FROM source_ingestion_input WHERE role='allthethings'");
      assertThat(SourceArchive.snapshot(c, attSnapshot).metadata().version()).contains("(local input;");
      assertThat(SourceArchive.artifacts(c, attSnapshot)).hasSize(AllTheThingsSource.paths().size())
          .allSatisfy(a -> assertThat(a.url()).isEmpty());
    }
  }

  @Test void changedAttCaptureReusesTheOriginalBaselineInsteadOfEmbeddingArchivedDatabase() throws Exception {
    Path database = baseline(), att = source();
    String before = LegacyAttWorkflow.ingest(options(database, att));
    String path = AllTheThingsSource.paths().stream().filter(AllTheThingsSource::isQuestInput).findFirst().orElseThrow();
    Files.writeString(att.resolve(path), "return {q(2,{sourceQuest=1})}");
    String after = LegacyAttWorkflow.ingest(options(database, att));
    assertThat(after).isNotEqualTo(before);
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + database)) {
      assertThat(scalar(c, "SELECT count(*) FROM source_archive_snapshot WHERE source='legacy-app-database'")).isEqualTo("1");
      assertThat(scalar(c, "SELECT count(DISTINCT snapshot_id) FROM source_ingestion_input WHERE role='legacy-app'")).isEqualTo("1");
      assertThat(scalar(c, "SELECT count(*) FROM source_ingestion")).isEqualTo("2");
    }
  }

  @Test void partialOrSymbolicAttSourcesAreRejectedBeforeArchivingChangesTheDatabase() throws Exception {
    Path database = baseline(), att = source();
    byte[] original = Files.readAllBytes(database);
    Path missing = att.resolve(AllTheThingsSource.paths().getFirst());
    Files.delete(missing);
    assertThatThrownBy(() -> LegacyAttWorkflow.ingest(options(database, att))).hasMessageContaining("Missing AllTheThings");
    assertThat(Files.readAllBytes(database)).isEqualTo(original);
    Path outside = tmp.resolve("outside"); Files.writeString(outside, "outside");
    Files.createSymbolicLink(missing, outside);
    assertThatThrownBy(() -> LegacyAttWorkflow.ingest(options(database, att))).hasMessageContaining("Symbolic");
    assertThat(Files.readAllBytes(database)).isEqualTo(original);
  }

  @Test void sourceDatabaseWithoutRetainedLegacyBaselineCannotBecomeItsOwnRecursiveBaseline() throws Exception {
    Path database = baseline();
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + database); var s = c.createStatement()) {
      s.execute("CREATE TABLE source_ingestion(id TEXT PRIMARY KEY,captured_at TEXT NOT NULL)");
    }
    assertThatThrownBy(() -> LegacyAttWorkflow.ingest(options(database, source())))
        .hasMessageContaining("no retained prearchive baseline");
  }

  @Test void activeJournalOrMissingDatabaseCannotBeCapturedAsAConsistentBaseline() throws Exception {
    Path database = baseline(), att = source();
    Files.writeString(database.resolveSibling(database.getFileName() + "-wal"), "not-checkpointed");
    assertThatThrownBy(() -> LegacyAttWorkflow.ingest(options(database, att))).hasMessageContaining("Active SQLite sidecar");
    Path missing = tmp.resolve("missing.sqlite");
    assertThatThrownBy(() -> LegacyAttWorkflow.ingest(options(missing, att))).hasMessageContaining("Existing application database");
    assertThat(missing).doesNotExist();
  }

  @Test void legacyProjectionReplaysOfflinePreservesCoreRowsAndRecapturesOriginalSnapshot() throws Exception {
    Path database = baseline(), att = source();
    String path = AllTheThingsSource.paths().stream().filter(AllTheThingsSource::isQuestInput).findFirst().orElseThrow();
    Files.writeString(att.resolve(path), "return {q(2,{sourceQuest=1})}");
    String ingestion = LegacyAttWorkflow.ingest(options(database, att));
    String base;
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + database)) {
      base = scalar(c, "SELECT snapshot_id FROM source_ingestion_input WHERE role='legacy-app'");
    }
    try (var files = Files.walk(att)) {
      for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file);
    }
    var output = new java.io.PrintStream(new java.io.ByteArrayOutputStream());
    assertThat(ArchiveWorkflow.project(database, ingestion, output)).isZero();
    String build;
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + database)) {
      assertThat(scalar(c, "SELECT value FROM meta WHERE key='source_base_snapshot_id'")).isEqualTo(base);
      assertThat(scalar(c, "SELECT name FROM item WHERE id=1")).isEqualTo("Preserved item");
      assertThat(scalar(c, "SELECT count(*) FROM quest_relation WHERE quest_id=2 AND related_id=1 AND kind='pre_single'")).isEqualTo("1");
      assertThat(scalar(c, "SELECT source_url FROM quest_relation_source WHERE quest_id=2")).isEmpty();
      assertThat(scalar(c, "SELECT value FROM meta WHERE key='att_local_input'")).isEqualTo("true");
      build = scalar(c, "SELECT value FROM meta WHERE key='build_id'");
    }
    assertThat(ArchiveWorkflow.project(database, ingestion, output)).isZero();
    source(); Files.writeString(att.resolve(path), "return {q(2,{sourceQuest=1})}");
    assertThat(LegacyAttWorkflow.ingest(options(database, att))).isEqualTo(ingestion);
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + database)) {
      assertThat(scalar(c, "SELECT value FROM meta WHERE key='build_id'")).isEqualTo(build);
      assertThat(scalar(c, "SELECT count(*) FROM source_projection")).isEqualTo("2");
      assertThat(scalar(c, "SELECT count(*) FROM source_archive_snapshot WHERE source='legacy-app-database'")).isEqualTo("1");
    }
  }

  @Test void corruptRetainedBaselineCannotBeRecapturedAsValidEvidence() throws Exception {
    Path database = baseline(), att = source();
    LegacyAttWorkflow.ingest(options(database, att));
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + database); var s = c.createStatement()) {
      s.executeUpdate("UPDATE source_archive_blob SET bytes=X'01' WHERE sha256 IN (SELECT sha256 FROM source_archive_file WHERE path='base.sqlite')");
    }
    assertThatThrownBy(() -> LegacyAttWorkflow.ingest(options(database, att))).hasMessageContaining("Corrupt source artifact");
  }

  @Test void explicitOtherSourceInputsAreRejectedInsteadOfSilentlyIgnored() throws Exception {
    Path database = baseline(), att = source(), other = tmp.resolve("other-source");
    byte[] original = Files.readAllBytes(database);
    List<ImportOptions> choices = List.of(
        new ImportOptions(database, tmp, Optional.of(other), Optional.empty(), false, tmp.resolve("build-id.txt"),
            ClientDataOptions.skipped(), 85, Optional.empty(), Optional.of(att), false),
        new ImportOptions(database, tmp, Optional.empty(), Optional.of(other), false, tmp.resolve("build-id.txt"),
            ClientDataOptions.skipped(), 85, Optional.empty(), Optional.of(att), false),
        new ImportOptions(database, tmp, Optional.empty(), Optional.empty(), false, tmp.resolve("build-id.txt"),
            ClientDataOptions.of(other), 85, Optional.empty(), Optional.of(att), false),
        new ImportOptions(database, tmp, Optional.empty(), Optional.empty(), false, tmp.resolve("build-id.txt"),
            ClientDataOptions.skipped(), 85, Optional.of(other), Optional.of(att), false));
    for (var choice : choices) assertThatThrownBy(() -> LegacyAttWorkflow.ingest(choice))
        .hasMessageContaining("use normal ingest to capture these inputs");
    assertThat(Files.readAllBytes(database)).isEqualTo(original);
  }

  private Path baseline() throws Exception {
    Path database = tmp.resolve("app.sqlite");
    dev.wowforever.importer.sqlite.SqliteWriter.write(database,
        new dev.wowforever.importer.sqlite.ImportModel(java.util.Map.of(),
            new dev.wowforever.importer.drops.DropRates(), java.util.Map.of(), List.of(), java.util.Map.of("build_id", "original-build")));
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + database); var s = c.createStatement()) {
      s.execute("INSERT OR REPLACE INTO meta VALUES('build_id','original-build')");
      s.execute("INSERT INTO item(id,name) VALUES(1,'Preserved item')");
      s.execute("INSERT INTO quest(id,name) VALUES(1,'Preserved quest')");
      s.execute("INSERT INTO quest(id,name) VALUES(2,'Follow-up')");
    }
    return database;
  }
  private Path source() throws Exception {
    Path root = tmp.resolve("att");
    for (String path : AllTheThingsSource.paths()) {
      Path file = root.resolve(path); Files.createDirectories(file.getParent());
      Files.writeString(file, path.endsWith(".csv") ? "ID\n" : path.endsWith(".lua") ? "return {}" : "local fixture");
    }
    return root;
  }
  private ImportOptions options(Path database, Path att) {
    return new ImportOptions(database, tmp.resolve("empty-cache"), Optional.empty(), Optional.empty(), false,
        tmp.resolve("build-id.txt"), ClientDataOptions.skipped(), 85, Optional.empty(), Optional.of(att), false);
  }
  private static String scalar(java.sql.Connection c, String sql) throws Exception {
    try (var s = c.createStatement(); var r = s.executeQuery(sql)) { r.next(); return r.getString(1); }
  }
}
