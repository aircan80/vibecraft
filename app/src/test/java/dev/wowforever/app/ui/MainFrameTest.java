package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

/** {@link MainFrame}'s static parts; the frame itself is never created in tests (see {@link MainFrameContentTest}). */
class MainFrameTest {

  @Test
  void onlyJournalCanConstructTheProductionShell() {
    assertThat(MainFrame.supportedExperience(UiExperience.JOURNAL)).isEqualTo(UiExperience.JOURNAL);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> MainFrame.supportedExperience(UiExperience.CLASSIC))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("retired");
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> MainFrame.supportedExperience(null))
        .isInstanceOf(IllegalArgumentException.class);
  }


  @Test
  void focusStaysInThePageOnlyWhenItsOwnerSitsInTheHolder() {
    JPanel holder = new JPanel();
    JPanel page = new JPanel();
    JTable table = new JTable();
    page.add(table);
    holder.add(page);
    JTextField elsewhere = new JTextField();
    assertThat(MainFrame.focusStaysInPage(table, holder)).isTrue();
    assertThat(MainFrame.focusStaysInPage(elsewhere, holder)).isFalse();
    assertThat(MainFrame.focusStaysInPage(null, holder)).isFalse();
  }

  @Test
  void aFreshPageTakesTheFocusOnItsFirstTableOrItself() {
    JPanel page = new JPanel();
    assertThat(MainFrame.focusTargetIn(page)).isSameAs(page);
    JTable table = new JTable();
    page.add(new JPanel());
    page.add(table);
    assertThat(MainFrame.focusTargetIn(page)).isSameAs(table);
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
