package dev.wowforever.app.db;

import java.util.ArrayList;
import java.util.List;

/** Bounded, recorded city placements; deliberately does not infer service types from NPC names. */
public final class CityServicesRepository {
  public static final int PEOPLE_LIMIT=60, LINKS_LIMIT=12;
  public record Link(long id,String name,String role) {}
  public record Person(long id,String name,List<String> locations,List<Link> stock,List<Link> quests,
                       int stockCount,int questCount) {
    public Person { locations=List.copyOf(locations);stock=List.copyOf(stock);quests=List.copyOf(quests); }
  }
  public record Snapshot(List<Person> people,boolean more) {
    public Snapshot { people=List.copyOf(people); }
    public static Snapshot empty(){return new Snapshot(List.of(),false);}
  }
  private final Database db;
  public CityServicesRepository(Database db){this.db=db;}
  /** Only this exact area's spawn records are selected; district placements are never reassigned. */
  public Snapshot load(long areaId) {
    return db.read(c->{
      List<Person> people=new ArrayList<>();
      try(var p=c.prepareStatement("""
          SELECT n.id,n.name FROM npc n JOIN
            (SELECT DISTINCT npc_id FROM npc_spawn WHERE zone_id=?) placed ON placed.npc_id=n.id
          WHERE EXISTS(SELECT 1 FROM item_vendor v WHERE v.npc_id=n.id)
             OR EXISTS(SELECT 1 FROM quest_giver g WHERE g.kind='npc' AND g.entity_id=n.id)
          ORDER BY n.name COLLATE NOCASE,n.id LIMIT ?
          """)) {
        p.setLong(1,areaId);p.setInt(2,PEOPLE_LIMIT+1);
        try(var r=p.executeQuery()){while(r.next())people.add(new Person(r.getLong(1),r.getString(2),List.of(),List.of(),List.of(),0,0));}
      }
      boolean more=people.size()>PEOPLE_LIMIT;if(more)people.remove(PEOPLE_LIMIT);
      List<Person> loaded=new ArrayList<>();
      try(var positions=c.prepareStatement("SELECT DISTINCT x,y FROM npc_spawn WHERE zone_id=? AND npc_id=? AND x BETWEEN 0 AND 100 AND y BETWEEN 0 AND 100 ORDER BY x,y LIMIT 4");
          var stock=c.prepareStatement("SELECT v.item_id,i.name,count(*) OVER() FROM item_vendor v JOIN item i ON i.id=v.item_id WHERE v.npc_id=? ORDER BY i.name COLLATE NOCASE,v.item_id LIMIT ?");
          var quests=c.prepareStatement("SELECT g.quest_id,q.name,g.role,count(*) OVER() FROM (SELECT DISTINCT quest_id,role FROM quest_giver WHERE kind='npc' AND entity_id=?) g JOIN quest q ON q.id=g.quest_id ORDER BY q.name COLLATE NOCASE,g.quest_id,g.role LIMIT ?")) {
        for(Person person:people){
          List<String> locations=new ArrayList<>();positions.setLong(1,areaId);positions.setLong(2,person.id());
          try(var r=positions.executeQuery()){while(r.next())locations.add(String.format(java.util.Locale.ROOT,"%.1f, %.1f",r.getDouble(1),r.getDouble(2)));}
          List<Link> items=new ArrayList<>(),missions=new ArrayList<>();int itemsCount=0,questsCount=0;
          stock.setLong(1,person.id());stock.setInt(2,LINKS_LIMIT);
          try(var r=stock.executeQuery()){while(r.next()){items.add(new Link(r.getLong(1),r.getString(2),"stock"));itemsCount=r.getInt(3);}}
          quests.setLong(1,person.id());quests.setInt(2,LINKS_LIMIT);
          try(var r=quests.executeQuery()){while(r.next()){missions.add(new Link(r.getLong(1),r.getString(2),r.getString(3)));questsCount=r.getInt(4);}}
          loaded.add(new Person(person.id(),person.name(),locations,items,missions,itemsCount,questsCount));
        }
      }
      return new Snapshot(loaded,more);
    });
  }
}
