package dev.wowforever.app.ui.journal;

import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.theme.Fonts;
import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.ui.widgets.WrappingText;
import java.awt.*;
import javax.swing.*;

/** Width-tracking surfaces shared by the optional Explorer journal. */
final class JournalSurface {
  private JournalSurface() {}
  static JLabel heading(String text,int size) {
    JLabel label=new JLabel(text);label.setFont(Fonts.heading(UIScale.scale((float)size)));label.setForeground(Theme.current().textPrimary());return label;
  }
  static JTextArea title(String text,int size) {
    JTextArea title=new JTextArea(text,0,20) {
      @Override public Color getForeground(){return Theme.current().textPrimary();}
      @Override public Dimension getPreferredSize(){
        Dimension natural=super.getPreferredSize();int width=getParent()!=null?getParent().getWidth()-getParent().getInsets().left-getParent().getInsets().right:getWidth();
        if(width<=0)width=natural.width;
        var view=getUI().getRootView(this);view.setSize(Math.max(1,width),Integer.MAX_VALUE);
        return new Dimension(natural.width,(int)Math.ceil(view.getPreferredSpan(javax.swing.text.View.Y_AXIS)));
      }
    };
    title.setFont(Fonts.heading(UIScale.scale((float)size)));title.setEditable(false);title.setFocusable(false);title.setOpaque(false);title.setLineWrap(true);title.setWrapStyleWord(true);
    title.getAccessibleContext().setAccessibleName(text);return title;
  }
  static JPanel stack() { JPanel panel=new JPanel(new Stack());panel.setOpaque(false);return panel; }
  static JPanel card() {
    JPanel panel=new JPanel(new Stack()) {
      @Override protected void paintComponent(Graphics graphics){
        Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.current().bgSurface());g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,18,18);
        g.setColor(Theme.current().hairline());
        g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,18,18);g.dispose();
      }
    };
    panel.setOpaque(false);panel.setBorder(BorderFactory.createEmptyBorder(22,22,22,22));return panel;
  }
  static void add(JPanel parent,JComponent child) { parent.add(child);parent.add(Box.createVerticalStrut(10)); }
  static WrappingText copy(String value) { return new WrappingText(value,value); }
  static JPanel grid() {return grid(2,720);}
  static JPanel grid(int columns,int threshold) { JPanel panel=new JPanel(new Grid(columns,threshold));panel.setOpaque(false);return panel; }
  static JScrollPane scroll(Body body) {
    JScrollPane pane=new JScrollPane(body,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    pane.setBorder(BorderFactory.createEmptyBorder());pane.getVerticalScrollBar().setUnitIncrement(24);return pane;
  }
  static final class Body extends JPanel implements Scrollable {
    @Override public Color getBackground(){Theme theme=Theme.current();return theme==null?super.getBackground():theme.bgBase();}
    Body(){super(new Stack());setBorder(BorderFactory.createEmptyBorder(24,24,30,24));}
    public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
    public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 24;}
    public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(24,r.height-24);}
    public boolean getScrollableTracksViewportWidth(){return true;}
    public boolean getScrollableTracksViewportHeight(){return false;}
  }
  static class Stack implements LayoutManager {
    public void addLayoutComponent(String n,Component c){} public void removeLayoutComponent(Component c){}
    public Dimension minimumLayoutSize(Container p){return new Dimension(0,preferredLayoutSize(p).height);}
    public Dimension preferredLayoutSize(Container p){
      Insets in=p.getInsets();int h=in.top+in.bottom,w=0;
      for(Component c:p.getComponents())if(c.isVisible()){Dimension d=c.getPreferredSize();h+=d.height;w=Math.max(w,d.width);}
      return new Dimension(w+in.left+in.right,h);
    }
    public void layoutContainer(Container p){Insets in=p.getInsets();int y=in.top,w=Math.max(0,p.getWidth()-in.left-in.right);
      for(Component c:p.getComponents())if(c.isVisible()){
        c.setBounds(in.left,y,w,Math.max(1,c.getHeight()));if(c instanceof Container nested)nested.doLayout();
        int h=c.getPreferredSize().height;c.setBounds(in.left,y,w,h);y+=h;
      }
    }
  }
  static final class Grid implements LayoutManager {
    private final int wideColumns,threshold;
    Grid(int wideColumns,int threshold){this.wideColumns=wideColumns;this.threshold=threshold;}
    private int columns(Container p){return p.getWidth()>=threshold?wideColumns:1;}
    public void addLayoutComponent(String n,Component c){}public void removeLayoutComponent(Component c){}
    public Dimension minimumLayoutSize(Container p){return new Dimension(0,preferredLayoutSize(p).height);}
    public Dimension preferredLayoutSize(Container p){return measure(p,false);}
    public void layoutContainer(Container p){measure(p,true);}
    private Dimension measure(Container p,boolean layout){
      int columns=columns(p),gap=16,width=Math.max(0,(p.getWidth()-gap*(columns-1))/columns),y=0,n=p.getComponentCount();
      for(int i=0;i<n;i+=columns){int h=0;
        for(int j=i;j<Math.min(n,i+columns);j++){
          Component c=p.getComponent(j);c.setSize(width,Math.max(1,c.getHeight()));
          if(c instanceof Container nested)nested.doLayout();h=Math.max(h,c.getPreferredSize().height);
        }
        if(layout)for(int j=i;j<Math.min(n,i+columns);j++)p.getComponent(j).setBounds((j-i)*(width+gap),y,width,h);
        y+=h+gap;
      }
      return new Dimension(Math.max(300,p.getWidth()),Math.max(0,y-gap));
    }
  }
}
