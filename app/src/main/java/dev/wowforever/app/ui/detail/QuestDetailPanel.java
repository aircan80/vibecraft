package dev.wowforever.app.ui.detail;

import dev.wowforever.app.model.QuestDetail;
import dev.wowforever.app.model.QuestGiver;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;

/** The quest page: name, levels and zone, the objectives text, then who gives it, who takes it and its items. */
public final class QuestDetailPanel extends JPanel {
  private final HeaderPanel header;
  private final JTextArea objectives = new JTextArea();
  private final JTabbedPane tabs = new JTabbedPane();

  public QuestDetailPanel(QuestDetail d, Navigator nav) {
    super(new BorderLayout());
    QuestSummary s = d.summary();
    header = new HeaderPanel(s.displayName(), HeaderPanel.join("Quest #" + s.id(),
        s.questLevel() == null ? null : "Level " + s.questLevel(),
        s.requiredLevel() == null ? null : "Requires " + s.requiredLevel(), s.zoneName()));
    objectives.setText(d.objectivesText() == null ? "" : d.objectivesText());
    objectives.setEditable(false);
    objectives.setLineWrap(true);
    objectives.setWrapStyleWord(true);
    objectives.setRows(4);
    objectives.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    objectives.setCaretPosition(0);
    LinkTable.addTab(tabs, "Given by", Columns.questGivers(), byRole(d.givers(), "start"), nav);
    LinkTable.addTab(tabs, "Turned in to", Columns.questGivers(), byRole(d.givers(), "end"), nav);
    LinkTable.addTab(tabs, "Rewards", Columns.lootRows(), d.rewards(), nav);
    LinkTable.addTab(tabs, "Related items", Columns.itemSources(), d.relatedItems(), nav);
    LinkTable.selectFirstEnabled(tabs);
    JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(objectives), tabs);
    split.setResizeWeight(0.25);
    split.setBorder(null);
    add(header, BorderLayout.NORTH);
    add(split, BorderLayout.CENTER);
  }

  private static List<QuestGiver> byRole(List<QuestGiver> givers, String role) {
    return givers.stream().filter(g -> role.equals(g.role())).toList();
  }

  public HeaderPanel header() {
    return header;
  }

  /** The read-only objectives text. */
  public JTextArea objectives() {
    return objectives;
  }

  public JTabbedPane tabs() {
    return tabs;
  }
}
