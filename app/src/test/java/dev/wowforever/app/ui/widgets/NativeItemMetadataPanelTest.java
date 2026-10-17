package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.app.model.NativeItemMetadata;
import dev.wowforever.app.nav.ViewTarget;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class NativeItemMetadataPanelTest {
  @Test void equipmentSlotAndExactSetAreVisibleWithoutAnItemCard()throws Exception {
    SwingUtilities.invokeAndWait(()-> {
      var targets=new ArrayList<ViewTarget>();
      var data=new NativeItemMetadata("1.60.1.70450",12,0L,List.of(new NativeItemMetadata.SetMembership(42,"Native set","ItemSet.csv:4")),List.of(),"Item.csv:3",true);
      var panel=new NativeItemMetadataPanel(data,targets::add);
      assertThat(NativeItemMetadataPanel.slot(12)).isEqualTo("Trinket");
      assertThat(NativeItemMetadataPanel.slot(0)).isEqualTo("Not equippable");
      for(var child:panel.getComponents())if(child instanceof JButton link)link.doClick();
      assertThat(targets).containsExactly(new ViewTarget.ItemContent("set",42));
    });
  }
}
