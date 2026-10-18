package dev.wowforever.app.sound;

import dev.wowforever.app.assets.NativeAssetClient;
import dev.wowforever.app.music.CityMusicService;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;

/** Short, exact-build feedback. Cold clicks are dropped rather than played late. */
public final class UiSoundService implements AutoCloseable {
  public static final String PRODUCT="wow_classic_beta", BUILD="1.60.1.70450";
  public enum Cue {
    OPEN(850,567490), CLOSE(851,567464), CONFIRM(852,567481), TAB(841,567422), TOGGLE(856,567407);
    public final int soundKitId; public final long fileDataId;
    Cue(int kit,long file){soundKitId=kit;fileDataId=file;}
  }
  private static final class Shared { static final UiSoundService INSTANCE=new UiSoundService(); }
  public static UiSoundService shared(){return Shared.INSTANCE;}
  private final ExecutorService downloads=pool("ui-sound-assets",8),audio=pool("ui-sound-audio",1),control=pool("ui-sound-preferences",1);
  private final ConcurrentMap<Cue,Path> ready=new ConcurrentHashMap<>();
  private final Set<Cue> pending=ConcurrentHashMap.newKeySet();
  private final Preferences preferences;
  private final CityMusicService.Assets assets;
  private final CityMusicService.Player player;
  private final LongSupplier clock;
  private final AtomicLong lastCue=new AtomicLong(Long.MIN_VALUE);
  private final AtomicBoolean preferenceQueued=new AtomicBoolean();
  private final AtomicLong generation=new AtomicLong();
  private final Path cache;
  private volatile boolean muted,closed,configured;
  private volatile int volume;
  public UiSoundService(){this(null,new CityMusicService.SoundPlayer(),Preferences.userNodeForPackage(UiSoundService.class),System::nanoTime);}
  public UiSoundService(CityMusicService.Assets assets,CityMusicService.Player player,Preferences preferences,LongSupplier clock){
    this.preferences=preferences;this.player=player;this.clock=clock;this.assets=assets==null?this::acquire:assets;
    String base=System.getenv("XDG_CACHE_HOME");if(base==null||base.isBlank())base=System.getProperty("user.home")+"/.cache";
    cache=Path.of(System.getProperty("wowforever.sound.cache",base+"/wowforever-browser/ui-sounds"));
    muted=preferences.getBoolean("muted",false);volume=Math.max(0,Math.min(100,preferences.getInt("volume",22)));
  }
  private static ExecutorService pool(String name,int queue){return new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(queue),r->{Thread t=new Thread(r,name);t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());}
  private static void submit(ExecutorService executor,Runnable task){try{executor.execute(task);}catch(RejectedExecutionException ignored){}}
  /** Must receive the installed gameplay catalog identity, never an archived comparison build. */
  public void configure(String product,String build){
    configured=PRODUCT.equals(product)&&BUILD.equals(build);generation.incrementAndGet();
    if(configured&&!muted&&enabled())prewarm();
  }
  public boolean muted(){return muted;}
  public int volume(){return volume;}
  public void muted(boolean value){muted=value;generation.incrementAndGet();persist();if(!value)prewarm();}
  public void volume(int value){volume=Math.max(0,Math.min(100,value));persist();}
  private void persist(){
    if(closed||!preferenceQueued.compareAndSet(false,true))return;
    submit(control,()->{boolean savedMuted=muted;int savedVolume=volume;try{preferences.putBoolean("muted",savedMuted);preferences.putInt("volume",savedVolume);}finally{preferenceQueued.set(false);}if(!closed&&(muted!=savedMuted||volume!=savedVolume))persist();});
  }
  private boolean enabled(){return !closed&&configured&&Boolean.parseBoolean(System.getProperty("wowforever.sound.enabled","true"));}
  public void prewarm(){if(!enabled()||muted)return;for(Cue cue:Cue.values())if(!ready.containsKey(cue)&&pending.add(cue))submit(downloads,()->{
    try{if(!closed)ready.put(cue,assets.acquire(PRODUCT,BUILD,cue.fileDataId));}catch(Exception ignored){}finally{pending.remove(cue);}
  });}
  /** Called by deliberate navigation, window, tab and confirmation actions; never key/hover events. */
  public void play(Cue cue){
    if(!enabled()||muted||volume==0)return;
    Path file=ready.get(Objects.requireNonNull(cue));if(file==null){prewarm();return;}
    long now=clock.getAsLong(),before=lastCue.get();
    if(before!=Long.MIN_VALUE&&now-before<120_000_000L)return;
    if(!lastCue.compareAndSet(before,now))return;
    long token=generation.get();
    submit(audio,()->{if(!valid(token)||clock.getAsLong()-now>200_000_000L)return;
      try{player.play(file,()->valid(token),()->volume/100.0);}catch(Exception ignored){}
    });
  }
  private boolean valid(long token){return enabled()&&!muted&&volume>0&&generation.get()==token;}
  @Override public void close(){if(closed)return;closed=true;generation.incrementAndGet();downloads.shutdownNow();audio.shutdownNow();control.shutdown();Thread stop=new Thread(player::stop,"ui-sound-stop");stop.setDaemon(true);stop.start();ready.clear();}
  private Path acquire(String product,String build,long fileDataId)throws Exception {
    if(SwingUtilities.isEventDispatchThread())throw new IllegalStateException("UI sound asset IO on EDT");
    if(!PRODUCT.equals(product)||!BUILD.equals(build))throw new IOException("Unsupported native UI sound catalog");
    var asset=NativeAssetClient.shared().fetch(fileDataId,()->closed,2*1024*1024);
    if(asset.bytes()>2L*1024*1024)throw new IOException("Oversized UI sound asset");
    if(closed||Thread.currentThread().isInterrupted())throw new CancellationException("UI sound acquisition cancelled");
    return ShortVorbis.wave(asset.path(),cache.resolve(product+"-"+build+"-"+fileDataId+".wav"));
  }
}
