package dev.wowforever.app.ui.pages;

import static org.junit.jupiter.api.Assertions.*;
import dev.wowforever.app.model.systems.ClientSystems.Catalog;
import dev.wowforever.app.model.systems.ClientSystems.Row;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class LegacyPageTest {
  @Test void absentRecordsExplainCoverageWithoutInventingChallenges() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      LegacyPage page = new LegacyPage(new Catalog("", "", Map.of()));
      assertEquals(4, page.tabs().getTabCount());
      assertEquals("Ruleset set 60", page.tabs().getTitleAt(0));
      assertTrue(descendants(page.tabs().getComponentAt(0), JTable.class).isEmpty());
      assertTrue(descendants(page, JTextArea.class).stream().anyMatch(a -> a.getText().contains("No Legacy challenge records")));

    });
  }

  @Test void nativeRulesetAndRewardFilteringRetainsSourceFields() throws Exception {
    Catalog catalog = new Catalog("1.60.1.test", "snapshot", Map.of(
        "TraitCurrencySource", List.of(row("TraitCurrencySource", 1, Map.of("TraitCurrencyID", "4225", "SuperDistrictSetID", "60", "AchievementID", "10", "Requirement_lang", "Reach a milestone")),
            row("TraitCurrencySource", 2, Map.of("TraitCurrencyID", "4225", "SuperDistrictSetID", "61", "AchievementID", "11")),
            row("TraitCurrencySource", 3, Map.of("TraitCurrencyID", "99", "SuperDistrictSetID", "60"))),
        "Achievement", List.of(row("Achievement", 10, Map.of("Title_lang", "A challenge"))),
        "RenownRewards", List.of(row("RenownRewards", 20, Map.of("CovenantID", "48", "Name_lang", "A reward", "Level", "25", "ItemID", "123")),
            row("RenownRewards", 21, Map.of("CovenantID", "49", "Name_lang", "Other reward")))));
    SwingUtilities.invokeAndWait(() -> {
      LegacyPage page = new LegacyPage(catalog);
      List<JTable> challengeTables = descendants(page.tabs().getComponentAt(0), JTable.class);
      JTable challenges = challengeTables.getFirst();
      assertEquals(1, challenges.getRowCount());
      assertEquals("A challenge", challenges.getValueAt(0, 1));
      assertFalse(challenges.isCellEditable(0, 0));
      JTable fields = challengeTables.get(1);
      assertTrue(java.util.stream.IntStream.range(0, fields.getRowCount())
          .anyMatch(i -> fields.getValueAt(i, 0).equals("Achievement.Title_lang")));
      JTable rewards = descendants(page.tabs().getComponentAt(2), JTable.class).getFirst();
      assertEquals(1, rewards.getRowCount());
      assertEquals("123", rewards.getValueAt(0, 3));
    });
  }

  @Test void nativeRewardToastSuppliesNameBeforeExactCurrentItemFallback() throws Exception {
    Catalog catalog=new Catalog("1.60.1.70450","native-snapshot",Map.of(
        "RenownRewards",List.of(
            row("RenownRewards",1856,Map.of("CovenantID","48","Name_lang","","ToastDescription_lang","Replica Ironforge Air Rifle","ItemID","123")),
            row("RenownRewards",1857,Map.of("CovenantID","48","Name_lang","Authored name","ToastDescription_lang","Other toast","ItemID","123")),
            row("RenownRewards",1858,Map.of("CovenantID","48","Name_lang","","ToastDescription_lang","","ItemID","123")),
            row("RenownRewards",1859,Map.of("CovenantID","48","Name_lang","","ItemID","999"))),
        "ItemSparse",List.of(row("ItemSparse",123,Map.of("Display_lang","Exact current item")))));
    SwingUtilities.invokeAndWait(()->{
      JTable rewards=descendants(new LegacyPage(catalog).tabs().getComponentAt(2),JTable.class).getFirst();
      assertEquals("Replica Ironforge Air Rifle",rewards.getValueAt(0,1));
      assertEquals("Authored name",rewards.getValueAt(1,1));
      assertEquals("Exact current item",rewards.getValueAt(2,1));
      assertEquals("RenownRewards #1859",rewards.getValueAt(3,1));
    });
  }

  private static Row row(String table, long id, Map<String,String> values) {
    return new Row(table, id, values, table + ".csv", 2);
  }
  private static <T> List<T> descendants(Component component, Class<T> type) {
    List<T> result = new ArrayList<>();
    if (type.isInstance(component)) result.add(type.cast(component));
    if (component instanceof Container parent)
      for (Component child : parent.getComponents()) result.addAll(descendants(child, type));
    return result;
  }
}
