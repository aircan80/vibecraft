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
import java.util.function.Predicate;
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
 * space is kept so numbers line up. A known rate whose headline source is VMaNGOS's (the row's {@code pct_source},
 * {@link #RateBarRenderer(Function, Predicate, Function) headlineSource}) is followed by {@link Columns#VANILLA},
 * painted muted ("Vanilla rate", decision 19). A rate whose sources disagree (a {@link ResultsTableModel} rate
 * column's {@linkplain ResultsTableModel#disagreesAt flag}) is followed by {@link Columns#DISAGREES}, painted in the
 * theme's {@linkplain Theme#warn() warning amber}. The number keeps the usual colour, and the glyphs stay, so colour
 * is never the only sign: {@code "9.00 % V ≠"}.
 *
 * <p>Given the drop rate sources of a row (loaded beforehand: nothing is queried here, on the EDT), the cell's
 * tooltip lists them ({@link #sourcesText(List, boolean)}): {@code "Questie 2.31 %  ·  AtlasLoot 1.10 %"}, a
 * quest-only VMaNGOS rate read "on quest", and on a dungeon or raid boss with a VMaNGOS rate a second line
 * {@value RateSource#BOSS_NOTE}.
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
  /**
   * After a quest-only VMaNGOS rate in the tooltip: the chance while the looter is on the quest, not per kill (a
   * different quantity from the other sources' rates).
   */
  static final String ON_QUEST = " on quest";
  /** Between the tooltip's lines (FlatLaf shows a tooltip with line breaks as several lines). */
  static final String LINE_BREAK = "\n";

  private final Function<Object, List<RateSource>> sourcesOf;
  private final Predicate<Object> onBoss;
  private final Function<Object, String> headlineSource;
  private Double pct;
  /** Whether the last rendered rate ends in {@link Columns#DISAGREES}. */
  private boolean marked;
  /** Whether the last rendered rate carries {@link Columns#VANILLA}. */
  private boolean vanilla;
  /** Whether the last rendered cell is selected (its glyphs then take the selection's colour, bar the amber). */
  private boolean selected;
  /** While a marked rate paints its number: {@link #getText()} leaves the markers out, which are painted apart. */
  private boolean numberOnly;

  /** A renderer without tooltips. */
  public RateBarRenderer() {
    this(row -> null);
  }

  /**
   * A renderer whose cells' tooltips list the drop rate sources {@code sourcesOf} gives for a row of the
   * {@link ResultsTableModel} (null or empty: no tooltip). It is called while rendering, so it must only look up
   * sources loaded beforehand. No row is a boss, and no headline is marked.
   */
  public RateBarRenderer(Function<Object, List<RateSource>> sourcesOf) {
    this(sourcesOf, row -> false, row -> null);
  }

  /**
   * As {@link #RateBarRenderer(Function)}; {@code onBoss} tells whether a row's NPC is a dungeon or raid boss (its
   * VMaNGOS rates add {@value RateSource#BOSS_NOTE}) and {@code headlineSource} gives the source of a row's headline
   * rate ({@code pct_source}; {@value RateSource#VMANGOS} marks it with {@link Columns#VANILLA}). Both are called
   * while rendering, so they must only look up what was loaded beforehand.
   */
  public RateBarRenderer(Function<Object, List<RateSource>> sourcesOf, Predicate<Object> onBoss,
      Function<Object, String> headlineSource) {
    this.sourcesOf = Objects.requireNonNull(sourcesOf, "sourcesOf");
    this.onBoss = Objects.requireNonNull(onBoss, "onBoss");
    this.headlineSource = Objects.requireNonNull(headlineSource, "headlineSource");
    setHorizontalAlignment(RIGHT);
  }

  /** Renders {@code column} with a rate bar and widens it to {@link #COLUMN_WIDTH}. */
  public static void install(TableColumn column) {
    install(column, row -> null);
  }

  /** {@link #install(TableColumn)} with tooltips listing the sources {@code sourcesOf} gives for a row. */
  public static void install(TableColumn column, Function<Object, List<RateSource>> sourcesOf) {
    install(column, sourcesOf, row -> false, row -> null);
  }

  /**
   * {@link #install(TableColumn, Function)}, a row's boss status from {@code onBoss} and its headline source from
   * {@code headlineSource} ({@link #RateBarRenderer(Function, Predicate, Function)}).
   */
  public static void install(TableColumn column, Function<Object, List<RateSource>> sourcesOf,
      Predicate<Object> onBoss, Function<Object, String> headlineSource) {
    column.setCellRenderer(new RateBarRenderer(sourcesOf, onBoss, headlineSource));
    column.setPreferredWidth(UIScale.scale(COLUMN_WIDTH));
  }

  /**
   * {@code "Questie 2.31 %  ·  AtlasLoot 1.10 %"}: each source and its rate ("no rate" when it gave none), in
   * {@link RateSource#ORDER}; null for no sources. As {@link #sourcesText(List, boolean)} for a row that is no boss.
   */
  public static String sourcesText(List<RateSource> sources) {
    return sourcesText(sources, false);
  }

  /**
   * Each source and its rate ("no rate" when it gave none), in {@link RateSource#ORDER}, a quest-only rate followed
   * by {@value #ON_QUEST}: {@code "Questie 84.00 %  ·  Vanilla rate (VMaNGOS) 100.00 % on quest"}. On a
   * {@code boss} with a VMaNGOS source, a second line reads {@value RateSource#BOSS_NOTE}. Null for no sources.
   */
  public static String sourcesText(List<RateSource> sources, boolean boss) {
    if (sources == null || sources.isEmpty()) {
      return null;
    }
    String line = sources.stream().sorted(RateSource.ORDER)
        .map(s -> s.label() + " " + (s.pct() == null ? NO_RATE : Columns.percentText(s.pct()))
            + (s.questOnly() ? ON_QUEST : ""))
        .collect(Collectors.joining(SOURCE_SEPARATOR));
    return boss && sources.stream().anyMatch(RateSource::vanilla) ? line + LINE_BREAK + RateSource.BOSS_NOTE : line;
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
      int row, int column) {
    pct = value instanceof Number n ? n.doubleValue() : null;
    marked = disagrees(table, row, column);
    Object modelRow = modelRow(table, row);
    vanilla = pct != null && modelRow != null && RateSource.VMANGOS.equals(headlineSource.apply(modelRow));
    selected = isSelected;
    super.getTableCellRendererComponent(table, Columns.rateText(pct, vanilla, marked), isSelected, hasFocus, row,
        column);
    setToolTipText(modelRow == null ? null : sourcesText(sourcesOf.apply(modelRow), onBoss.test(modelRow)));
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

  /** The {@link ResultsTableModel} row of view row {@code viewRow}; null outside one. */
  private static Object modelRow(JTable table, int viewRow) {
    if (table == null || !(table.getModel() instanceof ResultsTableModel<?> model) || viewRow < 0
        || viewRow >= table.getRowCount()) {
      return null;
    }
    return model.rowAt(table.convertRowIndexToModel(viewRow));
  }

  @Override
  public String getText() {
    return numberOnly ? Columns.percentText(pct) : super.getText();
  }

  /**
   * Where the disagreement marker of the last rendered rate is painted, the full height of the cell: the end of the
   * right-aligned text, left of the bar. Null without a marker, or when the text does not fit (the label then cuts it
   * short, markers and all, in the usual colour).
   */
  Rectangle markerBounds() {
    if (!marked || !fits()) {
      return null;
    }
    int w = getFontMetrics(getFont()).stringWidth(Columns.DISAGREES);
    return new Rectangle(getWidth() - getInsets().right - w, 0, w, getHeight());
  }

  /**
   * Where the {@link Columns#VANILLA} marker of the last rendered rate is painted, the full height of the cell: right
   * after the number, before any disagreement marker. Null without one, or when the text does not fit.
   */
  Rectangle vanillaBounds() {
    if (!vanilla || !fits()) {
      return null;
    }
    FontMetrics fm = getFontMetrics(getFont());
    int after = marked ? fm.stringWidth(Columns.DISAGREES) : 0;
    int w = fm.stringWidth(Columns.VANILLA);
    return new Rectangle(getWidth() - getInsets().right - after - w, 0, w, getHeight());
  }

  /** Whether the whole text (number and markers) fits the cell. */
  private boolean fits() {
    Insets in = getInsets();
    return getFontMetrics(getFont()).stringWidth(super.getText()) <= getWidth() - in.left - in.right;
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

  /**
   * The background, the number moved left by the markers' width in the usual colour, then the V muted (the
   * selection's colour on a selected row) and the disagreement marker in amber.
   */
  private void paintMarked(Graphics g, Rectangle v, Rectangle ne) {
    if (isOpaque()) {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());
    }
    int baseline = getBaseline(getWidth(), getHeight());
    int shift = (v == null ? 0 : v.width) + (ne == null ? 0 : ne.width);
    Graphics2D number = (Graphics2D) g.create();
    numberOnly = true;
    try {
      number.translate(-shift, 0);
      ui.paint(number, this);
    } finally {
      numberOnly = false;
      number.dispose();
    }
    if (v != null) {
      glyph(g, Columns.VANILLA, selected ? getForeground() : Theme.current().textMuted(), v.x, baseline);
    }
    if (ne != null) {
      glyph(g, Columns.DISAGREES, Theme.current().warn(), ne.x, baseline);
    }
  }

  private void glyph(Graphics g, String text, Color color, int x, int baseline) {
    Graphics2D glyph = (Graphics2D) g.create();
    try {
      glyph.setColor(color);
      glyph.setFont(getFont());
      FlatUIUtils.drawString(this, glyph, text, x, baseline);
    } finally {
      glyph.dispose();
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    Rectangle v = vanillaBounds();
    Rectangle ne = markerBounds();
    if (v == null && ne == null) {
      super.paintComponent(g);
    } else {
      paintMarked(g, v, ne);
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
