package dev.wowforever.app.ui.pages;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ObjectiveRow;
import dev.wowforever.app.model.ObjectiveVerb;
import dev.wowforever.app.model.QuestObjective;
import dev.wowforever.app.model.QuestPageData;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.pages.quest.QuestTestData;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class QuestPageKeysTest {
  private static boolean press(QuestPage page, String key) {
    Object name = page.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("typed " + key));
    var action = name == null ? null : page.getActionMap().get(name);
    if (action == null || !action.isEnabled()) return false;
    action.actionPerformed(new ActionEvent(page, 0, key));
    return true;
  }

  private static void layout(Container parent) {
    parent.doLayout();
    for (Component child : parent.getComponents()) if (child instanceof Container c) layout(c);
  }

  @Test
  void typingInSearchDoesNotJumpOrFocusMap() {
    var loaded = QuestTestData.load(166);
    QuestPage page = new QuestPage(loaded.data(), loaded.maps(), t -> {}, text -> {});
    JTextField field = new JTextField();
    page.installPageKeys(() -> field);
    for (char c : "Hogger".toCharArray()) assertThat(press(page, String.valueOf(c))).isFalse();
    assertThat(page.mapPanel().hasFocusFilter()).isFalse();
    assertThat(page.scrollPane().getVerticalScrollBar().getValue()).isZero();
  }

  @Test
  void lettersJumpAndBracketsWalkTheChain() {
    var loaded = QuestTestData.load(166);
    List<ViewTarget> opened = new ArrayList<>();
    QuestPage page = new QuestPage(loaded.data(), loaded.maps(), opened::add, text -> {});
    page.installPageKeys(JTable::new);
    page.setSize(1216, 680);
    for (int i = 0; i < 5; i++) layout(page);
    press(page, "g");
    int giverScroll = page.scrollPane().getVerticalScrollBar().getValue();
    assertThat(giverScroll).isPositive();
    press(page, "c");
    assertThat(page.scrollPane().getVerticalScrollBar().getValue()).isGreaterThanOrEqualTo(giverScroll);
    press(page, "["); press(page, "]");
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(EntityKind.QUEST, 155)));
  }

  @Test
  void compactHeaderKeepsTheCopyActionFullyVisible() {
    var loaded = QuestTestData.load(166);
    var page = new QuestPage(loaded.data(), loaded.maps(), t -> {}, text -> {});
    javax.swing.JPanel host = new javax.swing.JPanel(new java.awt.BorderLayout());
    host.putClientProperty(dev.wowforever.app.ui.detail.CompactColumns.KEY, true);
    host.add(page); host.setSize(420, 720);
    for (int i = 0; i < 6; i++) layout(host);
    var button = page.copyInGameLinkButton();
    assertThat(button.getVisibleRect().width).isEqualTo(button.getWidth());
    assertThat(button.getVisibleRect().height).isEqualTo(button.getHeight());
  }

  @Test
  void digitFocusAndEscapeArePageLocal() {
    var loaded = QuestTestData.load(452);
    QuestPage page = new QuestPage(loaded.data(), loaded.maps(), t -> {}, text -> {});
    page.installPageKeys(JTable::new);
    assertThat(press(page, "1")).isTrue();
    assertThat(page.mapPanel().focusedObjective()).isEqualTo(1);
    assertThat(page.escape()).isTrue();
    assertThat(page.mapPanel().focusedObjective()).isNull();
    assertThat(page.escape()).isFalse();
  }

  @Test
  void zCyclesZoneTabsAndCopyButtonUsesClipboard() {
    var loaded = QuestTestData.load(176);
    var extra = new ObjectiveRow(2, ObjectiveVerb.EXPLORE, List.of(), null, "Explore Westfall", List.of(),
        List.of(new QuestObjective.Point(40, 50, 50)), null);
    var rows = new ArrayList<>(loaded.data().objectives()); rows.add(extra);
    var old = loaded.data();
    QuestPageData data = new QuestPageData(old.detail(), old.facts(), rows, old.sourceItems(), old.starts(), old.ends(), old.chain());
    List<String> copied = new ArrayList<>();
    var maps = new java.util.HashMap<>(loaded.maps());
    maps.put(40L, new BufferedImage(1002, 668, BufferedImage.TYPE_INT_RGB));
    QuestPage page = new QuestPage(data, maps, Map.of(12L, "Elwynn Forest", 40L, "Westfall"), t -> {}, copied::add);
    page.installPageKeys(JTable::new);
    assertThat(page.mapPanel().shownZone()).isEqualTo(12);
    press(page, "z"); assertThat(page.mapPanel().shownZone()).isEqualTo(40);
    press(page, "z"); assertThat(page.mapPanel().shownZone()).isEqualTo(12);
    page.copyInGameLinkButton().doClick();
    assertThat(copied).containsExactly("|Hquest:176:11|h[Wanted:  \"Hogger\"]|h");
  }
}
