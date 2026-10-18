package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.db.Database;
import dev.wowforever.app.db.RecordedObjectSourcesRepository;
import dev.wowforever.app.db.RecordedObjectSourcesRepositoryTest;
import dev.wowforever.app.nav.ViewTarget;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournalRecordedObjectSourcesTest {
  @TempDir Path temp;
  @Test void loadsOnlyOnExplicitActionInspectsWithoutNavigationAndRestoresSelection() throws Exception {
    Path file=temp.resolve("panel.sqlite");RecordedObjectSourcesRepositoryTest.fixture(file);
    try(var db=Database.open(file)){
      SwingUtilities.invokeAndWait(()->{
        var queue=new ArrayDeque<Runnable>();List<ViewTarget> opened=new ArrayList<>();
        var panel=new JournalRecordedObjectSources(2770,new RecordedObjectSourcesRepository(db),queue::add,opened::add);
        assertThat(queue).isEmpty();button(panel,"Load recorded object sources").doClick();assertThat(queue).hasSize(1);
        drain(queue);assertThat(opened).isEmpty();
        JList<?> list=find(panel,JList.class);assertThat(list.getModel().getSize()).isEqualTo(2);
        list.setSelectedIndex(1);drain(queue);assertThat(opened).isEmpty();
        assertThat(coordinates(panel).getText()).contains("Battered Chest","30.00, 40.00").doesNotContain("10.00, 20.00");
        button(panel,"Open object").doClick();assertThat(opened).hasSize(1);
        var state=panel.saveState();var restored=new JournalRecordedObjectSources(2770,new RecordedObjectSourcesRepository(db),queue::add,opened::add);
        restored.restoreState(state);drain(queue);assertThat(restored.saveState()).isEqualTo(state);
        button(restored,"Open zone guide").doClick();assertThat(opened).hasSize(2);
      });
    }
  }
  @Test void obsoleteQueuedZoneAndPointRequestsDoNotPublishAfterSelectionOrRemoval() throws Exception {
    Path file=temp.resolve("cancel.sqlite");RecordedObjectSourcesRepositoryTest.fixture(file);
    try(var db=Database.open(file)){
      SwingUtilities.invokeAndWait(()->{
        var queue=new ArrayDeque<Runnable>();var panel=new JournalRecordedObjectSources(2770,new RecordedObjectSourcesRepository(db),queue::add,target->{});
        button(panel,"Load recorded object sources").doClick();queue.remove().run();
        JComboBox<?> zones=find(panel,JComboBox.class);zones.setSelectedIndex(1);drain(queue);
        assertThat(coordinates(panel).getText()).contains("5.00, 6.00").doesNotContain("30.00, 40.00");
        zones.setSelectedIndex(0);panel.removeNotify();drain(queue);
        assertThat(button(panel,"Load recorded object sources").isEnabled()).isTrue();
        assertThat(find(panel,JList.class).getModel().getSize()).isZero();
      });
    }
  }
  private static JTextArea coordinates(Container root){return all(root,JTextArea.class).stream().filter(a->"Selected object recorded coordinates".equals(a.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();}
  private static void drain(ArrayDeque<Runnable> queue){while(!queue.isEmpty())queue.remove().run();}
  private static JButton button(Container root,String label){return all(root,JButton.class).stream().filter(b->label.equals(b.getText())).findFirst().orElseThrow();}
  private static <T> T find(Container root,Class<T> type){return all(root,type).getFirst();}
  private static <T> List<T> all(Container root,Class<T> type){List<T> result=new ArrayList<>();for(Component child:root.getComponents()){if(type.isInstance(child))result.add(type.cast(child));if(child instanceof Container container)result.addAll(all(container,type));}return result;}
}
