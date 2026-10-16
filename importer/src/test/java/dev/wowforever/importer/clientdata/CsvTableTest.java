package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wowforever.importer.Fixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvTableTest {

  @TempDir Path tmp;

  private Path write(String name, String content) throws IOException {
    Path p = tmp.resolve(name);
    Files.write(p, content.getBytes(StandardCharsets.UTF_8));
    return p;
  }

  private static List<CsvTable.Row> rows(CsvTable t) {
    List<CsvTable.Row> out = new ArrayList<>();
    t.rows().forEach(out::add);
    return out;
  }

  @Test
  void quotedCellsWithCommasNewlinesAndEscapedQuotes() throws IOException {
    CsvTable t = CsvTable.read(write("Q.csv", "A,B,C\na,\"x, \"\"y\"\"\nz\",3\n\"\",,\"\"\"\"\n"));

    assertThat(t.columns()).containsExactly("A", "B", "C");
    List<CsvTable.Row> rows = rows(t);
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).get("A")).isEqualTo("a");
    assertThat(rows.get(0).get("B")).isEqualTo("x, \"y\"\nz");
    assertThat(rows.get(0).get("C")).isEqualTo("3");
    // the embedded newline does not start a new record; empty and quoted-empty cells are ""
    assertThat(rows.get(1).get("A")).isEmpty();
    assertThat(rows.get(1).get("B")).isEmpty();
    assertThat(rows.get(1).get("C")).isEqualTo("\"");
    assertThat(rows.get(1).line()).isEqualTo(4);
  }

  @Test
  void bomAndCrlfAreTolerated() throws IOException {
    CsvTable t = CsvTable.read(write("B.csv", "﻿ID,Name\r\n1,Alpha\r\n2,\"Be\r\nta\"\r\n3,Gamma"));

    assertThat(t.columns()).containsExactly("ID", "Name");
    assertThat(t.column("ID")).isZero();
    List<CsvTable.Row> rows = rows(t);
    assertThat(rows).extracting(r -> r.get("Name")).containsExactly("Alpha", "Be\r\nta", "Gamma");
    assertThat(t.size()).isEqualTo(3);
  }

  @Test
  void unknownColumnNamesTheFile() throws IOException {
    CsvTable t = CsvTable.read(write("Thing.csv", "ID\n1\n"));

    assertThatThrownBy(() -> t.column("Nope"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Thing.csv")
        .hasMessageContaining("Nope");
    CsvTable.Row row = rows(t).get(0);
    assertThatThrownBy(() -> row.get("Nope"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Thing.csv")
        .hasMessageContaining("Nope");
  }

  @Test
  void malformedInputNamesFileAndLine() throws IOException {
    Path shortRow = write("Short.csv", "A,B\n1,2\n3\n");
    assertThatThrownBy(() -> CsvTable.read(shortRow))
        .hasMessageContaining("Short.csv:3")
        .hasMessageContaining("expected 2 fields, got 1");

    Path open = write("Open.csv", "A,B\n1,\"never closed\n2,3\n");
    assertThatThrownBy(() -> CsvTable.read(open))
        .hasMessageContaining("Open.csv:2")
        .hasMessageContaining("unterminated");

    Path junk = write("Junk.csv", "A,B\n\"x\"y,2\n");
    assertThatThrownBy(() -> CsvTable.read(junk))
        .hasMessageContaining("Junk.csv:2")
        .hasMessageContaining("after closing quote");

    Path dup = write("Dup.csv", "A,A\n1,2\n");
    assertThatThrownBy(() -> CsvTable.read(dup)).hasMessageContaining("Dup.csv").hasMessageContaining("duplicate column A");

    assertThatThrownBy(() -> CsvTable.read(tmp.resolve("Missing.csv")))
        .hasMessageContaining("Missing.csv");
  }

  @Test
  void typedGettersReturnEmptyForEmptyCells() throws IOException {
    CsvTable t = CsvTable.read(write("T.csv", "ID,N,F,G\n7,-3,1.5,\n8,,,x\n"));
    List<CsvTable.Row> rows = rows(t);
    CsvTable.Row a = rows.get(0);
    CsvTable.Row b = rows.get(1);

    assertThat(a.getLong("ID")).isEqualTo(OptionalLong.of(7));
    assertThat(a.getInt("N")).isEqualTo(OptionalInt.of(-3));
    assertThat(a.getDouble("F")).isEqualTo(OptionalDouble.of(1.5));
    assertThat(a.getInt("G")).isEmpty();
    assertThat(b.getInt("N")).isEmpty();
    assertThat(b.getLong("N")).isEmpty();
    assertThat(b.getDouble("F")).isEmpty();
    assertThat(b.intOr("N", 42)).isEqualTo(42);
    assertThat(a.longOr("ID", 0)).isEqualTo(7);
    assertThat(b.doubleOr("F", 0.25)).isEqualTo(0.25);
    assertThat(a.get(t.column("F"))).isEqualTo("1.5");
    assertThatThrownBy(() -> b.getInt("G"))
        .isInstanceOf(NumberFormatException.class)
        .hasMessageContaining("T.csv:3")
        .hasMessageContaining("G");
  }

  @Test
  void indexAndGroupByLong() throws IOException {
    CsvTable t = CsvTable.read(write("G.csv", "ID,Parent\n1,0\n2,1\n3,1\n4,\n"));

    Map<Long, CsvTable.Row> byId = t.indexByLong("ID");
    assertThat(byId).containsOnlyKeys(1L, 2L, 3L, 4L);
    assertThat(byId.get(3L).get("Parent")).isEqualTo("1");

    Map<Long, List<CsvTable.Row>> byParent = t.groupByLong("Parent");
    assertThat(byParent).containsOnlyKeys(0L, 1L); // rows with an empty key are left out
    assertThat(byParent.get(1L)).extracting(r -> r.get("ID")).containsExactly("2", "3");

    assertThatThrownBy(() -> t.indexByLong("Parent"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("G.csv")
        .hasMessageContaining("duplicate Parent 1");
  }

  @Test
  void itemSparseFixtureLoadsExpectedColumns() {
    CsvTable t = CsvTable.read(Fixtures.clientDataRoot().resolve("csv/ItemSparse.csv"));

    assertThat(t.columns()).contains("ID", "ItemLevel", "OverallQualityID", "StatModifier_bonusStat_0",
        "StatPercentEditor_0", "Display_lang", "Description_lang");
    CsvTable.Row eye = t.indexByLong("ID").get(17204L);
    assertThat(eye.get("Display_lang")).isEqualTo("Eye of Sulfuras");
    assertThat(eye.getInt("OverallQualityID")).isEqualTo(OptionalInt.of(5));
    assertThat(eye.getInt("ItemLevel")).isEqualTo(OptionalInt.of(60));
  }

  @Test
  void realCrlfInsideAQuotedDescriptionKeepsColumnsAligned() {
    CsvTable t = CsvTable.read(Fixtures.clientDataRoot().resolve("csv/ItemSparse.csv"));
    CsvTable.Row formula = t.indexByLong("ID").get(215129L);

    assertThat(formula.get("Description_lang"))
        .startsWith("Permanently enchant a Weapon")
        .endsWith("mechanical creatures.\r\n");
    assertThat(formula.get("Display_lang")).isEqualTo("Formula: Enchant Weapon - Dismantle");
    assertThat(formula.get("AmmunitionType")).isEqualTo("0"); // last column still in place
  }

  @Test
  void quotedNewlinesInRealSpellTextKeepColumnsAligned() {
    CsvTable spells = CsvTable.read(Fixtures.clientDataRoot().resolve("csv/Spell.csv"));
    Map<Long, CsvTable.Row> byId = spells.indexByLong("ID");
    assertThat(byId.get(21992L).get("Description_lang")).startsWith("Blasts your enemy with lightning");
    assertThat(byId.get(430L).get("AuraDescription_lang")).isEqualTo("Restores $/5;s1 mana per second.");
  }
}
