package dev.wowforever.app.ui;

import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.NPC;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.DropSource;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceLoot;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link DropRow#merged}: what merges into an NPC row and what stays a row of its own. */
class DropRowTest {
  private static final EntityRef MC = new EntityRef(INSTANCE, 27);
  private static final EntityRef BWL = new EntityRef(INSTANCE, 28);

  private static DropSource npc(long id, String name, Double pct, boolean disagrees) {
    return new DropSource(new EntityRef(NPC, id), name, 63, 63, "Molten Core", 2717L, pct, "atlasloot", "both",
        disagrees, disagrees ? 2 : 1);
  }

  private static InstanceLoot list(EntityRef instance, String instanceName, long boss, String bossName, Long npc,
      Integer slot, Double pct, boolean disagrees) {
    return new InstanceLoot(instance, instanceName, boss, bossName, npc, slot, pct, disagrees, disagrees ? 2 : 1);
  }

  private static List<String> names(List<DropRow> rows) {
    return rows.stream().map(r -> r instanceof DropRow.Divider d ? d.text() : r.name()).toList();
  }

  @Test
  void aBossListOfTheSameRateMergesIntoItsNpcRow() {
    var rows = DropRow.merged(List.of(npc(11502, "Ragnaros", 12.0, false)),
        List.of(list(MC, "Molten Core", 299, "Ragnaros", 11502L, 7, 12.0, false)));
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).isInstanceOf(DropRow.Source.class);
    assertThat(rows.get(0).loot()).map(InstanceLoot::instanceName).contains("Molten Core");
  }

  @Test
  void aSecondListOfTheSameBossStaysARowOfItsOwn() {
    var rows = DropRow.merged(List.of(npc(11502, "Ragnaros", 12.0, false)),
        List.of(list(MC, "Molten Core", 299, "Ragnaros", 11502L, 7, 12.0, false),
            list(BWL, "Blackwing Lair", 400, "Ragnaros", 11502L, 2, 12.0, false)));
    // same rate and name: by instance, Blackwing Lair's own row before the NPC row Molten Core's list merged into
    assertThat(names(rows)).containsExactly("Ragnaros", "Ragnaros");
    assertThat(rows.get(0)).isInstanceOf(DropRow.Loot.class);
    assertThat(rows.get(0).loot()).map(InstanceLoot::instanceName).contains("Blackwing Lair");
    assertThat(rows.get(1)).isInstanceOf(DropRow.Source.class);
    assertThat(rows.get(1).loot()).map(InstanceLoot::instanceName).contains("Molten Core");
  }

  @Test
  void aListWhoseRateDiffersFromTheNpcsStaysARowOfItsOwn() {
    var rows = DropRow.merged(List.of(npc(11502, "Ragnaros", 12.0, false)),
        List.of(list(MC, "Molten Core", 299, "Ragnaros", 11502L, 7, 20.0, false)));
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0)).isInstanceOf(DropRow.Loot.class);
    assertThat(rows.get(0).pct()).isEqualTo(20.0);
    assertThat(rows.get(1)).isInstanceOf(DropRow.Source.class);
    assertThat(rows.get(1).loot()).as("nothing merged into the NPC row").isEmpty();
  }

  @Test
  void aMergedRowIsMarkedWhenTheListsSourcesDisagree() {
    var rows = DropRow.merged(List.of(npc(11502, "Ragnaros", 12.0, false)),
        List.of(list(MC, "Molten Core", 299, "Ragnaros", 11502L, 7, 12.0, true)));
    var rate = Columns.dropSourcesMerged().get(3);
    assertThat(rate.title()).isEqualTo(Columns.RATE);
    assertThat(rate.disagreesFor(rows.get(0))).isTrue();
    var agreeing = DropRow.merged(List.of(npc(11502, "Ragnaros", 12.0, false)),
        List.of(list(MC, "Molten Core", 299, "Ragnaros", 11502L, 7, 12.0, false)));
    assertThat(rate.disagreesFor(agreeing.get(0))).isFalse();
  }

  @Test
  void listsThatWouldReadTheSameNameTheirSlot() {
    var rows = DropRow.merged(List.of(),
        List.of(list(MC, "Molten Core", 290, "Trash", null, 24, null, false),
            list(MC, "Molten Core", 290, "Trash", null, 3, null, false),
            list(BWL, "Blackwing Lair", 401, "Trash", null, 5, null, false)));
    assertThat(names(rows)).containsExactly("Trash", "Trash · slot 3", "Trash · slot 24");
    assertThat(rows.get(0).loot()).map(InstanceLoot::instanceName).contains("Blackwing Lair");
  }

  @Test
  void ratedRowsLeadAndTheUnknownFollowTheDivider() {
    var rows = DropRow.merged(List.of(npc(1, "Low", 1.0, false), npc(2, "Unknown", null, false)),
        List.of(list(MC, "Molten Core", 290, "Trash", null, 3, 5.0, false)));
    assertThat(names(rows)).containsExactly("Trash", "Low", "Rate unknown (1)", "Unknown");
  }
}
