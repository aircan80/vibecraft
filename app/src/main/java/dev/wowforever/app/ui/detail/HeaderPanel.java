package dev.wowforever.app.ui.detail;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.theme.Fonts;
import dev.wowforever.app.ui.widgets.LinkButton;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The top of a detail page: the entity name in {@value #TITLE_SIZE} pt Marcellus ({@link Fonts#heading}, at the
 * current scale, so it follows the text size), optionally followed by badges such as a rank chip, over a subtitle
 * line that may end in links. The title may be coloured (an item's quality colour, say).
 */
public final class HeaderPanel extends JPanel {
  private static final String SEPARATOR = " · ";
  private static final int BADGE_GAP = 8;
  /** The title's size in points at 100 % text size (Marcellus, {@link Fonts#heading}). */
  static final float TITLE_SIZE = 20f;

  private final JLabel title;
  private final JPanel titleRow = new JPanel();
  private final JPanel subtitleRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
  private final List<JButton> links = new ArrayList<>();
  private String subtitle;
  private Supplier<Color> titleColor;

  public HeaderPanel(String title, String subtitle) {
    this.title = new JLabel(title) {
      @Override
      public Color getForeground() { // looked up per paint, so theme changes apply
        Supplier<Color> colour = titleColor;
        return colour != null ? colour.get() : super.getForeground();
      }

      @Override
      public void updateUI() { // the font again at the new scale, so it follows the text size
        super.updateUI();
        setFont(Fonts.heading(UIScale.scale(TITLE_SIZE)));
      }
    };
    this.subtitle = Objects.requireNonNull(subtitle, "subtitle");
    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
    setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
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
    return addLink(text, new ViewTarget.Entity(ref), nav);
  }

  /** Appends {@code " · <text>"} to the subtitle, {@code text} being a link button that opens {@code target}. */
  public JButton addLink(String text, ViewTarget target, Navigator nav) {
    JButton b = LinkButton.create(text, target, nav);
    subtitleRow.add(new JLabel(subtitle.isEmpty() ? "" : SEPARATOR));
    subtitleRow.add(b);
    subtitle = subtitle.isEmpty() ? text : subtitle + SEPARATOR + text;
    links.add(b);
    return b;
  }

  public String title() {
    return title.getText();
  }

  /**
   * Shows or hides the title row (the title and its badges), leaving the subtitle line: for a page that shows the
   * name elsewhere, such as an item's tooltip card.
   */
  public void setTitleShown(boolean shown) {
    titleRow.setVisible(shown);
  }

  /** Whether the title row is shown. */
  public boolean isTitleShown() {
    return titleRow.isVisible();
  }

  /** Colours the title with {@code colour}, asked on every paint; null restores the look-and-feel colour. */
  public void setTitleColor(Supplier<Color> colour) {
    titleColor = colour;
    title.repaint();
  }

  /** The colour the title is painted in. */
  public Color titleColor() {
    return title.getForeground();
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
