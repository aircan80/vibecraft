package dev.wowforever.app.ui.widgets;

import java.awt.*;
import java.util.List;
import javax.swing.*;

/** Read-only source facts supplied by an immutable UI snapshot. Never resolves or infers missing metadata. */
public final class SourceEvidencePanel extends JPanel implements Scrollable {
  public record Fact(String label,String value) {}
  public SourceEvidencePanel(List<Fact> facts,String interpretation,String technical){
    setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));setOpaque(false);setAlignmentX(LEFT_ALIGNMENT);
    for(Fact fact:List.copyOf(facts)){
      var label=copy(fact.label());label.setFont(label.getFont().deriveFont(Font.BOLD));add(label);
      add(copy(fact.value()==null||fact.value().isBlank()?"Not recorded":fact.value()));add(Box.createVerticalStrut(10));
    }
    if(interpretation!=null&&!interpretation.isBlank()){add(copy(interpretation));add(Box.createVerticalStrut(10));}
    if(technical!=null&&!technical.isBlank()){
      var toggle=new JButton("Technical evidence");toggle.setFont(toggle.getFont().deriveFont(18f));toggle.setAlignmentX(LEFT_ALIGNMENT);
      var evidence=copy(technical);evidence.setVisible(false);add(toggle);add(evidence);
      toggle.addActionListener(event->{boolean shown=!evidence.isVisible();evidence.setVisible(shown);toggle.setText(shown?"Hide technical evidence":"Technical evidence");revalidate();repaint();});
    }
  }
  private static WrappingText copy(String value){var text=new WrappingText(value,"Source evidence");text.setFont(text.getFont().deriveFont(18f));text.setAlignmentX(LEFT_ALIGNMENT);((javax.swing.text.DefaultCaret)text.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);return text;}
  @Override public Dimension getPreferredScrollableViewportSize(){return new Dimension(620,500);}
  @Override public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 24;}
  @Override public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(24,r.height-24);}
  @Override public boolean getScrollableTracksViewportWidth(){return true;}
  @Override public boolean getScrollableTracksViewportHeight(){return false;}
}
