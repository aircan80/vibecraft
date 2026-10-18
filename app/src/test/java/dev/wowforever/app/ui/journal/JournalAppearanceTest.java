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
      // FlatLaf observes font changes synchronously. Clearing the live table and
      // restoring entries individually exposes a half-restored font dependency
      // graph to UIScale and can recurse through ActiveFont. Restore atomically.
      var live=javax.swing.UIManager.getLookAndFeelDefaults();
      Object[] restored=new Object[defaults.size()*2];int index=0;
      for(var entry:defaults.entrySet()){restored[index++]=entry.getKey();restored[index++]=entry.getValue();}
      live.putDefaults(restored);
      for(Object key:new java.util.HashSet<>(live.keySet()))if(!defaults.containsKey(key))live.remove(key);
      Theme.setCurrent(previous);
    }
  }
  @Test void flatLafFontDefaultsRemainUsableAfterPaletteRestoration() throws Exception {
    var previous=javax.swing.UIManager.getLookAndFeel();
    try {
      javax.swing.UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatDarkLaf());
      var font=javax.swing.UIManager.getFont("Label.font");
      swingDefaultsCanBeReplacedWhenThemeChanges();
      assertThat(javax.swing.UIManager.getFont("Label.font")).isEqualTo(font);
      assertThat(new javax.swing.JLabel("After palette restoration").getFont()).isNotNull();
    } finally {
      javax.swing.UIManager.setLookAndFeel(previous);
    }
  }
  @Test void journalTextAndActionsRemainReadableInBothPalettes() {
    for(Theme original:new Theme[]{Tokens.DARK,Tokens.PARCHMENT}) {
      Theme journal=JournalAppearance.palette(original);
      for(Color text:new Color[]{journal.textPrimary(),journal.textMuted(),journal.goldText(),journal.link()})
        for(Color background:new Color[]{journal.bgBase(),journal.bgSurface(),journal.bgRaised(),journal.selection()})
          assertThat(Contrast.ratio(text,background)).isGreaterThanOrEqualTo(4.5);
      assertThat(journal.cardBg()).isEqualTo(journal.bgSurface());
      assertThat(journal.cardBorder()).isEqualTo(journal.hairline());
      assertThat(journal.plate()).isEqualTo(original.plate());
    }
  }
}
