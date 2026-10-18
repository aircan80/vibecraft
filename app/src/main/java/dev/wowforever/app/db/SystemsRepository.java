package dev.wowforever.app.db;

import dev.wowforever.app.model.systems.ClientSystems.*;
import java.util.*;

/** Loads an immutable snapshot on a worker; Swing pages perform no database work. */
public final class SystemsRepository {
  private final Database db;
  private static final Map<Database,Catalog> CACHE=Collections.synchronizedMap(new WeakHashMap<>());
  public SystemsRepository(Database db) { this.db=db; }
  public synchronized Catalog load() {
    Catalog existing=CACHE.get(db);if(existing!=null)return existing;
    return db.read(c -> {
      // Different catalog pages create different repository instances. Recheck after acquiring the
      // database lock so simultaneous first visits share one immutable snapshot rather than queueing
      // duplicate native-table scans.
      Catalog ready=CACHE.get(db);if(ready!=null)return ready;
      if (!Sql.hasTable(c,"client_table_dataset") || !Sql.hasTable(c,"client_table_row"))
        return new Catalog("","",Map.of());
      var datasets=Sql.list(c,"SELECT snapshot_id,build FROM client_table_dataset WHERE build LIKE '1.60.%' ORDER BY build DESC",
          r->new String[]{r.getString(1),r.getString(2)});
      if(datasets.isEmpty()) return new Catalog("","",Map.of());
      String[] dataset=datasets.getFirst();
      var era=Sql.one(c,"SELECT snapshot_id,build FROM client_table_dataset WHERE build LIKE '1.15.%' ORDER BY build DESC LIMIT 1",r->new String[]{r.getString(1),r.getString(2)});
      Catalog loaded=new Catalog(dataset[1],dataset[0],readTables(c,dataset[0]),era.map(e->e[1]).orElse(""),era.isPresent()?readTables(c,era.get()[0]):Map.of());
      CACHE.put(db,loaded);
      return loaded;
    });
  }
  private static Map<String,List<Row>> readTables(java.sql.Connection c,String snapshot) throws java.sql.SQLException {
    Map<String,List<Row>> tables=new LinkedHashMap<>();
    String sql="SELECT r.table_name,r.row_id,r.source_path,r.source_line,r.payload "
        +"FROM client_table_row r WHERE r.snapshot_id=? AND "
        +"(r.table_name LIKE 'Trait%' OR r.table_name IN ('ChrClasses','SkillLineXTraitTree','SkillLine','SkillLineAbility','SpellLevels','SpellName','Achievement','Criteria','CriteriaTree','RenownRewards','Talent','TalentTab') OR (r.table_name='Spell' AND CAST(r.row_id AS INTEGER) IN (SELECT CAST(json_extract(payload,'$.SpellID') AS INTEGER) FROM client_table_row WHERE snapshot_id=? AND table_name='TraitDefinition'))) "
        +"ORDER BY r.table_name,r.row_id";
    try(var ps=c.prepareStatement(sql)) {
      ps.setString(1,snapshot);ps.setString(2,snapshot);
      try(var rs=ps.executeQuery()) {
        while(rs.next()) {
          String table=rs.getString(1);
          Map<String,String> values=new LinkedHashMap<>(ClientTableRows.decodePayload(rs.getString(5)));
          // The previous json_each reader omitted SQL/JSON null cells. Preserve that contract;
          // real numeric zero and empty strings remain distinct and retained.
          values.values().removeIf(Objects::isNull);
          tables.computeIfAbsent(table,k->new ArrayList<>()).add(
              new Row(table,rs.getLong(2),values,rs.getString(3),rs.getInt(4)));
        }
      }
    }
    return tables;
  }
}
