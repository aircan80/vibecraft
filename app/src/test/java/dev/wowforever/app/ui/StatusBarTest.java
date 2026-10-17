package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.ui.detail.CompactColumns;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class StatusBarTest {
  private static final String HASH = "6c9ed1fa2901b9ff63680623e8816bd3a748f7f8b1c38e480b29eb72f1113148";

  @Test
  void theBuildShowsItsFirstEightCharactersWithTheWholeIdInTheTooltip() {
    StatusBar status = new StatusBar();
    status.setBuild(HASH);
    assertThat(status.info()).isEqualTo("Data build 6c9ed1fa");
    assertThat(status.infoLabel().getToolTipText()).isEqualTo(HASH);
    status.setBuild("b-42");
    assertThat(status.info()).isEqualTo("Data build b-42");
    status.setBuild(null);
    assertThat(status.info()).isEmpty();
    assertThat(status.infoLabel().getToolTipText()).isNull();
  }

  @Test
  void buildLabelShortensOnlyLongIds() {
    assertThat(StatusBar.buildLabel(HASH)).isEqualTo("Data build 6c9ed1fa");
    assertThat(StatusBar.buildLabel("abc")).isEqualTo("Data build abc");
  }

  @Test
  void theBuildIsHiddenInTheCompactWindowAndBackInTheFullOne() {
    StatusBar status = new StatusBar();
    status.setBuild(HASH);
    JPanel compact = new JPanel(new BorderLayout());
    compact.putClientProperty(CompactColumns.KEY, Boolean.TRUE);
    compact.add(status);
    assertThat(status.infoLabel().isVisible()).isFalse();
    new JPanel(new BorderLayout()).add(status);
    assertThat(status.infoLabel().isVisible()).isTrue();
    assertThat(status.info()).isEqualTo("Data build 6c9ed1fa");
  }
}
