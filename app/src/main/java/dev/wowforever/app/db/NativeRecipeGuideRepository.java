package dev.wowforever.app.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import static dev.wowforever.app.db.ClientTableRows.*;

/** Exact pinned Forever recipe evidence. No legacy Craft/Era values or inferred trainers. */
public final class NativeRecipeGuideRepository {
  public record Evidence(String table,String path,long line) {}
  public record Ingredient(long itemId,String name,Long count) {}
  public record Skill(long id,String name,Long rank,Long trivialLow,Long trivialHigh) {}
  public record Recipe(long spellId,String name,long outputItemId,String outputName,String quantity,String variant,
      List<Ingredient> ingredients,List<Skill> skills,List<Evidence> evidence,String reagentCoverage) {
    public Recipe { ingredients=List.copyOf(ingredients);skills=List.copyOf(skills);evidence=List.copyOf(evidence); }
  }
  public record Snapshot(String snapshotId,String build,List<Recipe> recipes) {
    public Snapshot { recipes=List.copyOf(recipes); }
    public boolean available(){return !recipes.isEmpty();}
  }
  private final Database db;
  private static final Map<Database,Map<String,Snapshot>> CACHE=Collections.synchronizedMap(new WeakHashMap<>());
  public NativeRecipeGuideRepository(Database db){this.db=db;}
  /** Only matching native rows are decoded. Call on a worker, never the EDT. */
  public Snapshot forItem(long itemId) {
    String pin=db.meta().get("forever_diff_forever_snapshot_id");
    if(pin==null||itemId<=0)return new Snapshot("","unknown",List.of());
    String key=pin+":"+itemId;
    synchronized(CACHE){var entries=CACHE.get(db);if(entries!=null&&entries.containsKey(key))return entries.get(key);}
    Snapshot result=db.read(c->{
      Dataset dataset=dataset(c,pin);if(dataset==null)return new Snapshot("","unknown",List.of());
      var effects=ItemContentRows.read(c,dataset,"SpellEffect"," AND CAST(json_extract(r.payload,'$.Effect') AS INTEGER)=24 AND CAST(json_extract(r.payload,'$.EffectItemType') AS INTEGER)=?",List.of(itemId));
      List<Recipe> recipes=new ArrayList<>();
      for(Row effect:effects) {
        Long spell=effect.number("SpellID");if(spell==null||spell<=0)continue;
        List<Evidence> evidence=new ArrayList<>();evidence.add(evidence("SpellEffect",effect));
        Row name=one(c,dataset,"SpellName",spell);if(name!=null)evidence.add(evidence("SpellName",name));
        Row output=one(c,dataset,"ItemSparse",itemId);if(output!=null)evidence.add(evidence("ItemSparse",output));
        var reagents=ItemContentRows.read(c,dataset,"SpellReagents"," AND CAST(json_extract(r.payload,'$.SpellID') AS INTEGER)=?",List.of(spell));
        List<Ingredient> ingredients=new ArrayList<>();
        for(Row reagent:reagents) {
          evidence.add(evidence("SpellReagents",reagent));
          for(int slot=0;slot<8;slot++) {
            Long id=reagent.number("Reagent_"+slot),count=reagent.number("ReagentCount_"+slot);
            if(id==null||id<=0)continue;
            Row item=one(c,dataset,"ItemSparse",id);if(item!=null)evidence.add(evidence("ItemSparse",item));
            // Retain zero quantities as zero; never convert them to one or silently consume them.
            ingredients.add(new Ingredient(id,text(item,"Display_lang","Item #"+id),count));
          }
        }
        List<Skill> skills=new ArrayList<>();
        for(Row ability:ItemContentRows.read(c,dataset,"SkillLineAbility"," AND CAST(json_extract(r.payload,'$.Spell') AS INTEGER)=?",List.of(spell))) {
          Long id=ability.number("SkillLine");if(id==null||id<=0)continue;
          Row skill=one(c,dataset,"SkillLine",id);evidence.add(evidence("SkillLineAbility",ability));if(skill!=null)evidence.add(evidence("SkillLine",skill));
          skills.add(new Skill(id,text(skill,"DisplayName_lang","Skill #"+id),ability.number("MinSkillLineRank"),ability.number("TrivialSkillLineRankLow"),ability.number("TrivialSkillLineRankHigh")));
        }
        String coverage=reagents.isEmpty()?"Reagent record not retained; requirements unknown."
            :ingredients.isEmpty()?"Native reagent row has no positive item IDs; no listed item reagents.":"Native reagent slots retained, including explicit zero quantities.";
        String variant="Effect #"+effect.id()+" · Difficulty "+effect.text("DifficultyID","Unknown")+" · Effect slot "+effect.text("EffectIndex","Unknown");
        recipes.add(new Recipe(spell,text(name,"Name_lang","Spell #"+spell),itemId,text(output,"Display_lang","Item #"+itemId),quantity(effect),variant,ingredients,skills,evidence,coverage));
      }
      return new Snapshot(pin,dataset.build(),recipes);
    });
    synchronized(CACHE){var entries=CACHE.computeIfAbsent(db,ignored->new LinkedHashMap<>(32,.75f,true));entries.put(key,result);while(entries.size()>32)entries.remove(entries.keySet().iterator().next());}
    return result;
  }
  private static Dataset dataset(Connection c,String pin)throws SQLException {
    if(!Sql.hasTable(c,"client_table_dataset")||!Sql.hasTable(c,"client_table_row"))return null;
    return Sql.one(c,"SELECT snapshot_id,product,build,locale FROM client_table_dataset WHERE snapshot_id=? AND product='wow_classic_beta'",r->new Dataset(r.getString(1),r.getString(2),r.getString(3),r.getString(4)),pin).orElse(null);
  }
  private static Row one(Connection c,Dataset data,String table,long id)throws SQLException {
    var rows=ItemContentRows.ids(c,data,table,List.of(id));return rows.isEmpty()?null:rows.getFirst();
  }
  private static Evidence evidence(String table,Row row){return new Evidence(table,row.sourcePath(),row.sourceLine());}
  private static String text(Row row,String field,String fallback){return row==null?fallback:row.text(field,fallback);}
  private static String quantity(Row effect) {
    String base=effect.text("EffectBasePointsF"),variance=effect.text("Variance");
    if(base==null||variance==null)return "Unknown";
    try {
      double b=Double.parseDouble(base),v=Double.parseDouble(variance);
      if(!Double.isFinite(b)||!Double.isFinite(v)||v<0)return "Unknown";
      long min=Math.max(1,Math.round(b*(1-v/2))),max=Math.max(min,Math.round(b*(1+v/2)));
      return min==max?Long.toString(min):min+"–"+max;
    }catch(NumberFormatException ignored){return "Unknown";}
  }
}
