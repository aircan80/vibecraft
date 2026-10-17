package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.ForeverContent.Discovery;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.widgets.Chip;
import java.awt.FlowLayout;
import javax.swing.*;

/** Reports retain their state even when the map itself is known in the client. */
public final class InstanceDiscoveryPage extends ForeverPageLayout {
  public InstanceDiscoveryPage(Discovery data,Navigator nav) {
    super(data.name());
    text(data.type()+" · Map #"+data.mapId()+" · Client build "+data.build());
    text("Loot: 0 confirmed items in this discovery view. Boss reports are not evidence of drops.");
    text("Opening: "+data.opening()+(data.openingSource().isBlank()?"":" · "+data.openingSource()));
    if(data.encounters().isEmpty())text("Bosses: Not yet discovered in the archived sources.");
    for(var encounter:data.encounters()){
      JPanel row=new JPanel(new FlowLayout(FlowLayout.LEADING));row.add(new JLabel(encounter.name()));
      Chip state=new Chip(encounter.state(),Chip.Style.PLAIN);state.setToolTipText(encounter.source());row.add(state);addRow(row);
      text(encounter.source());
    }
    text("Abilities and encounter coverage: Not yet discovered. No items are linked by matching their names.");
  }
}
