package dev.wowforever.app.ui.journal;

import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.*;
import dev.wowforever.app.ui.pages.PageState;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.*;
import java.util.List;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;

/** Searchable, unified dungeon index. Reported discoveries stay distinct from known instances. */
public final class JournalDungeonPage extends JPanel implements PageState {
  private static final int PAGE_SIZE=8;
  private final JTextField search=new JTextField();
  private final JComboBox<String> scope=new JComboBox<>(new String[]{"All sources","Forever","Classic"});
  private final JComboBox<String> kind=new JComboBox<>(new String[]{"Dungeons & raids","Dungeons","Raids"});
  private final JComboBox<String> level=new JComboBox<>(new String[]{"Any level","1–20","21–40","41–60","Level not recorded"});
  private final JComboBox<String> knowledge=new JComboBox<>(new String[]{"Known & reported","Known instances","Reported discoveries"});
  private final JournalSurface.Body rows=new JournalSurface.Body();
  private final JournalSurface.Body selection=new JournalSurface.Body();
  private final JPanel detailHost=new JPanel(new BorderLayout());
  private final JPanel workspace=new JPanel(new WorkspaceLayout());
  private final JPanel modeBar=new JPanel(new FlowLayout(FlowLayout.LEADING,8,0));
  private final JToggleButton destinations=new JToggleButton("Destinations"),guideMode=new JToggleButton("Selected guide");
  private final JComponent attachedGuide;
  private final Long attachedInstance;
  private boolean compactDetail;
  private final JLabel count=new JLabel();
  private final JButton previous=new JButton("Previous"),next=new JButton("Next");
  private final JScrollPane scroll;
  private final JScrollPane previewScroll;
  private final List<Entry> entries;
  private final Navigator nav;
  private int page;
  private String selected;
  private boolean restoring;
  public record State(String query,int scope,int kind,int level,int knowledge,int page,String selected,int scroll) {}
  private record Entry(String key,String name,String type,Integer min,Integer max,String zone,boolean forever,boolean reported,
                       ViewTarget target,String source,String details) {}

  /** Standalone index. All rows come from the supplied worker snapshot. */
  public JournalDungeonPage(JournalCatalog.DungeonSnapshot data,Navigator nav) {
    this(data,nav,null,null);
  }
  /** Keeps destination navigation beside an already loaded, database-free guide component. */
  public JournalDungeonPage(JournalCatalog.DungeonSnapshot data,Navigator nav,long selectedInstanceId,JComponent guide) {
    this(data,nav,Long.valueOf(selectedInstanceId),guide);
  }
  private JournalDungeonPage(JournalCatalog.DungeonSnapshot data,Navigator nav,Long instanceId,JComponent guide) {
    super(new BorderLayout(0,14));this.nav=nav;entries=entries(data);attachedGuide=guide;attachedInstance=instanceId;
    if(instanceId!=null){selected="instance:"+instanceId;compactDetail=true;}
    setBorder(BorderFactory.createEmptyBorder(20,24,24,24));
    JPanel header=JournalSurface.stack();
    JournalSurface.add(header,JournalSurface.heading("Dungeons & raids",32));
    JournalSurface.add(header,JournalSurface.copy("Choose a destination, then explore its encounters, loot and maps without losing your place."));
    search.putClientProperty("JTextField.placeholderText","Search dungeon, raid, or zone…");
    search.getAccessibleContext().setAccessibleName("Search dungeons, raids, and zones");
    search.putClientProperty("JComponent.minimumHeight",36);JournalSurface.add(header,search);
    JPanel filters=new JPanel(new FilterLayout());filters.setOpaque(false);filter(filters,"Source",scope);filter(filters,"Content",kind);filter(filters,"Level range",level);filter(filters,"Evidence",knowledge);
    JournalSurface.add(header,filters);JournalSurface.add(header,count);
    modeBar.setOpaque(false);ButtonGroup modes=new ButtonGroup();modes.add(destinations);modes.add(guideMode);
    destinations.setSelected(!compactDetail);guideMode.setSelected(compactDetail);
    destinations.getAccessibleContext().setAccessibleDescription("Show the dungeon destination list in a compact window");
    guideMode.getAccessibleContext().setAccessibleDescription("Show the selected dungeon guide in a compact window");
    destinations.addActionListener(e->{compactDetail=false;workspace.revalidate();workspace.repaint();});
    guideMode.addActionListener(e->{compactDetail=true;workspace.revalidate();workspace.repaint();});
    modeBar.add(destinations);modeBar.add(guideMode);header.add(modeBar);add(header,BorderLayout.NORTH);
    rows.setBorder(BorderFactory.createEmptyBorder(0,0,8,8));scroll=JournalSurface.scroll(rows);
    scroll.getAccessibleContext().setAccessibleName("Dungeon destinations");
    JPanel results=new JPanel(new BorderLayout(0,8));results.setOpaque(false);results.add(scroll);
    JPanel paging=new JPanel(new FlowLayout(FlowLayout.LEADING,4,0));paging.setOpaque(false);paging.add(previous);paging.add(next);results.add(paging,BorderLayout.SOUTH);
    selection.setBorder(BorderFactory.createEmptyBorder());
    previewScroll=new JScrollPane(selection,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    previewScroll.setBorder(BorderFactory.createEmptyBorder());previewScroll.getVerticalScrollBar().setUnitIncrement(24);
    detailHost.setOpaque(false);detailHost.add(previewScroll);
    workspace.setOpaque(false);workspace.add(results);workspace.add(detailHost);add(workspace);
    for(JComboBox<String> combo:List.of(scope,kind,level,knowledge))combo.addActionListener(e->changed());
    search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){changed();}public void removeUpdate(DocumentEvent e){changed();}public void changedUpdate(DocumentEvent e){changed();}});
    previous.addActionListener(e->{page--;refresh();});next.addActionListener(e->{page++;refresh();});refresh();
  }
  private static void filter(JPanel parent,String text,JComboBox<String> combo){
    JPanel field=JournalSurface.stack();JLabel label=new JLabel(text);label.setLabelFor(combo);combo.getAccessibleContext().setAccessibleName(text);
    JournalSurface.add(field,label);field.add(combo);parent.add(field);
  }
  private static List<Entry> entries(JournalCatalog.DungeonSnapshot data){
    List<Entry> result=new ArrayList<>();Set<Long> knownMaps=new HashSet<>();
    for(InstanceSummary i:data.instances()){
      boolean forever=InstanceSummary.CLIENT.equals(i.source());
      if(i.key()!=null&&i.key().startsWith(InstanceSummary.CLIENT_KEY_PREFIX))try{knownMaps.add(Long.parseLong(i.key().substring(InstanceSummary.CLIENT_KEY_PREFIX.length())));}catch(NumberFormatException ignored){}
      result.add(new Entry("instance:"+i.id(),i.displayName(),Objects.toString(i.contentType(),"DUNGEON"),i.levelMin(),i.levelMax(),Objects.toString(i.zoneName(),""),forever,false,
          new ViewTarget.Entity(i.ref()),forever?"Forever client":"AtlasLoot · Classic",forever?"The client records this instance. Boss and loot evidence is labelled on its detail page.":"Classic encounters and loot are supplied by AtlasLoot. Source reports remain visible on the detail page."));
    }
    for(ForeverContent.Discovery d:data.discoveries())if(!knownMaps.contains(d.mapId())){
      String encounters=d.encounters().isEmpty()?"No encounters recorded in the archived sources.":"Reported encounters: "+String.join(", ",d.encounters().stream().map(ForeverContent.Encounter::name).toList())+".";
      result.add(new Entry("discovery:"+d.mapId(),d.name(),Objects.toString(d.type(),"DUNGEON"),null,null,"",true,true,
          new ViewTarget.Forever("discovery",d.mapId()),"Forever discovery · build "+d.build(),encounters+" Reported presence does not establish a complete boss list or loot table."));
    }
    result.sort(Comparator.comparing((Entry e)->!e.forever()).thenComparing(Entry::reported).thenComparing(Entry::name,String.CASE_INSENSITIVE_ORDER));return List.copyOf(result);
  }
  private void changed(){if(!restoring){page=0;refresh();}}
  private void refresh(){
    String query=search.getText().strip().toLowerCase(Locale.ROOT);
    List<Entry> matching=entries.stream().filter(e->(e.name()+" "+e.zone()).toLowerCase(Locale.ROOT).contains(query))
        .filter(e->scope.getSelectedIndex()==0||(scope.getSelectedIndex()==1)==e.forever())
        .filter(e->kind.getSelectedIndex()==0||(kind.getSelectedIndex()==2)==e.type().toUpperCase(Locale.ROOT).contains("RAID"))
        .filter(this::levelMatches).filter(e->knowledge.getSelectedIndex()==0||(knowledge.getSelectedIndex()==2)==e.reported()).toList();
    int max=Math.max(0,(matching.size()-1)/PAGE_SIZE);page=Math.max(0,Math.min(page,max));int from=page*PAGE_SIZE,to=Math.min(from+PAGE_SIZE,matching.size());
    count.setText(matching.isEmpty()?"No matching dungeons or raids":(from+1)+"–"+to+" of "+matching.size()+" destinations");
    rows.removeAll();selection.removeAll();detailHost.removeAll();
    Entry active=matching.stream().filter(e->e.key().equals(selected)).findFirst().orElse(matching.isEmpty()?null:matching.get(from));
    if(attachedGuide!=null) {
      detailHost.add(attachedGuide); // The actual guide owns its encounter, tab, and loot state.
    }else {
      if(active!=null)showSelection(active);
      else JournalSurface.add(selection,JournalSurface.copy("No destination matches these filters. Broaden the search to continue exploring."));
      detailHost.add(previewScroll);
    }
    String activeKey=attachedInstance==null?(active==null?null:active.key()):"instance:"+attachedInstance;
    for(Entry e:matching.subList(from,to)){
      boolean chosen=e.key().equals(activeKey);
      JButton row=new JButton();row.setLayout(new BorderLayout(0,5));row.setHorizontalAlignment(SwingConstants.LEADING);
      row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(chosen?Theme.current().link():Theme.current().hairline()),BorderFactory.createEmptyBorder(14,12,14,12)));
      row.setBackground(chosen?Theme.current().selection():Theme.current().bgRaised());row.setOpaque(true);row.setSelected(chosen);
      var name=JournalSurface.title(e.name(),18);name.setFocusable(false);row.add(name,BorderLayout.NORTH);
      var subtitle=JournalSurface.copy((e.reported()?"Reported discovery":e.forever()?"Forever · client-listed":"Classic · AtlasLoot")+" · "+levelLabel(e));
      subtitle.setFocusable(false);row.add(subtitle);
      row.setToolTipText(description(e));row.getAccessibleContext().setAccessibleName((chosen?"Selected: ":"")+e.name());
      row.getAccessibleContext().setAccessibleDescription(description(e)+". "+(attachedGuide==null?"Preview this destination.":"Open this dungeon guide."));
      // Text children must not intercept the destination button's mouse clicks.
      java.awt.event.MouseAdapter click=new java.awt.event.MouseAdapter(){@Override public void mouseClicked(java.awt.event.MouseEvent event){if(SwingUtilities.isLeftMouseButton(event))row.doClick();}};
      name.addMouseListener(click);subtitle.addMouseListener(click);
      row.addActionListener(event->{
        if(attachedGuide!=null&&!e.key().equals("instance:"+attachedInstance)){nav.open(e.target());return;}
        selected=e.key();compactDetail=true;destinations.setSelected(false);guideMode.setSelected(true);refresh();
      });
      JournalSurface.add(rows,row);
    }
    if(matching.isEmpty()){
      JPanel empty=JournalSurface.card();JournalSurface.add(empty,JournalSurface.heading("Try a wider search",18));
      JournalSurface.add(empty,JournalSurface.copy("Search by name or zone, or reset the filters. An empty result does not prove that content does not exist."));
      JButton reset=new JButton("Reset filters");reset.addActionListener(e->restoreState(new State("",0,0,0,0,0,null,0)));JournalSurface.add(empty,reset);rows.add(empty);
    }
    previous.setEnabled(page>0);next.setEnabled(page<max);guideMode.setEnabled(active!=null||attachedGuide!=null);
    rows.revalidate();rows.repaint();detailHost.revalidate();detailHost.repaint();revalidate();repaint();
  }
  private void showSelection(Entry e){
    JPanel card=JournalSurface.card();
    JLabel evidence=new JLabel(e.reported()?"REPORTED DISCOVERY":e.forever()?"FOREVER · CLIENT-LISTED":"CLASSIC · ATLASLOOT");evidence.setForeground(Theme.current().goldText());
    JournalSurface.add(card,evidence);JournalSurface.add(card,JournalSurface.title(e.name(),30));
    JournalSurface.add(card,JournalSurface.copy(description(e)));
    JournalSurface.add(card,JournalSurface.copy(e.details()));
    JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEADING,0,0));actions.setOpaque(false);
    JButton open=new JButton("Open dungeon guide");open.setMargin(new Insets(10,14,10,14));open.getAccessibleContext().setAccessibleName("Open "+e.name()+" guide");open.addActionListener(event->nav.open(e.target()));actions.add(open);JournalSurface.add(card,actions);
    JButton explore=new JButton("Explore encounters, maps & loot");explore.setMargin(new Insets(8,12,8,12));explore.addActionListener(event->nav.open(e.target()));
    JPanel more=new JPanel(new FlowLayout(FlowLayout.LEADING,0,0));more.setOpaque(false);more.add(explore);JournalSurface.add(card,more);
    JPanel evidencePanel=JournalSurface.stack();evidencePanel.setVisible(false);
    JournalSurface.add(evidencePanel,JournalSurface.copy("Source: "+e.source()));
    JournalSurface.add(evidencePanel,JournalSurface.copy(e.reported()?"Reported discoveries are not promoted to confirmed instance or loot records.":"Known identifies the instance record. It does not verify every boss, loot item, or drop rate."));
    JToggleButton sources=new JToggleButton("About this destination’s sources");sources.addActionListener(event->{evidencePanel.setVisible(sources.isSelected());card.revalidate();card.repaint();});
    JournalSurface.add(card,sources);JournalSurface.add(card,evidencePanel);selection.add(card);
  }
  private boolean levelMatches(Entry e){
    int selected=level.getSelectedIndex();if(selected==0)return true;if(selected==4)return e.min()==null&&e.max()==null;
    if(e.min()==null&&e.max()==null)return false;int low=selected==1?1:selected==2?21:41,high=selected==1?20:selected==2?40:60;
    return (e.min()==null?e.max():e.min())<=high&&(e.max()==null?e.min():e.max())>=low;
  }
  private static String description(Entry e){
    return (e.reported()?"Reported discovery":"Known instance")+" · "+(e.type().toUpperCase(Locale.ROOT).contains("RAID")?"Raid":"Dungeon")+" · "+levelLabel(e)+(e.zone().isBlank()?"":" · "+e.zone());
  }
  private static String levelLabel(Entry e){
    return e.min()==null&&e.max()==null?"Level not recorded":e.min()==null?"Up to level "+e.max():e.max()==null?"Level "+e.min()+"+":"Levels "+e.min()+"–"+e.max();
  }
  private record WorkspaceState(State index,boolean compactDetail,Object guide) {}
  /** Destination filters can be shared across different instance targets without copying boss or tab state. */
  public State destinationState(){return new State(search.getText(),scope.getSelectedIndex(),kind.getSelectedIndex(),level.getSelectedIndex(),knowledge.getSelectedIndex(),page,selected,scroll.getVerticalScrollBar().getValue());}
  public Object saveState(){
    State index=destinationState();
    return attachedGuide==null?index:new WorkspaceState(index,compactDetail,attachedGuide instanceof PageState p?p.saveState():null);
  }
  public void restoreState(Object state){
    if(state instanceof WorkspaceState w){compactDetail=w.compactDetail();if(attachedGuide instanceof PageState p)p.restoreState(w.guide());state=w.index();}
    if(!(state instanceof State s))return;restoring=true;
    search.setText(s.query());scope.setSelectedIndex(s.scope());kind.setSelectedIndex(s.kind());level.setSelectedIndex(s.level());knowledge.setSelectedIndex(s.knowledge());page=s.page();selected=attachedInstance==null?s.selected():"instance:"+attachedInstance;restoring=false;refresh();
    SwingUtilities.invokeLater(()->scroll.getVerticalScrollBar().setValue(s.scroll()));
  }
  private static final class FilterLayout implements LayoutManager {
    public void addLayoutComponent(String name,Component child){}public void removeLayoutComponent(Component child){}
    public Dimension minimumLayoutSize(Container parent){return new Dimension(0,preferredLayoutSize(parent).height);}
    public Dimension preferredLayoutSize(Container parent){return measure(parent,false);}
    public void layoutContainer(Container parent){measure(parent,true);}
    private Dimension measure(Container parent,boolean layout){
      int columns=parent.getWidth()>=900?4:parent.getWidth()>=280?2:1,gap=12,w=Math.max(0,(parent.getWidth()-gap*(columns-1))/columns),y=0;
      for(int i=0;i<parent.getComponentCount();i+=columns){int height=0;
        for(int j=i;j<Math.min(parent.getComponentCount(),i+columns);j++){
          Component child=parent.getComponent(j);child.setSize(w,Math.max(1,child.getHeight()));if(child instanceof Container nested)nested.doLayout();height=Math.max(height,child.getPreferredSize().height);
        }
        if(layout)for(int j=i;j<Math.min(parent.getComponentCount(),i+columns);j++)parent.getComponent(j).setBounds((j-i)*(w+gap),y,w,height);
        y+=height+gap;
      }
      return new Dimension(Math.max(0,parent.getWidth()),Math.max(0,y-gap));
    }
  }
  /** Stable destinations beside the guide; compact windows switch panes explicitly. */
  private final class WorkspaceLayout implements LayoutManager {
    public void addLayoutComponent(String name,Component child){}public void removeLayoutComponent(Component child){}
    public Dimension minimumLayoutSize(Container parent){return new Dimension(0,240);}
    public Dimension preferredLayoutSize(Container parent){return new Dimension(900,450);}
    public void layoutContainer(Container parent){
      boolean wide=parent.getWidth()>=800;int width=parent.getWidth(),height=parent.getHeight();
      Component results=parent.getComponent(0),detail=parent.getComponent(1);
      if(modeBar.isVisible()==wide){modeBar.setVisible(!wide);JournalDungeonPage.this.revalidate();}
      results.setVisible(wide||!compactDetail);detail.setVisible(wide||compactDetail);
      if(wide){int rail=245,gap=18;results.setBounds(0,0,rail,height);detail.setBounds(rail+gap,0,Math.max(0,width-rail-gap),height);}
      else {results.setBounds(0,0,width,height);detail.setBounds(0,0,width,height);}
    }
  }

}
