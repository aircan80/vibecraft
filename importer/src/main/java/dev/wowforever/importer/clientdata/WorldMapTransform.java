package dev.wowforever.importer.clientdata;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Pure projection of client world coordinates through the most specific containing UiMap assignment. */
public final class WorldMapTransform {
  private WorldMapTransform() {}

  public record Assignment(int id, int uiMapId, int mapId, int orderIndex,
      double worldMinX, double worldMinY, double worldMinZ,
      double worldMaxX, double worldMaxY, double worldMaxZ,
      double uiMinX, double uiMinY, double uiMaxX, double uiMaxY) {
    public Assignment {
      double[] values={worldMinX,worldMinY,worldMinZ,worldMaxX,worldMaxY,worldMaxZ,uiMinX,uiMinY,uiMaxX,uiMaxY};
      for(double value:values) if(!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite map assignment "+id);
      if(worldMinX>=worldMaxX || worldMinY>=worldMaxY || worldMinZ>worldMaxZ || uiMinX>=uiMaxX || uiMinY>=uiMaxY)
        throw new IllegalArgumentException("Degenerate map assignment "+id);
    }
    double area() { return (worldMaxX-worldMinX)*(worldMaxY-worldMinY); }
    boolean contains(int worldMapId,double x,double y,double z) {
      return mapId==worldMapId && x>=worldMinX && x<=worldMaxX && y>=worldMinY && y<=worldMaxY && z>=worldMinZ && z<=worldMaxZ;
    }
  }
  /** Percentages, on the same 0..100 axes used by Questie and /way. */
  public record Position(int uiMapId,int assignmentId,double x,double y) {}

  public static List<Assignment> load(CsvTable table) {
    var result=new java.util.ArrayList<Assignment>();
    for(var r:table.rows()) {
      // WMO/interior assignments require placement context that a bare world point cannot establish.
      if((table.hasColumn("WMODoodadPlacementID") && r.intOr("WMODoodadPlacementID",0)!=0)
          || (table.hasColumn("WMOGroupID") && r.intOr("WMOGroupID",0)!=0)) continue;
      double minX=r.doubleOr("Region_0",Double.NaN),minY=r.doubleOr("Region_1",Double.NaN);
      double maxX=r.doubleOr("Region_3",Double.NaN),maxY=r.doubleOr("Region_4",Double.NaN);
      if(minX>=maxX || minY>=maxY) continue;
      result.add(new Assignment(r.intOr("ID",-1),r.intOr("UiMapID",-1),r.intOr("MapID",-1),r.intOr("OrderIndex",0),
          minX,minY,r.doubleOr("Region_2",Double.NaN),maxX,maxY,r.doubleOr("Region_5",Double.NaN),
          r.doubleOr("UiMin_0",Double.NaN),r.doubleOr("UiMin_1",Double.NaN),r.doubleOr("UiMax_0",Double.NaN),r.doubleOr("UiMax_1",Double.NaN)));
    }
    return List.copyOf(result);
  }

  public static Optional<Position> project(List<Assignment> assignments,int mapId,double x,double y,double z) {
    return assignments.stream().filter(a -> a.contains(mapId,x,y,z))
        .min(Comparator.comparingDouble(Assignment::area).thenComparingInt(Assignment::orderIndex).thenComparingInt(Assignment::id))
        .map(a -> project(a,x,y));
  }

  /** Explicit target map projection, useful when clipping a route to a displayed zone. */
  public static Optional<Position> projectTo(List<Assignment> assignments,int uiMapId,int mapId,double x,double y,double z) {
    return project(assignments.stream().filter(a -> a.uiMapId()==uiMapId).toList(),mapId,x,y,z);
  }

  private static Position project(Assignment a,double x,double y) {
    return new Position(a.uiMapId(),a.id(),100*(a.uiMaxX()-(y-a.worldMinY())/(a.worldMaxY()-a.worldMinY())*(a.uiMaxX()-a.uiMinX())),
        100*(a.uiMaxY()-(x-a.worldMinX())/(a.worldMaxX()-a.worldMinX())*(a.uiMaxY()-a.uiMinY())));
  }
}
