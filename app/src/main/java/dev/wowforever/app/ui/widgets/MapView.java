package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.TravelMapData;
import dev.wowforever.app.model.Route;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.QuestPin;
import dev.wowforever.app.model.QuestRef;
import dev.wowforever.app.model.SpawnPin;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.HtmlText;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.BasicStroke;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Locale;
import dev.wowforever.app.ui.WayCommand;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.ToolTipManager;

/**
 * A zone map, already decoded (off the EDT, by {@code DetailPanels.load}), scaled to the component's width keeping
 * its aspect ratio and never enlarged beyond its own size, with spawn pins on it. A pin sits at its map percentages
 * of the drawn image ({@link Projection}). Pins within {@value #CLUSTER_PX} px of each other are drawn as one marker
 * with a count badge, in the colour of its most important member ({@link PinKind} order). Hovering a marker tells
 * its name (and count) in a tooltip; clicking it opens its entity, or, for a marker of several entities, a menu of
 * them. The page's own pins ({@link PinKind#SELF}) open nothing. Quest givers' pins are a layer of their own, drawn
 * above the others as {@code "!"} and {@code "?"} glyphs ({@link PinGlyphs}) and clustered only among themselves;
 * they tell and open their quests. Hit testing uses the painting maths, the quest layer first.
 */
public final class MapView extends JComponent {
  /** Pins closer than this (logical px, inclusive) share one marker. */
  public static final int CLUSTER_PX = 8;
  /** A pointer this close (logical px, inclusive) to a marker's centre is on it. */
  public static final int HIT_PX = 8;
  /** Most entries in a marker's menu or tooltip. */
  static final int LIST_CAP = 25;
  /** Most givers a quest marker's menu lists after its quests. */
  static final int GIVER_CAP = 10;
  /** What assistive technology calls the map. */
  public static final String ACCESSIBLE_NAME = "Zone map";
  private static final int RADIUS = 4;
  private static final int SELF_RADIUS = 6;
  private static final int BOSS_RADIUS = 5;
  static final Color OUTLINE = new Color(0x0C0E12);
  private static final Color SILVER = new Color(0xC9CED6);
  private static final Color HOVER = new Color(0xFFFFFF);

  /**
   * What a pin stands for, most important first: a marker of several pins takes the first kind among them. The
   * quest kinds are quest givers, drawn as glyphs ({@link PinGlyphs}) in a layer of their own above the others.
   */
  public enum Layer { GIVERS, OBJECTIVES, SPAWNS }

  public enum PinKind {
    SELF, BOSS, RARE, OBJECT, NPC, QUEST_START, QUEST_END, KILL, COLLECT, INTERACT, EXPLORE;

    public Layer layer() {
      return quest() ? Layer.GIVERS : switch (this) {
        case KILL, COLLECT, INTERACT, EXPLORE -> Layer.OBJECTIVES;
        default -> Layer.SPAWNS;
      };
    }

    /** Whether pins of this kind are quest givers, which cluster only among themselves. */
    public boolean quest() {
      return this == QUEST_START || this == QUEST_END;
    }
  }

  /**
   * A pin: the entity it opens, the name its tooltip tells and its place as map percentages (0 to 100). A quest
   * giver's pin also has the quests it starts and ends there (the starts first), which its tooltip lists and a click
   * opens; an {@code emphasised} one is drawn larger.
   */
  public record Pin(EntityRef ref, String name, double x, double y, PinKind kind, List<QuestRef> quests,
      boolean emphasised, int number) {
    public Pin(EntityRef ref, String name, double x, double y, PinKind kind, List<QuestRef> quests, boolean emphasised) {
      this(ref, name, x, y, kind, quests, emphasised, 0);
    }

    public static Pin objective(EntityRef ref, String name, double x, double y, PinKind kind, int number) {
      if (kind.layer() != Layer.OBJECTIVES || number < 1) {
        throw new IllegalArgumentException("Objective kind and positive number required");
      }
      return new Pin(ref, name, x, y, kind, List.of(), false, number);
    }

    public Pin {
      Objects.requireNonNull(ref, "ref");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(kind, "kind");
      quests = List.copyOf(quests);
    }

    /** A pin without quests, not emphasised. */
    public Pin(EntityRef ref, String name, double x, double y, PinKind kind) {
      this(ref, name, x, y, kind, List.of(), false);
    }

    /**
     * The pin of a quest giver's spawn point: {@link PinKind#QUEST_START} when it starts any of its quests there
     * (starts win), else {@link PinKind#QUEST_END}; its quests with the starts first.
     */
    public static Pin of(QuestPin p) {
      List<QuestRef> quests = Stream.concat(p.quests().stream().filter(QuestRef::starts),
          p.quests().stream().filter(q -> !q.starts())).toList();
      return new Pin(p.giver(), p.displayName(), p.x(), p.y(), p.starts() ? PinKind.QUEST_START : PinKind.QUEST_END,
          quests, false);
    }

    /**
     * A quest page's pin of a giver of its quest: emphasised, a {@code "!"} when the giver starts it
     * ({@code starts}), else a {@code "?"}; it lists no quests and opens the giver.
     */
    public static Pin giver(EntityRef ref, String name, double x, double y, boolean starts) {
      return new Pin(ref, name, x, y, starts ? PinKind.QUEST_START : PinKind.QUEST_END, List.of(), true);
    }

    /**
     * The pin of a spawn point: {@link PinKind#SELF} when it is the page's own entity ({@code self}), else
     * {@link PinKind#OBJECT} for an object, {@link PinKind#BOSS} for rank 3, {@link PinKind#RARE} for ranks 2 and 4
     * (rare elite, rare) and {@link PinKind#NPC} otherwise.
     */
    public static Pin of(SpawnPin p, boolean self) {
      PinKind kind;
      if (self) {
        kind = PinKind.SELF;
      } else if (p.ref().kind() == EntityKind.OBJECT) {
        kind = PinKind.OBJECT;
      } else if (p.rank() != null && p.rank() == 3) {
        kind = PinKind.BOSS;
      } else if (p.rank() != null && (p.rank() == 2 || p.rank() == 4)) {
        kind = PinKind.RARE;
      } else {
        kind = PinKind.NPC;
      }
      return new Pin(p.ref(), p.displayName(), p.x(), p.y(), kind);
    }
  }

  /**
   * Where an image {@code imageWidth} x {@code imageHeight} is drawn in a component {@code componentWidth} wide: from
   * the top left corner, as wide as the component but never wider than the image, keeping the aspect ratio.
   */
  public record Projection(int imageWidth, int imageHeight, int componentWidth, double scale) {
    public Projection {
      if (!Double.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Positive UI scale required");
    }
    public Projection(int imageWidth, int imageHeight, int componentWidth) {
      this(imageWidth, imageHeight, componentWidth, UIScale.getUserScaleFactor());
    }

    public Optional<Point2D.Double> toMap(double cx, double cy) {
      if (drawnWidth() <= 0 || drawnHeight() <= 0 || cx < 0 || cy < 0
          || cx > drawnWidth() || cy > drawnHeight()) {
        return Optional.empty();
      }
      return Optional.of(new Point2D.Double(100 * cx / drawnWidth(), 100 * cy / drawnHeight()));
    }
    public int drawnWidth() {
      return Math.max(0, Math.min(componentWidth, (int) Math.round(imageWidth * scale)));
    }

    public int drawnHeight() {
      if (imageWidth <= 0) {
        return 0;
      }
      return (int) Math.round((double) drawnWidth() * imageHeight / imageWidth);
    }

    /** The component point of map percentages {@code x}, {@code y}. */
    public Point2D.Double toComponent(double x, double y) {
      return new Point2D.Double(x / 100 * drawnWidth(), y / 100 * drawnHeight());
    }
  }

  /** One marker: its pins (the most important first) and where it is drawn (at its first pin). */
  public record Cluster(List<Pin> pins, Point2D.Double position) {
    public Cluster {
      pins = List.copyOf(pins);
    }

    public int count() {
      return pins.size();
    }

    public PinKind kind() {
      return pins.get(0).kind();
    }

    /** Whether the marker is drawn larger: its first pin is emphasised. */
    public boolean emphasised() {
      return pins.get(0).emphasised();
    }

    /** The quests of the pins, each once, in pin order. */
    public List<QuestRef> quests() {
      return pins.stream().flatMap(p -> p.quests().stream()).distinct().toList();
    }

    /** Each entity among the pins once, in pin order, with its number of pins. */
    public Map<Pin, Integer> entities() {
      Map<EntityRef, Pin> first = new LinkedHashMap<>();
      Map<EntityRef, Integer> counts = new HashMap<>();
      for (Pin p : pins) {
        first.putIfAbsent(p.ref(), p);
        counts.merge(p.ref(), 1, Integer::sum);
      }
      Map<Pin, Integer> out = new LinkedHashMap<>();
      first.forEach((ref, p) -> out.put(p, counts.get(ref)));
      return out;
    }
  }

  public static final String END_TOOLTIP = "Last known point of the patrol; the NPC may despawn here";

  public record RouteLine(EntityRef npc, String name, PinKind colourKind, List<Route.Point> points, boolean loop) {
    public RouteLine {
      Objects.requireNonNull(npc, "npc");
      Objects.requireNonNull(colourKind, "colourKind");
      points = List.copyOf(points);
    }
  }

  private TravelMapData travel = TravelMapData.EMPTY;

  /** Travel data is projected and loaded before reaching the map; marker layers remain independent of spawns. */
  public void setTravel(TravelMapData data) {
    TravelMapData previous = travel;
    travel = Objects.requireNonNull(data);
    firePropertyChange("travel", previous, travel);
    repaint();
  }

  public TravelMapData travel() { return travel; }

  public int visibleMarkerCount() {
    return visiblePins().size()
        + (hiddenLayers.contains(MapLayer.ENTRANCES) ? 0 : travel.entrances().size())
        + (hiddenLayers.contains(MapLayer.FLIGHTS) ? 0 : travel.flights().size());
  }

  /** Travel markers precede spawn clusters in hit testing, matching their visual stacking. */
  public TravelMapPainter.Marker travelAt(int x, int y) {
    if (coordinatesAt(x, y).isEmpty()) return null;
    return TravelMapPainter.markerAt(travel, projection(), x, y,
        !hiddenLayers.contains(MapLayer.ENTRANCES), !hiddenLayers.contains(MapLayer.FLIGHTS));
  }

  private List<RouteLine> routes = List.of();
  private final java.util.Set<String> hiddenLayers = new java.util.HashSet<>();

  public void setRoutes(List<RouteLine> routes) {
    List<RouteLine> before = this.routes;
    this.routes = List.copyOf(routes);
    firePropertyChange("routes", before, this.routes);
    repaint();
  }

  public List<RouteLine> routes() { return routes; }

  public boolean layerHasContent(MapLayer layer) {
    if (layer.kinds().isEmpty()) return switch (layer.id()) {
      case MapLayer.ENTRANCES -> !travel.entrances().isEmpty();
      case MapLayer.FLIGHTS -> !travel.flights().isEmpty();
      case MapLayer.FLIGHT_ROUTES -> !travel.routes().isEmpty();
      default -> "routes".equals(layer.id()) && !routes.isEmpty();
    };
    return pins.stream().filter(p -> !bossesAndRaresOnly || p.kind() != PinKind.NPC)
            .anyMatch(p -> layer.kinds().contains(p.kind()));
  }

  public boolean isLayerVisible(MapLayer layer) {
    return layer.kinds().isEmpty() ? !hiddenLayers.contains(layer.id())
        : layer.kinds().stream().allMatch(this::isKindVisible);
  }

  public void setLayerVisible(MapLayer layer, boolean visible) {
    if (layer.kinds().isEmpty()) {
      boolean changed = visible ? hiddenLayers.remove(layer.id()) : hiddenLayers.add(layer.id());
      if (changed) {
        firePropertyChange("layerVisibility", null, layer.id());
        repaint();
      }
    } else {
      layer.kinds().forEach(kind -> setKindVisible(kind, visible));
    }
  }

  /** The nearest visible patrol within the hit radius; pins are tested before this by callers. */
  public RouteLine routeAt(int x, int y) {
    if (hiddenLayers.contains("routes") || coordinatesAt(x, y).isEmpty()) return null;
    RouteLine nearest = null;
    double distance = UIScale.scale((float) HIT_PX);
    Point2D at = new Point2D.Double(x, y);
    for (RouteLine route : routes) {
      double d = RouteGeometry.distanceToPolyline(at, routePoints(route), route.loop());
      if (d <= distance) { distance = d; nearest = route; }
    }
    return nearest;
  }

  private List<Point2D.Double> routePoints(RouteLine route) {
    Projection p = projection();
    return route.points().stream().map(point -> p.toComponent(point.x(), point.y())).toList();
  }

  private String zoneName;
  private ClipboardPort clipboard = ClipboardPort.system();
  private Integer focus;
  private final EnumSet<PinKind> hiddenKinds = EnumSet.noneOf(PinKind.class);
  private String coordinatesText = "";

  private final BufferedImage image;
  private final Navigator nav;
  private final java.util.function.BiConsumer<JPopupMenu, Point2D.Double> popupPresenter;
  private List<Pin> pins = List.of();
  private boolean bossesAndRaresOnly;
  private List<Cluster> clusters;
  private int clustersWidth = -1;
  private Cluster hovered;
  private Font fontBase;
  private Font badgeFont;
  private Font glyphFont;
  private Font largeGlyphFont;
  /** Whether the last resize asked for a layout because the map was shorter than its width wants. */
  private boolean grewLast;

  public MapView(BufferedImage image, Navigator nav) {
    this(image, nav, null);
  }

  /** Headless interaction seam; production presents the requested menu at its marker when showing. */
  MapView(BufferedImage image, Navigator nav,
      java.util.function.BiConsumer<JPopupMenu, Point2D.Double> popupPresenter) {
    this.popupPresenter = popupPresenter != null ? popupPresenter : (menu, at) -> {
      if (isShowing()) {
        menu.show(this, (int) at.x, (int) at.y);
      }
    };
    this.image = Objects.requireNonNull(image, "image");
    MapLayer.REGISTERED.stream().filter(layer -> !layer.defaultVisible())
        .forEach(layer -> hiddenKinds.addAll(layer.kinds()));
    MapLayer.REGISTERED.stream().filter(layer -> !layer.defaultVisible() && layer.kinds().isEmpty())
        .forEach(layer -> hiddenLayers.add(layer.id()));
    this.nav = Objects.requireNonNull(nav, "nav");
    setMinimumSize(new Dimension(0, 0)); // the map shrinks with the detail area rather than holding it open
    ToolTipManager.sharedInstance().registerComponent(this);
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        if (needsLayout(getWidth(), getHeight())) {
          revalidate(); // the preferred height follows the width
        }
      }
    });
    MouseAdapter mouse = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        coordinatesText = coordinatesAt(e.getX(), e.getY())
            .map(p -> String.format(Locale.ROOT, "%.1f, %.1f", p.x, p.y)).orElse("");
        repaint();
        setHovered(clusterAt(e.getX(), e.getY()));
        TravelMapPainter.Marker travelMarker = travelAt(e.getX(), e.getY());
        boolean opens = travelMarker != null ? travelMarker.target() != null
            : hovered == null ? routeAt(e.getX(), e.getY()) != null
            : hovered.entities().size() > 1 || hovered.kind() != PinKind.SELF;
        setCursor(opens ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        coordinatesText = "";
        repaint();
        setHovered(null);
        setCursor(null);
      }

      @Override
      public void mousePressed(MouseEvent e) { popup(e); }

      @Override
      public void mouseReleased(MouseEvent e) { popup(e); }

      private void popup(MouseEvent e) {
        if (e.isPopupTrigger() && isShowing()) {
          JPopupMenu menu = contextMenuAt(e.getX(), e.getY());
          if (menu.getComponentCount() > 0) menu.show(MapView.this, e.getX(), e.getY());
        }
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        TravelMapPainter.Marker travelMarker = travelAt(e.getX(), e.getY());
        if (travelMarker != null && e.getButton() == MouseEvent.BUTTON1) {
          if (travelMarker.target() != null) nav.open(new ViewTarget.Entity(travelMarker.target()));
          return;
        }
        Cluster c = clusterAt(e.getX(), e.getY());
        if (c != null && e.getButton() == MouseEvent.BUTTON1) {
          activate(c);
        } else if (e.getButton() == MouseEvent.BUTTON1) {
          RouteLine route = routeAt(e.getX(), e.getY());
          if (route != null) nav.open(new ViewTarget.Entity(route.npc()));
        }
      }
    };
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
  }

  public void setZoneName(String name) { zoneName = name == null || name.isBlank() ? null : name; repaint(); }

  public void setClipboard(ClipboardPort clipboard) { this.clipboard = Objects.requireNonNull(clipboard); }

  public void setFocus(Integer number) { focus = number; invalidateClusters(); }

  public void setKindVisible(PinKind kind, boolean visible) {
    if (visible) hiddenKinds.remove(kind); else hiddenKinds.add(kind);
    invalidateClusters();
    firePropertyChange("kindVisibility", null, kind);
  }

  public void applyPreset(String preset) {
    for (MapLayer layer : MapLayer.REGISTERED) {
      setLayerVisible(layer, layer.presets().contains(preset));
    }
  }

  public boolean isKindVisible(PinKind kind) { return !hiddenKinds.contains(kind); }

  public Optional<Point2D.Double> coordinatesAt(int x, int y) { return projection().toMap(x, y); }

  public String coordinatesText() { return coordinatesText; }

  public JPopupMenu contextMenuAt(int x, int y) {
    JPopupMenu menu = new JPopupMenu();
    coordinatesAt(x, y).ifPresent(point -> {
      String command = WayCommand.of(zoneName, point.x, point.y);
      JMenuItem copy = new JMenuItem("Copy " + command);
      copy.addActionListener(e -> clipboard.copy(command));
      menu.add(copy);
    });
    return menu;
  }

  public BufferedImage image() {
    return image;
  }

  /** A map panel for assistive technology, named {@value #ACCESSIBLE_NAME}, its description the pin count. */
  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleJComponent() {
        @Override
        public AccessibleRole getAccessibleRole() {
          return AccessibleRole.PANEL;
        }

        @Override
        public String getAccessibleDescription() {
          return (zoneName == null ? "" : zoneName + ", ") +
              (visibleMarkerCount() == 1 ? "1 pin" : visibleMarkerCount() + " pins");
        }
      };
      accessibleContext.setAccessibleName(ACCESSIBLE_NAME);
    }
    return accessibleContext;
  }

  /** Shows {@code pins} in place of the current ones. */
  public void setPins(List<Pin> pins) {
    List<Pin> old = this.pins;
    this.pins = List.copyOf(pins);
    firePropertyChange("pins", old, this.pins);
    invalidateClusters();
  }

  public List<Pin> pins() {
    return pins;
  }

  /** With {@code bossesAndRaresOnly}, plain NPC pins ({@link PinKind#NPC}) are hidden; the others always show. */
  public void setFilter(boolean bossesAndRaresOnly) {
    if (this.bossesAndRaresOnly != bossesAndRaresOnly) {
      this.bossesAndRaresOnly = bossesAndRaresOnly;
      invalidateClusters();
      firePropertyChange("pinFilter", !bossesAndRaresOnly, bossesAndRaresOnly);
    }
  }

  public boolean bossesAndRaresOnly() {
    return bossesAndRaresOnly;
  }

  /** The pins the filter lets through, in order. */
  public List<Pin> visiblePins() {
    return pins.stream().filter(p -> !hiddenKinds.contains(p.kind()))
        .filter(p -> !bossesAndRaresOnly || p.kind() != PinKind.NPC)
        .filter(p -> focus == null || p.kind().layer() != Layer.OBJECTIVES || p.number() == focus).toList();
  }

  /** The height the map is drawn at in a component {@code width} pixels wide. */
  public int heightFor(int width) {
    return new Projection(image.getWidth(), image.getHeight(), width).drawnHeight();
  }

  /**
   * Whether the map, resized to {@code width} x {@code height}, asks for a new layout so that its height follows its
   * width. A map shorter than its width wants always asks: the page was measured while it was narrower. A taller one
   * asks too (say, the window narrowed), except right after a shorter one asked. A map is taller for a wider page, so
   * that new layout can bring a scrollbar that narrows the map again, and asking once more could take the scrollbar
   * away and bring it back for ever; the map keeps the spare height under its image instead.
   */
  boolean needsLayout(int width, int height) {
    int wanted = heightFor(width);
    if (height < wanted) {
      grewLast = true;
      return true;
    }
    boolean ask = height > wanted && !grewLast;
    grewLast = false;
    return ask;
  }

  /** Where the map is drawn at the current width. */
  public Projection projection() {
    return new Projection(image.getWidth(), image.getHeight(), getWidth());
  }

  /** The markers at the current width, the most important first. */
  public List<Cluster> clusters() {
    if (clusters == null || clustersWidth != getWidth()) {
      clusters = cluster(visiblePins(), projection(), UIScale.scale(CLUSTER_PX));
      clustersWidth = getWidth();
    }
    return clusters;
  }

  /**
   * {@code pins} drawn by {@code projection} as markers ({@link PinClusters#of}): the quest givers' layer first, each
   * pin joining the first marker within {@code threshold} px of it.
   */
  public static List<Cluster> cluster(List<Pin> pins, Projection projection, double threshold) {
    return PinClusters.of(pins, projection, threshold);
  }

  /**
   * The marker nearest to component point {@code x}, {@code y} within {@value #HIT_PX} px, a quest giver's (the top
   * layer) before any other; null when none is.
   */
  public Cluster clusterAt(int x, int y) {
    Point2D.Double at = new Point2D.Double(x, y);
    double limit = UIScale.scale(HIT_PX);
    Cluster best = null;
    double bestDistance = Double.MAX_VALUE;
    for (Cluster c : clusters()) {
      double d = c.position().distance(at);
      boolean above = best != null && c.kind().layer().ordinal() < best.kind().layer().ordinal();
      boolean below = best != null && c.kind().layer().ordinal() > best.kind().layer().ordinal();
      if (d <= limit && !below && (above || d < bestDistance)) {
        best = c;
        bestDistance = d;
      }
    }
    return best;
  }

  /** The marker under the pointer; null when there is none. */
  public Cluster hovered() {
    return hovered;
  }

  /**
   * {@code "Hogger"}, {@code "Hogger (5)"} for five pins of one entity, or for several entities a list headed
   * {@code "<n> pins"}; null away from the markers. Quest givers tell their quests ({@link QuestPinText#tooltip}).
   */
  @Override
  public String getToolTipText(MouseEvent e) {
    TravelMapPainter.Marker travelMarker = travelAt(e.getX(), e.getY());
    if (travelMarker != null) return travelMarker.tooltip();
    Cluster c = clusterAt(e.getX(), e.getY());
    if (c == null) {
      RouteLine route = routeAt(e.getX(), e.getY());
      if (route == null) return null;
      List<Point2D.Double> points = routePoints(route);
      if (!route.loop() && !points.isEmpty()
          && points.getLast().distance(e.getPoint()) <= UIScale.scale(6f)) return END_TOOLTIP;
      return route.npc().displayName(route.name());
    }
    if (!c.quests().isEmpty()) {
      return QuestPinText.tooltip(c);
    }
    Map<Pin, Integer> entities = c.entities();
    if (entities.size() == 1) {
      return entryText(c.pins().get(0), c.count());
    }
    StringBuilder html = new StringBuilder("<html>").append(c.count()).append(" pins");
    int shown = 0;
    for (Map.Entry<Pin, Integer> e2 : entities.entrySet()) {
      if (shown++ == LIST_CAP) {
        html.append("<br>…");
        break;
      }
      html.append("<br>").append(HtmlText.escape(entryText(e2.getKey(), e2.getValue())));
    }
    return html.append("</html>").toString();
  }

  /**
   * The menu a marker of several entities opens: one entry per entity, opening it (at most {@value #LIST_CAP}, then
   * "…"). A quest givers' marker lists its quests instead (each opening its quest, at most {@value #LIST_CAP}), then
   * {@code "Giver: <name>"} for each giver, opening the giver (at most {@value #GIVER_CAP}, then "…").
   */
  public JPopupMenu menuFor(Cluster c) {
    JPopupMenu menu = new JPopupMenu();
    if (!c.quests().isEmpty()) {
      List<QuestRef> quests = QuestPinText.distinctQuests(c);
      for (int i = 0; i < quests.size(); i++) {
        if (i == LIST_CAP) {
          menu.add(more());
          break;
        }
        QuestRef q = quests.get(i);
        JMenuItem item = new JMenuItem(q.label());
        item.addActionListener(a -> nav.open(new ViewTarget.Entity(q.ref())));
        menu.add(item);
      }
      int givers = 0;
      for (Pin giver : c.entities().keySet()) {
        if (givers++ == GIVER_CAP) {
          menu.add(more());
          break;
        }
        JMenuItem item = new JMenuItem("Giver: " + giver.name());
        item.addActionListener(a -> nav.open(new ViewTarget.Entity(giver.ref())));
        menu.add(item);
      }
      return menu;
    }
    // Quest-page giver pins have no quest list: keep both roles when one giver starts and ends here.
    List<Pin> roles = giverRoles(c);
    if (!roles.isEmpty()) {
      int shownRoles = 0;
      for (Pin p : roles) {
        if (shownRoles++ == LIST_CAP) {
          menu.add(more());
          break;
        }
        JMenuItem item = new JMenuItem(p.name());
        item.addActionListener(a -> nav.open(new ViewTarget.Entity(p.ref())));
        menu.add(item);
      }
      return menu;
    }
    int shown = 0;
    for (Map.Entry<Pin, Integer> e : c.entities().entrySet()) {
      if (shown++ == LIST_CAP) {
        menu.add(more());
        break;
      }
      Pin p = e.getKey();
      JMenuItem item = new JMenuItem(entryText(p, e.getValue()));
      if (p.kind() == PinKind.SELF) {
        item.setEnabled(false);
      } else {
        item.addActionListener(a -> nav.open(new ViewTarget.Entity(p.ref())));
      }
      menu.add(item);
    }
    return menu;
  }

  /** Distinct entity/role pairs on a quest-page marker; zone quest markers retain their quest-list behavior. */
  private static List<Pin> giverRoles(Cluster cluster) {
    if (!cluster.quests().isEmpty()
        || cluster.pins().stream().noneMatch(p -> p.emphasised() && p.kind().quest())) {
      return List.of();
    }
    record Role(EntityRef ref, PinKind kind) {}
    Map<Role, Pin> roles = new LinkedHashMap<>();
    cluster.pins().forEach(p -> roles.putIfAbsent(new Role(p.ref(), p.kind()), p));
    return List.copyOf(roles.values());
  }

  private static JMenuItem more() {
    JMenuItem more = new JMenuItem("…");
    more.setEnabled(false);
    return more;
  }

  private static String entryText(Pin p, int count) {
    return count > 1 ? p.name() + " (" + count + ")" : p.name();
  }

  /**
   * Opens the marker's entity (a quest giver's: its one quest), or shows the menu of a marker of several (when the
   * map is on screen).
   */
  private void activate(Cluster c) {
    Map<Pin, Integer> entities = c.entities();
    if (!c.quests().isEmpty()) {
      List<QuestRef> quests = QuestPinText.distinctQuests(c);
      if (quests.size() == 1) {
        nav.open(new ViewTarget.Entity(quests.get(0).ref()));
      } else {
        popupPresenter.accept(menuFor(c), c.position());
      }
    } else if (giverRoles(c).size() > 1) {
      popupPresenter.accept(menuFor(c), c.position());
    } else if (entities.size() == 1) {
      Pin p = c.pins().get(0);
      if (p.kind() != PinKind.SELF) {
        nav.open(new ViewTarget.Entity(p.ref()));
      }
    } else {
      popupPresenter.accept(menuFor(c), c.position());
    }
  }

  /** Moves the hover ring, repainting only the markers it leaves and enters. */
  private void setHovered(Cluster c) {
    if (c != hovered) {
      if (hovered != null) {
        repaint(markerBounds(hovered));
      }
      hovered = c;
      if (c != null) {
        repaint(markerBounds(c));
      }
      boolean opens = c != null && (c.entities().size() > 1 || c.kind() != PinKind.SELF);
      setCursor(opens ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);
    }
  }

  /**
   * The area marker {@code c} paints in: its dot (or glyph), the hover ring and the count badge, with a pixel to
   * spare.
   */
  Rectangle markerBounds(Cluster c) {
    double r = radius(c);
    double ring = r + UIScale.scale(2) + UIScale.scale(1.5f);
    double x = c.position().x;
    double y = c.position().y;
    double right = x + ring;
    double top = y - ring;
    if (c.count() > 1) {
      FontMetrics fm = getFontMetrics(badgeFont());
      double h = fm.getAscent() + UIScale.scale(1);
      double w = Math.max(fm.stringWidth(String.valueOf(c.count())) + 2 * UIScale.scale(3), h);
      double badgeX = x + r * 0.6;
      double badgeY = y - r * 0.6;
      right = Math.max(right, badgeX + w + UIScale.scale(1));
      top = Math.min(top, badgeY - h - UIScale.scale(1));
    }
    int x0 = (int) Math.floor(x - ring) - 1;
    int y0 = (int) Math.floor(top) - 1;
    return new Rectangle(x0, y0, (int) Math.ceil(right) + 1 - x0, (int) Math.ceil(y + ring) + 1 - y0);
  }

  /** How far marker {@code c}'s dot (or a quest giver's glyph) reaches from its centre. */
  private static double radius(Cluster c) {
    return UIScale.scale(switch (c.kind()) {
      case SELF -> SELF_RADIUS;
      case BOSS -> BOSS_RADIUS;
      case KILL, COLLECT, INTERACT, EXPLORE -> 9;
      case QUEST_START, QUEST_END -> c.emphasised() ? PinGlyphs.EMPHASISED_RADIUS : PinGlyphs.RADIUS;
      default -> RADIUS;
    });
  }

  /** The badge font, derived from the component's font once per font. */
  private Font badgeFont() {
    deriveFonts();
    return badgeFont;
  }

  /** The quest glyph font (an emphasised pin's when {@code emphasised}), derived once per font. */
  private Font glyphFont(boolean emphasised) {
    deriveFonts();
    return emphasised ? largeGlyphFont : glyphFont;
  }

  private void deriveFonts() {
    Font base = getFont() != null ? getFont() : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    if (base != fontBase) {
      fontBase = base;
      badgeFont = base.deriveFont(Font.BOLD, base.getSize2D() * 0.75f);
      glyphFont = PinGlyphs.font(base, false);
      largeGlyphFont = PinGlyphs.font(base, true);
    }
  }

  private void invalidateClusters() {
    clusters = null;
    hovered = null;
    repaint();
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet()) {
      return super.getPreferredSize();
    }
    int width = getWidth() > 0 ? getWidth() : image.getWidth();
    return new Dimension(width, heightFor(width));
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (getWidth() <= 0) {
      return;
    }
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      Projection p = projection();
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2.drawImage(image, 0, 0, p.drawnWidth(), p.drawnHeight(), null);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      paintRoutes(g2);
      if (!hiddenLayers.contains(MapLayer.FLIGHT_ROUTES)) TravelMapPainter.paintRoutes(g2, travel.routes(), p);
      Stroke outline = new BasicStroke(UIScale.scale(1f));
      Stroke ring = new BasicStroke(UIScale.scale(1.5f));
      g2.setFont(badgeFont());
      FontMetrics fm = g2.getFontMetrics();
      g2.setFont(getFont() != null ? getFont() : new Font(Font.SANS_SERIF, Font.PLAIN, 12));
      paintLabel(g2, zoneName, UIScale.scale(6), UIScale.scale(6));
      if (!coordinatesText.isEmpty()) {
        paintLabel(g2, coordinatesText,
            Math.max(0, p.drawnWidth() - g2.getFontMetrics().stringWidth(coordinatesText) - UIScale.scale(14)),
            Math.max(0, p.drawnHeight() - g2.getFontMetrics().getHeight() - UIScale.scale(10)));
      }
      g2.setFont(badgeFont());
      List<Cluster> all = clusters();
      for (int i = all.size() - 1; i >= 0; i--) { // the most important on top
        paintCluster(g2, all.get(i), outline, ring, fm);
      }
      TravelMapPainter.paintMarkers(g2, travel, p, this,
          !hiddenLayers.contains(MapLayer.ENTRANCES), !hiddenLayers.contains(MapLayer.FLIGHTS));
    } finally {
      g2.dispose();
    }
  }

  private void paintRoutes(Graphics2D target) {
    if (hiddenLayers.contains("routes")) return;
    Graphics2D g = (Graphics2D) target.create();
    try {
      Projection projection = projection();
      g.clipRect(0, 0, projection.drawnWidth(), projection.drawnHeight());
      float width = UIScale.scale(2f);
      Stroke dashed = new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
          10, new float[]{UIScale.scale(7f), UIScale.scale(5f)}, 0);
      Stroke solid = new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
      for (RouteLine route : routes) {
        List<Point2D.Double> points = routePoints(route);
        if (points.size() < 2) continue;
        g.setColor(colour(route.colourKind()));
        java.awt.geom.Path2D path = new java.awt.geom.Path2D.Double();
        path.moveTo(points.getFirst().x, points.getFirst().y);
        for (int i = 1; i < points.size(); i++) {
          Point2D.Double point = points.get(i);
          if (!route.loop() && i == points.size() - 1) {
            Point2D.Double previous = points.get(i - 1);
            double length = previous.distance(point);
            double trim = Math.min(UIScale.scale(5f), length);
            if (length > 0) path.lineTo(point.x + (previous.x - point.x) * trim / length,
                point.y + (previous.y - point.y) * trim / length);
          } else path.lineTo(point.x, point.y);
        }
        if (route.loop()) path.closePath();
        g.setStroke(dashed); g.draw(path); g.setStroke(solid);
        for (RouteGeometry.Anchor anchor : RouteGeometry.arrowAnchors(points)) {
          // The terminal point stays hollow, including routes whose point count lands on an arrow index.
          if (!route.loop() && anchor.at().distance(points.getLast()) < UIScale.scale(6f)) continue;
          Graphics2D arrow = (Graphics2D) g.create();
          try {
            arrow.translate(anchor.at().getX(), anchor.at().getY()); arrow.rotate(anchor.angle());
            double arm = UIScale.scale(4f);
            java.awt.geom.Path2D head = new java.awt.geom.Path2D.Double();
            head.moveTo(-arm, -arm); head.lineTo(0, 0); head.lineTo(-arm, arm); arrow.draw(head);
          } finally { arrow.dispose(); }
        }
        if (!route.loop()) {
          Point2D.Double end = points.getLast(); double radius = UIScale.scale(4f);
          g.draw(new java.awt.geom.Rectangle2D.Double(end.x-radius, end.y-radius, radius*2, radius*2));
        }
      }
    } finally { g.dispose(); }
  }

  private void paintCluster(Graphics2D g2, Cluster c, Stroke outline, Stroke ring, FontMetrics fm) {
    if (focus != null && c.kind().quest()) {
      Rectangle bounds = markerBounds(c);
      BufferedImage marker = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D local = marker.createGraphics();
      try {
        local.setRenderingHints(g2.getRenderingHints());
        local.setFont(g2.getFont());
        local.translate(-bounds.x, -bounds.y);
        paintClusterContents(local, c, outline, ring, fm);
      } finally { local.dispose(); }
      Composite before = g2.getComposite();
      g2.setComposite(AlphaComposite.SrcOver.derive(0.5f));
      g2.drawImage(marker, bounds.x, bounds.y, null);
      g2.setComposite(before);
    } else {
      paintClusterContents(g2, c, outline, ring, fm);
    }
  }

  private void paintClusterContents(Graphics2D g2, Cluster c, Stroke outline, Stroke ring, FontMetrics fm) {
    double r = radius(c);
    double x = c.position().x;
    double y = c.position().y;
    Color fill;
    if (c.kind().quest()) {
      fill = PinGlyphs.fill(c.kind());
      if (c.emphasised()) PinGlyphs.paintDisc(g2, glyphFont(true), c.kind(), x, y);
      else PinGlyphs.paint(g2, glyphFont(false), c.kind(), x, y);
    } else if (c.kind().layer() == Layer.OBJECTIVES) {
      fill = colour(c.kind());
      PinGlyphs.paintObjective(g2, badgeFont(), c.kind(), c.pins().get(0).number(), x, y, r);
    } else {
      Ellipse2D.Double dot = new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r);
      fill = colour(c.kind());
      g2.setColor(fill);
      g2.fill(dot);
      g2.setStroke(outline);
      g2.setColor(OUTLINE);
      g2.draw(dot);
    }
    if (c == hovered) {
      double h = r + UIScale.scale(2);
      g2.setColor(HOVER);
      g2.setStroke(ring);
      g2.draw(new Ellipse2D.Double(x - h, y - h, 2 * h, 2 * h));
    }
    if (c.count() > 1 && c.kind().layer() != Layer.OBJECTIVES) {
      g2.setStroke(outline);
      paintBadge(g2, fm, String.valueOf(c.count()), x + r * 0.6, y - r * 0.6, fill);
    }
  }

  private static void paintLabel(Graphics2D g, String text, int x, int y) {
    if (text == null || text.isEmpty()) return;
    FontMetrics fm = g.getFontMetrics();
    g.setColor(PinGlyphs.OUTLINE);
    g.fillRoundRect(x, y, fm.stringWidth(text) + 8, fm.getHeight() + 4, 4, 4);
    g.setColor(PinGlyphs.HALO);
    g.drawString(text, x + 4, y + fm.getAscent() + 2);
  }

  /** The count badge: a small dark pill outlined in the marker's colour, its lower left corner at {@code x, y}. */
  private static void paintBadge(Graphics2D g2, FontMetrics fm, String text, double x, double y, Color edge) {
    double padX = UIScale.scale(3);
    double w = fm.stringWidth(text) + 2 * padX;
    double h = fm.getAscent() + UIScale.scale(1);
    RoundRectangle2D.Double pill = new RoundRectangle2D.Double(x, y - h, Math.max(w, h), h, h, h);
    g2.setColor(OUTLINE);
    g2.fill(pill);
    g2.setColor(edge);
    g2.draw(pill);
    g2.setColor(HOVER);
    g2.drawString(text, (float) (x + (pill.width - fm.stringWidth(text)) / 2),
        (float) (y - (h - fm.getAscent()) / 2 - UIScale.scale(0.5f)));
  }

  /** Boss and the page's own pins gold, rares silver, objects the link accent, other NPCs muted. */
  static Color colour(PinKind kind) {
    Theme t = Theme.current();
    return switch (kind) {
      case SELF, BOSS -> t.accentGold();
      case RARE -> SILVER;
      case OBJECT -> t.link();
      case NPC -> t.textMuted();
      case KILL -> new Color(0xE5705C);
      case COLLECT -> new Color(0x7CC46B);
      case INTERACT, EXPLORE -> new Color(0x6FA8DC);
      case QUEST_START, QUEST_END -> PinGlyphs.fill(kind);
    };
  }
}
