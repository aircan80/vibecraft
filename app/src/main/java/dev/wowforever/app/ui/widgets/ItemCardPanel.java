package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.ItemCard;
import dev.wowforever.app.model.TooltipLine;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.theme.QualityColors;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.border.AbstractBorder;

/**
 * An item's tooltip card, as in the game: a navy card ({@link #BACKGROUND}, a 1 px {@link #BORDER} line, corners of
 * {@value #RADIUS} px) on both themes, its {@value IconCache#CARD} px icon at the top left beside the name in the
 * canonical colour of the item's quality (without one, the name line's stored colour, else white), then the other
 * tooltip lines ({@link TooltipLinesPanel}, which owns their colours, wrapping and tab-split slot lines). The card
 * is as wide as its longest line, at most {@link #maxWidth()}, and long lines wrap when it is narrower.
 */
public final class ItemCardPanel extends JPanel {
  /** The card background (UI review token {@code card.bg}). */
  public static final Color BACKGROUND = TooltipLinesPanel.BACKGROUND;
  /** The card border (UI review token {@code card.border}); the line is painted in {@link Theme#cardBorder()}. */
  public static final Color BORDER = TooltipLinesPanel.BORDER;
  /** The corner radius, in pixels. */
  public static final int RADIUS = 4;
  /** The widest the card gets, in logical pixels. */
  private static final int MAX_WIDTH = 400;
  private static final int PAD_X = 10;
  private static final int PAD_Y = 8;
  private static final int ICON_GAP = 10;
  private static final int NAME_GAP = 2;

  private final JLabel icon = new JLabel();
  private final TooltipLinesPanel.LineText name;
  private final TooltipLinesPanel lines;

  /**
   * The card of {@code card}: titled with its {@code name} line ({@code fallbackName} without one), showing
   * {@code itemIcon} (a {@value IconCache#CARD} px icon).
   */
  public ItemCardPanel(ItemCard card, String fallbackName, Icon itemIcon) {
    this(card, fallbackName, itemIcon, null);
  }

  /**
   * The card of {@code card} as {@link #ItemCardPanel(ItemCard, String, Icon)}, its set pieces opening their items
   * through {@code nav} (null: plain text).
   */
  public ItemCardPanel(ItemCard card, String fallbackName, Icon itemIcon, Navigator nav) {
    super(null); // laid out by doLayout: heights follow the wrapped width
    Optional<TooltipLine> nameLine = card.lines().stream().filter(l -> "name".equals(l.kind())).findFirst();
    List<TooltipLine> rest = card.lines().stream().filter(l -> nameLine.isEmpty() || l != nameLine.get()).toList();
    Color nameColour = card.quality() != null ? QualityColors.canonical(card.quality())
        : TooltipLinesPanel.colour(nameLine.map(TooltipLine::colour).orElse(null));
    name = new TooltipLinesPanel.LineText(nameLine.map(TooltipLine::text).orElse(fallbackName), nameColour,
        Font.BOLD, 1f);
    lines = new TooltipLinesPanel(rest, false, nav);
    icon.setIcon(itemIcon);
    icon.setVerticalAlignment(SwingConstants.TOP);
    setOpaque(false); // the rounded corners show what is behind
    setBackground(BACKGROUND);
    setBorder(new CardBorder());
    add(icon);
    add(name);
    add(lines);
    getAccessibleContext().setAccessibleName(name.getText());
  }

  /** The widest the card gets, in pixels at the current scale. */
  public static int maxWidth() {
    return UIScale.scale(MAX_WIDTH);
  }

  /** The item icon. */
  public JLabel icon() {
    return icon;
  }

  /** The item name, in its quality colour. */
  public JTextArea name() {
    return name;
  }

  /** The tooltip lines below the name. */
  public TooltipLinesPanel lines() {
    return lines;
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet()) {
      return super.getPreferredSize();
    }
    Insets in = getInsets();
    int column = Math.max(name.naturalWidth(), lines.isVisible() ? lines.naturalWidth() : 0);
    int width = Math.min(maxWidth(), in.left + iconSide() + gap() + column + in.right);
    int laidOut = getWidth() > 0 ? Math.min(getWidth(), width) : width;
    return new Dimension(width, in.top + Math.max(iconSide(), columnHeight(columnWidth(laidOut))) + in.bottom);
  }

  @Override
  public Dimension getMaximumSize() { // never stretched wider than its lines or taller than its text
    return getPreferredSize();
  }

  @Override
  public Dimension getMinimumSize() {
    Insets in = getInsets();
    return new Dimension(in.left + iconSide() + gap() + in.right, getPreferredSize().height);
  }

  @Override
  public void doLayout() {
    Insets in = getInsets();
    int side = iconSide();
    icon.setBounds(in.left, in.top, side, side);
    int x = in.left + side + gap();
    int width = columnWidth(getWidth());
    int nameHeight = name.heightFor(width);
    name.setBounds(x, in.top, width, nameHeight);
    lines.setBounds(x, in.top + nameHeight + UIScale.scale(NAME_GAP), width, lines.heightFor(width));
  }

  /** A new width wraps the lines anew, which changes the height the card needs: lay the page out again. */
  @Override
  @SuppressWarnings("deprecation") // reshape is what every setBounds/setSize goes through
  public void reshape(int x, int y, int w, int h) {
    boolean widthChanged = w != getWidth();
    super.reshape(x, y, w, h);
    if (widthChanged && getParent() != null) {
      revalidate();
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(getBackground());
      g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 2 * RADIUS, 2 * RADIUS));
    } finally {
      g2.dispose();
    }
  }

  private int columnWidth(int cardWidth) {
    Insets in = getInsets();
    return Math.max(1, cardWidth - in.left - iconSide() - gap() - in.right);
  }

  private int columnHeight(int width) {
    int height = name.heightFor(width);
    if (lines.isVisible()) {
      height += UIScale.scale(NAME_GAP) + lines.heightFor(width);
    }
    return height;
  }

  private int iconSide() {
    Icon i = icon.getIcon();
    return i != null ? Math.max(i.getIconWidth(), i.getIconHeight()) : UIScale.scale(IconCache.CARD);
  }

  private static int gap() {
    return UIScale.scale(ICON_GAP);
  }

  /** The 1 px rounded line around the card, and the padding inside it. */
  private static final class CardBorder extends AbstractBorder {
    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setColor(Theme.current().cardBorder()); // card.border: the same navy line in both themes
        g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, width - 1, height - 1, 2 * RADIUS, 2 * RADIUS));
      } finally {
        g2.dispose();
      }
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
      insets.set(1 + UIScale.scale(PAD_Y), 1 + UIScale.scale(PAD_X), 1 + UIScale.scale(PAD_Y),
          1 + UIScale.scale(PAD_X));
      return insets;
    }
  }
}
