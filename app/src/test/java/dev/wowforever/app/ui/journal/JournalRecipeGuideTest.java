package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.db.NativeRecipeGuideRepository.*;
import dev.wowforever.app.ui.StatusSink;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class JournalRecipeGuideTest {
  private static Snapshot snapshot(){return new Snapshot("native","70450",List.of(recipe("one"),recipe("two")));}
  private static Recipe recipe(String variant){return new Recipe(1,"Recipe",10,"Output","1",variant,List.of(),List.of(),List.of(),"No listed reagents");}

  @Test void explicitSelectionDefersWorkAndObsoleteQueuedQueriesNeverRun()throws Exception {
    var jobs=new ArrayList<Runnable>();var calls=new AtomicInteger();
    SwingUtilities.invokeAndWait(()->{
      var panel=new JournalRecipeGuide(()->{calls.incrementAndGet();return snapshot();},jobs::add,t->{},StatusSink.NONE);
      assertThat(jobs).isEmpty();panel.setActive(true);panel.setActive(true);assertThat(jobs).hasSize(1);
      panel.setActive(false);jobs.removeFirst().run();assertThat(calls).hasValue(0);
      panel.setActive(true);jobs.removeFirst().run();assertThat(calls).hasValue(1);
      panel.setActive(false);panel.setActive(true);assertThat(jobs).isEmpty();
    });
  }
  @Test void removingPageInvalidatesQueuedWorkAndRecipeVariantRestores()throws Exception {
    var jobs=new ArrayList<Runnable>();var calls=new AtomicInteger();
    SwingUtilities.invokeAndWait(()->{
      var panel=new JournalRecipeGuide(()->{calls.incrementAndGet();return snapshot();},jobs::add,t->{},StatusSink.NONE);
      panel.setActive(true);panel.removeNotify();jobs.removeFirst().run();assertThat(calls).hasValue(0);
      panel.setActive(true);jobs.removeFirst().run();combo(panel).setSelectedIndex(1);
      var restored=new JournalRecipeGuide(JournalRecipeGuideTest::snapshot,jobs::add,t->{},StatusSink.NONE);
      restored.restoreState(panel.saveState());assertThat(jobs).isEmpty();restored.setActive(true);jobs.removeFirst().run();
      assertThat(((Recipe)combo(restored).getSelectedItem()).variant()).isEqualTo("two");
    });
  }
  @Test void failureHasRetryAndEmptyCoverageDoesNotAssertCannotCraft()throws Exception {
    AtomicInteger calls=new AtomicInteger();var failures=new ArrayList<Exception>();
    SwingUtilities.invokeAndWait(()->{
      var panel=new JournalRecipeGuide(()->{if(calls.getAndIncrement()==0)throw new IllegalStateException("fixture");return new Snapshot("f","70450",List.of());},Runnable::run,t->{},new StatusSink(){public void message(String t){}public void fail(Exception e){failures.add(e);}});
      panel.setActive(true);assertThat(failures).hasSize(1);assertThat(text(panel)).contains("query failure");
      descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast).filter(b->b.getText().equals("Retry crafting guide")).findFirst().orElseThrow().doClick();
      assertThat(text(panel)).contains("does not establish that the item cannot be crafted");assertThat(calls).hasValue(2);
    });
  }
  @Test void backgroundLoadRunsOffEdtAndAbandonedDeliveryDoesNotPopulate()throws Exception {
    var jobs=new ArrayList<Runnable>();var panel=new JournalRecipeGuide[1];
    SwingUtilities.invokeAndWait(()->{panel[0]=new JournalRecipeGuide(()->{assertThat(SwingUtilities.isEventDispatchThread()).isFalse();return snapshot();},jobs::add,t->{},StatusSink.NONE);panel[0].setActive(true);});
    // Keep EDT occupied while the worker queues its result, then abandon the section before delivery.
    SwingUtilities.invokeAndWait(()->{var thread=new Thread(jobs.getFirst());thread.start();try{thread.join();}catch(InterruptedException e){throw new RuntimeException(e);}panel[0].setActive(false);});
    SwingUtilities.invokeAndWait(()->assertThat(combo(panel[0]).getItemCount()).isZero());
  }
  @Test void selectedVariantAndScrollRestoreAfterLazyLoadAndLayout()throws Exception {
    List<Ingredient> many=java.util.stream.LongStream.range(100,130).mapToObj(id->new Ingredient(id,"Reagent "+id,1L)).toList();
    Snapshot data=new Snapshot("f","70450",List.of(new Recipe(1,"Recipe",10,"Output","1","one",many,List.of(),List.of(),"Native"),new Recipe(2,"Recipe",10,"Output","1","two",many,List.of(),List.of(),"Native")));
    var restored=new JournalRecipeGuide[1];
    SwingUtilities.invokeAndWait(()->{
      var first=new JournalRecipeGuide(()->data,Runnable::run,t->{},StatusSink.NONE);first.setActive(true);combo(first).setSelectedIndex(1);first.setSize(600,400);for(int i=0;i<12;i++)layout(first);
      scroll(first).getVerticalScrollBar().setValue(123);assertThat(scroll(first).getVerticalScrollBar().getValue()).isEqualTo(123);
      restored[0]=new JournalRecipeGuide(()->data,Runnable::run,t->{},StatusSink.NONE);restored[0].restoreState(first.saveState());restored[0].setActive(true);restored[0].setSize(600,400);for(int i=0;i<12;i++)layout(restored[0]);
    });
    SwingUtilities.invokeAndWait(()->{assertThat(((Recipe)combo(restored[0]).getSelectedItem()).variant()).isEqualTo("two");assertThat(scroll(restored[0]).getVerticalScrollBar().getValue()).isEqualTo(123);});
  }
  @Test void teachingOnlyLoadsOnButtonAndVariantChangeSkipsOldJobsWhileBackRestoresLoadedEvidence()throws Exception {
    var jobs=new ArrayList<Runnable>();var calls=new ArrayList<Long>();
    Recipe second=new Recipe(2,"Other recipe",10,"Output","1","two",List.of(),List.of(),List.of(),"Native");
    Snapshot data=new Snapshot("native","70450",List.of(recipe("one"),second));
    java.util.function.LongFunction<dev.wowforever.app.db.NativeTeachingItemRepository.Snapshot> load=spell->{calls.add(spell);return new dev.wowforever.app.db.NativeTeachingItemRepository.Snapshot("native","70450","enUS",spell,List.of(),"No teaching item recorded.");};
    SwingUtilities.invokeAndWait(()->{
      var page=new JournalRecipeGuide(()->data,jobs::add,t->{},StatusSink.NONE,load);
      assertThat(jobs).isEmpty();page.setActive(true);jobs.removeFirst().run();assertThat(calls).isEmpty();assertThat(jobs).isEmpty();
      button(page,"Find teaching items").doClick();combo(page).setSelectedIndex(1);jobs.removeFirst().run();assertThat(calls).isEmpty();
      button(page,"Find teaching items").doClick();jobs.removeFirst().run();assertThat(calls).containsExactly(2L);
      Object state=page.saveState();var returned=new JournalRecipeGuide(()->data,jobs::add,t->{},StatusSink.NONE,load);returned.restoreState(state);assertThat(jobs).isEmpty();returned.setActive(true);jobs.removeFirst().run();
      assertThat(((Recipe)combo(returned).getSelectedItem()).spellId()).isEqualTo(2);assertThat(descendants(returned)).anyMatch(TeachingItemsPanel.class::isInstance);assertThat(calls).containsExactly(2L);assertThat(jobs).isEmpty();
    });
  }
  @Test void hidingCraftingTabCancelsQueuedTeachingLookup()throws Exception {
    var jobs=new ArrayList<Runnable>();var calls=new AtomicInteger();
    SwingUtilities.invokeAndWait(()->{
      var page=new JournalRecipeGuide(JournalRecipeGuideTest::snapshot,jobs::add,t->{},StatusSink.NONE,spell->{calls.incrementAndGet();return new dev.wowforever.app.db.NativeTeachingItemRepository.Snapshot("native","70450","enUS",spell,List.of(),"None recorded");});
      page.setActive(true);jobs.removeFirst().run();button(page,"Find teaching items").doClick();page.setActive(false);jobs.removeFirst().run();assertThat(calls).hasValue(0);
    });
  }
  private static JButton button(Container root,String text){return descendants(root).stream().filter(JButton.class::isInstance).map(JButton.class::cast).filter(b->b.getText().equals(text)).findFirst().orElseThrow();}
  private static JScrollPane scroll(Container root){return descendants(root).stream().filter(JScrollPane.class::isInstance).map(JScrollPane.class::cast).findFirst().orElseThrow();}
  private static void layout(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container child)layout(child);}
  private static JComboBox<?> combo(Container root){return descendants(root).stream().filter(JComboBox.class::isInstance).map(JComboBox.class::cast).findFirst().orElseThrow();}
  private static String text(Container root){return descendants(root).stream().map(c->c instanceof JTextArea t?t.getText():c instanceof JLabel l?l.getText():"").collect(java.util.stream.Collectors.joining(" "));}
  private static List<Component> descendants(Container root){var found=new ArrayList<Component>();for(Component child:root.getComponents()){found.add(child);if(child instanceof Container c)found.addAll(descendants(c));}return found;}
}
