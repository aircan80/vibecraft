package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.systems.ClientSystems.Catalog;
import dev.wowforever.app.model.systems.ClientSystems.Row;
import dev.wowforever.app.ui.detail.HeaderPanel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;

/** A source-native Legacy reference. It never reads or records character progress. */
public final class LegacyPage extends JPanel {
  private final JTabbedPane tabs = new JTabbedPane();

  public LegacyPage(Catalog catalog) {
    super(new BorderLayout());
    JPanel heading = new JPanel(new BorderLayout());
    heading.add(new HeaderPanel("Legacy", catalog.build().isBlank()
        ? "Client system data is not available in this database."
        : "Client build " + catalog.build()), BorderLayout.NORTH);
    JTextArea note = note("Browse client-reported challenges and rewards. Ruleset set IDs are shown as recorded; "
        + "their gameplay meaning has not been verified. No account progress is read or saved.");
    heading.add(note, BorderLayout.SOUTH);
    add(heading, BorderLayout.NORTH);
    List<Row> sources = catalog.where("TraitCurrencySource", "TraitCurrencyID", 4225);
    for (long set : new long[]{60, 61}) {
      List<Row> rows = sources.stream().filter(r -> r.number("SuperDistrictSetID") == set).toList();
      tabs.addTab("Ruleset set " + set, rows(catalog, rows, true,
          "No Legacy challenge records for ruleset set " + set + " are available in this database."));
    }
    List<Row> other = sources.stream().filter(r -> r.number("SuperDistrictSetID") != 60
        && r.number("SuperDistrictSetID") != 61).toList();
    if (!other.isEmpty()) tabs.addTab("Other ruleset sets", rows(catalog, other, true, ""));
    tabs.addTab("Reward track", rows(catalog, catalog.where("RenownRewards", "CovenantID", 48), false,
        "No Legacy reward track records (CovenantID 48) are available in this database."));
    tabs.addTab("Perks", new TalentPage(catalog, 45));
    add(tabs, BorderLayout.CENTER);
  }

  private static JPanel rows(Catalog catalog, List<Row> rows, boolean challenges, String absent) {
    JPanel panel = new JPanel(new BorderLayout());
    if (rows.isEmpty()) {
      panel.add(note(absent), BorderLayout.NORTH);
      return panel;
    }
    String[] columns = challenges ? new String[]{"Source ID", "Challenge", "Requirement", "Ruleset set"}
        : new String[]{"Source ID", "Reward", "Level", "Item ID"};
    Object[][] data = new Object[rows.size()][columns.length];
    for (int i = 0; i < rows.size(); i++) {
      Row row = rows.get(i);
      Row achievement = challenges ? catalog.row("Achievement", row.number("AchievementID")).orElse(null) : null;
      data[i] = challenges ? new Object[]{row.id(), achievement == null
          ? "Achievement #" + row.number("AchievementID") : achievement.name(), row.text("Requirement_lang"),
          row.text("SuperDistrictSetID")}
          : new Object[]{row.id(), rewardName(catalog,row), row.text("Level"), row.text("ItemID")};
    }
    JTable table = new JTable(readOnly(data, columns));
    table.setAutoCreateRowSorter(true);
    table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    table.getAccessibleContext().setAccessibleName(challenges ? "Legacy challenge source records" : "Legacy reward source records");
    JTable fields = new JTable(readOnly(new Object[0][2], new String[]{"Field", "Recorded value"}));
    fields.getAccessibleContext().setAccessibleName("Selected source record fields");
    JLabel source = new JLabel("Select a row to inspect its fields and source.");
    source.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    JPanel details = new JPanel(new BorderLayout());
    details.add(source, BorderLayout.NORTH);
    details.add(new JScrollPane(fields), BorderLayout.CENTER);
    table.getSelectionModel().addListSelectionListener(event -> {
      if (event.getValueIsAdjusting() || table.getSelectedRow() < 0) return;
      Row row = rows.get(table.convertRowIndexToModel(table.getSelectedRow()));
      List<Object[]> values = new ArrayList<>();
      row.values().entrySet().stream().sorted(Comparator.comparing(java.util.Map.Entry::getKey))
          .forEach(e -> values.add(new Object[]{e.getKey(), e.getValue()}));
      if (challenges) catalog.row("Achievement", row.number("AchievementID")).ifPresent(achievement -> {
        achievement.values().entrySet().stream().sorted(Comparator.comparing(java.util.Map.Entry::getKey))
            .forEach(e -> values.add(new Object[]{"Achievement." + e.getKey(), e.getValue()}));
      });
      if(!challenges && row.text("Name_lang").isBlank() && row.text("ToastDescription_lang").isBlank())
        catalog.row("ItemSparse",row.number("ItemID")).ifPresent(item->{
          item.values().entrySet().stream().sorted(Comparator.comparing(java.util.Map.Entry::getKey))
              .forEach(e->values.add(new Object[]{"ItemSparse."+e.getKey(),e.getValue()}));
          values.add(new Object[]{"ItemSparse.source",item.sourcePath()+":"+item.sourceLine()});
        });
      fields.setModel(readOnly(values.toArray(Object[][]::new), new String[]{"Field", "Recorded value"}));
      source.setText(row.table() + " #" + row.id() + " · " + row.sourcePath() + ":" + row.sourceLine());
      source.setToolTipText("Snapshot " + catalog.snapshot() + " · " + source.getText());
    });
    JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), details);
    split.setResizeWeight(.55);
    split.setBorder(BorderFactory.createEmptyBorder());
    panel.add(split, BorderLayout.CENTER);
    table.setRowSelectionInterval(0, 0);
    return panel;
  }

  private static String rewardName(Catalog catalog,Row reward) {
    if(!reward.text("Name_lang").isBlank())return reward.text("Name_lang");
    if(!reward.text("ToastDescription_lang").isBlank())return reward.text("ToastDescription_lang");
    // Only an exact item ID in this same Forever dataset can supply a missing reward name.
    String item=catalog.row("ItemSparse",reward.number("ItemID")).map(r->r.text("Display_lang")).orElse("");
    return item.isBlank()?"RenownRewards #"+reward.id():item;
  }

  private static DefaultTableModel readOnly(Object[][] values, String[] columns) {
    return new DefaultTableModel(values, columns) {
      @Override public boolean isCellEditable(int row, int column) { return false; }
    };
  }

  private static JTextArea note(String text) {
    JTextArea area = new JTextArea(text);
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setOpaque(false);
    area.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
    area.getAccessibleContext().setAccessibleName(text);
    return area;
  }

  public JTabbedPane tabs() { return tabs; }
}
