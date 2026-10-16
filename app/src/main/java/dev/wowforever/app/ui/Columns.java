package dev.wowforever.app.ui;

import dev.wowforever.app.model.DropSource;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.ObjectSummary;
import dev.wowforever.app.model.QuestGiver;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.model.SearchHit;
import dev.wowforever.app.model.Spawn;
import dev.wowforever.app.model.ZoneSummary;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Column lists for the results and detail tables, one per row type. The first column of each is a link. */
public final class Columns {
  private Columns() {}

  /**
   * One table column: its title, value class (drives sorting and alignment), cell value and, for link
   * columns, the entity a cell points at. {@code link} is null for plain columns; {@code order} sorts the cell
   * values and is null for the sorter's default order of {@code type}.
   */
  public record Column<T>(String title, Class<?> type, Function<T, Object> value,
      Function<T, Optional<EntityRef>> link, Comparator<?> order) {
    public Column {
      Objects.requireNonNull(title, "title");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(value, "value");
    }

    /** A column sorted in the default order of {@code type}. */
    public Column(String title, Class<?> type, Function<T, Object> value, Function<T, Optional<EntityRef>> link) {
      this(title, type, value, link, null);
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

  /** Shown for a missing percentage or coordinate. */
  public static final String NONE = "—";

  /**
   * Orders formatted numbers ({@code "5.00 %"}, {@code "45.3"}) by their leading number; text without one
   * (such as {@link #NONE}) sorts last.
   */
  public static final Comparator<String> NUMERIC_TEXT = Comparator.comparing(Columns::leadingNumber,
      Comparator.nullsLast(Comparator.naturalOrder()));

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
        plain("Type", String.class, i -> Labels.contentType(i.contentType())),
        plain("Levels", Levels.class, i -> new Levels(i.levelMin(), i.levelMax())),
        link("Zone", InstanceSummary::zoneName, i -> zoneRef(i.zoneId())));
  }

  public static List<Column<SearchHit>> searchHits() {
    return List.of(
        link("Name", SearchHit::displayName, h -> Optional.of(h.ref())),
        plain("Details", String.class, SearchHit::subtitle));
  }

  /**
   * The ranked drop table of the item page ({@link DropRow#ranked}): source, level, zone, rate (a {@link Double},
   * null when unknown) and data origin. The divider row shows its text in the Source column and links nowhere.
   */
  public static List<Column<DropRow>> dropSourcesRanked() {
    return List.of(
        link("Source", r -> r instanceof DropRow.Divider d ? d.text() : r.drop().orElseThrow().displayName(),
            r -> r.drop().map(DropSource::ref)),
        plain("Level", Levels.class, r -> r.drop().map(s -> new Levels(Labels.positive(s.levelMin()),
            Labels.positive(s.levelMax()))).orElse(null)),
        link("Zone", r -> r.drop().map(DropSource::zoneName).orElse(null),
            r -> r.drop().flatMap(s -> zoneRef(s.zoneId()))),
        plain("Rate", Double.class, r -> r.drop().map(DropSource::dropPct).orElse(null)),
        plain("Origin", String.class, r -> r.drop().map(DropSource::origin).orElse(null)));
  }

  /**
   * The AtlasLoot loot lists an item is in ({@link LootListRow}): the list linked to its instance, the boss
   * linked to its NPC if any, and the rate (a {@link Double}, null when unknown).
   */
  public static List<Column<LootListRow>> lootLists() {
    return List.of(
        link("Loot list", LootListRow::title, r -> Optional.of(r.loot().instance())),
        link("Boss", r -> r.loot().bossDisplayName(), r -> r.loot().bossNpc()),
        plain("Rate", Double.class, r -> r.loot().dropPct()));
  }

  /** Objects, containers, vendors and quests linked to an item. */
  public static List<Column<ItemSource>> itemSources() {
    return List.of(
        link("Name", ItemSource::displayName, s -> Optional.of(s.ref())),
        plain("Kind", String.class, s -> sourceKindText(s.kind())));
  }

  /** Vendors or quests linked to an item, when a table holds one kind only: the name alone. */
  public static List<Column<ItemSource>> sourceNames() {
    return List.of(link("Name", ItemSource::displayName, s -> Optional.of(s.ref())));
  }

  /** Items an NPC drops or sells, an object contains or a quest rewards. */
  public static List<Column<LootRow>> lootRows() {
    return List.of(
        link("Item", LootRow::displayName, l -> Optional.of(l.item())),
        plain("Item level", Integer.class, LootRow::itemLevel),
        numeric("Drop %", l -> percentText(l.dropPct())),
        plain("Origin", String.class, LootRow::origin),
        plain("Slot", Integer.class, LootRow::slot));
  }

  /** The loot of an AtlasLoot boss, in loot-table slot order. */
  public static List<Column<LootRow>> bossLoot() {
    return List.of(
        link("Item", LootRow::displayName, l -> Optional.of(l.item())),
        plain("Item level", Integer.class, LootRow::itemLevel),
        plain("Slot", Integer.class, LootRow::slot),
        numeric("Drop %", l -> percentText(l.dropPct())),
        plain("Origin", String.class, LootRow::origin));
  }

  /** The NPCs, objects and items that start or end a quest. */
  public static List<Column<QuestGiver>> questGivers() {
    return List.of(
        link("Name", QuestGiver::displayName, g -> Optional.of(g.ref())),
        plain("Type", String.class, g -> g.ref().kind().label()),
        plain("Role", String.class, g -> roleText(g.role())));
  }

  /** Spawn points: the zone and the map coordinates. */
  public static List<Column<Spawn>> spawns() {
    return List.of(
        link("Zone", s -> s.zoneName() != null ? s.zoneName() : EntityKind.ZONE.unknownName(s.zoneId()),
            s -> zoneRef(s.zoneId())),
        numeric("X", s -> String.format(Locale.ROOT, "%.1f", s.x())),
        numeric("Y", s -> String.format(Locale.ROOT, "%.1f", s.y())));
  }

  /** A drop chance as {@code "56.07 %"}, or {@link #NONE} when unknown. */
  public static String percentText(Double pct) {
    return pct == null ? NONE : String.format(Locale.ROOT, "%.2f %%", pct);
  }

  /** {@link ItemSource} kind for display. */
  public static String sourceKindText(String kind) {
    if (kind == null) {
      return null;
    }
    return switch (kind) {
      case ItemSource.OBJECT -> "Object";
      case ItemSource.CONTAINER -> "Container item";
      case ItemSource.VENDOR -> "Vendor";
      case ItemSource.REWARD -> "Quest reward";
      case ItemSource.RELATED -> "Related quest";
      default -> kind;
    };
  }

  /** {@link QuestGiver} role for display: start → Starts, end → Ends. */
  public static String roleText(String role) {
    if (role == null) {
      return null;
    }
    return switch (role) {
      case "start" -> "Starts";
      case "end" -> "Ends";
      default -> role;
    };
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

  private static Optional<EntityRef> zoneRef(Long zoneId) {
    return Optional.ofNullable(zoneId).map(id -> new EntityRef(EntityKind.ZONE, id));
  }

  private static Double leadingNumber(String text) {
    if (text == null) {
      return null;
    }
    int end = text.indexOf(' ');
    try {
      return Double.parseDouble(end < 0 ? text : text.substring(0, end));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** A column of formatted numbers, sorted by {@link #NUMERIC_TEXT}. */
  private static <T> Column<T> numeric(String title, Function<T, Object> value) {
    return new Column<>(title, String.class, value, null, NUMERIC_TEXT);
  }

  private static <T> Column<T> plain(String title, Class<?> type, Function<T, Object> value) {
    return new Column<>(title, type, value, null);
  }

  private static <T> Column<T> link(String title, Function<T, Object> value, Function<T, Optional<EntityRef>> link) {
    return new Column<>(title, String.class, value, link);
  }
}
