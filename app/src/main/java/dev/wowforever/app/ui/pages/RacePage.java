package dev.wowforever.app.ui.pages;

import dev.wowforever.app.db.RaceRepository.Catalog;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/** Race details with explicitly named build baselines and source-qualified abilities. */
public final class RacePage extends JPanel {
  public RacePage(Catalog catalog,int raceId) {
    super(new BorderLayout(12,12));
    var race=catalog.races().stream().filter(r->r.id()==raceId).findFirst().orElse(null);
    if(race==null){add(new JLabel("Race not recorded in this dataset."));return;}
    var content=new PageContent();
    content.add(new dev.wowforever.app.ui.widgets.WrappingText(race.name()+" · Forever "+catalog.build(), "Race details"));
    content.add(new dev.wowforever.app.ui.widgets.WrappingText("Starting zone: "+(race.startZone().isBlank()?"not recorded":race.startZone()), "Race details"));
    content.add(new dev.wowforever.app.ui.widgets.WrappingText("Classic comparison: "+(catalog.classicBuild().isBlank()?"baseline not available":catalog.classicBuild()), "Race details"));
    var classes=new DefaultTableModel(new Object[]{"Class","<html>Forever<br>"+catalog.build(),"<html>Classic<br>"+(catalog.classicBuild().isBlank()?"unavailable":catalog.classicBuild())},0){@Override public boolean isCellEditable(int r,int c){return false;}};
    for(var cl:race.classes())classes.addRow(new Object[]{cl.name(),"Available",cl.classicAvailable()==null?"Not recorded":cl.classicAvailable()?"Available":"Absent from baseline allow-list"});
    var table=new JTable(classes);table.setFillsViewportHeight(true);table.getTableHeader().setPreferredSize(new Dimension(1,40));var classScroll=new JScrollPane(table);classScroll.setPreferredSize(new Dimension(550,150));content.add(classScroll);
    content.add(new dev.wowforever.app.ui.widgets.WrappingText("Racial abilities", "Race details"));
    content.add(new dev.wowforever.app.ui.widgets.WrappingText("Classic racial comparison: baseline ability text not recorded.", "Race details"));
    if(race.racials().isEmpty())content.add(new dev.wowforever.app.ui.widgets.WrappingText("Racial abilities are not recorded in this dataset.", "Race details"));
    for(var racial:race.racials()){
      content.add(new dev.wowforever.app.ui.widgets.WrappingText((racial.name().isBlank()?"Ability "+racial.abilityId():racial.name())+" · "+racial.kind(), "Race details"));
      var text=new dev.wowforever.app.ui.widgets.WrappingText(racial.description().isBlank()?"Description not recorded.":racial.description(),"Racial description");content.add(text);
      content.add(new dev.wowforever.app.ui.widgets.WrappingText("Source: "+racial.source()+" · record "+racial.abilityId(), "Race details"));
      content.add(Box.createVerticalStrut(10));
    }
    add(new JScrollPane(content),BorderLayout.CENTER);
  }
}
