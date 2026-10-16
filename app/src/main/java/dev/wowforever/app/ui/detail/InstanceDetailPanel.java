package dev.wowforever.app.ui.detail;

import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.BossSummary;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceDetail;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;

/**
 * The dungeon or raid page: the bosses (and AtlasLoot's extra loot lists) on the left, the loot of the selected
 * one on the right. The first boss is selected on construction.
 */
public final class InstanceDetailPanel extends JPanel {
  private final Repositories repos;
  private final Navigator nav;
  private final HeaderPanel header;
  private final JList<BossSummary> bosses;
  private final JPanel right = new JPanel(new BorderLayout());
  private BossLootPanel lootPanel;

  public InstanceDetailPanel(InstanceDetail d, Repositories repos, Navigator nav) {
    super(new BorderLayout());
    this.repos = repos;
    this.nav = nav;
    InstanceSummary s = d.summary();
    header = new HeaderPanel(s.displayName(), HeaderPanel.join("Instance #" + s.id(),
        Columns.contentTypeText(s.contentType()), HeaderPanel.levelText(s.levelMin(), s.levelMax())));
    bosses = new JList<>(d.bosses().toArray(BossSummary[]::new));
    bosses.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    bosses.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
          boolean cellHasFocus) {
        Object text = value instanceof BossSummary b ? bossLabel(b) : value;
        return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
      }
    });
    bosses.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        showLoot(bosses.getSelectedValue());
      }
    });
    showLoot(null);
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(bosses), right);
    split.setResizeWeight(0.3);
    split.setBorder(null);
    add(header, BorderLayout.NORTH);
    add(split, BorderLayout.CENTER);
    if (!d.bosses().isEmpty()) {
      selectBoss(0);
    }
  }

  /** {@code "<name>  (Lv <level>)"} followed by {@code [<special type>]} and {@code [extra]} markers. */
  static String bossLabel(BossSummary b) {
    StringBuilder sb = new StringBuilder(b.displayName());
    if (b.level() != null) {
      sb.append("  (Lv ").append(b.level()).append(')');
    }
    if (b.specialType() != null && !b.specialType().isBlank()) {
      sb.append("  [").append(b.specialType()).append(']');
    }
    if (b.extraList()) {
      sb.append("  [extra]");
    }
    return sb.toString();
  }

  /** Selects boss {@code index} and shows its loot. */
  public void selectBoss(int index) {
    bosses.setSelectedIndex(index);
    bosses.ensureIndexIsVisible(index);
  }

  /**
   * Shows the loot of {@code boss} (nothing for null), with a link to the boss's NPC page when it has one.
   * Runs on the EDT and loads the loot synchronously: the one documented exception to loading off the EDT.
   * A boss loot query is a single indexed lookup returning a few dozen rows, and the panel would otherwise
   * need its own async plumbing and stale-load guard for a load users never notice.
   */
  private void showLoot(BossSummary boss) {
    List<LootRow> loot = boss == null ? List.of() : repos.instances().bossLoot(boss.id());
    lootPanel = new BossLootPanel(loot, nav);
    right.removeAll();
    if (boss != null) {
      HeaderPanel bossHeader = new HeaderPanel(boss.displayName(), HeaderPanel.join(
          boss.level() == null ? null : "Level " + boss.level(), loot.size() + " items"));
      if (boss.npcId() != null) {
        bossHeader.addLink("NPC page", new EntityRef(EntityKind.NPC, boss.npcId()), nav);
      }
      right.add(bossHeader, BorderLayout.NORTH);
    }
    right.add(lootPanel, BorderLayout.CENTER);
    right.revalidate();
    right.repaint();
  }

  public HeaderPanel header() {
    return header;
  }

  /** The loot panel of the selected boss. */
  public BossLootPanel lootPanel() {
    return lootPanel;
  }
}
