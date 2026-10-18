package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class JournalCatalogTest {
  private static InstanceSummary dungeon(long id,String name,boolean forever){
    return new InstanceSummary(id,name,"DUNGEON",15,20,1L,"Westfall",forever?"client:3065":"Deadmines",
        forever?InstanceSummary.CLIENT:InstanceSummary.ATLASLOOT,null,"client_named","none");
  }
  @Test void homeTasksOpenRealTargets() throws Exception {
    SwingUtilities.invokeAndWait(()->{
      List<ViewTarget> opened=new ArrayList<>();
      var page=new JournalHomePage(new JournalCatalog.HomeSnapshot(List.of(dungeon(7,"Dalaran",true)),"70450"),opened::add);
      for(String text:List.of("Explore the world","Find a dungeon","Browse items","Plan talents","Browse recipes","Discover Forever"))button(page,text).doClick();
      assertThat(opened).containsExactly(new ViewTarget.WorldMap(947),new ViewTarget.Forever("ladder"),new ViewTarget.List(EntityKind.ITEM),new ViewTarget.Forever("talents"),new ViewTarget.ItemContent("recipes"),new ViewTarget.Forever("hub"));
    });
  }
  @Test void dungeonFiltersSelectionAndPageSurviveNavigation() throws Exception {
    SwingUtilities.invokeAndWait(()->{
      var data=new JournalCatalog.DungeonSnapshot(List.of(dungeon(1,"Deadmines",false),dungeon(2,"Dalaran",true)),List.of());
      List<ViewTarget> opened=new ArrayList<>();var page=new JournalDungeonPage(data,opened::add);
      var state=new JournalDungeonPage.State("Dalaran",1,1,1,1,0,"instance:2",0);page.restoreState(state);
      assertThat(page.saveState()).isEqualTo(state);
      assertThat(buttons(page)).noneMatch(b->b.getText().contains("Deadmines"));
      button(page,"Explore encounters, maps & loot").doClick();assertThat(opened).containsExactly(new ViewTarget.Entity(dungeon(2,"Dalaran",true).ref()));
      var returned=new JournalDungeonPage(data,opened::add);returned.restoreState(page.saveState());assertThat(returned.saveState()).isEqualTo(state);
    });
  }
  @Test void reportedDiscoveryDoesNotBecomeKnownAndUsesDiscoveryRoute() throws Exception {
    SwingUtilities.invokeAndWait(()->{
      var discovery=new ForeverContent.Discovery(2981,"Hyjal","RAID","70450",List.of(new ForeverContent.Encounter("Wild King","Reported","achievement")),"Unknown","");
      List<ViewTarget> opened=new ArrayList<>();var page=new JournalDungeonPage(new JournalCatalog.DungeonSnapshot(List.of(),List.of(discovery)),opened::add);
      page.restoreState(new JournalDungeonPage.State("",1,2,4,2,0,null,0));button(page,"Open dungeon guide").doClick();
      assertThat(opened).containsExactly(new ViewTarget.Forever("discovery",2981));
      page.restoreState(new JournalDungeonPage.State("",1,2,4,1,0,null,0));assertThat(buttons(page)).anyMatch(b->b.getText().equals("Reset filters"));
    });
  }
  @Test void desktopHomeKeepsPrimaryTasksAndDalaranInTheFirstViewport() throws Exception {
    SwingUtilities.invokeAndWait(()->{
      var image=new java.awt.image.BufferedImage(500,500,java.awt.image.BufferedImage.TYPE_INT_RGB);
      var page=new JournalHomePage(new JournalCatalog.HomeSnapshot(List.of(dungeon(7,"City of Dalaran",true)),"70450",image,"Map A"),ignored->{});
      page.setSize(1030,760);for(int i=0;i<12;i++)layout(page);
      JButton world=button(page,"Explore the world"),dungeons=button(page,"Find a dungeon"),gear=button(page,"Browse items");
      Point w=SwingUtilities.convertPoint(world,0,0,page),d=SwingUtilities.convertPoint(dungeons,0,0,page),g=SwingUtilities.convertPoint(gear,0,0,page);
      assertThat(w.x).isLessThan(d.x);assertThat(d.x).isLessThan(g.x);
      assertThat(Math.abs(w.y-d.y)).isLessThan(80);assertThat(Math.abs(d.y-g.y)).isLessThan(80);
      assertThat(g.y+gear.getHeight()).isLessThan(760);
      JButton dalaran=button(page,"Explore Dalaran");Point feature=SwingUtilities.convertPoint(dalaran,0,0,page);
      assertThat(feature.y).isGreaterThan(g.y).isLessThan(760);
    });
  }
  @Test void persistentRailNavigatesOtherDestinationsAndRestoresTheEmbeddedGuide() throws Exception {
    SwingUtilities.invokeAndWait(()->{
      var data=new JournalCatalog.DungeonSnapshot(List.of(dungeon(1,"Deadmines",false),dungeon(2,"Dalaran",true)),List.of());
      List<ViewTarget> opened=new ArrayList<>();TestGuide guide=new TestGuide();guide.tab=2;
      var page=new JournalDungeonPage(data,opened::add,2,guide);page.setSize(1030,760);for(int i=0;i<8;i++)layout(page);
      assertThat(guide.getParent().isVisible()).isTrue();
      JButton other=buttons(page).stream().filter(b->"Deadmines".equals(b.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();
      other.doClick();assertThat(opened).containsExactly(new ViewTarget.Entity(dungeon(1,"Deadmines",false).ref()));
      Object saved=page.saveState();TestGuide replacement=new TestGuide();var returned=new JournalDungeonPage(data,opened::add,2,replacement);returned.restoreState(saved);assertThat(replacement.tab).isEqualTo(2);
      returned.setSize(420,760);for(int i=0;i<8;i++)layout(returned);
      for(Component c:all(returned))if(c instanceof JToggleButton toggle&&toggle.getText().equals("Destinations"))toggle.doClick();
      for(int i=0;i<8;i++)layout(returned);assertThat(replacement.getParent().isVisible()).isFalse();
      for(Component c:all(returned))if(c instanceof JToggleButton toggle&&toggle.getText().equals("Selected guide"))toggle.doClick();
      for(int i=0;i<8;i++)layout(returned);assertThat(replacement.getParent().isVisible()).isTrue();assertThat(replacement.tab).isEqualTo(2);
    });
  }
  private static final class TestGuide extends JPanel implements dev.wowforever.app.ui.pages.PageState {
    int tab;public Object saveState(){return tab;}public void restoreState(Object state){if(state instanceof Integer n)tab=n;}
  }
  private static List<Component> all(Container root){List<Component> result=new ArrayList<>();for(Component c:root.getComponents()){result.add(c);if(c instanceof Container child)result.addAll(all(child));}return result;}
  private static void layout(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container child)layout(child);}
  private static JButton button(Container root,String text){return buttons(root).stream().filter(b->text.equals(b.getText())).findFirst().orElseThrow(()->new AssertionError("Missing button: "+text));}
  private static List<JButton> buttons(Container root){List<JButton> result=new ArrayList<>();for(Component c:root.getComponents()){if(c instanceof JButton b)result.add(b);if(c instanceof Container child)result.addAll(buttons(child));}return result;}
}
