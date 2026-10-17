package dev.wowforever.app.ui.pages;

import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.ItemClassNode;
import dev.wowforever.app.model.ZoneSummary;
import java.util.Comparator;
import java.util.List;

/**
 * What the filter bars of the list pages offer: the item class tree (classes and subclasses that have items) and
 * the zones that have content, by name. {@link #load} reads them; call it off the EDT.
 */
public record ListOptions(List<ItemClassNode> classes, List<ZoneSummary> zones) {
  /** No classes and no zones, for the lists that have no filter bar. */
  public static final ListOptions EMPTY = new ListOptions(List.of(), List.of());

  public ListOptions {
    classes = List.copyOf(classes);
    zones = List.copyOf(zones);
  }

  /** Runs the class tree and zone queries. */
  public static ListOptions load(Repositories repos) {
    return new ListOptions(repos.items().classTree(), byName(repos.zones().all()));
  }

  /** {@code zones} by display name, ignoring case, then id. */
  static List<ZoneSummary> byName(List<ZoneSummary> zones) {
    return zones.stream().sorted(Comparator.comparing(ZoneSummary::displayName, String.CASE_INSENSITIVE_ORDER)
        .thenComparingLong(ZoneSummary::id)).toList();
  }
}
