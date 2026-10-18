package dev.wowforever.app.ui.detail;

import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.GiverSpot;
import dev.wowforever.app.model.InstanceDetail;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.ItemDetail;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.MapCoordinates;
import dev.wowforever.app.model.NpcDetail;
import dev.wowforever.app.model.ObjectDetail;
import dev.wowforever.app.model.QuestDetail;
import dev.wowforever.app.model.QuestPageData;
import dev.wowforever.app.model.QuestGiver;
import dev.wowforever.app.model.QuestInvolvement;
import dev.wowforever.app.model.QuestPageData;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.model.RateSource;
import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.model.Spawn;
import dev.wowforever.app.model.Route;
import dev.wowforever.app.model.SpawnPin;
import dev.wowforever.app.model.ZoneDetail;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.BooleanSetting;
import dev.wowforever.app.ui.StatusSink;
import dev.wowforever.app.ui.pages.BossLoot;
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
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
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
   * (null when there is none), which the page's tooltip card shows; the drop rate sources of its drops and loot
   * lists, which their rate tooltips list; and which of the NPCs with sources are dungeon or raid bosses
   * ({@link dev.wowforever.app.db.NpcRepository#bosses}), whose VMaNGOS rates say {@value RateSource#BOSS_NOTE}.
   */
  public record LoadedItem(ItemDetail detail, BufferedImage icon, Map<RateSource.Key, List<RateSource>> rateSources,
      Set<Long> bossNpcs, QuestInvolvement.Result involvement, List<dev.wowforever.app.model.SourceDifference> sourceDifferences,
      List<dev.wowforever.app.model.FieldKnowledgeView> knowledge, dev.wowforever.app.db.ItemContentRepository.Appearance appearance,
      dev.wowforever.app.model.ForeverContent.Context foreverContext, dev.wowforever.app.model.NativeItemMetadata nativeMetadata,
      dev.wowforever.app.db.ItemRepository.ClassicComparison classicComparison) {
    public LoadedItem(ItemDetail detail, BufferedImage icon, Map<RateSource.Key, List<RateSource>> rateSources,
        Set<Long> bossNpcs, QuestInvolvement.Result involvement, List<dev.wowforever.app.model.SourceDifference> sourceDifferences,
        List<dev.wowforever.app.model.FieldKnowledgeView> knowledge, dev.wowforever.app.db.ItemContentRepository.Appearance appearance,
        dev.wowforever.app.model.ForeverContent.Context foreverContext, dev.wowforever.app.model.NativeItemMetadata nativeMetadata) {
      this(detail,icon,rateSources,bossNpcs,involvement,sourceDifferences,knowledge,appearance,foreverContext,nativeMetadata,null);
    }
    public LoadedItem(ItemDetail detail, BufferedImage icon, Map<RateSource.Key, List<RateSource>> rateSources,
        Set<Long> bossNpcs, QuestInvolvement.Result involvement, List<dev.wowforever.app.model.SourceDifference> sourceDifferences,
        List<dev.wowforever.app.model.FieldKnowledgeView> knowledge, dev.wowforever.app.db.ItemContentRepository.Appearance appearance,
        dev.wowforever.app.model.ForeverContent.Context foreverContext) {
      this(detail,icon,rateSources,bossNpcs,involvement,sourceDifferences,knowledge,appearance,foreverContext,
          dev.wowforever.app.model.NativeItemMetadata.empty());
    }
    public LoadedItem(ItemDetail detail, BufferedImage icon, Map<RateSource.Key, List<RateSource>> rateSources,
        Set<Long> bossNpcs, QuestInvolvement.Result involvement, List<dev.wowforever.app.model.SourceDifference> sourceDifferences) {
      this(detail,icon,rateSources,bossNpcs,involvement,sourceDifferences,List.of(),null,dev.wowforever.app.model.ForeverContent.Context.empty(),dev.wowforever.app.model.NativeItemMetadata.empty());
    }
    public LoadedItem(ItemDetail detail, BufferedImage icon, Map<RateSource.Key, List<RateSource>> rateSources,
        Set<Long> bossNpcs, QuestInvolvement.Result involvement) {
      this(detail,icon,rateSources,bossNpcs,involvement,List.of());
    }
    public LoadedItem(ItemDetail detail, BufferedImage icon, Map<RateSource.Key, List<RateSource>> rateSources,
        Set<Long> bossNpcs) {
      this(detail, icon, rateSources, bossNpcs, QuestInvolvement.fromRelated(detail.relatedQuests()));
    }

    public LoadedItem {
      knowledge = List.copyOf(knowledge);
      sourceDifferences = List.copyOf(sourceDifferences);
      rateSources = Map.copyOf(rateSources);
      bossNpcs = Set.copyOf(bossNpcs);
    }
  }

  /**
   * What {@link #load} returns for a zone: the zone, its decoded map (null: none, or it does not decode) and the
   * spawn points of its bosses and rares, which the map pins at first (empty without a map).
   */
  public record LoadedZone(ZoneDetail detail, BufferedImage map, List<SpawnPin> pins,
      List<dev.wowforever.app.model.GeographyStep> breadcrumbs, dev.wowforever.app.model.TravelMapData travel,
      dev.wowforever.app.model.ForeverContent.Context foreverContext) {
    public LoadedZone(ZoneDetail detail,BufferedImage map,List<SpawnPin> pins,
        List<dev.wowforever.app.model.GeographyStep> breadcrumbs,dev.wowforever.app.model.TravelMapData travel) {
      this(detail,map,pins,breadcrumbs,travel,dev.wowforever.app.model.ForeverContent.Context.empty());
    }
    public LoadedZone(ZoneDetail detail, BufferedImage map, List<SpawnPin> pins,
        List<dev.wowforever.app.model.GeographyStep> breadcrumbs) {
      this(detail,map,pins,breadcrumbs,dev.wowforever.app.model.TravelMapData.EMPTY);
    }
    public LoadedZone {
      pins = List.copyOf(pins);
      breadcrumbs = List.copyOf(breadcrumbs);
    }
    public LoadedZone(ZoneDetail detail, BufferedImage map, List<SpawnPin> pins) {
      this(detail,map,pins,List.of());
    }
  }

  /**
   * What {@link #load} returns for an NPC: the NPC and the decoded map of the zone of its first spawn point with
   * coordinates, {@code mapZoneId} (both null without such a point; the map alone null when that zone has none); the
   * drop rate sources of its drops, which their rate tooltips list; and its id when it is a dungeon or raid boss
   * ({@link dev.wowforever.app.db.NpcRepository#bosses}; asked only when a drop has sources), whose VMaNGOS rates say
   * {@value RateSource#BOSS_NOTE}.
   */
  public record LoadedNpc(NpcDetail detail, Long mapZoneId, BufferedImage map,
      Map<RateSource.Key, List<RateSource>> rateSources, Set<Long> bossNpcs, List<Route> routes,
      QuestInvolvement.Result involvement, dev.wowforever.app.db.NpcPortraits.Portrait portrait,
      dev.wowforever.app.model.NpcAppearance appearance) {
    public LoadedNpc(NpcDetail detail, Long mapZoneId, BufferedImage map,
        Map<RateSource.Key, List<RateSource>> rateSources, Set<Long> bossNpcs, List<Route> routes,
        QuestInvolvement.Result involvement, dev.wowforever.app.db.NpcPortraits.Portrait portrait) {
      this(detail,mapZoneId,map,rateSources,bossNpcs,routes,involvement,portrait,null);
    }
    public LoadedNpc(NpcDetail detail, Long mapZoneId, BufferedImage map,
        Map<RateSource.Key, List<RateSource>> rateSources, Set<Long> bossNpcs, List<Route> routes,
        QuestInvolvement.Result involvement) {
      this(detail,mapZoneId,map,rateSources,bossNpcs,routes,involvement,null);
    }
    public LoadedNpc(NpcDetail detail, Long mapZoneId, BufferedImage map,
        Map<RateSource.Key, List<RateSource>> rateSources, Set<Long> bossNpcs) {
      this(detail, mapZoneId, map, rateSources, bossNpcs, List.of(),
          QuestInvolvement.fromGivers(detail.starts(), detail.ends()));
    }
    public LoadedNpc(NpcDetail detail, Long mapZoneId, BufferedImage map,
        Map<RateSource.Key, List<RateSource>> rateSources, Set<Long> bossNpcs, List<Route> routes) {
      this(detail, mapZoneId, map, rateSources, bossNpcs, routes,
          QuestInvolvement.fromGivers(detail.starts(), detail.ends()));
    }
    public LoadedNpc(NpcDetail detail, Long mapZoneId, BufferedImage map,
        Map<RateSource.Key, List<RateSource>> rateSources, Set<Long> bossNpcs, QuestInvolvement.Result involvement) {
      this(detail, mapZoneId, map, rateSources, bossNpcs, List.of(), involvement);
    }
    public LoadedNpc {
      routes = List.copyOf(routes);
      rateSources = Map.copyOf(rateSources);
      bossNpcs = Set.copyOf(bossNpcs);
    }
  }

  /**
   * What {@link #load} returns for a dungeon or raid: the instance and the quests filed under its zone
   * ({@link dev.wowforever.app.db.QuestRepository#byZone}; none without a zone), which its page lists after the loot.
   */
  public record LoadedInstance(InstanceDetail detail, List<QuestSummary> quests, String clientBuild,
      BossLoot clientLoot, List<dev.wowforever.app.model.GeographyStep> breadcrumbs,
      dev.wowforever.app.ui.pages.InstanceEntrancePanel.Loaded access,
      dev.wowforever.app.model.ForeverContent.Context foreverContext,
      List<dev.wowforever.app.model.InstanceFloorMap> floorMaps) {
    public LoadedInstance(InstanceDetail detail,List<QuestSummary> quests,String clientBuild,BossLoot clientLoot,
        List<dev.wowforever.app.model.GeographyStep> breadcrumbs,dev.wowforever.app.ui.pages.InstanceEntrancePanel.Loaded access) {
      this(detail,quests,clientBuild,clientLoot,breadcrumbs,access,dev.wowforever.app.model.ForeverContent.Context.empty(),List.of());
    }
    public LoadedInstance(InstanceDetail detail,List<QuestSummary> quests,String clientBuild,BossLoot clientLoot,
        List<dev.wowforever.app.model.GeographyStep> breadcrumbs) {
      this(detail,quests,clientBuild,clientLoot,breadcrumbs,dev.wowforever.app.ui.pages.InstanceEntrancePanel.Loaded.EMPTY);
    }
    public LoadedInstance(InstanceDetail detail, List<QuestSummary> quests, String clientBuild, BossLoot clientLoot) {
      this(detail,quests,clientBuild,clientLoot,List.of());
    }
    public LoadedInstance(InstanceDetail detail, List<QuestSummary> quests) {
      this(detail, quests, "unknown", null);
    }

    public LoadedInstance(InstanceDetail detail, List<QuestSummary> quests, String clientBuild) {
      this(detail, quests, clientBuild, null);
    }

    public LoadedInstance {
      floorMaps = List.copyOf(floorMaps);
      quests = List.copyOf(quests);
      breadcrumbs = List.copyOf(breadcrumbs);
    }
  }

  /** What {@link #load} returns for an object: as {@link LoadedNpc}. */
  public record LoadedObject(ObjectDetail detail, Long mapZoneId, BufferedImage map,
      QuestInvolvement.Result involvement) {
    public LoadedObject(ObjectDetail detail, Long mapZoneId, BufferedImage map) {
      this(detail, mapZoneId, map, QuestInvolvement.fromGivers(detail.starts(), detail.ends()));
    }
  }

  /**
   * What {@link #load} returns for a quest: all page data and its decoded zone maps, loaded off the EDT.
   */
  public record LoadedQuest(QuestPageData data, Map<Long, BufferedImage> maps, Map<Long, String> zoneNames) {
    public LoadedQuest(QuestPageData data, Map<Long, BufferedImage> maps) { this(data, maps, Map.of()); }
    public LoadedQuest {
      maps = Map.copyOf(maps);
      zoneNames = Map.copyOf(zoneNames);
    }

    public QuestDetail detail() {
      return data.detail();
    }
  }

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
   * rate tooltips list (the instance page loads its bosses' as it shows them), and which of those NPCs are dungeon or
   * raid bosses ({@code bossNpcs}: their VMaNGOS rates say {@value RateSource#BOSS_NOTE}). An instance also loads the quests
   * filed under its zone ({@link LoadedInstance}).
   */
  public static Optional<Object> load(EntityRef ref, Repositories repos) {
    long id = ref.id();
    Optional<?> detail = switch (ref.kind()) {
      case ITEM -> repos.items().detail(id).map(d -> warmedSources(d, d.containedInItems())).map(d -> {
        List<Long> rated = ratedNpcs(d);
        return new LoadedItem(d,
            repos.assets().iconOrFallback(iconId(d)).flatMap(Images::decode)
                .map(image -> IconCache.scale(image, IconCache.CARD)).orElse(null),
            repos.items().sourcesOfItem(id, rated), repos.npcs().bosses(rated), repos.quests().involvingResult(ref),
            repos.items().sourceDifferences(id),repos.items().knowledge(id),repos.items().appearance(id),repos.items().foreverContext(id),repos.items().nativeMetadata(id),repos.items().classicComparison(id).orElse(null));
      });
      case NPC -> repos.npcs().detail(id).map(d -> warmed(d, d.drops(), d.sells())).map(d -> {
        Long zone = mapZone(d.spawns());
        List<Long> rated = RateSource.ratedItems(d.drops());
        return new LoadedNpc(d, zone, zoneMap(zone, repos), repos.items().sourcesOfNpc(id, rated),
            rated.isEmpty() ? Set.of() : repos.npcs().bosses(List.of(id)), repos.npcs().routes(id),
            repos.quests().involvingResult(ref),
            null,
            new dev.wowforever.app.db.NpcAppearanceRepository(repos.database()).resolve(id));
      });
      case OBJECT -> repos.objects().detail(id).map(d -> warmed(d, d.contains())).map(d -> {
        Long zone = mapZone(d.spawns());
        return new LoadedObject(d, zone, zoneMap(zone, repos), repos.quests().involvingResult(ref));
      });
      case QUEST -> repos.questLocator().load(id).map(data -> {
        QuestDetail d = data.detail();
        warmed(warmedSources(warmedGivers(d), d.relatedItems()), d.rewards());
        Map<Long, BufferedImage> maps = new java.util.LinkedHashMap<>();
        for (long zone : data.mapZones()) {
          BufferedImage map = zoneMap(zone, repos);
          if (map != null) {
            maps.put(zone, map);
          }
        }
        return new LoadedQuest(data, maps, repos.zones().names(data.mapZones()));
      });
      case ZONE -> repos.zones().detail(id).map(d -> loadedZone(d, repos));
      case INSTANCE -> repos.instances().detail(id)
          .map(d -> new LoadedInstance(d, d.summary().zoneId() == null ? List.of()
              : repos.quests().byZone(d.summary().zoneId()), repos.instances().clientBuild(),
              BossLoot.preloaded(d, repos), instanceBreadcrumbs(d,repos),
              dev.wowforever.app.ui.pages.InstanceEntrancePanel.load(repos.travel(),d.summary().id()),
              new dev.wowforever.app.db.ForeverContentRepository(repos.database()).instanceContext(d.summary().key()),
              new dev.wowforever.app.db.InstanceMapRepository(repos.database()).load(d.summary().id())));
      case BOSS -> Optional.empty();
    };
    return detail.map(Object.class::cast);
  }

  private static List<dev.wowforever.app.model.GeographyStep> instanceBreadcrumbs(InstanceDetail d, Repositories repos) {
    var path=new ArrayList<dev.wowforever.app.model.GeographyStep>();
    if(d.summary().zoneId()!=null)path.addAll(repos.zones().breadcrumbs(d.summary().zoneId()));
    String name=d.summary().displayName();
    if(!path.isEmpty() && path.getLast().name().equalsIgnoreCase(name))path.removeLast();
    path.add(new dev.wowforever.app.model.GeographyStep(name,d.summary().id(),dev.wowforever.app.model.GeographyStep.Kind.INSTANCE));
    return List.copyOf(path);
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
    return new LoadedZone(d, map, map == null ? List.of() : repos.zones().npcSpawnsInZone(d.summary().id(), true),
        repos.zones().breadcrumbs(d.summary().id()), repos.travel().inZone(d.summary().id()),
        new dev.wowforever.app.db.ForeverContentRepository(repos.database()).zoneContext(d.summary().id()));
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
    return render(detail, repos, nav, worker, status, filter, f -> { }, questPins);
  }

  /**
   * {@link #render(Object, Repositories, Navigator, Executor, StatusSink, SourceFilter, BooleanSetting)}, the item
   * and NPC pages' filter control handing a new filter to {@code onFilterChange}.
   */
  public static JComponent render(Object detail, Repositories repos, Navigator nav, Executor worker,
      StatusSink status, SourceFilter filter, Consumer<SourceFilter> onFilterChange, BooleanSetting questPins) {
    return render(detail, repos, nav, worker, status, filter, onFilterChange, questPins,
        BooleanSetting.inMemory(false));
  }

  /** Renders a page with the user's remembered quest-pin and patrol-route settings. */
  public static JComponent render(Object detail, Repositories repos, Navigator nav, Executor worker,
      StatusSink status, SourceFilter filter, Consumer<SourceFilter> onFilterChange, BooleanSetting questPins,
      BooleanSetting routes) {
    return switch (detail) {
      case LoadedItem p -> {
        var page = new ItemPage(p.detail(), p.icon(), p.rateSources(), p.bossNpcs(), filter, onFilterChange,
            p.involvement(), nav, ClipboardPort.system(), p.sourceDifferences(),p.knowledge(),p.appearance(),p.foreverContext(),p.nativeMetadata());
        page.setClassicComparison(p.classicComparison());
        yield page;
      }
      case LoadedNpc p -> new NpcPage(p.detail(), p.mapZoneId(), p.map(), p.rateSources(), p.bossNpcs(), filter,
          onFilterChange, p.involvement(), nav, ClipboardPort.system(), p.routes());
      case LoadedObject p -> new ObjectPage(p.detail(), p.mapZoneId(), p.map(), p.involvement(), nav, ClipboardPort.system());
      case LoadedQuest p -> new QuestPage(p.data(), p.maps(), p.zoneNames(), nav, ClipboardPort.system());
      case LoadedZone p -> new ZonePage(p.detail(), p.map() == null ? null
          : new ZoneMapPanel(p.detail().summary().id(), p.map(), p.pins(), ZoneMapPanel.PinSource.of(repos.zones()),
              worker, status, nav, questPins, routes, p.travel()), p.breadcrumbs(), p.foreverContext(), nav);
      case LoadedInstance p -> {
        var page = new InstancePage(p.detail(),p.quests(),repos,p.clientBuild(),p.clientLoot(),p.breadcrumbs(),nav);
        page.setEntrance(p.access());
        page.setForeverContext(p.foreverContext());
        page.addFloorMaps(p.floorMaps());
        yield page;
      }
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
