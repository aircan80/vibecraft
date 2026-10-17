package dev.wowforever.app.ui.detail;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Decodes the PNG and JPEG blobs of the database in memory (no ImageIO disk cache). */
final class Images {
  private Images() {}

  /**
   * The decoded image, or empty when {@code bytes} is null or not an image ImageIO can read. Corrupt data never
   * throws: ImageIO plugins may fail with unchecked exceptions as well as {@link IOException}.
   */
  static Optional<BufferedImage> decode(byte[] bytes) {
    if (bytes == null) {
      return Optional.empty();
    }
    try { // ImageIO.read closes the stream itself
      return Optional.ofNullable(ImageIO.read(new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))));
    } catch (IOException | RuntimeException e) {
      return Optional.empty();
    }
  }
}
