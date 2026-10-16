package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class MouseNavigationTest {
  private final List<String> calls = new ArrayList<>();

  private MouseNavigation nav(Predicate<Component> isAppWindow) {
    return MouseNavigation.create(() -> calls.add("back"), () -> calls.add("forward"), isAppWindow);
  }

  private static MouseEvent event(Component source, int id, int button) {
    // A headless toolkit rejects extra buttons in the constructor, so report the button by override.
    return new MouseEvent(source, id, 0L, 0, 1, 1, 1, false, MouseEvent.BUTTON1) {
      @Override
      public int getButton() {
        return button;
      }
    };
  }

  @Test
  void button4GoesBackAndButton5GoesForwardAndTheEventIsConsumed() {
    MouseNavigation nav = nav(c -> true);
    JPanel panel = new JPanel();
    MouseEvent back = event(panel, MouseEvent.MOUSE_RELEASED, 4);
    MouseEvent forward = event(panel, MouseEvent.MOUSE_RELEASED, 5);
    assertThat(nav.handle(back)).isTrue();
    assertThat(nav.handle(forward)).isTrue();
    assertThat(calls).containsExactly("back", "forward");
    assertThat(back.isConsumed()).isTrue();
    assertThat(forward.isConsumed()).isTrue();
  }

  @Test
  void textComponentsKeepTheirOwnBehaviour() {
    MouseNavigation nav = nav(c -> true);
    assertThat(nav.handle(event(new JTextField(), MouseEvent.MOUSE_RELEASED, 4))).isFalse();
    assertThat(calls).isEmpty();
  }

  @Test
  void otherWindowsAreIgnored() {
    MouseNavigation nav = nav(c -> false);
    assertThat(nav.handle(event(new JPanel(), MouseEvent.MOUSE_RELEASED, 5))).isFalse();
    assertThat(calls).isEmpty();
  }

  @Test
  void otherButtonsAndEventsAreIgnored() {
    MouseNavigation nav = nav(c -> true);
    JPanel panel = new JPanel();
    assertThat(nav.handle(event(panel, MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1))).isFalse();
    assertThat(nav.handle(event(panel, MouseEvent.MOUSE_PRESSED, 4))).isFalse();
    assertThat(nav.handle(event(panel, MouseEvent.MOUSE_CLICKED, 5))).isFalse();
    assertThat(calls).isEmpty();
  }


  @Test
  void aSourceWithoutAWindowIsNotInTheAppWindow() {
    assertThat(MouseNavigation.sameWindow(new JPanel(), new JPanel())).isFalse();
  }
}
