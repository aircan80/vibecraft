package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.MapCoordinates;
import dev.wowforever.app.model.Route;
import dev.wowforever.app.model.TravelMapData;
import dev.wowforever.app.model.TravelMapData.Entrance;
import dev.wowforever.app.model.TravelMapData.Faction;
import dev.wowforever.app.model.TravelMapData.FlightNode;
import dev.wowforever.app.model.TravelMapData.FlightRoute;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import javax.swing.Icon;

/** Travel geometry and drawing, kept separate from quest/spawn clustering so wings retain their identities. */
public final class TravelMapPainter {
  private TravelMapPainter() {}
  private static final Color NAVY = new Color(0x142033);
  private static final Color GOLD = new Color(0xE4BC69);
  private static final Color RED = new Color(0xED7979);
  private static final Color BLUE = new Color(0x83BDF5);
  public record Marker(EntityRef target, String tooltip) {}

  static Marker markerAt(TravelMapData data, MapView.Projection projection, int x, int y,
      boolean entrances, boolean flights) {
    double radius = UIScale.scale(12f);
    if (entrances) for (Entrance entrance : data.entrances()) {
      if (!MapCoordinates.onMap(entrance.x(), entrance.y())) continue;
      if (projection.toComponent(entrance.x(), entrance.y()).distance(x, y) <= radius)
        return new Marker(entrance.instance(), entrance.tooltip()
            + (entrance.exitHere() ? " · Exit lands here" : ""));
    }
    if (flights) for (FlightNode node : data.flights()) {
      if (!MapCoordinates.onMap(node.x(), node.y())) continue;
      if (projection.toComponent(node.x(), node.y()).distance(x, y) <= radius) {
        String faction = switch (node.faction()) {
          case ALLIANCE -> "Alliance"; case HORDE -> "Horde"; case NEUTRAL -> "Neutral";
        };
        String destinations = node.destinations().isEmpty() ? ""
            : " · Flies to: " + String.join(", ", node.destinations());
        return new Marker(node.flightMaster(), node.name() + " · " + faction + " flight path" + destinations);
      }
    }
    return null;
  }

  static void paintMarkers(Graphics2D graphics, TravelMapData data, MapView.Projection projection,
      Component component, boolean entrances, boolean flights) {
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      g.clipRect(0, 0, projection.drawnWidth(), projection.drawnHeight());
      g.setFont(component.getFont() == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 12) : component.getFont());
      if (flights) for (FlightNode node : data.flights()) {
      if (!MapCoordinates.onMap(node.x(), node.y())) continue;
        Point2D.Double point = projection.toComponent(node.x(), node.y());
        paintFlight(g, point.x, point.y, node.faction());
      }
      List<Rectangle> occupied = new ArrayList<>();
      if (entrances) for (Entrance entrance : data.entrances()) {
        if (!MapCoordinates.onMap(entrance.x(), entrance.y())) continue;
        Point2D.Double point = projection.toComponent(entrance.x(), entrance.y());
        paintEntrance(g, point.x, point.y, entrance.raid(), entrance.exit(), component);
        int radius = UIScale.scale(13);
        occupied.add(new Rectangle((int) point.x - radius, (int) point.y - radius, radius * 2, radius * 2));
      }
      if (entrances) for (Entrance entrance : data.entrances()) {
        if (!MapCoordinates.onMap(entrance.x(), entrance.y())) continue;
        Point2D.Double point = projection.toComponent(entrance.x(), entrance.y());
        Rectangle plate = labelBounds(g.getFontMetrics(), entranceLabel(entrance),
            (int) point.x + UIScale.scale(15), (int) point.y - g.getFontMetrics().getHeight() / 2,
            projection.drawnWidth(), projection.drawnHeight(), occupied);
        if (Math.abs(plate.y - point.y) > plate.height) {
          g.setColor(GOLD); g.setStroke(new BasicStroke(UIScale.scale(1f)));
          g.draw(new Line2D.Double(point.x + UIScale.scale(12), point.y, plate.x, plate.getCenterY()));
        }
        paintLabel(g, entranceLabel(entrance), plate);
        occupied.add(plate);
      }
    } finally { g.dispose(); }
  }

  private static void paintEntrance(Graphics2D g, double x, double y, boolean raid, boolean exit, Component component) {
    int side = UIScale.scale(24), left = (int) Math.round(x - side / 2.0), top = (int) Math.round(y - side / 2.0);
    Shape plate = new RoundRectangle2D.Double(left, top, side, side, UIScale.scale(5), UIScale.scale(5));
    if (!exit) { g.setColor(NAVY); g.fill(plate); }
    g.setColor(raid ? RED : GOLD); g.setStroke(new BasicStroke(UIScale.scale(2f))); g.draw(plate);
    if (!exit) {
      Icon icon = RailIcons.shared().small(RailTarget.INSTANCES, 16);
      icon.paintIcon(component, g, (int) x - icon.getIconWidth() / 2, (int) y - icon.getIconHeight() / 2);
    }
  }

  private static Color factionColor(Faction faction) {
    return switch (faction) { case ALLIANCE -> BLUE; case HORDE -> RED; case NEUTRAL -> GOLD; };
  }

  /** Wing strokes and the printed faction letter remain readable independently of border colour. */
  private static void paintFlight(Graphics2D g, double x, double y, Faction faction) {
    double radius = UIScale.scale(11f);
    Shape circle = new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2);
    g.setColor(NAVY); g.fill(circle);
    g.setColor(factionColor(faction)); g.setStroke(new BasicStroke(UIScale.scale(2f))); g.draw(circle);
    double unit = UIScale.scale(1f);
    Path2D wing = new Path2D.Double();
    wing.moveTo(x - 7 * unit, y - 5 * unit); wing.lineTo(x - 2 * unit, y - unit);
    wing.lineTo(x + 2 * unit, y - 4 * unit); wing.lineTo(x + 7 * unit, y - 6 * unit);
    wing.moveTo(x - 6 * unit, y - 2 * unit); wing.lineTo(x - 2 * unit, y + unit);
    wing.lineTo(x + 2 * unit, y - 2 * unit); wing.lineTo(x + 6 * unit, y - 3 * unit);
    g.setColor(Color.WHITE); g.setStroke(new BasicStroke(UIScale.scale(1.4f))); g.draw(wing);
    Font original = g.getFont(); g.setFont(original.deriveFont(Font.BOLD, UIScale.scale(9f)));
    String letter = faction.name().substring(0, 1);
    g.drawString(letter, (float) (x - g.getFontMetrics().stringWidth(letter) / 2.0), (float) (y + 8 * unit));
    g.setFont(original);
  }

  static Icon legendIcon(String layerId) {
    return new Icon() {
      public int getIconWidth() { return UIScale.scale(24); }
      public int getIconHeight() { return UIScale.scale(24); }
      public void paintIcon(Component c, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
          g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g.setFont(c.getFont());
          if (layerId.equals(MapLayer.ENTRANCES)) paintEntrance(g, x + getIconWidth()/2.0, y + getIconHeight()/2.0, false, false, c);
          else paintFlight(g, x + getIconWidth()/2.0, y + getIconHeight()/2.0, Faction.NEUTRAL);
        } finally { g.dispose(); }
      }
    };
  }

  static void paintRoutes(Graphics2D graphics, List<FlightRoute> routes, MapView.Projection projection) {
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      g.clipRect(0, 0, projection.drawnWidth(), projection.drawnHeight());
      g.setColor(GOLD);
      g.setStroke(new BasicStroke(UIScale.scale(1f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
          10, new float[]{UIScale.scale(4f), UIScale.scale(4f)}, 0));
      for (FlightRoute route : routes) {
        List<Route.Point> points = route.points();
        for (int i = 1; i < points.size(); i++) {
          var from = projection.toComponent(points.get(i - 1).x(), points.get(i - 1).y());
          var to = projection.toComponent(points.get(i).x(), points.get(i).y());
          g.setColor(GOLD); g.draw(new Line2D.Double(from, to));
          exitPoint(points.get(i - 1), points.get(i)).ifPresent(edge -> {
            var at = projection.toComponent(edge.x, edge.y);
            label(g, route.destination(), (int) at.x + 3, (int) at.y + 3,
                projection.drawnWidth(), projection.drawnHeight());
          });
        }
      }
    } finally { g.dispose(); }
  }

  /** Last visible intersection when a directed route segment leaves the map; no invented off-map nodes. */
  static Optional<Point2D.Double> exitPoint(Route.Point from, Route.Point to) {
    if (to.x() >= 0 && to.x() <= 100 && to.y() >= 0 && to.y() <= 100) return Optional.empty();
    double dx = to.x() - from.x(), dy = to.y() - from.y();
    double low = 0, high = 1;
    double[] p = {-dx, dx, -dy, dy}, q = {from.x(), 100 - from.x(), from.y(), 100 - from.y()};
    for (int i = 0; i < 4; i++) {
      if (p[i] == 0) { if (q[i] < 0) return Optional.empty(); continue; }
      double t = q[i] / p[i];
      if (p[i] < 0) low = Math.max(low, t); else high = Math.min(high, t);
      if (low > high) return Optional.empty();
    }
    return Optional.of(new Point2D.Double(from.x() + high * dx, from.y() + high * dy));
  }

  static String entranceLabel(Entrance entrance) {
    return entrance.exit() && !entrance.name().startsWith("Exit of ") ? "Exit of " + entrance.name() : entrance.name();
  }

  /** Entrance names retain their full text while nearby plates move vertically to stay distinct. */
  static Rectangle labelBounds(FontMetrics metrics, String text, int x, int y, int mapWidth, int mapHeight,
      List<Rectangle> occupied) {
    int width = metrics.stringWidth(text) + 8, height = metrics.getHeight() + 4;
    x = Math.max(0, Math.min(x, mapWidth - width));
    Rectangle candidate = new Rectangle(x, Math.max(0, Math.min(y, mapHeight - height)), width, height);
    for (int step = 0; step < 40; step++) {
      int offset = step == 0 ? 0 : ((step + 1) / 2) * (height + 3) * (step % 2 == 1 ? -1 : 1);
      candidate.y = Math.max(0, Math.min(y + offset, mapHeight - height));
      if (occupied.stream().noneMatch(candidate::intersects)) return candidate;
    }
    return candidate;
  }

  private static void label(Graphics2D g, String text, int x, int y, int mapWidth, int mapHeight) {
    paintLabel(g, text, labelBounds(g.getFontMetrics(), text, x, y, mapWidth, mapHeight, List.of()));
  }

  private static void paintLabel(Graphics2D g, String text, Rectangle plate) {
    g.setColor(NAVY); g.fillRoundRect(plate.x, plate.y, plate.width, plate.height, 4, 4);
    g.setColor(Color.WHITE); g.drawString(text, plate.x + 4, plate.y + g.getFontMetrics().getAscent() + 2);
  }
}
