import dev.wowforever.app.db.*;
import dev.wowforever.app.nav.*;
import dev.wowforever.app.model.*;
import dev.wowforever.app.ui.*;
import dev.wowforever.app.ui.journal.*;
import dev.wowforever.app.ui.pages.NpcPage;
import dev.wowforever.app.ui.theme.Fonts;
import java.awt.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;

/** Separate real-worker/cached-restart acceptance; it never supplies a fixture Renderer. */
public final class GeneralNpcPreviewAcceptance {
  static final List<String> report=JournalSnapshots.report;
  static void check(boolean condition,String text){JournalSnapshots.check(condition,text);}
  static JButton statusButton()throws Exception{return JournalSnapshots.edt(()->JournalSnapshots.all(JournalSnapshots.frame).stream().filter(c->c instanceof JButton b&&List.of("Generate preview","Preview cached","Retry preview").contains(b.getText())).map(c->(JButton)c).findFirst().orElse(null));}
  public static void main(String[] args)throws Exception{
    if(args.length!=6)throw new IllegalArgumentException("REVIEW_JAR DATABASE EMPTY_OUTPUT NPC_ID CACHE_MODE(generate|cached|unresolved|variants) CACHE_DIRECTORY");
    if(GraphicsEnvironment.isHeadless())throw new IllegalArgumentException("Explicit isolated native X11 display required");
    Path jar=Path.of(args[0]),out=Path.of(args[2]),cache=Path.of(args[5]);long npcId=Long.parseLong(args[3]);String mode=args[4];
    if(!Set.of("generate","cached","unresolved","variants").contains(mode))throw new IllegalArgumentException("Unknown cache mode");
    Files.createDirectories(out);try(var files=Files.list(out)){if(files.findAny().isPresent())throw new IllegalArgumentException("Output must be empty");}
    System.setProperty("wowforever.npcPreview.cache",cache.toAbsolutePath().toString());JournalSnapshots.out=out;
    report.add("Created: "+Instant.now());report.add("JAR SHA-256: "+JournalSnapshots.hash(jar));report.add("Scope: isolated X11 native UI; actual configured preview worker, no fixture Renderer");report.add("NPC: "+npcId+"; requested mode: "+mode+"; cache: "+cache.toAbsolutePath());
    ExecutorService worker=Executors.newSingleThreadExecutor();
    try(var db=Database.open(Path.of(args[1]))){
      report.add("Database build: "+db.meta().get("build_id"));JournalSnapshots.edt(()->{Fonts.install();ThemeManager.apply(ThemeManager.Mode.DARK);JournalSnapshots.frame=new MainFrame();JournalSnapshots.controller=JournalSnapshots.wire(JournalSnapshots.frame,db,worker);JournalSnapshots.frame.setBounds(20,20,1280,900);JournalSnapshots.frame.setVisible(true);JournalSnapshots.controller.open(new ViewTarget.Entity(new EntityRef(EntityKind.NPC,npcId)));return null;});
      JournalSnapshots.robot=new Robot();JournalSnapshots.robot.setAutoDelay(55);JournalSnapshots.waitFor(JournalNpcPreview.class);
      if(!mode.equals("variants"))JournalSnapshots.edt(()->{JournalSnapshots.all(JournalSnapshots.frame).stream().filter(c->c instanceof JComboBox<?> combo&&"Display variant".equals(combo.getAccessibleContext().getAccessibleName())).map(c->(JComboBox<?>)c).findFirst().ifPresent(combo->{if(combo.getItemCount()>1&&combo.getSelectedIndex()==0)combo.setSelectedIndex(1);});return null;});
      if(mode.equals("variants")){
        JComboBox<?> variants=JournalSnapshots.edt(()->JournalSnapshots.all(JournalSnapshots.frame).stream().filter(c->c instanceof JComboBox<?> combo&&"Display variant".equals(combo.getAccessibleContext().getAccessibleName())).map(c->(JComboBox<?>)c).findFirst().orElseThrow());
        check(JournalSnapshots.edt(variants::getItemCount)>=3&&JournalSnapshots.edt(variants::getSelectedIndex)==0,"Multiple native displays require an explicit variant choice");JButton action=statusButton();check(action!=null&&!JournalSnapshots.edt(action::isEnabled),"Generate stays disabled before variant selection");JournalSnapshots.snap("explicit-variant-prompt");JournalSnapshots.edt(()->{variants.setSelectedIndex(1);return null;});long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(System.nanoTime()<end&&!JournalSnapshots.edt(action::isEnabled))Thread.sleep(100);check(JournalSnapshots.edt(action::isEnabled),"Selecting a mapped native display enables explicit generation");JournalSnapshots.snap("explicit-variant-selected");
      }else if(mode.equals("unresolved")){
        Thread.sleep(500);JButton action=statusButton();check(action==null,"Unresolved appearance omits an unusable Generate control");JToggleButton disclosure=JournalSnapshots.edt(()->JournalSnapshots.all(JournalSnapshots.frame).stream().filter(c->c instanceof JToggleButton b&&"Model preview unavailable".equals(b.getAccessibleContext().getAccessibleName())).map(c->(JToggleButton)c).findFirst().orElseThrow());check(!JournalSnapshots.edt(disclosure::isSelected),"Unresolved appearance starts as compact closed disclosure");check(JournalSnapshots.edt(disclosure::getHeight)<=44,"Unresolved appearance disclosure remains compact");check(JournalSnapshots.find(JournalTurntable.class)==null,"Unresolved native appearance does not display a borrowed model");JournalSnapshots.edt(()->{JournalSnapshots.frame.setSize(420,900);return null;});JournalSnapshots.snap("unresolved-npc-"+npcId);JournalSnapshots.click(disclosure);check(JournalSnapshots.edt(disclosure::isSelected),"Native click opens unavailable appearance explanation");check(JournalSnapshots.edt(()->JournalSnapshots.all(JournalSnapshots.frame).stream().anyMatch(c->c.isShowing()&&"Appearance availability reason".equals(c.getAccessibleContext()==null?null:c.getAccessibleContext().getAccessibleName()))),"Unavailable preview retains source-qualified reason on demand");JournalSnapshots.snap("unresolved-npc-"+npcId+"-expanded");
      }else{
        JButton action=null;long readyEnd=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        while(System.nanoTime()<readyEnd){action=statusButton();if(action!=null&&(JournalSnapshots.edt(action::isEnabled)||JournalSnapshots.edt(action::getText).equals("Preview cached")))break;Thread.sleep(100);}
        check(action!=null,"Native preview action exists");final JButton previewAction=action;JButton completionAction=action;
        if(mode.equals("generate")){
          check(JournalSnapshots.edt(previewAction::isEnabled)&&JournalSnapshots.edt(previewAction::getText).equals("Generate preview"),"Fresh cache requires explicit Generate preview");JournalSnapshots.snap("before-generate");long start=System.nanoTime();JournalSnapshots.click(previewAction);
          check(JournalSnapshots.edt(()->JournalSnapshots.texts(JournalSnapshots.findUnchecked(JournalNpcPreview.class))).contains("queued or rendering locally"),"Native Generate preview exposes asynchronous render state");
          double[] latencies=new double[30];for(int i=0;i<latencies.length;i++){long ping=System.nanoTime();JournalSnapshots.edt(()->null);latencies[i]=(System.nanoTime()-ping)/1e6;Thread.sleep(20);}Arrays.sort(latencies);report.add("EDT dispatch p95 during actual generation ms: "+latencies[28]);check(latencies[28]<=300,"EDT remains responsive during actual renderer job");JournalSnapshots.snap("generation-in-progress");
          long navigationStart=System.nanoTime();JournalSnapshots.edt(()->{JournalSnapshots.controller.open(new ViewTarget.Home());return null;});JournalSnapshots.waitFor(JournalHomePage.class);double navigationMs=(System.nanoTime()-navigationStart)/1e6;report.add("Home navigation during actual render ms: "+navigationMs);check(navigationMs<2000,"Unrelated Home navigation remains responsive during actual rendering");JournalSnapshots.snap("home-during-render");JournalSnapshots.back();JournalSnapshots.waitFor(JournalNpcPreview.class);completionAction=statusButton();check(JournalSnapshots.edt(()->JournalSnapshots.texts(JournalSnapshots.findUnchecked(JournalNpcPreview.class))).contains("queued or rendering locally"),"Returning to NPC automatically rejoins existing asynchronous render job");

          long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(360);while(System.nanoTime()<end){String text=JournalSnapshots.edt(completionAction::getText);if(text.equals("Preview cached"))break;if(text.equals("Retry preview"))throw new AssertionError("Actual worker failed; inspect native evidence/cache renderer logs");Thread.sleep(250);}
          report.add("Actual native Generate-to-cached elapsed ms: "+(System.nanoTime()-start)/1e6);
        }
        check(JournalSnapshots.edt(completionAction::getText).equals("Preview cached")&&!JournalSnapshots.edt(completionAction::isEnabled),mode.equals("cached")?"Fresh JVM loaded persistent verified preview cache without Generate":"Actual worker output installed as verified cached preview");
        check(JournalSnapshots.edt(()->JournalSnapshots.texts(JournalSnapshots.findUnchecked(JournalNpcPreview.class))).contains("native bind pose, not an in-game screenshot"),"Generated preview describes its actual scope");
        for(int width:new int[]{1280,420}){JournalSnapshots.edt(()->{JournalSnapshots.frame.setSize(width,900);return null;});JournalSnapshots.snap(mode+"-npc-"+npcId+"-"+width);}
        JournalSnapshots.click(JournalSnapshots.toggle("Rotating preview"));JournalSnapshots.waitFor(JournalTurntable.class);JournalTurntable turntable=JournalSnapshots.find(JournalTurntable.class);int initial=JournalSnapshots.edt(turntable::frameIndex);JournalSnapshots.click(JournalSnapshots.button("Rotate right"));check(JournalSnapshots.edt(turntable::frameIndex)==(initial+1)%12,"Generic NPC rotates its own verified twelve-view preview");JournalSnapshots.snap(mode+"-rotating-"+npcId+"-420");
        try(var paths=Files.walk(cache)){report.add("Verified manifest files on disk: "+paths.filter(p->p.getFileName().toString().equals("manifest.json")).count());}
      }

    }catch(Throwable error){report.add("FAIL: "+error);throw error;}finally{if(JournalSnapshots.frame!=null)JournalSnapshots.edt(()->{JournalSnapshots.frame.dispose();return null;});worker.shutdownNow();dev.wowforever.app.preview.NpcPreviewService.shared().close();Files.write(out.resolve("report.txt"),report);System.out.println(String.join("\n",report));}System.exit(0);
  }
}
