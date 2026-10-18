package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.widgets.WrappingText;
import java.awt.*;
import java.util.List;
import java.util.Map;
import javax.swing.*;

/** Journal-only shared-list workspace, retaining the ordinary loot controls and source comparisons. */
public final class SharedLootPage extends JPanel implements PageState {
  public static final String HEADING = "Source-reported shared and extra lists";
  public static final String EMPTY = "No shared or extra loot list recorded for this instance. Unknown is not proof that no shared pool exists.";
  public static final String EVIDENCE = "These are the source's original list names. A shared or extra list is not necessarily trash, all bosses or world drops. Reported membership does not establish Forever drop confirmation, rates, equal probabilities or eligible mobs. Class, slot and resistance filters use recorded Forever item fields; missing fields remain unknown.";
  private final InstancePage lists;

  /** Call with the preloaded detail snapshot. No repository or worker is needed here. */
  public SharedLootPage(InstanceDetail detail, BossLoot loaded, String clientBuild, Navigator nav) {
    super(new BorderLayout(0, 8));
    setName("shared-loot-workspace");
    var snapshot = SharedLootSnapshot.from(detail, loaded);
    JPanel intro = new JPanel();
    intro.setLayout(new BoxLayout(intro, BoxLayout.PAGE_AXIS));
    intro.setBorder(BorderFactory.createEmptyBorder(10, 16, 6, 16));
    JLabel heading = new JLabel(HEADING);
    heading.setFont(heading.getFont().deriveFont(Font.BOLD));
    heading.setAlignmentX(LEFT_ALIGNMENT); intro.add(heading);
    var explanation = new WrappingText(snapshot.lists().isEmpty() ? EMPTY : EVIDENCE, "Shared loot evidence coverage");
    explanation.setAlignmentX(LEFT_ALIGNMENT); intro.add(explanation);
    JPanel identities = new JPanel(new GridLayout(0, 1, 0, 3));
    identities.setAlignmentX(LEFT_ALIGNMENT); intro.add(identities);
    add(intro, BorderLayout.NORTH);
    if (snapshot.lists().isEmpty()) { lists = null; return; }
    BossLoot scoped = new BossLoot() {
      @Override public List<LootRow> lookup(long id, String mode) { return snapshot.lookup(id, mode); }
      @Override public List<SourceDifference> differences(long id) { return loaded.differences(id); }
      @Override public Map<RateSource.Key, List<RateSource>> sources(long id, List<Long> items) {
        return loaded.sources(id, items);
      }
    };
    lists = new InstancePage(snapshot.detail(), List.of(), scoped, clientBuild, nav);
    Component header = ((BorderLayout) lists.getLayout()).getLayoutComponent(BorderLayout.NORTH);
    if (header != null) lists.remove(header);
    // Client-sourced instances normally prepend encounter captions. This workspace contains only source lists.
    Container rail = lists.encounters().getParent();
    while (rail.getComponentCount() > 0 && rail.getComponent(0) != lists.extraListsHeading())
      rail.remove(0);
    lists.extraListsHeading().setText("Source lists");
    lists.extraLists().getAccessibleContext().setAccessibleName("Source-reported shared and extra loot lists");
    Runnable updateIdentities = () -> {
      identities.removeAll();
      BossSummary selected = lists.extraLists().getSelectedValue();
      if (selected != null) {
        if (selected.npcId() != null) addIdentity(identities, EntityKind.NPC, selected.npcId(), nav);
        if (selected.objectId() != null) addIdentity(identities, EntityKind.OBJECT, selected.objectId(), nav);
      }
      identities.revalidate(); identities.repaint();
    };
    lists.extraLists().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) updateIdentities.run(); });
    updateIdentities.run();
    add(lists, BorderLayout.CENTER);
  }

  private static void addIdentity(JPanel panel, EntityKind kind, long id, Navigator nav) {
    JButton link = new JButton("Recorded " + kind.label() + " #" + id + " →");
    link.addActionListener(e -> nav.open(new dev.wowforever.app.nav.ViewTarget.Entity(new EntityRef(kind, id))));
    panel.add(link);
  }

  private record State(Object selection, List<Integer> scroll) {}
  @Override public Object saveState() {
    return new State(lists == null ? null : lists.saveState(), scrollbars(this).stream().map(JScrollBar::getValue).toList());
  }
  @Override public void restoreState(Object value) {
    if (!(value instanceof State state)) return;
    if (lists != null) lists.restoreState(state.selection());
    SwingUtilities.invokeLater(() -> {
      var bars = scrollbars(this);
      for (int i = 0; i < Math.min(bars.size(), state.scroll().size()); i++) bars.get(i).setValue(state.scroll().get(i));
    });
  }
  private static List<JScrollBar> scrollbars(Container parent) {
    var bars = new java.util.ArrayList<JScrollBar>();
    for (Component child : parent.getComponents()) {
      if (child instanceof JScrollPane pane) bars.add(pane.getVerticalScrollBar());
      else if (child instanceof Container nested) bars.addAll(scrollbars(nested));
    }
    return bars;
  }

  /** Exposed for Journal styling and acceptance. */
  public InstancePage listPage() { return lists; }
}
