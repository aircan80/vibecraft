package dev.wowforever.app.ui.journal;

import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Async;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.StatusSink;
import dev.wowforever.app.ui.pages.PageState;
import dev.wowforever.app.ui.pages.quest.ChainPanel;
import dev.wowforever.app.ui.widgets.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.LongFunction;
import javax.swing.*;

/** Area associations and explicitly requested quest evidence; never inferred instance access rules. */
final class JournalPreparationResearch extends JScrollPane implements PageState {
  static final String ACCESS_UNKNOWN="No source-qualified instance access requirement recorded in this data update.";
  record Evidence(QuestChain chain,List<QuestSourceItem> items) {
    Evidence {items=List.copyOf(items);}
  }
  private record State(long questId,boolean inspected,int scroll) {}
  private final List<QuestSummary> quests;
  private final JComboBox<QuestSummary> selection;
  private final JPanel evidence=new JPanel(new JournalSurface.Stack());
  private final LongFunction<Evidence> load;
  private final Executor worker;
  private final Navigator nav;
  private final StatusSink sink;
  private volatile boolean active;
  private volatile long generation;
  private boolean inspected;
  private boolean loading;
  private long loadedId=-1;
  private Integer restoredScroll;

  JournalPreparationResearch(List<QuestSummary> quests,LongFunction<Evidence> load,
      Executor worker,Navigator nav,StatusSink sink) {
    this.quests=List.copyOf(quests);this.load=load;this.worker=worker;this.nav=nav;this.sink=sink;
    setBorder(BorderFactory.createEmptyBorder());getVerticalScrollBar().setUnitIncrement(20);
    JPanel body=new Body();body.setOpaque(false);body.setBorder(BorderFactory.createEmptyBorder(16,18,16,18));
    body.add(JournalSurface.title("Preparation research",26));
    body.add(JournalSurface.title("Access requirements evidence",20));
    body.add(new WrappingText(ACCESS_UNKNOWN,"Instance access coverage"));
    body.add(new WrappingText("Quest names, item names and entrance locations do not establish mandatory access. These records do not track your character's progress.","Access evidence scope"));
    body.add(JournalSurface.title("Recorded quests for this area · "+quests.size(),20));
    body.add(new WrappingText("These quests are associated with the instance's recorded area. That association does not prove each quest takes place wholly inside this instance.","Quest area association"));
    if(quests.isEmpty())body.add(new WrappingText("No area-associated quests recorded in this data update. Missing records do not prove there are no quests.","Quest coverage"));
    else {var table=LinkTable.inline(Columns.quests(),nav);table.setRows(quests);body.add(table);}
    selection=new JComboBox<>(quests.toArray(QuestSummary[]::new));
    selection.getAccessibleContext().setAccessibleName("Quest to research");
    selection.setRenderer(new DefaultListCellRenderer(){
      @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
        return super.getListCellRendererComponent(list,value instanceof QuestSummary quest?quest.displayName():value,index,selected,focus);
      }
    });
    selection.setMaximumSize(new Dimension(Integer.MAX_VALUE,selection.getPreferredSize().height));
    selection.addActionListener(event->{loadedId=-1;generation++;loading=false;if(inspected&&active)refresh();});
    if(!quests.isEmpty()){
      body.add(new JLabel("Quest to research"));body.add(selection);
      JButton inspect=new JButton("Inspect recorded prerequisites & items");
      inspect.addActionListener(event->{inspected=true;if(active)refresh();});body.add(inspect);
    }
    evidence.setOpaque(false);body.add(evidence);message("Inspect one quest to load its recorded connections and required/provided items.");setViewportView(body);
  }

  void setActive(boolean value){
    if(active==value)return;active=value;
    if(!value){generation++;loading=false;return;}
    if(inspected&&!loading&&loadedId!=selectedId())refresh();
    if(!loading)restoreScroll();
  }
  @Override public void addNotify(){
    super.addNotify();
    // Guide containers can detach and reattach without changing the selected tab.
    SwingUtilities.invokeLater(()->{
      if(!isDisplayable())return;
      Container ancestor=SwingUtilities.getAncestorOfClass(JTabbedPane.class,this);
      if(ancestor instanceof JTabbedPane tabs)setActive(tabs.getSelectedComponent()==this);
    });
  }
  @Override public void removeNotify(){setActive(false);super.removeNotify();}
  private long selectedId(){QuestSummary quest=(QuestSummary)selection.getSelectedItem();return quest==null?-1:quest.id();}
  private void refresh(){
    QuestSummary quest=(QuestSummary)selection.getSelectedItem();if(!active||quest==null)return;
    long request=++generation;loading=true;message("Loading recorded quest prerequisites & items…");
    Async.runIf(worker,()->active&&generation==request,()->load.apply(quest.id()),result->{
      loading=false;loadedId=quest.id();evidence.removeAll();
      evidence.add(LinkButton.create("Open quest: "+quest.displayName(),quest.ref(),nav));
      if(result.chain().isEmpty())evidence.add(new WrappingText("Quest connections are missing from the installed data.","Missing quest connections"));
      else {
        if(result.chain().standalone())evidence.add(new WrappingText("No prerequisites or follow-ups recorded. This is a coverage statement, not proof none exist.","Quest connection coverage"));
        evidence.add(new ChainPanel(result.chain(),nav));
      }
      addItems("Quest required items","required",result.items());
      addItems("Quest provided items","provided",result.items());
      evidence.add(new WrappingText("These are recorded quest source items, separate from rewards and collected-item objectives. They do not establish instance keys or entry requirements.","Quest item evidence scope"));
      evidence.revalidate();evidence.repaint();restoreScroll();
    },failure->{loading=false;message("Quest evidence could not be loaded. This query failure does not establish missing requirements.");
      JButton retry=new JButton("Retry quest evidence");retry.addActionListener(event->refresh());evidence.add(retry);evidence.revalidate();sink.fail(failure);
    });
  }
  private void addItems(String title,String kind,List<QuestSourceItem> items){
    evidence.add(JournalSurface.title(title,20));
    List<QuestSourceItem> matching=items.stream().filter(item->kind.equals(item.kind())).toList();
    if(matching.isEmpty())evidence.add(new WrappingText("No items recorded in this category.",title+" coverage"));
    else for(QuestSourceItem item:matching)evidence.add(LinkButton.create(item.displayName(),item.ref(),nav));
  }
  private void message(String text){evidence.removeAll();evidence.add(new WrappingText(text,"Preparation research status"));evidence.revalidate();evidence.repaint();}
  private void restoreScroll(){if(restoredScroll!=null){int position=restoredScroll;restoredScroll=null;SwingUtilities.invokeLater(()->getVerticalScrollBar().setValue(position));}}
  @Override public Object saveState(){return new State(selectedId(),inspected,getVerticalScrollBar().getValue());}
  @Override public void restoreState(Object value){
    if(!(value instanceof State state))return;
    inspected=false;for(QuestSummary quest:quests)if(quest.id()==state.questId()){selection.setSelectedItem(quest);break;}
    inspected=state.inspected();restoredScroll=state.scroll();if(active&&inspected)refresh();
  }
  private static final class Body extends JPanel implements Scrollable {
    Body(){super(new JournalSurface.Stack());}
    public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
    public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 20;}
    public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(20,r.height-20);}
    public boolean getScrollableTracksViewportWidth(){return true;}
    public boolean getScrollableTracksViewportHeight(){return false;}
  }
}
