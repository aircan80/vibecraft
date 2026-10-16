package dev.wowforever.app.ui;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.model.SearchHit;
import dev.wowforever.app.nav.ViewTarget;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import org.junit.jupiter.api.Test;

class ResultsPanelTest {

  private static ResultsTableModel<ItemSummary> twoItems() {
    var m = new ResultsTableModel<>(Columns.items());
    m.setRows(List.of(new ItemSummary(2, "Zeta", 5, 1, "Armor", "Cloth", 0),
        new ItemSummary(1, "Alpha", 9, 1, "Armor", "Cloth", 2)));
    return m;
  }

  @Test
  void titleShowsCount() {
    ResultsPanel p = new ResultsPanel(t -> {});
    p.show("Armor", twoItems());
    assertThat(p.title()).isEqualTo("Armor (2)");
    assertThat(p.table().getRowCount()).isEqualTo(2);
    assertThat(p.model().getRowCount()).isEqualTo(2);
  }

  @Test
  void cappedTitleShowsFirstNOnce() {
    ResultsPanel p = new ResultsPanel(t -> {});
    p.show("Weapon", twoItems(), true);
    assertThat(p.title()).isEqualTo("Weapon (first 2)");
  }

  @Test
  void activateRowOpensNameLinkThroughTheSorter() {
    List<ViewTarget> opened = new ArrayList<>();
    ResultsPanel p = new ResultsPanel(opened::add);
    p.show("Armor", twoItems());
    p.table().getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
    p.activateRow(0); // "Alpha" sorts first but is model row 1
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(ITEM, 1)));
  }

  @Test
  void enterOpensTheSelectedRow() {
    List<ViewTarget> opened = new ArrayList<>();
    ResultsPanel p = new ResultsPanel(opened::add);
    var m = new ResultsTableModel<>(Columns.searchHits());
    m.setRows(List.of(new SearchHit(new EntityRef(NPC, 448), "Hogger", "NPC")));
    p.show("Search: hogger", m);
    p.table().setRowSelectionInterval(0, 0);
    Object key = p.table().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .get(KeyStroke.getKeyStroke("ENTER"));
    p.table().getActionMap().get(key).actionPerformed(new ActionEvent(p.table(), ActionEvent.ACTION_PERFORMED, ""));
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(NPC, 448)));
  }

  @Test
  void linkColumnsUseTheLinkRenderer() {
    ResultsPanel p = new ResultsPanel(t -> {});
    p.show("Armor", twoItems());
    assertThat(p.table().getColumnModel().getColumn(0).getCellRenderer()).isInstanceOf(LinkRenderer.class);
    assertThat(p.table().getColumnModel().getColumn(1).getCellRenderer()).isNull();
  }
}
