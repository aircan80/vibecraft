package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.Columns.Column;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Table rows grouped under heading rows ({@code "Gear (2)"}), for one table whose columns line up across the
 * groups. Heading rows show their text in the first column, leave the others blank (or show a group's own column
 * label, {@link GroupCells}) and link nowhere; pages install them as
 * {@linkplain dev.wowforever.app.ui.widgets.DividerRows dividers}. A collapsible group's heading reads
 * {@code "World drops (23) ▸"} while its entries are left out and {@code "World drops (23) ▾"} while they show.
 */
final class GroupedRows {
  /** The marker of a collapsed group's heading. */
  static final String COLLAPSED = " ▸";
  /** The marker of an expanded collapsible group's heading. */
  static final String EXPANDED = " ▾";

  private GroupedRows() {}

  /** A row of a grouped table: a group's heading or one of its entries. */
  sealed interface Row<T> permits Heading, Entry {}

  /** How a group's heading shows its entries. */
  enum Fold { NONE, COLLAPSED, EXPANDED }

  /** The heading of a group of {@code count} entries, collapsible unless {@code fold} is {@link Fold#NONE}. */
  record Heading<T>(String group, int count, Fold fold) implements Row<T> {
    /** A heading that cannot be collapsed. */
    Heading(String group, int count) {
      this(group, count, Fold.NONE);
    }

    /** {@code "Gear (2)"}, a collapsible group's ending in {@link #COLLAPSED} or {@link #EXPANDED}. */
    String text() {
      String text = String.format(Locale.ROOT, "%s (%,d)", group, count);
      return switch (fold) {
        case NONE -> text;
        case COLLAPSED -> text + COLLAPSED;
        case EXPANDED -> text + EXPANDED;
      };
    }

    boolean collapsible() {
      return fold != Fold.NONE;
    }
  }

  /** One entry of a group ({@code group} null when unknown). */
  record Entry<T>(T value, String group) implements Row<T> {
    Entry {
      Objects.requireNonNull(value, "value");
    }

    /** An entry of no particular group. */
    Entry(T value) {
      this(value, null);
    }

    /** The value of {@code row} when it is an entry whose value is a {@code type}; null otherwise (a heading). */
    static <V> V valueOf(Object row, Class<V> type) {
      return row instanceof Entry<?> e && type.isInstance(e.value()) ? type.cast(e.value()) : null;
    }
  }

  /**
   * What some columns show for one group instead of their own values: an entry's value of column {@code title} in
   * {@code group} ({@link #entryValue}), and the label its heading shows in that column ({@link #headingLabel}),
   * each null to keep the column's own (a blank heading cell).
   */
  interface GroupCells<T> {
    Function<T, Object> entryValue(String group, String title);

    String headingLabel(String group, String title);

    /** No group shows anything of its own. */
    static <T> GroupCells<T> none() {
      return new GroupCells<>() {
        @Override
        public Function<T, Object> entryValue(String group, String title) {
          return null;
        }

        @Override
        public String headingLabel(String group, String title) {
          return null;
        }
      };
    }
  }

  static boolean isHeading(Row<?> row) {
    return row instanceof Heading;
  }

  /** Whether {@code row} is the heading of a collapsible group. */
  static boolean isCollapsibleHeading(Row<?> row) {
    return row instanceof Heading<?> h && h.collapsible();
  }

  /**
   * {@code rows} grouped by {@code groupOf}, the groups in {@code order} (groups without rows are left out; a
   * group missing from {@code order} goes last), each under its heading and sorted by {@code within}, or kept in
   * the given order when {@code within} is null.
   */
  static <T> List<Row<T>> of(List<T> rows, Function<T, String> groupOf, List<String> order, Comparator<T> within) {
    return of(rows, groupOf, order, within, Set.of(), Set.of());
  }

  /**
   * {@code rows} grouped as {@link #of(List, Function, List, Comparator)}; the groups in {@code collapsible} can be
   * collapsed, and those of them in {@code collapsed} show their heading alone.
   */
  static <T> List<Row<T>> of(List<T> rows, Function<T, String> groupOf, List<String> order, Comparator<T> within,
      Set<String> collapsible, Set<String> collapsed) {
    Map<String, List<T>> groups = new LinkedHashMap<>();
    order.forEach(g -> groups.put(g, new ArrayList<>()));
    rows.forEach(r -> groups.computeIfAbsent(groupOf.apply(r), g -> new ArrayList<>()).add(r));
    List<Row<T>> out = new ArrayList<>();
    groups.forEach((group, entries) -> {
      if (entries.isEmpty()) {
        return;
      }
      if (within != null) {
        entries.sort(within);
      }
      Fold fold = !collapsible.contains(group) ? Fold.NONE
          : collapsed.contains(group) ? Fold.COLLAPSED : Fold.EXPANDED;
      out.add(new Heading<>(group, entries.size(), fold));
      if (fold != Fold.COLLAPSED) {
        entries.forEach(e -> out.add(new Entry<>(e, group)));
      }
    });
    return List.copyOf(out);
  }

  /**
   * {@code cols} over grouped rows: entries read, link, colour, show icons and flag their rates as before; a heading
   * fills the first column only.
   */
  static <T> List<Column<Row<T>>> columns(List<Column<T>> cols) {
    return columns(cols, GroupCells.none());
  }

  /** {@link #columns(List)}, where {@code cells} gives some groups' own values and heading labels. */
  static <T> List<Column<Row<T>>> columns(List<Column<T>> cols, GroupCells<T> cells) {
    List<Column<Row<T>>> out = new ArrayList<>();
    for (int i = 0; i < cols.size(); i++) {
      Column<T> c = cols.get(i);
      boolean first = i == 0;
      Function<Row<T>, Object> value = r -> switch (r) {
        case Entry<T> e -> {
          Function<T, Object> own = e.group() == null ? null : cells.entryValue(e.group(), c.title());
          yield (own != null ? own : c.value()).apply(e.value());
        }
        case Heading<T> h -> first ? h.text() : cells.headingLabel(h.group(), c.title());
      };
      Function<Row<T>, Optional<EntityRef>> link = c.isLink()
          ? r -> r instanceof Entry<T> e ? c.refFor(e.value()) : Optional.empty()
          : null;
      Function<Row<T>, Integer> quality = c.quality() == null ? null
          : r -> r instanceof Entry<T> e ? c.qualityFor(e.value()) : null;
      Function<Row<T>, Boolean> disagrees = c.disagrees() == null ? null
          : r -> r instanceof Entry<T> e && c.disagreesFor(e.value());
      Function<Row<T>, Long> icon = c.icon() == null ? null
          : r -> r instanceof Entry<T> e ? c.iconFor(e.value()) : null;
      out.add(new Columns.Column<>(c.title(), c.type(), value, link, c.order(), quality, disagrees, icon));
    }
    return List.copyOf(out);
  }
}
