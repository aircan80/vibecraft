package dev.wowforever.app.ui.journal;

import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.*;
import dev.wowforever.app.ui.detail.*;
import dev.wowforever.app.ui.pages.*;
import dev.wowforever.app.ui.widgets.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.swing.*;

/** Explorer's task-oriented detail pages. All data is supplied by the ordinary off-EDT loader. */
public final class JournalDetails {
  private JournalDetails() {}

  public static JComponent render(Object loaded, Repositories repos, Navigator nav, Executor worker,
      StatusSink sink, SourceFilter filter, BooleanSetting setting) {
    return render(loaded,repos,nav,worker,sink,filter,f -> {},setting,BooleanSetting.inMemory(false));
  }

  public static JComponent render(Object loaded, Repositories repos, Navigator nav, Executor worker,
      StatusSink sink, SourceFilter filter, Consumer<SourceFilter> onFilter, BooleanSetting questPins,
      BooleanSetting routes) {
    if (loaded instanceof DetailPanels.LoadedInstance p) return instance(p,nav,worker,sink);
    if (loaded instanceof DetailPanels.LoadedItem p) return item(p,nav,filter,onFilter);
    if (loaded instanceof DetailPanels.LoadedNpc p && p.portrait()!=null)
      return new JournalNpcPortrait(new NpcPage(p.detail(),p.mapZoneId(),p.map(),p.rateSources(),p.bossNpcs(),filter,
          onFilter,p.involvement(),nav,ClipboardPort.system(),p.routes()),p.portrait());
    return DetailPanels.render(loaded,repos,nav,worker,sink,filter,onFilter,questPins,routes);
  }

  private static JComponent instance(DetailPanels.LoadedInstance p, Navigator nav, Executor worker, StatusSink sink) {
    // An absent preload must never turn this new page into a synchronous repository reader.
    BossLoot loot=p.clientLoot()==null ? (boss,mode) -> List.of() : p.clientLoot();
    var encounters=new InstancePage(p.detail(),List.of(),loot,p.clientBuild(),nav);
    Component heading=((BorderLayout)encounters.getLayout()).getLayoutComponent(BorderLayout.NORTH);
    encounters.remove(heading);
    var page=new JournalPage(encounters);
    JPanel top=new JPanel(new BorderLayout());top.setOpaque(false);
    if(!p.breadcrumbs().isEmpty())top.add(new BreadcrumbTrail(p.breadcrumbs(),nav),BorderLayout.NORTH);
    top.add(heading,BorderLayout.CENTER);page.add(top,BorderLayout.NORTH);

    JPanel overview=column();
    overview.add(heading("Your dungeon guide"));
    overview.add(new WrappingText("Choose an encounter to inspect its recorded loot, or use Maps to plan your approach.","Dungeon guide"));
    JPanel counts=new JPanel(new GridLayout(1,3,10,0));counts.setOpaque(false);
    counts.add(fact("Encounters",Long.toString(p.detail().bosses().stream().filter(b -> !b.extraList()).count())));
    counts.add(fact("Floor maps",Integer.toString(p.floorMaps().size())));
    counts.add(fact("Related quests",Integer.toString(p.quests().size())));overview.add(counts);
    if(!p.foreverContext().changes().isEmpty())overview.add(new ForeverContextStrip(p.foreverContext()));
    if(InstanceSummary.CLIENT.equals(p.detail().summary().source()))overview.add(new WrappingText(InstancePage.BOSSES_NOTE,"Data coverage"));
    JButton browse=new JButton("Browse bosses & loot →");browse.setAlignmentX(Component.LEFT_ALIGNMENT);
    browse.addActionListener(e -> page.tabs.setSelectedIndex(1));overview.add(browse);
    page.tab("Overview",KeyEvent.VK_O,scroll(overview));
    page.tab("Bosses & loot",KeyEvent.VK_B,encounters);
    JPanel maps=column();maps.add(new InstanceEntrancePanel(p.access(),nav));
    if(!p.floorMaps().isEmpty())maps.add(new JournalAtlasMaps(p.floorMaps(),nav,worker,sink));
    else maps.add(new WrappingText("No floor map recorded for this instance.","Floor map coverage"));
    page.tab("Maps",KeyEvent.VK_M,scroll(maps));
    JPanel quests=column();
    quests.add(heading("Quests in this instance"));
    if(p.quests().isEmpty())quests.add(new WrappingText("No quests recorded for this instance's zone.","Quest coverage"));
    else {
      var table=LinkTable.inline(Columns.quests(),nav);table.setRows(p.quests());quests.add(table);
    }
    page.tab("Quests",KeyEvent.VK_Q,scroll(quests));
    return page;
  }

  private static JComponent item(DetailPanels.LoadedItem p, Navigator nav, SourceFilter filter,
      Consumer<SourceFilter> onFilter) {
    var item=new ItemPage(p.detail(),p.icon(),p.rateSources(),p.bossNpcs(),filter,onFilter,
        p.involvement(),nav,ClipboardPort.system(),List.of(),p.knowledge().stream().filter(k -> k.field().equals("stats")).toList(),p.appearance(),p.foreverContext(),p.nativeMetadata());
    item.setClassicComparison(p.classicComparison());
    var page=new JournalPage(item);
    HeaderPanel header=new HeaderPanel(p.detail().summary().displayName(),"Item #"+p.detail().summary().id());
    p.knowledge().stream().filter(k -> k.field().equals("stats")).findFirst().ifPresent(k -> header.add(new KnowledgeChip(k)));
    page.add(header,BorderLayout.NORTH);
    page.tab("Item & acquisition",KeyEvent.VK_I,item);
    JPanel evidence=column();evidence.add(heading("Understand this item's evidence"));
    evidence.add(new WrappingText("Field states distinguish recorded values, hidden data and missing evidence. Source comparisons retain each provider's version and original values.","Evidence guide"));
    if(!p.knowledge().isEmpty())evidence.add(new FieldKnowledgePanel(p.knowledge()));
    if(!p.sourceDifferences().isEmpty())evidence.add(new SourceDifferencesPanel(p.sourceDifferences()));
    else evidence.add(new WrappingText("No source comparisons recorded for this item.","Source comparison coverage"));
    page.tab("Source evidence"+(p.sourceDifferences().isEmpty()?"":" ("+p.sourceDifferences().size()+")"),KeyEvent.VK_E,scroll(evidence));
    return page;
  }

  private static JPanel column() {
    JPanel panel=new StackPanel();panel.setBorder(BorderFactory.createEmptyBorder(16,18,16,18));return panel;
  }
  private static JLabel heading(String text) {
    JLabel label=new JLabel(text);label.setFont(label.getFont().deriveFont(Font.BOLD,18f));
    label.setBorder(BorderFactory.createEmptyBorder(0,0,12,0));label.setAlignmentX(Component.LEFT_ALIGNMENT);return label;
  }
  private static JPanel fact(String label,String value) {
    JPanel card=new JPanel(new BorderLayout(0,5));card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor") == null ? Color.GRAY : UIManager.getColor("Component.borderColor")),
        BorderFactory.createEmptyBorder(12,12,12,12)));
    JLabel number=new JLabel(value);number.setFont(number.getFont().deriveFont(Font.BOLD,24f));
    card.add(number,BorderLayout.NORTH);card.add(new JLabel(label),BorderLayout.CENTER);return card;
  }
  private static JScrollPane scroll(JComponent content) {
    JScrollPane scroll=new JScrollPane(content);scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.getVerticalScrollBar().setUnitIncrement(20);return scroll;
  }

  private static final class StackPanel extends JPanel implements Scrollable {
    StackPanel() {
      setLayout(new java.awt.LayoutManager() {
        public void addLayoutComponent(String name,Component c) {}
        public void removeLayoutComponent(Component c) {}
        public Dimension preferredLayoutSize(Container parent) {
          Insets in=parent.getInsets();int w=0,h=0;
          for(Component c:parent.getComponents()){w=Math.max(w,c.getPreferredSize().width);h+=c.getPreferredSize().height+12;}
          return new Dimension(w+in.left+in.right,h+in.top+in.bottom);
        }
        public Dimension minimumLayoutSize(Container parent){return new Dimension(0,preferredLayoutSize(parent).height);}
        public void layoutContainer(Container parent){
          Insets in=parent.getInsets();int y=in.top,w=Math.max(0,parent.getWidth()-in.left-in.right);
          for(Component c:parent.getComponents()){int h=c.getPreferredSize().height;c.setBounds(in.left,y,w,h);y+=h+12;}
        }
      });
    }
    public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
    public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 20;}
    public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(20,r.height-20);}
    public boolean getScrollableTracksViewportWidth(){return true;}
    public boolean getScrollableTracksViewportHeight(){return getParent()!=null&&getParent().getHeight()>getPreferredSize().height;}
  }

  /** Tab selection and the original page's encounter/filter/scroll state survive Back/Forward and rerenders. */
  public static final class JournalPage extends JPanel implements PageState {
    private final JTabbedPane tabs=new JTabbedPane();
    private final PageState delegate;
    private record State(int tab,Object detail,int[] scroll) {}
    private JournalPage(PageState delegate) {
      super(new BorderLayout(0,8));this.delegate=delegate;add(tabs,BorderLayout.CENTER);
      tabs.getAccessibleContext().setAccessibleName("Journal detail sections");
      tabs.putClientProperty("JTabbedPane.tabHeight",40);
      tabs.putClientProperty("JTabbedPane.tabType","underlined");
    }
    private void tab(String title,int mnemonic,JComponent content) {
      tabs.addTab(title,content);tabs.setMnemonicAt(tabs.getTabCount()-1,mnemonic);
    }
    public JTabbedPane tabs(){return tabs;}
    @Override public Object saveState() {
      int[] positions=new int[tabs.getTabCount()];
      for(int i=0;i<positions.length;i++)if(tabs.getComponentAt(i) instanceof JScrollPane s)positions[i]=s.getVerticalScrollBar().getValue();
      return new State(tabs.getSelectedIndex(),delegate.saveState(),positions);
    }
    @Override public void restoreState(Object value) {
      if(!(value instanceof State state))return;
      delegate.restoreState(state.detail());
      if(state.tab()>=0 && state.tab()<tabs.getTabCount())tabs.setSelectedIndex(state.tab());
      SwingUtilities.invokeLater(() -> {
        for(int i=0;i<Math.min(state.scroll().length,tabs.getTabCount());i++)
          if(tabs.getComponentAt(i) instanceof JScrollPane s)s.getVerticalScrollBar().setValue(state.scroll()[i]);
      });
    }
  }
}
