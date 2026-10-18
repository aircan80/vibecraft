package dev.wowforever.app.assets;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

/** Bounded reviewed metadata, loaded from an explicit portable data root or classpath.
 * An explicit directory is authoritative: missing/changed files do not fall back.
 * The expected SHA must be reviewed and compiled into the caller, not read beside the data.
 */
public final class ReviewedDataFiles {
    public static final String DIRECTORY_PROPERTY = "wowforever.data.dir";
    private ReviewedDataFiles() {}
    public static InputStream open(String relativePath, String expectedSha256, int maxBytes) throws IOException {
        if (relativePath == null || !relativePath.matches("[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*")
                || relativePath.contains("..") || maxBytes < 1 || maxBytes > 512 * 1024 * 1024
                || expectedSha256 == null || !expectedSha256.matches("[0-9a-f]{64}"))
            throw new IOException("Invalid reviewed data resource specification");
        String configured = System.getProperty(DIRECTORY_PROPERTY);
        InputStream input;
        if (configured != null) {
            if (configured.isBlank()) throw new IOException("Explicit reviewed data directory is empty");
            Path directory = Path.of(configured).toRealPath();
            Path file = directory.resolve(relativePath).toRealPath();
            if (!Files.isDirectory(directory) || !file.startsWith(directory) || !Files.isRegularFile(file))
                throw new IOException("Reviewed data resource is outside its directory");
            if (Files.size(file) > maxBytes) throw new IOException("Reviewed data resource exceeds bound");
            input = Files.newInputStream(file);
        } else {
            input = ReviewedDataFiles.class.getResourceAsStream("/data/" + relativePath);
            if (input == null) throw new FileNotFoundException("Reviewed data resource unavailable: " + relativePath);
        }
        byte[] bytes;
        try (input) {
            bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) throw new IOException("Reviewed data resource exceeds bound");
        }
        try {
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!expectedSha256.equals(sha)) throw new IOException("Reviewed data resource SHA-256 mismatch: " + relativePath);
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        return new ByteArrayInputStream(bytes);
    }
}
