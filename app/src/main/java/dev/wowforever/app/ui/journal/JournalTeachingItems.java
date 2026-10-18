package dev.wowforever.app.ui.journal;

import dev.wowforever.app.db.NativeTeachingItemRepository.Snapshot;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Async;
import dev.wowforever.app.ui.StatusSink;
import java.awt.BorderLayout;
import java.awt.event.HierarchyEvent;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.swing.*;

/** Explicit per-creation-spell lookup: neither construction nor selecting the recipe runs acquisition SQL. */
public final class JournalTeachingItems extends JPanel {
  private final Supplier<Snapshot> load;
  private final Executor worker;
  private final Navigator nav;
  private final StatusSink sink;
  private volatile long generation;
  private volatile boolean active;
  private boolean loading,everShown;
  private Snapshot snapshot;

  public JournalTeachingItems(Supplier<Snapshot> load,Executor worker,Navigator nav,StatusSink sink) {
    super(new BorderLayout(0,8));this.load=load;this.worker=worker;this.nav=nav;this.sink=sink;setOpaque(false);prompt(false);
    addHierarchyListener(event->{
      if((event.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)==0)return;
      if(isShowing())everShown=true;else if(everShown)deactivate();
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
    active=true;loading=true;long request=++generation;
    removeAll();add(JournalSurface.copy("Loading exact Forever teaching-item records…"));revalidate();repaint();
    Async.runIf(worker,()->active&&generation==request,load,result->{loading=false;snapshot=result;showSnapshot();},failure->{loading=false;prompt(true);sink.fail(failure);});
  }
  private void showSnapshot(){removeAll();add(new TeachingItemsPanel(snapshot,nav));revalidate();repaint();}
  /** A tab/variant owner may invalidate hidden work even before the component has a display peer. */
  public void deactivate(){active=false;generation++;if(loading){loading=false;if(snapshot==null)prompt(false);}}
  @Override public void removeNotify(){deactivate();super.removeNotify();}
}
