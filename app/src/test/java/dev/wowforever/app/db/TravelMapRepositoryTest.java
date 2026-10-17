package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FixtureDb.class)
class TravelMapRepositoryTest {
  @Test void olderDatabaseKeepsTravelUnknown() {
    assertThat(FixtureDb.repositories().travel().inZone(40).entrances()).isEmpty();
    assertThat(FixtureDb.repositories().travel().forInstance(3).locations()).isEmpty();
  }

  @Test void recordsKeepSourcesOptionalNpcAndSeparateRouteFragments(@TempDir Path dir) throws Exception {
    Path file=dir.resolve("travel.sqlite"); Files.copy(FixtureDb.path(),file);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("CREATE TABLE map_instance_access(zone_id,instance_id,trigger_id,name,x,y,raid,is_exit,exit_here,tooltip,source_snapshot_id)");
      s.execute("CREATE TABLE map_instance_entry_context(instance_id,parent_instance_id,trigger_id,source_snapshot_id)");
      s.execute("CREATE TABLE map_flight_node(zone_id,node_id,name,faction,x,y,npc_id,source_snapshot_id)");
      s.execute("CREATE TABLE map_flight_connection(from_node_id,to_node_id,to_name,source_snapshot_id)");
      s.execute("CREATE TABLE map_flight_route(zone_id,path_id,destination,point_index,x,y,source_snapshot_id,segment_index)");
      s.execute("INSERT INTO map_instance_access VALUES(40,3,78,'The Deadmines',38,77,0,0,1,'Dungeon · recorded source','snapshot')");
      s.execute("INSERT INTO map_flight_node VALUES(40,4,'Sentinel Hill','ALLIANCE',56,52,523,'snapshot'),(40,99,'Unmatched','NEUTRAL',40,40,NULL,'snapshot')");
      s.execute("INSERT INTO map_flight_connection VALUES(4,2,'Stormwind','snapshot')");
      s.execute("INSERT INTO map_flight_route VALUES(40,7,'Stormwind',0,10,10,'snapshot',0),(40,7,'Stormwind',1,20,20,'snapshot',0),(40,7,'Stormwind',2,80,80,'snapshot',1),(40,7,'Stormwind',3,90,90,'snapshot',1)");
      s.execute("INSERT OR REPLACE INTO meta(key,value) VALUES('map_layers_source_label','Client and Vanilla sources')");
    }
    try(var db=Database.open(file)) {
      var repository=new TravelMapRepository(db); var travel=repository.inZone(40);
      assertThat(travel.entrances()).singleElement().satisfies(p -> {assertThat(p.instance().id()).isEqualTo(3);assertThat(p.exitHere()).isTrue();});
      assertThat(travel.flights()).filteredOn(f -> f.id()==4).singleElement().satisfies(f -> {
        assertThat(f.flightMaster().id()).isEqualTo(523);assertThat(f.destinations()).containsExactly("Stormwind");
      });
      assertThat(travel.flights()).filteredOn(f -> f.id()==99).singleElement().satisfies(f -> assertThat(f.flightMaster()).isNull());
      assertThat(travel.routes()).hasSize(2).allSatisfy(r -> assertThat(r.points()).hasSize(2));
      assertThat(travel.sourceLabel()).isEqualTo("Client and Vanilla sources");
      assertThat(repository.forInstance(3).locations()).singleElement().satisfies(l -> assertThat(l.zoneName()).isEqualTo("Westfall"));
    }
  }
}
