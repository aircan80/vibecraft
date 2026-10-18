package dev.wowforever.app;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.importer.clientdata.MiniJson;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class PortableSelfTestTest {
  @TempDir Path temporary;
  String oldTmp,oldVersion;
  @BeforeEach void saveProperties()throws java.io.IOException {
    temporary=temporary.toRealPath();
    oldTmp=System.getProperty("org.sqlite.tmpdir");oldVersion=System.getProperty("wowforever.release.version");
  }
  @AfterEach void restoreProperties(){restore("org.sqlite.tmpdir",oldTmp);restore("wowforever.release.version",oldVersion);}
  private static void restore(String key,String value){if(value==null)System.clearProperty(key);else System.setProperty(key,value);}
  private Path report(String suffix)throws Exception{return Files.createDirectory(temporary.resolve("vibecraft-portable-self-test-"+suffix)).resolve("report.json");}
  private Map<?,?> read(Path report)throws Exception{return (Map<?,?>)MiniJson.parse(Files.readString(report));}

  @Test void successReportIsBoundedAndConfiguresPrivateNativeStorageBeforeProbe()throws Exception {
    Path report=report("success");System.setProperty("wowforever.release.version","0.4.1-preview.1");
    int code=PortableSelfTest.run(report,nativeDirectory->{
      assertThat(nativeDirectory).isDirectory();
      assertThat(nativeDirectory.getParent()).isEqualTo(report.getParent().toRealPath());
      assertThat(System.getProperty("org.sqlite.tmpdir")).isEqualTo(nativeDirectory.toString());return "3.49.1";
    });
    assertThat(code).isZero();assertThat(Files.size(report)).isLessThanOrEqualTo(8192);
    var result=read(report);assertThat(result.get("success")).isEqualTo(true);
    assertThat(result.get("marker")).isEqualTo(PortableSelfTest.SUCCESS_MARKER);
    assertThat(result.get("productVersion")).isEqualTo("0.4.1-preview.1");
    assertThat(result.get("sqliteNativeVersion")).isEqualTo("3.49.1");
  }
  @Test void failedPrerequisiteWritesExplicitFailureReportAndNonzeroStatus()throws Exception {
    Path report=report("failure");
    assertThat(PortableSelfTest.run(report,nativeDirectory->{throw new UnsatisfiedLinkError("synthetic native failure");})).isEqualTo(1);
    var result=read(report);assertThat(result.get("success")).isEqualTo(false);
    assertThat(result.get("marker")).isEqualTo(PortableSelfTest.FAILURE_MARKER);
    assertThat(result.get("error").toString()).contains("UnsatisfiedLinkError");
  }
  @Test void existingOrNonDedicatedNonemptyMissingAndRelativeStorageIsRefusedWithoutProbing()throws Exception {
    AtomicInteger probes=new AtomicInteger();PortableSelfTest.Probe probe=directory->{probes.incrementAndGet();return "3.49.1";};
    Path existing=report("existing");Files.writeString(existing,"preserve existing report");
    assertThat(PortableSelfTest.run(existing,probe)).isEqualTo(2);
    assertThat(Files.readString(existing)).isEqualTo("preserve existing report");
    Path nonempty=report("nonempty");Files.writeString(nonempty.getParent().resolve("unrelated-file"),"preserve");
    assertThat(PortableSelfTest.run(nonempty,probe)).isEqualTo(2);
    assertThat(PortableSelfTest.run(temporary.resolve("report.json"),probe)).isEqualTo(2);
    assertThat(PortableSelfTest.run(temporary.resolve("vibecraft-portable-self-test-missing/report.json"),probe)).isEqualTo(2);
    assertThat(PortableSelfTest.run(Path.of("relative-report.json"),probe)).isEqualTo(2);
    assertThat(probes).hasValue(0);
  }
  @Test void gameAncestorCannotBecomeExtractionStorage()throws Exception {
    Path game=Files.createDirectory(temporary.resolve("game"));Files.writeString(game.resolve(".build.info"),"read-only fixture");
    Path report=Files.createDirectory(game.resolve("vibecraft-portable-self-test-forbidden")).resolve("report.json");
    assertThat(PortableSelfTest.run(report,directory->{throw new AssertionError("must not probe");})).isEqualTo(2);
    try(var children=Files.list(report.getParent())){assertThat(children.toList()).isEmpty();}
  }
  @Test void arbitrarySymlinkAliasesCannotBecomeExtractionStorage()throws Exception {
    Path target=report("symlink-target").getParent();Path alias=temporary.resolve("vibecraft-portable-self-test-alias");
    try{Files.createSymbolicLink(alias,target);}
    catch(java.io.IOException|UnsupportedOperationException unavailable){Assumptions.abort("Host cannot create test symbolic links: "+unavailable.getMessage());}
    assertThat(PortableSelfTest.run(alias.resolve("report.json"),directory->{throw new AssertionError("must not probe");})).isEqualTo(2);
    try(var children=Files.list(target)){assertThat(children.toList()).isEmpty();}
  }
  @Test void longEscapedVersionAndFailureTextRemainValidBoundedJson()throws Exception {
    System.setProperty("wowforever.release.version","quoted\"\\\n"+"x".repeat(1000));Path report=report("bounded");
    assertThat(PortableSelfTest.run(report,directory->{throw new java.io.IOException("\"\\\n".repeat(10000));})).isEqualTo(1);
    var result=read(report);assertThat(result.get("productVersion").toString()).hasSize(128);
    assertThat(result.get("error").toString().length()).isLessThanOrEqualTo(400);assertThat(Files.size(report)).isLessThanOrEqualTo(8192);
  }
  @Test void realMainRunsOfflineHeadlessWithFreshHomeAndNoGameplayDatabase()throws Exception {
    Path report=report("real-process"),home=Files.createDirectory(temporary.resolve("fresh-home"));
    Path stdout=temporary.resolve("stdout.log"),stderr=temporary.resolve("stderr.log");
    String executable=System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")?"java.exe":"java";
    Path java=Path.of(System.getProperty("java.home"),"bin",executable);
    var process=new ProcessBuilder(java.toString(),"-Djava.awt.headless=true","-Duser.home="+home,
        "-Dwowforever.release.version=portable-process-test","-cp",System.getProperty("java.class.path"),Main.class.getName(),
        "--portable-self-test",report.toString());
    process.environment().put("PATH",temporary.resolve("no-external-tools").toString());
    process.environment().put("XDG_DATA_HOME",home.resolve("data").toString());
    process.environment().put("XDG_CACHE_HOME",home.resolve("cache").toString());
    process.environment().put("XDG_CONFIG_HOME",home.resolve("config").toString());
    process.environment().put("LOCALAPPDATA",home.resolve("local-app-data").toString());
    process.redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
    var running=process.start();boolean completed=running.waitFor(60,TimeUnit.SECONDS);
    if(!completed)running.destroyForcibly();
    assertThat(completed).as("portable native probe must terminate without GUI").isTrue();
    assertThat(running.exitValue()).as(Files.readString(stderr)).isZero();
    var result=read(report);assertThat(result.get("success")).isEqualTo(true);
    assertThat(result.get("marker")).isEqualTo(PortableSelfTest.SUCCESS_MARKER);
    assertThat(result.get("productVersion")).isEqualTo("portable-process-test");
    assertThat(result.get("javaFeature")).isEqualTo((long)Runtime.version().feature());
    assertThat(result.get("sqliteNativeVersion").toString()).matches("[0-9]+(?:\\.[0-9]+){1,3}");
    try(var files=Files.walk(home)){assertThat(files.toList()).containsExactly(home);}
    assertThat(report.getParent().resolve("sqlite-native")).isDirectory(); // SQLite may remove extracted JNI files at JVM exit.
  }
}
