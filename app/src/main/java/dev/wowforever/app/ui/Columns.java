package dev.wowforever.app.ui;

import dev.wowforever.app.db.AssetRepository;
import dev.wowforever.app.model.DropSource;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceLoot;
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
   * values and is null for the sorter's default order of {@code type}. {@code quality} gives the item quality
   * a cell's text is coloured by (null, or a null quality, for the usual colour). {@code disagrees} tells, for a
   * {@linkplain #RATE rate} column, whether a cell's drop rate sources disagree (null: never). {@code icon} gives
   * the item icon a cell shows before its text ({@link AssetRepository#FALLBACK_ICON_ID} for the question mark,
   * null for none; a null function for a column without icons).
   */
  public record Column<T>(String title, Class<?> type, Function<T, Object> value,
      Function<T, Optional<EntityRef>> link, Comparator<?> order, Function<T, Integer> quality,
      Function<T, Boolean> disagrees, Function<T, Long> icon) {
    public Column {
      Objects.requireNonNull(title, "title");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(value, "value");
    }

    /** A column without item icons. */
    public Column(String title, Class<?> type, Function<T, Object> value, Function<T, Optional<EntityRef>> link,
        Comparator<?> order, Function<T, Integer> quality, Function<T, Boolean> disagrees) {
      this(title, type, value, link, order, quality, disagrees, null);
    }

    /** A column without rate provenance. */
    public Column(String title, Class<?> type, Function<T, Object> value, Function<T, Optional<EntityRef>> link,
        Comparator<?> order, Function<T, Integer> quality) {
      this(title, type, value, link, order, quality, null);
    }

    /** A column without quality colours. */
    public Column(String title, Class<?> type, Function<T, Object> value, Function<T, Optional<EntityRef>> link,
        Comparator<?> order) {
      this(title, type, value, link, order, null);
    }

    /** A column sorted in the default order of {@code type}. */
    public Column(String title, Class<?> type, Function<T, Object> value, Function<T, Optional<EntityRef>> link) {
      this(title, type, value, link, null);
    }

    /** The item quality the cell of {@code row} is coloured by; null for the usual colour. */
    public Integer qualityFor(T row) {
      return quality == null ? null : quality.apply(row);
    }

    /** Whether the rate in the cell of {@code row} has sources that disagree; false for other columns. */
    public boolean disagreesFor(T row) {
      return disagrees != null && Boolean.TRUE.equals(disagrees.apply(row));
    }

    /** Whether the cells of this column show item icons. */
    public boolean hasIcon() {
      return icon != null;
    }

    /** The icon id the cell of {@code row} shows (0: the question mark); null for none. */
    public Long iconFor(T row) {
      return icon == null ? null : icon.apply(row);
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
  /** The title of the drop-rate columns (a {@link Double} percentage, null when unknown). */
  public static final String RATE = "Rate";
  /** Appended to a drop rate whose sources disagree. */
  public static final String DISAGREES = " ≠";
  /** Appended to a drop rate whose headline is VMaNGOS's ("Vanilla rate"), before {@link #DISAGREES}. */
  public static final String VANILLA = " V";
  /** The title of the loot tables' class restriction column ({@link Labels#classes}). */
  public static final String CLASSES = "Classes";
  /** The title of the NPC tables' reaction column ({@link #friendlyText}). */
  public static final String FRIENDLY_TO = "Friendly to";
  /** The title of the loot tables' AtlasLoot entry order column (not an equipment slot). */
  public static final String LOOT_ORDER = "#";
  /** The origin of a row that comes from an AtlasLoot loot list. */
  public static final String ATLASLOOT = "atlasloot";

  /**
   * Orders formatted numbers ({@code "5.00 %"}, {@code "45.3"}) by their leading number; text without one
   * (such as {@link #NONE}) sorts last.
   */
  public static final Comparator<String> NUMERIC_TEXT = Comparator.comparing(Columns::leadingNumber,
      Comparator.nullsLast(Comparator.naturalOrder()));

  public static List<Column<ItemSummary>> items() {
    return List.of(
        itemLink("Name", ItemSummary::displayName, i -> Optional.of(i.ref()), ItemSummary::quality,
            i -> iconOrFallback(i.iconId())),
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
        plain(FRIENDLY_TO, String.class, n -> friendlyText(n.friendlyTo())),
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
        plain("Kind", String.class, ZoneSummary::kind),
        link("Parent", ZoneSummary::parentName, z -> zoneRef(z.parentId())));
  }

  /** Instances, each named with its wing when its key names one ({@link Labels#instanceTitle}). */
  public static List<Column<InstanceSummary>> instances() {
    return List.of(
        link("Name", i -> Labels.instanceTitle(i.displayName(), i.key()), i -> Optional.of(i.ref())),
        plain("Type", String.class, i -> Labels.contentType(i.contentType())),
        plain("Levels", Levels.class, i -> new Levels(i.levelMin(), i.levelMax())),
        link("Zone", InstanceSummary::zoneName, i -> zoneRef(i.zoneId())));
  }

  public static List<Column<SearchHit>> searchHits() {
    return List.of(
        itemLink("Name", SearchHit::displayName, h -> Optional.of(h.ref()), SearchHit::quality,
            h -> h.ref().kind() == EntityKind.ITEM ? iconOrFallback(h.iconId()) : null),
        plain("Details", String.class, SearchHit::subtitle));
  }

  /**
   * The item page's "Dropped by" table ({@link DropRow#merged}): source, level, zone, rate (a {@link Double}, null
   * when unknown) and data origin. An NPC row links its NPC; a loot list reads as its boss (or list, such as
   * "Trash"), linked to the boss's NPC or else the instance, from AtlasLoot, with no level or zone. The page shows
   * the instance of a loot list (and of an NPC row a boss list was merged into) as a chip in the Source column. The
   * divider row shows its text in the Source column and links nowhere.
   */
  public static List<Column<DropRow>> dropSourcesMerged() {
    return List.of(
        link("Source", r -> r instanceof DropRow.Divider d ? d.text() : r.name(), DropRow::ref),
        plain("Level", Levels.class, r -> r.drop().map(s -> new Levels(Labels.positive(s.levelMin()),
            Labels.positive(s.levelMax()))).orElse(null)),
        link("Zone", r -> r.drop().map(DropSource::zoneName).orElse(null),
            r -> r.drop().flatMap(s -> zoneRef(s.zoneId()))),
        rate(DropRow::pct, r -> r.drop().map(DropSource::disagrees).orElse(false)
            || r.loot().map(InstanceLoot::disagrees).orElse(false)),
        plain("Origin", String.class, r -> r instanceof DropRow.Loot ? ATLASLOOT
            : r.drop().map(DropSource::origin).orElse(null)));
  }

  /**
   * Objects, containers, vendors and quests linked to an item, or the items related to a quest: an item in its
   * quality colour after its icon.
   */
  public static List<Column<ItemSource>> itemSources() {
    return List.of(
        itemLink("Name", ItemSource::displayName, s -> Optional.of(s.ref()), ItemSource::quality,
            s -> s.ref().kind() == EntityKind.ITEM ? iconOrFallback(s.iconId()) : null),
        plain("Kind", String.class, s -> sourceKindText(s.kind())));
  }

  /** Vendors or quests linked to an item, when a table holds one kind only: the name alone. */
  public static List<Column<ItemSource>> sourceNames() {
    return List.of(link("Name", ItemSource::displayName, s -> Optional.of(s.ref())));
  }

  /** Items an NPC drops or sells, an object contains or a quest rewards. */
  public static List<Column<LootRow>> lootRows() {
    return List.of(
        lootItem(),
        plain("Item level", Integer.class, LootRow::itemLevel),
        numeric("Drop %", l -> rateText(l.dropPct(), l.disagrees())),
        plain("Origin", String.class, LootRow::origin),
        plain(LOOT_ORDER, Integer.class, LootRow::slot));
  }

  /**
   * Items on the entity pages (an NPC's drops and wares, an object's contents, a quest's rewards, a boss's loot):
   * the item, the classes it is restricted to ({@value #CLASSES}, blank for most), its level, the rate (a
   * {@link Double}, null when unknown), the data origin and AtlasLoot's entry order ({@value #LOOT_ORDER}).
   */
  public static List<Column<LootRow>> lootRated() {
    return List.of(
        lootItem(),
        plain(CLASSES, String.class, l -> Labels.classes(l.allowableClass())),
        plain("Item level", Integer.class, LootRow::itemLevel),
        rate(LootRow::dropPct, LootRow::disagrees),
        plain("Origin", String.class, LootRow::origin),
        plain(LOOT_ORDER, Integer.class, LootRow::slot));
  }

  /** The NPCs, objects and items that start or end a quest; an item in its quality colour after its icon. */
  public static List<Column<QuestGiver>> questGivers() {
    return List.of(
        itemLink("Name", QuestGiver::displayName, g -> Optional.of(g.ref()), QuestGiver::quality,
            g -> g.ref().kind() == EntityKind.ITEM ? iconOrFallback(g.iconId()) : null),
        plain("Type", String.class, g -> g.ref().kind().label()),
        plain("Role", String.class, g -> roleText(g.role())));
  }

  /** Spawn points: the zone and the map coordinates ({@link #coordinatesText}). */
  public static List<Column<Spawn>> spawns() {
    return List.of(
        link("Zone", s -> s.zoneName() != null ? s.zoneName() : EntityKind.ZONE.unknownName(s.zoneId()),
            s -> zoneRef(s.zoneId())),
        plain("Coordinates", String.class, Columns::coordinatesText));
  }

  /** Whether a spawn point has map coordinates (instance spawns are imported at -1, -1). */
  public static boolean hasCoordinates(Spawn s) {
    return s.x() >= 0 && s.y() >= 0;
  }

  /** {@code "42.3, 67.4"}, or {@link #NONE} without {@linkplain #hasCoordinates coordinates}. */
  public static String coordinatesText(Spawn s) {
    return hasCoordinates(s) ? String.format(Locale.ROOT, "%.1f, %.1f", s.x(), s.y()) : NONE;
  }

  /** A drop chance as {@code "56.07 %"}, or {@link #NONE} when unknown. */
  public static String percentText(Double pct) {
    return pct == null ? NONE : String.format(Locale.ROOT, "%.2f %%", pct);
  }

  /** {@link #percentText} followed by {@link #DISAGREES} when the rate's sources disagree. */
  public static String rateText(Double pct, boolean disagrees) {
    return rateText(pct, false, disagrees);
  }

  /**
   * {@link #percentText}, then {@link #VANILLA} when the known rate is VMaNGOS's ({@code "12.50 % V"}), then
   * {@link #DISAGREES} when the rate's sources disagree ({@code "9.00 % V ≠"}).
   */
  public static String rateText(Double pct, boolean vanilla, boolean disagrees) {
    return percentText(pct) + (vanilla && pct != null ? VANILLA : "") + (disagrees ? DISAGREES : "");
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

  /** The factions an NPC is friendly to: A Alliance, H Horde, AH Both; null for a hostile NPC. */
  public static String friendlyText(String friendlyTo) {
    if (friendlyTo == null || friendlyTo.isBlank()) {
      return null;
    }
    String f = friendlyTo.strip().toUpperCase(Locale.ROOT);
    boolean alliance = f.contains("A");
    boolean horde = f.contains("H");
    return alliance && horde ? "Both" : alliance ? "Alliance" : horde ? "Horde" : friendlyTo.strip();
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

  /**
   * The {@value #RATE} column: the drop rate as a {@link Double} percentage (null when unknown), flagged where
   * its sources disagree (shown with {@link #DISAGREES} by the rate renderer).
   */
  private static <T> Column<T> rate(Function<T, Double> pct, Function<T, Boolean> disagrees) {
    return new Column<>(RATE, Double.class, pct::apply, null, null, null, disagrees);
  }

  /**
   * An item link column: the text coloured by the row's item quality, after the item's icon ({@code icon}: null
   * for none).
   */
  private static <T> Column<T> itemLink(String title, Function<T, Object> value,
      Function<T, Optional<EntityRef>> link, Function<T, Integer> quality, Function<T, Long> icon) {
    return new Column<>(title, String.class, value, link, null, quality, null, icon);
  }

  /** The Item column of loot rows: name in its quality colour after its icon. */
  private static Column<LootRow> lootItem() {
    return itemLink("Item", LootRow::displayName, l -> Optional.of(l.item()), LootRow::quality,
        l -> iconOrFallback(l.iconId()));
  }

  /** {@code iconId}, or the question mark for an item without one. */
  private static Long iconOrFallback(Long iconId) {
    return iconId != null ? iconId : AssetRepository.FALLBACK_ICON_ID;
  }
}
