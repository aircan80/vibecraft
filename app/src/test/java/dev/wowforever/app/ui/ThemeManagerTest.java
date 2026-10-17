package dev.wowforever.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wowforever.app.ui.theme.Contrast;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.theme.Tokens;
import java.awt.Color;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.prefs.Preferences;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThemeManagerTest {
  private String saved;

  @BeforeEach
  void save() {
    saved = prefs().get("theme", null);
  }

  @AfterEach
  void restore() {
    if (saved == null) prefs().remove("theme");
    else prefs().put("theme", saved);
    ThemeManager.applySaved();
  }

  private static Preferences prefs() {
    return Preferences.userNodeForPackage(ThemeManager.class);
  }

  private static Color rgb(String key) {
    return new Color(UIManager.getColor(key).getRGB());
  }

  @Test
  void defaultsToDarkWithoutPreference() {
    prefs().remove("theme");
    assertTrue(ThemeManager.isDark());
    assertEquals(ThemeManager.Mode.DARK, ThemeManager.current());
  }

  @Test
  void preferenceRoundTrip() {
    ThemeManager.apply(ThemeManager.Mode.LIGHT);
    assertEquals("light", prefs().get("theme", null));
    assertFalse(ThemeManager.isDark());
    ThemeManager.apply(ThemeManager.Mode.DARK);
    assertEquals("dark", prefs().get("theme", null));
    assertTrue(ThemeManager.isDark());
  }

  @Test
  void themeCurrentFlipsWithMode() {
    ThemeManager.apply(ThemeManager.Mode.DARK);
    assertEquals(Tokens.DARK, Theme.current());
    assertEquals(new Color(0x12161D), Theme.current().bgBase());
    ThemeManager.apply(ThemeManager.Mode.LIGHT);
    assertEquals(Tokens.LIGHT, Theme.current());
    assertEquals(new Color(0xF4F5F7), Theme.current().bgBase());
    assertEquals(new Color(0x9A7419), Theme.current().accentGold());
  }

  @Test
  void lafUsesTokens() {
    ThemeManager.apply(ThemeManager.Mode.DARK);
    assertEquals(new Color(0x12161D), rgb("Panel.background"));
    assertEquals(new Color(0x1B2027), rgb("Table.background"));
    assertEquals(new Color(0x9CC3E6), rgb("Component.linkColor"));
    assertEquals(2, UIManager.getInt("Component.focusWidth"));
    assertEquals(Tokens.DARK.altRow(), rgb("Table.alternateRowColor"));
    assertEquals(Tokens.DARK.selection(), rgb("Table.selectionBackground"));
    assertEquals(Tokens.DARK.selection(), rgb("Table.selectionInactiveBackground"));
    assertEquals(Tokens.DARK.textPrimary(), rgb("Table.selectionForeground"));
    assertEquals(Tokens.DARK.textPrimary(), rgb("Table.selectionInactiveForeground"));
    ThemeManager.apply(ThemeManager.Mode.LIGHT);
    assertEquals(new Color(0xF4F5F7), rgb("Panel.background"));
    assertEquals(new Color(0xFFFFFF), rgb("Table.background"));
  }

  /** The pairs FlatLaf paints on a selected row: focused, then unfocused (FlatTableUI swaps them on focus). */
  @Test
  void slateTableSelectionTextReadsAtFourAndAHalfToOne() {
    ThemeManager.apply(ThemeManager.Mode.DARK);
    assertTrue(Contrast.ratio(rgb("Table.selectionForeground"), rgb("Table.selectionBackground")) >= 4.5,
        "focused table selection");
    assertTrue(
        Contrast.ratio(rgb("Table.selectionInactiveForeground"), rgb("Table.selectionInactiveBackground")) >= 4.5,
        "unfocused table selection");
  }

  @Test
  void focusRingIsTwoPixelsOfGoldInBothThemes() {
    ThemeManager.apply(ThemeManager.Mode.DARK);
    assertEquals(2, UIManager.getInt("Component.focusWidth"));
    assertEquals(Tokens.DARK.accentGold(), rgb("Component.focusColor"));
    ThemeManager.apply(ThemeManager.Mode.LIGHT);
    assertEquals(2, UIManager.getInt("Component.focusWidth"));
    assertEquals(Tokens.LIGHT.accentGold(), rgb("Component.focusColor"));
  }

  @Test
  void propertiesResourcesContainTokens() throws Exception {
    assertProps("WowForeverDarkLaf.properties", "#12161D", "#1B2027", "#D6B25E", "#9CC3E6", "#E0A83A");
    assertProps("WowForeverLightLaf.properties", "#F4F5F7", "#FFFFFF", "#9A7419", "#1F5FA8", "#9A6B00");
  }

  private static void assertProps(String name, String... tokens) throws Exception {
    try (InputStream in = Theme.class.getResourceAsStream(name)) {
      assertNotNull(in, name);
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
      for (String t : tokens) assertTrue(text.contains(t.toUpperCase()), name + " missing " + t);
    }
  }
}
