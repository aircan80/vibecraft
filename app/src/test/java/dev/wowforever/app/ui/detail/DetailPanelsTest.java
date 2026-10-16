package dev.wowforever.app.ui.detail;

import static dev.wowforever.app.model.EntityKind.BOSS;
import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.OBJECT;
import static dev.wowforever.app.model.EntityKind.QUEST;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.ui.pages.InstancePage;
import dev.wowforever.app.ui.pages.ItemPage;
import dev.wowforever.app.ui.pages.NpcPage;
import dev.wowforever.app.ui.pages.ObjectPage;
import dev.wowforever.app.ui.pages.QuestPage;
import dev.wowforever.app.ui.pages.ZonePage;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class DetailPanelsTest {
  private final Repositories repos = FixtureDb.repositories();

  private JComponent render(EntityKind kind, long id) {
    Object detail = DetailPanels.load(new EntityRef(kind, id), repos).orElseThrow();
    return DetailPanels.render(detail, repos, t -> {});
  }

  @Test
  void rendersAPanelForEveryNavigableKind() {
    assertThat(render(ITEM, 14149)).isInstanceOf(ItemPage.class);
    assertThat(render(NPC, 639)).isInstanceOf(NpcPage.class);
    assertThat(render(OBJECT, 386759)).isInstanceOf(ObjectPage.class);
    assertThat(render(QUEST, 2)).isInstanceOf(QuestPage.class);
    assertThat(render(ZONE, 40)).isInstanceOf(ZonePage.class);
    assertThat(render(INSTANCE, 1)).isInstanceOf(InstancePage.class);
  }

  @Test
  void namesTheEntity() {
    assertThat(DetailPanels.name(DetailPanels.load(new EntityRef(ITEM, 14149), repos).orElseThrow()))
        .isEqualTo("Subterranean Cape");
    assertThat(DetailPanels.name(DetailPanels.load(new EntityRef(ZONE, 40), repos).orElseThrow()))
        .isEqualTo("Westfall");
  }

  @Test
  void missingEntitiesAndBossesLoadEmpty() {
    assertThat(DetailPanels.load(new EntityRef(ITEM, -1), repos)).isEmpty();
    assertThat(DetailPanels.load(new EntityRef(BOSS, 1), repos)).isEmpty();
  }

  @Test
  void buildFallsBackToNotFound() {
    JComponent c = DetailPanels.build(new EntityRef(NPC, -7), repos, t -> {});
    assertThat(c).isInstanceOfSatisfying(JLabel.class, l -> assertThat(l.getText()).isEqualTo("Not found: NPC #-7"));
  }
}
