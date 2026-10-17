package dev.wowforever.app.db;

import dev.wowforever.app.model.FieldKnowledgeView;
import dev.wowforever.app.model.FieldKnowledgeView.State;
import java.util.*;

/** Reads asserted knowledge; legacy fallbacks never infer hidden data from empty tooltips. */
public final class FieldKnowledgeRepository {
  private final Database db;
  public FieldKnowledgeRepository(Database db){this.db=db;}
  public List<FieldKnowledgeView> item(long id) {
    return db.read(c->{
      var values=new LinkedHashMap<String,FieldKnowledgeView>();
      if(Sql.hasTable(c,"field_knowledge")) {
        for(var field:Sql.list(c,"SELECT k.field,k.state,k.source,k.data_version,a.value,a.snapshot,a.path,a.line FROM field_knowledge k JOIN field_knowledge_assertion a ON a.assertion_id=k.assertion_id WHERE k.entity='item' AND k.id=? ORDER BY k.field",
            r -> new FieldKnowledgeView(r.getString("field"),State.valueOf(r.getString("state")),r.getString("value"),r.getString("source"),r.getString("data_version"),
                r.getString("snapshot")+" · "+r.getString("path")+(r.getObject("line")==null?"":":"+r.getInt("line"))),Long.toString(id)))values.put(field.field(),field);
      }
      var raw=Sql.one(c,"SELECT name,item_level,required_level,has_client_data,client_source FROM item WHERE id=?",r->new Object[]{r.getString(1),r.getObject(2),r.getObject(3),r.getBoolean(4),r.getString(5)},id);
      if(raw.isEmpty())return List.of();
      Object[] row=raw.get();String source=Objects.toString(row[4],"questie");
      if(source.equals("era")) {
        for(String field:List.of("name","item_level","required_level","stats"))values.remove(field);
      }
      boolean current=Boolean.TRUE.equals(row[3]) && (source.equals("forever") || source.equals("hotfix"));
      String build=Sql.one(c,"SELECT value FROM meta WHERE key=?",r->r.getString(1),source.equals("era")?"client_fallback_build":"client_build").orElse("");
      String[] names={"name","item_level","required_level"};
      for(int i=0;i<names.length;i++) {
        Object value=row[i];String displayed=value==null?null:value.toString();
        values.putIfAbsent(names[i],new FieldKnowledgeView(names[i],value==null?State.NOT_YET_DISCOVERED:current?State.KNOWN:State.REPORTED,
            displayed,source,build,source.equals("era")?"Forever value not yet discovered; Classic Era comparison is separate":"Retained item field"));
      }
      values.putIfAbsent("stats",new FieldKnowledgeView("stats",current?State.KNOWN:State.NOT_YET_DISCOVERED,null,source,build,
          current?"Forever client item data retained":"No explicit Forever stats assertion retained"));
      if(source.equals("era"))values.putIfAbsent("classic_stats",new FieldKnowledgeView("classic_stats",State.KNOWN,null,"era",build,"Classic Era value; not Forever stats"));
      return List.copyOf(values.values());
    });
  }
}
