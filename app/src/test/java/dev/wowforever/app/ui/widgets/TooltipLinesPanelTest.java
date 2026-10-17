package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.TooltipLine;
import dev.wowforever.app.nav.ViewTarget;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class TooltipLinesPanelTest {
  private static final String LONG_USE = "Use: The Eye of Sulfuras can be combined with the Sulfuron Hammer to create"
      + " Sulfuras, legendary hammer of Ragnaros.";

  @Test
  void anItemCardFromTheDatabaseShowsItsLinesInOrder() {
    var p = new TooltipLinesPanel(FixtureDb.repositories().items().detail(17204).orElseThrow().card().lines());
    assertThat(p.texts()).containsExactly("Eye of Sulfuras", "Binds when picked up", LONG_USE, "Item Level 60",
        "Sell Price: 20g");
    assertThat(p.colours().get(2)).isEqualTo(new Color(0x1EFF00));
  }

  @Test
  void unresolvedSpellVariablesReadAsQuestionMarks() {
    var p = new TooltipLinesPanel(List.of(new TooltipLine("set_bonus", "(6) Set: absorbing $1293733s2 damage. Once"
        + " every ${$proccooldown/60} min, $d left.", "#808080")));
    assertThat(p.texts()).containsExactly("(6) Set: absorbing ? damage. Once every ? min, ? left.");
    assertThat(TooltipLinesPanel.withoutSpellTokens("Costs 5 $ and $s1")).isEqualTo("Costs 5 $ and ?");
    assertThat(TooltipLinesPanel.withoutSpellTokens(null)).isNull();
  }

  @Test
  void aSetPieceWithAKnownItemIsALinkInTheGreyOfTheLine() {
    List<ViewTarget> opened = new ArrayList<>();
    var p = new TooltipLinesPanel(List.of(new TooltipLine("set_name", "Garb (0/2)", "#ffd100"),
        new TooltipLine("set_item", "Bloodvine Vest", "#808080", 19682L),
        new TooltipLine("set_item", "Lost Boots", "#808080")), true, opened::add);
    assertThat(p.texts()).containsExactly("Garb (0/2)", "Bloodvine Vest", "Lost Boots");
    assertThat(p.parts().get(1)).isInstanceOf(JButton.class);
    assertThat(p.parts().get(2)).as("a piece without an item stays text").isNotInstanceOf(JButton.class);
    JButton link = (JButton) p.parts().get(1);
    assertThat(link.getForeground()).isEqualTo(new Color(0x808080));
    assertThat(link.getAccessibleContext().getAccessibleName()).isEqualTo("Bloodvine Vest");
    link.doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(EntityKind.ITEM, 19682)));
    assertThat(new TooltipLinesPanel(List.of(new TooltipLine("set_item", "Bloodvine Vest", "#808080", 19682L)))
        .parts().get(0)).as("no navigator, no link").isNotInstanceOf(JButton.class);
  }

  @Test
  void anItemWithoutClientDataHasNoLines() {
    var p = new TooltipLinesPanel(FixtureDb.repositories().items().detail(1176).orElseThrow().card().lines());
    assertThat(p.texts()).isEmpty();
    assertThat(p.isVisible()).isFalse();
  }

  @Test
  void oneLinePerTooltipLineInTheGivenOrderWithTheLineColour() {
    var p = new TooltipLinesPanel(List.of(
        new TooltipLine("name", "Eye of Sulfuras", "#ff8000"),
        new TooltipLine("use", "Use: Combine it.", "#1eff00"),
        new TooltipLine("item_level", "Item Level 60", "#9d9d9d")));
    assertThat(p.texts()).containsExactly("Eye of Sulfuras", "Use: Combine it.", "Item Level 60");
    assertThat(p.colours()).containsExactly(new Color(0xFF8000), new Color(0x1EFF00), new Color(0x9D9D9D));
    assertThat(p.parts()).extracting(c -> c.getForeground()).containsExactlyElementsOf(p.colours());
  }

  @Test
  void lineWithoutAColourUsesTheDefaultTextColour() {
    var p = new TooltipLinesPanel(List.of(new TooltipLine("description", "\"Flavour.\"", null),
        new TooltipLine("stat", "+1 Stamina", "not-a-colour")));
    assertThat(p.colours()).containsExactly(TooltipLinesPanel.DEFAULT_TEXT, TooltipLinesPanel.DEFAULT_TEXT);
  }

  @Test
  void tabSplitsALineIntoLeftAndRightText() {
    var p = new TooltipLinesPanel(List.of(new TooltipLine("slot", "Chest\tCloth", "#ffffff")));
    assertThat(p.texts()).containsExactly("Chest", "Cloth");
    assertThat(p.colours()).containsOnly(Color.WHITE);
    JLabel left = (JLabel) p.parts().get(0);
    JLabel right = (JLabel) p.parts().get(1);
    assertThat(right.getHorizontalAlignment()).isEqualTo(JLabel.TRAILING);
    BorderLayout row = (BorderLayout) left.getParent().getLayout();
    assertThat(row.getConstraints(left)).as("the left text truncates first").isEqualTo(BorderLayout.CENTER);
    assertThat(row.getConstraints(right)).isEqualTo(BorderLayout.EAST);
  }

  @Test
  void longLinesWrapToTheWidthKeepingTheirColour() {
    var p = new TooltipLinesPanel(List.of(new TooltipLine("use", LONG_USE, "#1eff00")));
    JTextArea use = (JTextArea) p.parts().get(0);
    assertThat(use.getLineWrap()).isTrue();
    assertThat(use.isEditable()).isFalse();
    assertThat(use.isFocusable()).isFalse();
    int oneLine = use.getFontMetrics(use.getFont()).getHeight();
    p.setSize(160, 10);
    assertThat(p.getPreferredSize().height).as("wrapped at 160 px").isGreaterThan(oneLine * 2);
    p.setSize(160, p.getPreferredSize().height);
    p.doLayout();
    assertThat(use.getHeight()).isGreaterThan(oneLine * 2);
    assertThat(use.getWidth()).isLessThanOrEqualTo(160);
    assertThat(use.getForeground()).isEqualTo(new Color(0x1EFF00));
    assertThat(p.naturalWidth()).as("the unwrapped width").isGreaterThan(160);
  }

  @Test
  void noLinesNoParts() {
    assertThat(new TooltipLinesPanel(List.of()).parts()).isEmpty();
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
      assertThat(p.parts().get(0).getForeground()).isEqualTo(new Color(0xFF8000));
      SwingUtilities.updateComponentTreeUI(p);
    }
  }
}
