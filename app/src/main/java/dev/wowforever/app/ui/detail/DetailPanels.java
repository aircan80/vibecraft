package dev.wowforever.app.ui.detail;

import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceDetail;
import dev.wowforever.app.model.ItemDetail;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.MapCoordinates;
import dev.wowforever.app.model.NpcDetail;
import dev.wowforever.app.model.ObjectDetail;
import dev.wowforever.app.model.QuestDetail;
import dev.wowforever.app.model.QuestGiver;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.model.RateSource;
import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.model.Spawn;
import dev.wowforever.app.model.SpawnPin;
import dev.wowforever.app.model.ZoneDetail;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.BooleanSetting;
import dev.wowforever.app.ui.StatusSink;
import dev.wowforever.app.ui.pages.InstancePage;
import dev.wowforever.app.ui.pages.ItemPage;
import dev.wowforever.app.ui.pages.NpcPage;
import dev.wowforever.app.ui.pages.ObjectPage;
import dev.wowforever.app.ui.pages.QuestPage;
import dev.wowforever.app.ui.pages.ZoneMapPanel;
import dev.wowforever.app.ui.pages.ZonePage;
import dev.wowforever.app.ui.widgets.ClipboardPort;
import dev.wowforever.app.ui.widgets.IconCache;
import dev.wowforever.app.ui.widgets.Images;
import dev.wowforever.app.ui.widgets.MapCache;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
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
   * (null when there is none), which the page's tooltip card shows; and the drop rate sources of its drops and loot
   * lists, which their rate tooltips list.
   */
  public record LoadedItem(ItemDetail detail, BufferedImage icon, Map<RateSource.Key, List<RateSource>> rateSources) {
    public LoadedItem {
      rateSources = Map.copyOf(rateSources);
    }
  }

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
   * coordinates, {@code mapZoneId} (both null without such a point; the map alone null when that zone has none); and
   * the drop rate sources of its drops, which their rate tooltips list.
   */
  public record LoadedNpc(NpcDetail detail, Long mapZoneId, BufferedImage map,
      Map<RateSource.Key, List<RateSource>> rateSources) {
    public LoadedNpc {
      rateSources = Map.copyOf(rateSources);
    }
  }

  /**
   * What {@link #load} returns for a dungeon or raid: the instance and the quests filed under its zone
   * ({@link dev.wowforever.app.db.QuestRepository#byZone}; none without a zone), which its page lists after the loot.
   */
  public record LoadedInstance(InstanceDetail detail, List<QuestSummary> quests) {
    public LoadedInstance {
      quests = List.copyOf(quests);
    }
  }

  /** What {@link #load} returns for an object: as {@link LoadedNpc}. */
  public record LoadedObject(ObjectDetail detail, Long mapZoneId, BufferedImage map) {}

  /**
   * What {@link #load} returns for a quest: the quest and the zone maps of its first start giver and first end giver
   * (NPC or object) with a spawn point on a zone map, each null without such a giver.
   */
  public record LoadedQuest(QuestDetail detail, QuestPage.GiverMap starts, QuestPage.GiverMap ends) {}

  /**
   * The detail record of {@code ref} ({@link LoadedItem}, {@link LoadedNpc}, {@link LoadedZone}, ...), or empty when
   * there is no such entity. Bosses have no page of their own (they are shown inside their instance), so they load
   * empty. An item's icon (or the question-mark fallback), a zone's map and the pins of its bosses and rares, the
   * map of an NPC's or object's first spawn zone and those of a quest's givers are read and decoded here, off the EDT
   * (maps through the {@linkplain MapCache#shared shared map cache}, so a map shown again is not decoded again).
   * The icon id is the summary's, which an item with only an {@code Item} row (no tooltip card) has too. The item
   * icons the page's tables show first are loaded into the {@linkplain IconCache#shared shared cache} here too:
   * an NPC's drops and wares, an object's contents, a quest's item givers, rewards and related items, an item's
   * containers, an instance's first boss's loot. So are the drop rate sources of the rates with any
   * ({@code sourceCount > 0}) of an item's drops and loot lists and an NPC's drops, in one query per page, which the
   * rate tooltips list (the instance page loads its bosses' as it shows them). An instance also loads the quests
   * filed under its zone ({@link LoadedInstance}).
   */
  public static Optional<Object> load(EntityRef ref, Repositories repos) {
    long id = ref.id();
    Optional<?> detail = switch (ref.kind()) {
      case ITEM -> repos.items().detail(id).map(d -> warmedSources(d, d.containedInItems()))
          .map(d -> new LoadedItem(d,
              repos.assets().iconOrFallback(iconId(d)).flatMap(Images::decode)
                  .map(image -> IconCache.scale(image, IconCache.CARD)).orElse(null),
              repos.items().sourcesOfItem(id, ratedNpcs(d))));
      case NPC -> repos.npcs().detail(id).map(d -> warmed(d, d.drops(), d.sells())).map(d -> {
        Long zone = mapZone(d.spawns());
        return new LoadedNpc(d, zone, zoneMap(zone, repos),
            repos.items().sourcesOfNpc(id, RateSource.ratedItems(d.drops())));
      });
      case OBJECT -> repos.objects().detail(id).map(d -> warmed(d, d.contains())).map(d -> {
        Long zone = mapZone(d.spawns());
        return new LoadedObject(d, zone, zoneMap(zone, repos));
      });
      case QUEST -> repos.quests().detail(id).map(d -> warmed(warmedSources(warmedGivers(d), d.relatedItems()),
              d.rewards()))
          .map(d -> new LoadedQuest(d, giverMap(d, "start", repos), giverMap(d, "end", repos)));
      case ZONE -> repos.zones().detail(id).map(d -> loadedZone(d, repos));
      case INSTANCE -> repos.instances().detail(id).map(d -> warmed(d, firstBossLoot(d, repos)))
          .map(d -> new LoadedInstance(d, d.summary().zoneId() == null ? List.of()
              : repos.quests().byZone(d.summary().zoneId())));
      case BOSS -> Optional.empty();
    };
    return detail.map(Object.class::cast);
  }

  /** The NPCs of item {@code d}'s drops and loot lists' bosses whose rate has sources. */
  private static List<Long> ratedNpcs(ItemDetail d) {
    List<Long> npcs = new ArrayList<>();
    d.droppedBy().stream().filter(s -> s.sourceCount() > 0).forEach(s -> npcs.add(s.ref().id()));
    d.inInstanceLoot().stream().filter(l -> l.sourceCount() > 0 && l.bossNpcId() != null)
        .forEach(l -> npcs.add(l.bossNpcId()));
    return npcs;
  }

  /** The zone with its decoded map and, when it has one, the pins of its bosses and rares. */
  private static LoadedZone loadedZone(ZoneDetail d, Repositories repos) {
    BufferedImage map = d.map().flatMap(MapCache.shared()::decoded).orElse(null);
    return new LoadedZone(d, map, map == null ? List.of() : repos.zones().npcSpawnsInZone(d.summary().id(), true));
  }

  /** The zone of the first spawn point {@linkplain MapCoordinates#onMap on the map}; null when none is. */
  private static Long mapZone(List<Spawn> spawns) {
    return spawns.stream().filter(s -> MapCoordinates.onMap(s.x(), s.y())).findFirst().map(Spawn::zoneId)
        .orElse(null);
  }

  /** The decoded map of zone {@code zoneId}; null for a null id, a zone without a map or one that does not decode. */
  private static BufferedImage zoneMap(Long zoneId, Repositories repos) {
    return zoneId == null ? null : repos.zones().map(zoneId).flatMap(MapCache.shared()::decoded).orElse(null);
  }

  /**
   * The zone map of quest {@code d}'s first giver in {@code role} ("start" or "end") that is an NPC or object with a
   * spawn point on the map of a zone that has one (its points in that zone); null when none has.
   */
  private static QuestPage.GiverMap giverMap(QuestDetail d, String role, Repositories repos) {
    for (QuestGiver g : d.givers()) {
      if (!role.equals(g.role()) || g.ref().kind() == EntityKind.ITEM) {
        continue;
      }
      List<Spawn> spawns = repos.spawnsOf(g.ref()).stream()
          .filter(s -> MapCoordinates.onMap(s.x(), s.y())).toList();
      for (long zone : spawns.stream().map(Spawn::zoneId).distinct().toList()) {
        BufferedImage map = zoneMap(zone, repos);
        if (map != null) {
          return new QuestPage.GiverMap(zone, map, spawns.stream().filter(s -> s.zoneId() == zone)
              .map(s -> new SpawnPin(g.ref(), g.name(), null, s.x(), s.y())).toList());
        }
      }
    }
    return null;
  }

  /** {@code detail}, once the {@value IconCache#ROW} px icons of the items in {@code lists} are cached. */
  @SafeVarargs
  private static <T> T warmed(T detail, List<LootRow>... lists) {
    for (List<LootRow> rows : lists) {
      IconCache.shared().warm(rows.stream().map(LootRow::iconId).toList(), IconCache.ROW);
    }
    return detail;
  }

  /** {@code detail}, once the {@value IconCache#ROW} px icons of the items among {@code sources} are cached. */
  private static <T> T warmedSources(T detail, List<ItemSource> sources) {
    IconCache.shared().warm(sources.stream().filter(s -> s.ref().kind() == EntityKind.ITEM)
        .map(ItemSource::iconId).toList(), IconCache.ROW);
    return detail;
  }

  /** {@code d}, once the {@value IconCache#ROW} px icons of its item givers are cached. */
  private static QuestDetail warmedGivers(QuestDetail d) {
    IconCache.shared().warm(d.givers().stream().filter(g -> g.ref().kind() == EntityKind.ITEM)
        .map(QuestGiver::iconId).toList(), IconCache.ROW);
    return d;
  }

  /**
   * The loot the instance page shows on opening: its {@linkplain InstancePage#firstBoss first boss}'s in the first
   * difficulty; empty without bosses or difficulties.
   */
  private static List<LootRow> firstBossLoot(InstanceDetail d, Repositories repos) {
    if (d.difficulties().isEmpty()) {
      return List.of();
    }
    return InstancePage.firstBoss(d).map(b -> repos.instances().bossLoot(b.id(), d.difficulties().get(0)))
        .orElse(List.of());
  }

  /** The summary's icon id, else the card's (null when neither has one). */
  private static Long iconId(ItemDetail d) {
    Long summary = d.summary().iconId();
    return summary != null ? summary : d.card().iconId();
  }

  /**
   * The page for a record from {@link #load}. The item and NPC pages show the drop rows {@code filter} keeps. Work a
   * page does once shown (a zone map loading more pins) runs on {@code worker} and reports to {@code status}; a zone
   * map's "Quests" toggle starts as {@code questPins} says and saves its changes there.
   */
  public static JComponent render(Object detail, Repositories repos, Navigator nav, Executor worker,
      StatusSink status, SourceFilter filter, BooleanSetting questPins) {
    return switch (detail) {
      case LoadedItem p -> new ItemPage(p.detail(), p.icon(), p.rateSources(), filter, nav);
      case LoadedNpc p -> new NpcPage(p.detail(), p.mapZoneId(), p.map(), p.rateSources(), filter, nav,
          ClipboardPort.system());
      case LoadedObject p -> new ObjectPage(p.detail(), p.mapZoneId(), p.map(), nav, ClipboardPort.system());
      case LoadedQuest p -> new QuestPage(p.detail(), p.starts(), p.ends(), nav);
      case LoadedZone p -> new ZonePage(p.detail(), p.map() == null ? null
          : new ZoneMapPanel(p.detail().summary().id(), p.map(), p.pins(), ZoneMapPanel.PinSource.of(repos.zones()),
              worker, status, nav, questPins), nav);
      case LoadedInstance p -> new InstancePage(p.detail(), p.quests(), repos, nav);
      default -> throw new IllegalArgumentException("No detail page for " + detail.getClass().getName());
    };
  }

  /** The display name of a record from {@link #load}. */
  public static String name(Object detail) {
    return switch (detail) {
      case LoadedItem p -> p.detail().summary().displayName();
      case LoadedNpc p -> p.detail().summary().displayName();
      case LoadedObject p -> p.detail().summary().displayName();
      case LoadedQuest p -> p.detail().summary().displayName();
      case LoadedZone p -> p.detail().summary().displayName();
      case LoadedInstance p -> p.detail().summary().displayName();
      default -> throw new IllegalArgumentException("No detail page for " + detail.getClass().getName());
    };
  }

  /** The detail shown for an entity the database does not have: {@code "Not found: <kind> #<id>"}. */
  public static JLabel notFound(EntityRef ref) {
    JLabel label = new JLabel("Not found: " + ref.kind().label() + " #" + ref.id(), SwingConstants.LEADING);
    label.setVerticalAlignment(SwingConstants.TOP);
    label.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    return label;
  }
}
