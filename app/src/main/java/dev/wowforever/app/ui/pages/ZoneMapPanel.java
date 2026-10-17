package dev.wowforever.app.ui.pages;

import dev.wowforever.app.db.ZoneRepository;
import dev.wowforever.app.model.QuestPin;
import dev.wowforever.app.model.SpawnPin;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Async;
import dev.wowforever.app.ui.BooleanSetting;
import dev.wowforever.app.ui.StatusSink;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.widgets.MapView;
import dev.wowforever.app.ui.widgets.MapView.Pin;
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
 * its toggles go back to what the map shows. In the compact window the toggles
 * collapse into a "Pins ▾" menu.
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

    /** The spawn points of the zone's quest givers, with their quests. */
    List<QuestPin> questPins(long zoneId);

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

        @Override
        public List<QuestPin> questPins(long zoneId) {
          return zones.questGiversInZone(zoneId);
        }
      };
    }
  }

  /**
   * What one pin load brought: the pins of every NPC, of the objects and of the quest givers, each null when not
   * asked for.
   */
  private record LoadedPins(List<Pin> npcs, List<Pin> objects, List<Pin> quests) {}

  /** The pins a toggle loads: every NPC, the objects, the quest givers. */
  private enum PinLoad { NPCS, OBJECTS, QUESTS }

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
  private final List<Pin> defaultPins;
  private List<Pin> allNpcPins;
  private List<Pin> objectPins;
  private List<Pin> questPins;
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
    super(new BorderLayout());
    Objects.requireNonNull(questsShown, "questsShown");
    this.zoneId = zoneId;
    this.pinSource = Objects.requireNonNull(pinSource, "pinSource");
    this.worker = Objects.requireNonNull(worker, "worker");
    this.status = Objects.requireNonNull(status, "status");
    this.defaultPins = pins(pins);
    mapView = new MapView(map, nav);
    ButtonGroup npcs = new ButtonGroup();
    npcs.add(bossesToggle);
    npcs.add(allNpcsToggle);
    toggleRow.setOpaque(false);
    toggleRow.add(bossesToggle);
    toggleRow.add(allNpcsToggle);
    toggleRow.add(objectsToggle);
    toggleRow.add(questsToggle);
    pinsMenu.add(sharing(new JRadioButtonMenuItem(bossesToggle.getText()), bossesToggle));
    pinsMenu.add(sharing(new JRadioButtonMenuItem(allNpcsToggle.getText()), allNpcsToggle));
    pinsMenu.add(sharing(new JCheckBoxMenuItem(objectsToggle.getText()), objectsToggle));
    pinsMenu.add(sharing(new JCheckBoxMenuItem(questsToggle.getText()), questsToggle));
    pinsMenuButton.setVisible(false);
    pinsMenuButton.addActionListener(e -> {
      if (pinsMenuButton.isShowing()) {
        pinsMenu.show(pinsMenuButton, 0, pinsMenuButton.getHeight());
      }
    });
    // set before its listener: opening a page is not a change to save (the quest pins load below)
    questsToggle.setSelected(questsShown.isOn());
    // one of the pair always changes when the selection moves between them
    allNpcsToggle.addItemListener(e -> pinsChanged());
    objectsToggle.addItemListener(e -> pinsChanged());
    questsToggle.addItemListener(e -> {
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
    setBorder(BorderFactory.createEmptyBorder(8, 12, 2, 12));
    add(controls, BorderLayout.NORTH);
    add(mapView, BorderLayout.CENTER);
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
    Async.run(worker, () -> new LoadedPins(needNpcs ? pins(pinSource.npcs(zoneId, false)) : null,
        needObjects ? pins(pinSource.objects(zoneId)) : null,
        needQuests ? questPins(pinSource.questPins(zoneId)) : null), loaded -> {
          loading.removeAll(load);
          if (loaded.npcs() != null) {
            allNpcPins = loaded.npcs();
          }
          if (loaded.objects() != null) {
            objectPins = loaded.objects();
          }
          if (loaded.quests() != null) {
            questPins = loaded.quests();
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
  public JToggleButton questsToggle() {
    return questsToggle;
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
