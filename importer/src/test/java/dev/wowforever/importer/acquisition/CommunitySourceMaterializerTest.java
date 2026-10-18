package dev.wowforever.importer.acquisition;

import dev.wowforever.importer.setup.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.wowforever.importer.setup.SetupPlan.Source.*;

class CommunitySourceMaterializerTest {
  @TempDir Path temp;
  static CommunitySourceMaterializer.Spec spec(SetupPlan.Source source,String key,String commit,List<String> paths){return new CommunitySourceMaterializer.Spec(source,key,"owner","repo",commit,paths);}
  static List<CommunitySourceMaterializer.Spec> specs(){return List.of(spec(QUESTIE,"questiedb","a".repeat(40),List.of("data/Forever/items.lua","src/meta/itemMeta.lua")),spec(ATLASLOOT,"atlasloot","b".repeat(40),List.of("loot.lua","LICENSE")),spec(ALL_THE_THINGS,"allthethings","c".repeat(40),List.of(".contrib/.db/forever/quest name.lua","LICENSE")));}
  static SetupPlan plan(Set<SetupPlan.Source> selected,List<CommunitySourceMaterializer.Spec> specs){Map<SetupPlan.Source,String> pins=new EnumMap<>(SetupPlan.Source.class);for(var spec:specs)if(selected.contains(spec.source()))pins.put(spec.source(),spec.commit());return new SetupPlan(selected,Set.of(),Optional.empty(),List.of(),pins);}
  SetupPlan.JobContext job(SetupPlan plan,String id)throws Exception{return plan.prepareJob(temp.resolve("app"),Optional.empty(),id,List.of());}
  static CommunitySourceMaterializer.Limits limits(){return new CommunitySourceMaterializer.Limits(10000,100000,100,Duration.ofSeconds(2),Duration.ZERO);}
  static CommunitySourceMaterializer.Transport fake(List<URI> requests){return(uri,file,max,cancel,progress)->{requests.add(uri);byte[] bytes=("-- "+uri.getPath()+"\n").getBytes();Files.write(file,bytes,StandardOpenOption.CREATE_NEW);progress.accept((long)bytes.length,(long)bytes.length);};}
  @Test void raidbotsSelectionIsNotMistakenForAnotherGithubCommunitySource()throws Exception {
    var selected=Set.of(QUESTIE,NATIVE_DATA,RAIDBOTS_HOTFIX);
    var pins=Map.of(QUESTIE,"a".repeat(40),RAIDBOTS_HOTFIX,PinnedItemSparseOverlayMaterializer.SNAPSHOT_SHA);
    var plan=new SetupPlan(selected,Set.of(SetupPlan.Acquisition.WAGO_EXPORT),Optional.of(new SetupPlan.NativeIdentity("wow_classic_beta","1.60.1.70450","enUS")),List.of(),pins);
    List<URI> requests=new ArrayList<>();
    try(var materializer=new CommunitySourceMaterializer(specs(),limits(),fake(requests))) {
      var result=materializer.materialize(plan,job(plan,"hotfix-community"),()->false,p->{});
      assertEquals(2,requests.size());assertEquals(2,result.evidence().size());
      assertTrue(result.evidence().stream().allMatch(file->file.source()==QUESTIE));
    }
  }
  @Test void uncheckedSourcesNeverDownloadOrAppearInMaterializedInputs()throws Exception{
    var plan=plan(Set.of(QUESTIE),specs());var job=job(plan,"selected");List<URI> requests=new ArrayList<>();
    try(var materializer=new CommunitySourceMaterializer(specs(),limits(),fake(requests))){var result=materializer.materialize(plan,job,()->false,p->{});assertEquals(2,requests.size());assertTrue(requests.stream().allMatch(uri->uri.getPath().contains("a".repeat(40))));assertTrue(result.atlasLoot().isEmpty());assertTrue(result.allTheThings().isEmpty());assertTrue(Files.isRegularFile(result.questie().resolve("data/Forever/items.lua")));assertEquals(2,result.evidence().size());assertTrue(result.inputs(Optional.empty()).atlasLoot().isEmpty());}
  }
  @Test void exactSelectedOptionalSourcesAndCompleteManifestsAreRetained()throws Exception{
    var plan=plan(Set.of(QUESTIE,ATLASLOOT,ALL_THE_THINGS),specs());var job=job(plan,"all");List<URI> requests=new ArrayList<>();
    try(var materializer=new CommunitySourceMaterializer(specs(),limits(),fake(requests))){var result=materializer.materialize(plan,job,()->false,p->{});assertEquals(6,requests.size());assertTrue(Files.isRegularFile(result.atlasLoot().orElseThrow().resolve("LICENSE")));assertTrue(Files.isRegularFile(result.allTheThings().orElseThrow().resolve(".contrib/.db/forever/quest name.lua")));assertTrue(requests.stream().allMatch(uri->uri.getHost().equals("raw.githubusercontent.com")));}
  }
  @Test void verifiedCacheReusesOnlyCompleteUnchangedInputs()throws Exception{
    var plan=plan(Set.of(QUESTIE),specs());var job=job(plan,"reuse");List<URI> requests=new ArrayList<>();
    try(var materializer=new CommunitySourceMaterializer(specs(),limits(),fake(requests))){var first=materializer.materialize(plan,job,()->false,p->{});var second=materializer.materialize(plan,job,()->false,p->{});assertTrue(second.reused());assertEquals(first.questie(),second.questie());assertEquals(2,requests.size());assertEquals(0,second.downloadedBytes());Files.writeString(first.questie().resolve("src/meta/itemMeta.lua"),"corrupted");var third=materializer.materialize(plan,job,()->false,p->{});assertFalse(third.reused());assertNotEquals(first.questie(),third.questie());assertEquals(4,requests.size());}
  }
  @Test void failedRefreshKeepsLastCompleteInputAndPointer()throws Exception{
    var plan=plan(Set.of(QUESTIE),specs());var job=job(plan,"failure");Path firstPath;
    try(var materializer=new CommunitySourceMaterializer(specs(),limits(),fake(new ArrayList<>()))){firstPath=materializer.materialize(plan,job,()->false,p->{}).questie();}
    Path pointer=job.paths().cache().resolve("community/current-input.properties");byte[] before=Files.readAllBytes(pointer);Files.writeString(firstPath.resolve("extra.lua"),"unselected cache content");
    try(var materializer=new CommunitySourceMaterializer(specs(),limits(),(u,f,m,c,p)->{throw new IOException("unavailable");})){assertThrows(IOException.class,()->materializer.materialize(plan,job,()->false,p->{}));}
    assertArrayEquals(before,Files.readAllBytes(pointer));assertTrue(Files.isRegularFile(firstPath.resolve("data/Forever/items.lua")));
  }
  @Test void malformedCachedPointerCanBeRebuiltWithoutManualDeletion()throws Exception{
    var plan=plan(Set.of(QUESTIE),specs());var job=job(plan,"metadata-recovery");List<URI> requests=new ArrayList<>();
    try(var materializer=new CommunitySourceMaterializer(specs(),limits(),fake(requests))){
      var first=materializer.materialize(plan,job,()->false,p->{});
      Files.writeString(job.paths().cache().resolve("community/current-input.properties"),"directory="+"\\"+"uZZZZ\n");
      var restored=materializer.materialize(plan,job,()->false,p->{});
      assertFalse(restored.reused());assertNotEquals(first.questie(),restored.questie());assertEquals(4,requests.size());
      assertTrue(Files.isRegularFile(first.questie().resolve("data/Forever/items.lua")));
      assertTrue(materializer.materialize(plan,job,()->false,p->{}).reused());
      assertEquals(4,requests.size());
    }
  }
  @Test void changedOrFloatingPinsFailBeforeDownloadsOrCacheWrites()throws Exception{
    var valid=plan(Set.of(QUESTIE),specs());var job=job(valid,"pins");var altered=new SetupPlan(Set.of(QUESTIE),Set.of(),Optional.empty(),List.of(),Map.of(QUESTIE,"d".repeat(40)));
    AtomicInteger requests=new AtomicInteger();try(var materializer=new CommunitySourceMaterializer(specs(),limits(),(u,f,m,c,p)->requests.incrementAndGet())){assertThrows(IOException.class,()->materializer.materialize(altered,job,()->false,p->{}));}
    assertEquals(0,requests.get());assertFalse(Files.exists(job.paths().cache().resolve("community")));
    var floating=List.of(spec(QUESTIE,"questiedb","main",List.of("file.lua")));var plan=plan(Set.of(QUESTIE),floating);try(var materializer=new CommunitySourceMaterializer(floating,limits(),fake(new ArrayList<>()))){assertThrows(IOException.class,()->materializer.materialize(plan,job,()->false,p->{}));}
  }
  @Test void rejectsUnsafeAndCaseCollidingPathsBeforeAnyDownload()throws Exception{
    for(var paths:List.of(List.of("../escape.lua"),List.of("/escape.lua"),List.of("data\\escape.lua"),List.of("a.lua","A.lua"))){var specs=List.of(spec(QUESTIE,"questiedb","a".repeat(40),paths));var plan=plan(Set.of(QUESTIE),specs);var job=job(plan,UUID.randomUUID().toString());try(var materializer=new CommunitySourceMaterializer(specs,limits(),(u,f,m,c,p)->fail("No unsafe download"))){assertThrows(IOException.class,()->materializer.materialize(plan,job,()->false,p->{}));}}
    assertFalse(Files.exists(temp.resolve("escape.lua")));
  }
  @Test void cancellationCleansNewStageWithoutPublishingPartialInput()throws Exception{
    var plan=plan(Set.of(QUESTIE),specs());var job=job(plan,"cancel");AtomicBoolean cancel=new AtomicBoolean();
    try(var materializer=new CommunitySourceMaterializer(specs(),limits(),(u,f,m,c,p)->{Files.writeString(f,"partial");cancel.set(true);})){assertThrows(CancellationException.class,()->materializer.materialize(plan,job,cancel::get,p->{}));}
    assertFalse(Files.exists(job.paths().cache().resolve("community/current-input.properties")));try(var entries=Files.list(job.paths().cache().resolve("community"))){assertEquals(List.of("materialize.lock"),entries.map(p->p.getFileName().toString()).toList());}
  }
  @Test void oversizedAndTooManyFilesAreRefused()throws Exception{
    var plan=plan(Set.of(QUESTIE),specs());var job=job(plan,"bound");var limits=new CommunitySourceMaterializer.Limits(2,4,10,Duration.ofSeconds(1),Duration.ZERO);
    try(var materializer=new CommunitySourceMaterializer(specs(),limits,fake(new ArrayList<>()))){assertThrows(IOException.class,()->materializer.materialize(plan,job,()->false,p->{}));}
    var countLimit=new CommunitySourceMaterializer.Limits(10000,100000,1,Duration.ofSeconds(1),Duration.ZERO);try(var materializer=new CommunitySourceMaterializer(specs(),countLimit,(u,f,m,c,p)->fail("No download before count gate"))){assertThrows(IOException.class,()->materializer.materialize(plan,job,()->false,p->{}));}
  }
  @Test void symlinkIntoGameCannotRedirectJobWrites()throws Exception{
    var plan=plan(Set.of(QUESTIE),specs());Path game=temp.resolve("game");Files.createDirectories(game);var job=plan.prepareJob(temp.resolve("safe"),Optional.empty(),"game",List.of(game));
    try{Files.createSymbolicLink(job.paths().cache().resolve("community"),game);}catch(UnsupportedOperationException|FileSystemException unavailable){return;}
    try(var materializer=new CommunitySourceMaterializer(specs(),limits(),(u,f,m,c,p)->fail("No download into game"))){assertThrows(IOException.class,()->materializer.materialize(plan,job,()->false,p->{}));}
    try(var files=Files.list(game)){assertEquals(0,files.count());}
  }
  @Test void actualHttpFixtureDoesNotFollowRedirectsAndPreservesCompleteSourceBoundary()throws Exception{
    HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger calls=new AtomicInteger();server.createContext("/",exchange->{calls.incrementAndGet();byte[] body="-- pinned fixture\n".getBytes();exchange.sendResponseHeaders(200,body.length);try(var out=exchange.getResponseBody()){out.write(body);}});server.start();
    var plan=plan(Set.of(QUESTIE),specs());var job=job(plan,"http");URI base=URI.create("http://127.0.0.1:"+server.getAddress().getPort());
    try(var materializer=new CommunitySourceMaterializer(specs(),limits(),HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),base)){var result=materializer.materialize(plan,job,()->false,p->{});assertEquals(2,calls.get());assertEquals(2,result.evidence().size());}finally{server.stop(0);}
  }
  @Test void unsupportedSelectedSourceDoesNotChooseAnotherProvider()throws Exception{
    var plan=plan(Set.of(QUESTIE,ATLASLOOT),specs());var job=job(plan,"unsupported");
    try(var materializer=new CommunitySourceMaterializer(List.of(specs().getFirst()),limits(),(u,f,m,c,p)->fail("No fallback download"))){assertThrows(IOException.class,()->materializer.materialize(plan,job,()->false,p->{}));}
    assertFalse(Files.exists(job.paths().cache().resolve("community")));
  }
  @Test void productionReviewedManifestsAreCompleteAndSafeWithSyntheticTransport()throws Exception{
    var reviewed=CommunitySourceMaterializer.registered();var plan=plan(Set.of(QUESTIE,ATLASLOOT,ALL_THE_THINGS),reviewed);var job=job(plan,"reviewed");List<URI> requests=new ArrayList<>();
    try(var materializer=new CommunitySourceMaterializer(reviewed,new CommunitySourceMaterializer.Limits(64L<<20,1L<<30,2048,Duration.ofSeconds(2),Duration.ZERO),fake(requests))){
      var result=materializer.materialize(plan,job,()->false,p->{});assertEquals(reviewed.stream().mapToInt(spec->spec.paths().size()).sum(),result.evidence().size());assertTrue(result.atlasLoot().isPresent());assertTrue(result.allTheThings().isPresent());
    }
  }
  @Test void actualHttpCancellationInterruptsStreamingWithoutPublishing()throws Exception{
    HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",exchange->{exchange.sendResponseHeaders(200,0);try(var out=exchange.getResponseBody()){for(int i=0;i<20;i++){out.write(new byte[]{1,2,3});out.flush();try{Thread.sleep(50);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();break;}}}catch(IOException stopped){}});server.start();
    var plan=plan(Set.of(QUESTIE),specs());var job=job(plan,"httpcancel");AtomicBoolean cancelled=new AtomicBoolean();
    try(var materializer=new CommunitySourceMaterializer(specs(),limits(),HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()))){
      assertThrows(CancellationException.class,()->materializer.materialize(plan,job,cancelled::get,p->{if(p.stage()==CommunitySourceMaterializer.Stage.DOWNLOADING&&p.bytes()>0)cancelled.set(true);}));assertFalse(Files.exists(job.paths().cache().resolve("community/current-input.properties")));
    }finally{server.stop(0);}
  }
  @Test void actualHttpRedirectFailsWithoutFollowingLocation()throws Exception{
    HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger followed=new AtomicInteger();server.createContext("/owner/",exchange->{exchange.getResponseHeaders().set("Location","/other");exchange.sendResponseHeaders(302,-1);exchange.close();});server.createContext("/other",exchange->{followed.incrementAndGet();exchange.sendResponseHeaders(200,-1);exchange.close();});server.start();
    var plan=plan(Set.of(QUESTIE),specs());var job=job(plan,"redirect");try(var materializer=new CommunitySourceMaterializer(specs(),limits(),HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()))){assertThrows(IOException.class,()->materializer.materialize(plan,job,()->false,p->{}));assertEquals(0,followed.get());assertFalse(Files.exists(job.paths().cache().resolve("community/current-input.properties")));}finally{server.stop(0);}
  }
}
