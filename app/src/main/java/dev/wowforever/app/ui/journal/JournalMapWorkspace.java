package dev.wowforever.app.ui.journal;

import java.awt.*;
import java.util.function.IntUnaryOperator;
import javax.swing.*;

/** A map and its inspector: side by side on desktop, a full-width drawer below the map on narrow windows. */
final class JournalMapWorkspace extends JPanel implements Scrollable {
  private final JComponent map;
  private final JComponent inspector;
  private final IntUnaryOperator mapHeight;
  private static final int GAP = 16;
  private static final int INSPECTOR_WIDTH = 280;

  JournalMapWorkspace(JComponent map, JComponent inspector, IntUnaryOperator mapHeight) {
    this.map = map; this.inspector = inspector; this.mapHeight = mapHeight;
    setLayout(null); setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder(12, 12, 18, 12));
    add(map); add(inspector);
  }

  private Dimension arrange(boolean layout) {
    Insets in = getInsets();
    int width = Math.max(1, getWidth() > 0 ? getWidth() - in.left - in.right : 1000);
    boolean side = width >= 840;
    int mapWidth = side ? width - INSPECTOR_WIDTH - GAP : width;
    int height = Math.max(80, mapHeight.applyAsInt(mapWidth));
    map.setSize(mapWidth, height); map.doLayout();
    int inspectorWidth = side ? INSPECTOR_WIDTH : width;
    inspector.setSize(inspectorWidth, Math.max(1, inspector.getHeight())); inspector.doLayout();
    int inspectorHeight = inspector.getPreferredSize().height;
    if (layout) {
      map.setBounds(in.left, in.top, mapWidth, height);
      inspector.setBounds(side ? in.left + mapWidth + GAP : in.left,
          side ? in.top : in.top + height + GAP, inspectorWidth, inspectorHeight);
    }
    return new Dimension(width + in.left + in.right, in.top + in.bottom
        + (side ? Math.max(height, inspectorHeight) : height + GAP + inspectorHeight));
  }
  @Override public void doLayout() { arrange(true); }
  @Override public Dimension getPreferredSize() { return arrange(false); }
  @Override public Dimension getMinimumSize() { return new Dimension(0, 0); }
  public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
  public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 24; }
  public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return Math.max(24, r.height - 24); }
  public boolean getScrollableTracksViewportWidth() { return true; }
  public boolean getScrollableTracksViewportHeight() { return false; }
}
