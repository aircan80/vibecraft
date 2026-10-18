package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.model.SourceDifference;
import java.awt.*;
import java.util.List;
import java.util.Locale;
import javax.swing.*;

/** Compact disclosure of already-loaded evidence; meaningful differences stay visible before disclosure. */
public final class SourceDifferencesPanel extends JPanel {
  public SourceDifferencesPanel(List<SourceDifference> differences) {
    List<SourceDifference> evidence=List.copyOf(differences);
    setLayout(new BorderLayout(8,0));setOpaque(false);setAlignmentX(LEFT_ALIGNMENT);
    setBorder(BorderFactory.createEmptyBorder(8,12,8,12));
    String summary=summary(evidence);
    var button=new SourceInfoButton("source comparisons",brief(evidence),()->details(evidence));
    button.setName("source-comparisons-info");add(button,BorderLayout.WEST);
    var text=copy(summary,"Visible source comparison status");text.setFont(text.getFont().deriveFont(18f));add(text);
  }
  private static String brief(List<SourceDifference> differences){
    var providers=differences.stream().flatMap(difference->difference.assertions().stream()).map(SourceDifference.Assertion::source).distinct().toList();
    String names=providers.stream().limit(3).map(name->name.length()>90?name.substring(0,87)+"…":name).collect(java.util.stream.Collectors.joining("; "));
    return (names.isBlank()?"Providers not recorded":names)+(providers.size()>3?"; +"+(providers.size()-3)+" more":"")+". "+summary(differences)+". Each provider keeps its own version and evidence.";
  }
  static String summary(List<SourceDifference> differences){
    var states=new java.util.LinkedHashSet<String>();
    for(var difference:differences){
      String state=difference.classification();
      if(state.contains("differ")||state.contains("Different")||state.contains("Incomplete")||state.contains("Single-source")||state.equals("No rate supplied"))states.add(state);
    }
    boolean comparison=differences.stream().anyMatch(d->d.topic().toLowerCase(Locale.ROOT).contains("classic stats")
        ||d.assertions().stream().anyMatch(a->a.source().toLowerCase(Locale.ROOT).contains("classic era")||a.source().equalsIgnoreCase("era")
            ||a.source().toLowerCase(Locale.ROOT).contains("vmangos")||a.evidence().contains("Vanilla rate (VMaNGOS)")));
    if(comparison)states.add("Includes other-edition comparison evidence");
    boolean unknown=differences.stream().flatMap(difference->difference.assertions().stream()).anyMatch(assertion->{String value=assertion.value().strip().toLowerCase(Locale.ROOT);return value.equals("unknown")||value.startsWith("unknown ·")||value.startsWith("not yet discovered");});
    if(unknown)states.add("Includes unknown source values");
    String count=differences.size()+" source comparison"+(differences.size()==1?"":"s");
    return count+(states.isEmpty()?" · retained evidence":" · "+String.join(" · ",states));
  }
  /** Builds the first bounded evidence batch only when the user opens details. No SQL or network work. */
  static JPanel details(List<SourceDifference> differences){
    JPanel details=new EvidenceBody();details.setLayout(new BoxLayout(details,BoxLayout.Y_AXIS));details.setOpaque(false);
    details.add(copy("Current values follow source precedence. Missing coverage does not disprove a drop; different rate scopes and list order remain distinct.","How to read source comparisons"));
    details.add(copy("Sources keep their own versions and snapshots. A build or refresh time not supplied by an assertion is not recorded here; a client build does not date an addon claim.","Source version and refresh coverage"));
    JPanel comparisons=new JPanel();comparisons.setOpaque(false);comparisons.setLayout(new BoxLayout(comparisons,BoxLayout.Y_AXIS));comparisons.setAlignmentX(LEFT_ALIGNMENT);details.add(comparisons);
    JButton more=new JButton("Show next 40 comparisons");more.setFont(more.getFont().deriveFont(18f));more.setAlignmentX(LEFT_ALIGNMENT);details.add(more);
    int[] shown={0};
    Runnable loadMore=()->{
      int end=Math.min(differences.size(),shown[0]+40);
      for(int index=shown[0];index<end;index++){
        var difference=differences.get(index);comparisons.add(Box.createVerticalStrut(12));
        var title=copy(difference.topic()+" — "+difference.classification(),"Source comparison");title.setFont(title.getFont().deriveFont(Font.BOLD));comparisons.add(title);
        for(var assertion:difference.assertions()){
          String value=assertion.value();int json=aggregateStatsStart(difference.topic(),value);
          String displayed=json<0?value:value.substring(0,json)+"Structured source row retained";
          comparisons.add(copy(assertion.source()+(assertion.selected()?" · selected value":"")+"\n"+displayed,"Source assertion"));
          var technical=new JButton("Technical evidence");technical.setFont(technical.getFont().deriveFont(18f));technical.setAlignmentX(LEFT_ALIGNMENT);
          var payload=copy(assertion.evidence()+(json<0?"":"\n"+value),"Technical source evidence");payload.setVisible(false);
          comparisons.add(technical);comparisons.add(payload);
          technical.addActionListener(event->{boolean show=!payload.isVisible();payload.setVisible(show);technical.setText(show?"Hide technical evidence":"Technical evidence");details.revalidate();details.repaint();});
          comparisons.add(Box.createVerticalStrut(8));
        }
      }
      shown[0]=end;more.setVisible(end<differences.size());more.setText("Show next "+Math.min(40,differences.size()-end)+" comparisons ("+end+" of "+differences.size()+" shown)");details.revalidate();details.repaint();
    };
    more.addActionListener(event->loadMore.run());loadMore.run();return details;
  }
  private static WrappingText copy(String text,String label){var copy=new WrappingText(text,label);copy.setFont(copy.getFont().deriveFont(18f));((javax.swing.text.DefaultCaret)copy.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);copy.setAlignmentX(LEFT_ALIGNMENT);return copy;}
  private static int aggregateStatsStart(String topic,String value){if(!topic.equalsIgnoreCase("stats")&&!topic.equalsIgnoreCase("classic stats"))return -1;int object=value.indexOf('{'),array=value.indexOf('[');return object<0?array:array<0?object:Math.min(object,array);}
  @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
  @Override public Dimension getMinimumSize(){return new Dimension(0,getPreferredSize().height);}
  private static final class EvidenceBody extends JPanel implements Scrollable {
    @Override public Dimension getPreferredScrollableViewportSize(){return new Dimension(620,500);}
    @Override public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 24;}
    @Override public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(24,r.height-24);}
    @Override public boolean getScrollableTracksViewportWidth(){return true;}
    @Override public boolean getScrollableTracksViewportHeight(){return false;}
  }
}
