package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FixtureDb.class)
class WorldMapRepositoryTest {
  @Test void olderDatabaseKeepsListAvailable() {
    assertThat(FixtureDb.repositories().worldMaps().catalog().regions()).isEmpty();
  }

  @Test void readsHierarchyWithoutDroppingUnavailableOrUnplacedChildren(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("world.sqlite");
    Files.copy(FixtureDb.path(),file);
    try (var c=DriverManager.getConnection("jdbc:sqlite:"+file); var s=c.createStatement()) {
      s.execute("CREATE TABLE ui_map(id INTEGER PRIMARY KEY,parent_id INTEGER,name TEXT NOT NULL)");
      s.execute("CREATE TABLE ui_map_child(parent_ui_map_id INTEGER,child_ui_map_id INTEGER,x0 REAL,y0 REAL,x1 REAL,y1 REAL)");
      s.execute("INSERT INTO ui_map VALUES(947,0,'Azeroth'),(1415,947,'Eastern Kingdoms'),(1436,1415,'Westfall'),(99991,1415,'Unknown'),(2521,947,'Zephras Isle')");
      s.execute("INSERT INTO ui_map_child VALUES(947,1415,.55,.09,.90,.87),(1415,1436,.36,.71,.47,.82),(1415,99991,.1,.1,.2,.2),(947,2521,NULL,NULL,NULL,NULL)");
      // The fixture zone map need not be shipped; the hierarchy still links to the existing zone page.
      s.execute("UPDATE zone SET ui_map_id=1436 WHERE id=40");
    }
    try (var db=Database.open(file)) {
      var catalog=new WorldMapRepository(db).catalog();
      assertThat(catalog.children(947)).extracting(r -> r.uiMapId()).containsExactly(1415L,2521L);
      var westfall=catalog.map(1436).orElseThrow();
      assertThat(westfall.zoneId()).isEqualTo(40L);
      assertThat(westfall.placed()).isTrue();
      assertThat(westfall.x0()).isEqualTo(.36);
      assertThat(catalog.map(99991).orElseThrow().zoneId()).isNull();
      assertThat(catalog.map(2521).orElseThrow().placed()).isFalse();
      assertThat(catalog.map(947).orElseThrow().placed()).isFalse();
    }
  }
}
