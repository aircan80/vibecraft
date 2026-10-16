package dev.wowforever.app.ui.pages;

import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.BossSummary;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceDetail;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Labels;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;

/**
 * The dungeon or raid page: the header ({@code "Raid (40) · Level 60–63 · <zone> · NPCs in <zone>"}), then on the
 * left the numbered encounters ({@code "1. Lucifron  Lv 63  Boss"}) and AtlasLoot's extra lists under
 * "Other loot lists", on the right the loot of the selected one grouped by {@link SlotGroups} in slot order.
 * A difficulty switch shows only when the instance's loot has more than one difficulty. The first encounter is
 * selected on construction.
 */
public final class InstancePage extends JPanel {
  /** AtlasLoot's level for a boss shown with a skull. */
  private static final int SKULL_LEVEL = 999;

  private final Repositories repos;
  private final Navigator nav;
  private final HeaderPanel header;
  private final List<BossSummary> mainBosses;
  private final List<BossSummary> extraBosses;
  private final JList<BossSummary> encounters;
  private final JList<BossSummary> extraLists;
  private final JLabel extraListsHeading = goldHeading("Other loot lists");
  private final List<String> difficulties;
  private final JPanel difficultyControl;
  private final PageContent lootPane = new PageContent();
  private String difficulty;
  private BossSummary shown;
  private HeaderPanel bossHeader;
  private SourceSection lootSection;
  private LinkTable<GroupedRows.Row<LootRow>> lootTable;

  public InstancePage(InstanceDetail d, Repositories repos, Navigator nav) {
    super(new BorderLayout());
    this.repos = repos;
    this.nav = nav;
    InstanceSummary s = d.summary();
    header = header(s, nav);
    mainBosses = d.bosses().stream().filter(b -> !b.extraList()).toList();
    extraBosses = d.bosses().stream().filter(BossSummary::extraList).toList();
    encounters = bossList(mainBosses, i -> encounterLabel(i + 1, mainBosses.get(i)));
    extraLists = bossList(extraBosses, i -> extraBosses.get(i).displayName());
    extraListsHeading.setVisible(!extraBosses.isEmpty());
    extraLists.setVisible(!extraBosses.isEmpty());
    linkSelections(encounters, extraLists);
    linkSelections(extraLists, encounters);
    difficulties = d.bosses().isEmpty() ? List.of() : repos.instances().difficulties(s.id());
    difficulty = difficulties.isEmpty() ? null : difficulties.get(0);
    difficultyControl = difficulties.size() > 1 ? difficultySwitch() : null;
    JScrollPane right = new JScrollPane(lootPane);
    right.setBorder(BorderFactory.createEmptyBorder());
    right.getVerticalScrollBar().setUnitIncrement(16);
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, encounterPane(), right);
    split.setResizeWeight(0.3);
    split.setBorder(null);
    add(header, BorderLayout.NORTH);
    add(split, BorderLayout.CENTER);
    if (!d.bosses().isEmpty()) {
      selectBoss(0);
    }
  }

  /** The instance name over its type, levels, a zone link and an "NPCs in" link to the same zone. */
  private static HeaderPanel header(InstanceSummary s, Navigator nav) {
    HeaderPanel header = new HeaderPanel(Labels.instanceTitle(s.displayName(), s.key()),
        Labels.instanceSubtitle(s.contentType(), s.levelMin(), s.levelMax(), null));
    if (s.zoneId() != null) {
      EntityRef zone = new EntityRef(EntityKind.ZONE, s.zoneId());
      String name = zone.displayName(s.zoneName());
      header.addLink(name, zone, nav);
      header.addLink("NPCs in " + name, zone, nav);
    }
    return header;
  }

  /** {@code "1. Lucifron  Lv 63  Boss"}: AtlasLoot's special type (Rare, Elite) in place of Boss when it has one. */
  static String encounterLabel(int number, BossSummary b) {
    String level = levelText(b.level());
    String kind = b.specialType() == null || b.specialType().isBlank() ? "Boss" : capitalised(b.specialType());
    return number + ". " + b.displayName() + (level == null ? "" : "  Lv " + level) + "  " + kind;
  }

  /** The level, {@code "??"} for AtlasLoot's skull level, null when unknown. */
  private static String levelText(Integer level) {
    if (level == null || level <= 0) {
      return null;
    }
    return level >= SKULL_LEVEL ? "??" : String.valueOf(level);
  }

  private static String capitalised(String word) {
    String w = word.strip().toLowerCase(Locale.ROOT);
    return w.substring(0, 1).toUpperCase(Locale.ROOT) + w.substring(1);
  }

  private static JList<BossSummary> bossList(List<BossSummary> bosses, IntFunction<String> label) {
    JList<BossSummary> list = new JList<>(bosses.toArray(BossSummary[]::new));
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setAlignmentX(LEFT_ALIGNMENT);
    list.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean isSelected,
          boolean cellHasFocus) {
        return super.getListCellRendererComponent(l, label.apply(index), index, isSelected, cellHasFocus);
      }
    });
    return list;
  }

  /** A selection in {@code from} clears {@code other} and shows the selected loot. */
  private void linkSelections(JList<BossSummary> from, JList<BossSummary> other) {
    from.addListSelectionListener(e -> {
      BossSummary boss = from.getSelectedValue();
      if (!e.getValueIsAdjusting() && boss != null) {
        other.clearSelection();
        showLoot(boss);
      }
    });
  }

  private JScrollPane encounterPane() {
    JPanel left = new JPanel();
    left.setLayout(new BoxLayout(left, BoxLayout.PAGE_AXIS));
    left.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
    left.add(encounters);
    extraListsHeading.setAlignmentX(LEFT_ALIGNMENT);
    left.add(extraListsHeading);
    left.add(extraLists);
    JPanel top = new JPanel(new BorderLayout());
    top.add(left, BorderLayout.NORTH);
    JScrollPane pane = new JScrollPane(top);
    pane.setBorder(BorderFactory.createEmptyBorder());
    return pane;
  }

  /** One toggle per difficulty ({@code "Normal"}, {@code "Heroic"}), the first selected. */
  private JPanel difficultySwitch() {
    JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    bar.setOpaque(false);
    bar.setAlignmentX(LEFT_ALIGNMENT);
    bar.setBorder(BorderFactory.createEmptyBorder(0, 12, 4, 12));
    ButtonGroup group = new ButtonGroup();
    for (String value : difficulties) {
      JToggleButton b = new JToggleButton(capitalised(value), value.equals(difficulty));
      b.addActionListener(e -> {
        difficulty = value;
        if (shown != null) {
          showLoot(shown);
        }
      });
      group.add(b);
      bar.add(b);
    }
    return bar;
  }

  /**
   * Selects {@code d.bosses().get(index)} (main encounters first, then extra lists, as the repository orders
   * them) in its list, which shows its loot.
   */
  public void selectBoss(int index) {
    if (index < 0 || index >= mainBosses.size() + extraBosses.size()) {
      return;
    }
    BossSummary boss = bossAt(index);
    JList<BossSummary> list = boss.extraList() ? extraLists : encounters;
    int at = (boss.extraList() ? extraBosses : mainBosses).indexOf(boss);
    list.setSelectedIndex(at);
    list.ensureIndexIsVisible(at);
  }

  private BossSummary bossAt(int index) {
    return index < mainBosses.size() ? mainBosses.get(index) : extraBosses.get(index - mainBosses.size());
  }

  /**
   * Shows the loot of {@code boss} in the selected difficulty, under a header linking the boss's NPC page.
   * Runs on the EDT and loads the loot synchronously: the one documented exception to loading off the EDT (with
   * the instance's difficulties, read once on construction). A boss loot query is a single indexed lookup
   * returning a few dozen rows, and the page would otherwise need its own async plumbing and stale-load guard
   * for a load users never notice.
   */
  private void showLoot(BossSummary boss) {
    shown = boss;
    List<LootRow> loot = difficulties.size() > 1
        ? repos.instances().bossLoot(boss.id(), difficulty) : repos.instances().bossLoot(boss.id());
    String level = levelText(boss.level());
    bossHeader = new HeaderPanel(boss.displayName(), Labels.join(level == null ? null : "Level " + level));
    bossHeader.setAlignmentX(LEFT_ALIGNMENT);
    if (boss.npcId() != null) {
      bossHeader.addLink("NPC page", new EntityRef(EntityKind.NPC, boss.npcId()), nav);
    }
    lootTable = LootTables.grouped(loot, SlotGroups::of, SlotGroups.ORDER, null, nav);
    lootSection = new SourceSection("Loot", loot.size(), lootTable);
    lootPane.removeAll();
    lootPane.add(bossHeader);
    if (difficultyControl != null) {
      lootPane.add(difficultyControl);
    }
    lootPane.add(lootSection);
    lootPane.revalidate();
    lootPane.repaint();
  }

  private static JLabel goldHeading(String text) {
    JLabel heading = new JLabel(text) {
      @Override
      public Color getForeground() { // looked up per paint, so theme changes apply
        return Theme.current().accentGold();
      }
    };
    heading.setFont(heading.getFont().deriveFont(Font.BOLD));
    heading.setBorder(BorderFactory.createEmptyBorder(14, 0, 6, 0));
    return heading;
  }

  public HeaderPanel header() {
    return header;
  }

  /** The numbered encounters (AtlasLoot's main list). */
  public JList<BossSummary> encounters() {
    return encounters;
  }

  /** AtlasLoot's extra loot lists (trash, sets, ...); hidden when there are none. */
  public JList<BossSummary> extraLists() {
    return extraLists;
  }

  public JLabel extraListsHeading() {
    return extraListsHeading;
  }

  /** The difficulty switch; null when the instance's loot has one difficulty or none. */
  public JPanel difficultyControl() {
    return difficultyControl;
  }

  /** The header of the selected encounter or list; null before one is selected. */
  public HeaderPanel bossHeader() {
    return bossHeader;
  }

  /** The loot section of the selected encounter or list; null before one is selected. */
  public SourceSection lootSection() {
    return lootSection;
  }

  /** The grouped loot of the selected encounter or list; null before one is selected. */
  public LinkTable<GroupedRows.Row<LootRow>> lootTable() {
    return lootTable;
  }
}
