package dev.wowforever.app.ui.journal;

import dev.wowforever.app.db.NativeTeachingItemRepository.Snapshot;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Async;
import dev.wowforever.app.ui.StatusSink;
import dev.wowforever.app.ui.pages.PageState;
import java.awt.BorderLayout;
import java.awt.event.HierarchyEvent;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.swing.*;

/** Explicit per-creation-spell lookup: neither construction nor selecting the recipe runs acquisition SQL. */
public final class JournalTeachingItems extends JPanel implements PageState {
  private final Supplier<Snapshot> load;
  private final Executor worker;
  private final Navigator nav;
  private final StatusSink sink;
  private final String expectedSnapshot;
  private final long expectedSpell;
  private volatile long generation;
  private volatile boolean active;
  private boolean loading,everShown,requested;
  private Snapshot snapshot;
  private record State(String context,long spell,boolean requested,Snapshot snapshot) {}

  public JournalTeachingItems(Supplier<Snapshot> load,Executor worker,Navigator nav,StatusSink sink) {
    this(load,worker,nav,sink,null,-1);
  }
  public JournalTeachingItems(Supplier<Snapshot> load,Executor worker,Navigator nav,StatusSink sink,String expectedSnapshot,long expectedSpell) {
    super(new BorderLayout(0,8));this.load=load;this.worker=worker;this.nav=nav;this.sink=sink;this.expectedSnapshot=expectedSnapshot;this.expectedSpell=expectedSpell;setOpaque(false);prompt(false);
    addHierarchyListener(event->{
      if((event.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)==0)return;
      if(isShowing()){everShown=true;resumeIfRequested();}else if(everShown)deactivate();
    });
  }
  private void prompt(boolean retry) {
    removeAll();JPanel body=JournalSurface.stack();JournalSurface.add(body,JournalSurface.title("How is this creation learned?",22));
    JournalSurface.add(body,JournalSurface.copy(retry?"Teaching-item records could not be loaded. This is a query failure, not evidence that no teaching item exists.":"Look up exact native teaching items for this selected creation spell. Reported item acquisition stays separate from the native teaching relationship."));
    JButton find=new JButton(retry?"Retry teaching items":"Find teaching items");find.addActionListener(event->refresh());JournalSurface.add(body,find);add(body);revalidate();repaint();
  }
  private void refresh() {
    if(loading)return;
    if(snapshot!=null){showSnapshot();return;}
    active=true;loading=true;requested=true;long request=++generation;
    removeAll();add(JournalSurface.copy("Loading exact Forever teaching-item records…"));revalidate();repaint();
    Async.runIf(worker,()->active&&generation==request,()->{
      Snapshot result=load.get();
      if(expectedSnapshot!=null&&(!expectedSnapshot.equals(result.snapshotId())||expectedSpell!=result.spellId()))throw new IllegalStateException("Teaching result does not match the selected native creation context");
      return result;
    },result->{loading=false;snapshot=result;showSnapshot();},failure->{loading=false;prompt(true);sink.fail(failure);});
  }
  private void showSnapshot(){removeAll();add(new TeachingItemsPanel(snapshot,nav));revalidate();repaint();}
  /** A tab/variant owner may invalidate hidden work even before the component has a display peer. */
  public void deactivate(){active=false;generation++;if(loading){loading=false;if(snapshot==null)prompt(false);}}
  /** Previously requested work resumes only while this section is actually visible. */
  public void resumeIfRequested(){if(requested&&snapshot==null&&!loading&&isShowing())refresh();}
  @Override public Object saveState(){return new State(expectedSnapshot,expectedSpell,requested,snapshot);}
  @Override public void restoreState(Object value){
    if(!(value instanceof State state)||!java.util.Objects.equals(expectedSnapshot,state.context())||expectedSpell!=state.spell())return;
    if(state.snapshot()!=null&&(expectedSnapshot==null||!expectedSnapshot.equals(state.snapshot().snapshotId())||expectedSpell!=state.snapshot().spellId()))return;
    deactivate();requested=state.requested();snapshot=state.snapshot();if(snapshot!=null)showSnapshot();else prompt(false);
    resumeIfRequested();
  }
  @Override public void removeNotify(){deactivate();super.removeNotify();}
}
