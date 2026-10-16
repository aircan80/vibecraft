package dev.wowforever.app.ui.detail;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.widgets.LinkButton;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The top of a detail page: the entity name in large type, optionally followed by badges such as a rank chip,
 * over a subtitle line that may end in links.
 */
public final class HeaderPanel extends JPanel {
  private static final String SEPARATOR = " · ";
  private static final int BADGE_GAP = 8;

  private final JLabel title;
  private final JPanel titleRow = new JPanel();
  private final JPanel subtitleRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
  private final List<JButton> links = new ArrayList<>();
  private String subtitle;

  public HeaderPanel(String title, String subtitle) {
    this.title = new JLabel(title);
    this.subtitle = Objects.requireNonNull(subtitle, "subtitle");
    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
    setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
    this.title.setFont(this.title.getFont().deriveFont(Font.BOLD, this.title.getFont().getSize2D() * 1.5f));
    this.title.setAlignmentY(CENTER_ALIGNMENT);
    titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.LINE_AXIS));
    titleRow.setAlignmentX(LEFT_ALIGNMENT);
    titleRow.setOpaque(false);
    titleRow.add(this.title);
    subtitleRow.setAlignmentX(LEFT_ALIGNMENT);
    subtitleRow.setOpaque(false);
    subtitleRow.add(new JLabel(subtitle));
    add(titleRow);
    add(subtitleRow);
  }

  /** Shows {@code badge} after the title (and any earlier badges), vertically centred on it. */
  public void addBadge(JComponent badge) {
    badge.setAlignmentY(CENTER_ALIGNMENT);
    titleRow.add(Box.createHorizontalStrut(UIScale.scale(BADGE_GAP)));
    titleRow.add(badge);
  }

  /**
   * Appends {@code " · <text>"} to the subtitle, {@code text} being a link button that opens {@code ref}.
   * Returns the button.
   */
  public JButton addLink(String text, EntityRef ref, Navigator nav) {
    JButton b = LinkButton.create(text, ref, nav);
    subtitleRow.add(new JLabel(subtitle.isEmpty() ? "" : SEPARATOR));
    subtitleRow.add(b);
    subtitle = subtitle.isEmpty() ? text : subtitle + SEPARATOR + text;
    links.add(b);
    return b;
  }

  public String title() {
    return title.getText();
  }

  /** The subtitle line as read, link texts included. */
  public String subtitle() {
    return subtitle;
  }

  /** The link buttons of the subtitle line, in order. */
  public List<JButton> links() {
    return List.copyOf(links);
  }
}
