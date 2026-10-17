package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.systems.*;
import dev.wowforever.app.model.systems.ClientSystems.*;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;

/** Offline hypothetical builds. All allocation state belongs to this page and is never persisted. */
public final class TalentPage extends JPanel {
  private final Catalog catalog;
  private final JComboBox<TreeChoice> trees=new JComboBox<>();
  private final JLabel points=new JLabel();
  private final JTextArea detail=new JTextArea(7,30);
  private final JTextField build=new JTextField();
  private final JTextArea feedback=new JTextArea(2,20);
  private final JTable table=new JTable();
  private List<Row> nodes=List.of();
  private TalentAllocation allocation;
  private final long system;
  private final java.util.Map<Long,TalentAllocation> allocations=new java.util.HashMap<>();
  private final NodeTable model=new NodeTable();
  private record TreeChoice(long id,String name) { @Override public String toString(){return name;} }
  public TalentPage(Catalog catalog){this(catalog,10);}
  public TalentPage(Catalog catalog,long system) {
    super(new BorderLayout(0,8));this.catalog=catalog;this.system=system;
    setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
    JPanel header=new JPanel();header.setLayout(new BoxLayout(header,BoxLayout.Y_AXIS));
    header.add(new JLabel(system==45?"Legacy perk calculator":"Forever talent browser & calculator"));
    header.add(new JLabel("Beta client "+(catalog.build().isBlank()?"data not imported":catalog.build())+" · Hypothetical only"));
    JTextArea beta=new JTextArea("Seen in beta "+catalog.build()+"; content above level 30 is unconfirmed.");
    beta.setEditable(false);beta.setLineWrap(true);beta.setWrapStyleWord(true);beta.setOpaque(false);header.add(beta);
    if(system==10)header.add(new JLabel("Milestones: 11 · 16 · 21 · 31 points"));
    header.add(trees);header.add(points);
    add(header,BorderLayout.NORTH);
    for(Row tree:catalog.rows("TraitTree")) {
      if(system==10 ? !TraitRules.isClassTree(catalog,tree.id()) : tree.number("TraitSystemID")!=system)continue;
      if(catalog.where("TraitNode","TraitTreeID",tree.id()).isEmpty())continue;
      if(system==10 && catalog.where("SkillLineXTraitTree","TraitTreeID",tree.id()).isEmpty())continue;
      String name=tree.text("TitleText_lang");
      if(name.isBlank())name=catalog.where("SkillLineXTraitTree","TraitTreeID",tree.id()).stream()
          .map(link->catalog.row("SkillLine",link.number("SkillLineID")).map(Row::name).orElse("Skill #"+link.number("SkillLineID")))
          .findFirst().orElse("Perk tree #"+tree.id());
      if(system==10) {
        long mask=0;
        for(Row anchor:catalog.where("SkillLineXTraitTree","TraitTreeID",tree.id()))
          for(Row ability:catalog.where("SkillLineAbility","SkillLine",anchor.number("SkillLineID")))mask|=ability.number("ClassMask");
        if(Long.bitCount(mask)==1){long classId=Long.numberOfTrailingZeros(mask)+1;name=catalog.row("ChrClasses",classId).map(Row::name).orElse(name)+" · "+name;}
      }
      trees.addItem(new TreeChoice(tree.id(),name));
    }
    table.setModel(model);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setAutoCreateRowSorter(true);
    table.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())showDetails();});
    table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);table.getColumnModel().getColumn(0).setPreferredWidth(220);
    JScrollPane scroll=new JScrollPane(table);scroll.setPreferredSize(new Dimension(500,320));
    JPanel center=new JPanel(new BorderLayout(0,6));center.add(scroll,BorderLayout.CENTER);
    detail.setEditable(false);detail.setLineWrap(true);detail.setWrapStyleWord(true);
    center.add(new JScrollPane(detail),BorderLayout.SOUTH);add(center,BorderLayout.CENTER);
    JPanel bottom=new JPanel();bottom.setLayout(new BoxLayout(bottom,BoxLayout.Y_AXIS));
    JPanel controls=new JPanel(new GridLayout(0,2,6,6));
    JButton add=new JButton("+ Rank"),remove=new JButton("− Rank"),reset=new JButton("Reset");
    controls.add(add);controls.add(remove);controls.add(reset);
    if(system==10){JButton compare=new JButton("Compare with Classic");compare.addActionListener(e->compareClassic());controls.add(compare);}
    bottom.add(controls);
    add.addActionListener(e->change(1));remove.addActionListener(e->change(-1));
    reset.addActionListener(e->{if(allocation!=null){allocation.clear();refresh();feedback.setText("Reset hypothetical build.");}});
    bottom.add(new JLabel("Build string · copy / paste"));bottom.add(build);
    JPanel transfer=new JPanel(new FlowLayout(FlowLayout.LEADING));JButton export=new JButton("Show build"),load=new JButton("Load build");
    transfer.add(export);transfer.add(load);bottom.add(transfer);
    export.addActionListener(e->{if(allocation!=null){build.setText(allocation.buildString());build.selectAll();}});
    load.addActionListener(e->{if(allocation!=null){feedback.setText(allocation.importBuild(build.getText().trim()).orElse("Hypothetical build loaded."));refresh();}});
    feedback.setEditable(false);feedback.setLineWrap(true);feedback.setWrapStyleWord(true);feedback.setOpaque(false);
    bottom.add(feedback);add(bottom,BorderLayout.SOUTH);
    trees.addActionListener(e->selectTree());selectTree();
  }
  private void selectTree(){
    TreeChoice tree=(TreeChoice)trees.getSelectedItem();allocation=null;
    if(tree==null){nodes=List.of();points.setText("Source-native Trait data is not available in this database.");model.fireTableDataChanged();return;}
    nodes=catalog.where("TraitNode","TraitTreeID",tree.id()).stream()
        .sorted(java.util.Comparator.comparingLong((Row r)->r.number("PosY")).thenComparingLong(r->r.number("PosX"))).toList();
    try{allocation=allocations.computeIfAbsent(system==45?0:tree.id(),key->TraitRules.allocation(catalog,tree.id()));feedback.setText("Only source-supported rules can be allocated.");}
    catch(IllegalArgumentException ex){feedback.setText(ex.getMessage());}
    build.setText("");refresh();if(!nodes.isEmpty())table.setRowSelectionInterval(0,0);
  }
  private void refresh(){model.fireTableDataChanged();points.setText(allocation==null?"Calculator unavailable: source rules incomplete.":"<html>"+allocation.spent()+" / "+allocation.cap()+" points<br>Hypothetical build · nothing is saved</html>");}
  private Row selected(){int index=table.getSelectedRow();return index<0?null:nodes.get(table.convertRowIndexToModel(index));}
  private void change(int delta){Row node=selected();if(node==null||allocation==null)return;long id=node.id();feedback.setText(allocation.change(id,delta).orElse("Hypothetical allocation updated."));refresh();for(int i=0;i<nodes.size();i++)if(nodes.get(i).id()==id){int view=table.convertRowIndexToView(i);table.setRowSelectionInterval(view,view);break;}}
  private void showDetails(){Row node=selected();if(node==null){detail.setText("");return;}
    StringBuilder text=new StringBuilder(TraitRules.name(catalog,node)).append("\n");
    for(Row link:catalog.where("TraitNodeXTraitNodeEntry","TraitNodeID",node.id())) {
      var entry=catalog.row("TraitNodeEntry",link.number("TraitNodeEntryID"));
      entry.flatMap(r->catalog.row("TraitDefinition",r.number("TraitDefinitionID"))).ifPresent(def->{
        String description=def.text("OverrideDescription_lang");
        if(description.isBlank())description=catalog.row("Spell",def.number("SpellID")).map(r->r.text("Description_lang")).orElse("");
        if(!description.isBlank()) {text.append(description).append('\n');if(description.contains("$"))text.append("Client formula tokens are unresolved; no numeric value is inferred.\n");}
        else text.append("Description not recorded in this client snapshot.\n");
        text.append("Spell #").append(def.number("SpellID")).append(" · Maximum ranks: ").append(entry.get().number("MaxRanks")).append('\n');
      });
    }
    if(allocation!=null)allocation.rule(node.id()).ifPresent(rule->{
      text.append("Ranks: ").append(allocation.rank(node.id())).append(" / ").append(rule.maxRanks()).append(" · Cost per rank: ").append(rule.cost()).append('\n');
      for(var gate:rule.gates())text.append("Requires ").append(gate.minimum()).append(" points in its source-defined branch.\n");
      for(var pre:rule.prerequisites())text.append("Requires ").append(pre.ranks()).append(" ranks in node #").append(pre.nodeId()).append('.').append('\n');
      if(!rule.anyPrerequisites().isEmpty())text.append("Requires a connected prerequisite at its maximum rank.\n");
      if(!rule.unavailableReason().isBlank())text.append(rule.unavailableReason()).append('\n');
    });
    text.append("Source: ").append(node.sourcePath()).append(':').append(node.sourceLine()).append(" · client ").append(catalog.build());
    detail.setText(text.toString());detail.setCaretPosition(0);
  }
  private void compareClassic(){
    if(catalog.baselineBuild().isBlank() || catalog.baselineTables().getOrDefault("Talent",List.of()).isEmpty()) {
      detail.setText("Classic comparison unavailable: a pinned Era Talent baseline has not been imported.");return;
    }
    java.util.Map<Long,Row> classic=new java.util.HashMap<>();
    for(Row talent:catalog.baselineTables().getOrDefault("Talent",List.of()))for(int rank=0;rank<9;rank++) {
      long spell=talent.number("SpellRank_"+rank);if(spell!=0)classic.put(spell,talent);
    }
    StringBuilder text=new StringBuilder("Forever ").append(catalog.build()).append(" vs Classic Era ").append(catalog.baselineBuild())
        .append("\nExact spell-ID comparison; names are never used to match talents.\n");
    for(Row node:nodes)for(Row link:catalog.where("TraitNodeXTraitNodeEntry","TraitNodeID",node.id()))
      catalog.row("TraitNodeEntry",link.number("TraitNodeEntryID")).ifPresent(entry->
        catalog.row("TraitDefinition",entry.number("TraitDefinitionID")).ifPresent(def->{
          Row era=classic.get(def.number("SpellID"));
          text.append(TraitRules.name(catalog,node)).append(": ");
          if(era==null)text.append("Spell #").append(def.number("SpellID")).append(" absent from Era talents");
          else {int ranks=0;for(int i=0;i<9;i++)if(era.number("SpellRank_"+i)>0)ranks++;
            text.append("same spell ID; ranks ").append(entry.number("MaxRanks")).append(" Forever / ").append(ranks).append(" Era")
                .append(" · Era Talent #").append(era.id());}
          text.append('\n');
        }));
    detail.setText(text.toString());detail.setCaretPosition(0);
  }
  private final class NodeTable extends AbstractTableModel {
    public int getRowCount(){return nodes.size();}public int getColumnCount(){return 3;}
    public String getColumnName(int col){return new String[]{"Talent / perk","Ranks","Node"}[col];}
    public Object getValueAt(int row,int col){Row node=nodes.get(row);return switch(col){case 0->TraitRules.name(catalog,node);case 1->allocation==null?"Unavailable":allocation.rank(node.id());default->node.id();};}
  }
}
