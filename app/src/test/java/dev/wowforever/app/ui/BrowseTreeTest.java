package dev.wowforever.app.ui;

import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.OBJECT;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemClassNode;
import dev.wowforever.app.nav.ViewTarget;
import java.util.ArrayList;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class BrowseTreeTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void treeHasTopLevelSectionsAndInstances() {
    BrowseTree t = new BrowseTree(repos, target -> {});
    TreeModel m = t.model();
    assertThat(m.getChildCount(m.getRoot())).isEqualTo(6);
    assertThat(t.instanceNodeNames()).contains("Ragefire Chasm", "The Deadmines");
  }

  @Test
  void selectingInstanceOpensEntity() {
    List<ViewTarget> opened = new ArrayList<>();
    BrowseTree t = new BrowseTree(repos, opened::add);
    t.selectInstance("Ragefire Chasm");
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(INSTANCE, 1)));
  }

  @Test
  void sectionsInOrder() {
    BrowseTree t = new BrowseTree(repos, target -> {});
    TreeModel m = t.model();
    List<String> labels = new ArrayList<>();
    for (int i = 0; i < m.getChildCount(m.getRoot()); i++) {
      labels.add(m.getChild(m.getRoot(), i).toString());
    }
    assertThat(labels).containsExactly("Items", "NPCs by zone", "Objects by zone", "Quests by zone", "Zones",
        "Dungeons & Raids");
    assertThat(t.instanceNodeNames()).hasSize(repos.instances().all().size());
  }

  @Test
  void classNodeOpensWholeClassAndSubclassNodeFilters() {
    List<ViewTarget> opened = new ArrayList<>();
    BrowseTree t = new BrowseTree(repos, opened::add);
    t.selectPath("Items", "Armor");
    t.selectPath("Items", "Armor", "Cloth");
    assertThat(opened).containsExactly(new ViewTarget.ItemClass(4, ItemClassNode.CLASS_NODE),
        new ViewTarget.ItemClass(4, 1));
  }

  @Test
  void sectionRootsOpenLists() {
    List<ViewTarget> opened = new ArrayList<>();
    BrowseTree t = new BrowseTree(repos, opened::add);
    t.selectPath("Zones");
    t.selectPath("Dungeons & Raids");
    t.selectPath("Items"); // a plain folder: opens nothing
    assertThat(opened).containsExactly(new ViewTarget.List(ZONE), new ViewTarget.List(INSTANCE));
  }

  @Test
  void zoneUnderAListSectionOpensTheZoneList() {
    List<String> zoneLists = new ArrayList<>();
    List<ViewTarget> opened = new ArrayList<>();
    BrowseTree t = new BrowseTree(BrowseTree.Data.load(repos), opened::add,
        (kind, zoneId) -> zoneLists.add(kind + ":" + zoneId));
    t.selectZone("NPCs by zone", 2437);
    t.selectZone("Objects by zone", 12);
    assertThat(zoneLists).containsExactly(NPC + ":2437", OBJECT + ":12");
    assertThat(opened).isEmpty();
  }

  @Test
  void zoneUnderAListSectionDefaultsToZoneEntity() {
    List<ViewTarget> opened = new ArrayList<>();
    BrowseTree t = new BrowseTree(repos, opened::add);
    t.selectZone("Quests by zone", 12);
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(ZONE, 12)));
  }

  @Test
  void subzonesNestUnderTheirParent() {
    BrowseTree t = new BrowseTree(repos, target -> {});
    DefaultMutableTreeNode deadminesSubzone = t.zoneNode("NPCs by zone", 1581);
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) deadminesSubzone.getParent();
    assertThat(parent).isEqualTo(t.zoneNode("NPCs by zone", 40));
  }

  @Test
  void zoneSectionsHoldEveryZoneWithContent() {
    BrowseTree t = new BrowseTree(repos, target -> {});
    for (EntityKind kind : List.of(NPC, OBJECT, EntityKind.QUEST)) {
      String section = BrowseTree.zoneSection(kind);
      for (var z : repos.zones().all()) {
        assertThat(t.zoneNode(section, z.id())).as("%s / %s", section, z.id()).isNotNull();
      }
    }
  }
}
