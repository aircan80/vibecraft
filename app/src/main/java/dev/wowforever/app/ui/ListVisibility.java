package dev.wowforever.app.ui;

/**
 * When the master list shows in the full window: it shows while searching (a search's results fill it) and collapses
 * when a result is opened (Enter or a click in the list, a hit picked in the search box's dropdown), so the page gets
 * the width; arrowing through the list (a preview) never collapses it. A window at least {@value #WIDE_PX} px wide
 * keeps the list open, and Ctrl+B pins it: shown by Ctrl+B, the list stays until Ctrl+B hides it again.
 *
 * <p>Only the pin is remembered ({@link UiState#listVisible}): a pinned list starts shown, an unpinned one hidden
 * until the next search.
 */
public final class ListVisibility {
  /** The window width, in pixels, from which opening a result keeps the list open. */
  public static final int WIDE_PX = 1600;

  private boolean visible;
  private boolean pinned;

  /** The list as last saved: {@code savedVisible} is whether Ctrl+B pinned it, so it starts shown. */
  public ListVisibility(boolean savedVisible) {
    this.pinned = savedVisible;
    this.visible = savedVisible;
  }

  /** A search's results went into the list: it shows. */
  public void resultsShown() {
    visible = true;
  }

  /** A row of the list was previewed (arrowing through it): nothing changes. */
  public void previewed() {
    // the list stays as it is: the user is still choosing
  }

  /**
   * A result was opened in a window {@code windowWidth} px wide: the list collapses unless Ctrl+B pinned it or the
   * window is at least {@value #WIDE_PX} px wide.
   */
  public void resultOpened(int windowWidth) {
    if (!pinned && windowWidth < WIDE_PX) {
      visible = false;
    }
  }

  /** Ctrl+B: a shown list hides (and is no longer pinned); a hidden one shows and is pinned. */
  public void toggle() {
    visible = !visible;
    pinned = visible;
  }

  /** Whether the list is on screen. */
  public boolean visible() {
    return visible;
  }

  /** Whether Ctrl+B keeps the list open; the state {@link UiState#listVisible} saves. */
  public boolean pinned() {
    return pinned;
  }
}
