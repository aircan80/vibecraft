package dev.wowforever.app.ui.pages;

import dev.wowforever.app.db.TravelMapRepository;
import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.WayCommand;
import dev.wowforever.app.ui.widgets.ClipboardPort;
import dev.wowforever.app.ui.widgets.MapCache;
import dev.wowforever.app.ui.widgets.MapView;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.*;

/** Factual instance access, loaded off the UI thread; missing geography stays explicit. */
public final class InstanceEntrancePanel extends JPanel {
  public record Located(InstanceAccess.Location location, BufferedImage map) {}
  public record Loaded(List<Located> locations, List<InstanceAccess.Parent> parents) {
    public static final Loaded EMPTY = new Loaded(List.of(),List.of());
    public Loaded { locations=List.copyOf(locations); parents=List.copyOf(parents); }
  }

  /** Call on the page worker, alongside the instance's other facts. */
  public static Loaded load(TravelMapRepository repository,long instanceId) {
    InstanceAccess access=repository.forInstance(instanceId);
    return new Loaded(access.locations().stream().map(location -> new Located(location,
        location.map().flatMap(MapCache.shared()::decoded).orElse(null))).toList(),access.parents());
  }

  public InstanceEntrancePanel(Loaded data,Navigator nav) { this(data,nav,ClipboardPort.system()); }

  public InstanceEntrancePanel(Loaded data,Navigator nav,ClipboardPort clipboard) {
    setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder(8,12,8,12));
    JLabel heading=new JLabel("Entrance"); heading.setAlignmentX(LEFT_ALIGNMENT); add(heading);
    if(data.locations().isEmpty() && data.parents().isEmpty()) {
      JLabel missing=new JLabel("Entrance not yet recorded"); missing.setAlignmentX(LEFT_ALIGNMENT); add(missing);
    }
    for(var parent:data.parents()) {
      JButton link=new JButton("Entered from "+parent.name()); link.setAlignmentX(LEFT_ALIGNMENT);
      link.addActionListener(e -> nav.open(new ViewTarget.Entity(new EntityRef(EntityKind.INSTANCE,parent.instanceId()))));
      add(link);
    }
    for(var located:data.locations()) {
      var location=located.location(); var pin=location.pin();
      JPanel row=new JPanel(new FlowLayout(FlowLayout.LEADING,4,0)); row.setOpaque(false); row.setAlignmentX(LEFT_ALIGNMENT);
      JButton zone=new JButton(location.zoneName());
      zone.addActionListener(e -> nav.open(new ViewTarget.Entity(new EntityRef(EntityKind.ZONE,location.zoneId()))));
      row.add(new JLabel(pin.name()+" · ")); row.add(zone);
      String way=WayCommand.of(location.zoneName(),pin.x(),pin.y());
      JButton copy=new JButton("Copy /way"); copy.setToolTipText(way); copy.addActionListener(e -> clipboard.copy(way)); row.add(copy);
      row.setMaximumSize(new Dimension(Integer.MAX_VALUE,row.getPreferredSize().height)); add(row);
      if(located.map()!=null) {
        MapView map=new MapView(located.map(),nav);
        map.setTravel(new TravelMapData(List.of(pin),List.of(),List.of()));
        JPanel frame=new JPanel(new BorderLayout()); frame.setOpaque(false); frame.add(map); frame.setAlignmentX(LEFT_ALIGNMENT);
        frame.setPreferredSize(new Dimension(480,320)); frame.setMaximumSize(new Dimension(640,427)); add(frame);
      }
    }
  }
}
