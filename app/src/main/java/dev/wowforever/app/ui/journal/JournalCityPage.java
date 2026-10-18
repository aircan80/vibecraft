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
    JournalSurface.add(discover,JournalSurface.copy("Forever client build "+data.build()+" · District names come from the native AreaTable. This directory lists archived child areas; it does not claim every city district or service is available."));
    columns.add(discover);JournalSurface.add(body,columns);
    tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);tabs.addTab("Overview",overview);tabs.addTab("Map & records",guide);
    JournalSurface.Body districtBody=new JournalSurface.Body();JournalSurface.add(districtBody,JournalSurface.title("District directory",28));districtRows(districtBody,data,nav);
    tabs.addTab("Districts",JournalSurface.scroll(districtBody));add(tabs,BorderLayout.CENTER);
  }
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
