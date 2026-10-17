package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.ui.theme.Fonts;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.theme.Tokens;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;

class SectionHeadingTest {

  @Test
  void headingUsesSmallCapsGoldAndCount() {
    SectionHeading h = new SectionHeading("Dropped by", 34);
    assertThat(h.text()).isEqualTo("Dropped by (34)");
    assertThat(h.label().getFont().getFamily()).isEqualTo(Fonts.headingSmallCaps(14f).getFamily());
    assertThat(h.getForeground()).isEqualTo(Theme.current().goldText());
    assertThat(h.label().getForeground()).isEqualTo(Theme.current().goldText());
    Theme saved = Theme.current();
    try {
      Theme.setCurrent(Tokens.PARCHMENT);
      assertThat(h.label().getForeground()).as("looked up per paint").isEqualTo(Tokens.PARCHMENT.goldText());
    } finally {
      Theme.setCurrent(saved);
    }
    h.setCount(1234);
    assertThat(h.text()).isEqualTo("Dropped by (1,234)");
  }

  @Test
  void ruleFadesRight() {
    SectionHeading h = new SectionHeading("Dropped by", 34);
    h.setSize(300, 24);
    h.doLayout();
    BufferedImage img = new BufferedImage(300, 24, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    h.paint(g);
    g.dispose();
    int y = 23; // the rule is the bottom row
    int near = img.getRGB(120, y) >>> 24;
    int far = img.getRGB(290, y) >>> 24;
    assertThat(near).as("the rule is drawn under the heading").isPositive();
    assertThat(far).as("and fades to the right").isLessThan(near);
  }

  @Test
  void takesAnIconAndATrailingControl() {
    SectionHeading h = new SectionHeading("Drops", 2);
    h.setIcon(IconCache.empty(16));
    assertThat(h.label().getIcon()).isNotNull();
    JButton filter = new JButton("Filter");
    h.setTrailing(filter);
    assertThat(filter.getParent()).isSameAs(h);
    h.setTrailing(null);
    assertThat(filter.getParent()).isNull();
  }
}
