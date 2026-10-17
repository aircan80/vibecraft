package dev.wowforever.app.ui.pages;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.OBJECT;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static dev.wowforever.app.ui.pages.Tables.column;
import static dev.wowforever.app.ui.pages.Tables.columnNames;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ObjectDetail;
import dev.wowforever.app.model.ObjectSummary;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.widgets.Images;
import dev.wowforever.app.ui.widgets.MapView.Pin;
import dev.wowforever.app.ui.widgets.MapView.PinKind;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class ObjectPageTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void headerContentsAndSpawns() {
    List<ViewTarget> opened = new ArrayList<>();
    List<String> copied = new ArrayList<>();
    var p = new ObjectPage(repos.objects().detail(386759).get(), opened::add, copied::add);
    assertThat(p.header().title()).isEqualTo("Library Book");
    assertThat(p.header().subtitle()).isEqualTo("Object #386759 · Elwynn Forest");
    assertThat(p.sections()).extracting(SourceSection::title).containsExactly("Contains", "Spawns");
    var contains = p.section("Contains").table();
    assertThat(columnNames(contains.table())).containsExactly("Item");
    assertThat(column(contains.table(), 0)).containsExactly("Archmage Theocritus' Research Journal");
    contains.activateRow(0);
    p.header().links().get(0).doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(ITEM, 203755)),
        new ViewTarget.Entity(new EntityRef(ZONE, 12)));
    // the spawn is in the object's own zone: coordinates only
    assertThat(columnNames(p.spawns().table().table())).containsExactly("Coordinates");
    p.spawns().copyButton().doClick();
    assertThat(copied).containsExactly("/way Elwynn Forest 65.4 70.1");
  }

  @Test
  void questSectionsAndAnObjectWithoutZone() {
    QuestSummary q = new QuestSummary(136, "Captain Sander's Hidden Treasure", 16, 14, "Westfall");
    var p = new ObjectPage(new ObjectDetail(new ObjectSummary(35, null, null, null), List.of(), List.of(q),
        List.of(q), List.of()), t -> {}, text -> {});
    assertThat(p.header().title()).isEqualTo("Unknown object #35");
    assertThat(p.header().subtitle()).isEqualTo("Object #35");
    assertThat(p.sections()).extracting(SourceSection::title).containsExactly("Starts quests", "Ends quests");
    assertThat(columnNames(p.section("Ends quests").table().table()))
        .containsExactly("Name", "Level", "Required level", "Zone");
    assertThat(p.spawns()).isNull();
  }

  @Test
  void spawnsOnMap() {
    BufferedImage elwynn = Images.decode(repos.zones().map(12).orElseThrow().jpeg()).orElseThrow();
    var p = new ObjectPage(repos.objects().detail(386759).get(), 12L, elwynn, t -> {}, text -> {});
    assertThat(p.spawnMap().pins()).singleElement()
        .isEqualTo(new Pin(new EntityRef(OBJECT, 386759), "Library Book", 65.4, 70.1, PinKind.SELF));
    assertThat(javax.swing.SwingUtilities.isDescendingFrom(p.spawnMap(), p.section("Spawns"))).isTrue();
    assertThat(new ObjectPage(repos.objects().detail(386759).get(), t -> {}, text -> {}).spawnMap()).isNull();
  }
}
