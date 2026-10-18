package dev.wowforever.app.sound;

import com.google.gson.*;
import dev.wowforever.app.music.CityMusicService;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
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
  private final Set<Process> processes=ConcurrentHashMap.newKeySet();
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
  @Override public void close(){if(closed)return;closed=true;generation.incrementAndGet();processes.forEach(Process::destroyForcibly);downloads.shutdownNow();audio.shutdownNow();control.shutdown();Thread stop=new Thread(player::stop,"ui-sound-stop");stop.setDaemon(true);stop.start();ready.clear();}
  private Path acquire(String product,String build,long fileDataId)throws Exception {
    if(SwingUtilities.isEventDispatchThread())throw new IllegalStateException("UI sound asset IO on EDT");
    Files.createDirectories(cache);
    Path result=cache.resolve(product+"-"+build+"-"+fileDataId+".json");
    // Worker always revalidates native root membership and content-addressed bytes.
    String shared=System.getProperty("wowforever.nativeAssets.cache",System.getenv("NPC_PREVIEW_ASSET_CACHE"));
    if(shared==null||shared.isBlank()){String base=System.getenv("XDG_CACHE_HOME");if(base==null||base.isBlank())base=System.getProperty("user.home")+"/.cache";shared=base+"/wowforever-browser/native-assets";}
    Process process=new ProcessBuilder(System.getProperty("wowforever.sound.python","python3"),Path.of(System.getProperty("wowforever.sound.worker","tools/npcmodels/native_asset_worker.py")).toAbsolutePath().toString(),"--product",product,"--build",build,
        "--file-data-id",Long.toString(fileDataId),"--cache",shared,"--output",result.toString())
        .redirectErrorStream(true).redirectOutput(cache.resolve("worker-"+fileDataId+".log").toFile()).start();
    processes.add(process);
    try {
      if(!process.waitFor(240000,TimeUnit.MILLISECONDS)){process.destroyForcibly();throw new IOException("UI sound acquisition timed out");}
      if(process.exitValue()!=0)throw new IOException("UI sound acquisition failed");
    } finally {processes.remove(process);if(process.isAlive())process.destroyForcibly();}
    if(Files.size(result)>1024*1024)throw new IOException("Oversized ui-sound proof");
    JsonObject proof=JsonParser.parseString(Files.readString(result)).getAsJsonObject();
    if(!product.equals(proof.get("product").getAsString())||!build.equals(proof.get("build").getAsString())||fileDataId!=proof.get("fileDataID").getAsLong())throw new IOException("Wrong ui-sound identity");
    Path asset=Path.of(proof.get("path").getAsString());if(Files.size(asset)>2L*1024*1024)throw new IOException("Oversized ui-sound asset");
    MessageDigest hash=MessageDigest.getInstance("SHA-256");
    try(var input=Files.newInputStream(asset)){byte[] buffer=new byte[65536];for(int n;(n=input.read(buffer))>=0;)hash.update(buffer,0,n);}
    if(!HexFormat.of().formatHex(hash.digest()).equals(proof.get("sha256").getAsString()))throw new IOException("UI sound cache checksum mismatch");
    return ShortVorbis.wave(asset,cache.resolve(product+"-"+build+"-"+fileDataId+".wav"));
  }

}
