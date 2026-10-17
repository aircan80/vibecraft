package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.model.TooltipLine;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Document;
import javax.swing.text.View;

/**
 * An item's tooltip lines as stored, one per row in the line's colour, on a dark tooltip card in both themes: the
 * card is a WoW tooltip, not a themed panel, so the stored game colours and the default white text stay readable on
 * the light theme too. A long line wraps at word boundaries (plain text, no HTML); a tab in a line splits it into
 * left and right text, the left truncating first when the row is narrow. Hidden when there are no lines.
 * {@link ItemCardPanel} shows the lines inside its own card, without this panel's frame.
 */
public final class TooltipLinesPanel extends JPanel {
  /** The colour of a line without one (or with one that is not {@code #rrggbb}). */
  public static final Color DEFAULT_TEXT = Color.WHITE;
  /** The card background (UI review token {@code card.bg}), the same in both themes. */
  public static final Color BACKGROUND = new Color(0x0C1424);
  /** The card border (UI review token {@code card.border}), the same in both themes. */
  public static final Color BORDER = new Color(0x46587A);
  private static final int SPLIT_GAP = 12;

  /** The text components in line order: a wrapping line, or a tab-split line's left then right label. */
  private final List<JComponent> parts = new ArrayList<>();

  /** {@code lines} on their own dark card. */
  public TooltipLinesPanel(List<TooltipLine> lines) {
    this(lines, true);
  }

  /** {@code lines}, on their own dark card when {@code framed}, else transparent and without a border. */
  TooltipLinesPanel(List<TooltipLine> lines, boolean framed) {
    setLayout(new Stack());
    setOpaque(framed);
    setBackground(BACKGROUND);
    setBorder(framed ? BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
        BorderFactory.createEmptyBorder(6, 8, 6, 8)) : BorderFactory.createEmptyBorder());
    for (TooltipLine line : lines) {
      add(row(line));
    }
    setVisible(!lines.isEmpty());
  }

  /** Every text component in line order (a tab-split line contributes its left, then its right label). */
  public List<JComponent> parts() {
    return List.copyOf(parts);
  }

  /** The text of every {@linkplain #parts part}, in order. */
  public List<String> texts() {
    return parts.stream().map(c -> c instanceof JLabel l ? l.getText() : ((JTextArea) c).getText()).toList();
  }

  /** The colour of every {@linkplain #parts part}, in order. */
  public List<Color> colours() {
    return parts.stream().map(Component::getForeground).toList();
  }

  /** The width the lines take unwrapped, insets included. */
  public int naturalWidth() {
    Insets in = getInsets();
    int width = 0;
    for (Component c : getComponents()) {
      width = Math.max(width, c instanceof LineText t ? t.naturalWidth() : c.getPreferredSize().width);
    }
    return width + in.left + in.right;
  }

  /** The height the lines take when laid out {@code width} wide, insets included. */
  public int heightFor(int width) {
    Insets in = getInsets();
    int inner = Math.max(1, width - in.left - in.right);
    int height = 0;
    for (Component c : getComponents()) {
      if (c.isVisible()) {
        height += Stack.heightOf(c, inner);
      }
    }
    return height + in.top + in.bottom;
  }

  private JComponent row(TooltipLine line) {
    Color colour = colour(line.colour());
    String text = line.text() == null ? "" : line.text();
    int tab = text.indexOf('\t');
    if (tab < 0) {
      LineText wrapped = new LineText(text, colour, Font.PLAIN, 0);
      parts.add(wrapped);
      return wrapped;
    }
    JPanel row = new JPanel(new BorderLayout(SPLIT_GAP, 0));
    row.setOpaque(false);
    row.add(label(text.substring(0, tab), colour), BorderLayout.CENTER); // truncates first on a narrow card
    JLabel right = label(text.substring(tab + 1), colour);
    right.setHorizontalAlignment(JLabel.TRAILING);
    row.add(right, BorderLayout.EAST);
    return row;
  }

  private JLabel label(String text, Color colour) {
    JLabel label = new JLabel(text);
    label.setForeground(colour);
    label.setToolTipText(text.isEmpty() ? null : text); // the whole text when it is truncated
    parts.add(label);
    return label;
  }

  /** {@code #rrggbb} as a colour; {@link #DEFAULT_TEXT} for null or anything else. */
  private static Color colour(String hex) {
    if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
      return DEFAULT_TEXT;
    }
    return new Color(Integer.parseInt(hex.substring(1), 16));
  }

  /**
   * One line of tooltip text that wraps at word boundaries: a read-only, transparent, unfocusable text area in the
   * label font (bold or larger by {@code style} and {@code sizeDelta}), kept across theme and text-size changes.
   */
  static final class LineText extends JTextArea {
    private int style;
    private float sizeDelta;

    LineText(String text, Color colour, int style, float sizeDelta) {
      super(text);
      this.style = style;
      this.sizeDelta = sizeDelta;
      setForeground(colour);
      setLineWrap(true);
      setWrapStyleWord(true);
      setEditable(false);
      setFocusable(false);
      setHighlighter(null); // no selection
      setCursor(Cursor.getDefaultCursor());
      setAlignmentX(LEFT_ALIGNMENT);
      plain();
    }

    @Override
    public void updateUI() {
      super.updateUI();
      plain();
    }

    /** No background, border or margin, in the (derived) label font. */
    private void plain() {
      setOpaque(false);
      setBorder(BorderFactory.createEmptyBorder());
      setMargin(new Insets(0, 0, 0, 0));
      Font label = UIManager.getFont("Label.font");
      if (label != null) {
        setFont(label.deriveFont(style, label.getSize2D() + sizeDelta));
      }
    }

    /** The width of the text on one line, insets included. */
    int naturalWidth() {
      Insets in = getInsets();
      return getFontMetrics(getFont()).stringWidth(getText()) + in.left + in.right;
    }

    /** The height of the text wrapped at {@code width}, insets included; leaves the bounds alone. */
    int heightFor(int width) {
      Insets in = getInsets();
      View root = getUI().getRootView(this);
      Document doc = getDocument();
      if (doc instanceof AbstractDocument d) {
        d.readLock();
      }
      try {
        root.setSize(Math.max(1, width - in.left - in.right), Integer.MAX_VALUE);
        return (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS)) + in.top + in.bottom;
      } finally {
        if (doc instanceof AbstractDocument d) {
          d.readUnlock();
        }
      }
    }

    @Override
    public Dimension getMinimumSize() { // may be narrowed to any width: it wraps
      return new Dimension(0, super.getMinimumSize().height);
    }
  }

  /**
   * The lines top to bottom, each as wide as the panel and as tall as its text wrapped at that width. Nothing is
   * cached, so heights follow the width of every layout.
   */
  private static final class Stack implements LayoutManager {
    @Override
    public void addLayoutComponent(String name, Component comp) {}

    @Override
    public void removeLayoutComponent(Component comp) {}

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      TooltipLinesPanel p = (TooltipLinesPanel) parent;
      int natural = p.naturalWidth();
      return new Dimension(natural, p.heightFor(p.getWidth() > 0 ? p.getWidth() : natural));
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      Insets in = parent.getInsets();
      return new Dimension(in.left + in.right, preferredLayoutSize(parent).height);
    }

    @Override
    public void layoutContainer(Container parent) {
      Insets in = parent.getInsets();
      int width = Math.max(0, parent.getWidth() - in.left - in.right);
      int y = in.top;
      for (Component c : parent.getComponents()) {
        if (c.isVisible()) {
          int height = heightOf(c, width);
          c.setBounds(in.left, y, width, height);
          y += height;
        }
      }
    }

    static int heightOf(Component c, int width) {
      return c instanceof LineText t ? t.heightFor(width) : c.getPreferredSize().height;
    }
  }
}
