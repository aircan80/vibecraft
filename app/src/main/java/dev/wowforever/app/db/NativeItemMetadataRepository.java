package dev.wowforever.app.db;

import dev.wowforever.app.model.NativeItemMetadata;
import dev.wowforever.app.model.NativeItemMetadata.*;
import java.sql.*;
import java.util.*;

/** Exact native IDs only; no names, stats, set membership or spell behavior are inferred. */
public final class NativeItemMetadataRepository {
  private final Database db;
  public NativeItemMetadataRepository(Database db){this.db=db;}
  public NativeItemMetadata item(long itemId) {
    return db.read(c->{
      var canonical=Sql.one(c,"SELECT inventory_type,icon_id,item_set_id,client_source FROM item WHERE id=?",r->
          new Object[]{Sql.integer(r,"inventory_type"),Sql.longValue(r,"icon_id"),Sql.longValue(r,"item_set_id"),r.getString("client_source")},itemId);
      if(canonical.isEmpty())return NativeItemMetadata.empty();
      Object[] item=canonical.get();Integer slot=(Integer)item[0];Long icon=(Long)item[1],set=(Long)item[2];
      String build=db.meta().getOrDefault("era".equals(item[3])?"client_fallback_build":"client_build","");
      String evidence="Retained canonical item metadata";boolean nativeRow=false;
      var sets=new ArrayList<SetMembership>();var effects=new ArrayList<Effect>();
      if(Sql.hasTable(c,"client_table_row")) {
        String current=db.meta().getOrDefault("client_build","");
        var dataset=ClientTableRows.datasets(c).stream().filter(d->d.build().equals(current)).findFirst();
        if(dataset.isEmpty())dataset=ClientTableRows.datasets(c).stream().filter(d->d.build().startsWith("1.60.")).findFirst();
        if(dataset.isPresent()) {
          var data=dataset.get();
          var own=Sql.one(c,"SELECT payload,source_path,source_line FROM client_table_row WHERE snapshot_id=? AND table_name='Item' AND row_id=?",r->
              new String[]{r.getString(1),r.getString(2),r.getString(3)},data.snapshotId(),Long.toString(itemId));
          if(own.isPresent()) {
            nativeRow=true;build=data.build();evidence=own.get()[1]+":"+own.get()[2]+" · snapshot "+data.snapshotId();
            slot=Sql.one(c,"SELECT CAST(json_extract(?,'$.InventoryType') AS TEXT)",r->number(r.getString(1)),own.get()[0]).map(Long::intValue).orElse(null);
            icon=Sql.one(c,"SELECT CAST(json_extract(?,'$.IconFileDataID') AS TEXT)",r->number(r.getString(1)),own.get()[0]).orElse(null);
          }
          sets.addAll(Sql.list(c,"SELECT DISTINCT r.row_id,json_extract(r.payload,'$.Name_lang') AS name,r.source_path,r.source_line FROM client_table_row r,json_each(r.payload) j WHERE r.snapshot_id=? AND r.table_name='ItemSet' AND j.key GLOB 'ItemID_*' AND CAST(j.value AS TEXT)=? ORDER BY CAST(r.row_id AS INTEGER)",r->
              new SetMembership(Long.parseLong(r.getString("row_id")),Objects.toString(r.getString("name"),"Set #"+r.getString("row_id")),r.getString("source_path")+":"+r.getLong("source_line")+" · snapshot "+data.snapshotId()),data.snapshotId(),Long.toString(itemId)));
          record Link(String effect,String path,long line) {}
          var links=Sql.list(c,"SELECT CAST(json_extract(payload,'$.ItemEffectID') AS TEXT),source_path,source_line FROM client_table_row WHERE snapshot_id=? AND table_name='ItemXItemEffect' AND CAST(json_extract(payload,'$.ItemID') AS TEXT)=? ORDER BY source_line",r->new Link(r.getString(1),r.getString(2),r.getLong(3)),data.snapshotId(),Long.toString(itemId));
          for(var link:links) {
            Long effectId=number(link.effect);if(effectId==null || effectId<1)continue;
            var effect=Sql.one(c,"SELECT CAST(json_extract(payload,'$.SpellID') AS TEXT),CAST(json_extract(payload,'$.TriggerType') AS TEXT),source_path,source_line FROM client_table_row WHERE snapshot_id=? AND table_name='ItemEffect' AND row_id=?",r->new String[]{r.getString(1),r.getString(2),r.getString(3),r.getString(4)},data.snapshotId(),link.effect);
            if(effect.isEmpty())continue;
            Long spell=number(effect.get()[0]),trigger=number(effect.get()[1]);if(spell==null || spell<1)continue;
            String name=Sql.one(c,"SELECT json_extract(payload,'$.Name_lang') FROM client_table_row WHERE snapshot_id=? AND table_name='SpellName' AND row_id=?",r->r.getString(1),data.snapshotId(),Long.toString(spell)).orElse("Spell #"+spell);
            effects.add(new Effect(effectId,spell,name,trigger==null?null:trigger.intValue(),link.path+":"+link.line+" → "+effect.get()[2]+":"+effect.get()[3]+" · snapshot "+data.snapshotId()));
          }
        }
      }
      if(sets.isEmpty() && !"era".equals(item[3]) && set!=null && set>0) {
        String name=Sql.one(c,"SELECT name FROM item_set WHERE id=?",r->r.getString(1),set).orElse("Set #"+set);
        sets.add(new SetMembership(set,name,"Retained canonical item_set_id; "+evidence));
      }
      return new NativeItemMetadata(build,slot,icon,sets,effects,evidence,nativeRow);
    });
  }
  private static Long number(String text){if(text==null || text.isBlank())return null;try{return Long.valueOf(text);}catch(NumberFormatException bad){return null;}}
}
