package dev.wowforever.app.ui.journal;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;

/** Section parents are separate from visit history; geographic pages retain their genuine location trail. */
public final class JournalBreadcrumbs extends JPanel {
  private final Consumer<ViewTarget> navigate;
  private ViewTarget target = new ViewTarget.Home();
  private String pageTitle = "";
  private record Step(String label, ViewTarget target) {}

  public JournalBreadcrumbs(Consumer<ViewTarget> navigate) {
    this.navigate = navigate;
    setLayout(new Wrap());
    setBorder(BorderFactory.createEmptyBorder(12, 20, 10, 20));
    getAccessibleContext().setAccessibleName("Journal section hierarchy");
    rebuild();
  }
  public void showTarget(ViewTarget target) { this.target=target; pageTitle=""; rebuild(); }
  public void setPageTitle(String title) { pageTitle=title == null ? "" : title; rebuild(); }
  private void rebuild() {
    removeAll();
    List<Step> steps = new ArrayList<>();
    steps.add(new Step("Journal", target instanceof ViewTarget.Home ? null : new ViewTarget.Home()));
    if(target instanceof ViewTarget.WorldMap) steps.add(new Step("World & maps",null));
    else if(target instanceof ViewTarget.Entity e) {
      String section = label(e.ref().kind());
      ViewTarget parent = e.ref().kind()==EntityKind.INSTANCE || e.ref().kind()==EntityKind.BOSS
          ? new ViewTarget.List(EntityKind.INSTANCE) : new ViewTarget.List(
              e.ref().kind()==EntityKind.OBJECT ? EntityKind.NPC : e.ref().kind());
      steps.add(new Step(section,parent));
      steps.add(new Step(pageTitle.isBlank()?e.ref().kind().label()+" #"+e.ref().id():pageTitle,null));
    } else if(target instanceof ViewTarget.List l) steps.add(new Step(label(l.kind()),null));
    else if(target instanceof ViewTarget.ItemContent c) {
      steps.add(new Step("Gear & items",new ViewTarget.List(EntityKind.ITEM)));
      steps.add(new Step(c.section().equals("set")?"Item set #"+c.id():human(c.section()),null));
    } else if(target instanceof ViewTarget.Forever f) {
      if(f.section().equals("ladder")) steps.add(new Step("Dungeons & raids",null));
      else if(f.section().equals("hub")) steps.add(new Step("What's changed",null));
      else if(f.section().equals("discovery")) {
        steps.add(new Step("Dungeons & raids",new ViewTarget.List(EntityKind.INSTANCE)));
        steps.add(new Step("Discovery #"+f.id(),null));
      } else {
        steps.add(new Step("Character & systems",new ViewTarget.Forever("talents")));
        steps.add(new Step(human(f.section()),null));
      }
    } else if(target instanceof ViewTarget.Search) steps.add(new Step("Search results",null));
    for(int i=0;i<steps.size();i++) {
      if(i>0) { JLabel divider=new JLabel("›"); divider.setForeground(Theme.current().textMuted());add(divider); }
      Step step=steps.get(i);
      if(step.target()==null) {JLabel current=new JLabel(step.label());current.setToolTipText(step.label());add(current);}
      else {
        JButton link=new JButton(step.label()); link.setBorder(BorderFactory.createEmptyBorder(2,0,2,0));
        link.putClientProperty("JButton.buttonType","borderless");link.setForeground(Theme.current().link());
        link.setToolTipText("Go to "+step.label());link.addActionListener(e -> navigate.accept(step.target()));add(link);
      }
    }
    revalidate();repaint();
  }
  private static String label(EntityKind kind) { return switch(kind) {
    case ITEM -> "Gear & items";case NPC,OBJECT -> "NPCs";case QUEST -> "Quests";
    case ZONE -> "World & maps";case INSTANCE,BOSS -> "Dungeons & raids";
  }; }
  private static String human(String section) {return switch(section) {
    case "class-spells" -> "Class spells";case "xp-food" -> "XP food";
    default -> Character.toUpperCase(section.charAt(0))+section.substring(1);
  };}
  private static final class Wrap implements LayoutManager {
    public void addLayoutComponent(String n,Component c){} public void removeLayoutComponent(Component c){}
    public Dimension preferredLayoutSize(Container p){return arrange(p,false);}
    public Dimension minimumLayoutSize(Container p){return new Dimension(0,arrange(p,false).height);}
    public void layoutContainer(Container p){arrange(p,true);}
    private Dimension arrange(Container p,boolean place) {
      Insets in=p.getInsets();int width=p.getWidth()>0?p.getWidth():800;
      int room=Math.max(1,width-in.left-in.right),x=0,y=0,row=0,used=0;
      for(Component c:p.getComponents()) {
        Dimension d=c.getPreferredSize();int w=Math.min(room,d.width);
        if(x>0&&x+8+w>room){y+=row+5;x=0;row=0;}
        if(x>0)x+=8;
        if(place)c.setBounds(in.left+x,in.top+y,w,d.height);
        x+=w;row=Math.max(row,d.height);used=Math.max(used,x);
      }
      return new Dimension(used+in.left+in.right,y+row+in.top+in.bottom);
    }
  }
}
