import dev.wowforever.app.db.Database;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.CatalogPages;
import dev.wowforever.app.ui.ThemeManager;
import dev.wowforever.app.ui.theme.Fonts;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.swing.SwingUtilities;

/** Exact-JAR worker-load plus EDT panel-construction timing. No layout, paint, or native interaction claim. */
public final class CatalogPerformance {
  private static final int WARMUPS=3,SAMPLES=30;
  private static final double LIMIT_MS=300;
  private record Sample(double query,double render,double combined) {}
  public static void main(String[] args)throws Exception {
    if(args.length!=3)throw new IllegalArgumentException("Usage: CatalogPerformance REVIEW_JAR DATABASE OUTPUT_JSON");
    Path jar=Path.of(args[0]),database=Path.of(args[1]),output=Path.of(args[2]);
    if(Files.exists(output))throw new IllegalArgumentException("Output must not exist");
    SwingUtilities.invokeAndWait(()->{Fonts.install();ThemeManager.apply(ThemeManager.Mode.DARK);});
    List<ViewTarget> targets=new ArrayList<>();
    for(String section:List.of("hub","ladder","pvp","races","camping","talents","legacy","class-spells"))targets.add(new ViewTarget.Forever(section));
    targets.add(new ViewTarget.Forever("race",95));targets.add(new ViewTarget.Forever("race",96));targets.add(new ViewTarget.Forever("discovery",2981));
    for(String section:List.of("sets","stats","trinkets","recipes","xp-food","comprehension"))targets.add(new ViewTarget.ItemContent(section));
    targets.add(new ViewTarget.ItemContent("set",2133));targets.add(new ViewTarget.ItemContent("set",2135));
    String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
    List<String> results=new ArrayList<>(),failures=new ArrayList<>();String build;
    try(var db=Database.open(database)) {
      build=db.meta().get("build_id");
      for(ViewTarget target:targets) {
        Sample cold=sample(target,db);for(int i=1;i<WARMUPS;i++)sample(target,db);
        double[] query=new double[SAMPLES],render=new double[SAMPLES],combined=new double[SAMPLES];
        for(int i=0;i<SAMPLES;i++){Sample s=sample(target,db);query[i]=s.query;render[i]=s.render;combined[i]=s.combined;}
        double q=p95(query),r=p95(render),total=p95(combined);boolean pass=total<=LIMIT_MS;
        if(!pass)failures.add(target.toString());
        System.out.printf(Locale.ROOT,"%s query p95 %.3f ms; EDT construction p95 %.3f ms; combined p95 %.3f ms; %s%n",target,q,r,total,pass?"PASS":"FAIL");
        results.add("{\"target\":"+quote(target.toString())+",\"warmup_iterations\":"+WARMUPS+",\"samples\":"+SAMPLES+",\"first_load_ms\":"+cold.query+",\"first_render_ms\":"+cold.render+",\"query_p95_ms\":"+q+",\"edt_construction_p95_ms\":"+r+",\"combined_p95_ms\":"+total+",\"passes_300ms\":"+pass+"}");
      }
    }
    String json="{\"created_utc\":"+quote(Instant.now().toString())+",\"app_jar_sha256\":"+quote(sha)+",\"database_build\":"+quote(build)+",\"theme\":\"dark\",\"scope\":\"All 19 base catalog targets used by CatalogSnapshots; 3 warmup load-and-render iterations, then 30 samples per target. Combined time includes worker-side CatalogPages.load and actual EDT CatalogPages.render construction. No geometry layout, paint, theme variants, or native interaction measured. First cold timings reported separately.\",\"limit_ms\":"+LIMIT_MS+",\"targets\":["+String.join(",",results)+"],\"failures\":["+String.join(",",failures.stream().map(CatalogPerformance::quote).toList())+"]}";
    if(output.getParent()!=null)Files.createDirectories(output.getParent());Files.writeString(output,json+"\n");
    if(!failures.isEmpty())throw new IllegalStateException("Combined p95 exceeds 300 ms: "+failures);
  }
  private static Sample sample(ViewTarget target,Database db)throws Exception {
    long begin=System.nanoTime();Object data=CatalogPages.load(target,db);long loaded=System.nanoTime();
    double[] render={0};SwingUtilities.invokeAndWait(()->{long start=System.nanoTime();CatalogPages.render(target,data,ignored->{});render[0]=(System.nanoTime()-start)/1_000_000.0;});
    double query=(loaded-begin)/1_000_000.0;return new Sample(query,render[0],query+render[0]);
  }
  private static double p95(double[] values){Arrays.sort(values);return values[(int)Math.ceil(values.length*.95)-1];}
  private static String quote(String text){return "\""+text.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\"";}
}
