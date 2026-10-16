package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.QuestDetail;
import dev.wowforever.app.model.QuestGiver;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.Labels;
import dev.wowforever.app.ui.detail.HeaderPanel;
import java.awt.Dimension;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JTextArea;

/**
 * The quest page, one scrolling page: the header (name, level, required level and zone), the objectives text,
 * then the sections Given by, Turned in to, Rewards and Related items (those without rows are left out).
 */
public final class QuestPage extends SourcesPage {
  private final HeaderPanel header;
  private final JTextArea objectives = new JTextArea() {
    @Override
    public Dimension getMaximumSize() { // never stretched taller than its text
      return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
  };

  public QuestPage(QuestDetail d, Navigator nav) {
    super(nav);
    QuestSummary s = d.summary();
    header = new HeaderPanel(s.displayName(), Labels.join("Quest #" + s.id(),
        s.questLevel() == null ? null : "Level " + s.questLevel(),
        s.requiredLevel() == null ? null : "Requires " + s.requiredLevel(), s.zoneName()));
    header.setAlignmentX(LEFT_ALIGNMENT);
    content.add(header);
    String text = d.objectivesText() == null ? "" : d.objectivesText().strip();
    objectives.setText(text);
    objectives.setVisible(!text.isEmpty());
    objectives.setEditable(false);
    objectives.setLineWrap(true);
    objectives.setWrapStyleWord(true);
    objectives.setOpaque(false);
    objectives.setBorder(BorderFactory.createEmptyBorder(0, 12, 4, 12));
    objectives.setAlignmentX(LEFT_ALIGNMENT);
    objectives.setCaretPosition(0);
    content.add(objectives);
    List<QuestGiver> starts = byRole(d.givers(), "start");
    List<QuestGiver> ends = byRole(d.givers(), "end");
    addSection("Given by", starts.size(), VisibleColumns.of(Columns.questGivers(), starts, null), starts);
    addSection("Turned in to", ends.size(), VisibleColumns.of(Columns.questGivers(), ends, null), ends);
    addLootSection("Rewards", d.rewards());
    addSection("Related items", d.relatedItems().size(), Columns.itemSources(), d.relatedItems());
    bindJumpKeys();
  }

  private static List<QuestGiver> byRole(List<QuestGiver> givers, String role) {
    return givers.stream().filter(g -> role.equals(g.role())).toList();
  }

  public HeaderPanel header() {
    return header;
  }

  /** The read-only objectives text; hidden when the quest has none. */
  public JTextArea objectives() {
    return objectives;
  }
}
