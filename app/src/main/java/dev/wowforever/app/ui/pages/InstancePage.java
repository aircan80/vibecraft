package dev.wowforever.app.ui.pages;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.db.InstanceRepository;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.BossSummary;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceDetail;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.NpcFilter;
import dev.wowforever.app.model.RateSource;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.Columns;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * The dungeon or raid page: the header ({@code "Raid (40) · Level 60–63 · <zone> · NPCs in <zone>"}), then the
 * numbered encounters ({@code "1. Lucifron  Lv 63  Boss"}, the level left out when unknown) with AtlasLoot's extra
 * lists under "Other loot lists", and the loot of the selected one grouped by {@link SlotGroups} in slot order. The
 * encounters sit left of the loot, or above it while the page is narrower than {@value #STACK_BELOW} px (the compact
 * window); the layout follows the width as the page is resized. A difficulty switch shows only when the instance's
 * loot has more than one difficulty. The first encounter is selected on construction.
 */
public final class InstancePage extends JPanel implements PageState {
  /** The page width, in logical pixels, below which the encounters stack above the loot in one column. */
  public static final int STACK_BELOW = 600;
  /** The share of a stacked page's height the encounters start with. */
  private static final double STACKED_LIST_SHARE = 0.4;

  private final BossLoot bossLoot;
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
  private final JSplitPane split;
  private String difficulty;
  private BossSummary shown;
  private HeaderPanel bossHeader;
  private SourceSection lootSection;
  private LinkTable<GroupedRows.Row<LootRow>> lootTable;

  /** The page over the repository's boss loot. */
  public InstancePage(InstanceDetail d, Repositories repos, Navigator nav) {
    this(d, BossLoot.of(repos), nav);
  }

  /** The page reading each boss's loot from {@code bossLoot}. */
  public InstancePage(InstanceDetail d, BossLoot bossLoot, Navigator nav) {
    super(new BorderLayout());
    this.bossLoot = bossLoot;
    this.nav = nav;
    InstanceSummary s = d.summary();
    header = header(s, nav);
    mainBosses = d.bosses().stream().filter(b -> !b.extraList()).toList();
    extraBosses = d.bosses().stream().filter(BossSummary::extraList).toList();
    encounters = bossList(mainBosses, i -> encounterLabel(i + 1, mainBosses.get(i)));
    encounters.getAccessibleContext().setAccessibleName("Encounters");
    extraLists = bossList(extraBosses, i -> extraBosses.get(i).displayName());
    extraLists.getAccessibleContext().setAccessibleName(extraListsHeading.getText());
    extraListsHeading.setVisible(!extraBosses.isEmpty());
    extraLists.setVisible(!extraBosses.isEmpty());
    linkSelections(encounters, extraLists);
    linkSelections(extraLists, encounters);
    difficulties = d.difficulties();
    difficulty = difficulties.isEmpty() ? null : difficulties.get(0);
    difficultyControl = difficulties.size() > 1 ? difficultySwitch() : null;
    JScrollPane right = new JScrollPane(lootPane);
    right.setBorder(BorderFactory.createEmptyBorder());
    right.getVerticalScrollBar().setUnitIncrement(16);
    split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, encounterPane(), right);
    split.setResizeWeight(0.3);
    split.setBorder(null);
    add(header, BorderLayout.NORTH);
    add(split, BorderLayout.CENTER);
    if (!d.bosses().isEmpty()) {
      selectBoss(0); // firstBoss(d): main encounters come first
    }
  }

  /**
   * The encounter the page selects on opening: the first main encounter, else the first extra list; empty without
   * bosses. {@link dev.wowforever.app.ui.detail.DetailPanels#load} pre-loads the icons of its loot.
   */
  public static Optional<BossSummary> firstBoss(InstanceDetail d) {
    return d.bosses().stream().filter(b -> !b.extraList()).findFirst().or(() -> d.bosses().stream().findFirst());
  }

  /**
   * Lays the page out, first turning the split to stack the encounters above the loot when the page has become
   * narrower than {@value #STACK_BELOW} px, or back to side by side when it has grown wider. The split takes its new
   * bounds before it turns, so its divider starts from the new size: the encounters get
   * {@value #STACKED_LIST_SHARE} of a stacked page's height, or their preferred width side by side.
   */
  @Override
  public void doLayout() {
    super.doLayout();
    boolean stack = getWidth() > 0 && getWidth() < UIScale.scale(STACK_BELOW);
    if (stack != isStacked()) {
      split.setOrientation(stack ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT);
      if (stack) {
        split.setDividerLocation(STACKED_LIST_SHARE);
      } else {
        split.resetToPreferredSizes();
      }
    }
  }

  /** Whether the encounters sit above the loot (a narrow page) rather than left of it. */
  public boolean isStacked() {
    return split.getOrientation() == JSplitPane.VERTICAL_SPLIT;
  }

  JSplitPane split() {
    return split;
  }

  /** The instance name over its type, levels, a zone link and an "NPCs in" link to the NPC list of that zone. */
  private static HeaderPanel header(InstanceSummary s, Navigator nav) {
    HeaderPanel header = new HeaderPanel(Labels.instanceTitle(s.displayName(), s.key()),
        Labels.instanceSubtitle(s.contentType(), s.levelMin(), s.levelMax(), null));
    if (s.zoneId() != null) {
      EntityRef zone = new EntityRef(EntityKind.ZONE, s.zoneId());
      String name = zone.displayName(s.zoneName());
      header.addLink(name, zone, nav);
      header.addLink("NPCs in " + name, new ViewTarget.List(EntityKind.NPC, NpcFilter.ALL.withZone(s.zoneId())),
          nav);
    }
    return header;
  }

  /** {@code "1. Lucifron  Lv 63  Boss"}: AtlasLoot's special type (Rare, Elite) in place of Boss when it has one. */
  static String encounterLabel(int number, BossSummary b) {
    String level = levelText(b.level());
    String kind = b.specialType() == null || b.specialType().isBlank() ? "Boss" : capitalised(b.specialType());
    return number + ". " + b.displayName() + (level == null ? "" : "  Lv " + level) + "  " + kind;
  }

  /** The level; null when unknown, as AtlasLoot's skull level is (the repository fills it from the NPC if it can). */
  private static String levelText(Integer level) {
    if (level == null || level <= 0) {
      return null;
    }
    return level >= InstanceRepository.SKULL_LEVEL ? null : String.valueOf(level);
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

  /** The selected encounter (by {@link #selectBoss} index, -1 for none) and difficulty. */
  private record Selection(int boss, String difficulty) {}

  @Override
  public Object saveState() {
    int index = shown == null ? -1 : mainBosses.contains(shown) ? mainBosses.indexOf(shown)
        : mainBosses.size() + extraBosses.indexOf(shown);
    return new Selection(index, difficulty);
  }

  /** Selects the saved encounter in the saved difficulty (when this instance has them). */
  @Override
  public void restoreState(Object state) {
    if (!(state instanceof Selection s)) {
      return;
    }
    if (s.difficulty() != null && difficulties.contains(s.difficulty()) && !s.difficulty().equals(difficulty)) {
      difficulty = s.difficulty();
      if (difficultyControl != null) {
        for (Component c : difficultyControl.getComponents()) {
          if (c instanceof JToggleButton b) {
            b.setSelected(b.getText().equals(capitalised(difficulty)));
          }
        }
      }
      if (shown != null) {
        showLoot(shown);
      }
    }
    if (s.boss() >= 0) {
      selectBoss(s.boss());
    }
  }

  private BossSummary bossAt(int index) {
    return index < mainBosses.size() ? mainBosses.get(index) : extraBosses.get(index - mainBosses.size());
  }

  /**
   * Shows the loot of {@code boss} in the selected difficulty (none when the instance has no difficulty), under a
   * header linking the boss's NPC page. Runs on the EDT and loads the loot synchronously, with the drop rate sources
   * its rate tooltips list: the one documented exception to loading off the EDT. A boss loot query is a single
   * indexed lookup returning a few dozen rows, and its sources one more (a primary-key prefix lookup on the boss's
   * NPC, only when some rate has sources); the page would otherwise need its own async plumbing and stale-load
   * guard for a load users never notice.
   */
  private void showLoot(BossSummary boss) {
    shown = boss;
    List<LootRow> loot = difficulty == null ? List.of() : bossLoot.lookup(boss.id(), difficulty);
    Map<RateSource.Key, List<RateSource>> sources = boss.npcId() == null ? Map.of()
        : bossLoot.sources(boss.npcId(), RateSource.ratedItems(loot));
    String level = levelText(boss.level());
    bossHeader = new HeaderPanel(boss.displayName(), Labels.join(level == null ? null : "Level " + level));
    bossHeader.setAlignmentX(LEFT_ALIGNMENT);
    if (boss.npcId() != null) {
      bossHeader.addLink("NPC page", new EntityRef(EntityKind.NPC, boss.npcId()), nav);
    }
    // AtlasLoot's entry order ("#") orders the rows but is no equipment slot: the column is left out
    lootTable = LootTables.grouped(loot, new LootTables.Grouping(SlotGroups::of, SlotGroups.ORDER, null, Set.of(),
        Set.of(Columns.LOOT_ORDER)),
        boss.npcId() == null ? LootTables.NO_SOURCES : LootTables.sourcesOf(boss.npcId(), sources), nav);
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
        return Theme.current().goldText();
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
