package dev.wowforever.importer.acquisition;

import static org.assertj.core.api.Assertions.*;
import static dev.wowforever.importer.setup.SetupPlan.Acquisition.*;
import com.sun.net.httpserver.HttpServer;
import dev.wowforever.importer.setup.*;
import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class WagoTableMaterializerTest {
  @TempDir Path temporary;
  private HttpServer server;
  private final Map<String,Route> routes=new ConcurrentHashMap<>();
  private final List<String> requests=new CopyOnWriteArrayList<>();
  private URI base;
  private static final SetupPlan.NativeIdentity BUILD=new SetupPlan.NativeIdentity("wow_classic_beta","1.60.1.70450","enUS");
  private record Route(int status,byte[] bytes,String type,boolean chunked) {}
  @BeforeEach void start() throws Exception {
    server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",exchange->{
      String uri=exchange.getRequestURI().toString();requests.add(uri);
      Route route=routes.getOrDefault(uri,new Route(404,"missing".getBytes(StandardCharsets.UTF_8),"text/plain",false));
      exchange.getResponseHeaders().set("Content-Type",route.type());
      if(route.status()==302)exchange.getResponseHeaders().set("Location","/unselected-provider");
      exchange.sendResponseHeaders(route.status(),route.chunked()?0:route.bytes().length);
      try(var out=exchange.getResponseBody()){out.write(route.bytes());}
    });
    server.start();base=URI.create("http://127.0.0.1:"+server.getAddress().getPort());
  }
  @AfterEach void stop(){server.stop(0);}
  private SetupPlan plan(SetupPlan.Acquisition method){var sources=Set.of(SetupPlan.Source.QUESTIE,SetupPlan.Source.NATIVE_DATA);return new SetupPlan(sources,Set.of(method),Optional.of(BUILD),List.of(),SetupPlan.currentPins(sources));}
  private SetupPlan.JobContext job() throws Exception{return plan(WAGO_EXPORT).prepareJob(temporary.resolve("app"),Optional.empty(),"job",List.of());}
  private WagoTableMaterializer reader(){return reader(4096,8192,Duration.ofSeconds(2));}
  private WagoTableMaterializer reader(long table,long total,Duration timeout){return new WagoTableMaterializer(base,HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),new WagoTableMaterializer.Limits(table,total,4,timeout,Duration.ZERO));}
  private byte[] csv(String value){return ("ID,Name\n1,"+value+"\n").getBytes(StandardCharsets.UTF_8);}
  private String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
  private WagoTableMaterializer.Manifest manifest(String table,byte[] bytes)throws Exception{return new WagoTableMaterializer.Manifest(BUILD,Map.of(table,hash(bytes)));}
  private void route(String table,byte[] bytes){routes.put("/db2/"+table+"/csv?build="+BUILD.build(),new Route(200,bytes,"text/csv",false));}
  private WagoTableMaterializer.Result get(WagoTableMaterializer reader,SetupPlan.JobContext job,WagoTableMaterializer.Manifest manifest)throws Exception{return reader.materialize(plan(WAGO_EXPORT),job,manifest,()->false,p->{});}
  private Path pointer(SetupPlan.JobContext job){return job.paths().cache().resolve("wago/current-input.properties");}

  @Test void explicitWagoConsentAndExactIdentityPrecedeNetworkOrCacheWrites() throws Exception {
    var job=job();var manifest=manifest("Item",csv("one"));
    assertThatIOException().isThrownBy(()->reader().materialize(plan(BLIZZARD_CDN),job,manifest,()->false,p->{}));
    var wrong=new WagoTableMaterializer.Manifest(new SetupPlan.NativeIdentity("wow_classic_beta","1.60.1.70000","enUS"),manifest.tableSha256());
    assertThatIOException().isThrownBy(()->get(reader(),job,wrong));
    assertThat(requests).isEmpty();assertThat(job.paths().cache().resolve("wago")).doesNotExist();
  }
  @Test void downloadsOnlyPinnedSelectionAndProducesConsumableNonCascProvenance() throws Exception {
    byte[] one=csv("one"),two="\"ID\",\"Name\"\n1,two\n".getBytes(StandardCharsets.UTF_8);route("Item",one);route("AreaTable",two);
    var manifest=new WagoTableMaterializer.Manifest(BUILD,Map.of("Item",hash(one),"AreaTable",hash(two)));
    var job=job();var result=get(reader(),job,manifest);
    assertThat(requests).containsExactly("/db2/AreaTable/csv?build=1.60.1.70450","/db2/Item/csv?build=1.60.1.70450");
    assertThat(result.downloaded()).isEqualTo(2);assertThat(result.reused()).isZero();
    var lock=dev.wowforever.importer.clientdata.BuildLock.read(result.directory().resolve("build.lock"));
    assertThat(lock.tableSha()).isEqualTo(manifest.tableSha256());assertThat(lock.product()).isEqualTo("wow_classic_beta");
    Properties proof=new Properties();try(var in=Files.newInputStream(result.directory().resolve("acquisition.properties"))){proof.load(in);}
    assertThat(proof.getProperty("cascRootVerified")).isEqualTo("false");assertThat(proof.getProperty("provider")).isEqualTo("WAGO_EXPORT");
    var prepared=plan(WAGO_EXPORT).prepareCapture(job.storage(),job.paths(),new SetupPlan.MaterializedInputs(
        dev.wowforever.importer.Fixtures.questieDbRoot(),Optional.empty(),Optional.empty(),Optional.of(result.client())));
    assertThat(prepared.options().clientData().dir()).contains(result.directory());
    assertThat(job.paths().archiveDatabase()).doesNotExist();assertThat(job.paths().candidateDatabase()).doesNotExist();
  }
  @Test void verifiedCompleteCacheIsRehashedAndReusedWithoutAnyNetwork() throws Exception {
    byte[] bytes=csv("one");route("Item",bytes);var job=job();var manifest=manifest("Item",bytes);
    var first=get(reader(),job,manifest);requests.clear();routes.clear();
    var second=get(reader(),job,manifest);
    assertThat(second.directory()).isEqualTo(first.directory());assertThat(second.downloaded()).isZero();assertThat(second.reused()).isEqualTo(1);
    assertThat(second.downloadedBytes()).isZero();assertThat(requests).isEmpty();
  }
  @Test void corruptedCompleteFileAndBlobAreNotTrustedAndRepairRequiresPinnedBytes() throws Exception {
    byte[] bytes=csv("one");route("Item",bytes);var job=job();var manifest=manifest("Item",bytes);
    var first=get(reader(),job,manifest);
    Files.writeString(first.directory().resolve("csv/Item.csv"),"corrupt");
    Files.writeString(job.paths().cache().resolve("wago/blobs/"+hash(bytes)+".csv"),"corrupt");requests.clear();
    var repaired=get(reader(),job,manifest);
    assertThat(requests).hasSize(1);assertThat(repaired.directory()).isNotEqualTo(first.directory());
    assertThat(Files.readAllBytes(repaired.directory().resolve("csv/Item.csv"))).isEqualTo(bytes);
  }
  @Test void malformedPointerAndProofRecoverFromVerifiedBlobsWithoutNetwork() throws Exception {
    byte[] bytes=csv("one");route("Item",bytes);var job=job();var manifest=manifest("Item",bytes);
    var first=get(reader(),job,manifest);requests.clear();routes.clear();
    Files.writeString(pointer(job),"directory="+"\\"+"uZZZZ\n");
    var recovered=get(reader(),job,manifest);
    assertThat(recovered.directory()).isNotEqualTo(first.directory());
    Files.writeString(recovered.directory().resolve("acquisition.properties"),"provider="+"\\"+"uZZZZ\n");
    var repaired=get(reader(),job,manifest);
    assertThat(repaired.directory()).isNotEqualTo(recovered.directory());
    Files.delete(repaired.directory().resolve("acquisition.properties"));
    var restored=get(reader(),job,manifest);
    assertThat(Files.readAllBytes(restored.directory().resolve("csv/Item.csv"))).isEqualTo(bytes);
    assertThat(restored.downloaded()).isZero();assertThat(requests).isEmpty();
  }
  @Test void failedNewInputKeepsPriorCompleteManifestAndFiles() throws Exception {
    byte[] old=csv("old");route("Item",old);var job=job();var first=get(reader(),job,manifest("Item",old));
    byte[] before=Files.readAllBytes(pointer(job));
    var replacement=new WagoTableMaterializer.Manifest(BUILD,Map.of("Item",hash(old),"MissingTable",hash(csv("missing"))));
    assertThatIOException().isThrownBy(()->get(reader(),job,replacement));
    assertThat(Files.readAllBytes(pointer(job))).isEqualTo(before);
    assertThat(Files.readAllBytes(first.directory().resolve("csv/Item.csv"))).isEqualTo(old);
    assertThat(job.paths().archiveDatabase()).doesNotExist();
  }
  @Test void hashMismatchHtmlAndRedirectAreErrorsWithoutPublishingAnInput() throws Exception {
    var job=job();byte[] expected=csv("expected");route("Item",csv("wrong"));
    assertThatIOException().isThrownBy(()->get(reader(),job,manifest("Item",expected)));
    byte[] html="<html>blocked</html>".getBytes(StandardCharsets.UTF_8);
    routes.put("/db2/Item/csv?build="+BUILD.build(),new Route(200,html,"text/html",false));
    assertThatIOException().isThrownBy(()->get(reader(),job,manifest("Item",html)));
    routes.put("/db2/Item/csv?build="+BUILD.build(),new Route(302,expected,"text/csv",false));
    requests.clear();assertThatIOException().isThrownBy(()->get(reader(),job,manifest("Item",expected)));
    assertThat(requests).hasSize(1);assertThat(pointer(job)).doesNotExist();
  }
  @Test void bothKnownAndChunkedResponsesRespectByteLimits() throws Exception {
    var job=job();byte[] large=csv("x".repeat(200));route("Item",large);
    assertThatIOException().isThrownBy(()->get(reader(32,64,Duration.ofSeconds(2)),job,manifest("Item",large)));
    routes.put("/db2/Item/csv?build="+BUILD.build(),new Route(200,large,"text/csv",true));
    assertThatIOException().isThrownBy(()->get(reader(32,64,Duration.ofSeconds(2)),job,manifest("Item",large)));
    assertThat(pointer(job)).doesNotExist();
  }
  @Test void aggregateLimitIncludesPreviouslyCachedTables() throws Exception {
    var job=job();byte[] one=csv("a".repeat(30)),two=csv("b".repeat(30));route("Item",one);route("AreaTable",two);
    get(reader(),job,manifest("Item",one));byte[] before=Files.readAllBytes(pointer(job));
    var both=new WagoTableMaterializer.Manifest(BUILD,Map.of("Item",hash(one),"AreaTable",hash(two)));
    assertThatIOException().isThrownBy(()->get(reader(64,64,Duration.ofSeconds(2)),job,both));
    assertThat(Files.readAllBytes(pointer(job))).isEqualTo(before);
  }
  @Test void cancellationBeforePublishLeavesManifestUntouchedAndVerifiedBlobsCanResume() throws Exception {
    var job=job();byte[] bytes=csv("one");route("Item",bytes);var manifest=manifest("Item",bytes);
    AtomicBoolean cancelled=new AtomicBoolean();
    assertThatThrownBy(()->reader().materialize(plan(WAGO_EXPORT),job,manifest,cancelled::get,
        progress->{if(progress.stage()==WagoTableMaterializer.Stage.PUBLISHING)cancelled.set(true);})).isInstanceOf(CancellationException.class);
    assertThat(pointer(job)).doesNotExist();requests.clear();
    var resumed=get(reader(),job,manifest);assertThat(resumed.downloaded()).isZero();assertThat(resumed.reused()).isEqualTo(1);assertThat(requests).isEmpty();
  }
  @Test void cancellationWhileServerStallsDoesNotWaitForResponseTimeout() throws Exception {
    CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
    server.createContext("/db2/Slow/csv",exchange->{
      requests.add(exchange.getRequestURI().toString());exchange.sendResponseHeaders(200,0);entered.countDown();
      try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}
      exchange.close();
    });
    AtomicBoolean cancelled=new AtomicBoolean();var job=job();var manifest=manifest("Slow",csv("slow"));
    ExecutorService worker=Executors.newSingleThreadExecutor();
    try {
      var future=worker.submit(()->reader().materialize(plan(WAGO_EXPORT),job,manifest,cancelled::get,p->{}));
      assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();cancelled.set(true);
      assertThatThrownBy(()->future.get(2,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(CancellationException.class);
      assertThat(pointer(job)).doesNotExist();
    } finally{release.countDown();worker.shutdownNow();}
  }
  @Test void stalledBodyHasAnOverallTimeoutAndCannotPublishPartialInput() throws Exception {
    CountDownLatch release=new CountDownLatch(1);
    server.createContext("/db2/Timeout/csv",exchange->{
      exchange.sendResponseHeaders(200,0);
      try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}
      exchange.close();
    });
    var job=job();
    try {
      assertThatIOException().isThrownBy(()->get(reader(4096,8192,Duration.ofMillis(500)),job,manifest("Timeout",csv("timeout"))));
      assertThat(pointer(job)).doesNotExist();
    } finally{release.countDown();}
  }
  @Test void invalidTableNamesEraAndHotfixSelectionsNeverReachNetwork() throws Exception {
    for(String name:List.of("../Item","ItemSparse.era","Item.hotfix","Item?build=other"))
      assertThatIllegalArgumentException().isThrownBy(()->new WagoTableMaterializer.Manifest(BUILD,Map.of(name,"a".repeat(64))));
    assertThatIllegalArgumentException().isThrownBy(()->new WagoTableMaterializer.Manifest(BUILD,Map.of("Item","bad")));
    assertThat(requests).isEmpty();
  }
  @Test void jobCacheAliasIntoGameInstallationIsRejectedWithoutWritingOrDownloading() throws Exception {
    Path game=Files.createDirectory(temporary.resolve("WoW"));
    var job=plan(WAGO_EXPORT).prepareJob(temporary.resolve("app"),Optional.empty(),"job",List.of(game));
    Files.delete(job.paths().cache());Files.createSymbolicLink(job.paths().cache(),game);
    assertThatIOException().isThrownBy(()->get(reader(),job,manifest("Item",csv("one"))));
    assertThat(requests).isEmpty();assertThat(game.resolve("wago")).doesNotExist();
  }
}
