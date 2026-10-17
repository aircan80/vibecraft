package dev.wowforever.app.ui;

import dev.wowforever.app.model.DropSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A row of the ranked drop table ({@link Columns#dropSourcesRanked()}): an NPC that drops the item, or the
 * divider that heads the NPCs whose drop rate is unknown.
 */
public sealed interface DropRow {
  /** The NPC of this row; empty for the divider. */
  Optional<DropSource> drop();

  /** An NPC that drops the item. */
  record Source(DropSource source) implements DropRow {
    public Source {
      Objects.requireNonNull(source, "source");
    }

    @Override
    public Optional<DropSource> drop() {
      return Optional.of(source);
    }
  }

  /** The muted "Rate unknown (n)" row above the {@code count} NPCs whose rate is unknown. */
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
   * The NPCs with a known rate, highest first (ties by name), then a {@link Divider} and the NPCs whose rate is
   * unknown, by name. The divider only separates two groups: there is none when every rate is known or every
   * rate is unknown.
   */
  static List<DropRow> ranked(List<DropSource> sources) {
    Comparator<DropSource> byName = Comparator.comparing(DropSource::displayName, String.CASE_INSENSITIVE_ORDER);
    List<DropRow> rows = new ArrayList<>();
    sources.stream().filter(s -> s.dropPct() != null)
        .sorted(Comparator.comparing(DropSource::dropPct, Comparator.reverseOrder()).thenComparing(byName))
        .forEach(s -> rows.add(new Source(s)));
    List<DropSource> unknown = sources.stream().filter(s -> s.dropPct() == null).sorted(byName).toList();
    if (!unknown.isEmpty() && !rows.isEmpty()) {
      rows.add(new Divider(unknown.size()));
    }
    unknown.forEach(s -> rows.add(new Source(s)));
    return List.copyOf(rows);
  }
}
