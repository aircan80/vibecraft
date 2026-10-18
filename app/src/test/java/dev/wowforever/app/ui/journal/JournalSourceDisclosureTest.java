package dev.wowforever.app.ui.journal;
import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.TravelMapData;
import dev.wowforever.app.ui.widgets.SourceInfoButton;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
class JournalSourceDisclosureTest {
 @Test void otherEditionAndMissingForeverMapQualificationsStayVisible()throws Exception{
  SwingUtilities.invokeAndWait(()->{
   String label="Flight paths: client 1.60.1.70450 · Entrances/exits: VMaNGOS Vanilla with matching trigger positions; Forever entrances not yet recorded";
   var row=JournalMapInspector.sourceDisclosure("Westfall",new TravelMapData(List.of(),List.of(),List.of(),label));
   assertThat(text(row)).contains("VMaNGOS Vanilla","Forever entrances not yet recorded");
   var info=info(row);assertThat(text(info.detailsContent())).contains(label,"Not recorded","Loaded map and travel overlays on Westfall");
  });
 }
 @Test void neutralSourceLabelRemainsExactInDetailsWithoutInventedBuild()throws Exception{
  SwingUtilities.invokeAndWait(()->{
   var row=JournalMapInspector.sourceDisclosure("Test map",new TravelMapData(List.of(),List.of(),List.of(),"Retained provider label"));
   assertThat(text(row)).isEqualTo("Retained provider label");
   assertThat(text(info(row).detailsContent())).contains("Retained provider label","Not recorded").doesNotContain("1.60.1.70450");
  });
 }
 private static SourceInfoButton info(Container root){for(Component c:root.getComponents()){if(c instanceof SourceInfoButton source)return source;if(c instanceof Container nested){try{return info(nested);}catch(AssertionError ignored){}}}throw new AssertionError("Missing source button");}
 private static String text(Container root){var result=new StringBuilder();for(Component c:root.getComponents()){if(c instanceof JTextArea area)result.append(area.getText());if(c instanceof Container nested)result.append(text(nested));}return result.toString();}
}
