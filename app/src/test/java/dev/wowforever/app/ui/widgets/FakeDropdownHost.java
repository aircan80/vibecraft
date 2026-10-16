package dev.wowforever.app.ui.widgets;

import javax.swing.JComponent;

/** A {@link SearchDropdown.Host} that records whether the dropdown is on screen instead of opening a window. */
public final class FakeDropdownHost implements SearchDropdown.Host {
  public boolean showing;

  @Override
  public void show(JComponent anchor, JComponent content) {
    showing = true;
  }

  @Override
  public void hide() {
    showing = false;
  }
}
