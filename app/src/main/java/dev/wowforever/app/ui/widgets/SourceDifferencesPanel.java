package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.model.SourceDifference;
import java.util.List;
import java.awt.*;
import javax.swing.*;

/** Expandable, wrapping source comparisons; all evidence is supplied before UI construction. */
public final class SourceDifferencesPanel extends JPanel {
  public SourceDifferencesPanel(List<SourceDifference> differences) {
    setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));setOpaque(false);setAlignmentX(LEFT_ALIGNMENT);
    setBorder(BorderFactory.createEmptyBorder(8,12,8,12));
    JButton toggle=new JButton("Source differences and evidence ("+differences.size()+") ▸");
    toggle.setAlignmentX(LEFT_ALIGNMENT);toggle.getAccessibleContext().setAccessibleName("Expand source differences and evidence");
    JPanel details=new JPanel();details.setLayout(new BoxLayout(details,BoxLayout.Y_AXIS));details.setOpaque(false);details.setAlignmentX(LEFT_ALIGNMENT);
    details.add(new WrappingText("Current values follow source precedence. Different scopes and list order are shown separately; missing coverage does not disprove a drop.","How to read source comparisons"));
    JPanel comparisons=new JPanel();comparisons.setOpaque(false);comparisons.setLayout(new BoxLayout(comparisons,BoxLayout.Y_AXIS));comparisons.setAlignmentX(LEFT_ALIGNMENT);
    details.add(comparisons);
    JButton more=new JButton("Show next 40 comparisons");more.setAlignmentX(LEFT_ALIGNMENT);more.setVisible(false);details.add(more);
    int[] shown={0};
    Runnable loadMore=()-> {
      int end=Math.min(differences.size(),shown[0]+40);
      for(int index=shown[0];index<end;index++) {
        var d=differences.get(index);
        var title=new WrappingText(d.topic()+" — "+d.classification(),"Source comparison");
        title.setFont(title.getFont().deriveFont(Font.BOLD));comparisons.add(Box.createVerticalStrut(8));comparisons.add(title);
        for(var assertion:d.assertions()) {
          String value=assertion.value();
          int json=aggregateStatsStart(d.topic(),value);
          String displayed=json<0?value:value.substring(0,json)+"Structured source row retained";
          comparisons.add(new WrappingText(assertion.source()+(assertion.selected()?" · selected value":"")+"\n"+displayed+"\n"+assertion.evidence(),"Source assertion"));
          if(json>=0) {
            JButton technical=new JButton("Show technical source evidence");technical.setAlignmentX(LEFT_ALIGNMENT);
            WrappingText payload=new WrappingText(value,"Technical source evidence");payload.setVisible(false);
            comparisons.add(technical);comparisons.add(payload);
            technical.addActionListener(e->{boolean show=!payload.isVisible();payload.setVisible(show);technical.setText((show?"Hide":"Show")+" technical source evidence");revalidate();repaint();});
          }
          comparisons.add(Box.createVerticalStrut(4));
        }
      }
      shown[0]=end;more.setVisible(end<differences.size());
      more.setText("Show next "+Math.min(40,differences.size()-end)+" comparisons ("+end+" of "+differences.size()+" shown)");
      revalidate();repaint();
    };
    more.addActionListener(e->loadMore.run());
    details.setVisible(false);add(toggle);add(details);
    toggle.addActionListener(e->{boolean expanded=!details.isVisible();if(expanded && shown[0]==0)loadMore.run();details.setVisible(expanded);toggle.setText("Source differences and evidence ("+differences.size()+") "+(expanded?"▾":"▸"));toggle.getAccessibleContext().setAccessibleName((expanded?"Collapse":"Expand")+" source differences and evidence");revalidate();repaint();});
  }
  private static int aggregateStatsStart(String topic,String value) {
    if(!topic.equalsIgnoreCase("stats") && !topic.equalsIgnoreCase("classic stats"))return -1;
    int object=value.indexOf('{'),array=value.indexOf('[');
    return object<0?array:array<0?object:Math.min(object,array);
  }
  @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
  @Override public Dimension getMinimumSize(){return new Dimension(0,getPreferredSize().height);}
}
