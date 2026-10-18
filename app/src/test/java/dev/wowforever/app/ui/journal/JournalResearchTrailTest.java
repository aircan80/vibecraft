package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.StatusSink;
import dev.wowforever.app.ui.pages.quest.ChainPanel;
import dev.wowforever.app.ui.pages.quest.ChainRows;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class JournalResearchTrailTest {
  private static ItemSource reward(long id){return new ItemSource(ItemSource.REWARD,new EntityRef(EntityKind.QUEST,id),"Quest "+id);}
  private static QuestChain chain(long id){
    var before=new QuestChain.Step(1,"First",1,"Zone",QuestChain.Connector.REQUIRES_ALL,id,-1,false);
    var current=new QuestChain.Step(id,"Reward quest",2,"Zone",null,null,0,false);
    return new QuestChain(List.of(before,current),1,List.of(before,current),false,
        List.of(new QuestRelationEvidence(id,1,"pre_group","allthethings-forever","exact-build","quests.lua#12","https://example.com/source#L12","authored")));
  }

  @Test void defersWorkAndReusesQualifiedChainWithQuestNavigation() throws Exception {
    var jobs=new ArrayList<Runnable>();var opened=new ArrayList<ViewTarget>();
    SwingUtilities.invokeAndWait(()->{
      var panel=new JournalResearchTrail(List.of(reward(2)),JournalResearchTrailTest::chain,jobs::add,opened::add,StatusSink.NONE);
      assertThat(jobs).isEmpty();panel.activate();panel.activate();assertThat(jobs).hasSize(1);
      jobs.removeFirst().run();
      var graph=descendants(panel).stream().filter(ChainPanel.class::isInstance).map(ChainPanel.class::cast).findFirst().orElseThrow();
      assertThat(graph.rows()).anyMatch(r->r.kind()==ChainRows.Kind.STEP&&r.step().connector()==QuestChain.Connector.REQUIRES_ALL);
      var source=descendants(panel).stream().filter(dev.wowforever.app.ui.widgets.SourceInfoButton.class::isInstance)
          .map(dev.wowforever.app.ui.widgets.SourceInfoButton.class::cast).findFirst().orElseThrow();
      assertThat(source.getToolTipText()).contains("AllTheThings Forever","not in-game verified");
      assertThat(text(source.detailsContent())).contains("exact-build","quests.lua#12","https://example.com/source#L12");
      button(panel,"Open quest: Quest 2").doClick();assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(EntityKind.QUEST,2)));
      assertThat(text(panel)).contains("not your character's progress","not establish an in-game observation");
    });
  }

  @Test void obsoleteQueuedSelectionNeverLoadsAndSelectedRewardRestores() throws Exception {
    var jobs=new ArrayList<Runnable>();var loaded=new ArrayList<Long>();
    SwingUtilities.invokeAndWait(()->{
      var panel=new JournalResearchTrail(List.of(reward(2),reward(3)),id->{loaded.add(id);return chain(id);},jobs::add,t->{},StatusSink.NONE);
      panel.activate();combo(panel).setSelectedIndex(1);assertThat(jobs).hasSize(2);
      jobs.removeFirst().run();jobs.removeFirst().run();assertThat(loaded).containsExactly(3L);
      var restored=new JournalResearchTrail(List.of(reward(2),reward(3)),JournalResearchTrailTest::chain,jobs::add,t->{},StatusSink.NONE);
      restored.restoreState(panel.saveState());assertThat(jobs).isEmpty();restored.activate();
      assertThat(((ItemSource)combo(restored).getSelectedItem()).ref().id()).isEqualTo(3);
    });
  }

  @Test void failureIsDistinctFromNoConnectionsAndCanRetry() throws Exception {
    AtomicInteger calls=new AtomicInteger();var failures=new ArrayList<Exception>();
    SwingUtilities.invokeAndWait(()->{
      var panel=new JournalResearchTrail(List.of(reward(2)),id->{if(calls.getAndIncrement()==0)throw new IllegalStateException("fixture failure");return QuestChain.EMPTY;},Runnable::run,t->{},new StatusSink(){public void message(String t){}public void fail(Exception e){failures.add(e);}});
      panel.activate();assertThat(failures).hasSize(1);assertThat(text(panel)).contains("could not be loaded");
      button(panel,"Retry quest connections").doClick();assertThat(text(panel)).contains("details are missing");
      assertThat(calls.get()).isEqualTo(2);
    });
  }

  @Test void realWorkerLoadsOffEdtAndDeliversOnEdt() throws Exception {
    var jobs=new ArrayList<Runnable>();var panel=new JournalResearchTrail[1];
    SwingUtilities.invokeAndWait(()->{
      panel[0]=new JournalResearchTrail(List.of(reward(2)),id->{assertThat(SwingUtilities.isEventDispatchThread()).isFalse();return chain(id);},jobs::add,t->{},StatusSink.NONE);panel[0].activate();
    });
    var thread=new Thread(jobs.getFirst());thread.start();thread.join();
    SwingUtilities.invokeAndWait(()->assertThat(button(panel[0],"Open quest: Quest 2")).isNotNull());
  }

  private static JComboBox<?> combo(Container root){return descendants(root).stream().filter(JComboBox.class::isInstance).map(JComboBox.class::cast).findFirst().orElseThrow();}
  private static JButton button(Container root,String text){return descendants(root).stream().filter(JButton.class::isInstance).map(JButton.class::cast).filter(b->text.equals(b.getText())).findFirst().orElseThrow();}
  private static String text(Container root){return descendants(root).stream().map(c->c instanceof JTextArea a?a.getText():c instanceof JLabel l?l.getText():"").collect(java.util.stream.Collectors.joining(" "));}
  private static List<Component> descendants(Container root){var out=new ArrayList<Component>();for(Component child:root.getComponents()){out.add(child);if(child instanceof Container container)out.addAll(descendants(container));}return out;}
}
