package dev.wowforever.app.ui.pages;

import static org.junit.jupiter.api.Assertions.*;
import dev.wowforever.app.db.RaceRepository.*;
import dev.wowforever.app.model.CampingData;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class RaceCampingLayoutTest {
  @Test void narrowPagesTrackViewportAndRetainLongSourceCopy() throws Exception {
    SwingUtilities.invokeAndWait(()->{
      String description="Original source description with a long explanation of this racial ability and its recorded effects ".repeat(5);
      var catalog=new Catalog("1.60.1.70450","1.15.9.70003",List.of(new Race(95,"High Order Skyborne","",List.of(new ClassChoice(1,"Warrior",false)),List.of(new Racial(694,"A long original racial ability name",description,"Active","ChrRaceRacialAbility · 1.60.1.70450")))));
      var camp=new CampingData("1.60.1.70450","a".repeat(64),List.of(new CampingData.Camp(1307172,"Original long source camp feature name",123,description,"SpellEffect.csv",2,"Tier 3","Alchemy",300)),List.of(),List.of());
      for(JPanel page:List.of(new RacesPage(catalog,id->{}),new RacePage(catalog,95),new CampingPage(camp))){
        page.setSize(420,700);for(int i=0;i<8;i++) layout(page);
        checkScrollPanes(page);
      }
      RacePage race=new RacePage(catalog,95);race.setSize(420,700);for(int i=0;i<8;i++)layout(race);
      assertTrue(hasText(race,description),"Full source description is retained");
    });
  }
  private static void layout(Container container){container.doLayout();for(Component child:container.getComponents())if(child instanceof Container nested)layout(nested);}
  private static void checkScrollPanes(Container container){
    if(container instanceof JScrollPane scroll){
      assertFalse(scroll.getHorizontalScrollBar().isVisible(),"Narrow page requires horizontal scrolling");
      if(scroll.getViewport().getView()!=null)assertTrue(scroll.getViewport().getView().getWidth()<=scroll.getViewport().getExtentSize().width,"View exceeds viewport");
    }
    for(Component child:container.getComponents())if(child instanceof Container nested)checkScrollPanes(nested);
  }
  private static boolean hasText(Container container,String expected){
    if(container instanceof JTextArea area&&area.getText().equals(expected))return true;
    for(Component child:container.getComponents())if(child instanceof Container nested&&hasText(nested,expected))return true;
    return false;
  }
}
