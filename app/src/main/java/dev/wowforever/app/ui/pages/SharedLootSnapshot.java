package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.*;
import java.util.List;

/** Derived presentation of source-declared extra lists, not inferred reference-pool identities. */
public record SharedLootSnapshot(InstanceDetail detail, List<SourceList> lists) {
  public SharedLootSnapshot { lists = List.copyOf(lists); }
  public record Mode(String difficulty, List<LootRow> rows) {
    public Mode { rows = List.copyOf(rows); }
  }
  public record SourceList(BossSummary identity, List<Mode> modes) {
    public SourceList { modes = List.copyOf(modes); }
  }
  public record Membership(BossSummary list, String difficulty, LootRow evidence) {}

  /** The supplied lookup must be an already loaded snapshot, never a live repository adapter. */
  public static SharedLootSnapshot from(InstanceDetail detail, BossLoot loaded) {
    var extras = detail.bosses().stream().filter(BossSummary::extraList).toList();
    var lists = extras.stream().map(b -> new SourceList(b, detail.difficulties().stream()
        .map(mode -> new Mode(mode, loaded.lookup(b.id(), mode))).toList())).toList();
    return new SharedLootSnapshot(new InstanceDetail(detail.summary(), extras, detail.difficulties()), lists);
  }

  /** Every membership retains its original row, including rate, origin and observed state. */
  public List<Membership> memberships(long itemId) {
    return lists.stream().flatMap(list -> list.modes().stream().flatMap(mode -> mode.rows().stream()
        .filter(row -> row.item().id() == itemId)
        .map(row -> new Membership(list.identity(), mode.difficulty(), row)))).toList();
  }

  public List<LootRow> lookup(long listId, String difficulty) {
    return lists.stream().filter(list -> list.identity().id() == listId)
        .flatMap(list -> list.modes().stream()).filter(mode -> mode.difficulty().equals(difficulty))
        .findFirst().map(Mode::rows).orElse(List.of());
  }
}
