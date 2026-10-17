package dev.wowforever.app.ui.widgets;

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
 * A zone map, already decoded (off the EDT, by {@link DetailPanels#load}), scaled to the panel's width, keeping
 * its aspect ratio and never enlarged beyond its own size.
 */
public final class MapImagePanel extends JComponent {
  private final BufferedImage image;

  public MapImagePanel(BufferedImage image) {
    this.image = Objects.requireNonNull(image, "image");
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

  public BufferedImage image() {
    return image;
  }

  /** The height the map is drawn at in a panel {@code width} pixels wide. */
  public int heightFor(int width) {
    if (width <= 0 || image.getWidth() <= 0) {
      return 0;
    }
    int drawnWidth = Math.min(width, image.getWidth());
    return (int) Math.round((double) drawnWidth * image.getHeight() / image.getWidth());
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet()) {
      return super.getPreferredSize();
    }
    int width = getWidth() > 0 ? getWidth() : image.getWidth();
    return new Dimension(width, heightFor(width));
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (getWidth() <= 0) {
      return;
    }
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2.drawImage(image, 0, 0, Math.min(getWidth(), image.getWidth()), heightFor(getWidth()), null);
    } finally {
      g2.dispose();
    }
  }
}
