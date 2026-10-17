package dev.wowforever.app.ui.widgets;
import static org.assertj.core.api.Assertions.*;
import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.ViewTarget;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
class AtlasMapPanelTest {
 @Test void keepsAtlasLegendAndCreditSeparateAndLinksOnlyKnownNpcs()throws Exception {
  var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB),"jpg",bytes);
  SwingUtilities.invokeAndWait(()->{
   var opened=new ArrayList<ViewTarget>();
   var map=new InstanceFloorMap("A","Map A",bytes.toByteArray(),true,List.of(new InstanceFloorMap.Legend("1","Known",639L),new InstanceFloorMap.Legend("2","Unmatched",null)));
   var panel=new AtlasMapPanel(List.of(map),opened::add,true);
   var components=all(panel);var tabs=components.stream().filter(JTabbedPane.class::isInstance).map(JTabbedPane.class::cast).findFirst().orElseThrow();
   assertThat(tabs.isVisible()).isFalse();
   components.stream().filter(JToggleButton.class::isInstance).map(JToggleButton.class::cast).findFirst().orElseThrow().doClick();assertThat(tabs.isVisible()).isTrue();
   assertThat(components.stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast).map(JTextArea::getText).toList()).containsExactly(AtlasMapPanel.CREDIT+" · drawn for the Forever beta");
   components.stream().filter(JButton.class::isInstance).map(JButton.class::cast).filter(b->b.getText().equals("1) Known")).findFirst().orElseThrow().doClick();
   assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(EntityKind.NPC,639)));
  });
 }
 private static List<Component> all(Container c){var out=new ArrayList<Component>();for(var child:c.getComponents()){out.add(child);if(child instanceof Container nested)out.addAll(all(nested));}return out;}
}
