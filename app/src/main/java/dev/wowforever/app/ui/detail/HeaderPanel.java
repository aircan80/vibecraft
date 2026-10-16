package dev.wowforever.app.ui.detail;

import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.LinkRenderer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** The top of a detail page: the entity name in large type over a subtitle line that may end in links. */
public final class HeaderPanel extends JPanel {
  private static final String SEPARATOR = " · ";

  private final JLabel title;
  private final JPanel subtitleRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
  private final List<JButton> links = new ArrayList<>();
  private String subtitle;

  public HeaderPanel(String title, String subtitle) {
    this.title = new JLabel(title);
    this.subtitle = Objects.requireNonNull(subtitle, "subtitle");
    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
    setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
    this.title.setFont(this.title.getFont().deriveFont(Font.BOLD, this.title.getFont().getSize2D() * 1.5f));
    this.title.setAlignmentX(LEFT_ALIGNMENT);
    subtitleRow.setAlignmentX(LEFT_ALIGNMENT);
    subtitleRow.setOpaque(false);
    subtitleRow.add(new JLabel(subtitle));
    add(this.title);
    add(subtitleRow);
  }

  /** Joins the non-null {@code parts} with {@code " · "}. */
  static String join(String... parts) {
    return Stream.of(parts).filter(Objects::nonNull).collect(Collectors.joining(SEPARATOR));
  }

  /** {@code "Level a–b"}, {@code "Level a"} when both are equal or one is unknown, null when both are. */
  static String levelText(Integer min, Integer max) {
    Integer low = min != null ? min : max;
    Integer high = max != null ? max : min;
    if (low == null) {
      return null;
    }
    return low.equals(high) ? "Level " + low : "Level " + low + "–" + high;
  }

  /**
   * Appends {@code " · <text>"} to the subtitle, {@code text} being a link button that opens {@code ref}.
   * Returns the button.
   */
  public JButton addLink(String text, EntityRef ref, Navigator nav) {
    JButton b = new JButton(text) {
      @Override
      public Color getForeground() { // looked up per paint, so theme changes apply
        return LinkRenderer.linkColor();
      }
    };
    b.setBorderPainted(false);
    b.setContentAreaFilled(false);
    b.setFocusPainted(false);
    b.setMargin(new Insets(0, 0, 0, 0));
    b.setBorder(BorderFactory.createEmptyBorder());
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.setToolTipText("Open " + text);
    b.addActionListener(e -> nav.open(new ViewTarget.Entity(ref)));
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
