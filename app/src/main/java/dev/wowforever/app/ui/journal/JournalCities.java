package dev.wowforever.app.ui.journal;

import dev.wowforever.app.db.Database;
import dev.wowforever.app.db.CityServicesRepository;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** City identities are verified against the pinned Forever client; rendering performs no SQL. */
public final class JournalCities {
  private JournalCities() {}
  public record City(long id,String name,String parent) {}
  public record Snapshot(List<City> cities,String build) {
    public Snapshot { cities=List.copyOf(cities); }
  }
  public record District(long id,String name,boolean guideAvailable) {}
  public record GuideSnapshot(City city,String build,List<District> districts,CityServicesRepository.Snapshot services) {
    public GuideSnapshot(City city,String build,List<District> districts){this(city,build,districts,CityServicesRepository.Snapshot.empty());}
    public GuideSnapshot { districts=List.copyOf(districts); }
  }

  /** Additional city metadata, loaded on the same worker as the existing zone guide. */
  public static GuideSnapshot loadGuide(Database db,long cityId) {
    Snapshot catalog=load(db);
    City city=catalog.cities().stream().filter(value->value.id()==cityId).findFirst().orElse(null);
    if(city==null)return null;
    String snapshot=db.meta().get("forever_diff_forever_snapshot_id");
    List<District> districts=db.read(c->{
      List<District> result=new ArrayList<>();
      try(var p=c.prepareStatement("""
          SELECT CAST(a.row_id AS INTEGER),json_extract(a.payload,'$.AreaName_lang'),
                 EXISTS(SELECT 1 FROM zone z WHERE CAST(z.id AS TEXT)=a.row_id)
          FROM client_table_row a WHERE a.snapshot_id=? AND a.table_name='AreaTable'
            AND CAST(json_extract(a.payload,'$.ParentAreaID') AS INTEGER)=?
            AND length(trim(json_extract(a.payload,'$.AreaName_lang')))>0
          ORDER BY json_extract(a.payload,'$.AreaName_lang') COLLATE NOCASE,a.row_id
          """)) {
        p.setString(1,snapshot);p.setLong(2,cityId);try(var r=p.executeQuery()){while(r.next())result.add(new District(r.getLong(1),r.getString(2),r.getBoolean(3)));}
      }
      return List.copyOf(result);
    });
    return new GuideSnapshot(city,catalog.build(),districts,new CityServicesRepository(db).load(cityId));
  }

  /** Called by the catalog worker. No Era snapshot, names, or inferred services are used. */
  public static Snapshot load(Database db) {
    String snapshot=db.meta().get("forever_diff_forever_snapshot_id");
    if(snapshot==null)return new Snapshot(List.of(),"unknown");
    return db.read(c->{
      String build="unknown";
      try(var p=c.prepareStatement("SELECT build FROM client_table_dataset WHERE snapshot_id=? AND product='wow_classic_beta'")) {
        p.setString(1,snapshot);try(var r=p.executeQuery()){if(!r.next())return new Snapshot(List.of(),build);build=r.getString(1);}
      }
      List<City> cities=new ArrayList<>();
      try(var p=c.prepareStatement("""
          SELECT z.id,json_extract(a.payload,'$.AreaName_lang') AS name,
                 json_extract(parent.payload,'$.AreaName_lang') AS parent_name
          FROM zone z JOIN client_table_row a ON a.snapshot_id=? AND a.table_name='AreaTable'
            AND a.row_id=CAST(z.id AS TEXT)
          LEFT JOIN client_table_row parent ON parent.snapshot_id=a.snapshot_id AND parent.table_name='AreaTable'
            AND parent.row_id=CAST(json_extract(a.payload,'$.ParentAreaID') AS TEXT)
          WHERE z.kind='city' AND length(trim(json_extract(a.payload,'$.AreaName_lang')))>0
          ORDER BY name COLLATE NOCASE,z.id
          """)) {
        p.setString(1,snapshot);try(var r=p.executeQuery()){while(r.next())cities.add(new City(r.getLong(1),r.getString(2),r.getString(3)));}
      }
      return new Snapshot(cities,build);
    });
  }

  public static JComponent render(Snapshot snapshot,Navigator nav) {
    JPanel page=new JPanel(new BorderLayout());JournalSurface.Body body=new JournalSurface.Body();
    page.add(JournalSurface.scroll(body));
    JournalSurface.add(body,JournalSurface.title("Cities",36));
    JournalSurface.add(body,JournalSurface.copy("Find your way around Azeroth’s capitals. Open a city guide to explore its map, districts, quests and inhabitants."));
    JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEADING,0,0));actions.setOpaque(false);
    JButton world=new JButton("Explore the world");world.addActionListener(e->nav.open(new ViewTarget.WorldMap(947)));actions.add(world);
    JournalSurface.add(body,actions);
    JPanel grid=JournalSurface.grid(2,760);
    for(City city:snapshot.cities()) {
      JPanel card=JournalSurface.card();JournalSurface.add(card,JournalSurface.title(city.name(),25));
      if(city.parent()!=null&&!city.parent().isBlank())JournalSurface.add(card,JournalSurface.copy(city.parent()+" › "+city.name()));
      JournalSurface.add(card,JournalSurface.copy("Map, local connections and source-backed discoveries."));
      JButton open=new JButton("Explore "+city.name());open.addActionListener(e->nav.open(new ViewTarget.Entity(new EntityRef(EntityKind.ZONE,city.id()))));
      JPanel row=new JPanel(new FlowLayout(FlowLayout.LEADING,0,0));row.setOpaque(false);row.add(open);JournalSurface.add(card,row);grid.add(card);
    }
    JournalSurface.add(body,grid);
    if(snapshot.cities().isEmpty())JournalSurface.add(body,JournalSurface.copy("No cities have been verified in this installed Forever client snapshot."));
    JournalSurface.add(body,JournalSurface.copy("City identities verified in Forever client build "+snapshot.build()+". Services and amenities are not inferred."));
    return page;
  }
}
