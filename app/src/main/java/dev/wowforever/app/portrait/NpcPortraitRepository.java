package dev.wowforever.app.portrait;

import com.google.gson.*;
import dev.wowforever.app.db.NpcPortraits;
import dev.wowforever.app.portrait.NpcPortraitProvider.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Bounded, asynchronous portrait decoding. Never downloads or generates assets. */
public final class NpcPortraitRepository implements AutoCloseable {
  private final List<NpcPortraitProvider> providers;
  private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(16),r->{var t=new Thread(r,"npc-portraits");t.setDaemon(true);return t;});
  private final Map<Key,CompletableFuture<Result>> pending=new HashMap<>();
  private final LinkedHashMap<Key,Result> decoded=new LinkedHashMap<>(16,.75f,true);
  private boolean closed;
  private static final class Shared {static final NpcPortraitRepository INSTANCE=new NpcPortraitRepository(systemProviders());}
  public static NpcPortraitRepository shared(){return Shared.INSTANCE;}
  public NpcPortraitRepository(List<NpcPortraitProvider> providers){this.providers=List.copyOf(providers);}
  public synchronized CompletableFuture<Result> load(Key key){
    Objects.requireNonNull(key);
    if(closed)return CompletableFuture.completedFuture(Result.invalid("Portrait repository is closed"));
    Result cached=decoded.get(key);if(cached!=null)return CompletableFuture.completedFuture(cached);
    var existing=pending.get(key);if(existing!=null)return existing;
    var future=new CompletableFuture<Result>();pending.put(key,future);
    try {worker.execute(()->{
      if(future.isDone())return;
      Result result=read(key);
      synchronized(this){if(!closed&&result.status()==Status.AVAILABLE){decoded.put(key,result);while(decoded.size()>8)decoded.remove(decoded.keySet().iterator().next());}}
      future.complete(result);
    });}catch(RejectedExecutionException busy){future.complete(Result.invalid("Portrait queue is busy"));}
    future.whenComplete((result,error)->{synchronized(this){pending.remove(key,future);}});
    return future;
  }
  private Result read(Key key){
    if(SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Decode portraits on a worker");
    Result invalid=null;
    for(var provider:providers){
      try {var result=Objects.requireNonNull(provider.load(key));
        if(result.status()==Status.AVAILABLE){if(!result.portrait().key().equals(key)){invalid=Result.invalid("Portrait identity mismatch");continue;}return result;}
        if(result.status()==Status.INVALID)invalid=result;
      }catch(Exception failure){invalid=Result.invalid("Portrait source could not be verified");}
    }
    return invalid==null?Result.missing():invalid;
  }
  public static NpcPortraitProvider bundled(){return key->{
    if(!key.product().equals("wow_classic_beta"))return Result.missing();
    return NpcPortraits.load(key.npcId(),key.build()).map(p->Result.available(new Portrait(p.image(),key,"World of Warcraft artwork © Blizzard Entertainment · bundled",p.evidence()))).orElseGet(Result::missing);
  };}
  private static List<NpcPortraitProvider> systemProviders(){
    var providers=new ArrayList<NpcPortraitProvider>();String pack=System.getProperty("wowforever.npcPortrait.pack");
    if(pack!=null&&!pack.isBlank())providers.add(localPack(Path.of(pack)));
    providers.add(bundled());providers.add(legacyCache(systemCache()));return providers;
  }
  /** Optional read-only pack: manifest.json {schema: npc-portraits-v1, entries: [...]}. */
  public static NpcPortraitProvider localPack(Path directory){return key->{
    if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))return Result.missing();
    Path root=directory.toRealPath(),file=root.resolve("manifest.json");
    if(!regularInside(root,file)||Files.size(file)>1048576)return Result.invalid("Portrait pack manifest is invalid");
    try {
      var manifest=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
      if(!"npc-portraits-v1".equals(text(manifest,"schema")))return Result.invalid("Unknown portrait pack schema");
      var entries=manifest.getAsJsonArray("entries");if(entries==null||entries.size()>4096)return Result.invalid("Portrait pack entry limit exceeded");
      JsonObject selected=null;Set<Key> identities=new HashSet<>();
      for(var value:entries){var entry=value.getAsJsonObject();
        var identity=new Key(text(entry,"product"),text(entry,"build"),entry.get("npcId").getAsLong());
        if(!identities.add(identity))return Result.invalid("Duplicate portrait pack identity");
        if(identity.equals(key))selected=entry;
      }
      if(selected==null)return Result.missing();
      String source=text(selected,"source");if(source.isBlank())return Result.invalid("Portrait source is required");
      var evidence=selected.has("evidence")?selected.get("evidence"):selected.get("provenance");
      if(evidence==null||evidence.isJsonNull()||evidence.isJsonPrimitive()&&evidence.getAsString().isBlank())return Result.invalid("Portrait provenance is required");
      Path relative=Path.of(text(selected,"file"));
      if(relative.isAbsolute()||relative.getNameCount()==0)return Result.invalid("Unsafe portrait path");
      for(Path component:relative)if(component.toString().equals("..")||component.toString().equals("."))return Result.invalid("Unsafe portrait path");
      Path image=root.resolve(relative).normalize();
      if(!image.startsWith(root)||!regularInside(root,image))return Result.invalid("Unsafe portrait path");
      Path walked=root;for(Path component:relative){walked=walked.resolve(component);if(Files.isSymbolicLink(walked))return Result.invalid("Portrait symlinks are not accepted");}
      long size=Files.size(image);if(size<24||size>33554432)return Result.invalid("Portrait size is invalid");
      byte[] bytes=Files.readAllBytes(image);
      if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(text(selected,"sha256")))return Result.invalid("Portrait hash mismatch");
      return Result.available(new Portrait(decodePng(bytes),key,source,evidence.toString()));
    }catch(Exception malformed){return Result.invalid("Portrait pack could not be verified");}
  };}
  private static Path systemCache(){String base=System.getenv("XDG_CACHE_HOME");if(base==null||base.isBlank())base=System.getProperty("user.home")+"/.cache";return Path.of(System.getProperty("wowforever.npcPreview.cache",base+"/wow-forever/npc-previews"));}
  /** Reads only portrait.png from old local preview entries; rotation data is ignored entirely. */
  public static NpcPortraitProvider legacyCache(Path directory){return key->{
    if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))return Result.missing();
    Path root=directory.toRealPath();boolean invalid=false;int inspected=0;
    try(var entries=Files.newDirectoryStream(root)){
      for(Path entry:entries){
        if(++inspected>4096)break;
        if(!entry.getFileName().toString().matches("[a-f0-9]{64}")||!Files.isDirectory(entry,LinkOption.NOFOLLOW_LINKS))continue;
        Path manifest=entry.resolve("manifest.json");
        try {
          if(!regularInside(root,manifest)||Files.size(manifest)>262144)continue;
          var m=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
          if(!key.product().equals(text(m,"product"))||!key.build().equals(text(m,"build")))continue;
          var mapping=m.getAsJsonObject("evidence").getAsJsonObject("mapping");
          if(mapping.get("npcId").getAsLong()!=key.npcId())continue;
          if(!key.product().equals(text(mapping,"product"))||!key.build().equals(text(mapping,"build"))){invalid=true;continue;}
          var portrait=m.getAsJsonObject("portrait");if(!"portrait.png".equals(text(portrait,"file"))){invalid=true;continue;}
          Path image=entry.resolve("portrait.png");
          if(!regularInside(root,image)||Files.size(image)<24||Files.size(image)>33554432){invalid=true;continue;}
          byte[] bytes=Files.readAllBytes(image);
          if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(text(portrait,"sha256"))){invalid=true;continue;}
          var decoded=decodePng(bytes);
          return Result.available(new Portrait(decoded,key,"World of Warcraft artwork © Blizzard Entertainment · local cache",m.get("evidence").toString()));
        }catch(Exception malformed){invalid=true;}
      }
    }
    return invalid?Result.invalid("Local portrait could not be verified"):Result.missing();
  };}
  private static String text(JsonObject object,String key){return object.get(key).getAsString();}
  private static boolean regularInside(Path root,Path path)throws IOException{return Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&path.toRealPath().startsWith(root)&&!Files.isSymbolicLink(path.getParent());}
  private static java.awt.image.BufferedImage decodePng(byte[] bytes)throws IOException {
    byte[] signature={(byte)137,80,78,71,13,10,26,10};for(int i=0;i<8;i++)if(bytes[i]!=signature[i])throw new IOException("Expected PNG");
    try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
      var readers=ImageIO.getImageReadersByFormatName("png");if(!readers.hasNext())throw new IOException("PNG decoder unavailable");var reader=readers.next();
      try {reader.setInput(input);int width=reader.getWidth(0),height=reader.getHeight(0);if(width<1||height<1||width>2048||height>2048)throw new IOException("Portrait dimensions exceed limits");return reader.read(0);}finally{reader.dispose();}
    }
  }
  @Override public synchronized void close(){closed=true;worker.shutdownNow();for(var future:List.copyOf(pending.values()))future.complete(Result.invalid("Portrait repository is closed"));pending.clear();decoded.clear();}
}
