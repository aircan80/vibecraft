package dev.wowforever.app.portrait;

import com.google.gson.*;
import dev.wowforever.app.portrait.NpcPortraitProvider.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class NpcPortraitPackTest {
  @TempDir Path directory;
  private final Key key=new Key("wow_classic_beta","1.60.1.70450",7550);
  @Test void exactPackLookupPreservesSourceAndProvenance()throws Exception {
    var manifest=pack();save(manifest);var provider=NpcPortraitRepository.localPack(directory);
    var result=provider.load(key);assertThat(result.status()).isEqualTo(Status.AVAILABLE);
    assertThat(result.portrait().source()).isEqualTo("World of Warcraft artwork © Blizzard Entertainment · local pack");
    assertThat(result.portrait().evidence()).contains("native source");
    assertThat(provider.load(new Key(key.product(),"other",key.npcId())).status()).isEqualTo(Status.MISSING);
    assertThat(provider.load(new Key("wow",key.build(),key.npcId())).status()).isEqualTo(Status.MISSING);
    assertThat(provider.load(new Key(key.product(),key.build(),246)).status()).isEqualTo(Status.MISSING);
  }
  @Test void rejectsDuplicatesUnknownSchemaAndMissingAttribution()throws Exception {
    var m=pack();m.getAsJsonArray("entries").add(m.getAsJsonArray("entries").get(0).deepCopy());save(m);assertInvalid();
    m=pack();m.addProperty("schema","unknown");save(m);assertInvalid();
    for(String field:new String[]{"source","provenance"}){m=pack();m.getAsJsonArray("entries").get(0).getAsJsonObject().remove(field);save(m);assertInvalid();}
    m=pack();m.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("source","");save(m);assertInvalid();
  }
  @Test void rejectsHashCorruptionPathEscapeAndSymlinks()throws Exception {
    var m=pack();save(m);Files.write(directory.resolve("portrait.png"),new byte[24]);assertInvalid();
    for(String path:new String[]{"../portrait.png","/tmp/portrait.png"}){m=pack();m.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("file",path);save(m);assertInvalid();}
    m=pack();save(m);Path moved=directory.resolve("other.png");Files.move(directory.resolve("portrait.png"),moved);Files.createSymbolicLink(directory.resolve("portrait.png"),moved);assertInvalid();
  }
  private void assertInvalid()throws Exception {assertThat(NpcPortraitRepository.localPack(directory).load(key).status()).isEqualTo(Status.INVALID);}
  private void save(JsonObject m)throws Exception {Files.writeString(directory.resolve("manifest.json"),m.toString());}
  private JsonObject pack()throws Exception {
    Files.deleteIfExists(directory.resolve("portrait.png"));ImageIO.write(new BufferedImage(4,4,2),"png",directory.resolve("portrait.png").toFile());
    var entry=new JsonObject();entry.addProperty("product",key.product());entry.addProperty("build",key.build());entry.addProperty("npcId",key.npcId());entry.addProperty("file","portrait.png");entry.addProperty("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(directory.resolve("portrait.png")))));entry.addProperty("source","World of Warcraft artwork © Blizzard Entertainment · local pack");entry.addProperty("provenance","native source");
    var entries=new JsonArray();entries.add(entry);var m=new JsonObject();m.addProperty("schema","npc-portraits-v1");m.add("entries",entries);return m;
  }
}
