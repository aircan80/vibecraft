package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.BorderLayout;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import org.junit.jupiter.api.Test;

/** {@link MainFrame}'s static parts; the frame itself is never created in tests. */
class MainFrameTest {

  @Test
  void buildContentLaysOutToolbarSplitsAndStatus() {
    JPanel toolbar = new JPanel();
    JPanel tree = new JPanel();
    ResultsPanel results = new ResultsPanel(t -> {});
    JPanel detail = new JPanel();
    StatusBar status = new StatusBar();
    JPanel content = MainFrame.buildContent(toolbar, tree, results, detail, status);
    BorderLayout layout = (BorderLayout) content.getLayout();
    assertThat(layout.getLayoutComponent(BorderLayout.NORTH)).isSameAs(toolbar);
    assertThat(layout.getLayoutComponent(BorderLayout.SOUTH)).isSameAs(status);
    JSplitPane main = (JSplitPane) layout.getLayoutComponent(BorderLayout.CENTER);
    assertThat(main.getLeftComponent()).isSameAs(tree);
    assertThat(main.getDividerLocation()).isEqualTo(240);
    JSplitPane right = (JSplitPane) main.getRightComponent();
    assertThat(right.getLeftComponent()).isSameAs(results);
    assertThat(right.getRightComponent()).isSameAs(detail);
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
