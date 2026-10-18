import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Build-time gate; never shipped as an end-user dependency. */
class ArchiveAudit {
  public static void main(String[] args) throws Exception {
    boolean privateQa = args.length == 2 && args[1].equals("--private-qa");
    int rejected = 0;
    try (var zip = new ZipFile(Path.of(args[0]).toFile())) {
      var entries = zip.entries();
      while (entries.hasMoreElements()) {
        var entry = entries.nextElement();
        String name = entry.getName().toLowerCase(Locale.ROOT);
        // Native SQLite JNI libraries are permitted. External worker runtimes are not.
        boolean worker = name.endsWith(".py") || name.endsWith(".pyc") || name.endsWith(".blend")
            || name.endsWith(".m2") || name.endsWith(".blp") || name.contains("node_modules/")
            || name.matches(".*(?:^|/)(?:python[0-9.]*|node|blender)(?:\\.exe)?$");
        boolean asset = name.startsWith("data/") || name.startsWith("portraits/")
            || name.startsWith("audio/") || name.endsWith(".sqlite") || name.endsWith(".sqlite3")
            || name.endsWith(".mp3") || name.endsWith(".ogg") || name.endsWith(".wav")
            || name.endsWith(".webm") || name.endsWith(".mp4")
            || (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".webp"));
        if (worker || (!privateQa && asset)) {
          System.err.println("Packaging rejected: " + entry.getName());
          rejected++;
        }
      }
    }
    if (rejected != 0) throw new IllegalStateException("Rejected " + rejected
        + " entries. Build with -Psource-only. Private local QA may explicitly allow assets, never worker runtimes.");
    System.out.println(privateQa ? "Archive audit: PRIVATE QA assets allowed; worker runtimes excluded."
        : "Archive audit: source-only distribution, no bulk assets or worker runtimes.");
  }
}
