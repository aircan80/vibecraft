package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.ui.theme.Theme;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.TableColumn;
import org.junit.jupiter.api.Test;

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
  void barTakesTheBandColour() {
    assertThat(render(30.0).barColor()).isEqualTo(new Color(0x7FBF6A));
    assertThat(render(10.0).barColor()).isEqualTo(new Color(0xC9B458));
    assertThat(render(3.0).barColor()).isEqualTo(new Color(0xC98A4E));
    assertThat(render(0.5).barColor()).isEqualTo(new Color(0xB3625A));
  }

  @Test
  void unknownRateIsAMutedDashWithoutABar() {
    RateBarRenderer r = render(null);
    assertThat(r.getText()).isEqualTo("—");
    assertThat(r.barColor()).isNull();
    assertThat(r.getForeground()).isEqualTo(Theme.current().textMuted());
  }

  @Test
  void paintsTheBarInTheBandColourRightOfTheNumber() {
    RateBarRenderer r = render(30.0);
    r.setSize(140, 20);
    Rectangle track = r.barTrack();
    assertThat(track.width).isEqualTo(48);
    assertThat(track.height).isEqualTo(10);
    assertThat(track.x + track.width).isLessThanOrEqualTo(140);
    BufferedImage img = new BufferedImage(140, 20, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    r.paint(g);
    g.dispose();
    assertThat(new Color(img.getRGB(track.x + 2, track.y + track.height / 2))).isEqualTo(new Color(0x7FBF6A));
  }

  @Test
  void installWidensTheColumnForNumberAndBar() {
    TableColumn column = new TableColumn(0);
    RateBarRenderer.install(column);
    assertThat(column.getCellRenderer()).isInstanceOf(RateBarRenderer.class);
    assertThat(column.getPreferredWidth()).isEqualTo(RateBarRenderer.COLUMN_WIDTH);
  }
}
