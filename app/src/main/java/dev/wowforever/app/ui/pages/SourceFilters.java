package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Applies the drop source filter ({@link SourceFilter}) to the drop rows of the item and NPC pages, and notes under a
 * section how many rows it hid.
 */
final class SourceFilters {
  private SourceFilters() {}

  /** The rows a filter shows, and how many it hid. */
  record Filtered<T>(List<T> rows, int hidden) {
    Filtered {
      rows = List.copyOf(rows);
    }
  }

  /** The rows of {@code rows} whose origin ({@code originOf}) {@code filter} keeps, in order. */
  static <T> Filtered<T> apply(List<T> rows, Function<T, String> originOf, SourceFilter filter) {
    List<T> kept = rows.stream().filter(r -> filter.keeps(originOf.apply(r))).toList();
    return new Filtered<>(kept, rows.size() - kept.size());
  }

  /** {@code "3 rows hidden by the source filter"} ({@code "1 row ..."}). */
  static String note(int hidden) {
    return hidden == 1 ? "1 row hidden by the source filter"
        : String.format(Locale.ROOT, "%,d rows hidden by the source filter", hidden);
  }

  /**
   * Notes under {@code section} how many rows the filter hid (nothing when none); a section left without rows hides
   * its empty table, so only the heading and the note show.
   */
  static void annotate(SourceSection section, Filtered<?> filtered) {
    if (filtered.hidden() > 0) {
      section.setNote(note(filtered.hidden()));
    }
    if (filtered.rows().isEmpty()) {
      section.markEmptied();
    }
  }
}
