package dev.wowforever.app.db;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoreSearchRepositoryTest {
  @TempDir Path directory;
  private Database fixture() throws Exception {
    Path file=directory.resolve("lore.sqlite");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("CREATE TABLE quest(id INTEGER PRIMARY KEY,name TEXT,objectives_text TEXT)");
      s.execute("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT)");
      s.execute("INSERT INTO meta VALUES('build_id','test-build'),('questiedb_commit','base'),('questiedb_refresh_commit','refresh')");
      s.execute("INSERT INTO quest VALUES(1,'A city','Visit Dalaran and ask about the tower.'),(2,'Dalaran',NULL),(3,'Literal','100% /path\\name under_score <script>')");
    }
    return Database.open(file);
  }
  @Test void exactMentionsPreserveIdentityAndSource() throws Exception {
    try(var db=fixture()) {
      var result=new LoreSearchRepository(db).search("dalaran",()->true);
      assertEquals(2,result.hits().size());assertEquals(1,result.hits().getFirst().questId());
      var hit=result.hits().getFirst();assertEquals("Dalaran",hit.excerpt().substring(hit.matchStart(),hit.matchEnd()));
      assertTrue(result.source().contains("refresh"));assertEquals("test-build",result.buildId());
    }
  }
  @Test void wildcardsAndBackslashesAreLiteral() throws Exception {
    try(var db=fixture()) {var repo=new LoreSearchRepository(db);
      for(String q:new String[]{"100%","/path\\name","under_score","<script>"})assertEquals(3,repo.search(q,()->true).hits().getFirst().questId());
      assertTrue(repo.search("%%%",()->true).hits().isEmpty());assertTrue(repo.search("___",()->true).hits().isEmpty());
      assertTrue(repo.search("a",()->true).hits().isEmpty());assertTrue(repo.search("x".repeat(121),()->true).hits().isEmpty());
      assertThrows(CancellationException.class,()->repo.search("Dalaran",()->false));
    }
  }
  @Test void cappedResultsHaveExplicitSentinel() throws Exception {
    try(var db=fixture()) {
      // A separate writer represents corpus construction, not a production app write.
      try(var c=DriverManager.getConnection("jdbc:sqlite:"+directory.resolve("lore.sqlite"));var s=c.createStatement()){
        s.execute("WITH RECURSIVE n(x) AS (VALUES(10) UNION ALL SELECT x+1 FROM n WHERE x<120) INSERT INTO quest SELECT x,'Mention','Dalaran' FROM n");
      }
      var result=new LoreSearchRepository(db).search("Dalaran",()->true);assertEquals(100,result.hits().size());assertTrue(result.truncated());
    }
  }
  @Test void deterministicExcerptKeepsOriginalOffsets() {
    String value="x".repeat(100)+"Dalaran"+"y".repeat(200);
    var hit=LoreSearchRepository.excerpt(1,"Title",value,"dalaran");
    assertTrue(hit.excerpt().startsWith("…"));assertEquals("Dalaran",hit.excerpt().substring(hit.matchStart(),hit.matchEnd()));
    assertEquals(hit,LoreSearchRepository.excerpt(1,"Title",value,"dalaran"));
  }
}
