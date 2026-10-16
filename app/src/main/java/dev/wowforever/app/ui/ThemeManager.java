package dev.wowforever.app.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Component;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;

/** FlatLaf light or dark theme, remembered in the user's preferences (key {@code theme}: light or dark). */
public final class ThemeManager {
  static final String KEY = "theme";
  static final String LIGHT = "light";
  static final String DARK = "dark";

  private ThemeManager() {}

  /** Installs the saved theme (light when none is saved); call before creating any component. */
  public static void applySaved() {
    install(isDark());
  }

  /** Whether the saved theme is dark. */
  public static boolean isDark() {
    return DARK.equals(prefs().get(KEY, LIGHT));
  }

  /** Switches between light and dark, saves the choice and repaints {@code root}'s component tree. */
  public static void toggle(Component root) {
    boolean dark = !isDark();
    prefs().put(KEY, dark ? DARK : LIGHT);
    install(dark);
    SwingUtilities.updateComponentTreeUI(root);
  }

  private static void install(boolean dark) {
    if (dark) {
      FlatDarkLaf.setup();
    } else {
      FlatLightLaf.setup();
    }
  }

  private static Preferences prefs() {
    return Preferences.userNodeForPackage(ThemeManager.class);
  }
}
