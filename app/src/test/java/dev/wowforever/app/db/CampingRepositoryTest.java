package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CampingRepositoryTest {
  @TempDir Path temp;
  @Test void legacyDatabaseDoesNotInventCampingFacts() throws Exception {
    try(var db=Database.open(FixtureDb.path())) {
      var data=new CampingRepository(db).load();
      assertThat(new CampingRepository(db).load()).isSameAs(data);
      assertThat(data.camps()).isEmpty();assertThat(data.blueprints()).isEmpty();
      assertThat(data.coverageNote()).contains("does not establish","stacking");
    }
  }
  @Test void scopesEffectsAndKeepsAbsentRequirementsUnknown() throws Exception {
    Path file=temp.resolve("camp.sqlite");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("CREATE TABLE client_table_dataset(snapshot_id TEXT,build TEXT)");
      s.execute("CREATE TABLE client_table_row(snapshot_id TEXT,table_name TEXT,row_id TEXT,payload TEXT,source_path TEXT,source_line INTEGER)");
      s.execute("INSERT INTO client_table_dataset VALUES('source','1.60.1')");
      s.execute("INSERT INTO client_table_row VALUES('source','SpellEffect','1','{\"SpellID\":\"1307172\",\"Effect\":\"50\",\"EffectMiscValue_0\":\"123\"}','SpellEffect.csv',2)");
      s.execute("INSERT INTO client_table_row VALUES('source','SpellEffect','2','{\"SpellID\":\"1\",\"Effect\":\"50\"}','SpellEffect.csv',3)");
      s.execute("INSERT INTO client_table_row VALUES('source','SpellName','1307172','{\"Name_lang\":\"Example camp\"}','SpellName.csv',2)");
      s.execute("INSERT INTO client_table_row VALUES('source','ItemSparse','22','{\"Display_lang\":\"Blueprint: Example\",\"RequiredSkillRank\":\"75\"}','ItemSparse.csv',2)");
      s.execute("INSERT INTO client_table_row VALUES('source','SpellEffect','3','{\"SpellID\":\"1307172\",\"Effect\":\"50\",\"EffectMiscValue_0\":\"456\"}','SpellEffect.csv',4)");
      s.execute("INSERT INTO client_table_row VALUES('source','Spell','1307172','{\"NameSubtext_lang\":\"Tier 3\"}','Spell.csv',2)");
      s.execute("INSERT INTO client_table_row VALUES('source','ItemEffect','101','{\"SpellID\":\"1307172\"}','ItemEffect.csv',2)");
      s.execute("INSERT INTO client_table_row VALUES('source','ItemXItemEffect','102','{\"ItemEffectID\":\"101\",\"ItemID\":\"103\"}','ItemXItemEffect.csv',2)");
      s.execute("INSERT INTO client_table_row VALUES('source','ItemSparse','103','{\"RequiredSkill\":\"171\",\"RequiredSkillRank\":\"300\"}','ItemSparse.csv',3)");
      s.execute("INSERT INTO client_table_row VALUES('source','SkillLine','171','{\"DisplayName_lang\":\"Alchemy\"}','SkillLine.csv',2)");
    }
    try(var db=Database.open(file)) {
      var data=new CampingRepository(db).load();
      assertThat(new CampingRepository(db).load()).isSameAs(data);
      assertThat(data.camps()).hasSize(1);assertThat(data.camps().getFirst().name()).isEqualTo("Example camp");
      assertThat(data.camps().getFirst().objectId()).isEqualTo(123);
      assertThat(data.blueprints().getFirst().requiredRank()).isEqualTo(75);
      assertThat(data.blueprints().getFirst().requiredSkill()).isNull();
      assertThat(data.missingTables()).isEmpty();
      assertThat(data.camps().getFirst().profession()).isEqualTo("Alchemy");
      assertThat(data.camps().getFirst().requiredRank()).isEqualTo(300);
      assertThat(data.camps().getFirst().tier()).isEqualTo("Tier 3");
    }
  }
}
