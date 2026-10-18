package dev.wowforever.app.assets;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.*;
import javax.swing.SwingUtilities;

/** Selected-build CASC reader; legacy audio constructors retain the reviewed Forever 70450 profile. Blocking API: call only on a worker thread.
 * Config is pinned; encoding and root are authenticated by their decoded content hashes.
 * No external executable or local metadata trust is required. Unsupported formats fail closed.
 */
public final class NativeAssetClient implements AutoCloseable {
    public static final String PRODUCT = "wow_classic_beta", BUILD = "1.60.1.70450";
    public static final String CONFIG = "84bea4233331e390fda8724c6f91ffa5";
    public enum TransportPolicy { OFFICIAL_ONLY, ALLOW_ROOT_VERIFIED_WAGO }
    @FunctionalInterface interface Transport {
        byte[] download(URI uri,int limit,BooleanSupplier cancelled)throws IOException,InterruptedException;
    }
    @FunctionalInterface interface RangeTransport {
        byte[] download(URI uri,long offset,int size,BooleanSupplier cancelled)throws IOException,InterruptedException;
    }
    /** Bounds index metadata searched on a loose-file miss, never archive-body download size. */
    public record ArchiveBudget(int indexes,long bytes) {
        public ArchiveBudget { if(indexes<1||indexes>2048||bytes<28||bytes>128L*1024*1024)throw new IllegalArgumentException("Archive lookup budget"); }
        public static ArchiveBudget standard(){return new ArchiveBudget(64,16L*1024*1024);}
    }
    private boolean sharedRuntime;
    private final RangeTransport rangeTransport;
    private final ArchiveBudget archiveBudget;
    private List<String> archives=List.of();
    private int scannedIndexes,newIndexes;
    private long newIndexBytes;
    private String usedArchive="";
    private long usedArchiveOffset,usedArchiveBytes;
    private long scannedIndexBytes;
    private static final int LIMIT = 512 * 1024 * 1024;
    private static final Object RATE = new Object();
    private static long nextRequest;
    private final Path cache;
    private final HttpClient http;
    private final BlizzardBuildDescriptor descriptor;
    private final TransportPolicy policy;
    private final Transport transport;
    private byte[] encoding, root;
    private String rootKey;
    private final java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();
    public record Asset(Path path, long fileDataId, String contentMd5, String sha256, long bytes,
                        String product, String build, String rootContentMd5) {}
    private record Encoding(long size, List<String> keys) {}
    /** Shared audio metadata/cache owner, initialized lazily by the first worker request. */
    private static final class Shared {
        static final NativeAssetClient INSTANCE = create();
        private static NativeAssetClient create() {
            NativeAssetClient client = new NativeAssetClient(systemCache(), PRODUCT, BUILD).withSharedPermissions();
            Runtime.getRuntime().addShutdownHook(new Thread(client::close, "native-audio-assets-shutdown"));
            return client;
        }
    }
    NativeAssetClient withSharedPermissions(){sharedRuntime=true;return this;}
    public static NativeAssetClient shared() { return Shared.INSTANCE; }
    public static Path systemCache() {
        String configured = System.getProperty("wowforever.nativeAssets.cache", System.getenv("NPC_PREVIEW_ASSET_CACHE"));
        if (configured != null && !configured.isBlank()) return Path.of(configured);
        String base = System.getenv("XDG_CACHE_HOME");
        if (base == null || base.isBlank()) base = System.getProperty("user.home") + "/.cache";
        return Path.of(base, "wowforever-browser", "native-assets");
    }

    public NativeAssetClient(Path cache, String product, String build) {
        this(cache, legacyProfile(product,build), TransportPolicy.ALLOW_ROOT_VERIFIED_WAGO);
    }
    private static BlizzardBuildDescriptor legacyProfile(String product,String build) {
        if (!PRODUCT.equals(product) || !BUILD.equals(build))
            throw new IllegalArgumentException("No authenticated native asset profile for " + product + " " + build);
        return BlizzardBuildDescriptor.legacyForever();
    }
    /** New setup jobs must pass their explicit transport permission; there is no implicit third-party fallback. */
    public NativeAssetClient(Path cache,BlizzardBuildDescriptor descriptor,TransportPolicy policy) {
        this(cache,descriptor,policy,ArchiveBudget.standard());
    }
    public NativeAssetClient(Path cache,BlizzardBuildDescriptor descriptor,TransportPolicy policy,ArchiveBudget archiveBudget) {
        this.cache=Objects.requireNonNull(cache).toAbsolutePath().normalize();
        this.descriptor=Objects.requireNonNull(descriptor);this.policy=Objects.requireNonNull(policy);
        this.archiveBudget=Objects.requireNonNull(archiveBudget);
        http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
        transport=this::download; rangeTransport=this::downloadRange;
    }
    NativeAssetClient(Path cache,BlizzardBuildDescriptor descriptor,TransportPolicy policy,Transport transport) {
        this(cache,descriptor,policy,transport,(u,o,n,c)->{throw new IOException("Range transport not configured");},ArchiveBudget.standard());
    }
    NativeAssetClient(Path cache,BlizzardBuildDescriptor descriptor,TransportPolicy policy,Transport transport,
                      RangeTransport rangeTransport,ArchiveBudget archiveBudget) {
        this.cache=Objects.requireNonNull(cache).toAbsolutePath().normalize();this.descriptor=Objects.requireNonNull(descriptor);
        this.policy=Objects.requireNonNull(policy);this.transport=Objects.requireNonNull(transport);http=null;
        this.rangeTransport=Objects.requireNonNull(rangeTransport);this.archiveBudget=Objects.requireNonNull(archiveBudget);
    }
    /** Cancellation is checked during cache reads, transport and metadata parsing. */
    public Asset fetch(long fileDataId, BooleanSupplier cancelled) throws IOException, InterruptedException {
        return fetch(fileDataId, cancelled, LIMIT);
    }
    public Asset fetch(long fileDataId, BooleanSupplier cancelled, int maxAssetBytes) throws IOException, InterruptedException {
        if (maxAssetBytes <= 0 || maxAssetBytes > LIMIT) throw new IllegalArgumentException("Invalid asset bound");
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Native assets must load off the EDT");
        if (fileDataId <= 0 || fileDataId > 0xffffffffL) throw new IllegalArgumentException("Invalid native file ID");
        Objects.requireNonNull(cancelled);
        check(cancelled);
        while (!lock.tryLock(50, java.util.concurrent.TimeUnit.MILLISECONDS)) check(cancelled);
        try { return fetchLocked(fileDataId, cancelled, maxAssetBytes); }
        finally { lock.unlock(); }
    }
    private Asset fetchLocked(long fileDataId, BooleanSupplier cancelled, int maxAssetBytes) throws IOException, InterruptedException {
        check(cancelled);
        writableStorage(cache);
        if(sharedRuntime)AssetDownloadPermissions.checkCacheWrite(cache,cache);
        scannedIndexes=0;scannedIndexBytes=0;newIndexes=0;newIndexBytes=0;usedArchive="";usedArchiveOffset=0;usedArchiveBytes=0;
        prepare(cancelled);
        String key = rootEntry(root, fileDataId, descriptor.localeMask(), cancelled);
        if (key == null) throw new FileNotFoundException("No neutral/"+descriptor.locale()+" native asset " + fileDataId + " in " + descriptor.build());
        Encoding entry = encodingEntry(encoding, key, cancelled);
        if (entry == null) throw new IOException("Root asset absent from authenticated encoding");
        if (entry.size() > maxAssetBytes) throw new IOException("Native asset exceeds requested size bound");
        byte[] bytes;
        usedArchive="";usedArchiveOffset=0;usedArchiveBytes=0;
        String usedTransport="official CDN or verified content cache";
        try { bytes = content(key, entry.keys(), cancelled, maxAssetBytes); }
        catch (IOException officialUnavailable) {
            if(policy==TransportPolicy.OFFICIAL_ONLY)throw officialUnavailable;
            // Separate opt-in transport only: exact root MD5 and encoding size remain mandatory.
            usedTransport="root-verified Wago transport";
            usedArchive="";usedArchiveOffset=0;usedArchiveBytes=0;
            bytes = received(URI.create("https://wago.tools/api/casc/" + fileDataId
                    + "?download&version=" + descriptor.build() + "&branch=" + descriptor.product()), maxAssetBytes, cancelled);
            verify(bytes, key);
            if(bytes.length!=entry.size())throw new IOException("Fallback asset size differs from authenticated encoding");
            check(cancelled);atomic(cache.resolve("content/" + key + ".bin"), bytes);
        }
        if (bytes.length != entry.size()) throw new IOException("Asset size differs from authenticated encoding");
        check(cancelled);
        String sha = digest("SHA-256", bytes);
        String proof = "{\n  \"product\": \"" + descriptor.product() + "\",\n  \"build\": \"" + descriptor.build()
                + "\",\n  \"file_data_id\": " + fileDataId + ",\n  \"build_config_md5\": \"" + descriptor.buildConfigMd5()
                + "\",\n  \"root_content_md5\": \"" + rootKey + "\",\n  \"content_md5\": \"" + key
                + "\",\n  \"sha256\": \"" + sha + "\",\n  \"bytes\": " + bytes.length
                + ",\n  \"exact_root_membership_verified\": true,\n  \"locale\": \""+descriptor.locale()
                + "\",\n  \"transport_policy\": \""+policy+"\",\n  \"transport\": \""+usedTransport
                + "\",\n  \"cdn_config_md5\": \""+descriptor.cdnConfigMd5()+"\",\n  \"region\": \""+descriptor.region()
                + "\",\n  \"discovery_captured_at\": \""+descriptor.capturedAt()
                + "\",\n  \"discovery_build_pointer_sha256\": \""+descriptor.metadataSha256().getOrDefault("versions",descriptor.metadataSha256().getOrDefault("bgdl",""))
                + "\",\n  \"discovery_cdns_sha256\": \""+descriptor.metadataSha256().getOrDefault("cdns","")
                + "\",\n  \"archive_md5\": \""+usedArchive+"\",\n  \"archive_offset\": "+usedArchiveOffset
                + ",\n  \"archive_encoded_bytes\": "+usedArchiveBytes+",\n  \"new_archive_indexes\": "+newIndexes
                + ",\n  \"new_archive_index_bytes\": "+newIndexBytes
                + ",\n  \"implementation\": \"java-native-casc-v3\"\n}\n";
        atomic(cache.resolve("proof/" + descriptor.product() + "/" + descriptor.build() + "/" + fileDataId + ".json"), proof.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new Asset(cache.resolve("content/" + key + ".bin"), fileDataId, key, sha, bytes.length, descriptor.product(), descriptor.build(), rootKey);
    }
    private void prepare(BooleanSupplier cancelled) throws IOException, InterruptedException {
        if (root != null) return;
        String configKey=descriptor.buildConfigMd5();
        Path configPath = cache.resolve("config/" + configKey);
        byte[] config = Files.exists(configPath) ? read(configPath, 1024 * 1024, cancelled)
                : official("config/"+keyPath(configKey),1024*1024,cancelled);
        verify(config, configKey);
        Map<String,String> values = new HashMap<>();
        for (String line : new String(config, java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
            int at = line.indexOf(" = ");
            if (at > 0) values.put(line.substring(0, at), line.substring(at + 3));
        }
        String build=descriptor.build(),number=build.substring(build.lastIndexOf('.')+1),patch=build.substring(0,build.lastIndexOf('.'));
        if(!values.getOrDefault("build-name","").matches("WOW-"+java.util.regex.Pattern.quote(number)+"patch"+java.util.regex.Pattern.quote(patch)+"(?:_.*)?"))
            throw new IOException("Selected configuration build-name mismatch");
        if(!descriptor.product().equals(values.getOrDefault("build-uid",descriptor.product())))throw new IOException("Selected configuration product mismatch");
        if(!descriptor.cdnConfigMd5().isBlank()){
            String cdnKey=descriptor.cdnConfigMd5();Path cdnPath=cache.resolve("config/"+cdnKey);
            byte[] cdn=Files.exists(cdnPath)?read(cdnPath,1024*1024,cancelled):official("config/"+keyPath(cdnKey),1024*1024,cancelled);
            verify(cdn,cdnKey);check(cancelled);archives=parseArchives(cdn);atomic(cdnPath,cdn);
        }
        String[] pair = values.getOrDefault("encoding", "").split("\\s+");
        if (pair.length != 2) throw new IOException("Missing encoding identity");
        String candidateRoot = values.get("root");
        validKey(candidateRoot);
        byte[] candidateEncoding = content(pair[0], List.of(pair[1]), cancelled);
        Encoding rootEncoding = encodingEntry(candidateEncoding, candidateRoot, cancelled);
        if (rootEncoding == null) throw new IOException("Root absent from authenticated encoding");
        byte[] candidate = content(candidateRoot, rootEncoding.keys(), cancelled);
        if (candidate.length != rootEncoding.size()) throw new IOException("Root encoded size mismatch");
        atomic(configPath, config);
        encoding = candidateEncoding; root = candidate; rootKey = candidateRoot;
    }
    private byte[] content(String key, List<String> keys, BooleanSupplier cancelled) throws IOException, InterruptedException {
        return content(key, keys, cancelled, LIMIT);
    }
    private byte[] content(String key, List<String> keys, BooleanSupplier cancelled, int limit) throws IOException, InterruptedException {
        validKey(key);
        Path path = cache.resolve("content/" + key + ".bin");
        if (Files.exists(path)) { byte[] bytes = read(path, limit, cancelled); verify(bytes, key); return bytes; }
        IOException failure = new IOException("No verified native encoding for " + key);
        for (String encodedKey : keys) {
            validKey(encodedKey);
            try {
                Path encodedPath = cache.resolve("encoded/" + encodedKey + ".blte");
                byte[] encoded;
                if(Files.exists(encodedPath))encoded=read(encodedPath,LIMIT,cancelled);
                else {
                    try{encoded=official("data/"+keyPath(encodedKey),encodedBound(limit),cancelled);}
                    catch(IOException looseMissing){
                        try{encoded=archiveContent(encodedKey,cancelled,encodedBound(limit));}
                        catch(IOException missing){missing.addSuppressed(looseMissing);throw missing;}
                    }
                }
                verifyEncoded(encoded,encodedKey);
                byte[] decoded = decodeBlte(encoded, limit);
                verify(decoded, key); check(cancelled);
                atomic(encodedPath, encoded); atomic(path, decoded); return decoded;
            } catch (IOException e) { failure.addSuppressed(e); }
        }
        throw failure;
    }
    static List<String> parseArchives(byte[] cdn)throws IOException {
        String value=null;
        for(String line:new String(cdn,java.nio.charset.StandardCharsets.UTF_8).split("\\R"))
            if(line.startsWith("archives = ")){if(value!=null)throw new IOException("Duplicate archive list");value=line.substring(11).trim();}
        if(value==null||value.isEmpty())return List.of();
        String[] keys=value.split("\\s+");if(keys.length>2048)throw new IOException("Archive list exceeds bound");
        var unique=new HashSet<String>();for(String key:keys){validKey(key);if(!unique.add(key))throw new IOException("Duplicate archive identity");}
        return List.of(keys);
    }
    private byte[] archiveIndex(String archive,BooleanSupplier cancelled)throws IOException,InterruptedException {
        if(scannedIndexes>=2048||scannedIndexBytes>=128L*1024*1024)throw new IOException("Archive index parse budget exhausted");
        Path path=cache.resolve("indexes/"+archive+".index");
        byte[] index;
        if(Files.exists(path))index=read(path,(int)Math.min(2*1024*1024,128L*1024*1024-scannedIndexBytes),cancelled);
        else {
            if(newIndexes>=archiveBudget.indexes()||newIndexBytes>=archiveBudget.bytes())
                throw new IOException("Archive index network budget exhausted after "+newIndexes+" new indexes / "+newIndexBytes+" bytes; retry can reuse verified indexes; content availability remains unknown");
            int bound=(int)Math.min(2*1024*1024,archiveBudget.bytes()-newIndexBytes);
            newIndexes++;index=official("data/"+keyPath(archive)+".index",bound,cancelled);newIndexBytes+=index.length;
        }
        scannedIndexes++;scannedIndexBytes+=index.length;return index;
    }
    private byte[] archiveRange(String archive,CdnArchiveIndex.Entry entry,String encodedKey,BooleanSupplier cancelled,int encodedLimit)throws IOException,InterruptedException {
        if(entry.size()>encodedLimit)throw new IOException("Archived encoded asset exceeds bound");
        IOException unavailable=new IOException("Selected archive range unavailable");
        for(URI base:descriptor.cdnBases()) {
            check(cancelled);
            try {
                networkAllowed(base.resolve("data/"+keyPath(archive)));
                byte[] encoded=rangeTransport.download(base.resolve("data/"+keyPath(archive)),entry.offset(),entry.size(),cancelled);
                check(cancelled);if(encoded.length!=entry.size())throw new IOException("Archive range length mismatch");
                verifyEncoded(encoded,encodedKey);usedArchive=archive;usedArchiveOffset=entry.offset();usedArchiveBytes=entry.size();return encoded;
            }catch(IOException failure){unavailable.addSuppressed(failure);}
        }
        throw unavailable;
    }
    private byte[] archiveContent(String encodedKey,BooleanSupplier cancelled,int encodedLimit)throws IOException,InterruptedException {
        Path location=cache.resolve("archive-locations/"+descriptor.cdnConfigMd5()+"/"+encodedKey);
        if(Files.exists(location)) {
            String archive=new String(read(location,32,cancelled),java.nio.charset.StandardCharsets.US_ASCII);
            if(!archives.contains(archive))throw new IOException("Cached archive location not in selected configuration");
            byte[] index=archiveIndex(archive,cancelled);
            var entry=CdnArchiveIndex.find(index,archive,encodedKey,cancelled);
            if(entry==null)throw new IOException("Cached archive location lacks exact EKey membership");
            return archiveRange(archive,entry,encodedKey,cancelled,encodedLimit);
        }
        for(String archive:archives) {
            check(cancelled);byte[] index=archiveIndex(archive,cancelled);
            var entry=CdnArchiveIndex.find(index,archive,encodedKey,cancelled);
            Path path=cache.resolve("indexes/"+archive+".index");
            check(cancelled);if(!Files.exists(path))atomic(path,index);
            if(entry==null)continue;
            byte[] encoded=archiveRange(archive,entry,encodedKey,cancelled,encodedLimit);
            check(cancelled);atomic(location,archive.getBytes(java.nio.charset.StandardCharsets.US_ASCII));return encoded;
        }
        throw new FileNotFoundException("Encoding key absent from selected archive indexes");
    }
    private static int encodedBound(int decodedLimit){return (int)Math.min(LIMIT,Math.max(1024*1024L,2L*decodedLimit+65536));}
    static void verifyEncoded(byte[] encoded,String key)throws IOException {
        if(encoded.length<8)throw new IOException("Truncated encoded identity");
        int header=ByteBuffer.wrap(encoded,4,4).getInt();
        if(header<0||header>encoded.length)throw new IOException("Invalid encoded header identity");
        verify(header==0?encoded:Arrays.copyOf(encoded,header),key);
    }
    private static String keyPath(String key)throws IOException{validKey(key);return key.substring(0,2)+"/"+key.substring(2,4)+"/"+key;}
    private byte[] official(String relative,int limit,BooleanSupplier cancelled)throws IOException,InterruptedException{
        IOException failure=new IOException("No selected official CDN host supplied verified bytes");
        for(URI base:descriptor.cdnBases()){
            check(cancelled);try{return received(base.resolve(relative),limit,cancelled);}catch(IOException unavailable){failure.addSuppressed(unavailable);}
        }
        throw failure;
    }
    private byte[] received(URI uri,int limit,BooleanSupplier cancelled)throws IOException,InterruptedException{
        networkAllowed(uri);
        byte[] bytes=transport.download(uri,limit,cancelled);check(cancelled);
        if(bytes.length>limit)throw new IOException("Native response exceeds bound");return bytes;
    }
    private void networkAllowed(URI uri)throws IOException {
        if(!sharedRuntime)return;
        var consent=AssetDownloadPermissions.current();
        boolean allowed="wago.tools".equals(uri.getHost())?consent.allowWagoExport():consent.allowBlizzardCdn();
        if(!allowed)throw new IOException("Active library has not selected this asset network source");
    }
    public BlizzardBuildDescriptor descriptor(){return descriptor;}
    private byte[] download(URI uri, int limit, BooleanSupplier cancelled) throws IOException, InterruptedException {
        return downloadResponse(uri,limit,cancelled,null,-1,-1);
    }
    private byte[] downloadRange(URI uri,long offset,int size,BooleanSupplier cancelled)throws IOException,InterruptedException {
        return downloadResponse(uri,size,cancelled,"bytes="+offset+"-"+(offset+size-1),offset,size);
    }
    private byte[] downloadResponse(URI uri,int limit,BooleanSupplier cancelled,String range,long offset,int size)throws IOException,InterruptedException {
        networkAllowed(uri);
        synchronized (RATE) {
            while (System.nanoTime() < nextRequest) { check(cancelled); Thread.sleep(50); }
            nextRequest = System.nanoTime() + 500_000_000L;
        }
        check(cancelled);
        var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(90)).header("Accept-Encoding","identity").GET();
        if(range!=null)builder.header("Range",range);
        HttpRequest request=builder.build();
        // ofByteArray enforces the request deadline throughout body transfer; subscriber bounds memory.
        var future = http.sendAsync(request, response -> new BoundedBody(limit));
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        try {
            while (!future.isDone()) {
                check(cancelled);
                if (System.nanoTime() > deadline) throw new IOException("Native transport exceeded deadline");
                Thread.sleep(25);
            }
            HttpResponse<byte[]> response;
            try { response = future.join(); }
            catch (java.util.concurrent.CompletionException e) { throw new IOException("Native transport failed", e.getCause()); }
            validateResponse(response.statusCode(),response.headers().firstValue("Content-Range").orElse(""),response.body().length,offset,size);
            if(response.headers().firstValue("Content-Encoding").filter(v->!v.equalsIgnoreCase("identity")).isPresent())
                throw new IOException("Unexpected native content encoding");
            check(cancelled); return response.body();
        } finally { if (!future.isDone()) future.cancel(true); }
    }
    static void validateResponse(int status,String contentRange,int actualBytes,long offset,int size)throws IOException {
        if(offset<0){if(status!=200)throw new IOException("Native transport HTTP "+status);return;}
        if(status!=206||actualBytes!=size)throw new IOException("Archive request requires exact bounded HTTP 206");
        var match=java.util.regex.Pattern.compile("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matcher(contentRange);
        try {
            if(!match.matches()||Long.parseLong(match.group(1))!=offset||Long.parseLong(match.group(2))!=offset+size-1
                    ||Long.parseLong(match.group(3))<offset+size)throw new IOException("Archive Content-Range mismatch");
        }catch(NumberFormatException invalid){throw new IOException("Invalid archive Content-Range",invalid);}
    }
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final java.util.concurrent.CompletableFuture<byte[]> result = new java.util.concurrent.CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int limit; private java.util.concurrent.Flow.Subscription subscription;
        BoundedBody(int limit) { this.limit = limit; }
        public java.util.concurrent.CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(java.util.concurrent.Flow.Subscription s) { subscription = s; s.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer b : buffers) {
                if (b.remaining() > limit - bytes.size()) { subscription.cancel(); result.completeExceptionally(new IOException("Native response exceeds bound")); return; }
                byte[] block = new byte[b.remaining()]; b.get(block); bytes.writeBytes(block);
            }
            subscription.request(1);
        }
        public void onError(Throwable t) { result.completeExceptionally(t); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
    static byte[] decodeBlte(byte[] raw, int limit) throws IOException {
        try {
            ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
            if (b.getInt() != 0x424c5445) throw new IOException("Not BLTE");
            int header = b.getInt();
            if (header == 0) return block(Arrays.copyOfRange(raw, 8, raw.length), limit);
            if (b.get() != 15) throw new IOException("Unsupported BLTE table");
            int count = (Byte.toUnsignedInt(b.get()) << 16) | (Byte.toUnsignedInt(b.get()) << 8) | Byte.toUnsignedInt(b.get());
            if (count == 0 || header != 12L + 24L * count || header > raw.length) throw new IOException("Invalid BLTE header");
            ByteArrayOutputStream output = new ByteArrayOutputStream(); int offset = header;
            for (int i = 0; i < count; i++) {
                int size = b.getInt(), unpacked = b.getInt(); byte[] hash = new byte[16]; b.get(hash);
                if (size <= 0 || unpacked < 0 || unpacked > limit - output.size() || size > raw.length - offset) throw new IOException("Invalid BLTE size");
                byte[] encoded = Arrays.copyOfRange(raw, offset, offset + size);
                verify(encoded, HexFormat.of().formatHex(hash));
                byte[] decoded = block(encoded, unpacked);
                if (decoded.length != unpacked) throw new IOException("BLTE decoded length mismatch");
                output.writeBytes(decoded); offset += size;
            }
            if (offset != raw.length) throw new IOException("Trailing BLTE bytes");
            return output.toByteArray();
        } catch (BufferUnderflowException | IndexOutOfBoundsException e) { throw new IOException("Truncated BLTE", e); }
    }
    private static byte[] block(byte[] bytes, int limit) throws IOException {
        if (bytes.length < 1) throw new IOException("Empty BLTE block");
        if (bytes[0] == 'N') {
            if (bytes.length - 1 > limit) throw new IOException("BLTE exceeds bound");
            return Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        if (bytes[0] != 'Z') throw new IOException("Unsupported/encrypted BLTE block");
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(bytes, 1, bytes.length - 1);
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[65536];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n > limit - out.size()) throw new IOException("BLTE inflation exceeds bound");
                out.write(buf, 0, n);
                if (n == 0 && !inflater.finished()) throw new IOException("Invalid BLTE compression framing");
            }
            if (inflater.getRemaining() != 0) throw new IOException("Trailing compressed bytes");
            return out.toByteArray();
        } catch (DataFormatException e) { throw new IOException("Invalid BLTE compression", e); }
        finally { inflater.end(); }
    }
    static Encoding encodingEntry(byte[] raw, String wanted, BooleanSupplier cancelled) throws IOException {
        try {
            ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
            if (b.getShort() != 0x454e || raw[3] != 16 || raw[4] != 16) throw new IOException("Unsupported encoding table");
            int page = Short.toUnsignedInt(b.getShort(5)) * 1024;
            long count = Integer.toUnsignedLong(b.getInt(9)), spec = Integer.toUnsignedLong(b.getInt(18));
            long start = 22 + spec + count * 32;
            if (page == 0 || start + count * page > raw.length) throw new IOException("Invalid encoding extent");
            byte[] key = HexFormat.of().parseHex(wanted);
            for (int i = 0; i < count; i++) {
                check(cancelled); int at = (int)(start + (long)page * i), end = at + page;
                while (at < end) {
                    int n = Byte.toUnsignedInt(raw[at++]); if (n == 0) break;
                    if (at + 21L + n * 16L > end) throw new IOException("Encoding entry exceeds page");
                    long size = 0; for (int j = 0; j < 5; j++) size = (size << 8) | Byte.toUnsignedInt(raw[at++]);
                    boolean match = Arrays.equals(raw, at, at + 16, key, 0, 16); at += 16;
                    List<String> keys = new ArrayList<>();
                    for (int j = 0; j < n; j++, at += 16) if (match) keys.add(HexFormat.of().formatHex(raw, at, at + 16));
                    if (match) return new Encoding(size, List.copyOf(keys));
                }
            }
            return null;
        } catch (IndexOutOfBoundsException | BufferUnderflowException | IllegalArgumentException e) { throw new IOException("Malformed encoding", e); }
    }
    static String rootEntry(byte[] raw,long wanted,BooleanSupplier cancelled)throws IOException{return rootEntry(raw,wanted,2,cancelled);}
    static String rootEntry(byte[] raw, long wanted, int localeMask, BooleanSupplier cancelled) throws IOException {
        try {
            ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            if (b.getInt() != 0x4d465354 || b.getInt() != 24) throw new IOException("Unsupported native root");
            int version = b.getInt(), total = b.getInt(), named = b.getInt();
            if (version != 1 && version != 2) throw new IOException("Unsupported root version");
            b.position(24); String selected = null;
            while (b.hasRemaining()) {
                check(cancelled); int count = b.getInt(), flags, locale;
                if (version == 1) { flags = b.getInt(); locale = b.getInt(); }
                else { locale = b.getInt(); flags = b.getInt() | b.getInt() | (Byte.toUnsignedInt(b.get()) << 17); }
                if (count < 0 || (long)count * 20 > b.remaining()) throw new IOException("Invalid root block count");
                long last = 0; int selectedIndex = -1;
                for (int i = 0; i < count; i++) { long id = last + b.getInt(); last = id + 1; if (id == wanted) selectedIndex = i; }
                int keysAt = b.position();
                if (selected == null && selectedIndex >= 0 && (locale == 0 || (locale & localeMask) != 0) && (flags & 0x80) == 0)
                    selected = HexFormat.of().formatHex(raw, keysAt + selectedIndex * 16, keysAt + selectedIndex * 16 + 16);
                long skip = (long)count * 16 + (total != named && (flags & 0x10000000) != 0 ? 0 : (long)count * 8);
                if (skip > b.remaining()) throw new IOException("Truncated root block");
                b.position((int)(keysAt + skip));
            }
            return selected;
        } catch (IndexOutOfBoundsException | BufferUnderflowException e) { throw new IOException("Truncated native root", e); }
    }
    @Override public void close() { if(http!=null)http.shutdownNow(); }
    private static void check(BooleanSupplier cancelled) {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new CancellationException("Native asset request cancelled");
    }
    private static byte[] read(Path path, int limit, BooleanSupplier cancelled) throws IOException {
        check(cancelled);
        if (Files.size(path) > limit) throw new IOException("Cached native content exceeds bound");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] bytes = in.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("Cached native content exceeds bound");
            check(cancelled); return bytes;
        }
    }
    private static void validKey(String key) throws IOException { if (key == null || !key.matches("[0-9a-f]{32}")) throw new IOException("Invalid native content key"); }
    private static void verify(byte[] bytes, String key) throws IOException { validKey(key); if (!digest("MD5", bytes).equals(key)) throw new IOException("Native content MD5 mismatch"); }
    private static String digest(String algorithm, byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes)); } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); } }
    private static void writableStorage(Path path)throws IOException{
        Path absolute=path.toAbsolutePath().normalize(),existing=absolute;
        while(existing!=null&&!Files.exists(existing))existing=existing.getParent();
        if(existing==null)throw new IOException("Cannot resolve native cache storage");
        Path canonical=existing.toRealPath().resolve(existing.relativize(absolute));
        for(Path parent=canonical;parent!=null;parent=parent.getParent())
            if(Files.isRegularFile(parent.resolve(".build.info")))throw new IOException("Native cache must not be inside a WoW installation");
    }
    private void atomic(Path path, byte[] bytes) throws IOException {
        writableStorage(path);
        if(sharedRuntime)path=AssetDownloadPermissions.checkCacheWrite(cache,path);
        Files.createDirectories(path.getParent()); Path temp = Files.createTempFile(path.getParent(), ".native-", ".tmp");
        try { Files.write(temp, bytes); Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temp); }
    }
}
