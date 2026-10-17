package dev.wowforever.importer.archive.records;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceRecordFieldsTest {
  private static SourceRecords.SourceInput input(String path, String text) {
    return new SourceRecords.SourceInput(path, text.getBytes(StandardCharsets.UTF_8));
  }

  @Test void jsonPreservesHugeIntegersPreciseDecimalsNestedTypesAndCanonicalKeyOrder() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      SourceRecords.capture(c, "a", List.of(input("manifest.json",
          "{\"z\":[null,true,\"9007199254740993\"],\"n\":9007199254740993,\"decimal\":0.123456789012345678901234567890}")));
      String payload = SourceRecords.records(c, "a").getFirst().payload();
      assertThat(payload).isEqualTo("{\"decimal\":0.12345678901234567890123456789,\"n\":9007199254740993,\"z\":[null,true,\"9007199254740993\"]}");
      assertThat(SourceRecords.statuses(c, "a").getFirst().status()).isEqualTo("complete");
    }
  }

  @Test void jsonRejectsDuplicateKeysAndNonstandardNumbersWithoutClaimingStructuredSuccess() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      int i = 0;
      for (String json : List.of("{\"a\":1,\"a\":2}", "01", "-.1", "[1,]", "1.", "{\"x\":true,}")) {
        String id = "s" + (++i);
        SourceRecords.capture(c, id, List.of(input("manifest.json", json)));
        assertThat(SourceRecords.statuses(c, id).getFirst().status()).as(json).isEqualTo("error");
        assertThat(SourceRecords.records(c, id)).isEmpty();
      }
    }
  }

  @Test void lockManifestsAreJsonAndInvalidUnicodeEscapesCannotBeAcceptedByMiniJson() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      SourceRecords.capture(c, "a", List.of(input("build.lock", "{\"build\":70450}")));
      assertThat(SourceRecords.records(c, "a").getFirst().format()).isEqualTo("json");
      SourceRecords.capture(c, "b", List.of(input("x.json", "\"" + "\\" + "u+123\"")));
      assertThat(SourceRecords.statuses(c, "b").getFirst().status()).isEqualTo("error");
      SourceRecords.capture(c, "c", List.of(input("x.json", "\"" + "\\" + "uD800\"")));
      assertThat(SourceRecords.records(c, "c").getFirst().payload()).contains("u" + "d800");
    }
  }

  @Test void jsonFieldPathsEscapeKeysAndDistinguishAbsentNullAndDifferentTypes() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      SourceRecords.capture(c, "a", List.of(input("m.json", "{\"a/b~\":{\"gone\":null,\"v\":[1,2]},\"kind\":1}")));
      SourceRecords.capture(c, "b", List.of(input("m.json", "{\"a/b~\":{\"v\":[1,3],\"new\":null},\"kind\":\"1\"}")));
      var changes = SourceRecords.fieldDiff(c, "a", "b");
      assertThat(changes).extracting(SourceRecords.FieldChange::fieldPath)
          .containsExactly("/a~1b~0/gone", "/a~1b~0/new", "/a~1b~0/v/1", "/kind");
      assertThat(changes.get(0).beforePayload()).isEqualTo("null");
      assertThat(changes.get(0).afterPayload()).isNull();
      assertThat(changes.get(1).beforePayload()).isNull();
      assertThat(changes.get(1).afterPayload()).isEqualTo("null");
      assertThat(changes.getLast().beforePayload()).isEqualTo("1");
      assertThat(changes.getLast().afterPayload()).isEqualTo("\"1\"");
    }
  }

  @Test void csvComparesByHeaderNameInsteadOfPositionAndRetainsDuplicateHeaderOccurrences() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      SourceRecords.capture(c, "a", List.of(input("x.csv", "ID,Name,Extra\n1,A,old\n")));
      SourceRecords.capture(c, "b", List.of(input("x.csv", "Extra,ID,Name\nnew,1,A\n")));
      assertThat(SourceRecords.fieldDiff(c, "a", "b").stream().filter(x -> x.key().equals("ID:1#1")))
          .singleElement().satisfies(x -> assertThat(x.fieldPath()).isEqualTo("/Extra#1"));
      var before = new SourceRecords.Record("x", "row", "csv-json", "[[\"N\",\"N\"],[\"a\",\"b\"]]", "a");
      var after = new SourceRecords.Record("x", "row", "csv-json", "[[\"N\",\"N\"],[\"a\",\"c\"]]", "b");
      assertThat(FieldDiff.compare(before, after)).singleElement()
          .satisfies(x -> assertThat(x.fieldPath()).isEqualTo("/N#2"));
    }
  }

  @Test void luaNestedFieldDiffIgnoresNamedEntryReorderingAndFindsChangedValues() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      SourceRecords.capture(c, "a", List.of(input("q.lua", "return {[1]={alpha=2, beta={value=3}}}")));
      SourceRecords.capture(c, "b", List.of(input("q.lua", "return {[1]={beta={value=4}, alpha=2}}")));
      var changes = SourceRecords.fieldDiff(c, "a", "b");
      assertThat(changes).singleElement().satisfies(x -> {
        assertThat(x.fieldPath()).contains("name:beta#1", "name:value#1");
        assertThat(x.beforePayload()).isEqualTo("3");
        assertThat(x.afterPayload()).isEqualTo("4");
      });
    }
  }

  @Test void priorExtractionGenerationRemainsReadableAndExplicitlySelectable() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      var data = input("x.csv", "ID,Name\n1,A\n");
      SourceRecords.capture(c, "a", List.of(data));
      try (var s = c.createStatement()) {
        s.executeUpdate("UPDATE source_record SET extraction_version=1");
        s.executeUpdate("UPDATE source_record_parse SET extraction_version=1");
      }
      assertThat(SourceRecords.records(c, "a")).hasSize(2);
      SourceRecords.capture(c, "a", List.of(data));
      assertThat(SourceRecords.generations(c, "a")).containsExactly(1, 2);
      assertThat(SourceRecords.records(c, "a", 1)).isEqualTo(SourceRecords.records(c, "a", 2));
      assertThat(SourceRecords.statuses(c, "a", 1)).hasSize(1);
    }
  }

  @Test void fieldDiffDoesNotTurnIncompleteFilesOrNewRecordsIntoFieldRemovals() throws Exception {
    try (var c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      SourceRecords.capture(c, "a", List.of(input("x.json", "{\"a\":1}"), input("q.lua", "return {[1]={v=2}}")));
      SourceRecords.capture(c, "b", List.of(input("x.json", "{bad"), input("q.lua", "if X then z=1 end return {[1]={v=3}}")));
      assertThat(SourceRecords.fieldDiff(c, "a", "b")).isEmpty();
      assertThat(SourceRecords.diff(c, "a", "b")).anySatisfy(x -> assertThat(x.kind()).isEqualTo("unresolved"));
    }
  }
}
