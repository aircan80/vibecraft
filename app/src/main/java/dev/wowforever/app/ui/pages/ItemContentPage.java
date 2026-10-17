package dev.wowforever.app.ui.pages;

import dev.wowforever.app.db.ItemContentRepository;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.Navigator;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.*;

/** Fully preloaded item-content browser; navigation callbacks perform subsequent loading. */
public final class ItemContentPage extends JPanel {
  public ItemContentPage(ItemContentRepository.Snapshot data,Navigator nav,BiConsumer<String,Long> contentNav) {
    super(new BorderLayout());
    PageContent body=new PageContent();body.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
    JLabel title=new JLabel(data.title());title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD,22f));body.add(title);
    JTextArea note=new JTextArea(data.note());note.setEditable(false);note.setOpaque(false);note.setLineWrap(true);note.setWrapStyleWord(true);note.setAlignmentX(LEFT_ALIGNMENT);body.add(note);
    if(data.section().equals("recipes")) {
      JComboBox<String> bands=new JComboBox<>(new String[]{"Any skill band","1–74","75–149","150–224","225–299","300+"});
      long[] ids={0,1,75,150,225,300};int current=0;for(int n=0;n<ids.length;n++)if(ids[n]==data.id())current=n;
      bands.setSelectedIndex(current);bands.getAccessibleContext().setAccessibleName("Recipe skill band");
      bands.addActionListener(e->contentNav.accept("recipes",ids[bands.getSelectedIndex()]));body.add(bands);
    }
    if(data.section().equals("trinkets")) {
      JComboBox<String> situations=new JComboBox<>(new String[]{"All situations","Humanoids","Elementals","Demons","Undead","Dragonkin","Giants","Beasts","Mechanical creatures","Water breathing","Water walking"});
      long[] ids={0,125,126,127,128,129,130,131,132,-82,-104};
      int selected=0;for(int n=0;n<ids.length;n++)if(ids[n]==data.id())selected=n;
      situations.setSelectedIndex(selected);situations.getAccessibleContext().setAccessibleName("Trinket situation");
      situations.addActionListener(e->contentNav.accept("trinkets",ids[situations.getSelectedIndex()]));body.add(situations);
    }
    if(!data.classic().isEmpty()) {
      JCheckBox compare=new JCheckBox("Compare with Classic");body.add(compare);
      JPanel classic=rows(data.classic(),nav,contentNav);classic.setBorder(BorderFactory.createTitledBorder("Classic Era baseline"));classic.setVisible(false);
      compare.addActionListener(e->{classic.setVisible(compare.isSelected());body.revalidate();});
      body.add(rows(data.entries(),nav,contentNav));body.add(classic);
    } else body.add(rows(data.entries(),nav,contentNav));
    if(data.entries().isEmpty())body.add(new JLabel("Not yet discovered in the retained source data"));
    JScrollPane scroll=new JScrollPane(body);scroll.setBorder(BorderFactory.createEmptyBorder());scroll.getVerticalScrollBar().setUnitIncrement(16);add(scroll);
  }
  static JPanel rows(List<ItemContentRepository.Entry> entries,Navigator nav,BiConsumer<String,Long> contentNav) {
    PageContent panel=new PageContent();panel.setAlignmentX(LEFT_ALIGNMENT);
    for(var entry:entries) {
      JPanel row=new JPanel(new BorderLayout(8,2));row.setBorder(BorderFactory.createEmptyBorder(6,0,6,0));
      JPanel label=new JPanel(new BorderLayout(4,0));
      if(entry.target().isEmpty())label.add(new JLabel(entry.name()),BorderLayout.CENTER);else {
        JButton link=new JButton(entry.name());link.setHorizontalAlignment(SwingConstants.LEFT);
        link.addActionListener(e->{if(entry.target().equals("item"))nav.open(new dev.wowforever.app.nav.ViewTarget.Entity(new EntityRef(EntityKind.ITEM,entry.id())));else contentNav.accept(entry.target(),entry.id());});link.setToolTipText(entry.name());label.add(link,BorderLayout.CENTER);
      }
      label.add(new JLabel("["+entry.state()+"]"),BorderLayout.EAST);row.add(label,BorderLayout.NORTH);
      JTextArea details=new JTextArea(entry.details()==null?"Not yet discovered":entry.details());details.setOpaque(false);details.setEditable(false);details.setLineWrap(true);details.setWrapStyleWord(true);row.add(details,BorderLayout.CENTER);panel.add(row);
    }return panel;
  }
}
