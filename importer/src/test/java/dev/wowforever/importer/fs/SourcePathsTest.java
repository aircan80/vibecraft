package dev.wowforever.importer.fs;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourcePathsTest {
  @TempDir Path temporary;

  @Test void verifiedTrustedAliasAllowsFutureOutputAndNormalizesItsIdentity() throws Exception {
    Path root = temporary.toRealPath();
    Path actual = Files.createDirectories(root.resolve("private/var"));
    Path alias = link(root.resolve("var"), actual);
    Map<Path,Path> trusted = Map.of(alias,actual);
    Path selected = alias.resolve("new/output.sqlite");
    SourcePaths.rejectSymlinks(selected,trusted);
    assertThat(SourcePaths.normalize(selected,trusted)).isEqualTo(actual.resolve("new/output.sqlite"));
    assertThat(SourcePaths.normalize(selected,trusted)).isEqualTo(SourcePaths.normalize(actual.resolve("new/output.sqlite"),trusted));
    assertThat(Files.exists(actual.resolve("new"))).isFalse();
  }
  @Test void productionNeverTrustsAnArbitrarySourceOrOutputAlias() throws Exception {
    Path root=temporary.toRealPath();
    Path target=Files.createDirectory(root.resolve("actual"));
    Path alias=link(root.resolve("untrusted"),target);
    assertThatIOException().isThrownBy(() -> SourcePaths.rejectSymlinks(alias.resolve("output.sqlite")));
    assertThatIOException().isThrownBy(() -> SourcePaths.normalize(alias.resolve("output.sqlite")));
  }
  @Test void trustedParentDoesNotAllowNestedDirectoryFileOrLockSymlinks() throws Exception {
    Path root=temporary.toRealPath();
    Path actual=Files.createDirectory(root.resolve("private"));
    Path alias=link(root.resolve("trusted"),actual);
    Path outside=Files.createDirectory(root.resolve("outside"));
    Path file=Files.writeString(outside.resolve("source.lua"),"synthetic");
    Map<Path,Path> trusted=Map.of(alias,actual);
    link(actual.resolve("nested"),outside);
    link(actual.resolve("source.lua"),file);
    link(actual.resolve("app.sqlite.lock"),file);
    for(String input: new String[]{"nested/app.sqlite","source.lua","app.sqlite.lock"}) {
      assertThatIOException().isThrownBy(() -> SourcePaths.rejectSymlinks(alias.resolve(input),trusted));
    }
  }
  @Test void mismatchedTargetAndChainedTargetAreNotTrusted() throws Exception {
    Path root=temporary.toRealPath();
    Path expected=Files.createDirectory(root.resolve("expected"));
    Path other=Files.createDirectory(root.resolve("other"));
    Path alias=link(root.resolve("alias"),other);
    assertThatIOException().isThrownBy(() -> SourcePaths.normalize(alias.resolve("output"),Map.of(alias,expected)));
    Path chained=link(root.resolve("chained"),expected);
    Path outer=link(root.resolve("outer"),chained);
    assertThatIOException().isThrownBy(() -> SourcePaths.normalize(outer.resolve("output"),Map.of(outer,chained)));
  }
  @Test void relativeTrustedTargetIsVerifiedAndDotDotDoesNotHideUntrustedLinks() throws Exception {
    Path root=temporary.toRealPath();
    Path actual=Files.createDirectory(root.resolve("actual"));
    Path alias=link(root.resolve("alias"),Path.of("actual"));
    assertThat(SourcePaths.normalize(alias.resolve("output"),Map.of(alias,actual))).isEqualTo(actual.resolve("output"));
    assertThatIOException().isThrownBy(() -> SourcePaths.normalize(alias.resolve("../escape"),Map.of()));
  }
  private static Path link(Path link, Path target) throws Exception {
    try { return Files.createSymbolicLink(link,target); }
    catch (java.io.IOException | UnsupportedOperationException failure) {
      Assumptions.abort("Host cannot create test symbolic links: " + failure.getMessage());
      throw failure;
    }
  }
}
