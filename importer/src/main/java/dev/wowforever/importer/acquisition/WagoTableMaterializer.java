package dev.wowforever.importer.acquisition;

import dev.wowforever.importer.setup.SetupPlan;
import dev.wowforever.importer.setup.SetupStorage;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import static dev.wowforever.importer.setup.SetupPlan.Acquisition.WAGO_EXPORT;

/** Explicit pinned CSV export acquisition. This is not Blizzard CASC/root authentication. */
public final class WagoTableMaterializer {
  public record Limits(long tableBytes, long totalBytes, int tables, Duration requestTimeout, Duration spacing) {
    public Limits {
      if(tableBytes<1||totalBytes<tableBytes||tables<1||tables>256||requestTimeout.isNegative()
          ||requestTimeout.isZero()||spacing.isNegative())throw new IllegalArgumentException("Invalid export bounds");
    }
    public static Limits defaults(){return new Limits(128L<<20,1L<<30,64,Duration.ofSeconds(120),Duration.ofMillis(500));}
  }
  public record Manifest(SetupPlan.NativeIdentity identity, Map<String,String> tableSha256) {
    public Manifest {
      Objects.requireNonNull(identity);
      tableSha256=Collections.unmodifiableMap(new TreeMap<>(tableSha256));
      if(tableSha256.isEmpty())throw new IllegalArgumentException("Select at least one pinned table");
      for(var entry:tableSha256.entrySet()) {
        if(!entry.getKey().matches("[A-Za-z][A-Za-z0-9_]{0,95}")||!entry.getValue().matches("[a-f0-9]{64}"))
          throw new IllegalArgumentException("Invalid table name or SHA-256; fallback and hotfix tables are not selected here");
      }
    }
    String id(){return sha((identity.product()+"\n"+identity.build()+"\n"+identity.locale()+"\n"+tableSha256).getBytes(StandardCharsets.UTF_8));}
  }
  public enum Stage { CHECKING_CACHE, DOWNLOADING, REUSING, PUBLISHING, COMPLETE }
  public record Progress(Stage stage,String table,int completed,int total,long bytes,long expectedBytes) {}
  public record Result(Path directory,String manifestId,int downloaded,int reused,long downloadedBytes) {
    public SetupPlan.MaterializedClient client(){return new SetupPlan.MaterializedClient(directory,WAGO_EXPORT);}
  }

  private final URI base;
  private final HttpClient http;
  private final Limits limits;
  public WagoTableMaterializer(){this(URI.create("https://wago.tools"),HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(15)).build(),Limits.defaults());}
  /** Package-local injection is for bounded loopback fixtures, not an alternate runtime provider selector. */
  WagoTableMaterializer(URI base,HttpClient http,Limits limits){
    if(http.followRedirects()!=HttpClient.Redirect.NEVER)throw new IllegalArgumentException("Export redirects must be explicit, not automatic");
    this.base=base;this.http=http;this.limits=limits;
  }

  public Result materialize(SetupPlan plan,SetupPlan.JobContext job,Manifest manifest,
      BooleanSupplier cancelled,Consumer<Progress> progress) throws IOException {
    Objects.requireNonNull(cancelled);Objects.requireNonNull(progress);
    // Consent and identity gates precede even cache-directory creation.
    if(!plan.allows(WAGO_EXPORT)||!plan.sources().contains(SetupPlan.Source.NATIVE_DATA))
      throw new IOException("Wago export acquisition was not selected");
    if(!plan.nativeIdentity().orElseThrow().equals(manifest.identity()))throw new IOException("Export manifest does not match the selected native build");
    if(manifest.tableSha256().size()>limits.tables())throw new IOException("Too many selected export tables");
    check(cancelled);
    SetupStorage storage=job.storage();storage.verifyJob(job.paths());
    Path root=checked(storage,job.paths().cache().resolve("wago"));
    Files.createDirectories(root);
    Path lock=checked(storage,root.resolve("materialize.lock"));
    if(Files.isSymbolicLink(lock))throw new IOException("Export job lock alias refused");
    try(var channel=java.nio.channels.FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        var held=channel.tryLock()) {
      if(held==null)throw new IOException("Another export acquisition is using this job");
      return materializeLocked(storage,job.paths(),root,manifest,cancelled,progress);
    } catch(java.nio.channels.OverlappingFileLockException failure){throw new IOException("Another export acquisition is using this job",failure);}
  }

  private Result materializeLocked(SetupStorage storage,SetupStorage.JobPaths job,Path root,Manifest manifest,
      BooleanSupplier cancelled,Consumer<Progress> progress) throws IOException {
    String id=manifest.id();int total=manifest.tableSha256().size();
    progress.accept(new Progress(Stage.CHECKING_CACHE,"",0,total,0,-1));
    Path pointer=checked(storage,root.resolve("current-input.properties"));
    Path complete=findComplete(storage,root,pointer,manifest,cancelled);
    if(complete!=null){progress.accept(new Progress(Stage.COMPLETE,"",total,total,0,0));return new Result(complete,id,0,total,0);}
    Path blobs=checked(storage,root.resolve("blobs"));Files.createDirectories(blobs);
    Path stage=checked(storage,root.resolve("input-"+id+"-"+UUID.randomUUID()));Files.createDirectory(stage);
    Path csv=checked(storage,stage.resolve("csv"));Files.createDirectory(csv);
    int downloaded=0,reused=0,finished=0;long networkBytes=0,materializedBytes=0,lastRequest=0;boolean published=false;
    try {
      for(var table:manifest.tableSha256().entrySet()) {
        check(cancelled);storage.verifyJob(job);
        String name=table.getKey(),expected=table.getValue();
        Path blob=checked(storage,blobs.resolve(expected+".csv"));
        if(Files.isRegularFile(blob)&&Files.size(blob)<=limits.tableBytes()&&expected.equals(hash(blob,cancelled))) {
          reused++;progress.accept(new Progress(Stage.REUSING,name,finished,total,Files.size(blob),Files.size(blob)));
        } else {
          pause(lastRequest,cancelled);lastRequest=System.nanoTime();
          Path partial=checked(storage,blobs.resolve(expected+".part-"+UUID.randomUUID()));
          long remaining=limits.totalBytes()-materializedBytes;
          if(remaining<1)throw new IOException("Selected export download exceeds the job byte limit");
          int completed=finished;
          try {
            long bytes=download(url(name,manifest.identity().build()),partial,Math.min(limits.tableBytes(),remaining),cancelled,
                (received,length)->progress.accept(new Progress(Stage.DOWNLOADING,name,completed,total,received,length)));
            if(!expected.equals(hash(partial,cancelled)))throw new IOException("CSV SHA-256 mismatch for "+name);
            validateCsv(partial);
            check(cancelled);
            Files.move(checked(storage,partial),checked(storage,blob),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            downloaded++;networkBytes+=bytes;
          } finally {Files.deleteIfExists(checked(storage,partial));}
        }
        materializedBytes+=Files.size(blob);
        if(materializedBytes>limits.totalBytes())throw new IOException("Selected export exceeds the job byte limit");
        validateCsv(blob);
        Files.copy(checked(storage,blob),checked(storage,csv.resolve(name+".csv")));
        finished++;
      }
      check(cancelled);
      write(checked(storage,stage.resolve("build.lock")),buildLock(manifest));
      Properties evidence=new Properties();evidence.setProperty("provider","WAGO_EXPORT");
      evidence.setProperty("evidence","service-export-with-explicit-build-and-pinned-csv-sha256");
      evidence.setProperty("cascRootVerified","false");evidence.setProperty("source",base.toString());
      evidence.setProperty("product",manifest.identity().product());evidence.setProperty("build",manifest.identity().build());
      evidence.setProperty("requestedLocale",manifest.identity().locale());evidence.setProperty("manifest",id);
      evidence.setProperty("capturedAt",Instant.now().toString());
      for(String table:manifest.tableSha256().keySet())evidence.setProperty("url."+table,url(table,manifest.identity().build()).toString());
      writeProperties(checked(storage,stage.resolve("acquisition.properties")),evidence);
      progress.accept(new Progress(Stage.PUBLISHING,"",total,total,networkBytes,networkBytes));
      check(cancelled);
      Properties current=new Properties();current.setProperty("manifest",id);current.setProperty("directory",stage.getFileName().toString());
      Path temporary=checked(storage,root.resolve("current-input.part-"+UUID.randomUUID()));
      try {
        writeProperties(temporary,current);check(cancelled);
        Files.move(checked(storage,temporary),checked(storage,pointer),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
      } finally {Files.deleteIfExists(checked(storage,temporary));}
      published=true;
      progress.accept(new Progress(Stage.COMPLETE,"",total,total,networkBytes,networkBytes));
      return new Result(stage,id,downloaded,reused,networkBytes);
    } finally {if(!published)removeStage(storage,stage,manifest.tableSha256().keySet());}
  }

  private URI url(String table,String build){return base.resolve("/db2/"+table+"/csv?build="+build);}
  private Path findComplete(SetupStorage storage,Path root,Path pointer,Manifest manifest,BooleanSupplier cancelled)throws IOException {
    if(!Files.isRegularFile(pointer)||Files.size(pointer)>4096)return null;
    Properties values=cachedProperties(pointer);if(values==null)return null;
    if(!manifest.id().equals(values.getProperty("manifest")))return null;
    String name=values.getProperty("directory","");
    if(!name.matches("input-[a-f0-9]{64}-[a-f0-9-]{36}"))return null;
    Path directory=checked(storage,root.resolve(name));
    if(!Files.isDirectory(directory)||!Files.isRegularFile(directory.resolve("build.lock"))
        ||Files.size(checked(storage,directory.resolve("build.lock")))>65536)return null;
    Set<String> expectedFiles=Set.of("build.lock","acquisition.properties","csv");
    try(var files=Files.list(directory)) {
      if(!files.map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(expectedFiles))return null;
    }
    try(var files=Files.list(checked(storage,directory.resolve("csv")))) {
      Set<String> expectedCsv=manifest.tableSha256().keySet().stream().map(tableName->tableName+".csv").collect(java.util.stream.Collectors.toSet());
      if(!files.map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(expectedCsv))return null;
    }
    long totalBytes=0;
    for(var table:manifest.tableSha256().entrySet()) {
      check(cancelled);Path file=checked(storage,directory.resolve("csv/"+table.getKey()+".csv"));
      if(!Files.isRegularFile(file)||Files.size(file)>limits.tableBytes()||!table.getValue().equals(hash(file,cancelled)))return null;
      totalBytes+=Files.size(file);if(totalBytes>limits.totalBytes())return null;
      validateCsv(file);
    }
    String lock=Files.readString(checked(storage,directory.resolve("build.lock")));
    Object parsed;
    try{parsed=dev.wowforever.importer.clientdata.MiniJson.parse(lock);}catch(IllegalArgumentException failure){return null;}
    if(!(parsed instanceof Map<?,?> map)||!manifest.identity().product().equals(map.get("product"))
        ||!manifest.identity().build().equals(map.get("build"))||!manifest.identity().locale().equals(map.get("locale"))
        ||!manifest.tableSha256().equals(map.get("tables"))||!base.toString().equals(map.get("base_url"))
        ||!map.keySet().equals(Set.of("product","build","locale","fetched_at","base_url","tables")))return null;
    Path proof=checked(storage,directory.resolve("acquisition.properties"));
    if(!Files.isRegularFile(proof)||Files.size(proof)>65536)return null;
    Properties evidence=cachedProperties(proof);if(evidence==null)return null;
    if(!"WAGO_EXPORT".equals(evidence.getProperty("provider"))||!"false".equals(evidence.getProperty("cascRootVerified"))
        ||!manifest.id().equals(evidence.getProperty("manifest"))||!base.toString().equals(evidence.getProperty("source")))return null;
    for(String table:manifest.tableSha256().keySet())
      if(!url(table,manifest.identity().build()).toString().equals(evidence.getProperty("url."+table)))return null;
    return directory;
  }

  /** Invalid cached metadata is recoverable; destination/consent checks remain outside this parser. */
  private static Properties cachedProperties(Path file)throws IOException {
    Properties values=new Properties();
    try(var input=Files.newInputStream(file)){values.load(input);return values;}
    catch(IllegalArgumentException|NoSuchFileException invalid){return null;}
  }

  private long download(URI uri,Path output,long maximum,BooleanSupplier cancelled,BiConsumer<Long,Long> bytes)throws IOException {
    AtomicReference<DownloadBody> body=new AtomicReference<>();
    java.util.concurrent.atomic.AtomicBoolean stopped=new java.util.concurrent.atomic.AtomicBoolean();
    HttpRequest request=HttpRequest.newBuilder(uri).timeout(limits.requestTimeout())
        .header("User-Agent","Vibecraft/1 pinned-table-export").header("Accept","text/csv").GET().build();
    CompletableFuture<HttpResponse<Path>> pending=http.sendAsync(request,info->{
      DownloadBody subscriber=new DownloadBody(output,maximum,info.statusCode(),info.headers().firstValueAsLong("Content-Length").orElse(-1),
          info.headers().firstValue("Content-Type").orElse(""),()->cancelled.getAsBoolean()||stopped.get(),bytes);body.set(subscriber);return subscriber;
    });
    long end=System.nanoTime()+limits.requestTimeout().toNanos();
    try {
      while(true){
        check(cancelled);
        if(System.nanoTime()>end)throw new IOException("Table export request timed out");
        try{pending.get(50,TimeUnit.MILLISECONDS);return Files.size(output);}
        catch(TimeoutException wait){/* Poll cancellation even when the server stops sending. */}
        catch(ExecutionException failure){
          check(cancelled);
          if(failure.getCause() instanceof CancellationException cancellation)throw cancellation;
          throw new IOException("Table export failed: "+uri+": "+failure.getCause().getMessage(),failure.getCause());
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
        if(status!=200)throw new IOException("Table export HTTP "+status);
        if(length>maximum)throw new IOException("Table export exceeds byte limit");
        if(type.toLowerCase(Locale.ROOT).contains("html"))throw new IOException("Table export returned HTML");
        output=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);subscription.request(1);
      }catch(Exception failure){abort(failure);}
    }
    public synchronized void onNext(List<ByteBuffer> buffers){
      if(result.isDone())return;
      try{
        for(ByteBuffer buffer:buffers){
          while(buffer.hasRemaining()){
            check(cancelled);int count=Math.min(buffer.remaining(),8192);
            if(received+count>maximum)throw new IOException("Table export exceeds byte limit");
            byte[] chunk=new byte[count];buffer.get(chunk);output.write(chunk);received+=count;
          }
        }
        progress.accept(received,length);subscription.request(1);
      }catch(Exception failure){abort(failure);}
    }
    public synchronized void onError(Throwable failure){abort(failure);}
    public synchronized void onComplete(){
      if(result.isDone())return;
      try{output.close();if(length>=0&&length!=received)throw new IOException("Truncated table export");result.complete(file);}
      catch(Exception failure){abort(failure);}
    }
    synchronized void abort(Throwable failure){
      if(subscription!=null)subscription.cancel();
      if(output!=null)try{output.close();}catch(IOException close){failure.addSuppressed(close);}
      result.completeExceptionally(failure);
    }
  }

  private void pause(long last,BooleanSupplier cancelled)throws IOException {
    while(last!=0&&System.nanoTime()-last<limits.spacing().toNanos()){
      check(cancelled);try{Thread.sleep(25);}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new CancellationException("Export interrupted");}
    }
  }
  private static void check(BooleanSupplier cancelled){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("Export cancelled");}
  private static Path checked(SetupStorage storage,Path path)throws IOException {
    Path verified=storage.verifyDestination(path);if(!verified.equals(path))throw new IOException("Export storage alias changed");return verified;
  }
  private static String hash(Path file,BooleanSupplier cancelled)throws IOException {
    try{
      MessageDigest digest=MessageDigest.getInstance("SHA-256");
      try(var in=Files.newInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){check(cancelled);digest.update(buffer,0,n);}}
      return HexFormat.of().formatHex(digest.digest());
    }catch(NoSuchAlgorithmException failure){throw new IllegalStateException(failure);}
  }
  private static String sha(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(NoSuchAlgorithmException failure){throw new IllegalStateException(failure);}}
  private static void validateCsv(Path file)throws IOException {
    try(var in=new InputStreamReader(Files.newInputStream(file),StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT))){
      StringBuilder header=new StringBuilder();int ch;
      while((ch=in.read())!=-1&&ch!='\n'&&ch!='\r'){if(header.length()>16384)throw new IOException("CSV header too long");header.append((char)ch);}
      String text=header.toString().replace("\uFEFF","");
      try {
        var columns=dev.wowforever.importer.clientdata.CsvTable.read("export header",new StringReader(text+"\n")).columns();
        if(!columns.contains("ID")||text.indexOf('\0')>=0)throw new IOException("Table export is not a CSV with an ID column");
      } catch(IllegalStateException failure){throw new IOException("Malformed table export CSV header",failure);}
    }
  }
  private String buildLock(Manifest manifest){
    String tables=manifest.tableSha256().entrySet().stream().map(e->quote(e.getKey())+":"+quote(e.getValue())).collect(java.util.stream.Collectors.joining(","));
    return "{\"product\":"+quote(manifest.identity().product())+",\"build\":"+quote(manifest.identity().build())+",\"locale\":"+quote(manifest.identity().locale())
        +",\"fetched_at\":"+quote(Instant.now().toString())+",\"base_url\":"+quote(base.toString())+",\"tables\":{"+tables+"}}\n";
  }
  private static String quote(String value){return "\""+value.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
  private static void write(Path file,String value)throws IOException{Files.writeString(file,value,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
  private static void writeProperties(Path file,Properties properties)throws IOException{try(var out=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW)){properties.store(out,"Wago export acquisition; not CASC proof");}}
  private static void removeStage(SetupStorage storage,Path directory,Set<String> tables)throws IOException {
    for(String table:tables)Files.deleteIfExists(checked(storage,directory.resolve("csv/"+table+".csv")));
    for(String name:List.of("build.lock","acquisition.properties"))Files.deleteIfExists(checked(storage,directory.resolve(name)));
    Files.deleteIfExists(checked(storage,directory.resolve("csv")));Files.deleteIfExists(checked(storage,directory));
  }
}
