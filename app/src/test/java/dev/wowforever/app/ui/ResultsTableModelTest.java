package dev.wowforever.app.ui;

import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.SearchHit;
import java.util.ArrayList;
import java.util.List;
import javax.swing.event.TableModelEvent;
import org.junit.jupiter.api.Test;

class ResultsTableModelTest {

  @Test
  void itemColumnsAndLinks() {
    var m = new ResultsTableModel<>(Columns.items());
    m.setRows(List.of(new ItemSummary(14149, "Subterranean Cape", 19, 14, "Armor", "Cloth", 1)));
    assertThat(m.getColumnCount()).isEqualTo(6);
    assertThat(m.getValueAt(0, 0)).isEqualTo("Subterranean Cape");
    assertThat(m.refAt(0, 0)).contains(new EntityRef(ITEM, 14149));
    assertThat(m.refAt(0, 1)).isEmpty();
  }

  @Test
  void itemColumnTitlesTypesAndValues() {
    var m = new ResultsTableModel<>(Columns.items());
    m.setRows(List.of(new ItemSummary(14149, "Subterranean Cape", 19, 14, "Armor", "Cloth", 1)));
    List<String> titles = new ArrayList<>();
    for (int c = 0; c < m.getColumnCount(); c++) {
      titles.add(m.getColumnName(c));
    }
    assertThat(titles).containsExactly("Name", "Item level", "Required level", "Class", "Subclass", "Sources");
    assertThat(m.getColumnClass(1)).isEqualTo(Integer.class);
    assertThat(m.getValueAt(0, 5)).isEqualTo(1);
    assertThat(m.column(0).isLink()).isTrue();
    assertThat(m.column(1).isLink()).isFalse();
    assertThat(m.firstLinkColumn()).isZero();
  }

  @Test
  void nameFallsBackForNamelessRows() {
    var m = new ResultsTableModel<>(Columns.items());
    m.setRows(List.of(new ItemSummary(7, null, null, null, null, null, 0)));
    assertThat(m.getValueAt(0, 0)).isEqualTo("Unknown item #7");
    assertThat(m.getValueAt(0, 1)).isNull();
  }

  @Test
  void setRowsFiresAndRowAtReturnsRow() {
    var m = new ResultsTableModel<>(Columns.searchHits());
    List<TableModelEvent> events = new ArrayList<>();
    m.addTableModelListener(events::add);
    SearchHit hit = new SearchHit(new EntityRef(NPC, 448), "Hogger", "NPC · Level 11 · Elwynn Forest");
    m.setRows(List.of(hit));
    assertThat(events).hasSize(1);
    assertThat(m.getRowCount()).isEqualTo(1);
    assertThat(m.rowAt(0)).isSameAs(hit);
    assertThat(m.refAt(0, 0)).contains(new EntityRef(NPC, 448));
    assertThat(m.getValueAt(0, 1)).isEqualTo("NPC · Level 11 · Elwynn Forest");
  }

  @Test
  void npcZoneColumnLinksToZone() {
    var m = new ResultsTableModel<>(Columns.npcs());
    m.setRows(List.of(new NpcSummary(448, "Hogger", null, 11, 11, 1, "Elwynn Forest", 12L),
        new NpcSummary(9, "Nowhere", null, null, null, null, null, null)));
    int zone = titles(m).indexOf("Zone");
    assertThat(m.refAt(0, zone)).contains(new EntityRef(ZONE, 12));
    assertThat(m.refAt(1, zone)).isEmpty();
    assertThat(m.getValueAt(0, titles(m).indexOf("Level")).toString()).isEqualTo("11");
    assertThat(m.getValueAt(0, titles(m).indexOf("Rank"))).isEqualTo("Elite");
  }

  @Test
  void instanceColumns() {
    var m = new ResultsTableModel<>(Columns.instances());
    m.setRows(List.of(new InstanceSummary(1, "Ragefire Chasm", "DUNGEON", 10, 13, 2437L, "Ragefire Chasm")));
    assertThat(m.refAt(0, 0)).contains(new EntityRef(INSTANCE, 1));
    assertThat(m.getValueAt(0, titles(m).indexOf("Type"))).isEqualTo("Dungeon");
    assertThat(m.getValueAt(0, titles(m).indexOf("Levels")).toString()).isEqualTo("10–13");
  }

  @Test
  void levelsSortNumericallyWithUnknownLast() {
    List<Columns.Levels> levels = new ArrayList<>(List.of(new Columns.Levels(10, 12), new Columns.Levels(null, null),
        new Columns.Levels(9, 9), new Columns.Levels(10, 11)));
    levels.sort(null);
    assertThat(levels).extracting(Columns.Levels::toString).containsExactly("9", "10–11", "10–12", "");
  }

  @Test
  void textHelpers() {
    assertThat(Columns.rankText(0)).isEqualTo("Normal");
    assertThat(Columns.rankText(3)).isEqualTo("Boss");
    assertThat(Columns.rankText(null)).isNull();
    assertThat(Columns.contentTypeText("RAID40")).isEqualTo("Raid (40)");
    assertThat(Columns.contentTypeText(null)).isNull();
  }

  @Test
  void everyListHasALinkedNameFirst() {
    for (List<? extends Columns.Column<?>> cols : List.of(Columns.items(), Columns.npcs(), Columns.objects(),
        Columns.quests(), Columns.zones(), Columns.instances(), Columns.searchHits())) {
      assertThat(cols.get(0).title()).isEqualTo("Name");
      assertThat(cols.get(0).isLink()).isTrue();
    }
  }

  private static List<String> titles(ResultsTableModel<?> m) {
    List<String> out = new ArrayList<>();
    for (int c = 0; c < m.getColumnCount(); c++) {
      out.add(m.getColumnName(c));
    }
    return out;
  }
}
