package dev.wowforever.app.assets;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.function.*;
import javax.swing.SwingUtilities;

/** Read-only official WoW build discovery. Published metadata is not proof of asset availability,
 * supported table layouts, client ownership or a completed gameplay database. Blocking worker API.
 */
public final class BlizzardBuildDiscovery implements AutoCloseable {
    private static final URI OFFICIAL = URI.create("https://us.version.battle.net/");
    private static final int MAX_DOCUMENT = 1024 * 1024;
    public enum Channel { CURRENT, PRELOAD }
    public enum Status { PUBLISHED, NOT_PUBLISHED }
    public record Product(String id, String name) {}
    /** Familiar discovery channels, not an assertion that their gameplay catalogs are supported. */
    public static List<Product> products() {
        return List.of(new Product("wow", "WoW Retail"), new Product("wow_beta", "WoW Retail beta"),
                new Product("wow_classic", "WoW Classic"), new Product("wow_classic_era", "WoW Classic Era"),
                new Product("wow_classic_beta", "WoW Classic beta"));
    }
    public record Build(String product, String region, String version, long buildId, String buildConfigMd5,
                        String cdnConfigMd5, String productConfigMd5, Channel channel) {}
    public record Cdn(String region, String path, List<String> hosts) {
        public Cdn { hosts = List.copyOf(hosts); }
    }
    public record Progress(String stage, URI source, long receivedBytes, long totalBytes) {}
    /** Captured official pointer evidence, not decoded root membership attestation. */
    public record Document(URI source, Instant fetchedAt, Status status, String sha256, long bytes, String text) {}
    public record Snapshot(String product, Instant discoveredAt, List<Build> builds, List<Cdn> cdns,
                           List<Document> documents) {
        public Snapshot { builds=List.copyOf(builds); cdns=List.copyOf(cdns); documents=List.copyOf(documents); }
    }
    record Response(int status, byte[] body) {}
    @FunctionalInterface interface Transport {
        Response get(URI source, BooleanSupplier cancelled, Consumer<Progress> progress) throws IOException, InterruptedException;
    }
    private final HttpClient client;
    private final Transport transport;
    public BlizzardBuildDiscovery() {
        client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        transport=this::download;
    }
    BlizzardBuildDiscovery(Transport transport) { this.transport=Objects.requireNonNull(transport); client=null; }

    /** Exact current/preload pointers are frozen into the result; no game files are downloaded. */
    public Snapshot discover(String product, BooleanSupplier cancelled, Consumer<Progress> progress)
            throws IOException, InterruptedException {
        if(SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Build discovery must run off the EDT");
        if(product==null||!product.matches("wow(?:_[a-z0-9]+)*"))throw new IllegalArgumentException("Invalid WoW product channel");
        Objects.requireNonNull(cancelled);Objects.requireNonNull(progress);check(cancelled);
        List<Document> documents=new ArrayList<>();
        Document versions=document(product,"versions",false,cancelled,progress);documents.add(versions);
        Document cdns=document(product,"cdns",false,cancelled,progress);documents.add(cdns);
        Document preload=document(product,"bgdl",true,cancelled,progress);documents.add(preload);
        List<Build> builds=new ArrayList<>(parseBuilds(product,versions.text(),Channel.CURRENT));
        if(preload.status()==Status.PUBLISHED)builds.addAll(parseBuilds(product,preload.text(),Channel.PRELOAD));
        List<Cdn> locations=parseCdns(cdns.text());
        if(builds.isEmpty()||locations.isEmpty())throw new IOException("Official product has no usable build or CDN pointers");
        check(cancelled);progress.accept(new Progress("Build discovery complete",versions.source(),versions.bytes(),versions.bytes()));
        return new Snapshot(product,Instant.now(),builds,locations,documents);
    }
    private Document document(String product,String endpoint,boolean optional,BooleanSupplier cancelled,Consumer<Progress> progress)
            throws IOException,InterruptedException {
        URI source=OFFICIAL.resolve(product+"/"+endpoint);
        progress.accept(new Progress("Reading "+endpoint,source,0,-1));check(cancelled);
        Response response=transport.get(source,cancelled,progress);check(cancelled);
        if(response.body().length>MAX_DOCUMENT)throw new IOException("Official metadata exceeds size bound");
        String text;
        try { text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(response.body())).toString(); }
        catch(CharacterCodingException badText){throw new IOException("Official metadata is not UTF-8",badText);}
        boolean absent=optional&&(response.status()==404||response.status()==204
                || (response.status()==200&&(text.isBlank()||text.strip().startsWith("No matched data for selector"))));
        if(!absent&&response.status()!=200)throw new IOException("Official "+endpoint+" metadata HTTP "+response.status());
        return new Document(source,Instant.now(),absent?Status.NOT_PUBLISHED:Status.PUBLISHED,
                sha256(response.body()),response.body().length,text);
    }
    static List<Build> parseBuilds(String product,String text,Channel channel)throws IOException {
        List<Map<String,String>> rows=table(text,Set.of("Region","BuildConfig","CDNConfig","BuildId","VersionsName"));
        List<Build> builds=new ArrayList<>();Set<String> unique=new HashSet<>();
        for(Map<String,String> row:rows){
            String region=region(row.get("Region")),version=row.get("VersionsName");
            if(!version.matches("[0-9]+(?:\\.[0-9]+){3}"))throw new IOException("Invalid official build version");
            long id;
            try{id=Long.parseLong(row.get("BuildId"));}catch(NumberFormatException e){throw new IOException("Invalid official build ID",e);}
            if(id<=0||!Long.toString(id).equals(version.substring(version.lastIndexOf('.')+1)))throw new IOException("Official build ID/version mismatch");
            String config=key(row.get("BuildConfig")),cdn=key(row.get("CDNConfig")),productConfig=row.getOrDefault("ProductConfig","");
            if(!productConfig.isEmpty())key(productConfig);
            String identity=region+":"+version+":"+config+":"+cdn;
            if(!unique.add(identity))continue;
            builds.add(new Build(product,region,version,id,config,cdn,productConfig,channel));
        }
        return List.copyOf(builds);
    }
    static List<Cdn> parseCdns(String text)throws IOException {
        List<Cdn> cdns=new ArrayList<>();
        for(Map<String,String> row:table(text,Set.of("Name","Path","Hosts"))){
            String region=region(row.get("Name")),path=row.get("Path");
            if(!path.matches("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*"))throw new IOException("Invalid official CDN path");
            List<String> hosts=new ArrayList<>();
            for(String host:row.get("Hosts").strip().split("\\s+")){
                if(!host.matches("[a-zA-Z0-9](?:[a-zA-Z0-9.-]*[a-zA-Z0-9])?")||host.contains(".."))throw new IOException("Invalid official CDN host");
                hosts.add(host.toLowerCase(Locale.ROOT));
            }
            cdns.add(new Cdn(region,path,hosts));
        }
        return List.copyOf(cdns);
    }
    private static List<Map<String,String>> table(String text,Set<String> required)throws IOException {
        if(text.startsWith("\ufeff"))text=text.substring(1);
        String[] names=null;List<Map<String,String>> rows=new ArrayList<>();
        for(String line:text.split("\\R")){
            if(line.isBlank()||line.startsWith("#"))continue;
            String[] cells=line.split("\\|",-1);
            if(names==null){
                names=new String[cells.length];Set<String> seen=new HashSet<>();
                for(int i=0;i<cells.length;i++){names[i]=cells[i].split("!",2)[0];if(names[i].isBlank()||!seen.add(names[i]))throw new IOException("Duplicate/empty official metadata column");}
                if(!seen.containsAll(required))throw new IOException("Official metadata lacks required columns");
            }else{
                if(cells.length!=names.length)throw new IOException("Official metadata row has wrong column count");
                Map<String,String> row=new LinkedHashMap<>();for(int i=0;i<cells.length;i++)row.put(names[i],cells[i]);rows.add(Map.copyOf(row));
            }
        }
        if(names==null)throw new IOException("Official metadata has no table header");
        return rows;
    }
    private static String region(String value)throws IOException {if(value==null||!value.matches("[a-z][a-z0-9-]{0,15}"))throw new IOException("Invalid official region");return value;}
    private static String key(String value)throws IOException {if(value==null||!value.matches("[0-9a-f]{32}"))throw new IOException("Invalid official configuration hash");return value;}
    private static String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static void check(BooleanSupplier cancelled){if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new CancellationException("Build discovery cancelled");}
    private Response download(URI source,BooleanSupplier cancelled,Consumer<Progress> progress)throws IOException,InterruptedException {
        check(cancelled);
        HttpRequest request=HttpRequest.newBuilder(source).timeout(Duration.ofSeconds(60))
                .header("User-Agent","Vibecraft-build-discovery/1").GET().build();
        var future=client.sendAsync(request,info->new BoundedBody(source,info.headers().firstValueAsLong("Content-Length").orElse(-1),progress));
        long deadline=System.nanoTime()+Duration.ofSeconds(90).toNanos();
        try{
            while(!future.isDone()){check(cancelled);if(System.nanoTime()>deadline)throw new IOException("Official metadata transfer timed out");Thread.sleep(25);}
            HttpResponse<byte[]> response;
            try{response=future.join();}catch(CompletionException failure){throw new IOException("Official metadata transport failed",failure.getCause());}
            check(cancelled);return new Response(response.statusCode(),response.body());
        }finally{if(!future.isDone())future.cancel(true);}
    }
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private final URI source;private final long total;private final Consumer<Progress> progress;private Flow.Subscription subscription;
        BoundedBody(URI source,long total,Consumer<Progress> progress){this.source=source;this.total=total;this.progress=progress;}
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription s){subscription=s;if(total>MAX_DOCUMENT){s.cancel();result.completeExceptionally(new IOException("Official metadata exceeds size bound"));}else s.request(1);}
        public void onNext(List<ByteBuffer> buffers){
            try{
                for(ByteBuffer b:buffers){if(b.remaining()>MAX_DOCUMENT-bytes.size()){subscription.cancel();result.completeExceptionally(new IOException("Official metadata exceeds size bound"));return;}byte[] block=new byte[b.remaining()];b.get(block);bytes.writeBytes(block);}
                progress.accept(new Progress("Downloading metadata",source,bytes.size(),total));subscription.request(1);
            }catch(RuntimeException failure){subscription.cancel();result.completeExceptionally(failure);}
        }
        public void onError(Throwable failure){result.completeExceptionally(failure);}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }
    @Override public void close(){if(client!=null)client.shutdownNow();}
}
