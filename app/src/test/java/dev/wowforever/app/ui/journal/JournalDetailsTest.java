package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.model.*;
import dev.wowforever.app.ui.*;
import dev.wowforever.app.ui.detail.DetailPanels;
import dev.wowforever.app.ui.pages.InstancePage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class JournalDetailsTest {
  @Test void dungeonSectionsKeepEncounterAndTabStateWithoutRepositoryAccess() throws Exception {
    var detail=new InstanceDetail(new InstanceSummary(99,"Test dungeon","DUNGEON",20,25,null,null),
        List.of(new BossSummary(1,"First",null,null,null,false),new BossSummary(2,"Second",null,null,null,false)),List.of("NORMAL"));
    var loaded=new DetailPanels.LoadedInstance(detail,List.of(),"fixture",(boss,mode)->List.of());
    SwingUtilities.invokeAndWait(() -> {
      var first=render(loaded);
      assertThat(first.tabs().getTabCount()).isEqualTo(4);
      assertThat(first.tabs().getTitleAt(1)).isEqualTo("Bosses & loot");
      first.tabs().setSelectedIndex(1);
      ((InstancePage)first.tabs().getComponentAt(1)).selectBoss(1);
      Object saved=first.saveState();
      var restored=render(loaded);restored.restoreState(saved);
      assertThat(restored.tabs().getSelectedIndex()).isEqualTo(1);
      assertThat(((InstancePage)restored.tabs().getComponentAt(1)).encounters().getSelectedIndex()).isEqualTo(1);
    });
  }

  @Test void classicDungeonUsesTheSamePreloadedRealBossLoot() throws Exception {
    var loaded=(DetailPanels.LoadedInstance)DetailPanels.load(new EntityRef(EntityKind.INSTANCE,2),FixtureDb.repositories()).orElseThrow();
    assertThat(loaded.clientLoot()).isNotNull();
    SwingUtilities.invokeAndWait(() -> {
      var page=render(loaded);
      var encounters=(InstancePage)page.tabs().getComponentAt(1);
      assertThat(encounters.lootTable()).isNotNull();
      assertThat(encounters.lootTable().table().getRowCount()).isGreaterThan(0);
    });
  }

  @Test void itemMovesEvidenceToItsOwnTabAndKeepsAcquisitionAvailable() throws Exception {
    var detail=FixtureDb.repositories().items().detail(19019).orElseThrow();
    var loaded=new DetailPanels.LoadedItem(detail,null,Map.of(),Set.of());
    SwingUtilities.invokeAndWait(() -> {
      var page=render(loaded);
      assertThat(page.tabs().getTitleAt(0)).isEqualTo("Item & acquisition");
      assertThat(page.tabs().getTitleAt(1)).isEqualTo("Source evidence");
      page.tabs().setSelectedIndex(1);
      var restored=render(loaded);restored.restoreState(page.saveState());
      assertThat(restored.tabs().getSelectedIndex()).isEqualTo(1);
    });
  }

  @Test void floorDecodingIsDeferredToWorkerAndFailuresStayVisible() throws Exception {
    var jobs=new java.util.ArrayList<Runnable>();
    var failures=new java.util.ArrayList<Exception>();
    var panel=new java.util.concurrent.atomic.AtomicReference<JournalAtlasMaps>();
    SwingUtilities.invokeAndWait(() -> panel.set(new JournalAtlasMaps(
        List.of(new InstanceFloorMap("main","Main floor",new byte[]{1,2,3},true,List.of())),
        t->{},jobs::add,new StatusSink(){
          public void message(String text){}
          public void fail(Exception e){assertThat(SwingUtilities.isEventDispatchThread()).isTrue();failures.add(e);}
        })));
    assertThat(jobs).hasSize(1);
    assertThat(failures).isEmpty();
    Thread worker=new Thread(jobs.getFirst());worker.start();worker.join();
    SwingUtilities.invokeAndWait(() -> {
      assertThat(failures).hasSize(1);
      assertThat(((javax.swing.JLabel)panel.get().getComponent(0)).getText()).contains("could not be loaded");
    });
  }

  @Test void themedItemFactsKeepTheOriginalTooltipBehindAUserAction() throws Exception {
    var detail=FixtureDb.repositories().items().detail(19019).orElseThrow();
    var loaded=new DetailPanels.LoadedItem(detail,null,Map.of(),Set.of());
    SwingUtilities.invokeAndWait(() -> {
      var page=render(loaded);
      var all=descendants(page);
      assertThat(all.stream().filter(JournalItemFacts.class::isInstance)).hasSize(1);
      var original=all.stream().filter(dev.wowforever.app.ui.widgets.ItemCardPanel.class::isInstance).findFirst().orElseThrow();
      assertThat(original.getParent().isVisible()).isFalse();
      var toggle=(javax.swing.JToggleButton)all.stream().filter(c -> c instanceof javax.swing.JToggleButton b && b.getText().equals("In-game tooltip")).findFirst().orElseThrow();
      toggle.doClick();assertThat(original.getParent().isVisible()).isTrue();
    });
  }
  @Test void recordedQuestRewardExposesLazyResearchAndRestoresSelectedSection() throws Exception {
    var repos=FixtureDb.repositories();
    var base=repos.items().detail(19019).orElseThrow();
    // Synthetic acquisition record exercises the presentation independently of fixture reward coverage.
    var detail=new ItemDetail(base.summary(),base.classId(),base.subclassId(),base.startQuestId(),base.droppedBy(),
        base.inInstanceLoot(),base.containedInObjects(),base.containedInItems(),base.soldBy(),
        List.of(new ItemSource(ItemSource.REWARD,new EntityRef(EntityKind.QUEST,373),"Fixture reward quest")),
        base.relatedQuests(),base.card());
    var loaded=new DetailPanels.LoadedItem(detail,null,Map.of(),Set.of());
    var jobs=new java.util.ArrayList<Runnable>();
    SwingUtilities.invokeAndWait(()->{
      var page=(JournalDetails.JournalPage)JournalDetails.render(loaded,repos,t->{},jobs::add,StatusSink.NONE,SourceFilter.ALL,BooleanSetting.inMemory(false));
      assertThat(jobs).isEmpty();
      int index=page.tabs().indexOfTab("Research trail");assertThat(index).isGreaterThan(0);
      var action=descendants(page).stream().filter(c->c instanceof javax.swing.JButton b&&b.getText().equals("Research quest trail →")).map(javax.swing.JButton.class::cast).findFirst().orElseThrow();
      action.doClick();assertThat(page.tabs().getSelectedIndex()).isEqualTo(index);assertThat(jobs).hasSize(1);
      Object saved=page.saveState();
      var restored=(JournalDetails.JournalPage)JournalDetails.render(loaded,repos,t->{},jobs::add,StatusSink.NONE,SourceFilter.ALL,BooleanSetting.inMemory(false));
      restored.restoreState(saved);assertThat(restored.tabs().getSelectedIndex()).isEqualTo(index);
    });
  }

  private static java.util.List<java.awt.Component> descendants(java.awt.Container root) {
    var values=new java.util.ArrayList<java.awt.Component>();
    for(var c:root.getComponents()){values.add(c);if(c instanceof java.awt.Container nested)values.addAll(descendants(nested));}
    return values;
  }

  private static JournalDetails.JournalPage render(Object loaded) {
    // Null repositories deliberately prove these two routes consume only preloaded data on the EDT.
    return (JournalDetails.JournalPage)JournalDetails.render(loaded,null,t->{},Runnable::run,
        StatusSink.NONE,SourceFilter.ALL,BooleanSetting.inMemory(false));
  }
}
