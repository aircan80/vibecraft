package dev.wowforever.app.ui.detail;

import dev.wowforever.app.model.ZoneMap;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.swing.JComponent;

/**
 * A zone map scaled to the panel's width, keeping its aspect ratio and never enlarged beyond its own size. The
 * JPEG is decoded on first use; a map that does not decode paints nothing.
 */
public final class MapImagePanel extends JComponent {
  private final ZoneMap map;
  private BufferedImage image;
  private boolean decoded;

  public MapImagePanel(ZoneMap map) {
    this.map = Objects.requireNonNull(map, "map");
    setMinimumSize(new Dimension(0, 0)); // the map shrinks with the detail area rather than holding it open
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        if (getHeight() != heightFor(getWidth())) {
          revalidate(); // the preferred height follows the width
        }
      }
    });
  }

  public ZoneMap zoneMap() {
    return map;
  }

  /** The decoded map, or null when the JPEG does not decode. */
  public BufferedImage image() {
    if (!decoded) {
      decoded = true;
      image = Images.decode(map.jpeg()).orElse(null);
    }
    return image;
  }

  /** The height the map is drawn at in a panel {@code width} pixels wide. */
  public int heightFor(int width) {
    if (width <= 0 || map.width() <= 0) {
      return 0;
    }
    int drawnWidth = Math.min(width, map.width());
    return (int) Math.round((double) drawnWidth * map.height() / map.width());
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet()) {
      return super.getPreferredSize();
    }
    int width = getWidth() > 0 ? getWidth() : map.width();
    return new Dimension(width, heightFor(width));
  }

  @Override
  protected void paintComponent(Graphics g) {
    BufferedImage img = image();
    if (img == null || getWidth() <= 0) {
      return;
    }
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2.drawImage(img, 0, 0, Math.min(getWidth(), map.width()), heightFor(getWidth()), null);
    } finally {
      g2.dispose();
    }
  }
}
