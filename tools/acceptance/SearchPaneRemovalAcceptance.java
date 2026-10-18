import dev.wowforever.app.ui.*;
import dev.wowforever.app.ui.journal.*;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.widgets.*;
import dev.wowforever.app.nav.*;
import dev.wowforever.app.model.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

/** Real Main search dropdown + full page results, never the retired side pane. */
public final class SearchPaneRemovalAcceptance {
 static MasterList legacy;
 static SearchDropdown dropdown()throws Exception{var f=SearchBar.class.getDeclaredField("dropdown");f.setAccessible(true);return (SearchDropdown)f.get(JournalSnapshots.frame.searchBar());}
 static void absent(String after)throws Exception{
  JournalSnapshots.check(JournalSnapshots.edt(()->legacy.getParent()==null&&!legacy.isVisible()&&!legacy.isShowing()),"Legacy model stays detached and hidden after "+after);
  JPanel content=JournalHeaderAcceptance.field("content",JPanel.class);Component detail=JournalHeaderAcceptance.field("detailSlot",JPanel.class);
  JournalSnapshots.check(JournalSnapshots.edt(()->{JPanel workspace=(JPanel)((BorderLayout)content.getLayout()).getLayoutComponent(BorderLayout.CENTER);return ((BorderLayout)workspace.getLayout()).getLayoutComponent(BorderLayout.CENTER)==detail;}),"Main page fills workspace directly without split pane or gutter");
  JournalSnapshots.check(!JournalSnapshots.frame.showsMasterList(),"MainView directs full searches to main result page");
 }
 static void query(String value)throws Exception{JournalHeaderAcceptance.chord(KeyEvent.VK_CONTROL,KeyEvent.VK_F);JournalSnapshots.edt(()->{JournalSnapshots.frame.searchBar().textField().setText(value);return null;});ResearchAcceptance.until(()->JournalSnapshots.edt(()->{try{return dropdown().rows().stream().anyMatch(row->row instanceof SearchDropdown.Row.Hit);}catch(Exception e){throw new IllegalStateException(e);}}),"actual dropdown hits for "+value);}
 static void loaded(EntityRef ref,String title)throws Exception{ResearchAcceptance.target(ref.kind(),ref.id());ResearchAcceptance.until(()->JournalSnapshots.edt(()->JournalSnapshots.all(JournalSnapshots.frame).stream().anyMatch(c->c instanceof HeaderPanel h&&h.isShowing()&&title.equals(h.title()))),"loaded actual selected record "+ref);}
 public static void main(String[] args)throws Exception{
  Path out=Path.of(args[1]);Files.createDirectories(out);JournalSnapshots.out=out;var report=JournalSnapshots.report;report.add("Exact source JAR SHA-256: "+JournalSnapshots.hash(Path.of(args[0])));report.add("Actual Main(no args), real compatible compact DB, isolated X11/preferences; dropdown and main results both native interactions.");int exit=0;
  try{
   dev.wowforever.app.Main.main(new String[0]);ResearchAcceptance.until(()->{MainFrame frame=JournalSnapshots.edt(()->Arrays.stream(Window.getWindows()).filter(w->w instanceof MainFrame&&w.isShowing()).map(w->(MainFrame)w).findFirst().orElse(null));if(frame==null)return false;JournalSnapshots.frame=frame;return JournalSnapshots.find(JournalHomePage.class)!=null;},"actual startup Home");JournalSnapshots.robot=new Robot();JournalSnapshots.robot.setAutoDelay(55);JournalSnapshots.controller=JournalHeaderAcceptance.field("controller",AppController.class);legacy=JournalSnapshots.frame.masterList();
   JournalSnapshots.check(JournalSnapshots.edt(()->JournalSnapshots.all(JournalSnapshots.frame.getJMenuBar()).stream().noneMatch(c->c instanceof JMenuItem item&&"Show list".equals(item.getText()))),"View menu no longer offers Show list");
   for(int width:new int[]{1280,420}){
    JournalSnapshots.edt(()->{JournalSnapshots.frame.setMinimumSize(new Dimension(1,1));JournalSnapshots.frame.setBounds(20,20,width,900);JournalSnapshots.controller.open(new ViewTarget.Home());return null;});JournalSnapshots.waitFor(JournalHomePage.class);absent("Home at "+width);
    query("Siamese");JournalSnapshots.snap("search-dropdown-"+width);absent("dropdown search");SearchDropdown.Row.Hit hit=JournalSnapshots.edt(()->{try{return dropdown().rows().stream().filter(row->row instanceof SearchDropdown.Row.Hit).map(row->(SearchDropdown.Row.Hit)row).findFirst().orElseThrow();}catch(Exception e){throw new IllegalStateException(e);}});ResearchAcceptance.key(KeyEvent.VK_ENTER);loaded(hit.entry().ref(),hit.entry().title());absent("native dropdown Enter");JournalSnapshots.check(true,"Dropdown Enter opens exact source-backed hit "+hit.entry().ref());
    query("Siamese");JournalHeaderAcceptance.chord(KeyEvent.VK_SHIFT,KeyEvent.VK_ENTER);ResearchAcceptance.until(()->JournalSnapshots.edt(()->JournalSnapshots.all(JournalSnapshots.frame).stream().anyMatch(c->c instanceof MasterList list&&list!=legacy&&list.isShowing()&&!list.entries().isEmpty())),"full search main result page");MasterList results=JournalSnapshots.find(MasterList.class);JournalSnapshots.check(results!=legacy&&JournalSnapshots.edt(()->results.getWidth())>=width-(width==1280?224:64)-40,"Full search fills available main page");absent("Shift+Enter full results");JournalSnapshots.snap("search-full-results-"+width);
    ListEntry entry=JournalSnapshots.edt(()->results.entries().getFirst());JournalSnapshots.edt(()->{results.jlist().setSelectedIndex(0);results.jlist().requestFocusInWindow();return null;});ResearchAcceptance.key(KeyEvent.VK_ENTER);loaded(entry.ref(),entry.title());absent("full results Enter");JournalSnapshots.back();ResearchAcceptance.until(()->JournalSnapshots.find(MasterList.class)!=null,"Back to full search page");JournalSnapshots.check(JournalSnapshots.controller.current() instanceof ViewTarget.Search,"Native Back restores actual full search target");
    for(int pass=0;pass<3;pass++)ResearchAcceptance.key(KeyEvent.VK_ESCAPE);absent("Escape");JournalHeaderAcceptance.chord(KeyEvent.VK_CONTROL,KeyEvent.VK_B);absent("retired Ctrl+B");ResearchAcceptance.key(KeyEvent.VK_F10);ResearchAcceptance.key(KeyEvent.VK_F10);ResearchAcceptance.until(()->JournalHeaderAcceptance.field("themeButton",JButton.class).isShowing(),"F10 full header return");absent("F10 roundtrip");
   }
  }catch(Throwable failure){report.add("FAIL: "+failure);failure.printStackTrace();exit=1;}finally{Files.write(out.resolve("report.txt"),report);System.out.println(String.join("\n",report));}System.exit(exit);
 }
}
