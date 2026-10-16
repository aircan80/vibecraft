package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.Fixtures;
import dev.wowforever.importer.report.ImportReport;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Test helpers: fixture client items and synthetic Forever items. */
final class ClientItems {
  private ClientItems() {}

  static ClientDataSource fixture() {
    return new ClientDataSource(Fixtures.clientDataRoot());
  }

  static ScalingTables fixtureTables() {
    return ScalingTables.load(fixture());
  }

  /** Fixture items by id, through the real loader. */
  static Map<Long, ClientItem> load(long... ids) {
    Set<Long> set = Arrays.stream(ids).boxed().collect(Collectors.toSet());
    return ItemSparseLoader.load(fixture(), set, new ImportReport());
  }

  static ClientItem one(long id) {
    return load(id).get(id);
  }

  /** A Forever-sourced item with the given scaling inputs; flags all 0 unless given. */
  static ClientItem forever(int classId, int subclassId, int inventoryType, int quality, int itemLevel, Integer delayMs,
      Double variance, List<Integer> flags, ClientItem.StatShare... shares) {
    return new ClientItem(1, true, quality, itemLevel, 1, classId, subclassId, inventoryType, 0, 0L, 0L, 1, null,
        null, "Synthetic", null, -1, -1L, null, null, flags == null ? List.of(0, 0, 0, 0, 0) : flags, null,
        List.of(shares), delayMs, variance, 0, ClientItem.FOREVER, null, null);
  }

  static ClientItem.StatShare share(int statId, int share) {
    return new ClientItem.StatShare(statId, share);
  }
}
