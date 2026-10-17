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
        new Cell("Slot", "7"))))
        .isEqualTo("Level 21 · Zone: Westfall · Slot 7");
    assertThat(CompactColumns.tooltip(List.of(new Cell("Zone", null), new Cell("Kind", "")))).isNull();
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
}
