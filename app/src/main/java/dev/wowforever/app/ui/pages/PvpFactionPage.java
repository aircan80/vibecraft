package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.ForeverContent.Faction;
import dev.wowforever.app.nav.Navigator;

public final class PvpFactionPage extends ForeverPageLayout {
  public PvpFactionPage(Faction faction,Navigator nav){
    super(faction.name());text("Faction #"+faction.id()+" · Client build "+faction.build());
    if(!faction.description().isBlank())text(faction.description());
    text("Vendors: Not yet discovered. Reputation sources and currency requirements: Not yet discovered.");
  }
}
