package dev.wowforever.app.ui.widgets;

import javax.swing.JComponent;

/** A {@link SearchDropdown.Host} that records whether the dropdown is on screen instead of opening a window. */
public final class FakeDropdownHost implements SearchDropdown.Host {
  public boolean showing;
  /** The height of the last show. */
  public int height;
  /** Whether a show succeeds; false stands for a search box that is not on screen. */
  public boolean anchorShowing = true;

  @Override
  public boolean show(JComponent anchor, JComponent content, int height) {
    this.height = height;
    showing = anchorShowing;
    return anchorShowing;
  }

  @Override
  public void hide() {
    showing = false;
  }
}
