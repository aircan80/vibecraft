package dev.wowforever.app.ui;

import dev.wowforever.app.model.DropSource;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceLoot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A row of the item page's "Dropped by" table ({@link Columns#dropSourcesMerged()}): an NPC that drops the item, an
 * AtlasLoot loot list of a dungeon or raid the item is in ({@link Loot}, its instance shown as a chip), or the
 * divider that heads the rows whose drop rate is unknown.
 */
public sealed interface DropRow {
  /** The NPC of this row; empty for a loot list and the divider. */
  Optional<DropSource> drop();

  /** The instance loot list of this row (a loot list, or an NPC row its boss list was merged into); else empty. */
  default Optional<InstanceLoot> loot() {
    return Optional.empty();
  }

  /** The drop rate in percent; null when unknown (and for the divider). */
  default Double pct() {
    return null;
  }

  /** The name the row is sorted by; null for the divider. */
  default String name() {
    return null;
  }

  /** Where the Source cell links: the NPC, the boss's NPC or the instance; empty for the divider. */
  default Optional<EntityRef> ref() {
    return Optional.empty();
  }

  /**
   * An NPC that drops the item; {@code instance} is the AtlasLoot loot list of the same boss (null for none), which
   * the row stands for too, so its instance shows as a chip.
   */
  record Source(DropSource source, InstanceLoot instance) implements DropRow {
    public Source {
      Objects.requireNonNull(source, "source");
    }

    /** An NPC row without a loot list. */
    public Source(DropSource source) {
      this(source, null);
    }

    @Override
    public Optional<DropSource> drop() {
      return Optional.of(source);
    }

    @Override
    public Optional<InstanceLoot> loot() {
      return Optional.ofNullable(instance);
    }

    @Override
    public Double pct() {
      return source.dropPct();
    }

    @Override
    public String name() {
      return source.displayName();
    }

    @Override
    public Optional<EntityRef> ref() {
      return Optional.of(source.ref());
    }
  }

  /**
   * An AtlasLoot loot list of a dungeon or raid, read as {@code title}: its boss (or list, such as "Trash"), with
   * {@code " · slot n"} after it when another row would read the same ({@link #merged}). It links the boss's NPC when
   * it has one and else the instance; the instance shows as a chip.
   */
  record Loot(InstanceLoot list, String title) implements DropRow {
    public Loot {
      Objects.requireNonNull(list, "list");
      Objects.requireNonNull(title, "title");
    }

    /** A loot list read as its boss or list name. */
    public Loot(InstanceLoot list) {
      this(list, list.bossDisplayName());
    }

    @Override
    public Optional<DropSource> drop() {
      return Optional.empty();
    }

    @Override
    public Optional<InstanceLoot> loot() {
      return Optional.of(list);
    }

    @Override
    public Double pct() {
      return list.dropPct();
    }

    @Override
    public String name() {
      return title;
    }

    @Override
    public Optional<EntityRef> ref() {
      return Optional.of(list.bossNpc().orElse(list.instance()));
    }
  }

  /** The muted "Rate unknown (n)" row above the {@code count} rows whose rate is unknown. */
  record Divider(int count) implements DropRow {
    @Override
    public Optional<DropSource> drop() {
      return Optional.empty();
    }

    public String text() {
      return "Rate unknown (" + count + ")";
    }
  }

  /**
   * The NPC rows and the instance loot lists in one ranking: highest rate first (ties by name, then instance, then
   * slot), then a {@link Divider} and the rows whose rate is unknown, by name. The divider only separates two groups:
   * there is none when every rate is known or every rate is unknown.
   *
   * <p>A boss's NPC row takes in the first of the boss's loot lists that gives the same rate (the row then shows the
   * instance chip, and its rate is marked when either the NPC's or the list's sources disagree). Every other list is a
   * row of its own, so nothing is lost: a second list of the same boss, a list whose rate differs from the NPC's,
   * trash lists, bosses without an NPC and bosses whose NPC row is not shown. Loot rows that would read the same (one
   * list, several slots) end in {@code " · slot n"}.
   */
  static List<DropRow> merged(List<DropSource> npcRows, List<InstanceLoot> loot) {
    List<InstanceLoot> left = new ArrayList<>(loot);
    List<DropRow> rows = new ArrayList<>();
    for (DropSource s : npcRows) {
      InstanceLoot list = left.stream().filter(l -> Objects.equals(l.bossNpcId(), s.ref().id())
          && Objects.equals(l.dropPct(), s.dropPct())).findFirst().orElse(null);
      if (list != null) {
        left.remove(list); // that one list: the boss's others stay rows of their own
      }
      rows.add(new Source(s, list));
    }
    Map<String, Long> readings = left.stream().collect(Collectors.groupingBy(DropRow::reading,
        Collectors.counting()));
    for (InstanceLoot l : left) {
      rows.add(readings.get(reading(l)) > 1 && l.slot() != null
          ? new Loot(l, l.bossDisplayName() + " · slot " + l.slot()) : new Loot(l));
    }
    // by the boss or list name without any slot, then instance, then slot (slot 3 before slot 24)
    Comparator<DropRow> byName = Comparator
        .comparing((DropRow r) -> r instanceof Loot l ? l.list().bossDisplayName() : r.name(),
            String.CASE_INSENSITIVE_ORDER)
        .thenComparing(r -> r.loot().map(InstanceLoot::instanceDisplayName).orElse(""),
            String.CASE_INSENSITIVE_ORDER)
        .thenComparing(r -> r.loot().map(InstanceLoot::slot).orElse(null),
            Comparator.nullsLast(Comparator.naturalOrder()));
    List<DropRow> out = new ArrayList<>(rows.stream().filter(r -> r.pct() != null)
        .sorted(Comparator.comparing(DropRow::pct, Comparator.reverseOrder()).thenComparing(byName)).toList());
    List<DropRow> unknown = rows.stream().filter(r -> r.pct() == null).sorted(byName).toList();
    if (!unknown.isEmpty() && !out.isEmpty()) {
      out.add(new Divider(unknown.size()));
    }
    out.addAll(unknown);
    return List.copyOf(out);
  }

  /** How a loot list reads before any slot: its instance and boss or list. */
  private static String reading(InstanceLoot l) {
    return l.instanceDisplayName() + " › " + l.bossDisplayName();
  }

  /** Whether {@code row} lists an item source (not the divider). */
  static boolean isData(DropRow row) {
    return !(row instanceof Divider);
  }
}
