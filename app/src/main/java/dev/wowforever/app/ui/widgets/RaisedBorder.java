package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;

/**
 * The border of a raised panel: a 1 px line in the theme's {@linkplain Theme#hairline() hairline} (gold at 35 %
 * alpha) with corners of {@value #RADIUS} px, like FlatLaf's {@code FlatLineBorder}. The colour is looked up on
 * every paint, so theme changes apply.
 */
public final class RaisedBorder extends AbstractBorder {
  /** The corner radius, in logical pixels. */
  public static final int RADIUS = 4;

  private RaisedBorder() {}

  /** A new raised-panel border; its insets are the 1 px line. */
  public static Border create() {
    return new RaisedBorder();
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g2.setColor(Theme.current().hairline());
      float arc = 2f * UIScale.scale(RADIUS);
      g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, width - 1, height - 1, arc, arc));
    } finally {
      g2.dispose();
    }
  }

  @Override
  public Insets getBorderInsets(Component c, Insets insets) {
    insets.set(1, 1, 1, 1);
    return insets;
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
