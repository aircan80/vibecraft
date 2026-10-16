package dev.wowforever.app.ui.detail;

import static dev.wowforever.app.model.EntityKind.INSTANCE;
import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.QUEST;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.db.AssetRepository;
import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceLoot;
import dev.wowforever.app.model.ItemDetail;
import dev.wowforever.app.model.ItemSource;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.LinkRenderer;
import dev.wowforever.app.ui.ThemeManager;
import dev.wowforever.app.ui.theme.QualityColors;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class ItemDetailPanelTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void tabsCarryCounts() {
    var p = new ItemDetailPanel(repos.items().detail(14149).get(), t -> {});
    assertThat(p.tabs().getTitleAt(0)).startsWith("Dropped by (").doesNotStartWith("Dropped by (0");
  }

  @Test
  void unknownNpcRendersPlaceholder() {
    var p = new ItemDetailPanel(repos.items().detail(190179).get(), t -> {});
    assertThat(p.droppedBy().table().getValueAt(0, 0)).isEqualTo("Unknown NPC #185333");
  }

  @Test
  void dropPercentFormattedAndMarkedWhenSourcesDisagree() {
    var p = new ItemDetailPanel(repos.items().detail(2874).get(), t -> {});
    assertThat(p.droppedBy().table().getValueAt(0, 3)).isEqualTo("60.00 % ≠");
  }

  @Test
  void everyTabIsPresentAndEmptyOnesAreDisabled() {
    var p = new ItemDetailPanel(repos.items().detail(14149).get(), t -> {});
    assertThat(p.tabs().getTabCount()).isEqualTo(6);
    List<String> prefixes = List.of("Dropped by (", "In instance loot (", "Contained in (", "Sold by (",
        "Reward from (", "Related quests (");
    for (int i = 0; i < 6; i++) {
      String title = p.tabs().getTitleAt(i);
      assertThat(title).startsWith(prefixes.get(i));
      assertThat(p.tabs().isEnabledAt(i)).as(title).isEqualTo(!title.endsWith("(0)"));
    }
  }

  @Test
  void headerNamesClassSubclassAndLevels() {
    ItemDetail d = repos.items().detail(14149).get();
    var p = new ItemDetailPanel(d, t -> {});
    assertThat(p.header().title()).isEqualTo("Subterranean Cape");
    assertThat(p.header().subtitle()).isEqualTo("Item #14149 · Armor · Cloth · iLvl 18 · Requires 13");
  }

  @Test
  void instanceLootTabLinksInstanceAndBoss() {
    List<ViewTarget> opened = new ArrayList<>();
    var p = new ItemDetailPanel(repos.items().detail(14149).get(), opened::add);
    assertThat(p.tabs().getTitleAt(1)).isEqualTo("In instance loot (1)");
    var t = p.inInstanceLoot();
    assertThat(t.table().getColumnName(0)).isEqualTo("Instance");
    assertThat(t.table().getColumnName(1)).isEqualTo("Boss");
    assertThat(t.table().getColumnName(2)).isEqualTo("Slot");
    assertThat(t.table().getColumnName(3)).isEqualTo("Drop %");
    assertThat(t.table().getValueAt(0, 0)).isEqualTo("Ragefire Chasm");
    assertThat(t.table().getValueAt(0, 1)).isEqualTo("Taragaman the Hungerer");
    assertThat(t.model().refAt(0, 1)).contains(new EntityRef(NPC, 11520));
    t.activateRow(0);
    assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(INSTANCE, 1)));
  }

  @Test
  void bossWithoutAnNpcIsPlainText() {
    InstanceLoot trash = new InstanceLoot(new EntityRef(INSTANCE, 9), "Scarlet Monastery", 70, "Trash Mobs", null,
        4, null);
    ItemDetail d = new ItemDetail(new ItemSummary(10329, "Scarlet Belt", 34, 29, "Armor", "Mail", 0), 4, 3, null,
        List.of(), List.of(trash), List.of(), List.of(), List.of(), List.of(), List.of());
    var p = new ItemDetailPanel(d, t -> {});
    assertThat(p.tabs().getSelectedIndex()).isEqualTo(1);
    var table = p.inInstanceLoot().table();
    assertThat(table.getValueAt(0, 1)).isEqualTo("Trash Mobs");
    assertThat(p.inInstanceLoot().model().refAt(0, 1)).isEmpty();
    Component boss = table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1);
    assertThat(boss.getForeground()).isNotEqualTo(LinkRenderer.linkColor());
    Component instance = table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0);
    assertThat(instance.getForeground()).isEqualTo(LinkRenderer.linkColor());
    assertThat(p.header().subtitle()).isEqualTo("Item #10329 · Armor · Mail · iLvl 34 · Requires 29");
  }

  @Test
  void startsQuestLinkOpensTheQuest() {
    EntityRef quest = new EntityRef(QUEST, 373);
    ItemDetail d = new ItemDetail(new ItemSummary(2874, "An Unsent Letter", 1, null, "Quest", "Quest", 1), 12, 0,
        373L, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        List.of(new ItemSource(ItemSource.RELATED, quest, "The Unsent Letter")));
    List<ViewTarget> opened = new ArrayList<>();
    var p = new ItemDetailPanel(d, opened::add);
    assertThat(p.header().subtitle()).isEqualTo("Item #2874 · Quest · iLvl 1 · Starts quest: The Unsent Letter");
    p.header().links().get(0).doClick();
    assertThat(opened).containsExactly(new ViewTarget.Entity(quest));
  }

  @Test
  void cardShowsIconQualityAndLines() {
    Object loaded = DetailPanels.load(new EntityRef(ITEM, 17204), repos).orElseThrow();
    var p = (ItemDetailPanel) DetailPanels.render(loaded, repos, t -> {});
    assertThat(p.iconLabel().getIcon()).isNotNull();
    assertThat(p.iconLabel().getIcon().getIconWidth()).isEqualTo(ItemDetailPanel.ICON_SIZE);
    assertThat(p.iconLabel().getIcon().getIconHeight()).isEqualTo(ItemDetailPanel.ICON_SIZE);
    assertThat(p.header().title()).isEqualTo("Eye of Sulfuras");
    assertThat(p.header().titleColor()).isEqualTo(QualityColors.text(5, ThemeManager.isDark()));
    assertThat(p.tooltip().labels()).extracting(JLabel::getText).containsExactly("Eye of Sulfuras",
        "Binds when picked up",
        "Use: The Eye of Sulfuras can be combined with the Sulfuron Hammer to create Sulfuras, legendary hammer"
            + " of Ragnaros.",
        "Item Level 60", "Sell Price: 20g");
    assertThat(p.tooltip().labels().get(2).getForeground()).isEqualTo(new Color(0x1EFF00));
    assertThat(SwingUtilities.isDescendingFrom(p.tooltip(), p)).isTrue();
    assertThat(SwingUtilities.isDescendingFrom(p.iconLabel(), p)).isTrue();
  }

  @Test
  void missingIconFallsBackToTheQuestionMark() {
    BufferedImage fallback = decode(repos.assets().icon(AssetRepository.FALLBACK_ICON_ID).orElseThrow());
    Object loaded = DetailPanels.load(new EntityRef(ITEM, 1307), repos).orElseThrow(); // icon blob missing
    var p = (ItemDetailPanel) DetailPanels.render(loaded, repos, t -> {});
    assertThat(pixels(((ImageIcon) p.iconLabel().getIcon()).getImage())).isEqualTo(pixels(fallback));
  }

  @Test
  void itemWithoutClientDataHasNoLinesAndAPlainName() {
    Object loaded = DetailPanels.load(new EntityRef(ITEM, 1176), repos).orElseThrow();
    var p = (ItemDetailPanel) DetailPanels.render(loaded, repos, t -> {});
    assertThat(p.header().title()).isEqualTo("Smelling Salts");
    assertThat(p.header().titleColor()).isEqualTo(new JLabel().getForeground());
    assertThat(p.tooltip().labels()).isEmpty();
    assertThat(p.iconLabel().getIcon()).as("question-mark fallback").isNotNull();
  }

  @Test
  void withoutAnyIconTheIconSpaceStaysEmpty() {
    var p = new ItemDetailPanel(repos.items().detail(17204).get(), null, t -> {});
    assertThat(p.iconLabel().getIcon()).isNull();
    assertThat(p.tooltip().labels()).hasSize(5);
  }

  private static BufferedImage decode(byte[] png) {
    try {
      return ImageIO.read(new ByteArrayInputStream(png));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static int[] pixels(Image image) {
    BufferedImage copy = new BufferedImage(image.getWidth(null), image.getHeight(null),
        BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = copy.createGraphics();
    g.drawImage(image, 0, 0, null);
    g.dispose();
    return copy.getRGB(0, 0, copy.getWidth(), copy.getHeight(), null, 0, copy.getWidth());
  }
}
