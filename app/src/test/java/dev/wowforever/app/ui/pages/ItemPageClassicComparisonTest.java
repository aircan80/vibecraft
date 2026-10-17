package dev.wowforever.app.ui.pages;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.db.ItemRepository;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class ItemPageClassicComparisonTest {
  @Test void eraValuesRequireAnExplicitUserAction() throws Exception {
    var detail=FixtureDb.repositories().items().detail(19019).orElseThrow();
    SwingUtilities.invokeAndWait(() -> {
      var page=new ItemPage(detail,t -> {});
      page.setClassicComparison(new ItemRepository.ClassicComparison("1.15.9.70003","Distinct Era name",71,60,"ItemSparse.csv:7 · snapshot era-id"));
      JToggleButton toggle=descendants(page).stream().filter(JToggleButton.class::isInstance)
          .map(JToggleButton.class::cast).filter(b -> b.getText().equals("Compare with Classic Era")).findFirst().orElseThrow();
      assertThat(toggle.isSelected()).isFalse();
      assertThat(texts(page)).noneMatch(t -> t.contains("Distinct Era name"));
      toggle.doClick();
      assertThat(texts(page)).anyMatch(t -> t.contains("Distinct Era name"));
      assertThat(texts(page)).anyMatch(t -> t.contains("1.15.9.70003"));
      assertThat(texts(page)).anyMatch(t -> t.contains("snapshot era-id"));
      assertThat(detail.summary().displayName()).isNotEqualTo("Distinct Era name");
      toggle.doClick();
      assertThat(descendants(page).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast)
          .filter(t -> t.getText().contains("Distinct Era name")).findFirst().orElseThrow().getParent().isVisible()).isFalse();
    });
  }
  private static List<Component> descendants(Container root){
    var result=new ArrayList<Component>();
    for(Component c:root.getComponents()){result.add(c);if(c instanceof Container child)result.addAll(descendants(child));}
    return result;
  }
  private static List<String> texts(Container root){return descendants(root).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast).map(JTextArea::getText).toList();}
}
