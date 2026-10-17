package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.ui.UiState;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.JList;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class SourceFilterControlTest {

  @Test
  void changesFilterAndRerenders() throws BackingStoreException {
    Preferences node = Preferences.userRoot().node("dev/wowforever/test/ui-state-" + java.util.UUID.randomUUID());
    try {
      UiState state = new UiState(node);
      List<SourceFilter> rerendered = new ArrayList<>();
      var control = new SourceFilterControl(state.sourceFilter(), f -> {
        state.setSourceFilter(f); // what the controller does: save, then show the page again
        rerendered.add(f);
      });
      assertThat(control.getSelectedItem()).isEqualTo(SourceFilter.ALL);
      control.setSelectedItem(SourceFilter.HIDE_ATLASLOOT_ONLY);
      assertThat(rerendered).containsExactly(SourceFilter.HIDE_ATLASLOOT_ONLY);
      assertThat(new UiState(node).sourceFilter()).as("round-trips").isEqualTo(SourceFilter.HIDE_ATLASLOOT_ONLY);
      assertThat(new SourceFilterControl(new UiState(node).sourceFilter(), f -> {}).getSelectedItem())
          .isEqualTo(SourceFilter.HIDE_ATLASLOOT_ONLY);
      control.setSelectedItem(SourceFilter.ALL); // back to the page's own filter: nothing to show again
      assertThat(rerendered).containsExactly(SourceFilter.HIDE_ATLASLOOT_ONLY, SourceFilter.ALL);
    } finally {
      node.removeNode();
    }
  }

  @Test
  void choosingThePagesOwnFilterDoesNothing() {
    List<SourceFilter> changes = new ArrayList<>();
    var control = new SourceFilterControl(SourceFilter.HIDE_QUESTIE_ONLY, changes::add);
    control.setSelectedItem(SourceFilter.HIDE_QUESTIE_ONLY);
    assertThat(changes).isEmpty();
    assertThat(control.current()).isEqualTo(SourceFilter.HIDE_QUESTIE_ONLY);
  }

  @Test
  void theTooltipIsTheRateLegendAndTheItemsReadAsTheFilters() {
    var control = new SourceFilterControl(SourceFilter.ALL, f -> {});
    assertThat(control.getToolTipText()).isEqualTo(SourceFilterControl.LEGEND)
        .isEqualTo("≠ The sources disagree on this rate; hover the rate to see each source.");
    assertThat(control.getAccessibleContext().getAccessibleName()).isEqualTo("Drop sources");
    List<String> labels = new ArrayList<>();
    JList<SourceFilter> list = new JList<>(SourceFilter.values());
    for (int i = 0; i < control.getItemCount(); i++) {
      labels.add(((JLabel) control.getRenderer().getListCellRendererComponent(list, control.getItemAt(i), i, false,
          false)).getText());
    }
    assertThat(labels).containsExactly("All sources", "Hide AtlasLoot-only rows", "Hide Questie-only rows");
  }
}
