package dev.wowforever.app.ui.detail;

import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Async;
import dev.wowforever.app.ui.BooleanSetting;
import dev.wowforever.app.ui.StatusSink;
import java.util.concurrent.Executor;
import javax.swing.JComponent;

/**
 * Short ways for tests to build detail pages through {@link DetailPanels}, with the settings the app would pass
 * filled in: every drop row, a zone map's "Quests" toggle off and not remembered.
 */
public final class TestPages {
  private TestPages() {}

  /** The page for a record from {@link DetailPanels#load}; a zone map loads more pins in a SwingWorker. */
  public static JComponent render(Object detail, Repositories repos, Navigator nav) {
    return render(detail, repos, nav, Async.SWING_WORKER, StatusSink.NONE);
  }

  /** The page as above, a zone map loading more pins on {@code worker} and reporting to {@code status}. */
  public static JComponent render(Object detail, Repositories repos, Navigator nav, Executor worker,
      StatusSink status) {
    return render(detail, repos, nav, worker, status, SourceFilter.ALL);
  }

  /** The page as above, the item and NPC pages showing the drop rows {@code filter} keeps. */
  public static JComponent render(Object detail, Repositories repos, Navigator nav, Executor worker,
      StatusSink status, SourceFilter filter) {
    return DetailPanels.render(detail, repos, nav, worker, status, filter, BooleanSetting.inMemory(false));
  }

  /** {@link DetailPanels#load} then {@link #render} on the calling thread, or the not-found label. */
  public static JComponent build(EntityRef ref, Repositories repos, Navigator nav) {
    return DetailPanels.load(ref, repos).map(d -> render(d, repos, nav)).orElseGet(() -> DetailPanels.notFound(ref));
  }
}
