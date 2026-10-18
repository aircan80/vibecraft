package dev.wowforever.importer.sqlite;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.importer.clientdata.SpellTextEvidence;
import java.sql.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpellTextEvidenceWriterTest {
  @Test void updatesDeleteOnlyItemEvidenceAndRollbackRestoresExactEvidence()throws Exception {
    try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
      SpellTextEvidenceWriter.ensure(c);
      var evidence=new SpellTextEvidence("INCOMPLETE",42,"Restores $q1",List.of("$q1"),"csv/Spell.csv","wow_classic_beta","1.60.1.70450","hash");
      for(String kind:List.of("effect","set_bonus","tooltip")) SpellTextEvidenceWriter.write(c,kind,1,0,evidence);
      c.setAutoCommit(false);SpellTextEvidenceWriter.deleteItem(c,1);
      try(var s=c.createStatement();var row=s.executeQuery("SELECT owner_kind FROM spell_text_evidence")) {assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("set_bonus");assertThat(row.next()).isFalse();}
      c.rollback();
      try(var s=c.createStatement();var row=s.executeQuery("SELECT count(*) FROM spell_text_evidence WHERE raw_text='Restores $q1' AND status='INCOMPLETE' AND source='csv/Spell.csv'")) {row.next();assertThat(row.getInt(1)).isEqualTo(3);}
    }
  }
}
