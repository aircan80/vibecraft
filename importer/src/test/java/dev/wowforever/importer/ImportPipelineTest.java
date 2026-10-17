package dev.wowforever.importer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.clientdata.ClientDataOptions;
import dev.wowforever.importer.clientdata.ClientDataStage;
import dev.wowforever.importer.report.ImportReport;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportPipelineTest {
  private static final PrintStream QUIET = new PrintStream(OutputStream.nullOutputStream());

  @Test
  void endToEndOnFixtures(@TempDir Path tmp) throws Exception {
    ImportOptions o = new ImportOptions(tmp.resolve("out.sqlite"), tmp.resolve("cache"),
        Optional.of(Fixtures.questieDbRoot()), Optional.of(Fixtures.atlasLootRoot()), true,
        ClientDataOptions.skipped());
    ImportReport r;
    PrintStream stdout = System.out;
    ByteArrayOutputStream printed = new ByteArrayOutputStream();
    System.setOut(new PrintStream(printed, true, StandardCharsets.UTF_8));
    try {
      r = ImportPipeline.run(o);
    } finally {
      System.setOut(stdout);
    }
    assertThat(printed.toString(StandardCharsets.UTF_8)).isEqualTo(r.render());
    try (Connection c = open(o.out())) {
      assertThat(scalar(c, "select count(*) from instance")).isEqualTo(3);
      assertThat(scalar(c, "select drop_pct from item_npc_drop where item_id = 2874 and npc_id = 639")).isEqualTo(60.0);
      assertThat(scalar(c, "select origin from item_npc_drop where item_id = 14149 and npc_id = 11520")).isEqualTo("both");
      assertThat(scalar(c, "select count(*) from boss_loot bl join boss b on b.id = bl.boss_id where b.npc_id = 11520")).isEqualTo(3);
      assertThat(scalar(c, "select count(*) from drop_rate_source where npc_id = 639 and item_id = 2874")).isEqualTo(4);
      assertThat(scalar(c, "select source_count || '/' || disagrees from item_npc_drop where item_id = 2874 and npc_id = 639")).isEqualTo("4/1");
      assertThat(scalar(c, "select source_count || '/' || disagrees from item_npc_drop where item_id = 14149 and npc_id = 11520")).isEqualTo("1/0");
      assertThat(scalar(c, "select count(*) from drop_rate_source where npc_id = 11520 and item_id = 14149")).isEqualTo(1);
      assertThat(scalar(c, "select min(source_count || '/' || disagrees) from boss_loot bl join boss b on b.id = bl.boss_id where b.npc_id = 11520 and bl.item_id = 14149")).isEqualTo("1/0");
      assertThat(scalar(c, "select name from zone where id = 1581")).isEqualTo("The Deadmines");
      assertThat(scalar(c, "select value from meta where key = 'questiedb_commit'")).isEqualTo("49e7dbca1f18eb3b8e57ee5d9562ac3e5c9adcc4");
      assertThat(scalar(c, "select count(*) from quest_giver where quest_id = 2 and role = 'start'")).isEqualTo(1);
    }
    assertThat(r.skipsOnUiFields()).isZero();
    assertThat(tmp.resolve("import-report.txt")).exists();
    assertThat(r.get("provenance.pairs")).isPositive();
    assertThat(r.get("provenance.disagreeing")).isPositive();
    assertThat(r.get("provenance.sources.questie-correction")).isPositive();
    assertThat(r.render()).contains("== Conflicts ==").contains("639 2874 questie-correction=60.0");
  }

  @Test
  void writesBuildIdMetaAndRowCounts(@TempDir Path tmp) throws Exception {
    ImportOptions o = new ImportOptions(tmp.resolve("db/out.sqlite"), tmp.resolve("cache"),
        Optional.of(Fixtures.questieDbRoot()), Optional.of(Fixtures.atlasLootRoot()), false,
        ClientDataOptions.skipped());
    ImportReport r = ImportPipeline.run(o, QUIET);
    String buildId;
    try (Connection c = open(o.out())) {
      buildId = (String) scalar(c, "select value from meta where key = 'build_id'");
      assertThat(buildId).matches("[0-9a-f]{64}");
      assertThat(scalar(c, "select value from meta where key = 'importer_version'")).isEqualTo("0.1.0");
      assertThat((String) scalar(c, "select value from meta where key = 'built_at'")).endsWith("Z");
      assertThat(scalar(c, "select count(*) from meta where key = 'atlasloot_commit'")).isEqualTo(1);
      // every spawn zone resolves, including dungeon alt-area ids and placeholders
      assertThat(scalar(c, "select count(*) from npc_spawn s left join zone z on z.id = s.zone_id where z.id is null")).isEqualTo(0);
      assertThat(scalar(c, "select kind from zone where id = 2717")).isEqualTo("raid");
      assertThat(scalar(c, "select zone_id from instance where key = 'TheDeadmines'")).isEqualTo(1581);
      assertThat(r.get("table.item")).isEqualTo((long) (int) scalar(c, "select count(*) from item"));
    }
    assertThat(Files.readString(o.buildIdOut(), StandardCharsets.UTF_8)).isEqualTo(buildId);
    assertThat(o.buildIdOut()).isEqualTo(tmp.resolve("db/build-id.txt"));
    assertThat(r.get("table.npc_spawn")).isPositive();
    assertThat(r.get("table.search_index")).isPositive();
    assertThat(r.get("rows.item")).as("per-table counters are table.<name>, not rows.<name>").isZero();
    assertThat(Files.readString(tmp.resolve("db/import-report.txt"), StandardCharsets.UTF_8)).isEqualTo(r.render());
  }

  @Test
  void everyRunGetsANewBuildIdEqualToTheWrittenMeta(@TempDir Path tmp) throws Exception {
    String first = buildId(tmp.resolve("a"));
    String second = buildId(tmp.resolve("b"));
    assertThat(second).isNotEqualTo(first);
  }

  @Test
  void buildIdFoldsInTheBuildTime() {
    Instant t = Instant.parse("2026-10-16T12:00:00.123Z");
    Map<String, String> a = ImportPipeline.meta("q", "a", t);
    assertThat(a.get("built_at")).isEqualTo("2026-10-16T12:00:00.123Z");
    assertThat(ImportPipeline.meta("q", "a", t).get("build_id")).isEqualTo(a.get("build_id"));
    assertThat(ImportPipeline.meta("q", "a", t.plusMillis(1)).get("build_id")).isNotEqualTo(a.get("build_id"));
  }

  /** Tables the client-data stage and drop-rate provenance fill. */
  private static final List<String> CLIENT_TABLES = List.of("item_stat", "item_damage", "item_armor", "item_effect",
      "item_set", "item_set_item", "item_set_bonus", "item_tooltip_line", "icon", "zone_map", "drop_rate_source",
      "item_alt_value");

  @Test
  void endToEndWithClientDataFixture(@TempDir Path tmp) throws Exception {
    ImportOptions o = new ImportOptions(tmp.resolve("out.sqlite"), tmp.resolve("cache"),
        Optional.of(Fixtures.questieDbRoot()), Optional.of(Fixtures.atlasLootRoot()), true,
        ImportOptions.defaultBuildIdOut(tmp.resolve("out.sqlite")), ClientDataOptions.of(Fixtures.clientDataRoot()), 75);
    ImportReport r = ImportPipeline.run(o, QUIET);
    try (Connection c = open(o.out())) {
      for (String table : CLIENT_TABLES) {
        long rows = ((Number) scalar(c, "select count(*) from " + table)).longValue();
        assertThat(rows).as(table).isPositive();
        assertThat(r.counters()).as("table." + table).containsEntry("table." + table, rows);
      }
      assertThat(strings(c, "select kind from item_tooltip_line where item_id = 17204")).contains("use");
      assertThat(scalar(c, "select ui_map_id from zone where id = 40")).isEqualTo(1436);
      byte[] png = (byte[]) scalar(c, "select png from icon where id = 134124");
      java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
      assertThat(img.getWidth()).isEqualTo(56);
      assertThat(img.getHeight()).isEqualTo(56);
      assertThat(scalar(c, "select value from meta where key = 'client_build'")).isEqualTo("1.60.1.70425");
      assertThat(scalar(c, "select value from meta where key = 'client_fallback_build'")).isEqualTo("1.15.9.70003");
      assertThat(scalar(c, "select value from meta where key = 'client_item_count'"))
          .isEqualTo(String.valueOf(scalar(c, "select count(*) from item where has_client_data = 1")));
      assertThat(((Number) scalar(c, "select count(*) from item where has_client_data = 1")).longValue())
          .as("an ItemSparse row, either build").isEqualTo(r.get("clientdata.items.matched"));
      // Illari's Key: an Item row only, so its icon but no client data
      assertThat(r.get("clientdata.items.iconOnly")).isEqualTo(1);
      assertThat(scalar(c, "select has_client_data from item where id = 212347")).isEqualTo(0);
      assertThat(scalar(c, "select icon_id from item where id = 212347")).isEqualTo(134236);
      assertThat(scalar(c, "select count(*) from icon where id = 134236")).isEqualTo(1);
    }
    long named = r.get("clientdata.items.named");
    assertThat(named).isPositive();
    long namedMatched = r.get("clientdata.items.named.matched");
    assertThat(namedMatched).isPositive().isLessThanOrEqualTo(named);
    assertThat(r.counters()).containsEntry("clientdata.coverage.pct", namedMatched * 100 / named);
    assertThat(r.get("clientdata.coverage.pct")).as("12 of 16 named items").isEqualTo(75);
    assertThat(r.get("clientdata.items.matched")).as("nameless stubs match too").isGreaterThan(namedMatched);
    assertThat(ClientDataStage.coverageTooLow(r, o.coverageMinPct())).isFalse();
    assertThat(ClientDataStage.coverageTooLow(r, ClientDataStage.DEFAULT_MIN_COVERAGE_PCT)).isTrue();
    assertThat(r.get("clientdata.skipped")).isZero();
    assertThat(r.get("clientdata.icons")).isPositive();
    assertThat(r.get("clientdata.maps")).isPositive();
    assertThat(r.counters()).containsKey(ClientDataStage.DISPUTED);
    assertThat(r.notes()).anyMatch(n -> n.startsWith("clientdata.unmatched.ranges: "));
    assertThat(r.skipsOnUiFields()).isZero();
  }

  @Test
  void skipClientDataStillProducesOpenableDb(@TempDir Path tmp) throws Exception {
    ImportOptions o = new ImportOptions(tmp.resolve("out.sqlite"), tmp.resolve("cache"),
        Optional.of(Fixtures.questieDbRoot()), Optional.of(Fixtures.atlasLootRoot()), true,
        ClientDataOptions.skipped());
    ImportReport r = ImportPipeline.run(o, QUIET);
    try (Connection c = open(o.out())) {
      for (String table : CLIENT_TABLES) {
        if (!table.equals("drop_rate_source")) { // provenance comes from Questie and AtlasLoot, not the client
          assertThat(scalar(c, "select count(*) from " + table)).as(table).isEqualTo(0);
          assertThat(r.counters()).as("table." + table).containsEntry("table." + table, 0L);
        }
      }
      assertThat(((Number) scalar(c, "select count(*) from item")).longValue()).isPositive();
      assertThat(scalar(c, "select count(*) from item where has_client_data <> 0")).isEqualTo(0);
      assertThat(scalar(c, "select count(*) from meta where key like 'client%'")).isEqualTo(0);
      assertThat(scalar(c, "select count(*) from zone where ui_map_id is not null")).isEqualTo(0);
    }
    assertThat(r.get("clientdata.skipped")).isEqualTo(1);
    assertThat(r.counters()).doesNotContainKeys("clientdata.items.matched", "clientdata.coverage.pct");
  }

  /** Runs the import into {@code dir}; asserts build-id.txt equals meta.build_id of the written DB. */
  private static String buildId(Path dir) throws Exception {
    ImportOptions o = new ImportOptions(dir.resolve("out.sqlite"), dir.resolve("cache"),
        Optional.of(Fixtures.questieDbRoot()), Optional.of(Fixtures.atlasLootRoot()), false,
        ClientDataOptions.skipped());
    ImportPipeline.run(o, QUIET);
    String txt = Files.readString(o.buildIdOut(), StandardCharsets.UTF_8);
    try (Connection c = open(o.out())) {
      assertThat(scalar(c, "select value from meta where key = 'build_id'")).isEqualTo(txt);
    }
    return txt;
  }

  static Connection open(Path file) throws SQLException {
    return DriverManager.getConnection("jdbc:sqlite:" + file);
  }

  static List<String> strings(Connection c, String sql) throws SQLException {
    List<String> out = new ArrayList<>();
    try (ResultSet rs = c.createStatement().executeQuery(sql)) {
      while (rs.next()) {
        out.add(rs.getString(1));
      }
    }
    return out;
  }

  static Object scalar(Connection c, String sql) throws SQLException {
    try (ResultSet rs = c.createStatement().executeQuery(sql)) {
      assertThat(rs.next()).as("a row for: " + sql).isTrue();
      return rs.getObject(1);
    }
  }
}
