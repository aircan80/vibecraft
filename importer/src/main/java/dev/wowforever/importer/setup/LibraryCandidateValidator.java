package dev.wowforever.importer.setup;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;

/** Read-only structural/source validation, not a claim that every game record or native asset is present. */
public final class LibraryCandidateValidator {
  public record Validated(String buildId,SetupPlan.NativeIdentity nativeIdentity,String ingestionId,
      Map<String,Long> entityCounts,List<String> warnings) {
    public Validated {entityCounts=Map.copyOf(entityCounts);warnings=List.copyOf(warnings);}
  }
  public Validated validate(Path file,SetupPlan plan,String ingestion,BooleanSupplier cancelled)throws IOException,SQLException {
    check(cancelled);
    if(!Files.isRegularFile(file))throw new IOException("Candidate library is not a regular database file");
    SQLiteConfig config=new SQLiteConfig();config.setReadOnly(true);config.setOpenMode(SQLiteOpenMode.READONLY);
    config.resetOpenMode(SQLiteOpenMode.READWRITE);config.resetOpenMode(SQLiteOpenMode.CREATE);
    try(Connection c=config.createConnection("jdbc:sqlite:"+file.toAbsolutePath())) {
      if(!"ok".equals(scalar(c,"PRAGMA quick_check",cancelled)))throw new IOException("Candidate library integrity check failed");
      try(var s=c.createStatement();var r=s.executeQuery("PRAGMA foreign_key_check")) {
        if(r.next())throw new IOException("Candidate library has broken declared foreign keys");
      }
      Map<String,String> meta=new HashMap<>();
      try(var s=c.createStatement();var r=s.executeQuery("SELECT key,value FROM meta")){while(r.next()){check(cancelled);meta.put(r.getString(1),r.getString(2));}}
      SetupPlan.NativeIdentity identity=plan.nativeIdentity().orElseThrow(()->new IOException("Native identity is required for a runnable Forever library"));
      if(!"4".equals(meta.get("schema_version")))throw new IOException("Candidate library schema is unsupported");
      String buildId=meta.get("build_id");
      if(buildId==null||!buildId.matches("[a-f0-9]{64}"))throw new IOException("Candidate projection identity is missing or invalid");
      if(!identity.product().equals(meta.get("client_product"))||!identity.build().equals(meta.get("client_build")))
        throw new IOException("Candidate library does not match the selected Forever build");
      if(meta.containsKey("client_locale")&&!identity.locale().equals(meta.get("client_locale")))
        throw new IOException("Candidate library locale conflicts with the selected native source");
      if(!ingestion.equals(meta.get("source_ingestion_id")))throw new IOException("Candidate library does not match this job's source capture");
      Set<String> expected=new HashSet<>(Set.of("configuration","questiedb","client"));
      if(plan.sources().contains(SetupPlan.Source.ATLASLOOT))expected.add("atlasloot");
      if(plan.sources().contains(SetupPlan.Source.ALL_THE_THINGS))expected.add("allthethings");
      Set<String> actual=new HashSet<>();List<String> warnings=new ArrayList<>();boolean clientSeen=false;
      try(var p=c.prepareStatement("SELECT i.role,s.source,s.product,s.build,s.locale,s.version FROM source_ingestion_input i JOIN source_archive_snapshot s ON s.id=i.snapshot_id WHERE i.ingestion_id=?")) {
        p.setString(1,ingestion);try(var r=p.executeQuery()){while(r.next()){
          check(cancelled);String role=r.getString(1);actual.add(role);
          if(role.equals("client")){
            clientSeen=true;
            if(!"blizzard-client".equals(r.getString(2))||!identity.product().equals(r.getString(3))
                ||!identity.build().equals(r.getString(4))||!identity.locale().equals(r.getString(5)))
              throw new IOException("Archived native source does not match the selected product/build/locale");
          }
          String version=r.getString(6);
          if(version!=null&&version.contains("partial selection"))warnings.add(role+": some declared source inputs were unavailable");
        }}
      }
      if(!clientSeen||!actual.equals(expected))throw new IOException("Candidate library includes missing or unselected source roles");
      try(var p=c.prepareStatement("SELECT validation_summary FROM source_projection WHERE ingestion_id=? AND build_id=? AND schema_version=4 ORDER BY id DESC LIMIT 1")) {
        p.setString(1,ingestion);p.setString(2,buildId);
        try(var r=p.executeQuery()){
          if(!r.next())throw new IOException("Candidate has no matching completed projection record");
          String summary=r.getString(1);
          if(summary==null||summary.contains("Legacy baseline retained"))throw new IOException("Candidate was not rebuilt and validated as a fresh library");
          var coverage=java.util.regex.Pattern.compile("(?:^|;\\s*)client coverage=(\\d+)%").matcher(summary);
          if(!summary.startsWith("UI skips=0; unsupported fields=0;")||!summary.contains("client data skipped=0")
              ||!coverage.find()||Integer.parseInt(coverage.group(1))<85||Integer.parseInt(coverage.group(1))>100)
            throw new IOException("Candidate projection did not meet the selected strict core coverage policy");
          warnings.add("Projection: "+summary);
        }
      }
      String atlas=plan.sources().contains(SetupPlan.Source.ATLASLOOT)?"selected":"not-selected";
      if(!atlas.equals(meta.get("atlasloot_selection")))throw new IOException("Candidate AtlasLoot policy differs from the selected plan");
      if(!plan.sources().contains(SetupPlan.Source.ATLASLOOT)
          &&(count(c,"SELECT count(*) FROM instance WHERE source='atlasloot'",cancelled)>0
          ||count(c,"SELECT count(*) FROM drop_rate_source WHERE source LIKE 'atlasloot%'",cancelled)>0))
        throw new IOException("Unselected AtlasLoot facts appeared in the candidate");
      if(!plan.sources().contains(SetupPlan.Source.ALL_THE_THINGS)&&meta.keySet().stream().anyMatch(key->key.startsWith("att_")))
        throw new IOException("Unselected AllTheThings metadata appeared in the candidate");
      if(count(c,"SELECT count(*) FROM item WHERE client_source IN ('era','hotfix')",cancelled)>0)
        throw new IOException("Unselected fallback or hotfix item data appeared in the candidate");
      Map<String,Long> counts=new LinkedHashMap<>();
      for(String table:List.of("item","npc","quest","zone","game_object","instance")) {
        // Fixed identifiers only. This also checks the query shape expected by browser navigation.
        try(var s=c.createStatement();var r=s.executeQuery("SELECT id,name FROM "+table+" LIMIT 0")){check(cancelled);}
        counts.put(table,count(c,"SELECT count(*) FROM "+table,cancelled));
      }
      for(String table:List.of("item","npc","quest","zone"))
        if(counts.get(table)==0)throw new IOException("Required "+table+" coverage is empty; candidate is not a usable library");
      for(String sql:List.of("SELECT item_id,npc_id FROM item_npc_drop LIMIT 0",
          "SELECT quest_id FROM quest_giver LIMIT 0","SELECT kind,id,name FROM search_index LIMIT 0"))
        try(var s=c.createStatement();var r=s.executeQuery(sql)){check(cancelled);}
      check(cancelled);
      warnings.add("This job performs core/client projection only; additive foundation, map, hotfix and collector replay is not performed.");
      warnings.add("Validation covers selected inputs and structural usability; missing artwork and source gaps remain explicit coverage limitations.");
      return new Validated(buildId,identity,ingestion,counts,warnings);
    }
  }
  private static long count(Connection c,String sql,BooleanSupplier cancelled)throws SQLException {return Long.parseLong(scalar(c,sql,cancelled));}
  private static String scalar(Connection c,String sql,BooleanSupplier cancelled)throws SQLException {
    check(cancelled);try(var s=c.createStatement()){s.setQueryTimeout(60);try(var r=s.executeQuery(sql)){
      if(!r.next())throw new SQLException("Validation query returned no result");check(cancelled);return r.getString(1);
    }}
  }
  static void check(BooleanSupplier cancelled){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("Library job cancelled");}
}
