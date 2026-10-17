package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.systems.ClientSystems.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TalentPageTest {
  private Row row(String table,long id,String... pairs){Map<String,String> values=new HashMap<>();for(int i=0;i<pairs.length;i+=2)values.put(pairs[i],pairs[i+1]);return new Row(table,id,values,"native.csv",2);}
  @Test void betaMilestonesAndUnresolvedNativeDescriptionsAreQualified()throws Exception{
    Map<String,List<Row>> t=new HashMap<>();
    t.put("TraitTree",List.of(row("TraitTree",1,"TraitSystemID","10")));
    t.put("TraitTreeXTraitCurrency",List.of(row("TraitTreeXTraitCurrency",1,"TraitTreeID","1","TraitCurrencyID","3820")));
    t.put("SkillLineXTraitTree",List.of(row("SkillLineXTraitTree",1,"TraitTreeID","1","SkillLineID","8")));
    t.put("TraitCurrency",List.of(row("TraitCurrency",3820,"SourcedMax","51")));
    t.put("TraitNode",List.of(row("TraitNode",1,"TraitTreeID","1","Type","0","Flags","0")));
    t.put("TraitNodeXTraitNodeEntry",List.of(row("TraitNodeXTraitNodeEntry",1,"TraitNodeID","1","TraitNodeEntryID","2")));
    t.put("TraitNodeEntry",List.of(row("TraitNodeEntry",2,"TraitDefinitionID","3","MaxRanks","1","NodeEntryType","0")));
    t.put("TraitDefinition",List.of(row("TraitDefinition",3,"SpellID","4")));
    t.put("Spell",List.of(row("Spell",4,"Description_lang","Adds $s1 damage.")));
    SwingUtilities.invokeAndWait(()->{
      JPanel page=new TalentPage(new Catalog("1.60.1.70450","s",t));String text=allText(page);
      assertThat(text).contains("11 · 16 · 21 · 31", "above level 30 is unconfirmed", "Adds $s1 damage", "formula tokens are unresolved");
    });
  }
  private String allText(Container c){StringBuilder b=new StringBuilder();for(Component child:c.getComponents()){if(child instanceof JLabel l)b.append(l.getText());if(child instanceof JTextArea a)b.append(a.getText());if(child instanceof Container nested)b.append(allText(nested));}return b.toString();}
}
