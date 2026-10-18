package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.ui.theme.Contrast;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.theme.Tokens;
import java.awt.Color;
import org.junit.jupiter.api.Test;

class JournalAppearanceTest {
  @Test void swingDefaultsCanBeReplacedWhenThemeChanges() {
    Theme previous=Theme.current();
    var defaults=new java.util.HashMap<Object,Object>();
    defaults.putAll(javax.swing.UIManager.getLookAndFeelDefaults());
    try {
      for(Theme original:new Theme[]{Tokens.DARK,Tokens.PARCHMENT}) {
        Theme.setCurrent(original);
        JournalAppearance.apply();
        for(String key:new String[]{"Panel.background","Label.foreground","TextField.background",
            "Table.selectionBackground","Component.borderColor"})
          assertThat(javax.swing.UIManager.get(key)).isInstanceOf(javax.swing.plaf.ColorUIResource.class);
      }
    } finally {
      javax.swing.UIManager.getLookAndFeelDefaults().clear();
      javax.swing.UIManager.getLookAndFeelDefaults().putAll(defaults);
      Theme.setCurrent(previous);
    }
  }
  @Test void journalTextAndActionsRemainReadableInBothPalettes() {
    for(Theme original:new Theme[]{Tokens.DARK,Tokens.PARCHMENT}) {
      Theme journal=JournalAppearance.palette(original);
      for(Color text:new Color[]{journal.textPrimary(),journal.textMuted(),journal.goldText(),journal.link()})
        for(Color background:new Color[]{journal.bgBase(),journal.bgSurface(),journal.bgRaised(),journal.selection()})
          assertThat(Contrast.ratio(text,background)).isGreaterThanOrEqualTo(4.5);
      assertThat(journal.cardBg()).isEqualTo(original.cardBg());
      assertThat(journal.plate()).isEqualTo(original.plate());
    }
  }
}
