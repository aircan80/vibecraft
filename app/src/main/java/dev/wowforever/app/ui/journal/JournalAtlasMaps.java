package dev.wowforever.app.ui.journal;

import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.StatusSink;
import dev.wowforever.app.ui.widgets.AtlasMapPanel;
import dev.wowforever.app.ui.widgets.LinkButton;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Journal floor maps decode on the page worker, never during Swing construction or painting. */
final class JournalAtlasMaps extends JPanel {
  private record Decoded(InstanceFloorMap floor,BufferedImage image) {}
  JournalAtlasMaps(List<InstanceFloorMap> floors,Navigator nav,Executor worker,StatusSink status) {
    super(new BorderLayout());setOpaque(false);
    add(new JLabel("Loading floor maps…"));
    try {
      worker.execute(() -> {
        try {
          List<Decoded> decoded=new ArrayList<>();
          for(var floor:floors){
            BufferedImage image=ImageIO.read(new ByteArrayInputStream(floor.jpeg()));
            if(image==null)throw new java.io.IOException("Unreadable Atlas floor: "+floor.label());
            decoded.add(new Decoded(floor,image));
          }
          SwingUtilities.invokeLater(() -> {
            removeAll();JTabbedPane tabs=new JTabbedPane();
            for(var floor:decoded)tabs.addTab(floor.floor().label(),floor(floor,nav));
            add(tabs);revalidate();repaint();
          });
        } catch(Exception failure){SwingUtilities.invokeLater(() -> {
          removeAll();add(new JLabel("Floor maps could not be loaded."));revalidate();repaint();status.fail(failure);
        });}
      });
    } catch(RuntimeException failure){removeAll();add(new JLabel("Floor maps could not be loaded."));status.fail(failure);}
  }
  private static JComponent floor(Decoded decoded,Navigator nav) {
    JPanel panel=new JPanel(new BorderLayout(6,8));panel.setOpaque(false);
    Canvas art=new Canvas(decoded.image());JScrollPane scroll=new JScrollPane(art);
    scroll.setPreferredSize(new Dimension(560,360));panel.add(scroll,BorderLayout.CENTER);
    JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEADING,6,0));controls.setOpaque(false);
    JButton minus=new JButton("−"),plus=new JButton("+"),reset=new JButton("100%");
    minus.setToolTipText("Zoom out");plus.setToolTipText("Zoom in");reset.setToolTipText("Reset zoom");
    minus.addActionListener(e->art.zoom(.8));plus.addActionListener(e->art.zoom(1.25));reset.addActionListener(e->art.reset());
    controls.add(minus);controls.add(plus);controls.add(reset);panel.add(controls,BorderLayout.NORTH);
    JPanel below=new JPanel();below.setOpaque(false);below.setLayout(new BoxLayout(below,BoxLayout.Y_AXIS));
    below.add(new JLabel("Atlas legend"));
    for(var entry:decoded.floor().legend()){
      String text=(entry.number().isBlank()?"":entry.number()+") ")+entry.name();
      JComponent row=entry.npcId()==null?new JLabel(text):LinkButton.create(text,new EntityRef(EntityKind.NPC,entry.npcId()),nav);
      row.setToolTipText(text);below.add(row);
    }
    JTextArea credit=new JTextArea(AtlasMapPanel.CREDIT+(decoded.floor().forever()?" · drawn for the Forever beta":""));
    credit.setEditable(false);credit.setOpaque(false);credit.setLineWrap(true);credit.setWrapStyleWord(true);credit.setRows(2);
    below.add(credit);panel.add(below,BorderLayout.SOUTH);return panel;
  }
  private static final class Canvas extends JPanel {
    private final BufferedImage image;private double scale=1;private Point pressed;
    Canvas(BufferedImage image){
      this.image=image;setFocusable(true);
      getAccessibleContext().setAccessibleName("Atlas floor map; use zoom controls and scroll bars to explore");
      MouseAdapter drag=new MouseAdapter(){
        public void mousePressed(MouseEvent e){pressed=e.getPoint();}
        public void mouseDragged(MouseEvent e){
          if(pressed==null||!(getParent() instanceof JViewport view))return;
          Point p=view.getViewPosition();p.translate(pressed.x-e.getX(),pressed.y-e.getY());
          p.x=Math.max(0,Math.min(p.x,Math.max(0,getWidth()-view.getWidth())));
          p.y=Math.max(0,Math.min(p.y,Math.max(0,getHeight()-view.getHeight())));view.setViewPosition(p);
        }
      };addMouseListener(drag);addMouseMotionListener(drag);
    }
    void zoom(double factor){scale=Math.max(.25,Math.min(3,scale*factor));revalidate();repaint();}
    void reset(){scale=1;revalidate();repaint();}
    public Dimension getPreferredSize(){return new Dimension((int)(image.getWidth()*scale),(int)(image.getHeight()*scale));}
    protected void paintComponent(Graphics g){super.paintComponent(g);Dimension size=getPreferredSize();g.drawImage(image,0,0,size.width,size.height,null);}
  }
}
