package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.*;
import dev.wowforever.app.ui.StatusSink;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class JournalPreparationResearchTest {
  private static final List<QuestSummary> QUESTS=List.of(new QuestSummary(7848,"Attunement to the Core",60,55,"Molten Core"),new QuestSummary(166,"The Defias Brotherhood",20,14,"Deadmines"));
  private static JournalPreparationResearch.Evidence evidence(){return new JournalPreparationResearch.Evidence(QuestChain.EMPTY,List.of(new QuestSourceItem(1,"required","Required token",null,null),new QuestSourceItem(2,"provided","Provided token",null,null)));}

  @Test void constructionAndTabSelectionDoNotQueryUntilExplicitInspection()throws Exception {
    SwingUtilities.invokeAndWait(()->{
      var jobs=new ArrayList<Runnable>();var calls=new AtomicInteger();
      var panel=new JournalPreparationResearch(QUESTS,id->{calls.incrementAndGet();return evidence();},jobs::add,t->{},StatusSink.NONE);
      panel.setActive(true);assertThat(jobs).isEmpty();assertThat(text(panel)).contains(JournalPreparationResearch.ACCESS_UNKNOWN,"Recorded quests for this area · 2","does not prove each quest");
      button(panel,"Inspect recorded prerequisites & items").doClick();assertThat(jobs).hasSize(1);jobs.removeFirst().run();assertThat(calls).hasValue(1);
      assertThat(text(panel)).contains("Quest required items","Quest provided items","do not establish instance keys");
    });
  }
  @Test void changingSelectionAndLeavingSectionDiscardObsoleteQueuedQueries()throws Exception {
    SwingUtilities.invokeAndWait(()->{
      var jobs=new ArrayList<Runnable>();var ids=new ArrayList<Long>();
      var panel=new JournalPreparationResearch(QUESTS,id->{ids.add(id);return evidence();},jobs::add,t->{},StatusSink.NONE);
      panel.setActive(true);button(panel,"Inspect recorded prerequisites & items").doClick();combo(panel).setSelectedIndex(1);
      jobs.removeFirst().run();assertThat(ids).isEmpty();jobs.removeFirst().run();assertThat(ids).containsExactly(166L);
      combo(panel).setSelectedIndex(0);panel.removeNotify();jobs.removeFirst().run();assertThat(ids).containsExactly(166L);
    });
  }
  @Test void selectionAndInspectedStateRestoreWithoutEagerWork()throws Exception {
    SwingUtilities.invokeAndWait(()->{
      var first=new JournalPreparationResearch(QUESTS,id->evidence(),Runnable::run,t->{},StatusSink.NONE);
      first.setActive(true);combo(first).setSelectedIndex(1);button(first,"Inspect recorded prerequisites & items").doClick();
      var ids=new ArrayList<Long>();var jobs=new ArrayList<Runnable>();
      var restored=new JournalPreparationResearch(QUESTS,id->{ids.add(id);return evidence();},jobs::add,t->{},StatusSink.NONE);
      restored.restoreState(first.saveState());assertThat(jobs).isEmpty();assertThat(((QuestSummary)combo(restored).getSelectedItem()).id()).isEqualTo(166L);
      restored.setActive(true);jobs.removeFirst().run();assertThat(ids).containsExactly(166L);
      restored.setActive(false);restored.setActive(true);assertThat(jobs).isEmpty();
    });
  }
  @Test void backgroundQueryIsOffEdtAndStaleDeliveryCannotPopulate()throws Exception {
    var jobs=new ArrayList<Runnable>();var panel=new JournalPreparationResearch[1];
    SwingUtilities.invokeAndWait(()->{panel[0]=new JournalPreparationResearch(QUESTS,id->{assertThat(SwingUtilities.isEventDispatchThread()).isFalse();return evidence();},jobs::add,t->{},StatusSink.NONE);panel[0].setActive(true);button(panel[0],"Inspect recorded prerequisites & items").doClick();});
    SwingUtilities.invokeAndWait(()->{var thread=new Thread(jobs.getFirst());thread.start();try{thread.join();}catch(InterruptedException e){throw new RuntimeException(e);}panel[0].setActive(false);});
    SwingUtilities.invokeAndWait(()->assertThat(text(panel[0])).doesNotContain("Quest required items"));
  }
  @Test void queryFailureOffersRetryAndItemLinksRetainRealIdentity()throws Exception {
    SwingUtilities.invokeAndWait(()->{
      var calls=new AtomicInteger();var failures=new ArrayList<Exception>();var destinations=new ArrayList<dev.wowforever.app.nav.ViewTarget>();
      var panel=new JournalPreparationResearch(QUESTS,id->{if(calls.getAndIncrement()==0)throw new IllegalStateException("fixture");return evidence();},Runnable::run,destinations::add,new StatusSink(){public void message(String t){}public void fail(Exception e){failures.add(e);}});
      panel.setActive(true);button(panel,"Inspect recorded prerequisites & items").doClick();assertThat(failures).hasSize(1);assertThat(text(panel)).contains("query failure");
      button(panel,"Retry quest evidence").doClick();button(panel,"Required token").doClick();assertThat(destinations).hasSize(1);
      assertThat(destinations.getFirst()).isEqualTo(new dev.wowforever.app.nav.ViewTarget.Entity(new EntityRef(EntityKind.ITEM,1)));
    });
  }
  @Test void emptyAreaRetainsUnknownAccessWithoutAnInspectionAction()throws Exception {
    SwingUtilities.invokeAndWait(()->{var panel=new JournalPreparationResearch(List.of(),id->{throw new AssertionError("no query");},Runnable::run,t->{},StatusSink.NONE);panel.setActive(true);assertThat(text(panel)).contains(JournalPreparationResearch.ACCESS_UNKNOWN,"No area-associated quests");assertThat(descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast).noneMatch(b->b.getText().equals("Inspect recorded prerequisites & items"))).isTrue();});
  }
  @Test void scrollRestoresAfterLazyEvidenceLayout()throws Exception {
    var restored=new JournalPreparationResearch[1];
    var items=java.util.stream.LongStream.range(1,40).mapToObj(id->new QuestSourceItem(id,"required","Required item "+id,null,null)).toList();
    var data=new JournalPreparationResearch.Evidence(QuestChain.EMPTY,items);
    SwingUtilities.invokeAndWait(()->{
      var first=new JournalPreparationResearch(QUESTS,id->data,Runnable::run,t->{},StatusSink.NONE);
      first.setActive(true);button(first,"Inspect recorded prerequisites & items").doClick();first.setSize(600,400);for(int i=0;i<12;i++)layout(first);
      first.getVerticalScrollBar().setValue(123);assertThat(first.getVerticalScrollBar().getValue()).isEqualTo(123);
      restored[0]=new JournalPreparationResearch(QUESTS,id->data,Runnable::run,t->{},StatusSink.NONE);
      restored[0].restoreState(first.saveState());restored[0].setActive(true);restored[0].setSize(600,400);for(int i=0;i<12;i++)layout(restored[0]);
    });
    SwingUtilities.invokeAndWait(()->assertThat(restored[0].getVerticalScrollBar().getValue()).isEqualTo(123));
  }
  @Test void reattachingSelectedTabRestartsCancelledInspectionWithoutTabChange()throws Exception {
    var jobs=new ArrayList<Runnable>();var calls=new AtomicInteger();
    var host=new JPanel[1];var panel=new JournalPreparationResearch[1];
    SwingUtilities.invokeAndWait(()->{
      panel[0]=new JournalPreparationResearch(QUESTS,id->{calls.incrementAndGet();return evidence();},jobs::add,t->{},StatusSink.NONE);
      var tabs=new JTabbedPane();tabs.addTab("Preparation",panel[0]);host[0]=new JPanel();host[0].add(tabs);host[0].addNotify();
    });
    SwingUtilities.invokeAndWait(()->{
      button(panel[0],"Inspect recorded prerequisites & items").doClick();assertThat(jobs).hasSize(1);
      host[0].removeNotify();jobs.removeFirst().run();assertThat(calls).hasValue(0);
      host[0].addNotify();
    });
    SwingUtilities.invokeAndWait(()->{
      assertThat(jobs).hasSize(1);jobs.removeFirst().run();assertThat(calls).hasValue(1);
      assertThat(text(panel[0])).contains("Quest required items");host[0].removeNotify();
    });
  }
  private static void layout(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container child)layout(child);}
  private static JButton button(Container root,String text){return descendants(root).stream().filter(JButton.class::isInstance).map(JButton.class::cast).filter(b->text.equals(b.getText())).findFirst().orElseThrow();}
  private static JComboBox<?> combo(Container root){return descendants(root).stream().filter(JComboBox.class::isInstance).map(JComboBox.class::cast).findFirst().orElseThrow();}
  private static String text(Container root){return descendants(root).stream().map(c->c instanceof JTextArea t?t.getText():c instanceof JLabel l?l.getText():"").collect(java.util.stream.Collectors.joining(" "));}
  private static List<Component> descendants(Container root){var found=new ArrayList<Component>();for(Component child:root.getComponents()){found.add(child);if(child instanceof Container c)found.addAll(descendants(c));}return found;}
}
