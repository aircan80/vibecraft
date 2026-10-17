package dev.wowforever.importer.atlasloot;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.report.ImportReport;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The AtlasLoot Classic Forever fork against the Hoizame pin it replaces (Task 19, pm-data-sources.md): loot
 * membership stays a union, so a Classic boss row the fork dropped is kept while Questie or VMaNGOS still list the
 * pair; additions and removals are counted.
 */
class AtlasLootBaselineTest {
  private static AtlasBoss boss(String name, Long npc, int order, AtlasLoot... loot) {
    return new AtlasBoss(name, npc, null, null, null, false, order, List.of(loot));
  }

  private static AtlasLoot row(int slot, long item) {
    return new AtlasLoot("NORMAL", slot, item);
  }

  private static AtlasInstance instance(String key, Long instanceId, AtlasBoss... bosses) {
    return new AtlasInstance(key, key, null, instanceId, "DUNGEON", 10, 20, List.of(bosses));
  }

  @Test
  void removedRowsStayWhileQuestieOrVmangosListThemAndAdditionsAreCounted() {
    List<AtlasInstance> hoizame = List.of(instance("TheDeadmines", 36L,
        boss("Rhahk'Zor", 644L, 1, row(1, 872), row(2, 5187)),
        boss("Sneed", 643L, 2, row(1, 5194), row(2, 5195)),
        boss("Trash", null, 3, row(1, 1))));
    List<AtlasInstance> fork = List.of(
        instance("TheDeadmines", 36L, boss("Rhahk'Zor", 644L, 1, row(1, 872), row(2, 900001))),
        instance("CityOfDalaran", 2959L, boss("Archmage", 900L, 1, row(1, 900002))));
    // Questie or VMaNGOS list 644 -> 5187 and 643 -> 5194; nobody lists 643 -> 5195
    Set<String> listed = Set.of("644:5187", "643:5194");
    ImportReport report = new ImportReport();

    List<AtlasInstance> merged = AtlasLootBaseline.merge(fork, hoizame, (npc, item) -> listed.contains(npc + ":" + item),
        report);

    AtlasInstance deadmines = merged.getFirst();
    assertThat(deadmines.bosses()).extracting(AtlasBoss::name).containsExactly("Rhahk'Zor", "Sneed");
    assertThat(deadmines.bosses().getFirst().loot()).extracting(AtlasLoot::itemId).containsExactly(872L, 900001L, 5187L);
    AtlasBoss sneed = deadmines.bosses().get(1);
    assertThat(sneed.loot()).extracting(AtlasLoot::itemId).as("only the listed row").containsExactly(5194L);
    assertThat(sneed.sortOrder()).isEqualTo(2);
    assertThat(merged.get(1).key()).isEqualTo("CityOfDalaran");

    assertThat(report.get("atlasloot.forever.removedVsHoizame")).isEqualTo(3);
    assertThat(report.get("atlasloot.forever.removedVsHoizame.kept")).isEqualTo(2);
    assertThat(report.get("atlasloot.forever.removedVsHoizame.dropped")).isEqualTo(1);
    assertThat(report.get("atlasloot.forever.additions")).as("900001 and 900002").isEqualTo(2);
  }

  @Test
  void aForkEqualToTheBaselineChangesNothing() {
    List<AtlasInstance> same = List.of(instance("RagefireChasm", 389L, boss("Taragaman", 11520L, 1, row(1, 14149))));
    ImportReport report = new ImportReport();
    assertThat(AtlasLootBaseline.merge(same, same, (n, i) -> true, report)).isEqualTo(same);
    assertThat(report.get("atlasloot.forever.removedVsHoizame")).isZero();
    assertThat(report.get("atlasloot.forever.additions")).isZero();
  }
}
