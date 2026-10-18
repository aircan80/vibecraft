package dev.wowforever.app.ui.journal;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.pages.PageState;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/** Native city editorial wrapper; the original interactive map/records guide stays intact. */
public final class JournalCityPage extends JPanel implements PageState {
  private final JTabbedPane tabs=new JTabbedPane();
  private final JComponent guide;
  private final JScrollPane overview;
  public JournalCityPage(JournalCities.GuideSnapshot data,JComponent guide,Navigator nav,BufferedImage mapArt) {
    this(data,guide,nav,mapArt,null);
  }
  /** The optional music component is supplied by the app's asynchronous audio controller. */
  public JournalCityPage(JournalCities.GuideSnapshot data,JComponent guide,Navigator nav,BufferedImage mapArt,JComponent music) {
    super(new BorderLayout(0,12));this.guide=guide;setBorder(BorderFactory.createEmptyBorder(12,12,0,12));
    JPanel hero=JournalSurface.card();
    JLabel eyebrow=new JLabel("AZEROTH  /  CITY GUIDE");eyebrow.setFont(eyebrow.getFont().deriveFont(11f));JournalSurface.add(hero,eyebrow);
    JournalSurface.add(hero,JournalSurface.title(data.city().name(),34));
    JournalSurface.add(hero,JournalSurface.copy("Take the scenic route. Explore the city, follow its districts, and discover the people and stories on your map."));
    JPanel controls=JournalSurface.grid(music==null?2:3,700);
    JButton cities=new JButton("All cities");cities.addActionListener(e->nav.open(new ViewTarget.Forever("cities")));controls.add(cities);
    JButton openMap=new JButton("Open city map");openMap.addActionListener(e->tabs.setSelectedIndex(1));controls.add(openMap);
    if(music!=null)controls.add(music);JournalSurface.add(hero,controls);add(hero,BorderLayout.NORTH);

    JournalSurface.Body body=new JournalSurface.Body();overview=JournalSurface.scroll(body);
    if(mapArt!=null)JournalSurface.add(body,new MapPreview(mapArt,data.city().name(),()->tabs.setSelectedIndex(1)));
    JPanel columns=JournalSurface.grid(2,800);
    JPanel districts=JournalSurface.card();JournalSurface.add(districts,JournalSurface.title("A city worth exploring",25));
    JournalSurface.add(districts,JournalSurface.copy("Follow a district into its guide, or open the city map to explore the complete local index."));
    districtRows(districts,data,nav);columns.add(districts);
    JPanel discover=JournalSurface.card();JournalSurface.add(discover,JournalSurface.title("Make yourself at home",25));
    JournalSurface.add(discover,JournalSurface.copy("The map and records bring local inhabitants, quest connections and places together. Select a map marker to inspect its details without losing your place."));
    JButton records=new JButton("Explore map & records");records.addActionListener(e->tabs.setSelectedIndex(1));JournalSurface.add(discover,records);
    JButton services=new JButton("People & services");services.addActionListener(e->tabs.setSelectedIndex(3));JournalSurface.add(discover,services);
    JournalSurface.add(discover,JournalSurface.copy("Forever client build "+data.build()+" · District names come from the native AreaTable. This directory lists archived child areas; it does not claim every city district or service is available."));
    columns.add(discover);JournalSurface.add(body,columns);
    tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);tabs.addTab("Overview",overview);tabs.addTab("Map & records",guide);
    JournalSurface.Body districtBody=new JournalSurface.Body();JournalSurface.add(districtBody,JournalSurface.title("District directory",28));districtRows(districtBody,data,nav);
    tabs.addTab("Districts",JournalSurface.scroll(districtBody));
    tabs.addTab("People & services",services(data,nav));add(tabs,BorderLayout.CENTER);
  }
  private static JComponent services(JournalCities.GuideSnapshot data,Navigator nav) {
    JournalSurface.Body body=new JournalSurface.Body();
    JournalSurface.add(body,JournalSurface.title("People & services",28));
    JournalSurface.add(body,JournalSurface.copy("Recorded vendor stock and quest connections at "+data.city().name()+" placements. Questie NPC locations and quest relations, plus archived vendor relations, are separate from Forever native geography; this is not complete city service coverage. District-only placements remain in their district guides."));
    var snapshot=data.services();
    JournalSurface.add(body,JournalSurface.copy("Showing "+snapshot.people().size()+" recorded people"+(snapshot.more()?" · first "+dev.wowforever.app.db.CityServicesRepository.PEOPLE_LIMIT+" alphabetically":"")));
    JPanel filters=JournalSurface.grid(3,700);
    ButtonGroup group=new ButtonGroup();String[] names={"All recorded people","Vendors","Quest connections"};
    java.util.List<JPanel> cards=new java.util.ArrayList<>();
    for(int i=0;i<names.length;i++){final int mode=i;JToggleButton filter=new JToggleButton(names[i],i==0);group.add(filter);filters.add(filter);
      filter.addActionListener(e->{for(int n=0;n<cards.size();n++){var person=snapshot.people().get(n);cards.get(n).setVisible(mode==0||(mode==1?person.stockCount()>0:person.questCount()>0));}body.revalidate();body.repaint();});}
    JournalSurface.add(body,filters);
    for(var person:snapshot.people()) {
      JPanel card=JournalSurface.card();cards.add(card);
      JButton npc=new JButton(label(person.name(),"NPC",person.id())+"  ›");
      npc.addActionListener(e->nav.open(new ViewTarget.Entity(new EntityRef(EntityKind.NPC,person.id()))));JournalSurface.add(card,npc);
      JournalSurface.add(card,JournalSurface.copy(person.locations().isEmpty()?"City placement recorded; valid coordinates unavailable":"Recorded city coordinates: "+String.join(" · ",person.locations())+" (up to 4)"));
      if(person.stockCount()>0)JournalSurface.add(card,JournalSurface.copy("Vendor stock · "+person.stockCount()+" linked items"));
      for(var item:person.stock()) {JButton link=new JButton(label(item.name(),"Item",item.id()));link.addActionListener(e->nav.open(new ViewTarget.Entity(new EntityRef(EntityKind.ITEM,item.id()))));JournalSurface.add(card,link);}
      if(person.stockCount()>person.stock().size())JournalSurface.add(card,JournalSurface.copy("First 12 items shown; open the NPC for all stock."));
      if(person.questCount()>0)JournalSurface.add(card,JournalSurface.copy("Quest connections · "+person.questCount()+" recorded roles"));
      for(var quest:person.quests()){JButton link=new JButton(quest.role()+": "+label(quest.name(),"Quest",quest.id()));link.addActionListener(e->nav.open(new ViewTarget.Entity(new EntityRef(EntityKind.QUEST,quest.id()))));JournalSurface.add(card,link);}
      if(person.questCount()>person.quests().size())JournalSurface.add(card,JournalSurface.copy("First 12 connections shown; open the NPC for more."));
      JournalSurface.add(body,card);
    }
    if(snapshot.people().isEmpty())JournalSurface.add(body,JournalSurface.copy("No people with linked vendor stock or quests have recorded placements in this exact city area. This does not mean those services are absent in game."));
    return JournalSurface.scroll(body);
  }
  private static String label(String name,String kind,long id){return name==null||name.isBlank()?kind+" #"+id:name;}
  private static void districtRows(JPanel parent,JournalCities.GuideSnapshot data,Navigator nav) {
    if(data.districts().isEmpty()){JournalSurface.add(parent,JournalSurface.copy("The archived client does not enumerate child districts for this city. Explore its map and local records instead."));return;}
    for(var district:data.districts()) {
      if(district.guideAvailable()) {
        JButton button=new JButton(district.name()+"  ›");button.setHorizontalAlignment(SwingConstants.LEADING);button.setMargin(new Insets(10,12,10,12));
        button.addActionListener(e->nav.open(new ViewTarget.Entity(new EntityRef(EntityKind.ZONE,district.id()))));JournalSurface.add(parent,button);
      }else JournalSurface.add(parent,JournalSurface.copy(district.name()+" · Client area; local guide not indexed"));
    }
  }
  private record State(int tab,int scroll,Object guideState) {}
  public Object saveState(){return new State(tabs.getSelectedIndex(),overview.getVerticalScrollBar().getValue(),guide instanceof PageState state?state.saveState():null);}
  public void restoreState(Object value){if(value instanceof State state){tabs.setSelectedIndex(Math.min(state.tab(),tabs.getTabCount()-1));SwingUtilities.invokeLater(()->overview.getVerticalScrollBar().setValue(state.scroll()));if(guide instanceof PageState page)page.restoreState(state.guideState());}}
  private static final class MapPreview extends JButton {
    private final BufferedImage art;
    MapPreview(BufferedImage art,String name,Runnable open){this.art=art;setPreferredSize(new Dimension(0,210));setToolTipText("Open "+name+" map");getAccessibleContext().setAccessibleName("Open "+name+" map");addActionListener(e->open.run());}
    @Override protected void paintComponent(Graphics graphics){super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      double scale=Math.max((double)getWidth()/art.getWidth(),(double)getHeight()/art.getHeight());int w=(int)(art.getWidth()*scale),h=(int)(art.getHeight()*scale);g.drawImage(art,(getWidth()-w)/2,(getHeight()-h)/2,w,h,null);
      g.setColor(new Color(0,0,0,100));g.fillRect(0,getHeight()-42,getWidth(),42);g.setColor(Color.WHITE);g.drawString("EXPLORE THE CITY MAP  →",18,getHeight()-16);g.dispose();}
  }
}
