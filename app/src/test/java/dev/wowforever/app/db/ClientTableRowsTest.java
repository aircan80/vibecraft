package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ClientTableRowsTest {
  @Test void decodedNativeCellsMatchSqliteWithoutLosingEmptyZeroNullOrEscapes() throws Exception {
    String payload="{\"ID\":\"0\",\"Empty\":\"\",\"MissingValue\":null,\"Description\":\"An \\\"escaped\\\" line\\nwith \\u00e9 and \\\\ paths\",\"Numeric\":0,\"Real\":2.5,\"Enabled\":true,\"Disabled\":false}";
    Map<String,String> expected=new LinkedHashMap<>();
    try(var c=DriverManager.getConnection("jdbc:sqlite::memory:");var ps=c.prepareStatement("SELECT key,value FROM json_each(?)")) {
      ps.setString(1,payload);try(var rs=ps.executeQuery()){while(rs.next())expected.put(rs.getString(1),rs.getString(2));}
    }
    var actual=ClientTableRows.decodePayload(payload);
    assertThat(actual).containsExactlyEntriesOf(expected);
    assertThat(actual).containsEntry("ID","0").containsEntry("Empty","").containsEntry("MissingValue",null).doesNotContainKey("Absent");
    var row=new ClientTableRows.Row("0",actual,"native.csv",3);
    assertThat(row.number("ID")).isZero();assertThat(row.number("Empty")).isNull();assertThat(row.number("MissingValue")).isNull();
    assertThatThrownBy(()->row.cells().put("ID","1")).isInstanceOf(UnsupportedOperationException.class);
  }
  @Test void nativeRowsRetainSourceOrderAndOnlyRequestedSnapshotAndTable() throws Exception {
    try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
      c.createStatement().execute("CREATE TABLE client_table_row(snapshot_id TEXT,table_name TEXT,row_id TEXT,payload TEXT,source_path TEXT,source_line INTEGER,PRIMARY KEY(snapshot_id,table_name,row_id))");
      try(var ps=c.prepareStatement("INSERT INTO client_table_row VALUES(?,?,?,?,?,?)")){
        insert(ps,"current","Item","1","{\"ID\":\"1\",\"Name\":\"Later\"}","Item.70450.csv",20);
        insert(ps,"current","Item","2","{\"ID\":\"2\",\"Name\":\"Earlier\"}","Item.70450.csv",10);
        insert(ps,"era","Item","3","{\"ID\":\"3\"}","Item.70003.csv",2);
        insert(ps,"current","Spell","4","{\"ID\":\"4\"}","Spell.70450.csv",2);
      }
      var rows=ClientTableRows.read(c,"current","Item");assertThat(rows).extracting(ClientTableRows.Row::id).containsExactly("2","1");
      assertThat(rows.getFirst().sourcePath()).isEqualTo("Item.70450.csv");assertThat(rows.getFirst().sourceLine()).isEqualTo(10);
      assertThat(rows.getFirst().cells()).containsEntry("ID","2").containsEntry("Name","Earlier");
      assertThat(new ArrayList<>(rows.getFirst().cells().keySet())).containsExactly("ID","Name");
    }
  }
  @Test void malformedNativePayloadFailsRatherThanFabricatingCells(){
    assertThatThrownBy(()->ClientTableRows.decodePayload("{\"ID\":")).isInstanceOf(SQLException.class);
    assertThatThrownBy(()->ClientTableRows.decodePayload("{\"ID\":\"1\"} trailing")).isInstanceOf(SQLException.class);
    assertThatThrownBy(()->ClientTableRows.decodePayload("[]")).isInstanceOf(SQLException.class);
  }
  private static void insert(PreparedStatement ps,String snapshot,String table,String id,String payload,String path,long line)throws SQLException{
    ps.setString(1,snapshot);ps.setString(2,table);ps.setString(3,id);ps.setString(4,payload);ps.setString(5,path);ps.setLong(6,line);ps.executeUpdate();
  }
}
