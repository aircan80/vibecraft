package dev.wowforever.app.music;

import dev.wowforever.app.assets.NativeAssetClient;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.prefs.Preferences;
import javax.sound.sampled.*;
import javax.swing.SwingUtilities;

/** City soundtrack controller. Neither navigation nor Swing waits for asset/audio IO. */
public final class CityMusicService implements AutoCloseable {
  public record State(long cityId,String cityName,String status,boolean muted,int volume) {}
  @FunctionalInterface public interface Assets {Path acquire(String product,String build,long fileDataId)throws Exception;}
  public interface Player {void play(Path file,BooleanSupplier current,DoubleSupplier volume)throws Exception;void stop();}
  private static final class Shared {static final CityMusicService INSTANCE=new CityMusicService();}
  public static CityMusicService shared(){return Shared.INSTANCE;}
  private final CityMusicCatalog catalog;
  private final Assets assets;
  private final Player player;
  private final Preferences preferences;
  private final ExecutorService downloads=pool("city-music-assets",2),audio=pool("city-music-audio",1),control=pool("city-music-control",1);
  private final ConcurrentMap<String,CompletableFuture<Path>> jobs=new ConcurrentHashMap<>();
  private final CopyOnWriteArrayList<Consumer<State>> listeners=new CopyOnWriteArrayList<>();
  private final AtomicLong generation=new AtomicLong();
  private volatile CompletableFuture<Void> stopBarrier=CompletableFuture.completedFuture(null);
  private final AtomicBoolean preferenceQueued=new AtomicBoolean();
  private volatile State state;
  private volatile boolean closed;
  private volatile String activeBuild="";

  public CityMusicService() {
    this.catalog=CityMusicCatalog.bundled();this.player=new SoundPlayer();
    this.preferences=Preferences.userNodeForPackage(CityMusicService.class);this.assets=this::acquire;
    this.state=new State(0,"","City music",preferences.getBoolean("muted",false),Math.max(0,Math.min(100,preferences.getInt("volume",35))));
    Runtime.getRuntime().addShutdownHook(new Thread(()->{close();player.stop();},"city-music-shutdown"));
  }
  public CityMusicService(CityMusicCatalog catalog,Assets assets,Player player,Preferences preferences) {
    this.catalog=catalog;this.assets=assets;this.player=player;this.preferences=preferences;
    this.state=new State(0,"","City music",preferences.getBoolean("muted",false),Math.max(0,Math.min(100,preferences.getInt("volume",35))));
  }
  private static ExecutorService pool(String name,int threads) {
    return new ThreadPoolExecutor(threads,threads,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),r->{Thread t=new Thread(r,name);t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
  }
  public State state(){return state;}
  public void addListener(Consumer<State> listener){listeners.add(listener);}
  public void removeListener(Consumer<State> listener){listeners.remove(listener);}
  private synchronized void update(long token,String status) {
    if(closed||token!=generation.get())return;
    State old=state;state=new State(old.cityId(),old.cityName(),status,old.muted(),old.volume());notifyListeners();
  }
  private void notifyListeners(){SwingUtilities.invokeLater(()->listeners.forEach(listener->listener.accept(state)));}
  private void stopPlayer(){if(!closed)stopBarrier=CompletableFuture.runAsync(player::stop,control); }

  public synchronized void enterCity(long cityId,String exactBuild) {
    if(closed)return;
    if(state.cityId()==cityId&&Objects.equals(activeBuild,exactBuild))return;
    long token=generation.incrementAndGet();stopPlayer();activeBuild=exactBuild;
    var city=catalog.city(cityId);state=new State(cityId,city.map(CityMusicCatalog.City::name).orElse("City"),"Loading city music…",state.muted(),state.volume());notifyListeners();
    if(!Boolean.parseBoolean(System.getProperty("wowforever.music.enabled","true"))){update(token,"City music disabled");return;}
    if(!catalog.build().equals(exactBuild)||city.isEmpty()){update(token,"City music unavailable for this build");return;}
    if(state.muted()){update(token,"Music muted");return;}
    start(token,city.get(),0);
  }
  private void start(long token,CityMusicCatalog.City city,int index) {
    if(!valid(token))return;
    var tracks=city.tracks(LocalTime.now());if(tracks.isEmpty()){update(token,"No verified music assignment");return;}
    var track=tracks.get(index%tracks.size());String key=catalog.product()+":"+catalog.build()+":"+track.fileDataId();
    CompletableFuture<Path> job=jobs.computeIfAbsent(key,k->CompletableFuture.supplyAsync(()->{
      try{return assets.acquire(catalog.product(),catalog.build(),track.fileDataId());}
      catch(Exception failure){throw new CompletionException(failure);}
    },downloads));
    CompletableFuture<Void> stopped=stopBarrier;
    job.thenCombine(stopped,(path,ignored)->path).whenComplete((path,error)->{
      if(!valid(token))return;
      if(error!=null){jobs.remove(key,job);update(token,"City music unavailable · try re-entering");return;}
      audio.execute(()->{
        if(!valid(token))return;
        try {
          update(token,"Playing city soundtrack");
          player.play(path,()->valid(token),()->state.volume()/100.0*track.volume());
          if(valid(token))start(token,city,index+1);
        }catch(Exception failure){update(token,"Audio playback unavailable");}
      });
    });
  }
  private boolean valid(long token){return Boolean.parseBoolean(System.getProperty("wowforever.music.enabled","true"))&&!closed&&token==generation.get()&&!state.muted()&&state.cityId()!=0;}
  public synchronized void leaveCity() {
    if(closed)return;
    generation.incrementAndGet();stopPlayer();activeBuild="";
    state=new State(0,"","City music",state.muted(),state.volume());notifyListeners();
  }
  public synchronized void muted(boolean muted) {
    if(closed||state.muted()==muted)return;
    long token=generation.incrementAndGet();stopPlayer();
    state=new State(state.cityId(),state.cityName(),muted?"Music muted":"Loading city music…",muted,state.volume());
    control.execute(()->preferences.putBoolean("muted",muted));notifyListeners();
    if(!muted&&catalog.build().equals(activeBuild))catalog.city(state.cityId()).ifPresent(city->start(token,city,0));
  }
  public synchronized void volume(int volume) {
    if(closed)return;int bounded=Math.max(0,Math.min(100,volume));
    state=new State(state.cityId(),state.cityName(),state.status(),state.muted(),bounded);notifyListeners();
    persistVolume();
  }
  private void persistVolume() {
    if(!preferenceQueued.compareAndSet(false,true))return;
    control.execute(()->{int value=state.volume();try{preferences.putInt("volume",value);}finally{preferenceQueued.set(false);}if(!closed&&state.volume()!=value)persistVolume();});
  }
  @Override public synchronized void close() {
    if(closed)return;closed=true;generation.incrementAndGet();
    control.execute(player::stop);
    downloads.shutdownNow();audio.shutdownNow();control.shutdown();listeners.clear();
  }

  private Path acquire(String product,String build,long fileDataId)throws Exception {
    if(SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Music asset IO on EDT");
    if(!NativeAssetClient.PRODUCT.equals(product)||!NativeAssetClient.BUILD.equals(build))throw new IOException("Unsupported native music catalog");
    var asset=NativeAssetClient.shared().fetch(fileDataId,()->closed,64*1024*1024);
    if(!product.equals(asset.product())||!build.equals(asset.build()))throw new IOException("Wrong music identity");
    if(asset.bytes()>64L*1024*1024)throw new IOException("Oversized music asset");
    return asset.path();
  }

  /** Software gain works even where the Linux mixer exposes no MASTER_GAIN. */
  public static final class SoundPlayer implements Player {
    private final AtomicReference<SourceDataLine> current=new AtomicReference<>();
    @Override public void stop(){SourceDataLine line=current.getAndSet(null);if(line!=null){line.stop();line.flush();line.close();}}
    @Override public void play(Path file,BooleanSupplier valid,DoubleSupplier volume)throws Exception {
      if(SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Audio IO on EDT");
      try(var buffered=new BufferedInputStream(Files.newInputStream(file));var encoded=AudioSystem.getAudioInputStream(buffered)) {
        var base=encoded.getFormat();var pcm=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,base.getSampleRate(),16,base.getChannels(),base.getChannels()*2,base.getSampleRate(),false);
        try(var decoded=AudioSystem.getAudioInputStream(pcm,encoded)) {
          SourceDataLine line=AudioSystem.getSourceDataLine(pcm);
          try {
            line.open(pcm,16384);if(!valid.getAsBoolean())return;
            current.set(line);if(!valid.getAsBoolean())return;line.start();
            byte[] buffer=new byte[8192];
            for(int n;valid.getAsBoolean()&&(n=decoded.read(buffer))>=0;) {
              double gain=Math.max(0,Math.min(1,volume.getAsDouble()));
              for(int i=0;i+1<n;i+=2){short sample=(short)((buffer[i]&255)|(buffer[i+1]<<8));int scaled=(int)(sample*gain);buffer[i]=(byte)scaled;buffer[i+1]=(byte)(scaled>>8);}
              if(valid.getAsBoolean())line.write(buffer,0,n);
            }
            if(valid.getAsBoolean())line.drain();
          }finally {current.compareAndSet(line,null);line.close();}
        }
      }
    }
  }
}
