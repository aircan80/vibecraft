import dev.wowforever.app.db.*;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.*;
import dev.wowforever.app.ui.theme.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Real JFrame/Robot acceptance on an explicitly selected isolated X11 display. No Wayland claim. */
public final class NativeNavigation {
  static MainFrame frame; static AppController controller; static Robot robot;
  static final List<String> checks=new ArrayList<>();
  interface Read<T>{T get();}
  static <T>T edt(Read<T> f)throws Exception {var v=new AtomicReference<T>();SwingUtilities.invokeAndWait(()->v.set(f.get()));return v.get();}
  static List<Component> all(Container c){var out=new ArrayList<Component>();for(var x:c.getComponents()){out.add(x);if(x instanceof Container cc)out.addAll(all(cc));}return out;}
  static JButton button(String text)throws Exception{return edt(()->all(frame).stream().filter(c->c instanceof JButton b && b.isShowing() && b.getText()!=null && b.getText().equals(text)).map(c->(JButton)c).findFirst().orElseThrow(()->new AssertionError("Missing visible button: "+text)));}
  static void settle(){robot.waitForIdle();robot.delay(450);}
  static void key(int key){robot.keyPress(key);robot.keyRelease(key);settle();}
  static void alt(int key){System.out.println("Native Alt key "+key+" before="+controller.current());robot.keyPress(KeyEvent.VK_ALT);robot.keyPress(key);robot.keyRelease(key);robot.keyRelease(KeyEvent.VK_ALT);settle();System.out.println("after="+controller.current());}
  static void click(JButton b)throws Exception{Point p=edt(()->{Point q=b.getLocationOnScreen();q.translate(b.getWidth()/2,b.getHeight()/2);return q;});robot.mouseMove(p.x,p.y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);settle();}
  static void map(long id)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(System.nanoTime()<end){if(edt(()->controller.current() instanceof ViewTarget.WorldMap w && w.uiMapId()==id)){settle();return;}robot.delay(100);}throw new AssertionError("Expected map "+id+", got "+edt(controller::current));}
  static void snap(Path out,String name)throws Exception{Rectangle r=edt(()->frame.getBounds());ImageIO.write(robot.createScreenCapture(r),"png",out.resolve(name+".png").toFile());}
  public static void main(String[] args)throws Exception{
    if(args.length!=3 || GraphicsEnvironment.isHeadless())throw new IllegalArgumentException("DATABASE EMPTY_OUTPUT_DIRECTORY REVIEW_JAR (headful isolated X11 required)");
    Path out=Path.of(args[1]);Files.createDirectories(out);try(var s=Files.list(out)){if(s.findAny().isPresent())throw new IllegalArgumentException("Output must be empty");}
    var worker=Executors.newSingleThreadExecutor();
    try(var db=Database.open(Path.of(args[0]))){var repos=Repositories.of(db);var meta=db.meta();
      SwingUtilities.invokeAndWait(()->{WowForeverDarkLaf.setup();Theme.setCurrent(Tokens.DARK);Fonts.install();frame=new MainFrame();controller=new AppController(repos,frame,worker,meta);frame.attach(controller);frame.setBounds(20,20,1280,900);frame.setVisible(true);controller.open(new ViewTarget.WorldMap(947));});
      robot=new Robot();robot.setAutoDelay(70);map(947);snap(out,"01-azeroth");
      JButton ek=button("Eastern Kingdoms");SwingUtilities.invokeAndWait(()->ek.requestFocusInWindow());settle();
      if(!edt(ek::isFocusOwner))throw new AssertionError("Map button did not gain native focus");checks.add("Real window map button gained native focus");
      key(KeyEvent.VK_TAB);if(edt(ek::isFocusOwner))throw new AssertionError("Tab failed to move focus");checks.add("Native Tab moved focus");
      SwingUtilities.invokeAndWait(()->ek.requestFocusInWindow());settle();key(KeyEvent.VK_ENTER);map(1415);snap(out,"02-eastern-kingdoms");checks.add("Native Enter opened Eastern Kingdoms");
      JButton westfall=button("Westfall");Point center=edt(()->{Point p=westfall.getLocationOnScreen();p.translate(westfall.getWidth()/2,westfall.getHeight()/2);return p;});
      var entered=new AtomicReference<Point>();SwingUtilities.invokeAndWait(()->westfall.addMouseListener(new MouseAdapter(){public void mouseEntered(MouseEvent e){entered.set(e.getLocationOnScreen());}}));robot.mouseMove(0,0);robot.delay(100);robot.mouseMove(center.x,center.y);robot.delay(1200);
      if(entered.get()==null || !entered.get().equals(center))throw new AssertionError("Native pointer coordinates differ: intended "+center+", delivered "+entered.get());
      checks.add("Native pointer screen coordinates matched map button hover");snap(out,"03-westfall-hover");click(westfall);
      long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(System.nanoTime()<end && !(edt(controller::current) instanceof ViewTarget.Entity))robot.delay(100);
      if(!(edt(controller::current) instanceof ViewTarget.Entity))throw new AssertionError("Pointer click did not open zone");settle();snap(out,"04-westfall-zone");checks.add("Native pointer click opened Westfall zone");
      alt(KeyEvent.VK_LEFT);map(1415);checks.add("Native Alt+Left restored continent");alt(KeyEvent.VK_LEFT);map(947);checks.add("Native Alt+Left restored Azeroth");alt(KeyEvent.VK_RIGHT);map(1415);checks.add("Native Alt+Right restored continent");snap(out,"05-history-forward");
      String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(args[2]))));
      String report="Scope: isolated X11 JFrame/Robot acceptance; no Wayland desktop claim\nDisplay: "+System.getenv("DISPLAY")+"\nJAR SHA-256: "+sha+"\nDatabase build: "+meta.get("build_id")+"\nChecks:\n- "+String.join("\n- ",checks)+"\n";Files.writeString(out.resolve("report.txt"),report);System.out.print(report);
    }finally{SwingUtilities.invokeAndWait(()->{if(frame!=null)frame.dispose();});worker.shutdownNow();}System.exit(0);
  }
}
