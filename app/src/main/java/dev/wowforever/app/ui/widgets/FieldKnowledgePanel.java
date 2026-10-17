package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.model.FieldKnowledgeView;
import java.util.List;
import java.awt.*;
import javax.swing.*;

/** Compact wrapping field states, with unknown values explicit and zero values retained. */
public final class FieldKnowledgePanel extends JPanel {
  public FieldKnowledgePanel(List<FieldKnowledgeView> fields) {
    setOpaque(false);setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));setAlignmentX(LEFT_ALIGNMENT);
    setBorder(BorderFactory.createEmptyBorder(4,12,8,12));
    for(var field:fields) {
      JPanel row=new JPanel(new BorderLayout(8,0));row.setOpaque(false);row.setAlignmentX(LEFT_ALIGNMENT);
      row.add(new WrappingText(field.label(),field.label()),BorderLayout.CENTER);
      row.add(new KnowledgeChip(field),BorderLayout.EAST);add(row);
      if(field.value()!=null && !aggregateStats(field))add(new WrappingText(field.displayValue(),field.label()+" value"));
      if(field.field().equals("classic_stats"))add(new WrappingText("Classic Era value · build "+field.version()+"; Forever stats remain separate.","Classic Era fallback"));
    }
  }
  private static boolean aggregateStats(FieldKnowledgeView field) {
    if(!field.field().equals("stats") && !field.field().equals("classic_stats"))return false;
    String value=field.value().strip();
    // Aggregate states describe whether stats are known, not the importer's evidence marker.
    return !value.isEmpty() && !value.matches("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?");
  }
  @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
  @Override public Dimension getMinimumSize(){return new Dimension(0,getPreferredSize().height);}
}
