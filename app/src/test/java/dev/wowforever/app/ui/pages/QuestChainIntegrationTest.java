package dev.wowforever.app.ui.pages;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.pages.quest.ChainRows;
import dev.wowforever.app.ui.pages.quest.QuestTestData;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class QuestChainIntegrationTest {
  @Test
  void pageContainsTheRealChainAndNavigatesItsSpine() {
    var loaded = QuestTestData.load(166);
    List<ViewTarget> opened = new ArrayList<>();
    var page = new QuestPage(loaded.data(), loaded.maps(), opened::add, text -> {});
    assertThat(page.chainView().rows()).anySatisfy(r -> {
      assertThat(r.kind()).isEqualTo(ChainRows.Kind.CURRENT);
      assertThat(r.step().questId()).isEqualTo(166);
    });
    page.chainView().previous();
    page.chainView().next();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(EntityKind.QUEST, 155)));
  }

  @Test
  void compactHasOneChainDisclosureAndNoStandaloneHeading() {
    JPanel host = new JPanel(new BorderLayout());
    host.putClientProperty(CompactColumns.KEY, true);
    var standalone = QuestTestData.load(176);
    var page = new QuestPage(standalone.data(), standalone.maps(), t -> {}, text -> {});
    host.add(page);
    page.chainView().doLayout();
    page.doLayout();
    assertThat(page.section("Quest chain").isVisible()).isFalse();
    host.remove(page);
    var linear = QuestTestData.load(166);
    page = new QuestPage(linear.data(), linear.maps(), t -> {}, text -> {});
    host.add(page);
    page.chainView().doLayout();
    page.doLayout();
    assertThat(page.section("Quest chain").heading().isVisible()).isFalse();
    assertThat(page.chainView().heading().isVisible()).isTrue();
    assertThat(page.chainView().isCollapsed()).isTrue();
    page.chainView().heading().doClick();
    assertThat(page.chainView().isCollapsed()).isFalse();
  }
}
