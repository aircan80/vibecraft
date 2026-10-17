package dev.wowforever.app.ui.pages.quest;

import dev.wowforever.app.model.QuestChain;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.detail.CompactColumns;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Navigation along the quest chain's deterministic spine, without completion or progress state. */
public final class ChainStepper extends JPanel {
  private final QuestChain chain;
  private final JButton previous = new JButton("‹ Previous");
  private final JButton next = new JButton("Next ›");
  private final JLabel position = new JLabel();

  public ChainStepper(QuestChain chain, Navigator nav) {
    super(new FlowLayout(FlowLayout.LEADING, 8, 0));
    this.chain = chain;
    setOpaque(false);
    previous.setEnabled(chain.previous().isPresent());
    next.setEnabled(chain.next().isPresent());
    previous.addActionListener(e -> chain.previous().ifPresent(s -> nav.open(new ViewTarget.Entity(s.ref()))));
    next.addActionListener(e -> chain.next().ifPresent(s -> nav.open(new ViewTarget.Entity(s.ref()))));
    previous.getAccessibleContext().setAccessibleName("Previous quest");
    next.getAccessibleContext().setAccessibleName("Next quest");
    add(previous);
    add(position);
    add(next);
    setVisible(chain.spine().size() > 1);
    refresh();
  }

  private void refresh() {
    boolean compact = CompactColumns.within(this);
    previous.setText(compact ? "‹ Prev" : "‹ Previous");
    position.setText(compact ? (chain.spineIndex() + 1) + "/" + chain.spine().size()
        : "This quest · Step " + (chain.spineIndex() + 1) + " of " + chain.spine().size());
  }

  @Override
  public void doLayout() {
    refresh();
    super.doLayout();
  }

  public String text() {
    return CompactColumns.within(this)
        ? "‹ Prev · " + (chain.spineIndex() + 1) + "/" + chain.spine().size() + " · Next ›"
        : "‹ Previous · This quest · Next ›  Step " + (chain.spineIndex() + 1) + " of " + chain.spine().size();
  }

  public JButton previous() {
    return previous;
  }

  public JButton next() {
    return next;
  }
}
