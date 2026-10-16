package dev.wowforever.app.ui.detail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.TooltipLine;
import java.awt.Color;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import org.junit.jupiter.api.Test;

class TooltipLinesPanelTest {

  @Test
  void oneLabelPerLineInTheGivenOrderWithTheLineColour() {
    var p = new TooltipLinesPanel(List.of(
        new TooltipLine("name", "Eye of Sulfuras", "#ff8000"),
        new TooltipLine("use", "Use: Combine it.", "#1eff00"),
        new TooltipLine("item_level", "Item Level 60", "#9d9d9d")));
    assertThat(p.labels()).extracting(JLabel::getText)
        .containsExactly("Eye of Sulfuras", "Use: Combine it.", "Item Level 60");
    assertThat(p.labels()).extracting(JLabel::getForeground)
        .containsExactly(new Color(0xFF8000), new Color(0x1EFF00), new Color(0x9D9D9D));
  }

  @Test
  void lineWithoutAColourUsesTheDefaultTextColour() {
    var p = new TooltipLinesPanel(List.of(new TooltipLine("description", "\"Flavour.\"", null),
        new TooltipLine("stat", "+1 Stamina", "not-a-colour")));
    assertThat(p.labels()).extracting(JLabel::getForeground)
        .containsExactly(TooltipLinesPanel.DEFAULT_TEXT, TooltipLinesPanel.DEFAULT_TEXT);
  }

  @Test
  void tabSplitsALineIntoLeftAndRightText() {
    var p = new TooltipLinesPanel(List.of(new TooltipLine("slot", "Chest\tCloth", "#ffffff")));
    assertThat(p.labels()).extracting(JLabel::getText).containsExactly("Chest", "Cloth");
    assertThat(p.labels()).extracting(JLabel::getForeground).containsOnly(Color.WHITE);
    assertThat(p.labels().get(1).getHorizontalAlignment()).isEqualTo(JLabel.TRAILING);
  }

  @Test
  void noLinesNoLabels() {
    assertThat(new TooltipLinesPanel(List.of()).labels()).isEmpty();
  }

  @Test
  void cardUsesTheDarkCardTokensOnEitherTheme() {
    var p = new TooltipLinesPanel(List.of(new TooltipLine("name", "Eye of Sulfuras", "#ff8000")));
    assertThat(TooltipLinesPanel.BACKGROUND).isEqualTo(new Color(0x0C1424));
    assertThat(TooltipLinesPanel.BORDER).isEqualTo(new Color(0x46587A));
    for (int pass = 0; pass < 2; pass++) { // a theme switch re-installs UI defaults; the card keeps its tokens
      assertThat(p.isOpaque()).isTrue();
      assertThat(p.getBackground()).isEqualTo(TooltipLinesPanel.BACKGROUND);
      assertThat(p.getBorder()).isInstanceOfSatisfying(CompoundBorder.class,
          b -> assertThat(b.getOutsideBorder()).isInstanceOfSatisfying(LineBorder.class,
              line -> assertThat(line.getLineColor()).isEqualTo(TooltipLinesPanel.BORDER)));
      assertThat(p.labels().get(0).getForeground()).isEqualTo(new Color(0xFF8000));
      SwingUtilities.updateComponentTreeUI(p);
    }
  }
}
