package dev.wowforever.importer.clientdata;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorldMapTransformTest {
  private static CsvTable fixture(String file) throws Exception {
    try(var in=new InputStreamReader(WorldMapTransformTest.class.getResourceAsStream("/worldmap/"+file),StandardCharsets.UTF_8)) {
      return CsvTable.read(file,in);
    }
  }
  @Test void fixedTwentyOneNpcCorpusReportsTwentyAgreementsAndHoggerOutlier() throws Exception {
    var assignments=WorldMapTransform.load(fixture("assignments.csv"));
    var examples=fixture("npc-comparison.csv");
    assertThat(examples.size()).isEqualTo(21);
    int agreements=0;var outliers=new java.util.ArrayList<Integer>();
    boolean messenger=false;
    for(var r:examples.rows()) {
      var actual=WorldMapTransform.projectTo(assignments,r.intOr("ui_map_id",-1),r.intOr("map_id",-1),r.doubleOr("world_x",0),r.doubleOr("world_y",0),r.doubleOr("world_z",0)).orElseThrow();
      assertThat(actual.uiMapId()).as("NPC %s map",r.get("npc_id")).isEqualTo(r.intOr("ui_map_id",-1));
      double error=Math.max(Math.abs(actual.x()-r.doubleOr("questie_x",0)),Math.abs(actual.y()-r.doubleOr("questie_y",0)));
      if(error<=1)agreements++;else outliers.add(r.intOr("npc_id",-1));
      messenger |= r.intOr("npc_id",0)==550;
    }
    assertThat(messenger).isTrue();
    assertThat(agreements).isEqualTo(20);
    assertThat(outliers).containsExactly(448);
  }
  @Test void choosesSmallestContainingAssignmentAndReturnsNoMapOutsideOrWrongWorldMap() {
    var outer=new WorldMapTransform.Assignment(1,947,0,0,0,0,-10,100,100,10,0,0,1,1);
    var inner=new WorldMapTransform.Assignment(2,1436,0,0,20,20,-10,40,40,10,0,0,1,1);
    assertThat(WorldMapTransform.project(List.of(outer,inner),0,25,30,0).orElseThrow()).isEqualTo(new WorldMapTransform.Position(1436,2,50,75));
    assertThat(WorldMapTransform.project(List.of(outer,inner),0,101,30,0)).isEmpty();
    assertThat(WorldMapTransform.project(List.of(outer,inner),1,25,30,0)).isEmpty();
    assertThat(WorldMapTransform.project(List.of(outer,inner),0,25,30,11)).isEmpty();
    assertThat(WorldMapTransform.project(List.of(outer,inner),0,Double.NaN,30,0)).isEmpty();
  }
  @Test void hoggerSourceDisagreementAndOverlappingZonesRemainExplicit() throws Exception {
    var assignments=WorldMapTransform.load(fixture("assignments.csv"));
    // VM creature guid81027, NPC448; QuestieDB Forever npc448 gives Elwynn(25.02,92.9).
    var result=WorldMapTransform.projectTo(assignments,1429,0,-10107.2,618.213,38.2045).orElseThrow();
    assertThat(result.x()).isCloseTo(26.4260354617,within(.000001));
    assertThat(Math.abs(result.x()-25.02)).isGreaterThan(1);
  }
}
