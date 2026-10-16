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
  void replaceCurrentDoesNotGrowHistory() {
    var h = new NavigationHistory();
    h.push(new Home());
    h.push(new List(ZONE));
    h.replaceCurrent(new Entity(new EntityRef(ITEM, 1)));
    h.replaceCurrent(new Entity(new EntityRef(ITEM, 2)));
    assertThat(h.current()).contains(new Entity(new EntityRef(ITEM, 2)));
    assertThat(h.canForward()).isFalse();
    assertThat(h.back()).contains(new Home());
    assertThat(h.canBack()).isFalse();
    assertThat(h.forward()).contains(new Entity(new EntityRef(ITEM, 2)));
  }

  @Test
  void replaceCurrentKeepsForwardHistory() {
    var h = new NavigationHistory();
    h.push(new Home());
    h.push(new List(ZONE));
    h.push(new List(INSTANCE));
    h.back();
    h.replaceCurrent(new Entity(new EntityRef(ITEM, 1)));
    assertThat(h.forward()).contains(new List(INSTANCE));
    assertThat(h.back()).contains(new Entity(new EntityRef(ITEM, 1)));
  }

  @Test
  void replaceCurrentOnEmptyHistoryPushes() {
    var h = new NavigationHistory();
    h.replaceCurrent(new Home());
    assertThat(h.current()).contains(new Home());
    assertThat(h.canBack()).isFalse();
  }

  @Test
  void backAtStartIsEmpty() {
    assertThat(new NavigationHistory().back()).isEmpty();
  }
}
