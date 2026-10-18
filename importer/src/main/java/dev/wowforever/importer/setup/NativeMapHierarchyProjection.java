package dev.wowforever.importer.setup;
import dev.wowforever.importer.clientdata.CsvTable;
import java.io.IOException;import java.util.*;import java.util.concurrent.CancellationException;import java.util.function.BooleanSupplier;
/** Source-native small app facts, not generic client-table storage or artwork. */
final class NativeMapHierarchyProjection {
 record Node(long id,long parent,String name,int type){}
 record Bounds(double x0,double y0,double x1,double y1){}
 record ZoneLink(long area,long map,List<Long> assignments){ZoneLink{assignments=List.copyOf(assignments);}}
 record Model(List<Node> nodes,Map<Long,Bounds> bounds,Set<Long> ambiguousBounds,List<ZoneLink> zoneLinks,Set<Long> ambiguousAreas,Set<Long> conflictingOverrides){
  Model{nodes=List.copyOf(nodes);bounds=Map.copyOf(bounds);ambiguousBounds=Set.copyOf(ambiguousBounds);zoneLinks=List.copyOf(zoneLinks);ambiguousAreas=Set.copyOf(ambiguousAreas);conflictingOverrides=Set.copyOf(conflictingOverrides);}
 }
 private record Assignment(long id,long ui,long world,long area,double minX,double minY,double maxX,double maxY,double u0,double v0,double u1,double v1){}
 static Model build(CsvTable maps,CsvTable assignments,Map<Long,Long> overrides,Set<Long> zoneIds,BooleanSupplier cancelled)throws IOException{
  required(maps,"ID","ParentUiMapID","Name_lang","Type");required(assignments,"ID","UiMapID","MapID","AreaID","Region_0","Region_1","Region_3","Region_4","UiMin_0","UiMin_1","UiMax_0","UiMax_1","WMODoodadPlacementID","WMOGroupID");
  SortedMap<Long,Node> all=new TreeMap<>();for(var row:maps.rows()){check(cancelled);long id=row.longOr("ID",-1),parent=row.longOr("ParentUiMapID",-1);if(id<=0||parent<0||row.get("Name_lang").isBlank()||all.putIfAbsent(id,new Node(id,parent,row.get("Name_lang"),row.intOr("Type",-1)))!=null)throw new IOException("Invalid or duplicate native UiMap ID");if(all.size()>4096)throw new IOException("Too many native maps");}
  for(var node:all.values()){Set<Long> seen=new HashSet<>();long at=node.id();while(at>0&&all.containsKey(at)){if(!seen.add(at)||seen.size()>64)throw new IOException("Native map parent cycle or excessive depth");at=all.get(at).parent();}}
  if(!all.containsKey(947L)||all.get(947L).parent()!=0)throw new IOException("Native Azeroth root 947 is missing or has an unexpected parent");
  Set<Long> included=new HashSet<>(Set.of(947L));boolean added;do{added=false;for(var node:all.values())if(included.contains(node.parent()))added|=included.add(node.id());}while(added);
  List<Assignment> usable=new ArrayList<>(),areaAssignments=new ArrayList<>();Set<Long> seenAssignments=new HashSet<>();
  for(var row:assignments.rows()){check(cancelled);long id=row.longOr("ID",-1);if(id<0||!seenAssignments.add(id))throw new IOException("Duplicate or invalid UiMapAssignment ID");if(row.longOr("WMODoodadPlacementID",0)!=0||row.longOr("WMOGroupID",0)!=0)continue;
   var a=new Assignment(id,row.longOr("UiMapID",-1),row.longOr("MapID",-1),row.longOr("AreaID",-1),row.doubleOr("Region_0",Double.NaN),row.doubleOr("Region_1",Double.NaN),row.doubleOr("Region_3",Double.NaN),row.doubleOr("Region_4",Double.NaN),row.doubleOr("UiMin_0",Double.NaN),row.doubleOr("UiMin_1",Double.NaN),row.doubleOr("UiMax_0",Double.NaN),row.doubleOr("UiMax_1",Double.NaN));
   if(a.world()>=0)areaAssignments.add(a);
   if(a.world()>=0&&Double.isFinite(a.minX()+a.minY()+a.maxX()+a.maxY())&&a.minX()<a.maxX()&&a.minY()<a.maxY()&&unit(new Bounds(a.u0(),a.v0(),a.u1(),a.v1())))usable.add(a);
  }
  Map<Long,List<Assignment>> byMap=new HashMap<>();for(var a:usable)byMap.computeIfAbsent(a.ui(),k->new ArrayList<>()).add(a);
  List<Node> nodes=all.values().stream().filter(n->included.contains(n.id())).toList();Map<Long,Bounds> bounds=new TreeMap<>();Set<Long> ambiguous=new TreeSet<>();
  for(var node:nodes){check(cancelled);if(node.id()==947)continue;Set<Bounds> candidates=new HashSet<>();
   for(var child:byMap.getOrDefault(node.id(),List.of()))for(var parent:byMap.getOrDefault(node.parent(),List.of())){
    if(child.world()!=parent.world())continue;
    if(node.parent()==947&&node.type()==2){candidates.add(new Bounds(parent.u0(),parent.v0(),parent.u1(),parent.v1()));continue;}
    // Partial child UI rectangles do not establish the outline of its whole map image.
    if(child.u0()!=0||child.v0()!=0||child.u1()!=1||child.v1()!=1)continue;
    double dx=parent.maxX()-parent.minX(),dy=parent.maxY()-parent.minY();double x0=parent.u1()-(child.maxY()-parent.minY())/dy*(parent.u1()-parent.u0()),x1=parent.u1()-(child.minY()-parent.minY())/dy*(parent.u1()-parent.u0());
    double y0=parent.v1()-(child.maxX()-parent.minX())/dx*(parent.v1()-parent.v0()),y1=parent.v1()-(child.minX()-parent.minX())/dx*(parent.v1()-parent.v0());
    var rectangle=new Bounds(Math.max(0,x0),Math.max(0,y0),Math.min(1,x1),Math.min(1,y1));if(unit(rectangle))candidates.add(rectangle);
   }
   if(candidates.size()==1)bounds.put(node.id(),candidates.iterator().next());else if(candidates.size()>1)ambiguous.add(node.id());
  }
  Map<Long,List<Assignment>> byArea=new TreeMap<>();for(var a:areaAssignments)if(a.area()>0&&zoneIds.contains(a.area())&&included.contains(a.ui()))byArea.computeIfAbsent(a.area(),k->new ArrayList<>()).add(a);
  List<ZoneLink> links=new ArrayList<>();Set<Long> ambiguousAreas=new TreeSet<>(),conflicts=new TreeSet<>();for(var e:byArea.entrySet()){
   Set<Long> ids=new HashSet<>();e.getValue().forEach(a->ids.add(a.ui()));if(ids.size()!=1){ambiguousAreas.add(e.getKey());continue;}long map=ids.iterator().next();Long override=overrides.get(e.getKey());if(override!=null&&override!=map){conflicts.add(e.getKey());continue;}
   links.add(new ZoneLink(e.getKey(),map,e.getValue().stream().map(Assignment::id).sorted().toList()));
  }
  return new Model(nodes,bounds,ambiguous,links,ambiguousAreas,conflicts);
 }
 private static boolean unit(Bounds b){return Double.isFinite(b.x0()+b.y0()+b.x1()+b.y1())&&b.x0()>=0&&b.y0()>=0&&b.x1()<=1&&b.y1()<=1&&b.x0()<b.x1()&&b.y0()<b.y1();}
 private static void required(CsvTable table,String...columns)throws IOException{for(String column:columns)if(!table.hasColumn(column))throw new IOException("Required native map column missing: "+column);}
 private static void check(BooleanSupplier c){if(c.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("Native map projection cancelled");}
}
