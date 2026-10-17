package dev.wowforever.app;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.ui.ThemeManager;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.Test;

/** The tests' settings stay in memory: none of them touches the user's real preferences. */
class PreferencesIsolationTest {

  @Test
  void theTestJvmKeepsPreferencesInMemory() {
    assertThat(System.getProperty("java.util.prefs.PreferencesFactory"))
        .isEqualTo(InMemoryPreferencesFactory.class.getName());
    assertThat(Preferences.userRoot()).isInstanceOf(InMemoryPreferencesFactory.Node.class);
    assertThat(Preferences.userNodeForPackage(ThemeManager.class)).isInstanceOf(InMemoryPreferencesFactory.Node.class);
  }

  @Test
  void nodesKeepValuesAndCanBeRemoved() throws BackingStoreException {
    Preferences node = Preferences.userRoot().node("dev/wowforever/isolation");
    node.put("k", "v");
    node.putInt("n", 3);
    assertThat(Preferences.userRoot().node("dev/wowforever/isolation").get("k", null)).isEqualTo("v");
    assertThat(node.getInt("n", 0)).isEqualTo(3);
    assertThat(node.keys()).containsExactlyInAnyOrder("k", "n");
    assertThat(Preferences.userRoot().node("dev/wowforever").childrenNames()).contains("isolation");
    node.removeNode();
    assertThat(Preferences.userRoot().nodeExists("dev/wowforever/isolation")).isFalse();
    assertThat(Preferences.userRoot().node("dev/wowforever/isolation").get("k", null)).isNull();
  }
}
