package dev.wowforever.app.ui;

import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ListFilter;
import dev.wowforever.app.model.NoFilter;
import dev.wowforever.app.nav.NavigationHistory;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.detail.DetailPanels;
import dev.wowforever.app.ui.pages.ListOptions;
import dev.wowforever.app.ui.pages.ListPage;
import dev.wowforever.app.ui.pages.ListRows;
import dev.wowforever.app.ui.widgets.ListEntries;
import dev.wowforever.app.ui.widgets.ListEntry;
import dev.wowforever.app.ui.widgets.RailTarget;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns view targets into master list contents and detail views: searches fill the master list; entities and the
 * rail's list pages ({@link ListPage}) fill the detail. Every repository call runs on {@code worker} through
 * {@link Async}; the results are shown on the EDT, and a load that a newer one has overtaken is dropped. All
 * methods are called on the EDT.
 *
 * <p>A filter change on a list page reloads its table in place and replaces the list's history entry, so Back
 * does not step through every filter tried, and Back from an entity opened off the list restores it filtered.
 *
 * <p>Arrowing through the list previews rows: the first preview after a navigation takes a new history slot and
 * later ones reuse it ({@link NavigationHistory#replaceCurrent}), so twenty rows previewed are one back step. A
 * commit (Enter, a click) or any other navigation keeps that slot, and the next preview takes a new one. A row
 * already on screen is neither previewed nor committed again: its history slot is kept and nothing reloads.
 */
public final class AppController implements Navigator {
  /** Most search hits shown; list pages show every row. */
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
  /** The list page on screen, null while the detail shows anything else. */
  private ListPage listPage;
  /** The target of {@link #listPage}'s history entry, updated as soon as a filter changes. */
  private ViewTarget.List listTarget;
  private volatile ListOptions listOptions; // cached on the worker

  /** One list to show: its title and rows; {@code page} is the row limit of the query, 0 for none. */
  private record Listing(String title, List<ListEntry> entries, int page) {
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
      case ITEMS -> new ViewTarget.List(EntityKind.ITEM);
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
   * A row already on screen (previewed, or clicked twice) is only kept in the history, not loaded again.
   */
  public void commit(EntityRef ref) {
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
      case ViewTarget.List l -> showList(l);
      case ViewTarget.Entity e -> showEntity(e.ref());
      case ViewTarget.Home h -> showHome();
    }
  }

  private void loadResults(Supplier<Listing> work) {
    int generation = ++resultsGeneration;
    view.status().setMessage("Loading…");
    Async.run(worker, work, listing -> {
      if (generation == resultsGeneration) {
        view.masterList().show(listing.title(), listing.entries(), listing.capped());
        view.setTitleSuffix(cappedTitle(listing.title(), listing.entries().size(), listing.page()));
        showCount(listing.entries().size());
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
    leaveListPage();
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
    leaveListPage();
    view.masterList().show("Results", List.of(), false);
    view.setDetail(welcome(meta));
    view.setTitleSuffix("");
    view.status().setMessage("Ready");
  }

  /** Shows the list page of {@code t} in the detail area; the master list is left as it is. */
  private void showList(ViewTarget.List t) {
    int generation = ++detailGeneration;
    leaveListPage();
    view.setTitleSuffix(ListPage.title(t.kind()));
    view.status().setMessage("Loading…");
    Async.run(worker, () -> new LoadedList(listOptions(t), ListRows.load(t, repos)), loaded -> {
      if (generation == detailGeneration) {
        AtomicReference<ListPage> self = new AtomicReference<>();
        ListPage page = new ListPage(t, loaded.options(), loaded.rows(), this, f -> refilter(self.get(), f));
        self.set(page);
        listPage = page;
        listTarget = t;
        view.setDetail(page);
        showCount(loaded.rows().size());
      }
    }, e -> {
      if (generation == detailGeneration) {
        fail(e);
      }
    });
  }

  /** A list page's filter options and rows, loaded together on the worker. */
  private record LoadedList(ListOptions options, List<?> rows) {}

  /** The filter bar options a list of {@code t} needs: none for zones and instances, else the cached ones. */
  private ListOptions listOptions(ViewTarget.List t) {
    if (t.filter() instanceof NoFilter) {
      return ListOptions.EMPTY;
    }
    ListOptions o = listOptions;
    if (o == null) {
      o = ListOptions.load(repos);
      listOptions = o;
    }
    return o;
  }

  /**
   * Reloads the rows of {@code page} under {@code filter}. The list's history entry takes the new filter (when a
   * search has been opened since, the filtered list is pushed after it instead). Ignored for a page no longer
   * shown.
   */
  private void refilter(ListPage page, ListFilter filter) {
    if (page == null || page != listPage) {
      return;
    }
    ViewTarget.List t = new ViewTarget.List(listTarget.kind(), filter);
    if (isCurrent(listTarget)) {
      history.replaceCurrent(t);
    } else {
      history.push(t);
    }
    listTarget = t;
    previewSlot = false;
    view.setHistoryState(history.canBack(), history.canForward());
    int generation = ++detailGeneration;
    view.status().setMessage("Loading…");
    Async.run(worker, () -> ListRows.load(t, repos), rows -> {
      if (generation == detailGeneration && page == listPage) {
        page.showRows(t, rows);
        showCount(rows.size());
      }
    }, e -> {
      if (generation == detailGeneration) {
        fail(e);
      }
    });
  }

  private void leaveListPage() {
    listPage = null;
    listTarget = null;
  }

  private void showCount(int n) {
    view.status().setMessage(n == 1 ? "1 result" : String.format(Locale.ROOT, "%,d results", n));
  }

  private void fail(Exception e) {
    LOG.warn("Loading failed", e);
    view.status().setMessage("Error: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
  }

  private Listing search(ViewTarget.Search s) {
    var hits = repos.search().search(s.text(), s.kind(), SEARCH_LIMIT);
    return new Listing("Search: " + s.text(), entries(hits, ListEntries::of), SEARCH_LIMIT);
  }

  /** {@code name}, marked {@code (first <page>)} when {@code rows} fills a page; unchanged when {@code page} is 0. */
  static String cappedTitle(String name, int rows, int page) {
    return page > 0 && rows >= page ? name + " (first " + page + ")" : name;
  }

  private static <T> List<ListEntry> entries(List<T> rows, Function<T, ListEntry> toEntry) {
    return rows.stream().map(toEntry).toList();
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
