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
  private static JButton button(Container root,String text){return buttons(root).stream().filter(b->text.equals(b.getText())).findFirst().orElseThrow(()->new AssertionError("Missing button: "+text));}
  private static List<JButton> buttons(Container root){List<JButton> result=new ArrayList<>();for(Component c:root.getComponents()){if(c instanceof JButton b)result.add(b);if(c instanceof Container child)result.addAll(buttons(child));}return result;}
}
