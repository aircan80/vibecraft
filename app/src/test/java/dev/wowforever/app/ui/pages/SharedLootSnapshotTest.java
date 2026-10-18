package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SharedLootSnapshotTest {
  private final InstanceSummary instance = new InstanceSummary(27, "Molten Core", "RAID40", 60, 60, 2717L, "Molten Core");
  private LootRow row(long id, Double rate, String source) {
    return new LootRow(new EntityRef(EntityKind.ITEM, id), "Item " + id, null, rate, source, 1);
  }

  @Test void retainsListIdentityAndEachDifficultyWithoutCombiningRatesOrInventingNpcIds() {
    var boss = new BossSummary(1, "Actual boss", 99L, null, null, false);
    var all = new BossSummary(289, "All bosses", null, null, null, true);
    var special = new BossSummary(-1040830853632188594L, "Arcanic Enigma", null, null, null, true);
    var detail = new InstanceDetail(instance, List.of(boss, all, special), List.of("NORMAL", "HEROIC"));
    var queries = new ArrayList<String>();
    var snapshot = SharedLootSnapshot.from(detail, (id, mode) -> {
      queries.add(id + ":" + mode);
      return List.of(row(18264, id == 289 ? 10.0 : null, mode));
    });
    assertThat(snapshot.detail().bosses()).containsExactly(all, special);
    assertThat(queries).containsExactly("289:NORMAL", "289:HEROIC", special.id() + ":NORMAL", special.id() + ":HEROIC");
    assertThat(snapshot.memberships(18264)).hasSize(4);
    assertThat(snapshot.memberships(18264).stream().map(m -> m.list().npcId())).containsOnlyNulls();
    assertThat(snapshot.memberships(18264).stream().map(m -> m.evidence().dropPct())).containsExactly(10.0, 10.0, null, null);
    assertThat(snapshot.memberships(18264).stream().map(SharedLootSnapshot.Membership::difficulty))
        .containsExactly("NORMAL", "HEROIC", "NORMAL", "HEROIC");
    assertThat(snapshot.lookup(1, "NORMAL")).isEmpty();
  }

  @Test void snapshotsMutableInputAndKeepsKnownZeroSeparateFromUnknown() {
    var extra = new BossSummary(22, "Defias Gunpowder", null, null, null, true);
    var rows = new ArrayList<>(List.of(row(5397, 0.0, "atlasloot"), row(8492, null, "continued")));
    var snapshot = SharedLootSnapshot.from(new InstanceDetail(instance, List.of(extra), List.of("NORMAL")), (id, mode) -> rows);
    rows.clear();
    assertThat(snapshot.lookup(22, "NORMAL")).hasSize(2);
    assertThat(snapshot.lookup(22, "NORMAL").get(0).dropPct()).isZero();
    assertThat(snapshot.lookup(22, "NORMAL").get(1).dropPct()).isNull();
    assertThat(new LootFilter(null, 6).apply(snapshot.lookup(22, "NORMAL")).unknown()).isEqualTo(2);
    assertThat(snapshot.lookup(22, "UNKNOWN")).isEmpty();
  }

  @Test void missingExtraListsAreUnknownCoverageRatherThanAnInferredEmptyPool() {
    var snapshot = SharedLootSnapshot.from(new InstanceDetail(instance, List.of()), (id, mode) -> {
      throw new AssertionError("Empty snapshot must not query");
    });
    assertThat(snapshot.lists()).isEmpty();
    assertThat(SharedLootPage.EMPTY).contains("Unknown is not proof");
  }

  @Test void workspaceUsesOnlyExtraListsAndPreservesExistingSelectionState() throws Exception {
    javax.swing.SwingUtilities.invokeAndWait(() -> {
      var detail = new InstanceDetail(instance, List.of(
          new BossSummary(1, "Boss", 9L, null, null, false),
          new BossSummary(289, "All bosses", null, null, null, true),
          new BossSummary(290, "Trash", null, null, null, true, 179564L)), List.of("NORMAL"));
      BossLoot loaded = (id, mode) -> List.of(row(id, null, "atlasloot"));
      var opened = new ArrayList<dev.wowforever.app.nav.ViewTarget>();
      var page = new SharedLootPage(detail, loaded, "70450", opened::add);
      assertThat(page.listPage().encounters().getModel().getSize()).isZero();
      assertThat(page.listPage().extraLists().getModel().getSize()).isEqualTo(2);
      page.listPage().selectBoss(1);
      findButton(page, "Recorded Object #179564 →").doClick();
      assertThat(opened).containsExactly(new dev.wowforever.app.nav.ViewTarget.Entity(new EntityRef(EntityKind.OBJECT, 179564)));
      Object saved = page.saveState();
      var rebuilt = new SharedLootPage(detail, loaded, "70450", target -> {});
      rebuilt.restoreState(saved);
      assertThat(rebuilt.listPage().extraLists().getSelectedValue().id()).isEqualTo(290);
      // Original interface still receives both encounters and extras through the unchanged InstancePage.
      var original = new InstancePage(detail, loaded, target -> {});
      assertThat(original.encounters().getModel().getSize()).isEqualTo(1);
      assertThat(original.extraLists().getModel().getSize()).isEqualTo(2);
    });
  }

  private javax.swing.JButton findButton(java.awt.Container parent, String text) {
    for (java.awt.Component component : parent.getComponents()) {
      if (component instanceof javax.swing.JButton button && text.equals(button.getText())) return button;
      if (component instanceof java.awt.Container nested) {
        var button = findButton(nested, text); if (button != null) return button;
      }
    }
    return null;
  }
}
