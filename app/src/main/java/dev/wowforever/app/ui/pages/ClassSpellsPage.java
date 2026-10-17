package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.systems.ClientSystems.Catalog;
import dev.wowforever.app.model.systems.ClientSystems.Row;
import dev.wowforever.app.ui.detail.HeaderPanel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;

/** Read-only class spell reference from explicit client class masks and spell level records. */
public final class ClassSpellsPage extends JPanel {
  private final JComboBox<ClassChoice> classes = new JComboBox<>();
  private final JTable spells = new JTable();
  private final JLabel coverage = new JLabel();
  private final Map<Long,List<Row>> levelsBySpell = new LinkedHashMap<>();
  private final Map<Long,String> spellNames = new LinkedHashMap<>();
  private final Map<Long,Row> eraAbilities = new LinkedHashMap<>();
  private final Map<Long,Row> eraTalents = new LinkedHashMap<>();
  private boolean comparisonAvailable;
  private record ClassChoice(long mask, String name) {
    @Override public String toString() { return name; }
  }

  public ClassSpellsPage(Catalog catalog) {
    super(new BorderLayout());
    JPanel top = new JPanel(new BorderLayout());
    top.add(new HeaderPanel("Class-linked spells", catalog.build().isBlank()
        ? "Client spell data is unavailable." : "Client build " + catalog.build() + (catalog.baselineBuild().isBlank() ? "" : " · Era " + catalog.baselineBuild())), BorderLayout.NORTH);
    JTextArea note = new JTextArea("Client spell levels are shown as recorded, with acquisition method IDs. "
        + "These records do not establish trainer availability or a complete learning schedule. "
        + (catalog.baselineBuild().isBlank() ? "No Era comparison is available in this view."
            : "Era comparison matches exact spell IDs in SkillLineAbility and Talent rank fields. "
                + "Acquisition method IDs are not interpreted as baseline learning rules."));
    note.setEditable(false); note.setLineWrap(true); note.setWrapStyleWord(true); note.setOpaque(false);
    note.setBorder(BorderFactory.createEmptyBorder(6, 12, 8, 12));
    top.add(note, BorderLayout.CENTER);
    JPanel filters = new JPanel(new BorderLayout(8, 0));
    filters.setBorder(BorderFactory.createEmptyBorder(4, 12, 8, 12));
    filters.add(new JLabel("Class"), BorderLayout.WEST);
    filters.add(classes, BorderLayout.CENTER);
    top.add(filters, BorderLayout.SOUTH);
    add(top, BorderLayout.NORTH);
    classes.getAccessibleContext().setAccessibleName("Class spell filter");
    spells.getAccessibleContext().setAccessibleName("Client class spell levels");
    spells.setAutoCreateRowSorter(true);
    add(new JScrollPane(spells), BorderLayout.CENTER);
    coverage.setBorder(BorderFactory.createEmptyBorder(6, 12, 8, 12));
    add(coverage, BorderLayout.SOUTH);
    List<Row> oldAbilities = baselineRows(catalog, "SkillLineAbility");
    List<Row> oldTalents = baselineRows(catalog, "Talent");
    comparisonAvailable = !catalog.baselineBuild().isBlank()
        && hasBaselineTable(catalog, "SkillLineAbility") && hasBaselineTable(catalog, "Talent");
    for (Row ability : oldAbilities) if (ability.number("Spell") > 0) eraAbilities.put(ability.number("Spell"), ability);
    for (Row talent : oldTalents) {
      if (talent.number("SpellID") > 0) eraTalents.put(talent.number("SpellID"), talent);
      for (int rank = 0; rank < 9; rank++) {
        long spell = talent.number("SpellRank_" + rank);
        if (spell > 0) eraTalents.put(spell, talent);
      }
    }
    catalog.rows("SpellLevels").forEach(r -> levelsBySpell.computeIfAbsent(r.number("SpellID"), key -> new ArrayList<>()).add(r));
    catalog.rows("SpellName").forEach(r -> spellNames.put(r.id(), r.name()));
    Map<Long,Row> skillLines = new LinkedHashMap<>();
    catalog.rows("SkillLine").forEach(r -> skillLines.put(r.id(), r));
    long representedMask = 0;
    for (Row ability : catalog.rows("SkillLineAbility")) representedMask |= ability.number("ClassMask");
    for (Row clazz : catalog.rows("ChrClasses").stream().sorted(Comparator.comparing(Row::name)).toList()) {
      if (clazz.id() < 1 || clazz.id() > 63) continue;
      long mask = 1L << (clazz.id() - 1);
      if ((representedMask & mask) != 0) classes.addItem(new ClassChoice(mask, clazz.name()));
    }
    classes.addActionListener(event -> show(catalog, skillLines));
    show(catalog, skillLines);
  }

  private void show(Catalog catalog, Map<Long,Row> skillLines) {
    ClassChoice selected = (ClassChoice) classes.getSelectedItem();
    List<Object[]> values = new ArrayList<>();
    if (selected != null) {
      for (Row ability : catalog.rows("SkillLineAbility")) {
        if ((ability.number("ClassMask") & selected.mask()) == 0) continue;
        long spell = ability.number("Spell");
        Row skill = skillLines.get(ability.number("SkillLine"));
        String school = skill == null ? "Skill #" + ability.number("SkillLine") : skill.text("DisplayName_lang");
        List<Row> levels = levelsBySpell.getOrDefault(spell, List.of());
        if (levels.isEmpty()) values.add(new Object[]{spellNames.getOrDefault(spell, "Spell #" + spell), spell, school, null, null, null,
            ability.text("AcquireMethod"), comparison(spell), source(ability) + comparisonSource(spell), ability.number("ClassMask")});
        for (Row level : levels) values.add(new Object[]{spellNames.getOrDefault(spell, "Spell #" + spell), spell, school,
            level.number("SpellLevel"), level.number("BaseLevel"), level.number("DifficultyID"),
            ability.text("AcquireMethod"), comparison(spell), source(ability) + " · " + source(level) + comparisonSource(spell), ability.number("ClassMask")});
      }
    }
    DefaultTableModel model = new DefaultTableModel(values.toArray(Object[][]::new),
        new String[]{"Spell", "Spell ID", "Skill", "Spell level", "Base level", "Difficulty ID", "Acquire method", "Era comparison", "Source", "Class mask"}) {
      @Override public boolean isCellEditable(int row, int col) { return false; }
      @Override public Class<?> getColumnClass(int col) { return col == 1 || col == 9 || col >= 3 && col <= 5 ? Long.class : String.class; }
    };
    spells.setModel(model);
    if (!values.isEmpty()) spells.getRowSorter().setSortKeys(List.of(new javax.swing.RowSorter.SortKey(3, javax.swing.SortOrder.ASCENDING)));
    coverage.setText(selected == null ? "Class spell links are unavailable in this database."
        : values.size() + " records · " + catalog.rows("SkillLineAbility").stream().filter(r -> r.number("ClassMask") == 0).count()
            + " unclassified ability records excluded.");
    coverage.setToolTipText("Snapshot " + catalog.snapshot() + ". Blank levels have no matching SpellLevels record.");
  }
  private static List<Row> baselineRows(Catalog catalog, String table) {
    return catalog.baselineTables().getOrDefault(table, catalog.baselineTables().getOrDefault("Era" + table, List.of()));
  }
  private static boolean hasBaselineTable(Catalog catalog, String table) {
    return catalog.baselineTables().containsKey(table) || catalog.baselineTables().containsKey("Era" + table);
  }
  private String comparison(long spell) {
    if (!comparisonAvailable) return "Comparison unavailable";
    if (eraTalents.containsKey(spell)) return "Was an Era talent";
    if (eraAbilities.containsKey(spell)) return "Present in Era class spells";
    return "New class-linked spell";
  }
  private String comparisonSource(long spell) {
    Row previous = eraTalents.getOrDefault(spell, eraAbilities.get(spell));
    return previous == null ? "" : " · Era " + source(previous);
  }
  private static String source(Row row) { return row.sourcePath() + ":" + row.sourceLine(); }
  public JComboBox<?> classFilter() { return classes; }
  public JTable spellTable() { return spells; }
}
