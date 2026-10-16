package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.model.SearchHit;
import dev.wowforever.app.ui.Labels;

/** List rows made from search results, alike in the master list and the type-ahead dropdown. */
public final class ListEntries {
  private ListEntries() {}

  /** {@code hit} as a row: its name over its subtitle, with the count of hits it stands for. */
  public static ListEntry of(SearchHit hit) {
    return new ListEntry(hit.ref(), hit.displayName(), Labels.withCount(hit.subtitle(), hit.count()), null, null);
  }
}
