package dev.wowforever.app.ui.pages;

import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.Columns.Column;
import java.util.List;
import java.util.Objects;

/**
 * Leaves out the columns of a page table that would say nothing: a column empty for every row (null, blank or
 * {@link Columns#NONE}), an Origin column with the same origin on every row, and a Zone column naming the page's
 * own zone on every row. A table without rows keeps every column; a table whose every column would go keeps its
 * first (an instance spawn in the page's zone, without coordinates, still names the zone).
 */
final class VisibleColumns {
  static final String ORIGIN = "Origin";
  static final String ZONE = "Zone";

  private VisibleColumns() {}

  /** The columns of {@code cols} worth showing for {@code rows} on a page about {@code pageZone} (may be null). */
  static <T> List<Column<T>> of(List<Column<T>> cols, List<T> rows, String pageZone) {
    if (rows.isEmpty()) {
      return cols;
    }
    List<Column<T>> shown = cols.stream().filter(c -> !hidden(c, rows, pageZone)).toList();
    return shown.isEmpty() ? cols.subList(0, Math.min(1, cols.size())) : shown;
  }

  private static <T> boolean hidden(Column<T> c, List<T> rows, String pageZone) {
    List<Object> values = rows.stream().map(c.value()).toList();
    if (values.stream().allMatch(VisibleColumns::empty)) {
      return true;
    }
    if (c.title().equals(ORIGIN)) {
      return values.stream().distinct().count() == 1;
    }
    if (c.title().equals(ZONE) && pageZone != null) {
      return values.stream().allMatch(v -> Objects.equals(text(v), pageZone));
    }
    return false;
  }

  private static boolean empty(Object value) {
    String text = text(value);
    return text == null || text.isBlank() || text.equals(Columns.NONE);
  }

  private static String text(Object value) {
    return value == null ? null : value.toString();
  }
}
