package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.report.ImportReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemSparseLoaderTest {
  /** Item-only in Forever's ItemSparse; all of them have an Era row in the fixture. */
  private static final Set<Long> FELHEART = Set.of(16803L, 16804L, 16805L, 16806L, 16807L, 16808L, 16809L, 16810L);

  @TempDir Path tmp;

  @Test
  void joinsItemAndForeverItemSparse() {
    ImportReport report = new ImportReport();
    Map<Long, ClientItem> items = ItemSparseLoader.load(source(), Set.of(17204L, 25L, 19019L, 2874L), report);

    ClientItem eye = items.get(17204L);
    assertThat(eye.hasSparse()).isTrue();
    assertThat(eye.clientSource()).isEqualTo("forever");
    assertThat(eye.name()).isEqualTo("Eye of Sulfuras");
    assertThat(eye.quality()).isEqualTo(5);
    assertThat(eye.itemLevel()).isEqualTo(60);
    assertThat(eye.iconId()).isEqualTo(134124L);
    assertThat(eye.itemSetId()).isNull(); // ItemSet 0
    assertThat(eye.classId()).isEqualTo(7);
    assertThat(eye.subclassId()).isEqualTo(11);
    assertThat(eye.sellPrice()).isEqualTo(200_000L);
    assertThat(eye.buyPrice()).isEqualTo(800_000L);
    assertThat(eye.flags()).containsExactly(1056, 28676, 0, 0, 0);
    assertThat(eye.statShares()).isEmpty();
    assertThat(eye.delayMs()).isNull(); // ItemDelay 0

    ClientItem sword = items.get(25L);
    assertThat(sword.hasSparse()).isTrue();
    assertThat(sword.name()).isEqualTo("Worn Shortsword");
    assertThat(sword.clientSource()).as("Forever wins over the Era row").isEqualTo("forever");
    assertThat(sword.delayMs()).isEqualTo(1900);
    assertThat(sword.dmgVariance()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(1e-6));
    assertThat(sword.inventoryType()).isEqualTo(21);
    assertThat(sword.maxStack()).isEqualTo(1);
    assertThat(sword.allowableClass()).isEqualTo(-1);
    assertThat(sword.allowableRace()).isEqualTo(-1L);

    assertThat(items.get(19019L).statShares()).containsExactly(new ClientItem.StatShare(3, 2174),
        new ClientItem.StatShare(7, 3478), new ClientItem.StatShare(51, 3478), new ClientItem.StatShare(55, 3913));

    ClientItem letter = items.get(2874L);
    assertThat(letter.startQuestId()).isEqualTo(373L);
    assertThat(letter.description()).isEqualTo("A letter found on Edwin VanCleef's person.");
    assertThat(letter.bonding()).isEqualTo(1);
    assertThat(eye.description()).as("empty Description_lang").isNull();

    assertThat(report.get("clientdata.items.matched")).isEqualTo(4);
    assertThat(report.get("clientdata.items.matched.forever")).isEqualTo(4);
    assertThat(report.get("clientdata.items.matched.era")).isZero();
  }

  @Test
  void stackSizeComesFromStackableNotTheUniqueCount() {
    Map<Long, ClientItem> items = ItemSparseLoader.load(source(), Set.of(117L), new ImportReport());
    assertThat(items.get(117L).maxStack()).isEqualTo(20); // Tough Jerky: Stackable 20, MaxCount 0
  }

  @Test
  void eraRowsFillItemsMissingFromForeversItemSparse() {
    ImportReport report = new ImportReport();
    Map<Long, ClientItem> items = ItemSparseLoader.load(source(), Set.of(16800L, 16803L, 17204L), report);

    ClientItem boots = items.get(16800L);
    assertThat(boots.hasSparse()).isTrue();
    assertThat(boots.clientSource()).isEqualTo("era");
    assertThat(boots.name()).isEqualTo("Arcanist Boots");
    assertThat(boots.quality()).isEqualTo(4);
    assertThat(boots.itemLevel()).isEqualTo(66);
    assertThat(boots.itemSetId()).as("Arcanist Raiment in the Era data").isEqualTo(201L);
    assertThat(boots.iconId()).as("icon from Forever's Item row").isEqualTo(132541L);
    assertThat(boots.classId()).isEqualTo(4);
    assertThat(items.get(16803L).itemSetId()).as("Felheart Slippers").isEqualTo(203L);

    assertThat(report.get("clientdata.items.matched")).isEqualTo(3);
    assertThat(report.get("clientdata.items.matched.forever")).isEqualTo(1);
    assertThat(report.get("clientdata.items.matched.era")).isEqualTo(2);
    assertThat(report.get("clientdata.items.iconOnly")).isZero();
  }

  @Test
  void countsIconOnlyAndUnmatchedItemsAndNotesUnmatchedRanges() {
    ImportReport report = new ImportReport();
    Set<Long> ids = new java.util.HashSet<>(Set.of(17204L, 16800L, 190179L, 999_999L, 995_000L, 12L));
    Map<Long, ClientItem> items = ItemSparseLoader.load(source(), ids, report);

    assertThat(items).doesNotContainKeys(999_999L, 995_000L, 12L);
    assertThat(items.get(190179L).hasSparse()).as("Avelina's Heart is in this client").isTrue();
    assertThat(report.get("clientdata.items.matched")).isEqualTo(3);
    assertThat(report.get("clientdata.items.unmatched")).isEqualTo(3);
    assertThat(report.counters()).containsKey("clientdata.items.iconOnly");
    assertThat(report.notes()).contains("clientdata.unmatched.ranges: 0-9999 (1), 990000-999999 (2)");
  }

  @Test
  void withoutTheEraTableItemOnlyRowsKeepClassAndIcon() throws IOException {
    Path dir = copyTables("Item", "ItemSparse");
    ImportReport report = new ImportReport();
    Map<Long, ClientItem> items = ItemSparseLoader.load(new ClientDataSource(dir), Set.of(16800L, 17204L), report);

    ClientItem boots = items.get(16800L);
    assertThat(boots.hasSparse()).isFalse();
    assertThat(boots.clientSource()).isNull();
    assertThat(boots.iconId()).isEqualTo(132541L);
    assertThat(boots.classId()).isEqualTo(4);
    assertThat(boots.subclassId()).isEqualTo(1);
    assertThat(boots.quality()).isNull();
    assertThat(boots.name()).isNull();
    assertThat(boots.flags()).isEmpty();
    assertThat(report.get("clientdata.items.iconOnly")).isEqualTo(1);
    assertThat(report.get("clientdata.items.matched")).isEqualTo(1);
    assertThat(report.get("clientdata.items.unmatched")).isZero();
    assertThat(report.notes()).contains("clientdata.unmatched.ranges: none");
  }

  @Test
  void aMissingColumnFailsNamingTableAndColumn() throws IOException {
    Path dir = copyTables("Item", "ItemSparse");
    Path sparse = dir.resolve("csv/ItemSparse.csv");
    List<String> lines = Files.readAllLines(sparse);
    Files.write(sparse, List.of(lines.get(0).replace("OverallQualityID", "Quality")));
    assertThatThrownBy(() -> ItemSparseLoader.load(new ClientDataSource(dir), Set.of(1L), new ImportReport()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ItemSparse")
        .hasMessageContaining("OverallQualityID");
  }

  @Test
  void aMissingColumnInTheEraTableFailsNamingIt() throws IOException {
    Path dir = copyTables("Item", "ItemSparse");
    Files.writeString(dir.resolve("csv/ItemSparse.era.csv"), "ID,Display_lang\n16800,Arcanist Boots\n");
    assertThatThrownBy(() -> ItemSparseLoader.load(new ClientDataSource(dir), Set.of(16800L), new ImportReport()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ItemSparse.era")
        .hasMessageContaining("OverallQualityID");
  }

  private static ClientDataSource source() {
    return new ClientDataSource(Fixtures.clientDataRoot());
  }

  private Path copyTables(String... names) throws IOException {
    Path csv = Files.createDirectories(tmp.resolve("cd/csv"));
    for (String n : names) {
      Files.copy(Fixtures.clientDataRoot().resolve("csv/" + n + ".csv"), csv.resolve(n + ".csv"));
    }
    return tmp.resolve("cd");
  }

  @Test
  void felheartPiecesAllComeFromTheEraTable() {
    ImportReport report = new ImportReport();
    Map<Long, ClientItem> items = ItemSparseLoader.load(source(), FELHEART, report);
    assertThat(items.values()).allSatisfy(i -> {
      assertThat(i.clientSource()).isEqualTo("era");
      assertThat(i.itemSetId()).isEqualTo(203L);
    });
    assertThat(report.get("clientdata.items.matched.era")).isEqualTo(8);
  }
}
