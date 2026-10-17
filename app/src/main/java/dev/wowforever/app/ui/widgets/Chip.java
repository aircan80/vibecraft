package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.ui.Labels;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.Objects;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JLabel;

/**
 * A small outline-only chip, such as an NPC's rank beside its name. Chips are never filled: Elite is a gold outline
 * with gold text, Rare a silver outline, Rare Elite a silver outline with gold text and Boss a red ({@link #RED})
 * outline. The silver is {@link #SILVER} on Slate and the darker {@link #PARCHMENT_SILVER} on Parchment, where
 * {@code #C0C6CF} reads only 1.42:1 on base. The text is the theme's {@code text.primary} unless it is gold
 * ({@code gold.text}, the small-text gold; on Parchment the slightly darker {@link #PARCHMENT_GOLD_TEXT}, because
 * Parchment's gold text reads 4.49:1 on surface); the red is only ever an outline, as red text reads 3.8:1 on Slate.
 * Every chip's text is at least 4.5:1 on {@code bg.base} and {@code bg.surface} in both themes. Colours come from
 * {@link Theme#current()} on every paint.
 */
public final class Chip extends JLabel {
  /** The outline of Rare and Rare Elite on Slate. */
  public static final Color SILVER = new Color(0xC0C6CF);
  /** The outline of Rare and Rare Elite on Parchment: 3.48:1 on base, 3.10:1 on surface, 3.87:1 on raised. */
  public static final Color PARCHMENT_SILVER = new Color(0x767C85);
  /** The gold text of Elite and Rare Elite on Parchment: 5.21:1 on base, 4.63:1 on surface. */
  public static final Color PARCHMENT_GOLD_TEXT = new Color(0x785C12);
  /** The outline of Boss. */
  public static final Color RED = new Color(0xC8463C);

  private static final int PAD_X = 6;
  private static final int PAD_Y = 1;
  private static final int ARC = 8;

  /** How a chip is drawn. */
  public enum Style { PLAIN, ELITE, RARE, RARE_ELITE, BOSS, REPEATABLE, FACTION }

  private final Style style;

  public Chip(String text, Style style) {
    super(text);
    this.style = Objects.requireNonNull(style, "style");
    setOpaque(false);
    setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() * 0.85f));
    setBorder(BorderFactory.createEmptyBorder(UIScale.scale(PAD_Y), UIScale.scale(PAD_X), UIScale.scale(PAD_Y),
        UIScale.scale(PAD_X)));
    setAlignmentY(CENTER_ALIGNMENT);
  }

  /**
   * The chip of the raw NPC {@code rank} (1 elite, 2 rare elite, 3 boss, 4 rare); empty for normal (0), unknown
   * and missing ranks, which show no chip.
   */
  public static Optional<Chip> rank(Integer rank) {
    if (rank == null) {
      return Optional.empty();
    }
    Style style = switch (rank) {
      case 1 -> Style.ELITE;
      case 2 -> Style.RARE_ELITE;
      case 3 -> Style.BOSS;
      case 4 -> Style.RARE;
      default -> null;
    };
    return style == null ? Optional.empty() : Optional.of(new Chip(Labels.rank(rank), style));
  }

  public Style style() {
    return style;
  }

  /** The outline colour. */
  public Color outline() {
    Theme t = Theme.current();
    return switch (style) {
      case ELITE -> t.accentGold();
      case RARE, RARE_ELITE -> t.dark() ? SILVER : PARCHMENT_SILVER;
      case BOSS -> RED;
      case REPEATABLE -> t.link();
      case PLAIN, FACTION -> t.textMuted();
    };
  }

  /** The text colour: the small-text gold for Elite and Rare Elite, {@code text.primary} otherwise. */
  public Color textColor() {
    Theme t = Theme.current();
    if (style == Style.ELITE || style == Style.RARE_ELITE) {
      return t.dark() ? t.goldText() : PARCHMENT_GOLD_TEXT;
    }
    return t.textPrimary();
  }

  @Override
  public Color getForeground() {
    return style == null ? super.getForeground() : textColor(); // null while the super constructor runs
  }

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g2.setColor(outline());
      float arc = UIScale.scale(ARC);
      g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, arc, arc));
    } finally {
      g2.dispose();
    }
    super.paintComponent(g);
  }
}
