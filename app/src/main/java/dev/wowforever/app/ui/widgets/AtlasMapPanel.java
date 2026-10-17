package dev.wowforever.app.ui.widgets;
import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.Navigator;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
/** Genuine Atlas art, with no app-created pins or inferred positions. */
public final class AtlasMapPanel extends JPanel {
 public static final String CREDIT="Map: Atlas (GPL-2.0) by Dan Gilbert, Lothaer, Arith Hsu and the Atlas Team";
 public AtlasMapPanel(List<InstanceFloorMap> maps, Navigator nav, boolean compact) {
  super(new BorderLayout(0,6)); setOpaque(false);
  if(maps.isEmpty()) { add(new JLabel("No floor map for this dungeon yet.")); return; }
  JTabbedPane tabs=new JTabbedPane();
  for(var map:maps) tabs.addTab(map.label(),floor(map,nav,compact));
  if(compact) {
   JToggleButton expand=new JToggleButton("Atlas maps ("+maps.size()+")");
   tabs.setVisible(false); expand.addActionListener(e->{tabs.setVisible(expand.isSelected()); revalidate();});
   add(expand,BorderLayout.NORTH);
  }
  add(tabs,BorderLayout.CENTER);
 }
 private JComponent floor(InstanceFloorMap map,Navigator nav,boolean compact) {
  JPanel panel=new JPanel(new BorderLayout(6,6)); panel.setOpaque(false);
  Canvas art=new Canvas(map.jpeg()); JScrollPane scroll=new JScrollPane(art);
  scroll.setPreferredSize(new Dimension(560,compact?280:420));
  JPanel legend=new JPanel();legend.setOpaque(false);legend.setLayout(new BoxLayout(legend,BoxLayout.Y_AXIS));
  legend.add(new JLabel("Atlas legend"));
  for(var entry:map.legend()) {
   String text=(entry.number().isBlank()?"":entry.number()+") ")+entry.name();
   JComponent row=entry.npcId()==null ? new JLabel(text) : LinkButton.create(text,new EntityRef(EntityKind.NPC,entry.npcId()),nav);
   row.setToolTipText(text); legend.add(row);
  }
  JPanel content=new JPanel(new BorderLayout(6,6));content.setOpaque(false);content.add(scroll,BorderLayout.CENTER);
  content.add(legend,compact?BorderLayout.SOUTH:BorderLayout.EAST);panel.add(content,BorderLayout.CENTER);
  JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0)); controls.setOpaque(false);
  JButton minus=new JButton("−"),plus=new JButton("+"),reset=new JButton("100%");
  minus.setToolTipText("Zoom out");plus.setToolTipText("Zoom in");
  minus.addActionListener(e->art.zoom(.8)); plus.addActionListener(e->art.zoom(1.25));reset.addActionListener(e->art.reset());
  controls.add(minus);controls.add(plus);controls.add(reset); panel.add(controls,BorderLayout.NORTH);
  JTextArea caption=new JTextArea(CREDIT+(map.forever()?" · drawn for the Forever beta":""));
  caption.setLineWrap(true);caption.setWrapStyleWord(true);caption.setEditable(false);caption.setOpaque(false);caption.setRows(2);
  panel.add(caption,BorderLayout.SOUTH);return panel;
 }
 private static final class Canvas extends JPanel {
  private final BufferedImage image; private double scale=1; private Point pressed;
  Canvas(byte[] bytes) {
   try {image=ImageIO.read(new ByteArrayInputStream(bytes)); if(image==null)throw new IllegalArgumentException("Unreadable Atlas image");}
   catch(java.io.IOException e){throw new IllegalArgumentException("Unreadable Atlas image",e);}
   setFocusable(true);getAccessibleContext().setAccessibleName("Atlas floor map; use zoom controls and scroll bars to explore");
   MouseAdapter drag=new MouseAdapter(){public void mousePressed(MouseEvent e){pressed=e.getPoint();}public void mouseDragged(MouseEvent e){
    if(pressed==null)return;var view=(JViewport)getParent();Point p=view.getViewPosition();p.translate(pressed.x-e.getX(),pressed.y-e.getY());
    p.x=Math.max(0,Math.min(p.x,Math.max(0,getWidth()-view.getWidth())));p.y=Math.max(0,Math.min(p.y,Math.max(0,getHeight()-view.getHeight())));view.setViewPosition(p);
   }};addMouseListener(drag);addMouseMotionListener(drag);
  }
  void zoom(double factor){scale=Math.max(.25,Math.min(3,scale*factor));revalidate();repaint();}
  void reset(){scale=1;revalidate();repaint();}
  @Override public Dimension getPreferredSize(){return new Dimension((int)(image.getWidth()*scale),(int)(image.getHeight()*scale));}
  @Override protected void paintComponent(Graphics g){super.paintComponent(g);g.drawImage(image,0,0,getPreferredSize().width,getPreferredSize().height,null);}
 }
}
