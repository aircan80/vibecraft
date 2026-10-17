package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.model.NativeItemMetadata;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import javax.swing.*;
import java.awt.*;

/** Known metadata stays visible independently of missing tooltip stats. */
public final class NativeItemMetadataPanel extends JPanel {
  public NativeItemMetadataPanel(NativeItemMetadata data,Navigator nav) {
    setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));setOpaque(false);setAlignmentX(LEFT_ALIGNMENT);
    setBorder(BorderFactory.createEmptyBorder(4,12,8,12));
    add(new WrappingText((data.nativeRow()?"Native item metadata":"Retained item metadata")+(data.build().isBlank()?"":" · Client "+data.build()),"Item metadata source"));
    if(data.inventoryType()!=null)add(new WrappingText("Slot: "+slot(data.inventoryType()),"Equipment slot"));
    for(var set:data.sets()) {
      JButton link=LinkButton.create("Set: "+set.name(),new ViewTarget.ItemContent("set",set.id()),nav);
      link.setAlignmentX(LEFT_ALIGNMENT);link.setFocusPainted(true);link.setToolTipText(set.evidence());add(link);
    }
    for(var effect:data.effects())add(new WrappingText("Effect: "+effect.name()+" (spell #"+effect.spellId()+") · "+trigger(effect.triggerType())+"\n"+effect.evidence(),"Native item effect"));
    setToolTipText(data.evidence());
  }
  public static String slot(int id) {
    return switch(id){case 0->"Not equippable";case 1->"Head";case 2->"Neck";case 3->"Shoulder";case 4->"Shirt";case 5,20->"Chest";case 6->"Waist";case 7->"Legs";case 8->"Feet";case 9->"Wrist";case 10->"Hands";case 11->"Finger";case 12->"Trinket";case 13->"One-Hand";case 14->"Shield";case 15->"Ranged";case 16->"Back";case 17->"Two-Hand";case 18->"Bag";case 19->"Tabard";case 21->"Main Hand";case 22->"Off Hand";case 23->"Held in Off-hand";case 24->"Ammo";case 25->"Thrown";case 26->"Ranged";case 28->"Relic";default->"Inventory type #"+id;};
  }
  private static String trigger(Integer id){if(id==null)return "Trigger unknown";return switch(id){case 0->"Use";case 1->"Equip";case 2->"Chance on hit";default->"Trigger type #"+id;};}
  @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
  @Override public Dimension getMinimumSize(){return new Dimension(0,getPreferredSize().height);}
}
