package dev.wowforever.app.catalog;

import static org.junit.jupiter.api.Assertions.*;
import dev.wowforever.app.ui.Async;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.prefs.Preferences;
import javax.swing.JRadioButton;
import org.junit.jupiter.api.Test;

class CatalogSessionTest {
  private static EditionRegistry registry() {
    return EditionRegistry.fromLegacyMetadata(Map.of("client_product", "wow_classic_beta",
        "client_build", "1.60.1.70450", "build_id", "projection-1"));
  }
  @Test void eraReferenceDoesNotAuthorizeGameplayAndUnknownMetadataFailsClosed() {
    assertEquals(1, registry().editions().stream().filter(EditionRegistry.Edition::installed).count());
    assertTrue(registry().installed("classic-era").isEmpty());
    var era = EditionRegistry.fromLegacyMetadata(Map.of("client_product", "wow_classic_era",
        "client_build", "1.15.9.70003", "build_id", "era"));
    assertTrue(era.editions().stream().noneMatch(EditionRegistry.Edition::installed));
  }
  @Test void staleSelectionRecoversButUnavailableSelectionNeverChangesSession() throws Exception {
    Preferences p = Preferences.userRoot().node("edition-test-" + UUID.randomUUID());
    try {
      p.put("edition", "retail");
      var session = new CatalogSession(registry(), p);
      assertEquals("forever", session.token().context().editionId());
      assertFalse(session.current(new CatalogSession(registry(), p).token()));
      var old = session.token();
      assertThrows(IllegalArgumentException.class, () -> session.select("retail"));
      assertEquals(old, session.token());
      session.select("forever");
      assertFalse(session.current(old));
      assertEquals("forever", p.get("edition", ""));
    } finally { p.removeNode(); }
  }
  @Test void obsoleteQueuedWorkAndLateDeliveryAreRejected() throws Exception {
    Preferences p = Preferences.userRoot().node("edition-test-" + UUID.randomUUID());
    try {
      var session = new CatalogSession(registry(), p);
      var queue = new ArrayList<Runnable>();
      var delivered = new ArrayList<String>();
      var old = session.token();
      Async.runIf(queue::add, session.guard(old), () -> { fail("Obsolete work started"); return "old"; },
          delivered::add, failure -> fail(failure));
      session.select("forever"); queue.removeFirst().run();
      assertTrue(delivered.isEmpty());
      var current = session.token();
      Async.runIf(Runnable::run, session.guard(current), () -> {
        session.select("forever"); return "late";
      }, delivered::add, failure -> fail(failure));
      assertTrue(delivered.isEmpty());
    } finally { p.removeNode(); }
  }
  @Test void identicalNativeIdsCannotCollideAcrossEditionBuildOrLocale() {
    var base = new CatalogContext("forever", "projection-1", "1.60.1.70450", "enUS");
    assertNotEquals(base.key(2671L), new CatalogContext("classic-era", "projection-1", "1.60.1.70450", "enUS").key(2671L));
    assertNotEquals(base.key(2671L), new CatalogContext("forever", "projection-2", "1.60.1.70451", "enUS").key(2671L));
    assertNotEquals(base.key(2671L), new CatalogContext("forever", "projection-1", "1.60.1.70450", "frFR").key(2671L));
  }
  @Test void pickerDisablesUninstalledGamesAndStateIsProjectionScoped() throws Exception {
    Preferences p = Preferences.userRoot().node("edition-test-" + UUID.randomUUID());
    try {
      var session = new CatalogSession(registry(), p);
      var original = session.token();
      var picker = new EditionPicker(registry(), session, token -> fail("Current game should not reload"));
      int enabled = 0;
      for (var component : picker.getComponents()) if (component instanceof JRadioButton button) {
        if (button.isEnabled()) { enabled++; button.doClick(); }
        assertEquals(button.getName().equals("edition-forever"), button.isEnabled());
      }
      assertEquals(1, enabled);
      assertEquals(original, session.token());
      session.statePreferences().put("last-target", "npc:2671");
      assertEquals("npc:2671", new CatalogSession(registry(), p).statePreferences().get("last-target", ""));
    } finally { p.removeNode(); }
  }
}
