package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.db.NativeTeachingItemRepository.Snapshot;
import dev.wowforever.app.ui.StatusSink;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class JournalTeachingItemsTest {
  private static Snapshot empty(){return new Snapshot("f","70450","enUS",2543,List.of(),"No teaching item recorded in this pinned native snapshot.");}
  @Test void explicitButtonOnlyLoadsAndHiddenQueuedWorkIsSkipped()throws Exception {
    var jobs=new ArrayList<Runnable>();var calls=new AtomicInteger();
    SwingUtilities.invokeAndWait(()->{
      var page=new JournalTeachingItems(()->{calls.incrementAndGet();return empty();},jobs::add,t->{},StatusSink.NONE);
      assertThat(jobs).isEmpty();button(page,"Find teaching items").doClick();assertThat(jobs).hasSize(1);
      page.deactivate();jobs.removeFirst().run();assertThat(calls).hasValue(0);
      button(page,"Find teaching items").doClick();jobs.removeFirst().run();assertThat(calls).hasValue(1);assertThat(descendants(page)).anyMatch(TeachingItemsPanel.class::isInstance);
    });
  }
  @Test void failureAllowsRetryAndDetachDiscardsDelivery()throws Exception {
    var calls=new AtomicInteger();var failures=new ArrayList<Exception>();
    SwingUtilities.invokeAndWait(()->{
      var page=new JournalTeachingItems(()->{if(calls.getAndIncrement()==0)throw new IllegalStateException("fixture");return empty();},Runnable::run,t->{},new StatusSink(){public void message(String text){}public void fail(Exception e){failures.add(e);}});
      button(page,"Find teaching items").doClick();assertThat(failures).hasSize(1);button(page,"Retry teaching items").doClick();assertThat(calls).hasValue(2);
    });
    var jobs=new ArrayList<Runnable>();var page=new JournalTeachingItems[1];
    SwingUtilities.invokeAndWait(()->{page[0]=new JournalTeachingItems(()->{assertThat(SwingUtilities.isEventDispatchThread()).isFalse();return empty();},jobs::add,t->{},StatusSink.NONE);button(page[0],"Find teaching items").doClick();});
    SwingUtilities.invokeAndWait(()->{var thread=new Thread(jobs.getFirst());thread.start();try{thread.join();}catch(InterruptedException e){throw new RuntimeException(e);}page[0].removeNotify();});
    SwingUtilities.invokeAndWait(()->{assertThat(descendants(page[0])).noneMatch(TeachingItemsPanel.class::isInstance);assertThat(button(page[0],"Find teaching items")).isNotNull();});
  }
  @Test void restoredSnapshotsAreScopedToNativeSnapshotAndCreationSpell()throws Exception {
    SwingUtilities.invokeAndWait(()->{
      var source=new JournalTeachingItems(JournalTeachingItemsTest::empty,Runnable::run,t->{},StatusSink.NONE,"f",2543);
      button(source,"Find teaching items").doClick();Object state=source.saveState();var jobs=new ArrayList<Runnable>();
      var same=new JournalTeachingItems(JournalTeachingItemsTest::empty,jobs::add,t->{},StatusSink.NONE,"f",2543);same.restoreState(state);assertThat(descendants(same)).anyMatch(TeachingItemsPanel.class::isInstance);assertThat(jobs).isEmpty();
      var other=new JournalTeachingItems(JournalTeachingItemsTest::empty,jobs::add,t->{},StatusSink.NONE,"different",2543);other.restoreState(state);assertThat(descendants(other)).noneMatch(TeachingItemsPanel.class::isInstance);assertThat(button(other,"Find teaching items")).isNotNull();
      var otherSpell=new JournalTeachingItems(JournalTeachingItemsTest::empty,jobs::add,t->{},StatusSink.NONE,"f",999);otherSpell.restoreState(state);assertThat(descendants(otherSpell)).noneMatch(TeachingItemsPanel.class::isInstance);
    });
  }
  private static JButton button(Container root,String text){return descendants(root).stream().filter(JButton.class::isInstance).map(JButton.class::cast).filter(b->b.getText().equals(text)).findFirst().orElseThrow();}
  private static List<Component> descendants(Container root){var result=new ArrayList<Component>();for(Component child:root.getComponents()){result.add(child);if(child instanceof Container c)result.addAll(descendants(c));}return result;}
}
