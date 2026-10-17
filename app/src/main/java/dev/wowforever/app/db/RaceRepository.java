package dev.wowforever.app.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/** Read-only race catalogue derived from archived client rows; absent facts stay absent. */
public final class RaceRepository {
  public record ClassChoice(int id, String name, Boolean classicAvailable) {}
  public record Racial(int abilityId, String name, String description, String kind, String source) {}
  public record Race(int id, String name, String startZone, List<ClassChoice> classes, List<Racial> racials) {
    public Race { classes = List.copyOf(classes); racials = List.copyOf(racials); }
  }
  public record Catalog(String build, String classicBuild, List<Race> races) {
    public Catalog { races = List.copyOf(races); }
  }
  // Database handles are immutable, read-only snapshots. Weak keys do not retain closed handles.
  private static final Map<Database,Catalog> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
  private final Database db;
  public RaceRepository(Database db) { this.db = db; }
  public Catalog load() {
    synchronized (db) {
      Catalog cached = CACHE.get(db);
      if (cached != null) return cached;
      Catalog loaded = db.read(this::load);
      CACHE.put(db, loaded);
      return loaded;
    }
  }
  private Catalog load(Connection c) throws SQLException {
    if (!Sql.hasTable(c,"client_table_dataset") || !Sql.hasTable(c,"client_table_row"))
      return new Catalog("", "", List.of());
    String[] current = null, era = null;
    for (var d : ClientTableRows.datasets(c)) {
      if (d.product().contains("beta") && current == null) current=new String[]{d.snapshotId(),d.product(),d.build()};
      else if ((d.product().equals("wow_classic_era") || d.product().equals("wow_classic")) && era == null) era=new String[]{d.snapshotId(),d.product(),d.build()};
    }
    if (current == null) return new Catalog("","",List.of());
    var races=rows(c,current[0],"ChrRaces"); var combos=rows(c,current[0],"CharBaseInfo");
    var classRows=rows(c,current[0],"ChrClasses"); var abilities=rows(c,current[0],"ChrRaceRacialAbility");
    
    var baseline=era==null ? List.<Map<String,String>>of() : rows(c,era[0],"CharBaseInfo");
    var result=new ArrayList<Race>();
    for (var race:races) {
      int id=number(race,"ID"); if ((number(race,"Flags") & 1) != 0) continue; var choices=new ArrayList<ClassChoice>();
      for(var combo:combos) if(number(combo,"RaceID","Race")==id) {
        int cl=number(combo,"ClassID","Class"); if(cl<=0) continue;
        // Only combos in the client allow-list qualify; no inferred class availability.
        String name=find(classRows,cl,"Name_lang","Name");
        Boolean old=era==null || baseline.isEmpty() ? null : baseline.stream().anyMatch(x -> number(x,"RaceID","Race")==id && number(x,"ClassID","Class")==cl);
        if(choices.stream().noneMatch(x->x.id()==cl)) choices.add(new ClassChoice(cl,name.isBlank()?"Class "+cl:name,old));
      }
      if(choices.isEmpty()) continue;
      var racial=new ArrayList<Racial>();
      for(var a:abilities) if(number(a,"ChrRacesID","RaceID")==id) {
        int ability=number(a,"ID"); if(ability<=0)continue;
        String rawName=value(a,"Name_lang","Name");
        String kind=rawName.contains(" - Active - ")?"Active":rawName.contains(" - Passive - ")?"Passive":"Classification not recorded";
        String name=rawName.contains(" - ")?rawName.substring(rawName.lastIndexOf(" - ")+3):rawName;
        racial.add(new Racial(ability,name,value(a,"Description_lang","Description"),kind, "ChrRaceRacialAbility · "+current[2]));
      }
      // A race-to-start-area relationship must be present explicitly; Map labels alone are insufficient.
      result.add(new Race(id,value(race,"Name_lang","Name"),value(race,"StartZoneName_lang","StartZoneName"),choices,racial));
    }
    result.sort(Comparator.comparing(Race::name));
    return new Catalog(current[2],era==null?"":era[2],result);
  }
  static List<Map<String,String>> rows(Connection c,String snapshot,String table)throws SQLException {
    return ClientTableRows.read(c,snapshot,table).stream().map(ClientTableRows.Row::cells).toList();
  }

  static String value(Map<String,String> row,String...keys){for(String key:keys){String v=row.get(key);if(v!=null&&!v.isBlank())return v;}return "";}
  static int number(Map<String,String> row,String...keys){try{return Integer.parseInt(value(row,keys));}catch(NumberFormatException e){return 0;}}
  private static String find(List<Map<String,String>> rows,int id,String...keys){return rows.stream().filter(x->number(x,"ID")==id).findFirst().map(x->value(x,keys)).orElse("");}
}
