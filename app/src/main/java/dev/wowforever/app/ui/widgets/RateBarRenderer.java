package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.RateSource;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.ResultsTableModel;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

/**
 * Renders a drop rate (a {@link Number}, in percent) as its number, right-aligned ({@code "60.00 %"}), followed
 * by a {@value #BAR_WIDTH} × {@value #BAR_HEIGHT} px bar on {@code bg.raised}, filled in proportion with the
 * theme's dark-gold to gold gradient ({@link #barPaint}) and marked by faint ticks every 10 %. The number carries
 * the rate; the bar only shows it. An unknown (null) rate prints a muted {@code "—"} without a bar; the bar's
 * space is kept so numbers line up. A rate whose sources disagree (a {@link ResultsTableModel} rate column's
 * {@linkplain ResultsTableModel#disagreesAt flag}) is followed by {@link Columns#DISAGREES}, the marker alone painted
 * in the theme's {@linkplain Theme#warn() warning amber} (the glyph stays, so colour is never the only sign).
 *
 * <p>Given the drop rate sources of a row ({@link #RateBarRenderer(Function)}, loaded beforehand: nothing is queried
 * here, on the EDT), the cell's tooltip lists them: {@code "Questie 2.31 %  ·  AtlasLoot 1.10 %"}.
 */
public final class RateBarRenderer extends DefaultTableCellRenderer {
  static final int BAR_WIDTH = 48;
  /** The bar's height, in logical pixels. */
  public static final int BAR_HEIGHT = 6;
  /** The alpha of the 10 % ticks, drawn in {@code text.muted}. */
  private static final int TICK_ALPHA = 80;
  private static final int GAP = 8;
  private static final int PAD = 6;
  /** Preferred width of a rate column: the number, the gap and the bar. */
  public static final int COLUMN_WIDTH = 130;

  /** Between two sources in the tooltip. */
  static final String SOURCE_SEPARATOR = "  ·  ";
  /** A source's value in the tooltip when it gave no rate. */
  static final String NO_RATE = "no rate";

  private final Function<Object, List<RateSource>> sourcesOf;
  private Double pct;
  /** Whether the last rendered rate ends in {@link Columns#DISAGREES}. */
  private boolean marked;
  /** While a marked rate paints its number: {@link #getText()} leaves the marker out, which is painted on its own. */
  private boolean numberOnly;

  /** A renderer without tooltips. */
  public RateBarRenderer() {
    this(row -> null);
  }

  /**
   * A renderer whose cells' tooltips list the drop rate sources {@code sourcesOf} gives for a row of the
   * {@link ResultsTableModel} (null or empty: no tooltip). It is called while rendering, so it must only look up
   * sources loaded beforehand.
   */
  public RateBarRenderer(Function<Object, List<RateSource>> sourcesOf) {
    this.sourcesOf = Objects.requireNonNull(sourcesOf, "sourcesOf");
    setHorizontalAlignment(RIGHT);
  }

  /** Renders {@code column} with a rate bar and widens it to {@link #COLUMN_WIDTH}. */
  public static void install(TableColumn column) {
    install(column, row -> null);
  }

  /** {@link #install(TableColumn)} with tooltips listing the sources {@code sourcesOf} gives for a row. */
  public static void install(TableColumn column, Function<Object, List<RateSource>> sourcesOf) {
    column.setCellRenderer(new RateBarRenderer(sourcesOf));
    column.setPreferredWidth(UIScale.scale(COLUMN_WIDTH));
  }

  /**
   * {@code "Questie 2.31 %  ·  AtlasLoot 1.10 %"}: each source and its rate ("no rate" when it gave none), in
   * {@link RateSource#ORDER}; null for no sources.
   */
  public static String sourcesText(List<RateSource> sources) {
    if (sources == null || sources.isEmpty()) {
      return null;
    }
    return sources.stream().sorted(RateSource.ORDER)
        .map(s -> s.label() + " " + (s.pct() == null ? NO_RATE : Columns.percentText(s.pct())))
        .collect(Collectors.joining(SOURCE_SEPARATOR));
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
      int row, int column) {
    pct = value instanceof Number n ? n.doubleValue() : null;
    marked = disagrees(table, row, column);
    super.getTableCellRendererComponent(table, Columns.rateText(pct, marked), isSelected, hasFocus, row, column);
    setToolTipText(sourcesText(sources(table, row)));
    setHorizontalAlignment(RIGHT);
    setBorder(BorderFactory.createCompoundBorder(getBorder(),
        BorderFactory.createEmptyBorder(0, 0, 0, UIScale.scale(GAP + BAR_WIDTH))));
    if (pct == null && !isSelected) {
      setForeground(Theme.current().textMuted());
    }
    return this;
  }

  /** Whether the cell's rate has sources that disagree (false outside a {@link ResultsTableModel}). */
  private static boolean disagrees(JTable table, int viewRow, int viewColumn) {
    if (table == null || !(table.getModel() instanceof ResultsTableModel<?> model) || viewRow < 0 || viewColumn < 0) {
      return false;
    }
    return model.disagreesAt(table.convertRowIndexToModel(viewRow), table.convertColumnIndexToModel(viewColumn));
  }

  /** The loaded sources of view row {@code viewRow}'s rate; null outside a {@link ResultsTableModel}. */
  private List<RateSource> sources(JTable table, int viewRow) {
    if (table == null || !(table.getModel() instanceof ResultsTableModel<?> model) || viewRow < 0
        || viewRow >= table.getRowCount()) {
      return null;
    }
    return sourcesOf.apply(model.rowAt(table.convertRowIndexToModel(viewRow)));
  }

  @Override
  public String getText() {
    String text = super.getText();
    return numberOnly && text != null && text.endsWith(Columns.DISAGREES)
        ? text.substring(0, text.length() - Columns.DISAGREES.length()) : text;
  }

  /**
   * Where the disagreement marker of the last rendered rate is painted, the full height of the cell: the end of the
   * right-aligned text, left of the bar. Null without a marker, or when the text does not fit (the label then cuts it
   * short, marker and all, in the usual colour).
   */
  Rectangle markerBounds() {
    if (!marked) {
      return null;
    }
    Insets in = getInsets();
    FontMetrics fm = getFontMetrics(getFont());
    if (fm.stringWidth(super.getText()) > getWidth() - in.left - in.right) {
      return null;
    }
    int w = fm.stringWidth(Columns.DISAGREES);
    return new Rectangle(getWidth() - in.right - w, 0, w, getHeight());
  }

  /**
   * The fill of the last rendered rate's bar: a gradient from {@link Theme#barStart()} at the track's left end to
   * {@link Theme#barEnd()} at its right end, so a fuller bar reaches a brighter gold; null when the rate is unknown
   * (no bar).
   */
  public Paint barPaint() {
    if (pct == null) {
      return null;
    }
    Theme t = Theme.current();
    Rectangle track = barTrack();
    return new GradientPaint(track.x, 0, t.barStart(), track.x + track.width, 0, t.barEnd());
  }

  /** Where the bar's track sits: right of the number, vertically centred. */
  Rectangle barTrack() {
    int w = UIScale.scale(BAR_WIDTH);
    int h = UIScale.scale(BAR_HEIGHT);
    return new Rectangle(getWidth() - UIScale.scale(PAD) - w, (getHeight() - h) / 2, w, h);
  }

  /** The background, the number moved left by the marker's width in the usual colour, then the marker in amber. */
  private void paintMarked(Graphics g, Rectangle marker) {
    if (isOpaque()) {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());
    }
    int baseline = getBaseline(getWidth(), getHeight());
    Graphics2D number = (Graphics2D) g.create();
    numberOnly = true;
    try {
      number.translate(-marker.width, 0);
      ui.paint(number, this);
    } finally {
      numberOnly = false;
      number.dispose();
    }
    Graphics2D glyph = (Graphics2D) g.create();
    try {
      glyph.setColor(Theme.current().warn());
      glyph.setFont(getFont());
      FlatUIUtils.drawString(this, glyph, Columns.DISAGREES, marker.x, baseline);
    } finally {
      glyph.dispose();
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    Rectangle marker = markerBounds();
    if (marker == null) {
      super.paintComponent(g);
    } else {
      paintMarked(g, marker);
    }
    if (pct == null) {
      return;
    }
    Rectangle track = barTrack();
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      Theme t = Theme.current();
      g2.setColor(t.bgRaised());
      g2.fillRect(track.x, track.y, track.width, track.height);
      int fill = pct <= 0 ? 0 : Math.max(2, (int) Math.round(track.width * Math.min(pct, 100.0) / 100.0));
      g2.setPaint(barPaint());
      g2.fillRect(track.x, track.y, fill, track.height);
      Color muted = t.textMuted();
      g2.setColor(new Color(muted.getRed(), muted.getGreen(), muted.getBlue(), TICK_ALPHA));
      for (int tenth = 1; tenth < 10; tenth++) {
        g2.fillRect(track.x + Math.round(track.width * tenth / 10f), track.y, 1, track.height);
      }
    } finally {
      g2.dispose();
    }
  }
}
