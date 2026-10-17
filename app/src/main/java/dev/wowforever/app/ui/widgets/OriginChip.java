package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

/**
 * Renders a data origin: nothing for {@code questie} (almost every row), an outlined chip reading
 * {@code "AtlasLoot"} for {@code atlasloot} and {@code "Questie + AtlasLoot"} for {@code both}; any other origin
 * is shown as is.
 */
public final class OriginChip extends DefaultTableCellRenderer {
  private static final int PAD_X = 6;
  private static final int INDENT = 4;
  private static final int ARC = 8;

  private String chip;

  /** Renders {@code column} with origin chips. */
  public static void install(TableColumn column) {
    column.setCellRenderer(new OriginChip());
    column.setPreferredWidth(UIScale.scale(150));
  }

  /** The chip text for {@code origin}; null when no chip is shown (Questie only, or unknown). */
  public static String label(String origin) {
    if (origin == null || origin.isBlank()) {
      return null;
    }
    return switch (origin.strip().toLowerCase(Locale.ROOT)) {
      case "questie" -> null;
      case "atlasloot" -> "AtlasLoot";
      case "both" -> "Questie + AtlasLoot";
      default -> origin.strip();
    };
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
      int row, int column) {
    chip = label(value == null ? null : value.toString());
    super.getTableCellRendererComponent(table, chip == null ? "" : chip, isSelected, hasFocus, row, column);
    setBorder(BorderFactory.createCompoundBorder(getBorder(),
        BorderFactory.createEmptyBorder(0, UIScale.scale(INDENT + PAD_X), 0, UIScale.scale(PAD_X))));
    setFont(table.getFont().deriveFont(table.getFont().getSize2D() * 0.9f));
    if (!isSelected) {
      setForeground(Theme.current().textMuted());
    }
    return this;
  }

  /** The chip text of the last rendered cell; null when it shows none. */
  public String chipText() {
    return chip;
  }

  /** The chip outline: around the text, padded, vertically centred; empty when there is no chip. */
  Rectangle chipBounds() {
    if (chip == null) {
      return new Rectangle();
    }
    FontMetrics fm = getFontMetrics(getFont());
    Insets in = getInsets();
    int pad = UIScale.scale(PAD_X);
    int h = fm.getHeight() + UIScale.scale(2);
    return new Rectangle(in.left - pad, (getHeight() - h) / 2, fm.stringWidth(chip) + 2 * pad, h);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (chip == null) {
      return;
    }
    Rectangle r = chipBounds();
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(getForeground());
      int arc = UIScale.scale(ARC);
      g2.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, arc, arc);
    } finally {
      g2.dispose();
    }
  }
}
