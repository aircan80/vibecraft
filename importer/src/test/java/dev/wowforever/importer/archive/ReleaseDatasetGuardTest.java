package dev.wowforever.importer.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wowforever.importer.archive.records.SourceRecords;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReleaseDatasetGuardTest {
  @Test void releaseRejectsCaptureRebuildAndRawReadsBeforeChangingAnything() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      SourceArchive.initialize(c);
      SourceRecords.initialize(c);
      try (var statement = c.createStatement()) {
        statement.execute("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)");
        statement.execute("INSERT INTO meta VALUES ('release_dataset_profile','runtime-v1')");
      }
      assertThatThrownBy(() -> SourceArchive.initialize(c)).isInstanceOf(SQLException.class).hasMessageContaining("Runtime release database");
      assertThatThrownBy(() -> SourceArchive.capture(c,
          new SourceArchive.Snapshot("test", "1", "wow_classic_beta", "70450", "enUS"), List.of()))
          .isInstanceOf(SQLException.class).hasMessageContaining("development database");
      assertThatThrownBy(() -> SourceArchive.artifacts(c, "test")).isInstanceOf(SQLException.class).hasMessageContaining("cannot be rebuilt");
      assertThatThrownBy(() -> SourceRecords.capture(c, "test", List.of())).isInstanceOf(SQLException.class).hasMessageContaining("omits raw source evidence");
      assertThatThrownBy(() -> SourceRecords.records(c, "test", 2)).isInstanceOf(SQLException.class).hasMessageContaining("Runtime release database");
      try (var statement = c.createStatement(); var result = statement.executeQuery("SELECT count(*) FROM source_archive_snapshot")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isZero();
      }
    }
  }

  @Test void developmentAndNewDatabasesRemainUsable() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      SourceArchive.requireDevelopmentDataset(c);
      SourceArchive.initialize(c);
      try (var statement = c.createStatement()) { statement.execute("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)"); }
      SourceArchive.requireDevelopmentDataset(c);
      SourceRecords.initialize(c);
      assertThat(SourceRecords.records(c, "missing")).isEmpty();
    }
  }
}
