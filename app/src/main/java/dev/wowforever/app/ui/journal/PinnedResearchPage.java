package dev.wowforever.app.ui.journal;

import dev.wowforever.app.nav.ViewTarget;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;
import javax.swing.*;

/** Keyboard-accessible saved references, opened through ordinary navigation/history. */
public final class PinnedResearchPage extends JPanel {
  private final PinnedResearch store;
  private final DefaultListModel<PinnedResearch.Reference> model=new DefaultListModel<>();
  private final JList<PinnedResearch.Reference> list=new JList<>(model);
  private final JLabel message=new JLabel();
  public PinnedResearchPage(PinnedResearch store, Consumer<ViewTarget> open) {
    super(new BorderLayout(12,12)); this.store=store;
    setBorder(BorderFactory.createEmptyBorder(24,24,24,24));
    JPanel heading=new JPanel(new GridLayout(0,1,0,8));
    heading.add(new JLabel("Pinned research"));
    heading.add(new JLabel("Ctrl+D saves the page you are viewing. References belong to this game and data build."));
    heading.add(message); add(heading,BorderLayout.NORTH);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.getAccessibleContext().setAccessibleName("Pinned research references");
    list.setCellRenderer(new DefaultListCellRenderer(){
      @Override public Component getListCellRendererComponent(JList<?> l,Object value,int index,boolean selected,boolean focus) {
        var reference=(PinnedResearch.Reference)value;
        String identity=switch(reference.target()) {
          case ViewTarget.Entity e -> e.ref().kind().label()+" #"+e.ref().id();
          case ViewTarget.WorldMap m -> "Map #"+m.uiMapId();
          case ViewTarget.ItemContent c -> c.section()+(c.id()==0?"":" #"+c.id());
          case ViewTarget.Forever f -> f.section()+(f.id()==0?"":" #"+f.id());
          case ViewTarget.List list -> list.kind().label()+" catalog";
          default -> "";
        };
        return super.getListCellRendererComponent(l,reference.label()+" · "+identity,index,selected,focus);
      }
    });
    add(new JScrollPane(list),BorderLayout.CENTER);
    Runnable visit=()->{var selected=list.getSelectedValue();if(selected!=null)open.accept(selected.target());};
    Runnable remove=()->{var selected=list.getSelectedValue();if(selected!=null){int index=list.getSelectedIndex();store.remove(selected.target());refresh();if(!model.isEmpty())list.setSelectedIndex(Math.min(index,model.size()-1));}};
    JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));
    JButton visitButton=new JButton("Open"),removeButton=new JButton("Remove");
    visitButton.addActionListener(e->visit.run());removeButton.addActionListener(e->remove.run());
    actions.add(visitButton);actions.add(removeButton);add(actions,BorderLayout.SOUTH);
    list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"open-reference");
    list.getActionMap().put("open-reference",new AbstractAction(){public void actionPerformed(ActionEvent e){visit.run();}});
    list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE,0),"remove-reference");
    list.getActionMap().put("remove-reference",new AbstractAction(){public void actionPerformed(ActionEvent e){remove.run();}});
    list.addListSelectionListener(e->{boolean selected=list.getSelectedIndex()>=0;visitButton.setEnabled(selected);removeButton.setEnabled(selected);});
    refresh();visitButton.setEnabled(!model.isEmpty());removeButton.setEnabled(!model.isEmpty());
  }
  private void refresh(){model.clear();store.references().forEach(model::addElement);message.setText(model.isEmpty()?"No references yet. Open an entity, map or catalog and press Ctrl+D.":model.size()+" saved references · Enter opens · Delete removes");if(!model.isEmpty())list.setSelectedIndex(0);}
}
