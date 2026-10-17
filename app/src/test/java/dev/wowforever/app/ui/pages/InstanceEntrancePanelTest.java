package dev.wowforever.app.ui.pages;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.*;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class InstanceEntrancePanelTest {
  @Test void missingAndNestedEntrancesRemainExplicit() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var empty=new InstanceEntrancePanel(InstanceEntrancePanel.Loaded.EMPTY,t -> {});
      assertThat(descendants(empty)).filteredOn(JLabel.class::isInstance).map(c -> ((JLabel)c).getText())
          .contains("Entrance not yet recorded");
      var opened=new ArrayList<dev.wowforever.app.nav.ViewTarget>();
      var nested=new InstanceEntrancePanel(new InstanceEntrancePanel.Loaded(List.of(),List.of(new InstanceAccess.Parent(18,"Blackrock Depths"))),opened::add);
      var link=descendants(nested).stream().filter(JButton.class::isInstance).map(JButton.class::cast).findFirst().orElseThrow();
      assertThat(link.getText()).isEqualTo("Entered from Blackrock Depths"); link.doClick(); assertThat(opened).hasSize(1);
    });
  }
  @Test void waypointCopiesRecordedZoneCoordinates() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var pin=new TravelMapData.Entrance(new EntityRef(EntityKind.INSTANCE,3),"The Deadmines",38.03,77.50,false,false,true,"Recorded entrance");
      var location=new InstanceAccess.Location(40,"Westfall",pin,Optional.empty());
      var copied=new ArrayList<String>();
      var panel=new InstanceEntrancePanel(new InstanceEntrancePanel.Loaded(List.of(new InstanceEntrancePanel.Located(location,null)),List.of()),t -> {},copied::add);
      descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast).filter(b -> b.getText().equals("Copy /way")).findFirst().orElseThrow().doClick();
      assertThat(copied).containsExactly("/way Westfall 38.0 77.5");
    });
  }
  private static List<Component> descendants(Container parent) {
    List<Component> result=new ArrayList<>();
    for(var child:parent.getComponents()) {result.add(child);if(child instanceof Container c)result.addAll(descendants(c));}
    return result;
  }
}
