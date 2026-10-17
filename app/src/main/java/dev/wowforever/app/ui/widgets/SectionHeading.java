package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.ui.theme.Fonts;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Locale;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A section's heading: its title and count ({@code "Dropped by (34)"}) in Marcellus SC ({@link Fonts#headingSmallCaps})
 * in the theme's text gold, over a 1 px gold rule that fades out to the right. It may carry a 16 px icon before the
 * title ({@link #setIcon}) and a control at its right end ({@link #setTrailing}), such as a filter. The font is
 * {@value #SIZE} pt at the current scale and taken again on every look-and-feel update, so it follows the text size;
 * the colours come from {@link Theme#current()} on every paint.
 */
public final class SectionHeading extends JPanel {
  /** The heading's size in points at 100 % text size. */
  static final float SIZE = 15f;

  private final String title;
  private final JLabel label;
  private JComponent trailing;

  public SectionHeading(String title, int count) {
    super(new BorderLayout(UIScale.scale(8), 0));
    this.title = Objects.requireNonNull(title, "title");
    label = new JLabel(text(title, count)) {
      @Override
      public Color getForeground() { // looked up per paint, so theme changes apply
        return Theme.current().goldText();
      }

      @Override
      public void updateUI() {
        super.updateUI();
        setFont(Fonts.headingSmallCaps(UIScale.scale(SIZE)));
      }
    };
    label.setIconTextGap(UIScale.scale(6));
    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder(14, 0, 5, 0));
    add(label, BorderLayout.CENTER);
  }

  /** {@code "Dropped by (1,234)"}. */
  static String text(String title, int count) {
    return String.format(Locale.ROOT, "%s (%,d)", title, count);
  }

  /** Shows {@code count} after the title. */
  public void setCount(int count) {
    label.setText(text(title, count));
  }

  /** The heading as read: {@code "Dropped by (34)"}. */
  public String text() {
    return label.getText();
  }

  /** The title without the count. */
  public String title() {
    return title;
  }

  /** Shows {@code icon} (16 px) before the title; null removes it. */
  public void setIcon(Icon icon) {
    label.setIcon(icon);
  }

  /** Puts {@code control} at the heading's right end, replacing an earlier one; null removes it. */
  public void setTrailing(JComponent control) {
    if (trailing != null) {
      remove(trailing);
    }
    trailing = control;
    if (control != null) {
      add(control, BorderLayout.EAST);
    }
    revalidate();
    repaint();
  }

  /** The label that shows the title and count. */
  public JLabel label() {
    return label;
  }

  /** The text gold, as the label paints it. */
  @Override
  public Color getForeground() {
    Theme t = Theme.current();
    return t != null ? t.goldText() : super.getForeground(); // null while the super constructor runs
  }

  /** The rule: 1 px along the bottom, gold at the left fading to nothing at the right. */
  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    int w = getWidth();
    if (w <= 0) {
      return;
    }
    Color gold = Theme.current().accentGold();
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setPaint(new GradientPaint(0, 0, gold, w, 0, new Color(gold.getRGB() & 0xFFFFFF, true)));
      g2.fillRect(0, getHeight() - 1, w, 1);
    } finally {
      g2.dispose();
    }
  }
}
