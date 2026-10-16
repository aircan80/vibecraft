package dev.wowforever.app.nav;

import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.ViewTarget.Entity;
import dev.wowforever.app.nav.ViewTarget.Home;
import dev.wowforever.app.nav.ViewTarget.List;
import org.junit.jupiter.api.Test;

class NavigationHistoryTest {
  @Test
  void backAndForward() {
    var h = new NavigationHistory();
    h.push(new Home());
    h.push(new Entity(new EntityRef(ITEM, 1)));
    assertThat(h.back()).contains(new Home());
    assertThat(h.canForward()).isTrue();
    assertThat(h.forward()).contains(new Entity(new EntityRef(ITEM, 1)));
  }

  @Test
  void pushClearsForward() {
    var h = new NavigationHistory();
    h.push(new Home());
    h.push(new List(ZONE));
    h.back();
    h.push(new List(INSTANCE));
    assertThat(h.canForward()).isFalse();
  }

  @Test
  void duplicatePushIgnored() {
    var h = new NavigationHistory();
    h.push(new Home());
    h.push(new Home());
    assertThat(h.canBack()).isFalse();
  }

  @Test
  void backAtStartIsEmpty() {
    assertThat(new NavigationHistory().back()).isEmpty();
  }
}
