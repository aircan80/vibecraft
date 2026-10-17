package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.theme.Tokens;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.JToggleButton;
import javax.swing.JToolTip;
import javax.swing.SwingConstants;
import org.junit.jupiter.api.Test;

class RailTest {

  @Test
  void browseSectionsWithTooltipsAndIcons() {
    Rail rail = new Rail();
    assertThat(rail.getOrientation()).isEqualTo(SwingConstants.VERTICAL);
    assertThat(rail.getComponentCount()).isEqualTo(7);
    assertThat(Arrays.stream(RailTarget.values()).map(t -> rail.button(t).getToolTipText()))
        .containsExactly("Home", "WoW: Forever", "Items", "NPCs", "Quests", "Zones", "Dungeons & Raids");
    for (RailTarget t : RailTarget.values()) {
      JToggleButton b = rail.button(t);
      assertThat(rail.getComponentIndex(b)).isEqualTo(t.ordinal());
      assertThat(b.getAccessibleContext().getAccessibleName()).isEqualTo(t.label());
      assertThat(b.getIcon()).isInstanceOfSatisfying(FlatSVGIcon.class, i -> {
        assertThat(i.hasFound()).as(t.iconName()).isTrue();
        assertThat(i.getImage()).isNotNull();
      });
    }
  }

  @Test
  void clickingAButtonFiresItsTargetAndSelectsIt() {
    Rail rail = new Rail();
    List<RailTarget> fired = new ArrayList<>();
    rail.onSelect(fired::add);
    rail.button(RailTarget.ZONES).doClick(0);
    rail.button(RailTarget.ITEMS).doClick(0);
    assertThat(fired).containsExactly(RailTarget.ZONES, RailTarget.ITEMS);
    assertThat(rail.button(RailTarget.ITEMS).isSelected()).isTrue();
    assertThat(rail.button(RailTarget.ZONES).isSelected()).isFalse();
  }

  @Test
  void selectMarksWithoutFiring() {
    Rail rail = new Rail();
    List<RailTarget> fired = new ArrayList<>();
    rail.onSelect(fired::add);
    rail.select(RailTarget.QUESTS);
    assertThat(rail.button(RailTarget.QUESTS).isSelected()).isTrue();
    rail.select(null);
    assertThat(rail.button(RailTarget.QUESTS).isSelected()).isFalse();
    assertThat(fired).isEmpty();
  }

  @Test
  void railIs48LogicalPixelsWide() {
    assertThat(new Rail().getPreferredSize().width).isEqualTo(UIScale.scale(48));
  }

  @Test
  void everyIconRendersHeadless() {
    for (RailTarget t : RailTarget.values()) {
      FlatSVGIcon icon = Icons.icon(t.iconName(), 24);
      assertThat(icon.hasFound()).as(t.iconName()).isTrue();
      assertThat(icon.getIconWidth()).isEqualTo(UIScale.scale(24));
      assertThat(icon.getImage()).as(t.iconName()).isNotNull();
    }
  }

  @Test
  void unknownIconFails() {
    assertThatThrownBy(() -> Icons.icon("no-such-icon", 24)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no-such-icon");
  }

  @Test
  void plateStates() {
    Theme saved = Theme.current();
    try {
      for (Theme theme : new Theme[] {Tokens.DARK, Tokens.PARCHMENT}) {
        Theme.setCurrent(theme);
        String mode = theme.dark() ? "Slate" : "Parchment";
        Rail rail = laidOut(redIcons());
        JToggleButton b = rail.button(RailTarget.HOME);
        Rectangle plate = plateBounds(b);
        int cy = plate.y + plate.height / 2;
        int gold = theme.accentGold().getRGB() & 0xFFFFFF;

        BufferedImage idle = paint(rail);
        assertThat(rgb(idle, plate.x + 1, cy)).as("%s idle plate", mode).isEqualTo(0x0C1424);
        assertThat(rgb(idle, plate.x + plate.width - 2, cy)).as("%s idle plate", mode).isEqualTo(0x0C1424);
        int icon = rgb(idle, plate.x + plate.width / 2, cy);
        assertClose(icon, blend(0xFF0000, 0x0C1424, 0.85), mode + " idle icon at 85 %");
        assertThat(red(icon)).as("never desaturated").isGreaterThan(green(icon));
        assertThat(rgb(idle, plate.x, cy)).as("%s idle: no ring", mode).isEqualTo(0x0C1424);
        assertThat(isClose(rgb(idle, 1, cy), gold)).as("%s idle: no bar", mode).isFalse();

        b.getModel().setRollover(true);
        BufferedImage hover = paint(rail);
        assertClose(rgb(hover, plate.x, cy), blend(gold, 0x0C1424, 0.5), mode + " hover ring");
        assertClose(rgb(hover, plate.x + plate.width / 2, cy), 0xFF0000, mode + " hover icon at full alpha");
        assertThat(rgb(hover, plate.x + 1, cy)).as("%s hover plate", mode).isEqualTo(0x0C1424);
        assertThat(isClose(rgb(hover, 1, cy), gold)).as("%s hover: no bar", mode).isFalse();
        b.getModel().setRollover(false);

        rail.select(RailTarget.HOME);
        BufferedImage selected = paint(rail);
        assertClose(rgb(selected, plate.x, cy), gold, mode + " selected border");
        assertClose(rgb(selected, plate.x + plate.width - 1, cy), gold, mode + " selected border");
        for (int x = 0; x <= 2; x++) {
          assertClose(rgb(selected, x, cy), gold, mode + " selected bar at x " + x);
        }
        assertThat(isClose(rgb(selected, 3, cy), gold)).as("%s the bar is 3 px", mode).isFalse();
        assertThat(isClose(rgb(selected, 1, plate.y - 3), gold)).as("%s the bar spans the plate", mode).isFalse();
        assertThat(rgb(selected, plate.x + 1, cy)).as("%s selected plate", mode).isEqualTo(0x0C1424);
        assertClose(rgb(selected, plate.x + plate.width / 2, cy), 0xFF0000, mode + " selected icon at full alpha");
      }
    } finally {
      Theme.setCurrent(saved);
    }
  }

  @Test
  void svgIconsSitOnTheSamePlatesUntilTheArtArrives() {
    Theme saved = Theme.current();
    try {
      Theme.setCurrent(Tokens.PARCHMENT);
      Rail rail = laidOut(null);
      Rectangle plate = plateBounds(rail.button(RailTarget.QUESTS));
      BufferedImage image = paint(rail);
      assertThat(rgb(image, plate.x + 1, plate.y + plate.height / 2)).isEqualTo(0x0C1424);
      // the glyph is light on the navy plate in either theme, never the light theme's dark text colour
      int brightest = 0;
      for (int y = plate.y; y < plate.y + plate.height; y++) {
        for (int x = plate.x; x < plate.x + plate.width; x++) {
          int p = rgb(image, x, y);
          brightest = Math.max(brightest, red(p) + green(p) + (p & 0xFF));
        }
      }
      assertThat(brightest).isGreaterThan(3 * 180);
    } finally {
      Theme.setCurrent(saved);
    }
  }

  @Test
  void iconsSwapInWithoutALayoutChange() {
    Rail rail = new Rail();
    Dimension before = rail.getPreferredSize();
    List<Dimension> buttons = Arrays.stream(RailTarget.values()).map(t -> rail.button(t).getPreferredSize()).toList();
    Map<RailTarget, BufferedImage> plates = new EnumMap<>(RailTarget.class);
    plates.put(RailTarget.ITEMS, RailIconsTest.solid(24, 0xFFFF0000));
    RailIcons icons = new RailIcons(plates, Map.of());
    rail.setIcons(icons);
    assertThat(rail.getPreferredSize()).isEqualTo(before);
    assertThat(Arrays.stream(RailTarget.values()).map(t -> rail.button(t).getPreferredSize())).isEqualTo(buttons);
    assertThat(rail.button(RailTarget.ITEMS).getIcon()).isEqualTo(icons.plate(RailTarget.ITEMS).orElseThrow());
    assertThat(rail.button(RailTarget.HOME).getIcon()).as("no art: the SVG stays").isInstanceOf(FlatSVGIcon.class);
    for (RailTarget t : RailTarget.values()) {
      assertThat(rail.button(t).getAccessibleContext().getAccessibleName()).isEqualTo(t.label());
    }
  }

  @Test
  void tooltipsOnNavyCard() {
    Rail rail = new Rail();
    rail.setIcons(redIcons());
    for (RailTarget t : RailTarget.values()) {
      JToggleButton b = rail.button(t);
      assertThat(b.getToolTipText()).isEqualTo(t.label());
      assertThat(b.getAccessibleContext().getAccessibleName()).isEqualTo(t.label());
      JToolTip tip = b.createToolTip();
      assertThat(tip.getBackground()).isEqualTo(new Color(0x0C1424));
      assertThat(tip.getForeground()).isEqualTo(TooltipLinesPanel.DEFAULT_TEXT);
    }
  }

  /** Every target with a solid red 24 px plate image. */
  private static RailIcons redIcons() {
    Map<RailTarget, BufferedImage> plates = new EnumMap<>(RailTarget.class);
    for (RailTarget t : RailTarget.values()) {
      plates.put(t, RailIconsTest.solid(24, 0xFFFF0000));
    }
    return new RailIcons(plates, Map.of());
  }

  private static Rail laidOut(RailIcons icons) {
    Rail rail = new Rail();
    if (icons != null) {
      rail.setIcons(icons);
    }
    rail.setSize(rail.getPreferredSize());
    layOut(rail);
    return rail;
  }

  private static void layOut(Component c) {
    if (c instanceof Container container) {
      container.doLayout();
      for (Component child : container.getComponents()) {
        layOut(child);
      }
    }
  }

  /** The plate of {@code b} in the rail's coordinates. */
  private static Rectangle plateBounds(JToggleButton b) {
    int size = UIScale.scale(RailButtonUI.PLATE);
    return new Rectangle(b.getX() + (b.getWidth() - size) / 2, b.getY() + (b.getHeight() - size) / 2, size, size);
  }

  private static BufferedImage paint(Rail rail) {
    BufferedImage image = new BufferedImage(rail.getWidth(), rail.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setColor(new Color(0x808080)); // whatever is under the rail
      g.fillRect(0, 0, image.getWidth(), image.getHeight());
      rail.paint(g);
    } finally {
      g.dispose();
    }
    return image;
  }

  private static int rgb(BufferedImage image, int x, int y) {
    return image.getRGB(x, y) & 0xFFFFFF;
  }

  private static int red(int rgb) {
    return (rgb >> 16) & 0xFF;
  }

  private static int green(int rgb) {
    return (rgb >> 8) & 0xFF;
  }

  /** {@code top} at {@code alpha} over {@code bottom}. */
  private static int blend(int top, int bottom, double alpha) {
    int out = 0;
    for (int shift = 0; shift <= 16; shift += 8) {
      double v = ((top >> shift) & 0xFF) * alpha + ((bottom >> shift) & 0xFF) * (1 - alpha);
      out |= (int) Math.round(v) << shift;
    }
    return out;
  }

  private static boolean isClose(int a, int b) {
    for (int shift = 0; shift <= 16; shift += 8) {
      if (Math.abs(((a >> shift) & 0xFF) - ((b >> shift) & 0xFF)) > 3) {
        return false;
      }
    }
    return true;
  }

  private static void assertClose(int actual, int expected, String what) {
    assertThat(isClose(actual, expected))
        .as("%s: #%06X, expected #%06X", what, actual, expected & 0xFFFFFF).isTrue();
  }
}
