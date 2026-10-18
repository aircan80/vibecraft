package dev.wowforever.importer.acquisition;

import dev.wowforever.importer.setup.SetupPlan;
import dev.wowforever.importer.setup.SetupStorage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

/** Explicit third-party raw snapshot acquisition. No decoded rows, overlay merge or CASC proof. */
public final class RaidbotsHotfixMaterializer {
  public static final URI SOURCE=URI.create("https://storage.googleapis.com/raidbots-static/wow/classic_beta/enUS/DBCache.bin");
  public enum Permission { DENIED, ALLOW_RAIDBOTS_HOTFIX }
  public record Pin(String product,String build,String locale,String sha256) {
    public Pin {
      if(!"wow_classic_beta".equals(product)||!build.matches("1\\.60\\.\\d+\\.\\d+")||!"enUS".equals(locale)
          ||!sha256.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Unsupported exact hotfix identity or SHA-256");
    }
    long buildNumber(){return Long.parseLong(build.substring(build.lastIndexOf('.')+1));}
  }
  public record Progress(long receivedBytes,long expectedBytes) {}
  public record Result(Path snapshot,Path evidence,HotfixSnapshotReader.Snapshot structure,boolean reused) {}
  private final URI source;private final HttpClient http;private final long maximumBytes;private final int maximumEntries;private final Duration timeout;
  public RaidbotsHotfixMaterializer(){this(SOURCE,HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
      .connectTimeout(Duration.ofSeconds(15)).build(),64L<<20,500000,Duration.ofSeconds(120));}
  RaidbotsHotfixMaterializer(URI source,HttpClient http,long maximumBytes,int maximumEntries,Duration timeout){
    if(http.followRedirects()!=HttpClient.Redirect.NEVER||maximumBytes<44||maximumEntries<1||timeout.isNegative()||timeout.isZero())
      throw new IllegalArgumentException("Invalid hotfix acquisition bounds");
    if(!SOURCE.equals(source)&&!("http".equals(source.getScheme())&&"127.0.0.1".equals(source.getHost())&&source.getUserInfo()==null&&source.getQuery()==null))
      throw new IllegalArgumentException("Only reviewed Raidbots source or loopback fixtures are allowed");
    this.source=source;this.http=http;this.maximumBytes=maximumBytes;this.maximumEntries=maximumEntries;this.timeout=timeout;
  }
  public Result materialize(Permission permission,SetupPlan.JobContext job,Pin pin,BooleanSupplier cancelled,
      Consumer<Progress> progress)throws IOException {
    if(permission!=Permission.ALLOW_RAIDBOTS_HOTFIX)throw new IOException("Raidbots hotfix source was not selected");
    Objects.requireNonNull(pin);Objects.requireNonNull(cancelled);Objects.requireNonNull(progress);check(cancelled);
    SetupStorage storage=job.storage();storage.verifyJob(job.paths());
    Path root=checked(storage,job.paths().cache().resolve("raidbots-hotfix"));Files.createDirectories(root);
    Path lock=checked(storage,root.resolve("acquire.lock"));
    try(var channel=java.nio.channels.FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);var held=channel.tryLock()){
      if(held==null)throw new IOException("Hotfix acquisition already running");
      Path complete=checked(storage,root.resolve(pin.sha256()));
      if(Files.exists(complete))return validate(storage,complete,pin,cancelled,true);
      Path stage=checked(storage,root.resolve("pending-"+UUID.randomUUID()));Files.createDirectory(stage);boolean published=false;
      try {
        Path data=checked(storage,stage.resolve("DBCache.bin"));download(source,data,maximumBytes,cancelled,
            (received,expected)->progress.accept(new Progress(received,expected)));
        if(!pin.sha256().equals(hash(data,cancelled)))throw new IOException("Hotfix SHA-256 mismatch");
        var structure=new HotfixSnapshotReader().read(data,pin.buildNumber(),maximumBytes,maximumEntries,cancelled);
        Properties proof=new Properties();proof.setProperty("provider","RAIDBOTS_HOTFIX");proof.setProperty("source",source.toString());
        proof.setProperty("product",pin.product());proof.setProperty("build",pin.build());proof.setProperty("requestedLocale",pin.locale());
        proof.setProperty("sha256",pin.sha256());proof.setProperty("capturedAt",Instant.now().toString());
        proof.setProperty("cascRootVerified","false");proof.setProperty("decodedRowsAvailable","false");
        proof.setProperty("formatVersion",Integer.toString(structure.version()));proof.setProperty("headerBuild",Long.toString(structure.build()));
        proof.setProperty("identityEvidence","fixed-provider-url-and-header-build;product-and-locale-are-not-encoded-in-XFTH");
        for(var status:HotfixSnapshotReader.Status.values())proof.setProperty("status."+status,Long.toString(structure.entries().stream().filter(e->e.status()==status).count()));
        try(var output=Files.newOutputStream(checked(storage,stage.resolve("acquisition.properties")),StandardOpenOption.CREATE_NEW)){proof.store(output,"Raw snapshot only; do not merge records");}
        check(cancelled);storage.verifyJob(job.paths());
        Files.move(checked(storage,stage),checked(storage,complete),StandardCopyOption.ATOMIC_MOVE);published=true;
        return new Result(complete.resolve("DBCache.bin"),complete.resolve("acquisition.properties"),structure,false);
      }finally{if(!published){Files.deleteIfExists(checked(storage,stage.resolve("DBCache.bin")));Files.deleteIfExists(checked(storage,stage.resolve("acquisition.properties")));Files.deleteIfExists(checked(storage,stage));}}
    }catch(java.nio.channels.OverlappingFileLockException failure){throw new IOException("Hotfix acquisition already running",failure);}
  }
  private Result validate(SetupStorage storage,Path directory,Pin pin,BooleanSupplier cancelled,boolean reused)throws IOException {
    Path data=checked(storage,directory.resolve("DBCache.bin")),proof=checked(storage,directory.resolve("acquisition.properties"));
    if(!Files.isRegularFile(proof)||Files.size(proof)>16384)throw new IOException("Cached hotfix evidence missing or oversized");
    Properties values=new Properties();try(var input=Files.newInputStream(proof)){values.load(input);}catch(IllegalArgumentException malformed){throw new IOException("Invalid cached hotfix evidence",malformed);}
    Map<String,String> expected=Map.of("provider","RAIDBOTS_HOTFIX","source",source.toString(),"product",pin.product(),"build",pin.build(),"requestedLocale",pin.locale(),"sha256",pin.sha256(),"cascRootVerified","false","decodedRowsAvailable","false");
    for(var entry:expected.entrySet())if(!entry.getValue().equals(values.getProperty(entry.getKey())))throw new IOException("Cached hotfix identity evidence mismatch");
    if(!Files.isRegularFile(data)||Files.size(data)>maximumBytes||!pin.sha256().equals(hash(data,cancelled)))throw new IOException("Cached hotfix content mismatch");
    return new Result(data,proof,new HotfixSnapshotReader().read(data,pin.buildNumber(),maximumBytes,maximumEntries,cancelled),reused);
  }
  private long download(URI uri,Path output,long maximum,BooleanSupplier cancelled,BiConsumer<Long,Long> bytes)throws IOException {
    AtomicReference<DownloadBody> body=new AtomicReference<>();
    java.util.concurrent.atomic.AtomicBoolean stopped=new java.util.concurrent.atomic.AtomicBoolean();
    HttpRequest request=HttpRequest.newBuilder(uri).timeout(timeout)
        .header("User-Agent","Vibecraft/1 pinned-hotfix-snapshot").header("Accept","application/octet-stream").GET().build();
    CompletableFuture<HttpResponse<Path>> pending=http.sendAsync(request,info->{
      DownloadBody subscriber=new DownloadBody(output,maximum,info.statusCode(),info.headers().firstValueAsLong("Content-Length").orElse(-1),
          info.headers().firstValue("Content-Type").orElse(""),()->cancelled.getAsBoolean()||stopped.get(),bytes);body.set(subscriber);return subscriber;
    });
    long end=System.nanoTime()+timeout.toNanos();
    try {
      while(true){
        check(cancelled);
        if(System.nanoTime()>end)throw new IOException("Hotfix snapshot request timed out");
        try{pending.get(50,TimeUnit.MILLISECONDS);return Files.size(output);}
        catch(TimeoutException wait){/* Poll cancellation even when the server stops sending. */}
        catch(ExecutionException failure){
          check(cancelled);
          if(failure.getCause() instanceof CancellationException cancellation)throw cancellation;
          throw new IOException("Hotfix snapshot failed: "+uri+": "+failure.getCause().getMessage(),failure.getCause());
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
        if(status!=200)throw new IOException("Hotfix snapshot HTTP "+status);
        if(length>maximum)throw new IOException("Hotfix snapshot exceeds byte limit");
        if(type.toLowerCase(Locale.ROOT).contains("html"))throw new IOException("Hotfix snapshot returned HTML");
        output=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);subscription.request(1);
      }catch(Exception failure){abort(failure);}
    }
    public synchronized void onNext(List<ByteBuffer> buffers){
      if(result.isDone())return;
      try{
        for(ByteBuffer buffer:buffers){
          while(buffer.hasRemaining()){
            check(cancelled);int count=Math.min(buffer.remaining(),8192);
            if(received+count>maximum)throw new IOException("Hotfix snapshot exceeds byte limit");
            byte[] chunk=new byte[count];buffer.get(chunk);output.write(chunk);received+=count;
          }
        }
        progress.accept(received,length);subscription.request(1);
      }catch(Exception failure){abort(failure);}
    }
    public synchronized void onError(Throwable failure){abort(failure);}
    public synchronized void onComplete(){
      if(result.isDone())return;
      try{output.close();if(length>=0&&length!=received)throw new IOException("Truncated hotfix snapshot");result.complete(file);}
      catch(Exception failure){abort(failure);}
    }
    synchronized void abort(Throwable failure){
      if(subscription!=null)subscription.cancel();
      if(output!=null)try{output.close();}catch(IOException close){failure.addSuppressed(close);}
      result.completeExceptionally(failure);
    }
  }

  private static void check(BooleanSupplier cancelled){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("Hotfix acquisition cancelled");}
  private static Path checked(SetupStorage storage,Path path)throws IOException{Path actual=storage.verifyDestination(path);if(!actual.equals(path))throw new IOException("Hotfix storage alias changed");return actual;}
  private static String hash(Path path,BooleanSupplier cancelled)throws IOException{
    try{var digest=MessageDigest.getInstance("SHA-256");try(var input=Files.newInputStream(path)){byte[] bytes=new byte[65536];int n;while((n=input.read(bytes))!=-1){check(cancelled);digest.update(bytes,0,n);}}return HexFormat.of().formatHex(digest.digest());}
    catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
  }
}
