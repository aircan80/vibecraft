package dev.wowforever.app.ui.journal;

import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.*;
import dev.wowforever.app.ui.pages.PageState;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.*;
import java.awt.image.BufferedImage;
import dev.wowforever.app.ui.widgets.Chip;
import dev.wowforever.app.ui.widgets.AtlasMapPanel;
import java.util.Comparator;
import javax.swing.*;

/** A task-oriented starting point, with no invented character history or progress. */
public final class JournalHomePage extends JPanel implements PageState {
  private final JScrollPane scroll;
  public JournalHomePage(JournalCatalog.HomeSnapshot data,Navigator nav) {
    super(new BorderLayout());
    JournalSurface.Body body=new JournalSurface.Body();scroll=JournalSurface.scroll(body);add(scroll);
    JPanel hero=JournalSurface.card();
    JLabel eyebrow=JournalSurface.heading("YOUR AZEROTH FIELD GUIDE",11);eyebrow.setForeground(Theme.current().goldText());
    JPanel intro=new JPanel(new FlowLayout(FlowLayout.LEADING,8,0));intro.setOpaque(false);intro.add(eyebrow);intro.add(new Chip("Beta",Chip.Style.PLAIN));
    JournalSurface.add(hero,intro);JournalSurface.add(hero,JournalSurface.title("Find your next adventure",28));
    JournalSurface.add(hero,JournalSurface.copy("Explore the world, plan a dungeon run, and discover what is different in WoW: Forever."));
    JournalSurface.add(hero,JournalSurface.copy("Offline reference · client build "+data.build()));JournalSurface.add(body,hero);
    data.instances().stream().filter(i->InstanceSummary.CLIENT.equals(i.source())&&i.name()!=null&&i.name().contains("Dalaran"))
        .sorted(Comparator.comparing(InstanceSummary::displayName)).findFirst().ifPresent(instance->{
          JPanel feature=JournalSurface.card();JPanel featureCopy=JournalSurface.stack();JLabel title=JournalSurface.heading("SPOTLIGHT · FOREVER",11);title.setForeground(Theme.current().goldText());
          JournalSurface.add(featureCopy,title);JournalSurface.add(featureCopy,JournalSurface.title(instance.displayName(),22));
          JournalSurface.add(featureCopy,JournalSurface.copy("A dungeon recorded in the Forever client. Explore its encounters, maps, and source-labelled loot reports."));
          JButton open=new JButton("Explore "+instance.displayName());open.addActionListener(e->nav.open(new ViewTarget.Entity(instance.ref())));
          JournalSurface.add(featureCopy,action(open));
          if(data.spotlightArt()!=null){
            JPanel art=JournalSurface.stack();art.add(new SpotlightArt(data.spotlightArt(),instance.displayName()+" · "+data.artLabel()));
            var caption=JournalSurface.copy("Atlas floor preview · "+data.artLabel()+" · GPL-2.0");caption.setToolTipText(AtlasMapPanel.CREDIT);JournalSurface.add(art,caption);
            JPanel pair=new JPanel(new FeatureLayout());pair.setOpaque(false);pair.add(featureCopy);pair.add(art);feature.add(pair);
          }else feature.add(featureCopy);
          JournalSurface.add(body,feature);
        });
    JournalSurface.add(body,JournalSurface.heading("Where are you headed?",20));
    JPanel tasks=JournalSurface.grid();
    task(tasks,"World","Follow the map from Azeroth to a zone. Find quests, creatures, and routes.","Explore the world",new ViewTarget.WorldMap(947),nav);
    task(tasks,"Dungeons","Choose a dungeon or raid, explore encounters, and inspect reported loot.","Find a dungeon",new ViewTarget.Forever("ladder"),nav);
    task(tasks,"Gear","Look up an item, compare sets, and see where its information comes from.","Browse items",new ViewTarget.List(EntityKind.ITEM),nav);
    task(tasks,"Build planner","Explore talents and their requirements before spending a point.","Plan talents",new ViewTarget.Forever("talents"),nav);
    task(tasks,"Professions","Discover recipes and the materials they require.","Browse recipes",new ViewTarget.ItemContent("recipes"),nav);
    task(tasks,"What changed","Explore named additions and changes against the archived Classic Era client.","Discover Forever",new ViewTarget.Forever("hub"),nav);
    JournalSurface.add(body,tasks);
    JournalSurface.add(body,JournalSurface.copy("Your reference, not your character sheet. This journal does not assume your level, equipment, or completed quests."));
  }
  private static void task(JPanel grid,String title,String text,String action,ViewTarget target,Navigator nav){
    JPanel card=JournalSurface.card();JournalSurface.add(card,JournalSurface.heading(title,19));JournalSurface.add(card,JournalSurface.copy(text));
    JButton button=new JButton(action);button.addActionListener(e->nav.open(target));button.getAccessibleContext().setAccessibleDescription(text);
    JournalSurface.add(card,action(button));grid.add(card);
  }
  private static JPanel action(JButton button){
    JPanel row=new JPanel(new FlowLayout(FlowLayout.LEADING,0,0));row.setOpaque(false);row.add(button);return row;
  }
  private static final class SpotlightArt extends JPanel {
    private final BufferedImage image;
    SpotlightArt(BufferedImage image,String title){this.image=image;setOpaque(false);setToolTipText(title+" · "+AtlasMapPanel.CREDIT);getAccessibleContext().setAccessibleName(title+"; Atlas floor-map preview");}
    @Override public Dimension getPreferredSize(){return new Dimension(0,Math.min(300,Math.max(180,getWidth())));}
    @Override protected void paintComponent(Graphics graphics){super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      double scale=Math.min((double)getWidth()/image.getWidth(),(double)getHeight()/image.getHeight());
      int w=(int)(image.getWidth()*scale),h=(int)(image.getHeight()*scale);g.drawImage(image,(getWidth()-w)/2,(getHeight()-h)/2,w,h,null);g.dispose();
    }
  }
  /** Side-by-side on desktop; copy, art, and credit stack naturally in compact windows. */
  private static final class FeatureLayout implements LayoutManager {
    public void addLayoutComponent(String name,Component component){}public void removeLayoutComponent(Component component){}
    public Dimension minimumLayoutSize(Container parent){return new Dimension(0,preferredLayoutSize(parent).height);}
    public Dimension preferredLayoutSize(Container parent){return measure(parent,false);}
    public void layoutContainer(Container parent){measure(parent,true);}
    private Dimension measure(Container parent,boolean layout){
      boolean wide=parent.getWidth()>=680;int gap=18,width=Math.max(0,parent.getWidth()),x=0,y=0,height=0;
      int col=wide?(width-gap)/2:width;
      for(Component child:parent.getComponents()){
        child.setSize(col,Math.max(1,child.getHeight()));if(child instanceof Container nested)nested.doLayout();int h=child.getPreferredSize().height;
        if(layout)child.setBounds(x,y,col,h);
        if(wide){x+=col+gap;height=Math.max(height,h);}else {y+=h+gap;height=y-gap;}
      }
      return new Dimension(width,height);
    }
  }
  public Object saveState(){return scroll.getVerticalScrollBar().getValue();}
  public void restoreState(Object state){if(state instanceof Integer position)SwingUtilities.invokeLater(()->scroll.getVerticalScrollBar().setValue(position));}
}
