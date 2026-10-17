package dev.wowforever.app;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Geography and provenance checks against the actual retained client inputs. */
@Tag("shipped-data")
class ShippedWorldMapDataTest extends ShippedDataSupport {
  @Test void continentsLeadToWestfallWithoutInventedIslandCoordinates() {
    var catalog=repos.worldMaps().catalog();
    assertThat(catalog.sourceBuild()).isEqualTo("1.60.1.70374");
    assertThat(db.meta().get("client_build")).isEqualTo("1.60.1.70450");
    assertThat(catalog.children(947)).extracting(r -> r.uiMapId()).contains(1414L,1415L,2521L);
    var westfall=catalog.children(1415).stream().filter(r -> r.uiMapId()==1436).findFirst().orElseThrow();
    assertThat(westfall.zoneId()).isEqualTo(40L);
    assertThat(westfall.placed()).isTrue();
    assertThat(westfall.x0()).isCloseTo(.3688457454,within(.000001));
    assertThat(westfall.y0()).isCloseTo(.7187492022,within(.000001));
    assertThat(westfall.x1()).isCloseTo(.4682778397,within(.000001));
    assertThat(westfall.y1()).isCloseTo(.8181812885,within(.000001));
    assertThat(catalog.children(1414)).extracting(r -> r.uiMapId()).contains(2524L);
    assertThat(catalog.regions()).filteredOn(r -> r.uiMapId()==2521 || r.uiMapId()==2524)
        .hasSize(2).allSatisfy(r -> assertThat(r.placed()).isFalse());
    assertThat(scalar("SELECT count(*) FROM zone z JOIN ui_map m ON m.id=z.ui_map_id WHERE m.parent_id IN(1414,1415)"
        + " AND NOT EXISTS(SELECT 1 FROM ui_map_child r WHERE r.parent_ui_map_id=m.parent_id AND r.child_ui_map_id=m.id)")).isZero();
  }
  @Test void areaAndInstancePathsContinueThroughTheLowestKnownArea() {
    assertThat(repos.zones().breadcrumbs(40)).extracting(r -> r.name())
        .containsExactly("Azeroth","Eastern Kingdoms","Westfall");
    assertThat(repos.zones().breadcrumbs(6170)).extracting(r -> r.name())
        .containsExactly("Azeroth","Eastern Kingdoms","Elwynn Forest","Northshire");
    var loaded=(dev.wowforever.app.ui.detail.DetailPanels.LoadedInstance)
        dev.wowforever.app.ui.detail.DetailPanels.load(
            new dev.wowforever.app.model.EntityRef(dev.wowforever.app.model.EntityKind.INSTANCE,3),repos).orElseThrow();
    assertThat(loaded.breadcrumbs()).extracting(r -> r.name())
        .containsExactly("Azeroth","Eastern Kingdoms","Westfall","The Deadmines");
  }
  @Test void hierarchyHasArtAndKeepsTheOriginalEvidenceActive() {
    for(long id:new long[]{947,1414,1415}) assertThat(repos.zones().mapByUiMap(id)).isPresent();
    String snapshot=db.meta().get("world_map_snapshot_id");
    var catalog=repos.sourceArchive().catalog();
    assertThat(catalog.history()).filteredOn(r -> r.active()).singleElement().satisfies(r -> {
      assertThat(r.buildId()).isEqualTo(db.meta().get("build_id"));
      assertThat(r.inputs()).extracting(i -> i.role()).contains("allthethings","configuration","legacy-app","world-map-"+snapshot);
    });
    assertThat(catalog.snapshots()).filteredOn(s -> s.id().equals(snapshot)).singleElement().satisfies(s -> {
      assertThat(s.source()).isEqualTo("allthethings-world-map");
      assertThat(s.version()).contains("local input; content identified by hash");
      assertThat(s.files()).hasSize(2).allSatisfy(f -> assertThat(f.url()).isNullOrEmpty());
    });
    assertThat(scalar("SELECT count(*) FROM source_record WHERE snapshot_id='"+snapshot+"'")).isPositive();
    assertThat(scalar("SELECT count(*) FROM sqlite_master WHERE name='collector_fact'")).isZero();
  }
}
