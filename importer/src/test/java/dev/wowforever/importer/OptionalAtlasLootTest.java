package dev.wowforever.importer;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.importer.clientdata.ClientDataOptions;
import dev.wowforever.importer.source.SourceSpec;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OptionalAtlasLootTest {
  @TempDir Path temporary;
  private PrintStream quiet() { return new PrintStream(OutputStream.nullOutputStream()); }
  private ImportOptions options(Path database) {
    return new ImportOptions(database, temporary.resolve("cache"), Optional.of(Fixtures.questieDbRoot()),
        Optional.of(temporary.resolve("atlas-that-must-never-be-read")), false, ClientDataOptions.skipped());
  }
  @Test void liveSelectionDoesNotEvenRequireAnAtlasSpecificationOrReadableFolder() throws Exception {
    Path database = temporary.resolve("live.sqlite");
    var questie = SourceSpec.load().stream().filter(s -> s.key().equals("questiedb")).findFirst().orElseThrow();
    var report = ImportPipeline.runResolved(options(database), quiet(), List.of(questie),
        temporary.resolve("baseline-that-must-never-be-read"), CoreSourceSelection.QUESTIE_ONLY);
    assertOmitted(database);
    assertThat(report.render()).contains("AtlasLoot was not selected");
    assertThat(Files.exists(temporary.resolve("cache/atlasloot"))).isFalse();
  }
  @Test void archivedSelectionReplaysOfflineWithoutAtlasInputsAndKeepsTheChoice() throws Exception {
    Path database = temporary.resolve("archive.sqlite");
    String ingestion = ArchiveWorkflow.ingest(options(database), CoreSourceSelection.QUESTIE_ONLY);
    assertThat(ArchiveWorkflow.ingest(options(database), CoreSourceSelection.QUESTIE_ONLY)).isEqualTo(ingestion);
    assertThat(scalar(database,"SELECT count(*) FROM source_ingestion_input WHERE role LIKE 'atlasloot%'" )).isEqualTo("0");
    assertThat(scalar(database,"SELECT count(*) FROM source_archive_file WHERE path LIKE '%AtlasLoot%'" )).isEqualTo("0");
    assertThat(ArchiveWorkflow.project(database, ingestion, quiet())).isZero();
    assertOmitted(database);
    assertThat(ArchiveWorkflow.project(database, ingestion, quiet())).isZero();
    assertOmitted(database);
    assertThat(scalar(database,"SELECT count(*) FROM source_projection")).isEqualTo("2");
  }
  @Test void nativeDungeonCoverageDoesNotDependOnAtlasSelection() throws Exception {
    Path database = temporary.resolve("native.sqlite");
    var selected = new ImportOptions(database, temporary.resolve("cache"), Optional.of(Fixtures.questieDbRoot()),
        Optional.of(temporary.resolve("unreadable-atlas")), false, ClientDataOptions.of(Fixtures.clientDataRoot()));
    ImportPipeline.run(selected, quiet(), CoreSourceSelection.QUESTIE_ONLY);
    assertOmitted(database);
    assertThat(Long.parseLong(scalar(database,"SELECT count(*) FROM instance WHERE source='client'"))).isPositive();
    assertThat(scalar(database,"SELECT value FROM meta WHERE key='client_product'")).isEqualTo("wow_classic_beta");
  }
  @Test void contradictoryArchivedRolesRefuseReplayAndPreserveLastProjection() throws Exception {
    Path database = temporary.resolve("guard.sqlite");
    String ingestion = ArchiveWorkflow.ingest(options(database), CoreSourceSelection.QUESTIE_ONLY);
    ArchiveWorkflow.project(database, ingestion, quiet());
    String count = scalar(database,"SELECT count(*) FROM item");
    try (var c=DriverManager.getConnection("jdbc:sqlite:"+database);var s=c.prepareStatement(
        "INSERT INTO source_ingestion_input SELECT ingestion_id,'atlasloot',snapshot_id FROM source_ingestion_input WHERE ingestion_id=? AND role='questiedb'")) {
      s.setString(1,ingestion);s.executeUpdate();
    }
    assertThatThrownBy(() -> ArchiveWorkflow.project(database, ingestion, quiet()))
        .isInstanceOf(java.io.IOException.class).hasMessageContaining("explicit AtlasLoot selection");
    assertThat(scalar(database,"SELECT count(*) FROM item")).isEqualTo(count);
    assertThat(scalar(database,"SELECT count(*) FROM source_projection")).isEqualTo("1");
    assertOmitted(database);
  }
  @Test void selectedAtlasRetainsLegacyLootAndProvenance() throws Exception {
    Path database = temporary.resolve("selected.sqlite");
    var selected = new ImportOptions(database, temporary.resolve("cache"), Optional.of(Fixtures.questieDbRoot()),
        Optional.of(Fixtures.atlasLootRoot()), false, ClientDataOptions.skipped());
    ImportPipeline.run(selected, quiet(), CoreSourceSelection.DEFAULT);
    assertThat(scalar(database,"SELECT value FROM meta WHERE key='atlasloot_selection'")).isEqualTo("selected");
    assertThat(scalar(database,"SELECT count(*) FROM instance WHERE source='atlasloot'")).isEqualTo("3");
    assertThat(Long.parseLong(scalar(database,"SELECT count(*) FROM drop_rate_source WHERE source LIKE 'atlasloot%'"))).isPositive();
  }
  private void assertOmitted(Path database) throws Exception {
    assertThat(scalar(database,"SELECT value FROM meta WHERE key='atlasloot_selection'")).isEqualTo("not-selected");
    assertThat(scalar(database,"SELECT count(*) FROM meta WHERE key LIKE 'atlasloot_%' AND key != 'atlasloot_selection'")).isEqualTo("0");
    assertThat(scalar(database,"SELECT count(*) FROM instance WHERE source='atlasloot'")).isEqualTo("0");
    assertThat(scalar(database,"SELECT count(*) FROM drop_rate_source WHERE source LIKE 'atlasloot%'")).isEqualTo("0");
    assertThat(Long.parseLong(scalar(database,"SELECT count(*) FROM item"))).isPositive();
    assertThat(Long.parseLong(scalar(database,"SELECT count(*) FROM drop_rate_source WHERE source LIKE 'questie%'"))).isPositive();
  }
  private String scalar(Path database, String sql) throws Exception {
    try (var c=DriverManager.getConnection("jdbc:sqlite:"+database);var s=c.createStatement();var r=s.executeQuery(sql)) {
      assertThat(r.next()).isTrue();return r.getString(1);
    }
  }
}
