package dev.wowforever.app.ui.pages;

import dev.wowforever.app.db.ZoneRepository;
import dev.wowforever.app.model.QuestPin;
import dev.wowforever.app.model.QuestPins;
import dev.wowforever.app.model.SpawnPin;
import dev.wowforever.app.model.NpcRoute;
import dev.wowforever.app.ui.widgets.MapLayer;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Async;
import dev.wowforever.app.ui.BooleanSetting;
import dev.wowforever.app.ui.StatusSink;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.widgets.MapView;
import dev.wowforever.app.ui.widgets.MapView.Pin;
import dev.wowforever.app.ui.widgets.PinLegend;
import dev.wowforever.app.ui.widgets.RaisedBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.HierarchyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;
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
 * The zone page's map: toggles over a {@link MapView} of the zone. The map pins the zone's bosses and rares, loaded
 * with the page. "All NPCs" (paired with "Bosses & rares"), "Objects" and "Quests" (the quest givers, off unless
 * its remembered setting is on) load their pins on the worker the first time they are asked for, showing
 * {@code "Loading pins…"} meanwhile. Pins already loading are not asked for again: a toggle changed while a load is
 * in flight loads only what is neither loaded nor loading, and every load's pins are kept when it finishes; the pin
 * count is reported once nothing is loading any more. A load that fails is reported to the {@link StatusSink} and
 * its toggles go back to what the map shows. A legend under the map tells the pin kinds ({@link PinLegend}). In the
 * compact window the toggles collapse into a "Pins ▾" menu.
 */
public final class ZoneMapPanel extends JPanel {
  /** The status shown while pins load. */
  public static final String LOADING = "Loading pins…";

  /** Where the zone map's other pins come from (called on the worker, never on the EDT). */
  public interface PinSource {
    /** The NPC spawn points in the zone, of bosses and rares only or of every NPC. */
    List<SpawnPin> npcs(long zoneId, boolean bossesAndRaresOnly);

    /** The object spawn points in the zone. */
    List<SpawnPin> objects(long zoneId);

    /** The spawn points of the zone's quest givers, with their quests, and whether the query was cut. */
    QuestPins questPins(long zoneId);

    /** Named patrol routes, read on the worker. */
    default List<NpcRoute> routes(long zoneId) { return List.of(); }

    /** The spawn queries of {@code zones}. */
    static PinSource of(ZoneRepository zones) {
      return new PinSource() {
        @Override
        public List<NpcRoute> routes(long zoneId) {
          return zones.npcRoutesInZone(zoneId);
        }

        @Override
        public List<SpawnPin> npcs(long zoneId, boolean bossesAndRaresOnly) {
          return zones.npcSpawnsInZone(zoneId, bossesAndRaresOnly);
        }

        @Override
        public List<SpawnPin> objects(long zoneId) {
          return zones.objectSpawnsInZone(zoneId);
        }

        @Override
        public QuestPins questPins(long zoneId) {
          return zones.questGiversInZone(zoneId);
        }
      };
    }
  }

  /**
   * What one pin load brought: the pins of every NPC, of the objects and of the quest givers, each null when not
   * asked for, and whether the quest givers' query was cut at its cap.
   */
  private record LoadedPins(List<Pin> npcs, List<Pin> objects, List<Pin> quests, boolean questsCapped) {}

  /** The pins a toggle loads: every NPC, the objects, the quest givers. */
  private enum PinLoad { NPCS, OBJECTS, QUESTS }

  private static final MapLayer ROUTES_LAYER = MapLayer.REGISTERED.stream()
      .filter(layer -> layer.id().equals("routes")).findFirst().orElseThrow();
  private final JToggleButton routesToggle = new JToggleButton("Routes");
  private List<MapView.RouteLine> routeLines;
  private boolean loadingRoutes;

  private final long zoneId;
  private final PinSource pinSource;
  private final Executor worker;
  private final StatusSink status;
  private final MapView mapView;
  private final JPanel toggleRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
  private final JToggleButton bossesToggle = new JToggleButton("Bosses & rares", true);
  private final JToggleButton allNpcsToggle = new JToggleButton("All NPCs");
  private final JToggleButton objectsToggle = new JToggleButton("Objects");
  private final JToggleButton questsToggle = new JToggleButton("Quests");
  private final JButton pinsMenuButton = new JButton("Pins ▾");
  private final JPopupMenu pinsMenu = new JPopupMenu();
  private final PinLegend legend;
  private final JLabel capNote = PageContent.mutedLabel(String.format(Locale.ROOT,
      "Showing the first %,d points of a kind: the zone has more.", ZoneRepository.PIN_CAP));
  private final List<Pin> defaultPins;
  private List<Pin> allNpcPins;
  private List<Pin> objectPins;
  private List<Pin> questPins;
  /** Whether the quest givers' query was cut at {@link ZoneRepository#PIN_CAP} rows. */
  private boolean questPinsCapped;
  /** The pin kinds a load in flight is bringing; never asked for again meanwhile. */
  private final EnumSet<PinLoad> loading = EnumSet.noneOf(PinLoad.class);
  /** While a failed load puts its toggles back: their changes load nothing. */
  private boolean reverting;

  /**
   * The map of zone {@code zoneId}, decoded ({@code map}), pinning {@code pins} (its bosses and rares) at first; the
   * toggles load the other pins from {@code pinSource} on {@code worker} and report to {@code status}. "Quests" is off
   * and is not remembered.
   */
  public ZoneMapPanel(long zoneId, BufferedImage map, List<SpawnPin> pins, PinSource pinSource, Executor worker,
      StatusSink status, Navigator nav) {
    this(zoneId, map, pins, pinSource, worker, status, nav, BooleanSetting.inMemory(false));
  }

  /**
   * The map as above, whose "Quests" toggle starts as {@code questsShown} says (loading the quest givers at once
   * when it is on) and saves every change to it there.
   */
  public ZoneMapPanel(long zoneId, BufferedImage map, List<SpawnPin> pins, PinSource pinSource, Executor worker,
      StatusSink status, Navigator nav, BooleanSetting questsShown) {
    this(zoneId, map, pins, pinSource, worker, status, nav, questsShown, BooleanSetting.inMemory(false));
  }

  /** Zone map with independently remembered quest and patrol layers. */
  public ZoneMapPanel(long zoneId, BufferedImage map, List<SpawnPin> pins, PinSource pinSource, Executor worker,
      StatusSink status, Navigator nav, BooleanSetting questsShown, BooleanSetting routesShown) {
    super(new BorderLayout());
    Objects.requireNonNull(routesShown, "routesShown");
    Objects.requireNonNull(questsShown, "questsShown");
    this.zoneId = zoneId;
    this.pinSource = Objects.requireNonNull(pinSource, "pinSource");
    this.worker = Objects.requireNonNull(worker, "worker");
    this.status = Objects.requireNonNull(status, "status");
    this.defaultPins = pins(pins);
    mapView = new MapView(map, nav);
    legend = PinLegend.interactive(mapView);
    ButtonGroup npcs = new ButtonGroup();
    npcs.add(bossesToggle);
    npcs.add(allNpcsToggle);
    toggleRow.setOpaque(false);
    toggleRow.add(bossesToggle);
    toggleRow.add(allNpcsToggle);
    toggleRow.add(objectsToggle);
    toggleRow.add(questsToggle);
    toggleRow.add(routesToggle);
    pinsMenu.add(sharing(new JRadioButtonMenuItem(bossesToggle.getText()), bossesToggle));
    pinsMenu.add(sharing(new JRadioButtonMenuItem(allNpcsToggle.getText()), allNpcsToggle));
    pinsMenu.add(sharing(new JCheckBoxMenuItem(objectsToggle.getText()), objectsToggle));
    pinsMenu.add(sharing(new JCheckBoxMenuItem(questsToggle.getText()), questsToggle));
    pinsMenu.add(sharing(new JCheckBoxMenuItem(routesToggle.getText()), routesToggle));
    pinsMenuButton.setVisible(false);
    pinsMenuButton.addActionListener(e -> {
      if (pinsMenuButton.isShowing()) {
        pinsMenu.show(pinsMenuButton, 0, pinsMenuButton.getHeight());
      }
    });
    // set before its listener: opening a page is not a change to save (the quest pins load below)
    questsToggle.setSelected(questsShown.isOn());
    routesToggle.setSelected(routesShown.isOn());
    routesToggle.addItemListener(e -> {
      routesShown.set(routesToggle.isSelected());
      routesChanged();
    });
    mapView.addPropertyChangeListener("layerVisibility", e -> {
      if (ROUTES_LAYER.id().equals(e.getNewValue())) {
        routesToggle.setSelected(mapView.isLayerVisible(ROUTES_LAYER));
      }
    });
    // one of the pair always changes when the selection moves between them
    bossesToggle.addActionListener(e -> {
      if (reverting) return;
      objectsToggle.setSelected(false);
      questsToggle.setSelected(false);
      routesToggle.setSelected(false);
      mapView.applyPreset(dev.wowforever.app.ui.widgets.MapLayer.BOSSES_AND_RARES);
      pinsChanged();
    });
    allNpcsToggle.addItemListener(e -> {
      if (allNpcsToggle.isSelected()) {
        mapView.setKindVisible(MapView.PinKind.NPC, true);
        mapView.setKindVisible(MapView.PinKind.BOSS, true);
        mapView.setKindVisible(MapView.PinKind.RARE, true);
      }
      pinsChanged();
    });
    objectsToggle.addItemListener(e -> {
      if (objectsToggle.isSelected()) mapView.setKindVisible(MapView.PinKind.OBJECT, true);
      pinsChanged();
    });
    questsToggle.addItemListener(e -> {
      if (questsToggle.isSelected()) {
        mapView.setKindVisible(MapView.PinKind.QUEST_START, true);
        mapView.setKindVisible(MapView.PinKind.QUEST_END, true);
      }
      questsShown.set(questsToggle.isSelected());
      pinsChanged();
    });
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    controls.setOpaque(false);
    controls.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
    controls.add(toggleRow);
    controls.add(pinsMenuButton);
    setOpaque(false);
    setAlignmentX(LEFT_ALIGNMENT);
    setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(8, 12, 2, 12), // a raised panel
        BorderFactory.createCompoundBorder(RaisedBorder.create(), BorderFactory.createEmptyBorder(6, 6, 4, 6))));
    add(controls, BorderLayout.NORTH);
    add(mapView, BorderLayout.CENTER);
    JPanel below = new JPanel(new BorderLayout());
    below.setOpaque(false);
    below.add(legend, BorderLayout.NORTH);
    capNote.setVisible(false);
    below.add(capNote, BorderLayout.SOUTH);
    add(below, BorderLayout.SOUTH);
    addHierarchyListener(e -> {
      if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
        boolean compact = CompactColumns.within(this);
        toggleRow.setVisible(!compact);
        pinsMenuButton.setVisible(compact);
      }
    });
    showPins();
    if (questsToggle.isSelected()) {
      pinsChanged();
    }
    if (routesToggle.isSelected()) routesChanged();
  }

  private void routesChanged() {
    mapView.setLayerVisible(ROUTES_LAYER, routesToggle.isSelected());
    if (routeLines != null) {
      mapView.setRoutes(routeLines);
      return;
    }
    if (!routesToggle.isSelected() || loadingRoutes) return;
    loadingRoutes = true;
    status.message("Loading routes…");
    Async.run(worker, () -> pinSource.routes(zoneId).stream().map(r -> new MapView.RouteLine(r.npc(), r.name(),
        r.rank() != null && r.rank() == 3 ? MapView.PinKind.BOSS
            : r.rank() != null && (r.rank() == 2 || r.rank() == 4) ? MapView.PinKind.RARE : MapView.PinKind.NPC,
        r.route().points(), r.route().loop())).toList(), loaded -> {
          loadingRoutes = false;
          routeLines = loaded;
          // The latest toggle state wins if this load completed after a preset or a click switched it off.
          mapView.setRoutes(routeLines);
          mapView.setLayerVisible(ROUTES_LAYER, routesToggle.isSelected());
          status.message(routesToggle.isSelected() ? loaded.size() + " patrol routes on the map"
              : mapView.visiblePins().size() + " pins on the map");
        }, failure -> {
          loadingRoutes = false;
          routesToggle.setSelected(false);
          status.fail(failure);
        });
  }

  /** {@code item} driven by {@code toggle}'s model, so either changes both. */
  private static JMenuItem sharing(JMenuItem item, AbstractButton toggle) {
    item.setModel(toggle.getModel());
    return item;
  }

  private static List<Pin> pins(List<SpawnPin> spawns) {
    return spawns.stream().map(p -> Pin.of(p, false)).toList();
  }

  private static List<Pin> questPins(List<QuestPin> givers) {
    return givers.stream().map(Pin::of).toList();
  }

  /** Shows the pins the toggles ask for, loading on the worker those neither loaded nor loading yet. */
  private void pinsChanged() {
    if (reverting) {
      return;
    }
    boolean needNpcs = allNpcsToggle.isSelected() && allNpcPins == null && !loading.contains(PinLoad.NPCS);
    boolean needObjects = objectsToggle.isSelected() && objectPins == null && !loading.contains(PinLoad.OBJECTS);
    boolean needQuests = questsToggle.isSelected() && questPins == null && !loading.contains(PinLoad.QUESTS);
    showPins();
    if (!needNpcs && !needObjects && !needQuests) {
      return;
    }
    EnumSet<PinLoad> load = EnumSet.noneOf(PinLoad.class);
    if (needNpcs) {
      load.add(PinLoad.NPCS);
    }
    if (needObjects) {
      load.add(PinLoad.OBJECTS);
    }
    if (needQuests) {
      load.add(PinLoad.QUESTS);
    }
    loading.addAll(load);
    status.message(LOADING);
    Async.run(worker, () -> load(needNpcs, needObjects, needQuests), loaded -> {
          loading.removeAll(load);
          if (loaded.npcs() != null) {
            allNpcPins = loaded.npcs();
          }
          if (loaded.objects() != null) {
            objectPins = loaded.objects();
          }
          if (loaded.quests() != null) {
            questPins = loaded.quests();
            questPinsCapped = loaded.questsCapped();
          }
          showPins();
          if (loading.isEmpty()) {
            int n = mapView.visiblePins().size();
            status.message(n == 1 ? "1 pin on the map" : String.format(Locale.ROOT, "%,d pins on the map", n));
          }
        }, e -> {
          loading.removeAll(load);
          // back to what the map shows: those pins did not load (asking again retries). Objects and Quests go off
          // before the bosses come back, and none of the changes loads anything, so a combined load leaves no stray
          // one
          reverting = true;
          try {
            if (needObjects) {
              objectsToggle.setSelected(false);
            }
            if (needQuests) {
              questsToggle.setSelected(false);
            }
            if (needNpcs) {
              bossesToggle.setSelected(true);
            }
          } finally {
            reverting = false;
          }
          showPins();
          status.fail(e);
        });
  }

  /** Reads the pins asked for (on the worker): every NPC, then the objects, then the quest givers. */
  private LoadedPins load(boolean npcs, boolean objects, boolean quests) {
    List<Pin> everyNpc = npcs ? pins(pinSource.npcs(zoneId, false)) : null;
    List<Pin> objectSpawns = objects ? pins(pinSource.objects(zoneId)) : null;
    QuestPins givers = quests ? pinSource.questPins(zoneId) : null;
    return new LoadedPins(everyNpc, objectSpawns, givers == null ? null : questPins(givers.pins()),
        givers != null && givers.capped());
  }

  /**
   * The NPC pins (every NPC once loaded, else the bosses and rares; the map hides the plain NPCs unless "All NPCs"
   * is on), with "Objects" on and loaded the object pins, and with "Quests" on and loaded the quest givers' pins.
   */
  private void showPins() {
    List<Pin> shown = new ArrayList<>(allNpcPins != null ? allNpcPins : defaultPins);
    if (objectsToggle.isSelected() && objectPins != null) {
      shown.addAll(objectPins);
    }
    if (questsToggle.isSelected() && questPins != null) {
      shown.addAll(questPins);
    }
    mapView.setPins(shown);
    mapView.setFilter(!allNpcsToggle.isSelected());
    boolean capped = capped(allNpcPins) || (objectsToggle.isSelected() && capped(objectPins))
        || (questsToggle.isSelected() && questPins != null && questPinsCapped);
    if (capNote.isVisible() != capped) {
      capNote.setVisible(capped);
      revalidate();
    }
  }

  /**
   * Whether {@code pins} is a spawn query cut at {@link ZoneRepository#PIN_CAP}: one pin a row, so a full list was
   * cut. (Quest givers group rows into pins, so their query says so itself.)
   */
  private static boolean capped(List<Pin> pins) {
    return pins != null && pins.size() >= ZoneRepository.PIN_CAP;
  }

  @Override
  public Dimension getMaximumSize() {
    return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
  }

  public MapView mapView() {
    return mapView;
  }

  /** "Bosses & rares" (on at first) and "All NPCs": one of them is on. */
  public JToggleButton bossesToggle() {
    return bossesToggle;
  }

  public JToggleButton allNpcsToggle() {
    return allNpcsToggle;
  }

  /** "Objects": adds the object spawn points. */
  public JToggleButton objectsToggle() {
    return objectsToggle;
  }

  /** "Quests": adds the quest givers' pins. */
  public JToggleButton routesToggle() { return routesToggle; }

  public JToggleButton questsToggle() {
    return questsToggle;
  }

  /** The muted note shown while a pin list was cut at {@link ZoneRepository#PIN_CAP}. */
  public JLabel capNote() {
    return capNote;
  }

  /** The row under the map that tells what its pins are. */
  public PinLegend legend() {
    return legend;
  }

  /** The compact window's "Pins ▾" button, shown in place of the toggles. */
  public JButton pinsMenuButton() {
    return pinsMenuButton;
  }

  /** The menu of the "Pins ▾" button: the four toggles. */
  public JPopupMenu pinsMenu() {
    return pinsMenu;
  }
}
