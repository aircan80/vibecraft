package dev.wowforever.importer.archive.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceRecordsTest {
  private static SourceRecords.SourceInput input(String path, String text) {
    return new SourceRecords.SourceInput(path, text.getBytes(StandardCharsets.UTF_8));
  }

  @Test void csvPreservesEveryColumnDuplicateIdBlankAndQuotedNewline() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      var data = input("items.csv", "ID,Unknown,Blank\n1,\"a,b\nline\",\n1,new,tail\n");
      SourceRecords.capture(c, "a", List.of(data));
      SourceRecords.capture(c, "a", List.of(data));
      var rows = SourceRecords.records(c, "a");
      assertThat(rows).hasSize(3);
      assertThat(rows).extracting(SourceRecords.Record::key).contains("ID:1#1", "ID:1#2", "@header");
      assertThat(rows).anySatisfy(r -> assertThat(r.payload()).contains("Unknown", "a,b\\nline", "\"\""));
      assertThatThrownBy(() -> SourceRecords.capture(c, "a", List.of(input("items.csv", "ID,Unknown,Blank\n2,x,y\n"))))
          .hasMessageContaining("immutable");
    }
  }

  @Test void luaPreservesUnknownKeysNestedExpressionsAndEmbeddedQuestieWithoutEvaluation() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      SourceRecords.capture(c, "a", List.of(input("quests.lua",
          "QuestieDB.questData = [[return {[123] = {[99] = {phase=3, unknown=Some.VALUE}, [\"future\"] = f(4)}}]]")));
      var rows = SourceRecords.records(c, "a");
      assertThat(rows).hasSize(1);
      assertThat(rows.getFirst().payload()).contains("99", "phase", "unknown", "Some", "future", "Call");
      assertThat(SourceRecords.statuses(c, "a").getFirst().status()).isEqualTo("complete");
    }
  }

  @Test void recordDiffFindsActualEntityChangesWithoutFlaggingOtherRowsOrLineShifts() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      SourceRecords.capture(c, "a", List.of(input("q.lua", "return {[1]={name='a'},[2]={name='b'}}")));
      SourceRecords.capture(c, "b", List.of(input("q.lua", "\nreturn {[1]={name='changed'},[2]={name='b'},[3]={new=true}}")));
      assertThat(SourceRecords.diff(c, "a", "b")).extracting(SourceRecords.Change::kind)
          .containsExactly("changed", "added");
    }
  }

  @Test void unsupportedAndMalformedFilesHaveExplicitStatusAndCannotProveRemoval() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      SourceRecords.capture(c, "a", List.of(input("q.csv", "ID,Name\n1,A\n")));
      SourceRecords.capture(c, "b", List.of(input("q.csv", "ID,Name\n1,\"bad"), input("hotfix.bin", "binary")));
      assertThat(SourceRecords.statuses(c, "b")).extracting(SourceRecords.ParseStatus::status)
          .containsExactly("unsupported", "error");
      assertThat(SourceRecords.diff(c, "a", "b")).extracting(SourceRecords.Change::kind).containsOnly("unresolved");
    }
  }

  @Test void localTablesAreSplitWithoutLosingExtraValuesAndInvalidUtf8IsNotSilentlyDecoded() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      SourceRecords.capture(c, "a", List.of(input("local.lua", "local data = {[1]={a=2},[2]={a=3}}"),
          new SourceRecords.SourceInput("invalid.csv", new byte[] {(byte) 0xff})));
      assertThat(SourceRecords.records(c, "a")).hasSize(2);
      assertThat(SourceRecords.statuses(c, "a")).anySatisfy(p -> {
        assertThat(p.path()).isEqualTo("invalid.csv");
        assertThat(p.status()).isEqualTo("error");
      });
    }
  }

  @Test void nonfiniteNumericLiteralCannotCreateInvalidJsonWithCompleteStatus() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      SourceRecords.capture(c, "a", List.of(input("q.lua", "return {[1]={value=1e999}}")));
      assertThat(SourceRecords.records(c, "a")).isEmpty();
      assertThat(SourceRecords.statuses(c, "a").getFirst().status()).isEqualTo("error");
    }
  }

  @Test void skippedControlFlowIsPartialButKnownSyntaxRetained() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      SourceRecords.capture(c, "a", List.of(input("q.lua", "if FLAG then x=1 end\nreturn {[7]={unknown=false}}")));
      assertThat(SourceRecords.statuses(c, "a").getFirst().status()).isEqualTo("partial");
      assertThat(SourceRecords.records(c, "a")).anySatisfy(r -> assertThat(r.payload()).contains("unknown"));
    }
  }
}
