package dev.wowforever.app.ui.detail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.model.ZoneMap;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class MapImagePanelTest {
  private final ZoneMap westfall = FixtureDb.repositories().zones().map(40).orElseThrow();

  @Test
  void decodesTheJpeg() {
    var p = new MapImagePanel(westfall);
    assertThat(p.zoneMap()).isSameAs(westfall);
    assertThat(p.image()).isNotNull();
    assertThat(p.image().getWidth()).isEqualTo(1002);
    assertThat(p.image().getHeight()).isEqualTo(668);
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
  void undecodableBytesPaintNothing() {
    var p = new MapImagePanel(new ZoneMap(1, 100, 50, new byte[] {1, 2, 3}));
    assertThat(p.image()).isNull();
    p.setSize(100, 50);
    BufferedImage canvas = new BufferedImage(100, 50, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = canvas.createGraphics();
    assertThatCode(() -> p.paint(g)).doesNotThrowAnyException();
    g.dispose();
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
