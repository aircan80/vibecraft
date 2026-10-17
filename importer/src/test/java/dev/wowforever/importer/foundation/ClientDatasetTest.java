package dev.wowforever.importer.foundation;

import dev.wowforever.importer.archive.SourceArchive;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ClientDatasetTest {
  @Test void compositeNativeKeysAreUnambiguousAndQuotedMultilineCellsKeepLineEvidence()throws Exception {
    byte[] bytes="RaceID,ClassID,Description\n95,1,\"zero, and\nnewline\"\n96,7,\n".getBytes(StandardCharsets.UTF_8);
    try(var c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
      c.setAutoCommit(false);
      ClientDataset.project(c,"snapshot","wow_classic_beta","1.60.1.70450","enUS",
          List.of(new ClientDataset.Table("CharBaseInfo","CharBaseInfo.csv",ClientDataset.sha256(bytes),List.of("RaceID","ClassID"))),
          List.of(new SourceArchive.Artifact("CharBaseInfo.csv","",bytes)));
      try(var s=c.createStatement();var r=s.executeQuery("SELECT row_id,source_line,json_extract(payload,'$.Description') FROM client_table_row ORDER BY row_id")) {
        assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("[\"95\",\"1\"]");assertThat(r.getInt(2)).isEqualTo(2);assertThat(r.getString(3)).isEqualTo("zero, and\nnewline");
        assertThat(r.next()).isTrue();assertThat(r.getInt(2)).isEqualTo(4);assertThat(r.getString(3)).isEmpty();
      }
    }
  }
}
