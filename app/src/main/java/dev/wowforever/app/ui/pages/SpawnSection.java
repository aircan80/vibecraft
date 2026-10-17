package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.Spawn;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 * The Spawns section of the NPC and object pages: each spawn point's zone and coordinates ({@code "42.3, 67.4"};
 * the Zone column is left out when every point is in the page's zone), and a "Copy /way" button that copies a
 * TomTom waypoint command for the selected point (the first when none is selected). Ctrl+Shift+C on a point
 * copies the same command.
 */
final class SpawnSection {
  private final SourceSection section;
  private final LinkTable<Spawn> table;
  private final JButton copy = new JButton("Copy /way");

  SpawnSection(List<Spawn> spawns, String pageZone, Navigator nav, ClipboardPort clipboard) {
    table = LinkTable.inline(VisibleColumns.of(Columns.spawns(), spawns, pageZone), nav);
    table.setRows(spawns);
    table.setClipboard(clipboard);
    table.setIdText(s -> Columns.hasCoordinates(s) ? Optional.of(wayCommand(s)) : Optional.empty());
    section = new SourceSection("Spawns", spawns.size(), table);
    copy.setToolTipText("Copy a /way command for the selected spawn point");
    copy.addActionListener(e -> {
      Spawn target = target();
      if (target != null && Columns.hasCoordinates(target)) {
        clipboard.copy(wayCommand(target));
      }
    });
    table.table().getSelectionModel().addListSelectionListener(e -> updateCopy());
    updateCopy();
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    buttons.setOpaque(false);
    buttons.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
    buttons.add(copy);
    section.add(buttons, BorderLayout.SOUTH);
  }

  /** {@code "/way Westfall 42.3 67.4"}; without the zone when its name is unknown. */
  static String wayCommand(Spawn s) {
    String coordinates = String.format(Locale.ROOT, "%.1f %.1f", s.x(), s.y());
    return s.zoneName() == null ? "/way " + coordinates : "/way " + s.zoneName() + " " + coordinates;
  }

  /** The selected spawn point, else the first; null without spawns. */
  private Spawn target() {
    int view = table.table().getSelectedRow();
    if (view >= 0) {
      return table.model().rowAt(table.table().convertRowIndexToModel(view));
    }
    return table.rowCount() == 0 ? null : table.model().rowAt(0);
  }

  private void updateCopy() {
    Spawn target = target();
    copy.setEnabled(target != null && Columns.hasCoordinates(target));
  }

  SourceSection section() {
    return section;
  }

  LinkTable<Spawn> table() {
    return table;
  }

  JButton copyButton() {
    return copy;
  }
}
