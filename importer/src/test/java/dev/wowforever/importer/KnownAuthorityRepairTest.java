package dev.wowforever.importer;
import dev.wowforever.importer.foundation.FieldKnowledge;
import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class KnownAuthorityRepairTest {
 @Test void recordsVersionedSelectionWithoutChangingInputsOrCommittingCaller()throws Exception {
  try(var c=DriverManager.getConnection("jdbc:sqlite::memory:");var s=c.createStatement()) {
   ArchiveWorkflow.initialize(c);s.execute("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)");s.execute("INSERT INTO meta VALUES('build_id','before'),('source_ingestion_id','input')");s.execute("INSERT INTO source_ingestion VALUES('input','before')");s.execute("INSERT INTO source_ingestion_input VALUES('input','native','source-native')");
   FieldKnowledge.assertEvidence(c,new FieldKnowledge.Evidence("item","869","stats",FieldKnowledge.State.KNOWN,"blizzard-client","1.60.1.70450","native","ItemSparse.csv",1,"old"));
   long chosen=FieldKnowledge.assertEvidence(c,new FieldKnowledge.Evidence("item","869","stats",FieldKnowledge.State.KNOWN,"blizzard-hotfix","1.60.1.70450","hotfix","ItemSparse.csv",1,"new"));
   // Simulate the historical earliest-assertion selection, preserving all actual evidence.
   s.execute("UPDATE field_knowledge SET source='blizzard-client',assertion_id=1");c.setAutoCommit(false);
   assertThat(KnownAuthorityRepair.apply(c)).isEqualTo(1);assertThat(AdditiveProjection.meta(c,"source_overlay_transform_knowledge_selection")).isEqualTo(KnownAuthorityRepair.TRANSFORM);
   try(var r=s.executeQuery("SELECT assertion_id FROM field_knowledge")){assertThat(r.getLong(1)).isEqualTo(chosen);}
   try(var r=s.executeQuery("SELECT count(*) FROM field_knowledge_assertion")){assertThat(r.getInt(1)).isEqualTo(2);}
   assertThat(AdditiveProjection.meta(c,"build_id")).isNotEqualTo("before");c.rollback();
   assertThat(AdditiveProjection.meta(c,"build_id")).isEqualTo("before");
   try(var r=s.executeQuery("SELECT source FROM field_knowledge")){assertThat(r.getString(1)).isEqualTo("blizzard-client");}
  }
 }
}
