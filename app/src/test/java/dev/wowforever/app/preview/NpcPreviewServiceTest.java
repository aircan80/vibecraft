package dev.wowforever.app.preview;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NpcPreviewServiceTest {
  @TempDir Path cache;
  private NpcPreviewService.Config config(String version){return new NpcPreviewService.Config(cache,null,"python3",version,Duration.ofSeconds(5));}
  private static NpcPreviewService.Request request(String product,String build,String key){return new NpcPreviewService.Request(product,build,7380,5556,100,123,List.of(4L,0L,0L,0L),1d,1d,key,"catalog","native evidence");}

  @Test void cacheKeysSeparateGamesBuildsAppearancesAndRendererVersions(){
    try(var service=new NpcPreviewService(config("v1"));var next=new NpcPreviewService(config("v2"))){
      var request=request("wow_classic_beta","1.60.1.70450","appearance-a");String key=service.cacheKey(request);
      assertThat(key).matches("[a-f0-9]{64}");
      assertThat(service.cacheKey(request("wow","1.60.1.70450","appearance-a"))).isNotEqualTo(key);
      assertThat(service.cacheKey(request("wow_classic_beta","1.60.1.70451","appearance-a"))).isNotEqualTo(key);
      assertThat(service.cacheKey(request("wow_classic_beta","1.60.1.70450","appearance-b"))).isNotEqualTo(key);
      assertThat(next.cacheKey(request)).isNotEqualTo(key);
    }
  }
  @Test void duplicatePendingJobsShareOneBackgroundRenderAndPublishedCacheSurvivesRestart()throws Exception {
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();
    var request=request("wow_classic_beta","1.60.1.70450","same-appearance");
    try(var service=new NpcPreviewService(config("v1"),(r,file,output,c)->{
      assertThat(SwingUtilities.isEventDispatchThread()).isFalse();assertThat(Thread.currentThread().getName()).isEqualTo("npc-renderer");
      calls.incrementAndGet();entered.countDown();assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();write(r,output,c);
    })){
      var first=service.generate(request);assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();var second=service.generate(request);
      assertThat(second).isSameAs(first);assertThat(service.pending(request)).contains(first);assertThat(Files.exists(cache.resolve(service.cacheKey(request)))).isFalse();
      release.countDown();var ready=first.get(5,TimeUnit.SECONDS);
      assertThat(ready.frames()).hasSize(12);assertThat(calls.get()).isEqualTo(1);
      assertThat(Files.isRegularFile(ready.directory().resolve("manifest.json"))).isTrue();
      // File verification must still run after a successful decoded-cache hit.
      assertThat(service.cached(request).get(5,TimeUnit.SECONDS)).isPresent();
      Files.write(ready.directory().resolve("turntable-03.png"),new byte[]{1,2,3});
      assertThat(service.cached(request).get(5,TimeUnit.SECONDS)).isEmpty();
      service.generate(request).get(5,TimeUnit.SECONDS);assertThat(calls.get()).isEqualTo(2);
    }
    try(var reopened=new NpcPreviewService(config("v1"),(r,f,o,c)->{throw new AssertionError("Cached preview must not render again");})){
      assertThat(reopened.cached(request).get(5,TimeUnit.SECONDS)).isPresent();
      assertThat(reopened.generate(request).get(5,TimeUnit.SECONDS).build()).isEqualTo("1.60.1.70450");
    }
  }
  @Test void failedOrWrongBuildOutputIsNeverPublished()throws Exception {
    var request=request("wow_classic_beta","1.60.1.70450","broken");
    try(var service=new NpcPreviewService(config("v1"),(r,file,output,c)->{
      write(request(r.product(),"1.15.9.70003",r.appearanceKey()),output,c);
    })){
      assertThatThrownBy(()->service.generate(request).get(5,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IOException.class);
      assertThat(Files.exists(cache.resolve(service.cacheKey(request)))).isFalse();
      assertThat(service.cached(request).get(5,TimeUnit.SECONDS)).isEmpty();
      try(var files=Files.list(cache)){assertThat(files.toList()).isEmpty();}
    }
  }
  @Test void edtCallsQueueFileWorkInsteadOfReadingOrRenderingInline()throws Exception {
    var request=request("wow_classic_beta","1.60.1.70450","edt");var background=new AtomicBoolean();
    try(var service=new NpcPreviewService(config("v1"),(r,file,output,c)->{background.set(!SwingUtilities.isEventDispatchThread());write(r,output,c);})){
      var future=new AtomicReference<CompletableFuture<NpcPreviewService.Preview>>();
      SwingUtilities.invokeAndWait(()->future.set(service.generate(request)));
      assertThat(future.get().get(5,TimeUnit.SECONDS).portrait()).isNotNull();assertThat(background.get()).isTrue();
    }
  }
  private static void write(NpcPreviewService.Request request,Path output,NpcPreviewService.Config config)throws Exception {
    Files.createDirectories(output);JsonObject manifest=new JsonObject();manifest.addProperty("product",request.product());manifest.addProperty("build",request.build());manifest.addProperty("appearanceKey",request.appearanceKey());manifest.addProperty("rendererVersion",config.rendererVersion());manifest.addProperty("evidence","test-native evidence");
    manifest.add("portrait",image(output,"portrait.png",1));JsonArray frames=new JsonArray();
    for(int i=0;i<12;i++){var frame=image(output,"turntable-%02d.png".formatted(i),i);frame.addProperty("angle",i*30);frames.add(frame);}manifest.add("frames",frames);
    Files.writeString(output.resolve("manifest.json"),manifest.toString());
  }
  private static JsonObject image(Path output,String file,int color)throws Exception {
    var image=new BufferedImage(4,4,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,0xff000000|color);ImageIO.write(image,"png",output.resolve(file).toFile());
    JsonObject entry=new JsonObject();entry.addProperty("file",file);entry.addProperty("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(output.resolve(file)))));return entry;
  }
}
