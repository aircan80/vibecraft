package dev.wowforever.app.db;

import dev.wowforever.app.model.CampingData;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Reads archived client rows on a worker; the page never queries the database. */
public final class CampingRepository {
  // Values contain no connection references, so weak keys permit closed databases to be collected.
  private static final java.util.Map<Database,CampingData> CACHE =
      java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
  private final Database db;
  public CampingRepository(Database db) { this.db = db; }

  public CampingData load() {
    synchronized (db) {
      CampingData cached = CACHE.get(db);
      if (cached != null) return cached;
      CampingData loaded = read();
      CACHE.put(db, loaded);
      return loaded;
    }
  }

  private CampingData read() {
    return db.read(c -> {
      if (!hasTable(c, "client_table_row") || !hasTable(c, "client_table_dataset")) {
        return new CampingData("", "", List.of(), List.of(),
            List.of("SpellEffect", "SpellName", "Spell", "ItemSparse"));
      }
      String snapshot = "", build = "";
      try (var s = c.createStatement(); var r = s.executeQuery("""
          SELECT d.snapshot_id,d.build FROM client_table_dataset d
          WHERE EXISTS(SELECT 1 FROM client_table_row r WHERE r.snapshot_id=d.snapshot_id
            AND r.table_name='SpellEffect'
            AND CAST(json_extract(r.payload,'$.SpellID') AS INTEGER) BETWEEN 1307172 AND 1307397
            AND CAST(json_extract(r.payload,'$.Effect') AS INTEGER)=50)
          ORDER BY d.build DESC,d.snapshot_id LIMIT 1
          """)) {
        if (r.next()) { snapshot=r.getString(1); build=r.getString(2); }
      }
      List<String> missing = new ArrayList<>();
      for (String name : List.of("SpellEffect", "SpellName", "Spell", "ItemSparse")) {
        try (var s = c.prepareStatement("SELECT 1 FROM client_table_row WHERE snapshot_id=? AND table_name=? LIMIT 1")) {
          s.setString(1,snapshot); s.setString(2,name);
          try(var r=s.executeQuery()) { if(!r.next()) missing.add(name); }
        }
      }
      List<CampingData.Camp> camps = new ArrayList<>();
      try (var s=c.prepareStatement("""
          SELECT CAST(json_extract(e.payload,'$.SpellID') AS INTEGER) spell_id,
            coalesce(json_extract(n.payload,'$.Name_lang'),'Spell '||json_extract(e.payload,'$.SpellID')) name,
            CAST(json_extract(e.payload,'$.EffectMiscValue_0') AS INTEGER) object_id,
            coalesce(json_extract(t.payload,'$.Description_lang'),'') description,e.source_path,e.source_line,
            coalesce(json_extract(t.payload,'$.NameSubtext_lang'),'Unknown tier') tier,
            coalesce(json_extract(k.payload,'$.DisplayName_lang'),'Unknown profession') profession,
            CAST(nullif(json_extract(i.payload,'$.RequiredSkillRank'),'') AS INTEGER) required_rank
          FROM client_table_row e
          LEFT JOIN client_table_row n ON n.snapshot_id=e.snapshot_id AND n.table_name='SpellName'
            AND n.row_id=json_extract(e.payload,'$.SpellID')
          LEFT JOIN client_table_row t ON t.snapshot_id=e.snapshot_id AND t.table_name='Spell'
            AND t.row_id=json_extract(e.payload,'$.SpellID')
          LEFT JOIN client_table_row f ON f.snapshot_id=e.snapshot_id AND f.table_name='ItemEffect'
            AND json_extract(f.payload,'$.SpellID')=json_extract(e.payload,'$.SpellID')
          LEFT JOIN client_table_row x ON x.snapshot_id=e.snapshot_id AND x.table_name='ItemXItemEffect'
            AND json_extract(x.payload,'$.ItemEffectID')=f.row_id
          LEFT JOIN client_table_row i ON i.snapshot_id=e.snapshot_id AND i.table_name='ItemSparse'
            AND i.row_id=json_extract(x.payload,'$.ItemID')
          LEFT JOIN client_table_row k ON k.snapshot_id=e.snapshot_id AND k.table_name='SkillLine'
            AND k.row_id=json_extract(i.payload,'$.RequiredSkill')
          WHERE e.snapshot_id=? AND e.table_name='SpellEffect'
            AND CAST(json_extract(e.payload,'$.SpellID') AS INTEGER) BETWEEN 1307172 AND 1307397
            AND CAST(json_extract(e.payload,'$.Effect') AS INTEGER)=50
          ORDER BY spell_id,e.source_line
          """)) {
        s.setString(1,snapshot);
        try(var r=s.executeQuery()) { while(r.next()) camps.add(new CampingData.Camp(
            r.getLong("spell_id"),r.getString("name"),r.getLong("object_id"),
            r.getString("description"),r.getString("source_path"),r.getInt("source_line"),
            r.getString("tier"),r.getString("profession"),(Integer)r.getObject("required_rank"))); }
      }
      List<CampingData.Blueprint> blueprints=new ArrayList<>();
      try(var s=c.prepareStatement("""
          SELECT row_id,json_extract(payload,'$.Display_lang') name,
            CAST(nullif(json_extract(payload,'$.RequiredSkill'),'') AS INTEGER) skill,
            CAST(nullif(json_extract(payload,'$.RequiredSkillRank'),'') AS INTEGER) rank,source_path,source_line
          FROM client_table_row WHERE snapshot_id=? AND table_name='ItemSparse'
            AND json_extract(payload,'$.Display_lang') LIKE 'Blueprint:%'
          ORDER BY name,row_id
          """)) {
        s.setString(1,snapshot);
        try(var r=s.executeQuery()) { while(r.next()) blueprints.add(new CampingData.Blueprint(
            r.getLong(1),r.getString(2),(Integer)r.getObject(3),(Integer)r.getObject(4),r.getString(5),r.getInt(6))); }
      }
      var unique=new java.util.LinkedHashMap<Long,CampingData.Camp>();
      for(var camp:camps) unique.putIfAbsent(camp.spellId(),camp);
      return new CampingData(build,snapshot,List.copyOf(unique.values()),blueprints,missing);
    });
  }
  private static boolean hasTable(Connection c,String table) throws SQLException {
    try(var s=c.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
      s.setString(1,table);try(var r=s.executeQuery()) { return r.next(); }
    }
  }
}
