package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.ViewTarget;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class JournalSidebarTest {
  @Test void keepsDestinationAndAccessibleNameWhenCollapsed() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      List<ViewTarget> visited = new ArrayList<>();
      JournalSidebar sidebar = new JournalSidebar(visited::add);
      JButton dungeons = button(sidebar, "Dungeons & raids");
      sidebar.setCollapsed(true);
      assertThat(dungeons.getText()).isEmpty();
      assertThat(dungeons.getAccessibleContext().getAccessibleName()).isEqualTo("Dungeons & raids");
      dungeons.doClick();
      assertThat(visited).containsExactly(new ViewTarget.List(EntityKind.INSTANCE));
      sidebar.setReady(false);
      assertThat(dungeons.isEnabled()).isFalse();
      sidebar.setCollapsed(false);
      assertThat(dungeons.getText()).isEqualTo("Dungeons & raids");
    });
  }

  @Test void routeSelectionIncludesDetailAndSubsectionPages() {
    assertThat(JournalSidebar.matches(new ViewTarget.List(EntityKind.INSTANCE),
        new ViewTarget.Entity(new EntityRef(EntityKind.BOSS, 12)))).isTrue();
    assertThat(JournalSidebar.matches(new ViewTarget.Forever("talents"), new ViewTarget.Forever("talents"))).isTrue();
    assertThat(JournalSidebar.matches(new ViewTarget.Forever("talents"), new ViewTarget.Forever("hub"))).isFalse();
    assertThat(JournalSidebar.matches(new ViewTarget.ItemContent("recipes"), new ViewTarget.ItemContent("set", 4))).isTrue();
    assertThat(JournalSidebar.matches(new ViewTarget.WorldMap(947), new ViewTarget.List(EntityKind.QUEST))).isFalse();
  }

  private JButton button(JournalSidebar sidebar, String name) {
    for (Component c : sidebar.getComponents())
      if (c instanceof JButton b && name.equals(b.getAccessibleContext().getAccessibleName())) return b;
    throw new AssertionError("Missing navigation button " + name);
  }
}
