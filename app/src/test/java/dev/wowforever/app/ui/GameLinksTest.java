package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import org.junit.jupiter.api.Test;

class GameLinksTest {
  @Test
  void thunderfuryLinkAndRoundTrip() {
    String link = GameLinks.item(19019, "Thunderfury, Blessed Blade of the Windseeker");
    assertThat(link).isEqualTo("|Hitem:19019:0:0:0:0:0:0:0:60|h[Thunderfury, Blessed Blade of the Windseeker]|h");
    assertThat(PasteParser.parse(link)).contains(new dev.wowforever.app.nav.ViewTarget.Entity(new EntityRef(EntityKind.ITEM, 19019)));
    assertThat(GameLinks.quest(176, 11, "Wanted:  \"Hogger\""))
        .isEqualTo("|Hquest:176:11|h[Wanted:  \"Hogger\"]|h");
  }
}
