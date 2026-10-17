package dev.wowforever.app.ui;

import dev.wowforever.app.db.SearchRepository;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.SearchHit;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.widgets.KindChips;
import dev.wowforever.app.ui.widgets.KindMenu;
import dev.wowforever.app.ui.widgets.ListEntries;
import dev.wowforever.app.ui.widgets.ListEntry;
import dev.wowforever.app.ui.widgets.MasterList;
import dev.wowforever.app.ui.widgets.SearchDropdown;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The search box with type-ahead, kind chips, recent searches and paste parsing. Typing looks up the top hits and
 * the per-kind counts on the worker {@value #DEBOUNCE_MILLIS} ms after the last keystroke and shows them in the
 * {@link SearchDropdown} (a lookup that newer text has overtaken is dropped); the focused empty box shows the recent
 * searches. Enter opens the chosen or top hit ({@link Opener#openHit}, with the search for the text), or runs the
 * full search when there is none (or the text changed since the hits came); Shift+Enter and "Show all" run the
 * full search. A kind chip filters the dropdown and the full search, and re-runs the search at once while the
 * dropdown is closed. Pasting a link, item string, Wowhead URL or bare number over the whole text opens that page;
 * pasting {@code [Name]} searches for the name ({@link PasteParser}). Only what the user asked for goes to the recent
 * searches: the text of a search run with Enter, Shift+Enter or "Show all" (or a recent one run again), and the name
 * of a hit opened; never the text of a type-ahead lookup, a chip's re-run or a paste.
 * In the compact window ({@link #setCompact}) the "Search:" label goes and the chips fold into a {@link KindMenu}.
 */
public final class SearchBar extends JPanel {
  public static final int DEBOUNCE_MILLIS = 250;
  /** The accessible name of the search box's clear button, which shows only an icon. */
  public static final String CLEAR_NAME = "Clear search";
  private static final Logger LOG = LoggerFactory.getLogger(SearchBar.class);

  /** What a type-ahead lookup found: the best hits, and how many hits each kind has. */
  public record Lookup(List<SearchHit> hits, Map<EntityKind, Integer> counts) {
    public Lookup {
      hits = List.copyOf(hits);
      counts = Map.copyOf(counts);
    }
  }

  /** Looks up type-ahead hits; called on the worker. */
  @FunctionalInterface
  public interface Source {
    Lookup lookup(String text, Optional<EntityKind> kind);

    /** Only the per-kind counts of {@code text}, for a search that runs without type-ahead. */
    default Map<EntityKind, Integer> counts(String text) {
      return lookup(text, Optional.empty()).counts();
    }

    /** The top {@value SearchDropdown#MAX_HITS} hits of {@code repo} and its per-kind counts. */
    static Source of(SearchRepository repo) {
      return new Source() {
        @Override
        public Lookup lookup(String text, Optional<EntityKind> kind) {
          List<SearchHit> hits = repo.search(text, kind, SearchDropdown.MAX_HITS);
          ListEntries.warmIcons(hits); // on the worker: the dropdown's renderer only looks in the cache
          return new Lookup(hits, repo.countsByKind(text));
        }

        @Override
        public Map<EntityKind, Integer> counts(String text) {
          return repo.countsByKind(text);
        }
      };
    }
  }

  /**
   * Where the search bar sends what the user picks: searches and pasted pages to {@link #accept}, a hit picked in
   * the dropdown to {@link #openHit} with the search it was picked for.
   */
  @FunctionalInterface
  public interface Opener extends Consumer<ViewTarget> {
    /** Opens {@code hit}, picked in the dropdown for {@code search}; by default only the hit. */
    default void openHit(ViewTarget.Entity hit, ViewTarget.Search search) {
      accept(hit);
    }

    /** {@link #openHit(ViewTarget.Entity, ViewTarget.Search)} of a hit called {@code title}; by default the same. */
    default void openHit(ViewTarget.Entity hit, ViewTarget.Search search, String title) {
      openHit(hit, search);
    }
  }

  private final Opener onOpen;
  private final UiState state;
  private final MasterList.Delay delay;
  private final SearchDropdown dropdown;
  private final KindChips chips = new KindChips();
  private final KindMenu kindMenu = new KindMenu(chips);
  private final JLabel label = new JLabel("Search:");
  private final JTextField text = new JTextField(32) {
    @Override
    public void paste() {
      if (isEditable() && isEnabled()) {
        String clip = clipboardText();
        if (clip != null && pasteIntercepted(clip)) {
          return;
        }
      }
      super.paste();
    }
  };
  private Source source;
  private Executor worker;
  /** Bumped by every text change and lookup, so only the newest lookup's result is shown. */
  private int generation;
  private boolean focused;
  /** Set by {@link #focus()} so the focus event it causes counts as a deliberate entry into the box. */
  private boolean deliberateFocus;
  /** Set while the text is changed by code (a recent search or a pasted name), which then searches at once. */
  private boolean quiet;

  /** A search bar that opens what the user picks through {@code onOpen} and keeps recent searches in {@code state}. */
  public SearchBar(Opener onOpen, UiState state) {
    this(onOpen, state, MasterList.timerDelay(DEBOUNCE_MILLIS), new SearchDropdown());
  }

  SearchBar(Opener onOpen, UiState state, MasterList.Delay delay, SearchDropdown dropdown) {
    super(new BorderLayout(6, 0));
    this.onOpen = Objects.requireNonNull(onOpen, "onOpen");
    this.state = Objects.requireNonNull(state, "state");
    this.delay = Objects.requireNonNull(delay, "delay");
    this.dropdown = Objects.requireNonNull(dropdown, "dropdown");
    text.addContainerListener(new ContainerAdapter() {
      @Override
      public void componentAdded(ContainerEvent e) { // the look and feel adds the clear button as a child
        if (e.getChild() instanceof AbstractButton b) {
          b.getAccessibleContext().setAccessibleName(CLEAR_NAME);
          b.setToolTipText(CLEAR_NAME);
        }
      }
    });
    text.putClientProperty("JTextField.placeholderText", "Search items, NPCs, quests, zones… (Ctrl+F)");
    text.putClientProperty("JTextField.showClearButton", true);
    text.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        textChanged();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        textChanged();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        textChanged();
      }
    });
    text.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        focusChanged(true, e.getCause());
      }

      @Override
      public void focusLost(FocusEvent e) {
        focusChanged(false);
      }
    });
    text.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        clicked();
      }
    });
    dropdown.install(text, new SearchDropdown.Listener() {
      @Override
      public void open(ListEntry hit) {
        openHit(hit);
      }

      @Override
      public void showAll() {
        commit(true);
      }

      @Override
      public void searchRecent(String recent) {
        setTextQuietly(recent);
        commit(true);
      }

      @Override
      public void closed() {
        stop();
      }
    });
    chips.onSelect(kind -> kindChanged());
    setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
    add(label, BorderLayout.WEST);
    add(text, BorderLayout.CENTER);
    add(chips, BorderLayout.EAST);
  }

  /** Starts type-ahead: lookups go to {@code source} on {@code worker}. Until then typing looks nothing up. */
  public void attach(Source source, Executor worker) {
    this.source = Objects.requireNonNull(source, "source");
    this.worker = Objects.requireNonNull(worker, "worker");
  }

  /** Moves the keyboard focus to the search box and selects its text; an empty box then shows the recent searches. */
  public void focus() {
    markFocusDeliberate();
    if (!text.requestFocusInWindow()) {
      deliberateFocus = false;
    }
    text.selectAll();
  }

  /** Makes the next focus gain count as a deliberate entry into the box (what {@link #focus()} does). */
  void markFocusDeliberate() {
    deliberateFocus = true;
  }

  /**
   * A click into the box. When the box already has the focus (it was the window's last focus owner and the window
   * was just activated, so no mouse-caused focus gain arrives), an empty box with the list closed shows the recents.
   */
  void clicked() {
    if (focused && query().isEmpty() && !dropdown.isShowing()) {
      dropdown.showRecents(state.recentSearches());
    }
  }

  /**
   * The search box's steps of the Esc chain: closes the dropdown when it is open, else clears the text; returns
   * false when neither applied, so the window moves the focus on to the list. Either step drops the pending and
   * running lookups, and clearing does not show the recent searches, so one Esc never undoes another.
   */
  public boolean escape() {
    if (dropdown.isShowing()) {
      stop();
      return true;
    }
    if (!text.getText().isEmpty()) {
      setTextQuietly("");
      stop();
      chips.clearCounts();
      return true;
    }
    return false;
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    text.setEnabled(enabled);
    chips.setEnabled(enabled);
    kindMenu.setEnabled(enabled);
  }

  /** For the narrow compact window: no "Search:" label, and the kind menu in place of the chips; or back. */
  public void setCompact(boolean compact) {
    label.setVisible(!compact);
    remove(compact ? chips : kindMenu);
    add(compact ? kindMenu : chips, BorderLayout.EAST);
    kindMenu.refresh();
    revalidate();
    repaint();
  }

  public KindMenu kindMenu() {
    return kindMenu;
  }

  public JTextField textField() {
    return text;
  }

  public KindChips kindChips() {
    return chips;
  }

  /**
   * Whether the search box has the focus changed through a deliberate entry (a click into it, or {@link #focus()}):
   * the empty box then shows the recent searches; leaving it closes the list.
   */
  void focusChanged(boolean hasFocus) {
    focusChanged(hasFocus, FocusEvent.Cause.MOUSE_EVENT);
  }

  /**
   * Whether the search box has the focus changed, with how it got there. Only a deliberate entry (a click into the
   * box, or {@link #focus()} for Ctrl+F and {@code /}) opens the recent searches: focus that merely falls into the
   * box because the component that had it was removed (a page swap after a click on a link) or because the window
   * was activated must not pop the list open.
   */
  void focusChanged(boolean hasFocus, FocusEvent.Cause cause) {
    focused = hasFocus;
    boolean deliberate = deliberateFocus || cause == FocusEvent.Cause.MOUSE_EVENT;
    deliberateFocus = false;
    if (!hasFocus) {
      dropdown.hide();
    } else if (deliberate && query().isEmpty()) {
      dropdown.showRecents(state.recentSearches());
    }
  }

  /**
   * Handles pasting {@code clip}: when it replaces the whole text and {@link PasteParser} reads it, opens its page
   * or searches for its name and returns true; otherwise returns false for an ordinary paste.
   */
  boolean pasteIntercepted(String clip) {
    boolean replacesAll = text.getSelectionStart() == 0
        && text.getSelectionEnd() == text.getDocument().getLength();
    Optional<ViewTarget> target = replacesAll ? PasteParser.parse(clip) : Optional.empty();
    if (target.isEmpty()) {
      return false;
    }
    if (target.get() instanceof ViewTarget.Search s) {
      setTextQuietly(s.text());
      commit(false);
    } else {
      stop();
      onOpen.accept(target.get());
    }
    return true;
  }

  private void textChanged() {
    generation++;
    delay.cancel();
    dropdown.markStale();
    if (quiet) {
      return;
    }
    if (query().isEmpty()) {
      chips.clearCounts();
      if (focused) {
        dropdown.showRecents(state.recentSearches());
      } else {
        dropdown.hide();
      }
    } else {
      delay.schedule(this::lookUp);
    }
  }

  private void kindChanged() {
    if (query().isEmpty()) {
      return;
    }
    if (dropdown.isShowing()) {
      delay.cancel();
      dropdown.markStale(); // Enter must not open the old kind's top hit before the new rows come
      lookUp();
    } else {
      commit(false); // a re-run for the new kind, not a search the user ran
    }
  }

  /** Looks up hits and counts for the current text and kind on the worker; shows them if nothing overtook them. */
  private void lookUp() {
    String q = query();
    if (source == null || q.isEmpty()) {
      return;
    }
    Optional<EntityKind> kind = chips.selected();
    Source s = source;
    int lookup = ++generation;
    Async.run(worker, () -> s.lookup(q, kind), found -> {
      if (lookup == generation) {
        show(found, kind);
      }
    }, e -> {
      if (lookup == generation) {
        LOG.warn("Type-ahead for {} failed", q, e);
      }
    });
  }

  private void show(Lookup found, Optional<EntityKind> kind) {
    chips.setCounts(found.counts());
    if (focused) {
      int total = kind.map(k -> found.counts().getOrDefault(k, 0))
          .orElseGet(() -> found.counts().values().stream().mapToInt(Integer::intValue).sum());
      dropdown.showHits(found.hits().stream().map(ListEntries::of).toList(), total);
    }
  }

  /**
   * Runs the full search for the text and the selected kind, the text going to the recent searches when
   * {@code remember}; blank text does nothing.
   */
  private void commit(boolean remember) {
    stop();
    String q = query();
    if (q.isEmpty()) {
      return;
    }
    if (remember) {
      state.addRecentSearch(q);
    }
    countsFor(q);
    onOpen.accept(new ViewTarget.Search(q, chips.selected()));
  }

  /**
   * Updates the chip counts for {@code q} from the worker: a search that runs before (or without) a type-ahead
   * lookup for its text would otherwise leave the counts of older text on the chips.
   */
  private void countsFor(String q) {
    if (source == null) {
      return;
    }
    Source s = source;
    int lookup = ++generation;
    Async.run(worker, () -> s.counts(q), counts -> {
      if (lookup == generation) {
        chips.setCounts(counts);
      }
    }, e -> {
      if (lookup == generation) {
        LOG.warn("Counting hits for {} failed", q, e);
      }
    });
  }

  /**
   * Opens {@code entry}, picked for the text, with the search for the text and the selected kind (so the list can
   * show the hit among its search); the hit's name, not the text typed so far, goes to the recent searches.
   */
  private void openHit(ListEntry entry) {
    stop();
    ViewTarget.Entity hit = new ViewTarget.Entity(entry.ref());
    String q = query();
    if (q.isEmpty()) {
      onOpen.accept(hit);
      return;
    }
    state.addRecentSearch(entry.title() != null && !entry.title().isBlank() ? entry.title() : q);
    countsFor(q);
    onOpen.openHit(hit, new ViewTarget.Search(q, chips.selected()), entry.title());
  }

  /** Drops the pending and running lookups and closes the list. */
  private void stop() {
    delay.cancel();
    generation++;
    dropdown.hide();
  }

  private void setTextQuietly(String s) {
    quiet = true;
    try {
      text.setText(s);
    } finally {
      quiet = false;
    }
  }

  private String query() {
    return text.getText().strip();
  }

  private static String clipboardText() {
    try {
      return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
    } catch (Exception e) { // no text on the clipboard, the clipboard busy, or no clipboard at all
      return null;
    }
  }
}
