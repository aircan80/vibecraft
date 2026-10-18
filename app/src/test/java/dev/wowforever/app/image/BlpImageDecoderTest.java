package dev.wowforever.app.image;

import static org.assertj.core.api.Assertions.*;
import java.io.IOException;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Authored format vectors, never Blizzard payloads. Pixel expectations are independent BC/palette values. */
class BlpImageDecoderTest {
  @TempDir Path temporary;
  @Test void paletteIgnoresReservedAlphaWhenNoPlaneAndUsesBgraChannelOrder() throws Exception {
    var image=BlpImageDecoder.decode(blp2(2,1,1,0,0,new byte[]{4,1}));
    assertThat(image.getRGB(0,0)).isEqualTo(0xff112233);assertThat(image.getRGB(1,0)).isEqualTo(0xffff0000);
    assertThat(image.isAlphaPremultiplied()).isFalse();
  }
  @Test void oneBitPaletteAlphaIsLittleEndianAcrossByteBoundaries() throws Exception {
    byte[] pixels=new byte[11];Arrays.fill(pixels,0,9,(byte)4);pixels[9]=0x55;pixels[10]=1;
    var image=BlpImageDecoder.decode(blp2(9,1,1,1,0,pixels));
    for(int x=0;x<9;x++)assertThat(image.getRGB(x,0)>>>24).isEqualTo(x%2==0?255:0);
  }
  @Test void fourBitAlphaUsesLowNibbleFirstIncludingOddFinalPixel() throws Exception {
    var image=BlpImageDecoder.decode(blp2(3,1,1,4,0,new byte[]{4,4,4,(byte)0xf0,7}));
    assertThat(image.getRGB(0,0)).isEqualTo(0x00112233);assertThat(image.getRGB(1,0)).isEqualTo(0xff112233);
    assertThat(image.getRGB(2,0)).isEqualTo(0x77112233);
  }
  @Test void eightBitPaletteAlphaComesFromSeparatePlane() throws Exception {
    var image=BlpImageDecoder.decode(blp2(2,1,1,8,0,new byte[]{4,4,0,(byte)193}));
    assertThat(image.getRGB(0,0)).isEqualTo(0x00112233);assertThat(image.getRGB(1,0)).isEqualTo(0xc1112233);
  }
  @Test void dxt1FourColorInterpolationAnd565Expansion() throws Exception {
    var image=BlpImageDecoder.decode(blp2(4,4,2,0,0,colorBlock(0xf800,0x07e0,0,1,2,3)));
    assertThat(image.getRGB(0,0)).isEqualTo(0xffff0000);assertThat(image.getRGB(1,0)).isEqualTo(0xff00ff00);
    assertThat(image.getRGB(2,0)).isEqualTo(0xffaa5500);assertThat(image.getRGB(3,0)).isEqualTo(0xff55aa00);
    var expanded=BlpImageDecoder.decode(blp2(1,1,2,0,0,colorBlock(0x8410,0,0)));
    assertThat(expanded.getRGB(0,0)).isEqualTo(0xff848284);
  }
  @Test void dxt1ThreeColorAlphaIsControlledByHeader() throws Exception {
    byte[] colors=colorBlock(0,0xffff,0,1,2,3);
    var transparent=BlpImageDecoder.decode(blp2(4,4,2,1,0,colors));
    assertThat(transparent.getRGB(2,0)).isEqualTo(0xff7f7f7f);assertThat(transparent.getRGB(3,0)).isZero();
    var opaque=BlpImageDecoder.decode(blp2(4,4,2,0,0,colors));
    assertThat(opaque.getRGB(3,0)).isEqualTo(0xff000000);
  }
  @Test void dxt3AlwaysUsesFourColorsAndExplicitFourBitAlpha() throws Exception {
    byte[] block=new byte[16];for(int i=0;i<8;i++)block[i]=(byte)((i*2)|((i*2+1)<<4));
    System.arraycopy(colorBlock(0x001f,0xf800,2,3),0,block,8,8);
    var image=BlpImageDecoder.decode(blp2(4,4,2,8,1,block));
    assertThat(image.getRGB(0,0)).isEqualTo(0x005500aa);assertThat(image.getRGB(1,0)).isEqualTo(0x11aa0055);
    assertThat(image.getRGB(3,3)>>>24).isEqualTo(255);
    assertThat(BlpImageDecoder.decode(blp2(4,4,2,4,1,block)).getRGB(1,0)).isEqualTo(image.getRGB(1,0));
  }
  @Test void dxt5BothAlphaBranchesAndSelectorsCrossingBytes() throws Exception {
    for(int[] endpoints:new int[][]{{255,0},{0,255}}) {
      int[] expected=endpoints[0]==255?new int[]{255,0,218,182,145,109,72,36}:new int[]{0,255,51,102,153,204,0,255};
      byte[] block=new byte[16];block[0]=(byte)endpoints[0];block[1]=(byte)endpoints[1];long selectors=0;
      for(int i=0;i<16;i++)selectors|=(long)(i%8)<<(i*3);
      for(int i=0;i<6;i++)block[2+i]=(byte)(selectors>>(8*i));
      System.arraycopy(colorBlock(0xf800,0,0),0,block,8,8);
      var image=BlpImageDecoder.decode(blp2(4,4,2,8,7,block));
      for(int i=0;i<16;i++)assertThat(image.getRGB(i%4,i/4)).isEqualTo((expected[i%8]<<24)|0xff0000);
    }
  }
  @Test void partialDxtBlocksCropPaddingWithoutBleedingIntoNextRow() throws Exception {
    byte[] blocks=new byte[16];System.arraycopy(colorBlock(0xf800,0,0),0,blocks,0,8);
    System.arraycopy(colorBlock(0x001f,0,0),0,blocks,8,8);
    var image=BlpImageDecoder.decode(blp2(5,3,2,0,0,blocks));
    assertThat(image.getWidth()).isEqualTo(5);assertThat(image.getHeight()).isEqualTo(3);
    for(int y=0;y<3;y++) {assertThat(image.getRGB(3,y)).isEqualTo(0xffff0000);assertThat(image.getRGB(4,y)).isEqualTo(0xff0000ff);}
  }
  @Test void rawBgraPreservesItsInlineAlpha() throws Exception {
    var image=BlpImageDecoder.decode(blp2(1,1,3,8,0,new byte[]{0x33,0x22,0x11,0x55}));
    assertThat(image.getRGB(0,0)).isEqualTo(0x55112233);
  }
  @Test void documentedBlp1OpaqueInvertedPaletteAndSeparateAlphaVariants() throws Exception {
    assertThat(BlpImageDecoder.decode(blp1(0,5,new byte[]{4})).getRGB(0,0)).isEqualTo(0xff112233);
    assertThat(BlpImageDecoder.decode(blp1(8,5,new byte[]{4})).getRGB(0,0)).isEqualTo(0x80112233);
    for(int picture:new int[]{3,4})assertThat(BlpImageDecoder.decode(blp1(8,picture,new byte[]{4,0x21})).getRGB(0,0)).isEqualTo(0x21112233);
  }
  @Test void pathDecodeUsesSameBoundsAndDoesNotModifyInput() throws Exception {
    byte[] original=blp2(1,1,2,0,0,colorBlock(0xf800,0,0));Path path=temporary.resolve("authored.blp");Files.write(path,original);
    assertThat(BlpImageDecoder.decode(path).getRGB(0,0)).isEqualTo(0xffff0000);
    assertThat(Files.readAllBytes(path)).isEqualTo(original);
    Path oversized=temporary.resolve("oversized.blp");Files.write(oversized,Arrays.copyOf(original,original.length+1));
    assertThatIOException().isThrownBy(()->BlpImageDecoder.decode(oversized,new BlpImageDecoder.Limits(156,1,1),()->false));
  }
  @Test void validLowerMipIsCheckedButOnlyTopMipIsReturned() throws Exception {
    byte[] first=blp2(4,4,2,0,0,colorBlock(0xf800,0,0)),all=Arrays.copyOf(first,first.length+8);
    System.arraycopy(colorBlock(0x001f,0,0),0,all,first.length,8);ByteBuffer header=ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
    header.put(11,(byte)1);header.putInt(24,first.length);header.putInt(88,8);
    assertThat(BlpImageDecoder.decode(all).getWidth()).isEqualTo(4);assertThat(BlpImageDecoder.decode(all).getRGB(0,0)).isEqualTo(0xffff0000);
    header.putInt(88,7);assertThatIOException().isThrownBy(()->BlpImageDecoder.decode(all));
  }
  @Test void malformedUnsignedRangesDimensionsAndOverlapAreRejectedBeforeAllocation() throws Exception {
    byte[] original=blp2(4,4,2,0,0,colorBlock(0xf800,0,0));
    for(int[] edit:new int[][]{{12,0},{12,-1},{16,Integer.MAX_VALUE},{20,-8},{20,4},{84,7},{84,Integer.MAX_VALUE},{24,148},{88,8}}) {
      byte[] bad=original.clone();ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(edit[0],edit[1]);
      assertThatIOException().as("header edit %s",Arrays.toString(edit)).isThrownBy(()->BlpImageDecoder.decode(bad));
    }
    byte[] overlap=original.clone();var header=ByteBuffer.wrap(overlap).order(ByteOrder.LITTLE_ENDIAN);
    header.putInt(24,148);header.putInt(88,8);assertThatIOException().isThrownBy(()->BlpImageDecoder.decode(overlap));
    byte[] tooManyPixels=original.clone();header=ByteBuffer.wrap(tooManyPixels).order(ByteOrder.LITTLE_ENDIAN);
    header.putInt(12,8192);header.putInt(16,8192);assertThatIOException().isThrownBy(()->BlpImageDecoder.decode(tooManyPixels));
  }
  @Test void unknownJpegAmbiguousFlagsAndTruncatedPayloadsFailClosed() throws Exception {
    byte[] original=blp2(1,1,1,0,0,new byte[]{4});
    for(int end:new int[]{0,3,147,148,1171,original.length-1})assertThatIOException().isThrownBy(()->BlpImageDecoder.decode(Arrays.copyOf(original,end)));
    byte[] jpeg=blp1(0,5,new byte[]{4});ByteBuffer.wrap(jpeg).order(ByteOrder.LITTLE_ENDIAN).putInt(4,0);
    assertThatIOException().isThrownBy(()->BlpImageDecoder.decode(jpeg)).withMessageContaining("JPEG");
    for(int flags:new int[]{1,4,9})assertThatIOException().isThrownBy(()->BlpImageDecoder.decode(blp1(flags,5,new byte[]{4})));
    byte[] unknown=original.clone();unknown[8]=9;assertThatIOException().isThrownBy(()->BlpImageDecoder.decode(unknown));
    byte[] unsupported=original.clone();unsupported[9]=2;assertThatIOException().isThrownBy(()->BlpImageDecoder.decode(unsupported));
  }
  @Test void deterministicMalformedInputSweepDoesNotEscapeAsUncheckedBufferErrors() throws Exception {
    byte[] base=blp2(4,4,2,8,7,new byte[16]);var random=new Random(0x424c5032);
    for(int i=0;i<2000;i++) {
      byte[] mutated=base.clone();for(int n=0;n<1+random.nextInt(4);n++)mutated[random.nextInt(mutated.length)]=(byte)random.nextInt(256);
      try {BlpImageDecoder.decode(mutated);}catch(IOException expected) { /* Explicit rejection is the contract. */ }
    }
  }
  @Test void cancellationAndEdtRefusalAreExplicit() throws Exception {
    byte[] bytes=blp2(16,16,1,0,0,new byte[256]);var calls=new java.util.concurrent.atomic.AtomicInteger();
    assertThatThrownBy(()->BlpImageDecoder.decode(bytes,BlpImageDecoder.DEFAULT_LIMITS,()->calls.incrementAndGet()>6)).isInstanceOf(CancellationException.class);
    var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
    javax.swing.SwingUtilities.invokeAndWait(()->{try{BlpImageDecoder.decode(bytes);}catch(Throwable error){failure.set(error);}});
    assertThat(failure.get()).isInstanceOf(IllegalStateException.class);
  }

  private static byte[] blp2(int width,int height,int encoding,int alpha,int alphaEncoding,byte[] pixels) {
    int start=148+(encoding==1?1024:0);byte[] bytes=new byte[start+pixels.length];var b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    b.put(new byte[]{'B','L','P','2'}).putInt(1).put((byte)encoding).put((byte)alpha).put((byte)alphaEncoding).put((byte)0).putInt(width).putInt(height);
    b.putInt(20,start);b.putInt(84,pixels.length);if(encoding==1)palette(b,148);
    System.arraycopy(pixels,0,bytes,start,pixels.length);return bytes;
  }
  private static byte[] blp1(int flags,int picture,byte[] pixels) {
    int start=156+1024;byte[] bytes=new byte[start+pixels.length];var b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    b.put(new byte[]{'B','L','P','1'}).putInt(1).putInt(flags).putInt(1).putInt(1).putInt(picture).putInt(0);
    b.putInt(28,start);b.putInt(92,pixels.length);palette(b,156);System.arraycopy(pixels,0,bytes,start,pixels.length);return bytes;
  }
  private static void palette(ByteBuffer b,int offset) {
    b.putInt(offset+4,0xffff0000);b.putInt(offset+8,0xff00ff00);b.putInt(offset+12,0xff0000ff);b.putInt(offset+16,0x7f112233);
  }
  private static byte[] colorBlock(int first,int second,int... indices) {
    var b=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putShort((short)first).putShort((short)second);int selectors=0;
    for(int i=0;i<16;i++)selectors|=indices[i%indices.length]<<(i*2);b.putInt(selectors);return b.array();
  }
}
