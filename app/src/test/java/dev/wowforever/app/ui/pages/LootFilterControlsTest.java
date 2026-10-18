package dev.wowforever.app.ui.pages;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.LootFilter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

class LootFilterControlsTest {
  @Test void selectionsNotifyAndRestoringHistoryRetainsFiltersWithoutIntermediateLoads() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var changes=new AtomicInteger();
      var controls=new LootFilterControls(changes::incrementAndGet);
      assertThat(controls.filter()).isEqualTo(new LootFilter(null,null));
      controls.restore(new LootFilter(9,6));
      assertThat(controls.filter()).isEqualTo(new LootFilter(9,6));
      assertThat(changes).hasValue(0);
      for (var component:controls.getComponents()) {
        if (component instanceof JComboBox<?> box && "loot-class-filter".equals(box.getName()))
          box.setSelectedIndex(0);
      }
      assertThat(controls.filter()).isEqualTo(new LootFilter(null,6));
      assertThat(changes).hasValue(1);
    });
  }
}
