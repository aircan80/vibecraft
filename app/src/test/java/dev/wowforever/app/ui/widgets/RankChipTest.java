package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.ui.theme.Contrast;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.theme.Tokens;
import org.junit.jupiter.api.Test;

class RankChipTest {

  @Test
  void eliteIsAGoldOutlineRareASilverOneAndBossAGoldFill() {
    assertThat(new RankChip(1).style()).isEqualTo(RankChip.Style.GOLD_OUTLINE);
    assertThat(new RankChip(4).style()).isEqualTo(RankChip.Style.SILVER_OUTLINE);
    assertThat(new RankChip(2).style()).as("rare elite").isEqualTo(RankChip.Style.SILVER_OUTLINE);
    assertThat(new RankChip(3).style()).isEqualTo(RankChip.Style.GOLD_FILL);
  }

  @Test
  void readsTheRankInWords() {
    assertThat(new RankChip(1).getText()).isEqualTo("Elite");
    assertThat(new RankChip(2).getText()).isEqualTo("Rare Elite");
    assertThat(new RankChip(3).getText()).isEqualTo("Boss");
    assertThat(new RankChip(4).getText()).isEqualTo("Rare");
  }

  @Test
  void normalAndUnknownRanksShowNoChip() {
    for (Integer rank : new Integer[] {0, null, 9}) {
      RankChip chip = new RankChip(rank);
      assertThat(chip.style()).isEqualTo(RankChip.Style.NONE);
      assertThat(chip.isVisible()).isFalse();
      assertThat(chip.getText()).isEmpty();
    }
    assertThat(new RankChip(1).isVisible()).isTrue();
  }

  @Test
  void coloursComeFromTheThemeTokens() {
    Theme t = Theme.current();
    assertThat(new RankChip(1).outline()).isEqualTo(t.goldText());
    assertThat(new RankChip(1).getForeground()).isEqualTo(t.goldText());
    assertThat(new RankChip(4).outline()).isEqualTo(t.textMuted());
    assertThat(new RankChip(3).outline()).isEqualTo(t.goldText());
    assertThat(new RankChip(3).getForeground()).as("background-coloured text on the gold fill").isEqualTo(t.bgBase());
  }

  @Test
  void everyChipsTextReadsAtFourAndAHalfToOneInBothThemes() {
    Theme saved = Theme.current();
    try {
      for (Theme t : new Theme[] {Tokens.DARK, Tokens.LIGHT}) {
        Theme.setCurrent(t);
        String theme = t.dark() ? "dark" : "light";
        for (int rank : new int[] {1, 2, 4}) { // outlines: the text on the page, either background
          RankChip chip = new RankChip(rank);
          assertThat(Contrast.ratio(chip.getForeground(), t.bgBase())).as(chip.getText() + " on bg.base, " + theme)
              .isGreaterThanOrEqualTo(4.5);
          assertThat(Contrast.ratio(chip.getForeground(), t.bgSurface()))
              .as(chip.getText() + " on bg.surface, " + theme).isGreaterThanOrEqualTo(4.5);
        }
        RankChip boss = new RankChip(3); // the fill is the text's background
        assertThat(Contrast.ratio(boss.getForeground(), boss.outline())).as("Boss, " + theme)
            .isGreaterThanOrEqualTo(4.5);
      }
    } finally {
      Theme.setCurrent(saved);
    }
  }
}
