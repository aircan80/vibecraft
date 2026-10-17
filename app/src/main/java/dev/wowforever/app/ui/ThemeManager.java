package dev.wowforever.app.ui;

import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.theme.Tokens;
import dev.wowforever.app.ui.theme.WowForeverDarkLaf;
import dev.wowforever.app.ui.theme.WowForeverLightLaf;
import java.awt.Component;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;

/**
 * The Slate (dark, the default) or Parchment (light) theme, remembered in the user's preferences (key
 * {@code theme}). The saved values are still {@code dark} and {@code light}, so a choice saved before Parchment
 * replaced the 2B light theme keeps working.
 */
public final class ThemeManager {
  /** The two theme modes. */
  public enum Mode {
    DARK("Slate"),
    LIGHT("Parchment");

    private final String label;

    Mode(String label) {
      this.label = label;
    }

    /** The theme's name as the menus show it: "Slate" or "Parchment". */
    public String label() {
      return label;
    }
  }

  static final String KEY = "theme";
  static final String LIGHT = "light";
  static final String DARK = "dark";

  private ThemeManager() {}

  /** Installs the saved theme (dark when none is saved); call before creating any component. */
  public static void applySaved() {
    install(current());
  }

  /** The saved mode; dark when nothing is saved. */
  public static Mode current() {
    return LIGHT.equals(prefs().get(KEY, DARK)) ? Mode.LIGHT : Mode.DARK;
  }

  /** Whether the saved theme is dark. */
  public static boolean isDark() {
    return current() == Mode.DARK;
  }

  /** Saves and installs {@code mode}: the look and feel and the active token palette. */
  public static void apply(Mode mode) {
    prefs().put(KEY, mode == Mode.DARK ? DARK : LIGHT);
    install(mode);
  }

  /** Switches between Parchment and Slate, saves the choice and repaints {@code root}'s component tree. */
  public static void toggle(Component root) {
    apply(isDark() ? Mode.LIGHT : Mode.DARK);
    SwingUtilities.updateComponentTreeUI(root);
  }

  private static void install(Mode mode) {
    if (mode == Mode.DARK) {
      WowForeverDarkLaf.setup();
      Theme.setCurrent(Tokens.DARK);
    } else {
      WowForeverLightLaf.setup();
      Theme.setCurrent(Tokens.PARCHMENT);
    }
  }

  private static Preferences prefs() {
    return Preferences.userNodeForPackage(ThemeManager.class);
  }
}
