package dev.wowforever.importer.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wowforever.importer.archive.SourceArchive.Artifact;
import dev.wowforever.importer.archive.SourceArchive.Kind;
import dev.wowforever.importer.archive.SourceArchive.Snapshot;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceArchiveTest {
  @TempDir Path tmp;
  private static final Snapshot META = new Snapshot("questiedb", "commit-a", "forever", "70450", "enUS");

  @Test void exactBytesUnknownFieldsAndUrlsRoundTripAndDeduplicate() throws Exception {
    try (var c = db()) {
      byte[] raw = {0, -1, 13, 10, 42};
      String id = SourceArchive.capture(c, META, List.of(new Artifact("data/raw.lua", "https://example.test/a", raw),
          new Artifact("unconsumed.bin", null, raw)));
      c.commit();
      var files = SourceArchive.artifacts(c, id);
      assertThat(files).extracting(Artifact::path).containsExactly("data/raw.lua", "unconsumed.bin");
      assertThat(files.getFirst().bytes()).containsExactly(raw);
      assertThat(files.getFirst().url()).isEqualTo("https://example.test/a");
      assertThat(count(c, "source_archive_blob")).isEqualTo(1);
      assertThat(SourceArchive.snapshot(c, id).metadata()).isEqualTo(META);
      assertThat(SourceArchive.snapshot(c, id).capturedAt()).isNotBlank();
      SourceArchive.extract(c, id, tmp.resolve("extract"));
      assertThat(Files.readAllBytes(tmp.resolve("extract/data/raw.lua"))).containsExactly(raw);
    }
  }

  @Test void fingerprintIgnoresOrderAndTimeButIncludesMetadataAndUrls() throws Exception {
    try (var c = db()) {
      Artifact a = a("a", "x"), b = a("b", "y");
      String first = SourceArchive.capture(c, META, List.of(a, b));
      String time = SourceArchive.snapshot(c, first).capturedAt();
      assertThat(SourceArchive.capture(c, META, List.of(b, a))).isEqualTo(first);
      assertThat(SourceArchive.snapshot(c, first).capturedAt()).isEqualTo(time);
      assertThat(SourceArchive.capture(c, new Snapshot("questiedb", "commit-b", "forever", "70450", "enUS"),
          List.of(a, b))).isNotEqualTo(first);
      assertThat(SourceArchive.capture(c, META, List.of(new Artifact("a", "https://different.test", a.bytes()), b)))
          .isNotEqualTo(first);
      assertThat(count(c, "source_archive_snapshot")).isEqualTo(3);
      assertThat(count(c, "source_archive_blob")).isEqualTo(2);
    }
  }

  @Test void callerCanRollBackCaptureAndNoCommitIsHidden() throws Exception {
    try (var c = db()) {
      c.commit();
      SourceArchive.capture(c, META, List.of(a("a", "data")));
      c.rollback();
      assertThat(SourceArchive.snapshots(c)).isEmpty();
      assertThat(count(c, "source_archive_blob")).isZero();
      c.setAutoCommit(true);
      assertThatThrownBy(() -> SourceArchive.capture(c, META, List.of())).isInstanceOf(SQLException.class);
    }
  }

  @Test void historicalDiffIncludesAddedRemovedChangedAndUrlChanges() throws Exception {
    try (var c = db()) {
      String oldId = SourceArchive.capture(c, META, List.of(a("same", "same"), a("removed", "x"),
          a("changed", "before"), a("url", "same")));
      String newId = SourceArchive.capture(c, META, List.of(a("same", "same"), a("added", "new"),
          a("changed", "after"), new Artifact("url", "https://new.test", "same".getBytes(StandardCharsets.UTF_8))));
      var changes = SourceArchive.diff(c, oldId, newId);
      assertThat(changes).extracting(SourceArchive.Change::path).containsExactly("added", "changed", "removed", "url");
      assertThat(changes).extracting(SourceArchive.Change::kind)
          .containsExactly(Kind.ADDED, Kind.CHANGED, Kind.REMOVED, Kind.CHANGED);
      assertThat(changes.get(3).oldSha256()).isEqualTo(changes.get(3).newSha256());
      assertThat(SourceArchive.artifacts(c, oldId)).hasSize(4);
      assertThat(SourceArchive.snapshots(c)).hasSize(2);
    }
  }

  @Test void pathsCannotEscapeOrCollide() throws Exception {
    for (String path : List.of("../a", "/a", "a/../b", "a//b", "a/./b", "C:/a", "a\\b", "a/", "a\u0000b"))
      assertThatThrownBy(() -> a(path, "x")).isInstanceOf(IllegalArgumentException.class);
    try (var c = db()) {
      assertThatThrownBy(() -> SourceArchive.capture(c, META, List.of(a("a", "1"), a("a", "2"))))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> SourceArchive.capture(c, META, List.of(a("a", "1"), a("a/b", "2"))))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(SourceArchive.snapshots(c)).isEmpty();
    }
  }

  @Test void unicodePathsUseStableOrderingAcrossJavaAndSqlite() throws Exception {
    try (var c = db()) {
      String id = SourceArchive.capture(c, META, List.of(a("\uE000.lua", "a"), a("\uD83D\uDE00.lua", "b")));
      assertThat(SourceArchive.artifacts(c, id)).hasSize(2);
    }
  }

  @Test void artifactByteArraysAreDefensive() {
    byte[] input = {1, 2};
    Artifact artifact = new Artifact("a", null, input);
    input[0] = 8;
    byte[] output = artifact.bytes(); output[0] = 9;
    assertThat(artifact.bytes()).containsExactly((byte) 1, (byte) 2);
  }

  @Test void corruptBytesAreRejectedBeforeExtractionAndRecapture() throws Exception {
    try (var c = db()) {
      String id = SourceArchive.capture(c, META, List.of(a("a", "correct")));
      try (var s = c.createStatement()) { s.execute("UPDATE source_archive_blob SET bytes=x'00'"); }
      assertThatThrownBy(() -> SourceArchive.extract(c, id, tmp.resolve("out"))).isInstanceOf(SQLException.class)
          .hasMessageContaining("Corrupt source artifact");
      assertThat(tmp.resolve("out")).doesNotExist();
      assertThatThrownBy(() -> SourceArchive.capture(c, META, List.of(a("a", "correct"))))
          .isInstanceOf(SQLException.class);
    }
  }

  @Test void missingFilesOrMetadataTamperingFailSnapshotFingerprint() throws Exception {
    try (var c = db()) {
      String id = SourceArchive.capture(c, META, List.of(a("a", "correct")));
      try (var s = c.createStatement()) { s.execute("DELETE FROM source_archive_file"); }
      assertThatThrownBy(() -> SourceArchive.artifacts(c, id)).isInstanceOf(SQLException.class)
          .hasMessageContaining("Corrupt source snapshot");
      assertThatThrownBy(() -> SourceArchive.snapshot(c, "missing")).isInstanceOf(SQLException.class);
    }
  }

  @Test void extractionRefusesExistingFilesAndSymlinkAncestors() throws Exception {
    try (var c = db()) {
      String id = SourceArchive.capture(c, META, List.of(a("a", "data")));
      Path nonempty = Files.createDirectory(tmp.resolve("nonempty"));
      Files.writeString(nonempty.resolve("keep"), "original");
      assertThatThrownBy(() -> SourceArchive.extract(c, id, nonempty)).isInstanceOf(java.io.IOException.class);
      assertThat(Files.readString(nonempty.resolve("keep"))).isEqualTo("original");
      Path link = tmp.resolve("link");
      Files.createSymbolicLink(link, tmp);
      assertThatThrownBy(() -> SourceArchive.extract(c, id, link.resolve("child")))
          .isInstanceOf(java.io.IOException.class).hasMessageContaining("Symbolic link");
      assertThat(tmp.resolve("child")).doesNotExist();
    }
  }

  @Test void failedCaptureRollsBackItsWritesButPreservesCallerTransaction() throws Exception {
    try (var c = db()) {
      String first = SourceArchive.capture(c, META, List.of(a("first", "good")));
      try (var s = c.createStatement()) {
        s.execute("CREATE TRIGGER reject_file BEFORE INSERT ON source_archive_file WHEN NEW.path='bad'"
            + " BEGIN SELECT RAISE(ABORT,'test failure'); END");
      }
      assertThatThrownBy(() -> SourceArchive.capture(c, META, List.of(a("bad", "never retained"))))
          .isInstanceOf(SQLException.class);
      assertThat(SourceArchive.snapshots(c)).extracting(SourceArchive.StoredSnapshot::id).containsExactly(first);
      assertThat(count(c, "source_archive_blob")).isEqualTo(1);
      c.commit();
      assertThat(SourceArchive.artifacts(c, first)).hasSize(1);
    }
  }

  private static Artifact a(String path, String text) {
    return new Artifact(path, null, text.getBytes(StandardCharsets.UTF_8));
  }
  private static Connection db() throws Exception {
    Connection c = DriverManager.getConnection("jdbc:sqlite::memory:");
    c.setAutoCommit(false);
    SourceArchive.initialize(c);
    return c;
  }
  private static int count(Connection c, String table) throws Exception {
    try (var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM " + table)) { return r.getInt(1); }
  }
}
