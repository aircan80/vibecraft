package dev.wowforever.importer;
import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class AtlasMapsWorkflowTest {
 @TempDir Path temp;
 @Test void rejectsChangedPreparedSourcesBeforeAnyDatabaseMutation()throws Exception {
  Properties pins=new Properties();try(var in=AtlasMapsWorkflow.class.getResourceAsStream("/atlasmaps-pins.properties")){pins.load(in);}
  for(String name:pins.stringPropertyNames())Files.writeString(temp.resolve(name),"changed source");
  try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
   c.setAutoCommit(false);
   assertThatThrownBy(()->AtlasMapsWorkflow.apply(c,temp)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Atlas asset hash mismatch");
   try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM sqlite_master")){assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isZero();}
  }
 }
 @Test void refusesToCreateAnEmptyCandidate() {
  assertThatThrownBy(()->AtlasMapsWorkflow.main(new String[]{temp.resolve("missing.sqlite").toString(),temp.toString()})).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exist");
 }
}
