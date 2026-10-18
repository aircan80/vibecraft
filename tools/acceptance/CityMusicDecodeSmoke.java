import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.sound.sampled.*;

/** Run against the final shaded JAR; no speaker/audio device is needed. */
public final class CityMusicDecodeSmoke {
  public static void main(String[] args)throws Exception {
    Path path=Path.of(args[0]);MessageDigest hash=MessageDigest.getInstance("SHA-256");
    try(var input=Files.newInputStream(path)){byte[] bytes=new byte[65536];for(int n;(n=input.read(bytes))>=0;)hash.update(bytes,0,n);}
    try(var raw=new BufferedInputStream(Files.newInputStream(path));var source=AudioSystem.getAudioInputStream(raw)) {
      var format=source.getFormat();
      var pcm=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,format.getSampleRate(),16,format.getChannels(),format.getChannels()*2,format.getSampleRate(),false);
      try(var decoded=AudioSystem.getAudioInputStream(pcm,source)) {
        byte[] data=decoded.readNBytes((int)pcm.getFrameRate()*pcm.getFrameSize());
        if(data.length<8192)throw new AssertionError("Too little PCM audio decoded");
        boolean audible=false;for(byte value:data)if(value!=0){audible=true;break;}
        if(!audible)throw new AssertionError("Decoded audio is entirely silent");
        System.out.println("PASS source="+format+" PCM="+pcm+" decodedBytes="+data.length+" sha256="+HexFormat.of().formatHex(hash.digest()));
      }
    }
  }
}
