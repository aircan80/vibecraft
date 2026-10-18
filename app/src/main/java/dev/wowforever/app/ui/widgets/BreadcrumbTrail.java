package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.GeographyStep;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import java.awt.*;
import java.util.List;
import javax.swing.*;

/** Geographic parent links with separate, keyboard-accessible sibling menus from loaded metadata. */
public final class BreadcrumbTrail extends JPanel {
  public BreadcrumbTrail(List<GeographyStep> path, Navigator nav) {
    setOpaque(false);
    setAlignmentX(LEFT_ALIGNMENT);
    setLayout(new TrailLayout());
    getAccessibleContext().setAccessibleName("Location hierarchy");
    for (int i=0;i<path.size();i++) {
      var step=path.get(i);
      JPanel part=new JPanel(new BorderLayout(4,0));
      part.setOpaque(false);
      if (i>0) part.add(new JLabel("›"),BorderLayout.WEST);
      JComponent name;
      if (i==path.size()-1) {
        name=new JLabel(step.name());
        name.getAccessibleContext().setAccessibleName(step.name()+", current location");
      } else {
        ViewTarget target=step.kind()==GeographyStep.Kind.MAP ? new ViewTarget.WorldMap(step.id())
            : new ViewTarget.Entity(new EntityRef(step.kind()==GeographyStep.Kind.INSTANCE ? EntityKind.INSTANCE : EntityKind.ZONE,step.id()));
        JButton link=LinkButton.create(step.name(),target,nav);
        link.setFocusPainted(true);
        link.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"),"open-breadcrumb");
        link.getActionMap().put("open-breadcrumb",new AbstractAction() {
          @Override public void actionPerformed(java.awt.event.ActionEvent e) { link.doClick(); }
        });
        name=link;
      }
      name.setToolTipText(step.name());
      part.add(name,BorderLayout.CENTER);
      if (step.siblings().size() > 1) part.add(siblingButton(step, nav), BorderLayout.EAST);
      add(part);
    }
  }

  private JButton siblingButton(GeographyStep step, Navigator nav) {
    JButton arrow = new JButton("▾");
    arrow.setMargin(new Insets(2, 5, 2, 5));
    arrow.putClientProperty("JButton.buttonType", "borderless");
    arrow.setToolTipText("Choose a sibling of " + step.name());
    arrow.getAccessibleContext().setAccessibleName("Choose a sibling of " + step.name());
    JPopupMenu menu = new JPopupMenu("Siblings of " + step.name());
    ButtonGroup group = new ButtonGroup();
    JRadioButtonMenuItem current = null;
    for (var sibling : step.siblings()) {
      boolean selected = sibling.id() == step.id() && sibling.kind() == step.kind();
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(sibling.name(), selected);
      item.setEnabled(sibling.available());
      item.setToolTipText(sibling.note().isBlank() ? sibling.name() : sibling.note());
      item.getAccessibleContext().setAccessibleName(sibling.name() + (selected ? ", current location" : "")
          + (sibling.available() ? "" : ", unavailable"));
      item.addActionListener(e -> nav.open(target(sibling.kind(), sibling.id())));
      group.add(item); menu.add(item);
      if (selected) current = item;
    }
    JRadioButtonMenuItem selectedItem = current;
    arrow.putClientProperty("breadcrumbMenu", menu);
    menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
      public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {}
      public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
      public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) { arrow.requestFocusInWindow(); }
    });
    Runnable show = () -> {
      if (!arrow.isShowing()) return;
      menu.show(arrow, 0, arrow.getHeight());
      if (selectedItem != null && selectedItem.isEnabled())
        MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{menu, selectedItem});
    };
    arrow.addActionListener(e -> show.run());
    for (String key : List.of("ENTER", "DOWN", "alt DOWN")) {
      arrow.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), "choose-sibling");
    }
    arrow.getActionMap().put("choose-sibling", new AbstractAction() {
      @Override public void actionPerformed(java.awt.event.ActionEvent e) { show.run(); }
    });
    menu.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "close-siblings");
    menu.getActionMap().put("close-siblings", new AbstractAction() {
      @Override public void actionPerformed(java.awt.event.ActionEvent e) {
        MenuSelectionManager.defaultManager().clearSelectedPath();
        menu.setVisible(false); arrow.requestFocusInWindow();
      }
    });
    return arrow;
  }

  private static ViewTarget target(GeographyStep.Kind kind, long id) {
    return kind == GeographyStep.Kind.MAP ? new ViewTarget.WorldMap(id)
        : new ViewTarget.Entity(new EntityRef(kind == GeographyStep.Kind.INSTANCE ? EntityKind.INSTANCE : EntityKind.ZONE, id));
  }

  @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE,getPreferredSize().height); }
  @Override public Dimension getMinimumSize() { return new Dimension(0,getPreferredSize().height); }

  private static final class TrailLayout implements LayoutManager {
    @Override public void addLayoutComponent(String n,Component c) {}
    @Override public void removeLayoutComponent(Component c) {}
    @Override public Dimension minimumLayoutSize(Container p) { return preferredLayoutSize(p); }
    @Override public Dimension preferredLayoutSize(Container p) { return arrange(p,false); }
    @Override public void layoutContainer(Container p) { arrange(p,true); }
    private Dimension arrange(Container p,boolean place) {
      Insets in=p.getInsets();
      int width=p.getWidth()>0 ? p.getWidth() : p.getParent()!=null && p.getParent().getWidth()>0 ? p.getParent().getWidth() : 1002;
      int room=Math.max(1,width-in.left-in.right),x=0,y=0,row=0,used=0;
      for(Component c:p.getComponents()) {
        Dimension d=c.getPreferredSize();int w=Math.min(room,d.width);
        if(x>0 && x+4+w>room) {y+=row+4;x=0;row=0;}
        if(x>0)x+=4;
        if(place)c.setBounds(in.left+x,in.top+y,w,d.height);
        x+=w;row=Math.max(row,d.height);used=Math.max(used,x);
      }
      return new Dimension(used+in.left+in.right,y+row+in.top+in.bottom);
    }
  }
}
