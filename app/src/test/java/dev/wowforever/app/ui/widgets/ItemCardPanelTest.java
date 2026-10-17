package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.model.ItemCard;
import dev.wowforever.app.model.TooltipLine;
import dev.wowforever.app.ui.theme.QualityColors;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class ItemCardPanelTest {
  private static final ImageIcon ICON = new ImageIcon(new BufferedImage(56, 56, BufferedImage.TYPE_INT_ARGB));

  private static ItemCard card(long item) {
    return FixtureDb.repositories().items().detail(item).orElseThrow().card();
  }

  /** Lays {@code card} out at {@code width} as its parent would: at the height it asks for at that width. */
  private static void layOut(ItemCardPanel card, int width) {
    card.setSize(width, 10);
    card.setSize(width, card.getPreferredSize().height);
    card.doLayout();
    card.lines().doLayout();
  }

  @Test
  void theNameIsBesideTheIconThenTheLinesInOrderWithTheirColours() {
    var card = new ItemCardPanel(card(17204), "Eye of Sulfuras", ICON);
    assertThat(card.name().getText()).isEqualTo("Eye of Sulfuras");
    assertThat(card.name().getForeground()).as("the canonical legendary orange")
        .isEqualTo(QualityColors.canonical(5));
    assertThat(card.lines().texts()).as("the name line is the title, not repeated").containsExactly(
        "Binds when picked up",
        "Use: The Eye of Sulfuras can be combined with the Sulfuron Hammer to create Sulfuras, legendary hammer"
            + " of Ragnaros.",
        "Item Level 60", "Sell Price: 20g");
    List<TooltipLine> stored = card(17204).lines().subList(1, 5);
    assertThat(card.lines().colours()).containsExactlyElementsOf(stored.stream()
        .map(l -> l.colour() == null ? TooltipLinesPanel.DEFAULT_TEXT
            : new Color(Integer.parseInt(l.colour().substring(1), 16)))
        .toList());
    assertThat(card.lines().colours().get(1)).isEqualTo(new Color(0x1EFF00));
    layOut(card, 360);
    assertThat(card.icon().getX()).isLessThan(card.name().getX());
    assertThat(card.icon().getY()).isEqualTo(card.name().getY());
    assertThat(card.lines().getY()).isGreaterThanOrEqualTo(card.name().getY() + card.name().getHeight());
    assertThat(card.lines().getX()).isEqualTo(card.name().getX());
  }

  @Test
  void theIconIsShownAt56Pixels() {
    var card = new ItemCardPanel(card(17204), "Eye of Sulfuras", ICON);
    assertThat(card.icon().getIcon()).isSameAs(ICON);
    layOut(card, 360);
    assertThat(card.icon().getWidth()).isEqualTo(56);
    assertThat(card.icon().getHeight()).isEqualTo(56);
    assertThat(card.getHeight()).isGreaterThan(56);
  }

  @Test
  void slotLinesSplitIntoLeftAndRight() {
    ItemCard vest = card(19682);
    TooltipLine slot = vest.lines().stream().filter(l -> l.kind().equals("slot")).findFirst().orElseThrow();
    assertThat(slot.text()).contains("\t");
    var card = new ItemCardPanel(vest, "Bloodvine Vest", ICON);
    String[] halves = slot.text().split("\t");
    List<String> texts = card.lines().texts();
    int left = texts.indexOf(halves[0]);
    assertThat(left).isNotNegative();
    assertThat(texts.get(left + 1)).isEqualTo(halves[1]);
    JLabel right = (JLabel) card.lines().parts().get(left + 1);
    assertThat(right.getHorizontalAlignment()).isEqualTo(JLabel.TRAILING);
    layOut(card, 360);
    right.getParent().doLayout();
    assertThat(right.getX() + right.getWidth()).as("right text at the right edge")
        .isEqualTo(card.lines().getWidth());
  }

  @Test
  void longLinesWrapInsideTheCardAndTheCardKeepsToItsMaximumWidth() {
    var card = new ItemCardPanel(card(17204), "Eye of Sulfuras", ICON);
    assertThat(card.getPreferredSize().width).isLessThanOrEqualTo(ItemCardPanel.maxWidth());
    assertThat(card.getMaximumSize().width).isEqualTo(card.getPreferredSize().width);
    layOut(card, 260);
    JTextArea use = (JTextArea) card.lines().parts().get(1);
    int oneLine = use.getFontMetrics(use.getFont()).getHeight();
    assertThat(use.getHeight()).isGreaterThan(oneLine * 2);
    assertThat(card.lines().getY() + card.lines().getHeight()).as("every line fits in the card")
        .isLessThanOrEqualTo(card.getHeight() - card.getInsets().bottom);
  }

  @Test
  void aShortCardIsOnlyAsWideAsItsLines() {
    var shortCard = new ItemCardPanel(new ItemCard(0, List.of(new TooltipLine("name", "Rag", "#9d9d9d")), 1L, null,
        null, null), "Rag", ICON);
    assertThat(shortCard.getPreferredSize().width).isLessThan(ItemCardPanel.maxWidth());
  }

  @Test
  void theMutedSourceNoteIsJustALine() {
    var card = new ItemCardPanel(new ItemCard(2, List.of(new TooltipLine("name", "Blade", "#1eff00"),
        new TooltipLine("source_note", "Classic Era values. Forever stats are still hidden.", "#808080")), 1L, null, null, null), "Blade",
        ICON);
    assertThat(card.lines().texts()).containsExactly("Classic Era values. Forever stats are still hidden.");
    assertThat(card.lines().colours()).containsExactly(new Color(0x808080));
  }

  @Test
  void withoutAQualityTheNameKeepsItsStoredColour() {
    var card = new ItemCardPanel(new ItemCard(null, List.of(new TooltipLine("name", "Odd Relic", "#a335ee"),
        new TooltipLine("item_level", "Item Level 5", null)), null, null, null, null), "Odd Relic", ICON);
    assertThat(card.name().getForeground()).isEqualTo(new Color(0xA335EE));
  }

  @Test
  void withoutANameLineTheCardIsTitledWithTheGivenName() {
    var card = new ItemCardPanel(new ItemCard(null, List.of(new TooltipLine("item_level", "Item Level 5", null)),
        null, null, null, null), "Item #5", ICON);
    assertThat(card.name().getText()).isEqualTo("Item #5");
    assertThat(card.name().getForeground()).isEqualTo(TooltipLinesPanel.DEFAULT_TEXT);
    assertThat(card.lines().texts()).containsExactly("Item Level 5");
  }

  @Test
  void theCardIsNavyWithItsBorderOnBothThemes() throws Exception {
    LookAndFeel saved = UIManager.getLookAndFeel();
    try {
      for (LookAndFeel laf : List.of(new FlatDarkLaf(), new FlatLightLaf())) {
        UIManager.setLookAndFeel(laf);
        var card = new ItemCardPanel(card(17204), "Eye of Sulfuras", ICON);
        SwingUtilities.updateComponentTreeUI(card);
        layOut(card, 300);
        BufferedImage image = new BufferedImage(card.getWidth(), card.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        card.printAll(g);
        g.dispose();
        int w = image.getWidth();
        int h = image.getHeight();
        assertThat(new Color(image.getRGB(w / 2, h - 3), true)).as(laf.getName() + " background")
            .isEqualTo(ItemCardPanel.BACKGROUND);
        assertThat(new Color(image.getRGB(w / 2, h - 1), true)).as(laf.getName() + " border")
            .isEqualTo(ItemCardPanel.BORDER);
        assertThat(new Color(image.getRGB(w / 2, 0), true)).isEqualTo(ItemCardPanel.BORDER);
        assertThat(image.getRGB(0, 0) >>> 24).as(laf.getName() + " rounded corner").isLessThan(255);
        assertThat(card.name().getForeground()).isEqualTo(QualityColors.canonical(5));
        assertThat(card.lines().parts().get(0).getForeground()).isEqualTo(Color.WHITE);
      }
    } finally {
      UIManager.setLookAndFeel(saved);
    }
    assertThat(ItemCardPanel.BACKGROUND).isEqualTo(new Color(0x0C1424));
    assertThat(ItemCardPanel.BORDER).isEqualTo(new Color(0x46587A));
    assertThat(ItemCardPanel.RADIUS).isEqualTo(4);
  }
}
