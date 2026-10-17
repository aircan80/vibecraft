package dev.wowforever.app.ui.pages;

import static org.junit.jupiter.api.Assertions.*;
import dev.wowforever.app.model.systems.ClientSystems.Catalog;
import dev.wowforever.app.model.systems.ClientSystems.Row;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ClassSpellsPageTest {
  @Test void keepsExplicitClassLinksAndDistinctLevelRecords() throws Exception {
    Catalog catalog = new Catalog("1.60.1.test", "snapshot", Map.of(
        "ChrClasses", List.of(row("ChrClasses", 1, Map.of("Name_lang", "Warrior")), row("ChrClasses", 8, Map.of("Name_lang", "Mage"))),
        "SkillLineAbility", List.of(row("SkillLineAbility", 1, Map.of("Spell", "100", "SkillLine", "6", "ClassMask", "128", "AcquireMethod", "7")),
            row("SkillLineAbility", 2, Map.of("Spell", "101", "SkillLine", "26", "ClassMask", "1")),
            row("SkillLineAbility", 3, Map.of("Spell", "102", "ClassMask", "0"))),
        "SkillLine", List.of(row("SkillLine", 6, Map.of("DisplayName_lang", "Frost"))),
        "SpellName", List.of(row("SpellName", 100, Map.of("Name_lang", "A spell"))),
        "SpellLevels", List.of(row("SpellLevels", 10, Map.of("SpellID", "100", "SpellLevel", "20", "BaseLevel", "18", "DifficultyID", "0")),
            row("SpellLevels", 11, Map.of("SpellID", "100", "SpellLevel", "21", "BaseLevel", "19", "DifficultyID", "1")))));
    SwingUtilities.invokeAndWait(() -> {
      ClassSpellsPage page = new ClassSpellsPage(catalog);
      assertEquals(2, page.classFilter().getItemCount());
      assertEquals("Mage", page.classFilter().getSelectedItem().toString());
      assertEquals(2, page.spellTable().getRowCount());
      assertEquals("A spell", page.spellTable().getValueAt(0, 0));
      assertEquals(20L, page.spellTable().getValueAt(0, 3));
      assertEquals("7", page.spellTable().getValueAt(0, 6));
      assertTrue(page.spellTable().getValueAt(0, 8).toString().contains("SpellLevels.csv:2"));
      assertFalse(page.spellTable().isCellEditable(0, 0));
      page.classFilter().setSelectedIndex(1);
      assertEquals(1, page.spellTable().getRowCount());
      assertNull(page.spellTable().getValueAt(0, 3));
    });
  }
  @Test void absentDataProducesNoInventedClassOrLevel() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      ClassSpellsPage page = new ClassSpellsPage(new Catalog("", "", Map.of()));
      assertEquals(0, page.classFilter().getItemCount());
      assertEquals(0, page.spellTable().getRowCount());
    });
  }
  @Test void comparesExactEraSpellsAndTalentRanksWithoutClaimingBaselineLearning() throws Exception {
    Map<String,List<Row>> current = Map.of(
        "ChrClasses", List.of(row("ChrClasses", 1, Map.of("Name_lang", "Warrior"))),
        "SkillLineAbility", List.of(
            row("SkillLineAbility", 1, Map.of("Spell", "100", "ClassMask", "1", "AcquireMethod", "0")),
            row("SkillLineAbility", 2, Map.of("Spell", "101", "ClassMask", "1")),
            row("SkillLineAbility", 3, Map.of("Spell", "102", "ClassMask", "1"))));
    Catalog catalog = new Catalog("1.60.1.test", "current", current, "1.15.9.test", Map.of(
        "SkillLineAbility", List.of(row("SkillLineAbility", 10, Map.of("Spell", "100"))),
        "Talent", List.of(row("Talent", 11, Map.of("SpellRank_8", "101")))));
    SwingUtilities.invokeAndWait(() -> {
      JTableAssertions.check(new ClassSpellsPage(catalog));
      ClassSpellsPage incomplete = new ClassSpellsPage(new Catalog("current", "s", current, "Era", Map.of("Talent", List.of())));
      assertEquals("Comparison unavailable", incomplete.spellTable().getValueAt(0, 7));
    });
  }
  private static class JTableAssertions {
    static void check(ClassSpellsPage page) {
      assertEquals("Present in Era class spells", page.spellTable().getValueAt(0, 7));
      assertEquals("Was an Era talent", page.spellTable().getValueAt(1, 7));
      assertEquals("New class-linked spell", page.spellTable().getValueAt(2, 7));
      assertTrue(page.spellTable().getValueAt(1, 8).toString().contains("Era Talent.csv:2"));
    }
  }
  private static Row row(String table, long id, Map<String,String> fields) {
    return new Row(table, id, fields, table + ".csv", 2);
  }
}
