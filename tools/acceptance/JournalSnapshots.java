import dev.wowforever.app.db.*;
import dev.wowforever.app.nav.*;
import dev.wowforever.app.ui.*;
import dev.wowforever.app.ui.journal.*;
import dev.wowforever.app.ui.pages.PageState;
import dev.wowforever.app.ui.pages.InstancePage;
import dev.wowforever.app.ui.pages.ItemPage;
import dev.wowforever.app.ui.theme.Fonts;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Exact-artifact Journal acceptance. Native mode requires an explicitly selected isolated X11 display. */
public final class JournalSnapshots {
  static Path out; static final List<String> report=new ArrayList<>();static MainFrame frame;static AppController controller;static Robot robot;
  interface Read<T>{T get();}
  static <T>T edt(Read<T> f)throws Exception{AtomicReference<T> result=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();SwingUtilities.invokeAndWait(()->{try{result.set(f.get());}catch(Throwable t){error.set(t);}});if(error.get()!=null)throw new AssertionError(error.get());return result.get();}
  static List<Component> all(Container c){List<Component> a=new ArrayList<>();for(Component x:c.getComponents()){a.add(x);if(x instanceof Container child)a.addAll(all(child));}return a;}
  static void layout(Container c){c.doLayout();for(Component x:c.getComponents())if(x instanceof Container nested)layout(nested);}
  static void check(boolean b,String message){if(!b)throw new AssertionError(message);report.add("PASS: "+message);}
  static String hash(Path p)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(p)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))>=0)md.update(bytes,0,n);}return HexFormat.of().formatHex(md.digest());}
  static void paint(JComponent c,String name,int width,int height)throws Exception{edt(()->{c.setSize(width,height);for(int i=0;i<20;i++)layout(c);BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();c.printAll(g);g.dispose();try{ImageIO.write(image,"png",out.resolve(name+".png").toFile());}catch(Exception e){throw new RuntimeException(e);}return null;});}
  static <T extends Component>T find(Class<T> type)throws Exception{return edt(()->all(frame).stream().filter(type::isInstance).filter(Component::isShowing).map(type::cast).findFirst().orElse(null));}
  static void waitFor(Class<? extends Component> type)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(System.nanoTime()<end){if(find(type)!=null){robot.delay(350);return;}robot.delay(100);}throw new AssertionError("Timed out waiting for "+type.getSimpleName());}
  static JButton button(String text)throws Exception{return edt(()->all(frame).stream().filter(c->c instanceof JButton b&&b.isShowing()&&text.equals(b.getText())).map(c->(JButton)c).findFirst().orElseThrow(()->new AssertionError("No button "+text)));}
  static void click(Component c)throws Exception{edt(()->{if(c instanceof JComponent j)j.scrollRectToVisible(new Rectangle(0,0,c.getWidth(),c.getHeight()));return null;});robot.delay(150);Point p=edt(()->{Point q=c.getLocationOnScreen();q.translate(c.getWidth()/2,c.getHeight()/2);return q;});robot.mouseMove(p.x,p.y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();robot.delay(300);}
  static void snap(String name)throws Exception{robot.waitForIdle();robot.delay(300);ImageIO.write(robot.createScreenCapture(edt(()->frame.getBounds())),"png",out.resolve(name+".png").toFile());}
  static void back()throws Exception{robot.keyPress(KeyEvent.VK_ALT);robot.keyPress(KeyEvent.VK_LEFT);robot.keyRelease(KeyEvent.VK_LEFT);robot.keyRelease(KeyEvent.VK_ALT);robot.waitForIdle();robot.delay(400);}
  public static void main(String[] args)throws Exception{
    if(args.length!=4)throw new IllegalArgumentException("REVIEW_JAR DATABASE EMPTY_OUTPUT headless|native");out=Path.of(args[2]);Files.createDirectories(out);try(var s=Files.list(out)){if(s.findAny().isPresent())throw new IllegalArgumentException("Output must be empty");}
    report.add("Created: "+Instant.now());report.add("JAR SHA-256: "+hash(Path.of(args[0])));report.add("Scope: "+args[3]+"; native mode is isolated X11, not desktop Wayland acceptance");
    ExecutorService worker=Executors.newSingleThreadExecutor();
    try(var db=Database.open(Path.of(args[1]))){report.add("Database build: "+db.meta().get("build_id"));edt(()->{Fonts.install();ThemeManager.apply(ThemeManager.Mode.DARK);return null;});
      if(args[3].equals("headless")){coldCatalogs(Path.of(args[1]));headless(db);}else nativeRun(db,worker);
    }catch(Throwable t){report.add("FAIL: "+t);throw t;}finally{if(frame!=null)edt(()->{frame.dispose();return null;});worker.shutdownNow();Files.write(out.resolve("report.txt"),report);System.out.println(String.join("\n",report));}System.exit(0);
  }
  static dev.wowforever.app.catalog.CatalogSession catalogSession;
  static AppController wire(MainFrame window,Database db,java.util.concurrent.Executor worker){
    var registry=dev.wowforever.app.catalog.EditionRegistry.fromLegacyMetadata(db.meta());
    catalogSession=new dev.wowforever.app.catalog.CatalogSession(registry,java.util.prefs.Preferences.userNodeForPackage(dev.wowforever.app.Main.class).node("game-selection"));
    var controller=new AppController(Repositories.of(db,catalogSession.token().context()),window,worker,db.meta());controller.configureCatalogSession(catalogSession);window.configureCatalogSelection(registry,catalogSession);window.attach(controller);return controller;
  }
  static void coldCatalogs(Path database)throws Exception{
    for(ViewTarget target:List.of(new ViewTarget.ItemContent("set",2133),new ViewTarget.ItemContent("stats"),new ViewTarget.ItemContent("trinkets"),new ViewTarget.ItemContent("recipes"),new ViewTarget.ItemContent("xp-food"),new ViewTarget.ItemContent("comprehension"))){try(var fresh=Database.open(database)){long start=System.nanoTime();Object snapshot=CatalogPages.load(target,fresh);report.add("Fresh Database catalog cold load ms "+target+": "+(System.nanoTime()-start)/1e6+" ("+snapshot.getClass().getSimpleName()+")");}}
    report.add("Cold catalog timings use separate new Database instances per target; OS file cache may be warm; no baseline speedup inferred.");
  }
  static JButton inspectorButton(JournalMapInspector inspector,String accessible)throws Exception{return edt(()->all(inspector).stream().filter(c->c instanceof JButton b&&accessible.equals(b.getAccessibleContext().getAccessibleName())).map(c->(JButton)c).findFirst().orElseThrow());}
  static void headless(Database db)throws Exception{
    Map<ViewTarget,Object> snapshots=new LinkedHashMap<>();
    for(ViewTarget target:List.of(new ViewTarget.Home(),new ViewTarget.Forever("ladder"))){double[] times=new double[30];for(int i=-3;i<30;i++){long start=System.nanoTime();Object value=JournalCatalog.load(target,db);if(i>=0){times[i]=(System.nanoTime()-start)/1e6;snapshots.put(target,value);}}Arrays.sort(times);report.add(target+" warm worker load p95 ms: "+times[28]);check(times[28]<=300,"Warm load <=300ms: "+target);}
    db.close();report.add("Database closed before page construction/render");
    for(ThemeManager.Mode mode:ThemeManager.Mode.values()){edt(()->{ThemeManager.apply(mode);JournalAppearance.apply();return null;});for(int width:new int[]{1280,420})for(var entry:snapshots.entrySet()){
      long start=System.nanoTime();JComponent page=edt(()->JournalCatalog.render(entry.getValue(),ignored->{}));report.add(entry.getKey()+" EDT construction ms: "+(System.nanoTime()-start)/1e6);
      paint(page,(entry.getKey() instanceof ViewTarget.Home?"home":"dungeons")+"-"+mode.name().toLowerCase(Locale.ROOT)+"-"+width,width,900);
      if(page instanceof JournalDungeonPage d){var state=new JournalDungeonPage.State("Dalaran",1,0,0,0,0,null,0);edt(()->{d.restoreState(state);return null;});check(edt(d::saveState).equals(state),"Dungeon filter state round-trip "+mode+" "+width);paint(page,"dalaran-filter-"+mode.name().toLowerCase(Locale.ROOT)+"-"+width,width,900);}
    }}
    report.add("Headless captures are actual page components, not complete frame shells or native interaction evidence.");
  }
  static void nativeRun(Database db,ExecutorService worker)throws Exception{
    check(!GraphicsEnvironment.isHeadless(),"Native graphics available");robot=new Robot();robot.setAutoDelay(60);
    for(UiExperience experience:UiExperience.values()){
      AtomicReference<Optional<UiExperience>> selected=new AtomicReference<>();SwingUtilities.invokeLater(()->selected.set(ExperienceChooser.choose()));JButton choice=null;long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
      while(choice==null&&System.nanoTime()<end){choice=edt(()->Arrays.stream(Window.getWindows()).filter(Window::isShowing).flatMap(w->all(w).stream()).filter(c->c instanceof JButton b&&("choose-"+experience.name().toLowerCase(Locale.ROOT)).equals(b.getName())).map(c->(JButton)c).findFirst().orElse(null));robot.delay(100);}if(choice==null)throw new AssertionError("Chooser unavailable");click(choice);check(selected.get()!=null&&selected.get().orElseThrow()==experience,"Native chooser selected "+experience);
    }
    edt(()->{frame=new MainFrame(UiExperience.JOURNAL);controller=wire(frame,db,worker);frame.setBounds(20,20,1280,900);frame.setVisible(true);controller.open(new ViewTarget.Home());return null;});waitFor(JournalHomePage.class);
    for(ThemeManager.Mode mode:ThemeManager.Mode.values()){setNativeTheme(mode);for(int width:new int[]{1280,420}){edt(()->{frame.setMinimumSize(new Dimension(1,1));frame.setSize(width,900);return null;});snap("shell-home-"+mode.name().toLowerCase(Locale.ROOT)+"-"+width);if(width==420)check(edt(()->findUnchecked(JournalHomePage.class).getWidth())>=280,"Compact Journal leaves at least 280px for page content: "+mode);}}
    edt(()->{frame.setSize(1280,900);return null;});click(button("Find a dungeon"));waitFor(JournalDungeonPage.class);JournalDungeonPage d=find(JournalDungeonPage.class);var state=new JournalDungeonPage.State("Dalaran",1,0,0,0,0,null,0);edt(()->{d.restoreState(state);return null;});robot.delay(400);snap("journey-dalaran-filter");click(button("Open dungeon guide"));waitFor(JTabbedPane.class);click(button("Browse bosses & loot →"));JTabbedPane tabs=find(JTabbedPane.class);check(edt(()->tabs.getTitleAt(tabs.getSelectedIndex())).equals("Bosses & loot"),"Bosses & loot tab selected via native click");snap("journey-boss-loot");
    InstancePage instance=find(InstancePage.class);
    check(edt(()->findUnchecked(JournalDungeonPage.class).destinationState().query()).equals("Dalaran")&&edt(()->findUnchecked(JournalDungeonPage.class).destinationState().scope())==1,"Opening dungeon guide preserves visible destination search and source filter");
    Set<dev.wowforever.app.model.EntityRef> unfiltered=edt(()->lootRefs(instance));JComboBox<?> classFilter=combo("loot-class-filter"),slotFilter=combo("loot-slot-filter");
    selectChoice(classFilter,"Mage");selectChoice(slotFilter,"Hands");Set<dev.wowforever.app.model.EntityRef> filtered=edt(()->lootRefs(instance));
    check(unfiltered.containsAll(filtered),"Loot class/slot controls only narrow the recorded loot rows");check(filtered.contains(new dev.wowforever.app.model.EntityRef(dev.wowforever.app.model.EntityKind.ITEM,273034)),"Known Forever Hands item remains under Mage and Hands filters");snap("journey-loot-mage-hands-filter");int lootRow=-1;
    for(int boss=0;boss<edt(()->instance.encounters().getModel().getSize())&&lootRow<0;boss++){
      final int at=boss;edt(()->{instance.selectBoss(at);return null;});
      lootRow=edt(()->{var links=instance.lootTable();for(int row=0;row<links.table().getRowCount();row++)if(TableLinks.refAt(links.table(),links.model(),row).filter(ref->ref.kind()==dev.wowforever.app.model.EntityKind.ITEM).isPresent())return row;return -1;});
    }
    check(lootRow>=0,"A recorded loot item exists in a Dalaran encounter");
    final int chosen=lootRow;JTable loot=edt(()->instance.lootTable().table());edt(()->{loot.setRowSelectionInterval(chosen,chosen);loot.scrollRectToVisible(loot.getCellRect(chosen,0,true));loot.requestFocusInWindow();return null;});robot.delay(200);robot.keyPress(KeyEvent.VK_ENTER);robot.keyRelease(KeyEvent.VK_ENTER);waitFor(ItemPage.class);check(edt(controller::current) instanceof ViewTarget.Entity e&&e.ref().kind()==dev.wowforever.app.model.EntityKind.ITEM,"Native Enter activated the selected loot item");snap("journey-item");
    JToggleButton gameTooltip=edt(()->all(frame).stream().filter(c->c instanceof JToggleButton b&&"In-game tooltip".equals(b.getText())).map(c->(JToggleButton)c).findFirst().orElseThrow());
    check(!edt(gameTooltip::isSelected)&&!edt(()->findUnchecked(ItemPage.class).card().isShowing()),"Journal defaults to native item facts with in-game tooltip closed");
    click(gameTooltip);check(edt(()->findUnchecked(ItemPage.class).card().isShowing()),"Optional original in-game tooltip opens explicitly");snap("journey-item-optional-tooltip");click(gameTooltip);back();waitFor(InstancePage.class);JTabbedPane restoredTabs=find(JTabbedPane.class);check(edt(()->restoredTabs.getTitleAt(restoredTabs.getSelectedIndex())).equals("Bosses & loot"),"Native Back preserved encounter tab");check(edt(()->comboUnchecked("loot-class-filter").getSelectedItem().toString()).equals("Mage")&&edt(()->comboUnchecked("loot-slot-filter").getSelectedItem().toString()).equals("Hands"),"Native Back preserves loot class and equipment-slot filters");snap("journey-boss-tab-restored");back();waitFor(JournalDungeonPage.class);check(edt(()->findUnchecked(JournalDungeonPage.class).destinationState().query()).equals("Dalaran"),"Native Back preserved dungeon search");check(edt(()->findUnchecked(JournalDungeonPage.class).destinationState().scope())==1,"Native Back preserved source filter");snap("journey-filter-restored");
    edt(()->{controller.open(new ViewTarget.WorldMap(947));return null;});waitFor(JournalWorldMapPage.class);
    click(inspectorButton(find(JournalWorldMapPage.class).inspector(),"Select Eastern Kingdoms"));check(edt(()->controller.current() instanceof ViewTarget.WorldMap m&&m.uiMapId()==947),"World inspector destination selection does not navigate");click(button("Open destination →"));waitButton("Westfall");
    click(inspectorButton(find(JournalWorldMapPage.class).inspector(),"Select Westfall"));check(edt(()->controller.current() instanceof ViewTarget.WorldMap m&&m.uiMapId()==1415),"Continent inspector destination selection does not navigate");click(button("Open destination →"));waitFor(JournalZonePage.class);
    JournalZonePage zone=find(JournalZonePage.class);ViewTarget zoneTarget=edt(controller::current);
    check(edt(()->all(zone.inspector()).stream().filter(c->c instanceof JCheckBox box&&"Quest givers".equals(box.getText())).map(c->(JCheckBox)c).findFirst().orElseThrow().getModel()==zone.mapPanel().questsToggle().getModel()),"Inspector quest layer shares actual map toggle model");
    JButton location=edt(()->all(zone.inspector()).stream().filter(c->c instanceof JButton button&&button.getAccessibleContext().getAccessibleName()!=null&&button.getAccessibleContext().getAccessibleName().startsWith("Select ")).map(c->(JButton)c).findFirst().orElseThrow());click(location);check(edt(controller::current).equals(zoneTarget),"Zone recorded marker selection does not navigate before Open");check(edt(()->zone.inspector().selected()!=null),"Zone inspector shows selected recorded location");
    Point marker=edt(()->{var selected=zone.inspector().selected();var p=zone.mapPanel().mapView().projection().toComponent(selected.x(),selected.y());return new Point((int)Math.round(p.getX()),(int)Math.round(p.getY()));});
    edt(()->{zone.mapPanel().mapView().scrollRectToVisible(new Rectangle(marker.x-10,marker.y-10,20,20));return null;});robot.delay(300);Point markerScreen=edt(()->{Point p=zone.mapPanel().mapView().getLocationOnScreen();p.translate(marker.x,marker.y);return p;});robot.mouseMove(markerScreen.x,markerScreen.y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();robot.delay(400);check(edt(controller::current).equals(zoneTarget),"Actual native map marker click selects without navigating before Open");

    snap("journey-zone-inspector-wide");edt(()->{frame.setSize(420,900);return null;});snap("journey-zone-inspector-420");edt(()->{frame.setSize(1280,900);return null;});waitButton("Eastern Kingdoms");snap("journey-zone-breadcrumbs");click(button("Eastern Kingdoms"));long mapEnd=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<mapEnd&&!edt(()->controller.current() instanceof ViewTarget.WorldMap m&&m.uiMapId()==1415))robot.delay(100);check(edt(()->controller.current() instanceof ViewTarget.WorldMap m&&m.uiMapId()==1415),"Native zone breadcrumb returned to Eastern Kingdoms");snap("journey-continent-restored");
    edt(()->{controller.open(new ViewTarget.Entity(new dev.wowforever.app.model.EntityRef(dev.wowforever.app.model.EntityKind.ITEM,16806)));return null;});waitFor(ItemPage.class);
    ItemPage eraItem=find(ItemPage.class);String cardText=edt(()->eraItem.card()==null?"":texts(eraItem.card()));
    check(!cardText.contains("57 Armor")&&!cardText.contains("+15 Intellect")&&!cardText.contains("+18 Stamina"),"Default Forever item card excludes archived Era stats (16806)");
    JToggleButton compare=edt(()->all(frame).stream().filter(c->c instanceof JToggleButton b&&"Compare with Classic Era".equals(b.getText())).map(c->(JToggleButton)c).findFirst().orElseThrow());
    check(!edt(compare::isSelected),"Classic Era comparison starts collapsed");check(!edt(()->texts(eraItem)).contains("Classic Era · build 1.15.9.70003"),"Collapsed Classic comparison hides its field values");snap("era-item-default");
    edt(()->{compare.scrollRectToVisible(new Rectangle(0,0,compare.getWidth(),compare.getHeight()));return null;});robot.delay(300);click(compare);check(edt(compare::isSelected),"Native pointer opened explicit Classic comparison");check(edt(()->texts(eraItem)).contains("Classic Era · build 1.15.9.70003"),"Expanded comparison identifies archived Classic build");check(edt(()->texts(eraItem)).contains("These values do not establish Forever values"),"Classic comparison keeps Forever provenance distinct");snap("era-item-explicit-comparison");
    edt(()->{controller.open(new ViewTarget.Entity(new dev.wowforever.app.model.EntityRef(dev.wowforever.app.model.EntityKind.NPC,2671)));return null;});waitFor(JournalNpcPortrait.class);
    check(edt(()->texts(findUnchecked(JournalNpcPortrait.class))).contains("Forever 1.60.1.70450"),"Native NPC preview identifies actual Forever build");
    check(edt(()->texts(findUnchecked(JournalNpcPortrait.class))).contains("Native bind pose · not an in-game screenshot"),"Native NPC preview identifies generated bind pose honestly");
    JComponent portrait=edt(()->all(findUnchecked(JournalNpcPortrait.class)).stream().filter(c->c instanceof JComponent j&&("Mechanical Squirrel · generated native model preview".equals(j.getToolTipText())||"Generated native model preview".equals(j.getToolTipText()))).map(c->(JComponent)c).findFirst().orElseThrow());
    check(edt(()->{BufferedImage image=new BufferedImage(portrait.getWidth(),portrait.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();portrait.printAll(g);g.dispose();Set<Integer> colors=new HashSet<>();for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)colors.add(image.getRGB(x,y));return colors.size()>64;}),"Native NPC preview paints model image with >64 distinct colors");
    for(ThemeManager.Mode mode:ThemeManager.Mode.values()){setNativeTheme(mode);for(int width:new int[]{1280,420}){edt(()->{frame.setSize(width,900);return null;});snap("npc-2671-preview-"+mode.name().toLowerCase(Locale.ROOT)+"-"+width);}}
    edt(()->{frame.setSize(1280,900);return null;});JToggleButton rotating=toggle("Rotating preview");click(rotating);waitFor(JournalTurntable.class);JournalTurntable turntable=find(JournalTurntable.class);
    check(edt(turntable::frameIndex)==JournalTurntable.DEFAULT_FRAME,"Verified rotating preview starts at default rendered view");
    JComponent canvas=edt(()->all(turntable).stream().filter(c->c instanceof JComponent j&&"npc-turntable-canvas".equals(j.getName())).map(c->(JComponent)c).findFirst().orElseThrow());
    long initialImage=edt(()->paintHash(canvas));click(button("Rotate right"));check(edt(turntable::frameIndex)==(JournalTurntable.DEFAULT_FRAME+1)%12,"Native Rotate right changes rendered view");check(edt(()->paintHash(canvas))!=initialImage,"Rotating preview changes actual painted model image");click(button("Rotate left"));check(edt(turntable::frameIndex)==JournalTurntable.DEFAULT_FRAME,"Native Rotate left restores previous view");
    click(canvas);robot.keyPress(KeyEvent.VK_RIGHT);robot.keyRelease(KeyEvent.VK_RIGHT);robot.delay(200);check(edt(turntable::frameIndex)==(JournalTurntable.DEFAULT_FRAME+1)%12,"Native keyboard Right rotates focused model");robot.keyPress(KeyEvent.VK_HOME);robot.keyRelease(KeyEvent.VK_HOME);robot.delay(200);check(edt(turntable::frameIndex)==JournalTurntable.DEFAULT_FRAME,"Native keyboard Home resets model view");
    Point dragAt=edt(()->{Point p=canvas.getLocationOnScreen();p.translate(canvas.getWidth()/2,canvas.getHeight()/2);return p;});robot.mouseMove(dragAt.x,dragAt.y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);for(int dx=7;dx<=56;dx+=7)robot.mouseMove(dragAt.x+dx,dragAt.y);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.delay(200);check(edt(turntable::frameIndex)!=JournalTurntable.DEFAULT_FRAME,"Native mouse drag rotates rendered model");
    JSlider angle=edt(()->all(turntable).stream().filter(c->c instanceof JSlider).map(c->(JSlider)c).findFirst().orElseThrow());click(angle);edt(()->{angle.requestFocusInWindow();return null;});robot.keyPress(KeyEvent.VK_HOME);robot.keyRelease(KeyEvent.VK_HOME);robot.delay(200);check(edt(turntable::frameIndex)==0,"Native angle slider selects first rendered view");click(button("Reset view"));check(edt(turntable::frameIndex)==JournalTurntable.DEFAULT_FRAME,"Reset view restores verified default angle");
    JToggleButton play=toggle("Play rotation");click(play);robot.delay(250);check(edt(turntable::frameIndex)!=JournalTurntable.DEFAULT_FRAME,"Play rotation advances verified rendered frames");click(play);
    for(int width:new int[]{1280,420}){edt(()->{frame.setSize(width,900);return null;});snap("npc-2671-rotating-"+width);}
    click(play);check(edt(play::isSelected),"Rotation playback active before returning to portrait");click(rotating);check(find(JournalTurntable.class)==null&&!edt(play::isSelected),"Returning to portrait hides rotating preview and pauses playback");
    edt(()->{frame.setSize(1280,900);controller.open(new ViewTarget.Entity(new dev.wowforever.app.model.EntityRef(dev.wowforever.app.model.EntityKind.ZONE,1519)));return null;});waitFor(JournalCityPage.class);
    check(edt(()->texts(findUnchecked(JournalCityPage.class))).contains("Stormwind"),"Native city guide presents actual Stormwind identity");JTabbedPane cityTabs=find(JTabbedPane.class);
    for(int width:new int[]{1280,420}){edt(()->{frame.setSize(width,900);cityTabs.setSelectedIndex(0);return null;});snap("city-1519-overview-"+width);click(button("Explore map & records"));waitFor(JournalZonePage.class);check(edt(()->findUnchecked(JournalZonePage.class).mapPanel()!=null),"Stormwind city retains actual map and inspector at width "+width);snap("city-1519-map-records-"+width);}



  }
  static JComboBox<?> combo(String name)throws Exception{return edt(()->comboUnchecked(name));}
  static JComboBox<?> comboUnchecked(String name){return all(frame).stream().filter(c->c instanceof JComboBox<?> box&&name.equals(box.getName())&&box.isShowing()).map(c->(JComboBox<?>)c).findFirst().orElseThrow();}
  static void selectChoice(JComboBox<?> combo,String label)throws Exception{edt(()->{for(int i=0;i<combo.getItemCount();i++)if(label.equals(combo.getItemAt(i).toString())){combo.setSelectedIndex(i);return null;}throw new AssertionError("Missing filter choice "+label);});robot.delay(150);}
  static Set<dev.wowforever.app.model.EntityRef> lootRefs(InstancePage page){Set<dev.wowforever.app.model.EntityRef> result=new HashSet<>();var links=page.lootTable();for(int row=0;row<links.table().getRowCount();row++)TableLinks.refAt(links.table(),links.model(),row).ifPresent(result::add);return result;}
  static JToggleButton toggle(String label)throws Exception{return edt(()->all(frame).stream().filter(c->c instanceof JToggleButton b&&label.equals(b.getText())).map(c->(JToggleButton)c).findFirst().orElseThrow());}
  static long paintHash(JComponent c){BufferedImage image=new BufferedImage(c.getWidth(),c.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();c.printAll(g);g.dispose();long hash=1;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)hash=31*hash+image.getRGB(x,y);return hash;}
  static void setNativeTheme(ThemeManager.Mode mode)throws Exception{edt(()->{frame.setSize(1280,900);if(ThemeManager.current()!=mode)all(frame).stream().filter(c->c instanceof AbstractButton b&&"Slate".equals(b.getText())).map(c->(AbstractButton)c).findFirst().orElseThrow().doClick();return null;});if(edt(controller::current) instanceof ViewTarget.Home)waitFor(JournalHomePage.class);else waitFor(JournalNpcPortrait.class);check(edt(()->findUnchecked(JournalBreadcrumbs.class).getBackground().equals(dev.wowforever.app.ui.theme.Theme.current().bgBase())),"Native Journal breadcrumbs follow selected palette: "+mode);check(edt(()->findUnchecked(JTextField.class).getBackground().equals(dev.wowforever.app.ui.theme.Theme.current().bgBase())),"Native search field follows selected palette: "+mode);}
  static String texts(Container root){StringBuilder text=new StringBuilder();for(Component c:all(root))if(c.isShowing()){if(c instanceof JLabel l)text.append(l.getText()).append("\n");else if(c instanceof JTextArea a)text.append(a.getText()).append("\n");}return text.toString();}
  static void waitButton(String text)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(System.nanoTime()<end){try{button(text);robot.delay(500);return;}catch(AssertionError missing){robot.delay(100);}}throw new AssertionError("Timed out waiting for button "+text);}
  static <T extends Component>T findUnchecked(Class<T> t){return all(frame).stream().filter(t::isInstance).filter(Component::isShowing).map(t::cast).findFirst().orElseThrow();}
}
