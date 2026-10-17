package dev.wowforever.app.ui.theme;

import com.formdev.flatlaf.FlatLightLaf;

/** FlatLaf light with the app tokens; overrides live in WowForeverLightLaf.properties. */
public class WowForeverLightLaf extends FlatLightLaf {
  public static final String NAME = "WowForever Light";

  public static boolean setup() {
    return setup(new WowForeverLightLaf());
  }

  @Override
  public String getName() {
    return NAME;
  }
}
