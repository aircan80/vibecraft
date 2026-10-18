package dev.wowforever.app.ui.journal;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.db.NpcPortraits;
import dev.wowforever.app.portrait.NpcPortraitProvider.*;
import dev.wowforever.app.portrait.NpcPortraitRepository;
import dev.wowforever.app.ui.pages.NpcPage;
import dev.wowforever.app.ui.pages.PageState;
import dev.wowforever.app.ui.theme.Fonts;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.WrappingText;
import dev.wowforever.app.ui.widgets.SourceInfoButton;
import dev.wowforever.app.ui.widgets.SourceEvidencePanel;
import java.util.List;
import java.awt.*;
import java.util.concurrent.Executor;
import javax.swing.*;

/** Ordinary NPC images, loaded independently of navigation and database work. */
public final class JournalNpcPortrait extends JPanel implements PageState {
  private final NpcPage delegate;
  private final JPanel slot=new JPanel(new BorderLayout());
  private final NpcPortraitRepository repository;
  private final Key key;
  private JToggleButton details;
  private boolean loaded,disposed,pendingDetails;
  private int generation;
  private record State(Object npc,boolean details) {}

  public JournalNpcPortrait(NpcPage npc,Key key,NpcPortraitRepository repository) {
    super(new BorderLayout(0,8));this.delegate=npc;this.key=key;this.repository=repository;
    slot.setOpaque(false);add(slot,BorderLayout.NORTH);add(npc,BorderLayout.CENTER);load();
  }
  /** Compatibility for callers that already hold a verified, decoded image. */
  public JournalNpcPortrait(NpcPage npc,NpcPortraits.Portrait portrait) {
    super(new BorderLayout(0,8));delegate=npc;key=null;repository=null;
    slot.setOpaque(false);add(slot,BorderLayout.NORTH);add(npc,BorderLayout.CENTER);
    install(new Portrait(portrait.image(),new Key("wow_classic_beta",portrait.build(),2671),
        "World of Warcraft artwork © Blizzard Entertainment",portrait.evidence()));loaded=true;
  }
  public JournalNpcPortrait(NpcPage npc,NpcPortraits.Portrait portrait,long npcId,Executor worker) {this(npc,portrait);}

  private void load(){
    int token=++generation;slot.removeAll();slot.setBorder(BorderFactory.createEmptyBorder(2,18,2,18));
    JLabel loading=new JLabel("Loading portrait…");loading.setForeground(Theme.current().textMuted());
    loading.setPreferredSize(new Dimension(0,UIScale.scale(36)));slot.add(loading);refresh();
    repository.load(key).whenComplete((result,error)->SwingUtilities.invokeLater(()->{
      if(disposed||token!=generation)return;loaded=true;
      if(error==null&&result.status()==Status.AVAILABLE)install(result.portrait());
      else missing(error==null?result.message():"The portrait source could not be read.");
    }));
  }
  private void missing(String reason){
    slot.removeAll();slot.setBorder(BorderFactory.createEmptyBorder(2,18,2,18));
    details=new JToggleButton("Portrait unavailable ▸");details.setHorizontalAlignment(SwingConstants.LEADING);
    details.setPreferredSize(new Dimension(0,UIScale.scale(36)));details.getAccessibleContext().setAccessibleName("Portrait unavailable");
    slot.add(details,BorderLayout.NORTH);
    var explanation=new WrappingText("No verified portrait is available for this game version.\n"+key.product()+" · "+key.build()+"\n"+reason,"Portrait availability");
    explanation.setBorder(BorderFactory.createEmptyBorder(8,0,8,0));explanation.setVisible(pendingDetails);slot.add(explanation);
    details.setSelected(pendingDetails);details.setText(pendingDetails?"Portrait unavailable ▾":"Portrait unavailable ▸");
    details.addActionListener(e->{pendingDetails=details.isSelected();details.setText(pendingDetails?"Portrait unavailable ▾":"Portrait unavailable ▸");explanation.setVisible(pendingDetails);refresh();});refresh();
  }
  private void install(Portrait portrait){
    slot.removeAll();slot.setBorder(BorderFactory.createEmptyBorder());
    JPanel card=new JPanel(new PortraitLayout());card.setOpaque(false);card.setBorder(BorderFactory.createEmptyBorder(8,18,12,18));
    JComponent image=new JComponent(){
      @Override public Dimension getPreferredSize(){return new Dimension(UIScale.scale(220),UIScale.scale(220));}
      @Override protected void paintComponent(Graphics g){
        super.paintComponent(g);double scale=Math.min((double)getWidth()/portrait.image().getWidth(),(double)getHeight()/portrait.image().getHeight());
        int w=(int)(portrait.image().getWidth()*scale),h=(int)(portrait.image().getHeight()*scale);
        Graphics2D copy=(Graphics2D)g.create();copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        copy.drawImage(portrait.image(),(getWidth()-w)/2,(getHeight()-h)/2,w,h,null);copy.dispose();
      }
    };
    image.setToolTipText("NPC portrait");image.setName("npc-portrait-image");card.add(image);
    JPanel text=new JPanel(new JournalSurface.Stack());text.setOpaque(false);
    JPanel heading=new JPanel(new BorderLayout(8,0));heading.setOpaque(false);
    JLabel title=new JLabel("Portrait");title.setFont(Fonts.heading(UIScale.scale(23f)));heading.add(title,BorderLayout.CENTER);
    var sourceInfo=new SourceInfoButton("NPC portrait #"+portrait.key().npcId(),portrait.source()+" · "+portrait.key().product()+" · "+portrait.key().build(),()->new SourceEvidencePanel(List.of(
        new SourceEvidencePanel.Fact("Source / credit",portrait.source()),
        new SourceEvidencePanel.Fact("Game product",portrait.key().product()),
        new SourceEvidencePanel.Fact("Build",portrait.key().build()),
        new SourceEvidencePanel.Fact("Applies to","Static portrait for NPC #"+portrait.key().npcId()),
        new SourceEvidencePanel.Fact("Refreshed",null)),
        "This evidence qualifies the portrait image only; it does not establish this NPC's other fields.",portrait.evidence()));
    sourceInfo.setName("npc-portrait-source-info");heading.add(sourceInfo,BorderLayout.EAST);text.add(heading);text.add(Box.createVerticalStrut(8));
    JLabel build=new JLabel("Client build "+portrait.key().build());build.setForeground(Theme.current().textMuted());text.add(build);text.add(Box.createVerticalStrut(6));
    text.add(new WrappingText(portrait.source(),"Portrait source"));text.add(Box.createVerticalStrut(8));
    details=null;pendingDetails=false;
    card.add(text);slot.add(card);refresh();
  }
  private void refresh(){slot.revalidate();slot.repaint();revalidate();repaint();}
  @Override public void removeNotify(){disposed=true;generation++;super.removeNotify();}
  @Override public void addNotify(){boolean retry=disposed&&!loaded;disposed=false;super.addNotify();if(retry)load();}
  @Override public Object saveState(){return new State(delegate.saveState(),details==null?pendingDetails:details.isSelected());}
  @Override public void restoreState(Object value){
    if(!(value instanceof State state)){delegate.restoreState(value);return;}delegate.restoreState(state.npc());pendingDetails=state.details();
    if(details!=null&&details.isSelected()!=pendingDetails)details.doClick();
  }
  private static final class PortraitLayout implements LayoutManager {
    public void addLayoutComponent(String n,Component c){}public void removeLayoutComponent(Component c){}
    public Dimension minimumLayoutSize(Container p){return new Dimension(0,preferredLayoutSize(p).height);}
    public Dimension preferredLayoutSize(Container p){return arrange(p,false);}public void layoutContainer(Container p){arrange(p,true);}
    private Dimension arrange(Container p,boolean layout){
      Insets in=p.getInsets();int width=p.getWidth()>0?p.getWidth():UIScale.scale(800),available=Math.max(0,width-in.left-in.right),gap=UIScale.scale(20);
      boolean wide=available>=UIScale.scale(580);Component image=p.getComponent(0),text=p.getComponent(1);Dimension natural=image.getPreferredSize();
      int size=Math.min(available,natural.width),imageHeight=natural.height,tw=wide?available-size-gap:available;
      text.setSize(Math.max(1,tw),Math.max(1,text.getHeight()));if(text instanceof Container c)c.doLayout();int th=text.getPreferredSize().height;
      if(layout){image.setBounds(in.left,in.top,size,imageHeight);text.setBounds(wide?in.left+size+gap:in.left,wide?in.top+(Math.max(imageHeight,th)-th)/2:in.top+imageHeight+gap,tw,th);}
      return new Dimension(width,(wide?Math.max(imageHeight,th):imageHeight+gap+th)+in.top+in.bottom);
    }
  }
}
