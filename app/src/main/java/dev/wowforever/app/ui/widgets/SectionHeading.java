package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.ui.theme.Fonts;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Locale;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;

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
  /** Whether the section under a foldable heading is open; null for a heading that does not fold. */
  private Boolean expanded;

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
    addFocusListener(new FocusAdapter() { // the focus ring comes and goes
      @Override
      public void focusGained(FocusEvent e) {
        repaint();
      }

      @Override
      public void focusLost(FocusEvent e) {
        repaint();
      }
    });
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

  /**
   * Makes the heading the toggle of a foldable section, open ({@code true}) or closed ({@code false}): its accessible
   * role becomes a toggle button with an expandable state and its name ends in ", expanded" or ", collapsed"; null
   * makes it a plain heading again. Fires the accessible state and name changes.
   */
  public void setFoldState(Boolean expanded) {
    Boolean before = this.expanded;
    String nameBefore = accessibleName();
    this.expanded = expanded;
    AccessibleContext context = getAccessibleContext();
    context.firePropertyChange(AccessibleContext.ACCESSIBLE_STATE_PROPERTY, foldState(before), foldState(expanded));
    context.firePropertyChange(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, nameBefore, accessibleName());
    repaint();
  }

  /** Whether the section is open: null for a heading that does not fold. */
  public Boolean foldState() {
    return expanded;
  }

  private static AccessibleState foldState(Boolean expanded) {
    return expanded == null ? null : expanded ? AccessibleState.EXPANDED : AccessibleState.COLLAPSED;
  }

  /** {@code "Location (3), collapsed"} for a foldable heading, the text alone otherwise. */
  private String accessibleName() {
    String text = label == null ? title : text(); // null while the super constructor runs
    return expanded == null ? text : text + (expanded ? ", expanded" : ", collapsed");
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleJPanel() {
        @Override
        public String getAccessibleName() {
          return accessibleName();
        }

        @Override
        public AccessibleRole getAccessibleRole() {
          return expanded == null ? super.getAccessibleRole() : AccessibleRole.TOGGLE_BUTTON;
        }

        @Override
        public AccessibleStateSet getAccessibleStateSet() {
          AccessibleStateSet states = super.getAccessibleStateSet();
          if (expanded != null) {
            states.add(AccessibleState.EXPANDABLE);
            states.add(foldState(expanded));
          }
          return states;
        }
      };
    }
    return accessibleContext;
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
      if (isFocusOwner()) { // a foldable heading with the keyboard focus: a gold ring round the text
        g2.setColor(gold);
        int top = getInsets().top - 2;
        g2.drawRect(0, Math.max(0, top), w - 1, Math.max(1, getHeight() - 1 - Math.max(0, top) - 2));
      }
    } finally {
      g2.dispose();
    }
  }
}
