package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.app.db.SourceArchiveRepository.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceArchiveComparisonTest {
  private Snapshot snapshot(String source, String id, SourceFile... files) {
    return new Snapshot(id, source, "v", "", "", "", "2026-10-17", List.of(files));
  }
  @Test void comparesFileContentAndUrlsIndependentlyAndOmitsStableFiles() {
    var baseline = snapshot("questie", "a", new SourceFile("stable", "url", "same"),
        new SourceFile("removed", "url", "old"), new SourceFile("content", "url", "old"),
        new SourceFile("url", "old-url", "same"), new SourceFile("both", "old-url", "old"));
    var current = snapshot("questie", "b", new SourceFile("stable", "url", "same"),
        new SourceFile("added", null, "new"), new SourceFile("content", "url", "new"),
        new SourceFile("url", "new-url", "same"), new SourceFile("both", "new-url", "new"));
    var changes = SourceArchiveComparison.compare(baseline, current);
    assertThat(changes).extracting(SourceArchiveComparison.Change::path).containsExactly("added", "both", "content", "removed", "url");
    assertThat(changes).extracting(SourceArchiveComparison.Change::description).containsExactly(
        "Added to snapshot", "Content and URL changed", "Content changed", "Removed from snapshot", "URL changed");
    assertThat(changes.get(4).contentChanged()).isFalse();
    assertThat(changes.get(4).urlChanged()).isTrue();
    assertThat(SourceArchiveComparison.compare(baseline, baseline)).isEmpty();
  }
  @Test void unrelatedSourcesCannotBeCompared() {
    assertThatThrownBy(() -> SourceArchiveComparison.compare(snapshot("questie", "a"), snapshot("atlas", "b")))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("same source");
  }
}
