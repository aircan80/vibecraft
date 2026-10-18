import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Stages only explicitly reviewed, hash-pinned release data. Build-time tool, not shipped. */
class ReleaseData {
  record Entry(String sha256, long bytes, String relative, Path source) {}

  public static void main(String[] args) throws Exception {
    if (args.length != 3) throw new IllegalArgumentException("Usage: ReleaseData.java DATA_ROOT MANIFEST_TSV STAGING_DIR");
    Path root = Path.of(args[0]).toRealPath();
    Path manifest = Path.of(args[1]);
    Path stage = Path.of(args[2]).toAbsolutePath().normalize();
    List<Entry> verified = new ArrayList<>();
    Set<String> names = new HashSet<>();
    for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
      if (line.isBlank() || line.startsWith("#")) continue;
      String[] fields = line.split("\t", -1);
      if (fields.length != 3 || !fields[0].matches("[0-9a-f]{64}"))
        throw new IllegalArgumentException("Manifest must contain SHA256<TAB>bytes<TAB>data/path rows");
      String relative = fields[2];
      if (!relative.startsWith("data/") || relative.contains("\\") || relative.contains(":")
          || relative.contains("\n") || relative.contains("\r") || relative.endsWith("/"))
        throw new IllegalArgumentException("Invalid data path: " + relative);
      for (String part : relative.split("/", -1))
        if (part.isEmpty() || part.equals(".") || part.equals(".."))
          throw new IllegalArgumentException("Invalid path component: " + relative);
      // Explicit manifests permit static runtime data, never external worker runtimes/models.
      if (!relative.toLowerCase(Locale.ROOT).matches(".*\\.(sqlite|png|jpg|jpeg|webp|mp3|ogg|wav|json|txt)$"))
        throw new IllegalArgumentException("Unsupported release data type: " + relative);
      if (!names.add(relative)) throw new IllegalArgumentException("Duplicate manifest path: " + relative);
      Path source = root.resolve(relative).toRealPath();
      if (!source.startsWith(root) || !Files.isRegularFile(source))
        throw new IllegalArgumentException("Data file escapes root or is not a regular file: " + relative);
      long bytes = Long.parseLong(fields[1]);
      if (bytes < 0 || bytes != Files.size(source) || !fields[0].equals(hash(source)))
        throw new IllegalArgumentException("Data hash/size mismatch: " + relative);
      verified.add(new Entry(fields[0], bytes, relative, source));
    }
    if (!names.contains("data/wowforever.sqlite"))
      throw new IllegalArgumentException("Complete release needs manifest entry data/wowforever.sqlite");
    StringBuilder inventory = new StringBuilder("# Hash-verified release data; review records content selection, not legal clearance.\n")
        .append("# manifest-sha256: ").append(hash(manifest)).append('\n');
    for (Entry entry : verified) {
      Path target = stage.resolve(entry.relative()).normalize();
      Files.createDirectories(target.getParent());
      Files.copy(entry.source(), target, StandardCopyOption.REPLACE_EXISTING);
      if (!entry.sha256().equals(hash(target))) throw new IllegalStateException("Staged data changed: " + entry.relative());
      inventory.append(entry.sha256()).append('\t').append(entry.bytes()).append('\t').append(entry.relative()).append('\n');
    }
    Files.writeString(stage.resolve("data/release-data-inventory.tsv"), inventory, StandardCharsets.UTF_8);
    System.out.println("Reviewed release data: " + verified.size() + " files, "
        + verified.stream().mapToLong(Entry::bytes).sum() + " bytes. Inventory staged with application.");
  }

  static String hash(Path path) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream in = Files.newInputStream(path)) {
      byte[] buffer = new byte[1024 * 1024];
      for (int count; (count = in.read(buffer)) != -1;) digest.update(buffer, 0, count);
    }
    return HexFormat.of().formatHex(digest.digest());
  }
}
