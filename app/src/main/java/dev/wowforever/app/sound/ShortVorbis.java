package dev.wowforever.app.sound;

import com.jcraft.jogg.*;
import com.jcraft.jorbis.*;
import java.io.*;
import java.nio.file.*;
import javax.sound.sampled.*;

/** Packet decoder avoids VorbisSPI dropping audio on single-page short UI clips. */
public final class ShortVorbis {
  private ShortVorbis(){}
  public static Path wave(Path source,Path target)throws Exception{
    byte[] pcm;Info info=new Info();Comment comment=new Comment();SyncState sync=new SyncState();StreamState stream=new StreamState();DspState dsp=new DspState();Block block=new Block(dsp);
    info.init();comment.init();sync.init();
    try(var input=Files.newInputStream(source);var output=new ByteArrayOutputStream()){
      Page page=new Page();Packet packet=new Packet();int headers=0;boolean initialized=false;boolean eof=false;
      while(!eof){
        int pageResult=sync.pageout(page);
        if(pageResult==0){int offset=sync.buffer(4096);int n=input.read(sync.data,offset,4096);if(n<0){eof=true;continue;}sync.wrote(n);continue;}
        if(pageResult<0)throw new IOException("Corrupt Ogg page");
        if(!initialized){stream.init(page.serialno());initialized=true;}
        if(stream.pagein(page)<0)throw new IOException("Invalid Ogg stream");
        for(int packetResult;(packetResult=stream.packetout(packet))!=0;){
          if(packetResult<0)throw new IOException("Corrupt Ogg packet");
          if(headers<3){if(info.synthesis_headerin(comment,packet)<0)throw new IOException("Invalid Vorbis header");headers++;if(headers==3){if(info.channels<1||info.channels>2||info.rate<8000||info.rate>96000)throw new IOException("Unsupported UI audio format");dsp.synthesis_init(info);block.init(dsp);}continue;}
          if(block.synthesis(packet)==0)dsp.synthesis_blockin(block);
          float[][][] samples=new float[1][][];int[] indices=new int[info.channels];
          for(int count;(count=dsp.synthesis_pcmout(samples,indices))>0;){
            if(output.size()+count*info.channels*2>2*1024*1024)throw new IOException("Oversized UI sound");
            for(int i=0;i<count;i++)for(int channel=0;channel<info.channels;channel++){
              int value=Math.max(-32768,Math.min(32767,(int)(samples[0][channel][indices[channel]+i]*32767)));
              output.write(value&255);output.write((value>>>8)&255);
            }
            dsp.synthesis_read(count);
          }
        }
      }
      if(headers!=3||output.size()==0)throw new IOException("Empty UI sound");pcm=output.toByteArray();
    }finally{block.clear();dsp.clear();stream.clear();sync.clear();}
    AudioFormat format=new AudioFormat(info.rate,16,info.channels,true,false);info.clear();
    Files.createDirectories(target.getParent());Path temp=Files.createTempFile(target.getParent(),".ui-sound-",".wav");
    try(var audio=new AudioInputStream(new ByteArrayInputStream(pcm),format,pcm.length/format.getFrameSize())){AudioSystem.write(audio,AudioFileFormat.Type.WAVE,temp.toFile());Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
    return target;
  }
}
