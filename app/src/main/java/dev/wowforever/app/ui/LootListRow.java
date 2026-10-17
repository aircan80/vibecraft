package dev.wowforever.app.ui;

import dev.wowforever.app.model.InstanceLoot;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An AtlasLoot loot list an item is in, titled {@code "Scarlet Monastery › Armory trash"}: the instance, then the
 * boss or list. When two lists would read the same, each title ends in its slot ({@code " · slot 24"}).
 */
public record LootListRow(InstanceLoot loot, String title) {
  private static final String ARROW = " › ";

  public LootListRow {
    Objects.requireNonNull(loot, "loot");
    Objects.requireNonNull(title, "title");
  }

  /** The rows of {@code loot}: highest rate first (unknown last), then by title, then by slot. */
  public static List<LootListRow> of(List<InstanceLoot> loot) {
    Map<String, Long> readings = loot.stream().collect(Collectors.groupingBy(LootListRow::reading,
        Collectors.counting()));
    Comparator<LootListRow> order = Comparator
        .comparing((LootListRow r) -> r.loot().dropPct(), Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(r -> reading(r.loot()), String.CASE_INSENSITIVE_ORDER)
        .thenComparing(r -> r.loot().slot(), Comparator.nullsLast(Comparator.naturalOrder()));
    Function<InstanceLoot, LootListRow> row = l -> new LootListRow(l,
        readings.get(reading(l)) > 1 && l.slot() != null ? reading(l) + " · slot " + l.slot() : reading(l));
    return loot.stream().map(row).sorted(order).toList();
  }

  private static String reading(InstanceLoot l) {
    return l.instanceDisplayName() + ARROW + l.bossDisplayName();
  }
}
