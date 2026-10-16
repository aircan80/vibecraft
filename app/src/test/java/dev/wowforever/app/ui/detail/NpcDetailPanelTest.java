package dev.wowforever.app.ui.detail;

import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.NpcDetail;
import dev.wowforever.app.nav.ViewTarget;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class NpcDetailPanelTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void headerShowsLevelsAndRank() {
    var p = new NpcDetailPanel(repos.npcs().detail(639).get(), t -> {});
    assertThat(p.header().subtitle()).contains("NPC #639").contains("Level 21");
  }

  @Test
  void zoneLinkInHeaderOpensTheZone() {
    NpcDetail d = repos.npcs().detail(448).get();
    List<ViewTarget> opened = new ArrayList<>();
    var p = new NpcDetailPanel(d, opened::add);
    assertThat(d.summary().zoneId()).isNotNull();
    assertThat(p.header().subtitle()).endsWith(" · " + d.summary().zoneName());
    p.header().links().get(0).doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(ZONE, d.summary().zoneId())));
  }

  @Test
  void tabsCarryCountsAndSpawnsLinkToZones() {
    NpcDetail d = repos.npcs().detail(448).get();
    List<ViewTarget> opened = new ArrayList<>();
    var p = new NpcDetailPanel(d, opened::add);
    assertThat(p.tabs().getTitleAt(0)).isEqualTo("Drops (" + d.drops().size() + ")");
    assertThat(p.tabs().getTitleAt(1)).isEqualTo("Sells (" + d.sells().size() + ")");
    assertThat(p.tabs().getTitleAt(2)).isEqualTo("Starts quests (" + d.starts().size() + ")");
    assertThat(p.tabs().getTitleAt(3)).isEqualTo("Ends quests (" + d.ends().size() + ")");
    assertThat(p.tabs().getTitleAt(4)).isEqualTo("Spawns (" + d.spawns().size() + ")");
    p.spawns().activateRow(0);
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(ZONE, d.spawns().get(0).zoneId())));
  }
}
