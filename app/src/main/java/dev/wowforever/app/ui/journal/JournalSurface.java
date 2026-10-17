package dev.wowforever.app.ui.journal;

import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.WrappingText;
import java.awt.*;
import javax.swing.*;

/** Width-tracking surfaces shared by the optional Explorer journal. */
final class JournalSurface {
  private JournalSurface() {}
  static JLabel heading(String text,int size) {
    JLabel label=new JLabel(text);label.setFont(label.getFont().deriveFont(Font.BOLD,(float)size));label.setForeground(Theme.current().textPrimary());return label;
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
    title.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD,(float)size));title.setEditable(false);title.setFocusable(false);title.setOpaque(false);title.setLineWrap(true);title.setWrapStyleWord(true);
    title.getAccessibleContext().setAccessibleName(text);return title;
  }
  static JPanel stack() { JPanel panel=new JPanel(new Stack());panel.setOpaque(false);return panel; }
  static JPanel card() {
    JPanel panel=stack();panel.setOpaque(true);panel.setBackground(Theme.current().bgRaised());
    panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.current().hairline()),
        BorderFactory.createEmptyBorder(18,18,18,18)));return panel;
  }
  static void add(JPanel parent,JComponent child) { parent.add(child);parent.add(Box.createVerticalStrut(10)); }
  static WrappingText copy(String value) { return new WrappingText(value,value); }
  static JPanel grid() { JPanel panel=new JPanel(new Grid());panel.setOpaque(false);return panel; }
  static JScrollPane scroll(Body body) {
    JScrollPane pane=new JScrollPane(body,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    pane.setBorder(BorderFactory.createEmptyBorder());pane.getVerticalScrollBar().setUnitIncrement(24);return pane;
  }
  static final class Body extends JPanel implements Scrollable {
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
    private int columns(Container p){return p.getWidth()>=720?2:1;}
    public void addLayoutComponent(String n,Component c){}public void removeLayoutComponent(Component c){}
    public Dimension minimumLayoutSize(Container p){return new Dimension(0,preferredLayoutSize(p).height);}
    public Dimension preferredLayoutSize(Container p){return measure(p,false);}
    public void layoutContainer(Container p){measure(p,true);}
    private Dimension measure(Container p,boolean layout){
      int columns=columns(p),gap=14,width=Math.max(0,(p.getWidth()-gap*(columns-1))/columns),y=0,n=p.getComponentCount();
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
