package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wowforever.app.ui.detail.LinkTable;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class KeyMapTest {
  private final List<String> ran = new ArrayList<>();
  private final AtomicReference<Component> focusOwner = new AtomicReference<>();

  /** A recording handler for every root action. */
  private Map<String, Runnable> handlers() {
    Map<String, Runnable> h = new HashMap<>();
    KeyMap.ROOT_HANDLERS.forEach(name -> h.put(name, () -> ran.add(name)));
    return h;
  }

  private JRootPane installed() {
    JRootPane root = new JRootPane();
    KeyMap.install(root, handlers(), focusOwner::get);
    return root;
  }

  private static boolean press(JComponent c, int condition, String key) {
    Object name = c.getInputMap(condition).get(KeyStroke.getKeyStroke(key));
    Action action = name == null ? null : c.getActionMap().get(name);
    if (action == null || !action.isEnabled()) {
      return false;
    }
    action.actionPerformed(new ActionEvent(c, ActionEvent.ACTION_PERFORMED, key));
    return true;
  }

  @Test
  void theBindingListIsTheReviewKeyboardMap() {
    assertThat(KeyMap.BINDINGS).extracting(KeyMap.Binding::keys).containsExactly(
        "Ctrl+F, Ctrl+L, / outside text fields",
        "Esc",
        "Alt+Left/Right, mouse 4/5",
        "Up/Down, Enter",
        "Ctrl+1…6",
        "Ctrl+C / Ctrl+Shift+C on a row",
        "Ctrl+B",
        "F10, Ctrl+Shift+T",
        "Ctrl+= / Ctrl+- / Ctrl+0");
    assertThat(KeyMap.BINDINGS).extracting(KeyMap.Binding::description).containsExactly(
        "Focus search",
        "Close dropdown, then clear search, then focus list",
        "Back / forward",
        "Preview, open",
        "Jump to source section",
        "Copy name / copy item:ID or /way",
        "Toggle results list",
        "Compact mode, always on top",
        "Text size");
  }

  @Test
  void helpTableRowsEqualTheBindingList() {
    JTable help = KeyMap.helpTable();
    assertThat(help.getColumnCount()).isEqualTo(2);
    assertThat(help.getColumnName(0)).isEqualTo("Keys");
    assertThat(help.getColumnName(1)).isEqualTo("Action");
    assertThat(help.getRowCount()).isEqualTo(KeyMap.BINDINGS.size());
    for (int r = 0; r < help.getRowCount(); r++) {
      assertThat(help.getValueAt(r, 0)).isEqualTo(KeyMap.BINDINGS.get(r).keys());
      assertThat(help.getValueAt(r, 1)).isEqualTo(KeyMap.BINDINGS.get(r).description());
      assertThat(help.isCellEditable(r, 0)).isFalse();
    }
  }

  @Test
  void documentedOnlyRowsAreTheOnesBoundElsewhere() {
    assertThat(KeyMap.BINDINGS).filteredOn(b -> b.action().equals(KeyMap.DOCUMENTED_ONLY))
        .extracting(KeyMap.Binding::keys).containsExactly("Up/Down, Enter", "Ctrl+1…6");
    assertThat(KeyMap.strokes(KeyMap.BINDINGS.get(3))).isEmpty();
  }

  @Test
  void everyInstallableBindingIsInTheRootInputMap() {
    JRootPane root = installed();
    InputMap keys = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    int strokes = 0;
    for (KeyMap.Binding b : KeyMap.BINDINGS) {
      if (!KeyMap.installsOnRoot(b)) {
        continue;
      }
      assertThat(KeyMap.strokes(b)).as(b.keys()).isNotEmpty();
      for (KeyMap.Stroke s : KeyMap.strokes(b)) {
        KeyStroke ks = KeyStroke.getKeyStroke(s.key());
        assertThat(ks).as(s.key()).isNotNull();
        assertThat(keys.get(ks)).as(s.key()).isEqualTo(s.handler());
        assertThat(root.getActionMap().get(s.handler())).as(s.handler()).isNotNull();
        strokes++;
      }
    }
    assertThat(keys.allKeys()).hasSize(strokes); // the documented-only and row rows install nothing here
  }

  @Test
  void eachKeyRunsItsHandler() {
    JRootPane root = installed();
    int when = JComponent.WHEN_IN_FOCUSED_WINDOW;
    for (String key : List.of("ctrl F", "ctrl L", "ESCAPE", "alt LEFT", "alt RIGHT", "ctrl B", "F10",
        "shift ctrl T", "ctrl EQUALS", "ctrl MINUS", "ctrl 0")) {
      assertThat(press(root, when, key)).as(key).isTrue();
    }
    assertThat(ran).containsExactly(KeyMap.FOCUS_SEARCH, KeyMap.FOCUS_SEARCH, KeyMap.ESCAPE, KeyMap.BACK,
        KeyMap.FORWARD, KeyMap.TOGGLE_LIST, KeyMap.COMPACT_MODE, KeyMap.ALWAYS_ON_TOP,
        KeyMap.TEXT_BIGGER, KeyMap.TEXT_SMALLER, KeyMap.TEXT_RESET);
  }

  @Test
  void numpadAndShiftedTextSizeKeysWorkToo() {
    JRootPane root = installed();
    int when = JComponent.WHEN_IN_FOCUSED_WINDOW;
    for (String key : List.of("shift ctrl EQUALS", "ctrl PLUS", "ctrl ADD", "ctrl SUBTRACT", "ctrl NUMPAD0")) {
      assertThat(press(root, when, key)).as(key).isTrue();
    }
    assertThat(ran).containsExactly(KeyMap.TEXT_BIGGER, KeyMap.TEXT_BIGGER, KeyMap.TEXT_BIGGER, KeyMap.TEXT_SMALLER,
        KeyMap.TEXT_RESET);
  }

  @Test
  void slashFocusesSearchOnlyOutsideTextFields() {
    JRootPane root = installed();
    int when = JComponent.WHEN_IN_FOCUSED_WINDOW;
    focusOwner.set(new JTextField());
    assertThat(press(root, when, "typed /")).isFalse(); // the key goes on to the field: a slash is typed
    focusOwner.set(new JTable());
    assertThat(press(root, when, "typed /")).isTrue();
    focusOwner.set(null);
    assertThat(press(root, when, "typed /")).isTrue();
    assertThat(ran).containsExactly(KeyMap.FOCUS_SEARCH, KeyMap.FOCUS_SEARCH);
  }

  @Test
  void installNeedsAHandlerForEveryRootAction() {
    Map<String, Runnable> missing = handlers();
    missing.remove(KeyMap.TOGGLE_LIST);
    assertThatThrownBy(() -> KeyMap.install(new JRootPane(), missing, focusOwner::get))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(KeyMap.TOGGLE_LIST);
  }

  @Test
  void ctrlDIsNotBoundUntilPinnedPagesHaveAView() {
    assertThat(press(installed(), JComponent.WHEN_IN_FOCUSED_WINDOW, "ctrl D")).isFalse();
    assertThat(KeyMap.markdownTable()).doesNotContain("Ctrl+D").doesNotContain("Pin");
  }

  @Test
  void rowCopyKeysAreBoundOnLinkTables() {
    LinkTable<?> table = new LinkTable<>(Columns.items(), t -> {});
    InputMap keys = table.table().getInputMap(JComponent.WHEN_FOCUSED);
    KeyMap.Binding rowCopy = KeyMap.BINDINGS.get(5);
    assertThat(rowCopy.action()).isEqualTo(KeyMap.ROW_COPY);
    assertThat(KeyMap.strokes(rowCopy)).extracting(KeyMap.Stroke::handler)
        .containsExactly(KeyMap.COPY_NAME, KeyMap.COPY_ID);
    for (KeyMap.Stroke s : KeyMap.strokes(rowCopy)) {
      assertThat(keys.get(KeyStroke.getKeyStroke(s.key()))).as(s.key()).isEqualTo(s.handler());
      assertThat(table.table().getActionMap().get(s.handler())).isNotNull();
    }
  }
}
