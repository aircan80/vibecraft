package dev.wowforever.app.db;

import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** One verified, bounded offline turntable pack. No network lookup and no appearance inference. */
public final class NpcTurntables {
  public static final long NPC_ID=2671;
  public static final String BUILD="1.60.1.70450";
  public static final int FRAME_COUNT=12;
  private static final String DIR="/data/npc-turntables/mechanical-squirrel/";
  private static final String MANIFEST_SHA="8f5acda1acb1e83f9b9006b2b91129ee710f866cffa648b8f552e254dec8ddc0";
  private static Optional<Turntable> cached;
  private NpcTurntables() {}
  public record Turntable(List<BufferedImage> frames,String build,String evidence) {
    public Turntable {frames=List.copyOf(frames);if(frames.size()!=FRAME_COUNT)throw new IllegalArgumentException("Expected 12 views");}
    public BufferedImage frame(int index){return frames.get(Math.floorMod(index,frames.size()));}
  }
  public static boolean supports(long npcId,String build){return npcId==NPC_ID&&BUILD.equals(build);}
  /** Call on the supplied application worker. Cache holds only this single verified ~28MiB decoded pack. */
  public static synchronized Optional<Turntable> load(long npcId,String build) {
    if(!supports(npcId,build))return Optional.empty();
    if(SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Decode NPC turntables on a worker");
    if(cached==null)cached=read();return cached;
  }
  private static Optional<Turntable> read() {
    try {
      byte[] manifest=bytes("manifest.json");if(!sha(manifest).equals(MANIFEST_SHA))return Optional.empty();
      String evidence=new String(manifest,StandardCharsets.UTF_8);var json=JsonParser.parseString(evidence).getAsJsonObject();
      if(json.get("npc_id").getAsLong()!=NPC_ID||!BUILD.equals(json.get("build").getAsString()))return Optional.empty();
      var entries=json.getAsJsonArray("frames");if(entries.size()!=FRAME_COUNT)return Optional.empty();
      List<BufferedImage> images=new ArrayList<>();
      for(int i=0;i<FRAME_COUNT;i++){
        var entry=entries.get(i).getAsJsonObject();String expectedFile="turntable-%02d.png".formatted(i);
        if(!expectedFile.equals(entry.get("file").getAsString())||entry.get("angle").getAsInt()!=i*30)return Optional.empty();
        byte[] png=bytes(expectedFile);if(!sha(png).equals(entry.get("sha256").getAsString()))return Optional.empty();
        BufferedImage image=ImageIO.read(new ByteArrayInputStream(png));
        if(image==null||image.getWidth()!=768||image.getHeight()!=768)return Optional.empty();images.add(image);
      }
      return Optional.of(new Turntable(images,BUILD,evidence));
    }catch(IOException|RuntimeException|NoSuchAlgorithmException failure){return Optional.empty();}
  }
  private static byte[] bytes(String file)throws IOException {
    try(var in=NpcTurntables.class.getResourceAsStream(DIR+file)){
      if(in==null)throw new IOException("Missing verified turntable resource");return in.readAllBytes();
    }
  }
  private static String sha(byte[] bytes)throws NoSuchAlgorithmException{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
