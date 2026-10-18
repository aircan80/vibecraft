import dev.wowforever.app.ui.*;
import dev.wowforever.app.ui.journal.*;
import dev.wowforever.app.ui.widgets.*;
import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

/** Actual Main and loaded database evidence; no fixture source assertions or geographic siblings. */
public final class SourceBreadcrumbAcceptance {
 static void check(boolean yes,String text){JournalSnapshots.check(yes,text);}
 static void key(int key){ResearchAcceptance.key(key);}
 static JDialog dialog()throws Exception{return JournalSnapshots.edt(()->Arrays.stream(Window.getWindows()).filter(w->w instanceof JDialog&&w.isShowing()&&"source-details-dialog".equals(w.getName())).map(JDialog.class::cast).findFirst().orElse(null));}
 static <T extends Component>T named(Container parent,String name,Class<T> type)throws Exception{return JournalSnapshots.edt(()->JournalSnapshots.all(parent).stream().filter(type::isInstance).map(type::cast).filter(c->name.equals(c.getName())).findFirst().orElseThrow());}
 static void focus(Component c)throws Exception{JournalSnapshots.edt(()->{if(c instanceof JComponent j)j.scrollRectToVisible(new Rectangle(0,0,c.getWidth(),c.getHeight()));c.requestFocusInWindow();return null;});ResearchAcceptance.until(()->JournalSnapshots.edt(c::isFocusOwner),"native focus "+c.getClass().getSimpleName());}
 static void captureSurface(Component surface,String name)throws Exception{JournalSnapshots.robot.delay(200);Rectangle bounds=JournalSnapshots.edt(()->new Rectangle(surface.getLocationOnScreen(),surface.getSize()));javax.imageio.ImageIO.write(JournalSnapshots.robot.createScreenCapture(bounds),"png",JournalSnapshots.out.resolve(name+".png").toFile());}
 static void sources(ThemeManager.Mode mode,int width)throws Exception{
  ResearchAcceptance.open(EntityKind.ITEM,287012);
  String sourceTab=JournalSnapshots.edt(()->JournalSnapshots.all(JournalSnapshots.frame).stream().filter(JTabbedPane.class::isInstance).map(JTabbedPane.class::cast).flatMap(tabs->java.util.stream.IntStream.range(0,tabs.getTabCount()).mapToObj(tabs::getTitleAt)).filter(title->title.startsWith("Source evidence")).findFirst().orElseThrow());
  ResearchAcceptance.tab(sourceTab);SourceInfoButton info=JournalSnapshots.find(SourceInfoButton.class);
  if(info==null)throw new AssertionError("No actual retained source evidence on selected Source evidence tab");
  SourceInfoButton button=info;focus(button);
  check(JournalSnapshots.edt(()->button.getWidth()>=28&&button.getHeight()>=28),"Source icon retains minimum28px hit target "+mode+" "+width);
  check(JournalSnapshots.edt(()->button.getToolTipText()!=null&&button.getAccessibleContext().getAccessibleDescription().contains("evidence")),"Source icon exposes brief summary to hover and accessibility");
  key(KeyEvent.VK_ENTER);ResearchAcceptance.until(()->dialog()!=null,"Enter opens actual source dialog");JDialog details=dialog();
  check(JournalSnapshots.edt(()->details.getModalityType()==Dialog.ModalityType.DOCUMENT_MODAL),"Source details persist in document-modal dialog");
  check(JournalSnapshots.edt(()->details.getWidth()<=width&&details.getHeight()<=900),"Source dialog fits actual owner size "+width);
  var payloads=JournalSnapshots.edt(()->JournalSnapshots.all(details).stream().filter(c->c instanceof JComponent j&&"Technical source evidence".equals(j.getAccessibleContext().getAccessibleName())).toList());
  check(!payloads.isEmpty()&&JournalSnapshots.edt(()->payloads.stream().noneMatch(Component::isVisible)),"Technical payloads begin collapsed");
  JButton technical=JournalSnapshots.edt(()->JournalSnapshots.all(details).stream().filter(c->c instanceof JButton b&&"Technical evidence".equals(b.getText())).map(JButton.class::cast).findFirst().orElseThrow());
  JournalSnapshots.snap("sources-"+mode.name().toLowerCase()+"-"+width);captureSurface(details,"source-dialog-full-"+mode.name().toLowerCase()+"-"+width);JournalSnapshots.click(technical);
  check(JournalSnapshots.edt(()->payloads.stream().anyMatch(Component::isVisible)),"Technical disclosure reveals already-loaded retained payload");JournalSnapshots.snap("sources-technical-"+mode.name().toLowerCase()+"-"+width);captureSurface(details,"source-dialog-technical-full-"+mode.name().toLowerCase()+"-"+width);
  key(KeyEvent.VK_ESCAPE);ResearchAcceptance.until(()->dialog()==null,"Escape closes source dialog");JournalSnapshots.robot.delay(500);boolean restored=JournalSnapshots.edt(button::isFocusOwner);JournalSnapshots.report.add("FOCUS after Escape: "+JournalSnapshots.edt(()->"owner="+KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()+"; active="+KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow()+"; iconShowing="+button.isShowing()));if(restored)check(true,"Escape closes persistent source details and restores icon focus");else{JournalSnapshots.report.add("FAIL (deferred): source icon focus not restored after Escape "+mode+" "+width);JournalSnapshots.edt(()->{JournalSnapshots.frame.toFront();JournalSnapshots.frame.requestFocus();return null;});focus(button);}
  key(KeyEvent.VK_SPACE);ResearchAcceptance.until(()->dialog()!=null,"Space opens source details");JournalSnapshots.click(named(dialog(),"source-details-close",JButton.class));ResearchAcceptance.until(()->dialog()==null,"Close button dismisses source details");check(true,"Native Space and mouse Close operate source disclosure");JournalSnapshots.click(button);ResearchAcceptance.until(()->dialog()!=null,"mouse click opens source icon details");key(KeyEvent.VK_ESCAPE);ResearchAcceptance.until(()->dialog()==null,"Escape closes mouse-opened source dialog");check(true,"Mouse click opens persistent source icon details");
 }
 static void breadcrumbs(ThemeManager.Mode mode,int width)throws Exception{
  ResearchAcceptance.open(EntityKind.ZONE,40);JournalSnapshots.waitFor(JournalZonePage.class);BreadcrumbTrail trail=JournalSnapshots.find(BreadcrumbTrail.class);
  JButton arrow=JournalSnapshots.edt(()->{var arrows=JournalSnapshots.all(trail).stream().filter(c->c instanceof JButton b&&b.getClientProperty("breadcrumbMenu") instanceof JPopupMenu).map(JButton.class::cast).toList();return arrows.get(arrows.size()-1);});
  JPopupMenu menu=JournalSnapshots.edt(()->(JPopupMenu)arrow.getClientProperty("breadcrumbMenu"));focus(arrow);key(KeyEvent.VK_DOWN);ResearchAcceptance.until(()->JournalSnapshots.edt(menu::isShowing),"Down opens actual geographic siblings");
  check(JournalSnapshots.edt(()->Arrays.stream(menu.getComponents()).filter(JRadioButtonMenuItem.class::isInstance).map(JRadioButtonMenuItem.class::cast).filter(AbstractButton::isSelected).count())==1,"Actual breadcrumb menu marks one current location");
  JournalSnapshots.snap("breadcrumbs-"+mode.name().toLowerCase()+"-"+width);captureSurface(menu,"breadcrumb-menu-full-"+mode.name().toLowerCase()+"-"+width);key(KeyEvent.VK_ESCAPE);ResearchAcceptance.until(()->!JournalSnapshots.edt(menu::isShowing)&&JournalSnapshots.edt(arrow::isFocusOwner),"Escape restores geographic arrow focus");check(true,"Breadcrumb Escape closes sibling menu and restores focus");
  key(KeyEvent.VK_DOWN);ResearchAcceptance.until(()->JournalSnapshots.edt(menu::isShowing),"reopen siblings for outside click");var outsideTarget=JournalSnapshots.edt(JournalSnapshots.controller::current);JTextField search=JournalSnapshots.frame.searchBar().textField();JournalSnapshots.click(search);ResearchAcceptance.until(()->!JournalSnapshots.edt(menu::isShowing)&&JournalSnapshots.edt(search::isFocusOwner),"outside click dismisses sibling menu and focuses clicked search field");check(JournalSnapshots.edt(JournalSnapshots.controller::current).equals(outsideTarget),"Outside click closes siblings without navigation and focuses clicked control");focus(arrow);

  var before=JournalSnapshots.edt(JournalSnapshots.controller::current);key(KeyEvent.VK_DOWN);ResearchAcceptance.until(()->JournalSnapshots.edt(menu::isShowing),"reopen siblings");String selected=null;
  for(int i=0;i<menu.getComponentCount()+1;i++){key(KeyEvent.VK_DOWN);selected=JournalSnapshots.edt(()->{var path=MenuSelectionManager.defaultManager().getSelectedPath();if(path.length>0&&path[path.length-1] instanceof JRadioButtonMenuItem item&&!item.isSelected()&&item.isEnabled())return item.getText();return null;});if(selected!=null)break;}
  if(selected==null)throw new AssertionError("No actual available geographic sibling");String expected=selected;key(KeyEvent.VK_ENTER);ResearchAcceptance.until(()->!JournalSnapshots.edt(JournalSnapshots.controller::current).equals(before),"native sibling navigation");JournalSnapshots.waitFor(JournalZonePage.class);
  ResearchAcceptance.until(()->JournalSnapshots.edt(()->JournalSnapshots.texts(JournalSnapshots.frame).contains(expected)),"exact selected sibling name "+expected);check(true,"Keyboard sibling selection opens actual loaded location: "+expected);
  JournalSnapshots.back();ResearchAcceptance.until(()->JournalSnapshots.edt(JournalSnapshots.controller::current).equals(before),"Back restores original geographic location");check(true,"Back restores original zone after sibling navigation");
 }
 public static void main(String[] args)throws Exception{
  JournalSnapshots.out=Path.of(args[1]);Files.createDirectories(JournalSnapshots.out);try(var entries=Files.list(JournalSnapshots.out)){if(entries.findAny().isPresent())throw new IllegalArgumentException("Output must be empty");}var report=JournalSnapshots.report;report.add("Exact JAR SHA256: "+JournalSnapshots.hash(Path.of(args[0])));report.add("Actual Main, real compact database, isolated X11/preferences with Openbox WM for native activation/focus; no fixture comparisons or siblings. Full-dialog/menu crops include surfaces extending beyond the owner; owner screenshots preserve actual1280/420 frame widths.");int exit=0;
  try{dev.wowforever.app.Main.main(new String[0]);ResearchAcceptance.until(()->JournalSnapshots.edt(()->{JournalSnapshots.frame=Arrays.stream(Window.getWindows()).filter(w->w instanceof MainFrame&&w.isShowing()).map(MainFrame.class::cast).findFirst().orElse(null);return JournalSnapshots.frame!=null&&JournalSnapshots.all(JournalSnapshots.frame).stream().anyMatch(JournalHomePage.class::isInstance);}),"actual Main Home");JournalSnapshots.robot=new Robot();JournalSnapshots.robot.setAutoDelay(55);var field=MainFrame.class.getDeclaredField("controller");field.setAccessible(true);JournalSnapshots.controller=(AppController)field.get(JournalSnapshots.frame);
   for(var mode:ThemeManager.Mode.values()){JournalHeaderAcceptance.selectTheme(mode);for(int width:new int[]{1280,420}){JournalSnapshots.edt(()->{JournalSnapshots.frame.setBounds(20,20,width,900);return null;});sources(mode,width);breadcrumbs(mode,width);}}if(report.stream().anyMatch(line->line.startsWith("FAIL (deferred)")))throw new AssertionError("Deferred native failures; see report");
  }catch(Throwable failure){report.add("FAIL: "+failure);failure.printStackTrace();exit=1;}finally{Files.write(JournalSnapshots.out.resolve("report.txt"),report);System.out.println(String.join("\n",report));}System.exit(exit);
 }
}
