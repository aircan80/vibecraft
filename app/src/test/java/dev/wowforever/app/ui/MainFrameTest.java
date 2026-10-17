package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.ViewTarget;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.Test;

/** {@link MainFrame}'s static parts; the frame itself is never created in tests (see {@link MainFrameContentTest}). */
class MainFrameTest {

  @Test
  void ctrlDPinsAndUnpinsTheEntityOnScreen() throws Exception {
    Preferences node = UiStateTest.testNode();
    try {
      UiState state = new UiState(node);
      ViewTarget item = new ViewTarget.Entity(new EntityRef(EntityKind.ITEM, 19019));
      assertThat(MainFrame.togglePin(state, item)).isEqualTo("Pinned");
      assertThat(state.pinned()).containsExactly("item:19019");
      assertThat(MainFrame.togglePin(state, item)).isEqualTo("Unpinned");
      assertThat(state.pinned()).isEmpty();
      assertThat(MainFrame.togglePin(state, new ViewTarget.Home())).isEqualTo(MainFrame.CANNOT_PIN);
      assertThat(MainFrame.togglePin(state, null)).isEqualTo(MainFrame.CANNOT_PIN);
      assertThat(state.pinned()).isEmpty();
    } finally {
      node.removeNode();
    }
  }

  @Test
  void aboutTextShowsNoticeAndDatabaseMeta() {
    Map<String, String> meta = new LinkedHashMap<>();
    meta.put("build_id", "b-42");
    meta.put("built_at", "2026-10-16T12:00:00Z");
    String text = MainFrame.aboutText(meta);
    assertThat(text).contains("QuestieDB").contains("AtlasLootClassic").contains("GPL-2.0")
        .contains("build_id: b-42").contains("built_at: 2026-10-16T12:00:00Z");
  }

  @Test
  void noticeOnTheClasspathMatchesTheRootNotice() throws Exception {
    String root = Files.readString(Path.of("..", "NOTICE.md"), StandardCharsets.UTF_8);
    String bundled;
    try (InputStream in = MainFrame.class.getResourceAsStream("/NOTICE.md")) {
      assertThat(in).isNotNull();
      bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    assertThat(bundled.replace("\r\n", "\n")).isEqualTo(root.replace("\r\n", "\n"));
  }
}
