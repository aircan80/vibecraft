package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.app.model.ItemCard;
import java.sql.*;
import org.junit.jupiter.api.Test;

class SpellTextEvidenceRepositoryTest {
  private Connection fixture() throws Exception {
    var c=DriverManager.getConnection("jdbc:sqlite::memory:");
    try(var s=c.createStatement()) {
      s.execute("CREATE TABLE item(id INTEGER,quality INTEGER,icon_id INTEGER,inventory_type INTEGER,bonding INTEGER,sell_price INTEGER,has_client_data INTEGER,item_set_id INTEGER,name TEXT,client_source TEXT)");
      s.execute("INSERT INTO item VALUES(1,1,NULL,NULL,NULL,NULL,1,NULL,'Test','era')");
      s.execute("CREATE TABLE item_set_item(set_id INTEGER,item_id INTEGER)");
      s.execute("CREATE TABLE item_tooltip_line(item_id INTEGER,ordinal INTEGER,kind TEXT,text TEXT,colour TEXT)");
      s.execute("INSERT INTO item_tooltip_line VALUES(1,0,'use','Use: Restores $q1','#1eff00')");
    }
    return c;
  }
  private ItemCard card(Connection c)throws Exception {
    var method=ItemRepository.class.getDeclaredMethod("card",Connection.class,long.class);method.setAccessible(true);
    return (ItemCard)method.invoke(null,c,1L);
  }
  @Test void legacyDatabaseDoesNotInventCompletenessOrLoseSourceAndRawText()throws Exception {
    try(var c=fixture()) {
      var line=card(c).lines().getFirst();
      assertThat(line.evidence().status()).isEqualTo("UNKNOWN");
      assertThat(line.text()).isEqualTo("Use: Restores $q1");
      assertThat(line.evidence().product()).isNull();
      try(var s=c.createStatement();var rows=s.executeQuery("SELECT client_source FROM item")) {
        rows.next();assertThat(rows.getString(1)).isEqualTo("era");
      }
    }
  }
  @Test void sidecarLoadsExactRecordedEvidenceAndMissingRowsRemainUnknown()throws Exception {
    try(var c=fixture();var s=c.createStatement()) {
      s.execute("CREATE TABLE spell_text_evidence(owner_kind TEXT,owner_id INTEGER,ordinal INTEGER,status TEXT,spell_id INTEGER,raw_text TEXT,unresolved TEXT,source TEXT,product TEXT,build TEXT,table_sha256 TEXT)");
      s.execute("INSERT INTO spell_text_evidence VALUES('tooltip',1,0,'INCOMPLETE',42,'Restores $q1','$q1','csv/Spell.csv','wow_classic_beta','1.60.1.70450','actual-hash')");
      var line=card(c).lines().getFirst();
      assertThat(line.evidence().status()).isEqualTo("INCOMPLETE");
      assertThat(line.evidence().unresolved()).containsExactly("$q1");
      assertThat(line.evidence().rawText()).isEqualTo("Restores $q1");
      assertThat(line.evidence().tableSha256()).isEqualTo("actual-hash");
      s.execute("DELETE FROM spell_text_evidence");
      assertThat(card(c).lines().getFirst().evidence().status()).isEqualTo("UNKNOWN");
    }
  }
}
