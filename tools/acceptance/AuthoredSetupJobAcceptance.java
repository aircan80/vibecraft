import dev.wowforever.app.ui.*;
import dev.wowforever.app.ui.journal.JournalAppearance;
import dev.wowforever.app.ui.setup.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.prefs.Preferences;
import javax.swing.*;

/** AUTHORED SERVICE FIXTURES ONLY. No provider download, candidate database or durable registration exists. */
public final class AuthoredSetupJobAcceptance {
 static final java.util.List<String> report=new ArrayList<>();static Path out;static JFrame frame;static Service service;static AtomicInteger opens=new AtomicInteger();
 static void check(boolean yes,String label){if(!yes)throw new AssertionError(label);report.add("PASS: "+label);}
 static void await(ResearchAcceptance.Condition condition,String label)throws Exception{ResearchAcceptance.until(condition,label);}
 static AbstractButton control(String name)throws Exception{return FirstRunSetupAcceptance.named(name,AbstractButton.class);}
 static boolean exists(String name)throws Exception{return JournalSnapshots.edt(()->FirstRunSetupAcceptance.panel()!=null&&JournalSnapshots.all(FirstRunSetupAcceptance.panel()).stream().anyMatch(c->name.equals(c.getName())));}
 static void click(String name)throws Exception{FirstRunSetupAcceptance.click(control(name));}
 static String text()throws Exception{return FirstRunSetupAcceptance.text();}
 static void shot(String name)throws Exception{FirstRunSetupAcceptance.snap(name);}
 static final class Plan implements LibrarySetupService.PreparedPlan {}
 static final class Scenario {
  final CountDownLatch[] gates=java.util.stream.IntStream.range(0,6).mapToObj(i->new CountDownLatch(1)).toArray(CountDownLatch[]::new);
  final AtomicInteger phase=new AtomicInteger(-1);boolean lateRegistered,fail,continuous;int burst;
  void releaseAll(){for(var gate:gates)gate.countDown();}
 }
 static final class Service implements LibrarySetupService {
  final CountDownLatch reviewStarted=new CountDownLatch(1),reviewRelease=new CountDownLatch(1);final AtomicInteger reviews=new AtomicInteger();final AtomicReference<Control> reviewing=new AtomicReference<>();volatile Scenario scenario=new Scenario();volatile Mode observedMode;volatile LibrarySetupDraft builtDraft;volatile boolean holdFirstReview;
  static void background(){if(SwingUtilities.isEventDispatchThread())throw new AssertionError("Authored service called on EDT");}
  static void waitIgnoringInterrupt(CountDownLatch gate)throws Exception{while(gate.getCount()>0)try{gate.await(20,TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){}}
  public Capabilities capabilities(){background();return new Capabilities(Map.of("forever",Set.of("questie","native","wago")));}
  public Review review(LibrarySetupDraft draft,Control control)throws Exception{background();reviewing.set(control);if(reviews.incrementAndGet()==1&&holdFirstReview){reviewStarted.countDown();waitIgnoringInterrupt(reviewRelease);}return new Review(draft,"AUTHORED reviewed plan; no provider inspected",java.util.List.of(),new Plan());}
  public Result build(Review review,Mode mode,Control control)throws Exception{background();observedMode=mode;builtDraft=review.draft();Scenario selected=scenario;for(int update=0;update<selected.burst;update++)control.progress().accept(new Progress(Phase.DOWNLOAD,"AUTHORED progress flood",update,selected.burst,"events"));for(Phase phase:Phase.values()){control.boundary();selected.phase.set(phase.ordinal());control.progress().accept(new Progress(phase,"AUTHORED phase "+phase.name(),2,7,"fixture records"));if(selected.continuous&&phase==Phase.DOWNLOAD){long count=0;while(selected.gates[0].getCount()>0){control.progress().accept(new Progress(phase,"AUTHORED phase DOWNLOAD",count++,-1,"continuous events"));Thread.sleep(10);}}else waitIgnoringInterrupt(selected.gates[phase.ordinal()]);if(selected.fail&&phase==Phase.BUILD)throw new JobFailure("AUTHORED recoverable provider failure; retry source review");if(!(selected.lateRegistered&&phase==Phase.OPEN))control.boundary();}return new Result(out.resolve("authored-not-a-real-library.sqlite"),"AUTHORED candidate only; no database was created",selected.lateRegistered);}
 }
 static void show(boolean management,ThemeManager.Mode mode,int width,boolean holdReview)throws Exception{
  service=new Service();service.holdFirstReview=holdReview;opens.set(0);
  JournalSnapshots.edt(()->{ThemeManager.apply(mode);JournalAppearance.apply();var prefs=Preferences.userRoot().node("authored-setup/"+UUID.randomUUID());LibrarySetupPanel panel=management?LibrarySetupPanel.sourceManager(prefs,service):new LibrarySetupPanel(prefs,path->opens.incrementAndGet(),null,service);frame=new JFrame("AUTHORED service fixture — no real library job");frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);frame.addWindowListener(new WindowAdapter(){public void windowClosed(WindowEvent event){panel.close();}});frame.setContentPane(panel);frame.setBounds(20,20,width,900);frame.setVisible(true);return null;});FirstRunSetupAcceptance.wizard(4);
 }
 static void reviewed()throws Exception{click("setup-review-requirements");await(()->exists("setup-build-library")&&JournalSnapshots.edt(()->{try{return FirstRunSetupAcceptance.namedUnchecked("setup-build-library",AbstractButton.class).isEnabled();}catch(Exception e){throw new RuntimeException(e);}}),"authored requirements reviewed");}
 static void readyReview()throws Exception{click("setup-review-after-job");FirstRunSetupAcceptance.wizard(4);reviewed();}
 static void phase(int index)throws Exception{await(()->service.scenario.phase.get()==index&&text().contains("AUTHORED phase "+LibrarySetupService.Phase.values()[index].name()),"authored phase visible "+index);}
 static void stages(String prefix,boolean lateStop)throws Exception{
  click("setup-build-library");for(int phase=0;phase<6;phase++){phase(phase);String body=text();if(phase==0)for(var expected:LibrarySetupService.Phase.values())check(body.contains(service.observedMode==LibrarySetupService.Mode.MANAGEMENT&&expected==LibrarySetupService.Phase.OPEN?"Ready for next startup":expected.label()),"Six-stage fixture display retains "+expected.name());check(body.contains("2 / 7 fixture records in this step"),"Progress counts are explicitly phase-local");check(JournalSnapshots.edt(()->JournalSnapshots.all(FirstRunSetupAcceptance.panel()).stream().filter(JProgressBar.class::isInstance).map(JProgressBar.class::cast).allMatch(JProgressBar::isIndeterminate)),"No fabricated overall percentage");if(phase==0||phase==5)shot(prefix+"-phase-"+phase);if(lateStop&&phase==5){click("setup-stop-build");check(text().contains("Stopping after the current step"),"Late stop remains pending until authored registration result returns");}service.scenario.gates[phase].countDown();}
 }
 static void firstRun(ThemeManager.Mode mode,int width)throws Exception{
  show(false,mode,width,true);click("setup-review-requirements");await(()->service.reviewStarted.getCount()==0,"authored review pending");long max=0;for(int i=0;i<15;i++){long start=System.nanoTime();JournalSnapshots.edt(()->true);max=Math.max(max,System.nanoTime()-start);}check(max<TimeUnit.MILLISECONDS.toNanos(250),"Pending authored review stays off EDT; max probe "+max/1e6+"ms");
  FirstRunSetupAcceptance.tab(1);click("setup-source-questie");service.reviewRelease.countDown();FirstRunSetupAcceptance.wizard(4);await(()->!text().contains("Checking selected sources and requirements"),"obsolete review settles");check(!JournalSnapshots.edt(()->{try{return FirstRunSetupAcceptance.namedUnchecked("setup-build-library",AbstractButton.class).isEnabled();}catch(Exception failure){throw new RuntimeException(failure);}})&&!text().contains("AUTHORED reviewed plan"),"Changed source choices suppress obsolete reviewed result");
  reviewed();service.scenario.burst=100000;long floodStart=System.nanoTime();click("setup-build-library");phase(0);check(System.nanoTime()-floodStart<TimeUnit.SECONDS.toNanos(5),"100000 authored progress callbacks do not starve native controls/EDT");shot(mode+"-"+width+"-pending");click("setup-stop-build");check(text().contains("Stopping after the current step")&&!JournalSnapshots.edt(()->{try{return FirstRunSetupAcceptance.namedUnchecked("setup-stop-build",AbstractButton.class).isEnabled();}catch(Exception failure){throw new RuntimeException(failure);}}),"Stop requests delayed safe-boundary cancellation and disables repeat stop");Thread.sleep(200);check(!exists("setup-open-built-library")&&text().contains("Stopping after the current step"),"Held stage does not falsely report cancelled or READY");service.scenario.gates[0].countDown();await(()->text().contains("Stopped safely"),"authored stage boundary cancelled");check(opens.get()==0&&!exists("setup-open-built-library"),"Cancelled authored job never exposes Open or activates a library");
  readyReview();service.scenario=new Scenario();service.scenario.fail=true;click("setup-build-library");for(int i=0;i<3;i++){phase(i);service.scenario.gates[i].countDown();}await(()->text().contains("AUTHORED recoverable provider failure"),"authored recoverable failure");check(exists("setup-review-after-job")&&opens.get()==0,"Failed authored job keeps retry review and no activation");
  readyReview();service.scenario=new Scenario();stages(mode+"-"+width+"-first-run",false);await(()->exists("setup-open-built-library"),"authored first-run READY");check(opens.get()==0,"Authored READY does not automatically invoke Open");shot(mode+"-"+width+"-ready-first-run");click("setup-open-built-library");check(opens.get()==1,"Explicit Open delivers authored path to injected sink only; no database validation claim");JournalSnapshots.edt(()->{frame.dispose();return null;});
 }
 static void management(ThemeManager.Mode mode,int width)throws Exception{
  show(true,mode,width,false);reviewed();service.scenario.lateRegistered=true;stages(mode+"-"+width+"-management",true);await(()->text().contains("Your current session keeps its existing library"),"authored management READY");check(!exists("setup-open-built-library")&&!exists("setup-open-database")&&opens.get()==0,"Management authored READY has no Open/switch action and retains current-session boundary");check(text().contains("Ready for next startup"),"Confirmed authored registration wins late cancellation and remains READY");shot(mode+"-"+width+"-ready-management");JournalSnapshots.edt(()->{frame.dispose();return null;});
 }
 public static void main(String[] args)throws Exception{
  out=Path.of(args[1]);Files.createDirectories(out);try(var files=Files.list(out)){if(files.findAny().isPresent())throw new IllegalArgumentException("Output must be empty");}FirstRunSetupAcceptance.out=out;report.add("Dependency JAR SHA256: "+JournalSnapshots.hash(Path.of(args[0])));report.add(Boolean.getBoolean("acceptance.sourceOverride")?"DEV SOURCE-CLASS OVERRIDE: dry-run only, not exact-artifact acceptance":"Exact artifact UI classes; authored service fixture remains synthetic");report.add("AUTHORED SERVICE FIXTURES ONLY: no actual provider download, candidate DB, durable registration or production activation. Backend capability/review/build callbacks deliberately controlled; READY and path are synthetic.");int exit=0;
  try{FirstRunSetupAcceptance.robot=new Robot();FirstRunSetupAcceptance.robot.setAutoDelay(40);JournalSnapshots.robot=FirstRunSetupAcceptance.robot;for(var mode:ThemeManager.Mode.values())for(int width:new int[]{1280,420}){firstRun(mode,width);management(mode,width);}
   show(false,ThemeManager.Mode.DARK,420,true);click("setup-review-requirements");await(()->service.reviewStarted.getCount()==0,"pending authored review before close");Service closing=service;JournalSnapshots.edt(()->{frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING));return null;});await(()->closing.reviewing.get().cancellationRequested().getAsBoolean(),"close cancels authored review");closing.reviewRelease.countDown();Thread.sleep(250);check(JournalSnapshots.edt(FirstRunSetupAcceptance::panel)==null&&opens.get()==0,"Actual fixture window close suppresses late review UI and Open callback");
  }catch(Throwable failure){report.add("FAIL: "+failure);failure.printStackTrace();exit=1;}finally{if(service!=null){service.reviewRelease.countDown();service.scenario.releaseAll();}if(frame!=null)JournalSnapshots.edt(()->{frame.dispose();return null;});Files.write(out.resolve("report.txt"),report);System.out.println(String.join("\n",report));}System.exit(exit);
 }
}
