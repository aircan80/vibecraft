package dev.wowforever.importer.foundation;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Per-field knowledge is independent of the field's value. All assertions remain inspectable. */
public final class FieldKnowledge {
  private FieldKnowledge() {}

  public enum State {
    KNOWN("Known"), HIDDEN_BY_BLIZZARD("Hidden by Blizzard"),
    NOT_YET_DISCOVERED("Not yet discovered"), REPORTED("Reported"),
    UNANNOUNCED("Unannounced"), NONE("None");
    private final String label;
    State(String label) { this.label = label; }
    public String label() { return label; }
  }

  public record Evidence(String entity, String id, String field, State state, String source,
      String dataVersion, String snapshot, String path, Integer line, String value) {
    public Evidence {
      for (String s : new String[]{entity, id, field, source, dataVersion, snapshot, path})
        if (s == null || s.isBlank()) throw new IllegalArgumentException("Evidence identity/provenance must be nonblank");
      Objects.requireNonNull(state, "state");
      if (line != null && line < 1) throw new IllegalArgumentException("Line must be positive");
    }
  }

  public static void initialize(Connection c) throws SQLException {
    try (var s = c.createStatement()) {
      s.executeUpdate("""
          CREATE TABLE IF NOT EXISTS field_knowledge_assertion (
            assertion_id INTEGER PRIMARY KEY, entity TEXT NOT NULL, id TEXT NOT NULL,
            field TEXT NOT NULL, state TEXT NOT NULL CHECK(state IN
            ('KNOWN','HIDDEN_BY_BLIZZARD','NOT_YET_DISCOVERED','REPORTED','UNANNOUNCED','NONE')),
            source TEXT NOT NULL, data_version TEXT NOT NULL, snapshot TEXT NOT NULL,
            path TEXT NOT NULL, line INTEGER, value TEXT)
          """);
      s.executeUpdate("""
          CREATE UNIQUE INDEX IF NOT EXISTS field_knowledge_assertion_identity ON field_knowledge_assertion
          (entity,id,field,state,source,data_version,snapshot,path,coalesce(line,-1),value IS NULL,coalesce(value,''))
          """);
      s.executeUpdate("""
          CREATE TABLE IF NOT EXISTS field_knowledge_retraction (
            assertion_id INTEGER PRIMARY KEY REFERENCES field_knowledge_assertion(assertion_id),
            reason TEXT NOT NULL CHECK(length(trim(reason)) > 0),
            retracted_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)
          """);
      s.executeUpdate("""
          CREATE TABLE IF NOT EXISTS field_knowledge (
            entity TEXT NOT NULL, id TEXT NOT NULL, field TEXT NOT NULL, state TEXT NOT NULL,
            source TEXT NOT NULL, data_version TEXT NOT NULL,
            assertion_id INTEGER NOT NULL REFERENCES field_knowledge_assertion(assertion_id),
            PRIMARY KEY(entity,id,field))
          """);
    }
  }

  /** Does not commit the caller's transaction. Replays are idempotent, including retracted evidence. */
  public static long assertEvidence(Connection c, Evidence e) throws SQLException {
    initialize(c);
    boolean ownTransaction = c.getAutoCommit();
    if (ownTransaction) c.setAutoCommit(false);
    var savepoint = c.setSavepoint();
    try {
      try (var p = c.prepareStatement("""
          INSERT OR IGNORE INTO field_knowledge_assertion
          (entity,id,field,state,source,data_version,snapshot,path,line,value) VALUES(?,?,?,?,?,?,?,?,?,?)
          """)) {
        bind(p, e); p.executeUpdate();
      }
      long assertion;
      try (var p = c.prepareStatement("""
          SELECT assertion_id FROM field_knowledge_assertion WHERE entity=? AND id=? AND field=?
          AND state=? AND source=? AND data_version=? AND snapshot=? AND path=? AND line IS ? AND value IS ?
          """)) {
        bind(p, e);
        try (var r = p.executeQuery()) {
          if (!r.next()) throw new SQLException("Assertion was not stored");
          assertion = r.getLong(1);
        }
      }
      rebuild(c, e.entity(), e.id(), e.field());
      c.releaseSavepoint(savepoint);
      return assertion;
    } catch (SQLException | RuntimeException ex) {
      c.rollback(savepoint); c.releaseSavepoint(savepoint); throw ex;
    } finally {
      if (ownTransaction) c.setAutoCommit(true);
    }
  }

  /** Retraction is explicit, reasoned and permanent; an identical replay cannot resurrect it. */
  public static void retractEvidence(Connection c, long assertionId, String reason) throws SQLException {
    if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Retraction requires a reason");
    initialize(c);
    boolean ownTransaction = c.getAutoCommit();
    if (ownTransaction) c.setAutoCommit(false);
    var savepoint = c.setSavepoint();
    try {
      String entity, id, field;
      try (var p = c.prepareStatement("SELECT entity,id,field FROM field_knowledge_assertion WHERE assertion_id=?")) {
        p.setLong(1, assertionId);
        try (var r = p.executeQuery()) {
          if (!r.next()) throw new IllegalArgumentException("Unknown assertion: " + assertionId);
          entity = r.getString(1); id = r.getString(2); field = r.getString(3);
        }
      }
      try (var p = c.prepareStatement("INSERT OR IGNORE INTO field_knowledge_retraction(assertion_id,reason) VALUES(?,?)")) {
        p.setLong(1, assertionId); p.setString(2, reason); p.executeUpdate();
      }
      rebuild(c, entity, id, field);
      c.releaseSavepoint(savepoint);
    } catch (SQLException | RuntimeException ex) {
      c.rollback(savepoint); c.releaseSavepoint(savepoint); throw ex;
    } finally {
      if (ownTransaction) c.setAutoCommit(true);
    }
  }

  public static boolean upgrades(State current, State candidate) {
    if (current == State.KNOWN || current == State.NONE || current == candidate) return false;
    return candidate == State.KNOWN || candidate == State.NONE || candidate == State.REPORTED;
  }

  private static void bind(java.sql.PreparedStatement p, Evidence e) throws SQLException {
    p.setString(1,e.entity()); p.setString(2,e.id()); p.setString(3,e.field());
    p.setString(4,e.state().name()); p.setString(5,e.source()); p.setString(6,e.dataVersion());
    p.setString(7,e.snapshot()); p.setString(8,e.path()); p.setObject(9,e.line()); p.setString(10,e.value());
  }

  /** Re-select equal Known assertions without changing evidence or committing the caller's transaction. */
  public static int rebuildKnownSelections(Connection c) throws SQLException {
    if(c.getAutoCommit())throw new IllegalArgumentException("Knowledge selection repair requires a caller-owned transaction");
    initialize(c);
    var keys=new java.util.ArrayList<String[]>();
    try(var p=c.createStatement();var r=p.executeQuery("""
        SELECT a.entity,a.id,a.field FROM field_knowledge_assertion a
        LEFT JOIN field_knowledge_retraction r ON r.assertion_id=a.assertion_id
        WHERE a.state='KNOWN' AND r.assertion_id IS NULL GROUP BY a.entity,a.id,a.field HAVING count(*)>1
        """)) {while(r.next())keys.add(new String[]{r.getString(1),r.getString(2),r.getString(3)});}
    int changed=0;
    for(var key:keys) {
      long before=selected(c,key[0],key[1],key[2]);rebuild(c,key[0],key[1],key[2]);
      if(before!=selected(c,key[0],key[1],key[2]))changed++;
    }
    return changed;
  }

  private static long selected(Connection c,String entity,String id,String field)throws SQLException {
    try(var p=c.prepareStatement("SELECT assertion_id FROM field_knowledge WHERE entity=? AND id=? AND field=?")) {
      p.setString(1,entity);p.setString(2,id);p.setString(3,field);try(var r=p.executeQuery()){return r.next()?r.getLong(1):-1;}
    }
  }

  private record Claim(long assertion,State state,String source,String version,String snapshot,String path,Integer line,String value) {}
  private static void rebuild(Connection c, String entity, String id, String field) throws SQLException {
    Claim selected=null;
    try (var p = c.prepareStatement("""
        SELECT a.assertion_id,a.state,a.source,a.data_version,a.snapshot,a.path,a.line,a.value
        FROM field_knowledge_assertion a
        LEFT JOIN field_knowledge_retraction r ON r.assertion_id=a.assertion_id
        WHERE a.entity=? AND a.id=? AND a.field=? AND r.assertion_id IS NULL ORDER BY a.assertion_id
        """)) {
      p.setString(1,entity); p.setString(2,id); p.setString(3,field);
      try (var r = p.executeQuery()) {
        while (r.next()) {
          Integer line=r.getObject(7)==null?null:r.getInt(7);
          Claim candidate=new Claim(r.getLong(1),State.valueOf(r.getString(2)),r.getString(3),r.getString(4),r.getString(5),r.getString(6),line,r.getString(8));
          if(selected==null || upgrades(selected.state,candidate.state) || prefersKnown(c,candidate,selected))selected=candidate;
        }
      }
    }
    try (var p = c.prepareStatement("DELETE FROM field_knowledge WHERE entity=? AND id=? AND field=?")) {
      p.setString(1,entity); p.setString(2,id); p.setString(3,field); p.executeUpdate();
    }
    if (selected != null) try (var p = c.prepareStatement("""
        INSERT INTO field_knowledge(entity,id,field,state,source,data_version,assertion_id)
        SELECT entity,id,field,state,source,data_version,assertion_id FROM field_knowledge_assertion WHERE assertion_id=?
        """)) { p.setLong(1,selected.assertion); p.executeUpdate(); }
  }

  /** Authority only resolves equal Known facts from comparable, explicitly named Blizzard sources. */
  private static boolean prefersKnown(Connection c,Claim candidate,Claim current)throws SQLException {
    if(candidate.state!=State.KNOWN || current.state!=State.KNOWN)return false;
    int candidateAuthority=authority(candidate.source),currentAuthority=authority(current.source);
    if(candidateAuthority==0 || currentAuthority==0)return false;
    long[] next=build(candidate.version),prior=build(current.version);
    if(next==null || prior==null || next[0]!=prior[0] || next[1]!=prior[1])return false;
    for(int i=0;i<next.length;i++)if(next[i]!=prior[i])return next[i]>prior[i];
    if(candidateAuthority!=currentAuthority)return candidateAuthority>currentAuthority;
    if(candidateAuthority==2) {
      String active=activeHotfix(c);
      boolean candidateActive=candidate.snapshot.equals(active),currentActive=current.snapshot.equals(active);
      if(candidateActive!=currentActive)return candidateActive;
    }
    // Stable content identity is a tie-break only; it does not claim one cache was published later.
    int comparison=candidate.snapshot.compareTo(current.snapshot);
    if(comparison==0)comparison=candidate.path.compareTo(current.path);
    if(comparison==0)comparison=java.util.Comparator.nullsFirst(Integer::compareTo).compare(candidate.line,current.line);
    if(comparison==0)comparison=java.util.Comparator.nullsFirst(String::compareTo).compare(candidate.value,current.value);
    return comparison<0;
  }
  private static int authority(String source) {
    return switch(source){case "blizzard-hotfix"->2;case "blizzard-client","blizzard-client-era"->1;default->0;};
  }
  private static long[] build(String version) {
    if(version==null || !version.matches("[0-9]+(?:\\.[0-9]+){3}"))return null;
    try {String[] parts=version.split("\\.");long[] build=new long[4];for(int i=0;i<4;i++)build[i]=Long.parseLong(parts[i]);return build;}
    catch(NumberFormatException ignored){return null;}
  }
  private static String activeHotfix(Connection c)throws SQLException {
    try(var s=c.createStatement();var r=s.executeQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='meta'")){if(!r.next())return "";}
    try(var p=c.prepareStatement("SELECT value FROM meta WHERE key='hotfix_snapshot_id'");var r=p.executeQuery()){return r.next()?r.getString(1):"";}
  }
}
