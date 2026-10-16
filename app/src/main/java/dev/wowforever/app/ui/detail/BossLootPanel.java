package dev.wowforever.app.ui.detail;

import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JPanel;

/** The loot table of one AtlasLoot boss; each item links to its page. */
public final class BossLootPanel extends JPanel {
  private final LinkTable<LootRow> table;

  public BossLootPanel(List<LootRow> loot, Navigator nav) {
    super(new BorderLayout());
    table = new LinkTable<>(Columns.bossLoot(), nav);
    table.setRows(loot);
    add(table, BorderLayout.CENTER);
  }

  public int rowCount() {
    return table.rowCount();
  }

  public LinkTable<LootRow> table() {
    return table;
  }
}
