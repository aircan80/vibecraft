package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.model.NpcAppearance;
import dev.wowforever.app.preview.NpcPreviewService;
import dev.wowforever.app.ui.pages.NpcPage;
import java.awt.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FixtureDb.class)
class JournalNpcPreviewTest {
  @TempDir Path cache;
  @Test void unresolvedAppearanceNeverStartsARender()throws Exception {
    var detail=FixtureDb.repositories().npcs().detail(639).orElseThrow();
    try(var service=new NpcPreviewService(config(),(r,f,o,c)->{throw new AssertionError("Unresolved mapping must not render");})){
      var unresolved=new NpcAppearance("wow_classic_beta","1.60.1.70450",639,"Fixture NPC",NpcAppearance.State.UNRESOLVED,List.of(),List.of(),"No exact-build appearance mapping recorded.","");
      SwingUtilities.invokeAndWait(()->{
        var page=new JournalNpcPreview(new NpcPage(detail,t->{},s->{}),unresolved,service);
        assertThat(all(page)).noneMatch(c->c instanceof JButton b&&"Generate preview".equals(b.getText()));
        assertThat(text(page)).doesNotContain("No exact-build appearance mapping recorded.");
        for(int width:new int[]{1280,420}){
          page.setSize(width,700);layout(page);
          var strip=((BorderLayout)page.getLayout()).getLayoutComponent(BorderLayout.NORTH);
          assertThat(strip.getHeight()).isLessThanOrEqualTo(com.formdev.flatlaf.util.UIScale.scale(44));
          var npc=((BorderLayout)page.getLayout()).getLayoutComponent(BorderLayout.CENTER);
          assertThat(npc.getY()).isLessThanOrEqualTo(com.formdev.flatlaf.util.UIScale.scale(60));
        }
        unavailable(page).doClick();layout(page);
        assertThat(text(page)).contains("No exact-build appearance mapping recorded.","1.60.1.70450");
        assertThat(text(page)).doesNotContain("\"product\"");
        var restored=new JournalNpcPreview(new NpcPage(detail,t->{},s->{}),unresolved,service);
        restored.restoreState(page.saveState());assertThat(unavailable(restored).isSelected()).isTrue();
        assertThat(text(restored)).contains("No exact-build appearance mapping recorded.");
        ((JToggleButton)all(page).stream().filter(c->c instanceof JToggleButton b&&b.getText().equals("Appearance evidence")).findFirst().orElseThrow()).doClick();
        layout(page);assertThat(text(page)).contains("\"product\"", "wow_classic_beta");
        var strip=((BorderLayout)page.getLayout()).getLayoutComponent(BorderLayout.NORTH);
        assertThat(strip.getHeight()).isLessThanOrEqualTo(com.formdev.flatlaf.util.UIScale.scale(220));
      });
    }
  }
  @Test void multipleDisplaysRequireChoiceAndLateJobsCannotChangeAnotherVariant()throws Exception {
    var detail=FixtureDb.repositories().npcs().detail(639).orElseThrow();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    try(var service=new NpcPreviewService(config(),(r,f,o,c)->{entered.countDown();release.await(5,TimeUnit.SECONDS);throw new java.io.IOException("controlled late failure");})){
      var appearance=new NpcAppearance("wow_classic_beta","1.60.1.70450",639,"Fixture NPC",NpcAppearance.State.MAPPED,List.of(variant(1),variant(2)),List.of(),"Recorded displays","catalog");
      AtomicReference<JournalNpcPreview> ref=new AtomicReference<>();
      SwingUtilities.invokeAndWait(()->{
        var page=new JournalNpcPreview(new NpcPage(detail,t->{},s->{}),appearance,service);ref.set(page);
        assertThat(button(page,"Generate preview").isEnabled()).isFalse();
        combo(page).setSelectedIndex(1);
      });
      waitEnabled(ref.get());SwingUtilities.invokeAndWait(()->button(ref.get(),"Generate preview").doClick());assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
      SwingUtilities.invokeAndWait(()->{
        var rebuilt=new JournalNpcPreview(new NpcPage(detail,t->{},s->{}),appearance,service);
        combo(rebuilt).setSelectedIndex(1);
        assertThat(text(rebuilt)).contains("Preview queued or rendering locally");
        assertThat(button(rebuilt,"Generate preview").isEnabled()).isFalse();
        rebuilt.removeNotify(); // callbacks must not touch this discarded page
        combo(ref.get()).setSelectedIndex(2);
      });waitEnabled(ref.get());
      release.countDown();Thread.sleep(100);
      SwingUtilities.invokeAndWait(()->{
        assertThat(button(ref.get(),"Generate preview").isEnabled()).isTrue();
        assertThat(text(ref.get())).doesNotContain("This appearance could not be rendered.");
        assertThat(combo(ref.get()).getSelectedIndex()).isEqualTo(2);
      });
    }
  }
  @Test void unsupportedDisplaysKeepExactReasonsBehindTheCompactDisclosure()throws Exception {
    var detail=FixtureDb.repositories().npcs().detail(639).orElseThrow();
    try(var service=new NpcPreviewService(config(),(r,f,o,c)->{throw new AssertionError("Unsupported display must not render");})){
      var unsupported=new NpcAppearance.Variant(555,0,100,123,List.of(1L,0L,0L,0L),1d,1d,"unsupported",NpcAppearance.State.UNSUPPORTED,"Humanoid material assembly is not supported.",List.of());
      var appearance=new NpcAppearance("wow_classic_beta","1.60.1.70450",639,"Fixture NPC",NpcAppearance.State.UNSUPPORTED,List.of(unsupported),List.of(),"Native display recorded.","catalog");
      SwingUtilities.invokeAndWait(()->{
        var page=new JournalNpcPreview(new NpcPage(detail,t->{},s->{}),appearance,service);
        assertThat(all(page)).noneMatch(c->c instanceof JButton b&&"Generate preview".equals(b.getText()));
        unavailable(page).doClick();assertThat(text(page)).contains("Display 555","Humanoid material assembly is not supported.");
        unavailable(page).doClick();assertThat(unavailable(page).isSelected()).isFalse();
      });
    }
  }
  @Test void mixedDisplaySupportRetainsTheExplicitGenerationWorkflow()throws Exception {
    var detail=FixtureDb.repositories().npcs().detail(639).orElseThrow();
    try(var service=new NpcPreviewService(config(),(r,f,o,c)->{throw new AssertionError("Test only checks selection");})){
      var unsupported=new NpcAppearance.Variant(555,1,100,123,List.of(1L,0L,0L,0L),1d,1d,"unsupported",NpcAppearance.State.UNSUPPORTED,"Humanoid material assembly is not supported.",List.of());
      var appearance=new NpcAppearance("wow_classic_beta","1.60.1.70450",639,"Fixture NPC",NpcAppearance.State.PARTIAL,List.of(variant(1),unsupported),List.of(),"Mixed mapping coverage.","catalog");
      var page=new AtomicReference<JournalNpcPreview>();
      SwingUtilities.invokeAndWait(()->{
        page.set(new JournalNpcPreview(new NpcPage(detail,t->{},s->{}),appearance,service));
        assertThat(combo(page.get()).getItemCount()).isEqualTo(3);
        combo(page.get()).setSelectedIndex(2);assertThat(text(page.get())).contains("Humanoid material assembly is not supported.");
        assertThat(button(page.get(),"Generate preview").isEnabled()).isFalse();combo(page.get()).setSelectedIndex(1);
      });waitEnabled(page.get());
    }
  }
  private static JToggleButton unavailable(Container page){return (JToggleButton)all(page).stream().filter(c->c instanceof JToggleButton b&&b.getText().startsWith("Model preview unavailable")).findFirst().orElseThrow();}
  private static void layout(Container root){for(int i=0;i<16;i++)layoutOnce(root);}
  private static void layoutOnce(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container nested)layoutOnce(nested);}

  private NpcPreviewService.Config config(){return new NpcPreviewService.Config(cache,null,"python3","test",Duration.ofSeconds(2));}
  private static NpcAppearance.Variant variant(int display){return new NpcAppearance.Variant(display,display-1,100,123,List.of(1L,0L,0L,0L),1d,1d,"variant"+display,NpcAppearance.State.MAPPED,"",List.of());}
  private static void waitEnabled(JournalNpcPreview page)throws Exception {
    long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean[] enabled={false};
    while(System.nanoTime()<end){SwingUtilities.invokeAndWait(()->enabled[0]=button(page,"Generate preview").isEnabled());if(enabled[0])return;Thread.sleep(10);}throw new AssertionError("Preview action did not become ready");
  }
  private static JButton button(Container root,String label){return (JButton)all(root).stream().filter(c->c instanceof JButton b&&label.equals(b.getText())).findFirst().orElseThrow();}
  private static JComboBox<?> combo(Container root){return (JComboBox<?>)all(root).stream().filter(JComboBox.class::isInstance).findFirst().orElseThrow();}
  private static String text(Container root){return all(root).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast).map(JTextArea::getText).collect(java.util.stream.Collectors.joining("\n"));}
  private static List<Component> all(Container root){var result=new ArrayList<Component>();for(var c:root.getComponents()){result.add(c);if(c instanceof Container child)result.addAll(all(child));}return result;}
}
