import java.awt.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
/** External observer only: captures the isolated X11 display without attaching to or modifying the app. */
public final class X11ScreenCapture {
 public static void main(String[] args)throws Exception {
  if(args.length!=1||GraphicsEnvironment.isHeadless())throw new IllegalArgumentException("OUTPUT_PNG on an isolated DISPLAY");
  Path output=Path.of(args[0]);if(output.getParent()!=null)Files.createDirectories(output.getParent());
  Rectangle bounds=GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
  ImageIO.write(new Robot().createScreenCapture(bounds),"png",output.toFile());
  System.out.println("External isolated X11 capture: "+output+" "+bounds);
 }
}
