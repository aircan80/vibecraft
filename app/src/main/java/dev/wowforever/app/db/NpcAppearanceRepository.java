package dev.wowforever.app.db;

import com.google.gson.Gson;
import dev.wowforever.app.assets.ReviewedDataFiles;
import dev.wowforever.app.model.NpcAppearance;
import dev.wowforever.app.model.NpcAppearance.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/** Indexed native lookups, with a small attested same-build catalog when those tables are not projected. */
public final class NpcAppearanceRepository {
  private static final String PRODUCT="wow_classic_beta";
  private final Database db;
  public NpcAppearanceRepository(Database db){this.db=db;}

  /** Call on the page worker. Every positive display slot is retained, including unsupported variants. */
  public NpcAppearance resolve(long npcId) {
    return db.read(c->{
      String build=db.meta().getOrDefault("client_build","");
      if(build.isBlank())return unresolved(npcId,build,"The active Forever client build is not established.");
      if(Sql.hasTable(c,"client_table_dataset")&&Sql.hasTable(c,"client_table_manifest")&&Sql.hasTable(c,"client_table_row")) {
        var snapshots=Sql.list(c,"""
            SELECT d.snapshot_id FROM client_table_dataset d JOIN client_table_manifest m USING(snapshot_id)
            WHERE d.product=? AND d.build=? AND m.table_name='Creature' ORDER BY d.snapshot_id
            """,r->r.getString(1),PRODUCT,build);
        for(String snapshot:snapshots) {
          Evidence creature=nativeRow(c,snapshot,"Creature",Long.toString(npcId));
          if(creature!=null)return resolve(PRODUCT,build,npcId,snapshot,creature,(table,id)->nativeRow(c,snapshot,table,id));
        }
      }
      Catalog catalog=CatalogHolder.VALUE;
      if(catalog!=null && PRODUCT.equals(catalog.product())&&build.equals(catalog.build())) {
        NpcAppearance appearance=catalog.appearances().get(Long.toString(npcId));
        if(appearance!=null)return appearance;
      }
      return unresolved(npcId,build,"No exact-build Creature → display mapping is recorded for this NPC. Classic Era appearances are not substituted.");
    });
  }

  private static NpcAppearance unresolved(long npcId,String build,String message) {
    return new NpcAppearance(PRODUCT,build,npcId,"",State.UNRESOLVED,List.of(),List.of(),message,"");
  }
  private static Evidence nativeRow(Connection c,String snapshot,String table,String id)throws SQLException {
    return Sql.one(c,"""
        SELECT r.source_path,r.source_line,r.payload,m.sha256 FROM client_table_row r
        LEFT JOIN client_table_manifest m ON m.snapshot_id=r.snapshot_id AND m.table_name=r.table_name
        WHERE r.snapshot_id=? AND r.table_name=? AND r.row_id=?
        """,r->new Evidence(table,id,r.getString(1),r.getLong(2),r.getString(4),ClientTableRows.decodePayload(r.getString(3))),snapshot,table,id).orElse(null);
  }
  @FunctionalInterface private interface Lookup {Evidence get(String table,String id)throws SQLException;}
  private static NpcAppearance resolve(String product,String build,long npcId,String snapshot,Evidence creature,Lookup lookup)throws SQLException {
    List<Variant> variants=new ArrayList<>();Map<String,String> npc=creature.payload();
    List<Long> equipment=new ArrayList<>();for(int i=0;i<3;i++)equipment.add(integer(npc,"AlwaysItem_"+i));
    for(int slot=0;slot<4;slot++) {
      long displayId=integer(npc,"DisplayID_"+slot);if(displayId<=0)continue;
      Evidence display=lookup.get("CreatureDisplayInfo",Long.toString(displayId));
      Map<String,String> d=display==null?Map.of():display.payload();long modelId=integer(d,"ModelID");
      Evidence model=modelId>0?lookup.get("CreatureModelData",Long.toString(modelId)):null;
      Map<String,String> m=model==null?Map.of():model.payload();long modelFile=integer(m,"FileDataID"),extraId=integer(d,"ExtendedDisplayInfoID");
      List<Long> textures=new ArrayList<>();for(int i=0;i<4;i++)textures.add(integer(d,"TextureVariationFileDataID_"+i));
      Double scale=decimal(d,"CreatureModelScale"),modelScale=decimal(m,"ModelScale");
      List<Evidence> evidence=new ArrayList<>();evidence.add(creature);if(display!=null)evidence.add(display);if(model!=null)evidence.add(model);
      if(extraId>0){Evidence extra=lookup.get("CreatureDisplayInfoExtra",Long.toString(extraId));if(extra!=null)evidence.add(extra);}
      State state=State.MAPPED;List<String> reasons=new ArrayList<>();
      if(display==null){reasons.add("Display record is absent from this exact build.");state=State.UNRESOLVED;}
      else if(model==null||modelFile<=0){reasons.add("Model file mapping is absent from this exact build.");state=State.UNRESOLVED;}
      else if(scale==null||modelScale==null||scale<=0||modelScale<=0){reasons.add("A positive display/model scale is not established.");state=State.UNRESOLVED;}
      if(extraId>0)reasons.add("Humanoid customization and baked-material mapping are not implemented (extra "+extraId+").");
      if(equipment.stream().anyMatch(id->id>0))reasons.add("Creature equipment mapping is not implemented.");
      if(integer(m,"CreatureGeosetDataID")>0)reasons.add("Model geoset overrides are not implemented.");
      if(integer(d,"ConditionalCreatureModelID")>0)reasons.add("Conditional model selection is not implemented.");
      if(display!=null&&integer(d,"CreatureModelAlpha")!=255)reasons.add("Non-opaque display alpha is not supported by the generic preview request.");
      if(!reasons.isEmpty()&&state==State.MAPPED)state=State.UNSUPPORTED;
      String identity=String.join("\n","npc-appearance-v1",product,build,Long.toString(displayId),Long.toString(modelId),Long.toString(modelFile),
          join(textures),d.getOrDefault("CreatureModelScale",""),m.getOrDefault("ModelScale",""),Long.toString(extraId),join(equipment));
      variants.add(new Variant(displayId,slot,modelId,modelFile,textures,scale,modelScale,sha256(identity),state,String.join(" ",reasons),evidence));
    }
    Set<State> states=new HashSet<>();variants.forEach(v->states.add(v.state()));
    State state=variants.isEmpty()?State.UNRESOLVED:states.size()==1?states.iterator().next():State.PARTIAL;
    return new NpcAppearance(product,build,npcId,npc.getOrDefault("Name_lang",""),state,variants,List.of(creature),
        variants.isEmpty()?"No positive display IDs are recorded for this creature.":"Native display variants; mapping does not attest that a preview has rendered.",snapshot);
  }
  private static String join(List<Long> values){return String.join(",",values.stream().map(Object::toString).toList());}
  private static long integer(Map<String,String> row,String key){try{return Long.parseLong(row.getOrDefault(key,""));}catch(NumberFormatException e){return 0;}}
  private static Double decimal(Map<String,String> row,String key){String value=row.get(key);if(value==null)return null;try{double v=Double.parseDouble(value);return Double.isFinite(v)?v:null;}catch(NumberFormatException e){return null;}}
  private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}}

  private record Catalog(String schema,String product,String build,String catalogId,Map<String,NpcAppearance> appearances){}
  private static final class CatalogHolder {
    static final Catalog VALUE=load();
    private static Catalog load(){
      try(var stream=ReviewedDataFiles.open("npc-appearances/forever-70450.json",
          "4f036894ae143394e2ee851e23711538d504b11510cb1a538ebda5c15f227896",2*1024*1024);
          var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){
        Catalog c=new Gson().fromJson(reader,Catalog.class);
        if(!"npc-appearance-v1".equals(c.schema()))throw new IllegalStateException("Unsupported NPC appearance catalog schema");
        return c;
      }catch(java.io.IOException|RuntimeException failure){return null;}
    }
  }
}
