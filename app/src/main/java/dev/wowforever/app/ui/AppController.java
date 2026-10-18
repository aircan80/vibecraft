package dev.wowforever.app.ui;

import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ListFilter;
import dev.wowforever.app.model.NoFilter;
import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.nav.NavigationHistory;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.detail.DetailPanels;
import dev.wowforever.app.ui.pages.ListOptions;
import dev.wowforever.app.ui.pages.ListPage;
import dev.wowforever.app.ui.pages.ListRows;
import dev.wowforever.app.ui.pages.WorldMapPage;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import dev.wowforever.app.ui.pages.LoadingPage;
import dev.wowforever.app.ui.pages.PageState;
import dev.wowforever.app.ui.widgets.ListEntries;
import dev.wowforever.app.ui.widgets.ListEntry;
import dev.wowforever.app.ui.widgets.MasterList;
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
 *
 * <p>A hit opened from the search box's dropdown ({@link #openHit}) also runs its search into the master list, with
 * no history entry of its own, and selects the opened row there; Home empties the list to its placeholder.
 *
 * <p>The view hears when a result is opened for good (a commit from the list, a hit from the dropdown; never a
 * preview), when the master list may collapse, and when a search's results fill the list, which then shows
 * ({@link MainView#onResultOpened}, {@link MainView#onResultsShown}).
 *
 * <p>Where the view shows no master list (the compact window, {@link MainView#showsMasterList}), a full search's
 * results fill the detail area as a list of their own, without previews: Enter or a click opens a row.
 *
 * <p>An entity page that takes longer than {@value #LOADING_DELAY_MS} ms to load is announced by a
 * {@link LoadingPage} ({@code "Hogger"} over {@code "Loading drops…"}), its name taken from the list entry or the
 * search hit opened (else {@code "NPC #448"}); a load that finishes first never shows it.
 */
public final class AppController implements Navigator {
  /** Most search hits shown; list pages show every row. */
  public static final int SEARCH_LIMIT = 200;
  /** How long an entity page may take to load before the loading page shows, in milliseconds. */
  public static final int LOADING_DELAY_MS = 150;
  private static final Logger LOG = LoggerFactory.getLogger(AppController.class);

  private final Repositories repos;
  private final MainView view;
  private final Executor worker;
  private final Map<String, String> meta;
  private volatile dev.wowforever.app.catalog.CatalogSession catalogSession;
  /** The wait before a loading page shows: a Swing timer, or a delay the test fires by hand. */
  private final MasterList.Delay loadingDelay;
  /** The search hit last opened from the dropdown and its name, which its loading page reads. */
  private EntityRef hitRef;
  private String hitTitle;
  private final NavigationHistory history = new NavigationHistory();
  private volatile int resultsGeneration;
  private volatile int detailGeneration;
  /** Whether the current history slot was taken by a list preview, so the next preview may reuse it. */
  private boolean previewSlot;
  /** Search input revision when the current entity navigation was requested or explicitly committed. */
  private long pageSearchEditRevision;
  /** The list page on screen, null while the detail shows anything else. */
  private ListPage listPage;
  /** The target of {@link #listPage}'s history entry, updated as soon as a filter changes. */
  private ViewTarget.List listTarget;
  private volatile ListOptions listOptions; // cached on the worker
  /** The last target other than a search shown, whose page a full layout's detail area shows under a search. */
  private ViewTarget lastPage;
  /** The last status message the page on screen wrote ({@link #pageStatus}); null for none. */
  private String pageMessage;
  /** The component in the detail area. */
  private JComponent page;
  /** The target {@link #page} shows (a list page's follows its filter); null for compact search results. */
  private ViewTarget pageTarget;
  /** The entity page record last loaded ({@link DetailPanels#load}), which a rerender builds the page from. */
  private Loaded loaded;
  /** The view state a rerender saved for the page of a target that has to load again. */
  private PendingState pendingState;
  /** Small transient view states, never page components or database records. Classic keeps its original path. */
  private final java.util.LinkedHashMap<ViewTarget,Object> journalStates = new java.util.LinkedHashMap<>(16,.75f,true);
  private boolean journal() { return view.uiExperience() == UiExperience.JOURNAL; }
  private void rememberJournalState() {
    if (journal() && pageTarget != null && page instanceof PageState state) {
      journalStates.put(pageTarget,state.saveState());
      while(journalStates.size()>128) journalStates.remove(journalStates.keySet().iterator().next());
    }
  }
  private JComponent renderDetail(Object detail, StatusSink sink) {
    if (!journal()) return DetailPanels.render(detail,repos,this,worker,sink,
        view.sourceFilter(),this::changeSourceFilter,view.questPins(),view.routes());
    JComponent component=dev.wowforever.app.ui.journal.JournalDetails.render(detail,repos,this,worker,sink,
        view.sourceFilter(),this::changeSourceFilter,view.questPins(),view.routes());
    if (loaded!=null && loaded.detail()==detail) {
      if (loaded.dungeons()!=null && detail instanceof DetailPanels.LoadedInstance instance) {
        var workspace=new dev.wowforever.app.ui.journal.JournalDungeonPage(loaded.dungeons(),this,instance.detail().summary().id(),component);
        if(loaded.destinationState()!=null)workspace.restoreState(loaded.destinationState());
        return workspace;
      }
      if (loaded.city()!=null && detail instanceof DetailPanels.LoadedZone zone)
        return new dev.wowforever.app.ui.journal.JournalCityPage(loaded.city(),component,this,zone.map(),
            new dev.wowforever.app.music.CityMusicPanel(dev.wowforever.app.music.CityMusicService.shared()));
    }
    return component;
  }

  /** Loaded entity and optional worker-preloaded Journal context. */
  private record Loaded(EntityRef ref, Object detail,
      dev.wowforever.app.ui.journal.JournalCatalog.DungeonSnapshot dungeons,
      dev.wowforever.app.ui.journal.JournalCities.GuideSnapshot city,
      dev.wowforever.app.ui.journal.JournalDungeonPage.State destinationState) {}
  private record EntitySnapshot(java.util.Optional<Object> detail,
      dev.wowforever.app.ui.journal.JournalCatalog.DungeonSnapshot dungeons,
      dev.wowforever.app.ui.journal.JournalCities.GuideSnapshot city) {}

  /** The view state ({@link PageState#saveState}) saved for the page of {@code target}. */
  private record PendingState(ViewTarget target, Object state) {}

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

  /**
   * {@code meta} is the database's meta table (build id, client build, source commits); the build shows on home and
   * in the status line, which also shows where the data comes from ({@link StatusBar#setProvenance}).
   */
  public AppController(Repositories repos, MainView view, Executor worker, Map<String, String> meta) {
    this(repos, view, worker, meta, MasterList.timerDelay(LOADING_DELAY_MS));
  }

  /**
   * As above, the loading page waiting for {@code loadingDelay} (the loading delay seam: tests pass a delay they fire
   * by hand) rather than {@value #LOADING_DELAY_MS} ms on a Swing timer.
   */
  public AppController(Repositories repos, MainView view, Executor worker, Map<String, String> meta,
      MasterList.Delay loadingDelay) {
    this.loadingDelay = Objects.requireNonNull(loadingDelay, "loadingDelay");
    this.repos = Objects.requireNonNull(repos, "repos");
    this.view = Objects.requireNonNull(view, "view");
    this.worker = Objects.requireNonNull(worker, "worker");
    this.meta = Map.copyOf(meta);
    String buildId = meta.get("build_id");
    view.status().setBuild(buildId);
    view.configureResearchScope(buildId==null || buildId.isBlank()?null:"forever:"+buildId);
    view.status().setProvenance(this.meta);
    view.masterList().onPreview(e -> preview(e.ref()));
    view.masterList().onCommit(e -> commit(e.ref()));
  }

  /** Attach the validated data identity before starting navigation. */
  public void configureCatalogSession(dev.wowforever.app.catalog.CatalogSession session) {
    Objects.requireNonNull(session,"session");
    var context=session.token().context();
    if(dev.wowforever.app.catalog.EditionRegistry.fromLegacyMetadata(repos.database().meta())
        .installed(context.editionId()).filter(context::equals).isEmpty())
      throw new IllegalArgumentException("Selected catalog does not match the installed database");
    catalogSession=session;
    view.configureResearchScope(context.editionId()+"\n"+context.projectionId()+"\n"+context.clientBuild()+"\n"+context.locale());
  }
  private java.util.function.BooleanSupplier currentRequest(java.util.function.BooleanSupplier navigation) {
    var session=catalogSession;
    if(session==null)return navigation;
    var token=session.token();
    return ()->navigation.getAsBoolean()&&session.current(token);
  }

  /** What a rail button opens. */
  public static ViewTarget target(RailTarget t) {
    return switch (t) {
      case HOME -> new ViewTarget.Home();
      case FOREVER -> new ViewTarget.Forever("hub");
      case ITEMS -> new ViewTarget.List(EntityKind.ITEM);
      case NPCS -> new ViewTarget.List(EntityKind.NPC);
      case QUESTS -> new ViewTarget.List(EntityKind.QUEST);
      case ZONES -> new ViewTarget.WorldMap(947);
      case INSTANCES -> new ViewTarget.List(EntityKind.INSTANCE);
    };
  }

  /** Opens the rail, respecting the saved Zones Map/List choice. */
  public void openRail(RailTarget target) {
    open(target == RailTarget.ZONES && !view.zonesMap().isOn()
        ? new ViewTarget.List(EntityKind.ZONE) : target(target));
  }

  private void zonesView(boolean map) {
    view.zonesMap().set(map);
    open(map ? new ViewTarget.WorldMap(947) : new ViewTarget.List(EntityKind.ZONE));
  }

  /**
   * The rail section {@code t} belongs to: Home, or the list page of a kind; null for entities and searches,
   * which no rail button opens.
   */
  public static RailTarget railSection(ViewTarget t) {
    return switch (t) {
      case ViewTarget.Home h -> RailTarget.HOME;
      case ViewTarget.Forever f -> RailTarget.FOREVER;
      case ViewTarget.ItemContent c -> RailTarget.ITEMS;
      case ViewTarget.WorldMap w -> RailTarget.ZONES;
      case ViewTarget.List l -> switch (l.kind()) {
        case ITEM -> RailTarget.ITEMS;
        case NPC -> RailTarget.NPCS;
        case QUEST -> RailTarget.QUESTS;
        case ZONE -> RailTarget.ZONES;
        case INSTANCE -> RailTarget.INSTANCES;
        default -> null;
      };
      default -> null;
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
    view.onResultOpened();
    ViewTarget t = new ViewTarget.Entity(ref);
    if (isCurrent(t)) {
      previewSlot = false;
      pageSearchEditRevision = view.searchEditRevision();
      if (loaded != null && loaded.ref().equals(ref) && t.equals(pageTarget)) {
        followCommittedPage(ref, loaded.detail());
      }
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

  /**
   * Opens {@code hit}, picked in the search box's dropdown for {@code search}, as {@link #open} does, and runs
   * {@code search} (same text and kind) into the master list without a history entry of its own: the list stays
   * under its "Search: ..." heading with the opened row selected when it is among the rows, and Back leaves the
   * page in one step. The detail keeps the page, also in the compact window, where the list is not shown.
   */
  public void openHit(ViewTarget.Entity hit, ViewTarget.Search search) {
    view.onResultOpened();
    open(hit);
    loadResults(() -> search(search), hit.ref());
  }

  /** {@link #openHit(ViewTarget.Entity, ViewTarget.Search)}, the hit called {@code title} while its page loads. */
  public void openHit(ViewTarget.Entity hit, ViewTarget.Search search, String title) {
    hitRef = hit.ref();
    hitTitle = title;
    openHit(hit, search);
  }

  private boolean isCurrent(ViewTarget t) {
    return history.current().filter(t::equals).isPresent();
  }

  /**
   * Shows the current target again without touching the history, say after the window switched between its full
   * and compact layouts or the drop source filter changed; nothing happens before the first {@link #open}. An entity
   * page is built again from what it loaded, without loading the entity again, but building it runs the queries a
   * page runs as it is built: an instance page reads its first boss's loot again (and the restored encounter's when
   * another one was selected), and a zone map with "Quests" on loads its quest givers' pins again on the worker. A
   * page keeps its view state ({@link PageState}: how far it was scrolled, the selected encounter). A search shown
   * where there is a master list leaves the detail area as a search does: the last page shown there comes back, and
   * the list keeps the results it has (the search does not run again).
   */
  public void rerender() {
    ViewTarget t = current();
    if (t == null) {
      return;
    }
    if (t instanceof ViewTarget.Search && view.showsMasterList()) {
      if (lastPage == null || lastPage instanceof ViewTarget.Home) {
        detailGeneration++; // the welcome page behind the search; the list keeps its results
        leaveListPage();
        if (journal()) showJournalCatalog(new ViewTarget.Home());
        else setPage(new ViewTarget.Home(), welcome(meta), null);
      } else {
        reshow(lastPage, stateOf(lastPage));
      }
      view.setHistoryState(history.canBack(), history.canForward());
      view.selectRail(railSection(t));
      return;
    }
    reshow(t, stateOf(t));
  }

  /**
   * The view state of the page on screen when it is the page of {@code t}; null otherwise (say, while the page of
   * {@code t} is still loading and the old one shows), so one entity's scroll or encounter never lands on another's.
   */
  private Object stateOf(ViewTarget t) {
    return page instanceof PageState p && t.equals(pageTarget) ? p.saveState() : null;
  }

  /** Shows {@code t} again, its page restored to {@code state}: an entity page from what it loaded, if it is that. */
  private void reshow(ViewTarget t, Object state) {
    if (t instanceof ViewTarget.Entity e && loaded != null && loaded.ref().equals(e.ref())) {
      lastPage = t;
      view.setHistoryState(history.canBack(), history.canForward());
      view.selectRail(railSection(t));
      int generation = ++detailGeneration; // a load still in flight for an older page is dropped
      loadingDelay.cancel();
      leaveListPage();
      setPage(t, renderDetail(loaded.detail(), pageStatus(generation)), state);
      view.setTitleSuffix(DetailPanels.name(loaded.detail()));
      return;
    }
    pendingState = new PendingState(t, state);
    show(t, false);
  }

  /**
   * Puts {@code p}, the page of {@code t} (null for a page of no target, such as compact search results), in the
   * detail area, restored to {@code state} when it keeps one.
   */
  private void setPage(ViewTarget t, JComponent p, Object state) {
    rememberJournalState();
    page = p;
    pageTarget = t;
    view.setDetail(p);
    if (t != null) view.viewTargetChanged(t);
    if (journal() && t instanceof ViewTarget.Entity entity && loaded!=null && loaded.city()!=null
        && entity.ref().equals(loaded.ref())) view.highlightCitySection();
    if (state != null && p instanceof PageState s) {
      s.restoreState(state);
    }
  }

  /** {@link #setPage} for the page of {@code t}, restored to the state a rerender left for it, if any. */
  private void setPage(ViewTarget t, JComponent p) {
    PendingState pending = pendingState;
    pendingState = null;
    setPage(t, p, pending != null && pending.target().equals(t) ? pending.state()
        : journal() ? journalStates.get(t) : null);
  }

  /**
   * A page's filter control chose {@code filter}: the view saves it and the page on screen is built again under it,
   * keeping its scroll ({@link #rerender}).
   */
  public void changeSourceFilter(SourceFilter filter) {
    if (filter != view.sourceFilter()) {
      view.setSourceFilter(filter);
      rerender();
    }
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

  /** Load archive metadata on the controller's worker, never on the Swing event thread. */
  public void loadSourceCatalog(java.util.function.Consumer<dev.wowforever.app.db.SourceArchiveRepository.Catalog> loaded,
      java.util.function.Consumer<RuntimeException> failed) {
    worker.execute(() -> {
      try {
        var catalog = repos.sourceArchive().catalog();
        javax.swing.SwingUtilities.invokeLater(() -> loaded.accept(catalog));
      } catch (RuntimeException failure) {
        javax.swing.SwingUtilities.invokeLater(() -> failed.accept(failure));
      }
    });
  }

  /** The database meta values this controller was given. */
  public Map<String, String> meta() {
    return meta;
  }

  private void show(ViewTarget t) {
    show(t, true);
  }

  private void show(ViewTarget t, boolean captureSearchEdit) {
    view.navigationStarted();
    rememberJournalState();
    if(journal()) dev.wowforever.app.music.CityMusicService.shared().leaveCity();
    loadingDelay.cancel(); // the loading page of a page left behind
    if (!(t instanceof ViewTarget.Search)) {
      lastPage = t;
    }
    view.setHistoryState(history.canBack(), history.canForward());
    view.selectRail(railSection(t));
    switch (t) {
      case ViewTarget.Search s -> {
        if (!view.showsMasterList()) {
          detailGeneration++; // the results take the detail area: drop a page still loading
          leaveListPage();
        }
        loadResults(() -> search(s), null);
      }
      case ViewTarget.List l -> {
        if (journal() && l.kind()==EntityKind.INSTANCE) showJournalCatalog(l); else showList(l);
      }
      case ViewTarget.WorldMap w -> showWorldMap(w);
      case ViewTarget.Forever f -> showCatalog(f);
      case ViewTarget.ItemContent c -> showCatalog(c);
      case ViewTarget.Entity e -> showEntity(e.ref(), captureSearchEdit);
      case ViewTarget.Home h -> showHome();
    }
  }

  /**
   * Loads {@code work} into the master list (and, without a master list, into the detail). With {@code opened}, a
   * page already opened for the search, only the list changes: the row of {@code opened} is selected there, and
   * the detail and the window title keep the page.
   */
  private void loadResults(Supplier<Listing> work, EntityRef opened) {
    int generation = ++resultsGeneration;
    int detail = detailGeneration;
    view.status().setMessage("Loading…");
    Async.runIf(worker, currentRequest(() -> generation == resultsGeneration), work, listing -> {
      if (generation == resultsGeneration) {
        view.masterList().show(listing.title(), listing.entries(), listing.capped());
        if (opened != null) {
          view.masterList().selectQuietly(opened);
        } else {
          if (view.showsMasterList()) {
            view.onResultsShown();
          }
          if (!view.showsMasterList() && detail == detailGeneration) {
            setPage(null, resultsPage(listing), null);
          }
          view.setTitleSuffix(cappedTitle(listing.title(), listing.entries().size(), listing.page()));
        }
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
   * panel is built on the EDT ({@link DetailPanels#render}) under the view's drop source filter, a zone map's
   * "Quests" toggle as the view's setting says.
   */
  private void showEntity(EntityRef ref, boolean captureSearchEdit) {
    boolean journalExperience=journal();
    var destinationState=journalExperience && ref.kind()==EntityKind.INSTANCE
        && page instanceof dev.wowforever.app.ui.journal.JournalDungeonPage workspace
        ? workspace.destinationState() : null;
    if (captureSearchEdit) {
      pageSearchEditRevision = view.searchEditRevision();
    }
    int generation = ++detailGeneration;
    leaveListPage();
    if (pageMessage != null && pageMessage.equals(view.status().message())) {
      view.status().setMessage("Ready"); // the page left said it ("Loading pins…"); it no longer applies
    }
    pageMessage = null;
    view.setTitleSuffix(ref.kind().label() + " #" + ref.id());
    String title = knownTitle(ref);
    loadingDelay.cancel();
    loadingDelay.schedule(() -> { // before the load: a worker that runs it inline cancels this at once
      if (generation == detailGeneration) {
        setPage(null, LoadingPage.of(ref.kind(), title), null);
      }
    });
    Async.runIf(worker, currentRequest(() -> generation == detailGeneration), () -> {
      var detail=DetailPanels.load(ref,repos);
      var dungeons=journalExperience && ref.kind()==EntityKind.INSTANCE && detail.isPresent()
          ? (dev.wowforever.app.ui.journal.JournalCatalog.DungeonSnapshot)dev.wowforever.app.ui.journal.JournalCatalog.load(new ViewTarget.List(EntityKind.INSTANCE),repos.database()) : null;
      var city=journalExperience && ref.kind()==EntityKind.ZONE && detail.isPresent()
          ? dev.wowforever.app.ui.journal.JournalCities.loadGuide(repos.database(),ref.id()) : null;
      return new EntitySnapshot(detail,dungeons,city);
    }, snapshot -> {
      var detail=snapshot.detail();
      if (generation == detailGeneration) {
        loadingDelay.cancel();
        if (detail.isPresent()) {
          loaded = new Loaded(ref, detail.get(),snapshot.dungeons(),snapshot.city(),destinationState);
          if (snapshot.city()!=null) dev.wowforever.app.music.CityMusicService.shared().enterCity(snapshot.city().city().id(),snapshot.city().build());
          setPage(new ViewTarget.Entity(ref), renderDetail(detail.get(), pageStatus(generation)));
          view.setTitleSuffix(DetailPanels.name(detail.get()));
          followCommittedPage(ref, detail.get());
        } else {
          loaded = null;
          setPage(new ViewTarget.Entity(ref), DetailPanels.notFound(ref));
        }
      }
    }, e -> {
      if (generation == detailGeneration) {
        loadingDelay.cancel();
        if (page instanceof LoadingPage loading) { // it would read "Loading…" for good
          loading.showFailure(message(e));
        }
        fail(e);
      }
    });
  }

  /** A preview or a late page behind a newer search must leave the user's query alone. */
  private void followCommittedPage(EntityRef ref, Object detail) {
    if (!previewSlot && isCurrent(new ViewTarget.Entity(ref))
        && pageSearchEditRevision == view.searchEditRevision()) {
      view.followPageName(DetailPanels.name(detail));
    }
  }

  /**
   * The name of {@code ref} as the controller already has it, for its loading page: the search hit just opened, else
   * its row in the master list, else {@code "NPC #448"}.
   */
  private String knownTitle(EntityRef ref) {
    if (ref.equals(hitRef) && hitTitle != null && !hitTitle.isBlank()) {
      return hitTitle;
    }
    return view.masterList().entries().stream()
        .filter(e -> e.ref().equals(ref) && e.title() != null && !e.title().isBlank()).map(ListEntry::title)
        .findFirst().orElse(ref.kind().label() + " #" + ref.id());
  }

  private void showHome() {
    if (journal()) {
      resultsGeneration++;
      view.masterList().clear();
      showJournalCatalog(new ViewTarget.Home());
      return;
    }
    resultsGeneration++; // drop loads still in flight
    detailGeneration++;
    leaveListPage();
    view.masterList().clear();
    setPage(new ViewTarget.Home(), welcome(meta));
    view.setTitleSuffix("");
    view.status().setMessage("Ready");
  }

  /** Shows the list page of {@code t} in the detail area; the master list is left as it is. */
  private void showList(ViewTarget.List t) {
    int generation = ++detailGeneration;
    leaveListPage();
    view.setTitleSuffix(ListPage.title(t.kind()));
    view.status().setMessage("Loading…");
    Async.runIf(worker, currentRequest(() -> generation == detailGeneration), () -> new LoadedList(listOptions(t), ListRows.load(t, repos)), loaded -> {
      if (generation == detailGeneration) {
        AtomicReference<ListPage> self = new AtomicReference<>();
        ListPage page = new ListPage(t, loaded.options(), loaded.rows(), this, f -> refilter(self.get(), f));
        self.set(page);
        listPage = page;
        listTarget = t;
        if (t.kind() == EntityKind.ZONE) {
          page.showMapToggle(() -> zonesView(true));
          setPage(t, page);
        } else {
          setPage(t, page);
        }
        showCount(loaded.rows().size());
      }
    }, e -> {
      if (generation == detailGeneration) {
        fail(e);
      }
    });
  }

  /** Loads the hierarchy and decodes its art on the worker, then builds accessible regions on the EDT. */
  private void showWorldMap(ViewTarget.WorldMap target) {
    int generation = ++detailGeneration;
    leaveListPage();
    view.setTitleSuffix("Zones · Map");
    view.status().setMessage("Loading…");
    Async.runIf(worker, currentRequest(() -> generation == detailGeneration), () -> {
      var catalog = repos.worldMaps().catalog();
      var map = repos.zones().mapByUiMap(target.uiMapId());
      BufferedImage image = null;
      if (map.isPresent()) {
        try { image = ImageIO.read(new ByteArrayInputStream(map.get().jpeg())); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Could not decode world map", failure); }
      }
      return new LoadedWorldMap(catalog, image);
    }, data -> {
      if (generation != detailGeneration) return;
      setPage(target, worldMapPage(target, data.catalog(), data.image(), region -> {
        if (!data.catalog().children(region.uiMapId()).isEmpty()) {
          open(new ViewTarget.WorldMap(region.uiMapId()));
        } else if (region.zoneId() != null) {
          open(new ViewTarget.Entity(new EntityRef(EntityKind.ZONE, region.zoneId())));
        }
      }, child -> {
        if (current() instanceof ViewTarget.WorldMap current && current.uiMapId() == target.uiMapId()) {
          var focused = new ViewTarget.WorldMap(target.uiMapId(), child);
          history.replaceCurrent(focused);
          pageTarget = focused;
          lastPage = focused;
        }
      }, () -> open(new ViewTarget.WorldMap(947)), () -> {
        if (target.uiMapId() != 947) open(new ViewTarget.WorldMap(947, target.uiMapId()));
        else back();
      }, () -> zonesView(false)));
      view.status().setMessage("Choose a region");
    }, failure -> { if (generation == detailGeneration) fail(failure); });
  }

  private JComponent worldMapPage(ViewTarget.WorldMap target,
      dev.wowforever.app.model.WorldMapCatalog catalog, BufferedImage image,
      java.util.function.Consumer<dev.wowforever.app.model.WorldMapRegion> open,
      java.util.function.Consumer<Long> focused, Runnable root, Runnable up, Runnable list) {
    return journal() ? new dev.wowforever.app.ui.journal.JournalWorldMapPage(target,catalog,image,open,focused,root,up,list)
        : new WorldMapPage(target,catalog,image,open,focused,root,up,list);
  }

  private record LoadedWorldMap(dev.wowforever.app.model.WorldMapCatalog catalog, BufferedImage image) {}

  /** Content catalogs follow the same worker/EDT split and stale-load protection as entity pages. */
  private void showCatalog(ViewTarget target) {
    if(journal() && target instanceof ViewTarget.Forever f && f.section().equals("lore")) {
      ++detailGeneration;loadingDelay.cancel();leaveListPage();
      view.setTitleSuffix("Azeroth through a word");
      setPage(target,new dev.wowforever.app.ui.journal.JournalLoreSearchPage(repos.database(),this,worker));
      view.status().setMessage("Ready to search quest text");return;
    }
    if(journal() && target instanceof ViewTarget.Forever f && f.section().equals("pinned")) {
      ++detailGeneration;
      loadingDelay.cancel();
      leaveListPage();
      view.setTitleSuffix("Pinned research");
      setPage(target,view.pinnedResearchPage(this::open));
      view.status().setMessage("Ready");
      return;
    }
    if (journal() && target instanceof ViewTarget.Forever f && (f.section().equals("ladder") || f.section().equals("cities"))) {
      showJournalCatalog(target);
      return;
    }
    int generation = ++detailGeneration;
    leaveListPage();
    String title = target instanceof ViewTarget.Forever ? "WoW: Forever" : "Item research";
    view.setTitleSuffix(title);
    view.status().setMessage("Loading…");
    loadingDelay.cancel();
    loadingDelay.schedule(() -> {
      if (generation == detailGeneration) setPage(null,new LoadingPage(title,"Loading archived source data…"),null);
    });
    Async.runIf(worker, currentRequest(() -> generation == detailGeneration), () -> CatalogPages.load(target, repos.database()), data -> {
      if (generation != detailGeneration) return;
      loadingDelay.cancel();
      setPage(target, CatalogPages.render(target, data, this));
      view.status().setMessage("Ready");
    }, failure -> { if (generation == detailGeneration) fail(failure); });
  }

  /** The Journal home and unified dungeon catalog share the normal stale-load and history rules. */
  private void showJournalCatalog(ViewTarget target) {
    int generation=++detailGeneration;
    leaveListPage();
    String title=target instanceof ViewTarget.Home?"Explorer Journal":target instanceof ViewTarget.Forever f && f.section().equals("cities")?"Cities":"Dungeons & raids";
    view.setTitleSuffix(title);
    view.status().setMessage("Loading…");
    loadingDelay.cancel();
    loadingDelay.schedule(() -> {
      if(generation==detailGeneration)setPage(null,new LoadingPage(title,"Opening your journal…"),null);
    });
    Async.runIf(worker, currentRequest(() -> generation == detailGeneration), () -> dev.wowforever.app.ui.journal.JournalCatalog.load(target,repos.database()), data -> {
      if(generation!=detailGeneration)return;
      loadingDelay.cancel();
      JComponent component=dev.wowforever.app.ui.journal.JournalCatalog.render(data,this);
      if(component==null)throw new IllegalStateException("Unsupported journal catalog: "+target);
      setPage(target,component);
      view.setTitleSuffix(title);
      view.status().setMessage("Ready to explore");
    },failure -> { if(generation==detailGeneration)fail(failure); });
  }

  /** A list page's filter options and rows, loaded together on the worker. */
  private record LoadedList(ListOptions options, List<?> rows) {}

  /** {@code listing} as the detail of a view without a master list: Enter or a click opens a row. */
  private MasterList resultsPage(Listing listing) {
    MasterList results = new MasterList();
    results.show(listing.title(), listing.entries(), listing.capped());
    results.onCommit(e -> commit(e.ref()));
    return results;
  }

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
   * Reloads the rows of {@code page} under {@code filter}. The list's history entry takes the new filter and the
   * forward history goes, as in a browser (when a search has been opened since, the filtered list is pushed after
   * it instead). A reload that fails empties the table and says so under it, so no old rows stand under the new
   * filter. Ignored for a page no longer shown.
   */
  private void refilter(ListPage page, ListFilter filter) {
    if (page == null || page != listPage) {
      return;
    }
    ViewTarget.List t = new ViewTarget.List(listTarget.kind(), filter);
    if (isCurrent(listTarget)) {
      history.replaceCurrentDroppingForward(t);
    } else {
      history.push(t);
    }
    listTarget = t;
    lastPage = t;
    pageTarget = t; // the page now shows this filter
    previewSlot = false;
    view.setHistoryState(history.canBack(), history.canForward());
    int generation = ++detailGeneration;
    view.status().setMessage("Loading…");
    Async.runIf(worker, currentRequest(() -> generation == detailGeneration), () -> ListRows.load(t, repos), rows -> {
      if (generation == detailGeneration && page == listPage) {
        page.showRows(t, rows);
        showCount(rows.size());
      }
    }, e -> {
      if (generation == detailGeneration) {
        if (page == listPage) {
          page.showFailure(t, message(e));
        }
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

  /**
   * What the page of detail load {@code generation} reports once shown (a zone map loading pins): to the status bar,
   * failures as {@link #fail}, while that page is the one on screen. A report that arrives after the user moved on
   * (another page, a list, home) is dropped.
   */
  private StatusSink pageStatus(int generation) {
    var current=currentRequest(()->generation==detailGeneration);
    return new StatusSink() {
      @Override
      public void message(String text) {
        if (current.getAsBoolean()) {
          pageMessage = text;
          view.status().setMessage(text);
        }
      }

      @Override
      public void fail(Exception e) {
        if (current.getAsBoolean()) {
          AppController.this.fail(e);
        } else {
          LOG.debug("A page no longer shown failed to load more", e);
        }
      }
    };
  }

  private void fail(Exception e) {
    LOG.warn("Loading failed", e);
    view.status().setMessage("Error: " + message(e));
  }

  private static String message(Exception e) {
    return e.getMessage() != null ? e.getMessage() : e.toString();
  }

  private Listing search(ViewTarget.Search s) {
    var hits = repos.search().search(s.text(), s.kind(), SEARCH_LIMIT);
    ListEntries.warmIcons(hits); // on the worker: the list's renderer only looks in the cache
    return new Listing("Search: " + s.text(), entries(hits, ListEntries::of), SEARCH_LIMIT);
  }

  /** {@code name}, marked {@code (first <page>)} when {@code rows} fills a page; unchanged when {@code page} is 0. */
  static String cappedTitle(String name, int rows, int page) {
    return page > 0 && rows >= page ? name + " (first " + page + ")" : name;
  }

  private static <T> List<ListEntry> entries(List<T> rows, Function<T, ListEntry> toEntry) {
    return rows.stream().map(toEntry).toList();
  }

  /** The home page text, ending in the short build id ({@link StatusBar#buildLabel}); About has the whole one. */
  static JComponent welcome(Map<String, String> meta) {
    String build = meta.get("build_id");
    return placeholder("<html><h2>WoW Forever Item &amp; Drop Browser</h2>"
        + "<p>Search with the box above (Ctrl+F) or browse with the icons on the left.</p>"
        + "<p>Arrow through the list to preview a row here; Enter or a click opens it.<br>"
        + "Ctrl+B hides or shows the list. Alt+Left and Alt+Right go back and forward.</p>"
        + (build == null ? "" : "<p>" + StatusBar.buildLabel(build) + "</p>") + "</html>");
  }

  private static JLabel placeholder(String text) {
    JLabel label = new JLabel(text, SwingConstants.LEADING);
    label.setVerticalAlignment(SwingConstants.TOP);
    label.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    return label;
  }
}
