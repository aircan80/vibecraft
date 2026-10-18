package dev.wowforever.app.ui.journal;

import dev.wowforever.app.db.RecordedObjectSourcesRepository;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.Async;
import dev.wowforever.app.ui.pages.PageState;
import java.awt.BorderLayout;
import java.util.concurrent.Executor;
import javax.swing.*;

/** Explicitly lazy recorded sources. Selection inspects; Open is the only entity-navigation action. */
public final class JournalRecordedObjectSources extends JPanel implements PageState {
  private final RecordedObjectSourcesRepository repo;
  private final long itemId;
  private final Executor worker;
  private final JComboBox<RecordedObjectSourcesRepository.Zone> zones=new JComboBox<>();
  private final JList<RecordedObjectSourcesRepository.Source> sources=new JList<>();
  private final DefaultListModel<RecordedObjectSourcesRepository.Source> sourceModel=new DefaultListModel<>();
  private final JTextArea points=new JTextArea(9,28);
  private final JLabel status=new JLabel("Load recorded object sources to inspect archived locations.");
  private final JButton load=new JButton("Load recorded object sources"),previousZones=new JButton("Previous zones"),nextZones=new JButton("More zones"),previousSources=new JButton("Previous objects"),nextSources=new JButton("More objects");
  private final JButton openObject=new JButton("Open object"),openZone=new JButton("Open zone guide");
  private volatile long generation;
  private boolean adjusting,loaded;
  private int zoneOffset,sourceOffset;
  private long restoreZone=-1,restoreObject=-1;
  private record PointLoad(RecordedObjectSourcesRepository.Points points,String provenance){}
  private record ZoneLoad(RecordedObjectSourcesRepository.Page<RecordedObjectSourcesRepository.Zone> page,String provenance){}
  public JournalRecordedObjectSources(long itemId,RecordedObjectSourcesRepository repo,Executor worker,Navigator nav){
    super(new BorderLayout(0,8));this.itemId=itemId;this.repo=repo;this.worker=worker;
    JournalSurface.Body body=new JournalSurface.Body();add(JournalSurface.scroll(body));
    JournalSurface.add(body,JournalSurface.title("Recorded object sources",27));
    JournalSurface.add(body,JournalSurface.copy("Objects reported to contain this item, grouped by exact recorded zone. Object names do not establish Mining, Herbalism or other gathering mechanics. Missing placements are a coverage gap, not proof of absence."));
    JournalSurface.add(body,load);JournalSurface.add(body,status);
    zones.getAccessibleContext().setAccessibleName("Recorded source zone");zones.setEnabled(false);JournalSurface.add(body,zones);
    JPanel zoneActions=JournalSurface.grid(3,700);zoneActions.setOpaque(false);zoneActions.add(previousZones);zoneActions.add(nextZones);zoneActions.add(openZone);JournalSurface.add(body,zoneActions);
    sources.setModel(sourceModel);sources.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);sources.setVisibleRowCount(6);sources.getAccessibleContext().setAccessibleName("Recorded source objects");JournalSurface.add(body,new JScrollPane(sources));
    JPanel objectActions=JournalSurface.grid(3,700);objectActions.setOpaque(false);objectActions.add(previousSources);objectActions.add(nextSources);objectActions.add(openObject);JournalSurface.add(body,objectActions);
    points.setEditable(false);points.setLineWrap(true);points.setWrapStyleWord(true);points.getAccessibleContext().setAccessibleName("Selected object recorded coordinates");JournalSurface.add(body,new JScrollPane(points));
    for(JButton button:new JButton[]{previousZones,nextZones,previousSources,nextSources,openObject,openZone})button.setEnabled(false);
    load.addActionListener(e->{zoneOffset=0;sourceOffset=0;loadZones();});
    previousZones.addActionListener(e->{zoneOffset=Math.max(0,zoneOffset-RecordedObjectSourcesRepository.PAGE_SIZE);sourceOffset=0;loadZones();});nextZones.addActionListener(e->{zoneOffset+=RecordedObjectSourcesRepository.PAGE_SIZE;sourceOffset=0;loadZones();});
    previousSources.addActionListener(e->{sourceOffset=Math.max(0,sourceOffset-RecordedObjectSourcesRepository.PAGE_SIZE);loadSources();});nextSources.addActionListener(e->{sourceOffset+=RecordedObjectSourcesRepository.PAGE_SIZE;loadSources();});
    zones.addActionListener(e->{if(!adjusting){sourceOffset=0;loadSources();}});
    sources.addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&!adjusting)loadPoints();});
    openObject.addActionListener(e->{var selected=sources.getSelectedValue();if(selected!=null)nav.open(new ViewTarget.Entity(new EntityRef(EntityKind.OBJECT,selected.id())));});
    openZone.addActionListener(e->{var selected=(RecordedObjectSourcesRepository.Zone)zones.getSelectedItem();if(selected!=null)nav.open(new ViewTarget.Entity(new EntityRef(EntityKind.ZONE,selected.id())));});
  }
  private void loadZones(){long request=++generation;loaded=true;load.setEnabled(false);zones.setEnabled(false);previousZones.setEnabled(false);nextZones.setEnabled(false);status.setText("Loading recorded zones…");clearSources();
    Async.runIf(worker,()->generation==request,()->new ZoneLoad(repo.zones(itemId,zoneOffset),repo.provenance()),result->{
      adjusting=true;zones.removeAllItems();for(var zone:result.page().rows())zones.addItem(zone);
      for(int i=0;i<zones.getItemCount();i++)if(zones.getItemAt(i).id()==restoreZone)zones.setSelectedIndex(i);restoreZone=-1;adjusting=false;
      zones.setEnabled(zones.getItemCount()>0);previousZones.setEnabled(zoneOffset>0);nextZones.setEnabled(result.page().more());load.setEnabled(true);
      status.setText(result.page().rows().isEmpty()?"No recorded placements for this item in this zone page.":"Zones "+(zoneOffset+1)+"–"+(zoneOffset+result.page().rows().size())+(result.page().more()?" · more available":""));
      points.setText(result.provenance());if(zones.getItemCount()>0)loadSources();
    },this::failed);
  }
  private void clearSources(){adjusting=true;sourceModel.clear();adjusting=false;openObject.setEnabled(false);openZone.setEnabled(false);previousSources.setEnabled(false);nextSources.setEnabled(false);points.setText("");}
  private void loadSources(){var zone=(RecordedObjectSourcesRepository.Zone)zones.getSelectedItem();if(zone==null)return;long request=++generation;clearSources();status.setText("Loading recorded objects…");
    Async.runIf(worker,()->generation==request,()->repo.sources(itemId,zone.id(),sourceOffset),result->{adjusting=true;for(var source:result.rows())sourceModel.addElement(source);int selected=sourceModel.isEmpty()?-1:0;for(int i=0;i<sourceModel.size();i++)if(sourceModel.get(i).id()==restoreObject)selected=i;restoreObject=-1;sources.setSelectedIndex(selected);adjusting=false;
      previousSources.setEnabled(sourceOffset>0);nextSources.setEnabled(result.more());openZone.setEnabled(true);status.setText("Showing "+result.rows().size()+" objects from "+(sourceOffset+1)+(result.more()?" · more available":""));if(selected>=0)loadPoints();
    },this::failed);
  }
  private void loadPoints(){var source=sources.getSelectedValue();var zone=(RecordedObjectSourcesRepository.Zone)zones.getSelectedItem();if(source==null||zone==null)return;long request=++generation;openObject.setEnabled(true);points.setText("Loading selected object's recorded coordinates…");
    Async.runIf(worker,()->generation==request,()->new PointLoad(repo.points(itemId,zone.id(),source.id()),repo.provenance()),result->{var data=result.points();StringBuilder text=new StringBuilder(source.toString()).append("\n").append(data.rawRows()).append(" raw placement rows; ").append(data.validRows()).append(" valid-coordinate rows; ").append(data.rows().size()).append(" distinct points shown").append(data.more()?" (first 250; more recorded)":"").append(".\n");
      if(data.validRows()<data.rawRows())text.append("Missing/out-of-range coordinates are omitted.\n");if(data.rows().isEmpty())text.append("No valid recorded coordinates.\n");for(var point:data.rows())text.append(String.format(java.util.Locale.ROOT,"%.2f, %.2f%n",point.x(),point.y()));
      if(source.observed())text.append("Collector evidence exists for the item/object relationship; this does not verify each location.\n");text.append("\n").append(result.provenance());points.setText(text.toString());points.setCaretPosition(0);
    },this::failed);
  }
  private void failed(Exception failure){load.setEnabled(true);status.setText("Recorded source load failed; use Load to retry.");points.setText(failure.getMessage());}
  @Override public void removeNotify(){generation++;load.setEnabled(true);super.removeNotify();}
  public record State(boolean loaded,int zoneOffset,int sourceOffset,long zoneId,long objectId){}
  public Object saveState(){var zone=(RecordedObjectSourcesRepository.Zone)zones.getSelectedItem();var object=sources.getSelectedValue();return new State(loaded,zoneOffset,sourceOffset,zone==null?-1:zone.id(),object==null?-1:object.id());}
  public void restoreState(Object value){if(value instanceof State state&&state.loaded()){zoneOffset=Math.max(0,state.zoneOffset());sourceOffset=Math.max(0,state.sourceOffset());restoreZone=state.zoneId();restoreObject=state.objectId();loadZones();}}
}
