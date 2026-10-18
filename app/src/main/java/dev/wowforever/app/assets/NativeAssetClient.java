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

/** Exact Forever 70450 CASC reader. Blocking API: call only on a worker thread.
 * Config is pinned; encoding and root are authenticated by their decoded content hashes.
 * No external executable or local metadata trust is required. Unsupported formats fail closed.
 */
public final class NativeAssetClient implements AutoCloseable {
    public static final String PRODUCT = "wow_classic_beta", BUILD = "1.60.1.70450";
    public static final String CONFIG = "84bea4233331e390fda8724c6f91ffa5";
    private static final String BASE = "https://us.cdn.blizzard.com/tpr/wow/";
    private static final int LIMIT = 512 * 1024 * 1024;
    private static final Object RATE = new Object();
    private static long nextRequest;
    private final Path cache;
    private final HttpClient http;
    private byte[] encoding, root;
    private String rootKey;
    private final java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();
    public record Asset(Path path, long fileDataId, String contentMd5, String sha256, long bytes,
                        String product, String build, String rootContentMd5) {}
    private record Encoding(long size, List<String> keys) {}
    public NativeAssetClient(Path cache, String product, String build) {
        if (!PRODUCT.equals(product) || !BUILD.equals(build))
            throw new IllegalArgumentException("No authenticated native asset profile for " + product + " " + build);
        this.cache = Objects.requireNonNull(cache);
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    /** Cancellation is checked during cache reads, transport and metadata parsing. */
    public Asset fetch(long fileDataId, BooleanSupplier cancelled) throws IOException, InterruptedException {
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Native assets must load off the EDT");
        if (fileDataId <= 0 || fileDataId > 0xffffffffL) throw new IllegalArgumentException("Invalid native file ID");
        Objects.requireNonNull(cancelled);
        check(cancelled);
        while (!lock.tryLock(50, java.util.concurrent.TimeUnit.MILLISECONDS)) check(cancelled);
        try { return fetchLocked(fileDataId, cancelled); }
        finally { lock.unlock(); }
    }
    private Asset fetchLocked(long fileDataId, BooleanSupplier cancelled) throws IOException, InterruptedException {
        check(cancelled);
        prepare(cancelled);
        String key = rootEntry(root, fileDataId, cancelled);
        if (key == null) throw new FileNotFoundException("No neutral/enUS native asset " + fileDataId + " in " + BUILD);
        Encoding entry = encodingEntry(encoding, key, cancelled);
        if (entry == null) throw new IOException("Root asset absent from authenticated encoding");
        byte[] bytes;
        try { bytes = content(key, entry.keys(), cancelled); }
        catch (IOException officialUnavailable) {
            // Transport only: the exact root's MD5 and encoding size remain mandatory.
            bytes = download(URI.create("https://wago.tools/api/casc/" + fileDataId
                    + "?download&version=" + BUILD + "&branch=" + PRODUCT), LIMIT, cancelled);
            verify(bytes, key);
            atomic(cache.resolve("content/" + key + ".bin"), bytes);
        }
        if (bytes.length != entry.size()) throw new IOException("Asset size differs from authenticated encoding");
        check(cancelled);
        String sha = digest("SHA-256", bytes);
        String proof = "{\n  \"product\": \"" + PRODUCT + "\",\n  \"build\": \"" + BUILD
                + "\",\n  \"file_data_id\": " + fileDataId + ",\n  \"build_config_md5\": \"" + CONFIG
                + "\",\n  \"root_content_md5\": \"" + rootKey + "\",\n  \"content_md5\": \"" + key
                + "\",\n  \"sha256\": \"" + sha + "\",\n  \"bytes\": " + bytes.length
                + ",\n  \"exact_root_membership_verified\": true,\n  \"implementation\": \"java-native-casc-v1\"\n}\n";
        atomic(cache.resolve("proof/" + PRODUCT + "/" + BUILD + "/" + fileDataId + ".json"), proof.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new Asset(cache.resolve("content/" + key + ".bin"), fileDataId, key, sha, bytes.length, PRODUCT, BUILD, rootKey);
    }
    private void prepare(BooleanSupplier cancelled) throws IOException, InterruptedException {
        if (root != null) return;
        Path configPath = cache.resolve("config/" + CONFIG);
        byte[] config = Files.exists(configPath) ? read(configPath, 1024 * 1024, cancelled)
                : download(URI.create(BASE + "config/84/be/" + CONFIG), 1024 * 1024, cancelled);
        verify(config, CONFIG);
        Map<String,String> values = new HashMap<>();
        for (String line : new String(config, java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
            int at = line.indexOf(" = ");
            if (at > 0) values.put(line.substring(0, at), line.substring(at + 3));
        }
        if (!values.getOrDefault("build-name", "").matches("WOW-70450patch1\\.60\\.1(?:_.*)?"))
            throw new IOException("Pinned configuration build-name mismatch");
        String[] pair = values.getOrDefault("encoding", "").split(" ");
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
        validKey(key);
        Path path = cache.resolve("content/" + key + ".bin");
        if (Files.exists(path)) { byte[] bytes = read(path, LIMIT, cancelled); verify(bytes, key); return bytes; }
        IOException failure = new IOException("No verified native encoding for " + key);
        for (String encodedKey : keys) {
            validKey(encodedKey);
            try {
                Path encodedPath = cache.resolve("encoded/" + encodedKey + ".blte");
                byte[] encoded = Files.exists(encodedPath) ? read(encodedPath, LIMIT, cancelled)
                        : download(URI.create(BASE + "data/" + encodedKey.substring(0, 2) + "/" + encodedKey.substring(2, 4) + "/" + encodedKey), LIMIT, cancelled);
                byte[] decoded = decodeBlte(encoded, LIMIT);
                verify(decoded, key); check(cancelled);
                atomic(encodedPath, encoded); atomic(path, decoded); return decoded;
            } catch (IOException e) { failure.addSuppressed(e); }
        }
        throw failure;
    }
    private byte[] download(URI uri, int limit, BooleanSupplier cancelled) throws IOException, InterruptedException {
        synchronized (RATE) {
            while (System.nanoTime() < nextRequest) { check(cancelled); Thread.sleep(50); }
            nextRequest = System.nanoTime() + 500_000_000L;
        }
        check(cancelled);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(90)).GET().build();
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
            if (response.statusCode() != 200) throw new IOException("Native transport HTTP " + response.statusCode());
            check(cancelled); return response.body();
        } finally { if (!future.isDone()) future.cancel(true); }
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
    static String rootEntry(byte[] raw, long wanted, BooleanSupplier cancelled) throws IOException {
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
                if (selected == null && selectedIndex >= 0 && (locale == 0 || (locale & 2) != 0) && (flags & 0x80) == 0)
                    selected = HexFormat.of().formatHex(raw, keysAt + selectedIndex * 16, keysAt + selectedIndex * 16 + 16);
                long skip = (long)count * 16 + (total != named && (flags & 0x10000000) != 0 ? 0 : (long)count * 8);
                if (skip > b.remaining()) throw new IOException("Truncated root block");
                b.position((int)(keysAt + skip));
            }
            return selected;
        } catch (IndexOutOfBoundsException | BufferUnderflowException e) { throw new IOException("Truncated native root", e); }
    }
    @Override public void close() { http.shutdownNow(); }
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
    private static void atomic(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent()); Path temp = Files.createTempFile(path.getParent(), ".native-", ".tmp");
        try { Files.write(temp, bytes); Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temp); }
    }
}
