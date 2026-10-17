package dev.wowforever.app.ui.pages;

/**
 * A page whose view state (how far it is scrolled, which encounter is selected) survives being built again for the
 * same entity, say under another drop source filter or after the window switched layouts: the controller saves the
 * old page's state and hands it to the new page.
 */
public interface PageState {
  /** What the page shows of itself right now; opaque to callers. */
  Object saveState();

  /**
   * Shows {@code state}, saved from a page of the same entity; anything else (null, another page's state) is
   * ignored. The page may be laid out only later: what it cannot apply yet it applies once it is.
   */
  void restoreState(Object state);
}
