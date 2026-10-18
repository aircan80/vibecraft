package dev.wowforever.app.ui.journal;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.detail.DetailPanels;
import dev.wowforever.app.ui.theme.Fonts;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.*;
import java.awt.*;
import javax.swing.*;

/** Theme-native reading card with the exact in-game tooltip retained as an optional view. */
final class JournalItemFacts extends JPanel {
  private final JPanel facts=stack(), acquisition=stack();
  JournalItemFacts(DetailPanels.LoadedItem data,ItemCardPanel original,Navigator nav) {
    super(new BorderLayout(0,12));setOpaque(false);setAlignmentX(LEFT_ALIGNMENT);
    setBorder(BorderFactory.createEmptyBorder(8,12,16,12));
    JPanel columns=new JPanel(new ColumnsLayout());columns.setOpaque(false);
    facts.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.current().hairline()),
        BorderFactory.createEmptyBorder(18,18,18,18)));facts.setOpaque(true);facts.setBackground(Theme.current().bgSurface());
    JLabel label=new JLabel("Item details");label.setFont(Fonts.heading(UIScale.scale(23f)));
    JPanel identity=new JPanel(new BorderLayout(12,0));identity.setOpaque(false);identity.add(label,BorderLayout.CENTER);
    if(data.icon()!=null){JLabel icon=new JLabel(new ImageIcon(data.icon()));icon.setToolTipText(data.detail().summary().displayName());identity.add(icon,BorderLayout.WEST);}
    facts.add(identity);facts.add(Box.createVerticalStrut(12));
    for(var line:data.detail().card().lines()) {
      if("name".equals(line.kind()))continue;
      if(line.text()==null||line.text().isBlank()){facts.add(Box.createVerticalStrut(8));continue;}
      JComponent row;
      if("set_item".equals(line.kind())&&line.itemId()!=null)
        row=LinkButton.create(line.text().replace('\t',' '),new EntityRef(EntityKind.ITEM,line.itemId()),nav);
      else row=new FactText(line.text().replace("\t","    "),"use".equals(line.kind())||"equip".equals(line.kind())||"set_bonus".equals(line.kind()));
      row.setAlignmentX(LEFT_ALIGNMENT);facts.add(row);facts.add(Box.createVerticalStrut(5));
    }
    acquisition.setBorder(BorderFactory.createEmptyBorder(12,6,12,6));
    JLabel where=new JLabel("Where does this come from?");where.setFont(Fonts.heading(UIScale.scale(21f)));acquisition.add(where);
    acquisition.add(Box.createVerticalStrut(12));
    var item=data.detail();
    acquisition.add(new FactText("Recorded acquisition evidence",false));
    entry("NPC drop records",item.droppedBy().size());entry("Dungeon loot-list records",item.inInstanceLoot().size());
    entry("Vendor records",item.soldBy().size());entry("Quest reward records",item.rewardFrom().size());
    entry("Container records",item.containedInObjects().size()+item.containedInItems().size());
    if(item.droppedBy().isEmpty()&&item.inInstanceLoot().isEmpty()&&item.soldBy().isEmpty()&&item.rewardFrom().isEmpty()
        &&item.containedInObjects().isEmpty()&&item.containedInItems().isEmpty())acquisition.add(new FactText("No acquisition records yet.",false));
    acquisition.add(Box.createVerticalStrut(12));
    acquisition.add(new WrappingText("Exact sources and drop-rate qualifications are listed below. A loot-list entry and an NPC drop can describe the same item; these counts describe evidence records.","Acquisition evidence scope"));
    columns.add(facts);columns.add(acquisition);add(columns,BorderLayout.CENTER);
    JPanel optional=stack();JToggleButton toggle=new JToggleButton("In-game tooltip");toggle.setAlignmentX(LEFT_ALIGNMENT);
    optional.add(toggle);JPanel holder=new JPanel(new FlowLayout(FlowLayout.LEADING,0,8));holder.setOpaque(false);
    if(original.getParent()!=null)original.getParent().remove(original);holder.add(original);holder.setVisible(false);optional.add(holder);
    toggle.addActionListener(e->{holder.setVisible(toggle.isSelected());revalidate();repaint();});add(optional,BorderLayout.SOUTH);
  }
  private void entry(String label,int count){if(count>0){acquisition.add(new FactText(count+" · "+label,false));acquisition.add(Box.createVerticalStrut(5));}}
  @Override public Dimension getMaximumSize(){return new Dimension(UIScale.scale(1000),Integer.MAX_VALUE);}
  private static JPanel stack(){JPanel p=new JPanel(new JournalSurface.Stack());p.setOpaque(false);return p;}
  private static final class FactText extends JTextArea {
    FactText(String text,boolean effect){
      super(text,0,24);setEditable(false);setFocusable(false);setOpaque(false);setLineWrap(true);setWrapStyleWord(true);
      setFont(UIManager.getFont("Label.font").deriveFont(UIScale.scale(14f)));setForeground(effect?Theme.current().link():Theme.current().textPrimary());
      getAccessibleContext().setAccessibleName(text);
    }
    @Override public Dimension getPreferredSize(){
      Dimension d=super.getPreferredSize();int w=getWidth();
      if(getParent()!=null&&getParent().getWidth()>0){Insets in=getParent().getInsets();w=getParent().getWidth()-in.left-in.right;}
      if(w<=0)w=d.width;var v=getUI().getRootView(this);v.setSize(Math.max(1,w),Integer.MAX_VALUE);
      return new Dimension(d.width,(int)Math.ceil(v.getPreferredSpan(javax.swing.text.View.Y_AXIS)));
    }
  }
  private static final class ColumnsLayout implements LayoutManager {
    public void addLayoutComponent(String n,Component c){} public void removeLayoutComponent(Component c){}
    public Dimension minimumLayoutSize(Container p){return new Dimension(0,preferredLayoutSize(p).height);}
    public Dimension preferredLayoutSize(Container p){return arrange(p,false);}
    public void layoutContainer(Container p){arrange(p,true);}
    private Dimension arrange(Container p,boolean layout){
      int w=p.getWidth()>0?p.getWidth():UIScale.scale(760),gap=UIScale.scale(22),y=0,h=0;
      boolean wide=w>=UIScale.scale(680);int left=wide?(int)((w-gap)*.61):w;
      for(int i=0;i<p.getComponentCount();i++){
        Component c=p.getComponent(i);int cw=wide?(i==0?left:w-left-gap):w;
        c.setSize(cw,Math.max(1,c.getHeight()));if(c instanceof Container nested)nested.doLayout();int ch=c.getPreferredSize().height;
        if(layout)c.setBounds(wide&&i>0?left+gap:0,wide?0:y,cw,ch);
        h=Math.max(h,ch);y+=ch+gap;
      }
      return new Dimension(w,wide?h:Math.max(0,y-gap));
    }
  }
}
