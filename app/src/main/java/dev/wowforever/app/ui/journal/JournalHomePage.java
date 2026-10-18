package dev.wowforever.app.ui.journal;

import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.*;
import dev.wowforever.app.ui.pages.PageState;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.Chip;
import dev.wowforever.app.ui.widgets.AtlasMapPanel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Comparator;
import javax.swing.*;

/** An editorial, task-oriented starting point with no assumed character history or progress. */
public final class JournalHomePage extends JPanel implements PageState {
  private final JScrollPane scroll;
  private boolean restored;
  public JournalHomePage(JournalCatalog.HomeSnapshot data,Navigator nav) {
    super(new BorderLayout());
    JournalSurface.Body body=new JournalSurface.Body();scroll=JournalSurface.scroll(body);add(scroll);
    JPanel intro=JournalSurface.stack();
    JournalSurface.add(intro,eyebrow("YOUR NEXT ADVENTURE"));
    JournalSurface.add(intro,JournalSurface.title("Where do you want to go?",36));
    JournalSurface.add(intro,JournalSurface.copy("Explore Forever’s world, find dungeon encounters, or look up an item. Start with a question, then follow the connections."));
    JPanel badge=new JPanel(new FlowLayout(FlowLayout.LEADING,0,0));badge.setOpaque(false);badge.add(new Chip("Forever beta reference",Chip.Style.PLAIN));
    JournalSurface.add(intro,badge);JournalSurface.add(body,intro);

    JPanel tasks=JournalSurface.grid(3,900);
    task(tasks,"01","Explore the world","Move from continent to zone. Keep the whole path in view.","Explore the world",new ViewTarget.WorldMap(947),nav);
    task(tasks,"02","Plan a dungeon","See level guidance, maps, encounters and reported loot in one place.","Find a dungeon",new ViewTarget.Forever("ladder"),nav);
    task(tasks,"03","Find your gear","Look up items and sets, with clear labels for what is known and what is still hidden.","Browse items",new ViewTarget.List(EntityKind.ITEM),nav);
    JournalSurface.add(body,tasks);body.add(Box.createVerticalStrut(6));

    data.instances().stream().filter(i->InstanceSummary.CLIENT.equals(i.source())&&i.name()!=null&&i.name().contains("Dalaran"))
        .sorted(Comparator.comparing(InstanceSummary::displayName)).findFirst().ifPresent(instance->{
          JPanel feature=JournalSurface.card(),copy=JournalSurface.stack();
          JournalSurface.add(copy,eyebrow("DISCOVER FOREVER"));
          JournalSurface.add(copy,JournalSurface.title("A different "+instance.displayName(),26));
          if(instance.tunedMinLevel()!=null)JournalSurface.add(copy,JournalSurface.copy("Client entry guidance · Level "+instance.tunedMinLevel()+"+"));
          JournalSurface.add(copy,JournalSurface.copy("Bring maps and encounter details together before you head inside. Loot reports keep their source labels."));
          JButton open=primary("Explore Dalaran");open.getAccessibleContext().setAccessibleName("Explore "+instance.displayName());
          open.addActionListener(e->nav.open(new ViewTarget.Entity(instance.ref())));JournalSurface.add(copy,action(open));
          if(data.spotlightArt()!=null){
            JPanel pair=new JPanel(new FeatureLayout());pair.setOpaque(false);pair.add(copy);pair.add(new SpotlightArt(data.spotlightArt(),instance.displayName()+" · "+data.artLabel()));feature.add(pair);
          }else feature.add(copy);
          JournalSurface.add(body,feature);
        });
    JournalSurface.add(body,JournalSurface.heading("Keep exploring",24));
    JPanel links=JournalSurface.grid(3,900);
    quickLink(links,"Build planner","Plan talents",new ViewTarget.Forever("talents"),nav);
    quickLink(links,"Professions","Browse recipes",new ViewTarget.ItemContent("recipes"),nav);
    quickLink(links,"What changed","Discover Forever",new ViewTarget.Forever("hub"),nav);
    JournalSurface.add(body,links);
    if(data.spotlightArt()!=null)JournalSurface.add(body,JournalSurface.copy(AtlasMapPanel.CREDIT+" · "+data.artLabel()));
    JournalSurface.add(body,JournalSurface.copy("Offline client build "+data.build()+". No character profile, play history, or live game connection is assumed."));
    // A new Home starts at the editorial introduction; deliberate history restoration still wins.
    SwingUtilities.invokeLater(()->{if(!restored)scroll.getVerticalScrollBar().setValue(0);});
  }
  private static JLabel eyebrow(String text){
    JLabel label=new JLabel(text);label.setFont(label.getFont().deriveFont(Font.PLAIN,11f));label.setForeground(Theme.current().goldText());return label;
  }
  private static JButton primary(String text){
    JButton button=new JButton(text);button.setMargin(new Insets(9,14,9,14));button.putClientProperty("JButton.arc",8);
    button.putClientProperty("JComponent.minimumHeight",38);return button;
  }
  private static void task(JPanel grid,String number,String title,String text,String action,ViewTarget target,Navigator nav){
    JPanel card=JournalSurface.card();JournalSurface.add(card,eyebrow(number));JournalSurface.add(card,JournalSurface.title(title,25));
    JournalSurface.add(card,JournalSurface.copy(text));JButton button=primary(action);button.addActionListener(e->nav.open(target));
    button.getAccessibleContext().setAccessibleDescription(text);JournalSurface.add(card,action(button));grid.add(card);
  }
  private static void quickLink(JPanel grid,String title,String text,ViewTarget target,Navigator nav){
    JPanel group=JournalSurface.stack();JournalSurface.add(group,JournalSurface.copy(title));JButton button=new JButton(text);
    button.setMargin(new Insets(7,12,7,12));button.addActionListener(e->nav.open(target));group.add(action(button));grid.add(group);
  }
  private static JPanel action(JButton button){
    JPanel row=new JPanel(new FlowLayout(FlowLayout.LEADING,0,0));row.setOpaque(false);row.add(button);return row;
  }
  private static final class SpotlightArt extends JPanel {
    private final BufferedImage image;
    SpotlightArt(BufferedImage image,String title){this.image=image;setOpaque(false);setToolTipText(title+" · "+AtlasMapPanel.CREDIT);getAccessibleContext().setAccessibleName(title+"; Atlas floor-map preview. Open the guide to see the complete map.");}
    @Override public Dimension getPreferredSize(){return new Dimension(0,240);}
    @Override protected void paintComponent(Graphics graphics){super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      Shape clip=new java.awt.geom.RoundRectangle2D.Double(0,0,getWidth(),getHeight(),12,12);g.clip(clip);
      double scale=Math.max((double)getWidth()/image.getWidth(),(double)getHeight()/image.getHeight());
      int w=(int)Math.ceil(image.getWidth()*scale),h=(int)Math.ceil(image.getHeight()*scale);g.drawImage(image,(getWidth()-w)/2,(getHeight()-h)/2,w,h,null);g.dispose();
    }
  }
  /** Side-by-side on desktop; copy and art stack naturally in compact windows. */
  private static final class FeatureLayout implements LayoutManager {
    public void addLayoutComponent(String name,Component component){}public void removeLayoutComponent(Component component){}
    public Dimension minimumLayoutSize(Container parent){return new Dimension(0,preferredLayoutSize(parent).height);}
    public Dimension preferredLayoutSize(Container parent){return measure(parent,false);}
    public void layoutContainer(Container parent){measure(parent,true);}
    private Dimension measure(Container parent,boolean layout){
      boolean wide=parent.getWidth()>=680;int gap=24,width=Math.max(0,parent.getWidth()),x=0,y=0,height=0;
      int col=wide?(width-gap)/2:width;
      for(Component child:parent.getComponents()){
        child.setSize(col,Math.max(1,child.getHeight()));if(child instanceof Container nested)nested.doLayout();int h=child.getPreferredSize().height;
        if(layout)child.setBounds(x,y,col,h);
        if(wide){x+=col+gap;height=Math.max(height,h);}else{y+=h+gap;height=y-gap;}
      }
      return new Dimension(width,height);
    }
  }
  public Object saveState(){return scroll.getVerticalScrollBar().getValue();}
  public void restoreState(Object state){if(state instanceof Integer position){restored=true;SwingUtilities.invokeLater(()->scroll.getVerticalScrollBar().setValue(position));}}
}
