package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.QuestPin;
import dev.wowforever.app.model.QuestRef;
import dev.wowforever.app.model.SpawnPin;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.BasicStroke;
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
import java.util.Comparator;
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
  /** What assistive technology calls the map. */
  public static final String ACCESSIBLE_NAME = "Zone map";
  private static final int RADIUS = 4;
  private static final int SELF_RADIUS = 6;
  private static final int BOSS_RADIUS = 5;
  private static final Color OUTLINE = new Color(0x0C0E12);
  private static final Color SILVER = new Color(0xC9CED6);
  private static final Color HOVER = new Color(0xFFFFFF);

  /**
   * What a pin stands for, most important first: a marker of several pins takes the first kind among them. The
   * quest kinds are quest givers, drawn as glyphs ({@link PinGlyphs}) in a layer of their own above the others.
   */
  public enum PinKind {
    SELF, BOSS, RARE, OBJECT, NPC, QUEST_START, QUEST_END;

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
      boolean emphasised) {
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
  public record Projection(int imageWidth, int imageHeight, int componentWidth) {
    public int drawnWidth() {
      return Math.max(0, Math.min(componentWidth, imageWidth));
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

  private final BufferedImage image;
  private final Navigator nav;
  private List<Pin> pins = List.of();
  private boolean bossesAndRaresOnly;
  private List<Cluster> clusters;
  private int clustersWidth = -1;
  private Cluster hovered;
  private Font fontBase;
  private Font badgeFont;
  private Font glyphFont;
  private Font largeGlyphFont;

  public MapView(BufferedImage image, Navigator nav) {
    this.image = Objects.requireNonNull(image, "image");
    this.nav = Objects.requireNonNull(nav, "nav");
    setMinimumSize(new Dimension(0, 0)); // the map shrinks with the detail area rather than holding it open
    ToolTipManager.sharedInstance().registerComponent(this);
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        if (getHeight() != heightFor(getWidth())) {
          revalidate(); // the preferred height follows the width
        }
      }
    });
    MouseAdapter mouse = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        setHovered(clusterAt(e.getX(), e.getY()));
      }

      @Override
      public void mouseExited(MouseEvent e) {
        setHovered(null);
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        Cluster c = clusterAt(e.getX(), e.getY());
        if (c != null && e.getButton() == MouseEvent.BUTTON1) {
          activate(c);
        }
      }
    };
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
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
          return pins.size() == 1 ? "1 pin" : pins.size() + " pins";
        }
      };
      accessibleContext.setAccessibleName(ACCESSIBLE_NAME);
    }
    return accessibleContext;
  }

  /** Shows {@code pins} in place of the current ones. */
  public void setPins(List<Pin> pins) {
    this.pins = List.copyOf(pins);
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
    }
  }

  public boolean bossesAndRaresOnly() {
    return bossesAndRaresOnly;
  }

  /** The pins the filter lets through, in order. */
  public List<Pin> visiblePins() {
    return bossesAndRaresOnly ? pins.stream().filter(p -> p.kind() != PinKind.NPC).toList() : pins;
  }

  /** The height the map is drawn at in a component {@code width} pixels wide. */
  public int heightFor(int width) {
    return new Projection(image.getWidth(), image.getHeight(), width).drawnHeight();
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
   * {@code pins} drawn by {@code projection} as markers, the quest givers' layer first (it is drawn on top) and
   * clustered apart from the other pins: in each layer, taking the pins the most important first ({@link PinKind}
   * order, emphasised pins first, else in order), each joins the first marker within {@code threshold} px of it,
   * else starts a marker of its own there.
   */
  public static List<Cluster> cluster(List<Pin> pins, Projection projection, double threshold) {
    List<Cluster> out = new ArrayList<>(
        clusterLayer(pins.stream().filter(p -> p.kind().quest()).toList(), projection, threshold));
    out.addAll(clusterLayer(pins.stream().filter(p -> !p.kind().quest()).toList(), projection, threshold));
    return out;
  }

  private static List<Cluster> clusterLayer(List<Pin> pins, Projection projection, double threshold) {
    List<Pin> ordered = pins.stream()
        .sorted(Comparator.comparing(Pin::kind).thenComparing(p -> !p.emphasised())).toList();
    List<Point2D.Double> anchors = new ArrayList<>();
    List<List<Pin>> members = new ArrayList<>();
    Map<Long, List<Integer>> grid = new HashMap<>(); // cell -> marker indexes, cells threshold px wide
    double cell = Math.max(threshold, 1);
    for (Pin p : ordered) {
      Point2D.Double at = projection.toComponent(p.x(), p.y());
      long cx = (long) Math.floor(at.x / cell);
      long cy = (long) Math.floor(at.y / cell);
      int found = -1;
      for (long dx = -1; dx <= 1; dx++) {
        for (long dy = -1; dy <= 1; dy++) {
          for (int i : grid.getOrDefault(key(cx + dx, cy + dy), List.of())) {
            if ((found < 0 || i < found) && anchors.get(i).distance(at) <= threshold) {
              found = i;
            }
          }
        }
      }
      if (found >= 0) {
        members.get(found).add(p);
      } else {
        anchors.add(at);
        members.add(new ArrayList<>(List.of(p)));
        grid.computeIfAbsent(key(cx, cy), k -> new ArrayList<>()).add(anchors.size() - 1);
      }
    }
    List<Cluster> out = new ArrayList<>(anchors.size());
    for (int i = 0; i < anchors.size(); i++) {
      out.add(new Cluster(members.get(i), anchors.get(i)));
    }
    return out;
  }

  private static long key(long cx, long cy) {
    return (cx << 32) ^ (cy & 0xFFFFFFFFL);
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
      boolean above = best != null && c.kind().quest() && !best.kind().quest();
      boolean below = best != null && !c.kind().quest() && best.kind().quest();
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
    Cluster c = clusterAt(e.getX(), e.getY());
    if (c == null) {
      return null;
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
      html.append("<br>").append(escape(entryText(e2.getKey(), e2.getValue())));
    }
    return html.append("</html>").toString();
  }

  /**
   * The menu a marker of several entities opens: one entry per entity, opening it. A quest givers' marker lists its
   * quests instead (each opening its quest), then {@code "Giver: <name>"} for each giver, opening the giver.
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
      for (Pin giver : c.entities().keySet().stream().limit(LIST_CAP).toList()) {
        JMenuItem item = new JMenuItem("Giver: " + giver.name());
        item.addActionListener(a -> nav.open(new ViewTarget.Entity(giver.ref())));
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

  private static JMenuItem more() {
    JMenuItem more = new JMenuItem("…");
    more.setEnabled(false);
    return more;
  }

  private static String entryText(Pin p, int count) {
    return count > 1 ? p.name() + " (" + count + ")" : p.name();
  }

  private static String escape(String s) {
    return QuestPinText.escape(s);
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
      } else if (isShowing()) {
        menuFor(c).show(this, (int) c.position().x, (int) c.position().y);
      }
    } else if (entities.size() == 1) {
      Pin p = c.pins().get(0);
      if (p.kind() != PinKind.SELF) {
        nav.open(new ViewTarget.Entity(p.ref()));
      }
    } else if (isShowing()) {
      menuFor(c).show(this, (int) c.position().x, (int) c.position().y);
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
      Stroke outline = new BasicStroke(UIScale.scale(1f));
      Stroke ring = new BasicStroke(UIScale.scale(1.5f));
      g2.setFont(badgeFont());
      FontMetrics fm = g2.getFontMetrics();
      List<Cluster> all = clusters();
      for (int i = all.size() - 1; i >= 0; i--) { // the most important on top
        paintCluster(g2, all.get(i), outline, ring, fm);
      }
    } finally {
      g2.dispose();
    }
  }

  private void paintCluster(Graphics2D g2, Cluster c, Stroke outline, Stroke ring, FontMetrics fm) {
    double r = radius(c);
    double x = c.position().x;
    double y = c.position().y;
    Color fill;
    if (c.kind().quest()) {
      fill = PinGlyphs.fill(c.kind());
      PinGlyphs.paint(g2, glyphFont(c.emphasised()), c.kind(), x, y);
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
    if (c.count() > 1) {
      g2.setStroke(outline);
      paintBadge(g2, fm, String.valueOf(c.count()), x + r * 0.6, y - r * 0.6, fill);
    }
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
  private static Color colour(PinKind kind) {
    Theme t = Theme.current();
    return switch (kind) {
      case SELF, BOSS -> t.accentGold();
      case RARE -> SILVER;
      case OBJECT -> t.link();
      case NPC -> t.textMuted();
      case QUEST_START, QUEST_END -> PinGlyphs.fill(kind);
    };
  }
}
