package dev.wowforever.app.preview;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** On-demand, build-scoped preview jobs. Rendering never occupies the database/navigation executor. */
public final class NpcPreviewService implements AutoCloseable {
  public static final String RENDERER_VERSION="native-preview-v1";
  public record Request(String product,String build,long npcId,long displayId,long modelId,long modelFileDataId,
      List<Long> textures,Double displayScale,Double modelScale,String appearanceKey,String catalogId,String evidence) {
    public Request {
      Objects.requireNonNull(product);Objects.requireNonNull(build);Objects.requireNonNull(appearanceKey);
      if(product.isBlank()||build.isBlank()||appearanceKey.isBlank()||npcId<=0||displayId<=0||modelId<=0||modelFileDataId<=0)
        throw new IllegalArgumentException("A complete, exact native appearance is required");
      textures=List.copyOf(textures);if(textures.size()>16||textures.stream().anyMatch(id->id<0))throw new IllegalArgumentException("Invalid texture slots");
    }
  }
  public record Preview(BufferedImage portrait,List<BufferedImage> frames,String product,String build,
      String appearanceKey,String rendererVersion,String evidence,Path directory) {
    public Preview {frames=List.copyOf(frames);}
  }
  public record Config(Path cache,Path worker,String python,String rendererVersion,Duration timeout) {
    public Config {cache=cache.toAbsolutePath().normalize();if(worker!=null)worker=worker.toAbsolutePath().normalize();}
    public static Config system() {
      String worker=System.getProperty("wowforever.npcPreview.worker",System.getenv("WOWFOREVER_NPC_PREVIEW_WORKER"));
      if(worker==null||worker.isBlank())worker="tools/npcmodels/preview_worker.py";
      String base=System.getenv("XDG_CACHE_HOME");if(base==null||base.isBlank())base=System.getProperty("user.home")+"/.cache";
      Path cache=Path.of(System.getProperty("wowforever.npcPreview.cache",base+"/wow-forever/npc-previews"));
      return new Config(cache,Path.of(worker),System.getProperty("wowforever.npcPreview.python","python3"),
          System.getProperty("wowforever.npcPreview.rendererVersion",RENDERER_VERSION),Duration.ofSeconds(300));
    }
  }
  @FunctionalInterface public interface Renderer {void render(Request request,Path requestFile,Path output,Config config)throws Exception;}
  private record Decoded(String manifestHash,Preview preview) {}
  private final Config config;
  private final Renderer renderer;
  private final ExecutorService rendering=pool("npc-renderer",8),reading=pool("npc-preview-cache",24);
  private final ConcurrentMap<String,CompletableFuture<Preview>> jobs=new ConcurrentHashMap<>();
  private final Set<Process> children=ConcurrentHashMap.newKeySet();
  private final LinkedHashMap<String,Decoded> decoded=new LinkedHashMap<>(4,.75f,true);
  private static final class Shared {
    static final NpcPreviewService INSTANCE=new NpcPreviewService(Config.system());
    static {Runtime.getRuntime().addShutdownHook(new Thread(INSTANCE::close,"npc-preview-shutdown"));}
  }
  public static NpcPreviewService shared(){return Shared.INSTANCE;}
  public NpcPreviewService(Config config){this(config,null);}
  public NpcPreviewService(Config config,Renderer renderer){this.config=config;this.renderer=renderer==null?this::process:renderer;}
  private static ExecutorService pool(String name,int capacity){
    return new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(capacity),job->{Thread t=new Thread(job,name);t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
  }
  public String cacheKey(Request request){
    return hash((request.product()+"\n"+request.build()+"\n"+request.appearanceKey()+"\n"+config.rendererVersion()).getBytes(StandardCharsets.UTF_8));
  }
  public CompletableFuture<Optional<Preview>> cached(Request request){return submit(reading,()->read(request,config.cache().resolve(cacheKey(request))));}
  /** Pure in-memory lookup lets a rebuilt page follow an existing job without starting another one. */
  public Optional<CompletableFuture<Preview>> pending(Request request){return Optional.ofNullable(jobs.get(cacheKey(request)));}
  /** Identical appearances share one pending job. A failed job can be retried explicitly. */
  public CompletableFuture<Preview> generate(Request request){
    String key=cacheKey(request);
    CompletableFuture<Preview> job=jobs.computeIfAbsent(key,k->submit(rendering,()->generateNow(request,key)));
    job.whenComplete((result,error)->jobs.remove(key,job));return job;
  }
  private Preview generateNow(Request request,String key)throws Exception {
    offEdt();Path destination=config.cache().resolve(key);var existing=read(request,destination);if(existing.isPresent())return existing.orElseThrow();
    Files.createDirectories(config.cache());Path staging=Files.createTempDirectory(config.cache(),".render-");
    try {
      Path requestFile=staging.resolve("request.json");Files.writeString(requestFile,new GsonBuilder().setPrettyPrinting().create().toJson(request));
      Path output=staging.resolve("output");Files.createDirectories(output);renderer.render(request,requestFile,output,config);
      Preview validated=read(request,output).orElseThrow(()->new IOException("Renderer output failed build, identity, or image-hash verification"));
      // Only a fully validated result becomes a reusable cache entry.
      if(Files.exists(destination))delete(destination);
      try{Files.move(output,destination,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(output,destination);}
      synchronized(decoded){decoded.remove(key);}
      return read(request,destination).orElseThrow(()->new IOException("Published preview failed verification"));
    } finally {delete(staging);}
  }
  private Optional<Preview> read(Request request,Path directory)throws IOException {
    offEdt();Path manifest=directory.resolve("manifest.json");if(!Files.isRegularFile(manifest))return Optional.empty();
    try {
      if(Files.size(manifest)>262144)return Optional.empty();byte[] manifestBytes=Files.readAllBytes(manifest);String signature=hash(manifestBytes);
      String raw=new String(manifestBytes,StandardCharsets.UTF_8);JsonObject m=JsonParser.parseString(raw).getAsJsonObject();
      if(!request.product().equals(string(m,"product"))||!request.build().equals(string(m,"build"))
          ||!request.appearanceKey().equals(string(m,"appearanceKey"))||!config.rendererVersion().equals(string(m,"rendererVersion")))return Optional.empty();
      JsonArray frames=m.getAsJsonArray("frames");if(frames==null||frames.size()!=12||!m.has("evidence"))return Optional.empty();
      String key=cacheKey(request);Decoded previous;synchronized(decoded){previous=decoded.get(key);}
      boolean reuse=previous!=null&&previous.manifestHash().equals(signature);
      byte[] portraitBytes=verifiedImage(directory,m.getAsJsonObject("portrait"),"portrait.png");
      BufferedImage portrait=reuse?previous.preview().portrait():decode(portraitBytes);
      List<BufferedImage> images=new ArrayList<>();
      for(int i=0;i<12;i++){
        JsonObject entry=frames.get(i).getAsJsonObject();if(entry.get("angle").getAsInt()!=i*30)return Optional.empty();
        byte[] bytes=verifiedImage(directory,entry,"turntable-%02d.png".formatted(i));
        images.add(reuse?previous.preview().frames().get(i):decode(bytes));
      }
      Preview preview=new Preview(portrait,images,request.product(),request.build(),request.appearanceKey(),config.rendererVersion(),raw,directory);
      synchronized(decoded){decoded.put(key,new Decoded(signature,preview));while(decoded.size()>2)decoded.remove(decoded.keySet().iterator().next());}
      return Optional.of(preview);
    }catch(RuntimeException|IOException invalid){return Optional.empty();}
  }
  private static String string(JsonObject value,String key){return value.get(key).getAsString();}
  private static byte[] verifiedImage(Path directory,JsonObject entry,String required)throws IOException {
    if(entry==null||!required.equals(string(entry,"file")))throw new IOException("Unexpected render filename");
    Path file=directory.resolve(required);long size=Files.size(file);if(size<1||size>33554432)throw new IOException("Invalid image size");
    byte[] bytes=Files.readAllBytes(file);if(!hash(bytes).equals(string(entry,"sha256")))throw new IOException("Preview image hash mismatch");return bytes;
  }
  private static BufferedImage decode(byte[] bytes)throws IOException {
    try(var input=new java.io.ByteArrayInputStream(bytes)){
      BufferedImage image=ImageIO.read(input);if(image==null||image.getWidth()>2048||image.getHeight()>2048)throw new IOException("Invalid preview dimensions");return image;
    }
  }
  private void process(Request request,Path requestFile,Path output,Config config)throws Exception {
    offEdt();if(config.worker()==null||!Files.isRegularFile(config.worker()))throw new IOException("NPC renderer is not configured. Set wowforever.npcPreview.worker or WOWFOREVER_NPC_PREVIEW_WORKER.");
    ProcessBuilder command=new ProcessBuilder(config.python(),config.worker().toString(),"--request",requestFile.toString(),"--output",output.toString());
    command.redirectErrorStream(true);command.redirectOutput(output.resolve("renderer.log").toFile());Process child=command.start();children.add(child);
    try {
      if(!child.waitFor(config.timeout().toMillis(),TimeUnit.MILLISECONDS))throw new IOException("NPC rendering exceeded "+config.timeout().toSeconds()+" seconds");
      if(child.exitValue()!=0){String detail=Files.readString(output.resolve("renderer.log"));if(detail.length()>2000)detail=detail.substring(detail.length()-2000);throw new IOException("NPC renderer could not produce this appearance: "+detail.strip());}
    }finally{if(child.isAlive()){child.descendants().forEach(ProcessHandle::destroyForcibly);child.destroyForcibly();}children.remove(child);}
  }
  private static <T>CompletableFuture<T> submit(Executor executor,Callable<T> action){
    CompletableFuture<T> result=new CompletableFuture<>();
    try{executor.execute(()->{try{result.complete(action.call());}catch(Throwable failure){result.completeExceptionally(failure);}});}catch(RejectedExecutionException busy){result.completeExceptionally(new IOException("Preview queue is busy; try again shortly.",busy));}
    return result;
  }
  private static void offEdt(){if(SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Preview files and rendering belong on a worker");}
  private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
  private static void delete(Path directory)throws IOException {if(!Files.exists(directory))return;try(var paths=Files.walk(directory)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
  @Override public void close(){
    rendering.shutdownNow();reading.shutdownNow();
    for(Process child:children){child.descendants().forEach(ProcessHandle::destroyForcibly);child.destroyForcibly();}
    synchronized(decoded){decoded.clear();}
  }
}
