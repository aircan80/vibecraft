package dev.wowforever.app.ui.theme;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import java.awt.AWTError;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The app's typography. Headings use Marcellus and Marcellus SC (SIL OFL 1.1, bundled under {@code ui/fonts/}); body
 * text, tables and lists use Inter through FlatLaf's preferred font family. When a bundled heading font cannot be
 * loaded the headings fall back to the first installed family of {@link #FALLBACKS}; nothing here ever throws.
 */
public final class Fonts {
  private static final Logger LOG = LoggerFactory.getLogger(Fonts.class);

  /** The heading family when both bundled fonts load. */
  public static final String MARCELLUS = "Marcellus";

  /** Heading families tried in order when Marcellus is missing; {@link Font#SERIF} is a logical font, always there. */
  public static final List<String> FALLBACKS = List.of("Cinzel", "Georgia", Font.SERIF);

  private static final String DIR = "/dev/wowforever/app/ui/fonts/";

  private static volatile Font marcellus;
  private static volatile Font marcellusSc;
  private static volatile String family;
  /** The fallback family before {@link #install()}, resolved once: enumerating the system fonts is slow. */
  private static volatile String fallback;
  private static boolean installed;

  private Fonts() {}

  /**
   * Registers the bundled heading fonts with the {@link GraphicsEnvironment} and makes Inter FlatLaf's preferred font
   * family. Call before the look and feel is set up; later calls do nothing.
   */
  public static synchronized void install() {
    if (installed) {
      return;
    }
    installed = true;
    marcellus = load("Marcellus-Regular.ttf");
    marcellusSc = load("MarcellusSC-Regular.ttf");
    family = resolve(Fonts::isInstalled, marcellus != null && marcellusSc != null);
    try {
      FlatInterFont.installLazy();
      FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY);
    } catch (RuntimeException | LinkageError | AWTError e) {
      LOG.warn("Inter could not be installed; body text keeps the default font", e);
    }
  }

  /** A Marcellus heading font of {@code pt} points, or the fallback family. */
  public static Font heading(float pt) {
    return derive(marcellus, pt);
  }

  /** A Marcellus SC (small caps) heading font of {@code pt} points, or the fallback family. */
  public static Font headingSmallCaps(float pt) {
    return derive(marcellusSc, pt);
  }

  /** The family of {@link #heading}: {@value #MARCELLUS} once installed, otherwise a fallback. */
  public static String headingFamily() {
    String f = family;
    if (f != null) {
      return f;
    }
    String fb = fallback;
    if (fb == null) {
      fb = resolve(Fonts::isInstalled, false);
      fallback = fb;
    }
    return fb;
  }

  /**
   * The heading family: Marcellus when the bundled fonts loaded ({@code resourcesLoaded}), otherwise the first of
   * {@link #FALLBACKS} that {@code available} accepts, and {@link Font#SERIF} when none does.
   */
  public static String resolve(Predicate<String> available, boolean resourcesLoaded) {
    if (resourcesLoaded) {
      return MARCELLUS;
    }
    for (String name : FALLBACKS) {
      if (available.test(name)) {
        return name;
      }
    }
    return Font.SERIF;
  }

  private static Font derive(Font loaded, float pt) {
    if (loaded != null && MARCELLUS.equals(family)) {
      return loaded.deriveFont(Font.PLAIN, pt);
    }
    return new Font(headingFamily(), Font.PLAIN, Math.max(1, Math.round(pt))).deriveFont(pt);
  }

  /** Loads and registers {@code file} from {@code ui/fonts/}; {@code null} (and a log line) when it fails. */
  private static Font load(String file) {
    try (InputStream in = Fonts.class.getResourceAsStream(DIR + file)) {
      if (in == null) {
        LOG.warn("Font resource {} is missing; headings fall back to {}", file, FALLBACKS);
        return null;
      }
      Font font = Font.createFont(Font.TRUETYPE_FONT, in);
      GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
      return font;
    } catch (IOException | FontFormatException | RuntimeException | LinkageError | AWTError e) {
      LOG.warn("Font {} could not be loaded; headings fall back to {}", file, FALLBACKS, e);
      return null;
    }
  }

  private static boolean isInstalled(String name) {
    if (Font.SERIF.equals(name)) {
      return true;
    }
    try {
      Set<String> names = new HashSet<>(
          Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
      return names.contains(name);
    } catch (RuntimeException | LinkageError | AWTError e) {
      return false;
    }
  }
}
