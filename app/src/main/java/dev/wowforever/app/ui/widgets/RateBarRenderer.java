package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.theme.QualityColors;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

/**
 * Renders a drop rate (a {@link Number}, in percent) as its number, right-aligned ({@code "60.00 %"}), followed
 * by a {@value #BAR_WIDTH} × {@value #BAR_HEIGHT} px bar filled in proportion and coloured by
 * {@link QualityColors#rateBand}. An unknown (null) rate prints a muted {@code "—"} without a bar; the bar's
 * space is kept so numbers line up.
 */
public final class RateBarRenderer extends DefaultTableCellRenderer {
  static final int BAR_WIDTH = 48;
  static final int BAR_HEIGHT = 10;
  private static final int GAP = 8;
  private static final int PAD = 6;
  /** Preferred width of a rate column: the number, the gap and the bar. */
  public static final int COLUMN_WIDTH = 130;

  private Double pct;

  public RateBarRenderer() {
    setHorizontalAlignment(RIGHT);
  }

  /** Renders {@code column} with a rate bar and widens it to {@link #COLUMN_WIDTH}. */
  public static void install(TableColumn column) {
    column.setCellRenderer(new RateBarRenderer());
    column.setPreferredWidth(UIScale.scale(COLUMN_WIDTH));
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
      int row, int column) {
    pct = value instanceof Number n ? n.doubleValue() : null;
    super.getTableCellRendererComponent(table, Columns.percentText(pct), isSelected, hasFocus, row, column);
    setHorizontalAlignment(RIGHT);
    setBorder(BorderFactory.createCompoundBorder(getBorder(),
        BorderFactory.createEmptyBorder(0, 0, 0, UIScale.scale(GAP + BAR_WIDTH))));
    if (pct == null && !isSelected) {
      setForeground(Theme.current().textMuted());
    }
    return this;
  }

  /** The bar's colour for the last rendered rate; null when the rate is unknown (no bar). */
  Color barColor() {
    return pct == null ? null : QualityColors.rateBand(pct);
  }

  /** Where the bar's track sits: right of the number, vertically centred. */
  Rectangle barTrack() {
    int w = UIScale.scale(BAR_WIDTH);
    int h = UIScale.scale(BAR_HEIGHT);
    return new Rectangle(getWidth() - UIScale.scale(PAD) - w, (getHeight() - h) / 2, w, h);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (pct == null) {
      return;
    }
    Rectangle track = barTrack();
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setColor(Theme.current().bgRaised());
      g2.fillRect(track.x, track.y, track.width, track.height);
      int fill = pct <= 0 ? 0 : Math.max(2, (int) Math.round(track.width * Math.min(pct, 100.0) / 100.0));
      g2.setColor(barColor());
      g2.fillRect(track.x, track.y, fill, track.height);
    } finally {
      g2.dispose();
    }
  }
}
