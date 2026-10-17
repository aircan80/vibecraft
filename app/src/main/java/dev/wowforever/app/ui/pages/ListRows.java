package dev.wowforever.app.ui.pages;

import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.ItemFilter;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.model.NoFilter;
import dev.wowforever.app.model.NpcFilter;
import dev.wowforever.app.model.QuestFilter;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.widgets.IconCache;
import java.util.List;

/**
 * The rows a list page shows, read from the repositories, with the icons of an item list's first
 * {@value IconCache#WARM_ROWS} rows loaded into the {@linkplain IconCache#shared shared cache} (later rows load as
 * they are first painted). Call {@link #load} off the EDT.
 */
public final class ListRows {
  private ListRows() {}

  private static List<ItemSummary> warmed(List<ItemSummary> items) {
    IconCache.shared().warm(items.stream().limit(IconCache.WARM_ROWS).map(ItemSummary::iconId).toList(),
        IconCache.ROW);
    return items;
  }

  /**
   * The rows of {@code target}: {@code ItemSummary}, {@code NpcSummary} or {@code QuestSummary} rows its filter lets
   * through, every zone by name, or every instance in AtlasLoot order. Lists are not capped, as in v0.1.
   */
  public static List<?> load(ViewTarget.List target, Repositories repos) {
    return switch (target.filter()) {
      case ItemFilter f -> warmed(repos.items().list(f));
      case NpcFilter f -> repos.npcs().list(f);
      case QuestFilter f -> repos.quests().list(f);
      case NoFilter n -> switch (target.kind()) {
        case ZONE -> ListOptions.byName(repos.zones().all());
        case INSTANCE -> repos.instances().all();
        default -> throw new IllegalArgumentException("There is no list of " + target.kind().label() + " entities");
      };
    };
  }
}
