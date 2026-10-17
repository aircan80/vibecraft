package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.app.model.FieldKnowledgeView.State;
import java.nio.file.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FieldKnowledgeRepositoryTest {
  @TempDir Path temp;
  @Test void explicitKnowledgeOverridesTooltipGuessAndKeepsZero()throws Exception {
    Path file=temp.resolve("knowledge.sqlite");Files.copy(FixtureDb.path(),file);
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("CREATE TABLE field_knowledge(entity TEXT,id TEXT,field TEXT,state TEXT,source TEXT,data_version TEXT,assertion_id INTEGER)");
      s.execute("CREATE TABLE field_knowledge_assertion(assertion_id INTEGER,value TEXT,snapshot TEXT,path TEXT,line INTEGER)");
      s.execute("INSERT INTO field_knowledge VALUES('item','2874','stats','KNOWN','forever','70450',1),('item','14149','stats','HIDDEN_BY_BLIZZARD','forever','70450',2),('item','14149','classic_stats','KNOWN','era','70003',3)");
      s.execute("INSERT INTO field_knowledge_assertion VALUES(1,'0','snapshot','ItemSparse.csv',7),(2,NULL,'snapshot','Item.csv',8),(3,'Era values','era-snapshot','ItemSparse.csv',9)");
      s.execute("UPDATE item SET item_level=0,required_level=0 WHERE id=2874");
    }
    try(var db=Database.open(file)) {
      var known=new FieldKnowledgeRepository(db).item(2874);
      assertThat(known).anySatisfy(k->{assertThat(k.field()).isEqualTo("stats");assertThat(k.state()).isEqualTo(State.KNOWN);assertThat(k.displayValue()).isEqualTo("0");assertThat(k.evidence()).contains("ItemSparse.csv:7");});
      assertThat(known).anySatisfy(k->{assertThat(k.field()).isEqualTo("item_level");assertThat(k.displayValue()).isEqualTo("0");});
      var hidden=new FieldKnowledgeRepository(db).item(14149);
      assertThat(hidden).anySatisfy(k->{assertThat(k.field()).isEqualTo("stats");assertThat(k.state()).isEqualTo(State.HIDDEN_BY_BLIZZARD);});
      assertThat(hidden).anySatisfy(k->{assertThat(k.field()).isEqualTo("classic_stats");assertThat(k.state()).isEqualTo(State.KNOWN);assertThat(k.source()).isEqualTo("era");});
    }
  }
  @Test void legacyEmptyDataDoesNotBecomeAClaimThatBlizzardHidIt()throws Exception {
    try(var db=Database.open(FixtureDb.path())) {
      assertThat(new FieldKnowledgeRepository(db).item(190179)).noneSatisfy(k->assertThat(k.state()).isEqualTo(State.HIDDEN_BY_BLIZZARD));
      assertThat(new FieldKnowledgeRepository(db).item(-1)).isEmpty();
    }
  }
}
