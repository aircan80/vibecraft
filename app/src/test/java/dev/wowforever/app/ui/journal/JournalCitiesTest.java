package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.db.Database;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.ViewTarget;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournalCitiesTest {
  @TempDir Path temp;
  @Test void citiesRequireExactPinnedForeverAreaAndUseItsName() throws Exception {
    Path file=temp.resolve("cities.sqlite");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("CREATE TABLE meta(key TEXT,value TEXT)");
      s.execute("INSERT INTO meta VALUES('forever_diff_forever_snapshot_id','forever')");
      s.execute("CREATE TABLE zone(id INTEGER,name TEXT,kind TEXT)");
      s.execute("INSERT INTO zone VALUES(1519,'Legacy name','city'),(999,'Era only','city'),(1,'Not city','zone'),(1617,'Old district','subzone')");
      s.execute("CREATE TABLE client_table_dataset(snapshot_id TEXT,product TEXT,build TEXT)");
      s.execute("INSERT INTO client_table_dataset VALUES('forever','wow_classic_beta','1.60.test'),('era','wow_classic_era','1.15.test')");
      s.execute("CREATE TABLE client_table_row(snapshot_id TEXT,table_name TEXT,row_id TEXT,payload TEXT)");
      s.execute("INSERT INTO client_table_row VALUES('forever','AreaTable','1519','{\"AreaName_lang\":\"Stormwind City\",\"ParentAreaID\":0}'),('era','AreaTable','999','{\"AreaName_lang\":\"Era only\"}'),('forever','AreaTable','1617','{\"AreaName_lang\":\"Valley of Heroes\",\"ParentAreaID\":1519}')");
    }
    JournalCities.Snapshot snapshot;
    try(var db=Database.open(file)){snapshot=JournalCities.load(db);}
    assertThat(snapshot.cities()).containsExactly(new JournalCities.City(1519,"Stormwind City",null));
    assertThat(snapshot.build()).isEqualTo("1.60.test");
    try(var db=Database.open(file)){
      var guide=JournalCities.loadGuide(db,1519);
      assertThat(guide.districts()).containsExactly(new JournalCities.District(1617,"Valley of Heroes",true));
      assertThat(JournalCities.loadGuide(db,999)).isNull();
      SwingUtilities.invokeAndWait(()->{
        List<ViewTarget> opened=new ArrayList<>();var page=new JournalCityPage(guide,new javax.swing.JPanel(),opened::add,null);
        buttons(page).stream().filter(b->b.getText().equals("Valley of Heroes  ›")).findFirst().orElseThrow().doClick();
        assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(EntityKind.ZONE,1617)));
        buttons(page).stream().filter(b->b.getText().equals("Open city map")).findFirst().orElseThrow().doClick();
        Object state=page.saveState();var restored=new JournalCityPage(guide,new javax.swing.JPanel(),opened::add,null);restored.restoreState(state);
        assertThat(restored.saveState()).isEqualTo(state);
      });
    }
    SwingUtilities.invokeAndWait(()->{
      List<ViewTarget> opened=new ArrayList<>();var page=JournalCities.render(snapshot,opened::add);
      buttons(page).stream().filter(b->b.getText().equals("Explore Stormwind City")).findFirst().orElseThrow().doClick();
      assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(EntityKind.ZONE,1519)));
    });
  }
  private static List<JButton> buttons(Container root) {
    List<JButton> found=new ArrayList<>();for(Component c:root.getComponents()){if(c instanceof JButton b)found.add(b);if(c instanceof Container child)found.addAll(buttons(child));}return found;
  }
}
