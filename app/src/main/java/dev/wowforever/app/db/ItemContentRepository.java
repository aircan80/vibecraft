package dev.wowforever.app.db;

import dev.wowforever.app.model.StatGlossary;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import static dev.wowforever.app.db.ClientTableRows.*;

/** Preloads item content from retained native client tables and the existing derived item projection. */
public final class ItemContentRepository {
  private static final Map<Database,Map<String,Snapshot>> CONTENT_CACHE=Collections.synchronizedMap(new WeakHashMap<>());
  private static final Map<Database,Map<Long,Appearance>> APPEARANCE_CACHE=Collections.synchronizedMap(new WeakHashMap<>());
  private final Database db;
  public ItemContentRepository(Database db) {this.db=db;}
  public record Entry(long id,String name,String details,String state,String target) {}
  public record Snapshot(String section,long id,String title,String note,List<Entry> entries,List<Entry> classic) {
    public Snapshot {entries=List.copyOf(entries);classic=List.copyOf(classic);}
  }
  public record Appearance(boolean available,String build,String note,List<Entry> members) {
    public Appearance {members=List.copyOf(members);}
  }
  private record Tables(Connection c,Dataset current,Dataset era) {
    List<Row> rows(String table) throws SQLException {return current==null?List.of():read(c,current.snapshotId(),table);}
    List<Row> era(String table) throws SQLException {return era==null?List.of():read(c,era.snapshotId(),table);}
  }
  private static Tables tables(Connection c) throws SQLException {
    var all=datasets(c);
    var current=all.stream().filter(d->d.build().startsWith("1.60.")).max(Comparator.comparing(Dataset::build)).orElse(null);
    var era=all.stream().filter(d->d.build().startsWith("1.15.")).max(Comparator.comparing(Dataset::build)).orElse(null);
    return new Tables(c,current,era);
  }
  public Snapshot load(String section,long id) {
    synchronized(db) {
    var cache=CONTENT_CACHE.computeIfAbsent(db,k->new HashMap<>());String key=section+":"+id;
    if(cache.containsKey(key))return cache.get(key);
    Snapshot result=db.read(c->{var t=tables(c);return switch(section) {
      case "sets" -> sets(t);
      case "set" -> set(t,id);
      case "stats" -> stats(t,0,false,true);
      case "stat" -> stats(t,id,false,false);
      case "trinkets" -> stats(t,id,true,false);
      case "recipes" -> recipes(t,id);
      case "xp-food", "comprehension" -> special(t,section);
      default -> new Snapshot(section,id,"Item content","Unknown content page",List.of(),List.of());
    };});
    cache.put(key,result);return result;
    }
  }
  private static String provenance(Tables t) {
    return t.current==null?"Derived client data; native comparison tables not yet retained":
      "Forever "+t.current.build()+" · Classic Era "+(t.era==null?"baseline not yet retained":t.era.build());
  }
  private static Map<Long,Row> index(List<Row> rows,String column) {
    Map<Long,Row> map=new LinkedHashMap<>();for(var row:rows){Long id=row.number(column);if(id!=null)map.put(id,row);}return map;
  }
  private static String idsSql(Collection<Long> ids) {return ids.isEmpty()?"NULL":ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));}
  private static long number(Row r,String col) {Long n=r.number(col);return n==null?0:n;}
  private static Snapshot sets(Tables t)throws SQLException {
    var rows=t.rows("ItemSet");List<Entry> out=new ArrayList<>();
    if(rows.isEmpty())out.addAll(Sql.list(t.c,"SELECT id,name FROM item_set ORDER BY name",r->new Entry(r.getLong(1),r.getString(2),"Client item set","Known","set")));
    else {
      Map<String,String> statuses=new HashMap<>();
      if(t.current!=null&&t.era!=null&&Sql.hasTable(t.c,"forever_diff")&&Sql.hasTable(t.c,"forever_diff_coverage")) {
        boolean comparable=Sql.one(t.c,"SELECT 1 FROM forever_diff_coverage WHERE kind='ItemSet' AND forever_snapshot_id=? AND era_snapshot_id=? AND state IN ('comparable','partial_schema')",r->true,t.current.snapshotId(),t.era.snapshotId()).orElse(false);
        if(comparable)for(var change:Sql.list(t.c,"SELECT id,status FROM forever_diff WHERE kind='ItemSet' AND forever_snapshot_id=? AND era_snapshot_id=?",r->new String[]{r.getString(1),r.getString(2)},t.current.snapshotId(),t.era.snapshotId()))statuses.put(change[0],change[1]);
      }
      for(var r:rows){long id=number(r,"ID");String status=statuses.get(Long.toString(id));String state="new".equals(status)?"New":"changed".equals(status)?"Changed":"Known";out.add(new Entry(id,r.text("Name_lang","Item set #"+id),"Item set #"+id,state,"set"));}
    }
    return new Snapshot("sets",0,"Item sets",provenance(t),out,List.of());
  }
  private static Snapshot set(Tables t,long id)throws SQLException {
    Row row=index(ItemContentRows.ids(t.c,t.current,"ItemSet",List.of(id)),"ID").get(id),old=index(ItemContentRows.ids(t.c,t.era,"ItemSet",List.of(id)),"ID").get(id);
    String name=row==null?Sql.one(t.c,"SELECT name FROM item_set WHERE id=?",r->r.getString(1),id).orElse("Item set #"+id):row.text("Name_lang","Item set #"+id);
    List<Entry> entries=members(t,row,id,false),classic=old==null?List.of():members(t,old,id,true);
    entries.addAll(bonuses(t,id,false));if(old!=null)classic.addAll(bonuses(t,id,true));
    String note=provenance(t)+(old==null?" · Classic comparison unavailable for this set":" · Compare with Classic: membership and bonus spell IDs shown side by side");
    return new Snapshot("set",id,name,note,entries,classic);
  }
  private static List<Entry> members(Tables t,Row set,long setId,boolean era)throws SQLException {
    List<Long> ids=new ArrayList<>();if(set!=null){for(int n=0;n<17;n++){Long id=set.number("ItemID_"+n);if(id!=null&&id>0)ids.add(id);}}
    else ids.addAll(Sql.list(t.c,"SELECT item_id FROM item_set_item WHERE set_id=? ORDER BY rowid",r->r.getLong(1),setId));
    Map<Long,Row> sparse=index(ItemContentRows.ids(t.c,era?t.era:t.current,"ItemSparse",ids),"ID");List<Entry> out=new ArrayList<>();
    for(long id:ids){Row s=sparse.get(id);String[] derived=Sql.one(t.c,"SELECT name,client_source,has_client_data FROM item WHERE id=?",r->new String[]{r.getString(1),r.getString(2),r.getString(3)},id).orElse(null);
      boolean hotfix=!era&&derived!=null&&derived[1]!=null&&(derived[1].toLowerCase(Locale.ROOT).contains("hotfix")||derived[1].toLowerCase(Locale.ROOT).contains("raidbots"));
      boolean known=s!=null||hotfix||(set==null&&derived!=null&&"1".equals(derived[2]));
      String label=s!=null?s.text("Display_lang","Item #"+id):derived!=null&&derived[0]!=null?derived[0]:"Item #"+id;
      out.add(new Entry(id,label,known?(era?"Classic Era value":"Client item #"+id):"Stats hidden until this item drops on a realm",known?"Known":"Hidden","item"));
    }return out;
  }
  private static List<Entry> bonuses(Tables t,long id,boolean era)throws SQLException {
    var rows=ItemContentRows.read(t.c,era?t.era:t.current,"ItemSetSpell"," AND CAST(json_extract(r.payload,'$.ItemSetID') AS INTEGER)=?",List.of(id));List<Entry> out=new ArrayList<>();
    List<Long> spellIds=rows.stream().map(r->r.number("SpellID")).filter(Objects::nonNull).toList();
    Map<Long,Row> names=index(ItemContentRows.ids(t.c,era?t.era:t.current,"SpellName",spellIds),"ID");
    Map<Long,Row> descriptions=index(ItemContentRows.ids(t.c,era?t.era:t.current,"Spell",spellIds),"ID");
    Map<Long,String> rendered=new HashMap<>();
    // Legacy Era-labelled cards were rendered using Forever spell text; never use them for Era comparison.
    String sources="i.client_source IN ('forever','hotfix')";
    var pattern=java.util.regex.Pattern.compile("^\\((\\d+)\\)\\s*Set:\\s*(.*)$",java.util.regex.Pattern.DOTALL);
    if(!era)for(String line:Sql.list(t.c,"SELECT t.text FROM item_tooltip_line t JOIN item i ON i.id=t.item_id WHERE i.item_set_id=? AND t.kind='set_bonus' AND "+sources+" ORDER BY i.id,t.ordinal",r->r.getString(1),id)) {
      if(line==null)continue;var matcher=pattern.matcher(line);if(matcher.matches())rendered.putIfAbsent(Long.valueOf(matcher.group(1)),matcher.group(2));
    }
    rows=new ArrayList<>(rows);rows.sort(Comparator.comparing((Row r)->r.number("Threshold"),Comparator.nullsLast(Long::compareTo)).thenComparing(r->r.number("SpellID"),Comparator.nullsLast(Long::compareTo)));
    for(var r:rows)if(number(r,"ItemSetID")==id){
      long spell=number(r,"SpellID");var name=names.get(spell);var description=descriptions.get(spell);
      String text=rendered.get(r.number("Threshold"));
      if(text==null&&description!=null){String raw=description.text("Description_lang");if(raw!=null&&!raw.isBlank())text=raw.contains("$")?"Client formula (not resolved): "+raw:raw;}
      if(text==null)text=(name==null?"Spell #"+spell:name.text("Name_lang","Spell #"+spell))+" · Bonus description not yet discovered";
      else if(text.contains("$"))text+=" · Client formula contains unresolved values";
      out.add(new Entry(spell,"("+r.text("Threshold","?")+") Set bonus",text,"Known",""));
    }
    if(rows.isEmpty()&&!era&&t.rows("ItemSetSpell").isEmpty())out.addAll(Sql.list(t.c,"SELECT threshold,spell_id,text FROM item_set_bonus WHERE set_id=? ORDER BY threshold",r->new Entry(r.getLong(2),"("+r.getInt(1)+") Set bonus",r.getString(3)==null?"Spell #"+r.getLong(2):r.getString(3),"Known",""),id));
    return out;
  }
  private static Snapshot stats(Tables t,long selected,boolean trinkets,boolean glossary)throws SQLException {
    List<Entry> out=new ArrayList<>();if(glossary){for(var e:new TreeMap<>(StatGlossary.NAMES).entrySet())out.add(new Entry(e.getKey(),e.getValue(),StatGlossary.mapping(e.getKey()),"Known","stat"));}
    else {String predicate=trinkets?" AND i.inventory_type=12 AND s.stat_id BETWEEN 125 AND 140":" AND s.stat_id=?";
      String sql="SELECT i.id,i.name,s.stat_id,s.value FROM item_stat s JOIN item i ON i.id=s.item_id WHERE (i.client_source IS NULL OR i.client_source <> 'era')"+predicate+" ORDER BY s.stat_id,i.name";
      out.addAll(Sql.list(t.c,sql,r->new Entry(r.getLong(1),r.getString(2)==null?"Item #"+r.getLong(1):r.getString(2),StatGlossary.name(r.getInt(3))+": "+r.getInt(4),"Known","item"),trinkets?new Object[]{}:new Object[]{selected}));}
    if(trinkets) {
      Set<String> seen=new HashSet<>();for(var entry:out)seen.add(entry.id()+":"+entry.details().split(":")[0]);
      for(var item:ItemContentRows.read(t.c,t.current,"ItemSparse"," AND CAST(json_extract(r.payload,'$.InventoryType') AS INTEGER)=12",List.of()))if(number(item,"InventoryType")==12){for(int n=0;n<10;n++){Long stat=item.number("StatModifier_bonusStat_"+n);if(stat!=null&&stat>=125&&stat<=140){long id=number(item,"ID");String label=StatGlossary.name(stat.intValue());if(seen.add(id+":"+label))out.add(new Entry(id,item.text("Display_lang","Item #"+id),label+": value not yet derived from the retained native stat budget","Known","item"));}}}
      Map<Long,String> waterSpells=new HashMap<>();for(var effect:ItemContentRows.read(t.c,t.current,"SpellEffect"," AND CAST(json_extract(r.payload,'$.EffectAura') AS INTEGER) IN (82,155,104,315)",List.of())){long aura=number(effect,"EffectAura");if(aura==82||aura==155)waterSpells.put(number(effect,"SpellID"),"Water breathing");else if(aura==104||aura==315)waterSpells.put(number(effect,"SpellID"),"Water walking");}
      Map<Long,String> effectLabels=new HashMap<>();for(var effect:ItemContentRows.read(t.c,t.current,"ItemEffect"," AND CAST(json_extract(r.payload,'$.SpellID') AS INTEGER) IN ("+idsSql(waterSpells.keySet())+")",List.of())){String label=waterSpells.get(effect.number("SpellID"));if(label!=null)effectLabels.put(number(effect,"ID"),label);}
      var waterLinks=ItemContentRows.read(t.c,t.current,"ItemXItemEffect"," AND CAST(json_extract(r.payload,'$.ItemEffectID') AS INTEGER) IN ("+idsSql(effectLabels.keySet())+")",List.of());
      var waterIds=waterLinks.stream().map(r->r.number("ItemID")).filter(Objects::nonNull).toList();
      var nativeItems=index(ItemContentRows.ids(t.c,t.current,"Item",waterIds),"ID");var sparse=index(ItemContentRows.ids(t.c,t.current,"ItemSparse",waterIds),"ID");
      for(var link:waterLinks){long id=number(link,"ItemID");var base=nativeItems.get(id);String label=effectLabels.get(link.number("ItemEffectID"));if(label!=null&&base!=null&&number(base,"InventoryType")==12&&seen.add(id+":"+label)){var name=sparse.get(id);out.add(new Entry(id,name==null?"Item #"+id:name.text("Display_lang","Item #"+id),label+": explicit client aura",name==null?"Hidden":"Known","item"));}}
      if(selected>=125&&selected<=140){String label=StatGlossary.name((int)selected),spellLabel=StatGlossary.name((int)selected+8);out.removeIf(e->!e.details().startsWith(label+":")&&!e.details().startsWith(spellLabel+":"));}
      else if(selected==-82)out.removeIf(e->!e.details().startsWith("Water breathing:"));
      else if(selected==-104)out.removeIf(e->!e.details().startsWith("Water walking:"));
    }
    return new Snapshot(trinkets?"trinkets":glossary?"stats":"stat",selected,trinkets?"Situational trinkets":glossary?"Stat glossary":StatGlossary.name((int)selected),provenance(t)+(trinkets?" · Creature bonuses verified from stat IDs 125–140. Water breathing/walking use explicit client auras. Other environment and target restrictions remain unclassified until verified.":" · Stat labels: WoWDBDefs ItemStatType. Hit and Crit merge Classic categories."),out,List.of());
  }
  private static Snapshot recipes(Tables t,long band)throws SQLException {
    boolean currentPresent=t.current!=null&&Sql.one(t.c,"SELECT 1 FROM client_table_row WHERE snapshot_id=? AND table_name='SkillLineAbility' LIMIT 1",r->true,t.current.snapshotId()).orElse(false);
    boolean eraPresent=t.era!=null&&Sql.one(t.c,"SELECT 1 FROM client_table_row WHERE snapshot_id=? AND table_name='SkillLineAbility' LIMIT 1",r->true,t.era.snapshotId()).orElse(false);
    if(!currentPresent||!eraPresent)return new Snapshot("recipes",band,"New recipes by skill","Not yet discovered: both Forever and Classic Era SkillLineAbility tables are required for a new-recipe comparison",List.of(),List.of());
    var abilities=ItemContentRows.read(t.c,t.current,"SkillLineAbility"," AND CAST(json_extract(r.payload,'$.Spell') AS INTEGER) NOT IN (SELECT CAST(json_extract(e.payload,'$.Spell') AS INTEGER) FROM client_table_row e WHERE e.snapshot_id=? AND e.table_name='SkillLineAbility' AND json_extract(e.payload,'$.Spell') IS NOT NULL) AND j.key IN ('ID','Spell','SkillLine','MinSkillLineRank')",List.of(t.era.snapshotId()));
    Set<Long> candidateSpells=new HashSet<>();for(var ability:abilities){Long spell=ability.number("Spell");if(spell!=null)candidateSpells.add(spell);}
    Map<Long,Row> crafts=new HashMap<>();for(var effect:ItemContentRows.read(t.c,t.current,"SpellEffect"," AND CAST(json_extract(r.payload,'$.Effect') AS INTEGER)=24 AND CAST(json_extract(r.payload,'$.EffectItemType') AS INTEGER)>0 AND CAST(json_extract(r.payload,'$.SpellID') AS INTEGER) IN ("+idsSql(candidateSpells)+")",List.of()))if(number(effect,"Effect")==24&&number(effect,"EffectItemType")>0)crafts.put(number(effect,"SpellID"),effect);
    var skillNames=index(t.rows("SkillLine"),"ID");var names=index(ItemContentRows.ids(t.c,t.current,"SpellName",crafts.keySet()),"ID");
    Map<Long,Row> reagents=index(ItemContentRows.read(t.c,t.current,"SpellReagents"," AND CAST(json_extract(r.payload,'$.SpellID') AS INTEGER) IN ("+idsSql(crafts.keySet())+")",List.of()),"SpellID");
    Set<Long> itemIds=new HashSet<>();for(var effect:crafts.values())itemIds.add(number(effect,"EffectItemType"));for(var reagent:reagents.values())for(int n=0;n<8;n++){Long reagentId=reagent.number("Reagent_"+n);if(reagentId!=null&&reagentId>0)itemIds.add(reagentId);}
    var items=index(ItemContentRows.ids(t.c,t.current,"ItemSparse",itemIds),"ID");List<Entry> out=new ArrayList<>();Set<Long> seen=new HashSet<>();
    for(var row:abilities){long spell=number(row,"Spell");Row craft=crafts.get(spell);if(craft==null||!seen.add(spell))continue;
      Long rank=row.number("MinSkillLineRank");if(band>0&&(rank==null||rank<band||(band<300&&rank>=(band==1?75:band+75))))continue;
      long itemId=number(craft,"EffectItemType");Row item=items.get(itemId),skill=skillNames.get(number(row,"SkillLine")),spellName=names.get(spell);String name=item==null?"Item #"+itemId:item.text("Display_lang","Item #"+itemId);
      String details=(skill==null?"Skill #"+row.text("SkillLine","?"):skill.text("DisplayName_lang","Skill #"+number(row,"SkillLine")))+" · Required skill "+(rank==null?"not yet discovered":rank)+" · "+(spellName==null?"Spell #"+spell:spellName.text("Name_lang","Spell #"+spell));
      Row reagent=reagents.get(spell);if(reagent!=null){List<String> list=new ArrayList<>();for(int n=0;n<8;n++){Long rid=reagent.number("Reagent_"+n);if(rid!=null&&rid>0){Row ri=items.get(rid);list.add(reagent.text("ReagentCount_"+n,"?")+" × "+(ri==null?"Item #"+rid:ri.text("Display_lang","Item #"+rid)));}}if(!list.isEmpty())details+=" · Reagents: "+String.join(", ",list);}
      out.add(new Entry(itemId,name,details,item==null?"Hidden":"New","item"));
    }
    out.sort(Comparator.comparing(Entry::details).thenComparing(Entry::name));
    return new Snapshot("recipes",band,"New recipes by skill",provenance(t)+" · Native create-item spells absent from Classic SkillLineAbility. Required skill is shown; unknown skill ranks remain explicit.",out,List.of());
  }
  private static Snapshot special(Tables t,String section)throws SQLException {
    List<Entry> out=new ArrayList<>();
    if(section.equals("comprehension")){
      Long skillId=null;for(var skill:t.rows("SkillLine"))if("Comprehension".equals(skill.text("DisplayName_lang")))skillId=skill.number("ID");
      if(skillId!=null)for(var item:ItemContentRows.read(t.c,t.current,"ItemSparse"," AND CAST(json_extract(r.payload,'$.RequiredSkill') AS INTEGER)=?",List.of(skillId)))if(skillId.equals(item.number("RequiredSkill")))out.add(new Entry(number(item,"ID"),item.text("Display_lang","Item #"+number(item,"ID")),"Comprehension required: "+item.text("RequiredSkillRank","not yet discovered"),"Known","item"));
      return new Snapshot(section,0,"Comprehension scrolls",provenance(t)+(skillId==null?" · Comprehension skill line not yet retained":" · Verified by native RequiredSkill → Comprehension skill line "+skillId+"; list only, no inferred spell-learning graph"),out,List.of());
    }
    // MOD_XP_PCT=200 and MOD_XP_QUEST_PCT=291 from WoWDBDefs SpellEffectAura.
    Set<Long> xpSpells=new HashSet<>();var effects=ItemContentRows.read(t.c,t.current,"SpellEffect"," AND (CAST(json_extract(r.payload,'$.EffectAura') AS INTEGER) IN (200,291) OR CAST(json_extract(r.payload,'$.EffectTriggerSpell') AS INTEGER)>0) AND j.key IN ('ID','SpellID','EffectAura','EffectTriggerSpell')",List.of());for(var effect:effects)if(number(effect,"EffectAura")==200||number(effect,"EffectAura")==291)xpSpells.add(number(effect,"SpellID"));
    var descriptions=ItemContentRows.read(t.c,t.current,"Spell"," AND json_extract(r.payload,'$.Description_lang') LIKE '%$@spelldesc%'",List.of());boolean changed=true;while(changed){changed=false;for(var effect:effects)if(xpSpells.contains(effect.number("EffectTriggerSpell")))changed|=xpSpells.add(number(effect,"SpellID"));
      for(var desc:descriptions){var matcher=java.util.regex.Pattern.compile("\\$@spelldesc(\\d+)").matcher(desc.text("Description_lang",""));while(matcher.find())if(xpSpells.contains(Long.valueOf(matcher.group(1))))changed|=xpSpells.add(number(desc,"ID"));}}
    Set<Long> effectIds=new HashSet<>();for(var effect:ItemContentRows.read(t.c,t.current,"ItemEffect"," AND CAST(json_extract(r.payload,'$.SpellID') AS INTEGER) IN ("+idsSql(xpSpells)+")",List.of()))if(xpSpells.contains(effect.number("SpellID")))effectIds.add(number(effect,"ID"));
    Set<Long> itemIds=new TreeSet<>();for(var link:ItemContentRows.read(t.c,t.current,"ItemXItemEffect"," AND CAST(json_extract(r.payload,'$.ItemEffectID') AS INTEGER) IN ("+idsSql(effectIds)+")",List.of()))if(effectIds.contains(link.number("ItemEffectID")))itemIds.add(number(link,"ItemID"));
    var itemRows=index(ItemContentRows.ids(t.c,t.current,"Item",itemIds),"ID");var names=index(ItemContentRows.ids(t.c,t.current,"ItemSparse",itemIds),"ID");for(long id:itemIds){Row base=itemRows.get(id),name=names.get(id);if(base==null||number(base,"ClassID")!=0||number(base,"SubclassID")!=5)continue;out.add(new Entry(id,name==null?"Item #"+id:name.text("Display_lang","Item #"+id),"Food effect explicitly links to an XP aura. Bonus may depend on character conditions; no universal XP percentage inferred.",name==null?"Hidden":"Known","item"));}
    return new Snapshot(section,0,"XP food",provenance(t)+" · Native food class + ItemXItemEffect → ItemEffect → SpellEffect XP aura, including explicit spell-description references. Conditional bonuses remain conditional.",out,List.of());
  }
  public Appearance appearance(long itemId) {synchronized(db){var cache=APPEARANCE_CACHE.computeIfAbsent(db,k->new HashMap<>());if(cache.containsKey(itemId))return cache.get(itemId);Appearance result=db.read(c->{var t=tables(c);var rows=t.rows("ItemModifiedAppearance");if(rows.isEmpty())return new Appearance(false,"","Appearance data not yet retained",List.of());Set<Long> appearances=new HashSet<>();for(var row:rows)if(number(row,"ItemID")==itemId){Long id=row.number("ItemAppearanceID");if(id!=null&&id>0)appearances.add(id);}Set<Long> ids=new TreeSet<>();for(var row:rows)if(appearances.contains(row.number("ItemAppearanceID"))){Long id=row.number("ItemID");if(id!=null&&id!=itemId)ids.add(id);}List<Entry> out=new ArrayList<>();for(long id:ids){String name=Sql.one(c,"SELECT name FROM item WHERE id=?",r->r.getString(1),id).orElse("Item #"+id);out.add(new Entry(id,name,"Shared client appearance","Known","item"));}return new Appearance(true,t.current.build(),appearances.isEmpty()?"No appearance mapping recorded for this item":"Shares appearance with "+out.size()+" other items",out);});cache.put(itemId,result);return result;}}
}
