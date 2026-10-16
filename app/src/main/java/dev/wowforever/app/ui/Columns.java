package dev.wowforever.app.ui;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.ObjectSummary;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.model.SearchHit;
import dev.wowforever.app.model.ZoneSummary;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Column lists for the results table, one per row type. The first column of each is the linked name. */
public final class Columns {
  private Columns() {}

  /**
   * One table column: its title, value class (drives sorting and alignment), cell value and, for link
   * columns, the entity a cell points at. {@code link} is null for plain columns.
   */
  public record Column<T>(String title, Class<?> type, Function<T, Object> value,
      Function<T, Optional<EntityRef>> link) {
    public Column {
      Objects.requireNonNull(title, "title");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(value, "value");
    }

    /** Whether the cells of this column link to an entity. */
    public boolean isLink() {
      return link != null;
    }

    /** The entity the cell of {@code row} links to; empty for plain columns and empty links. */
    public Optional<EntityRef> refFor(T row) {
      return link == null ? Optional.empty() : link.apply(row);
    }
  }

  /** A level range that sorts numerically (unknown last) and prints as {@code 20}, {@code 20–21} or blank. */
  public record Levels(Integer min, Integer max) implements Comparable<Levels> {
    private static final Comparator<Levels> ORDER =
        Comparator.comparing(Levels::low, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Levels::high, Comparator.nullsLast(Comparator.naturalOrder()));

    private Integer low() {
      return min != null ? min : max;
    }

    private Integer high() {
      return max != null ? max : min;
    }

    @Override
    public int compareTo(Levels o) {
      return ORDER.compare(this, o);
    }

    @Override
    public String toString() {
      if (low() == null) {
        return "";
      }
      return low().equals(high()) ? String.valueOf(low()) : low() + "–" + high();
    }
  }

  public static List<Column<ItemSummary>> items() {
    return List.of(
        link("Name", ItemSummary::displayName, i -> Optional.of(i.ref())),
        plain("Item level", Integer.class, ItemSummary::itemLevel),
        plain("Required level", Integer.class, ItemSummary::requiredLevel),
        plain("Class", String.class, ItemSummary::className),
        plain("Subclass", String.class, ItemSummary::subclassName),
        plain("Sources", Integer.class, ItemSummary::sourceCount));
  }

  public static List<Column<NpcSummary>> npcs() {
    return List.of(
        link("Name", NpcSummary::displayName, n -> Optional.of(n.ref())),
        plain("Title", String.class, NpcSummary::subName),
        plain("Level", Levels.class, n -> new Levels(n.minLevel(), n.maxLevel())),
        plain("Rank", String.class, n -> rankText(n.rank())),
        link("Zone", NpcSummary::zoneName, n -> zoneRef(n.zoneId())));
  }

  public static List<Column<ObjectSummary>> objects() {
    return List.of(
        link("Name", ObjectSummary::displayName, o -> Optional.of(o.ref())),
        link("Zone", ObjectSummary::zoneName, o -> zoneRef(o.zoneId())));
  }

  public static List<Column<QuestSummary>> quests() {
    return List.of(
        link("Name", QuestSummary::displayName, q -> Optional.of(q.ref())),
        plain("Level", Integer.class, QuestSummary::questLevel),
        plain("Required level", Integer.class, QuestSummary::requiredLevel),
        plain("Zone", String.class, QuestSummary::zoneName));
  }

  public static List<Column<ZoneSummary>> zones() {
    return List.of(
        link("Name", ZoneSummary::displayName, z -> Optional.of(z.ref())),
        plain("Kind", String.class, ZoneSummary::kind));
  }

  public static List<Column<InstanceSummary>> instances() {
    return List.of(
        link("Name", InstanceSummary::displayName, i -> Optional.of(i.ref())),
        plain("Type", String.class, i -> contentTypeText(i.contentType())),
        plain("Levels", Levels.class, i -> new Levels(i.levelMin(), i.levelMax())));
  }

  public static List<Column<SearchHit>> searchHits() {
    return List.of(
        link("Name", SearchHit::displayName, h -> Optional.of(h.ref())),
        plain("Details", String.class, SearchHit::subtitle));
  }

  /** NPC rank text: 0 Normal, 1 Elite, 2 Rare Elite, 3 Boss, 4 Rare; null when unknown. */
  public static String rankText(Integer rank) {
    if (rank == null) {
      return null;
    }
    return switch (rank) {
      case 0 -> "Normal";
      case 1 -> "Elite";
      case 2 -> "Rare Elite";
      case 3 -> "Boss";
      case 4 -> "Rare";
      default -> "Rank " + rank;
    };
  }

  /** AtlasLoot content type for display: {@code DUNGEON} → Dungeon, {@code RAID40} → Raid (40). */
  public static String contentTypeText(String contentType) {
    if (contentType == null) {
      return null;
    }
    if (contentType.equals("DUNGEON")) {
      return "Dungeon";
    }
    if (contentType.startsWith("RAID")) {
      String size = contentType.substring("RAID".length());
      return size.isEmpty() ? "Raid" : "Raid (" + size + ")";
    }
    return contentType;
  }

  private static Optional<EntityRef> zoneRef(Long zoneId) {
    return Optional.ofNullable(zoneId).map(id -> new EntityRef(EntityKind.ZONE, id));
  }

  private static <T> Column<T> plain(String title, Class<?> type, Function<T, Object> value) {
    return new Column<>(title, type, value, null);
  }

  private static <T> Column<T> link(String title, Function<T, Object> value, Function<T, Optional<EntityRef>> link) {
    return new Column<>(title, String.class, value, link);
  }
}
