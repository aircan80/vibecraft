package dev.wowforever.app.ui.pages;

import dev.wowforever.app.db.ZoneRepository;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.model.SpawnPin;
import dev.wowforever.app.model.ZoneDetail;
import dev.wowforever.app.model.ZoneSummary;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Async;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.Labels;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.widgets.LinkButton;
import dev.wowforever.app.ui.widgets.MapView;
import dev.wowforever.app.ui.widgets.MapView.Pin;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.HierarchyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;

/**
 * The zone page, one scrolling page: the header (kind, the level range of the zone's NPCs, a parent link), its
 * dungeons and raids and its sub-zones as compact rows of links, the zone map with spawn pins (when the zone has a
 * map), then the sections NPCs (bosses and rares first), Objects and Quests (by level). Sections without rows are
 * left out.
 *
 * <p>The map pins the zone's bosses and rares, loaded with the page. Toggles above it switch to "All NPCs" and add
 * "Objects"; their spawn points are loaded on the worker the first time they are asked for (a newer load drops an
 * older one still in flight). In the compact window the toggles collapse into a "Pins" menu.
 */
public final class ZonePage extends SourcesPage {
  /** Where the zone map's other pins come from (called on the worker, never on the EDT). */
  public interface PinSource {
    /** The NPC spawn points in the zone, of bosses and rares only or of every NPC. */
    List<SpawnPin> npcs(long zoneId, boolean bossesAndRaresOnly);

    /** The object spawn points in the zone. */
    List<SpawnPin> objects(long zoneId);

    /** The spawn queries of {@code zones}. */
    static PinSource of(ZoneRepository zones) {
      return new PinSource() {
        @Override
        public List<SpawnPin> npcs(long zoneId, boolean bossesAndRaresOnly) {
          return zones.npcSpawnsInZone(zoneId, bossesAndRaresOnly);
        }

        @Override
        public List<SpawnPin> objects(long zoneId) {
          return zones.objectSpawnsInZone(zoneId);
        }
      };
    }
  }

  /** What one pin load brought: the pins of every NPC and of the objects, each null when not asked for. */
  private record LoadedPins(List<Pin> npcs, List<Pin> objects) {}

  private final HeaderPanel header;
  private final List<JButton> instanceLinks = new ArrayList<>();
  private final List<JButton> subzoneLinks = new ArrayList<>();
  private final long zoneId;
  private final PinSource pinSource;
  private final Executor worker;
  private final MapView mapView;
  private final JPanel mapArea;
  private final JPanel toggleRow;
  private final JToggleButton bossesToggle;
  private final JToggleButton allNpcsToggle;
  private final JToggleButton objectsToggle;
  private final JButton pinsMenuButton;
  private final JPopupMenu pinsMenu;
  private final List<Pin> defaultPins;
  private List<Pin> allNpcPins;
  private List<Pin> objectPins;
  private int pinGeneration;

  /** The page without a map. */
  public ZonePage(ZoneDetail d, Navigator nav) {
    this(d, null, List.of(), null, Runnable::run, nav);
  }

  /**
   * The page with {@code map}, the zone's decoded map (null: none, and no map area), pinning {@code pins} (the
   * zone's bosses and rares) at first; the toggles load the other pins from {@code pinSource} on {@code worker}.
   */
  public ZonePage(ZoneDetail d, BufferedImage map, List<SpawnPin> pins, PinSource pinSource, Executor worker,
      Navigator nav) {
    super(nav);
    this.zoneId = d.summary().id();
    this.pinSource = pinSource;
    this.worker = Objects.requireNonNull(worker, "worker");
    this.defaultPins = pins(pins);
    ZoneSummary s = d.summary();
    header = new HeaderPanel(s.displayName(), Labels.join("Zone #" + s.id(), Labels.zoneSubtitle(s.kind(), null),
        levelRange(d.npcs())));
    header.setAlignmentX(LEFT_ALIGNMENT);
    if (s.parentId() != null) {
      EntityRef parent = new EntityRef(EntityKind.ZONE, s.parentId());
      header.addLink(parent.displayName(s.parentName()), parent, nav);
    }
    content.add(header);
    addLinkRow("Dungeons & raids", d.instances(), i -> Labels.instanceTitle(i.displayName(), i.key()),
        InstanceSummary::ref, instanceLinks);
    addLinkRow("Sub-zones", d.subzones(), ZoneSummary::displayName, ZoneSummary::ref, subzoneLinks);
    if (map == null) {
      mapView = null;
      mapArea = null;
      toggleRow = null;
      bossesToggle = null;
      allNpcsToggle = null;
      objectsToggle = null;
      pinsMenuButton = null;
      pinsMenu = null;
    } else {
      Objects.requireNonNull(pinSource, "pinSource");
      mapView = new MapView(map, nav);
      bossesToggle = new JToggleButton("Bosses & rares", true);
      allNpcsToggle = new JToggleButton("All NPCs");
      objectsToggle = new JToggleButton("Objects");
      ButtonGroup npcs = new ButtonGroup();
      npcs.add(bossesToggle);
      npcs.add(allNpcsToggle);
      toggleRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
      toggleRow.setOpaque(false);
      toggleRow.add(bossesToggle);
      toggleRow.add(allNpcsToggle);
      toggleRow.add(objectsToggle);
      pinsMenu = new JPopupMenu();
      pinsMenu.add(sharing(new JRadioButtonMenuItem(bossesToggle.getText()), bossesToggle));
      pinsMenu.add(sharing(new JRadioButtonMenuItem(allNpcsToggle.getText()), allNpcsToggle));
      pinsMenu.add(sharing(new JCheckBoxMenuItem(objectsToggle.getText()), objectsToggle));
      pinsMenuButton = new JButton("Pins \u25BE");
      pinsMenuButton.setVisible(false);
      pinsMenuButton.addActionListener(e -> {
        if (pinsMenuButton.isShowing()) {
          pinsMenu.show(pinsMenuButton, 0, pinsMenuButton.getHeight());
        }
      });
      // one of the pair always changes when the selection moves between them
      allNpcsToggle.addItemListener(e -> pinsChanged());
      objectsToggle.addItemListener(e -> pinsChanged());
      JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
      controls.setOpaque(false);
      controls.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
      controls.add(toggleRow);
      controls.add(pinsMenuButton);
      mapArea = new JPanel(new BorderLayout()) {
        @Override
        public Dimension getMaximumSize() {
          return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
      };
      mapArea.setOpaque(false);
      mapArea.setAlignmentX(LEFT_ALIGNMENT);
      mapArea.setBorder(BorderFactory.createEmptyBorder(8, 12, 2, 12));
      mapArea.add(controls, BorderLayout.NORTH);
      mapArea.add(mapView, BorderLayout.CENTER);
      mapArea.addHierarchyListener(e -> {
        if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
          boolean compact = CompactColumns.within(mapArea);
          toggleRow.setVisible(!compact);
          pinsMenuButton.setVisible(compact);
        }
      });
      content.add(mapArea);
      showPins();
    }
    List<NpcSummary> npcs = d.npcs().stream().sorted(Comparator.comparingInt((NpcSummary n) -> rankOrder(n.rank()))
        .thenComparing(NpcSummary::displayName, String.CASE_INSENSITIVE_ORDER)
        .thenComparingLong(NpcSummary::id)).toList();
    addSection("NPCs", npcs.size(), VisibleColumns.of(Columns.npcs(), npcs, s.name()), npcs);
    addSection("Objects", d.objects().size(), VisibleColumns.of(Columns.objects(), d.objects(), s.name()),
        d.objects());
    List<QuestSummary> quests = d.quests().stream().sorted(Comparator.comparing(QuestSummary::questLevel,
            Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(QuestSummary::displayName, String.CASE_INSENSITIVE_ORDER)
        .thenComparingLong(QuestSummary::id)).toList();
    addSection("Quests", quests.size(), VisibleColumns.of(Columns.quests(), quests, s.name()), quests);
    bindJumpKeys();
  }

  /** {@code item} driven by {@code toggle}'s model, so either changes both. */
  private static JMenuItem sharing(JMenuItem item, AbstractButton toggle) {
    item.setModel(toggle.getModel());
    return item;
  }

  private static List<Pin> pins(List<SpawnPin> spawns) {
    return spawns.stream().map(p -> Pin.of(p, false)).toList();
  }

  /** Shows the pins the toggles ask for, loading on the worker those not loaded yet. */
  private void pinsChanged() {
    boolean needNpcs = allNpcsToggle.isSelected() && allNpcPins == null;
    boolean needObjects = objectsToggle.isSelected() && objectPins == null;
    if (needNpcs || needObjects) {
      int generation = ++pinGeneration;
      Async.run(worker, () -> new LoadedPins(needNpcs ? pins(pinSource.npcs(zoneId, false)) : null,
          needObjects ? pins(pinSource.objects(zoneId)) : null), loaded -> {
            if (generation == pinGeneration) {
              if (loaded.npcs() != null) {
                allNpcPins = loaded.npcs();
              }
              if (loaded.objects() != null) {
                objectPins = loaded.objects();
              }
              showPins();
            }
          }, e -> {
            // the map keeps the pins it shows; asking again retries
          });
    }
    showPins();
  }

  /**
   * The NPC pins (every NPC once loaded, else the bosses and rares; the map hides the plain NPCs unless "All NPCs"
   * is on) and, with "Objects" on and loaded, the object pins.
   */
  private void showPins() {
    List<Pin> shown = new ArrayList<>(allNpcPins != null ? allNpcPins : defaultPins);
    if (objectsToggle.isSelected() && objectPins != null) {
      shown.addAll(objectPins);
    }
    mapView.setPins(shown);
    mapView.setFilter(!allNpcsToggle.isSelected());
  }

  /**
   * {@code "Level 5–12"}: the lowest and highest level of the zone's NPCs, levels of 0 and below and unknown ones
   * left out (an NPC with one known level counts it as both); null when no NPC has a level.
   */
  static String levelRange(List<NpcSummary> npcs) {
    Integer low = null;
    Integer high = null;
    for (NpcSummary n : npcs) {
      Integer min = positive(n.minLevel()) != null ? positive(n.minLevel()) : positive(n.maxLevel());
      Integer max = positive(n.maxLevel()) != null ? positive(n.maxLevel()) : positive(n.minLevel());
      if (min != null) {
        low = low == null ? min : Math.min(low, min);
        high = high == null ? max : Math.max(high, max);
      }
    }
    return Labels.levels(low, high);
  }

  private static Integer positive(Integer level) {
    return level != null && level > 0 ? level : null;
  }

  /** Bosses first, then rare elites, rares, elites and the rest (normal or unknown). */
  private static int rankOrder(Integer rank) {
    if (rank == null) {
      return 4;
    }
    return switch (rank) {
      case 3 -> 0;
      case 2 -> 1;
      case 4 -> 2;
      case 1 -> 3;
      default -> 4;
    };
  }

  /** A row {@code "<label>: a · b"} of links to {@code entries}; nothing when there are none. */
  private <T> void addLinkRow(String label, List<T> entries, Function<T, String> text, Function<T, EntityRef> ref,
      List<JButton> links) {
    if (entries.isEmpty()) {
      return;
    }
    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0)) {
      @Override
      public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
      }
    };
    row.setOpaque(false);
    row.setAlignmentX(LEFT_ALIGNMENT);
    row.setBorder(BorderFactory.createEmptyBorder(2, 12, 2, 12));
    row.add(PageContent.mutedLabel(label + ": "));
    for (T entry : entries) {
      if (!links.isEmpty()) {
        row.add(new JLabel(" · "));
      }
      JButton link = LinkButton.create(Objects.requireNonNull(text.apply(entry)), ref.apply(entry), nav);
      links.add(link);
      row.add(link);
    }
    content.add(row);
  }

  public HeaderPanel header() {
    return header;
  }

  /** The links to the zone's dungeons and raids, in order. */
  public List<JButton> instanceLinks() {
    return List.copyOf(instanceLinks);
  }

  /** The links to the zone's sub-zones, in order. */
  public List<JButton> subzoneLinks() {
    return List.copyOf(subzoneLinks);
  }

  /** The zone map; null when the zone has none. */
  public MapView mapView() {
    return mapView;
  }

  /** The toggles and the map below them; null when the zone has no map. */
  public JPanel mapArea() {
    return mapArea;
  }

  /** "Bosses & rares" (on at first) and "All NPCs", one of them on; null without a map. */
  public JToggleButton bossesToggle() {
    return bossesToggle;
  }

  public JToggleButton allNpcsToggle() {
    return allNpcsToggle;
  }

  /** "Objects": adds the object spawn points; null without a map. */
  public JToggleButton objectsToggle() {
    return objectsToggle;
  }

  /** The compact window's "Pins" button, in place of the toggles; null without a map. */
  public JButton pinsMenuButton() {
    return pinsMenuButton;
  }

  /** The menu of the "Pins" button: the three toggles; null without a map. */
  public JPopupMenu pinsMenu() {
    return pinsMenu;
  }
}
