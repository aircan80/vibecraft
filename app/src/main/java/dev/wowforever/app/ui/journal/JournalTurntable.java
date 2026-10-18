package dev.wowforever.app.ui.journal;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.db.NpcTurntables;
import dev.wowforever.app.ui.widgets.WrappingText;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/** Rotates among verified rendered views; does not claim animated or real-time 3D rendering. */
public final class JournalTurntable extends JPanel {
  public static final int DEFAULT_FRAME=10;
  private final NpcTurntables.Turntable data;
  private final JSlider angle=new JSlider(0,NpcTurntables.FRAME_COUNT-1,DEFAULT_FRAME);
  private final JLabel view=new JLabel();
  private final JToggleButton play=new JToggleButton("Play rotation");
  private final Timer timer;
  private final JComponent canvas;
  public JournalTurntable(NpcTurntables.Turntable data) {this(data,"Mechanical Squirrel");}
  public JournalTurntable(NpcTurntables.Turntable data,String npcName) {
    super(new BorderLayout(0,6));this.data=data;setOpaque(false);
    canvas=new JComponent(){
      {setFocusable(true);setName("npc-turntable-canvas");setToolTipText("Drag to rotate; Left/Right to turn; Home to reset");}
      @Override public javax.accessibility.AccessibleContext getAccessibleContext(){
        if(accessibleContext==null)accessibleContext=new AccessibleJComponent(){};return accessibleContext;
      }
      @Override public Dimension getPreferredSize(){return new Dimension(UIScale.scale(260),UIScale.scale(220));}
      @Override protected void paintComponent(Graphics g){
        super.paintComponent(g);var image=data.frame(frameIndex());double scale=Math.min((double)getWidth()/image.getWidth(),(double)getHeight()/image.getHeight());
        int w=(int)(image.getWidth()*scale),h=(int)(image.getHeight()*scale);var copy=(Graphics2D)g.create();
        copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        copy.drawImage(image,(getWidth()-w)/2,(getHeight()-h)/2,w,h,null);
        if(hasFocus()){copy.setColor(getForeground());copy.drawRect(1,1,Math.max(0,getWidth()-3),Math.max(0,getHeight()-3));}copy.dispose();
      }
    };
    canvas.getAccessibleContext().setAccessibleName(npcName+" · 12-view bind-pose preview");
    canvas.setBorder(BorderFactory.createEmptyBorder());add(canvas,BorderLayout.CENTER);
    MouseAdapter drag=new MouseAdapter(){int startX,startFrame;boolean dragging;
      public void mousePressed(MouseEvent e){dragging=true;startX=e.getX();startFrame=frameIndex();pause();canvas.requestFocusInWindow();}
      public void mouseReleased(MouseEvent e){dragging=false;}
      public void mouseDragged(MouseEvent e){if(dragging)setFrame(startFrame+Math.round((e.getX()-startX)/(float)UIScale.scale(28)));}
    };canvas.addMouseListener(drag);canvas.addMouseMotionListener(drag);
    bind("LEFT","turn-left",()->setFrame(frameIndex()-1));bind("RIGHT","turn-right",()->setFrame(frameIndex()+1));bind("HOME","reset-view",()->setFrame(DEFAULT_FRAME));
    JPanel controls=new JPanel(new JournalSurface.Stack());controls.setOpaque(false);
    JPanel buttons=new JPanel(new GridLayout(0,2,4,4));buttons.setOpaque(false);
    JButton left=new JButton("Rotate left"),right=new JButton("Rotate right"),reset=new JButton("Reset view");
    left.addActionListener(e->{pause();setFrame(frameIndex()-1);});right.addActionListener(e->{pause();setFrame(frameIndex()+1);});reset.addActionListener(e->{pause();setFrame(DEFAULT_FRAME);});
    buttons.add(left);buttons.add(right);buttons.add(reset);controls.add(buttons);
    angle.getAccessibleContext().setAccessibleName("Model view angle");angle.setOpaque(false);angle.setMajorTickSpacing(1);angle.setPaintTicks(true);
    angle.addChangeListener(e->{view.setText((frameIndex()*30)+"° · view "+(frameIndex()+1)+" of 12");canvas.repaint();});controls.add(angle);
    JPanel playback=new JPanel(new JournalSurface.Stack());playback.setOpaque(false);playback.add(play);playback.add(view);controls.add(playback);
    controls.add(new WrappingText("12 rendered views · 30° steps · native bind pose, no animation","Turntable limitations"));add(controls,BorderLayout.SOUTH);
    timer=new Timer(180,e->setFrame(frameIndex()+1));
    play.addActionListener(e->{if(play.isSelected()&&isShowing())timer.start();else timer.stop();});
    addHierarchyListener(e->{if((e.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)!=0&&!isShowing())pause();});
    view.setText((frameIndex()*30)+"° · view "+(frameIndex()+1)+" of 12");
  }
  private void bind(String key,String name,Runnable action){canvas.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key),name);canvas.getActionMap().put(name,new AbstractAction(){public void actionPerformed(ActionEvent e){pause();action.run();}});}
  public int frameIndex(){return angle.getValue();}
  public void setFrame(int frame){angle.setValue(Math.floorMod(frame,NpcTurntables.FRAME_COUNT));}
  public void pause(){timer.stop();play.setSelected(false);}
  @Override public void removeNotify(){pause();super.removeNotify();}
  @Override public Dimension getPreferredSize(){Dimension d=super.getPreferredSize();return new Dimension(UIScale.scale(300),d.height);}
}
