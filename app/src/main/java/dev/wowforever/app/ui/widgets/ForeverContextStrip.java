package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.model.ForeverContent.Context;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.*;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

/** Content counts and badges come from exact IDs compared across two pinned builds. */
public final class ForeverContextStrip extends JPanel {
  public ForeverContextStrip(Context context) {
    super(new BorderLayout());setOpaque(false);setBorder(BorderFactory.createEmptyBorder(6,12,8,12));
    String added=summary(context,"new"),changed=summary(context,"changed");
    List<String> lines=new ArrayList<>();
    if(!added.isBlank())lines.add("New here: "+added);
    if(!changed.isBlank())lines.add("Changed: "+changed);
    if(lines.isEmpty())lines.add(context.changes().isEmpty()?"No content comparison is available for this view.":"Technical differences are available in the advanced table view.");
    JPanel chips=new JPanel(new FlowLayout(FlowLayout.LEADING,4,0));chips.setOpaque(false);
    if(!added.isBlank())chips.add(new Chip("New",Chip.Style.PLAIN));
    if(!changed.isBlank())chips.add(new Chip("Changed",Chip.Style.PLAIN));
    if(chips.getComponentCount()>0)add(chips,BorderLayout.NORTH);
    add(new WrappingText(String.join(" · ",lines)+" · Forever "+context.foreverBuild()+" / Classic Era "+context.eraBuild(),"Forever context"));
  }
  private static String summary(Context context,String state) {
    Map<String,Set<String>> counts=new LinkedHashMap<>();
    for(var change:context.changes()) {
      if(!state.equals(change.status()))continue;
      String kind=switch(change.kind()) {
        case "AreaTable"->"area";case "Map"->"map";case "ItemSet"->"item set";
        case "Item","ItemSparse"->"item";case "ChrRaces"->"race";case "Faction"->"faction";
        case "TaxiNodes"->"flight point";case "LFGDungeons"->"dungeon finder entry";default->null;
      };
      if(kind!=null)counts.computeIfAbsent(kind,k->new HashSet<>()).add(change.id());
    }
    return String.join(", ",counts.entrySet().stream().map(e->e.getValue().size()+" "+e.getKey()+(e.getValue().size()==1?"":"s")).toList());
  }
}
