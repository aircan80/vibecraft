package dev.wowforever.importer.foundation;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.importer.foundation.FieldKnowledge.Evidence;
import dev.wowforever.importer.foundation.FieldKnowledge.State;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;

class FieldKnowledgeTest {
  private Evidence evidence(State state, String source, String version, String value) {
    return new Evidence("item", "286978", "damage", state, source, version,
        "snapshot-" + version, "Item.csv", 42, value);
  }

  private String canonical(Connection c, String column) throws Exception {
    try (var s = c.createStatement(); var r = s.executeQuery("SELECT " + column + " FROM field_knowledge")) {
      return r.next() ? r.getString(1) : null;
    }
  }

  private int count(Connection c, String table) throws Exception {
    try (var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM " + table)) {
      return r.getInt(1);
    }
  }

  @Test void semanticUpgradesPreserveAllConflictingAssertionsAndProvenance() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      FieldKnowledge.assertEvidence(c, evidence(State.HIDDEN_BY_BLIZZARD,"client","1",null));
      FieldKnowledge.assertEvidence(c, evidence(State.UNANNOUNCED,"other","1",null));
      assertThat(canonical(c,"state")).isEqualTo("HIDDEN_BY_BLIZZARD");
      FieldKnowledge.assertEvidence(c, evidence(State.REPORTED,"community","2","8"));
      assertThat(canonical(c,"state")).isEqualTo("REPORTED");
      FieldKnowledge.assertEvidence(c, evidence(State.KNOWN,"client","3","0"));
      FieldKnowledge.assertEvidence(c, evidence(State.REPORTED,"community","4","9"));
      FieldKnowledge.assertEvidence(c, evidence(State.KNOWN,"client","5","10"));
      assertThat(canonical(c,"state")).isEqualTo("KNOWN");
      assertThat(canonical(c,"data_version")).isEqualTo("3");
      assertThat(count(c,"field_knowledge_assertion")).isEqualTo(6);
      try (var s = c.createStatement(); var r = s.executeQuery("SELECT * FROM field_knowledge_assertion WHERE data_version='4'")) {
        assertThat(r.next()).isTrue();
        assertThat(r.getString("snapshot")).isEqualTo("snapshot-4");
        assertThat(r.getString("path")).isEqualTo("Item.csv");
        assertThat(r.getInt("line")).isEqualTo(42);
        assertThat(r.getString("value")).isEqualTo("9");
      }
    }
  }

  @Test void noneIsExplicitAbsenceAndNeedsRetractionBeforeReplacement() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      long absent = FieldKnowledge.assertEvidence(c, evidence(State.NONE,"client","1",null));
      FieldKnowledge.assertEvidence(c, evidence(State.KNOWN,"client","2","7"));
      assertThat(canonical(c,"state")).isEqualTo("NONE");
      assertThatThrownBy(() -> FieldKnowledge.retractEvidence(c, absent," "))
          .isInstanceOf(IllegalArgumentException.class);
      FieldKnowledge.retractEvidence(c, absent,"Source corrected its explicit absence claim");
      assertThat(canonical(c,"state")).isEqualTo("KNOWN");
      assertThat(count(c,"field_knowledge_assertion")).isEqualTo(2);
      assertThat(count(c,"field_knowledge_retraction")).isEqualTo(1);
      FieldKnowledge.assertEvidence(c, evidence(State.NONE,"client","1",null));
      assertThat(canonical(c,"state")).isEqualTo("KNOWN");
      assertThat(count(c,"field_knowledge_assertion")).isEqualTo(2);
    }
  }

  @Test void blankZeroAndNullRemainDistinctValuesAndNeverInferState() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      for (String value : new String[]{null,"","0"}) {
        var e = evidence(State.KNOWN,"client","1",value);
        assertThat(FieldKnowledge.assertEvidence(c,e)).isEqualTo(FieldKnowledge.assertEvidence(c,e));
      }
      assertThat(c.getAutoCommit()).isTrue();
      assertThat(count(c,"field_knowledge_assertion")).isEqualTo(3);
      assertThat(canonical(c,"state")).isEqualTo("KNOWN");
      FieldKnowledge.assertEvidence(c,new Evidence("item","16806","name",State.KNOWN,"client","1","s","Item.csv",null,""));
      assertThat(count(c,"field_knowledge")).isEqualTo(2);
    }
  }

  @Test void explicitRetractionAllowsReportedFallbackAndRetainsHistoryWhenEmpty() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      long known = FieldKnowledge.assertEvidence(c,evidence(State.KNOWN,"client","1","1"));
      long reported = FieldKnowledge.assertEvidence(c,evidence(State.REPORTED,"community","1","2"));
      FieldKnowledge.retractEvidence(c,known,"Incorrect client mapping");
      assertThat(canonical(c,"state")).isEqualTo("REPORTED");
      FieldKnowledge.retractEvidence(c,reported,"Withdrawn report");
      assertThat(canonical(c,"state")).isNull();
      assertThat(count(c,"field_knowledge_assertion")).isEqualTo(2);
      assertThat(count(c,"field_knowledge_retraction")).isEqualTo(2);
      FieldKnowledge.retractEvidence(c,reported,"Replay");
      assertThat(count(c,"field_knowledge_retraction")).isEqualTo(2);
      assertThatThrownBy(() -> FieldKnowledge.retractEvidence(c,999,"Unknown"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test void callerCanRollbackAssertionsAndSchemaInitIsIdempotent() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      FieldKnowledge.initialize(c); FieldKnowledge.initialize(c);
      c.setAutoCommit(false);
      FieldKnowledge.assertEvidence(c,evidence(State.KNOWN,"client","1","0"));
      c.rollback();
      assertThat(count(c,"field_knowledge_assertion")).isZero();
      assertThat(count(c,"field_knowledge")).isZero();
    }
  }
  private Evidence stats(String source,String version,String snapshot,String value) {
    return new Evidence("item","869","stats",State.KNOWN,source,version,snapshot,"ItemSparse.csv",9,value);
  }
  @Test void sameBuildHotfixWinsKnownNativeAndOldReplayCannotStealSelection()throws Exception {
    try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
      var nativeRow=stats("blizzard-client","1.60.1.70450","native-70450","native-value");
      var hotfix=stats("blizzard-hotfix","1.60.1.70450","hotfix-70450","hotfix-value");
      FieldKnowledge.assertEvidence(c,nativeRow);FieldKnowledge.assertEvidence(c,hotfix);
      assertThat(canonical(c,"source")).isEqualTo("blizzard-hotfix");
      FieldKnowledge.assertEvidence(c,nativeRow);
      FieldKnowledge.assertEvidence(c,stats("blizzard-client","1.60.1.70449","older-native","older"));
      assertThat(canonical(c,"source")).isEqualTo("blizzard-hotfix");
      try(var s=c.createStatement();var r=s.executeQuery("SELECT value FROM field_knowledge_assertion WHERE assertion_id=(SELECT assertion_id FROM field_knowledge)")){assertThat(r.getString(1)).isEqualTo("hotfix-value");}
      assertThat(count(c,"field_knowledge_assertion")).isEqualTo(3);
    }
  }
  @Test void newerNumericBuildWinsBeforeAuthorityAndIncomparableVersionsDoNotClaimNewness()throws Exception {
    try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
      FieldKnowledge.assertEvidence(c,stats("blizzard-client","1.60.1.9999","old","old"));
      FieldKnowledge.assertEvidence(c,stats("blizzard-client","1.60.1.10000","new","new"));
      FieldKnowledge.assertEvidence(c,stats("blizzard-hotfix","1.60.1.9999","old-hotfix","old-hotfix"));
      FieldKnowledge.assertEvidence(c,stats("blizzard-hotfix","latest","unidentified-version","unknown"));
      FieldKnowledge.assertEvidence(c,stats("blizzard-client-era","1.15.9.70003","wrong-family","classic"));
      assertThat(canonical(c,"data_version")).isEqualTo("1.60.1.10000");
      assertThat(canonical(c,"source")).isEqualTo("blizzard-client");
      FieldKnowledge.assertEvidence(c,new Evidence("item","869","classic_stats",State.KNOWN,"blizzard-client-era","1.15.9.70003","era","ItemSparse.csv",3,"era-value"));
      assertThat(count(c,"field_knowledge")).isEqualTo(2);
    }
  }
  @Test void activeHotfixSnapshotRepairsCacheTieAndPreservesCallerRollbackAndEvidence()throws Exception {
    try(var c=DriverManager.getConnection("jdbc:sqlite::memory:");var s=c.createStatement()) {
      s.execute("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)");
      FieldKnowledge.assertEvidence(c,stats("blizzard-hotfix","1.60.1.70450","a-cache","first-value"));
      FieldKnowledge.assertEvidence(c,stats("blizzard-hotfix","1.60.1.70450","z-active-cache","active-value"));
      try(var r=s.executeQuery("SELECT a.snapshot FROM field_knowledge k JOIN field_knowledge_assertion a USING(assertion_id)")){assertThat(r.getString(1)).isEqualTo("a-cache");}
      c.setAutoCommit(false);s.execute("INSERT INTO meta VALUES ('hotfix_snapshot_id','z-active-cache')");
      assertThat(FieldKnowledge.rebuildKnownSelections(c)).isEqualTo(1);
      try(var r=s.executeQuery("SELECT a.snapshot FROM field_knowledge k JOIN field_knowledge_assertion a USING(assertion_id)")){assertThat(r.getString(1)).isEqualTo("z-active-cache");}
      assertThat(FieldKnowledge.rebuildKnownSelections(c)).isZero();
      assertThat(count(c,"field_knowledge_assertion")).isEqualTo(2);c.rollback();
      try(var r=s.executeQuery("SELECT a.snapshot FROM field_knowledge k JOIN field_knowledge_assertion a USING(assertion_id)")){assertThat(r.getString(1)).isEqualTo("a-cache");}
    }
  }

}
