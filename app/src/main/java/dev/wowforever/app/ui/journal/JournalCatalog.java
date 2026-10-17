package dev.wowforever.app.ui.journal;

import dev.wowforever.app.db.*;
import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.*;
import java.util.List;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import javax.swing.JComponent;

/** Explorer snapshots are read on the worker, then rendered without database access. */
public final class JournalCatalog {
  private JournalCatalog() {}
  /** Art is decoded by the worker and treated as read-only after publication. */
  public record HomeSnapshot(List<InstanceSummary> instances, String build, BufferedImage spotlightArt, String artLabel) {
    public HomeSnapshot { instances = List.copyOf(instances); }
    public HomeSnapshot(List<InstanceSummary> instances,String build){this(instances,build,null,"");}
  }
  public record DungeonSnapshot(List<InstanceSummary> instances, List<ForeverContent.Discovery> discoveries) {
    public DungeonSnapshot { instances=List.copyOf(instances); discoveries=List.copyOf(discoveries); }
  }
  /** Returns null for destinations which retain their existing detailed page. */
  public static Object load(ViewTarget target, Database db) {
    if (target instanceof ViewTarget.Home) {
      List<InstanceSummary> instances=new InstanceRepository(db).all();
      BufferedImage art=null;String label="";
      var spotlight=instances.stream().filter(i->InstanceSummary.CLIENT.equals(i.source())&&i.name()!=null&&i.name().contains("Dalaran"))
          .sorted(java.util.Comparator.comparing(InstanceSummary::displayName)).findFirst();
      if(spotlight.isPresent()) {
        var floors=new InstanceMapRepository(db).load(spotlight.get().id());
        if(!floors.isEmpty())try {
          art=ImageIO.read(new ByteArrayInputStream(floors.getFirst().jpeg()));label=floors.getFirst().label();
        }catch(java.io.IOException ignored){ /* The guide remains usable when optional art is unreadable. */ }
      }
      return new HomeSnapshot(instances,db.meta().getOrDefault("client_build", "unknown"),art,label);
    }
    if (target instanceof ViewTarget.Forever f && f.section().equals("ladder")
        || target instanceof ViewTarget.List l && l.kind()==EntityKind.INSTANCE)
      return new DungeonSnapshot(new InstanceRepository(db).all(),new ForeverContentRepository(db).discoveries());
    return null;
  }
  public static JComponent render(Object snapshot, Navigator nav) {
    if(snapshot instanceof HomeSnapshot home) return new JournalHomePage(home,nav);
    if(snapshot instanceof DungeonSnapshot dungeons) return new JournalDungeonPage(dungeons,nav);
    return null;
  }
}
