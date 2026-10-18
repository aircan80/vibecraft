import dev.wowforever.app.db.*;
import dev.wowforever.app.model.*;
import dev.wowforever.app.ui.*;
import dev.wowforever.app.ui.detail.*;
import dev.wowforever.app.ui.journal.*;
import dev.wowforever.app.ui.pages.quest.ChainPanel;
import dev.wowforever.app.ui.widgets.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

/** Actual Main and database pages; ordinary verified portrait assets; external provider links are never clicked. */
public final class SourceSurfacesAcceptance {
 static String mapSource;static java.util.List<String> questUrls;
 static void check(boolean value,String label){JournalSnapshots.check(value,label);}
 static SourceInfoButton button(String name)throws Exception{return JournalSnapshots.edt(()->JournalSnapshots.all(JournalSnapshots.frame).stream().filter(c->c instanceof SourceInfoButton&&c.isShowing()&&name.equals(c.getName())).map(SourceInfoButton.class::cast).findFirst().orElse(null));}
 static String visible(Container parent)throws Exception{return JournalSnapshots.edt(()->JournalSnapshots.texts(parent));}
 static void disclosure(String name,String label,ThemeManager.Mode mode,int width,boolean technical)throws Exception{
  ResearchAcceptance.until(()->button(name)!=null,"actual source control "+name);SourceInfoButton button=button(name);SourceBreadcrumbAcceptance.focus(button);ResearchAcceptance.key(KeyEvent.VK_ENTER);ResearchAcceptance.until(()->SourceBreadcrumbAcceptance.dialog()!=null,"Enter opens "+label);JDialog dialog=SourceBreadcrumbAcceptance.dialog();
  check(JournalSnapshots.edt(()->dialog.getModalityType()==Dialog.ModalityType.DOCUMENT_MODAL&&dialog.getWidth()<=width),label+" persistent dialog fits owner width "+mode+" "+width);
  String body=visible(dialog);check(body.contains("Not recorded"),label+" does not invent missing metadata");
  if(name.equals("map-travel-source-info"))check(body.contains(mapSource)&&body.contains("does not establish complete Forever"),"Map details retain exact loaded mixed-source label and coverage qualification");
  if(name.equals("npc-portrait-source-info"))check(body.contains("Blizzard Entertainment")&&body.contains("1.60.1.70450")&&body.contains("portrait image only"),"Portrait details preserve actual artwork credit/build and image-only scope");
  if(name.equals("quest-relationship-source-info")){
   check(body.contains("not establish every quest field")&&body.contains("not in-game verified"),"Quest relationship scope and authored qualification remain explicit");
   check(JournalSnapshots.edt(()->JournalSnapshots.all(dialog).stream().anyMatch(c->c instanceof JButton link&&link.isEnabled()&&"Open original source ↗".equals(link.getText()))),"Actual quest provider link remains enabled without opening an external browser");
  }
  SourceBreadcrumbAcceptance.captureSurface(dialog,label+"-"+mode.name().toLowerCase()+"-"+width);
  if(technical){JButton toggle=JournalSnapshots.edt(()->JournalSnapshots.all(dialog).stream().filter(c->c instanceof JButton b&&"Technical evidence".equals(b.getText())).map(JButton.class::cast).findFirst().orElseThrow());check(JournalSnapshots.edt(()->JournalSnapshots.all(dialog).stream().filter(c->c instanceof javax.swing.text.JTextComponent&&c.getParent()==toggle.getParent()).anyMatch(c->!c.isVisible())),label+" technical payload starts collapsed");JournalSnapshots.click(toggle);check(JournalSnapshots.edt(()->toggle.getText().equals("Hide technical evidence")),label+" technical disclosure expands through native mouse input");if(name.equals("quest-relationship-source-info"))check(questUrls.stream().anyMatch(url->{try{return visible(dialog).contains(url);}catch(Exception failure){throw new RuntimeException(failure);}}),"Quest technical evidence retains an exact database provider URL; no external action invoked");SourceBreadcrumbAcceptance.captureSurface(dialog,label+"-technical-"+mode.name().toLowerCase()+"-"+width);}
  ResearchAcceptance.key(KeyEvent.VK_ESCAPE);ResearchAcceptance.until(()->SourceBreadcrumbAcceptance.dialog()==null&&JournalSnapshots.edt(button::isFocusOwner),"Escape restores "+label+" source trigger");check(true,label+" Escape returns focus to the source icon");ResearchAcceptance.key(KeyEvent.VK_SPACE);ResearchAcceptance.until(()->SourceBreadcrumbAcceptance.dialog()!=null,"Space reopens "+label);JournalSnapshots.click(SourceBreadcrumbAcceptance.named(SourceBreadcrumbAcceptance.dialog(),"source-details-close",JButton.class));ResearchAcceptance.until(()->SourceBreadcrumbAcceptance.dialog()==null,"Close dismisses "+label);check(true,label+" Space and mouse Close operate persistent details");
 }
 static void pages(ThemeManager.Mode mode,int width)throws Exception{
  ResearchAcceptance.open(EntityKind.ZONE,40);JournalSnapshots.waitFor(JournalZonePage.class);check(ResearchAcceptance.text().contains(mapSource),"Map retains raw source label visibly outside dialog");disclosure("map-travel-source-info","map-source",mode,width,false);
  ResearchAcceptance.open(EntityKind.NPC,2671);ResearchAcceptance.until(()->button("npc-portrait-source-info")!=null,"actual verified portrait source icon for NPC2671");check(ResearchAcceptance.text().contains("Blizzard Entertainment"),"Actual available portrait retains visible artwork credit");disclosure("npc-portrait-source-info","portrait-source",mode,width,true);
  ResearchAcceptance.open(EntityKind.QUEST,13);JournalSnapshots.waitFor(ChainPanel.class);ChainPanel chain=JournalSnapshots.find(ChainPanel.class);if(JournalSnapshots.edt(chain::isCollapsed))JournalSnapshots.click(chain.heading());check(ResearchAcceptance.text().contains("not in-game verified"),"Quest authored relationship warning remains visible before source dialog");disclosure("quest-relationship-source-info","quest-source",mode,width,true);
 }
 public static void main(String[] args)throws Exception{
  JournalSnapshots.out=Path.of(args[1]);Files.createDirectories(JournalSnapshots.out);try(var entries=Files.list(JournalSnapshots.out)){if(entries.findAny().isPresent())throw new IllegalArgumentException("Output must be empty");}var report=JournalSnapshots.report;report.add("Exact JAR SHA256: "+JournalSnapshots.hash(Path.of(args[0])));report.add("Actual Main, real compactDB zone40/quest13/NPC2671, existing verified reviewed portrait pack. No synthetic portrait, renderer or external browser action. Isolated X11 with WM; Dark/Light1280/420.");int exit=0;
  try{
   try(var db=Database.open(Path.of(System.getProperty("wowforever.database")))){var repos=Repositories.of(db);mapSource=((DetailPanels.LoadedZone)DetailPanels.load(new EntityRef(EntityKind.ZONE,40),repos).orElseThrow()).travel().sourceLabel();questUrls=repos.quests().chain(13).evidence().stream().map(QuestRelationEvidence::sourceUrl).filter(url->url!=null&&!url.isBlank()).toList();check(!mapSource.isBlank()&&!questUrls.isEmpty(),"Expected source facts loaded off EDT from actual database");}
   dev.wowforever.app.Main.main(new String[0]);ResearchAcceptance.until(()->JournalSnapshots.edt(()->{JournalSnapshots.frame=Arrays.stream(Window.getWindows()).filter(w->w instanceof MainFrame&&w.isShowing()).map(MainFrame.class::cast).findFirst().orElse(null);return JournalSnapshots.frame!=null&&JournalSnapshots.all(JournalSnapshots.frame).stream().anyMatch(JournalHomePage.class::isInstance);}),"actual Main Home");JournalSnapshots.robot=new Robot();JournalSnapshots.robot.setAutoDelay(55);var field=MainFrame.class.getDeclaredField("controller");field.setAccessible(true);JournalSnapshots.controller=(AppController)field.get(JournalSnapshots.frame);
   for(var mode:ThemeManager.Mode.values()){JournalHeaderAcceptance.selectTheme(mode);for(int width:new int[]{1280,420}){JournalSnapshots.edt(()->{JournalSnapshots.frame.setBounds(20,20,width,900);return null;});pages(mode,width);}}
  }catch(Throwable failure){report.add("FAIL: "+failure);failure.printStackTrace();exit=1;}finally{Files.write(JournalSnapshots.out.resolve("report.txt"),report);System.out.println(String.join("\n",report));}System.exit(exit);
 }
}
