package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.wowforever.app.db.FixtureDb;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class MapImagePanelTest {
  private final BufferedImage westfall =
      Images.decode(FixtureDb.repositories().zones().map(40).orElseThrow().jpeg()).orElseThrow();

  @Test
  void holdsTheDecodedImage() {
    var p = new MapImagePanel(westfall);
    assertThat(p.image()).isSameAs(westfall);
    assertThat(p.image().getWidth()).isEqualTo(1002);
    assertThat(p.image().getHeight()).isEqualTo(668);
  }

  @Test
  void needsAnImage() {
    assertThatNullPointerException().isThrownBy(() -> new MapImagePanel(null));
  }

  @Test
  void scalesToTheWidthKeepingTheAspectRatioWithoutUpscaling() {
    var p = new MapImagePanel(westfall);
    assertThat(p.heightFor(501)).isEqualTo(334);
    assertThat(p.heightFor(1002)).isEqualTo(668);
    assertThat(p.heightFor(3000)).isEqualTo(668);
    assertThat(p.heightFor(0)).isZero();
    p.setSize(501, 10);
    assertThat(p.getPreferredSize().height).isEqualTo(334);
  }

  @Test
  void paintsTheScaledImage() {
    var p = new MapImagePanel(westfall);
    p.setSize(501, 334);
    BufferedImage canvas = new BufferedImage(501, 334, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = canvas.createGraphics();
    p.paint(g);
    g.dispose();
    assertThat(canvas.getRGB(250, 167) >>> 24).as("alpha of the centre pixel").isEqualTo(0xFF);
  }
}
