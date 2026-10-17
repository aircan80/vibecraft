package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.theme.Tokens;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class StatusBarTest {
  private static final String HASH = "6c9ed1fa2901b9ff63680623e8816bd3a748f7f8b1c38e480b29eb72f1113148";

  @Test
  void theBuildShowsItsFirstEightCharactersWithTheWholeIdInTheTooltip() {
    StatusBar status = new StatusBar();
    status.setBuild(HASH);
    assertThat(status.info()).isEqualTo("Data build 6c9ed1fa");
    assertThat(status.infoLabel().getToolTipText()).isEqualTo(HASH);
    status.setBuild("b-42");
    assertThat(status.info()).isEqualTo("Data build b-42");
    status.setBuild(null);
    assertThat(status.info()).isEmpty();
    assertThat(status.infoLabel().getToolTipText()).isNull();
  }

  @Test
  void buildLabelShortensOnlyLongIds() {
    assertThat(StatusBar.buildLabel(HASH)).isEqualTo("Data build 6c9ed1fa");
    assertThat(StatusBar.buildLabel("abc")).isEqualTo("Data build abc");
  }

  @Test
  void theBuildIsHiddenInTheCompactWindowAndBackInTheFullOne() {
    StatusBar status = new StatusBar();
    status.setBuild(HASH);
    JPanel compact = new JPanel(new BorderLayout());
    compact.putClientProperty(CompactColumns.KEY, Boolean.TRUE);
    compact.add(status);
    assertThat(status.infoLabel().isVisible()).isFalse();
    new JPanel(new BorderLayout()).add(status);
    assertThat(status.infoLabel().isVisible()).isTrue();
    assertThat(status.info()).isEqualTo("Data build 6c9ed1fa");
  }

  private static final Map<String, String> META = Map.of("client_product", "wow_classic_beta",
      "client_build", "1.60.1.70425", "client_item_count", "20492", "build_id", HASH);

  @Test
  void provenanceFromMeta() {
    assertThat(StatusBar.provenanceText(META)).isEqualTo("Forever beta 1.60.1 · 20,492 items");
    for (String key : new String[] {"client_product", "client_build", "client_item_count"}) {
      Map<String, String> missing = new HashMap<>(META);
      missing.remove(key);
      assertThat(StatusBar.provenanceText(missing)).as("without " + key).isEmpty();
    }
    assertThat(StatusBar.provenanceText(Map.of())).isEmpty();
    StatusBar status = new StatusBar();
    status.setProvenance(META);
    assertThat(status.provenance()).isEqualTo("Forever beta 1.60.1 · 20,492 items");
    assertThat(status.provenanceLabel().getToolTipText()).contains("wow_classic_beta 1.60.1.70425");
    status.setProvenance(Map.of());
    assertThat(status.provenance()).isEmpty();
    assertThat(status.getPreferredSize().height).isEqualTo(UIScale.scale(22));
  }

  @Test
  void goldTopHairline() {
    for (Theme t : new Theme[] {Tokens.DARK, Tokens.LIGHT}) {
      Theme saved = Theme.current();
      Theme.setCurrent(t);
      try {
        StatusBar status = new StatusBar();
        status.setSize(200, 22);
        status.doLayout();
        BufferedImage img = new BufferedImage(200, 22, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        status.paint(g);
        g.dispose();
        Color expected = over(t.hairline(), t.bgSurface());
        for (int x = 0; x < 200; x += 37) {
          assertThat(new Color(img.getRGB(x, 0), true)).as("row 0 at x " + x).isEqualTo(expected);
        }
        assertThat(new Color(img.getRGB(2, 12), true)).as("the bar is the surface").isEqualTo(t.bgSurface());
      } finally {
        Theme.setCurrent(saved);
      }
    }
  }

  /** {@code top} (translucent) painted over the opaque {@code bottom}, as Java 2D blends it. */
  private static Color over(Color top, Color bottom) {
    BufferedImage one = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = one.createGraphics();
    g.setColor(bottom);
    g.fillRect(0, 0, 1, 1);
    g.setColor(top);
    g.fillRect(0, 0, 1, 1);
    g.dispose();
    return new Color(one.getRGB(0, 0), true);
  }

  @Test
  void theThemeAndLayoutTogglesSitOnTheRight() {
    StatusBar status = new StatusBar();
    assertThat(status.themeToggle().getText()).isNotBlank();
    assertThat(status.layoutToggle().getText()).isNotBlank();
    assertThat(status.themeToggle().getToolTipText()).isNotBlank();
    assertThat(status.layoutToggle().getToolTipText()).contains("F10");
    assertThat(javax.swing.SwingUtilities.isDescendingFrom(status.themeToggle(), status)).isTrue();
    status.setSize(600, 22);
    status.doLayout();
    java.awt.Rectangle theme = javax.swing.SwingUtilities.convertRectangle(status.themeToggle().getParent(),
        status.themeToggle().getBounds(), status);
    java.awt.Rectangle message = javax.swing.SwingUtilities.convertRectangle(status.messageLabel().getParent(),
        status.messageLabel().getBounds(), status);
    java.awt.Rectangle provenance = javax.swing.SwingUtilities.convertRectangle(
        status.provenanceLabel().getParent(), status.provenanceLabel().getBounds(), status);
    assertThat(provenance.x).isLessThan(message.x);
    assertThat(theme.x).isGreaterThan(message.x);
  }

  @Test
  void theProvenanceIsHiddenInTheCompactWindowButTheTogglesStay() {
    StatusBar status = new StatusBar();
    status.setProvenance(META);
    JPanel compact = new JPanel(new BorderLayout());
    compact.putClientProperty(CompactColumns.KEY, Boolean.TRUE);
    compact.add(status);
    assertThat(status.provenanceLabel().isVisible()).isFalse();
    assertThat(status.layoutToggle().isVisible()).isTrue();
    new JPanel(new BorderLayout()).add(status);
    assertThat(status.provenanceLabel().isVisible()).isTrue();
  }
}
