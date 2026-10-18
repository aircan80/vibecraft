package dev.wowforever.app.db;

import java.util.ArrayList;
import java.util.List;

/** Item-first reported object relationships and exact-area archived placements; no gathering inference. */
public final class RecordedObjectSourcesRepository {
  public static final int PAGE_SIZE=100,POINT_LIMIT=250;
  public record Page<T>(List<T> rows,int offset,boolean more){public Page{rows=List.copyOf(rows);}}
  public record Zone(long id,String name) { public String toString(){return name==null?"Zone #"+id:name;} }
  public record Source(long id,String name,long rawRows,boolean observed) {public String toString(){return (name==null?"Object #"+id:name)+" · "+rawRows+" recorded rows";} }
  public record Point(double x,double y) {}
  public record Points(List<Point> rows,long rawRows,long validRows,boolean more){public Points{rows=List.copyOf(rows);}}
  private final Database db;
  public RecordedObjectSourcesRepository(Database db){this.db=db;}
  public String provenance(){var meta=db.meta();return "Normalized reported object/item relations; Questie archived placements. Base "+meta.getOrDefault("questiedb_commit","unrecorded")+"; corrections "+meta.getOrDefault("questiedb_refresh_commit","unrecorded")+". These archive revisions are dataset context, not per-point verification in Forever. No live availability, gathering skill, respawn, stock or drop rate is established.";}
  public Page<Zone> zones(long itemId,int offset){check(offset);return db.read(c->{
    List<Zone> rows=new ArrayList<>();
    try(var p=c.prepareStatement("""
      SELECT s.zone_id,z.name FROM item_object_drop d JOIN object_spawn s ON s.object_id=d.object_id
      LEFT JOIN zone z ON z.id=s.zone_id WHERE d.item_id=? GROUP BY s.zone_id
      ORDER BY s.zone_id LIMIT ? OFFSET ?
      """)){p.setLong(1,itemId);p.setInt(2,PAGE_SIZE+1);p.setInt(3,offset);try(var r=p.executeQuery()){while(r.next())rows.add(new Zone(r.getLong(1),r.getString(2)));}}
    return page(rows,offset);
  });}
  public Page<Source> sources(long itemId,long zoneId,int offset){check(offset);return db.read(c->{
    List<Source> rows=new ArrayList<>();
    String observed=CollectorFacts.observed(c,"object-drop","object","d.object_id","d.item_id");
    try(var p=c.prepareStatement("SELECT d.object_id,o.name,count(*),"+observed+" FROM item_object_drop d LEFT JOIN game_object o ON o.id=d.object_id JOIN object_spawn s ON s.object_id=d.object_id WHERE d.item_id=? AND s.zone_id=? GROUP BY d.object_id ORDER BY d.object_id LIMIT ? OFFSET ?")){
      p.setLong(1,itemId);p.setLong(2,zoneId);p.setInt(3,PAGE_SIZE+1);p.setInt(4,offset);try(var r=p.executeQuery()){while(r.next())rows.add(new Source(r.getLong(1),r.getString(2),r.getLong(3),r.getBoolean(4)));}}
    return page(rows,offset);
  });}
  public Points points(long itemId,long zoneId,long objectId){return db.read(c->{
    // Membership guard prevents unrelated object IDs leaking into a selected item's view.
    String where=" FROM object_spawn s WHERE s.object_id=? AND s.zone_id=? AND EXISTS(SELECT 1 FROM item_object_drop d WHERE d.item_id=? AND d.object_id=s.object_id)";
    long raw=0,valid=0;List<Point> rows=new ArrayList<>();
    try(var p=c.prepareStatement("SELECT count(*),count(CASE WHEN x BETWEEN 0 AND 100 AND y BETWEEN 0 AND 100 THEN 1 END)"+where)){p.setLong(1,objectId);p.setLong(2,zoneId);p.setLong(3,itemId);try(var r=p.executeQuery()){if(r.next()){raw=r.getLong(1);valid=r.getLong(2);}}}
    try(var p=c.prepareStatement("SELECT DISTINCT x,y"+where+" AND x BETWEEN 0 AND 100 AND y BETWEEN 0 AND 100 ORDER BY x,y LIMIT ?")){p.setLong(1,objectId);p.setLong(2,zoneId);p.setLong(3,itemId);p.setInt(4,POINT_LIMIT+1);try(var r=p.executeQuery()){while(r.next())rows.add(new Point(r.getDouble(1),r.getDouble(2)));}}
    boolean more=rows.size()>POINT_LIMIT;if(more)rows.remove(POINT_LIMIT);return new Points(rows,raw,valid,more);
  });}
  private static void check(int offset){if(offset<0)throw new IllegalArgumentException("negative offset");}
  private static <T> Page<T> page(List<T> rows,int offset){boolean more=rows.size()>PAGE_SIZE;if(more)rows.remove(PAGE_SIZE);return new Page<>(rows,offset,more);}
}
