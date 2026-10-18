import dev.wowforever.app.db.*;
import dev.wowforever.app.model.*;
import dev.wowforever.app.ui.*;
import dev.wowforever.app.ui.detail.*;
import dev.wowforever.app.ui.pages.ItemPage;
import dev.wowforever.app.ui.theme.Fonts;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.*;
import dev.wowforever.app.nav.ViewTarget;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Exact reviewed-JAR evidence capture: load off EDT, close database, then render immutable snapshots. */
public final class SourceEvidenceSnapshots {
  private static final long[] IDS={287012,278476,286978,869,273643,16806,17783};
  public static void main(String[] args)throws Exception {
    if(args.length!=3)throw new IllegalArgumentException("Usage: SourceEvidenceSnapshots REVIEW_JAR DATABASE EMPTY_OUTPUT");
    Path jar=Path.of(args[0]),database=Path.of(args[1]),output=Path.of(args[2]);Files.createDirectories(output);
    try(var paths=Files.list(output)){if(paths.findAny().isPresent())throw new IllegalArgumentException("Output must be empty");}
    SwingUtilities.invokeAndWait(()->{Fonts.install();ThemeManager.apply(ThemeManager.Mode.DARK);RepaintManager.currentManager(null).setDoubleBufferingEnabled(false);});
    String before=sha(database);var loaded=new LinkedHashMap<Long,DetailPanels.LoadedItem>();
    Repositories repos;var report=new ArrayList<String>();
    report.add("UTC: "+java.time.Instant.now());report.add("Reviewed JAR SHA-256: "+sha(jar));report.add("Database SHA-256: "+before);
    try(var db=Database.open(database)) {
      if(SwingUtilities.isEventDispatchThread())throw new AssertionError("Loading must be off EDT");
      repos=Repositories.of(db);report.add("Database build: "+db.meta().get("build_id"));
      for(long id:IDS) {
        var data=(DetailPanels.LoadedItem)DetailPanels.load(new EntityRef(EntityKind.ITEM,id),repos).orElseThrow(()->new IllegalStateException("Missing item "+id));
        loaded.put(id,data);report.add("Item "+id+": "+data.detail().summary().displayName()+"; comparisons="+data.sourceDifferences().size());
        for(var field:data.knowledge())report.add("  "+field.field()+"="+field.state()+" · "+field.source()+" · "+field.version()+" · "+field.evidence());
        report.add("  Native slot="+data.nativeMetadata().inventoryType()+"; sets="+data.nativeMetadata().sets().stream().map(s->s.id()+":"+s.name()).toList()+"; effects="+data.nativeMetadata().effects().stream().map(e->e.spellId()+":"+e.name()).toList());
      }
    }
    int[] captures={0};
    for(var mode:ThemeManager.Mode.values()) {
      SwingUtilities.invokeAndWait(()->ThemeManager.apply(mode));
      for(long id:IDS)for(int width:new int[]{1216,420})SwingUtilities.invokeAndWait(()->{
        try{captures[0]+=capture(loaded.get(id),repos,id,mode,width,output,report);}catch(Exception e){throw new IllegalStateException("Item "+id+" capture failed",e);}
      });
    }
    if(!before.equals(sha(database)))throw new IllegalStateException("Database changed during capture");
    report.add("Database closed before all page construction/rendering: true");report.add("Database unchanged: true");report.add("Captures: "+captures[0]);
    report.add("Scope: headless JPanel captures; no claim about native focus, hover, or frame navigation.");
    Files.write(output.resolve("report.txt"),report);System.out.println(String.join("\n",report));
  }
  private static int capture(DetailPanels.LoadedItem data,Repositories repos,long id,ThemeManager.Mode mode,int width,Path out,List<String> report)throws Exception {
    if(!SwingUtilities.isEventDispatchThread())throw new AssertionError("Rendering must be on EDT");
    var requests=new ArrayList<ViewTarget>();
    ItemPage page=(ItemPage)DetailPanels.render(data,repos,requests::add,task->{throw new AssertionError("Unexpected async work with closed database");},
        new StatusSink(){public void message(String s){}public void fail(Exception e){throw new IllegalStateException(e);}},SourceFilter.ALL,BooleanSetting.inMemory(false));
    JPanel root=new JPanel(new BorderLayout());root.setBackground(Theme.current().bgBase());root.putClientProperty(CompactColumns.KEY,width==420);root.add(page);root.setSize(width,width==420?720:900);settle(root);
    String base="item-"+id+"-"+mode.name().toLowerCase(Locale.ROOT)+"-"+width;int count=0;
    save(root,page,out,base+"-default",report);count++;
    var knowledge=find(page,FieldKnowledgePanel.class);
    if(knowledge!=null){scroll(page,knowledge);settle(root);save(root,page,out,base+"-knowledge",report);count++;}
    var metadata=find(page,NativeItemMetadataPanel.class);
    if(metadata!=null) {
      scroll(page,metadata);settle(root);save(root,page,out,base+"-metadata",report);count++;
      for(Component c:metadata.getComponents())if(c instanceof JButton button)button.doClick();
      if(requests.size()!=data.nativeMetadata().sets().size())throw new AssertionError("Missing set link for "+id);
      for(int i=0;i<requests.size();i++)if(!requests.get(i).equals(new ViewTarget.ItemContent("set",data.nativeMetadata().sets().get(i).id())))throw new AssertionError("Wrong set target for "+id);
    }
    var evidence=find(page,SourceDifferencesPanel.class);
    if(evidence!=null) {
      SourceInfoButton info=find(evidence,SourceInfoButton.class);
      JComponent retained=info.detailsContent();
      root.removeAll();root.add(new JScrollPane(retained));settle(root);
      save(root,page,out,base+"-evidence",report);count++;
      JButton technical=buttons(retained).stream().filter(button->button.getText().equals("Technical evidence")).findFirst().orElse(null);
      if(technical!=null && id==16806){technical.doClick();settle(root);save(root,page,out,base+"-technical-evidence",report);count++;}
    }else report.add(base+": no retained comparisons; no evidence fabricated.");
    return count;
  }
  private static void scroll(ItemPage page,JComponent target){JViewport v=page.scrollPane().getViewport();Component content=v.getView();Point p=SwingUtilities.convertPoint(target,0,0,content);v.setViewPosition(new Point(0,Math.max(0,Math.min(p.y-8,content.getHeight()-v.getHeight()))));}
  private static void settle(JPanel root){for(int i=0;i<12;i++)layout(root);var warm=new BufferedImage(root.getWidth(),root.getHeight(),BufferedImage.TYPE_INT_RGB);for(int i=0;i<3;i++){Graphics2D g=warm.createGraphics();root.printAll(g);g.dispose();layout(root);}}
  private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container nested)layout(nested);}
  private static <T>T find(Container c,Class<T> type){if(type.isInstance(c))return type.cast(c);for(Component child:c.getComponents())if(child instanceof Container nested){T found=find(nested,type);if(found!=null)return found;}return null;}
  private static List<JButton> buttons(Container c){var result=new ArrayList<JButton>();for(Component child:c.getComponents()){if(child instanceof JButton button)result.add(button);if(child instanceof Container nested)result.addAll(buttons(nested));}return result;}
  private static void save(JPanel root,ItemPage page,Path out,String name,List<String> report)throws Exception {
    if(page.scrollPane().getHorizontalScrollBar().isVisible())throw new IllegalStateException("Unexpected page horizontal scrolling: "+name);
    var image=new BufferedImage(root.getWidth(),root.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(Theme.current().bgBase());g.fillRect(0,0,image.getWidth(),image.getHeight());g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);root.printAll(g);g.dispose();
    ImageIO.write(image,"png",out.resolve(name+".png").toFile());StringBuilder geometry=new StringBuilder("class\tx\ty\twidth\theight\tvisible\ttext\n");describe(root,root,geometry);Files.writeString(out.resolve(name+".layout.tsv"),geometry);report.add(name+".png · scrollY="+page.scrollPane().getViewport().getViewPosition().y);
  }
  private static void describe(Component c,Component root,StringBuilder out){Rectangle b=SwingUtilities.convertRectangle(c,new Rectangle(0,0,c.getWidth(),c.getHeight()),root);String text=c instanceof JTextArea t?t.getText():c instanceof JLabel l?l.getText():c instanceof JButton button?button.getText():"";out.append(c.getClass().getSimpleName()).append('\t').append(b.x).append('\t').append(b.y).append('\t').append(b.width).append('\t').append(b.height).append('\t').append(c.isVisible()).append('\t').append(text==null?"":text.replace('\n',' ').replace('\t',' ')).append('\n');if(c instanceof Container nested)for(Component child:nested.getComponents())describe(child,root,out);}
  private static String sha(Path path)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);}return HexFormat.of().formatHex(digest.digest());}
}
