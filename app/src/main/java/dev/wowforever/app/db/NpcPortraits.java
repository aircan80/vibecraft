package dev.wowforever.app.db;

import dev.wowforever.app.ui.widgets.Images;
import dev.wowforever.app.assets.ReviewedDataFiles;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import javax.swing.SwingUtilities;

/** Small verified resource pack; decoding is restricted to the detail loader's worker. */
public final class NpcPortraits {
  private NpcPortraits() {}
  public record Portrait(BufferedImage image,String build,String evidence) {}
  private static Optional<Portrait> cached;
  public static synchronized Optional<Portrait> load(long npcId,String build) {
    if(npcId!=2671 || !"1.60.1.70450".equals(build))return Optional.empty();
    if(SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Load NPC portraits on a worker");
    if(cached!=null)return cached;
    cached=read();return cached;
  }
  private static Optional<Portrait> read() {
    try(var image=ReviewedDataFiles.open("npc-portraits/mechanical-squirrel.png","71982d7beff2d6452c37bd3c92ca784b08ca74bed7b482fea550da6a6308b10d",4*1024*1024);
        var manifest=ReviewedDataFiles.open("npc-portraits/mechanical-squirrel.json","606e902230ee6bd0d4b957dd5cb79bae796204644c1448f8977f1c6746a0b5b9",1024*1024)) {
      if(image==null || manifest==null)return Optional.empty();
      byte[] bytes=image.readAllBytes();
      String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      if(!hash.equals("71982d7beff2d6452c37bd3c92ca784b08ca74bed7b482fea550da6a6308b10d"))return Optional.empty();
      String evidence=new String(manifest.readAllBytes(),StandardCharsets.UTF_8);
      return Images.decode(bytes).map(decoded -> new Portrait(decoded,"1.60.1.70450",evidence));
    }catch(IOException | NoSuchAlgorithmException e){return Optional.empty();}
  }
}
