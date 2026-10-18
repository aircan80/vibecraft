package dev.wowforever.importer.acquisition;

import dev.wowforever.importer.allthethings.AllTheThingsSource;
import dev.wowforever.importer.setup.*;
import dev.wowforever.importer.source.SourceSpec;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

/** Selected community files at reviewed GitHub commit pins. No ZIPs, external programs or fallback sources.
 * Recorded download hashes detect later cache corruption; they are not independently reviewed expected hashes.
 */
public final class CommunitySourceMaterializer implements AutoCloseable {
  public record Limits(long fileBytes,long totalBytes,int files,Duration timeout,Duration spacing) {
    public Limits {if(fileBytes<1||totalBytes<fileBytes||files<1||files>4096||timeout.isNegative()||timeout.isZero()||spacing.isNegative())throw new IllegalArgumentException("Invalid community acquisition limits");}
    public static Limits defaults(){return new Limits(64L<<20,1L<<30,2048,Duration.ofSeconds(120),Duration.ofMillis(250));}
  }
  public enum Stage { CHECKING_CACHE, DOWNLOADING, PUBLISHING, COMPLETE }
  public record Progress(Stage stage,SetupPlan.Source source,String path,int completed,int total,long bytes,long expectedBytes) {}
  public record FileEvidence(SetupPlan.Source source,String path,URI url,long bytes,String recordedSha256) {}
  public record Result(Path questie,Optional<Path> atlasLoot,Optional<Path> allTheThings,String manifestId,
                       boolean reused,long downloadedBytes,List<FileEvidence> evidence) {
    public Result {evidence=List.copyOf(evidence);}
    public SetupPlan.MaterializedInputs inputs(Optional<SetupPlan.MaterializedClient> client){return new SetupPlan.MaterializedInputs(questie,atlasLoot,allTheThings,client);}
  }
  record Spec(SetupPlan.Source source,String key,String owner,String repository,String commit,List<String> paths) {
    Spec {paths=List.copyOf(paths);}
  }
  @FunctionalInterface interface Transport {void download(URI uri,Path file,long maximum,BooleanSupplier cancelled,BiConsumer<Long,Long> progress)throws IOException;}
  private final List<Spec> specs;
  private final Limits limits;
  private final HttpClient http;
  private final Transport transport;
  public CommunitySourceMaterializer(){
    specs=registered();limits=Limits.defaults();http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(15)).build();transport=this::download;
  }
  CommunitySourceMaterializer(List<Spec> specs,Limits limits,Transport transport){this.specs=List.copyOf(specs);this.limits=limits;this.transport=transport;http=null;}
  CommunitySourceMaterializer(List<Spec> specs,Limits limits,HttpClient http,URI loopback){
    if(http.followRedirects()!=HttpClient.Redirect.NEVER||!"http".equals(loopback.getScheme())||!Set.of("localhost","127.0.0.1","[::1]").contains(loopback.getHost()))throw new IllegalArgumentException("Only bounded loopback fixtures may replace GitHub transport");
    this.specs=List.copyOf(specs);this.limits=limits;this.http=http;
    this.transport=(uri,file,maximum,cancelled,progress)->download(loopback.resolve(uri.getRawPath()),file,maximum,cancelled,progress);
  }
  static List<Spec> registered(){
    List<Spec> specs=new ArrayList<>();for(SourceSpec spec:SourceSpec.load()){
      SetupPlan.Source source=spec.key().equals("questiedb")?SetupPlan.Source.QUESTIE:SetupPlan.Source.ATLASLOOT;
      if(spec.zip()!=null||spec.baseline()!=null)continue; // No hidden manual ZIP or baseline acquisition.
      specs.add(new Spec(source,spec.key(),spec.owner(),spec.repo(),spec.commit(),spec.paths()));
    }
    specs.add(new Spec(SetupPlan.Source.ALL_THE_THINGS,"allthethings",AllTheThingsSource.OWNER,AllTheThingsSource.REPOSITORY,AllTheThingsSource.PIN,AllTheThingsSource.paths()));return List.copyOf(specs);
  }
  public Result materialize(SetupPlan plan,SetupPlan.JobContext job,BooleanSupplier cancelled,Consumer<Progress> progress)throws IOException {
    Objects.requireNonNull(cancelled);Objects.requireNonNull(progress);check(cancelled);
    List<Spec> selected=selected(plan);int count=selected.stream().mapToInt(s->s.paths().size()).sum();
    if(count>limits.files())throw new IOException("Selected community manifest exceeds the file limit");
    String id=manifestId(selected);SetupStorage storage=job.storage();storage.verifyJob(job.paths());
    Path root=checked(storage,job.paths().cache().resolve("community"));Files.createDirectories(root);
    Path lock=checked(storage,root.resolve("materialize.lock"));if(Files.isSymbolicLink(lock))throw new IOException("Community lock alias refused");
    try(var channel=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);var held=channel.tryLock()){
      if(held==null)throw new IOException("Another community acquisition is using this job");
      return acquire(storage,root,selected,id,count,cancelled,progress);
    }catch(java.nio.channels.OverlappingFileLockException conflict){throw new IOException("Another community acquisition is using this job",conflict);}
  }
  private List<Spec> selected(SetupPlan plan)throws IOException {
    List<Spec> result=new ArrayList<>();
    for(SetupPlan.Source source:SetupPlan.Source.values()){
      if(source==SetupPlan.Source.NATIVE_DATA||!plan.sources().contains(source))continue;
      List<Spec> matching=specs.stream().filter(s->s.source()==source).toList();
      if(matching.size()!=1)throw new IOException("Selected source has no supported reviewed GitHub manifest: "+source);
      Spec spec=matching.getFirst();
      if(!spec.commit().matches("[a-f0-9]{40}")||!spec.commit().equals(plan.sourcePins().get(source)))throw new IOException("Selected source pin differs from its reviewed exact commit: "+source);
      if(!spec.owner().matches("[A-Za-z0-9][A-Za-z0-9_.-]*")||!spec.repository().matches("[A-Za-z0-9][A-Za-z0-9_.-]*")||!spec.key().matches("[a-z0-9-]+")||spec.paths().isEmpty())throw new IOException("Invalid source manifest identity");
      Set<String> unique=new HashSet<>();for(String path:spec.paths()){
        safePath(path);if(!unique.add(path.toLowerCase(Locale.ROOT)))throw new IOException("Duplicate or case-colliding community path");
      }
      result.add(spec);
    }
    if(result.stream().noneMatch(s->s.source()==SetupPlan.Source.QUESTIE))throw new IOException("Selected Questie source is required");
    return List.copyOf(result);
  }
  private Result acquire(SetupStorage storage,Path root,List<Spec> selected,String id,int count,BooleanSupplier cancelled,Consumer<Progress> progress)throws IOException {
    progress.accept(new Progress(Stage.CHECKING_CACHE,null,"",0,count,0,-1));Path pointer=checked(storage,root.resolve("current-input.properties"));
    Result cached=reuse(storage,root,pointer,selected,id,cancelled);if(cached!=null){progress.accept(new Progress(Stage.COMPLETE,null,"",count,count,0,0));return cached;}
    Path stage=checked(storage,root.resolve("input-"+id+"-"+UUID.randomUUID()));Files.createDirectory(stage);
    long totalBytes=0,lastRequest=0;int completed=0;boolean published=false;List<FileEvidence> evidence=new ArrayList<>();
    try{
      for(Spec spec:selected)for(String relative:spec.paths()){
        check(cancelled);pause(lastRequest,cancelled);lastRequest=System.nanoTime();Path target=checked(storage,stage.resolve(spec.key()).resolve(relative));
        Files.createDirectories(checked(storage,target.getParent()));Path part=checked(storage,target.resolveSibling(target.getFileName()+".part-"+UUID.randomUUID()));
        long remaining=limits.totalBytes()-totalBytes;if(remaining<1)throw new IOException("Selected community inputs exceed total byte limit");URI uri=url(spec,relative);int done=completed;
        try{
          transport.download(uri,part,Math.min(remaining,limits.fileBytes()),cancelled,(bytes,length)->progress.accept(new Progress(Stage.DOWNLOADING,spec.source(),relative,done,count,bytes,length)));
          check(cancelled);checked(storage,part);if(!Files.isRegularFile(part)||Files.size(part)>Math.min(remaining,limits.fileBytes()))throw new IOException("Community response exceeds its byte limit");
          long bytes=Files.size(part);String sha=hash(part,cancelled);
          Files.move(checked(storage,part),checked(storage,target),StandardCopyOption.ATOMIC_MOVE);
          evidence.add(new FileEvidence(spec.source(),relative,uri,bytes,sha));totalBytes+=bytes;completed++;
        }finally{Files.deleteIfExists(checked(storage,part));}
      }
      check(cancelled);Properties receipt=receipt(selected,id,evidence);Path receiptPath=checked(storage,stage.resolve("acquisition.properties"));write(receiptPath,receipt);if(Files.size(receiptPath)>2L*1024*1024)throw new IOException("Community evidence manifest exceeds byte limit");
      progress.accept(new Progress(Stage.PUBLISHING,null,"",count,count,totalBytes,totalBytes));check(cancelled);
      Properties next=new Properties();next.setProperty("manifest",id);next.setProperty("directory",stage.getFileName().toString());next.setProperty("receiptSha256",hash(receiptPath,cancelled));
      Path temporary=checked(storage,root.resolve("current-input.part-"+UUID.randomUUID()));
      try{write(temporary,next);check(cancelled);Files.move(checked(storage,temporary),checked(storage,pointer),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(checked(storage,temporary));}
      published=true;progress.accept(new Progress(Stage.COMPLETE,null,"",count,count,totalBytes,totalBytes));return result(stage,selected,id,false,totalBytes,evidence);
    }finally{if(!published)remove(storage,stage);}
  }
  private Result reuse(SetupStorage storage,Path root,Path pointer,List<Spec> selected,String id,BooleanSupplier cancelled)throws IOException {
    if(!Files.isRegularFile(pointer)||Files.size(pointer)>4096)return null;Properties current=readCached(pointer);if(current==null)return null;
    if(!id.equals(current.getProperty("manifest")))return null;String directory=current.getProperty("directory","");if(!directory.matches("input-[a-f0-9]{64}-[a-f0-9-]{36}"))return null;
    Path stage=checked(storage,root.resolve(directory)),receiptPath=checked(storage,stage.resolve("acquisition.properties"));
    if(!Files.isRegularFile(receiptPath)||Files.size(receiptPath)>2L*1024*1024||!hash(receiptPath,cancelled).equals(current.getProperty("receiptSha256")))return null;
    Properties receipt=readCached(receiptPath);if(receipt==null||!id.equals(receipt.getProperty("manifest")))return null;
    Set<Path> expected=new HashSet<>();expected.add(receiptPath);List<FileEvidence> evidence=new ArrayList<>();long total=0;int index=0;
    for(Spec spec:selected)for(String relative:spec.paths()){
      check(cancelled);String prefix="file."+index+++".";Path file=checked(storage,stage.resolve(spec.key()).resolve(relative));expected.add(file);
      if(!spec.source().name().equals(receipt.getProperty(prefix+"source"))||!relative.equals(receipt.getProperty(prefix+"path"))||!url(spec,relative).toString().equals(receipt.getProperty(prefix+"url")))return null;
      String sha=receipt.getProperty(prefix+"recordedSha256","");long size;try{size=Long.parseLong(receipt.getProperty(prefix+"bytes","-1"));}catch(NumberFormatException malformed){return null;}
      if(!sha.matches("[a-f0-9]{64}")||size<0||size>limits.fileBytes()||!Files.isRegularFile(file)||Files.size(file)!=size||!hash(file,cancelled).equals(sha))return null;
      total+=size;if(total>limits.totalBytes())return null;evidence.add(new FileEvidence(spec.source(),relative,url(spec,relative),size,sha));
    }
    try(var paths=Files.walk(stage)){
      int seen=0;for(Path path:(Iterable<Path>)paths::iterator){check(cancelled);if(++seen>limits.files()*33+10)return null;checked(storage,path);if(Files.isSymbolicLink(path))throw new IOException("Community input symlink refused");if(Files.isRegularFile(path)&&!expected.contains(path))return null;}
    }
    return result(stage,selected,id,true,0,evidence);
  }
  private static Result result(Path stage,List<Spec> specs,String id,boolean reused,long bytes,List<FileEvidence> evidence){
    Path questie=null;Optional<Path> atlas=Optional.empty(),att=Optional.empty();for(Spec spec:specs)switch(spec.source()){
      case QUESTIE->questie=stage.resolve(spec.key());case ATLASLOOT->atlas=Optional.of(stage.resolve(spec.key()));case ALL_THE_THINGS->att=Optional.of(stage.resolve(spec.key()));default->throw new IllegalStateException();
    }
    return new Result(questie,atlas,att,id,reused,bytes,evidence);
  }
  private static Properties receipt(List<Spec> specs,String id,List<FileEvidence> evidence){
    Properties receipt=new Properties();receipt.setProperty("manifest",id);receipt.setProperty("provider","GITHUB_PINNED_RAW");receipt.setProperty("hashAuthority","recorded-after-https-download-not-independent-reviewed-file-hash");receipt.setProperty("capturedAt",Instant.now().toString());
    for(Spec spec:specs){receipt.setProperty("commit."+spec.source(),spec.commit());receipt.setProperty("repository."+spec.source(),spec.owner()+"/"+spec.repository());}
    for(int i=0;i<evidence.size();i++){var file=evidence.get(i);String prefix="file."+i+".";receipt.setProperty(prefix+"source",file.source().name());receipt.setProperty(prefix+"path",file.path());receipt.setProperty(prefix+"url",file.url().toString());receipt.setProperty(prefix+"bytes",Long.toString(file.bytes()));receipt.setProperty(prefix+"recordedSha256",file.recordedSha256());}return receipt;
  }
  private static String manifestId(List<Spec> specs){StringBuilder value=new StringBuilder("community-raw-v1\n");for(Spec spec:specs){value.append(spec.source()).append(':').append(spec.owner()).append('/').append(spec.repository()).append('@').append(spec.commit()).append('\n');for(String path:spec.paths())value.append(path).append('\n');}return digest(value.toString().getBytes(StandardCharsets.UTF_8));}
  private static void safePath(String path)throws IOException {
    if(path.isBlank()||path.length()>1024||path.startsWith("/")||path.contains("\\")||path.contains(":")||path.indexOf('\0')>=0||path.split("/",-1).length>32)throw new IOException("Unsafe community source path");
    for(String part:path.split("/",-1))if(part.isBlank()||part.equals(".")||part.equals(".."))throw new IOException("Unsafe community source path");
  }
  private static URI url(Spec spec,String relative)throws IOException{try{return new URI("https","raw.githubusercontent.com","/"+spec.owner()+"/"+spec.repository()+"/"+spec.commit()+"/"+relative,null);}catch(java.net.URISyntaxException malformed){throw new IOException("Invalid pinned community URL",malformed);}}
  private static Path checked(SetupStorage storage,Path path)throws IOException{Path actual=storage.verifyDestination(path);if(!actual.equals(path))throw new IOException("Community storage alias changed");return actual;}
  private static Properties readCached(Path path)throws IOException{
    Properties result=new Properties();try(var in=Files.newInputStream(path)){result.load(in);return result;}
    catch(IllegalArgumentException|NoSuchFileException invalid){return null;}
  }
  private static void write(Path path,Properties values)throws IOException{try(var out=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW)){values.store(out,"Pinned raw GitHub community acquisition");}}
  private static String digest(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
  private static String hash(Path path,BooleanSupplier cancelled)throws IOException{try{MessageDigest hash=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){check(cancelled);hash.update(buffer,0,n);}}return HexFormat.of().formatHex(hash.digest());}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
  private static void remove(SetupStorage storage,Path stage)throws IOException{try(var paths=Files.walk(stage)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(checked(storage,path));}}
  private void pause(long previous,BooleanSupplier cancelled){while(previous!=0&&System.nanoTime()-previous<limits.spacing().toNanos()){check(cancelled);try{Thread.sleep(25);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new CancellationException("Community acquisition interrupted");}}}
  private static void check(BooleanSupplier cancelled){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("Community acquisition cancelled");}
  @Override public void close(){if(http!=null)http.shutdownNow();}

  private void download(URI uri,Path file,long maximum,BooleanSupplier cancelled,BiConsumer<Long,Long> progress)throws IOException {
    HttpRequest request=HttpRequest.newBuilder(uri).timeout(limits.timeout()).header("User-Agent","Vibecraft-library-setup/1").GET().build();
    AtomicReference<Body> body=new AtomicReference<>();var future=http.sendAsync(request,info->{Body subscriber=new Body(file,maximum,info.statusCode(),info.headers().firstValueAsLong("Content-Length").orElse(-1),cancelled,progress);body.set(subscriber);return subscriber;});
    long deadline=System.nanoTime()+limits.timeout().toNanos();try{
      while(!future.isDone()){check(cancelled);if(System.nanoTime()>deadline)throw new IOException("Community request timed out");try{Thread.sleep(25);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new CancellationException("Community acquisition interrupted");}}
      try{future.join();}catch(CompletionException failed){Throwable cause=failed.getCause();if(cause instanceof IOException io)throw io;if(cause instanceof CancellationException cancellation)throw cancellation;throw new IOException("Community request failed",cause);}
    }finally{if(!future.isDone()){Body subscriber=body.get();if(subscriber!=null)subscriber.abort(new IOException("Community transfer stopped"));future.cancel(true);}}
  }
  private static final class Body implements HttpResponse.BodySubscriber<Path>{
    private final CompletableFuture<Path> result=new CompletableFuture<>();private final Path file;private final long maximum,length;private final int status;private final BooleanSupplier cancelled;private final BiConsumer<Long,Long> progress;
    private java.util.concurrent.Flow.Subscription subscription;private OutputStream out;private long received;
    Body(Path file,long maximum,int status,long length,BooleanSupplier cancelled,BiConsumer<Long,Long> progress){this.file=file;this.maximum=maximum;this.status=status;this.length=length;this.cancelled=cancelled;this.progress=progress;}
    public CompletionStage<Path> getBody(){return result;}
    public synchronized void onSubscribe(java.util.concurrent.Flow.Subscription subscription){this.subscription=subscription;try{check(cancelled);if(status!=200)throw new IOException("Pinned community file HTTP "+status);if(length>maximum)throw new IOException("Community file exceeds byte limit");out=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW);subscription.request(1);}catch(Exception failed){abort(failed);}}
    public synchronized void onNext(List<ByteBuffer> buffers){if(result.isDone())return;try{for(ByteBuffer buffer:buffers)while(buffer.hasRemaining()){check(cancelled);int n=Math.min(buffer.remaining(),65536);if(n>maximum-received)throw new IOException("Community file exceeds byte limit");byte[] bytes=new byte[n];buffer.get(bytes);out.write(bytes);received+=n;}progress.accept(received,length);subscription.request(1);}catch(Exception failed){abort(failed);}}
    public synchronized void onError(Throwable failed){abort(failed);}
    public synchronized void onComplete(){if(result.isDone())return;try{out.close();if(length>=0&&length!=received)throw new IOException("Truncated community file");result.complete(file);}catch(Exception failed){abort(failed);}}
    synchronized void abort(Throwable failed){if(subscription!=null)subscription.cancel();if(out!=null)try{out.close();}catch(IOException close){failed.addSuppressed(close);}result.completeExceptionally(failed);}
  }
}
