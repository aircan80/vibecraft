import dev.wowforever.app.db.*;
import dev.wowforever.app.nav.*;
import dev.wowforever.app.ui.*;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.theme.Fonts;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Exact-JAR offline catalog acceptance. The source database is closed before any page renders. */
public final class CatalogSnapshots {
  public static void main(String[] args) throws Exception {
    if(args.length!=3)throw new IllegalArgumentException("Usage: CatalogSnapshots REVIEW_JAR DATABASE EMPTY_OUTPUT");
    Path output=Path.of(args[2]);Files.createDirectories(output);
    try(var entries=Files.list(output)){if(entries.findAny().isPresent())throw new IllegalArgumentException("Output must be empty");}
    SwingUtilities.invokeAndWait(()->{Fonts.install();ThemeManager.apply(ThemeManager.Mode.DARK);RepaintManager.currentManager(null).setDoubleBufferingEnabled(false);});
    Map<ViewTarget,Object> loaded=new LinkedHashMap<>();List<String> report=new ArrayList<>();
    report.add("Created UTC: "+Instant.now());
    report.add("JAR SHA256: "+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(args[0])))));
    List<ViewTarget> targets=new ArrayList<>();
    for(String section:List.of("hub","ladder","pvp","races","camping","talents","legacy","class-spells"))targets.add(new ViewTarget.Forever(section));
    targets.add(new ViewTarget.Forever("race",95));targets.add(new ViewTarget.Forever("race",96));targets.add(new ViewTarget.Forever("discovery",2981));
    for(String section:List.of("sets","stats","trinkets","recipes","xp-food","comprehension"))targets.add(new ViewTarget.ItemContent(section));
    targets.add(new ViewTarget.ItemContent("set",2133));targets.add(new ViewTarget.ItemContent("set",2135));
    try(var db=Database.open(Path.of(args[1]))) {
      report.add("Database build: "+db.meta().get("build_id"));
      for(ViewTarget target:targets) {
        for(int i=0;i<3;i++)CatalogPages.load(target,db);
        double[] samples=new double[30];
        for(int i=0;i<samples.length;i++) {
          long start=System.nanoTime();Object snapshot=CatalogPages.load(target,db);
          samples[i]=(System.nanoTime()-start)/1_000_000.0;loaded.put(target,snapshot);
        }
        Arrays.sort(samples);double p95=samples[(int)Math.ceil(samples.length*.95)-1];
        report.add(target+" warm load p95: "+p95+" ms");
        if(p95>300)throw new IllegalStateException("Catalog warm load exceeds 300 ms: "+target+" "+p95);
      }
    }
    int[] captures={0};List<String> failures=new ArrayList<>();
    for(var mode:ThemeManager.Mode.values()) {
      SwingUtilities.invokeAndWait(()->ThemeManager.apply(mode));
      for(var entry:loaded.entrySet())for(int width:new int[]{1216,420}) {
        String section=entry.getKey() instanceof ViewTarget.Forever f?f.section()+"-"+f.id():((ViewTarget.ItemContent)entry.getKey()).section()+"-"+((ViewTarget.ItemContent)entry.getKey()).id();
        String name=section+"-"+mode.name().toLowerCase(Locale.ROOT)+"-"+width;
        SwingUtilities.invokeAndWait(()->{
          JComponent page=CatalogPages.render(entry.getKey(),entry.getValue(),ignored->{});
          JPanel root=new JPanel(new BorderLayout());root.putClientProperty(CompactColumns.KEY,width==420);root.add(page);root.setSize(width,720);
          for(int pass=0;pass<16;pass++)layout(root);
          inspect(root,name,failures);
          BufferedImage image=new BufferedImage(width,720,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();root.printAll(g);g.dispose();
          try{ImageIO.write(image,"png",output.resolve(name+".png").toFile());}catch(Exception e){throw new RuntimeException(e);}captures[0]++;
        });
      }
    }
    report.add("Database closed before render: true");report.add("Captures: "+captures[0]);report.add("Geometry failures: "+failures.size());report.addAll(failures);
    report.add("Headless JPanel captures do not establish native keyboard, pointer or frame interaction acceptance.");
    Files.write(output.resolve("report.txt"),report);System.out.println(String.join("\n",report));
    if(!failures.isEmpty())throw new IllegalStateException("See report.txt");
  }
  private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container nested)layout(nested);}
  private static void inspect(Container c,String name,List<String> failures) {
    if(c instanceof JScrollPane scroll && scroll.getHorizontalScrollBar().isVisible())
      failures.add(name+": horizontal scrolling required by "+scroll.getViewport().getView().getClass().getSimpleName());
    for(Component child:c.getComponents())if(child instanceof Container nested)inspect(nested,name,failures);
  }
}
