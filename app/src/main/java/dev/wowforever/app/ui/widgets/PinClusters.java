package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.ui.widgets.MapView.Cluster;
import dev.wowforever.app.ui.widgets.MapView.Pin;
import dev.wowforever.app.ui.widgets.MapView.Projection;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The pure clustering of a {@link MapView}: pins as drawn by a projection grouped into markers, each pin joining the
 * first marker within the threshold of it. A grid of threshold-wide cells keeps it near linear in the pins.
 */
final class PinClusters {
  private PinClusters() {}

  /**
   * {@code pins} drawn by {@code projection} as markers, the quest givers' layer first (it is drawn on top) and
   * clustered apart from the other pins: in each layer, taking the pins the most important first
   * ({@link MapView.PinKind} order, emphasised pins first, else in order), each joins the first marker within
   * {@code threshold} px of it, else starts a marker of its own there.
   */
  static List<Cluster> of(List<Pin> pins, Projection projection, double threshold) {
    List<Cluster> out = new ArrayList<>();
    for (MapView.Layer layer : MapView.Layer.values()) {
      Map<Integer, List<Pin>> groups = new java.util.LinkedHashMap<>();
      for (Pin pin : pins) {
        if (pin.kind().layer() == layer) {
          groups.computeIfAbsent(layer == MapView.Layer.OBJECTIVES ? pin.number() : 0,
              k -> new ArrayList<>()).add(pin);
        }
      }
      groups.values().forEach(group -> out.addAll(clusterLayer(group, projection, threshold)));
    }
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
}
