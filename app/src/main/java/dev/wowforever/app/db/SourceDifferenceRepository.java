package dev.wowforever.app.db;

import dev.wowforever.app.model.SourceDifference;
import dev.wowforever.app.model.SourceDifference.Assertion;
import dev.wowforever.app.model.RateSource;
import java.sql.*;
import java.util.*;

/** Loads supplemental comparisons on the detail worker. No values are changed. */
public final class SourceDifferenceRepository {
  private final Database db;
  public SourceDifferenceRepository(Database db) { this.db=db; }
  public List<SourceDifference> item(long id) {
    return db.read(c -> {
      var out=new ArrayList<SourceDifference>();
      if(Sql.hasTable(c,"item_alt_value")) {
        var fields=Sql.list(c,"SELECT field,source,value FROM item_alt_value WHERE item_id=? ORDER BY field,source",
            r -> new String[]{r.getString(1),r.getString(2),r.getString(3)},id);
        Map<String,String> allowed=Map.of("name","Name","item_level","Item level","required_level","Required level","class_id","Class ID","subclass_id","Subclass ID");
        String build=meta(c,"client_build");
        for(var f:fields) if(allowed.containsKey(f[0])) {
          var current=Sql.one(c,"SELECT "+f[0]+",client_source FROM item WHERE id=?",r ->
              new String[]{r.getString(1),r.getString(2)},id);
          if(current.isPresent()) out.add(new SourceDifference(allowed.get(f[0]),f[1].startsWith("baseline-before-hotfix:")?"Updated by hotfix":"Values differ",List.of(
              new Assertion(Objects.toString(current.get()[1],"Client data"),Objects.toString(current.get()[0],"Unknown"),
                  ("era".equals(current.get()[1]) ? "Classic Era value · build "+meta(c,"client_fallback_build") : build.isBlank()?"Selected item value":"Client build "+build),true),
              new Assertion(f[1],Objects.toString(f[2],"Unknown"),"Retained alternate item value · "+provenance(c,f[1]),false))));
        }
      }
      hotfix(c,id,out);knowledge(c,id,out);rates(c,id,null,out);addon(c,id,null,out);return List.copyOf(out);
    });
  }
  public List<SourceDifference> boss(long id) {
    return db.read(c -> {var out=new ArrayList<SourceDifference>();rates(c,null,id,out);addon(c,null,id,out);return List.copyOf(out);});
  }
  private static void hotfix(Connection c,long item,List<SourceDifference> out)throws SQLException {
    if(!Sql.hasTable(c,"hotfix_previous_item"))return;
    Set<String> fields=Set.of("item_level","required_level","class_id","subclass_id","quality","inventory_type","sell_price","buy_price","max_stack","item_set_id","description","allowable_class","allowable_race","required_skill","required_skill_rank");
    for(var prior:Sql.list(c,"SELECT snapshot_id,field,value FROM hotfix_previous_item WHERE item_id=? ORDER BY field,snapshot_id",r->new String[]{r.getString(1),r.getString(2),r.getString(3)},item)) {
      if(!fields.contains(prior[1]))continue;
      var current=Sql.one(c,"SELECT "+prior[1]+",client_source FROM item WHERE id=?",r->new String[]{r.getString(1),r.getString(2)},item);
      if(current.isEmpty() || Objects.equals(current.get()[0],prior[2]))continue;
      out.add(new SourceDifference(prior[1].replace('_',' '),"Updated by hotfix",List.of(
          new Assertion(Objects.toString(current.get()[1],"Current item data"),Objects.toString(current.get()[0],"Unknown"),"Selected item value",true),
          new Assertion("Before this hotfix",Objects.toString(prior[2],"Unknown"),"Previous canonical scalar retained by hotfix snapshot "+prior[0],false))));
    }
  }
  private static void knowledge(Connection c,long item,List<SourceDifference> out)throws SQLException {
    if(!Sql.hasTable(c,"field_knowledge") || !Sql.hasTable(c,"field_knowledge_assertion"))return;
    boolean retractions=Sql.hasTable(c,"field_knowledge_retraction");
    record Claim(String field,String source,String version,String state,String value,String snapshot,String path,Integer line,boolean selected,String retraction) {}
    var claims=Sql.list(c,"SELECT a.*,k.assertion_id AS selected_assertion,"+(retractions?"r.reason":"NULL")+" AS retraction_reason FROM field_knowledge_assertion a LEFT JOIN field_knowledge k ON k.assertion_id=a.assertion_id "
        +(retractions?"LEFT JOIN field_knowledge_retraction r ON r.assertion_id=a.assertion_id ":"")+"WHERE a.entity='item' AND a.id=? ORDER BY a.field,a.assertion_id",
        r -> new Claim(r.getString("field"),r.getString("source"),r.getString("data_version"),r.getString("state"),r.getString("value"),r.getString("snapshot"),r.getString("path"),Sql.integer(r,"line"),r.getObject("selected_assertion")!=null,r.getString("retraction_reason")),Long.toString(item));
    var groups=new LinkedHashMap<String,List<Claim>>();for(var claim:claims)groups.computeIfAbsent(claim.field,k->new ArrayList<>()).add(claim);
    for(var group:groups.entrySet()) {
      var values=group.getValue();
      var active=values.stream().filter(v->v.retraction==null).toList();
      String status=active.stream().map(Claim::version).distinct().count()>1?"Retained assertions across data updates":active.stream().map(Claim::value).filter(Objects::nonNull).distinct().count()>1?"Values differ":active.stream().map(Claim::state).distinct().count()>1?"Knowledge assertions differ":"Retained knowledge evidence";
      out.add(new SourceDifference(group.getKey().replace('_',' '),status,values.stream().map(v->new Assertion(v.source+" · "+v.version,
          v.state.replace('_',' ')+" · "+(v.state.equals("NONE") && v.value==null?"None":Objects.toString(v.value,"Unknown"))+(v.retraction==null?"":" · retracted: "+v.retraction),
          v.path+(v.line==null?"":":"+v.line)+" · snapshot "+v.snapshot,v.selected)).toList()));
    }
  }
  private static void rates(Connection c,Long item,Long boss,List<SourceDifference> out) throws SQLException {
    if(!Sql.hasTable(c,"drop_rate_source"))return;
    String where=item!=null?"r.item_id=?":"r.npc_id=(SELECT npc_id FROM boss WHERE id=?)";
    record Value(long npc,long item,String npcName,String itemName,String source,Double pct,boolean quest,String selected) {}
    var rows=Sql.list(c,"SELECT r.*,n.name AS npc_name,i.name AS item_name,d.pct_source FROM drop_rate_source r LEFT JOIN npc n ON n.id=r.npc_id LEFT JOIN item i ON i.id=r.item_id LEFT JOIN item_npc_drop d ON d.npc_id=r.npc_id AND d.item_id=r.item_id WHERE "+where+" ORDER BY r.npc_id,r.item_id,r.source",
        r -> new Value(r.getLong("npc_id"),r.getLong("item_id"),r.getString("npc_name"),r.getString("item_name"),r.getString("source"),Sql.doubleValue(r,"pct"),r.getBoolean("quest_only"),r.getString("pct_source")),item!=null?item:boss);
    var groups=new LinkedHashMap<String,List<Value>>();for(var row:rows)groups.computeIfAbsent(row.npc+":"+row.item,k->new ArrayList<>()).add(row);
    for(var values:groups.values()) {
      var first=values.getFirst();
      boolean scope=values.stream().map(Value::quest).distinct().count()>1;
      boolean differs=values.stream().map(Value::pct).filter(Objects::nonNull).distinct().count()>1;
      var evidence=new HashMap<String,String>();
      for(var v:values)evidence.put(v.source,provenance(c,v.source));
      var assertions=values.stream().map(v -> new Assertion(new RateSource(v.source,v.pct,v.quest).label(),
          v.pct==null?"No rate":v.pct+" %"+(v.quest?" (quest only)":" (per kill)"),
          (v.source.equals("vmangos")?"Vanilla rate (VMaNGOS)":"Retained source rate")+" · "+evidence.get(v.source),Objects.equals(v.source,v.selected))).toList();
      out.add(new SourceDifference("Drop rate · "+Objects.toString(first.npcName,"NPC #"+first.npc)+" · "+Objects.toString(first.itemName,"Item #"+first.item),
          scope?"Different rate scopes":values.stream().allMatch(v->v.pct==null)?"No rate supplied":values.stream().anyMatch(v->v.pct==null)?"Incomplete rate coverage":differs?"Values differ":values.size()==1?"Single-source coverage":"Sources agree on rate",assertions));
    }
  }
  private static void addon(Connection c,Long item,Long boss,List<SourceDifference> out)throws SQLException {
    if(!Sql.hasTable(c,"atlasloot_forever_fact"))return;
    record Fact(long boss,long item,String name,String bossName,String difficulty,String provider,String version,int slot,Long npc,String path,int line,String snapshot) {}
    var rows=Sql.list(c,"SELECT f.*,i.name AS item_name,s.version FROM atlasloot_forever_fact f LEFT JOIN item i ON i.id=f.item_id LEFT JOIN source_archive_snapshot s ON s.id=f.snapshot_id WHERE "+(item!=null?"f.item_id=?":"f.boss_id=?")+" ORDER BY f.boss_id,f.item_id,f.difficulty,f.provider,f.snapshot_id",
        r -> new Fact(r.getLong("boss_id"),r.getLong("item_id"),r.getString("item_name"),r.getString("source_boss_name"),r.getString("difficulty"),r.getString("provider"),r.getString("version"),r.getInt("source_slot"),Sql.longValue(r,"claimed_npc_id"),r.getString("source_path"),r.getInt("source_line"),r.getString("snapshot_id")),item!=null?item:boss);
    var groups=new LinkedHashMap<String,List<Fact>>();for(var f:rows)groups.computeIfAbsent(f.boss+":"+f.item+":"+f.difficulty,k->new ArrayList<>()).add(f);
    for(var facts:groups.values()) {
      var first=facts.getFirst();
      String classification=facts.stream().map(Fact::npc).filter(Objects::nonNull).distinct().count()>1?"Claimed NPC IDs differ":facts.stream().map(Fact::slot).distinct().count()>1?"List order differs":facts.stream().map(Fact::provider).distinct().count()==1?"Single-source coverage":"Sources agree on listing";
      var assertions=new ArrayList<Assertion>();
      var canonical=Sql.one(c,"SELECT b.npc_id,bl.slot FROM boss b JOIN boss_loot bl ON bl.boss_id=b.id WHERE b.id=? AND bl.item_id=? AND bl.difficulty=?",
          r -> new Long[]{Sql.longValue(r,"npc_id"),Sql.longValue(r,"slot")},first.boss,first.item,first.difficulty);
      if(canonical.isPresent()) {
        Long npc=canonical.get()[0],slot=canonical.get()[1];
        if(npc!=null && facts.stream().anyMatch(f -> f.npc!=null && !npc.equals(f.npc)))classification="Claimed NPC differs from client identity";
        assertions.add(new Assertion("Displayed loot listing","Listed · "+first.difficulty+" · position "+Objects.toString(slot,"unknown")
            +(npc==null?" · NPC identity unknown":" · client NPC #"+npc),"Reported addon presence; claimed NPC IDs do not establish client identities.",true));
      }
      for(var f:facts)assertions.add(new Assertion(f.provider+" · "+f.version,"Listed · "+f.difficulty+" · position "+f.slot+(f.npc==null?" · NPC identity not supplied":" · claimed NPC #"+f.npc),
          f.path+":"+f.line+" · snapshot "+f.snapshot,false));
      if(classification.equals("Single-source coverage"))assertions.add(new Assertion("Other sources","No supporting listing retained","Missing coverage is not a claim that this item cannot drop.",false));
      out.add(new SourceDifference("Loot listing · "+Objects.toString(first.name,"Item #"+first.item)+" · "+first.bossName,classification,assertions));
    }
  }
  private static String provenance(Connection c,String source)throws SQLException {
    String key=source.startsWith("questie")?"questiedb_commit":source.startsWith("atlasloot")?"atlasloot_commit":source.equals("vmangos")?"serverdb_commit":"";
    String revision=key.isEmpty()?"":meta(c,key);
    return revision.isBlank()?"Source revision not recorded":revision;
  }
  private static String meta(Connection c,String key)throws SQLException {
    return Sql.one(c,"SELECT value FROM meta WHERE key=?",r->r.getString(1),key).orElse("");
  }
}
