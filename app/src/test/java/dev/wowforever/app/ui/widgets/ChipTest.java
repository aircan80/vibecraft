package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.ui.theme.Contrast;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.theme.Tokens;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class ChipTest {
  private static final Color SILVER = new Color(0xC0C6CF);
  private static final Color RED = new Color(0xC8463C);

  private static Chip rank(int rank) {
    return Chip.rank(rank).orElseThrow();
  }

  @Test
  void rankStyles() {
    Theme t = Theme.current();
    Chip elite = rank(1);
    assertThat(elite.style()).isEqualTo(Chip.Style.ELITE);
    assertThat(elite.outline()).isEqualTo(t.accentGold());
    assertThat(elite.textColor()).isEqualTo(t.goldText());
    Chip rare = rank(4);
    assertThat(rare.style()).isEqualTo(Chip.Style.RARE);
    assertThat(rare.outline()).isEqualTo(SILVER);
    assertThat(rare.textColor()).isEqualTo(t.textPrimary());
    Chip rareElite = rank(2);
    assertThat(rareElite.style()).isEqualTo(Chip.Style.RARE_ELITE);
    assertThat(rareElite.outline()).isEqualTo(SILVER);
    assertThat(rareElite.textColor()).isEqualTo(t.goldText());
    Chip boss = rank(3);
    assertThat(boss.style()).isEqualTo(Chip.Style.BOSS);
    assertThat(boss.outline()).isEqualTo(RED);
    assertThat(boss.textColor()).as("the red is only ever an outline").isEqualTo(t.textPrimary());
    assertThat(Chip.rank(0)).isEmpty();
    assertThat(Chip.rank(null)).isEmpty();
    assertThat(Chip.rank(9)).isEmpty();
  }

  @Test
  void readsTheRankInWords() {
    assertThat(rank(1).getText()).isEqualTo("Elite");
    assertThat(rank(2).getText()).isEqualTo("Rare Elite");
    assertThat(rank(3).getText()).isEqualTo("Boss");
    assertThat(rank(4).getText()).isEqualTo("Rare");
  }

  @Test
  void chipTextContrast() {
    Theme saved = Theme.current();
    try {
      for (Theme t : new Theme[] {Tokens.DARK, Tokens.PARCHMENT}) {
        Theme.setCurrent(t);
        String theme = t.dark() ? "Slate" : "Parchment";
        for (Chip.Style style : Chip.Style.values()) {
          Chip chip = new Chip(style.name(), style);
          assertThat(chip.getForeground()).isEqualTo(chip.textColor());
          assertThat(Contrast.ratio(chip.textColor(), t.bgBase())).as(style + " on bg.base, " + theme)
              .isGreaterThanOrEqualTo(4.5);
          assertThat(Contrast.ratio(chip.textColor(), t.bgSurface())).as(style + " on bg.surface, " + theme)
              .isGreaterThanOrEqualTo(4.5);
        }
      }
    } finally {
      Theme.setCurrent(saved);
    }
  }

  @Test
  void theSilverOutlineDarkensOnParchment() {
    Theme saved = Theme.current();
    try {
      Theme.setCurrent(Tokens.DARK);
      assertThat(rank(4).outline()).isEqualTo(Chip.SILVER).isEqualTo(new Color(0xC0C6CF));
      assertThat(rank(2).outline()).isEqualTo(Chip.SILVER);
      Theme.setCurrent(Tokens.PARCHMENT);
      assertThat(rank(4).outline()).isEqualTo(Chip.PARCHMENT_SILVER).isEqualTo(new Color(0x767C85));
      assertThat(rank(2).outline()).isEqualTo(Chip.PARCHMENT_SILVER);
      assertThat(rank(1).textColor()).as("Elite text on Parchment").isEqualTo(new Color(0x785C12));
      // the outline is a graphic: at least 3:1 where chips can sit, where #C0C6CF was 1.42:1 on base
      for (Color bg : new Color[] {Tokens.PARCHMENT.bgBase(), Tokens.PARCHMENT.bgSurface(),
          Tokens.PARCHMENT.bgRaised()}) {
        assertThat(Contrast.ratio(Chip.PARCHMENT_SILVER, bg)).as("on %s", bg).isGreaterThanOrEqualTo(3.0);
      }
    } finally {
      Theme.setCurrent(saved);
    }
  }

  @Test
  void chipsAreOutlinesOnly() {
    Chip boss = rank(3);
    boss.setSize(boss.getPreferredSize());
    BufferedImage img = new BufferedImage(boss.getWidth(), boss.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    boss.paint(g);
    g.dispose();
    assertThat(boss.isOpaque()).isFalse();
    assertThat(new Color(img.getRGB(boss.getWidth() / 2, 0), true)).as("the outline")
        .isEqualTo(RED);
    assertThat(img.getRGB(2, boss.getHeight() / 2) >>> 24).as("no fill inside").isZero();
  }
}
