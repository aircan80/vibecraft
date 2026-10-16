package dev.wowforever.app.ui;

import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.ItemClassNode;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.ObjectSummary;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.model.SearchHit;
import dev.wowforever.app.model.ZoneSummary;
import dev.wowforever.app.nav.NavigationHistory;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.detail.DetailPanels;
import dev.wowforever.app.ui.widgets.ListEntries;
import dev.wowforever.app.ui.widgets.ListEntry;
import dev.wowforever.app.ui.widgets.RailTarget;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns view targets into master list contents and detail views. Every repository call runs on {@code worker}
 * through {@link Async}; the results are shown on the EDT, and a load that a newer one has overtaken is dropped.
 * All methods are called on the EDT.
 *
 * <p>Arrowing through the list previews rows: the first preview after a navigation takes a new history slot and
 * later ones reuse it ({@link NavigationHistory#replaceCurrent}), so twenty rows previewed are one back step. A
 * commit (Enter, a click) or any other navigation keeps that slot, and the next preview takes a new one. A row
 * already on screen is neither previewed nor committed again: its history slot is kept and nothing reloads.
 */
public final class AppController implements Navigator {
  /** Most search hits shown; item class lists show every item. */
  public static final int SEARCH_LIMIT = 200;
  private static final Logger LOG = LoggerFactory.getLogger(AppController.class);

  private final Repositories repos;
  private final MainView view;
  private final Executor worker;
  private final Map<String, String> meta;
  private final NavigationHistory history = new NavigationHistory();
  private int resultsGeneration;
  private int detailGeneration;
  /** Whether the current history slot was taken by a list preview, so the next preview may reuse it. */
  private boolean previewSlot;
  /** The kind (NPC or QUEST) whose zone list a committed zone row opens, while the list is a zone picker. */
  private EntityKind zonePicker;
  private volatile List<ItemClassNode> classTree; // cached on the worker
  private volatile List<ZoneSummary> zones; // cached on the worker

  /**
   * One list to show: its title and rows; {@code page} is the row limit of the query, 0 for none.
   * {@code zonePicker} is the kind a committed zone row lists, null for an ordinary list.
   */
  private record Listing(String title, List<ListEntry> entries, int page, EntityKind zonePicker) {
    Listing(String title, List<ListEntry> entries) {
      this(title, entries, 0, null);
    }

    /** Whether the rows fill the page, so the list probably goes on. */
    boolean capped() {
      return page > 0 && entries.size() >= page;
    }
  }

  public AppController(Repositories repos, MainView view, Executor worker) {
    this(repos, view, worker, Map.of());
  }

  /** {@code meta} is the database's meta table (build id, source commits), shown on the home page. */
  public AppController(Repositories repos, MainView view, Executor worker, Map<String, String> meta) {
    this.repos = Objects.requireNonNull(repos, "repos");
    this.view = Objects.requireNonNull(view, "view");
    this.worker = Objects.requireNonNull(worker, "worker");
    this.meta = Map.copyOf(meta);
    String buildId = meta.get("build_id");
    view.status().setInfo(buildId == null ? "" : "Database build " + buildId);
    view.masterList().onPreview(e -> preview(e.ref()));
    view.masterList().onCommit(e -> commit(e.ref()));
  }

  /** What a rail button opens. */
  public static ViewTarget target(RailTarget t) {
    return switch (t) {
      case HOME -> new ViewTarget.Home();
      case ITEMS -> new ViewTarget.ItemClass(ViewTarget.ItemClass.ALL_CLASSES, ItemClassNode.CLASS_NODE);
      case NPCS -> new ViewTarget.List(EntityKind.NPC);
      case QUESTS -> new ViewTarget.List(EntityKind.QUEST);
      case ZONES -> new ViewTarget.List(EntityKind.ZONE);
      case INSTANCES -> new ViewTarget.List(EntityKind.INSTANCE);
    };
  }

  /** Opens the home page. */
  public void start() {
    open(new ViewTarget.Home());
  }

  @Override
  public void open(ViewTarget t) {
    history.push(t);
    previewSlot = false;
    show(t);
  }

  /**
   * Shows {@code ref} in the detail area, leaving the list as it is; the history slot of an earlier preview is
   * reused, otherwise the preview takes a new one. Nothing happens when {@code ref} is already the current page
   * (say, opened through a link), so the next preview cannot overwrite a page the user opened on purpose.
   */
  public void preview(EntityRef ref) {
    ViewTarget t = new ViewTarget.Entity(ref);
    if (isCurrent(t)) {
      return;
    }
    if (previewSlot) {
      history.replaceCurrent(t);
    } else {
      history.push(t);
    }
    previewSlot = true;
    show(t);
  }

  /**
   * Opens a list row for good: it takes the slot of a pending preview (or a new one) and stays in the history.
   * A row already on screen (previewed, or clicked twice) is only kept in the history, not loaded again. A zone
   * row of a zone picker lists that zone's NPCs or quests instead.
   */
  public void commit(EntityRef ref) {
    if (zonePicker != null && ref.kind() == EntityKind.ZONE) {
      openZoneList(zonePicker, ref.id());
      return;
    }
    ViewTarget t = new ViewTarget.Entity(ref);
    if (isCurrent(t)) {
      previewSlot = false;
      return;
    }
    if (previewSlot) {
      history.replaceCurrent(t);
    } else {
      history.push(t);
    }
    previewSlot = false;
    show(t);
  }

  private boolean isCurrent(ViewTarget t) {
    return history.current().filter(t::equals).isPresent();
  }

  /** Re-shows the previous target, if any. */
  public void back() {
    history.back().ifPresent(t -> {
      previewSlot = false;
      show(t);
    });
  }

  /** Re-shows the next target, if any. */
  public void forward() {
    history.forward().ifPresent(t -> {
      previewSlot = false;
      show(t);
    });
  }

  /**
   * Opens zone {@code zoneId} (pushed to history, shown in the detail area) and lists its {@code kind}
   * (NPC, OBJECT or QUEST) entities in the master list.
   */
  public void openZoneList(EntityKind kind, long zoneId) {
    open(new ViewTarget.Entity(new EntityRef(EntityKind.ZONE, zoneId)));
    loadResults(() -> zoneList(kind, zoneId));
  }

  public NavigationHistory history() {
    return history;
  }

  /** The target on screen, or null before the first {@link #open}. */
  public ViewTarget current() {
    return history.current().orElse(null);
  }

  /** The repositories this controller reads. */
  public Repositories repos() {
    return repos;
  }

  /** The executor every repository call runs on. */
  public Executor worker() {
    return worker;
  }

  /** The database meta values this controller was given. */
  public Map<String, String> meta() {
    return meta;
  }

  private void show(ViewTarget t) {
    view.setHistoryState(history.canBack(), history.canForward());
    switch (t) {
      case ViewTarget.Search s -> loadResults(() -> search(s));
      case ViewTarget.ItemClass c -> loadResults(() -> itemClass(c));
      case ViewTarget.List l -> loadResults(() -> list(l.kind()));
      case ViewTarget.Entity e -> showEntity(e.ref());
      case ViewTarget.Home h -> showHome();
    }
  }

  private void loadResults(Supplier<Listing> work) {
    int generation = ++resultsGeneration;
    view.status().setMessage("Loading…");
    Async.run(worker, work, listing -> {
      if (generation == resultsGeneration) {
        zonePicker = listing.zonePicker();
        view.masterList().show(listing.title(), listing.entries(), listing.capped());
        view.setTitleSuffix(cappedTitle(listing.title(), listing.entries().size(), listing.page()));
        int n = listing.entries().size();
        view.status().setMessage(n == 1 ? "1 result" : String.format(Locale.ROOT, "%,d results", n));
      }
    }, e -> {
      if (generation == resultsGeneration) {
        fail(e);
      }
    });
  }

  /**
   * Shows the detail page of {@code ref}: the repository call runs on the worker ({@link DetailPanels#load}), the
   * panel is built on the EDT ({@link DetailPanels#render}).
   */
  private void showEntity(EntityRef ref) {
    int generation = ++detailGeneration;
    view.setTitleSuffix(ref.kind().label() + " #" + ref.id());
    Async.run(worker, () -> DetailPanels.load(ref, repos), detail -> {
      if (generation == detailGeneration) {
        if (detail.isPresent()) {
          view.setDetail(DetailPanels.render(detail.get(), repos, this));
          view.setTitleSuffix(DetailPanels.name(detail.get()));
        } else {
          view.setDetail(DetailPanels.notFound(ref));
        }
      }
    }, e -> {
      if (generation == detailGeneration) {
        fail(e);
      }
    });
  }

  private void showHome() {
    resultsGeneration++; // drop loads still in flight
    detailGeneration++;
    zonePicker = null;
    view.masterList().show("Results", List.of(), false);
    view.setDetail(welcome(meta));
    view.setTitleSuffix("");
    view.status().setMessage("Ready");
  }

  private void fail(Exception e) {
    LOG.warn("Loading failed", e);
    view.status().setMessage("Error: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
  }

  private Listing search(ViewTarget.Search s) {
    var hits = repos.search().search(s.text(), s.kind(), SEARCH_LIMIT);
    return new Listing("Search: " + s.text(), entries(hits, ListEntries::of), SEARCH_LIMIT, null);
  }

  private Listing itemClass(ViewTarget.ItemClass c) {
    if (c.classId() == ViewTarget.ItemClass.ALL_CLASSES) {
      return new Listing("All items", entries(repos.items().list(null, null, null, 0), AppController::entry));
    }
    Integer subclass = c.subclassId() == ItemClassNode.CLASS_NODE ? null : c.subclassId();
    var items = repos.items().list(c.classId(), subclass, null, 0);
    return new Listing(itemClassName(c.classId(), c.subclassId()), entries(items, AppController::entry));
  }

  /** {@code name}, marked {@code (first <page>)} when {@code rows} fills a page; unchanged when {@code page} is 0. */
  static String cappedTitle(String name, int rows, int page) {
    return page > 0 && rows >= page ? name + " (first " + page + ")" : name;
  }

  private String itemClassName(int classId, int subclassId) {
    if (classTree == null) {
      classTree = repos.items().classTree();
    }
    for (ItemClassNode cls : classTree) {
      if (cls.classId() != classId) {
        continue;
      }
      if (subclassId == ItemClassNode.CLASS_NODE) {
        return cls.name();
      }
      for (ItemClassNode sub : cls.children()) {
        if (sub.subclassId() == subclassId) {
          return cls.name() + " › " + sub.name();
        }
      }
      return cls.name() + " › #" + subclassId;
    }
    return "Item class #" + classId;
  }

  /** All zones or instances; for NPCs and quests the zones to pick one from (until the list pages exist). */
  private Listing list(EntityKind kind) {
    return switch (kind) {
      case ZONE -> new Listing("Zones", entries(repos.zones().all(), AppController::entry));
      case INSTANCE -> new Listing("Dungeons & Raids", instanceEntries(repos.instances().all()));
      case NPC, QUEST -> new Listing((kind == EntityKind.NPC ? "NPCs" : "Quests") + ": pick a zone",
          entries(repos.zones().all(), AppController::entry), 0, kind);
      default -> throw new IllegalArgumentException("There is no list of " + kind.label() + " entities");
    };
  }

  private Listing zoneList(EntityKind kind, long zoneId) {
    if (zones == null) {
      zones = repos.zones().all();
    }
    String name = zones.stream().filter(z -> z.id() == zoneId).findFirst()
        .map(ZoneSummary::displayName).orElse(EntityKind.ZONE.unknownName(zoneId));
    return switch (kind) {
      case NPC -> new Listing("NPCs in " + name, entries(repos.npcs().byZone(zoneId), AppController::entry));
      case OBJECT -> new Listing("Objects in " + name,
          entries(repos.objects().byZone(zoneId), AppController::entry));
      case QUEST -> new Listing("Quests in " + name, entries(repos.quests().byZone(zoneId), AppController::entry));
      default -> throw new IllegalArgumentException("There is no zone list of " + kind.label() + " entities");
    };
  }

  private static <T> List<ListEntry> entries(List<T> rows, Function<T, ListEntry> toEntry) {
    return rows.stream().map(toEntry).toList();
  }

  private static ListEntry entry(ItemSummary i) {
    return new ListEntry(i.ref(), i.displayName(), Labels.withKind(EntityKind.ITEM,
        Labels.itemSubtitle(i.className(), i.subclassName(), i.itemLevel(), i.requiredLevel())), null, null);
  }

  private static ListEntry entry(NpcSummary n) {
    return new ListEntry(n.ref(), n.displayName(), Labels.withKind(EntityKind.NPC,
        Labels.npcSubtitle(n.rank(), n.minLevel(), n.maxLevel(), n.zoneName())), null, null);
  }

  private static ListEntry entry(ObjectSummary o) {
    return new ListEntry(o.ref(), o.displayName(),
        Labels.withKind(EntityKind.OBJECT, Labels.objectSubtitle(o.zoneName())), null, null);
  }

  private static ListEntry entry(QuestSummary q) {
    return new ListEntry(q.ref(), q.displayName(),
        Labels.withKind(EntityKind.QUEST, Labels.questSubtitle(q.questLevel(), q.zoneName())), null, null);
  }

  private static ListEntry entry(ZoneSummary z) {
    return new ListEntry(z.ref(), z.displayName(), Labels.zoneSubtitle(z.kind(), z.parentName()), null, null);
  }

  /** Instance entries; an instance whose name another one in {@code instances} shares is titled with its wing. */
  static List<ListEntry> instanceEntries(List<InstanceSummary> instances) {
    Map<String, Long> perName = instances.stream().filter(i -> i.name() != null)
        .collect(Collectors.groupingBy(InstanceSummary::name, Collectors.counting()));
    return instances.stream().map(i -> entry(i, perName.getOrDefault(i.name(), 0L) > 1)).toList();
  }

  private static ListEntry entry(InstanceSummary i, boolean sharedName) {
    // an instance's own zone usually carries its name; repeating it says nothing
    String zone = Objects.equals(i.zoneName(), i.name()) ? null : i.zoneName();
    String title = sharedName ? Labels.instanceTitle(i.displayName(), i.key()) : i.displayName();
    return new ListEntry(i.ref(), title,
        Labels.instanceSubtitle(i.contentType(), i.levelMin(), i.levelMax(), zone), null, null);
  }

  /** The home page text. */
  static JComponent welcome(Map<String, String> meta) {
    String build = meta.get("build_id");
    return placeholder("<html><h2>WoW Forever Item &amp; Drop Browser</h2>"
        + "<p>Search with the box above (Ctrl+F) or browse with the icons on the left.</p>"
        + "<p>Arrow through the list to preview a row here; Enter or a click opens it.<br>"
        + "Ctrl+B hides or shows the list. Alt+Left and Alt+Right go back and forward.</p>"
        + (build == null ? "" : "<p>Database build " + build + "</p>") + "</html>");
  }

  private static JLabel placeholder(String text) {
    JLabel label = new JLabel(text, SwingConstants.LEADING);
    label.setVerticalAlignment(SwingConstants.TOP);
    label.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    return label;
  }
}
