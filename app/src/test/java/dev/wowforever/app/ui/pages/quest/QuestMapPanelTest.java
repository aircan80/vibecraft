package dev.wowforever.app.ui.pages.quest;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.GiverSpot;
import dev.wowforever.app.model.QuestGiver;
import dev.wowforever.app.model.QuestPageData;
import dev.wowforever.app.model.Spawn;
import dev.wowforever.app.ui.widgets.MapView.PinKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuestMapPanelTest {
  @Test
  void hoggerPinsGiversOnElwynn() {
    var loaded = QuestTestData.load(176);
    var map = new QuestMapPanel(loaded.data(), loaded.maps(), target -> {}, text -> {});
    assertThat(map.shownZone()).isEqualTo(12);
    // Every location of both posters is retained, plus the one turn-in location.
    assertThat(map.mapView().pins()).filteredOn(p -> p.kind() == PinKind.QUEST_START)
        .extracting(p -> p.ref().id()).contains(68L, 156561L);
    assertThat(map.mapView().pins()).filteredOn(p -> p.kind() == PinKind.QUEST_END).hasSize(1);
    assertThat(map.mapView().pins()).allMatch(p -> p.emphasised());
    assertThat(map.tabs()).extracting(Object::toString).containsExactly("Elwynn Forest ! ?");
    assertThat(map.banner().isVisible()).isFalse();
  }

  @Test
  void defiasSharesOneSpot() {
    var loaded = QuestTestData.load(166);
    var map = new QuestMapPanel(loaded.data(), loaded.maps(), target -> {}, text -> {});
    var view = map.mapView();
    view.setSize(600, 400);
    assertThat(view.clusters()).hasSize(1);
    var menu = view.menuFor(view.clusters().getFirst());
    assertThat(menu.getComponentCount()).isEqualTo(2);
    assertThat(((javax.swing.JMenuItem) menu.getComponent(0)).getText()).contains("Given by");
    assertThat(((javax.swing.JMenuItem) menu.getComponent(1)).getText()).contains("Turned in to");
    assertThat(map.tabs()).extracting(Object::toString).containsExactly("Westfall ! ?");
  }

  @Test
  void turnInElsewhereShowsBanner() {
    var loaded = QuestTestData.load(6502);
    QuestPageData original = loaded.data();
    QuestPageData changed = new QuestPageData(original.detail(), original.facts(), original.objectives(),
        original.sourceItems(), original.starts(), List.of(new GiverSpot(new QuestGiver(
            new EntityRef(EntityKind.NPC, 240), "Marshal Dughan", "end"),
            List.of(new Spawn(1519, "Stormwind City", 50, 50)), List.of())), original.chain());
    var map = new QuestMapPanel(changed, loaded.maps(), target -> {}, text -> {});
    assertThat(map.banner().getText()).isEqualTo("Turn-in is in Stormwind City ›");
    map.banner().doClick();
    assertThat(map.shownZone()).isEqualTo(1519);
    map.nextZone();
    assertThat(map.shownZone()).isEqualTo(618);
  }

  @Test
  void noMapForZone() {
    var loaded = QuestTestData.load(176);
    var map = new QuestMapPanel(loaded.data(), Map.of(), target -> {}, text -> {});
    assertThat(map.noMapNote().getText()).isEqualTo("No map for Elwynn Forest yet.");
    assertThat(map.coordinates().getText()).contains("Marshal Dughan", "42.1, 65.9");
    assertThat(map.mapView()).isNull();
  }

  @Test
  void itemStarterHasNoExclamationPin() {
    var loaded = QuestTestData.load(7787);
    var map = new QuestMapPanel(loaded.data(), loaded.maps(), target -> {}, text -> {});
    assertThat(map.shownZone()).isEqualTo(1377);
    assertThat(map.mapView().pins()).noneMatch(p -> p.kind() == PinKind.QUEST_START);
    assertThat(map.mapView().pins()).anyMatch(p -> p.kind() == PinKind.QUEST_END && p.ref().id() == 14347);
  }
}
