package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.ui.Labels;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JLabel;

/**
 * An NPC's rank as a small chip beside its name: Elite in a gold outline, Rare and Rare Elite in a silver (muted
 * grey) outline, Boss as a gold fill with text in the page background colour. Normal and unknown ranks show no chip
 * (it is hidden). The gold is the theme's text gold ({@link Theme#goldText}): the chip's text is small, and the light
 * theme's accent gold is too light for it (about 3.9:1); with the text gold both chips read at 4.5:1 or more in both
 * themes. Colours come from {@link Theme#current()} per paint, so theme changes apply.
 */
public final class RankChip extends JLabel {
  private static final int PAD_X = 6;
  private static final int PAD_Y = 1;
  private static final int ARC = 8;

  /** How the chip is drawn. */
  public enum Style { NONE, GOLD_OUTLINE, SILVER_OUTLINE, GOLD_FILL }

  private final Style style;

  /** The chip of the raw NPC {@code rank} (0 normal, 1 elite, 2 rare elite, 3 boss, 4 rare). */
  public RankChip(Integer rank) {
    super(Labels.rank(rank));
    style = styleOf(rank);
    setVisible(style != Style.NONE);
    setOpaque(false);
    setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() * 0.85f));
    setBorder(BorderFactory.createEmptyBorder(UIScale.scale(PAD_Y), UIScale.scale(PAD_X), UIScale.scale(PAD_Y),
        UIScale.scale(PAD_X)));
    setAlignmentY(CENTER_ALIGNMENT);
  }

  private static Style styleOf(Integer rank) {
    if (rank == null) {
      return Style.NONE;
    }
    return switch (rank) {
      case 1 -> Style.GOLD_OUTLINE;
      case 2, 4 -> Style.SILVER_OUTLINE;
      case 3 -> Style.GOLD_FILL;
      default -> Style.NONE;
    };
  }

  public Style style() {
    return style;
  }

  /** The outline (and, for {@link Style#GOLD_FILL}, the fill) colour; null for {@link Style#NONE}. */
  public Color outline() {
    return switch (style) {
      case GOLD_OUTLINE, GOLD_FILL -> Theme.current().goldText();
      case SILVER_OUTLINE -> Theme.current().textMuted();
      case NONE -> null;
    };
  }

  /** The text colour: the outline colour, or the base background on a gold fill. */
  @Override
  public Color getForeground() {
    if (style == null || style == Style.NONE) { // null while the super constructor runs
      return super.getForeground();
    }
    return style == Style.GOLD_FILL ? Theme.current().bgBase() : outline();
  }

  @Override
  protected void paintComponent(Graphics g) {
    Color c = outline();
    if (c != null) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(c);
        int arc = UIScale.scale(ARC);
        if (style == Style.GOLD_FILL) {
          g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        } else {
          g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        }
      } finally {
        g2.dispose();
      }
    }
    super.paintComponent(g);
  }
}
