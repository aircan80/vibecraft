package dev.wowforever.app.ui.detail;

import dev.wowforever.app.model.TooltipLine;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * An item's tooltip lines as stored, one label per line on a dark tooltip background in either theme (the stored
 * colours are the game's, made for a dark background). A tab in a line splits it into left and right text.
 * Hidden when there are no lines.
 */
public final class TooltipLinesPanel extends JPanel {
  /** The colour of a line without one (or with one that is not {@code #rrggbb}). */
  public static final Color DEFAULT_TEXT = Color.WHITE;
  /** The tooltip background. */
  public static final Color BACKGROUND = new Color(0x10, 0x12, 0x1C);

  private final List<JLabel> labels = new ArrayList<>();

  public TooltipLinesPanel(List<TooltipLine> lines) {
    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
    setOpaque(true);
    setBackground(BACKGROUND);
    setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    setMinimumSize(new Dimension(0, 0)); // long lines truncate instead of widening the detail area
    for (TooltipLine line : lines) {
      add(row(line));
    }
    setVisible(!lines.isEmpty());
  }

  /** Every label in line order (a tab-split line contributes its left, then its right label). */
  public List<JLabel> labels() {
    return List.copyOf(labels);
  }

  private JComponent row(TooltipLine line) {
    Color colour = colour(line.colour());
    String text = line.text() == null ? "" : line.text();
    int tab = text.indexOf('\t');
    if (tab < 0) {
      JLabel label = label(text, colour);
      label.setAlignmentX(LEFT_ALIGNMENT);
      return label;
    }
    JPanel row = new JPanel(new BorderLayout(12, 0));
    row.setOpaque(false);
    row.setAlignmentX(LEFT_ALIGNMENT);
    row.add(label(text.substring(0, tab), colour), BorderLayout.WEST);
    JLabel right = label(text.substring(tab + 1), colour);
    right.setHorizontalAlignment(JLabel.TRAILING);
    row.add(right, BorderLayout.EAST);
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
    return row;
  }

  private JLabel label(String text, Color colour) {
    JLabel label = new JLabel(text);
    label.setForeground(colour);
    label.setToolTipText(text.isEmpty() ? null : text); // the whole line when it is truncated
    labels.add(label);
    return label;
  }

  /** {@code #rrggbb} as a colour; {@link #DEFAULT_TEXT} for null or anything else. */
  private static Color colour(String hex) {
    if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
      return DEFAULT_TEXT;
    }
    return new Color(Integer.parseInt(hex.substring(1), 16));
  }
}
