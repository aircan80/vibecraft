package dev.wowforever.app.ui;

import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.NPC;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.nav.ViewTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SearchBarTest {

  @Test
  void fireSendsTextAndKind() {
    AtomicReference<ViewTarget.Search> got = new AtomicReference<>();
    SearchBar b = new SearchBar(got::set);
    b.textField().setText("hogger");
    b.kindCombo().setSelectedIndex(2);
    b.fire();
    assertThat(got.get()).isEqualTo(new ViewTarget.Search("hogger", Optional.of(NPC)));
  }

  @Test
  void blankTextDoesNotFire() {
    AtomicInteger n = new AtomicInteger();
    SearchBar b = new SearchBar(t -> n.incrementAndGet());
    b.textField().setText("  ");
    b.fire();
    assertThat(n).hasValue(0);
  }

  @Test
  void kindsInOrderAndTextTrimmed() {
    List<ViewTarget.Search> got = new ArrayList<>();
    SearchBar b = new SearchBar(got::add, 60_000);
    List<String> kinds = new ArrayList<>();
    for (int i = 0; i < b.kindCombo().getItemCount(); i++) {
      kinds.add(b.kindCombo().getItemAt(i));
    }
    assertThat(kinds).containsExactly("All", "Items", "NPCs", "Objects", "Quests", "Zones", "Dungeons & Raids");
    b.textField().setText("  rage ");
    b.fire();
    b.kindCombo().setSelectedIndex(6); // changing the kind re-runs the search at once
    assertThat(got).containsExactly(new ViewTarget.Search("rage", Optional.empty()),
        new ViewTarget.Search("rage", Optional.of(INSTANCE)));
  }

  @Test
  void enterFiresImmediatelyAndTypingStartsTheDebounce() {
    List<ViewTarget.Search> got = new ArrayList<>();
    SearchBar b = new SearchBar(got::add, 60_000); // long delay: the timer never fires during the test
    b.textField().setText("hog");
    assertThat(b.debounceRunning()).isTrue();
    assertThat(got).isEmpty();
    b.textField().postActionEvent(); // Enter
    assertThat(b.debounceRunning()).isFalse();
    assertThat(got).containsExactly(new ViewTarget.Search("hog", Optional.empty()));
  }
}
