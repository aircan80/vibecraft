package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.RateSource;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.Columns.Column;
import dev.wowforever.app.ui.Labels;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.DividerRows;
import dev.wowforever.app.ui.widgets.OriginChip;
import dev.wowforever.app.ui.widgets.RateBarRenderer;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntConsumer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;

/**
 * The item tables of the NPC, object, quest and instance pages ({@link Columns#lootRated()}): columns that say
 * nothing are left out ({@link VisibleColumns}), rates get a bar (with a tooltip of the rate's sources when they are
 * given), origins a chip and class restrictions muted text. In a grouped table the Recipes group shows each
 * recipe's required skill ({@code "Leatherworking 65"}) in the Item level column, its heading labelling that column
 * "Skill"; a collapsible group opens and closes with a click on its heading, or Enter with the heading selected.
 */
final class LootTables {
  /** Highest rate first, unknown rates last, then by name. */
  static final Comparator<LootRow> BY_RATE = Comparator
      .comparing(LootRow::dropPct, Comparator.nullsLast(Comparator.reverseOrder()))
      .thenComparing(LootRow::displayName, String.CASE_INSENSITIVE_ORDER);
  /** The group whose rows show their required skill (on the NPC and the instance page alike). */
  static final String RECIPES = ItemCategories.RECIPES;
  /** What the Recipes heading calls the Item level column. */
  static final String SKILL = "Skill";
  /** The title of a grouped table's column of what a row is ({@link Grouping#typeOf}). */
  static final String TYPE = "Type";
  private static final String ITEM_LEVEL = "Item level";

  /** The rate sources of any row when none are known. */
  static final Function<Object, List<RateSource>> NO_SOURCES = row -> null;

  /**
   * How {@link #grouped} groups loot: by {@code groupOf}, the groups in {@code order}, each sorted by {@code within}
   * (null keeps the given order); the groups in {@code collapsed} start collapsed and can be opened; the columns
   * titled in {@code hiddenColumns} are left out. {@code typeOf} (null for none) fills a {@value #TYPE} column after
   * the item, for rows whose group does not say what they are (blank cells elsewhere; a column blank on every row is
   * left out).
   */
  record Grouping(Function<LootRow, String> groupOf, List<String> order, Comparator<LootRow> within,
      Set<String> collapsed, Set<String> hiddenColumns, Function<LootRow, String> typeOf) {
    Grouping {
      Objects.requireNonNull(groupOf, "groupOf");
      Objects.requireNonNull(order, "order");
      collapsed = Set.copyOf(collapsed);
      hiddenColumns = Set.copyOf(hiddenColumns);
    }

    /** Groups without a Type column. */
    Grouping(Function<LootRow, String> groupOf, List<String> order, Comparator<LootRow> within,
        Set<String> collapsed, Set<String> hiddenColumns) {
      this(groupOf, order, within, collapsed, hiddenColumns, null);
    }

    /** Groups none of which collapse, every column kept. */
    Grouping(Function<LootRow, String> groupOf, List<String> order, Comparator<LootRow> within) {
      this(groupOf, order, within, Set.of(), Set.of());
    }
  }

  private LootTables() {}

  /** A sortable table of {@code loot}. */
  static LinkTable<LootRow> plain(List<LootRow> loot, Navigator nav) {
    LinkTable<LootRow> table = LinkTable.inline(VisibleColumns.of(Columns.lootRated(), loot, null), nav);
    table.setRows(loot);
    decorate(table, NO_SOURCES);
    return table;
  }

  /**
   * {@code loot} grouped by {@code groupOf} under heading rows, the groups in {@code order}, each sorted by
   * {@code within} (null keeps the given order). The grouping fixes the order, so the table is not sortable.
   */
  static LinkTable<GroupedRows.Row<LootRow>> grouped(List<LootRow> loot, Function<LootRow, String> groupOf,
      List<String> order, Comparator<LootRow> within, Navigator nav) {
    return grouped(loot, new Grouping(groupOf, order, within), NO_SOURCES, nav);
  }

  /**
   * {@link #grouped(List, Function, List, Comparator, Navigator)} whose rate cells' tooltips list the sources
   * {@code sources} gives for a row (looked up while rendering: only sources loaded beforehand).
   */
  static LinkTable<GroupedRows.Row<LootRow>> grouped(List<LootRow> loot, Function<LootRow, String> groupOf,
      List<String> order, Comparator<LootRow> within, Function<? super LootRow, List<RateSource>> sources,
      Navigator nav) {
    return grouped(loot, new Grouping(groupOf, order, within), sources, nav);
  }

  /** {@code loot} grouped as {@code grouping} says, its rate tooltips listing what {@code sources} gives. */
  static LinkTable<GroupedRows.Row<LootRow>> grouped(List<LootRow> loot, Grouping grouping,
      Function<? super LootRow, List<RateSource>> sources, Navigator nav) {
    List<Column<LootRow>> all = new ArrayList<>(Columns.lootRated());
    if (grouping.typeOf() != null) {
      Function<LootRow, String> typeOf = grouping.typeOf();
      all.add(1, new Column<>(TYPE, String.class, typeOf::apply, null));
    }
    List<Column<LootRow>> cols = VisibleColumns.of(all, loot, null).stream()
        .filter(c -> !grouping.hiddenColumns().contains(c.title())).toList();
    boolean skills = loot.stream().anyMatch(l -> RECIPES.equals(grouping.groupOf().apply(l))
        && Labels.skill(l.skillLine()) != null);
    LinkTable<GroupedRows.Row<LootRow>> table =
        LinkTable.inline(GroupedRows.columns(cols, skills ? recipeSkills() : GroupedRows.GroupCells.none()), nav);
    Set<String> collapsed = new HashSet<>(grouping.collapsed());
    Runnable fill = () -> table.setRows(GroupedRows.of(loot, grouping.groupOf(), grouping.order(),
        grouping.within(), grouping.collapsed(), collapsed));
    fill.run();
    decorate(table, row -> {
      LootRow entry = GroupedRows.Entry.valueOf(row, LootRow.class);
      return entry == null ? null : sources.apply(entry);
    });
    DividerRows.install(table, GroupedRows::isHeading, GroupedRows::isCollapsibleHeading);
    if (!grouping.collapsed().isEmpty()) {
      installFolding(table, collapsed, fill);
    }
    return table;
  }

  /** Opens or closes a collapsible group on a click on its heading, or Enter with the heading selected. */
  private static void installFolding(LinkTable<GroupedRows.Row<LootRow>> table, Set<String> collapsed,
      Runnable fill) {
    JTable t = table.table();
    IntConsumer toggle = viewRow -> {
      if (viewRow < 0 || viewRow >= t.getRowCount()) {
        return;
      }
      if (table.model().rowAt(t.convertRowIndexToModel(viewRow)) instanceof GroupedRows.Heading<LootRow> h
          && h.collapsible()) {
        if (!collapsed.remove(h.group())) {
          collapsed.add(h.group());
        }
        fill.run();
        t.changeSelection(viewRow, 0, false, false); // the heading stays selected
      }
    };
    t.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
          toggle.accept(t.rowAtPoint(e.getPoint()));
        }
      }
    });
    Object key = t.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke("ENTER"));
    Action open = key == null ? null : t.getActionMap().get(key);
    if (open != null) {
      t.getActionMap().put(key, new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          int row = t.getSelectedRow();
          if (row >= 0 && GroupedRows.isCollapsibleHeading(table.model().rowAt(t.convertRowIndexToModel(row)))) {
            toggle.accept(row);
          } else {
            open.actionPerformed(e);
          }
        }
      });
    }
  }

  /** The Recipes group's own cells: each recipe's skill in the Item level column, which its heading calls Skill. */
  private static GroupedRows.GroupCells<LootRow> recipeSkills() {
    return new GroupedRows.GroupCells<>() {
      @Override
      public Function<LootRow, Object> entryValue(String group, String title) {
        if (!RECIPES.equals(group) || !ITEM_LEVEL.equals(title)) {
          return null;
        }
        return l -> {
          String skill = Labels.skill(l.skillLine());
          return skill != null ? skill : l.itemLevel();
        };
      }

      @Override
      public String headingLabel(String group, String title) {
        return RECIPES.equals(group) && ITEM_LEVEL.equals(title) ? SKILL : null;
      }
    };
  }

  /**
   * The loaded rate sources of a loot row of NPC {@code npcId}'s loot, from {@code sources} (as
   * {@link dev.wowforever.app.db.ItemRepository#sourcesOfNpc} groups them); null when the row has none
   * ({@code sourceCount} 0) or none were loaded.
   */
  static Function<LootRow, List<RateSource>> sourcesOf(long npcId, Map<RateSource.Key, List<RateSource>> sources) {
    return row -> row.sourceCount() > 0 ? sources.get(new RateSource.Key(npcId, row.item().id())) : null;
  }

  /**
   * Rate bars (tooltips from {@code sources}), origin chips and muted class names on the Rate, Origin and Classes
   * columns that are shown.
   */
  private static void decorate(LinkTable<?> table, Function<Object, List<RateSource>> sources) {
    TableColumnModel columns = table.table().getColumnModel();
    for (int i = 0; i < columns.getColumnCount(); i++) {
      switch (String.valueOf(columns.getColumn(i).getHeaderValue())) {
        case Columns.RATE -> RateBarRenderer.install(columns.getColumn(i), sources);
        case VisibleColumns.ORIGIN -> OriginChip.install(columns.getColumn(i));
        case Columns.CLASSES, TYPE -> columns.getColumn(i).setCellRenderer(new MutedRenderer());
        default -> { }
      }
    }
  }

  /** Plain text in the muted colour (the selection colour on a selected row). */
  static final class MutedRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
        boolean hasFocus, int row, int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (!isSelected) {
        setForeground(Theme.current().textMuted());
      }
      return this;
    }
  }
}
