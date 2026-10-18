import java.awt.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import dev.wowforever.app.db.*;
import dev.wowforever.app.model.*;
import dev.wowforever.app.ui.*;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.theme.Fonts;

/** Actual static PNG display only. Existing cache is evidence input, never a claim of UI generation. */
public final class StaticNpcPortraitAcceptance {
 static JComponent picture(){return JournalSnapshots.all(JournalSnapshots.frame).stream().filter(c->c instanceof JComponent&&c.isShowing()&&"npc-portrait-image".equals(c.getName())).map(c->(JComponent)c).findFirst().orElse(null);}
 static AbstractButton missing(){return JournalSnapshots.all(JournalSnapshots.frame).stream().filter(c->c instanceof AbstractButton b&&b.isShowing()&&"Portrait unavailable".equals(b.getAccessibleContext().getAccessibleName())).map(c->(AbstractButton)c).findFirst().orElse(null);}
 static void noGenerationControls()throws Exception{
  JournalSnapshots.check(JournalSnapshots.edt(()->JournalSnapshots.all(JournalSnapshots.frame).stream().noneMatch(c->c.getClass().getSimpleName().contains("Turntable")||c instanceof AbstractButton b&&b.getText()!=null&&List.of("generate preview","retry preview","rotating preview","rotate left","rotate right","reset view","play rotation").contains(b.getText().toLowerCase(Locale.ROOT)))),"Static-only NPC page has no Generate, rotation or turntable controls");
  JournalSnapshots.check(ProcessHandle.current().descendants().findAny().isEmpty(),"No renderer or external helper process active during static portrait checks");
 }
 static Map<String,String> fingerprint(Path root)throws Exception{Map<String,String> values=new TreeMap<>();try(var files=Files.walk(root)){for(Path path:files.filter(Files::isRegularFile).toList())values.put(root.relativize(path).toString(),JournalSnapshots.hash(path));}return values;}
 public static void main(String[] args)throws Exception{
  if(args.length!=3)throw new IllegalArgumentException("REVIEW_JAR DATABASE EMPTY_OUTPUT");Path out=Path.of(args[2]);Files.createDirectories(out);try(var files=Files.list(out)){if(files.findAny().isPresent())throw new IllegalArgumentException("Output must be empty");}JournalSnapshots.out=out;var report=JournalSnapshots.report;report.add("Created: "+Instant.now());report.add("JAR SHA-256: "+JournalSnapshots.hash(Path.of(args[0])));report.add("Scope: actual native static PNG UI; supplied read-only pre-existing cache, no generation claim");report.add("Existing cache input: "+System.getProperty("wowforever.npcPreview.cache","default"));ExecutorService worker=Executors.newSingleThreadExecutor();Path cache=Path.of(System.getProperty("wowforever.npcPreview.cache"));Map<String,String> before=fingerprint(cache);report.add("Read-only existing cache files: "+before.size());
  try(var db=Database.open(Path.of(args[1]))){db.meta();JournalSnapshots.edt(()->{Fonts.install();ThemeManager.apply(ThemeManager.Mode.DARK);JournalSnapshots.frame=new MainFrame();JournalSnapshots.controller=JournalSnapshots.wire(JournalSnapshots.frame,db,worker);JournalSnapshots.frame.setBounds(20,20,1280,900);JournalSnapshots.frame.setVisible(true);return null;});JournalSnapshots.robot=new Robot();JournalSnapshots.robot.setAutoDelay(55);
   for(long npc:new long[]{2671,7380,246}){
    ResearchAcceptance.open(EntityKind.NPC,npc);String expected=npc==2671?"Mechanical Squirrel":npc==7380?"Siamese":"\"Auntie\" Bernice Stonefield";
    ResearchAcceptance.until(()->JournalSnapshots.edt(()->JournalSnapshots.all(JournalSnapshots.frame).stream().anyMatch(c->c instanceof HeaderPanel h&&h.isShowing()&&expected.equals(h.title()))),"exact NPC header "+npc);
    if(npc==246){ResearchAcceptance.until(()->JournalSnapshots.edt(StaticNpcPortraitAcceptance::missing)!=null,"honest portrait unavailable disclosure");JournalSnapshots.check(JournalSnapshots.edt(StaticNpcPortraitAcceptance::picture)==null,"Missing NPC246 has no fabricated picture");JournalSnapshots.click(JournalSnapshots.edt(StaticNpcPortraitAcceptance::missing));JournalSnapshots.check(ResearchAcceptance.text().contains("No verified portrait is available for this game version.")&&ResearchAcceptance.text().contains("wow_classic_beta")&&ResearchAcceptance.text().contains("1.60.1.70450"),"Missing portrait explanation remains visible on explicit request");}
    else{ResearchAcceptance.until(()->JournalSnapshots.edt(StaticNpcPortraitAcceptance::picture)!=null,"verified static PNG for NPC"+npc);JournalSnapshots.check(JournalSnapshots.edt(()->"NPC portrait".equals(picture().getToolTipText())),"Static portrait component identifies ordinary NPC portrait");JournalSnapshots.check(ResearchAcceptance.text().contains("Blizzard Entertainment")&&ResearchAcceptance.text().contains(npc==2671?"bundled":"local cache"),"Static portrait attributes artwork and actual bundled/cache input");}
    noGenerationControls();for(int width:new int[]{1280,420}){JournalSnapshots.edt(()->{JournalSnapshots.frame.setSize(width,900);return null;});JournalSnapshots.robot.delay(250);if(npc!=246)JournalSnapshots.check(JournalSnapshots.edt(()->picture().getWidth()>0&&picture().getHeight()>0&&picture().getVisibleRect().height>0),"Static portrait remains visible at width "+width);JournalSnapshots.snap("npc-"+npc+"-static-"+width);}JournalSnapshots.edt(()->{JournalSnapshots.frame.setSize(1280,900);return null;});
   }
   JournalSnapshots.check(fingerprint(cache).equals(before),"Static browsing leaves pre-existing verified cache bytes unchanged");
  }catch(Throwable failure){report.add("FAIL: "+failure);throw failure;}finally{if(JournalSnapshots.frame!=null)JournalSnapshots.edt(()->{JournalSnapshots.frame.dispose();return null;});worker.shutdownNow();Files.write(out.resolve("report.txt"),report);System.out.println(String.join("\n",report));}System.exit(0);
 }
}
