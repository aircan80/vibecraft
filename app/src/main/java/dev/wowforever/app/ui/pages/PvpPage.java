package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.ForeverContent.Pvp;
import dev.wowforever.app.nav.*;
import dev.wowforever.app.ui.widgets.LinkButton;

/** Client-attested battleground and faction pages; no guessed stock or reputation gains. */
public final class PvpPage extends ForeverPageLayout {
  public PvpPage(Pvp data,Navigator nav){
    super("Forever PvP");
    for(var map:data.battlegrounds())text(map.name()+" · "+map.type()+" · Map #"+map.mapId()+" · Build "+map.build());
    if(data.battlegrounds().isEmpty())text("Battleground details: Not yet discovered in the archived client tables.");
    for(var faction:data.factions())addRow(LinkButton.create(faction.name(),new ViewTarget.Forever("pvp-faction",faction.id()),nav));
    text("Vendor stock, prices, reputation gains and match rules: Not yet discovered.");
  }
}
