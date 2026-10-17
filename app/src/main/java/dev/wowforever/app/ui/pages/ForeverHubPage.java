package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.ForeverContent.*;
import dev.wowforever.app.nav.*;
import dev.wowforever.app.ui.widgets.*;
import java.awt.*;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.*;

/** Named, searchable content differences, computed from two pinned snapshots. */
public final class ForeverHubPage extends ForeverPageLayout {
  private static final int PAGE_SIZE=40;
  private static final Set<String> PLAYER_CONTENT=Set.of("AreaTable","Map","Item","ItemSparse","ItemSet","ChrRaces","Faction","TaxiNodes","LFGDungeons");

  public ForeverHubPage(Hub data,Navigator nav) {
    super("WoW: Forever");
    addRow(new ForeverContextStrip(data.context()));
    JPanel links=new JPanel(new QuickLinksLayout());links.setOpaque(false);
    String[][] systems={{"Dungeons & raids","ladder"},{"PvP","pvp"},{"Talents","talents"},{"Races","races"},
        {"Legacy","legacy"},{"Camping","camping"},{"Class spells","class-spells"}};
    for(var link:systems)links.add(LinkButton.create(link[0],new ViewTarget.Forever(link[1]),nav));
    String[][] research={{"Item sets","sets"},{"Stat glossary","stats"},{"Situational trinkets","trinkets"},
        {"Recipes","recipes"},{"XP food","xp-food"},{"Comprehension scrolls","comprehension"}};
    for(var link:research)links.add(LinkButton.create(link[0],new ViewTarget.ItemContent(link[1]),nav));
    addRow(links);
    JComboBox<String> category=new JComboBox<>(new String[]{"All player content","Areas","Dungeons & maps","Items","Item sets","Races","Factions","Spells & abilities","All tables (advanced)"});
    category.getAccessibleContext().setAccessibleName("Content type");addRow(category);
    JComboBox<String> status=new JComboBox<>(new String[]{"New and changed","New","Changed"});
    status.getAccessibleContext().setAccessibleName("Forever change filter");addRow(status);
    JTextField search=new JTextField();search.getAccessibleContext().setAccessibleName("Search Forever changes by name or ID");
    search.putClientProperty("JTextField.placeholderText","Search by name or ID");addRow(search);
    JPanel paging=new JPanel(new FlowLayout(FlowLayout.LEADING,8,0));paging.setOpaque(false);
    JButton previous=new JButton("Previous"),next=new JButton("Next");JLabel range=new JLabel();
    paging.add(previous);paging.add(range);paging.add(next);addRow(paging);
    JPanel rows=new JPanel(new PageContent.TopStack());rows.setOpaque(false);addRow(rows);
    int[] page={0};
    Runnable refresh=()->{
      String selected=(String)status.getSelectedItem(),type=(String)category.getSelectedItem();
      String query=search.getText().trim().toLowerCase(Locale.ROOT);
      // Search covers the complete snapshot; pagination only limits the rendered components.
      List<Change> matching=data.context().changes().stream()
          .filter(c->matchesCategory(c,type))
          .filter(c->!"New".equals(selected)||"new".equals(c.status()))
          .filter(c->!"Changed".equals(selected)||"changed".equals(c.status()))
          .filter(c->(c.displayName()+" "+c.kind()+" "+c.id()).toLowerCase(Locale.ROOT).contains(query)).toList();
      int max=Math.max(0,(matching.size()-1)/PAGE_SIZE);page[0]=Math.min(page[0],max);
      int from=page[0]*PAGE_SIZE,to=Math.min(matching.size(),from+PAGE_SIZE);
      rows.removeAll();
      for(Change change:matching.subList(from,to)) {
        JPanel row=new JPanel(new PageContent.TopStack());row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(8,0,8,0));
        JPanel heading=new JPanel(new FlowLayout(FlowLayout.LEADING,6,0));heading.setOpaque(false);
        heading.add(new Chip("new".equals(change.status())?"New":"Changed",Chip.Style.PLAIN));
        heading.add(new JLabel(kindLabel(change.kind())));row.add(heading);
        ViewTarget target=target(change,data);
        if(target!=null)row.add(LinkButton.create(change.displayName(),target,nav));
        else row.add(new WrappingText(change.displayName(),"Changed content name"));
        if("changed".equals(change.status()))row.add(new WrappingText("Changed: "+friendlyFields(change.fields()),"Changed fields"));
        row.setToolTipText(change.kind()+" #"+change.id()+" · "+change.fields());rows.add(row);
      }
      if(matching.isEmpty())rows.add(new WrappingText("No matching recorded differences. Missing comparisons do not imply unchanged data.","Comparison coverage"));
      range.setText(matching.isEmpty()?"0 results":(from+1)+"–"+to+" of "+matching.size());
      previous.setEnabled(page[0]>0);next.setEnabled(page[0]<max);rows.revalidate();rows.repaint();
    };
    previous.addActionListener(e->{page[0]--;refresh.run();});next.addActionListener(e->{page[0]++;refresh.run();});
    category.addActionListener(e->{page[0]=0;refresh.run();});status.addActionListener(e->{page[0]=0;refresh.run();});
    search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
      private void update(){page[0]=0;refresh.run();}
      public void insertUpdate(javax.swing.event.DocumentEvent e){update();}
      public void removeUpdate(javax.swing.event.DocumentEvent e){update();}
      public void changedUpdate(javax.swing.event.DocumentEvent e){update();}
    });refresh.run();
  }

  private static boolean matchesCategory(Change change,String category) {
    return switch(category) {
      case "Areas" -> change.kind().equals("AreaTable");
      case "Dungeons & maps" -> Set.of("Map","LFGDungeons").contains(change.kind());
      case "Items" -> Set.of("Item","ItemSparse").contains(change.kind());
      case "Item sets" -> change.kind().equals("ItemSet");
      case "Races" -> change.kind().equals("ChrRaces");
      case "Factions" -> change.kind().equals("Faction");
      case "Spells & abilities" -> Set.of("SpellName","SkillLineAbility","Talent","TalentTab").contains(change.kind());
      case "All tables (advanced)" -> true;
      default -> PLAYER_CONTENT.contains(change.kind());
    };
  }

  static String kindLabel(String kind) {
    return switch(kind) {
      case "AreaTable" -> "Area";case "Map" -> "Map";case "Item","ItemSparse" -> "Item";
      case "ItemSet" -> "Item set";case "ChrRaces" -> "Race";case "LFGDungeons" -> "Dungeon finder entry";
      case "TaxiNodes" -> "Flight point";case "SkillLineAbility" -> "Ability";case "SpellName" -> "Spell";
      default -> kind;
    };
  }
  private static ViewTarget target(Change change,Hub hub) {
    if(change.ref()!=null)return new ViewTarget.Entity(change.ref());
    long id;try{id=Long.parseLong(change.id());}catch(NumberFormatException e){return null;}
    if("ItemSet".equals(change.kind()))return new ViewTarget.ItemContent("set",id);
    if("Map".equals(change.kind())){
      var instance=hub.instances().stream().filter(i->("client:"+change.id()).equals(i.key())).findFirst();
      if(instance.isPresent())return new ViewTarget.Entity(instance.get().ref());
      if(hub.discoveries().stream().anyMatch(d->d.mapId()==id))return new ViewTarget.Forever("discovery",id);
    }
    if("Faction".equals(change.kind())&&hub.pvp().factions().stream().anyMatch(f->f.id()==id))return new ViewTarget.Forever("pvp-faction",id);
    return null;
  }
  static String friendlyFields(String fields) {
    if(fields==null||fields.isBlank()||fields.equals("[]"))return "recorded client fields";
    return fields.replace("[","").replace("]","").replace("\"","").replace("_lang","")
        .replaceAll("([a-z])([A-Z])","$1 $2").replace('_',' ').replaceAll(",\\s*",", ");
  }

  /** Four columns on a wide page, two on compact; each link stays inside the viewport. */
  private static final class QuickLinksLayout implements LayoutManager {
    public void addLayoutComponent(String name,Component c){}public void removeLayoutComponent(Component c){}
    private int columns(Container p){return p.getWidth()>650?4:2;}
    private int height(Container p){int h=20;for(var c:p.getComponents())h=Math.max(h,c.getPreferredSize().height+4);return h;}
    public Dimension preferredLayoutSize(Container p){int n=columns(p);return new Dimension(360,((p.getComponentCount()+n-1)/n)*height(p));}
    public Dimension minimumLayoutSize(Container p){return new Dimension(0,preferredLayoutSize(p).height);}
    public void layoutContainer(Container p){int n=columns(p),w=Math.max(0,(p.getWidth()-(n-1)*10)/n),h=height(p);int i=0;
      for(var c:p.getComponents()){c.setBounds((i%n)*(w+10),(i/n)*h,w,h);i++;}}
  }
}
