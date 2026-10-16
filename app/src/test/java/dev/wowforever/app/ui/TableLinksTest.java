package dev.wowforever.app.ui;

import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.DropSource;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.detail.LinkTable;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;

/** Click and hover behaviour of {@link TableLinks}, driven through a {@link LinkTable} of drop sources. */
class TableLinksTest {
  private static final int SOURCE = 0; // link column
  private static final int LEVEL = 1; // plain column
  private static final int ZONE_COLUMN = 2; // link column

  private final List<ViewTarget> opened = new ArrayList<>();
  private final LinkTable<DropRow> linkTable = new LinkTable<>(Columns.dropSourcesRanked(), opened::add);
  private final JTable table = linkTable.table();

  TableLinksTest() {
    linkTable.setRows(List.of(new DropRow.Source(
        new DropSource(new EntityRef(NPC, 7), "Boss", 30, 31, "Ragefire Chasm", 2437L, 5.0, "questie", "both"))));
  }

  private Point centre(int row, int col) {
    Rectangle r = table.getCellRect(row, col, false);
    return new Point(r.x + r.width / 2, r.y + r.height / 2);
  }

  private void mouse(int id, Point p, int clickCount) {
    MouseEvent e = new MouseEvent(table, id, 0L, MouseEvent.BUTTON1_DOWN_MASK, p.x, p.y, clickCount, false,
        MouseEvent.BUTTON1);
    switch (id) {
      case MouseEvent.MOUSE_MOVED -> {
        for (var l : table.getMouseMotionListeners()) {
          l.mouseMoved(e);
        }
      }
      case MouseEvent.MOUSE_EXITED -> {
        for (var l : table.getMouseListeners()) {
          l.mouseExited(e);
        }
      }
      default -> {
        for (var l : table.getMouseListeners()) {
          l.mouseClicked(e);
        }
      }
    }
  }

  @Test
  void singleClickOnLinkCellOpens() {
    mouse(MouseEvent.MOUSE_CLICKED, centre(0, SOURCE), 1);
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(NPC, 7)));
  }

  @Test
  void singleClickOnAnotherLinkColumnOpensThatCellsEntity() {
    mouse(MouseEvent.MOUSE_CLICKED, centre(0, ZONE_COLUMN), 1);
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(ZONE, 2437)));
  }

  @Test
  void singleClickOnNonLinkCellDoesNothing() {
    mouse(MouseEvent.MOUSE_CLICKED, centre(0, LEVEL), 1);
    assertThat(opened).isEmpty();
  }

  @Test
  void doubleClickOpensOnce() {
    mouse(MouseEvent.MOUSE_CLICKED, centre(0, SOURCE), 1);
    mouse(MouseEvent.MOUSE_CLICKED, centre(0, SOURCE), 2);
    assertThat(opened).hasSize(1);
  }

  @Test
  void doubleClickOnAPlainCellStillOpensTheRow() {
    mouse(MouseEvent.MOUSE_CLICKED, centre(0, LEVEL), 1);
    mouse(MouseEvent.MOUSE_CLICKED, centre(0, LEVEL), 2);
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(NPC, 7)));
  }

  @Test
  void hoverTracksCell() {
    mouse(MouseEvent.MOUSE_MOVED, centre(0, SOURCE), 0);
    assertThat(TableLinks.hoveredCell(table)).contains(new TableLinks.Cell(0, SOURCE));
    assertThat(table.getCursor().getType()).isEqualTo(Cursor.HAND_CURSOR);

    mouse(MouseEvent.MOUSE_MOVED, centre(0, LEVEL), 0);
    assertThat(TableLinks.hoveredCell(table)).contains(new TableLinks.Cell(0, LEVEL));
    assertThat(table.getCursor().getType()).isEqualTo(Cursor.DEFAULT_CURSOR);

    mouse(MouseEvent.MOUSE_MOVED, centre(0, ZONE_COLUMN), 0);
    mouse(MouseEvent.MOUSE_EXITED, new Point(-5, -5), 0);
    assertThat(TableLinks.hoveredCell(table)).isEmpty();
    assertThat(table.getCursor().getType()).isEqualTo(Cursor.DEFAULT_CURSOR);
  }

  @Test
  void onlyTheHoveredLinkCellIsUnderlined() {
    var renderer = table.getColumnModel().getColumn(SOURCE).getCellRenderer();
    var plain = renderer.getTableCellRendererComponent(table, "x", false, false, 0, SOURCE).getFont();
    assertThat(plain.getAttributes().get(java.awt.font.TextAttribute.UNDERLINE)).isNull();
    mouse(MouseEvent.MOUSE_MOVED, centre(0, SOURCE), 0);
    var hovered = renderer.getTableCellRendererComponent(table, "x", false, false, 0, SOURCE).getFont();
    assertThat(hovered.getAttributes().get(java.awt.font.TextAttribute.UNDERLINE))
        .isEqualTo(java.awt.font.TextAttribute.UNDERLINE_ON);
  }
}
