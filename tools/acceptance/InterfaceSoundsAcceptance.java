import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.prefs.Preferences;
import javax.swing.*;
import dev.wowforever.app.db.*;
import dev.wowforever.app.ui.*;
import dev.wowforever.app.ui.theme.Fonts;

/** Actual settings interaction; audio disabled deliberately, no listening claim. */
public final class InterfaceSoundsAcceptance {
  static JDialog dialog()throws Exception{return JournalSnapshots.edt(()->Arrays.stream(Window.getWindows()).filter(w->w instanceof JDialog d&&d.isShowing()&&"Interface sounds".equals(d.getTitle())).map(w->(JDialog)w).findFirst().orElse(null));}
  static <T extends Component> T control(Class<T> type,String label)throws Exception{return JournalSnapshots.edt(()->JournalSnapshots.all(dialogUnchecked()).stream().filter(type::isInstance).map(type::cast).filter(c->c.isShowing()&&(c instanceof AbstractButton b?label.equals(b.getText()):label.equals(c.getAccessibleContext().getAccessibleName()))).findFirst().orElseThrow());}
  static JDialog dialogUnchecked(){return Arrays.stream(Window.getWindows()).filter(w->w instanceof JDialog d&&d.isShowing()&&"Interface sounds".equals(d.getTitle())).map(w->(JDialog)w).findFirst().orElseThrow();}
  static void open()throws Exception{
    JMenu settings=JournalSnapshots.edt(()->Arrays.stream(JournalSnapshots.frame.getJMenuBar().getSubElements()).filter(e->e instanceof JMenu menu&&"Settings".equals(menu.getText())).map(e->(JMenu)e).findFirst().orElseThrow());JournalSnapshots.click(settings);
    JMenuItem action=JournalSnapshots.edt(()->Arrays.stream(settings.getMenuComponents()).filter(c->c instanceof JMenuItem item&&"interface-sounds-menu".equals(item.getName())).map(c->(JMenuItem)c).findFirst().orElseThrow());JournalSnapshots.click(action);ResearchAcceptance.until(()->dialog()!=null,"Interface sounds settings modal");
  }
  static Map<String,String> values(Preferences prefs)throws Exception{Map<String,String> values=new TreeMap<>();for(String key:prefs.keys())values.put(key,prefs.get(key,null));return values;}
  public static void main(String[] args)throws Exception{
    Path out=Path.of(args[2]);Files.createDirectories(out);try(var files=Files.list(out)){if(files.findAny().isPresent())throw new IllegalArgumentException("Output must be empty");}JournalSnapshots.out=out;var report=JournalSnapshots.report;report.add("Created: "+Instant.now());report.add("JAR SHA-256: "+JournalSnapshots.hash(Path.of(args[0])));report.add("Scope: native Settings controls, isolated preferences; sound/music explicitly disabled; no acoustic playback assertion");ExecutorService worker=Executors.newSingleThreadExecutor();
    Preferences music=Preferences.userRoot().node("/dev/wowforever/app/music"),sound=Preferences.userRoot().node("/dev/wowforever/app/sound");Map<String,String> initialMusic=values(music);
    try(var db=Database.open(Path.of(args[1]))){db.meta();JournalSnapshots.edt(()->{Fonts.install();ThemeManager.apply(ThemeManager.Mode.DARK);JournalSnapshots.frame=new MainFrame();JournalSnapshots.controller=JournalSnapshots.wire(JournalSnapshots.frame,db,worker);JournalSnapshots.frame.setBounds(20,20,1280,900);JournalSnapshots.frame.setVisible(true);return null;});JournalSnapshots.robot=new Robot();JournalSnapshots.robot.setAutoDelay(55);
      for(int width:new int[]{1280,420}){
        JournalSnapshots.edt(()->{JournalSnapshots.frame.setSize(width,900);return null;});open();JCheckBox enabled=control(JCheckBox.class,"Interface sounds");JSlider volume=control(JSlider.class,"Interface sound volume");JButton preview=control(JButton.class,"Preview");report.add(JournalSnapshots.edt(()->"Dialog geometry at parent width "+width+": "+dialogUnchecked().getBounds()));if(width==420)JournalSnapshots.check(JournalSnapshots.edt(()->dialogUnchecked().getWidth())<=420,"Interface sounds dialog fits compact420 width");
        JournalSnapshots.check(JournalSnapshots.edt(()->enabled.isShowing()&&volume.isShowing()&&preview.isShowing()),"Interface sounds mute, volume and Preview visible at width "+width);
        for(Component c:List.of(enabled,volume,preview))JournalSnapshots.check(JournalSnapshots.edt(()->{Rectangle bounds=new Rectangle(c.getLocationOnScreen(),c.getSize());return dialogUnchecked().getBounds().contains(bounds);}),"Settings control remains within dialog bounds at width "+width);
        if(width==1280){if(JournalSnapshots.edt(enabled::isSelected))JournalSnapshots.click(enabled);ResearchAcceptance.until(()->sound.getBoolean("muted",false),"muted preference written off EDT");JournalSnapshots.edt(()->{volume.requestFocusInWindow();return null;});ResearchAcceptance.key(KeyEvent.VK_END);ResearchAcceptance.until(()->sound.getInt("volume",-1)==100,"volume preference persisted");JournalSnapshots.click(preview);JournalSnapshots.check(values(music).equals(initialMusic),"Interface mute/volume changes leave city music preferences independent");}
        else JournalSnapshots.check(!JournalSnapshots.edt(enabled::isSelected)&&JournalSnapshots.edt(volume::getValue)==100,"Reopened settings restore muted state and volume independently");
        JournalSnapshots.snap("settings-interface-sounds-"+width);JournalSnapshots.click(control(JButton.class,"OK"));ResearchAcceptance.until(()->dialog()==null,"Interface sounds modal closed");
      }
    }catch(Throwable failure){report.add("FAIL: "+failure);throw failure;}finally{if(JournalSnapshots.frame!=null)JournalSnapshots.edt(()->{JournalSnapshots.frame.dispose();return null;});worker.shutdownNow();Files.write(out.resolve("report.txt"),report);System.out.println(String.join("\n",report));}System.exit(0);
  }
}
