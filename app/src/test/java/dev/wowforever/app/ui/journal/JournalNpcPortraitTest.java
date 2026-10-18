package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.app.db.NpcPortraits;
import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.portrait.NpcPortraitProvider.*;
import dev.wowforever.app.portrait.NpcPortraitRepository;
import dev.wowforever.app.ui.pages.NpcPage;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class JournalNpcPortraitTest {
  private static final Key KEY=new Key("wow_classic_beta","1.60.1.70450",639);
  @Test void missingNpcAndDifferentBuildHaveNoBundledPortrait() {
    assertThat(NpcPortraits.load(247003,"1.60.1.70450")).isEmpty();
    assertThat(NpcPortraits.load(2671,"1.15.9.70003")).isEmpty();
  }
  @Test void alreadyDecodedPortraitHasOnlyOrdinaryImageAndSourceControls() throws Exception {
    var portrait=NpcPortraits.load(2671,"1.60.1.70450").orElseThrow();
    assertThat(portrait.image().getWidth()).isEqualTo(768);
    SwingUtilities.invokeAndWait(()->{
      var page=new JournalNpcPortrait(npc(),portrait);page.setSize(420,700);layout(page);
      assertThat(hasImage(page)).isTrue();assertNoGenerationOrRotation(page);
      assertThat(text(page)).contains("Blizzard Entertainment");
      var source=all(page).stream().filter(dev.wowforever.app.ui.widgets.SourceInfoButton.class::isInstance).map(dev.wowforever.app.ui.widgets.SourceInfoButton.class::cast).findFirst().orElseThrow();
      assertThat(text(source.detailsContent())).contains("wow_classic_beta","1.60.1.70450","Static portrait for NPC #2671","Not recorded");
      assertThat(all(page).stream().filter(c->c instanceof JScrollPane pane && pane.getViewport().getView() instanceof JTextArea).noneMatch(Component::isVisible)).isTrue();
      page.restoreState(page.saveState());
      assertThatThrownBy(()->NpcPortraits.load(2671,"1.60.1.70450")).isInstanceOf(IllegalStateException.class);
    });
  }
  @Test void asynchronousMissingStateStaysCompactAndRestoresSourceDisclosure()throws Exception {
    try(var repository=new NpcPortraitRepository(List.of(key->{assertThat(SwingUtilities.isEventDispatchThread()).isFalse();return Result.missing();}))){
      var page=new AtomicReference<JournalNpcPortrait>();SwingUtilities.invokeAndWait(()->page.set(new JournalNpcPortrait(npc(),KEY,repository)));
      waitFor(()->unavailable(page.get())!=null);
      var state=new AtomicReference<Object>();SwingUtilities.invokeAndWait(()->{
        for(int width:new int[]{1280,420}){page.get().setSize(width,700);layout(page.get());
          Component strip=((BorderLayout)page.get().getLayout()).getLayoutComponent(BorderLayout.NORTH);
          assertThat(strip.getHeight()).isLessThanOrEqualTo(com.formdev.flatlaf.util.UIScale.scale(44));
          Component body=((BorderLayout)page.get().getLayout()).getLayoutComponent(BorderLayout.CENTER);
          assertThat(body.getY()).isLessThanOrEqualTo(com.formdev.flatlaf.util.UIScale.scale(60));
        }
        assertThat(hasImage(page.get())).isFalse();assertNoGenerationOrRotation(page.get());
        unavailable(page.get()).doClick();assertThat(text(page.get())).contains("No verified portrait",KEY.build());state.set(page.get().saveState());
      });
      var restored=new AtomicReference<JournalNpcPortrait>();SwingUtilities.invokeAndWait(()->{restored.set(new JournalNpcPortrait(npc(),KEY,repository));restored.get().restoreState(state.get());});
      waitFor(()->unavailable(restored.get())!=null);
      SwingUtilities.invokeAndWait(()->assertThat(unavailable(restored.get()).isSelected()).isTrue());
    }
  }
  @Test void detachedPageIgnoresLateImageAndReattachesFromVerifiedMemoryCache()throws Exception {
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    try(var repository=new NpcPortraitRepository(List.of(key->{entered.countDown();release.await(5,TimeUnit.SECONDS);return Result.available(new Portrait(new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB),key,"Test image source","Fixture evidence"));}))){
      var page=new AtomicReference<JournalNpcPortrait>();SwingUtilities.invokeAndWait(()->page.set(new JournalNpcPortrait(npc(),KEY,repository)));
      assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();SwingUtilities.invokeAndWait(()->{page.get().removeNotify();assertThat(hasImage(page.get())).isFalse();});
      release.countDown();repository.load(KEY).get(5,TimeUnit.SECONDS);SwingUtilities.invokeAndWait(()->assertThat(hasImage(page.get())).isFalse());
      SwingUtilities.invokeAndWait(()->page.get().addNotify());waitFor(()->hasImage(page.get()));
      SwingUtilities.invokeAndWait(()->{assertNoGenerationOrRotation(page.get());page.get().removeNotify();});
    }finally{release.countDown();}
  }
  private static NpcPage npc(){var detail=FixtureDb.repositories().npcs().detail(639).orElseThrow();return new NpcPage(detail,t->{},text->{});}
  private static void assertNoGenerationOrRotation(Container page){assertThat(all(page).stream().filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast).map(AbstractButton::getText)).noneMatch(s->s.contains("Generate")||s.contains("Rotat")||s.contains("Play")||s.contains("model"));}
  private static boolean hasImage(Container page){return all(page).stream().anyMatch(c->"npc-portrait-image".equals(c.getName()));}
  private static JToggleButton unavailable(Container page){return (JToggleButton)all(page).stream().filter(c->c instanceof JToggleButton b&&"Portrait unavailable".equals(b.getAccessibleContext().getAccessibleName())).findFirst().orElse(null);}
  private static String text(Container page){return all(page).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast).map(JTextArea::getText).collect(java.util.stream.Collectors.joining("\n"));}
  private static List<Component> all(Container root){var out=new ArrayList<Component>();for(Component c:root.getComponents()){out.add(c);if(c instanceof Container nested)out.addAll(all(nested));}return out;}
  private static void layout(Container root){for(int i=0;i<12;i++)layoutOnce(root);}
  private static void layoutOnce(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container nested)layoutOnce(nested);}
  private static void waitFor(java.util.function.BooleanSupplier ready)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean[] value={false};while(System.nanoTime()<end){SwingUtilities.invokeAndWait(()->value[0]=ready.getAsBoolean());if(value[0])return;Thread.sleep(10);}throw new AssertionError("Portrait UI did not finish loading");}
}
