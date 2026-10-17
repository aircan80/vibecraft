package dev.wowforever.app.ui.pages.quest;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.GiverSpot;
import dev.wowforever.app.model.ObjectiveRow;
import dev.wowforever.app.model.ObjectiveSource;
import dev.wowforever.app.model.ObjectiveVerb;
import dev.wowforever.app.model.QuestObjective;
import dev.wowforever.app.model.QuestPageData;
import dev.wowforever.app.model.Spawn;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.widgets.MapView;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;

class ObjectiveMapTest {
  private QuestPageData data(List<ObjectiveRow> objectives) {
    var original = QuestTestData.load(176).data();
    return new QuestPageData(original.detail(), original.facts(), objectives, original.sourceItems(),
        original.starts(), original.ends(), original.chain());
  }

  @Test
  void focusChipCanClearAnObjectiveWithNoKnownZone() {
    var original = QuestTestData.load(176).data();
    var row = ObjectiveTextTest.row(ObjectiveVerb.KILL, List.of());
    var unknown = new QuestPageData(original.detail(), original.facts(), List.of(row), List.of(),
        List.of(), List.of(), original.chain());
    var map = new QuestMapPanel(unknown, Map.of(), t -> {}, text -> {});
    map.focus(1);
    assertThat(map.tabs()).isEmpty();
    assertThat(map.focusChip().isVisible()).isTrue();
    assertThat(javax.swing.SwingUtilities.isDescendingFrom(map.focusChip(), map)).isTrue();
    map.focusChip().doClick();
    assertThat(map.focusedObjective()).isNull();
    assertThat(map.focusChip().isVisible()).isFalse();
  }

  @Test
  void longUnmappedLocationListRemainsScrollableToItsLastLink() {
    var sources = IntStream.range(0, 9).mapToObj(i -> ObjectiveTextTest.source(EntityKind.NPC, 100 + i,
        "Source " + i, null, null)).toList();
    var map = new QuestMapPanel(data(List.of(ObjectiveTextTest.row(ObjectiveVerb.KILL, sources))), Map.of(),
        t -> {}, text -> {});
    map.setSize(500, map.getPreferredSize().height);
    layout(map);
    var scroll = map.coordinateScroll();
    assertThat(scroll.getViewport().getViewSize().height).isGreaterThan(scroll.getViewport().getExtentSize().height);
    var last = map.coordinateLinks().getComponent(map.coordinateLinks().getComponentCount() - 1);
    ((javax.swing.JComponent) last).scrollRectToVisible(new java.awt.Rectangle(0, 0, last.getWidth(), last.getHeight()));
    assertThat(scroll.getViewport().getViewPosition().y).isPositive();
  }

  private static void layout(java.awt.Container container) {
    container.doLayout();
    for (var child : container.getComponents()) {
      if (child instanceof java.awt.Container nested) layout(nested);
    }
  }

  @Test
  void topFiveSourcesThenShowAllAndFocusPersistsAcrossZones() {
    var sources = IntStream.range(0, 9).mapToObj(i -> ObjectiveTextTest.source(EntityKind.NPC, 100 + i,
        "Source " + i, 90.0 - i, "questie")).toList();
    var row = ObjectiveTextTest.row(ObjectiveVerb.COLLECT, sources);
    var map = new QuestMapPanel(data(List.of(row)), QuestTestData.load(176).maps(), t -> {}, text -> {});
    assertThat(map.pins(12).stream().filter(p -> p.kind() == MapView.PinKind.COLLECT)).hasSize(5);
    var panel = new ObjectivesPanel(List.of(row), List.of(), t -> {}, map::focus, map::showAllSources);
    assertThat(panel.rows().getFirst().showAllButton().getText()).isEqualTo("Show all 9 sources");
    panel.rows().getFirst().showAllButton().doClick();
    assertThat(map.mapView().pins().stream().filter(p -> p.kind() == MapView.PinKind.COLLECT)).hasSize(9);
    assertThat(panel.rows().getFirst().showAllButton().isVisible()).isFalse();
    map.focus(1);
    assertThat(map.focusedObjective()).isEqualTo(1);
    assertThat(map.focusChip().getText()).isEqualTo("Showing objective 1 ✕");
    map.focusChip().doClick();
    assertThat(map.focusedObjective()).isNull();
  }

  @Test
  void focusingSecondObjectiveHidesFirstAndRetainsGiversInCachedMaps() {
    var first = ObjectiveTextTest.row(ObjectiveVerb.KILL,
        List.of(ObjectiveTextTest.source(EntityKind.NPC, 1, "First", null, null)));
    var source = new ObjectiveSource(new EntityRef(EntityKind.OBJECT, 2), "Second", null, null,
        List.of(new Spawn(1519, "Stormwind City", 50, 50)));
    var second = new ObjectiveRow(2, ObjectiveVerb.USE, List.of(source.ref()), "Second", null,
        List.of(source), List.of(), null);
    var maps = Map.of(12L, new BufferedImage(100,100,2), 1519L, new BufferedImage(100,100,2));
    var map = new QuestMapPanel(data(List.of(first, second)), maps, t -> {}, text -> {});
    map.focus(2);
    assertThat(map.mapView().visiblePins()).noneMatch(p -> p.number() == 1).anyMatch(p -> p.kind().quest());
    map.showZone(1519);
    assertThat(map.mapView().visiblePins()).singleElement().satisfies(p -> {
      assertThat(p.number()).isEqualTo(2);
      assertThat(p.kind()).isEqualTo(MapView.PinKind.INTERACT);
    });
    map.showZone(12);
    assertThat(map.mapView().visiblePins()).noneMatch(p -> p.number() == 1);
    map.clearFocus();
    assertThat(map.mapView().visiblePins()).anyMatch(p -> p.number() == 1);
    var unmapped = new QuestMapPanel(data(List.of(first, second)), Map.of(), t -> {}, text -> {});
    unmapped.showZone(1519);
    unmapped.focus(1);
    assertThat(unmapped.coordinates().getText()).doesNotContain("Second");
    unmapped.clearFocus();
    assertThat(unmapped.coordinates().getText()).contains("Second");
  }

  @Test
  void killCreditAndActualEscortPointArePinned() {
    var loaded = QuestTestData.load(452);
    var map = new QuestMapPanel(loaded.data(), loaded.maps(), t -> {}, text -> {});
    assertThat(map.pins(130).stream().filter(p -> p.kind() == MapView.PinKind.KILL)
        .map(p -> p.ref().id()).distinct()).hasSize(9);
    var escort = QuestTestData.load(648);
    assertThat(escort.zoneNames()).containsEntry(440L, "Tanaris");
    assertThat(new QuestMapPanel(escort.data(), escort.maps(), t -> {}, text -> {}).pins(440))
        .anyMatch(p -> p.kind() == MapView.PinKind.EXPLORE && p.number() == 1);
  }

  @Test
  void unmappedItemSourcesAndObjectivePointsHaveLinksAndLoadedZoneNames() {
    var original = QuestTestData.load(7787).data();
    var giver = original.starts().getFirst();
    var source = new ObjectiveSource(new EntityRef(EntityKind.NPC, 14435), "Prince Thunderaan", null, null,
        List.of(new Spawn(999, "Unmapped test zone", 42, 65)));
    var start = new GiverSpot(giver.giver(), List.of(), List.of(source));
    var point = new ObjectiveRow(1, ObjectiveVerb.EXPLORE, List.of(), null, "Explore the cave", List.of(),
        List.of(new QuestObjective.Point(999, 50, 60)), null);
    var data = new QuestPageData(original.detail(), original.facts(), List.of(point), original.sourceItems(),
        List.of(start), List.of(), original.chain());
    List<ViewTarget> opened = new ArrayList<>();
    var map = new QuestMapPanel(data, Map.of(), Map.of(999L, "Unmapped test zone"), opened::add, text -> {});
    assertThat(map.tabs().getFirst().zoneName()).isEqualTo("Unmapped test zone");
    assertThat(map.coordinates().getText()).contains("Prince Thunderaan · 42.0, 65.0", "Explore the cave · 50.0, 60.0");
    ((JButton)map.coordinateLinks().getComponent(0)).doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(source.ref()));
    assertThat(map.noMapNote().getText()).isEqualTo("No map for Unmapped test zone yet.");
  }
}
