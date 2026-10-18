package dev.wowforever.app.image;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;

/** Bounded, worker-only BLP top-mip decoder. No network, cache writes, native tools or 3D support. */
public final class BlpImageDecoder {
  public record Limits(int maxInputBytes,int maxDimension,long maxPixels) {
    public Limits {
      if(maxInputBytes<156 || maxInputBytes>128*1024*1024 || maxDimension<1 || maxDimension>8192
          || maxPixels<1 || maxPixels>16L*1024*1024)throw new IllegalArgumentException("Invalid BLP resource limits");
    }
  }
  public static final Limits DEFAULT_LIMITS=new Limits(128*1024*1024,8192,16L*1024*1024);
  private enum Codec { PALETTE, BLP1_PALETTE_ALPHA, DXT1, DXT3, DXT5, BGRA }
  private record Header(int width,int height,int palette,int alphaDepth,Codec codec,int topOffset) {}
  private record Range(long start,long end) {}
  private BlpImageDecoder() {}

  public static BufferedImage decode(byte[] bytes) throws IOException {return decode(bytes,DEFAULT_LIMITS,()->false);}
  public static BufferedImage decode(Path file) throws IOException {return decode(file,DEFAULT_LIMITS,()->false);}
  public static BufferedImage decode(Path file,Limits limits,BooleanSupplier cancelled) throws IOException {
    worker(cancelled);Objects.requireNonNull(limits);
    if(!Files.isRegularFile(file) || Files.size(file)>limits.maxInputBytes())throw new IOException("BLP input exceeds its bound or is not a regular file");
    try(var input=Files.newInputStream(file);var output=new ByteArrayOutputStream()) {
      byte[] chunk=new byte[65536];int count;
      while((count=input.read(chunk))!=-1) {
        check(cancelled);
        if((long)output.size()+count>limits.maxInputBytes())throw new IOException("BLP input exceeds its byte bound");
        output.write(chunk,0,count);
      }
      return decode(output.toByteArray(),limits,cancelled);
    }
  }
  public static BufferedImage decode(byte[] bytes,Limits limits,BooleanSupplier cancelled) throws IOException {
    worker(cancelled);Objects.requireNonNull(bytes);Objects.requireNonNull(limits);
    if(bytes.length>limits.maxInputBytes())throw new IOException("BLP input exceeds its byte bound");
    Header header=header(bytes,limits);
    check(cancelled);
    var image=new BufferedImage(header.width(),header.height(),BufferedImage.TYPE_INT_ARGB);
    int[] pixels=((DataBufferInt)image.getRaster().getDataBuffer()).getData();
    if(header.codec()==Codec.PALETTE || header.codec()==Codec.BLP1_PALETTE_ALPHA)palette(bytes,header,pixels,cancelled);
    else if(header.codec()==Codec.BGRA)bgra(bytes,header,pixels,cancelled);
    else blocks(bytes,header,pixels,cancelled);
    check(cancelled);return image;
  }

  private static Header header(byte[] bytes,Limits limits) throws IOException {
    if(bytes.length<4 || bytes[0]!='B' || bytes[1]!='L' || bytes[2]!='P' || (bytes[3]!='1' && bytes[3]!='2'))
      throw new IOException("Not a supported BLP1/BLP2 image");
    boolean blp1=bytes[3]=='1';int fixed=blp1?156:148;
    if(bytes.length<fixed)throw new IOException("Truncated BLP header");
    long width=u32(bytes,12),height=u32(bytes,16);
    if(width<1 || height<1 || width>limits.maxDimension() || height>limits.maxDimension()
        || width*height>limits.maxPixels())throw new IOException("BLP dimensions exceed the pixel bound");
    int alpha,palette=-1;Codec codec;
    if(blp1) {
      if(u32(bytes,4)!=1)throw new IOException("Unsupported BLP1 compression (JPEG is not enabled)");
      long flags=u32(bytes,8),picture=u32(bytes,20),subtype=u32(bytes,24);
      if((flags!=0 && flags!=8) || subtype>1)throw new IOException("Unsupported or ambiguous BLP1 palette flags");
      if(picture==5) {codec=flags==8?Codec.BLP1_PALETTE_ALPHA:Codec.PALETTE;alpha=0;}
      else if((picture==3 || picture==4) && flags==8) {codec=Codec.PALETTE;alpha=8;}
      else throw new IOException("Unsupported BLP1 palette picture type");
      palette=fixed;
    } else {
      if(u32(bytes,4)!=1)throw new IOException("Unsupported BLP2 texture type");
      int encoding=u8(bytes,8),alphaEncoding=u8(bytes,10);alpha=u8(bytes,9);
      // Byte 11 is advisory (authenticated retail-format files also use 0x11).
      // Actual mip presence and safety come from the fully validated range tables.
      if(encoding==1 && Set.of(0,1,4,8).contains(alpha) && alphaEncoding==0) {codec=Codec.PALETTE;palette=fixed;}
      else if(encoding==2 && alphaEncoding==0 && (alpha==0 || alpha==1))codec=Codec.DXT1;
      else if(encoding==2 && alphaEncoding==1 && (alpha==4 || alpha==8))codec=Codec.DXT3;
      else if(encoding==2 && alphaEncoding==7 && alpha==8)codec=Codec.DXT5;
      else if(encoding==3 && (alpha==0 || alpha==8) && alphaEncoding==0)codec=Codec.BGRA;
      else throw new IOException("Unsupported BLP2 encoding/alpha combination");
    }
    int dataStart=palette<0?fixed:fixed+1024;
    if(bytes.length<dataStart)throw new IOException("Truncated BLP palette");
    int offsets=blp1?28:20;List<Range> ranges=new ArrayList<>();boolean gap=false;int top=0;
    for(int level=0;level<16;level++) {
      long offset=u32(bytes,offsets+level*4),length=u32(bytes,offsets+64+level*4);
      if(offset==0 && length==0) {gap=true;continue;}
      if(gap || offset<dataStart || length==0 || offset+length>bytes.length)throw new IOException("Truncated or invalid BLP mip range");
      int w=Math.max(1,(int)width>>level),h=Math.max(1,(int)height>>level);
      long expected=mipBytes(w,h,alpha,codec);
      if(length!=expected)throw new IOException("BLP mip length does not match its dimensions and encoding");
      ranges.add(new Range(offset,offset+length));if(level==0)top=(int)offset;
    }
    if(top==0)throw new IOException("BLP has no top mip");
    ranges.sort(Comparator.comparingLong(Range::start));
    for(int i=1;i<ranges.size();i++)if(ranges.get(i).start()<ranges.get(i-1).end())throw new IOException("BLP mip ranges overlap");
    return new Header((int)width,(int)height,palette,alpha,codec,top);
  }
  private static long mipBytes(int width,int height,int alpha,Codec codec) {
    long count=(long)width*height;
    return switch(codec) {
      case PALETTE -> count+(count*alpha+7)/8;
      case BLP1_PALETTE_ALPHA -> count;
      case BGRA -> count*4;
      case DXT1 -> ((width+3L)/4)*((height+3L)/4)*8;
      case DXT3,DXT5 -> ((width+3L)/4)*((height+3L)/4)*16;
    };
  }
  private static void palette(byte[] bytes,Header h,int[] output,BooleanSupplier cancelled) {
    int alphaStart=h.topOffset()+output.length;
    for(int i=0;i<output.length;i++) {
      if(i%h.width()==0)check(cancelled);
      int entry=h.palette()+4*u8(bytes,h.topOffset()+i),alpha=255;
      if(h.codec()==Codec.BLP1_PALETTE_ALPHA)alpha=255-u8(bytes,entry+3);
      else if(h.alphaDepth()==1)alpha=((u8(bytes,alphaStart+i/8)>>(i%8))&1)*255;
      else if(h.alphaDepth()==4)alpha=((u8(bytes,alphaStart+i/2)>>((i%2)*4))&15)*17;
      else if(h.alphaDepth()==8)alpha=u8(bytes,alphaStart+i);
      output[i]=(alpha<<24)|(u8(bytes,entry+2)<<16)|(u8(bytes,entry+1)<<8)|u8(bytes,entry);
    }
  }
  private static void bgra(byte[] bytes,Header h,int[] output,BooleanSupplier cancelled) {
    for(int i=0;i<output.length;i++) {
      if(i%h.width()==0)check(cancelled);int p=h.topOffset()+i*4;
      output[i]=(u8(bytes,p+3)<<24)|(u8(bytes,p+2)<<16)|(u8(bytes,p+1)<<8)|u8(bytes,p);
    }
  }
  private static void blocks(byte[] bytes,Header h,int[] output,BooleanSupplier cancelled) {
    int position=h.topOffset(),size=h.codec()==Codec.DXT1?8:16;
    int[] colors=new int[4],alpha=new int[8];
    for(int y=0;y<h.height();y+=4) {
      check(cancelled);
      for(int x=0;x<h.width();x+=4,position+=size) {
        int colorStart=position+(size==16?8:0),a=u16(bytes,colorStart),b=u16(bytes,colorStart+2);
        colors[0]=rgb565(a);colors[1]=rgb565(b);
        boolean three=h.codec()==Codec.DXT1 && a<=b;
        colors[2]=mix(colors[0],colors[1],three?1:2,1,three?2:3);
        colors[3]=three?0:mix(colors[0],colors[1],1,2,3);
        long selectors=u32(bytes,colorStart+4),alphaSelectors=0;
        if(h.codec()==Codec.DXT5) {
          alpha[0]=u8(bytes,position);alpha[1]=u8(bytes,position+1);
          if(alpha[0]>alpha[1])for(int i=2;i<8;i++)alpha[i]=((8-i)*alpha[0]+(i-1)*alpha[1])/7;
          else {
            for(int i=2;i<6;i++)alpha[i]=((6-i)*alpha[0]+(i-1)*alpha[1])/5;
            alpha[6]=0;alpha[7]=255;
          }
          for(int i=0;i<6;i++)alphaSelectors|=(long)u8(bytes,position+2+i)<<(8*i);
        }
        for(int py=0;py<4 && y+py<h.height();py++)for(int px=0;px<4 && x+px<h.width();px++) {
          int index=py*4+px,selector=(int)((selectors>>(index*2))&3),opacity=255;
          if(h.codec()==Codec.DXT1 && h.alphaDepth()==1 && three && selector==3)opacity=0;
          else if(h.codec()==Codec.DXT3)opacity=((u8(bytes,position+index/2)>>((index%2)*4))&15)*17;
          else if(h.codec()==Codec.DXT5)opacity=alpha[(int)((alphaSelectors>>(index*3))&7)];
          output[(y+py)*h.width()+x+px]=(opacity<<24)|colors[selector];
        }
      }
    }
  }
  private static int rgb565(int value) {
    int r=(value>>11)&31,g=(value>>5)&63,b=value&31;
    return (((r<<3)|(r>>2))<<16)|(((g<<2)|(g>>4))<<8)|(b<<3)|(b>>2);
  }
  private static int mix(int a,int b,int wa,int wb,int divisor) {
    int result=0;
    for(int shift=0;shift<24;shift+=8)result|=((wa*((a>>shift)&255)+wb*((b>>shift)&255))/divisor)<<shift;
    return result;
  }
  private static int u8(byte[] bytes,int position) {return bytes[position]&255;}
  private static int u16(byte[] bytes,int position) {return u8(bytes,position)|(u8(bytes,position+1)<<8);}
  private static long u32(byte[] bytes,int position) {return Integer.toUnsignedLong(u16(bytes,position)|(u16(bytes,position+2)<<16));}
  private static void worker(BooleanSupplier cancelled) {
    if(SwingUtilities.isEventDispatchThread())throw new IllegalStateException("BLP images must decode off the EDT");
    Objects.requireNonNull(cancelled);check(cancelled);
  }
  private static void check(BooleanSupplier cancelled) {
    if(cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())throw new CancellationException("BLP decode cancelled");
  }
}
