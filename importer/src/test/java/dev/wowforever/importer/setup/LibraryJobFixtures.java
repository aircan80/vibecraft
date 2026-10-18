package dev.wowforever.importer.setup;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Authored synthetic candidates test the orchestration boundary, not a completed real-data import. */
final class LibraryJobFixtures {
  static final String BUILD_ID="a".repeat(64);
  static SetupPlan plan(){var sources=Set.of(SetupPlan.Source.QUESTIE,SetupPlan.Source.NATIVE_DATA);return new SetupPlan(sources,
      Set.of(SetupPlan.Acquisition.WAGO_EXPORT),Optional.of(new SetupPlan.NativeIdentity("wow_classic_beta","1.60.1.70450","enUS")),
      List.of(),SetupPlan.currentPins(sources));}
  static SetupPlan.MaterializedInputs inputs(Path temporary)throws Exception {
    Path client=Files.createDirectory(temporary.resolve("native"));Files.createDirectory(client.resolve("csv"));
    byte[] bytes="ID,Name\n1,Synthetic\n".getBytes(StandardCharsets.UTF_8);Files.write(client.resolve("csv/Item.csv"),bytes);
    String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    Files.writeString(client.resolve("build.lock"),"{\"product\":\"wow_classic_beta\",\"build\":\"1.60.1.70450\",\"locale\":\"enUS\",\"fetched_at\":\"synthetic\",\"base_url\":\"https://wago.tools\",\"tables\":{\"Item\":\""+hash+"\"}}");
    return new SetupPlan.MaterializedInputs(dev.wowforever.importer.Fixtures.questieDbRoot(),Optional.empty(),Optional.empty(),
        Optional.of(new SetupPlan.MaterializedClient(client,SetupPlan.Acquisition.WAGO_EXPORT)));
  }
  static int syntheticProjection(Path archive,String ingestion,java.io.PrintStream report)throws Exception {
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+archive);var s=c.createStatement()) {
      String sql=dev.wowforever.importer.sqlite.SqliteWriter.schemaSql().lines().filter(line->!line.stripLeading().startsWith("--"))
          .collect(java.util.stream.Collectors.joining("\n"));
      for(String statement:sql.split("(?m);\\s*$"))if(!statement.isBlank())s.execute(statement);
      for(String table:List.of("item","npc","quest","zone","game_object"))s.executeUpdate("INSERT INTO "+table+"(id,name) VALUES (1,'Synthetic')");
      s.executeUpdate("UPDATE item SET has_client_data=1,client_source='forever'");
      Map<String,String> meta=Map.of("schema_version","4","build_id",BUILD_ID,"client_product","wow_classic_beta",
          "client_build","1.60.1.70450","source_ingestion_id",ingestion,"atlasloot_selection","not-selected");
      try(var p=c.prepareStatement("INSERT INTO meta VALUES (?,?)")){for(var entry:meta.entrySet()){p.setString(1,entry.getKey());p.setString(2,entry.getValue());p.executeUpdate();}}
      try(var p=c.prepareStatement("INSERT INTO source_projection(ingestion_id,projected_at,transform_version,schema_version,build_id,validation_summary) VALUES (?,'synthetic','synthetic-unit-projection',4,?,?)")){
        p.setString(1,ingestion);p.setString(2,BUILD_ID);p.setString(3,"UI skips=0; unsupported fields=0; client coverage=100%; client data skipped=0; synthetic unit boundary");p.executeUpdate();
      }
    }
    report.println("Authored synthetic unit projection; not real-data pipeline acceptance");return 0;
  }
  static String captureSynthetic(SetupPlan plan,SetupPlan.JobContext job,SetupPlan.MaterializedInputs inputs)throws Exception {
    String ingestion=plan.prepareCapture(job.storage(),job.paths(),inputs).capture();syntheticProjection(job.paths().archiveDatabase(),ingestion,System.out);return ingestion;
  }
  static void execute(Path file,String sql)throws Exception {try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()){s.execute(sql);}}
}
