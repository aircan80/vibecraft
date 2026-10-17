package dev.wowforever.importer;
import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class StatLabelProjectionTest {
  @Test void bytePinnedLabelsChangeOnlyKnownPlaceholdersAndRetainOriginalText()throws Exception {
    byte[] source;try(var input=StatLabelProjectionTest.class.getResourceAsStream("/reference/ItemStatType.dbde")){source=input.readAllBytes();}
    try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
      c.setAutoCommit(false);
      ArchiveWorkflow.initialize(c);
      try(var s=c.createStatement()) {
        s.execute("CREATE TABLE item_tooltip_line(item_id INTEGER,ordinal INTEGER,text TEXT)");
        s.execute("INSERT INTO item_tooltip_line VALUES(1,0,'+5 Stat #92'),(1,1,'+9 Stat #999'),(2,0,'+0 Mana')");
      }
      assertThat(StatLabelProjection.apply(c,source)).isEqualTo(1);
      assertThat(StatLabelProjection.apply(c,source)).isZero();
      try(var s=c.createStatement();var r=s.executeQuery("SELECT text FROM item_tooltip_line ORDER BY item_id,ordinal")) {
        List<String> text=new ArrayList<>();while(r.next())text.add(r.getString(1));
        assertThat(text).containsExactly("+5 Two-handed Swords","+9 Stat #999","+0 Mana");
      }
      try(var s=c.createStatement();var r=s.executeQuery("SELECT previous_text,replacement_text FROM stat_label_previous")) {
        assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("+5 Stat #92");assertThat(r.getString(2)).isEqualTo("+5 Two-handed Swords");assertThat(r.next()).isFalse();
      }
    }
  }
  @Test void unreviewedEnumCannotChangeAnyTooltip()throws Exception {
    try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
      assertThatThrownBy(()->StatLabelProjection.apply(c,"92 WRONG".getBytes())).isInstanceOf(IllegalArgumentException.class);
      try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM sqlite_master")){r.next();assertThat(r.getInt(1)).isZero();}
    }
  }
}
