package dev.wowforever.app.ui.detail;

import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceDetail;
import dev.wowforever.app.model.ItemDetail;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.NpcDetail;
import dev.wowforever.app.model.ObjectDetail;
import dev.wowforever.app.model.QuestDetail;
import dev.wowforever.app.model.QuestGiver;
import dev.wowforever.app.model.Spawn;
import dev.wowforever.app.model.SpawnPin;
import dev.wowforever.app.model.ZoneDetail;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Async;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.pages.InstancePage;
import dev.wowforever.app.ui.pages.ItemPage;
import dev.wowforever.app.ui.pages.NpcPage;
import dev.wowforever.app.ui.pages.ObjectPage;
import dev.wowforever.app.ui.pages.QuestPage;
import dev.wowforever.app.ui.pages.ZonePage;
import dev.wowforever.app.ui.widgets.ClipboardPort;
import dev.wowforever.app.ui.widgets.IconCache;
import dev.wowforever.app.ui.widgets.Images;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * Builds entity detail pages in two steps: {@link #load} queries the repository (call it off the EDT), and
 * {@link #render} builds the panel from what it returned (call it on the EDT).
 */
public final class DetailPanels {
  private DetailPanels() {}

  /**
   * What {@link #load} returns for an item: the item and its icon, decoded and scaled to {@value IconCache#CARD} px
   * (null when there is none), which the page's tooltip card shows.
   */
  public record LoadedItem(ItemDetail detail, BufferedImage icon) {}

  /**
   * What {@link #load} returns for a zone: the zone, its decoded map (null: none, or it does not decode) and the
   * spawn points of its bosses and rares, which the map pins at first (empty without a map).
   */
  public record LoadedZone(ZoneDetail detail, BufferedImage map, List<SpawnPin> pins) {
    public LoadedZone {
      pins = List.copyOf(pins);
    }
  }

  /**
   * What {@link #load} returns for an NPC: the NPC and the decoded map of the zone of its first spawn point with
   * coordinates, {@code mapZoneId} (both null without such a point; the map alone null when that zone has none).
   */
  public record LoadedNpc(NpcDetail detail, Long mapZoneId, BufferedImage map) {}

  /** What {@link #load} returns for an object: as {@link LoadedNpc}. */
  public record LoadedObject(ObjectDetail detail, Long mapZoneId, BufferedImage map) {}

  /**
   * The detail record of {@code ref} ({@link LoadedItem}, {@link LoadedNpc}, {@link LoadedZone}, ...), or empty when
   * there is no such entity. Bosses have no page of their own (they are shown inside their instance), so they load
   * empty. An item's icon (or the question-mark fallback), a zone's map and the pins of its bosses and rares, and
   * the map of an NPC's or object's first spawn zone are read and decoded here, off the EDT.
   * The icon id is the summary's, which an item with only an {@code Item} row (no tooltip card) has too. The item
   * icons the page's tables show first are loaded into the {@linkplain IconCache#shared shared cache} here too:
   * an NPC's drops and wares, an object's contents, a quest's item givers and rewards, an instance's first boss's
   * loot.
   */
  public static Optional<Object> load(EntityRef ref, Repositories repos) {
    long id = ref.id();
    Optional<?> detail = switch (ref.kind()) {
      case ITEM -> repos.items().detail(id).map(d -> new LoadedItem(d,
          repos.assets().iconOrFallback(iconId(d)).flatMap(Images::decode)
              .map(image -> IconCache.scale(image, IconCache.CARD)).orElse(null)));
      case NPC -> repos.npcs().detail(id).map(d -> warmed(d, d.drops(), d.sells())).map(d -> {
        Long zone = mapZone(d.spawns());
        return new LoadedNpc(d, zone, zoneMap(zone, repos));
      });
      case OBJECT -> repos.objects().detail(id).map(d -> warmed(d, d.contains())).map(d -> {
        Long zone = mapZone(d.spawns());
        return new LoadedObject(d, zone, zoneMap(zone, repos));
      });
      case QUEST -> repos.quests().detail(id).map(d -> warmed(warmedGivers(d), d.rewards()));
      case ZONE -> repos.zones().detail(id).map(d -> loadedZone(d, repos));
      case INSTANCE -> repos.instances().detail(id).map(d -> warmed(d, firstBossLoot(d, repos)));
      case BOSS -> Optional.empty();
    };
    return detail.map(Object.class::cast);
  }

  /** The zone with its decoded map and, when it has one, the pins of its bosses and rares. */
  private static LoadedZone loadedZone(ZoneDetail d, Repositories repos) {
    BufferedImage map = d.map().flatMap(m -> Images.decode(m.jpeg())).orElse(null);
    return new LoadedZone(d, map, map == null ? List.of() : repos.zones().npcSpawnsInZone(d.summary().id(), true));
  }

  /** The zone of the first spawn point with coordinates; null when none has any. */
  private static Long mapZone(List<Spawn> spawns) {
    return spawns.stream().filter(Columns::hasCoordinates).findFirst().map(Spawn::zoneId).orElse(null);
  }

  /** The decoded map of zone {@code zoneId}; null for a null id, a zone without a map or one that does not decode. */
  private static BufferedImage zoneMap(Long zoneId, Repositories repos) {
    return zoneId == null ? null : repos.zones().map(zoneId).flatMap(m -> Images.decode(m.jpeg())).orElse(null);
  }

  /** {@code detail}, once the {@value IconCache#ROW} px icons of the items in {@code lists} are cached. */
  @SafeVarargs
  private static <T> T warmed(T detail, List<LootRow>... lists) {
    for (List<LootRow> rows : lists) {
      IconCache.shared().warm(rows.stream().map(LootRow::iconId).toList(), IconCache.ROW);
    }
    return detail;
  }

  /** {@code d}, once the {@value IconCache#ROW} px icons of its item givers are cached. */
  private static QuestDetail warmedGivers(QuestDetail d) {
    IconCache.shared().warm(d.givers().stream().filter(g -> g.ref().kind() == EntityKind.ITEM)
        .map(QuestGiver::iconId).toList(), IconCache.ROW);
    return d;
  }

  /**
   * The loot the instance page shows on opening: the first main encounter's (else the first extra list's) in the
   * first difficulty, as {@link InstancePage} selects them; empty without bosses or difficulties.
   */
  private static List<LootRow> firstBossLoot(InstanceDetail d, Repositories repos) {
    if (d.difficulties().isEmpty()) {
      return List.of();
    }
    return d.bosses().stream().filter(b -> !b.extraList()).findFirst()
        .or(() -> d.bosses().stream().findFirst())
        .map(b -> repos.instances().bossLoot(b.id(), d.difficulties().get(0)))
        .orElse(List.of());
  }

  /** The summary's icon id, else the card's (null when neither has one). */
  private static Long iconId(ItemDetail d) {
    Long summary = d.summary().iconId();
    return summary != null ? summary : d.card().iconId();
  }

  /** The page for a record from {@link #load}. */
  public static JComponent render(Object detail, Repositories repos, Navigator nav) {
    return switch (detail) {
      case LoadedItem p -> new ItemPage(p.detail(), p.icon(), nav);
      case LoadedNpc p -> new NpcPage(p.detail(), p.mapZoneId(), p.map(), nav, ClipboardPort.system());
      case LoadedObject p -> new ObjectPage(p.detail(), p.mapZoneId(), p.map(), nav, ClipboardPort.system());
      case QuestDetail d -> new QuestPage(d, nav);
      case LoadedZone p -> new ZonePage(p.detail(), p.map(), p.pins(), ZonePage.PinSource.of(repos.zones()),
          Async.SWING_WORKER, nav);
      case InstanceDetail d -> new InstancePage(d, repos, nav);
      default -> throw new IllegalArgumentException("No detail page for " + detail.getClass().getName());
    };
  }

  /** The display name of a record from {@link #load}. */
  public static String name(Object detail) {
    return switch (detail) {
      case LoadedItem p -> p.detail().summary().displayName();
      case LoadedNpc p -> p.detail().summary().displayName();
      case LoadedObject p -> p.detail().summary().displayName();
      case QuestDetail d -> d.summary().displayName();
      case LoadedZone p -> p.detail().summary().displayName();
      case InstanceDetail d -> d.summary().displayName();
      default -> throw new IllegalArgumentException("No detail page for " + detail.getClass().getName());
    };
  }

  /** {@link #load} then {@link #render} on the calling thread, or {@link #notFound} for a missing entity. */
  public static JComponent build(EntityRef ref, Repositories repos, Navigator nav) {
    return load(ref, repos).map(d -> render(d, repos, nav)).orElseGet(() -> notFound(ref));
  }

  /** The detail shown for an entity the database does not have: {@code "Not found: <kind> #<id>"}. */
  public static JLabel notFound(EntityRef ref) {
    JLabel label = new JLabel("Not found: " + ref.kind().label() + " #" + ref.id(), SwingConstants.LEADING);
    label.setVerticalAlignment(SwingConstants.TOP);
    label.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    return label;
  }
}
