package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.Color;
import java.util.function.Function;

/**
 * The application's SVG icons ({@code dev/wowforever/app/ui/icons/<name>.svg}, 24×24 view box, one colour).
 * Every colour of the SVG is replaced at paint time by a token of the current {@link Theme}, so icons follow a
 * theme switch without being reloaded.
 */
public final class Icons {
  static final String DIR = "dev/wowforever/app/ui/icons/";

  private Icons() {}

  /** Icon {@code name} at {@code size} logical pixels, in the theme's primary text colour. */
  public static FlatSVGIcon icon(String name, int size) {
    return icon(name, size, Theme::textPrimary);
  }

  /** Icon {@code name} at {@code size} logical pixels, painted in {@code tint} of the current theme. */
  public static FlatSVGIcon icon(String name, int size, Function<Theme, Color> tint) {
    String path = DIR + name + ".svg";
    ClassLoader loader = Icons.class.getClassLoader();
    if (loader.getResource(path) == null) {
      throw new IllegalArgumentException("There is no icon " + name + " (" + path + ")");
    }
    FlatSVGIcon icon = new FlatSVGIcon(path, size, size, loader);
    icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> {
      Color t = tint.apply(Theme.current());
      return new Color(t.getRed(), t.getGreen(), t.getBlue(), c.getAlpha());
    }));
    return icon;
  }
}
