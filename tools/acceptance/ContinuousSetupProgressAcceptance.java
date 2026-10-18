import dev.wowforever.app.ui.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

/** AUTHORED100Hz progress only; native focus and button stability, no actual provider job or registration. */
public final class ContinuousSetupProgressAcceptance {
 static final java.util.List<String> report=new ArrayList<>();static Path out;static boolean failure;
 static void expect(boolean yes,String label){report.add((yes?"PASS: ":"FAIL: ")+label);if(!yes)failure=true;}
 static AbstractButton stop()throws Exception{return FirstRunSetupAcceptance.named("setup-stop-build",AbstractButton.class);}
 static boolean cancelling()throws Exception{return FirstRunSetupAcceptance.text().contains("Stopping after the current step");}
 static void directMouse(AbstractButton button,String scope)throws Exception{Point point=JournalSnapshots.edt(()->{Point p=button.getLocationOnScreen();p.translate(button.getWidth()/2,button.getHeight()/2);return p;});Robot robot=FirstRunSetupAcceptance.robot;robot.mouseMove(point.x,point.y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.delay(450);expect(JournalSnapshots.edt(()->button.isShowing()&&button.getModel().isArmed()&&button.getModel().isPressed()),"Continuous progress preserves active mouse press across multiple updates "+scope);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.delay(250);}
 public static void main(String[] args)throws Exception{
  out=Path.of(args[1]);Files.createDirectories(out);FirstRunSetupAcceptance.out=out;AuthoredSetupJobAcceptance.out=out;report.add("Exact JAR SHA256: "+JournalSnapshots.hash(Path.of(args[0])));report.add("AUTHORED SERVICE ONLY100Hz progress; no provider download, candidate DB, registration or activation.");int exit=0;
  try{FirstRunSetupAcceptance.robot=new Robot();FirstRunSetupAcceptance.robot.setAutoDelay(20);JournalSnapshots.robot=FirstRunSetupAcceptance.robot;
   for(var mode:ThemeManager.Mode.values())for(int width:new int[]{1280,420})for(boolean keyboard:new boolean[]{true,false}){
    AuthoredSetupJobAcceptance.show(false,mode,width,false);AuthoredSetupJobAcceptance.reviewed();AuthoredSetupJobAcceptance.service.scenario.continuous=true;AuthoredSetupJobAcceptance.click("setup-build-library");AuthoredSetupJobAcceptance.phase(0);AbstractButton original=stop();JournalSnapshots.edt(()->{original.scrollRectToVisible(new Rectangle(0,0,original.getWidth(),original.getHeight()));original.requestFocusInWindow();return null;});Thread.sleep(600);
    String scope=mode+" "+width+" "+(keyboard?"Space":"mouse");expect(stop()==original,"Continuous progress preserves Stop component identity "+scope);expect(JournalSnapshots.edt(original::isFocusOwner),"Continuous progress preserves Stop keyboard focus "+scope);
    if(keyboard)ResearchAcceptance.key(KeyEvent.VK_SPACE);else directMouse(stop(),scope);Thread.sleep(200);expect(cancelling(),"Native "+scope+" requests delayed stop during100Hz progress");
    FirstRunSetupAcceptance.snap("continuous-"+mode+"-"+width+"-"+(keyboard?"keyboard":"mouse"));AuthoredSetupJobAcceptance.service.scenario.releaseAll();Thread.sleep(300);JournalSnapshots.edt(()->{AuthoredSetupJobAcceptance.frame.dispose();return null;});
   }
  }catch(Throwable problem){report.add("FAIL: "+problem);problem.printStackTrace();failure=true;}finally{if(AuthoredSetupJobAcceptance.service!=null)AuthoredSetupJobAcceptance.service.scenario.releaseAll();if(AuthoredSetupJobAcceptance.frame!=null)JournalSnapshots.edt(()->{AuthoredSetupJobAcceptance.frame.dispose();return null;});Files.write(out.resolve("report.txt"),report);System.out.println(String.join("\n",report));}System.exit(failure?1:0);
 }
}
