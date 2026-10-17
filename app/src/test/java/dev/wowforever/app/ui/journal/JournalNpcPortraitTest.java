package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import dev.wowforever.app.db.NpcPortraits;
import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.model.SourceFilter;
import dev.wowforever.app.ui.detail.DetailPanels;
import dev.wowforever.app.ui.pages.NpcPage;
import dev.wowforever.app.ui.widgets.ClipboardPort;
import java.util.Map;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class JournalNpcPortraitTest {
  @Test void missingNpcAndDifferentBuildHaveNoPortrait() {
    assertThat(NpcPortraits.load(247003,"1.60.1.70450")).isEmpty();
    assertThat(NpcPortraits.load(2671,"1.15.9.70003")).isEmpty();
  }
  @Test void portraitLoadsBeforeRenderingWithoutDatabaseReads() throws Exception {
    var portrait=NpcPortraits.load(2671,"1.60.1.70450").orElseThrow();
    assertThat(portrait.image().getWidth()).isEqualTo(768);
    assertThat(portrait.evidence()).contains("84bea4233331e390fda8724c6f91ffa5","126062");
    var detail=FixtureDb.repositories().npcs().detail(639).orElseThrow();
    SwingUtilities.invokeAndWait(()-> {
      var page=new JournalNpcPortrait(new NpcPage(detail,t->{},text->{}),portrait);
      page.setSize(420,700);page.doLayout();
      assertThat(page.getComponentCount()).isEqualTo(2);
      page.restoreState(page.saveState());
      assertThatThrownBy(()->NpcPortraits.load(2671,"1.60.1.70450")).isInstanceOf(IllegalStateException.class);
    });
  }
}
