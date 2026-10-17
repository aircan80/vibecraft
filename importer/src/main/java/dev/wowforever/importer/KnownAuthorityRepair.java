package dev.wowforever.importer;

import dev.wowforever.importer.foundation.FieldKnowledge;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/** Versioned selection-only repair; original assertions and application values remain unchanged. */
public final class KnownAuthorityRepair {
  public static final String TRANSFORM="knowledge-selection-v2";
  private KnownAuthorityRepair() {}
  /** Explicit maintenance of a caller-owned candidate; never creates a database. */
  public static void main(String[] args)throws Exception {
    if(args.length!=1 || !java.nio.file.Files.isRegularFile(java.nio.file.Path.of(args[0])))
      throw new IllegalArgumentException("Usage: KnownAuthorityRepair EXISTING_CANDIDATE_DATABASE");
    try(var c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+java.nio.file.Path.of(args[0]).toAbsolutePath())) {
      c.setAutoCommit(false);
      try {
        int changed=apply(c);
        try(var s=c.createStatement();var r=s.executeQuery("PRAGMA foreign_key_check")) {
          if(r.next())throw new SQLException("Knowledge repair foreign-key validation failed");
        }
        c.commit();System.out.println("Known selections repaired: "+changed);
      } catch(Exception failure){c.rollback();throw failure;}
    }
  }
  public static int apply(Connection c)throws SQLException {
    if(c.getAutoCommit())throw new IllegalArgumentException("Knowledge authority repair requires a caller-owned transaction");
    int changed=FieldKnowledge.rebuildKnownSelections(c);
    AdditiveProjection.record(c,Map.of(),null,"knowledge_selection",TRANSFORM,
        "Equal Known selection: comparable numeric builds; hotfix over native at same build; active hotfix snapshot resolves cache ties; changed="+changed);
    return changed;
  }
}
