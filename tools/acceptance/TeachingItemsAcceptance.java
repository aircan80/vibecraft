import java.awt.*;
import java.nio.file.*;
import java.util.concurrent.*;
import dev.wowforever.app.db.*;
import dev.wowforever.app.model.*;
import dev.wowforever.app.ui.*;
import dev.wowforever.app.ui.journal.*;
import dev.wowforever.app.ui.theme.Fonts;
public final class TeachingItemsAcceptance {
 public static void main(String[] args)throws Exception{
  Path out=Path.of(args[2]);Files.createDirectories(out);JournalSnapshots.out=out;var report=JournalSnapshots.report;report.add("JAR SHA-256: "+JournalSnapshots.hash(Path.of(args[0])));report.add("Actual native source teaching records; music/sound disabled; isolated X11");ExecutorService worker=Executors.newSingleThreadExecutor();
  try(var db=Database.open(Path.of(args[1]))){db.meta();JournalSnapshots.edt(()->{Fonts.install();ThemeManager.apply(ThemeManager.Mode.DARK);JournalSnapshots.frame=new MainFrame();JournalSnapshots.controller=JournalSnapshots.wire(JournalSnapshots.frame,db,worker);JournalSnapshots.frame.setBounds(20,20,1280,900);JournalSnapshots.frame.setVisible(true);return null;});JournalSnapshots.robot=new Robot();JournalSnapshots.robot.setAutoDelay(55);ResearchAcceptance.open(EntityKind.ITEM,251525);ResearchAcceptance.tab("Crafting guide");JournalSnapshots.waitFor(RecipeFieldGuidePanel.class);ResearchAcceptance.teachingItems();
  }catch(Throwable failure){report.add("FAIL: "+failure);throw failure;}finally{if(JournalSnapshots.frame!=null)JournalSnapshots.edt(()->{JournalSnapshots.frame.dispose();return null;});worker.shutdownNow();Files.write(out.resolve("report.txt"),report);System.out.println(String.join("\n",report));}System.exit(0);
 }
}
