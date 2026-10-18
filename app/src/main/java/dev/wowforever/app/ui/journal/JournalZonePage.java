package dev.wowforever.app.ui.journal;

import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.*;
import dev.wowforever.app.ui.detail.DetailPanels;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.pages.*;
import dev.wowforever.app.ui.widgets.*;
import java.awt.BorderLayout;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;

/** Journal map-first zone page, assembled exclusively from the existing off-EDT load and async layer source. */
public final class JournalZonePage extends JPanel implements PageState {
  private final JTabbedPane tabs = new JTabbedPane();
  private final ZonePage records;
  private final ZoneMapPanel mapPanel;
  private final JournalMapInspector inspector;

  public JournalZonePage(DetailPanels.LoadedZone loaded, Repositories repos, Navigator nav, Executor worker,
      StatusSink status, BooleanSetting quests, BooleanSetting routes) {
    this(loaded, repos, nav, worker, status, quests, routes, ClipboardPort.system());
  }

  public JournalZonePage(DetailPanels.LoadedZone loaded, Repositories repos, Navigator nav, Executor worker,
      StatusSink status, BooleanSetting quests, BooleanSetting routes, ClipboardPort clipboard) {
    super(new BorderLayout());
    var detail = loaded.detail();
    JPanel heading = new JPanel(new BorderLayout()); heading.setOpaque(false);
    if (!loaded.breadcrumbs().isEmpty()) heading.add(new BreadcrumbTrail(loaded.breadcrumbs(), nav), BorderLayout.NORTH);
    heading.add(new HeaderPanel(detail.summary().displayName(), detail.levelBand()
        .map(b -> "Levels " + b.min() + "–" + b.max()).orElse("Zone map")), BorderLayout.CENTER);
    add(heading, BorderLayout.NORTH);
    records = new ZonePage(detail, null, loaded.breadcrumbs(), loaded.foreverContext(), nav);
    if (loaded.map() != null) {
      AtomicReference<JournalMapInspector> selection = new AtomicReference<>();
      mapPanel = new ZoneMapPanel(detail.summary().id(), loaded.map(), loaded.pins(),
          ZoneMapPanel.PinSource.of(repos.zones()), worker, status, target -> {
            if (target instanceof ViewTarget.Entity entity && selection.get() != null) selection.get().selectEntity(entity.ref());
          }, quests, routes, loaded.travel());
      mapPanel.mapView().setZoneName(detail.summary().displayName());
      // This Journal-owned panel puts its controls and evidence in the inspector instead of duplicating them.
      var mapLayout = (BorderLayout) mapPanel.getLayout();
      mapLayout.getLayoutComponent(BorderLayout.NORTH).setVisible(false);
      mapLayout.getLayoutComponent(BorderLayout.SOUTH).setVisible(false);
      inspector = JournalMapInspector.zone(detail.summary().displayName(), mapPanel, loaded.travel(), nav, clipboard);
      selection.set(inspector);
      var workspace = new JournalMapWorkspace(mapPanel, inspector, width -> {
        mapPanel.setSize(width, Math.max(1, mapPanel.getHeight())); mapPanel.doLayout();
        return mapPanel.getPreferredSize().height;
      });
      var scroll = new JScrollPane(workspace, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      scroll.setBorder(BorderFactory.createEmptyBorder()); scroll.getVerticalScrollBar().setUnitIncrement(24);
      tabs.addTab("Map", scroll); tabs.setMnemonicAt(0, java.awt.event.KeyEvent.VK_M);
    } else {
      mapPanel = null; inspector = null;
      var note = JournalSurface.copy("No map art is recorded for this zone. Browse its recorded content below.");
      tabs.addTab("Map coverage", note);
    }
    tabs.addTab("Zone records", records);
    add(tabs, BorderLayout.CENTER);
  }

  private record State(int tab, Object records, JournalMapInspector.Location selection, Map<String, Boolean> layers) {}
  @Override public Object saveState() {
    Map<String, Boolean> layers = new LinkedHashMap<>();
    if (mapPanel != null) for (MapLayer layer : MapLayer.REGISTERED) layers.put(layer.id(), mapPanel.mapView().isLayerVisible(layer));
    return new State(tabs.getSelectedIndex(), records.saveState(), inspector == null ? null : inspector.selected(), Map.copyOf(layers));
  }
  @Override public void restoreState(Object state) {
    if (!(state instanceof State saved)) return;
    tabs.setSelectedIndex(Math.min(saved.tab(), tabs.getTabCount() - 1)); records.restoreState(saved.records());
    if (mapPanel != null) for (MapLayer layer : MapLayer.REGISTERED) {
      Boolean visible = saved.layers().get(layer.id()); if (visible != null) mapPanel.mapView().setLayerVisible(layer, visible);
    }
    if (inspector != null) inspector.showSelection(saved.selection());
  }
  public ZoneMapPanel mapPanel() { return mapPanel; }
  public JournalMapInspector inspector() { return inspector; }
}
