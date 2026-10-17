package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForeverLootFactsTest {
  @TempDir Path temp;
  @Test void multipleAddonSupportsStayDistinctFromRatesAndDoNotDuplicateLoot() throws Exception {
    Path file=temp.resolve("reported.sqlite");Files.copy(FixtureDb.path(),file);
    long boss;
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      try(var r=s.executeQuery("SELECT id FROM boss WHERE npc_id=639 LIMIT 1")){assertThat(r.next()).isTrue();boss=r.getLong(1);}
      s.execute("CREATE TABLE atlasloot_forever_fact(boss_id INTEGER,item_id INTEGER,difficulty TEXT,snapshot_id TEXT,provider TEXT)");
      s.execute("INSERT INTO atlasloot_forever_fact VALUES("+boss+",2874,'NORMAL','a','atlasloot-forever'),("+boss+",2874,'NORMAL','b','atlasloot-continued-forever'),("+boss+",2874,'NORMAL','c','atlasloot-forever')");
      s.execute("CREATE TABLE collector_fact(snapshot_id TEXT,category TEXT,ordinal INTEGER,item_id INTEGER,entity_type TEXT,entity_id INTEGER)");
    }
    var original=FixtureDb.repositories().instances().bossLoot(boss,"NORMAL");
    try(var db=Database.open(file)) {
      var r=Repositories.of(db);
      var current=r.instances().bossLoot(boss,"NORMAL");
      assertThat(current).hasSize(original.size());
      assertThat(current).filteredOn(l -> l.item().id()==2874).singleElement().satisfies(l -> {
        var previous=original.stream().filter(o -> o.item().id()==2874).findFirst().orElseThrow();
        assertThat(l.origin()).isEqualTo("AtlasLoot Continued + AtlasLoot Forever (reported)");
        assertThat(l.observedInGame()).isFalse();
        assertThat(l.dropPct()).isEqualTo(previous.dropPct());assertThat(l.pctSource()).isEqualTo(previous.pctSource());
      });
      assertThat(r.items().detail(2874).orElseThrow().inInstanceLoot()).filteredOn(l -> l.bossId()==boss).singleElement()
          .satisfies(l -> assertThat(l.displayOrigin()).isEqualTo("AtlasLoot Continued + AtlasLoot Forever (reported)"));
    }
  }
}
