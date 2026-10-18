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
  @Test void siblingOptionsFollowRecordedParentsAndDistrictQueriesUseExistingIndex() throws Exception {
    Path file=temp.resolve("siblings.sqlite");Files.copy(FixtureDb.path(),file);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("CREATE TABLE ui_map(id INTEGER PRIMARY KEY,name TEXT,parent_id INTEGER)");
      s.execute("INSERT INTO ui_map VALUES(947,'Azeroth',0),(1415,'Eastern Kingdoms',947),(1414,'Kalimdor',947),(1436,'Westfall',1415),(1431,'Duskwood',1415)");
      s.execute("UPDATE zone SET ui_map_id=1436 WHERE id=40");
      s.execute("UPDATE zone SET ui_map_id=1431 WHERE id=10");
      s.execute("INSERT INTO zone(id,name,kind,parent_id) VALUES(99000,'Recorded district','subzone',40),(99001,'Other parent district','subzone',10)");
      try(var rows=s.executeQuery("EXPLAIN QUERY PLAN SELECT id,name FROM zone WHERE parent_id=40 ORDER BY name COLLATE NOCASE,id LIMIT 1001")) {
        var plan=new StringBuilder();while(rows.next())plan.append(rows.getString(4));
        assertThat(plan.toString()).contains("idx_zone_parent");
      }
    }
    try(var db=Database.open(file)) {
      var path=new ZoneRepository(db).breadcrumbs(99000);
      var zone=path.stream().filter(step -> step.id()==40 && step.kind()==GeographyStep.Kind.ZONE).findFirst().orElseThrow();
      assertThat(zone.siblings()).extracting(GeographyStep.Sibling::name).contains("Westfall","Duskwood");
      var district=path.getLast();
      assertThat(district.siblings()).extracting(GeographyStep.Sibling::name).contains("Recorded district").doesNotContain("Other parent district");
      assertThat(path.getFirst().siblings()).isEmpty();
      assertThatThrownBy(() -> district.siblings().clear()).isInstanceOf(UnsupportedOperationException.class);
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
