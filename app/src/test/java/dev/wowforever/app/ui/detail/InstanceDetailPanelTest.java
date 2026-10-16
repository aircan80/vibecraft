package dev.wowforever.app.ui.detail;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceDetail;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.nav.ViewTarget;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class InstanceDetailPanelTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void selectingBossLoadsLoot() {
    var p = new InstanceDetailPanel(repos.instances().detail(1).get(), repos, t -> {});
    p.selectBoss(0);
    assertThat(p.lootPanel().rowCount()).isEqualTo(3);
  }

  @Test
  void firstBossIsSelectedOnConstruction() {
    InstanceDetail d = repos.instances().detail(1).get();
    var p = new InstanceDetailPanel(d, repos, t -> {});
    assertThat(p.lootPanel().rowCount())
        .isEqualTo(repos.instances().bossLoot(d.bosses().get(0).id()).size());
    assertThat(p.header().title()).isEqualTo("Ragefire Chasm");
  }

  @Test
  void selectingAnotherBossReplacesTheLoot() {
    InstanceDetail d = repos.instances().detail(1).get();
    var p = new InstanceDetailPanel(d, repos, t -> {});
    BossLootPanel first = p.lootPanel();
    assertThat(d.bosses()).hasSizeGreaterThan(1);
    p.selectBoss(1);
    assertThat(p.lootPanel()).isNotSameAs(first);
    assertThat(p.lootPanel().rowCount()).isEqualTo(repos.instances().bossLoot(d.bosses().get(1).id()).size());
  }

  @Test
  void lootRowsLinkToItems() {
    List<ViewTarget> opened = new ArrayList<>();
    InstanceDetail d = repos.instances().detail(1).get();
    var p = new InstanceDetailPanel(d, repos, opened::add);
    p.lootPanel().table().activateRow(0);
    assertThat(opened).singleElement().isInstanceOfSatisfying(ViewTarget.Entity.class,
        e -> assertThat(e.ref().kind()).isEqualTo(ITEM));
  }

  @Test
  void headerLinksTheInstanceZone() {
    List<ViewTarget> opened = new ArrayList<>();
    var p = new InstanceDetailPanel(repos.instances().detail(1).get(), repos, opened::add);
    assertThat(p.header().subtitle()).isEqualTo("Instance #1 · Dungeon · Level 10–13 · Ragefire Chasm");
    assertThat(p.header().links()).hasSize(1);
    p.header().links().get(0).doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(ZONE, 2437)));
  }

  @Test
  void headerWithoutZoneHasNoLinkAndUnnamedZoneHasAPlaceholder() {
    var none = new InstanceDetailPanel(new InstanceDetail(
        new InstanceSummary(9, "X", "RAID40", 60, 60, null, null), List.of()), repos, t -> {});
    assertThat(none.header().subtitle()).isEqualTo("Instance #9 · Raid (40) · Level 60");
    assertThat(none.header().links()).isEmpty();
    var unnamed = new InstanceDetailPanel(new InstanceDetail(
        new InstanceSummary(9, "X", "DUNGEON", null, null, 77L, null), List.of()), repos, t -> {});
    assertThat(unnamed.header().subtitle()).isEqualTo("Instance #9 · Dungeon · Unknown zone #77");
  }

  @Test
  void bossListShowsLevelAndMarkers() {
    InstanceDetail d = repos.instances().detail(1).get();
    var b = d.bosses().get(0);
    String label = InstanceDetailPanel.bossLabel(b);
    assertThat(label).startsWith(b.displayName());
    assertThat(b.level()).isNotNull();
    assertThat(label).contains("(Lv " + b.level() + ")");
  }
}
