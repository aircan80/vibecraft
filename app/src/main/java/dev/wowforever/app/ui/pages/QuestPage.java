package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.MapCoordinates;
import dev.wowforever.app.model.QuestDetail;
import dev.wowforever.app.model.QuestGiver;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.model.SpawnPin;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.Labels;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.widgets.MapView;
import dev.wowforever.app.ui.widgets.MapView.Pin;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JTextArea;

/**
 * The quest page, one scrolling page: the header (name, level, required level and zone), the objectives text,
 * then the sections Given by, Turned in to, Rewards and Related items (those without rows are left out). Given the
 * zone map of a giver, Given by shows it above its table with the giver's {@code "!"} pins, and Turned in to its
 * own with the {@code "?"} pins; when those are all at the start pins' spots on the same map, Given by's map shows
 * both (a giver's {@code "!"} winning over its {@code "?"}) and Turned in to none.
 */
public final class QuestPage extends SourcesPage {
  /**
   * The zone map of one of the page's sections: the decoded map of zone {@code zoneId} and the spawn points there
   * of the section's first giver with a point on a zone map. Only points {@linkplain MapCoordinates#onMap on the map}
   * are kept (instance placeholders and points past the edge drop out here, once).
   */
  public record GiverMap(long zoneId, BufferedImage map, List<SpawnPin> points) {
    public GiverMap {
      Objects.requireNonNull(map, "map");
      points = points.stream().filter(p -> MapCoordinates.onMap(p.x(), p.y())).toList();
    }
  }

  private record Spot(double x, double y) {}

  private final HeaderPanel header;
  private final MapView givenByMap;
  private final MapView turnedInMap;
  private final JTextArea objectives = new JTextArea() {
    @Override
    public Dimension getMaximumSize() { // never stretched taller than its text
      return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
  };

  /** The page without giver maps. */
  public QuestPage(QuestDetail d, Navigator nav) {
    this(d, null, null, nav);
  }

  /**
   * The page with the zone maps of a start giver ({@code startMap}) and of an end giver ({@code endMap}); null for
   * none.
   */
  public QuestPage(QuestDetail d, GiverMap startMap, GiverMap endMap, Navigator nav) {
    super(nav);
    QuestSummary s = d.summary();
    header = new HeaderPanel(s.displayName(), Labels.join("Quest #" + s.id(),
        s.questLevel() == null ? null : "Level " + s.questLevel(),
        s.requiredLevel() == null ? null : "Requires " + s.requiredLevel(), s.zoneName()));
    header.setAlignmentX(LEFT_ALIGNMENT);
    content.add(header);
    String text = d.objectivesText() == null ? "" : d.objectivesText().strip();
    objectives.setText(text);
    objectives.setVisible(!text.isEmpty());
    objectives.setEditable(false);
    objectives.setLineWrap(true);
    objectives.setWrapStyleWord(true);
    objectives.setOpaque(false);
    objectives.setBorder(BorderFactory.createEmptyBorder(0, 12, 4, 12));
    objectives.setAlignmentX(LEFT_ALIGNMENT);
    objectives.setCaretPosition(0);
    content.add(objectives);
    List<QuestGiver> starts = byRole(d.givers(), "start");
    List<QuestGiver> ends = byRole(d.givers(), "end");
    addSection("Given by", starts.size(), VisibleColumns.of(Columns.questGivers(), starts, null), starts);
    addSection("Turned in to", ends.size(), VisibleColumns.of(Columns.questGivers(), ends, null), ends);
    List<Pin> startPins = giverPins(startMap, true);
    List<Pin> endPins = giverPins(endMap, false);
    if (!startPins.isEmpty() && !endPins.isEmpty() && startMap.zoneId() == endMap.zoneId()
        && spots(startPins).containsAll(spots(endPins))) {
      List<Pin> both = new ArrayList<>(startPins);
      endPins.stream().filter(end -> startPins.stream().noneMatch(start -> start.ref().equals(end.ref())
          && start.x() == end.x() && start.y() == end.y())).forEach(both::add);
      givenByMap = addMap("Given by", startMap.map(), both);
      turnedInMap = null;
    } else {
      givenByMap = addMap("Given by", startMap == null ? null : startMap.map(), startPins);
      turnedInMap = addMap("Turned in to", endMap == null ? null : endMap.map(), endPins);
    }
    addLootSection("Rewards", d.rewards());
    addSection("Related items", d.relatedItems().size(), Columns.itemSources(), d.relatedItems());
    bindJumpKeys();
  }

  /** The emphasised pins of {@code m}'s points (all on the map), each point once; none without a map. */
  private static List<Pin> giverPins(GiverMap m, boolean starts) {
    return m == null ? List.of() : m.points().stream()
        .map(p -> Pin.giver(p.ref(), p.displayName(), p.x(), p.y(), starts)).distinct().toList();
  }

  private static Set<Spot> spots(List<Pin> pins) {
    return pins.stream().map(p -> new Spot(p.x(), p.y())).collect(Collectors.toSet());
  }

  /** Puts a map pinning {@code pins} above the table of section {@code title}; null (no map) without pins. */
  private MapView addMap(String title, BufferedImage image, List<Pin> pins) {
    SourceSection section = section(title);
    if (section == null || image == null || pins.isEmpty()) {
      return null;
    }
    MapView map = new MapView(image, nav);
    map.setPins(pins);
    section.addAboveTable(map);
    return map;
  }

  private static List<QuestGiver> byRole(List<QuestGiver> givers, String role) {
    return givers.stream().filter(g -> role.equals(g.role())).toList();
  }

  public HeaderPanel header() {
    return header;
  }

  /** The map of the Given by section; null without one. */
  public MapView givenByMap() {
    return givenByMap;
  }

  /** The map of the Turned in to section; null without one (or when Given by's map shows the end givers too). */
  public MapView turnedInMap() {
    return turnedInMap;
  }

  /** The read-only objectives text; hidden when the quest has none. */
  public JTextArea objectives() {
    return objectives;
  }
}
