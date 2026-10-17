package dev.wowforever.app.ui.pages;

import dev.wowforever.app.db.RaceRepository.Catalog;
import java.awt.*;
import java.util.function.IntConsumer;
import javax.swing.*;

/** Race catalogue; all data is loaded before constructing Swing components. */
public final class RacesPage extends JPanel {
  public RacesPage(Catalog catalog, IntConsumer openRace) {
    super(new BorderLayout(12,12));
    add(new dev.wowforever.app.ui.widgets.WrappingText("Races · Forever " + (catalog.build().isBlank()?"build unavailable":catalog.build()), "Race catalogue build"),BorderLayout.NORTH);
    var grid=new PageContent();
    for(var race:catalog.races()) {
      var card=new JPanel(new BorderLayout(8,8));
      card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(),BorderFactory.createEmptyBorder(12,12,12,12)));
      var button=new JButton(race.name());button.addActionListener(e->openRace.accept(race.id()));card.add(button,BorderLayout.NORTH);
      card.add(new dev.wowforever.app.ui.widgets.WrappingText(race.classes().size()+" recorded classes · "+race.racials().size()+" racial abilities", "Race summary"),BorderLayout.CENTER);
      grid.add(card);
    }
    if(catalog.races().isEmpty()) grid.add(new JLabel("Race source tables are not available in this dataset."));
    add(new JScrollPane(grid),BorderLayout.CENTER);
  }
}
