package dev.wowforever.app.ui.widgets;

import static dev.wowforever.app.ui.widgets.RailTarget.HOME;
import static dev.wowforever.app.ui.widgets.RailTarget.INSTANCES;
import static dev.wowforever.app.ui.widgets.RailTarget.ITEMS;
import static dev.wowforever.app.ui.widgets.RailTarget.NPCS;
import static dev.wowforever.app.ui.widgets.RailTarget.QUESTS;
import static dev.wowforever.app.ui.widgets.RailTarget.ZONES;
import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import dev.wowforever.app.model.EntityKind;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import org.junit.jupiter.api.Test;

class RailIconsTest {
  private static final int RED = 0xFFFF0000;
  private static final int GREEN = 0xFF00FF00;

  @Test
  void assetNamesAndGossipGlyphs() {
    assertThat(Arrays.stream(RailTarget.values()).map(RailTarget::assetName)).containsExactly("rail.home", "rail.forever",
        "rail.items", "rail.npcs", "rail.quests", "rail.zones", "rail.instances");
    assertThat(Arrays.stream(RailTarget.values()).filter(RailTarget::gossip)).containsExactly(NPCS, QUESTS);
  }

  @Test
  void eachListableKindHasItsRailTarget() {
    assertThat(RailTarget.of(EntityKind.ITEM)).contains(ITEMS);
    assertThat(RailTarget.of(EntityKind.NPC)).contains(NPCS);
    assertThat(RailTarget.of(EntityKind.QUEST)).contains(QUESTS);
    assertThat(RailTarget.of(EntityKind.ZONE)).contains(ZONES);
    assertThat(RailTarget.of(EntityKind.INSTANCE)).contains(INSTANCES);
    assertThat(RailTarget.of(EntityKind.OBJECT)).isEmpty();
    assertThat(RailTarget.of(EntityKind.BOSS)).isEmpty();
  }

  @Test
  void cropsCentralEightySixPercent() {
    BufferedImage src = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 64; y++) {
      for (int x = 0; x < 64; x++) {
        src.setRGB(x, y, x == 0 || y == 0 || x == 63 || y == 63 ? RED : GREEN);
      }
    }
    BufferedImage plate = RailIcons.plateArt(src, false);
    assertThat(plate.getWidth()).isEqualTo(24);
    assertThat(plate.getHeight()).isEqualTo(24);
    for (int y = 0; y < 24; y++) {
      for (int x = 0; x < 24; x++) {
        int argb = plate.getRGB(x, y);
        assertThat((argb >> 16) & 0xFF).as("red at %d,%d", x, y).isZero();
        assertThat(argb >>> 24).as("alpha at %d,%d", x, y).isEqualTo(255);
      }
    }
  }

  @Test
  void gossipGlyphFillsTheIconBox() {
    BufferedImage glyph = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    for (int y = 2; y < 14; y++) {
      for (int x = 1; x < 15; x++) { // 14 px wide, a 1 px margin each side as the GossipFrame art has
        glyph.setRGB(x, y, 0xFFF0D060);
      }
    }
    BufferedImage plate = RailIcons.plateArt(glyph, true);
    assertThat(plate.getWidth()).isEqualTo(24);
    assertThat(plate.getHeight()).isEqualTo(24);
    int left = 24;
    int right = -1;
    for (int y = 0; y < 24; y++) {
      for (int x = 0; x < 24; x++) {
        if ((plate.getRGB(x, y) >>> 24) >= 128) {
          left = Math.min(left, x);
          right = Math.max(right, x);
        }
      }
    }
    assertThat(right - left + 1).as("opaque width").isGreaterThanOrEqualTo(20);
    assertThat(left + right).as("centred").isBetween(22, 24);
  }

  @Test
  void missingAssetFallsBackToSvg() {
    RailIcons icons = RailIcons.load(name -> Optional.empty());
    assertThat(icons.plate(QUESTS)).isEmpty();
    assertThat(icons.small(QUESTS, 16)).isInstanceOf(FlatSVGIcon.class);
    assertThat(icons.small(QUESTS, 16).getIconWidth()).isEqualTo(16);
    assertThat(RailIcons.none().plate(HOME)).isEmpty();
  }

  @Test
  void corruptArtFallsBackToSvg() {
    RailIcons icons = RailIcons.load(name -> Optional.of(new byte[] {1, 2, 3}));
    for (RailTarget t : RailTarget.values()) {
      assertThat(icons.plate(t)).as(t.name()).isEmpty();
      assertThat(icons.small(t, 16)).as(t.name()).isInstanceOf(FlatSVGIcon.class);
    }
  }

  @Test
  void loadsThePlateArtFromTheNativeTexturesAndTheSmallIconsFromTheBestVariant() {
    Map<String, byte[]> assets = new HashMap<>();
    for (RailTarget t : RailTarget.values()) {
      int size = t.gossip() ? 16 : 64;
      assets.put(t.assetName(), png(solid(size, GREEN)));
      assets.put(t.assetName() + "@24", png(solid(24, RED)));
    }
    List<String> asked = new ArrayList<>();
    RailIcons icons = RailIcons.load(name -> {
      asked.add(name);
      return Optional.ofNullable(assets.get(name));
    });
    for (RailTarget t : RailTarget.values()) {
      Icon plate = icons.plate(t).orElseThrow();
      assertThat(plate.getIconWidth()).as(t.name()).isEqualTo(24);
      assertThat(icons.plates().get(t).getWidth()).isEqualTo(24);
      assertThat((icons.plates().get(t).getRGB(12, 12) >> 8) & 0xFF).as("plate art from the native texture")
          .isEqualTo(255);
      Icon small = icons.small(t, 16);
      assertThat(small).isNotInstanceOf(FlatSVGIcon.class);
      assertThat(small.getIconWidth()).isEqualTo(16);
      assertThat(small).as("the same icon each time").isEqualTo(icons.small(t, 16));
      // Interface/Icons art: the 24 px variant (already cropped); a gossip glyph: its native 16 px art
      int expected = t.gossip() ? GREEN : RED;
      assertThat(icons.small().get(t).getRGB(4, 4)).as(t.name()).isEqualTo(expected);
    }
    assertThat(asked).contains("rail.home", "rail.home@24", "rail.quests").doesNotContain("rail.quests@24");
  }

  @Test
  void sharedIsNoneUntilInstalled() {
    RailIcons mine = RailIcons.load(name -> Optional.empty());
    RailIcons before = RailIcons.install(mine);
    try {
      assertThat(RailIcons.shared()).isSameAs(mine);
    } finally {
      RailIcons.install(before);
    }
  }

  static BufferedImage solid(int size, int argb) {
    BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        image.setRGB(x, y, argb);
      }
    }
    return image;
  }

  static byte[] png(BufferedImage image) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "png", out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
