package dev.wowforever.importer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.entity.EntityTable;
import dev.wowforever.importer.entity.RawEntityLoader;
import dev.wowforever.importer.schema.EntitySchema;
import dev.wowforever.importer.schema.MetaLoader;
import dev.wowforever.importer.source.SourceFetcher;
import dev.wowforever.importer.source.SourceSpec;
import dev.wowforever.lua.ProblemCollector;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** Runs the loaders over the cached upstream files; skipped when the importer cache is absent. */
@EnabledIf("cacheExists")
class RealSourcesTest {
  private static final Path QUESTIE = SourceFetcher.defaultCacheDir().resolve("questiedb/49e7dbca1f18eb3b8e57ee5d9562ac3e5c9adcc4");

  static boolean cacheExists() {
    return SourceSpec.load().stream().allMatch(s -> s.paths().stream()
        .allMatch(p -> Files.isRegularFile(SourceFetcher.defaultCacheDir().resolve(s.key()).resolve(s.commit()).resolve(p))));
  }

  @Test
  void cacheLayoutMatchesSoNothingIsDownloaded() throws IOException {
    // an unroutable base URI: any download attempt would fail the test
    SourceFetcher offline = new SourceFetcher(SourceFetcher.defaultCacheDir(), HttpClient.newHttpClient(), URI.create("http://127.0.0.1:9/"));
    assertThat(offline.resolve(SourceSpec.load().get(0), Optional.empty())).isEqualTo(QUESTIE);
    assertThat(offline.resolve(SourceSpec.load().get(1), Optional.empty())).endsWith(Path.of("atlasloot", "8e99341e4e779328460bf7684c0d5b22ce50ddf1"));
  }

  @Test
  void loadsEveryRealEntityTableWithoutProblems() {
    ProblemCollector problems = new ProblemCollector();
    for (EntityKind kind : EntityKind.values()) {
      String lower = kind.metaEntity().toLowerCase(Locale.ROOT);
      EntitySchema schema = MetaLoader.load(QUESTIE.resolve("src/meta/" + lower + "Meta.lua"));
      assertThat(schema.entity()).isEqualTo(kind.metaEntity());
      EntityTable t = RawEntityLoader.load(QUESTIE.resolve("data/Forever/forever" + kind.metaEntity() + "DB.lua"), kind, schema, problems);
      assertThat(t.size()).as(kind.name()).isGreaterThan(1000);
    }
    assertThat(problems.problems()).isEmpty();
  }
}
