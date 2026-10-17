package dev.wowforever.app.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatLaf;
import java.awt.Font;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class FontsTest {
  /** {@code install()} makes Inter FlatLaf's preferred family; later tests in this JVM get the default font back. */
  @AfterAll
  static void resetPreferredFont() {
    FlatLaf.setPreferredFontFamily(null);
  }

  @Test
  void marcellusLoadsFromResources() {
    Fonts.install();
    assertThat(Fonts.headingFamily()).isEqualTo("Marcellus");
    assertThat(Fonts.heading(20f).getFamily()).isEqualTo("Marcellus");
    assertThat(Fonts.heading(20f).getSize2D()).isEqualTo(20f);
    assertThat(Fonts.headingSmallCaps(14f).getFamily()).isEqualTo("Marcellus SC");
    assertThat(Fonts.headingSmallCaps(14f).getSize2D()).isEqualTo(14f);
  }

  @Test
  void installTwiceIsHarmless() {
    Fonts.install();
    Fonts.install();
    assertThat(Fonts.headingFamily()).isEqualTo("Marcellus");
  }

  @Test
  void fallsBackToCinzelThenGeorgia() {
    assertThat(Fonts.FALLBACKS).containsExactly("Cinzel", "Georgia", Font.SERIF);
    assertThat(Fonts.resolve(name -> true, true)).isEqualTo("Marcellus");
    assertThat(Fonts.resolve(name -> true, false)).isEqualTo("Cinzel");
    assertThat(Fonts.resolve(name -> name.equals("Georgia"), false)).isEqualTo("Georgia");
    assertThat(Fonts.resolve(n -> false, false)).isEqualTo(Font.SERIF);
  }

  @Test
  void oflLicenceShips() throws Exception {
    try (InputStream in = Fonts.class.getResourceAsStream("/dev/wowforever/app/ui/fonts/OFL.txt")) {
      assertThat(in).as("ui/fonts/OFL.txt").isNotNull();
      assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).contains("SIL OPEN FONT LICENSE");
    }
  }
}
