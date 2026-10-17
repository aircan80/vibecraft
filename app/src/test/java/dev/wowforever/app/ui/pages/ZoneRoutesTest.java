package dev.wowforever.app.ui.pages;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.model.NpcRoute;
import dev.wowforever.app.model.QuestPins;
import dev.wowforever.app.model.SpawnPin;
import dev.wowforever.app.ui.BooleanSetting;
import dev.wowforever.app.ui.StatusSink;
import dev.wowforever.app.ui.widgets.MapLayer;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ZoneRoutesTest {
  private static final MapLayer ROUTES = MapLayer.REGISTERED.stream().filter(l -> l.id().equals("routes"))
      .findFirst().orElseThrow();
  private final List<Runnable> work = new ArrayList<>();
  private final List<Exception> failures = new ArrayList<>();
  private final BooleanSetting saved = BooleanSetting.inMemory(false);

  private ZoneMapPanel panel() {
    return new ZoneMapPanel(40, new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB), List.of(),
        new ZoneMapPanel.PinSource() {
          public List<SpawnPin> npcs(long zone, boolean bosses) { return List.of(); }
          public List<SpawnPin> objects(long zone) { return List.of(); }
          public QuestPins questPins(long zone) { return new QuestPins(List.of(), false); }
          public List<NpcRoute> routes(long zone) { return FixtureDb.repositories().zones().npcRoutesInZone(zone); }
        }, work::add, new StatusSink() {
          public void message(String text) {}
          public void fail(Exception e) { failures.add(e); }
        }, target -> {}, BooleanSetting.inMemory(false), saved);
  }

  @Test
  void routesToggleOffByDefaultAndRemembered() throws Exception {
    ZoneMapPanel panel = panel();
    assertThat(panel.routesToggle().isSelected()).isFalse();
    assertThat(work).isEmpty();
    SwingUtilities.invokeAndWait(() -> panel.routesToggle().doClick());
    assertThat(saved.isOn()).isTrue();
    assertThat(work).hasSize(1);
    work.removeFirst().run();
    SwingUtilities.invokeAndWait(() -> {});
    assertThat(failures).isEmpty();
    assertThat(panel.mapView().routes()).singleElement().satisfies(r -> {
      assertThat(r.npc().id()).isEqualTo(550);
      assertThat(r.points()).hasSizeGreaterThanOrEqualTo(30);
    });
    SwingUtilities.invokeAndWait(() -> panel.bossesToggle().doClick());
    assertThat(saved.isOn()).isFalse();
    assertThat(panel.routesToggle().isSelected()).isFalse();
    assertThat(panel.mapView().isLayerVisible(ROUTES)).isFalse();
  }

  @Test
  void staleLoadCannotReenableRoutesAndLegendAgreesWithControls() throws Exception {
    ZoneMapPanel panel = panel();
    SwingUtilities.invokeAndWait(() -> panel.routesToggle().doClick());
    SwingUtilities.invokeAndWait(() -> panel.bossesToggle().doClick());
    work.removeFirst().run();
    SwingUtilities.invokeAndWait(() -> {});
    assertThat(panel.mapView().routes()).isNotEmpty();
    assertThat(panel.mapView().isLayerVisible(ROUTES)).isFalse();
    SwingUtilities.invokeAndWait(() -> panel.routesToggle().doClick());
    assertThat(work).isEmpty();
    JToggleButton legend = java.util.Arrays.stream(panel.legend().getComponents())
        .filter(c -> c instanceof JToggleButton b && b.getText().equals("Routes"))
        .map(JToggleButton.class::cast).findFirst().orElseThrow();
    SwingUtilities.invokeAndWait(legend::doClick);
    assertThat(panel.routesToggle().isSelected()).isFalse();
    assertThat(saved.isOn()).isFalse();
    assertThat(panel.mapView().isLayerVisible(ROUTES)).isFalse();
  }
}
