package dev.wowforever.app.ui.pages.quest;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.GiverSpot;
import dev.wowforever.app.model.QuestPageData;
import dev.wowforever.app.model.Spawn;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.widgets.ClipboardPort;
import dev.wowforever.app.ui.widgets.MapView;
import dev.wowforever.app.ui.widgets.MapView.Pin;
import dev.wowforever.app.ui.widgets.MapView.PinKind;
import dev.wowforever.app.ui.widgets.PinLegend;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;

/** Already-loaded quest maps, with pickup/turn-in pins and navigation between their zones. */
public final class QuestMapPanel extends JPanel {
  public record ZoneTab(long zoneId, String zoneName, String glyphs) {
    @Override
    public String toString() {
      return zoneName + (glyphs.isEmpty() ? "" : " " + glyphs);
    }
  }

  private final QuestPageData data;
  private final Map<Long, BufferedImage> maps;
  private final Navigator nav;
  private final ClipboardPort clipboard;
  private final List<ZoneTab> tabs;
  private final JPanel selectors = new JPanel(new CardLayout());
  private final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 3, 0));
  private final JComboBox<ZoneTab> combo;
  private final JPanel centre = new JPanel(new BorderLayout());
  private final JPanel foot = new JPanel(new BorderLayout());
  private final JButton banner = new JButton();
  private final JLabel noMapNote = new JLabel();
  private final JTextArea coordinates = new JTextArea();
  private final Map<Long, MapView> views = new LinkedHashMap<>();
  private final Map<Long, PinLegend> legends = new LinkedHashMap<>();
  private MapView mapView;
  private long shownZone;
  private Long bannerZone;

  public QuestMapPanel(QuestPageData data, Map<Long, BufferedImage> maps, Navigator nav, ClipboardPort clipboard) {
    super(new BorderLayout(0, 4));
    this.data = data;
    this.maps = Map.copyOf(maps);
    this.nav = nav;
    this.clipboard = clipboard;
    setOpaque(false);
    centre.setOpaque(false);
    foot.setOpaque(false);
    buttons.setOpaque(false);
    selectors.setOpaque(false);
    tabs = data.mapZones().stream().map(zone -> new ZoneTab(zone, zoneName(zone), glyphs(zone))).toList();
    combo = new JComboBox<>(tabs.toArray(ZoneTab[]::new));
    combo.getAccessibleContext().setAccessibleName("Quest map zone");
    for (ZoneTab tab : tabs) {
      JToggleButton button = new JToggleButton(tab.toString());
      button.putClientProperty("zoneId", tab.zoneId());
      button.addActionListener(e -> showZone(tab.zoneId()));
      buttons.add(button);
    }
    selectors.add(buttons, "tabs");
    selectors.add(combo, "combo");
    combo.addActionListener(e -> {
      if (combo.getSelectedItem() instanceof ZoneTab tab && tab.zoneId() != shownZone) {
        showZone(tab.zoneId());
      }
    });
    add(selectors, BorderLayout.NORTH);
    add(centre, BorderLayout.CENTER);
    add(foot, BorderLayout.SOUTH);
    coordinates.setEditable(false);
    coordinates.setLineWrap(true);
    coordinates.setWrapStyleWord(true);
    coordinates.setOpaque(false);
    coordinates.getAccessibleContext().setAccessibleName("Quest locations without a map");
    banner.addActionListener(e -> {
      if (bannerZone != null) {
        showZone(bannerZone);
      }
    });
    banner.getAccessibleContext().setAccessibleName("Show pickup or turn-in zone");
    banner.setVisible(false);
    foot.add(banner, BorderLayout.SOUTH);
    if (tabs.isEmpty()) {
      noMapNote.setText("No known locations yet.");
      centre.add(noMapNote, BorderLayout.NORTH);
      selectors.setVisible(false);
    } else {
      showZone(tabs.getFirst().zoneId());
    }
  }

  private String zoneName(long zone) {
    for (GiverSpot giver : allGivers()) {
      Optional<Spawn> point = giver.spawns().stream().filter(s -> s.zoneId() == zone).findFirst();
      if (point.isPresent() && point.get().zoneName() != null) {
        return point.get().zoneName();
      }
      for (var source : giver.itemSources()) {
        point = source.spawns().stream().filter(s -> s.zoneId() == zone).findFirst();
        if (point.isPresent() && point.get().zoneName() != null) {
          return point.get().zoneName();
        }
      }
    }
    for (var objective : data.objectives()) {
      for (var source : objective.sources()) {
        Optional<Spawn> point = source.spawns().stream().filter(s -> s.zoneId() == zone).findFirst();
        if (point.isPresent() && point.get().zoneName() != null) {
          return point.get().zoneName();
        }
      }
    }
    return data.facts().zoneId() != null && data.facts().zoneId() == zone && data.facts().zoneName() != null
        ? data.facts().zoneName() : "Zone #" + zone;
  }

  private List<GiverSpot> allGivers() {
    List<GiverSpot> all = new ArrayList<>(data.starts());
    all.addAll(data.ends());
    return all;
  }

  private String glyphs(long zone) {
    boolean starts = data.starts().stream().anyMatch(g -> g.spawns().stream().anyMatch(s -> s.zoneId() == zone));
    boolean ends = data.ends().stream().anyMatch(g -> g.spawns().stream().anyMatch(s -> s.zoneId() == zone));
    return starts && ends ? "! ?" : starts ? "!" : ends ? "?" : "";
  }

  /** A seam for objective pins in the next slice; giver roles remain distinct in a shared marker's menu. */
  public List<Pin> pins(long zone) {
    List<Pin> pins = new ArrayList<>();
    for (GiverSpot giver : allGivers()) {
      boolean start = "start".equals(giver.giver().role());
      for (Spawn spawn : giver.spawns()) {
        if (spawn.zoneId() == zone) {
          String label = giver.giver().displayName() + (start ? " — Given by" : " — Turned in to");
          pins.add(Pin.giver(giver.giver().ref(), label, spawn.x(), spawn.y(), start));
        }
      }
      if (giver.giver().ref().kind() == EntityKind.ITEM) {
        for (var source : giver.itemSources()) {
          source.spawns().stream().filter(s -> s.zoneId() == zone).forEach(s -> pins.add(new Pin(source.ref(),
              source.ref().displayName(source.name()), s.x(), s.y(), source.ref().kind() == EntityKind.OBJECT ? PinKind.OBJECT : PinKind.NPC)));
        }
      }
    }
    return pins.stream().distinct().toList();
  }

  public void showZone(long zone) {
    if (tabs.stream().noneMatch(t -> t.zoneId() == zone)) {
      return;
    }
    shownZone = zone;
    centre.removeAll();
    foot.removeAll();
    BufferedImage image = maps.get(zone);
    mapView = null;
    noMapNote.setVisible(image == null);
    if (image == null) {
      noMapNote.setText("No map for " + zoneName(zone) + " yet.");
      centre.add(noMapNote, BorderLayout.NORTH);
      coordinates.setText(allGivers().stream().flatMap(g -> g.spawns().stream()
          .filter(s -> s.zoneId() == zone).map(s -> g.giver().displayName() + " · " + Columns.coordinatesText(s)))
          .collect(java.util.stream.Collectors.joining("\n")));
      centre.add(coordinates, BorderLayout.CENTER);
    } else {
      mapView = views.computeIfAbsent(zone, ignored -> {
        MapView view = new MapView(image, nav);
        view.setPins(pins(zone));
        view.setClipboard(clipboard);
        view.setZoneName(zoneName(zone));
        return view;
      });
      centre.add(mapView, BorderLayout.CENTER);
      foot.add(legends.computeIfAbsent(zone, ignored -> PinLegend.interactive(views.get(zone))), BorderLayout.NORTH);
    }
    bannerZone = otherZone(data.ends());
    String role = "Turn-in";
    if (bannerZone == null) {
      bannerZone = otherZone(data.starts());
      role = "Pickup";
    }
    banner.setVisible(bannerZone != null);
    if (bannerZone != null) {
      banner.setText(role + " is in " + zoneName(bannerZone) + " ›");
    }
    foot.add(banner, BorderLayout.SOUTH);
    for (var component : buttons.getComponents()) {
      JToggleButton button = (JToggleButton) component;
      button.setSelected(((Long) button.getClientProperty("zoneId")) == zone);
    }
    tabs.stream().filter(t -> t.zoneId() == zone).findFirst().ifPresent(combo::setSelectedItem);
    revalidate();
    repaint();
  }

  private Long otherZone(List<GiverSpot> givers) {
    return givers.stream().flatMap(g -> g.spawns().stream()).map(Spawn::zoneId)
        .filter(zone -> zone != shownZone).findFirst().orElse(null);
  }

  @Override
  public void doLayout() {
    ((CardLayout) selectors.getLayout()).show(selectors, CompactColumns.within(this) ? "combo" : "tabs");
    super.doLayout();
  }

  @Override
  public Dimension getPreferredSize() {
    int width = getWidth() > 0 ? getWidth() : 500;
    int imageHeight = mapView == null ? 100 : mapView.heightFor(width);
    return new Dimension(width, imageHeight + selectors.getPreferredSize().height + foot.getPreferredSize().height + 8);
  }

  public void nextZone() {
    if (!tabs.isEmpty()) {
      int i = 0;
      for (; i < tabs.size(); i++) {
        if (tabs.get(i).zoneId() == shownZone) break;
      }
      showZone(tabs.get((i + 1) % tabs.size()).zoneId());
    }
  }

  public List<ZoneTab> tabs() { return tabs; }
  public long shownZone() { return shownZone; }
  public JButton banner() { return banner; }
  public JLabel noMapNote() { return noMapNote; }
  public MapView mapView() { return mapView; }
  public JComboBox<ZoneTab> zoneCombo() { return combo; }
  public JTextArea coordinates() { return coordinates; }
}
