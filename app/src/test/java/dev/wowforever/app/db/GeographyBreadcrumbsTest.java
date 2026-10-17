package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.app.model.GeographyStep;
import java.nio.file.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeographyBreadcrumbsTest {
  @TempDir Path temp;
  @Test void keepsTheCompleteMapZoneAndSubzoneHierarchy() throws Exception {
    Path file=temp.resolve("areas.sqlite");Files.copy(FixtureDb.path(),file);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("CREATE TABLE ui_map(id INTEGER PRIMARY KEY,name TEXT,parent_id INTEGER)");
      s.execute("INSERT INTO ui_map VALUES (947,'Azeroth',0),(1415,'Eastern Kingdoms',947),(1436,'Westfall',1415)");
      s.execute("UPDATE zone SET ui_map_id=1436 WHERE id=40");
      s.execute("INSERT INTO zone(id,name,kind,parent_id) VALUES(99000,'Lowest level','subzone',1581)");
    }
    try(var db=Database.open(file)) {
      var path=new ZoneRepository(db).breadcrumbs(99000);
      assertThat(path).extracting(GeographyStep::name).containsExactly("Azeroth","Eastern Kingdoms","Westfall","The Deadmines","Lowest level");
      assertThat(path).extracting(GeographyStep::kind).containsExactly(GeographyStep.Kind.MAP,GeographyStep.Kind.MAP,GeographyStep.Kind.ZONE,GeographyStep.Kind.ZONE,GeographyStep.Kind.ZONE);
      assertThat(path).extracting(GeographyStep::id).containsExactly(947L,1415L,40L,1581L,99000L);
    }
  }
  @Test void legacyMissingAndCyclicParentsNeverInventAWorldPath() throws Exception {
    Path file=temp.resolve("legacy.sqlite");Files.copy(FixtureDb.path(),file);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("INSERT INTO zone(id,name,kind,parent_id) VALUES(99000,'Broken parent','subzone',99999),(99001,'Cycle one','subzone',99002),(99002,'Cycle two','subzone',99001)");
    }
    try(var db=Database.open(file)) {
      var zones=new ZoneRepository(db);
      assertThat(zones.breadcrumbs(1581)).extracting(GeographyStep::name).containsExactly("Westfall","The Deadmines");
      assertThat(zones.breadcrumbs(99000)).extracting(GeographyStep::name).containsExactly("Broken parent");
      assertThat(zones.breadcrumbs(99001)).extracting(GeographyStep::name).containsExactly("Cycle one");
      assertThat(zones.breadcrumbs(99999)).isEmpty();
    }
  }
}
