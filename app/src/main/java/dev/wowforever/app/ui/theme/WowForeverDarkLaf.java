package dev.wowforever.app.ui.theme;

import com.formdev.flatlaf.FlatDarkLaf;

/** FlatLaf dark with the app tokens; overrides live in WowForeverDarkLaf.properties. */
public class WowForeverDarkLaf extends FlatDarkLaf {
  public static final String NAME = "WowForever Dark";

  public static boolean setup() {
    return setup(new WowForeverDarkLaf());
  }

  @Override
  public String getName() {
    return NAME;
  }
}
