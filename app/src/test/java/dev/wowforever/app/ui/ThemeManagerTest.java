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
    assertEquals(Tokens.PARCHMENT, Theme.current());
    assertEquals(new Color(0xF3E9D2), Theme.current().bgBase());
    assertEquals(new Color(0x8A6D1E), Theme.current().accentGold());
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
    Theme p = Tokens.PARCHMENT;
    assertEquals(new Color(0xF3E9D2), rgb("Panel.background"));
    assertEquals(new Color(0xFBF5E6), rgb("Table.background"), "tables sit on raised");
    assertEquals(new Color(0xFBF5E6), rgb("List.background"));
    assertEquals(new Color(0xFBF5E6), rgb("TextField.background"));
    assertEquals(new Color(0xF6EEDC), rgb("Table.alternateRowColor"));
    assertEquals(new Color(0x1F4E8C), rgb("Component.linkColor"));
    assertEquals(p.selection(), rgb("Table.selectionBackground"));
    assertEquals(p.selection(), rgb("Table.selectionInactiveBackground"));
    assertEquals(p.textPrimary(), rgb("Table.selectionForeground"));
    assertEquals(p.textPrimary(), rgb("Table.selectionInactiveForeground"));
  }

  /** The pairs FlatLaf paints on a selected row: focused, then unfocused (FlatTableUI swaps them on focus). */
  @Test
  void slateTableSelectionTextReadsAtFourAndAHalfToOne() {
    ThemeManager.apply(ThemeManager.Mode.DARK);
    assertSelectedRowPairsRead();
  }

  /** The same painted pairs in Parchment, which was #FFFFFF on #EADFC0 (1.33:1) in the 2B light theme. */
  @Test
  void parchmentTableSelectionTextReadsAtFourAndAHalfToOne() {
    ThemeManager.apply(ThemeManager.Mode.LIGHT);
    assertSelectedRowPairsRead();
  }

  /** Lists keep FlatLaf's accent selection; their selected rows are drawn in the list's selection foreground. */
  @Test
  void listSelectionTextReadsAtFourAndAHalfToOneInBothThemes() {
    for (ThemeManager.Mode mode : ThemeManager.Mode.values()) {
      ThemeManager.apply(mode);
      assertTrue(Contrast.ratio(rgb("List.selectionForeground"), rgb("List.selectionBackground")) >= 4.5,
          mode.label() + " focused list selection");
      assertTrue(
          Contrast.ratio(rgb("List.selectionInactiveForeground"), rgb("List.selectionInactiveBackground")) >= 4.5,
          mode.label() + " unfocused list selection");
    }
  }

  private static void assertSelectedRowPairsRead() {
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
    assertEquals(Tokens.PARCHMENT.accentGold(), rgb("Component.focusColor"));
  }

  @Test
  void labelsAndSavedValues() {
    assertEquals("Slate", ThemeManager.Mode.DARK.label());
    assertEquals("Parchment", ThemeManager.Mode.LIGHT.label());
    prefs().put("theme", "light"); // saved by an earlier version, before Parchment
    assertEquals(ThemeManager.Mode.LIGHT, ThemeManager.current());
    ThemeManager.applySaved();
    assertEquals(Tokens.PARCHMENT, Theme.current());
    assertEquals(new Color(0xF3E9D2), rgb("Panel.background"));
    ThemeManager.apply(ThemeManager.Mode.LIGHT);
    assertEquals("light", prefs().get("theme", null), "the saved value stays light");
  }

  @Test
  void propertiesResourcesContainTokens() throws Exception {
    assertProps("WowForeverDarkLaf.properties", "#12161D", "#1B2027", "#D6B25E", "#9CC3E6", "#E0A83A");
    assertProps("WowForeverLightLaf.properties", "#F3E9D2", "#EADCBB", "#FBF5E6", "#8A6D1E", "#1F4E8C", "#F6EEDC");
  }

  private static void assertProps(String name, String... tokens) throws Exception {
    try (InputStream in = Theme.class.getResourceAsStream(name)) {
      assertNotNull(in, name);
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
      for (String t : tokens) assertTrue(text.contains(t.toUpperCase()), name + " missing " + t);
    }
  }
}
