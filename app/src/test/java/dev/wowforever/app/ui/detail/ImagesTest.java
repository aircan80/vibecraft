package dev.wowforever.app.ui.detail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.wowforever.app.db.FixtureDb;
import java.awt.image.BufferedImage;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class ImagesTest {
  private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

  @Test
  void decodesPngAndJpeg() {
    byte[] png = FixtureDb.repositories().assets().icon(134124).orElseThrow();
    assertThat(Images.decode(png)).hasValueSatisfying(i -> assertThat(i.getWidth()).isEqualTo(56));
    byte[] jpeg = FixtureDb.repositories().zones().map(40).orElseThrow().jpeg();
    assertThat(Images.decode(jpeg)).hasValueSatisfying(i -> assertThat(i.getWidth()).isEqualTo(1002));
  }

  @Test
  void nullAndGarbageDecodeToNothing() {
    assertThat(Images.decode(null)).isEmpty();
    assertThat(Images.decode(new byte[0])).isEmpty();
    byte[] garbage = new byte[4096];
    new Random(42).nextBytes(garbage);
    assertThat(Images.decode(garbage)).isEmpty();
  }

  @Test
  void corruptImagesNeverThrow() {
    Random random = new Random(7);
    byte[] corruptPng = Arrays.copyOf(PNG_SIGNATURE, 512); // a PNG signature followed by noise
    for (int i = PNG_SIGNATURE.length; i < corruptPng.length; i++) {
      corruptPng[i] = (byte) random.nextInt();
    }
    byte[] jpeg = FixtureDb.repositories().zones().map(40).orElseThrow().jpeg();
    byte[] truncatedJpeg = Arrays.copyOf(jpeg, 600);
    byte[] scrambledJpeg = jpeg.clone();
    for (int i = 200; i < scrambledJpeg.length; i += 7) {
      scrambledJpeg[i] = (byte) random.nextInt();
    }
    for (byte[] corrupt : new byte[][] {corruptPng, truncatedJpeg, scrambledJpeg}) {
      assertThatCode(() -> Images.decode(corrupt)).doesNotThrowAnyException();
    }
  }

  @Test
  void aPluginThatThrowsUncheckedWhileProbingDecodesToNothing() {
    // ImageIO swallows only IOException from canDecodeInput, so this one reaches Images.decode unchanged
    assertDecodesToNothingWith(new ThrowingReaderSpi(true));
  }

  @Test
  void aReaderThatThrowsUncheckedWhileReadingDecodesToNothing() {
    assertDecodesToNothingWith(new ThrowingReaderSpi(false));
  }

  private static void assertDecodesToNothingWith(ThrowingReaderSpi spi) {
    IIORegistry registry = IIORegistry.getDefaultInstance();
    registry.registerServiceProvider(spi, ImageReaderSpi.class);
    try {
      byte[] boom = Arrays.copyOf(ThrowingReaderSpi.MAGIC, 64);
      assertThatCode(() -> Images.decode(boom)).doesNotThrowAnyException();
      assertThat(Images.decode(boom)).isEmpty();
      assertThat(spi.failures).as("the throwing plugin was really used").isPositive();
    } finally {
      registry.deregisterServiceProvider(spi, ImageReaderSpi.class);
    }
  }

  /**
   * An ImageIO plugin for a made-up format that fails with an unchecked exception, as buggy plugins do: while
   * probing the input ({@code throwWhileProbing}) or while reading it.
   */
  private static final class ThrowingReaderSpi extends ImageReaderSpi {
    static final byte[] MAGIC = {'B', 'O', 'O', 'M', 'I', 'M', 'G', '!'};
    final boolean throwWhileProbing;
    int failures;

    ThrowingReaderSpi(boolean throwWhileProbing) {
      super("test", "1", new String[] {"boom"}, new String[] {"boom"}, new String[] {"image/x-boom"},
          ImageReader.class.getName(), new Class<?>[] {ImageInputStream.class}, null, false, null, null, null,
          null, false, null, null, null, null);
      this.throwWhileProbing = throwWhileProbing;
    }

    @Override
    public boolean canDecodeInput(Object source) throws IOException {
      ImageInputStream in = (ImageInputStream) source;
      byte[] head = new byte[MAGIC.length];
      in.mark();
      try {
        in.readFully(head);
      } catch (EOFException e) {
        return false;
      } finally {
        in.reset();
      }
      boolean ours = Arrays.equals(head, MAGIC);
      if (ours && throwWhileProbing) {
        failures++;
        throw new IllegalStateException("corrupt boom header");
      }
      return ours;
    }

    @Override
    public ImageReader createReaderInstance(Object extension) {
      return new ImageReader(this) {
        @Override
        public int getNumImages(boolean allowSearch) {
          return 1;
        }

        @Override
        public int getWidth(int imageIndex) {
          return 1;
        }

        @Override
        public int getHeight(int imageIndex) {
          return 1;
        }

        @Override
        public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) {
          return Collections.emptyIterator();
        }

        @Override
        public IIOMetadata getStreamMetadata() {
          return null;
        }

        @Override
        public IIOMetadata getImageMetadata(int imageIndex) {
          return null;
        }

        @Override
        public BufferedImage read(int imageIndex, ImageReadParam param) {
          failures++;
          throw new IllegalStateException("corrupt boom image");
        }
      };
    }

    @Override
    public String getDescription(Locale locale) {
      return "test reader that throws unchecked";
    }
  }
}
