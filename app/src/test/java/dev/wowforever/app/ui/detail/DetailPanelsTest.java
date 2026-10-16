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
import dev.wowforever.app.model.QuestDetail;
import dev.wowforever.app.ui.pages.ItemPage;
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
    assertThat(render(NPC, 639)).isInstanceOf(NpcDetailPanel.class);
    assertThat(render(OBJECT, 386759)).isInstanceOf(ObjectDetailPanel.class);
    assertThat(render(QUEST, 2)).isInstanceOf(QuestDetailPanel.class);
    assertThat(render(ZONE, 40)).isInstanceOf(ZoneDetailPanel.class);
    assertThat(render(INSTANCE, 1)).isInstanceOf(InstanceDetailPanel.class);
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

  @Test
  void questPanelSplitsGiversAndShowsObjectives() {
    QuestDetail d = repos.quests().detail(2).get();
    var p = new QuestDetailPanel(d, t -> {});
    long starts = d.givers().stream().filter(g -> g.role().equals("start")).count();
    long ends = d.givers().stream().filter(g -> g.role().equals("end")).count();
    assertThat(p.tabs().getTitleAt(0)).isEqualTo("Given by (" + starts + ")");
    assertThat(p.tabs().getTitleAt(1)).isEqualTo("Turned in to (" + ends + ")");
    assertThat(p.tabs().getTitleAt(2)).isEqualTo("Rewards (" + d.rewards().size() + ")");
    assertThat(p.tabs().getTitleAt(3)).isEqualTo("Related items (" + d.relatedItems().size() + ")");
    assertThat(p.header().subtitle()).startsWith("Quest #2");
    assertThat(p.objectives().isEditable()).isFalse();
    assertThat(p.objectives().getLineWrap()).isTrue();
  }

  @Test
  void objectPanelTabs() {
    var d = repos.objects().detail(386759).get();
    var p = new ObjectDetailPanel(d, t -> {});
    assertThat(p.tabs().getTitleAt(0)).isEqualTo("Contains (" + d.contains().size() + ")");
    assertThat(p.tabs().getTitleAt(1)).isEqualTo("Starts quests (" + d.starts().size() + ")");
    assertThat(p.tabs().getTitleAt(2)).isEqualTo("Ends quests (" + d.ends().size() + ")");
    assertThat(p.tabs().getTitleAt(3)).isEqualTo("Spawns (" + d.spawns().size() + ")");
    assertThat(p.header().subtitle()).startsWith("Object #386759");
  }
}
