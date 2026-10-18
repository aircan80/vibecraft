package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.theme.Tokens;
import java.awt.Color;
import java.awt.Component;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class JournalSurfacePaletteTest {
  @Test void mockupSurfaceTokensAreExactInBothModes() {
    Theme.current();
    Theme dark = JournalAppearance.palette(Tokens.DARK), light = JournalAppearance.palette(Tokens.PARCHMENT);
    assertThat(dark.bgBase()).isEqualTo(new Color(0x111719));
    assertThat(dark.bgSurface()).isEqualTo(new Color(0x192124));
    assertThat(dark.bgRaised()).isEqualTo(new Color(0x202a2d));
    assertThat(dark.hairline()).isEqualTo(new Color(0x354043));
    assertThat(light.bgBase()).isEqualTo(new Color(0xf2eee5));
    assertThat(light.bgSurface()).isEqualTo(new Color(0xfaf7ef));
    assertThat(light.bgRaised()).isEqualTo(new Color(0xe8e4d9));
    assertThat(light.hairline()).isEqualTo(new Color(0xc8c6ba));
  }

  @Test void existingCardsAndSidebarFollowModeChangesWithoutRecreatingNavigation() throws Exception {
    Theme previous = Theme.current();
    try {
      SwingUtilities.invokeAndWait(() -> {
        var card = JournalSurface.card();
        var body = new JournalSurface.Body();
        var sidebar = new JournalSidebar(target -> {});
        sidebar.select(new dev.wowforever.app.nav.ViewTarget.List(dev.wowforever.app.model.EntityKind.NPC));
        for (Theme original : new Theme[]{Tokens.DARK, Tokens.PARCHMENT, Tokens.DARK}) {
          Theme theme = JournalAppearance.palette(original); Theme.setCurrent(theme); sidebar.refreshTheme();
          card.setSize(200, 120);
          var image = new BufferedImage(200,120,BufferedImage.TYPE_INT_RGB);
          var graphics = image.createGraphics(); card.paint(graphics); graphics.dispose();
          assertThat(new Color(image.getRGB(100,60))).isEqualTo(theme.bgSurface());
          assertThat(new Color(image.getRGB(0,60))).isEqualTo(theme.hairline());
          assertThat(body.getBackground()).isEqualTo(theme.bgBase());
          assertThat(sidebar.getBackground()).isEqualTo(theme.bgBase());
          sidebar.setCollapsed(true);
          for(Component component : sidebar.getComponents()) if(component instanceof JButton button) {
            boolean active = "NPCs".equals(button.getAccessibleContext().getAccessibleName());
            assertThat(button.isSelected()).isEqualTo(active);
            assertThat(button.getBackground()).isEqualTo(active ? theme.selection() : theme.bgBase());
          }
          sidebar.setCollapsed(false);
        }
      });
    } finally { Theme.setCurrent(previous); }
  }
}
