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
  void tabFeedbackIgnoresRestorationTypingAndEventsFromOtherControls() {
    var tabs=new javax.swing.JTabbedPane();tabs.addTab("One",new JPanel());tabs.addTab("Two",new JPanel());
    tabs.setSize(400,200);tabs.doLayout();tabs.setSelectedIndex(1);
    java.awt.Rectangle bounds=tabs.getBoundsAt(1);
    var click=new java.awt.event.MouseEvent(tabs,java.awt.event.MouseEvent.MOUSE_PRESSED,0,0,bounds.x+2,bounds.y+2,1,false);
    assertThat(MainFrame.userTabSelection(tabs,click)).isTrue();
    assertThat(MainFrame.userTabSelection(tabs,new java.awt.event.InvocationEvent(tabs,()->{}))).isFalse();
    assertThat(MainFrame.userTabSelection(tabs,new java.awt.event.KeyEvent(tabs,java.awt.event.KeyEvent.KEY_PRESSED,0,0,java.awt.event.KeyEvent.VK_RIGHT,' '))).isTrue();
    assertThat(MainFrame.userTabSelection(tabs,new java.awt.event.KeyEvent(tabs,java.awt.event.KeyEvent.KEY_PRESSED,0,0,java.awt.event.KeyEvent.VK_A,'a'))).isFalse();
    assertThat(MainFrame.userTabSelection(tabs,new java.awt.event.MouseEvent(new JPanel(),java.awt.event.MouseEvent.MOUSE_PRESSED,0,0,bounds.x+2,bounds.y+2,1,false))).isFalse();
    java.util.List<dev.wowforever.app.sound.UiSoundService.Cue> cues=new java.util.ArrayList<>();
    MainFrame.installTabSounds(tabs,cues::add);MainFrame.installTabSounds(tabs,cues::add);
    tabs.setSelectedIndex(0);assertThat(cues).isEmpty();
  }

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
