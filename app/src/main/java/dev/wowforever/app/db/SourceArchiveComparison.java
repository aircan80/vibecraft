package dev.wowforever.app.db;

import dev.wowforever.app.db.SourceArchiveRepository.Snapshot;
import dev.wowforever.app.db.SourceArchiveRepository.SourceFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/** Compares archived file metadata; an absent file does not establish an upstream deletion. */
public final class SourceArchiveComparison {
  private SourceArchiveComparison() {}
  public record Change(String path, boolean added, boolean removed, boolean contentChanged, boolean urlChanged,
      SourceFile before, SourceFile after) {
    public String description() {
      if (added) return "Added to snapshot";
      if (removed) return "Removed from snapshot";
      if (contentChanged && urlChanged) return "Content and URL changed";
      return contentChanged ? "Content changed" : "URL changed";
    }
  }

  public static List<Change> compare(Snapshot baseline, Snapshot current) {
    Objects.requireNonNull(baseline, "baseline");
    Objects.requireNonNull(current, "current");
    if (!Objects.equals(baseline.source(), current.source()))
      throw new IllegalArgumentException("Choose snapshots from the same source");
    var before = files(baseline);
    var after = files(current);
    var paths = new TreeSet<>(before.keySet());
    paths.addAll(after.keySet());
    var changes = new ArrayList<Change>();
    for (String path : paths) {
      var a = before.get(path);
      var b = after.get(path);
      boolean content = a != null && b != null && !Objects.equals(a.sha256(), b.sha256());
      boolean url = a != null && b != null && !Objects.equals(a.url(), b.url());
      if (a == null || b == null || content || url)
        changes.add(new Change(path, a == null, b == null, content, url, a, b));
    }
    return List.copyOf(changes);
  }

  private static TreeMap<String, SourceFile> files(Snapshot snapshot) {
    var files = new TreeMap<String, SourceFile>();
    for (var file : snapshot.files()) {
      if (files.put(file.path(), file) != null) throw new IllegalArgumentException("Duplicate archived path: " + file.path());
    }
    return files;
  }
}
