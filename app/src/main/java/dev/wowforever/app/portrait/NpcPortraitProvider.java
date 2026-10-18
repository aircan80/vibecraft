package dev.wowforever.app.portrait;

import java.awt.image.BufferedImage;
import java.util.Objects;

/** Read-only source of ordinary portraits. Providers run only on repository workers. */
@FunctionalInterface
public interface NpcPortraitProvider {
  record Key(String product,String build,long npcId) {
    public Key {Objects.requireNonNull(product);Objects.requireNonNull(build);if(product.isBlank()||build.isBlank()||npcId<=0)throw new IllegalArgumentException("Exact game, build and NPC are required");}
  }
  record Portrait(BufferedImage image,Key key,String source,String evidence) {
    public Portrait {Objects.requireNonNull(image);Objects.requireNonNull(key);Objects.requireNonNull(source);Objects.requireNonNull(evidence);}
  }
  enum Status {AVAILABLE,MISSING,INVALID}
  record Result(Status status,Portrait portrait,String message) {
    public Result {Objects.requireNonNull(status);Objects.requireNonNull(message);if((status==Status.AVAILABLE)!=(portrait!=null))throw new IllegalArgumentException("Available portraits require an image");}
    public static Result missing(){return new Result(Status.MISSING,null,"No portrait available");}
    public static Result invalid(String message){return new Result(Status.INVALID,null,message);}
    public static Result available(Portrait portrait){return new Result(Status.AVAILABLE,portrait,"");}
  }
  Result load(Key key)throws Exception;
}
