package dev.wowforever.app.ui;

import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.ui.widgets.MasterList;
import dev.wowforever.app.ui.widgets.RailTarget;
import javax.swing.JComponent;

/** What {@link AppController} drives: the main window in production, a headless stub in tests. */
public interface MainView {
  /** The optional shell; existing views retain their original behavior. */
  default UiExperience uiExperience() { return UiExperience.CLASSIC; }

  /** A committed page changed; the Journal shell updates its location and labelled navigation. */
  default void viewTargetChanged(dev.wowforever.app.nav.ViewTarget target) {}

  /** The master list left of the detail. */
  MasterList masterList();

  /** Replaces the right-hand detail area. */
  void setDetail(JComponent c);

  /** Enables or disables the back and forward buttons. */
  void setHistoryState(boolean canBack, boolean canForward);

  /** The status bar at the bottom. */
  StatusBar status();

  /** Shows {@code s} after the application name in the window title; empty for none. */
  void setTitleSuffix(String s);

  /** Revision of user edits to the search text, used to preserve input while a page is loading. */
  default long searchEditRevision() { return 0; }

  /** Quietly follows a committed entity page's loaded name, without searching or recording a recent. */
  default void followPageName(String displayName) {}

  /** Highlights the rail section of the page on screen, without opening it; null highlights none. */
  void selectRail(RailTarget target);

  /**
   * Whether the master list is on screen. The compact window has none: there a full search's results take the
   * detail area instead.
   */
  default boolean showsMasterList() {
    return true;
  }

  /**
   * Which drop rows the item and NPC pages show (the filter control beside their drop sections' headings); every
   * row unless set.
   */
  default SourceFilter sourceFilter() {
    return SourceFilter.ALL;
  }

  /** Saves {@code filter} as the drop source filter, which {@link #sourceFilter()} then answers. */
  default void setSourceFilter(SourceFilter filter) {}

  /**
   * A result was opened from the master list or the search box (not previewed): the master list may collapse
   * ({@link ListVisibility#resultOpened}).
   */
  default void onResultOpened() {}

  /** A search's results went into the master list: the list shows ({@link ListVisibility#resultsShown}). */
  default void onResultsShown() {}

  /** Whether zone maps pin quest givers, which their "Quests" toggle changes; off and not remembered unless set. */
  default BooleanSetting questPins() {
    return BooleanSetting.inMemory(false);
  }

  /** Saved Map/List choice for the Zones rail, defaulting to Map. */
  default BooleanSetting zonesMap() { return BooleanSetting.inMemory(true); }

  /** Saved zone-map patrol route setting. */
  default BooleanSetting routes() {
    return BooleanSetting.inMemory(false);
  }

}
