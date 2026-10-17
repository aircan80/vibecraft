package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.model.FieldKnowledgeView;
import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import dev.wowforever.app.ui.theme.Theme;

/** Explicit text carries the state, independently of colour or the presence of a value. */
public final class KnowledgeChip extends JLabel {
  public KnowledgeChip(FieldKnowledgeView field) {
    super(field.state().label());setOpaque(false);setIcon(new StateIcon(field.state()));
    setBorder(BorderFactory.createCompoundBorder(new LineBorder(Theme.current().textMuted()),BorderFactory.createEmptyBorder(1,5,1,5)));
    setToolTipText(field.source()+" · "+field.version()+" · "+field.evidence());
    getAccessibleContext().setAccessibleName(field.label()+": "+field.state().label());
  }
  private record StateIcon(FieldKnowledgeView.State state) implements Icon {
    @Override public int getIconWidth(){return 14;}
    @Override public int getIconHeight(){return 14;}
    @Override public void paintIcon(Component c,Graphics graphics,int x,int y) {
      Graphics2D g=(Graphics2D)graphics.create();g.translate(x,y);g.setColor(c.getForeground());
      g.setStroke(new BasicStroke(1.4f));g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
      switch(state) {
        case KNOWN -> {g.drawLine(2,7,6,11);g.drawLine(6,11,12,3);}
        case HIDDEN_BY_BLIZZARD -> {g.drawRoundRect(2,6,10,7,2,2);g.drawArc(4,1,6,9,0,180);}
        case NOT_YET_DISCOVERED -> {g.drawOval(1,1,12,12);g.drawString("?",4,11);}
        case REPORTED -> g.drawPolygon(new int[]{7,12,7,2},new int[]{1,7,13,7},4);
        case UNANNOUNCED -> {for(int dx:new int[]{2,6,10})g.fillOval(dx,6,2,2);}
        case NONE -> {g.drawOval(1,1,12,12);g.drawLine(3,11,11,3);}
      }
      g.dispose();
    }
  }
}
