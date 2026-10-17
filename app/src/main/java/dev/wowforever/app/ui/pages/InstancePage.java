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
import dev.wowforever.app.model.QuestInvolvement;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.model.RateSource;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.Labels;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.Chip;
import dev.wowforever.app.ui.widgets.RailIcons;
import dev.wowforever.app.ui.widgets.RailTarget;
import dev.wowforever.app.ui.widgets.SourceSection;
import dev.wowforever.app.ui.widgets.WrappingText;
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
 * The dungeon or raid page: the header (a dungeons plate, {@code "Raid (40) · Level 60–63 · <zone> · NPCs in <zone>"}),
 * then the numbered encounters ({@code "1. Lucifron  Lv 63  Boss"}, the level left out when unknown) with AtlasLoot's
 * extra lists under "Other loot lists", and the loot of the selected one grouped by {@link SlotGroups} in slot order,
 * followed by the quests filed under the instance's zone (a "Quests" section, when there are any): the
 * {@link SectionOrder#INSTANCE} order. The
 * encounters sit left of the loot, or above it while the page is narrower than {@value #STACK_BELOW} px (the compact
 * window); the layout follows the width as the page is resized. A difficulty switch shows only when the instance's
 * loot has more than one difficulty. The first encounter is selected on construction.
 */
public final class InstancePage extends JPanel implements PageState {
  /** The page width, in logical pixels, below which the encounters stack above the loot in one column. */
  public static final int STACK_BELOW = 600;
  public static final String COVERAGE_LINE = "Bosses: %d named in client data · Loot: %d confirmed items · Client build %s";
  public static final String BOSSES_NOTE = "Boss names come from the game client. Loot may be reported by addons or observed by players; each entry shows its source. Abilities still need player observations.";
  public static final String NO_LOOT_NOTE = "No loot recorded yet for this dungeon. Drops appear here when a data update records them.";
  public static final String ENCOUNTER_CAPTION = "Encounter order in the game client";
  private final boolean client;
  private final boolean clientNoLoot;
  private final WrappingText coverage;
  private final WrappingText encounterCaption;
  private final WrappingText bossesNote;
  private WrappingText noLootNote;
  /** The title of the section of the quests filed under the instance's zone. */
  private static final String QUESTS = "Quests";
  /** The title of the selected encounter's loot section. */
  private static final String LOOT = "Loot";
  /** The share of a stacked page's height the encounters start with. */
  private static final double STACKED_LIST_SHARE = 0.4;

  private final BossLoot bossLoot;
  /**
   * Whether the encounters are dungeon or raid bosses, whose Vanilla rates read "Forever loot reworked": the
   * instance has a zone (AtlasLoot's "World Bosses" list has none; its bosses roam the open world).
   */
  private final boolean dungeonBosses;
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
  private final SourceSection questsSection;

  /** The page over the repository's boss loot, without quests. */
  public InstancePage(InstanceDetail d, Repositories repos, Navigator nav) {
    this(d, List.of(), repos, nav);
  }

  /**
   * The page over the repository's boss loot; {@code quests} are the quests filed under the instance's zone, loaded
   * off the EDT ({@link dev.wowforever.app.ui.detail.DetailPanels#load}).
   */
  public InstancePage(InstanceDetail d, List<QuestSummary> quests, Repositories repos, Navigator nav) {
    this(d, quests, BossLoot.of(repos), "unknown", nav);
  }

  /** The app supplies client loot and build metadata already loaded off the EDT. */
  public InstancePage(InstanceDetail d, List<QuestSummary> quests, Repositories repos, String clientBuild,
      BossLoot clientLoot, Navigator nav) {
    this(d, quests, clientLoot != null ? clientLoot : BossLoot.of(repos),
        clientBuild, nav);
  }

  /** The geographic path is loaded alongside the instance, including any known parent zones. */
  public InstancePage(InstanceDetail d, List<QuestSummary> quests, Repositories repos, String clientBuild,
      BossLoot clientLoot, List<dev.wowforever.app.model.GeographyStep> breadcrumbs, Navigator nav) {
    this(d,quests,repos,clientBuild,clientLoot,nav);
    if(!breadcrumbs.isEmpty()) {
      Component heading=((BorderLayout)getLayout()).getLayoutComponent(BorderLayout.NORTH);
      remove(heading);
      JPanel top=new JPanel(new BorderLayout());top.setOpaque(false);
      var trail=new dev.wowforever.app.ui.widgets.BreadcrumbTrail(breadcrumbs,nav);
      trail.setBorder(BorderFactory.createEmptyBorder(4,12,8,12));
      top.add(trail,BorderLayout.NORTH);top.add(heading,BorderLayout.CENTER);add(top,BorderLayout.NORTH);
    }
  }

  /** The page reading each boss's loot from {@code bossLoot}, without quests. */
  public InstancePage(InstanceDetail d, BossLoot bossLoot, Navigator nav) {
    this(d, List.of(), bossLoot, nav);
  }

  /** The page with {@code quests}, reading each boss's loot from {@code bossLoot}. */
  public InstancePage(InstanceDetail d, List<QuestSummary> quests, BossLoot bossLoot, Navigator nav) {
    this(d, quests, bossLoot, "unknown", nav);
  }

  public InstancePage(InstanceDetail d, List<QuestSummary> quests, BossLoot bossLoot, String clientBuild,
      Navigator nav) {
    super(new BorderLayout());
    client = InstanceSummary.CLIENT.equals(d.summary().source());
    clientNoLoot = client && d.difficulties().isEmpty();
    String build = clientBuild == null || clientBuild.isBlank() ? "unknown" : clientBuild;
    coverage = !client ? null : new WrappingText(clientNoLoot
        ? COVERAGE_LINE.formatted(d.bosses().stream().filter(b -> !b.extraList()).count(), 0, build)
        : "Bosses: " + d.bosses().stream().filter(b -> !b.extraList()).count()
            + " named in client data · Loot: "
            + (d.difficulties().contains(InstanceRepository.OBSERVED_DIFFICULTY)
                ? "in-game observations available; difficulty unknown" : "recorded (unverified)")
            + " · Client build " + build, "Dungeon data coverage");
    encounterCaption = client ? new WrappingText(ENCOUNTER_CAPTION, "Encounter order source") : null;
    bossesNote = client ? new WrappingText(BOSSES_NOTE, "Boss data source") : null;
    this.bossLoot = bossLoot;
    this.dungeonBosses = d.summary().zoneId() != null;
    this.nav = nav;
    InstanceSummary s = d.summary();
    header = header(s, nav);
    questsSection = questsSection(quests, s.zoneName(), nav);
    mainBosses = d.bosses().stream().filter(b -> !b.extraList()).toList();
    extraBosses = d.bosses().stream().filter(BossSummary::extraList).toList();
    encounters = bossList(mainBosses, i -> encounterLabel(i + 1, mainBosses.get(i)));
    if (client) installClientEncounters();
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
    if (client) {
      JPanel top = new JPanel(new BorderLayout());
      top.setOpaque(false);
      top.add(header, BorderLayout.NORTH);
      coverage.setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(12), UIScale.scale(8), UIScale.scale(12)));
      top.add(coverage, BorderLayout.CENTER);
      add(top, BorderLayout.NORTH);
    } else {
      add(header, BorderLayout.NORTH);
    }
    add(split, BorderLayout.CENTER);
    if (!d.bosses().isEmpty()) {
      selectBoss(0); // firstBoss(d): main encounters come first
    } else if (client) {
      showEmptyLoot(NO_LOOT_NOTE);
    } else {
      addSections(questsSection);
    }
  }

  /**
   * Adds the given sections (nulls left out) to the right-hand pane in {@link SectionOrder#INSTANCE} order: Loot, then
   * Quests. The left-hand pane holds the order's other two, Encounters over Other loot lists.
   */
  private void addSections(SourceSection... sections) {
    List<SourceSection> present = java.util.Arrays.stream(sections).filter(java.util.Objects::nonNull).toList();
    SectionOrder.sort(SectionOrder.INSTANCE, present, SourceSection::title).forEach(lootPane::add);
  }

  /**
   * The "Quests" section of {@code quests} (their zone column left out, as every one is in the instance's zone
   * {@code zoneName}), headed by the quests rail icon; null without quests.
   */
  private static SourceSection questsSection(List<QuestSummary> quests, String zoneName, Navigator nav) {
    if (quests.isEmpty()) {
      return null;
    }
    List<QuestInvolvement> rows = quests.stream()
        .map(q -> new QuestInvolvement(q, Set.of(QuestInvolvement.Role.RELATED), null)).toList();
    LinkTable<QuestInvolvement> table = LinkTable.inline(VisibleColumns.of(Columns.questInvolvements(), rows, zoneName, Set.of("Role", " ")), nav);
    table.setRows(rows);
    SourceSection section = new SourceSection(QUESTS, quests.size(), table);
    section.heading().setIcon(RailIcons.shared().small(RailTarget.QUESTS, RailIcons.SMALL));
    return section;
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
    boolean client = InstanceSummary.CLIENT.equals(s.source());
    HeaderPanel header = new HeaderPanel(Labels.instanceTitle(s.displayName(), s.key()),
        client ? Labels.contentType(s.contentType())
            : Labels.instanceSubtitle(s.contentType(), s.levelMin(), s.levelMax(), null));
    if (client) {
      header.addChip(new Chip("New in Forever", Chip.Style.PLAIN));
      String level = Labels.tunedLevel(s.tunedMinLevel());
      if (level != null) {
        Chip tuning = new Chip(level, Chip.Style.PLAIN);
        tuning.setToolTipText("Tuning level from the game client");
        header.addChip(tuning);
      }
    }
    header.setPlate(SourcesPage.plateIcon(RailTarget.INSTANCES));
    header.setKindWord(EntityKind.INSTANCE.label());
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

  private void installClientEncounters() {
    encounters.setCellRenderer((list, boss, index, selected, focus) -> {
      JPanel row = new JPanel(new BorderLayout(UIScale.scale(6), 0));
      row.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(4), 0, UIScale.scale(4), UIScale.scale(4)));
      row.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
      Chip badge = new Chip(Integer.toString(index + 1), Chip.Style.PLAIN);
      row.add(badge, BorderLayout.WEST);
      JPanel text = new JPanel();
      text.setOpaque(false);
      text.setLayout(new BoxLayout(text, BoxLayout.PAGE_AXIS));
      JLabel name = new JLabel(boss.displayName());
      name.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
      String note = clientNoLoot ? "Loot not recorded yet" : "Loot status unverified";
      JLabel status = new JLabel(note);
      status.setForeground(selected ? list.getSelectionForeground() : Theme.current().textMuted());
      status.setFont(status.getFont().deriveFont(status.getFont().getSize2D() * 0.9f));
      text.add(name);
      text.add(status);
      row.add(text, BorderLayout.CENTER);
      row.getAccessibleContext().setAccessibleName((index + 1) + ". " + boss.displayName() + ", " + note);
      return row;
    });
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
    if (client) {
      left.add(goldHeading("Encounters"));
      encounterCaption.setAlignmentX(LEFT_ALIGNMENT);
      bossesNote.setAlignmentX(LEFT_ALIGNMENT);
      left.add(encounterCaption);
      left.add(bossesNote);
    }
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
    Map<RateSource.Key, List<RateSource>> sources = boss.npcId() == null || loot.isEmpty() ? Map.of()
        : bossLoot.sources(boss.npcId(), RateSource.ratedItems(loot));
    String level = levelText(boss.level());
    bossHeader = new HeaderPanel(boss.displayName(), Labels.join(level == null ? null : "Level " + level));
    bossHeader.setAlignmentX(LEFT_ALIGNMENT);
    if (boss.npcId() != null) {
      bossHeader.addLink("NPC page", new EntityRef(EntityKind.NPC, boss.npcId()), nav);
    }
    if (client && loot.isEmpty()) {
      showEmptyLoot(clientNoLoot ? NO_LOOT_NOTE : "No loot recorded yet for this encounter.");
      return;
    }
    noLootNote = null;
    // AtlasLoot's entry order ("#") orders the rows but is no equipment slot: the column is left out
    lootTable = LootTables.grouped(loot, new LootTables.Grouping(SlotGroups::of, SlotGroups.ORDER, null, Set.of(),
        Set.of(Columns.LOOT_ORDER)),
        boss.npcId() == null ? LootTables.NO_SOURCES : LootTables.sourcesOf(boss.npcId(), sources),
        dungeonBosses, nav);
    lootSection = new SourceSection(LOOT, loot.size(), lootTable);
    if (InstanceRepository.OBSERVED_DIFFICULTY.equals(difficulty)) {
      lootSection.setNote("Observed in game; difficulty unknown.");
    } else if (loot.stream().anyMatch(l -> l.origin()!=null && l.origin().endsWith(" (reported)"))) {
      String presenceSources=loot.stream().map(LootRow::origin).filter(java.util.Objects::nonNull)
          .filter(o -> o.endsWith(" (reported)")).map(o -> o.substring(0,o.length()-11)).distinct()
          .collect(java.util.stream.Collectors.joining("; "));
      lootSection.setNote("Addon-reported loot: "+presenceSources+". Not verified in game.");
    } else if (client && !loot.stream().allMatch(LootRow::observedInGame)) {
      lootSection.setNote(loot.stream().anyMatch(LootRow::observedInGame)
          ? "Some drops were observed in game; other recorded loot is unverified."
          : "Recorded loot is unverified.");
    }
    lootSection.setRowLimited(false); // the page's main content, rebuilt for every encounter: never cut
    lootPane.removeAll();
    lootPane.add(bossHeader);
    if (difficultyControl != null) {
      lootPane.add(difficultyControl);
    }
    addSections(lootSection, questsSection);
    lootPane.revalidate();
    lootPane.repaint();
  }

  private void showEmptyLoot(String message) {
    lootTable = null;
    LinkTable<String> hidden = LinkTable.inline(List.of(
        new Columns.Column<String>("Name", String.class, value -> value, null)), nav);
    hidden.setRows(List.of());
    hidden.setVisible(false);
    lootSection = new SourceSection(LOOT, 0, hidden);
    lootSection.heading().label().setText(LOOT);
    noLootNote = new WrappingText(message, "Loot coverage");
    lootSection.addAboveTable(noLootNote);
    lootPane.removeAll();
    if (bossHeader != null) lootPane.add(bossHeader);
    if (difficultyControl != null) lootPane.add(difficultyControl);
    addSections(lootSection, questsSection);
    lootPane.revalidate();
    lootPane.repaint();
  }

  public WrappingText coverage() { return coverage; }
  public WrappingText encounterCaption() { return encounterCaption; }
  public WrappingText bossesNote() { return bossesNote; }
  public WrappingText noLootNote() { return noLootNote; }

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

  /** The quests filed under the instance's zone, below the loot; null without quests. */
  public SourceSection questsSection() {
    return questsSection;
  }

  /** The grouped loot of the selected encounter or list; null before one is selected. */
  public LinkTable<GroupedRows.Row<LootRow>> lootTable() {
    return lootTable;
  }
}
