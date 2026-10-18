package dev.wowforever.app.ui.journal;

import com.google.gson.GsonBuilder;
import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.db.NpcTurntables;
import dev.wowforever.app.model.NpcAppearance;
import dev.wowforever.app.preview.NpcPreviewService;
import dev.wowforever.app.ui.pages.NpcPage;
import dev.wowforever.app.ui.pages.PageState;
import dev.wowforever.app.ui.theme.Fonts;
import dev.wowforever.app.ui.widgets.WrappingText;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/** Build-exact, explicitly requested local previews; unrelated navigation stays responsive while rendering. */
public final class JournalNpcPreview extends JPanel implements PageState {
  private final NpcPage delegate;
  private final NpcAppearance appearance;
  private final NpcPreviewService service;
  private final JComboBox<String> variants=new JComboBox<>();
  private final JButton generate=new JButton("Generate preview");
  private final WrappingText status=new WrappingText("","Model preview status");
  private final JPanel visual=new JPanel(new BorderLayout()),card=new JPanel(new Responsive());
  private final JToggleButton rotating=new JToggleButton("Rotating preview");
  private final JTextArea evidence=new JTextArea(6,32);
  private JournalTurntable turntable;
  private JComponent portrait;
  private NpcPreviewService.Request current;
  private int generation,pendingFrame=JournalTurntable.DEFAULT_FRAME;
  private boolean disposed,pendingRotation;
  private record State(Object npc,int variant,boolean rotating,int frame) {}

  public JournalNpcPreview(NpcPage npc,NpcAppearance appearance,NpcPreviewService service) {
    super(new BorderLayout(0,8));this.delegate=npc;this.appearance=appearance;this.service=service;
    card.setOpaque(false);card.setBorder(BorderFactory.createEmptyBorder(8,18,12,18));visual.setOpaque(false);visual.setVisible(false);card.add(visual);
    JPanel text=new JPanel(new JournalSurface.Stack());text.setOpaque(false);
    JLabel title=new JLabel("Model preview");title.setFont(Fonts.heading(UIScale.scale(23f)));text.add(title);text.add(Box.createVerticalStrut(6));
    text.add(new WrappingText(appearance.build()+" · exact native appearance evidence","Appearance build"));text.add(Box.createVerticalStrut(8));
    if(appearance.variants().size()>1)variants.addItem("Select a display variant…");
    for(var v:appearance.variants())variants.addItem("Display "+v.displayId()+" · slot "+(v.displaySlot()+1));
    variants.getAccessibleContext().setAccessibleName("Display variant");
    if(appearance.variants().size()>1){text.add(variants);text.add(Box.createVerticalStrut(8));}
    text.add(status);text.add(Box.createVerticalStrut(8));text.add(generate);text.add(Box.createVerticalStrut(8));
    rotating.setVisible(false);text.add(rotating);text.add(Box.createVerticalStrut(8));
    JToggleButton details=new JToggleButton("Appearance & render evidence");text.add(details);
    evidence.setEditable(false);evidence.setLineWrap(true);evidence.setWrapStyleWord(true);
    JScrollPane source=new JScrollPane(evidence);source.setVisible(false);text.add(source);
    details.addActionListener(e->{source.setVisible(details.isSelected());refresh();});
    card.add(text);add(card,BorderLayout.NORTH);add(npc,BorderLayout.CENTER);
    evidence.setText(new GsonBuilder().setPrettyPrinting().create().toJson(appearance));evidence.setCaretPosition(0);
    variants.addActionListener(e->select());generate.addActionListener(e->render());
    rotating.addActionListener(e->showPreview());select();
  }
  private NpcAppearance.Variant selected(){int index=variants.getSelectedIndex()-(appearance.variants().size()>1?1:0);return index>=0&&index<appearance.variants().size()?appearance.variants().get(index):null;}
  private void select(){
    int token=++generation;current=null;visual.setVisible(false);rotating.setVisible(false);rotating.setSelected(false);
    if(turntable!=null)turntable.pause();turntable=null;portrait=null;generate.setText("Generate preview");generate.setEnabled(false);
    var variant=selected();
    if(variant==null){status.setText(appearance.variants().isEmpty()?appearance.message():"Choose a recorded display variant before generating a preview.");refresh();return;}
    if(!variant.mapped()){status.setText(variant.reason());refresh();return;}
    current=new NpcPreviewService.Request(appearance.product(),appearance.build(),appearance.npcId(),variant.displayId(),variant.modelId(),variant.modelFileDataId(),variant.textures(),variant.displayScale(),variant.modelScale(),variant.appearanceKey(),appearance.catalogId(),new GsonBuilder().create().toJson(variant.evidence()));
    var pending=service.pending(current);
    if(pending.isPresent()){follow(pending.orElseThrow(),token);return;}
    status.setText("Checking the local preview cache…");var request=current;
    service.cached(request).whenComplete((cached,error)->SwingUtilities.invokeLater(()->{
      if(disposed||token!=generation)return;
      if(error==null&&cached.isPresent()){install(cached.orElseThrow());return;}
      var active=service.pending(request);if(active.isPresent()){follow(active.orElseThrow(),token);return;}
      status.setText("Generate a local model preview. The first render may take a few minutes; you can keep browsing.");generate.setEnabled(true);refresh();
    }));refresh();
  }
  private void render(){if(current!=null)follow(service.generate(current),generation);}
  private void follow(java.util.concurrent.CompletableFuture<NpcPreviewService.Preview> future,int token){
    generate.setEnabled(false);status.setText("Preview queued or rendering locally… You can keep browsing.");refresh();
    future.whenComplete((preview,error)->SwingUtilities.invokeLater(()->{
      if(disposed||token!=generation)return;
      if(error!=null){
        status.setText("This appearance could not be rendered. Check the render evidence for details, or retry.");generate.setText("Retry preview");generate.setEnabled(true);
        Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();
        evidence.append("\n\nRender attempt: "+cause.getMessage());refresh();return;
      }
      install(preview);
    }));
  }
  private void install(NpcPreviewService.Preview preview){
    portrait=new Image(preview.portrait());
    turntable=new JournalTurntable(new NpcTurntables.Turntable(preview.frames(),preview.build(),preview.evidence()),appearance.name()==null?"NPC #"+appearance.npcId():appearance.name());
    turntable.setFrame(pendingFrame);rotating.setVisible(true);rotating.setSelected(pendingRotation);
    generate.setText("Preview cached");generate.setEnabled(false);
    status.setText("Generated from this build’s model data · native bind pose, not an in-game screenshot.");
    evidence.setText(new GsonBuilder().setPrettyPrinting().create().toJson(appearance)+"\n\nVerified render manifest:\n"+preview.evidence());evidence.setCaretPosition(0);showPreview();
  }
  private void showPreview(){visual.removeAll();if(portrait==null)return;
    if(rotating.isSelected())visual.add(turntable);else{turntable.pause();visual.add(portrait);}visual.setVisible(true);refresh();
  }
  private void refresh(){card.revalidate();card.repaint();revalidate();repaint();}
  @Override public void addNotify(){
    boolean reattaching=disposed;disposed=false;super.addNotify();
    if(reattaching&&turntable==null)select();
  }
  @Override public void removeNotify(){disposed=true;generation++;if(turntable!=null)turntable.pause();super.removeNotify();}
  @Override public Object saveState(){return new State(delegate.saveState(),variants.getSelectedIndex(),rotating.isSelected(),turntable==null?pendingFrame:turntable.frameIndex());}
  @Override public void restoreState(Object value){
    if(!(value instanceof State s)){delegate.restoreState(value);return;}delegate.restoreState(s.npc());pendingRotation=s.rotating();pendingFrame=s.frame();
    if(s.variant()>=0&&s.variant()<variants.getItemCount()&&s.variant()!=variants.getSelectedIndex())variants.setSelectedIndex(s.variant());
    if(turntable!=null){turntable.setFrame(pendingFrame);rotating.setSelected(pendingRotation);showPreview();}
  }
  private static final class Image extends JComponent {
    private final BufferedImage image;Image(BufferedImage image){this.image=image;setToolTipText("Generated native bind-pose preview");}
    public Dimension getPreferredSize(){return new Dimension(UIScale.scale(240),UIScale.scale(220));}
    protected void paintComponent(Graphics g){super.paintComponent(g);double s=Math.min((double)getWidth()/image.getWidth(),(double)getHeight()/image.getHeight());int w=(int)(image.getWidth()*s),h=(int)(image.getHeight()*s);Graphics2D copy=(Graphics2D)g.create();copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);copy.drawImage(image,(getWidth()-w)/2,(getHeight()-h)/2,w,h,null);copy.dispose();}
  }
  private static final class Responsive implements LayoutManager {
    public void addLayoutComponent(String n,Component c){}public void removeLayoutComponent(Component c){}
    public Dimension preferredLayoutSize(Container p){return arrange(p,false);}public Dimension minimumLayoutSize(Container p){return new Dimension(0,preferredLayoutSize(p).height);}public void layoutContainer(Container p){arrange(p,true);}
    private Dimension arrange(Container p,boolean layout){
      Insets in=p.getInsets();int width=p.getWidth()>0?p.getWidth():UIScale.scale(800),available=Math.max(0,width-in.left-in.right),gap=UIScale.scale(20);Component image=p.getComponent(0),text=p.getComponent(1);
      boolean has=image.isVisible(),wide=has&&available>=UIScale.scale(580);Dimension natural=has?image.getPreferredSize():new Dimension();int iw=Math.min(available,natural.width),ih=natural.height,tw=wide?Math.max(0,available-iw-gap):available;
      text.setSize(Math.max(1,tw),Math.max(1,text.getHeight()));if(text instanceof Container nested)nested.doLayout();int th=text.getPreferredSize().height;
      if(layout){if(has)image.setBounds(in.left,in.top,iw,ih);text.setBounds(wide?in.left+iw+gap:in.left,in.top+(wide||!has?0:ih+gap),tw,th);}
      return new Dimension(width,in.top+in.bottom+(wide?Math.max(ih,th):th+(has?ih+gap:0)));
    }
  }
}
