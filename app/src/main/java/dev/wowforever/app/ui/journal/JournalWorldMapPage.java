package dev.wowforever.app.ui.journal;

import dev.wowforever.app.model.WorldMapCatalog;
import dev.wowforever.app.model.WorldMapRegion;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.pages.PageState;
import dev.wowforever.app.ui.pages.WorldMapPage;
import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javax.swing.*;

/** Journal atlas workspace; the original geographic map keeps its navigation and genuine assignments. */
public final class JournalWorldMapPage extends JPanel implements PageState {
  private final JournalMapInspector inspector;

  public JournalWorldMapPage(ViewTarget.WorldMap target, WorldMapCatalog catalog, BufferedImage art,
      Consumer<WorldMapRegion> open, Consumer<Long> focused, Runnable root, Runnable up, Runnable list) {
    super(new BorderLayout());
    String name = target.uiMapId() == 947 ? "Azeroth" : catalog.map(target.uiMapId())
        .map(WorldMapRegion::name).orElse("Map " + target.uiMapId());
    inspector = JournalMapInspector.world(name, catalog, catalog.children(target.uiMapId()), open, focused);
    inspector.selectRegion(target.focusedChildId());
    var map = new WorldMapPage(target, catalog, art, region -> {
      inspector.selectRegion(region.uiMapId()); open.accept(region);
    }, child -> { inspector.selectRegion(child); focused.accept(child); }, root, up, list);
    var workspace = new JournalMapWorkspace(map, inspector,
        width -> 100 + (int) Math.round(Math.min(width, art == null ? 1002 : art.getWidth())
            * (art == null ? 668.0 / 1002 : (double) art.getHeight() / art.getWidth())));
    JScrollPane scroll = new JScrollPane(workspace, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setBorder(BorderFactory.createEmptyBorder()); scroll.getVerticalScrollBar().setUnitIncrement(24);
    add(scroll);
  }

  @Override public Object saveState() { return inspector.selected(); }
  @Override public void restoreState(Object state) {
    if (state instanceof JournalMapInspector.Location location) inspector.showSelection(location);
  }
  public JournalMapInspector inspector() { return inspector; }
}
