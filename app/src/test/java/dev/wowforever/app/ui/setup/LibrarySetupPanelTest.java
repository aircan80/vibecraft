package dev.wowforever.app.ui.setup;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.ui.journal.JournalAppearance;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.theme.Tokens;
import java.awt.Component;
import java.awt.Container;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class LibrarySetupPanelTest {
  @Test void opensWithoutDatabaseAndDoesNotStartNetworkOrScan()throws Exception{
    var prefs=prefs();var count=new AtomicInteger();
    try{SwingUtilities.invokeAndWait(()->{
      var panel=new LibrarySetupPanel(prefs,path->{throw new AssertionError("Unexpected database open");},null,
          (product,cancelled)->{count.incrementAndGet();return "";},(path,cancelled)->{count.incrementAndGet();return new LibrarySetupPanel.Inspection("","");});
      advance(panel,2);
      assertThat(find(panel,"setup-source-atlas",JCheckBox.class).isSelected()).isFalse();
      assertThat(find(panel,"setup-source-wago",JCheckBox.class).isSelected()).isFalse();
      advance(panel,1);
      assertThat(find(panel,"setup-allow-cdn",JCheckBox.class).isSelected()).isFalse();
    });assertThat(count).hasValue(0);}finally{prefs.removeNode();}
  }
  @Test void preferencesRemainEditionScopedAndWagoDoesNotActivateWithoutNative()throws Exception{
    var prefs=prefs();
    try{SwingUtilities.invokeAndWait(()->{
      var panel=panel(prefs);advance(panel,2);
      find(panel,"setup-source-atlas",JCheckBox.class).doClick();
      find(panel,"setup-source-wago",JCheckBox.class).doClick();
      assertThat(find(panel,"setup-source-native",JCheckBox.class).isSelected()).isTrue();
      find(panel,"setup-source-native",JCheckBox.class).doClick();
      assertThat(find(panel,"setup-source-wago",JCheckBox.class).isSelected()).isFalse();
      assertThat(find(panel,"setup-source-wago",JCheckBox.class).isEnabled()).isFalse();
      panel.save();
      assertThat(prefs.getBoolean("forever.source.atlas",false)).isTrue();
      assertThat(prefs.getBoolean("classic.source.atlas",true)).isFalse();
      assertThat(prefs.getBoolean("forever.source.wago",true)).isFalse();
      assertThat(prefs.getBoolean("forever.cdn",true)).isFalse();
      var restored=panel(prefs);advance(restored,2);
      assertThat(find(restored,"setup-source-atlas",JCheckBox.class).isSelected()).isTrue();
    });}finally{prefs.removeNode();}
  }
  @Test void buildDiscoveryIsExplicitOffEdtAndNavigationSuppressesLateCompletion()throws Exception{
    var prefs=prefs();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var exited=new CountDownLatch(1);
    var ref=new AtomicReference<LibrarySetupPanel>();
    try{
      SwingUtilities.invokeAndWait(()->{
        var panel=new LibrarySetupPanel(prefs,path->{},null,(product,cancelled)->{
          assertThat(SwingUtilities.isEventDispatchThread()).isFalse();assertThat(product).isEqualTo("wow_classic_beta");entered.countDown();
          try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException ignored){release.await(3,TimeUnit.SECONDS);}finally{exited.countDown();}
          return "late build result";
        },(path,cancelled)->new LibrarySetupPanel.Inspection("local result",""));ref.set(panel);
        advance(panel,3);find(panel,"setup-discover-builds",JButton.class).doClick();
      });
      assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
      SwingUtilities.invokeAndWait(()->find(ref.get(),"setup-navigation",JTabbedPane.class).setSelectedIndex(1));
      release.countDown();assertThat(exited.await(3,TimeUnit.SECONDS)).isTrue();
      SwingUtilities.invokeAndWait(()->assertThat(allText(ref.get())).doesNotContain("late build result"));
    }finally{release.countDown();prefs.removeNode();}
  }
  @Test void localScanNeverEnablesCdnOrStartsDatabaseGeneration()throws Exception{
    var prefs=prefs();var called=new CountDownLatch(1);var ref=new AtomicReference<LibrarySetupPanel>();
    try{
      SwingUtilities.invokeAndWait(()->{
        var panel=new LibrarySetupPanel(prefs,path->{throw new AssertionError("Unexpected database open");},null,(product,cancelled)->"",(path,cancelled)->{
          assertThat(path).isNull();assertThat(SwingUtilities.isEventDispatchThread()).isFalse();called.countDown();return new LibrarySetupPanel.Inspection("No recognized installation found.","");});
        ref.set(panel);advance(panel,3);find(panel,"setup-detect-local",JButton.class).doClick();
      });assertThat(called.await(3,TimeUnit.SECONDS)).isTrue();
      SwingUtilities.invokeAndWait(()->{assertThat(find(ref.get(),"setup-allow-cdn",JCheckBox.class).isSelected()).isFalse();ref.get().removeNotify();});
      assertThat(prefs.get("forever.folder","")).isEmpty();
    }finally{prefs.removeNode();}
  }
  @Test void wizardBackPreservesChoicesAndReviewDoesNotPretendBuildIsAvailable()throws Exception{
    var prefs=prefs();try{SwingUtilities.invokeAndWait(()->{
      var panel=panel(prefs);advance(panel,2);find(panel,"setup-source-atlas",JCheckBox.class).doClick();
      find(panel,"setup-back",JButton.class).doClick();advance(panel,1);
      assertThat(find(panel,"setup-source-atlas",JCheckBox.class).isSelected()).isTrue();advance(panel,2);
      assertThat(find(panel,"setup-build-library",JButton.class).isEnabled()).isFalse();
      assertThat(allText(panel)).contains("AtlasLoot","Database generation is not ready yet");
    });}finally{prefs.removeNode();}
  }
  @Test void sourceManagementHasNoDatabaseSwitchEscape()throws Exception{
    var prefs=prefs();try{SwingUtilities.invokeAndWait(()->{
      var panel=LibrarySetupPanel.sourceManager(prefs);
      assertThat(find(panel,"setup-navigation",JTabbedPane.class).getSelectedIndex()).isEqualTo(1);
      org.assertj.core.api.Assertions.assertThatThrownBy(()->find(panel,"setup-open-database",JButton.class)).isInstanceOf(AssertionError.class);
    });}finally{prefs.removeNode();}
  }
  private static void advance(Container panel,int steps){for(int i=0;i<steps;i++)find(panel,"setup-next",JButton.class).doClick();}
  private static LibrarySetupPanel panel(Preferences prefs){return new LibrarySetupPanel(prefs,path->{},null,(product,cancelled)->"",(path,cancelled)->new LibrarySetupPanel.Inspection("",""));}
  private static Preferences prefs(){Theme.current();Theme.setCurrent(JournalAppearance.palette(Tokens.DARK));return Preferences.userRoot().node("vibecraft-tests/setup-"+UUID.randomUUID());}
  private static <T extends Component>T find(Container root,String name,Class<T>type){for(Component component:root.getComponents()){if(type.isInstance(component)&&name.equals(component.getName()))return type.cast(component);if(component instanceof Container nested){try{return find(nested,name,type);}catch(AssertionError ignored){}}}throw new AssertionError("Missing "+name);}
  private static String allText(Container root){var text=new StringBuilder();for(Component c:root.getComponents()){if(c instanceof JTextArea t)text.append(t.getText());if(c instanceof Container nested)text.append(allText(nested));}return text.toString();}
}
