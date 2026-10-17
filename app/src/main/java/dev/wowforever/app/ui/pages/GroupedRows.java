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
import java.util.function.Function;

/**
 * Table rows grouped under heading rows ({@code "Gear (2)"}), for one table whose columns line up across the
 * groups. Heading rows show their text in the first column, leave the others blank and link nowhere; pages
 * install them as {@linkplain dev.wowforever.app.ui.widgets.DividerRows dividers}.
 */
final class GroupedRows {
  private GroupedRows() {}

  /** A row of a grouped table: a group's heading or one of its entries. */
  sealed interface Row<T> permits Heading, Entry {}

  /** The heading of a group of {@code count} entries. */
  record Heading<T>(String group, int count) implements Row<T> {
    /** {@code "Gear (2)"}. */
    String text() {
      return String.format(Locale.ROOT, "%s (%,d)", group, count);
    }
  }

  /** One entry of a group. */
  record Entry<T>(T value) implements Row<T> {
    Entry {
      Objects.requireNonNull(value, "value");
    }
  }

  static boolean isHeading(Row<?> row) {
    return row instanceof Heading;
  }

  /**
   * {@code rows} grouped by {@code groupOf}, the groups in {@code order} (groups without rows are left out; a
   * group missing from {@code order} goes last), each under its heading and sorted by {@code within}, or kept in
   * the given order when {@code within} is null.
   */
  static <T> List<Row<T>> of(List<T> rows, Function<T, String> groupOf, List<String> order, Comparator<T> within) {
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
      out.add(new Heading<>(group, entries.size()));
      entries.forEach(e -> out.add(new Entry<>(e)));
    });
    return List.copyOf(out);
  }

  /**
   * {@code cols} over grouped rows: entries read, link, colour and flag their rates as before; a heading fills the
   * first column only.
   */
  static <T> List<Column<Row<T>>> columns(List<Column<T>> cols) {
    List<Column<Row<T>>> out = new ArrayList<>();
    for (int i = 0; i < cols.size(); i++) {
      Column<T> c = cols.get(i);
      boolean first = i == 0;
      Function<Row<T>, Object> value = r -> switch (r) {
        case Entry<T> e -> c.value().apply(e.value());
        case Heading<T> h -> first ? h.text() : null;
      };
      Function<Row<T>, Optional<EntityRef>> link = c.isLink()
          ? r -> r instanceof Entry<T> e ? c.refFor(e.value()) : Optional.empty()
          : null;
      Function<Row<T>, Integer> quality = c.quality() == null ? null
          : r -> r instanceof Entry<T> e ? c.qualityFor(e.value()) : null;
      Function<Row<T>, Boolean> disagrees = c.disagrees() == null ? null
          : r -> r instanceof Entry<T> e && c.disagreesFor(e.value());
      out.add(new Columns.Column<>(c.title(), c.type(), value, link, c.order(), quality, disagrees));
    }
    return List.copyOf(out);
  }
}
