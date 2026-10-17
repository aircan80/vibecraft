package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.ItemClassNode;
import dev.wowforever.app.model.ItemFilter;
import dev.wowforever.app.model.ListFilter;
import dev.wowforever.app.model.NpcFilter;
import dev.wowforever.app.model.QuestFilter;
import dev.wowforever.app.model.ZoneSummary;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.widgets.MasterList;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.DefaultFormatterFactory;

/**
 * The filter bar of a list page: combos for the item class, subclass and quality (items), the zone (NPCs, quests)
 * and the rank (NPCs), each starting with an "All …" (quality: "Any") choice, and two level spinners where 0 reads
 * "Any" (no bound). Every change the user makes reports the new filter once; showing the filter the bar was built
 * with reports nothing. Combo changes report at once; level changes once the spinners rest (an arrow held down is
 * one reload). A muted hint shows while the minimum level is above the maximum.
 */
public final class FilterBar extends JPanel {
  /** How long the level spinners must rest before a level change is reported. */
  public static final int LEVEL_DELAY_MILLIS = 250;
  /** The highest level the spinners go to. */
  static final int MAX_LEVEL = 999;
  /** What a level spinner shows at 0, its "no bound" value. */
  static final String ANY = "Any";
  /** Raw NPC ranks in the order the rank combo offers them: Normal, Elite, Rare, Rare Elite, Boss. */
  private static final int[] RANKS = {0, 1, 4, 2, 3};
  /** The item qualities the quality combo offers, by quality id (0 poor … 6 artifact). */
  private static final List<String> QUALITIES =
      List.of("Poor", "Common", "Uncommon", "Rare", "Epic", "Legendary", "Artifact");
  private static final int GAP = 6;

  /** One entry of a filter combo: its text and the filter value it stands for (null for "All …"). */
  public record Choice<V>(String label, V value) {
    @Override
    public String toString() {
      return label;
    }
  }

  private final Consumer<ListFilter> onChange;
  private final MasterList.Delay levelDelay;
  private final JLabel rangeHint = PageContent.mutedLabel("Minimum is above maximum");
  private final List<ItemClassNode> classTree;
  private ListFilter filter;
  /** True while the bar sets its own widgets, whose events are then not user changes. */
  private boolean updating = true;
  private JComboBox<Choice<Integer>> classes;
  private JComboBox<Choice<Integer>> subclasses;
  private JComboBox<Choice<Integer>> qualities;
  private JComboBox<Choice<Integer>> stats;
  private JComboBox<Choice<Long>> zones;
  private JComboBox<Choice<Integer>> ranks;
  private final JSpinner minLevel;
  private final JSpinner maxLevel;

  /**
   * A bar showing {@code initial}, an item, NPC or quest filter; {@code onChange} hears every user change, level
   * changes after {@code levelDelay}.
   */
  FilterBar(ListFilter initial, ListOptions options, Consumer<ListFilter> onChange, MasterList.Delay levelDelay) {
    super(new FlowLayout(FlowLayout.LEADING, GAP, GAP));
    this.filter = Objects.requireNonNull(initial, "initial");
    this.onChange = Objects.requireNonNull(onChange, "onChange");
    this.levelDelay = Objects.requireNonNull(levelDelay, "levelDelay");
    this.classTree = options.classes();
    setOpaque(false);
    setAlignmentX(LEFT_ALIGNMENT);
    setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
    String levelLabel = "Level";
    switch (initial) {
      case ItemFilter f -> {
        classes = combo("All classes", classTree.stream().map(c -> new Choice<>(c.name(), c.classId())).toList(),
            f.classId());
        subclasses = new JComboBox<>();
        fillSubclasses(f.classId(), f.subclassId());
        classes.addActionListener(e -> {
          if (!updating) {
            updating = true;
            fillSubclasses(value(classes), null);
            updating = false;
            changed();
          }
        });
        subclasses.addActionListener(e -> userChange());
        qualities = combo("Any", QUALITIES.stream().map(q -> new Choice<>(q, QUALITIES.indexOf(q))).toList(),
            f.quality());
        qualities.addActionListener(e -> userChange());
        add("Class", classes);
        add("Subclass", subclasses);
        add("Quality", qualities);
        stats = combo("Any", new java.util.TreeMap<>(dev.wowforever.app.model.StatGlossary.NAMES).entrySet().stream().map(e -> new Choice<>(e.getValue(),e.getKey())).toList(), f.statId());
        stats.addActionListener(e -> userChange());
        add("Has stat", stats);
        levelLabel = "Item level";
      }
      case NpcFilter f -> {
        zones = zoneCombo(options.zones(), f.zoneId());
        ranks = combo("All ranks", Arrays.stream(RANKS)
            .mapToObj(r -> new Choice<>(Columns.rankText(r), r)).toList(), f.rank());
        ranks.addActionListener(e -> userChange());
        add("Zone", zones);
        add("Rank", ranks);
      }
      case QuestFilter f -> {
        zones = zoneCombo(options.zones(), f.zoneId());
        add("Zone", zones);
      }
      default -> throw new IllegalArgumentException("No filter bar for " + initial);
    }
    minLevel = levelSpinner(min(initial));
    maxLevel = levelSpinner(max(initial));
    add(levelLabel, minLevel);
    add("to", maxLevel);
    // named by what they bound, not by their labels ("to" alone says little)
    String bound = levelLabel.toLowerCase(Locale.ROOT);
    named(minLevel, "Minimum " + bound);
    named(maxLevel, "Maximum " + bound);
    add(rangeHint);
    showRangeHint();
    updating = false;
  }

  /** The filter the bar shows. */
  public ListFilter filter() {
    return filter;
  }

  /** The item class combo; null unless this is an item bar. */
  public JComboBox<Choice<Integer>> classes() {
    return classes;
  }

  /** The item subclass combo, disabled while every class is shown; null unless this is an item bar. */
  public JComboBox<Choice<Integer>> subclasses() {
    return subclasses;
  }

  /** The item quality combo ("Any", then Poor … Artifact); null unless this is an item bar. */
  public JComboBox<Choice<Integer>> qualities() {
    return qualities;
  }

  /** The zone combo; null on an item bar. */
  public JComboBox<Choice<Long>> zones() {
    return zones;
  }

  /** The NPC rank combo; null unless this is an NPC bar. */
  public JComboBox<Choice<Integer>> ranks() {
    return ranks;
  }

  /** The lower level bound; 0 is none. */
  public JSpinner minLevel() {
    return minLevel;
  }

  /** The upper level bound; 0 is none. */
  public JSpinner maxLevel() {
    return maxLevel;
  }

  /** The hint shown while the minimum level is above the maximum. */
  public JLabel rangeHint() {
    return rangeHint;
  }

  /**
   * One row when it fits the width of the page; otherwise as many rows as the flow layout wraps the widgets into
   * at that width, so a narrow page shows them all.
   */
  @Override
  public Dimension getPreferredSize() {
    Dimension oneRow = super.getPreferredSize();
    int width = getParent() == null ? 0 : getParent().getWidth();
    if (width <= 0 || oneRow.width <= width) {
      return oneRow;
    }
    FlowLayout flow = (FlowLayout) getLayout();
    Insets insets = getInsets();
    int room = width - insets.left - insets.right - 2 * flow.getHgap();
    int rows = 0;
    int rowWidth = 0;
    int rowHeight = 0;
    int height = 0;
    for (Component c : getComponents()) {
      if (!c.isVisible()) {
        continue;
      }
      Dimension d = c.getPreferredSize();
      if (rowWidth > 0 && rowWidth + flow.getHgap() + d.width > room) {
        height += rowHeight;
        rows++;
        rowWidth = 0;
        rowHeight = 0;
      }
      rowWidth += (rowWidth > 0 ? flow.getHgap() : 0) + d.width;
      rowHeight = Math.max(rowHeight, d.height);
    }
    height += rowHeight;
    rows++;
    return new Dimension(width, height + (rows + 1) * flow.getVgap() + insets.top + insets.bottom);
  }

  @Override
  public Dimension getMaximumSize() {
    return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
  }

  private void userChange() {
    if (!updating) {
      changed();
    }
  }

  /** A level spinner moved: the hint follows at once, the report once the spinners rest. */
  private void levelChange() {
    if (!updating) {
      showRangeHint();
      levelDelay.cancel();
      levelDelay.schedule(this::changed);
    }
  }

  private void showRangeHint() {
    Integer min = level(minLevel);
    Integer max = level(maxLevel);
    boolean show = min != null && max != null && min > max;
    if (rangeHint.isVisible() != show) {
      rangeHint.setVisible(show);
      revalidate();
      repaint();
    }
  }

  /**
   * Reads the widgets and reports the filter they show, when it differs from the one shown before. A pending level
   * report is dropped: this one carries the levels too.
   */
  private void changed() {
    levelDelay.cancel();
    Integer min = level(minLevel);
    Integer max = level(maxLevel);
    ListFilter next = switch (filter) {
      case ItemFilter f -> {
        Integer classId = value(classes);
        yield f.withClass(classId, classId == null ? null : value(subclasses)).withQuality(value(qualities))
            .withLevels(min, max).withStat(value(stats));
      }
      case NpcFilter f -> f.withZone(value(zones)).withRank(value(ranks)).withLevels(min, max);
      case QuestFilter f -> f.withZone(value(zones)).withLevels(min, max);
      default -> filter;
    };
    if (!next.equals(filter)) {
      filter = next;
      onChange.accept(next);
    }
  }

  /** Fills the subclass combo with the subclasses of {@code classId}, selecting {@code selected}. */
  private void fillSubclasses(Integer classId, Integer selected) {
    subclasses.removeAllItems();
    subclasses.addItem(new Choice<>("All subclasses", null));
    classTree.stream().filter(c -> Objects.equals(c.classId(), classId)).findFirst()
        .ifPresent(c -> c.children().forEach(s -> subclasses.addItem(new Choice<>(s.name(), s.subclassId()))));
    select(subclasses, selected);
    subclasses.setEnabled(classId != null);
  }

  /** Adds {@code field} after its label; the two wrap onto the next row together. */
  private void add(String label, JComponent field) {
    JLabel l = new JLabel(label);
    l.setLabelFor(field);
    JPanel pair = new JPanel(new FlowLayout(FlowLayout.LEADING, GAP / 2, 0));
    pair.setOpaque(false);
    pair.add(l);
    pair.add(field);
    add(pair);
  }

  private <V> JComboBox<Choice<V>> combo(String all, List<Choice<V>> choices, V selected) {
    JComboBox<Choice<V>> combo = new JComboBox<>();
    combo.addItem(new Choice<>(all, null));
    choices.forEach(combo::addItem);
    select(combo, selected);
    return combo;
  }

  private JComboBox<Choice<Long>> zoneCombo(List<ZoneSummary> zones, Long selected) {
    JComboBox<Choice<Long>> combo = combo("All zones",
        zones.stream().map(z -> new Choice<>(z.displayName(), z.id())).toList(), selected);
    combo.setPrototypeDisplayValue(new Choice<>("Stranglethorn Vale (Eastern)", null));
    combo.setMaximumRowCount(20);
    combo.addActionListener(e -> userChange());
    return combo;
  }

  /** Selects the choice for {@code value}; the "All …" choice when there is none. */
  private static <V> void select(JComboBox<Choice<V>> combo, V value) {
    for (int i = 0; i < combo.getItemCount(); i++) {
      if (Objects.equals(combo.getItemAt(i).value(), value)) {
        combo.setSelectedIndex(i);
        return;
      }
    }
    combo.setSelectedIndex(0);
  }

  private static <V> V value(JComboBox<Choice<V>> combo) {
    int i = combo.getSelectedIndex();
    return i < 0 ? null : combo.getItemAt(i).value();
  }

  private JSpinner levelSpinner(Integer value) {
    int start = value == null ? 0 : Math.max(0, Math.min(MAX_LEVEL, value));
    JSpinner spinner = new JSpinner(new SpinnerNumberModel(start, 0, MAX_LEVEL, 1));
    JFormattedTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
    field.setFormatterFactory(new DefaultFormatterFactory(new LevelFormatter()));
    field.setColumns(3);
    spinner.addChangeListener(e -> levelChange());
    return spinner;
  }

  /** Names {@code spinner} and its text field (which takes the focus) {@code name}. */
  private static void named(JSpinner spinner, String name) {
    spinner.getAccessibleContext().setAccessibleName(name);
    ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().getAccessibleContext().setAccessibleName(name);
  }

  /** The bound a level spinner shows; null for 0 ("Any"). */
  private static Integer level(JSpinner spinner) {
    int v = ((Number) spinner.getValue()).intValue();
    return v == 0 ? null : v;
  }

  private static Integer min(ListFilter f) {
    return switch (f) {
      case ItemFilter i -> i.minLevel();
      case NpcFilter n -> n.minLevel();
      case QuestFilter q -> q.minLevel();
      default -> null;
    };
  }

  private static Integer max(ListFilter f) {
    return switch (f) {
      case ItemFilter i -> i.maxLevel();
      case NpcFilter n -> n.maxLevel();
      case QuestFilter q -> q.maxLevel();
      default -> null;
    };
  }

  /** Shows 0 as {@link #ANY}; reads a level, or "Any" or nothing as 0. */
  private static final class LevelFormatter extends JFormattedTextField.AbstractFormatter {
    @Override
    public Object stringToValue(String text) throws ParseException {
      String t = text == null ? "" : text.strip();
      if (t.isEmpty() || t.equalsIgnoreCase(ANY)) {
        return 0;
      }
      try {
        int v = Integer.parseInt(t);
        if (v >= 0 && v <= MAX_LEVEL) {
          return v;
        }
      } catch (NumberFormatException e) {
        // not a level: refused below
      }
      throw new ParseException("Not a level: " + text, 0);
    }

    @Override
    public String valueToString(Object value) {
      return value == null || ((Number) value).intValue() == 0 ? ANY : value.toString();
    }
  }
}
