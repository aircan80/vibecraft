package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RaceRepositoryTest {
  @TempDir Path temp;
  @Test void retainsUnknownClassicAndDoesNotInventStartingZone() throws Exception {
    Path file=temp.resolve("races.sqlite");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()){
      s.execute("create table client_table_dataset(snapshot_id text,product text,build text,locale text)");
      s.execute("create table client_table_row(snapshot_id text,table_name text,row_id text,payload text,source_path text,source_line integer)");
      s.execute("insert into client_table_dataset values('beta','wow_classic_beta','1.60.1.70450','enUS')");
      s.execute("insert into client_table_row values('beta','ChrRaces','95','{\"ID\":\"95\",\"Name_lang\":\"Skyborne\"}','ChrRaces.csv',2)");
      s.execute("insert into client_table_row values('beta','CharBaseInfo','1','{\"ID\":\"1\",\"RaceID\":\"95\",\"ClassID\":\"1\"}','CharBaseInfo.csv',2)");
      s.execute("insert into client_table_row values('beta','ChrRaceRacialAbility','694','{\"ID\":\"694\",\"ChrRacesID\":\"95\",\"Name_lang\":\"Skyborne - Alliance - Active - Walk on Air\",\"Description_lang\":\"Glide downward for 10 sec\"}','ChrRaceRacialAbility.csv',2)");
      s.execute("insert into client_table_row values('beta','ChrClasses','1','{\"ID\":\"1\",\"Name_lang\":\"Warrior\"}','ChrClasses.csv',2)");
    }
    try(var db=Database.open(file)){
      var catalog=new RaceRepository(db).load();assertThat(new RaceRepository(db).load()).isSameAs(catalog);assertThat(catalog.build()).isEqualTo("1.60.1.70450");
      assertThat(catalog.races()).hasSize(1);var race=catalog.races().getFirst();assertThat(race.startZone()).isEmpty();
      assertThat(race.racials()).hasSize(1); assertThat(race.racials().getFirst().kind()).isEqualTo("Active"); assertThat(race.racials().getFirst().description()).isEqualTo("Glide downward for 10 sec");
      assertThat(race.classes().getFirst().classicAvailable()).isNull();assertThat(race.classes().getFirst().name()).isEqualTo("Warrior");
    }
  }
  @Test void distinguishesAbsentClassicComboAndExcludesUnplayableRaces() throws Exception {
    Path file=temp.resolve("compare.sqlite");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()){
      s.execute("create table client_table_dataset(snapshot_id text,product text,build text,locale text)");
      s.execute("create table client_table_row(snapshot_id text,table_name text,row_id text,payload text,source_path text,source_line integer)");
      s.execute("insert into client_table_dataset values('beta','wow_classic_beta','1.60.1.70450','enUS'),('era','wow_classic_era','1.15.8.70003','enUS')");
      s.execute("insert into client_table_row values('beta','ChrRaces','95','{\"ID\":\"95\",\"Name_lang\":\"Skyborne\",\"Flags\":\"128\"}','ChrRaces.csv',2),('beta','ChrRaces','9','{\"ID\":\"9\",\"Name_lang\":\"Goblin\",\"Flags\":\"1\"}','ChrRaces.csv',3)");
      s.execute("insert into client_table_row values('beta','CharBaseInfo','1','{\"ID\":\"1\",\"RaceID\":\"95\",\"ClassID\":\"1\"}','CharBaseInfo.csv',2),('beta','CharBaseInfo','2','{\"ID\":\"2\",\"RaceID\":\"9\",\"ClassID\":\"1\"}','CharBaseInfo.csv',3),('era','CharBaseInfo','1','{\"ID\":\"1\",\"RaceID\":\"1\",\"ClassID\":\"1\"}','CharBaseInfo.csv',2)");
    }
    try(var db=Database.open(file)){
      var catalog=new RaceRepository(db).load();assertThat(catalog.races()).extracting(RaceRepository.Race::id).containsExactly(95);
      assertThat(catalog.classicBuild()).isEqualTo("1.15.8.70003");
      assertThat(catalog.races().getFirst().classes().getFirst().classicAvailable()).isFalse();
    }
  }
  @Test void oldDatasetHasExplicitEmptyCatalogue() throws Exception {
    Path file=temp.resolve("old.sqlite");try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()){s.execute("create table meta(key text,value text)");}
    try(var db=Database.open(file)){assertThat(new RaceRepository(db).load().races()).isEmpty();}
  }
}
