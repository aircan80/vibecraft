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
  private final JPanel objectivesPanel = new JPanel(new BorderLayout());
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
      JPanel fallback = new JPanel(new BorderLayout());
      fallback.setOpaque(false);
      fallback.add(objectives, BorderLayout.CENTER);
      fallback.add(new JLabel("No objective locations in the quest database."), BorderLayout.SOUTH);
      objectivesPanel.add(fallback, BorderLayout.NORTH);
      objectivesPanel.add(objectiveRows, BorderLayout.SOUTH);
    } else {
      objectivesPanel.add(objectiveRows, BorderLayout.CENTER);
    }
    objectiveSection = panelSection("Objectives", objectivesPanel);
    addSection(objectiveSection);
    content.remove(objectiveSection);
    body.add(objectiveSection);
    body.add(mapPanel);
    content.add(body);
    if (!data.starts().isEmpty()) {
      addSection(GiverSections.create("Given by", data.starts(), nav));
    }
    if (!data.ends().isEmpty()) {
      addSection(GiverSections.create("Turned in to", data.ends(), nav));
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

  /** Width-aware layout keeps the map above the text in a narrow or compact window. */
  private final class Body extends JPanel {
    private static final int PAD = 10;
    private static final int GAP = 16;

    Body() {
      setLayout(null);
      setOpaque(false);
      setBorder(BorderFactory.createEmptyBorder(0, PAD, 8, PAD));
    }

    private int available() {
      return Math.max(0, (getWidth() > 0 ? getWidth() : QuestPage.this.getWidth() > 0
          ? QuestPage.this.getWidth() : 1200) - PAD * 2);
    }

    private int mapWidth() {
      return isSideBySide() ? (available() - GAP) / 2 : available();
    }

    @Override
    public Dimension getPreferredSize() {
      int mapWidth = mapWidth();
      mapPanel.setSize(mapWidth, mapPanel.getHeight());
      int mapHeight = mapPanel.getPreferredSize().height;
      int objectiveWidth = isSideBySide() ? available() - mapWidth - GAP : available();
      objectives.setSize(Math.max(1, objectiveWidth - 24), 1);
      int textHeight = Math.max(30, objectivesPanel.getPreferredSize().height) + 44;
      return new Dimension(available() + PAD * 2,
          (isSideBySide() ? Math.max(mapHeight, textHeight) : mapHeight + GAP + textHeight) + 8);
    }

    @Override
    public void doLayout() {
      int mapWidth = mapWidth();
      mapPanel.setSize(mapWidth, mapPanel.getHeight());
      int mapHeight = mapPanel.getPreferredSize().height;
      if (isSideBySide()) {
        int left = available() - mapWidth - GAP;
        objectiveSection.setBounds(PAD, 0, left, getHeight() - 8);
        mapPanel.setBounds(PAD + left + GAP, 0, mapWidth, mapHeight);
      } else {
        mapPanel.setBounds(PAD, 0, mapWidth, mapHeight);
        objectiveSection.setBounds(PAD, mapHeight + GAP, available(), Math.max(0, getHeight() - mapHeight - GAP - 8));
      }
    }
  }
}
