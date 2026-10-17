package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;

class OriginChipTest {
  private final JTable table = new JTable(1, 1);

  private OriginChip render(String origin) {
    OriginChip c = new OriginChip();
    c.getTableCellRendererComponent(table, origin, false, false, 0, 0);
    return c;
  }

  @Test
  void questieOnlyRowsShowNothing() {
    assertThat(render("questie").chipText()).isNull();
    assertThat(render("questie").getText()).isEmpty();
    assertThat(render(null).chipText()).isNull();
  }

  @Test
  void otherOriginsShowAChip() {
    assertThat(render("atlasloot").chipText()).isEqualTo("AtlasLoot");
    assertThat(render("atlasloot").getText()).isEqualTo("AtlasLoot");
    assertThat(render("both").chipText()).isEqualTo("Questie + AtlasLoot");
  }

  @Test
  void chipIsOutlined() {
    BufferedImage chip = paint(render("both"));
    BufferedImage none = paint(render("questie"));
    int background = none.getRGB(1, 10);
    assertThat(countDifferent(none, background)).isZero();
    // the outline's left edge: a column of non-background pixels before the text starts
    int edge = render("both").chipBounds().x;
    boolean outlined = false;
    for (int y = 0; y < 20; y++) {
      outlined |= chip.getRGB(edge, y) != background;
    }
    assertThat(outlined).isTrue();
  }

  private static BufferedImage paint(OriginChip c) {
    c.setSize(200, 20);
    c.setBackground(Color.BLACK);
    c.setOpaque(true);
    BufferedImage img = new BufferedImage(200, 20, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    c.paint(g);
    g.dispose();
    return img;
  }

  private static int countDifferent(BufferedImage img, int rgb) {
    int n = 0;
    for (int x = 0; x < img.getWidth(); x++) {
      for (int y = 0; y < img.getHeight(); y++) {
        n += img.getRGB(x, y) != rgb ? 1 : 0;
      }
    }
    return n;
  }
}
