package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicToggleButtonUI;

/**
 * A {@link Rail} button: the whole width of the rail, {@value #ROW} px tall, its icon centred on a {@value #PLATE} px
 * navy plate ({@link Theme#plate()}, the same in both themes). Every state is static, never animated:
 * <ul>
 *   <li>idle: the icon in full colour at {@value #IDLE_ALPHA} opacity (never desaturated);</li>
 *   <li>hover (or keyboard focus): a 1 px gold ring at 50 % on the plate's edge, the icon fully opaque;</li>
 *   <li>selected: a solid 1 px gold border with a soft gold glow outside it, and a {@value #BAR} px gold bar on the
 *   rail's left edge, as tall as the plate.</li>
 * </ul>
 * The gold is the theme's {@link Theme#accentGold()}, looked up on every paint.
 */
public final class RailButtonUI extends BasicToggleButtonUI {
  /** The plate's size, in logical pixels. */
  public static final int PLATE = 28;
  /** The button's height, in logical pixels: the plate and 6 px above and below it. */
  public static final int ROW = 40;
  /** The width of the selected button's bar on the rail edge, in logical pixels. */
  public static final int BAR = 3;
  /** The opacity of an idle button's icon. */
  public static final float IDLE_ALPHA = 0.85f;
  /** The colour of the SVG glyphs on the plate (shown until the client art is in): light, as the plate is navy. */
  public static final Color GLYPH = new Color(0xE4E1D8);
  private static final int RADIUS = 4;
  private static final int HOVER_ALPHA = 128;
  /** The glow's two rings outside the selected border, inner first. */
  private static final int[] GLOW_ALPHA = {90, 35};

  /** The UI for {@code c} (Swing's factory method). */
  public static ComponentUI createUI(JComponent c) {
    return new RailButtonUI();
  }

  @Override
  protected void installDefaults(AbstractButton b) {
    super.installDefaults(b);
    b.setOpaque(false);
    b.setBorder(BorderFactory.createEmptyBorder());
    b.setRolloverEnabled(true);
    b.setFocusPainted(false);
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return new Dimension(UIScale.scale(Rail.WIDTH), UIScale.scale(ROW));
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return getPreferredSize(c);
  }

  @Override
  public Dimension getMaximumSize(JComponent c) {
    return getPreferredSize(c);
  }

  @Override
  public void update(Graphics g, JComponent c) {
    paint(g, c); // never opaque: the rail's background shows around the plate
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    AbstractButton b = (AbstractButton) c;
    ButtonModel model = b.getModel();
    boolean selected = model.isSelected();
    boolean hover = !selected && (model.isRollover() || model.isArmed() || b.isFocusOwner());
    Theme theme = Theme.current();
    Color gold = theme.accentGold();
    int size = UIScale.scale(PLATE);
    int x = (b.getWidth() - size) / 2;
    int y = (b.getHeight() - size) / 2;
    float arc = UIScale.scale(RADIUS) * 2f;

    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g2.setStroke(new BasicStroke(UIScale.scale(1f)));
      if (selected) {
        for (int i = 0; i < GLOW_ALPHA.length; i++) {
          g2.setColor(withAlpha(gold, GLOW_ALPHA[i]));
          ring(g2, x - i - 1, y - i - 1, size + 2 * (i + 1), arc + 2 * (i + 1));
        }
        g2.setColor(gold);
        g2.fillRect(0, y, UIScale.scale(BAR), size);
      }
      g2.setColor(theme.plate());
      g2.fill(new RoundRectangle2D.Float(x, y, size, size, arc, arc));

      Icon icon = selected && b.getSelectedIcon() != null ? b.getSelectedIcon() : b.getIcon();
      if (icon != null) {
        Graphics2D gi = (Graphics2D) g2.create();
        try {
          if (!selected && !hover) {
            gi.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, IDLE_ALPHA));
          }
          icon.paintIcon(b, gi, (b.getWidth() - icon.getIconWidth()) / 2, (b.getHeight() - icon.getIconHeight()) / 2);
        } finally {
          gi.dispose();
        }
      }

      if (selected || hover) {
        g2.setColor(selected ? gold : withAlpha(gold, HOVER_ALPHA));
        ring(g2, x, y, size, arc);
      }
    } finally {
      g2.dispose();
    }
  }

  /** A 1 px line just inside the square at {@code x}, {@code y} of {@code size}, corners rounded by {@code arc}. */
  private static void ring(Graphics2D g, int x, int y, int size, float arc) {
    float half = UIScale.scale(1f) / 2;
    g.draw(new RoundRectangle2D.Float(x + half, y + half, size - 2 * half, size - 2 * half, arc - 2 * half,
        arc - 2 * half));
  }

  private static Color withAlpha(Color c, int alpha) {
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
  }
}
