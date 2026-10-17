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
  private final JPanel rows=JournalSurface.grid(), selection=JournalSurface.stack();
  private final JLabel count=new JLabel();
  private final JButton previous=new JButton("Previous"),next=new JButton("Next");
  private final JScrollPane scroll;
  private final List<Entry> entries;
  private final Navigator nav;
  private int page;
  private String selected;
  private boolean restoring;
  public record State(String query,int scope,int kind,int level,int knowledge,int page,String selected,int scroll) {}
  private record Entry(String key,String name,String type,Integer min,Integer max,String zone,boolean forever,boolean reported,
                       ViewTarget target,String source,String details) {}

  public JournalDungeonPage(JournalCatalog.DungeonSnapshot data,Navigator nav) {
    super(new BorderLayout());this.nav=nav;entries=entries(data);
    JournalSurface.Body body=new JournalSurface.Body();scroll=JournalSurface.scroll(body);add(scroll);
    JournalSurface.add(body,JournalSurface.heading("Dungeons & raids",28));
    JournalSurface.add(body,JournalSurface.copy("Find your next run. Known instances and reported discoveries are labelled separately, with the source kept in view."));
    search.putClientProperty("JTextField.placeholderText","Search dungeon, raid, or zone…");
    search.getAccessibleContext().setAccessibleName("Search dungeons, raids, and zones");
    JournalSurface.add(body,search);
    JPanel filters=JournalSurface.grid();filter(filters,"Source",scope);filter(filters,"Content",kind);filter(filters,"Level range",level);filter(filters,"Evidence",knowledge);
    JournalSurface.add(body,filters);JournalSurface.add(body,count);JournalSurface.add(body,selection);JournalSurface.add(body,rows);
    JPanel paging=new JPanel(new FlowLayout(FlowLayout.LEADING));paging.setOpaque(false);paging.add(previous);paging.add(next);JournalSurface.add(body,paging);
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
    result.sort(Comparator.comparing((Entry e)->!e.forever()).thenComparing(Entry::name,String.CASE_INSENSITIVE_ORDER));return List.copyOf(result);
  }
  private void changed(){if(!restoring){page=0;refresh();}}
  private void refresh(){
    String query=search.getText().strip().toLowerCase(Locale.ROOT);
    List<Entry> matching=entries.stream().filter(e->(e.name()+" "+e.zone()).toLowerCase(Locale.ROOT).contains(query))
        .filter(e->scope.getSelectedIndex()==0||(scope.getSelectedIndex()==1)==e.forever())
        .filter(e->kind.getSelectedIndex()==0||(kind.getSelectedIndex()==2)==e.type().toUpperCase(Locale.ROOT).contains("RAID"))
        .filter(this::levelMatches).filter(e->knowledge.getSelectedIndex()==0||(knowledge.getSelectedIndex()==2)==e.reported()).toList();
    int max=Math.max(0,(matching.size()-1)/PAGE_SIZE);page=Math.max(0,Math.min(page,max));int from=page*PAGE_SIZE,to=Math.min(from+PAGE_SIZE,matching.size());
    count.setText(matching.isEmpty()?"No matching dungeons or raids":(from+1)+"–"+to+" of "+matching.size()+" · select a card to preview");
    rows.removeAll();selection.removeAll();
    matching.stream().filter(e->e.key().equals(selected)).findFirst().ifPresent(this::showSelection);
    for(Entry e:matching.subList(from,to)){
      JPanel card=JournalSurface.card();
      JLabel label=JournalSurface.heading(e.forever()?"FOREVER" : "CLASSIC",11);label.setForeground(Theme.current().goldText());JournalSurface.add(card,label);
      JournalSurface.add(card,JournalSurface.title(e.name(),18));JournalSurface.add(card,JournalSurface.copy(description(e)));
      JButton preview=new JButton(e.key().equals(selected)?"Selected · "+e.name():"Preview "+e.name());preview.addActionListener(event->{selected=e.key();refresh();});JournalSurface.add(card,preview);
      JButton open=new JButton("Open dungeon guide");open.getAccessibleContext().setAccessibleName("Open "+e.name()+" guide");open.addActionListener(event->nav.open(e.target()));JournalSurface.add(card,open);rows.add(card);
    }
    if(matching.isEmpty()){
      JPanel empty=JournalSurface.card();JournalSurface.add(empty,JournalSurface.heading("Try a wider search",18));
      JournalSurface.add(empty,JournalSurface.copy("Search by name or zone, or reset the filters. An empty result is not proof that content does not exist."));
      JButton reset=new JButton("Reset filters");reset.addActionListener(e->restoreState(new State("",0,0,0,0,0,null,0)));JournalSurface.add(empty,reset);rows.add(empty);
    }
    previous.setEnabled(page>0);next.setEnabled(page<max);revalidate();repaint();
  }
  private void showSelection(Entry e){
    JPanel card=JournalSurface.card();JournalSurface.add(card,JournalSurface.title(e.name(),22));JournalSurface.add(card,JournalSurface.copy(description(e)));
    JournalSurface.add(card,JournalSurface.copy(e.details()));JournalSurface.add(card,JournalSurface.copy("Source: "+e.source()));
    JButton open=new JButton("Explore encounters, maps & loot");open.addActionListener(event->nav.open(e.target()));JournalSurface.add(card,open);selection.add(card);selection.add(Box.createVerticalStrut(12));
  }
  private boolean levelMatches(Entry e){
    int selected=level.getSelectedIndex();if(selected==0)return true;if(selected==4)return e.min()==null&&e.max()==null;
    if(e.min()==null&&e.max()==null)return false;int low=selected==1?1:selected==2?21:41,high=selected==1?20:selected==2?40:60;
    return (e.min()==null?e.max():e.min())<=high&&(e.max()==null?e.min():e.max())>=low;
  }
  private static String description(Entry e){
    String levels=e.min()==null&&e.max()==null?"Level not recorded":e.min()==null?"Up to level "+e.max():e.max()==null?"Level "+e.min()+"+":"Levels "+e.min()+"–"+e.max();
    return (e.reported()?"Reported discovery":"Known instance")+" · "+(e.type().toUpperCase(Locale.ROOT).contains("RAID")?"Raid":"Dungeon")+" · "+levels+(e.zone().isBlank()?"":" · "+e.zone());
  }
  public Object saveState(){return new State(search.getText(),scope.getSelectedIndex(),kind.getSelectedIndex(),level.getSelectedIndex(),knowledge.getSelectedIndex(),page,selected,scroll.getVerticalScrollBar().getValue());}
  public void restoreState(Object state){if(!(state instanceof State s))return;restoring=true;
    search.setText(s.query());scope.setSelectedIndex(s.scope());kind.setSelectedIndex(s.kind());level.setSelectedIndex(s.level());knowledge.setSelectedIndex(s.knowledge());page=s.page();selected=s.selected();restoring=false;refresh();
    SwingUtilities.invokeLater(()->scroll.getVerticalScrollBar().setValue(s.scroll()));
  }
}
