package dev.wowforever.app.ui.pages.quest;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.QuestChain;
import dev.wowforever.app.model.QuestChain.Step;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.HierarchyEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Interactive chain tree. Expansion only rebuilds its view model, without reading the database. */
public final class ChainPanel extends JPanel {
  private final QuestChain chain;
  private final Navigator nav;
  private final Set<String> expandedFans = new HashSet<>();
  private final JPanel body = new JPanel();
  private final JButton heading = new JButton();
  private boolean expandEarlier;
  private boolean expandLater;
  private boolean compact;
  private boolean collapsed;
  private List<ChainRows.Row> rows = List.of();

  public ChainPanel(QuestChain chain, Navigator nav) {
    super(new BorderLayout());
    this.chain = chain;
    this.nav = nav;
    setOpaque(false);
    body.setOpaque(false);
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    heading.setHorizontalAlignment(JButton.LEADING);
    heading.addActionListener(e -> {
      collapsed = !collapsed;
      updateVisibility();
    });
    add(heading, BorderLayout.NORTH);
    add(body, BorderLayout.CENTER);
    addHierarchyListener(e -> {
      if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
        updateCompact();
      }
    });
    rebuild();
  }

  /** The displayed model, including expansion controls, independent of compact section visibility. */
  public List<ChainRows.Row> rows() { return rows; }
  public JButton heading() { return heading; }
  public boolean isCollapsed() { return collapsed; }

  public void previous() { chain.previous().ifPresent(this::open); }
  public void next() { chain.next().ifPresent(this::open); }

  private void open(Step step) {
    if (step.name() != null) {
      nav.open(new ViewTarget.Entity(step.ref()));
    }
  }

  private void updateCompact() {
    boolean now = CompactColumns.within(this);
    if (now != compact) {
      compact = now;
      collapsed = now;
      updateVisibility();
    }
  }

  private void updateVisibility() {
    heading.setText("Quest chain (" + chain.steps().size() + ")");
    heading.getAccessibleContext().setAccessibleName(heading.getText() + (collapsed ? ", collapsed" : ", expanded"));
    heading.setVisible(compact && !chain.standalone() && !chain.isEmpty());
    body.setVisible(!collapsed);
    setVisible(!chain.isEmpty() && !(compact && chain.standalone()));
    revalidate();
    repaint();
  }

  @Override
  public void doLayout() {
    updateCompact();
    super.doLayout();
  }

  private void rebuild() {
    rows = ChainRows.of(chain, expandedFans, expandEarlier, expandLater);
    body.removeAll();
    for (ChainRows.Row row : rows) {
      JPanel line = new JPanel(new BorderLayout()) {
        @Override
        protected void paintComponent(Graphics g) {
          super.paintComponent(g);
          if (row.kind() == ChainRows.Kind.CURRENT) {
            g.setColor(Theme.current().accentGold());
            g.fillRect(0, 0, UIScale.scale(3), getHeight());
          }
        }
      };
      line.setOpaque(false);
      line.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(3), UIScale.scale(8 + 16 * row.indent()),
          UIScale.scale(3), 0));
      line.setAlignmentX(Component.LEFT_ALIGNMENT);
      boolean action = row.kind() == ChainRows.Kind.FAN || row.kind() == ChainRows.Kind.SHOW_EARLIER
          || row.kind() == ChainRows.Kind.SHOW_LATER
          || row.kind() == ChainRows.Kind.STEP && row.step().name() != null;
      if (action) {
        JButton link = new JButton(row.text());
        link.setHorizontalAlignment(JButton.LEADING);
        link.setBorderPainted(false);
        link.setContentAreaFilled(false);
        link.setBorder(BorderFactory.createEmptyBorder());
        link.addActionListener(e -> {
          switch (row.kind()) {
            case FAN -> expandedFans.add(row.fanKey());
            case SHOW_EARLIER -> expandEarlier = true;
            case SHOW_LATER -> expandLater = true;
            case STEP -> open(row.step());
            default -> { }
          }
          if (row.kind() != ChainRows.Kind.STEP) {
            rebuild();
          }
        });
        line.add(link);
      } else {
        JLabel label = new JLabel(row.text()) {
          @Override
          public Color getForeground() {
            return row.kind() == ChainRows.Kind.CURRENT
                ? Theme.current().textPrimary() : Theme.current().textMuted();
          }
        };
        if (row.kind() == ChainRows.Kind.CURRENT) {
          label.setFont(label.getFont().deriveFont(Font.BOLD));
        }
        line.add(label);
      }
      line.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
      body.add(line);
    }
    updateVisibility();
  }
}
