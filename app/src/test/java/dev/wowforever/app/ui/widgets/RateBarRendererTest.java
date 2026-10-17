package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.LootRow;
import dev.wowforever.app.model.RateSource;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.ResultsTableModel;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.theme.Tokens;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.TableColumn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RateBarRendererTest {
  private final JTable table = new JTable(1, 1);

  private RateBarRenderer render(Double pct) {
    RateBarRenderer r = new RateBarRenderer();
    r.getTableCellRendererComponent(table, pct, false, false, 0, 0);
    return r;
  }

  @Test
  void printsTheRateRightAligned() {
    RateBarRenderer r = render(60.0);
    assertThat(r.getText()).isEqualTo("60.00 %");
    assertThat(r.getHorizontalAlignment()).isEqualTo(SwingConstants.RIGHT);
  }

  @Test
  void unknownRateIsAMutedDashWithoutABar() {
    RateBarRenderer r = render(null);
    assertThat(r.getText()).isEqualTo("—");
    assertThat(r.barPaint()).as("no bar").isNull();
    assertThat(r.getForeground()).isEqualTo(Theme.current().textMuted());
  }

  @Test
  void barIsSixPxGradientWithTicks() {
    RateBarRenderer r = render(50.0);
    int w = UIScale.scale(140);
    int h = UIScale.scale(20);
    r.setSize(w, h);
    Rectangle track = r.barTrack();
    assertThat(RateBarRenderer.BAR_HEIGHT).isEqualTo(6);
    assertThat(track.height).isEqualTo(UIScale.scale(6));
    assertThat(track.width).isEqualTo(UIScale.scale(48));
    assertThat(track.x + track.width).isLessThanOrEqualTo(w);
    assertThat(r.barPaint()).isNotNull();
    assertThat(r.getText()).as("the number is unchanged").isEqualTo("50.00 %");
    BufferedImage img = paint(r, w, h);
    int mid = track.y + track.height / 2;
    int fillEnd = track.x + track.width / 2 - 1;
    Color left = new Color(img.getRGB(track.x + 1, mid));
    Color right = new Color(img.getRGB(fillEnd - 1, mid));
    assertThat(left).as("a gradient: the fill's left end").isNotEqualTo(right);
    Theme t = Theme.current();
    assertThat(distance(left, t.barStart())).as("starts dark gold").isLessThan(distance(left, t.barEnd()));

    RateBarRenderer low = render(5.0); // the 10 % tick sits on the empty track
    low.setSize(w, h);
    low.setOpaque(false);
    BufferedImage lowImg = paint(low, w, h);
    int tick = track.x + Math.round(track.width * 0.1f);
    int tickPixel = lowImg.getRGB(tick, mid);
    assertThat(tickPixel >>> 24).as("a tick at 10 %").isPositive();
    assertThat(new Color(tickPixel)).as("the tick differs from the plain track")
        .isNotEqualTo(new Color(lowImg.getRGB(tick + 2, mid)));
    assertThat(new Color(lowImg.getRGB(tick + 2, mid))).isEqualTo(t.bgRaised());
  }

  @Test
  void numberTextIsUnchanged() {
    assertThat(render(60.0).getText()).isEqualTo("60.00 %");
  }

  private static BufferedImage paint(RateBarRenderer r, int w, int h) {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    r.paint(g);
    g.dispose();
    return img;
  }

  /** A one-row table of {@code row} over the loot columns, with the rate column given {@code renderer}. */
  private static JTable lootTable(LootRow row, RateBarRenderer renderer) {
    ResultsTableModel<LootRow> model = new ResultsTableModel<>(Columns.lootRated());
    model.setRows(List.of(row));
    JTable t = new JTable(model);
    t.getColumnModel().getColumn(rateColumn(t)).setCellRenderer(renderer);
    return t;
  }

  private static int rateColumn(JTable t) {
    return t.getColumnModel().getColumnIndex(Columns.RATE);
  }

  private static LootRow loot(long id, Double pct, boolean disagrees, int sourceCount) {
    return new LootRow(new EntityRef(EntityKind.ITEM, id), "Item " + id, 10, pct, "both", null, disagrees,
        sourceCount);
  }

  /** Paints {@code r} as the rate cell of row 0 of {@code t}, {@code w} × {@code h} px, on a plain background. */
  private static BufferedImage paintCell(JTable t, RateBarRenderer r, int w, int h) {
    r.getTableCellRendererComponent(t, t.getValueAt(0, rateColumn(t)), false, false, 0, rateColumn(t));
    r.setSize(w, h);
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    r.paint(g);
    g.dispose();
    return img;
  }

  /** How many pixels of {@code img} left of {@code right} are nearer {@code c} than the background ({@code bg}). */
  private static int pixelsNear(BufferedImage img, int left, int right, Color c, Color bg) {
    int n = 0;
    for (int x = left; x < right; x++) {
      for (int y = 0; y < img.getHeight(); y++) {
        Color p = new Color(img.getRGB(x, y));
        if (distance(p, c) < 60 && distance(p, c) < distance(p, bg)) {
          n++;
        }
      }
    }
    return n;
  }

  private static double distance(Color a, Color b) {
    int dr = a.getRed() - b.getRed();
    int dg = a.getGreen() - b.getGreen();
    int db = a.getBlue() - b.getBlue();
    return Math.sqrt(dr * dr + dg * dg + db * db);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void theDisagreementMarkerAloneIsPaintedInTheWarningColour(boolean dark) {
    Theme saved = Theme.current();
    Theme.setCurrent(dark ? Tokens.DARK : Tokens.PARCHMENT);
    try {
      assertMarkerAloneIsAmber();
    } finally {
      Theme.setCurrent(saved);
    }
  }

  private void assertMarkerAloneIsAmber() {
    RateBarRenderer r = new RateBarRenderer();
    JTable t = lootTable(loot(1, 60.0, true, 2), r);
    int w = UIScale.scale(200);
    int h = UIScale.scale(24);
    BufferedImage img = paintCell(t, r, w, h);
    assertThat(r.getText()).as("the glyph stays: colour is never the only sign").isEqualTo("60.00 % ≠");
    Color bg = r.getBackground();
    Color warn = Theme.current().warn();
    Rectangle marker = r.markerBounds();
    assertThat(marker).isNotNull();
    assertThat(marker.x + marker.width).isLessThanOrEqualTo(r.barTrack().x);
    assertThat(pixelsNear(img, marker.x, marker.x + marker.width, warn, bg)).as("amber marker").isPositive();
    assertThat(pixelsNear(img, 0, marker.x, warn, bg)).as("the number keeps its colour").isZero();
    assertThat(pixelsNear(img, 0, marker.x, r.getForeground(), bg)).as("the number is painted").isPositive();
    assertThat(pixelsNear(img, marker.x, marker.x + marker.width, r.getForeground(), bg)).isZero();
  }

  @Test
  void anAgreedRateHasNoMarkerAndNoWarningColour() {
    RateBarRenderer r = new RateBarRenderer();
    JTable t = lootTable(loot(1, 60.0, false, 2), r);
    BufferedImage img = paintCell(t, r, UIScale.scale(200), UIScale.scale(24));
    assertThat(r.getText()).isEqualTo("60.00 %");
    assertThat(r.markerBounds()).isNull();
    assertThat(pixelsNear(img, 0, r.barTrack().x, Theme.current().warn(), r.getBackground())).isZero();
  }

  @Test
  void theWarningColourIsAmberInBothThemes() {
    assertThat(Tokens.DARK.warn()).isEqualTo(new Color(0xE0A83A));
    assertThat(Tokens.PARCHMENT.warn()).isEqualTo(new Color(0x8A5A00));
  }

  @Test
  void sourcesTextListsEachSourceQuestieFirst() {
    assertThat(RateBarRenderer.sourcesText(List.of(new RateSource("questie-wowhead", 2.31),
        new RateSource("atlasloot", 1.1)))).isEqualTo("Questie 2.31 %  ·  AtlasLoot 1.10 %");
    assertThat(RateBarRenderer.sourcesText(List.of(new RateSource("atlasloot", null),
        new RateSource("questie", 5.0)))).isEqualTo("Questie 5.00 %  ·  AtlasLoot no rate");
    assertThat(RateBarRenderer.sourcesText(List.of())).isNull();
    assertThat(RateBarRenderer.sourcesText(null)).isNull();
  }

  @Test
  void aRateCellWithSourcesHasThemAsItsTooltip() {
    Map<Long, List<RateSource>> byItem = Map.of(1L,
        List.of(new RateSource("questie-wowhead", 2.31), new RateSource("atlasloot", 1.1)),
        3L, List.of(new RateSource("atlasloot", null)));
    RateBarRenderer r = new RateBarRenderer(row -> byItem.get(((LootRow) row).item().id()));
    ResultsTableModel<LootRow> model = new ResultsTableModel<>(Columns.lootRated());
    model.setRows(List.of(loot(1, 2.31, false, 2), loot(2, 5.0, false, 0), loot(3, null, false, 1),
        loot(4, 1.0, false, 3)));
    JTable t = new JTable(model);
    int col = rateColumn(t);
    r.getTableCellRendererComponent(t, t.getValueAt(0, col), false, false, 0, col);
    assertThat(r.getToolTipText()).isEqualTo("Questie 2.31 %  ·  AtlasLoot 1.10 %");
    r.getTableCellRendererComponent(t, t.getValueAt(1, col), false, false, 1, col);
    assertThat(r.getToolTipText()).as("no sources: no tooltip").isNull();
    r.getTableCellRendererComponent(t, t.getValueAt(2, col), false, false, 2, col);
    assertThat(r.getToolTipText()).isEqualTo("AtlasLoot no rate");
    r.getTableCellRendererComponent(t, t.getValueAt(3, col), false, false, 3, col);
    assertThat(r.getToolTipText()).as("sources not loaded: no tooltip").isNull();
  }

  @Test
  void installWidensTheColumnForNumberAndBar() {
    TableColumn column = new TableColumn(0);
    RateBarRenderer.install(column);
    assertThat(column.getCellRenderer()).isInstanceOf(RateBarRenderer.class);
    assertThat(column.getPreferredWidth()).isEqualTo(UIScale.scale(RateBarRenderer.COLUMN_WIDTH));
  }
}
