package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.SearchHit;
import dev.wowforever.app.ui.Labels;
import java.util.List;

/** List rows made from search results, alike in the master list and the type-ahead dropdown. */
public final class ListEntries {
  private ListEntries() {}

  /**
   * Loads the {@value IconCache#LIST} px icons of the item hits among {@code hits} into the {@linkplain
   * IconCache#shared shared cache}, so their rows paint with them: call on the worker that searched.
   */
  public static void warmIcons(List<SearchHit> hits) {
    IconCache.shared().warm(hits.stream().filter(h -> h.ref().kind() == EntityKind.ITEM).map(SearchHit::iconId)
        .toList(), IconCache.LIST);
  }

  /** {@code hit} as a row: its name over its subtitle, with the count of hits it stands for, quality and icon. */
  public static ListEntry of(SearchHit hit) {
    return new ListEntry(hit.ref(), hit.displayName(), Labels.withCount(hit.subtitle(), hit.count()), hit.quality(),
        hit.iconId());
  }
}
