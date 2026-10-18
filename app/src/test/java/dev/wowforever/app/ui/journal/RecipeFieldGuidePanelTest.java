package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.db.NativeRecipeGuideRepository.*;
import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.ViewTarget;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class RecipeFieldGuidePanelTest {
  @Test void reagentLinksOpenActualItemsAndEvidenceIsCollapsed()throws Exception {
    var recipe=new Recipe(100,"Native recipe",10,"Output","2","Effect #1",List.of(new Ingredient(11,"Reagent",3L)),List.of(),List.of(new Evidence("SpellEffect","native/effect.csv",2)),"Native row");
    SwingUtilities.invokeAndWait(()->{
      List<ViewTarget> opened=new ArrayList<>();var page=new RecipeFieldGuidePanel(new Snapshot("snapshot","70450",List.of(recipe)),opened::add);
      buttons(page).stream().filter(b->b.getAccessibleContext().getAccessibleName().equals("Inspect 3 × Reagent")).findFirst().orElseThrow().doClick();
      assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(EntityKind.ITEM,11)));
      assertThat(toggles(page)).hasSize(1).allMatch(t->!t.isSelected());toggles(page).getFirst().doClick();assertThat(toggles(page).getFirst().getText()).isEqualTo("Hide recipe source evidence");
    });
  }
  @Test void classCreationExplainsContextDependentYieldWithoutClaimingProfessionRank()throws Exception {
    var recipe=new Recipe(5504,"Conjure Water",5350,"Conjured Water","Context-dependent","Effect #1",List.of(),List.of(new Skill(237,"Arcane",1L,0L,0L,7L)),List.of(),"Missing reagent record");
    SwingUtilities.invokeAndWait(()->{
      var page=new RecipeFieldGuidePanel(new Snapshot("f","70450",List.of(recipe)),t->{});
      String text=text(page);assertThat(text).contains("Quantity Context-dependent","Class ability · Arcane","No fixed quantity is calculated").doesNotContain("Creates 2 ×","Required skill");
    });
  }
  private static String text(Container root){StringBuilder result=new StringBuilder();for(Component child:root.getComponents()){if(child instanceof JTextArea area)result.append(area.getText()).append(' ');if(child instanceof JLabel label)result.append(label.getText()).append(' ');if(child instanceof Container c)result.append(text(c));}return result.toString();}
  private static List<JButton> buttons(Container root){List<JButton> found=new ArrayList<>();for(Component child:root.getComponents()){if(child instanceof JButton button)found.add(button);if(child instanceof Container c)found.addAll(buttons(c));}return found;}
  private static List<JToggleButton> toggles(Container root){List<JToggleButton> found=new ArrayList<>();for(Component child:root.getComponents()){if(child instanceof JToggleButton button)found.add(button);if(child instanceof Container c)found.addAll(toggles(c));}return found;}
}
