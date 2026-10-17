package dev.wowforever.app.ui.detail;

import static dev.wowforever.app.model.EntityKind.BOSS;
import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.OBJECT;
import static dev.wowforever.app.model.EntityKind.QUEST;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.ui.pages.InstancePage;
import dev.wowforever.app.ui.pages.ItemPage;
import dev.wowforever.app.ui.pages.NpcPage;
import dev.wowforever.app.ui.pages.ObjectPage;
import dev.wowforever.app.ui.pages.QuestPage;
import dev.wowforever.app.ui.pages.ZonePage;
import dev.wowforever.app.db.AssetRepository;
import dev.wowforever.app.ui.widgets.Images;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class DetailPanelsTest {
  private final Repositories repos = FixtureDb.repositories();

  private JComponent render(EntityKind kind, long id) {
    Object detail = DetailPanels.load(new EntityRef(kind, id), repos).orElseThrow();
    return DetailPanels.render(detail, repos, t -> {});
  }

  @Test
  void rendersAPanelForEveryNavigableKind() {
    assertThat(render(ITEM, 14149)).isInstanceOf(ItemPage.class);
    assertThat(render(NPC, 639)).isInstanceOf(NpcPage.class);
    assertThat(render(OBJECT, 386759)).isInstanceOf(ObjectPage.class);
    assertThat(render(QUEST, 2)).isInstanceOf(QuestPage.class);
    assertThat(render(ZONE, 40)).isInstanceOf(ZonePage.class);
    assertThat(render(INSTANCE, 1)).isInstanceOf(InstancePage.class);
  }

  @Test
  void namesTheEntity() {
    assertThat(DetailPanels.name(DetailPanels.load(new EntityRef(ITEM, 14149), repos).orElseThrow()))
        .isEqualTo("Subterranean Cape");
    assertThat(DetailPanels.name(DetailPanels.load(new EntityRef(ZONE, 40), repos).orElseThrow()))
        .isEqualTo("Westfall");
  }

  @Test
  void anIconOnlyItemLoadsItsOwnIconNotTheFallback() {
    // Illari's Key has an Item row (icon 134236) but no ItemSparse row, so its card is empty
    var keyPage = (DetailPanels.LoadedItem) DetailPanels.load(new EntityRef(ITEM, 212347), repos).orElseThrow();
    assertThat(keyPage.detail().card().iconId()).isNull();
    assertThat(samePixels(keyPage.icon(), decoded(134236L))).as("Illari's Key shows icon 134236").isTrue();
    // an item without any client data still gets the question-mark fallback
    var saltsPage = (DetailPanels.LoadedItem) DetailPanels.load(new EntityRef(ITEM, 1176), repos).orElseThrow();
    assertThat(samePixels(saltsPage.icon(), decoded(0L))).as("Smelling Salts shows the fallback").isTrue();
  }

  @Test
  void aMissingIconBlobFallsBackToTheQuestionMark() {
    var page = (DetailPanels.LoadedItem) DetailPanels.load(new EntityRef(ITEM, 1307), repos).orElseThrow();
    assertThat(samePixels(page.icon(), decoded(AssetRepository.FALLBACK_ICON_ID))).isTrue();
  }

  @Test
  void anItemLoadsWithItsCardAndIcon() {
    var page = (DetailPanels.LoadedItem) DetailPanels.load(new EntityRef(ITEM, 17204), repos).orElseThrow();
    assertThat(page.icon()).as("decoded in load, off the EDT").isNotNull();
    assertThat(page.detail().card().quality()).isEqualTo(5);
    assertThat(page.detail().card().lines()).hasSize(5);
    assertThat(DetailPanels.render(page, repos, t -> {})).isInstanceOf(ItemPage.class);
  }

  @Test
  void aZoneMapIsDecodedInLoad() {
    Object loaded = DetailPanels.load(new EntityRef(ZONE, 40), repos).orElseThrow();
    assertThat(loaded).isInstanceOfSatisfying(DetailPanels.LoadedZone.class, page -> {
      assertThat(page.map()).as("decoded in load, off the EDT").isNotNull();
      assertThat(page.map().getWidth()).isEqualTo(1002);
    });
    assertThat(DetailPanels.render(loaded, repos, t -> {})).isInstanceOf(ZonePage.class);
  }

  @Test
  void aZoneWithoutAMapLoadsWithoutOne() {
    Object loaded = DetailPanels.load(new EntityRef(ZONE, 2437), repos).orElseThrow();
    assertThat(((DetailPanels.LoadedZone) loaded).map()).isNull();
    assertThat(DetailPanels.render(loaded, repos, t -> {})).isInstanceOf(ZonePage.class);
  }

  private BufferedImage decoded(long iconId) {
    return Images.decode(repos.assets().icon(iconId).orElseThrow()).orElseThrow();
  }

  private static boolean samePixels(BufferedImage a, BufferedImage b) {
    assertThat(a).isNotNull();
    return a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight()
        && Arrays.equals(a.getRGB(0, 0, a.getWidth(), a.getHeight(), null, 0, a.getWidth()),
            b.getRGB(0, 0, b.getWidth(), b.getHeight(), null, 0, b.getWidth()));
  }

  @Test
  void missingEntitiesAndBossesLoadEmpty() {
    assertThat(DetailPanels.load(new EntityRef(ITEM, -1), repos)).isEmpty();
    assertThat(DetailPanels.load(new EntityRef(BOSS, 1), repos)).isEmpty();
  }

  @Test
  void buildFallsBackToNotFound() {
    JComponent c = DetailPanels.build(new EntityRef(NPC, -7), repos, t -> {});
    assertThat(c).isInstanceOfSatisfying(JLabel.class, l -> assertThat(l.getText()).isEqualTo("Not found: NPC #-7"));
  }
}
