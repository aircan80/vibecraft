package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wowforever.importer.report.ImportReport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The hotfix overlay (Task 19, pm-data-sources.md): precedence 1 hotfix, 2 Forever, 3 Era. */
class HotfixOverlayTest {
  @TempDir Path tmp;

  @Test
  void hotfixRowsWinOverForeverAndEraAndFillItemsWithoutClientData() {
    ClientDataSource src = new ClientDataSource(HotfixFixture.standard(tmp).dir());
    ImportReport report = new ImportReport();
    Map<Long, ClientItem> items = ItemSparseLoader.load(src, Set.of(25L, 16805L, 16806L, 212347L, 117L), report);

    ClientItem sword = items.get(25L);
    assertThat(sword.clientSource()).isEqualTo("hotfix");
    assertThat(sword.name()).isEqualTo("Worn Shortsword (hotfix)");
    assertThat(sword.itemLevel()).isEqualTo(7);
    assertThat(sword.iconId()).as("the Item overlay wins too").isEqualTo(135000L);
    assertThat(sword.absolute()).as("hotfix rows are Forever rows: budget shares").isNull();

    ClientItem gloves = items.get(16805L);
    assertThat(gloves.clientSource()).as("was Era").isEqualTo("hotfix");
    assertThat(gloves.itemSetId()).isEqualTo(203L);
    assertThat(gloves.quality()).isEqualTo(4);
    assertThat(gloves.statShares()).containsExactly(new ClientItem.StatShare(7, 5000));
    assertThat(gloves.iconId()).as("no Item overlay row: Forever's Item row").isEqualTo(132953L);

    assertThat(items.get(16806L).clientSource()).as("no hotfix row: stays Era").isEqualTo("era");
    assertThat(items.get(117L).clientSource()).as("no hotfix row: stays Forever").isEqualTo("forever");

    ClientItem key = items.get(212347L);
    assertThat(key.hasSparse()).as("was icon only").isTrue();
    assertThat(key.clientSource()).isEqualTo("hotfix");
    assertThat(key.name()).isEqualTo("Illari's Key");

    assertThat(report.get("clientdata.items.matched")).isEqualTo(5);
    assertThat(report.get("clientdata.items.matched.hotfix")).isEqualTo(3);
    assertThat(report.get("clientdata.items.matched.forever")).isEqualTo(1);
    assertThat(report.get("clientdata.items.matched.era")).isEqualTo(1);
    assertThat(report.get("clientdata.items.iconOnly")).isZero();
    assertThat(report.get("hotfix.itemsparse.valid")).as("hotfix.json's count").isEqualTo(5);
    assertThat(report.get("hotfix.itemsparse.applied")).isEqualTo(3);
    assertThat(report.get("hotfix.itemsparse.invalidated")).isEqualTo(38);
    assertThat(report.get("hotfix.itemsparse.notInItemTable")).as("999999 is not a Questie item").isEqualTo(1);
    assertThat(report.get("hotfix.fromForever")).isEqualTo(1);
    assertThat(report.get("hotfix.fromEra")).isEqualTo(1);
    assertThat(report.get("hotfix.fromNoClient")).isEqualTo(1);
    assertThat(report.get("hotfix.item.applied")).isEqualTo(1);
  }

  @Test
  void withoutAnOverlayNothingChangesAndTheCountersReadZero() {
    ImportReport report = new ImportReport();
    Map<Long, ClientItem> items = ItemSparseLoader.load(new ClientDataSource(HotfixFixture.copy(tmp).dir()),
        Set.of(25L, 16805L), report);
    assertThat(items.get(25L).clientSource()).isEqualTo("forever");
    assertThat(items.get(16805L).clientSource()).isEqualTo("era");
    assertThat(report.get("clientdata.items.matched.hotfix")).isZero();
    assertThat(report.get("hotfix.itemsparse.applied")).isZero();
    assertThat(new ClientDataSource(HotfixFixture.copy(tmp.resolve("b")).dir()).hotfix()).isEmpty();
  }

  @Test
  void theSourceReadsTheSnapshotItWasConvertedFrom() {
    HotfixInfo info = new ClientDataSource(HotfixFixture.standard(tmp).dir()).hotfix().orElseThrow();
    assertThat(info.headerBuild()).isEqualTo(70425);
    assertThat(info.sha256()).isEqualTo(HotfixFixture.SHA);
    assertThat(info.lastModified()).isEqualTo(HotfixFixture.LAST_MODIFIED);
    assertThat(info.cacheDate()).isEqualTo("17 Oct 2026");
    assertThat(info.footer()).isEqualTo("Forever stats revealed by a Blizzard hotfix · beta 70425 · cache 17 Oct 2026");
    assertThat(info.footer()).doesNotContainIgnoringCase("live").doesNotContainIgnoringCase("confirmed");
  }

  @Test
  void aCacheOfAnotherBuildFailsWithTheBuildWatchText() {
    Path dir = HotfixFixture.copy(tmp).row("ItemSparse", 25, Map.of()).headerBuild(70450).lockHeaderBuild(70450)
        .write().dir();
    assertThatThrownBy(() -> new ClientDataSource(dir).lock())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("hotfix cache header build 70450 differs from build.lock build 1.60.1.70425")
        .hasMessageContaining("tools/build_watch.py");
    // the import stops at the stage's first step
    assertThatThrownBy(() -> ClientDataStage.run(ClientDataOptions.of(dir), Map.of(), Map.of(),
        dev.wowforever.importer.Fixtures.questieDbRoot(), new ImportReport()))
        .hasMessageContaining("differs from build.lock build").hasMessageContaining("tools/build_watch.py");
  }

  @Test
  void aStaleOverlayFails() throws IOException {
    // converted from another snapshot than the lock records
    Path other = HotfixFixture.copy(tmp.resolve("a")).row("ItemSparse", 25, Map.of()).lockSha("cd".repeat(32))
        .write().dir();
    assertThatThrownBy(() -> new ClientDataSource(other).lock())
        .hasMessageContaining("hotfix.json was converted from snapshot abab")
        .hasMessageContaining("convert.py");

    // an overlay CSV edited after the conversion
    Path edited = HotfixFixture.standard(tmp.resolve("b")).dir();
    Files.writeString(edited.resolve("csv/Item.hotfix.csv"), "\n", StandardCharsets.UTF_8,
        java.nio.file.StandardOpenOption.APPEND);
    assertThatThrownBy(() -> new ClientDataSource(edited).lock())
        .hasMessageContaining("Item.hotfix.csv does not match hotfix.json");

    // overlay CSVs without hotfix.json
    Path orphan = HotfixFixture.standard(tmp.resolve("c")).dir();
    Files.delete(orphan.resolve("hotfix.json"));
    assertThatThrownBy(() -> new ClientDataSource(orphan).lock())
        .hasMessageContaining(".hotfix.csv present but hotfix.json is missing");
  }

  @Test
  void hotfixItemsNeverTakeEraEffectsAndHotfixEffectRowsWin() {
    ClientDataSource src = new ClientDataSource(HotfixFixture.standard(tmp).dir());
    ImportReport report = new ImportReport();
    Map<Long, ClientItem> items = ItemSparseLoader.load(src, Set.of(16805L, 16806L, 117L), new ImportReport());
    Map<Long, List<ItemSpellEffect>> effects = ItemEffectLoader.load(src, items,
        new SpellDescriptionFormatter(SpellTables.load(src)), report);

    // a hotfix row is a Forever row: Forever moved the Era Equip bonuses into stats, so Era effects would repeat them
    assertThat(effects.get(16805L)).as("no Era effects on a hotfix item").isNull();
    assertThat(effects.get(16806L)).as("an Era item keeps its Era effects").isNotEmpty();
    assertThat(report.get("clientdata.era.effectsSkippedOnHotfixItems")).isEqualTo(1);
    assertThat(report.get("clientdata.era.effectRowsSkippedOnHotfixItems")).isPositive();
    assertThat(report.get("clientdata.era.effectsOnHotfixItems")).as("the old counter is gone").isZero();
    assertThat(effects.get(117L).getFirst().charges()).as("ItemEffect overlay row 98985").isEqualTo(-5);
    assertThat(report.get("hotfix.itemeffect.applied")).isEqualTo(1);
  }

  @Test
  void skippedSnapshotsAndRemovalsReachTheReport() {
    Path dir = HotfixFixture.copy(tmp).row("ItemSparse", 25, Map.of()).snapshotsSkipped(1).removed(3).write().dir();
    ImportReport report = new ImportReport();
    ItemSparseLoader.load(new ClientDataSource(dir), Set.of(25L), report);
    assertThat(report.get("hotfix.snapshotsSkipped")).isEqualTo(1);
    assertThat(report.get("hotfix.itemsparse.removed")).isEqualTo(3);
    assertThat(report.notes()).anyMatch(n -> n.startsWith("hotfix.snapshotsSkipped: 1 snapshot(s)")
        && n.contains("dddddddddddd"));

    ImportReport none = new ImportReport();
    ItemSparseLoader.load(new ClientDataSource(HotfixFixture.standard(tmp.resolve("b")).dir()), Set.of(25L), none);
    assertThat(none.get("hotfix.snapshotsSkipped")).isZero();
    assertThat(none.notes()).noneMatch(n -> n.startsWith("hotfix.snapshotsSkipped"));
  }

  @Test
  void footersPerSource() {
    ClientDataSource src = new ClientDataSource(HotfixFixture.standard(tmp).dir());
    Map<Long, ClientItem> items = ItemSparseLoader.load(src, Set.of(25L, 16806L, 117L), new ImportReport());
    ItemTooltips tooltips = ItemTooltips.build(src, items,
        ItemValues.compute(items, ScalingTables.load(src), new ImportReport()), Map.of(), new ImportReport());

    assertThat(note(tooltips.lines().get(25L).getLast()))
        .isEqualTo("source_note|Forever stats revealed by a Blizzard hotfix · beta 70425 · cache 17 Oct 2026|"
            + TooltipLine.MUTED);
    assertThat(note(tooltips.lines().get(16806L).getLast()))
        .isEqualTo("source_note|Classic Era values. Forever stats are still hidden.|" + TooltipLine.MUTED);
    assertThat(tooltips.lines().get(117L)).noneMatch(l -> l.kind().equals("source_note"));
  }

  private static String note(TooltipLine l) {
    return l.kind() + "|" + l.text() + "|" + l.colour();
  }
}
