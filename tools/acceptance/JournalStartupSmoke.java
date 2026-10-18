import dev.wowforever.app.ui.*;
import dev.wowforever.app.ui.journal.JournalHomePage;
import java.awt.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import javax.swing.*;

/** Runs the real Main startup in an isolated data/prefs directory, without an interface override. */
public final class JournalStartupSmoke {
  public static void main(String[] args)throws Exception{
    if(args.length!=2||GraphicsEnvironment.isHeadless())throw new IllegalArgumentException("REVIEW_JAR EMPTY_OUTPUT (isolated X11)");Path out=Path.of(args[1]);Files.createDirectories(out);try(var files=Files.list(out)){if(files.findAny().isPresent())throw new IllegalArgumentException("Output must be empty");}JournalSnapshots.out=out;
    var report=JournalSnapshots.report;report.add("Created: "+Instant.now());report.add("JAR SHA-256: "+JournalSnapshots.hash(Path.of(args[0])));report.add("Scope: actual Main.main with no args, isolated X11/data/prefs, validated existing database supplied by harness");
    try{
      dev.wowforever.app.Main.main(new String[0]);long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(60);MainFrame found=null;
      while(System.nanoTime()<end){
        if(!JournalSnapshots.edt(()->Arrays.stream(Window.getWindows()).noneMatch(w->w.isShowing()&&w instanceof JDialog dialog&&dialog.getTitle().toLowerCase(Locale.ROOT).contains("choose your experience"))))throw new AssertionError("An interface chooser appeared during startup");
        found=JournalSnapshots.edt(()->Arrays.stream(Window.getWindows()).filter(w->w instanceof MainFrame&&w.isShowing()).map(w->(MainFrame)w).findFirst().orElse(null));
        if(found!=null){JournalSnapshots.frame=found;if(JournalSnapshots.find(JournalHomePage.class)!=null)break;}Thread.sleep(100);
      }
      JournalSnapshots.check(true,"No interface chooser appeared during startup");JournalSnapshots.check(found!=null&&JournalSnapshots.find(JournalHomePage.class)!=null,"Real application startup reaches Journal Home without a chooser");JournalSnapshots.check(JournalSnapshots.edt(()->JournalSnapshots.frame.uiExperience()==UiExperience.JOURNAL),"Real Main defaults to Journal interface");JournalSnapshots.robot=new Robot();JournalSnapshots.edt(()->{JournalSnapshots.frame.setBounds(20,20,1280,900);return null;});JournalSnapshots.snap("default-main-journal-home");
    }catch(Throwable error){report.add("FAIL: "+error);throw error;}finally{if(JournalSnapshots.frame!=null)JournalSnapshots.edt(()->{JournalSnapshots.frame.dispose();return null;});Files.write(out.resolve("report.txt"),report);System.out.println(String.join("\n",report));}System.exit(0);
  }
}
