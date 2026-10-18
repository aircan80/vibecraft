package dev.wowforever.app.db;

import java.sql.*;
import java.util.*;
import static dev.wowforever.app.db.ClientTableRows.*;

/** Native teaching relationships are independent of reported acquisition or player eligibility. */
public final class NativeTeachingItemRepository {
  public record Evidence(String table,String path,long line,Map<String,String> fields) {
    public Evidence { fields=Collections.unmodifiableMap(new LinkedHashMap<>(fields)); }
  }
  public record Support(String relationship,String condition,List<Evidence> evidence) {
    public Support { evidence=List.copyOf(evidence); }
  }
  public record TeachingItem(long itemId,String name,List<Support> supports) {
    public TeachingItem { supports=List.copyOf(supports); }
  }
  public record Snapshot(String snapshotId,String build,String locale,long spellId,List<TeachingItem> items,String coverage) {
    public Snapshot { items=List.copyOf(items); }
  }
  private final Database db;
  private static final Map<Database,Map<String,Snapshot>> CACHE=Collections.synchronizedMap(new WeakHashMap<>());
  public NativeTeachingItemRepository(Database db){this.db=db;}

  /** Worker-only bounded reverse queries; never uses normalized legacy recipe links as authority. */
  public Snapshot forSpell(long spell) {
    String pin=db.meta().get("forever_diff_forever_snapshot_id");
    if(pin==null||spell<=0)return empty(spell,"No pinned Forever native snapshot is installed.");
    String key=pin+":"+spell;
    synchronized(CACHE){var entries=CACHE.get(db);if(entries!=null&&entries.containsKey(key))return entries.get(key);}
    Snapshot snapshot=db.read(c->load(c,pin,spell));
    synchronized(CACHE){var entries=CACHE.computeIfAbsent(db,ignored->new LinkedHashMap<>(32,.75f,true));entries.put(key,snapshot);while(entries.size()>32)entries.remove(entries.keySet().iterator().next());}
    return snapshot;
  }
  private static Snapshot load(Connection c,String pin,long spell)throws SQLException {
    if(!Sql.hasTable(c,"client_table_dataset")||!Sql.hasTable(c,"client_table_row"))return empty(spell,"Native source tables are not installed.");
    Dataset data=Sql.one(c,"SELECT snapshot_id,product,build,locale FROM client_table_dataset WHERE snapshot_id=? AND product='wow_classic_beta'",r->new Dataset(r.getString(1),r.getString(2),r.getString(3),r.getString(4)),pin).orElse(null);
    if(data==null)return empty(spell,"The pinned snapshot is not a Forever client dataset.");
    var creates=ItemContentRows.read(c,data,"SpellEffect"," AND CAST(json_extract(r.payload,'$.Effect') AS INTEGER)=24 AND CAST(json_extract(r.payload,'$.SpellID') AS INTEGER)=? AND CAST(json_extract(r.payload,'$.EffectItemType') AS INTEGER)>0",List.of(spell))
        .stream().filter(row->Long.valueOf(spell).equals(row.number("SpellID"))&&Long.valueOf(24).equals(row.number("Effect"))&&row.number("EffectItemType")!=null&&row.number("EffectItemType")>0).toList();
    if(creates.isEmpty())return new Snapshot(pin,data.build(),data.locale(),spell,List.of(),"No native create-item effect is retained for this spell.");
    var learns=ItemContentRows.read(c,data,"SpellEffect"," AND CAST(json_extract(r.payload,'$.Effect') AS INTEGER)=36 AND CAST(json_extract(r.payload,'$.EffectTriggerSpell') AS INTEGER)=?",List.of(spell));
    Map<Long,List<Row>> learnsBySpell=new LinkedHashMap<>();
    for(Row row:learns){Long id=row.number("SpellID");if(id!=null&&id>0&&Long.valueOf(36).equals(row.number("Effect"))&&Long.valueOf(spell).equals(row.number("EffectTriggerSpell")))learnsBySpell.computeIfAbsent(id,ignored->new ArrayList<>()).add(row);}
    List<Long> spellIds=new ArrayList<>(learnsBySpell.keySet());if(!spellIds.contains(spell))spellIds.add(spell);
    String placeholders=String.join(",",Collections.nCopies(spellIds.size(),"?"));
    var effects=ItemContentRows.read(c,data,"ItemEffect"," AND CAST(json_extract(r.payload,'$.TriggerType') AS INTEGER) IN (0,6) AND CAST(json_extract(r.payload,'$.SpellID') AS INTEGER) IN ("+placeholders+")",spellIds);
    Map<Long,Row> effectsById=new LinkedHashMap<>();for(Row effect:effects){Long id=effect.number("ID");if(id!=null&&id>0)effectsById.put(id,effect);}
    if(effectsById.isEmpty())return new Snapshot(pin,data.build(),data.locale(),spell,List.of(),missing());
    List<Long> effectIds=new ArrayList<>(effectsById.keySet());String links=String.join(",",Collections.nCopies(effectIds.size(),"?"));
    var itemLinks=ItemContentRows.read(c,data,"ItemXItemEffect"," AND CAST(json_extract(r.payload,'$.ItemEffectID') AS INTEGER) IN ("+links+")",effectIds);
    Map<Long,List<Support>> supports=new TreeMap<>();
    for(Row link:itemLinks) {
      Long item=link.number("ItemID"),effectId=link.number("ItemEffectID");if(item==null||item<=0||effectId==null)continue;
      Row effect=effectsById.get(effectId);if(effect==null)continue;
      Long triggered=effect.number("SpellID"),trigger=effect.number("TriggerType");
      if(triggered==null||trigger==null)continue;
      List<Evidence> evidence=new ArrayList<>();evidence.add(evidence("ItemXItemEffect",link));evidence.add(evidence("ItemEffect",effect));
      for(Row create:creates)evidence.add(evidence("SpellEffect",create));
      if(trigger==6&&triggered==spell) {
        supports.computeIfAbsent(item,ignored->new ArrayList<>()).add(new Support("Direct native learn-item effect (TriggerType 6)",condition(effect),evidence));
      }
      for(Row learn:learnsBySpell.getOrDefault(triggered,List.of())) {
        List<Evidence> path=new ArrayList<>(evidence);path.add(evidence("SpellEffect",learn));
        supports.computeIfAbsent(item,ignored->new ArrayList<>()).add(new Support("Native item effect → explicit Learn Spell effect (36)",condition(effect),path));
      }
    }
    List<TeachingItem> items=new ArrayList<>();
    for(var entry:supports.entrySet()) {
      String name=null;
      // Database.open installs the sanitized Forever item projection; Era-only names stay absent.
      if(Sql.hasTable(c,"item"))name=Sql.one(c,"SELECT name FROM item WHERE id=?",r->r.getString(1),entry.getKey()).orElse(null);
      if(name==null||name.isBlank())name="Item #"+entry.getKey();
      items.add(new TeachingItem(entry.getKey(),name,entry.getValue()));
    }
    return new Snapshot(pin,data.build(),data.locale(),spell,items,items.isEmpty()?missing():"Exact native teaching relationships; learning eligibility and reported acquisition are separate evidence.");
  }
  private static String condition(Row row) {
    Long id=row.number("PlayerConditionID"),specialization=row.number("ChrSpecializationID");
    String player=id==null||id<0?"Player-condition field missing or malformed; eligibility unknown.":id==0?"PlayerConditionID is 0; this does not prove unrestricted learning.":"PlayerCondition #"+id+" is recorded; eligibility has not been evaluated.";
    return player+(specialization==null||specialization<0?" Specialization field missing or malformed.":specialization==0?"":" Specialization #"+specialization+" is recorded and has not been evaluated.");
  }
  private static Evidence evidence(String table,Row row){return new Evidence(table,row.sourcePath(),row.sourceLine(),row.cells());}
  private static String missing(){return "No teaching item recorded in this pinned native snapshot. This does not establish a trainer source, automatic learning, or unavailability.";}
  private static Snapshot empty(long spell,String coverage){return new Snapshot("","unknown","unknown",spell,List.of(),coverage);}
}
