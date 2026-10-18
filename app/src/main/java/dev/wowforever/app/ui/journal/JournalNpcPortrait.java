package dev.wowforever.app.ui.journal;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.db.NpcPortraits;
import dev.wowforever.app.db.NpcTurntables;
import java.util.concurrent.Executor;
import dev.wowforever.app.ui.pages.PageState;
import dev.wowforever.app.ui.pages.NpcPage;
import dev.wowforever.app.ui.theme.Fonts;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.*;
import javax.swing.*;

/** Compact generated preview beside its caption; original NPC content retains its own state. */
public final class JournalNpcPortrait extends JPanel implements PageState {
  private final NpcPage delegate;
  private JToggleButton rotate;
  private JournalTurntable turntable;
  private int pendingFrame=JournalTurntable.DEFAULT_FRAME;
  private boolean loading;
  private record State(Object npc,boolean rotating,int frame) {}
  public JournalNpcPortrait(NpcPage npc,NpcPortraits.Portrait portrait) {
    this(npc,portrait,-1,Runnable::run);
  }
  public JournalNpcPortrait(NpcPage npc,NpcPortraits.Portrait portrait,long npcId,Executor worker) {
    super(new BorderLayout(0,8));delegate=npc;
    JPanel card=new JPanel(new PortraitLayout());card.setOpaque(false);
    card.setBorder(BorderFactory.createEmptyBorder(8,18,12,18));
    JComponent image=new JComponent(){
      @Override public Dimension getPreferredSize(){return new Dimension(UIScale.scale(220),UIScale.scale(220));}
      @Override protected void paintComponent(Graphics g){
        super.paintComponent(g);double scale=Math.min((double)getWidth()/portrait.image().getWidth(),(double)getHeight()/portrait.image().getHeight());
        int w=(int)(portrait.image().getWidth()*scale),h=(int)(portrait.image().getHeight()*scale);
        var copy=(Graphics2D)g.create();copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        copy.drawImage(portrait.image(),(getWidth()-w)/2,(getHeight()-h)/2,w,h,null);copy.dispose();
      }
    };
    image.setToolTipText("Generated native model preview");
    JPanel preview=new JPanel(new BorderLayout());preview.setOpaque(false);preview.add(image);card.add(preview);
    JPanel text=new JPanel(new JournalSurface.Stack());text.setOpaque(false);
    JLabel title=new JLabel("Generated model preview");title.setFont(Fonts.heading(UIScale.scale(23f)));text.add(title);text.add(Box.createVerticalStrut(8));
    JLabel build=new JLabel("Forever "+portrait.build());build.setForeground(Theme.current().textMuted());text.add(build);text.add(Box.createVerticalStrut(6));
    text.add(new dev.wowforever.app.ui.widgets.WrappingText("Native bind pose · not an in-game screenshot","Preview description"));text.add(Box.createVerticalStrut(8));
    JToggleButton details=new JToggleButton("Source details");text.add(details);
    JTextArea evidence=new JTextArea(portrait.evidence(),6,30);evidence.setEditable(false);evidence.setLineWrap(true);evidence.setWrapStyleWord(true);
    JScrollPane source=new JScrollPane(evidence);source.setVisible(false);
    details.addActionListener(e->{source.setVisible(details.isSelected());card.revalidate();revalidate();repaint();});
    text.add(source);
    if(NpcTurntables.supports(npcId,portrait.build())) {
      text.add(Box.createVerticalStrut(10));rotate=new JToggleButton("Rotating preview");text.add(rotate);
      JLabel progress=new JLabel();progress.setForeground(Theme.current().textMuted());text.add(progress);
      Runnable show=() -> {
        preview.removeAll();
        if(rotate.isSelected()&&turntable!=null)preview.add(turntable);
        else {if(turntable!=null)turntable.pause();preview.add(image);}
        card.revalidate();card.repaint();revalidate();repaint();
      };
      rotate.addActionListener(e -> {
        if(!rotate.isSelected()){show.run();return;}
        if(turntable!=null){show.run();return;}
        if(loading)return;loading=true;progress.setText("Loading rotating preview…");
        try { worker.execute(() -> {
          var loaded=NpcTurntables.load(npcId,portrait.build());
          SwingUtilities.invokeLater(() -> {
            loading=false;
            if(loaded.isEmpty()){
              progress.setText("Rotating preview unavailable: verified frames could not be loaded.");
              rotate.setSelected(false);rotate.setEnabled(false);show.run();return;
            }
            turntable=new JournalTurntable(loaded.orElseThrow());turntable.setFrame(pendingFrame);
            evidence.setText(portrait.evidence()+"\n\nTurntable provenance:\n"+loaded.orElseThrow().evidence());evidence.setCaretPosition(0);
            progress.setText("");show.run();
          });
        }); } catch(RuntimeException failure){loading=false;progress.setText("Rotating preview could not be started.");rotate.setSelected(false);show.run();}
      });
    }
    card.add(text);add(card,BorderLayout.NORTH);add(npc,BorderLayout.CENTER);
  }
  private static final class PortraitLayout implements LayoutManager {
    public void addLayoutComponent(String n,Component c){}public void removeLayoutComponent(Component c){}
    public Dimension minimumLayoutSize(Container p){return new Dimension(0,preferredLayoutSize(p).height);}
    public Dimension preferredLayoutSize(Container p){return arrange(p,false);}
    public void layoutContainer(Container p){arrange(p,true);}
    private Dimension arrange(Container p,boolean layout){
      Insets in=p.getInsets();int width=p.getWidth()>0?p.getWidth():UIScale.scale(800),available=Math.max(0,width-in.left-in.right),gap=UIScale.scale(20);
      boolean wide=available>=UIScale.scale(580);Component image=p.getComponent(0),text=p.getComponent(1);
      Dimension natural=image.getPreferredSize();int size=Math.min(available,natural.width),imageHeight=natural.height;
      int tw=wide?available-size-gap:available;
      text.setSize(Math.max(1,tw),Math.max(1,text.getHeight()));if(text instanceof Container c)c.doLayout();int th=text.getPreferredSize().height;
      if(layout){image.setBounds(in.left,in.top,size,imageHeight);text.setBounds(wide?in.left+size+gap:in.left,wide?in.top+(Math.max(imageHeight,th)-th)/2:in.top+imageHeight+gap,tw,th);}
      return new Dimension(width,(wide?Math.max(imageHeight,th):imageHeight+gap+th)+in.top+in.bottom);
    }
  }
  public Object saveState(){return new State(delegate.saveState(),rotate!=null&&rotate.isSelected(),turntable==null?pendingFrame:turntable.frameIndex());}
  public void restoreState(Object value){
    if(!(value instanceof State state)){delegate.restoreState(value);return;}
    delegate.restoreState(state.npc());pendingFrame=state.frame();if(turntable!=null)turntable.setFrame(pendingFrame);
    if(rotate!=null&&rotate.isSelected()!=state.rotating())rotate.doClick();
  }
}
