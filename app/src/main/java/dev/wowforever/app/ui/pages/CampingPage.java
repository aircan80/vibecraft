package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.CampingData;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Camping facts and a session-only party draft, with unsupported coverage stated explicitly. */
public final class CampingPage extends JPanel {
  public CampingPage(CampingData data) {
    super(new BorderLayout());
    JPanel body=new PageContent();
    body.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
    JLabel title=new JLabel("Camping");title.setFont(title.getFont().deriveFont(24f));body.add(title);
    text(body,data.snapshot().isEmpty()?"Camping client tables have not been imported.":
        "Client build "+data.build()+" · "+data.camps().size()+" summon-object spell effects");
    text(body,"Campfires and profession camps");
    table(body,new String[]{"Spell","Name","Profession","Tier","Required rank","Description"},data.camps().stream()
        .map(c->new Object[]{c.spellId(),c.name(),c.profession(),c.tier(),c.requiredRank(),c.description()}).toList());
    text(body,"Campfire tiers");
    table(body,new String[]{"Campfire","Tier","Required rank"},data.camps().stream()
        .filter(c->c.name().endsWith("Campfire"))
        .map(c->new Object[]{c.name(),c.tier(),c.requiredRank()}).toList());
    text(body,"Spell descriptions are original client text. Tokens such as $s1 remain unresolved.");
    text(body,"Blueprints");
    if(data.blueprints().isEmpty()) text(body,"No named Blueprint rows are available. Hidden items and drop sources remain unknown.");
    else table(body,new String[]{"Item","Name","Required skill ID","Required rank"},data.blueprints().stream()
        .map(b->new Object[]{b.itemId(),b.name(),b.requiredSkill(),b.requiredRank()}).toList());
    text(body,"Unnamed or unavailable Blueprint rows and drop sources remain unknown.");
    text(body,"Profession × tier matrix");
    var matrix=data.camps().stream().collect(java.util.stream.Collectors.groupingBy(
        c->c.profession()+" · "+c.tier(),java.util.TreeMap::new,
        java.util.stream.Collectors.mapping(CampingData.Camp::name,java.util.stream.Collectors.joining(", "))));
    table(body,new String[]{"Profession · tier","Features"},matrix.entrySet().stream()
        .map(e->new Object[]{e.getKey(),e.getValue()}).toList());
    text(body,data.coverageNote());
    text(body,"Hypothetical party");
    JPanel party=new PageContent();
    JComboBox<String> profession=new JComboBox<>(new String[]{"Alchemy","Blacksmithing","Cooking","Enchanting",
        "Engineering","First Aid","Fishing","Herbalism","Leatherworking","Mining","Skinning","Tailoring"});
    profession.getAccessibleContext().setAccessibleName("Party member profession");
    JSpinner rank=new JSpinner(new SpinnerNumberModel(1,1,1000,1));
    rank.getAccessibleContext().setAccessibleName("Party member profession rank");
    DefaultListModel<String> members=new DefaultListModel<>();JList<String> list=new JList<>(members) { @Override public boolean getScrollableTracksViewportWidth(){return true;} };
    list.getAccessibleContext().setAccessibleName("Hypothetical party members");
    DefaultListModel<String> coverage=new DefaultListModel<>();
    java.util.List<java.util.Map.Entry<String,Integer>> selected=new java.util.ArrayList<>();
    Runnable refresh=()->{coverage.clear();data.camps().stream().filter(c->c.requiredRank()!=null &&
        selected.stream().anyMatch(m->m.getKey().equals(c.profession()) && m.getValue()>=c.requiredRank()))
        .map(c->c.name()+" · "+c.tier()).distinct().forEach(coverage::addElement);
      if(coverage.isEmpty())coverage.addElement("No source-linked features meet this draft's rank requirements.");};
    JButton add=new JButton("Add member");add.addActionListener(e->{if(members.size()<5) {
      selected.add(java.util.Map.entry((String)profession.getSelectedItem(),(Integer)rank.getValue()));
      members.addElement(profession.getSelectedItem()+" · rank "+rank.getValue());}
      add.setEnabled(members.size()<5);refresh.run();});
    JButton remove=new JButton("Remove selected");remove.addActionListener(e->{if(list.getSelectedIndex()>=0)
      {selected.remove(list.getSelectedIndex());members.remove(list.getSelectedIndex());}
      add.setEnabled(members.size()<5);refresh.run();});
    party.add(profession);party.add(rank);party.add(add);party.add(remove);body.add(party);
    list.setVisibleRowCount(5);body.add(new JScrollPane(list));
    text(body,"Eligible features by placing-item requirements (recipe ownership is not assumed)");
    JList<String> coverageList=new JList<>(coverage) { @Override public boolean getScrollableTracksViewportWidth(){return true;} };coverageList.setVisibleRowCount(5);
    coverageList.getAccessibleContext().setAccessibleName("Party feature coverage");body.add(new JScrollPane(coverageList));
    refresh.run();text(body,"This hypothetical party lasts for this page only.");
    if(!data.missingTables().isEmpty()) text(body,"Unavailable source tables: "+String.join(", ",data.missingTables()));
    text(body,"Source snapshot: "+(data.snapshot().isEmpty()?"not available":data.snapshot()));
    JScrollPane scroll=new JScrollPane(body);scroll.getVerticalScrollBar().setUnitIncrement(16);add(scroll);
  }
  private static void text(JPanel body,String value) {
    body.add(new dev.wowforever.app.ui.widgets.WrappingText(value,"Camping details"));
  }
  private static void table(JPanel body,String[] columns,List<Object[]> rows) {
    if(rows.isEmpty()) { text(body,"No source rows available.");return; }
    JTable table=new JTable(new DefaultTableModel(rows.toArray(Object[][]::new),columns) {
      @Override public boolean isCellEditable(int row,int column) { return false; }
    }) {
      @Override public String getToolTipText(java.awt.event.MouseEvent event) {
        int row=rowAtPoint(event.getPoint()),column=columnAtPoint(event.getPoint());
        return row<0||column<0?null:String.valueOf(getValueAt(row,column));
      }
    };
    table.getTableHeader().setDefaultRenderer((t,value,selected,focus,row,column)->{
      JTextArea header=new JTextArea(String.valueOf(value));header.setLineWrap(true);header.setWrapStyleWord(true);
      header.setFont(t.getTableHeader().getFont());header.setBackground(t.getTableHeader().getBackground());
      header.setForeground(t.getTableHeader().getForeground());header.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
      return header;
    });
    table.getTableHeader().setPreferredSize(new java.awt.Dimension(1,64));
    table.setAutoCreateRowSorter(true);table.setFillsViewportHeight(true);
    JScrollPane scroll=new JScrollPane(table);scroll.setPreferredSize(new java.awt.Dimension(600,Math.min(300,68+rows.size()*22)));
    body.add(scroll);
  }
}
