package dev.wowforever.app.ui.detail;

import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ZoneDetail;
import dev.wowforever.app.model.ZoneSummary;
import dev.wowforever.app.nav.ViewTarget;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class ZoneDetailPanelTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void zoneTabsListInstances() {
    var p = new ZoneDetailPanel(repos.zones().detail(40).get(), t -> {});
    assertThat(p.tabs().getTitleAt(3)).isEqualTo("Dungeons & Raids (1)");
  }

  @Test
  void headerAndAllTabs() {
    ZoneDetail d = repos.zones().detail(40).get();
    var p = new ZoneDetailPanel(d, t -> {});
    assertThat(p.header().title()).isEqualTo("Westfall");
    assertThat(p.header().subtitle()).startsWith("Zone #40 · ");
    assertThat(p.tabs().getTitleAt(0)).isEqualTo("NPCs (" + d.npcs().size() + ")");
    assertThat(p.tabs().getTitleAt(1)).isEqualTo("Objects (" + d.objects().size() + ")");
    assertThat(p.tabs().getTitleAt(2)).isEqualTo("Quests (" + d.quests().size() + ")");
    assertThat(p.tabs().getTitleAt(4)).isEqualTo("Sub-zones (" + d.subzones().size() + ")");
  }

  @Test
  void parentLinkIsNamedAfterTheParent() {
    List<ViewTarget> opened = new ArrayList<>();
    var p = new ZoneDetailPanel(repos.zones().detail(1581).get(), opened::add);
    assertThat(p.header().links()).singleElement().satisfies(b -> assertThat(b.getText()).isEqualTo("Westfall"));
    p.header().links().get(0).doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(ZONE, 40)));
  }

  @Test
  void unnamedParentGetsAPlaceholder() {
    ZoneDetail d = new ZoneDetail(new ZoneSummary(5, "Sub", "subzone", 77L, null), List.of(), List.of(), List.of(),
        List.of(), List.of());
    var p = new ZoneDetailPanel(d, t -> {});
    assertThat(p.header().links()).singleElement()
        .satisfies(b -> assertThat(b.getText()).isEqualTo("Unknown zone #77"));
  }

  @Test
  void instanceRowOpensTheInstance() {
    List<ViewTarget> opened = new ArrayList<>();
    var p = new ZoneDetailPanel(repos.zones().detail(40).get(), opened::add);
    p.instances().activateRow(0);
    assertThat(opened).singleElement().isInstanceOfSatisfying(ViewTarget.Entity.class,
        e -> assertThat(e.ref().kind()).isEqualTo(INSTANCE));
  }

  @Test
  void mapShownWhenPresent() {
    var p = new ZoneDetailPanel(repos.zones().detail(40).get(), t -> {});
    assertThat(p.map()).hasValueSatisfying(m -> {
      assertThat(m.zoneMap().uiMapId()).isEqualTo(1436);
      assertThat(SwingUtilities.isDescendingFrom(m, p)).isTrue();
    });
  }

  @Test
  void noMapWithoutOne() {
    assertThat(new ZoneDetailPanel(repos.zones().detail(2437).get(), t -> {}).map()).isEmpty();
  }
}
