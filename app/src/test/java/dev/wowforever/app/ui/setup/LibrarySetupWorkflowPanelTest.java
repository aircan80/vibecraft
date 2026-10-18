package dev.wowforever.app.ui.setup;

import static org.assertj.core.api.Assertions.assertThat;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Injected authored services only; these checks do not claim a production build exists. */
class LibrarySetupWorkflowPanelTest {
 @Test void firstRunNeedsExplicitOpenAndUsesTheReviewedFrozenDraft()throws Exception{
  var prefs=prefs();var ref=new AtomicReference<LibrarySetupPanel>();var opened=new ArrayList<Path>();
  try{
   SwingUtilities.invokeAndWait(()->{ref.set(new LibrarySetupPanel(prefs,opened::add,null,new LibrarySetupCoordinatorTest.AuthoredService()));advance(ref.get(),4);button(ref.get(),"setup-review-requirements").doClick();});
   LibrarySetupCoordinatorTest.await(()->button(ref.get(),"setup-build-library").isEnabled());SwingUtilities.invokeAndWait(()->button(ref.get(),"setup-build-library").doClick());LibrarySetupCoordinatorTest.await(()->exists(ref.get(),"setup-open-built-library"));
   SwingUtilities.invokeAndWait(()->{assertThat(opened).isEmpty();button(ref.get(),"setup-open-built-library").doClick();assertThat(opened).containsExactly(Path.of("authored-library.sqlite"));ref.get().close();});
  }finally{prefs.removeNode();}
 }
 @Test void managementCompletionKeepsCurrentSessionAndHasNoOpenLibraryAction()throws Exception{
  var prefs=prefs();var ref=new AtomicReference<LibrarySetupPanel>();
  try{
   SwingUtilities.invokeAndWait(()->{ref.set(LibrarySetupPanel.sourceManager(prefs,new LibrarySetupCoordinatorTest.AuthoredService()));find(ref.get(),"setup-navigation",JTabbedPane.class).setSelectedIndex(0);advance(ref.get(),4);button(ref.get(),"setup-review-requirements").doClick();});
   LibrarySetupCoordinatorTest.await(()->button(ref.get(),"setup-build-library").isEnabled());SwingUtilities.invokeAndWait(()->button(ref.get(),"setup-build-library").doClick());LibrarySetupCoordinatorTest.await(()->text(ref.get()).contains("Your current session keeps its existing library"));
   SwingUtilities.invokeAndWait(()->{assertThat(exists(ref.get(),"setup-open-built-library")).isFalse();assertThat(exists(ref.get(),"setup-open-database")).isFalse();ref.get().close();});
  }finally{prefs.removeNode();}
 }
 @Test void changingAReviewedSourceRequiresFreshReviewAndUnsupportedChoiceStaysBlocked()throws Exception{
  var prefs=prefs();var ref=new AtomicReference<LibrarySetupPanel>();try{
   SwingUtilities.invokeAndWait(()->{ref.set(new LibrarySetupPanel(prefs,path->{},null,new LibrarySetupCoordinatorTest.AuthoredService()));advance(ref.get(),4);button(ref.get(),"setup-review-requirements").doClick();});LibrarySetupCoordinatorTest.await(()->button(ref.get(),"setup-build-library").isEnabled());
   SwingUtilities.invokeAndWait(()->{button(ref.get(),"setup-back").doClick();button(ref.get(),"setup-back").doClick();find(ref.get(),"setup-source-atlas",JCheckBox.class).doClick();advance(ref.get(),2);assertThat(button(ref.get(),"setup-build-library").isEnabled()).isFalse();button(ref.get(),"setup-review-requirements").doClick();});
   LibrarySetupCoordinatorTest.await(()->text(ref.get()).contains("atlas source is not available"));SwingUtilities.invokeAndWait(()->{assertThat(button(ref.get(),"setup-build-library").isEnabled()).isFalse();ref.get().close();});
  }finally{prefs.removeNode();}
 }
 @Test void continuousProgressPreservesStopButtonAndActivePress()throws Exception{
  var prefs=prefs();var panel=new AtomicReference<LibrarySetupPanel>();var release=new AtomicBoolean();var reported=new AtomicInteger();
  var service=new LibrarySetupCoordinatorTest.AuthoredService(){@Override public Result build(Review review,Mode mode,Control control)throws Exception{
   while(!release.get()){int count=reported.incrementAndGet();control.progress().accept(new Progress(Phase.DOWNLOAD,"Source report "+count,count,100_000,"bytes"));Thread.sleep(2);}
   control.boundary();return new Result(Path.of("authored.sqlite"),"Done",false);
  }};
  try{
   SwingUtilities.invokeAndWait(()->{panel.set(new LibrarySetupPanel(prefs,path->{},null,service));advance(panel.get(),4);button(panel.get(),"setup-review-requirements").doClick();});
   LibrarySetupCoordinatorTest.await(()->button(panel.get(),"setup-build-library").isEnabled());
   SwingUtilities.invokeAndWait(()->button(panel.get(),"setup-build-library").doClick());
   LibrarySetupCoordinatorTest.await(()->text(panel.get()).contains("Source report"));
   var stop=new AtomicReference<JButton>();var parent=new AtomicReference<Container>();var initialText=new AtomicReference<String>();var removed=new AtomicInteger();
   SwingUtilities.invokeAndWait(()->{var button=button(panel.get(),"setup-stop-build");stop.set(button);parent.set(button.getParent());initialText.set(text(panel.get()));button.addHierarchyListener(event->{if((event.getChangeFlags()&java.awt.event.HierarchyEvent.PARENT_CHANGED)!=0)removed.incrementAndGet();});button.getModel().setArmed(true);button.getModel().setPressed(true);});
   LibrarySetupCoordinatorTest.await(()->!text(panel.get()).equals(initialText.get()));
   SwingUtilities.invokeAndWait(()->{assertThat(button(panel.get(),"setup-stop-build")).isSameAs(stop.get());assertThat(stop.get().getParent()).isSameAs(parent.get());assertThat(removed).hasValue(0);assertThat(stop.get().getModel().isPressed()).isTrue();assertThat(stop.get().getModel().isArmed()).isTrue();stop.get().getModel().setArmed(false);stop.get().getModel().setPressed(false);panel.get().close();});
  }finally{release.set(true);SwingUtilities.invokeAndWait(()->{if(panel.get()!=null)panel.get().close();});prefs.removeNode();}
 }
 private static Preferences prefs(){dev.wowforever.app.ui.theme.Theme.current();return Preferences.userRoot().node("vibecraft-tests/authored-workflow-"+UUID.randomUUID());}
 private static void advance(Container root,int count){for(int i=0;i<count;i++)button(root,"setup-next").doClick();}
 private static JButton button(Container root,String name){return find(root,name,JButton.class);}
 private static boolean exists(Container root,String name){try{find(root,name,JComponent.class);return true;}catch(AssertionError absent){return false;}}
 private static <T extends Component>T find(Container root,String name,Class<T> type){for(Component child:root.getComponents()){if(type.isInstance(child)&&name.equals(child.getName()))return type.cast(child);if(child instanceof Container nested){try{return find(nested,name,type);}catch(AssertionError ignored){}}}throw new AssertionError("Missing "+name);}
 private static String text(Container root){var out=new StringBuilder();for(Component child:root.getComponents()){if(child instanceof JTextArea area)out.append(area.getText());if(child instanceof Container nested)out.append(text(nested));}return out.toString();}
}
