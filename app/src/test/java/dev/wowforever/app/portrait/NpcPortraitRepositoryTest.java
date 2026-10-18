package dev.wowforever.app.portrait;

import com.google.gson.*;
import dev.wowforever.app.portrait.NpcPortraitProvider.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class NpcPortraitRepositoryTest {
  @TempDir Path cache;
  private static Key key(long id){return new Key("wow_classic_beta","1.60.1.70450",id);}
  @Test void loadsOrdinaryPortraitIgnoringMissingOrInvalidRotationFrames()throws Exception {
    Path entry=write(key(7380));Files.writeString(entry.resolve("turntable-00.png"),"invalid ignored");
    try(var repository=new NpcPortraitRepository(List.of(NpcPortraitRepository.legacyCache(cache)))){
      var result=repository.load(key(7380)).get(5,TimeUnit.SECONDS);
      assertThat(result.status()).isEqualTo(Status.AVAILABLE);assertThat(result.portrait().image().getWidth()).isEqualTo(4);
      assertThat(result.portrait().key()).isEqualTo(key(7380));assertThat(result.portrait().source()).contains("local cache");
      assertThat(repository.load(key(7550)).get().status()).isEqualTo(Status.MISSING);
      assertThat(repository.load(new Key("wow","1.60.1.70450",7380)).get().status()).isEqualTo(Status.MISSING);
      assertThat(repository.load(new Key("wow_classic_beta","wrong-build",7380)).get().status()).isEqualTo(Status.MISSING);
    }
  }
  @Test void rejectsCorruptionAndMappingMismatch()throws Exception {
    Path entry=write(key(7550));Path file=entry.resolve("manifest.json");var m=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    m.getAsJsonObject("evidence").getAsJsonObject("mapping").addProperty("build","wrong");Files.writeString(file,m.toString());
    assertThat(NpcPortraitRepository.legacyCache(cache).load(key(7550)).status()).isEqualTo(Status.INVALID);
    m.getAsJsonObject("evidence").getAsJsonObject("mapping").addProperty("build",key(7550).build());Files.writeString(file,m.toString());
    Files.write(entry.resolve("portrait.png"),new byte[24]);
    assertThat(NpcPortraitRepository.legacyCache(cache).load(key(7550)).status()).isEqualTo(Status.INVALID);
  }
  @Test void rejectsSymlinksAndUnsafeFilenames()throws Exception {
    Path entry=write(key(7550));Path file=entry.resolve("manifest.json");var m=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    m.getAsJsonObject("portrait").addProperty("file","../portrait.png");Files.writeString(file,m.toString());
    assertThat(NpcPortraitRepository.legacyCache(cache).load(key(7550)).status()).isEqualTo(Status.INVALID);
    m.getAsJsonObject("portrait").addProperty("file","portrait.png");Files.writeString(file,m.toString());
    Path outside=cache.resolve("outside.png");Files.move(entry.resolve("portrait.png"),outside);Files.createSymbolicLink(entry.resolve("portrait.png"),outside);
    assertThat(NpcPortraitRepository.legacyCache(cache).load(key(7550)).status()).isEqualTo(Status.INVALID);
  }
  @Test void deduplicatesLoadsOffEdtAndBoundsDecodedCache()throws Exception {
    var calls=new AtomicInteger();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    try(var repository=new NpcPortraitRepository(List.of(k->{assertThat(SwingUtilities.isEventDispatchThread()).isFalse();calls.incrementAndGet();if(k.npcId()==1){entered.countDown();release.await();}return Result.available(new Portrait(new BufferedImage(1,1,2),k,"test","evidence"));}))){
      var first=new java.util.concurrent.atomic.AtomicReference<CompletableFuture<Result>>();SwingUtilities.invokeAndWait(()->first.set(repository.load(key(1))));
      assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();assertThat(repository.load(key(1))).isSameAs(first.get());release.countDown();first.get().get();
      repository.load(key(1)).get();assertThat(calls.get()).isEqualTo(1);
      for(int i=2;i<=9;i++)repository.load(key(i)).get();repository.load(key(1)).get();assertThat(calls.get()).isEqualTo(10);
    }finally{release.countDown();}
  }
  @Test void providerFailureIsInvalidAndCloseCompletesQueuedLoads()throws Exception {
    try(var repository=new NpcPortraitRepository(List.of(k->{throw new java.io.IOException("broken");}))){assertThat(repository.load(key(1)).get().status()).isEqualTo(Status.INVALID);}
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    var repository=new NpcPortraitRepository(List.of(k->{entered.countDown();release.await();return Result.missing();}));
    try {var first=repository.load(key(1));assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();var queued=repository.load(key(2));repository.close();assertThat(first.get().status()).isEqualTo(Status.INVALID);assertThat(queued.get().status()).isEqualTo(Status.INVALID);assertThat(repository.load(key(3)).get().status()).isEqualTo(Status.INVALID);}finally{release.countDown();repository.close();}
  }
  @Test void bundledPortraitIsExactGameAndBuildOnly()throws Exception {
    try(var repository=new NpcPortraitRepository(List.of(NpcPortraitRepository.bundled()))){
      assertThat(repository.load(key(2671)).get().status()).isEqualTo(Status.AVAILABLE);
      assertThat(repository.load(new Key("wow_classic_era",key(2671).build(),2671)).get().status()).isEqualTo(Status.MISSING);
      assertThat(repository.load(new Key(key(2671).product(),"other",2671)).get().status()).isEqualTo(Status.MISSING);
    }
  }
  @Test void boundsQueueAndRejectsProviderIdentityMismatch()throws Exception {
    try(var repository=new NpcPortraitRepository(List.of(k->Result.available(new Portrait(new BufferedImage(1,1,2),key(999),"test","evidence"))))){
      assertThat(repository.load(key(1)).get().status()).isEqualTo(Status.INVALID);
    }
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    try(var repository=new NpcPortraitRepository(List.of(k->{entered.countDown();release.await();return Result.missing();}))){
      var first=repository.load(key(1));assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
      var queued=new ArrayList<CompletableFuture<Result>>();for(int i=2;i<=17;i++)queued.add(repository.load(key(i)));
      assertThat(repository.load(key(18)).get().status()).isEqualTo(Status.INVALID);
      release.countDown();first.get();for(var f:queued)f.get();
    }finally{release.countDown();}
  }
  @Test void nonPngBytesAreRejectedEvenWithMatchingHash()throws Exception {
    Path entry=write(key(7550));byte[] invalid=new byte[40];Files.write(entry.resolve("portrait.png"),invalid);
    Path file=entry.resolve("manifest.json");var m=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    m.getAsJsonObject("portrait").addProperty("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(invalid)));Files.writeString(file,m.toString());
    assertThat(NpcPortraitRepository.legacyCache(cache).load(key(7550)).status()).isEqualTo(Status.INVALID);
  }
  private Path write(Key key)throws Exception {
    Path entry=cache.resolve("a".repeat(64));Files.createDirectories(entry);var image=new BufferedImage(4,4,2);ImageIO.write(image,"png",entry.resolve("portrait.png").toFile());
    var m=new JsonObject();m.addProperty("product",key.product());m.addProperty("build",key.build());var evidence=new JsonObject();var mapping=new JsonObject();mapping.addProperty("product",key.product());mapping.addProperty("build",key.build());mapping.addProperty("npcId",key.npcId());evidence.add("mapping",mapping);m.add("evidence",evidence);
    var portrait=new JsonObject();portrait.addProperty("file","portrait.png");portrait.addProperty("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(entry.resolve("portrait.png")))));m.add("portrait",portrait);m.addProperty("frames","deliberately ignored");Files.writeString(entry.resolve("manifest.json"),m.toString());return entry;
  }
}
