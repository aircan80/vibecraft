package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class QuestKeyMapTest {
  @Test
  void pageKeysDeclaredAndNeverInstalledOnTheRoot() {
    assertThat(KeyMap.BINDINGS).filteredOn(b -> b.action().equals(KeyMap.QUEST_PAGE)).hasSize(4)
        .allSatisfy(b -> assertThat(KeyMap.installsOnRoot(b)).isFalse());
    var binding = KeyMap.BINDINGS.stream().filter(b -> b.action().equals(KeyMap.QUEST_PAGE)).findFirst().orElseThrow();
    assertThat(KeyMap.strokes(binding)).extracting(KeyMap.Stroke::key)
        .contains("typed g", "typed t", "typed o", "typed m", "typed c", "typed r", "typed 1", "typed 9",
            "typed [", "typed ]", "typed z");
  }

  @Test
  void pageKeysIgnoredInTextComponents() {
    JPanel page = new JPanel();
    AtomicReference<Component> focus = new AtomicReference<>(new JTextField());
    AtomicInteger calls = new AtomicInteger();
    Map<String, Runnable> handlers = new HashMap<>();
    for (String key : new String[]{"g", "t", "o", "m", "c", "r", "1", "9", "[", "]", "z"}) {
      handlers.put(key, calls::incrementAndGet);
    }
    KeyMap.installPageKeys(page, handlers, focus::get);
    for (Component text : new Component[]{new JTextField(), new JTextArea()}) {
      focus.set(text);
      for (Object name : page.getActionMap().allKeys()) {
        var action = page.getActionMap().get(name);
        assertThat(action.isEnabled()).isFalse();
        action.actionPerformed(new ActionEvent(page, 0, name.toString()));
      }
    }
    assertThat(calls).hasValue(0);
    focus.set(new JTable());
    Object key = page.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("typed G"));
    var action = page.getActionMap().get(key);
    assertThat(action.isEnabled()).isTrue();
    action.actionPerformed(new ActionEvent(page, 0, key.toString()));
    assertThat(calls).hasValue(1);
  }
}
