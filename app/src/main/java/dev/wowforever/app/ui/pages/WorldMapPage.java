package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.WorldMapCatalog;
import dev.wowforever.app.model.WorldMapRegion;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.widgets.BreadcrumbTrail;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;

/** Geographical drill-down. Every region is a real keyboard-focusable button over the map art. */
public final class WorldMapPage extends JPanel {
  private final List<JButton> regions = new ArrayList<>();

  public WorldMapPage(ViewTarget.WorldMap target, WorldMapCatalog catalog, BufferedImage art,
      Consumer<WorldMapRegion> open, Consumer<Long> focused, Runnable root, Runnable up, Runnable list) {
    super(new BorderLayout(0, 8));
    setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    setFocusable(true);
    JPanel header = new JPanel(new BorderLayout());
    BreadcrumbTrail breadcrumb = new BreadcrumbTrail(catalog.breadcrumbs(target.uiMapId()), parent -> {
      long id=((ViewTarget.WorldMap)parent).uiMapId();
      if(id==947)root.run(); else catalog.map(id).ifPresent(open);
    });
    header.add(breadcrumb, BorderLayout.CENTER);
    header.add(viewToggle(true, root, list), BorderLayout.EAST);
    add(header, BorderLayout.NORTH);
    JPanel body = new MapBody();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    var children = catalog.children(target.uiMapId()).stream()
        .sorted(Comparator.comparingDouble(WorldMapRegion::y0).thenComparingDouble(WorldMapRegion::x0)
            .thenComparingLong(WorldMapRegion::uiMapId)).toList();
    RegionCanvas canvas = new RegionCanvas(art);
    canvas.setAlignmentX(LEFT_ALIGNMENT);
    for (WorldMapRegion region : children) {
      boolean navigable = !catalog.children(region.uiMapId()).isEmpty() || region.zoneId() != null;
      JButton button = new RegionButton(region.name(), region.placed());
      String facts = facts(region);
      button.getAccessibleContext().setAccessibleName(region.name() + facts + (catalog.children(region.uiMapId()).isEmpty()
          ? ", zone" : ", continent") + (navigable ? "" : ", not in the database yet"));
      button.setToolTipText(region.name() + facts + (!navigable ? " · Not in the database yet"
          : !region.hasArt() ? " · No map art in this client build" : ""));
      button.addActionListener(e -> {
        focused.accept(region.uiMapId());
        if (navigable) open.accept(region);
      });
      button.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "open-region");
      button.getActionMap().put("open-region", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { button.doClick(); }
      });
      button.addFocusListener(new FocusAdapter() {
        @Override public void focusGained(FocusEvent e) { focused.accept(region.uiMapId()); canvas.repaint(); }
        @Override public void focusLost(FocusEvent e) { canvas.repaint(); }
      });
      regions.add(button);
      if (region.placed()) canvas.addRegion(region, button);
      else {
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setText(region.name() + " · Position unavailable");
      }
    }
    canvas.stackRegions();
    body.add(canvas);
    for (JButton region : regions) if (region.getParent() == null) body.add(region);
    if (children.isEmpty()) body.add(new JLabel("World map geography is unavailable in this database. Use List to browse zones."));
    JScrollPane scroll = new JScrollPane(body);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    add(scroll, BorderLayout.CENTER);
    add(new JLabel(catalog.sourceBuild().isBlank() ? "Archived client geography" : "Map hierarchy: client " + catalog.sourceBuild()), BorderLayout.SOUTH);
    bind("BACK_SPACE", "up", up);
    bind("ESCAPE", "clear-region", () -> { focused.accept(null); requestFocusInWindow(); });
    if (target.focusedChildId() != null) {
      for (int i = 0; i < children.size(); i++) {
        if (children.get(i).uiMapId() == target.focusedChildId()) {
          JButton restore = regions.get(i);
          SwingUtilities.invokeLater(restore::requestFocusInWindow);
        }
      }
    }
  }

  private static String facts(WorldMapRegion region) {
    if (region.zoneId() == null) return "";
    return region.levelBand().map(b -> " · Levels " + b.min() + "–" + b.max()).orElse("")
        + " · " + region.instanceCount() + " instances · " + region.questCount() + " quests";
  }

  private void bind(String stroke, String name, Runnable action) {
    getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(stroke), name);
    getActionMap().put(name, new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { action.run(); }
    });
  }

  /** The same switch heads Map and the existing zone list. */
  public static JComponent viewToggle(boolean map, Runnable showMap, Runnable showList) {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.TRAILING, 2, 0));
    JToggleButton mapButton = new JToggleButton("Map", map);
    JToggleButton listButton = new JToggleButton("List", !map);
    ButtonGroup group = new ButtonGroup();
    group.add(mapButton); group.add(listButton);
    mapButton.addActionListener(e -> { if (!map) showMap.run(); });
    listButton.addActionListener(e -> { if (map) showList.run(); });
    panel.add(mapButton); panel.add(listButton);
    return panel;
  }

  private static final class MapBody extends JPanel implements Scrollable {
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
  }

  static final class RegionCanvas extends JPanel implements Scrollable {
    private final BufferedImage art;
    private final List<WorldMapRegion> geometry = new ArrayList<>();
    private final List<JButton> buttons = new ArrayList<>();
    RegionCanvas(BufferedImage art) {
      this.art = art;
      setLayout(null);
      setFocusTraversalPolicyProvider(true);
      setFocusTraversalPolicy(new FocusTraversalPolicy() {
        @Override public Component getComponentAfter(Container c, Component current) {
          int i = buttons.indexOf(current); return i + 1 < buttons.size() ? buttons.get(i + 1) : null;
        }
        @Override public Component getComponentBefore(Container c, Component current) {
          int i = buttons.indexOf(current); return i > 0 ? buttons.get(i - 1) : null;
        }
        @Override public Component getFirstComponent(Container c) { return buttons.isEmpty() ? null : buttons.getFirst(); }
        @Override public Component getLastComponent(Container c) { return buttons.isEmpty() ? null : buttons.getLast(); }
        @Override public Component getDefaultComponent(Container c) { return getFirstComponent(c); }
      });
    }
    void addRegion(WorldMapRegion region, JButton button) { geometry.add(region); buttons.add(button); add(button); }
    void stackRegions() {
      List<Integer> indices = new ArrayList<>();
      for (int i = 0; i < geometry.size(); i++) indices.add(i);
      indices.sort(Comparator.comparingDouble(i -> {
        var r = geometry.get(i); return (r.x1() - r.x0()) * (r.y1() - r.y0());
      }));
      for (int i = 0; i < indices.size(); i++) setComponentZOrder(buttons.get(indices.get(i)), i);
    }
    private int mapWidth() { return Math.min(getWidth(), art == null ? 1002 : art.getWidth()); }
    private int mapHeight() { return (int) Math.round(mapWidth() * (art == null ? 668.0 / 1002 : (double) art.getHeight() / art.getWidth())); }
    @Override public Dimension getPreferredSize() {
      int width = getParent() == null || getParent().getWidth() <= 0 ? 1002 : getParent().getWidth();
      width = Math.min(width, art == null ? 1002 : art.getWidth());
      return new Dimension(width, (int) Math.round(width * (art == null ? 668.0 / 1002 : (double) art.getHeight() / art.getWidth())));
    }
    @Override public Dimension getMinimumSize() { return new Dimension(0, 0); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
    @Override public void doLayout() {
      int width = mapWidth(), height = mapHeight();
      for (int i = 0; i < geometry.size(); i++) {
        WorldMapRegion r = geometry.get(i);
        int x = (int) Math.round(r.x0() * width), y = (int) Math.round(r.y0() * height);
        buttons.get(i).setBounds(x, y, Math.max(1, (int) Math.round(r.x1() * width) - x),
            Math.max(1, (int) Math.round(r.y1() * height) - y));
      }
    }
    @Override protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (art != null) g.drawImage(art, 0, 0, mapWidth(), mapHeight(), null);
      else { g.setColor(new Color(0x202830)); g.fillRect(0, 0, mapWidth(), mapHeight()); }
    }
    record LabelPlate(Rectangle bounds, String text, JButton button) {}

    /** Collision decisions use the same coordinates and font as the painted plates. */
    List<LabelPlate> labelPlates() {
      List<LabelPlate> result = new ArrayList<>();
      // Focus and hover have priority; their complete names remain available at compact widths.
      for (boolean activePass : new boolean[]{true, false}) for (JButton button : buttons) {
        boolean active = button.hasFocus() || button.getModel().isRollover();
        if (active != activePass || (!active && (mapWidth() <= 440 || button.getWidth() < 40))) continue;
        FontMetrics fm = button.getFontMetrics(labelFont(button));
        int available = active ? mapWidth() - 12 : button.getWidth() - 12;
        String text = fitLabel(button.getText(), fm, Math.max(0, available));
        if (text.isEmpty()) continue;
        int width = fm.stringWidth(text) + 8, height = fm.getHeight() + 4;
        int x = button.getX() + (button.getWidth() - width) / 2;
        int y = button.getY() + (button.getHeight() - height) / 2;
        Rectangle plate = new Rectangle(Math.max(2, Math.min(x, mapWidth() - width - 2)),
            Math.max(1, Math.min(y, mapHeight() - height - 1)), width, height);
        if (!active && result.stream().anyMatch(existing -> existing.bounds().intersects(plate))) continue;
        result.add(new LabelPlate(plate, text, button));
      }
      return result;
    }

    private static Font labelFont(JButton button) {
      return button.getFont().deriveFont(Math.min(button.getFont().getSize2D(), 11f));
    }

    @Override protected void paintChildren(Graphics graphics) {
      super.paintChildren(graphics);
      Graphics2D g = (Graphics2D) graphics.create();
      for (LabelPlate label : labelPlates()) {
        Rectangle plate = label.bounds();
        g.setFont(labelFont(label.button()));
        g.setColor(new Color(12, 16, 23, 240));
        g.fillRoundRect(plate.x, plate.y, plate.width, plate.height, 6, 6);
        g.setColor(Color.WHITE);
        g.drawString(label.text(), plate.x + 4, plate.y + 2 + g.getFontMetrics().getAscent());
      }
      g.dispose();
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
  }

  /** Keeps the beginning of a region name readable inside its clickable rectangle. */
  static String fitLabel(String text, FontMetrics metrics, int availableWidth) {
    if (metrics.stringWidth(text) <= availableWidth) return text;
    String ellipsis = "…";
    if (metrics.stringWidth(ellipsis) > availableWidth) return "";
    int length = text.length();
    while (length > 0 && metrics.stringWidth(text.substring(0, length) + ellipsis) > availableWidth) length--;
    return text.substring(0, length) + ellipsis;
  }

  private static final class RegionButton extends JButton {
    private final boolean placed;
    RegionButton(String name, boolean placed) {
      super(name); this.placed = placed;
      if (placed) { setOpaque(false); setContentAreaFilled(false); setBorderPainted(false); }
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      addMouseListener(new MouseAdapter() {
        @Override public void mouseEntered(MouseEvent e) { if (getParent() != null) getParent().repaint(); }
        @Override public void mouseExited(MouseEvent e) { if (getParent() != null) getParent().repaint(); }
      });
    }
    @Override protected void paintComponent(Graphics graphics) {
      if (!placed) { super.paintComponent(graphics); return; }
      Graphics2D g = (Graphics2D) graphics.create();
      boolean active = hasFocus() || getModel().isRollover();
      g.setColor(active ? new Color(0xE4BC69) : new Color(228, 188, 105, 55));
      g.drawRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1));
      g.dispose();
    }
  }
}
