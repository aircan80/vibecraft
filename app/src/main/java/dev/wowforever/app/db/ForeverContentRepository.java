package dev.wowforever.app.db;

import dev.wowforever.app.model.ForeverContent.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only discovery views of archived client tables, with a build on every assertion. */
public final class ForeverContentRepository {
  // Reviewed client/achievement map identities from backlog §4.3; this scope never assigns New status.
  private static final java.util.Set<Long> DISCOVERY_MAPS=java.util.Set.of(2959L,2998L,2999L,3065L,2981L,2994L,3001L,3052L);
  private static final Map<Database,Cache> CACHE=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
  private static final class Cache {
    volatile Hub hub;volatile Context context;volatile List<Discovery> discoveries;volatile Pvp pvp;
    final Map<Long,Context> zones=new java.util.concurrent.ConcurrentHashMap<>();
    final Map<String,Context> instances=new java.util.concurrent.ConcurrentHashMap<>();
  }
  private Cache cache() { synchronized(CACHE){return CACHE.computeIfAbsent(db,ignored->new Cache());} }
  private final Database db;
  public ForeverContentRepository(Database db) { this.db=db; }

  public Hub hub() {
    Cache cache=cache();Hub value=cache.hub;
    if(value==null)cache.hub=value=new Hub(context(), new InstanceRepository(db).all(), discoveries(), pvp());
    return value;
  }

  public Context context() {
    Cache cache=cache();Context value=cache.context;
    if(value!=null)return value;
    // Recheck under the database lock so independently created repositories share a single cold load.
    return db.read(c->{if(cache.context==null)cache.context=loadContext();return cache.context;});
  }

  private static final String CHANGE_SELECT="""
              SELECT d.kind,d.id,d.status,d.fields,
                COALESCE(NULLIF(json_extract(n.payload,'$.Name_lang'),''),
                  NULLIF(json_extract(n.payload,'$.AreaName_lang'),''),
                  NULLIF(json_extract(n.payload,'$.MapName_lang'),''),
                  NULLIF(json_extract(n.payload,'$.Display_lang'),''),
                  NULLIF(json_extract(n.payload,'$.DisplayName_lang'),''),
                  NULLIF(json_extract(n.payload,'$.Title_lang'),''),
                  NULLIF(json_extract(sp.payload,'$.Name_lang'),''),i.name,z.name,'') AS name,
                i.id AS item_id,z.id AS zone_id
              FROM selected d
              LEFT JOIN client_table_row n ON n.snapshot_id=d.forever_snapshot_id AND n.table_name=d.kind AND n.row_id=d.id
              LEFT JOIN client_table_row sp ON sp.snapshot_id=d.forever_snapshot_id AND sp.table_name='SpellName'
                AND sp.row_id=CAST(json_extract(n.payload,'$.Spell') AS TEXT)
              LEFT JOIN item i ON d.kind IN ('Item','ItemSparse') AND i.id=CAST(d.id AS INTEGER)
              LEFT JOIN zone z ON d.kind='AreaTable' AND z.id=CAST(d.id AS INTEGER)
              ORDER BY CASE d.kind WHEN 'Map' THEN 0 WHEN 'ItemSet' THEN 1 WHEN 'ChrRaces' THEN 2 WHEN 'AreaTable' THEN 3 ELSE 4 END,
                d.kind,d.status DESC,CAST(d.id AS INTEGER)
              """;

  private Context loadContext() {
    return readContext("WITH ","",List.of(),List.of());
  }

  /** Restrict IDs before looking up native names. Ordinary page headers never load the global diff. */
  private Context readContext(String prefix,String restriction,List<Object> leading,List<Object> trailing) {
    return db.read(c -> {
      var meta=db.meta();
      if (!Sql.hasTable(c,"forever_diff")) return Context.empty();
      String era=meta.get("forever_diff_era_snapshot_id"), forever=meta.get("forever_diff_forever_snapshot_id");
      if(era==null || forever==null)return Context.empty();
      String foreverBuild=Sql.one(c,"SELECT build FROM client_table_dataset WHERE snapshot_id=?",r->r.getString(1),forever).orElse("unknown");
      String eraBuild=Sql.one(c,"SELECT build FROM client_table_dataset WHERE snapshot_id=?",r->r.getString(1),era).orElse("unknown");
      List<Object> parameters=new ArrayList<>(leading);parameters.add(era);parameters.add(forever);parameters.addAll(trailing);
      String sql=prefix+"selected AS MATERIALIZED (SELECT * FROM forever_diff WHERE era_snapshot_id=? AND forever_snapshot_id=? "
          +restriction+") "+CHANGE_SELECT;
      return new Context(foreverBuild,eraBuild,Sql.list(c,sql,r -> {
        var ref=r.getObject("item_id")!=null?new dev.wowforever.app.model.EntityRef(dev.wowforever.app.model.EntityKind.ITEM,r.getLong("item_id")):
            r.getObject("zone_id")!=null?new dev.wowforever.app.model.EntityRef(dev.wowforever.app.model.EntityKind.ZONE,r.getLong("zone_id")):null;
        return new Change(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString("name"),ref);
      },parameters.toArray()));
    });
  }

  /** Only descendant AreaTable IDs are included; similarly named areas are never linked. */
  public Context zoneContext(long zoneId) {return cache().zones.computeIfAbsent(zoneId,this::loadZoneContext);}
  private Context loadZoneContext(long zoneId) {
    return readContext("""
        WITH RECURSIVE descendants(id) AS (
          SELECT ? UNION SELECT z.id FROM zone z JOIN descendants d ON z.parent_id=d.id
        ),
        """, "AND kind COLLATE NOCASE IN ('AreaTable','zone') AND id IN (SELECT CAST(id AS TEXT) FROM descendants)",
        List.of(zoneId),List.of());
  }

  public Context instanceContext(String key) {
    if(key==null || !key.startsWith("client:")) return Context.empty();
    return cache().instances.computeIfAbsent(key,this::loadInstanceContext);
  }
  private Context loadInstanceContext(String key) {
    return readContext("WITH ","AND kind='Map' AND id=?",List.of(),List.of(key.substring("client:".length())));
  }

  public List<Discovery> discoveries() {
    Cache cache=cache();List<Discovery> value=cache.discoveries;
    if(value==null)cache.discoveries=value=loadDiscoveries();return value;
  }
  private List<Discovery> loadDiscoveries() {
    return db.read(c -> {
      var maps=table(c,"Map",List.of("MapName_lang","InstanceType"));
      var achievements=table(c,"Achievement",List.of("Title_lang","Instance_ID","Category"));
      var encounters=table(c,"DungeonEncounter",List.of("Name_lang","MapID","OrderIndex"));
      Map<Long,List<Encounter>> bosses=new LinkedHashMap<>();
      Map<Long,String> reportedNames=new LinkedHashMap<>();
      for (var row:encounters) {
        long map=number(row.cells().get(1));
        if (map>0 && !row.cells().get(0).isBlank()) bosses.computeIfAbsent(map,k->new ArrayList<>())
            .add(new Encounter(row.cells().get(0),"Known",row.source()));
      }
      // Achievement names report encounter names, not a complete or confirmed encounter roster.
      for (var row:achievements) {
        long map=number(row.cells().get(1));
        if (!"14821".equals(row.cells().get(2)) || map<=0) continue;
        String title=row.cells().get(0);
        if (title.isBlank()) continue;
        var report=java.util.regex.Pattern.compile("^(.+?) [Kk]ills \\((.+)\\)$").matcher(title);
        if(!report.matches()) continue;
        String name=report.group(1);reportedNames.putIfAbsent(map,report.group(2));
        var list=bosses.computeIfAbsent(map,k->new ArrayList<>());
        if (list.stream().noneMatch(b->b.name().equals(name))) list.add(new Encounter(name,"Reported",row.source()));
      }
      List<Discovery> result=new ArrayList<>();
      for (var row:maps) {
        long map=number(row.id());
        if (!DISCOVERY_MAPS.contains(map) || !("1".equals(row.cells().get(1)) || "2".equals(row.cells().get(1)) || bosses.containsKey(map))) continue;
        result.add(new Discovery(map,row.cells().get(0),"2".equals(row.cells().get(1))?"Raid":"Dungeon",row.build(),
            bosses.getOrDefault(map,List.of()),"Not yet announced in the archived sources", ""));
      }
      for(var entry:reportedNames.entrySet()) {
        long map=entry.getKey();
        if(!DISCOVERY_MAPS.contains(map) || result.stream().anyMatch(d->d.mapId()==map))continue;
        var evidence=achievements.stream().filter(r->number(r.cells().get(1))==map).findFirst().orElseThrow();
        result.add(new Discovery(map,entry.getValue(),announcedType(map),evidence.build(),bosses.getOrDefault(map,List.of()),
            announcedOpening(map),announcedSource(map)));
      }
      return List.copyOf(result);
    });
  }

  public Pvp pvp() {
    Cache cache=cache();Pvp value=cache.pvp;
    if(value==null)cache.pvp=value=loadPvp();return value;
  }
  private Pvp loadPvp() {
    return db.read(c -> {
      List<Discovery> maps=new ArrayList<>();
      for(var r:table(c,"Map",List.of("MapName_lang","InstanceType")))
        if ("Darkspear Islands".equals(r.cells().get(0))) maps.add(new Discovery(number(r.id()),r.cells().get(0),"Battleground",r.build(),List.of(),"Not yet announced in the archived sources",""));
      List<Faction> factions=new ArrayList<>();
      for(var r:table(c,"Faction",List.of("Name_lang","Description_lang")))
        if (List.of("Darkspear Raiders","Theramore Expeditionary Force").contains(r.cells().get(0)))
          factions.add(new Faction(number(r.id()),r.cells().get(0),r.cells().get(1),r.build()));
      return new Pvp(maps,factions);
    });
  }

  private static final String RAID_ANNOUNCEMENT="https://worldofwarcraft.blizzard.com/en-us/news/24303862/world-of-warcraft-forever-whats-next-panel-recap";
  private static String announcedType(long map) {
    return map==2981?"Raid · 20 players (announced)":map==3052?"Raid · 10 players (announced)":"Instance (Reported)";
  }
  private static String announcedOpening(long map) {
    return map==2981 || map==3052?"9 December 2026 — shared new-raid announcement; no unlock hour announced":"Not yet announced in the archived sources";
  }
  private static String announcedSource(long map) {return map==2981 || map==3052?RAID_ANNOUNCEMENT:"";}

  private record Row(String id,List<String> cells,String build,String source) {}
  private static List<Row> table(Connection c,String name,List<String> fields) throws SQLException {
    if(!Sql.hasTable(c,"client_table_row") || !Sql.hasTable(c,"client_table_dataset")) return List.of();
    String pinned=Sql.one(c,"SELECT value FROM meta WHERE key='forever_diff_forever_snapshot_id'",r->r.getString(1)).orElse(null);
    for(var dataset:ClientTableRows.datasets(c)) {
      if(pinned!=null && !pinned.equals(dataset.snapshotId()))continue;
      if(!"wow_classic_beta".equals(dataset.product())) continue;
      // Only the fields used by this view cross JDBC; wide raw CSV payloads stay in SQLite.
      String columns=String.join(",",fields.stream().map(f->"json_extract(payload,'$."+f+"')").toList());
      List<Row> rows=Sql.list(c,"SELECT row_id,source_path,source_line,"+columns
          +" FROM client_table_row WHERE snapshot_id=? AND table_name=? ORDER BY source_line",r->{
        List<String> cells=new ArrayList<>();
        for(int index=0;index<fields.size();index++) {
          String value=r.getString(index+4);cells.add(value==null||value.isBlank()?"":value);
        }
        return new Row(r.getString(1),List.copyOf(cells),dataset.build(),name+" · build "+dataset.build()+" · "+r.getString(2)+":"+r.getLong(3));
      },dataset.snapshotId(),name);
      if(!rows.isEmpty())return rows;
    }
    return List.of();
  }

  private static long number(String value) { try{return Long.parseLong(value);}catch(NumberFormatException e){return 0;} }
}
