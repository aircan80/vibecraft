package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.GiverSpot;
import dev.wowforever.app.model.QuestChain;
import dev.wowforever.app.model.QuestDetail;
import dev.wowforever.app.model.QuestFacts;
import dev.wowforever.app.model.QuestPageData;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.pages.quest.ChainStepper;
import dev.wowforever.app.ui.pages.quest.ChainPanel;
import dev.wowforever.app.ui.pages.quest.GiverSections;
import dev.wowforever.app.ui.pages.quest.QuestHeader;
import dev.wowforever.app.ui.pages.quest.QuestMapPanel;
import dev.wowforever.app.ui.pages.quest.ObjectivesPanel;
import dev.wowforever.app.ui.widgets.ClipboardPort;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/** Quest facts, chain navigation and a shared map, rendered entirely from loaded records. */
public final class QuestPage extends SourcesPage implements PageEscape {
  public static final int SIDE_BY_SIDE_PX = 900;
  private final HeaderPanel header;
  private final javax.swing.JButton copyInGameLink = new javax.swing.JButton("Copy in-game link");
  private final ChainStepper stepper;
  private final QuestMapPanel mapPanel;
  private final JPanel objectivesPanel = new ObjectiveContent();
  private final JLabel noLocations = new JLabel("No objective locations in the quest database.");
  private final java.util.List<SourceSection> giverSections = new java.util.ArrayList<>();
  private final ChainPanel chainPanel;
  private final SourceSection chainSection;
  private final Body body = new Body();
  private final JTextArea objectives = new JTextArea();
  private final SourceSection objectiveSection;
  private final ObjectivesPanel objectiveRows;

  public QuestPage(QuestPageData data, Map<Long, BufferedImage> maps, Navigator nav, ClipboardPort clipboard) {
    this(data, maps, Map.of(), nav, clipboard);
  }

  public QuestPage(QuestPageData data, Map<Long, BufferedImage> maps, Map<Long, String> zoneNames,
      Navigator nav, ClipboardPort clipboard) {
    super(nav);
    header = QuestHeader.create(data, nav);
    var summary = data.detail().summary();
    copyInGameLink.addActionListener(e -> clipboard.copy(dev.wowforever.app.ui.GameLinks.quest(summary.id(),
        summary.questLevel() == null ? 0 : summary.questLevel(), summary.displayName())));
    copyInGameLink.setAlignmentX(LEFT_ALIGNMENT);
    header.add(copyInGameLink);
    stepper = new ChainStepper(data.chain(), nav);
    chainPanel = new ChainPanel(data.chain(), nav);
    mapPanel = new QuestMapPanel(data, maps, zoneNames, nav, clipboard);
    content.add(header);
    content.add(stepper);
    objectives.setText(dev.wowforever.app.ui.pages.quest.ObjectiveText.description(data.detail().objectivesText()));
    objectives.setVisible(!objectives.getText().isBlank());
    objectives.setEditable(false);
    objectives.setLineWrap(true);
    objectives.setWrapStyleWord(true);
    objectives.setOpaque(false);
    objectives.setCaretPosition(0);
    objectives.getAccessibleContext().setAccessibleName("Quest objectives");
    objectivesPanel.setOpaque(false);
    objectiveRows = new ObjectivesPanel(data.objectives(), data.sourceItems(), nav, mapPanel::focus,
        mapPanel::showAllSources);
    if (data.objectives().isEmpty()) {
      objectivesPanel.add(objectives);
      objectivesPanel.add(noLocations);
    }
    objectivesPanel.add(objectiveRows);
    objectiveSection = panelSection("Objectives", objectivesPanel);
    addSection(objectiveSection);
    content.remove(objectiveSection);
    body.add(objectiveSection);
    body.add(mapPanel);
    content.add(body);
    if (!data.starts().isEmpty()) {
      addGiverSection(GiverSections.create("Given by", data.starts(), nav));
    }
    if (!data.ends().isEmpty()) {
      addGiverSection(GiverSections.create("Turned in to", data.ends(), nav));
    }
    chainSection = panelSection("Quest chain", chainPanel);
    addSection(chainSection);
    addLootSection("Rewards", data.detail().rewards());
    addSection("Related items", data.detail().relatedItems().size(), Columns.itemSources(), data.detail().relatedItems());
    SourceSection related = section("Related items");
    if (related != null) {
      related.setCollapsible(true);
      related.setCollapsed(true);
    }
    bindJumpKeys();
    installPageKeys(() -> java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
  }

  /** Convenience for callers holding only old-style detail data, without locations or a chain. */
  public QuestPage(QuestDetail detail, Navigator nav) {
    this(new QuestPageData(detail, new QuestFacts(null, detail.summary().zoneName(), null, null, null, false),
        List.of(), List.of(), givers(detail, "start"), givers(detail, "end"), QuestChain.EMPTY), Map.of(), nav,
        ClipboardPort.system());
  }

  private static List<GiverSpot> givers(QuestDetail detail, String role) {
    return detail.givers().stream().filter(g -> role.equals(g.role()))
        .map(g -> new GiverSpot(g, List.of(), List.of())).toList();
  }

  private void addGiverSection(SourceSection section) {
    addSection(section); // retain section order and jump-key registration
    content.remove(section);
    giverSections.add(section);
    body.add(section);
  }

  private SourceSection panelSection(String title, JPanel panel) {
    LinkTable<String> table = LinkTable.inline(List.of(new Columns.Column<String>("Name", String.class, s -> s, null)), nav);
    table.setRows(List.of());
    table.setVisible(false);
    SourceSection section = new SourceSection(title, 0, table);
    section.addAboveTable(panel);
    section.heading().label().setText(title);
    return section;
  }

  @Override
  public void doLayout() {
    for (String title : List.of("Objectives", "Given by", "Turned in to")) {
      SourceSection section = section(title);
      if (section != null) {
        section.setCollapsible(false);
      }
    }
    if (section("Related items") != null) {
      section("Related items").setCollapsible(true);
    }
    boolean compact = CompactColumns.within(this);
    chainSection.setCollapsible(false); // the chain owns its compact disclosure control
    chainSection.heading().setVisible(!compact);
    chainSection.setVisible(!compact || chainPanel.isVisible());
    super.doLayout();
  }

  @Override
  public void jumpTo(int index) {
    if (index == 0) {
      objectivesPanel.scrollRectToVisible(new java.awt.Rectangle(0, 0, 1, objectivesPanel.getHeight()));
      if (!objectiveRows.rows().isEmpty()) objectiveRows.rows().getFirst().focusButton().requestFocusInWindow();
    } else {
      super.jumpTo(index);
    }
  }

  public ObjectivesPanel objectiveRows() { return objectiveRows; }
  /** Installs the real page handlers with a focus-owner seam for headless keyboard checks. */
  public void installPageKeys(java.util.function.Supplier<java.awt.Component> focusOwner) {
    java.util.Map<String, Runnable> handlers = new java.util.HashMap<>();
    handlers.put("g", () -> jumpSection("Given by"));
    handlers.put("t", () -> jumpSection("Turned in to"));
    handlers.put("o", () -> jumpSection("Objectives"));
    handlers.put("m", () -> jumpComponent(mapPanel));
    handlers.put("c", () -> {
      if (chainPanel.isCollapsed()) chainPanel.heading().doClick();
      jumpSection("Quest chain");
    });
    handlers.put("r", () -> jumpSection("Rewards"));
    for (int number = 1; number <= 9; number++) {
      final int objective = number;
      handlers.put(Integer.toString(number), () -> mapPanel.focus(objective));
    }
    handlers.put("[", chainPanel::previous);
    handlers.put("]", chainPanel::next);
    handlers.put("z", mapPanel::nextZone);
    dev.wowforever.app.ui.KeyMap.installPageKeys(this, handlers, focusOwner);
  }

  private void jumpSection(String title) {
    SourceSection target = section(title);
    if (target == null || !target.isVisible()) return;
    if (target.isCollapsed()) target.setCollapsed(false);
    jumpComponent(target);
  }

  private void jumpComponent(javax.swing.JComponent component) {
    java.awt.Point at = javax.swing.SwingUtilities.convertPoint(component, 0, 0, content);
    content.scrollRectToVisible(new java.awt.Rectangle(0, at.y, 1, scrollPane().getViewport().getHeight()));
  }

  @Override
  public boolean escape() {
    if (!mapPanel.hasFocusFilter()) return false;
    mapPanel.clearFocus();
    return true;
  }

  public javax.swing.JButton copyInGameLinkButton() { return copyInGameLink; }

  public HeaderPanel header() { return header; }
  public ChainStepper stepper() { return stepper; }
  public QuestMapPanel mapPanel() { return mapPanel; }
  public JPanel objectivesPanel() { return objectivesPanel; }
  public JPanel chainPanel() { return chainPanel; }
  public ChainPanel chainView() { return chainPanel; }
  public JPanel body() { return body; }
  public JTextArea objectives() { return objectives; }
  public boolean isSideBySide() { return getWidth() >= SIDE_BY_SIDE_PX && !CompactColumns.within(this); }

  /** Objective prose and rows are measured using their available width, never their previous height. */
  private final class ObjectiveContent extends JPanel {
    ObjectiveContent() { setLayout(null); }

    int heightFor(int width) {
      int height = objectiveRows.heightFor(width);
      if (objectiveRows.rows().isEmpty()) {
        height += ObjectivesPanel.textHeight(objectives, width) + noLocations.getPreferredSize().height;
      }
      return height;
    }

    @Override public Dimension getPreferredSize() {
      int width = getWidth() > 0 ? getWidth() : 480;
      return new Dimension(width, heightFor(width));
    }

    @Override public void doLayout() {
      int width = getWidth();
      int y = 0;
      if (objectiveRows.rows().isEmpty()) {
        int textHeight = ObjectivesPanel.textHeight(objectives, width);
        objectives.setBounds(0, y, width, textHeight);
        y += textHeight;
        int noteHeight = noLocations.getPreferredSize().height;
        noLocations.setBounds(0, y, width, noteHeight);
        y += noteHeight;
      }
      objectiveRows.setBounds(0, y, width, objectiveRows.heightFor(width));
    }
  }

  /** A natural-height left facts column alongside the map, stacking map first in compact mode. */
  private final class Body extends JPanel {
    private static final int PAD = 10;
    private static final int GAP = 16;
    private static final int SECTION_GAP = 8;

    Body() {
      setLayout(null);
      setOpaque(false);
      setBorder(BorderFactory.createEmptyBorder(0, PAD, SECTION_GAP, PAD));
    }

    private int available() {
      int pageWidth = QuestPage.this.getWidth() > 0 ? QuestPage.this.getWidth() : 1200;
      int parentWidth = getParent() != null && getParent().getWidth() > 0 ? getParent().getWidth() : pageWidth;
      return Math.max(1, Math.min(pageWidth, parentWidth) - PAD * 2);
    }

    private int mapWidth() { return isSideBySide() ? (available() - GAP) / 2 : available(); }

    private int objectiveHeight(int width) {
      int inner = Math.max(1, width - objectiveSection.getInsets().left - objectiveSection.getInsets().right);
      // SourceSection's AboveTable reserves its ordinary gap and the hidden empty table's natural height.
      return objectiveSection.heading().getPreferredSize().height + 6 + objectiveSection.table().getPreferredSize().height
          + ((ObjectiveContent) objectivesPanel).heightFor(inner);
    }

    private int leftHeight(int width) {
      int height = objectiveHeight(width);
      for (SourceSection section : giverSections) {
        if (section.isVisible()) height += SECTION_GAP + section.getPreferredSize().height;
      }
      return height;
    }

    private int mapHeight(int width) {
      var map = mapPanel.mapView();
      int imageHeight = map == null ? mapPanel.getPreferredSize().height : map.heightFor(width);
      if (map == null) return imageHeight;
      // The panel's chrome is independent of the image width; measuring it never resizes the live map.
      return imageHeight + mapPanel.chromeHeight();
    }

    @Override public Dimension getPreferredSize() {
      int mapWidth = mapWidth();
      int leftWidth = isSideBySide() ? available() - mapWidth - GAP : available();
      int height = isSideBySide() ? Math.max(mapHeight(mapWidth), leftHeight(leftWidth))
          : mapHeight(mapWidth) + GAP + leftHeight(leftWidth);
      return new Dimension(available() + PAD * 2, height + SECTION_GAP);
    }

    @Override public void doLayout() {
      int width = mapWidth();
      int height = mapHeight(width);
      int leftWidth = isSideBySide() ? available() - width - GAP : available();
      int y = isSideBySide() ? 0 : height + GAP;
      mapPanel.setBounds(isSideBySide() ? PAD + leftWidth + GAP : PAD, 0, width, height);
      int objectiveHeight = objectiveHeight(leftWidth);
      objectiveSection.setBounds(PAD, y, leftWidth, objectiveHeight);
      y += objectiveHeight;
      for (SourceSection section : giverSections) {
        if (!section.isVisible()) continue;
        y += SECTION_GAP;
        int h = section.getPreferredSize().height;
        section.setBounds(PAD, y, leftWidth, h);
        y += h;
      }
    }
  }
}
