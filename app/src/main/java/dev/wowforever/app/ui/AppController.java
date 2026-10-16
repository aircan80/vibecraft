package dev.wowforever.app.ui;

import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ItemClassNode;
import dev.wowforever.app.model.ZoneSummary;
import dev.wowforever.app.nav.NavigationHistory;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.detail.DetailPanels;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns view targets into results and detail views. Every repository call runs on {@code worker} through
 * {@link Async}; the results are shown on the EDT, and a load that a newer one has overtaken is dropped.
 * All methods are called on the EDT.
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
  private volatile List<ItemClassNode> classTree; // cached on the worker
  private volatile List<ZoneSummary> zones; // cached on the worker

  /** One table to show: its title, columns and rows; {@code page} is the row limit of the query, 0 for none. */
  private record Table<T>(String title, List<Columns.Column<T>> columns, List<T> rows, int page) {
    Table(String title, List<Columns.Column<T>> columns, List<T> rows) {
      this(title, columns, rows, 0);
    }

    ResultsTableModel<T> model() {
      ResultsTableModel<T> m = new ResultsTableModel<>(columns);
      m.setRows(rows);
      return m;
    }

    /** Whether the rows fill the page, so the list probably goes on. */
    boolean capped() {
      return page > 0 && rows.size() >= page;
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
  }

  /** Opens the home page. */
  public void start() {
    open(new ViewTarget.Home());
  }

  @Override
  public void open(ViewTarget t) {
    history.push(t);
    show(t);
  }

  /** Re-shows the previous target, if any. */
  public void back() {
    history.back().ifPresent(this::show);
  }

  /** Re-shows the next target, if any. */
  public void forward() {
    history.forward().ifPresent(this::show);
  }

  /**
   * Opens zone {@code zoneId} (pushed to history, shown in the detail area) and lists its {@code kind}
   * (NPC, OBJECT or QUEST) entities in the results table.
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

  private void loadResults(Supplier<Table<?>> work) {
    int generation = ++resultsGeneration;
    view.status().setMessage("Loading…");
    Async.run(worker, work, table -> {
      if (generation == resultsGeneration) {
        view.results().show(table.title(), table.model(), table.capped());
        view.setTitleSuffix(cappedTitle(table.title(), table.rows().size(), table.page()));
        int n = table.rows().size();
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
    view.results().show("Results", new ResultsTableModel<>(Columns.searchHits()));
    view.setDetail(welcome(meta));
    view.setTitleSuffix("");
    view.status().setMessage("Ready");
  }

  private void fail(Exception e) {
    LOG.warn("Loading failed", e);
    view.status().setMessage("Error: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
  }

  private Table<?> search(ViewTarget.Search s) {
    var hits = repos.search().search(s.text(), s.kind(), SEARCH_LIMIT);
    return new Table<>("Search: " + s.text(), Columns.searchHits(), hits, SEARCH_LIMIT);
  }

  private Table<?> itemClass(ViewTarget.ItemClass c) {
    Integer subclass = c.subclassId() == ItemClassNode.CLASS_NODE ? null : c.subclassId();
    var items = repos.items().list(c.classId(), subclass, null, 0);
    return new Table<>(itemClassName(c.classId(), c.subclassId()), Columns.items(), items);
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

  private Table<?> list(EntityKind kind) {
    return switch (kind) {
      case ZONE -> new Table<>("Zones", Columns.zones(), repos.zones().all());
      case INSTANCE -> new Table<>("Dungeons & Raids", Columns.instances(), repos.instances().all());
      default -> throw new IllegalArgumentException("There is no list of " + kind.label() + " entities");
    };
  }

  private Table<?> zoneList(EntityKind kind, long zoneId) {
    if (zones == null) {
      zones = repos.zones().all();
    }
    String name = zones.stream().filter(z -> z.id() == zoneId).findFirst()
        .map(ZoneSummary::displayName).orElse(EntityKind.ZONE.unknownName(zoneId));
    return switch (kind) {
      case NPC -> new Table<>("NPCs in " + name, Columns.npcs(), repos.npcs().byZone(zoneId));
      case OBJECT -> new Table<>("Objects in " + name, Columns.objects(), repos.objects().byZone(zoneId));
      case QUEST -> new Table<>("Quests in " + name, Columns.quests(), repos.quests().byZone(zoneId));
      default -> throw new IllegalArgumentException("There is no zone list of " + kind.label() + " entities");
    };
  }

  /** The home page text. */
  static JComponent welcome(Map<String, String> meta) {
    String build = meta.get("build_id");
    return placeholder("<html><h2>WoW Forever Item &amp; Drop Browser</h2>"
        + "<p>Search with the box above (Ctrl+F) or browse on the left.</p>"
        + "<p>Double-click a result, or select it and press Enter, to open it.<br>"
        + "Alt+Left and Alt+Right go back and forward.</p>"
        + (build == null ? "" : "<p>Database build " + build + "</p>") + "</html>");
  }

  private static JLabel placeholder(String text) {
    JLabel label = new JLabel(text, SwingConstants.LEADING);
    label.setVerticalAlignment(SwingConstants.TOP);
    label.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    return label;
  }
}
