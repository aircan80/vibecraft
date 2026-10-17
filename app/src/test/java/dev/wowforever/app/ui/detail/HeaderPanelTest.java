package dev.wowforever.app.ui.detail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.theme.Tokens;
import dev.wowforever.app.ui.widgets.Chip;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;

class HeaderPanelTest {
  private static final Color NAVY = new Color(0x0C1424);

  /** A 16 px red square. */
  private static final Icon RED = new Icon() {
    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      g.setColor(Color.RED);
      g.fillRect(x, y, 16, 16);
    }

    @Override
    public int getIconWidth() {
      return 16;
    }

    @Override
    public int getIconHeight() {
      return 16;
    }
  };

  private static BufferedImage paint(JComponent c) {
    c.setSize(c.getPreferredSize());
    BufferedImage image = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      c.paint(g);
    } finally {
      g.dispose();
    }
    return image;
  }

  @Test
  void chipsAndPlate() {
    HeaderPanel h = new HeaderPanel("Hogger", "NPC #448");
    h.setFacts("Lv 11");
    Chip elite = new Chip("Elite", Chip.Style.ELITE);
    Chip zone = new Chip("Elwynn Forest", Chip.Style.PLAIN);
    h.addChip(elite);
    h.addChip(zone);
    assertThat(h.facts()).isEqualTo("Lv 11");
    assertThat(h.chips()).containsExactly(elite, zone);
    List<Component> row = List.of(h.factsRow().getComponents());
    assertThat(row.indexOf(h.factsLabel())).as("the facts first").isGreaterThanOrEqualTo(0)
        .isLessThan(row.indexOf(elite));
    assertThat(row.indexOf(elite)).as("then the chips in order").isLessThan(row.indexOf(zone));

    assertThat(h.plate().isVisible()).as("no plate until set").isFalse();
    h.setPlate(RED);
    assertThat(h.plate().isVisible()).isTrue();
    Theme saved = Theme.current();
    try {
      for (Theme theme : List.of(Tokens.DARK, Tokens.PARCHMENT)) {
        Theme.setCurrent(theme);
        BufferedImage plate = paint(h.plate());
        assertThat(new Color(plate.getRGB(2, plate.getHeight() / 2), true)).as("navy in both themes")
            .isEqualTo(NAVY);
        assertThat(new Color(plate.getRGB(plate.getWidth() / 2, plate.getHeight() / 2))).as("the icon, centred")
            .isEqualTo(Color.RED);
      }
    } finally {
      Theme.setCurrent(saved);
    }
  }

  @Test
  void aChipLinkOpensItsEntityAfterTheOtherChips() {
    List<ViewTarget> opened = new ArrayList<>();
    HeaderPanel h = new HeaderPanel("Wanted:  \"Hogger\"", "");
    Chip elite = new Chip("Elite", Chip.Style.ELITE);
    h.addChip(elite);
    JButton zone = h.addChipLink("Elwynn Forest", new EntityRef(EntityKind.ZONE, 12), opened::add);
    assertThat(h.chips()).containsExactly(elite, zone);
    assertThat(zone.getText()).isEqualTo("Elwynn Forest");
    zone.doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(EntityKind.ZONE, 12)));
  }

  @Test
  void theKindWordAndAnEmptyFactsRow() {
    HeaderPanel h = new HeaderPanel("Hogger", "NPC #448");
    assertThat(h.kindWord()).isNull();
    assertThat(h.factsRow().isVisible()).as("no facts, no chips: no row").isFalse();
    h.setKindWord("NPC");
    assertThat(h.kindWord()).isEqualTo("NPC");
    h.setFacts("1 NPC · 1 quest");
    assertThat(h.factsRow().isVisible()).isTrue();
    h.setFacts("");
    assertThat(h.factsRow().isVisible()).isFalse();
    assertThat(h.facts()).isEmpty();
    h.setKindWord(null);
    assertThat(h.kindWord()).isNull();
  }
}
