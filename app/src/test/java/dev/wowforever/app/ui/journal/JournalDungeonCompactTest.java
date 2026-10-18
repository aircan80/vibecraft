package dev.wowforever.app.ui.journal;

import dev.wowforever.app.model.*;
import dev.wowforever.app.ui.*;
import dev.wowforever.app.ui.detail.DetailPanels;
import dev.wowforever.app.ui.pages.SharedLootPage;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JournalDungeonCompactTest {
  @Test void compactAttachedGuideKeepsLootUsableAndFiltersAccessible() throws Exception {
    SwingUtilities.invokeAndWait(()->{
      var instance=new InstanceSummary(27,"Molten Core","RAID40",60,60,2717L,"Molten Core");
      var detail=new InstanceDetail(instance,List.of(new BossSummary(1,"Lucifron",12118L,null,null,false),
          new BossSummary(289,"All bosses",null,null,null,true)),List.of("NORMAL"));
      var loaded=new DetailPanels.LoadedInstance(detail,List.of(),"70450",(id,mode)->List.of(
          new LootRow(new EntityRef(EntityKind.ITEM,18264),"Plans: Elemental Sharpening Stone",null,null,"atlasloot",1)));
      var guide=(JournalDetails.JournalPage)JournalDetails.render(loaded,null,t->{},Runnable::run,StatusSink.NONE,SourceFilter.ALL,BooleanSetting.inMemory(false));
      guide.tabs().setSelectedIndex(2);
      var shared=(SharedLootPage)guide.tabs().getSelectedComponent();
      var page=new JournalDungeonPage(new JournalCatalog.DungeonSnapshot(List.of(instance),List.of()),t->{},27,guide);
      // A 420×900 app leaves approximately 740px below its application chrome.
      page.setSize(420,740);layoutRepeated(page);
      assertThat(shared.listPage().getHeight()).isGreaterThanOrEqualTo(300);
      assertThat(shared.listPage().lootTable().table().getVisibleRect().height).isPositive();
      var filters=toggle(page,"Destination filters");assertThat(filters.isSelected()).isFalse();
      var search=all(page).stream().filter(JTextField.class::isInstance).findFirst().orElseThrow();
      assertThat(visibleThrough(search,page)).isFalse();
      filters.doClick();layoutRepeated(page);
      assertThat(visibleThrough(search,page)).isTrue();
      Object expanded=page.saveState();filters.doClick();page.restoreState(expanded);layoutRepeated(page);
      assertThat(filters.isSelected()).isTrue();
      filters.doClick();layoutRepeated(page);
      var evidence=toggle(page,"About shared loot evidence");evidence.doClick();layoutRepeated(page);
      assertThat(shared.listPage().getHeight()).isGreaterThan(200);
      evidence.doClick();layoutRepeated(page);
      assertThat(shared.listPage().getHeight()).isGreaterThanOrEqualTo(300);
      toggle(page,"Destinations").doClick();layoutRepeated(page);assertThat(guide.getParent().isVisible()).isFalse();
      toggle(page,"Selected guide").doClick();layoutRepeated(page);assertThat(shared.listPage().getHeight()).isGreaterThanOrEqualTo(300);
      page.setSize(1200,900);layoutRepeated(page);assertThat(filters.isVisible()).isFalse();
      assertThat(visibleThrough(search,page)).isTrue();
    });
  }
  private static JToggleButton toggle(Container root,String text){return all(root).stream().filter(JToggleButton.class::isInstance).map(JToggleButton.class::cast).filter(b->text.equals(b.getText())).findFirst().orElseThrow();}
  private static boolean visibleThrough(Component child,Component root){for(Component c=child;c!=null;c=c.getParent()){if(!c.isVisible())return false;if(c==root)return true;}return false;}
  private static List<Component> all(Container root){var list=new java.util.ArrayList<Component>();for(Component c:root.getComponents()){list.add(c);if(c instanceof Container child)list.addAll(all(child));}return list;}
  private static void layoutRepeated(Container root){for(int i=0;i<16;i++)layout(root);}
  private static void layout(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container child)layout(child);}
}
