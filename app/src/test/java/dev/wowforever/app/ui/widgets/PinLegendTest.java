package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.theme.Tokens;
import dev.wowforever.app.ui.widgets.MapView.PinKind;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class PinLegendTest {
  @Test
  void theLegendTellsEveryPinKindTheZoneMapDraws() {
    PinLegend legend = new PinLegend();
    assertThat(legend.texts()).containsExactly("Boss", "Rare", "NPC", "Object", "Starts a quest", "Ends a quest");
    assertThat(legend.entries()).allSatisfy(e -> assertThat(e.getForeground()).isEqualTo(Theme.current().textMuted()));
    assertThat(legend.getAccessibleContext().getAccessibleName()).isEqualTo("Map legend");
  }

  @Test
  void eachDotIsPaintedInTheMapsColourOfItsKind() {
    Theme saved = Theme.current();
    try {
      for (Theme theme : new Theme[] {Tokens.DARK, Tokens.LIGHT}) {
        Theme.setCurrent(theme);
        PinLegend legend = new PinLegend();
        for (int i = 0; i < 4; i++) {
          JLabel entry = legend.entries().get(i);
          PinKind kind = ((PinLegend.PinIcon) entry.getIcon()).kind();
          assertThat(centre(entry.getIcon(), entry)).as(kind + " in " + (theme.dark() ? "dark" : "light"))
              .isEqualTo(MapView.colour(kind).getRGB() & 0xFFFFFF);
        }
      }
    } finally {
      Theme.setCurrent(saved);
    }
  }

  @Test
  void theQuestEntriesShowTheGiversGlyphs() {
    PinLegend legend = new PinLegend();
    assertThat(((PinLegend.PinIcon) legend.entries().get(4).getIcon()).kind()).isEqualTo(PinKind.QUEST_START);
    assertThat(((PinLegend.PinIcon) legend.entries().get(5).getIcon()).kind()).isEqualTo(PinKind.QUEST_END);
    BufferedImage glyph = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = glyph.createGraphics();
    legend.entries().get(4).getIcon().paintIcon(legend.entries().get(4), g, 0, 0);
    g.dispose();
    boolean yellow = false;
    for (int x = 0; x < 20; x++) {
      for (int y = 0; y < 20; y++) {
        int rgb = glyph.getRGB(x, y);
        yellow |= (rgb >>> 24) > 200 && (rgb >> 16 & 0xFF) > 200 && (rgb >> 8 & 0xFF) > 170 && (rgb & 0xFF) < 90;
      }
    }
    assertThat(yellow).as("the yellow \"!\" (antialiased)").isTrue();
  }

  /** The RGB at the centre of {@code icon} painted for {@code c}. */
  private static int centre(Icon icon, JLabel c) {
    BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    icon.paintIcon(c, g, 0, 0);
    g.dispose();
    return image.getRGB(icon.getIconWidth() / 2, icon.getIconHeight() / 2) & 0xFFFFFF;
  }
}
