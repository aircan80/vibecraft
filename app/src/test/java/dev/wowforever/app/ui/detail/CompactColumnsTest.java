package dev.wowforever.app.ui.detail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.detail.CompactColumns.Cell;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompactColumnsTest {
  @Test
  void theTooltipSkipsEmptyCellsAndStripsMarkup() {
    assertThat(CompactColumns.tooltip(List.of(new Cell("Level", "21"),
        new Cell("Zone", "<html><u>Westfall</u></html>"), new Cell("Kind", Columns.NONE), new Cell("Note", " "),
        new Cell("#", "7"))))
        .isEqualTo("Level 21 · Zone: Westfall · #7");
    assertThat(CompactColumns.tooltip(List.of(new Cell("Zone", null), new Cell("Kind", "")))).isNull();
  }

  @Test
  void aHiddenRateWhoseSourcesDisagreeKeepsItsMarker() {
    assertThat(CompactColumns.tooltip(List.of(new Cell("Rate", "5.00 %", true), new Cell("Slot", "7", true))))
        .isEqualTo("Rate 5.00 %" + Columns.DISAGREES + " · Slot 7" + Columns.DISAGREES);
    assertThat(CompactColumns.tooltip(List.of(new Cell("Drop %", "5.00 %" + Columns.DISAGREES, true))))
        .as("a text with the marker already gets no second one").isEqualTo("Drop % 5.00 %" + Columns.DISAGREES);
    assertThat(CompactColumns.tooltip(List.of(new Cell("Rate", Columns.NONE, true)))).isNull();
  }

  @Test
  void joinPutsTheCellsOwnTooltipBelowTheRowsOne() {
    assertThat(CompactColumns.join(null, null)).isNull();
    assertThat(CompactColumns.join("Level 21", null)).isEqualTo("Level 21");
    assertThat(CompactColumns.join(null, "Questie & AtlasLoot")).isEqualTo("Questie & AtlasLoot");
    assertThat(CompactColumns.join("Zone: A & B", "Both <sources>"))
        .isEqualTo("<html>Zone: A &amp; B<br>Both &lt;sources&gt;</html>");
    assertThat(CompactColumns.join("Level 21", "<html><b>Full</b> name</html>"))
        .isEqualTo("<html>Level 21<br><b>Full</b> name</html>");
  }

  @Test
  void joinKeepsTheLineBreaksOfPlainParts() {
    // a boss's Vanilla rate: the note keeps its own line in a compact table's tooltip
    assertThat(CompactColumns.join("Item level 23", "a\nb")).isEqualTo("<html>Item level 23<br>a<br>b</html>");
    assertThat(CompactColumns.join("x\ny", "a & b")).isEqualTo("<html>x<br>y<br>a &amp; b</html>");
  }
}
