package dev.wowforever.importer.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dev.wowforever.importer.Fixtures;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceFetcherTest {
  private HttpServer server;
  private URI baseUri;
  private final HttpClient client = HttpClient.newHttpClient();
  private final AtomicInteger requests = new AtomicInteger();

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", exchange -> {
      requests.incrementAndGet();
      String path = exchange.getRequestURI().getPath();
      boolean missing = path.endsWith("/missing.lua");
      byte[] body = (missing ? "404: Not Found" : "ok:" + path).getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(missing ? 404 : 200, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    });
    server.start();
    baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void usesLocalDirWhenGiven(@TempDir Path tmp) throws IOException {
    SourceSpec s = SourceSpec.load().get(0);
    assertThat(new SourceFetcher(tmp, HttpClient.newHttpClient()).resolve(s, Optional.of(Fixtures.questieDbRoot())))
        .isEqualTo(Fixtures.questieDbRoot());
  }

  @Test
  void downloadsMissingFilesIntoCache(@TempDir Path tmp) throws IOException {
    SourceSpec s = new SourceSpec("t", "o", "r", "abc", List.of("a/b.lua"));
    Path root = new SourceFetcher(tmp, client, baseUri).resolve(s, Optional.empty());
    assertThat(root.resolve("a/b.lua")).content().isEqualTo("ok:/o/r/abc/a/b.lua");
    assertThat(root).isEqualTo(tmp.resolve("t/abc"));
  }

  @Test
  void cachedFilesAreNotDownloadedAgain(@TempDir Path tmp) throws IOException {
    SourceSpec s = new SourceSpec("t", "o", "r", "abc", List.of("a/b.lua", "c.lua"));
    Path cached = tmp.resolve("t/abc/a/b.lua");
    Files.createDirectories(cached.getParent());
    Files.writeString(cached, "cached");

    Path root = new SourceFetcher(tmp, client, baseUri).resolve(s, Optional.empty());

    assertThat(root.resolve("a/b.lua")).content().isEqualTo("cached");
    assertThat(root.resolve("c.lua")).content().isEqualTo("ok:/o/r/abc/c.lua");
    assertThat(requests).hasValue(1);
  }

  @Test
  void failedDownloadNamesTheUrlAndLeavesNoFile(@TempDir Path tmp) {
    SourceSpec s = new SourceSpec("t", "o", "r", "abc", List.of("missing.lua"));
    assertThatThrownBy(() -> new SourceFetcher(tmp, client, baseUri).resolve(s, Optional.empty()))
        .isInstanceOf(IOException.class)
        .hasMessageContaining(baseUri + "o/r/abc/missing.lua")
        .hasMessageContaining("404");
    assertThat(tmp.resolve("t/abc/missing.lua")).doesNotExist();
  }

  @Test
  void specLoadsBothSources() {
    assertThat(SourceSpec.load()).extracting(SourceSpec::key).containsExactly("questiedb", "atlasloot");
  }

  @Test
  void specPinsCommitsAndPaths() {
    SourceSpec questie = SourceSpec.load().get(0);
    SourceSpec atlas = SourceSpec.load().get(1);
    assertThat(questie.commit()).isEqualTo("e9a67d1a2753a0e64c4c96bf7864788fac7b765f");
    assertThat(questie.paths()).hasSize(42).doesNotHaveDuplicates()
        .contains("data/Forever/foreverItemDB.lua", "src/meta/questMeta.lua", "support/Forever/Zones/dungeons.lua")
        .noneMatch(p -> p.startsWith("docs/"));
    assertThat(atlas.paths()).containsExactly(
        "AtlasLootClassic_DungeonsAndRaids/data.lua",
        "AtlasLootClassic_DungeonsAndRaids/droprate.lua",
        "AtlasLootClassic_DungeonsAndRaids/droprate_override.lua",
        "LICENSE");
  }

  @Test
  void rawUrlPointsAtGithubRawContent() {
    SourceSpec s = new SourceSpec("t", "Questie", "QuestieDB", "abc", List.of());
    assertThat(s.rawUrl("src/meta/itemMeta.lua"))
        .isEqualTo(URI.create("https://raw.githubusercontent.com/Questie/QuestieDB/abc/src/meta/itemMeta.lua"));
  }

  @Test
  void defaultCacheDirIsUnderUserHome() {
    assertThat(SourceFetcher.defaultCacheDir())
        .isEqualTo(Path.of(System.getProperty("user.home"), ".cache", "wowforever-importer"));
  }
}
