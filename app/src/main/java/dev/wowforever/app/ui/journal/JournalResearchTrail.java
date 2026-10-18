package dev.wowforever.app.ui.journal;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.QuestChain;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Async;
import dev.wowforever.app.ui.StatusSink;
import dev.wowforever.app.ui.pages.PageState;
import dev.wowforever.app.ui.pages.quest.ChainPanel;
import dev.wowforever.app.ui.widgets.LinkButton;
import dev.wowforever.app.ui.widgets.WrappingText;
import java.awt.BorderLayout;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.LongFunction;
import javax.swing.*;

/** A bounded, source-backed path from a recorded reward item into the existing quest graph. */
final class JournalResearchTrail extends JScrollPane implements PageState {
  private final JPanel body = new TrailBody();
  private final JPanel trail = new JPanel(new JournalSurface.Stack());
  private final JComboBox<ItemSource> rewards;
  private final List<ItemSource> sources;
  private final LongFunction<QuestChain> load;
  private final Executor worker;
  private final Navigator nav;
  private final StatusSink sink;
  private boolean active;
  private Integer restoredScroll;
  private volatile long generation;
  private record State(long questId, int scroll) {}

  JournalResearchTrail(List<ItemSource> records, LongFunction<QuestChain> load,
      Executor worker, Navigator nav, StatusSink sink) {
    this.sources = records.stream().filter(s -> s.ref().kind() == EntityKind.QUEST).distinct().toList();
    this.load = load; this.worker = worker; this.nav = nav; this.sink = sink;
    setBorder(BorderFactory.createEmptyBorder());getVerticalScrollBar().setUnitIncrement(20);
    body.setOpaque(false);body.setBorder(BorderFactory.createEmptyBorder(16,18,16,18));trail.setOpaque(false);
    JPanel intro = new JPanel();intro.setLayout(new JournalSurface.Stack());intro.setOpaque(false);
    intro.add(JournalSurface.title("Follow the quest connections", 26));
    intro.add(new WrappingText("This item is recorded as a quest reward. Explore that quest's prerequisites, alternatives and follow-ups. These are world records, not your character's progress.", "Research trail scope"));
    rewards = new JComboBox<>(sources.toArray(ItemSource[]::new));
    rewards.setRenderer(new DefaultListCellRenderer() {
      @Override public java.awt.Component getListCellRendererComponent(JList<?> list,Object value,int index,
          boolean selected,boolean focus) {
        return super.getListCellRendererComponent(list,value instanceof ItemSource source ? source.displayName() : value,index,selected,focus);
      }
    });
    rewards.getAccessibleContext().setAccessibleName("Recorded reward quest");
    rewards.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,rewards.getPreferredSize().height));
    intro.add(new JLabel("Recorded reward quest"));intro.add(rewards);
    rewards.addActionListener(e -> {if(active) refresh();});
    body.add(intro,BorderLayout.NORTH);body.add(trail,BorderLayout.CENTER);setViewportView(body);
    if(sources.isEmpty())showMessage("No quest reward records are available for this item.");
    else showMessage("Choose this section to load the recorded quest connections.");
  }

  /** Called only when the research section is selected; constructing an item page does no extra SQL. */
  void activate() {if(!active){active=true;refresh();}}

  private void refresh() {
    ItemSource selected=(ItemSource)rewards.getSelectedItem();if(selected==null)return;
    long request=++generation;
    showMessage("Loading recorded quest connections…");
    Async.runIf(worker,()->generation==request,()->load.apply(selected.ref().id()),chain->{
      trail.removeAll();
      JPanel summary=new JPanel();summary.setOpaque(false);summary.setLayout(new JournalSurface.Stack());
      summary.add(new WrappingText(selected.observedInGame() ? "Reward relationship: observed in game." :
          "Reward relationship: recorded acquisition data. This does not establish an in-game observation.","Reward relationship evidence"));
      summary.add(LinkButton.create("Open quest: "+selected.displayName(),selected.ref(),nav));
      trail.add(summary,BorderLayout.NORTH);
      if(chain.isEmpty())trail.add(new WrappingText("The reward link is recorded, but this quest's details are missing from the installed data.","Missing quest details"));
      else {
        JPanel connections=new JPanel(new JournalSurface.Stack());connections.setOpaque(false);
        if(chain.standalone())connections.add(new WrappingText("No prerequisites or follow-ups are recorded. This is a coverage statement, not proof none exist.","Quest connection coverage"),BorderLayout.NORTH);
        connections.add(new ChainPanel(chain,nav),BorderLayout.CENTER);trail.add(connections,BorderLayout.CENTER);
      }
      trail.revalidate();trail.repaint();
      if(restoredScroll!=null){int position=restoredScroll;restoredScroll=null;SwingUtilities.invokeLater(()->getVerticalScrollBar().setValue(position));}
    },failure->{
      trail.removeAll();trail.add(new WrappingText("Quest connections could not be loaded. The recorded reward link remains available; retry the research query.","Research query failed"),BorderLayout.NORTH);
      trail.add(LinkButton.create("Open quest: "+selected.displayName(),selected.ref(),nav));
      JButton retry=new JButton("Retry quest connections");retry.addActionListener(e->refresh());trail.add(retry,BorderLayout.CENTER);
      trail.revalidate();trail.repaint();sink.fail(failure);
    });
  }

  private void showMessage(String text){trail.removeAll();trail.add(new WrappingText(text,"Research trail status"));trail.revalidate();trail.repaint();}
  @Override public Object saveState(){ItemSource selected=(ItemSource)rewards.getSelectedItem();return new State(selected==null?-1:selected.ref().id(),getVerticalScrollBar().getValue());}
  @Override public void restoreState(Object value){
    if(!(value instanceof State state))return;
    restoredScroll=state.scroll();
    for(ItemSource source:sources)if(source.ref().id()==state.questId()){rewards.setSelectedItem(source);break;}
    if(active)SwingUtilities.invokeLater(()->getVerticalScrollBar().setValue(state.scroll()));
  }
  private static final class TrailBody extends JPanel implements Scrollable {
    TrailBody(){super(new JournalSurface.Stack());}
    public java.awt.Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
    public int getScrollableUnitIncrement(java.awt.Rectangle r,int orientation,int direction){return 20;}
    public int getScrollableBlockIncrement(java.awt.Rectangle r,int orientation,int direction){return Math.max(20,r.height-20);}
    public boolean getScrollableTracksViewportWidth(){return true;}
    public boolean getScrollableTracksViewportHeight(){return false;}
  }
}
