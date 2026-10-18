package dev.wowforever.importer.acquisition;
import dev.wowforever.importer.setup.*;
import java.io.*;import java.net.*;import java.net.http.*;import java.nio.*;import java.nio.file.*;import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicReference;import java.util.function.*;
/** Acquires only the reviewed external schema dependency, with explicit independent Raidbots consent. */
public final class PinnedItemSparseDefinitionMaterializer {
 public record Result(Path definition,Path evidence,boolean reused){}
 public record Progress(long receivedBytes,long expectedBytes){}
 @FunctionalInterface interface Verifier{void verify(byte[] bytes)throws IOException;}
 private final URI source;private final HttpClient http;private final Duration timeout;private final String expectedSha;private final Verifier verifier;
 public PinnedItemSparseDefinitionMaterializer(){this(URI.create(PinnedItemSparseDefinition.SOURCE),HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(15)).build(),Duration.ofSeconds(45),PinnedItemSparseDefinition.SHA256,bytes->PinnedItemSparseDefinition.parse(bytes));}
 PinnedItemSparseDefinitionMaterializer(URI source,HttpClient http,Duration timeout,String expectedSha,Verifier verifier){
  if(http.followRedirects()!=HttpClient.Redirect.NEVER||timeout.isNegative()||timeout.isZero()||!expectedSha.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid definition acquisition bounds");
  if(!PinnedItemSparseDefinition.SOURCE.equals(source.toString())&&!("http".equals(source.getScheme())&&"127.0.0.1".equals(source.getHost())&&source.getQuery()==null&&source.getUserInfo()==null))throw new IllegalArgumentException("Unreviewed definition provider refused");
  this.source=source;this.http=http;this.timeout=timeout;this.expectedSha=expectedSha;this.verifier=verifier;
 }
 public Result materialize(RaidbotsHotfixMaterializer.Permission permission,SetupPlan.JobContext job,BooleanSupplier cancelled,Consumer<Progress> progress)throws IOException {
  if(permission!=RaidbotsHotfixMaterializer.Permission.ALLOW_RAIDBOTS_HOTFIX)throw new IOException("Raidbots permission including the disclosed schema dependency was not selected");
  check(cancelled);var storage=job.storage();storage.verifyJob(job.paths());Path root=checked(storage,job.paths().cache().resolve("itemsparse-definition"));Files.createDirectories(root);
  try(var lock=java.nio.channels.FileChannel.open(checked(storage,root.resolve("acquire.lock")),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var held=lock.tryLock()){
   if(held==null)throw new IOException("Definition acquisition is already running");
   Path pointer=checked(storage,root.resolve("current.properties"));Result cached=findCached(storage,root,pointer,cancelled);if(cached!=null)return cached;
   Path stage=checked(storage,root.resolve("definition-"+UUID.randomUUID()));Files.createDirectory(stage);Path partial=checked(storage,stage.resolve("ItemSparse.dbd.part"));boolean published=false;
   try{
    download(source,partial,1_048_576,cancelled,(received,total)->progress.accept(new Progress(received,total)));byte[] bytes=content(partial,cancelled);verifier.verify(bytes);check(cancelled);
    Path complete=checked(storage,stage.resolve("ItemSparse.dbd"));Files.move(checked(storage,partial),complete,StandardCopyOption.ATOMIC_MOVE);
    Properties proof=proof();proof.setProperty("capturedAt",Instant.now().toString());Path evidence=checked(storage,stage.resolve("acquisition.properties"));write(evidence,proof);
    Properties current=new Properties();current.setProperty("directory",stage.getFileName().toString());current.setProperty("sha256",expectedSha);Path next=checked(storage,root.resolve("current-"+UUID.randomUUID()+".part"));
    try{write(next,current);check(cancelled);storage.verifyJob(job.paths());Files.move(next,pointer,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(checked(storage,next));}
    published=true;return new Result(complete,evidence,false);
   }finally{if(!published){for(String file:List.of("ItemSparse.dbd.part","ItemSparse.dbd","acquisition.properties"))Files.deleteIfExists(checked(storage,stage.resolve(file)));Files.deleteIfExists(checked(storage,stage));}}
  }catch(java.nio.channels.OverlappingFileLockException busy){throw new IOException("Definition acquisition is already running",busy);}
 }
 private Result findCached(SetupStorage storage,Path root,Path pointer,BooleanSupplier cancelled)throws IOException{
  Properties current=readProperties(pointer);if(current==null||!expectedSha.equals(current.getProperty("sha256")))return null;String name=current.getProperty("directory","");if(!name.matches("definition-[a-f0-9-]{36}"))return null;
  Path directory=checked(storage,root.resolve(name));Path evidence=checked(storage,directory.resolve("acquisition.properties")),file=checked(storage,directory.resolve("ItemSparse.dbd"));Properties saved=readProperties(evidence);if(saved==null)return null;
  for(var entry:proof().entrySet())if(!entry.getValue().equals(saved.get(entry.getKey())))return null;
  try{verifier.verify(content(file,cancelled));return new Result(file,evidence,true);}catch(NoSuchFileException invalid){return null;}catch(IOException invalidContent){return null;}
 }
 private Properties proof(){Properties proof=new Properties();proof.setProperty("provider","WOWDBDEFS_SCHEMA");proof.setProperty("source",source.toString());proof.setProperty("commit",PinnedItemSparseDefinition.COMMIT);proof.setProperty("sha256",expectedSha);proof.setProperty("layout",PinnedItemSparseDefinition.LAYOUT);proof.setProperty("build",PinnedItemSparseDefinition.BUILD);proof.setProperty("table","ItemSparse");proof.setProperty("gameDataPayload","false");return proof;}
 private byte[] content(Path file,BooleanSupplier cancelled)throws IOException{
  check(cancelled);try(var input=Files.newInputStream(file);var output=new ByteArrayOutputStream()){
   byte[] chunk=new byte[8192];int n,total=0;while((n=input.read(chunk))!=-1){check(cancelled);total+=n;if(total>1_048_576)throw new IOException("Definition exceeds byte limit");output.write(chunk,0,n);}byte[] bytes=output.toByteArray();if(!expectedSha.equals(PinnedItemSparseDefinition.hash(bytes)))throw new IOException("Definition SHA-256 mismatch");return bytes;
  }
 }
 private static Properties readProperties(Path path)throws IOException{if(!Files.isRegularFile(path)||Files.size(path)>16384)return null;Properties values=new Properties();try(var input=Files.newInputStream(path)){values.load(input);return values;}catch(IllegalArgumentException|NoSuchFileException malformed){return null;}}
 private static void write(Path path,Properties values)throws IOException{try(var output=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW)){values.store(output,"Pinned external schema acquisition evidence");}}
  private long download(URI uri,Path output,long maximum,BooleanSupplier cancelled,BiConsumer<Long,Long> bytes)throws IOException {
    AtomicReference<DownloadBody> body=new AtomicReference<>();
    java.util.concurrent.atomic.AtomicBoolean stopped=new java.util.concurrent.atomic.AtomicBoolean();
    HttpRequest request=HttpRequest.newBuilder(uri).timeout(timeout)
        .header("User-Agent","Vibecraft/1 pinned-itemsparse-schema").header("Accept","application/octet-stream").GET().build();
    CompletableFuture<HttpResponse<Path>> pending=http.sendAsync(request,info->{
      DownloadBody subscriber=new DownloadBody(output,maximum,info.statusCode(),info.headers().firstValueAsLong("Content-Length").orElse(-1),
          info.headers().firstValue("Content-Type").orElse(""),()->cancelled.getAsBoolean()||stopped.get(),bytes);body.set(subscriber);return subscriber;
    });
    long end=System.nanoTime()+timeout.toNanos();
    try {
      while(true){
        check(cancelled);
        if(System.nanoTime()>end)throw new IOException("Definition request timed out");
        try{pending.get(50,TimeUnit.MILLISECONDS);return Files.size(output);}
        catch(TimeoutException wait){/* Poll cancellation even when the server stops sending. */}
        catch(ExecutionException failure){
          check(cancelled);
          if(failure.getCause() instanceof CancellationException cancellation)throw cancellation;
          throw new IOException("Definition failed: "+uri+": "+failure.getCause().getMessage(),failure.getCause());
        }
      }
    } catch(InterruptedException failure){Thread.currentThread().interrupt();throw new CancellationException("Export interrupted");}
    finally {stopped.set(true);if(!pending.isDone()){pending.cancel(true);DownloadBody active=body.get();if(active!=null)active.abort(new CancellationException("Export stopped"));}}
  }

  private static final class DownloadBody implements HttpResponse.BodySubscriber<Path> {
    private final CompletableFuture<Path> result=new CompletableFuture<>();
    private final Path file;private final long maximum,length;private final int status;private final String type;
    private final BooleanSupplier cancelled;private final BiConsumer<Long,Long> progress;
    private java.util.concurrent.Flow.Subscription subscription;private OutputStream output;private long received;
    DownloadBody(Path file,long maximum,int status,long length,String type,BooleanSupplier cancelled,BiConsumer<Long,Long> progress){
      this.file=file;this.maximum=maximum;this.status=status;this.length=length;this.type=type;this.cancelled=cancelled;this.progress=progress;
    }
    public CompletionStage<Path> getBody(){return result;}
    public synchronized void onSubscribe(java.util.concurrent.Flow.Subscription subscription){
      this.subscription=subscription;
      try{
        check(cancelled);
        if(status!=200)throw new IOException("Definition HTTP "+status);
        if(length>maximum)throw new IOException("Definition exceeds byte limit");
        if(type.toLowerCase(Locale.ROOT).contains("html"))throw new IOException("Definition returned HTML");
        output=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);subscription.request(1);
      }catch(Exception failure){abort(failure);}
    }
    public synchronized void onNext(List<ByteBuffer> buffers){
      if(result.isDone())return;
      try{
        for(ByteBuffer buffer:buffers){
          while(buffer.hasRemaining()){
            check(cancelled);int count=Math.min(buffer.remaining(),8192);
            if(received+count>maximum)throw new IOException("Definition exceeds byte limit");
            byte[] chunk=new byte[count];buffer.get(chunk);output.write(chunk);received+=count;
          }
        }
        progress.accept(received,length);subscription.request(1);
      }catch(Exception failure){abort(failure);}
    }
    public synchronized void onError(Throwable failure){abort(failure);}
    public synchronized void onComplete(){
      if(result.isDone())return;
      try{output.close();if(length>=0&&length!=received)throw new IOException("Truncated definition");result.complete(file);}
      catch(Exception failure){abort(failure);}
    }
    synchronized void abort(Throwable failure){
      if(subscription!=null)subscription.cancel();
      if(output!=null)try{output.close();}catch(IOException close){failure.addSuppressed(close);}
      result.completeExceptionally(failure);
    }
  }

 private static void check(BooleanSupplier cancelled){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("Definition acquisition cancelled");}
 private static Path checked(SetupStorage storage,Path path)throws IOException{Path actual=storage.verifyDestination(path);if(!actual.equals(path))throw new IOException("Definition storage alias changed");return actual;}
}
