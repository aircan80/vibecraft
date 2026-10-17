package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RouteGeometryTest {
  @Test
  void arrowsEveryTenPoints() {
    List<Point2D> points = IntStream.range(0, 35).mapToObj(i -> (Point2D) new Point2D.Double(i, 0)).toList();
    assertThat(RouteGeometry.arrowAnchors(points)).extracting(a -> a.at().getX()).containsExactly(10.0, 20.0, 30.0);
    assertThat(RouteGeometry.arrowAnchors(points)).allSatisfy(a -> assertThat(a.angle()).isZero());
  }

  @Test
  void distanceToClosedPolygonIncludesTheClosingSegment() {
    List<Point2D> points = List.of(new Point2D.Double(0, 0), new Point2D.Double(10, 0), new Point2D.Double(10, 10));
    Point2D pointer = new Point2D.Double(5, 5);
    assertThat(RouteGeometry.distanceToPolyline(pointer, points, true)).isCloseTo(0, within(.001));
    assertThat(RouteGeometry.distanceToPolyline(pointer, points, false)).isEqualTo(5);
  }

  @Test
  void emptySingleAndDuplicatePointsAreSafe() {
    Point2D point = new Point2D.Double(3, 4);
    assertThat(RouteGeometry.distanceToPolyline(point, List.of(), false)).isInfinite();
    assertThat(RouteGeometry.distanceToPolyline(point, List.of(new Point2D.Double()), false)).isEqualTo(5);
    assertThat(RouteGeometry.distanceToPolyline(point, List.of(new Point2D.Double(), new Point2D.Double()), true))
        .isEqualTo(5);
    assertThat(RouteGeometry.arrowAnchors(IntStream.range(0, 12).mapToObj(i -> (Point2D) new Point2D.Double()).toList()))
        .isEmpty();
  }
}
