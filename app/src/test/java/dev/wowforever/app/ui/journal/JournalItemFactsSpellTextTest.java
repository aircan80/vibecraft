package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.app.model.*;
import dev.wowforever.app.ui.detail.DetailPanels;
import dev.wowforever.app.ui.widgets.ItemCardPanel;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class JournalItemFactsSpellTextTest {
  private JournalItemFacts facts(List<TooltipLine> lines) {
    var card=new ItemCard(1,lines,null,null,null,null);
    var item=new ItemDetail(new ItemSummary(1176,"Smelling Salts",null,null,null,null,0),null,null,null,
        List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),card);
    return new JournalItemFacts(new DetailPanels.LoadedItem(item,null,Map.of(),Set.of()),
        new ItemCardPanel(card,"Smelling Salts",null),target->{});
  }
  private List<Component> descendants(Container parent) {
    var list=new java.util.ArrayList<Component>();
    for(var c:parent.getComponents()){list.add(c);if(c instanceof Container child)list.addAll(descendants(child));}
    return list;
  }
  @Test void legacyEffectsAndSetBonusesMaskTokensAndExplainUnknownWithoutQueries()throws Exception {
    SwingUtilities.invokeAndWait(()->{
      var panel=facts(List.of(new TooltipLine("use","Use: Restores $q1 mana.",null),
          new TooltipLine("set_bonus","(4) Set: ${$proccooldown/60} min.",null),
          new TooltipLine("proc","Chance on hit: $s1 damage.",null)));
      var text=descendants(panel).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast).map(JTextArea::getText).toList();
      assertThat(text).contains("Use: Restores ? mana.","(4) Set: ? min.","Chance on hit: ? damage.");
      assertThat(text).noneMatch(t->t.contains("$q1")||t.contains("$proccooldown")||t.contains("$s1"));
      assertThat(text.stream().filter(t->t.contains("completeness unknown"))).hasSize(6); // summary and retained tooltip
      assertThat(descendants(panel).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast)
          .map(JTextArea::getToolTipText).filter(java.util.Objects::nonNull)).anyMatch(t->t.contains("$q1"));
    });
  }
  @Test void knownIncompleteSummaryIsQualifiedAndPlainLegacyTextIsNotWarned()throws Exception {
    SwingUtilities.invokeAndWait(()->{
      var evidence=new SpellTextEvidence("INCOMPLETE",42L,"Restores $q1.",List.of("$q1"),"csv/Spell.csv",null,null,null);
      var panel=facts(List.of(new TooltipLine("equip","Equip: Restores $q1.",null,null,evidence),
          new TooltipLine("stat","+10 Stamina",null)));
      var text=descendants(panel).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast).map(JTextArea::getText).toList();
      assertThat(text).contains("Equip: Restores ?.","+10 Stamina");
      assertThat(text.stream().filter(t->t.contains("Incomplete spell text"))).hasSize(2);
      assertThat(text).noneMatch(t->t.contains("unknown"));
    });
  }
  @Test void narrowTooltipHostFitsAndWrapsWhileWideHostKeepsCardWidth()throws Exception {
    SwingUtilities.invokeAndWait(()->{
      var card=new ItemCardPanel(new ItemCard(1,List.of(new TooltipLine("use","Use: A long recorded effect description that wraps across several lines when the viewport is narrow and never requires horizontal page scrolling.",null)),null,null,null,null),"Smelling Salts",null);
      var holder=new JournalItemFacts.TooltipHolder(card);holder.add(card);
      var parent=new JPanel(null);parent.add(holder);parent.setSize(240,1000);holder.setSize(240,1000);holder.doLayout();
      int narrowHeight=card.getHeight();
      assertThat(card.getWidth()).isLessThanOrEqualTo(240);
      assertThat(card.lines().getX()+card.lines().getWidth()).isLessThanOrEqualTo(card.getWidth());
      parent.setSize(1000,1000);holder.setSize(1000,1000);holder.doLayout();
      assertThat(card.getWidth()).isLessThanOrEqualTo(ItemCardPanel.maxWidth());
      assertThat(card.getHeight()).isLessThan(narrowHeight);
    });
  }
}
