package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.db.NativeTeachingItemRepository.*;
import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.ViewTarget;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class TeachingItemsPanelTest {
  @Test void opensNormalItemAcquisitionAndKeepsSourceDisclosureCollapsed()throws Exception {
    SwingUtilities.invokeAndWait(()->{
      var opened=new ArrayList<ViewTarget>();var data=new Snapshot("f","70450","enUS",2543,List.of(new TeachingItem(728,"Recipe: Westfall Stew",List.of(new Support("Direct native learn-item effect","Eligibility unknown",List.of())))),"Native teaching evidence");
      var page=new TeachingItemsPanel(data,opened::add);
      descendants(page).stream().filter(JButton.class::isInstance).map(JButton.class::cast).filter(b->b.getText().equals("Open item & acquisition")).findFirst().orElseThrow().doClick();
      assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(EntityKind.ITEM,728)));
      assertThat(descendants(page).stream().filter(JToggleButton.class::isInstance).map(JToggleButton.class::cast)).allMatch(button->!button.isSelected());
    });
  }
  private static List<Component> descendants(Container root){var result=new ArrayList<Component>();for(Component child:root.getComponents()){result.add(child);if(child instanceof Container container)result.addAll(descendants(container));}return result;}
}
