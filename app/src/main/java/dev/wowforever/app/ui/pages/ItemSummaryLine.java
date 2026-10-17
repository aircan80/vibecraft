package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.ItemDetail;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.ui.Labels;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The item page's one-line summary of where an item comes from, positive facts only: {@code "3 NPCs · 2 loot lists ·
 * 1 quest"}. Every kind with sources is counted and a kind without any is left out (never "no vendor"); an item
 * with no source at all reads empty.
 */
final class ItemSummaryLine {
  private ItemSummaryLine() {}

  static String of(ItemDetail d) {
    int containers = d.containedInObjects().size() + d.containedInItems().size();
    long quests = Stream.concat(d.rewardFrom().stream(), d.relatedQuests().stream()).map(ItemSource::ref)
        .distinct().count();
    return Labels.join(
        count(d.droppedBy().size(), "NPC", "NPCs"),
        count(d.inInstanceLoot().size(), "loot list", "loot lists"),
        count(containers, "container", "containers"),
        count(d.soldBy().size(), "vendor", "vendors"),
        count(quests, "quest", "quests"));
  }

  /** {@code "1 NPC"}, {@code "3 NPCs"}; null for none. */
  private static String count(long n, String one, String many) {
    return n == 0 ? null : String.format(Locale.ROOT, "%,d %s", n, n == 1 ? one : many);
  }
}
