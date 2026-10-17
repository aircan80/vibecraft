package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.allthethings.AllTheThingsParser;
import dev.wowforever.importer.allthethings.AllTheThingsSource;
import dev.wowforever.importer.source.SourceSpec;
import java.net.URI;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Conservative gap filling: a prerequisite group is accepted as a whole, never unioned with another provider. */
public final class AllTheThingsProjection {
  private AllTheThingsProjection() {}
  public record Stats(int selected, int conflicts, int unsupported) {}

  public static Stats apply(Connection connection, Path root, String snapshot, SourceSpec spec) throws Exception {
    return apply(connection, root, snapshot, spec, true);
  }

  public static Stats apply(Connection connection, Path root, String snapshot, SourceSpec spec, boolean pinned) throws Exception {
    return apply(connection, AllTheThingsSource.load(root, spec.paths()), snapshot, spec, pinned);
  }

  public static Stats apply(Connection c, AllTheThingsParser.Result result, String snapshot, SourceSpec spec) throws Exception {
    return apply(c, result, snapshot, spec, true);
  }

  public static Stats apply(Connection c, AllTheThingsParser.Result result, String snapshot, SourceSpec spec, boolean pinned) throws Exception {
    try (Statement s = c.createStatement()) {
      s.execute("""
          CREATE TABLE IF NOT EXISTS quest_relation_source(
            quest_id INTEGER NOT NULL, related_id INTEGER NOT NULL, kind TEXT NOT NULL, source TEXT NOT NULL,
            snapshot_id TEXT NOT NULL, artifact_path TEXT NOT NULL, source_url TEXT NOT NULL, evidence_status TEXT NOT NULL,
            source_line INTEGER NOT NULL, evidence TEXT NOT NULL,
            PRIMARY KEY(quest_id,related_id,kind,source,snapshot_id,artifact_path))
          """);
    }
    Map<Long,List<AllTheThingsParser.QuestLink>> groups = new TreeMap<>();
    for (var link : result.links()) groups.computeIfAbsent(link.questId(), k -> new ArrayList<>()).add(link);
    Set<Long> quests = new HashSet<>();
    try (Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT id FROM quest")) {
      while(r.next()) quests.add(r.getLong(1));
    }
    int selected=0, conflicts=0, unsupported=result.issues().size();
    for (var entry : groups.entrySet()) {
      long quest=entry.getKey(); var links=entry.getValue();
      if (!quests.contains(quest) || links.stream().anyMatch(l -> !quests.contains(l.relatedId()))) { unsupported++; continue; }
      Set<String> wanted=new TreeSet<>();
      for(var link:links) {
        if (!Set.of("pre_single","pre_group").contains(link.kind())) throw new IllegalArgumentException("Unknown ATT relation kind");
        wanted.add(link.kind()+":"+link.relatedId());
      }
      Set<String> existing=new TreeSet<>();
      try(PreparedStatement s=c.prepareStatement("SELECT kind,related_id FROM quest_relation WHERE quest_id=? AND kind IN ('pre_single','pre_group')")) {
        s.setLong(1,quest);try(ResultSet r=s.executeQuery()){while(r.next())existing.add(r.getString(1)+":"+r.getLong(2));}
      }
      // A singleton has identical AND/OR semantics; preserve the canonical provider's representation.
      boolean singletonEquivalent=existing.size()==1 && wanted.size()==1
          && existing.iterator().next().split(":")[1].equals(wanted.iterator().next().split(":")[1]);
      if(!existing.isEmpty() && !existing.equals(wanted) && !singletonEquivalent) { conflicts++; continue; }
      Set<String> counted=new HashSet<>();
      for(var link:links) {
        String kind=singletonEquivalent ? existing.iterator().next().split(":")[0] : link.kind();
        try(PreparedStatement s=c.prepareStatement("INSERT OR IGNORE INTO quest_relation(quest_id,related_id,kind) VALUES (?,?,?)")) {
          s.setLong(1,quest);s.setLong(2,link.relatedId());s.setString(3,kind);s.executeUpdate();
        }
        String url=pinned ? new URI("https","github.com","/"+spec.owner()+"/"+spec.repo()+"/blob/"+spec.commit()+"/"+link.sourcePath(),null,
            link.sourceLine()>0 ? "L"+link.sourceLine():null).toASCIIString() : "";
        try(PreparedStatement s=c.prepareStatement("INSERT OR IGNORE INTO quest_relation_source(quest_id,related_id,kind,source,snapshot_id,artifact_path,source_url,evidence_status,source_line,evidence) VALUES (?,?,?,?,?,?,?,?,?,?)")) {
          s.setLong(1,quest);s.setLong(2,link.relatedId());s.setString(3,kind);s.setString(4,"allthethings-forever");
          s.setString(5,snapshot);s.setString(6,link.sourcePath());s.setString(7,url);s.setString(8,"authored");
          s.setInt(9,link.sourceLine());s.setString(10,link.evidence()+"; configured revision="+spec.pin()
              +(pinned ? "; pinned upstream input" : "; local input identified by snapshot hash"));s.executeUpdate();
        }
        if(counted.add(kind+":"+link.relatedId())) selected++;
      }
    }
    Map<String,String> meta=Map.of("att_schema_version","1","att_local_input",Boolean.toString(!pinned),"att_commit",spec.commit(),"att_source","AllTheThings Forever (MIT)",
        "att_quest_links_selected",Integer.toString(selected),"att_quest_links_conflict",Integer.toString(conflicts),
        "att_unsupported_conditions",Integer.toString(unsupported));
    for(var entry:meta.entrySet())try(PreparedStatement s=c.prepareStatement("INSERT OR REPLACE INTO meta(key,value) VALUES (?,?)")) {
      s.setString(1,entry.getKey());s.setString(2,entry.getValue());s.executeUpdate();
    }
    return new Stats(selected,conflicts,unsupported);
  }
}
