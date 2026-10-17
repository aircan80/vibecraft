package dev.wowforever.app.ui.journal;

import dev.wowforever.app.db.NpcPortraits;
import dev.wowforever.app.ui.pages.PageState;
import dev.wowforever.app.ui.pages.NpcPage;
import java.awt.*;
import javax.swing.*;

/** Journal-only preview; existing NPC information and navigation retain their own state. */
public final class JournalNpcPortrait extends JPanel implements PageState {
  private final NpcPage delegate;
  public JournalNpcPortrait(NpcPage npc,NpcPortraits.Portrait portrait) {
    super(new BorderLayout(0,8));delegate=npc;
    JPanel card=new JPanel(new BorderLayout(8,0));
    card.setBorder(BorderFactory.createEmptyBorder(8,16,8,16));
    JComponent image=new JComponent(){
      @Override public Dimension getPreferredSize(){return new Dimension(150,120);}
      @Override protected void paintComponent(Graphics g){
        super.paintComponent(g);int size=Math.max(1,Math.min(getWidth(),getHeight()));
        var copy=(Graphics2D)g.create();copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        copy.drawImage(portrait.image(),(getWidth()-size)/2,(getHeight()-size)/2,size,size,null);copy.dispose();
      }
    };
    image.getAccessibleContext();image.setToolTipText("Mechanical Squirrel · generated native model preview");
    card.add(image,BorderLayout.NORTH);
    JPanel text=new JPanel();text.setLayout(new BoxLayout(text,BoxLayout.Y_AXIS));text.setOpaque(false);
    text.add(new JLabel("Generated model preview"));text.add(new JLabel("Forever "+portrait.build()));
    text.add(new dev.wowforever.app.ui.widgets.WrappingText("Native bind pose · not an in-game screenshot","Preview description"));
    JToggleButton details=new JToggleButton("Source details");text.add(details);
    JTextArea evidence=new JTextArea(portrait.evidence(),8,30);evidence.setEditable(false);evidence.setLineWrap(true);evidence.setWrapStyleWord(true);
    JScrollPane source=new JScrollPane(evidence);source.setVisible(false);
    details.addActionListener(e->{source.setVisible(details.isSelected());revalidate();});
    text.add(source);card.add(text,BorderLayout.CENTER);add(card,BorderLayout.NORTH);add(npc,BorderLayout.CENTER);
  }
  public Object saveState(){return delegate.saveState();}
  public void restoreState(Object state){delegate.restoreState(state);}
}
