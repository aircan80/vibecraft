package dev.wowforever.app.ui.setup;

import dev.wowforever.app.assets.BlizzardBuildDiscovery;
import dev.wowforever.app.assets.LocalWowDiscovery;
import dev.wowforever.app.db.UserDataDir;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.WrappingText;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Database-independent setup. Saved choices are a draft, never an executable import or verified game context. */
public final class LibrarySetupPanel extends JPanel {
  record Game(String id, String name, String channel, boolean planned) { @Override public String toString(){return name;} }
  record Source(String id,String name,String adds,String needs,String omitted) {}
  record Inspection(String text, String selectedFolder) {}
  @FunctionalInterface interface BuildLookup { String discover(String product, BooleanSupplier cancelled) throws Exception; }
  @FunctionalInterface interface LocalLookup { Inspection inspect(Path selected, BooleanSupplier cancelled) throws Exception; }
  private static final List<Game> GAMES=List.of(new Game("forever","WoW Forever","wow_classic_beta",false),
      new Game("classic","WoW Classic","wow_classic_era",true),new Game("retail","WoW Retail","wow",true),
      new Game("anniversary","WoW Anniversary · TBC","wow_anniversary",true));
  private static final List<Source> SOURCES=List.of(
      new Source("questie","QuestieDB","Edition-matched quest, NPC and location records.","Compatible snapshot; currently required by the core importer.","Its records are omitted. Core generation needs a supported alternative."),
      new Source("native","Blizzard native data","Native item/geography metadata and verified asset references.","Exact product, build and locale; supported extraction layouts.","Native fields and assets are omitted. Other editions never fill gaps."),
      new Source("wago","Wago.tools","An optional delivery provider for selected Blizzard-native table exports.","Blizzard native data selected and a verified exact build. Never automatic fallback.","No Wago export is requested; no replacement provider is selected automatically."),
      new Source("atlas","AtlasLoot","Addon-reported loot lists and explicitly authored rates.","An explicitly compatible edition-specific addon snapshot.","AtlasLoot lists are omitted; other providers' loot remains separate."),
      new Source("att","All The Things","Additional authored prerequisites and acquisition connections.","Compatible edition-specific snapshot and supported relationships.","Its additional connections are omitted; Questie records remain separate."),
      new Source("vmangos","vMaNGOS","Clearly labelled non-Forever comparison evidence only.","Explicitly reviewed revision and edition; adapter planned.","Comparison claims are omitted. Forever facts are unaffected."),
      new Source("collector","Local collector observations","Your actual captured observations with their recorded context.","A real compatible capture with validated schema, edition and build.","Those observations are omitted; nothing is invented."));
  private final Preferences preferences;
  private final Consumer<Path> openDatabase;
  private final BuildLookup buildLookup;
  private final LocalLookup localLookup;
  private final JPanel body = stack();
  private final JComboBox<Game> activeGame = new JComboBox<>();
  private final Map<String,JCheckBox> gameChecks = new LinkedHashMap<>();
  private final Map<String,Map<String,Boolean>> selectedSources = new HashMap<>();
  private final Map<String,String> folders = new HashMap<>();
  private final Map<String,Boolean> cdn = new HashMap<>();
  private final WrappingText status = copy("Your choices stay a draft until a supported build job is available.");
  private final WrappingText report = copy("");
  private final JButton cancelWork = button("Cancel check");
  private SwingWorker<String,Void> worker;
  private long generation;
  private String page = "setup";
  private JScrollPane scroll;
  private final String startupError;

  public LibrarySetupPanel(Preferences preferences, Consumer<Path> openDatabase, String error) {
    this(preferences,openDatabase,error,LibrarySetupPanel::discover,LibrarySetupPanel::inspect);
  }
  LibrarySetupPanel(Preferences preferences,Consumer<Path> openDatabase,String error,BuildLookup builds,LocalLookup local) {
    this.preferences=Objects.requireNonNull(preferences);this.openDatabase=Objects.requireNonNull(openDatabase);
    startupError=error;buildLookup=builds;localLookup=local;
    setLayout(new BorderLayout(0,12));setBorder(new EmptyBorder(18,20,18,20));setBackground(Theme.current().bgBase());
    var heading = new JPanel(new BorderLayout(12,8));heading.setOpaque(false);
    heading.add(title("Vibecraft",30),BorderLayout.WEST);
    var context=copy("Every place. Every drop. Every adventure.");context.setFont(context.getFont().deriveFont(15f));heading.add(context,BorderLayout.CENTER);
    add(heading,BorderLayout.NORTH);
    var center=new JPanel(new BorderLayout(0,12));center.setOpaque(false);
    var nav=new JTabbedPane(JTabbedPane.TOP,JTabbedPane.SCROLL_TAB_LAYOUT);
    style(nav);
    nav.setName("setup-navigation");
    for(String name:new String[]{"Build your library","Sources & updates","Game data"})nav.addTab(name,new JPanel());
    nav.addChangeListener(e->{cancel();page=new String[]{"setup","sources","data"}[nav.getSelectedIndex()];render();scrollTop();});
    center.add(nav,BorderLayout.NORTH);
    scroll=new JScrollPane(body,ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setBorder(null);scroll.getViewport().setBackground(Theme.current().bgBase());scroll.getVerticalScrollBar().setUnitIncrement(24);
    center.add(scroll);add(center);
    addHierarchyListener(event->{if((event.getChangeFlags()&java.awt.event.HierarchyEvent.SHOWING_CHANGED)!=0&&isShowing())scrollTop();});
    var footer=stack();
    var open=button("Use an existing database…");open.setName("setup-open-database");open.addActionListener(e->chooseDatabase());footer.add(open);
    status.setName("setup-status");report.setName("setup-discovery-report");cancelWork.setName("setup-cancel-check");
    footer.add(status);cancelWork.setEnabled(false);cancelWork.addActionListener(e->cancel());footer.add(cancelWork);
    footer.add(copy("Java only. WoW folders are read-only. Downloads, caches and databases stay in Vibecraft-owned storage."));
    add(footer,BorderLayout.SOUTH);
    for(Game game:GAMES){
      var sources=new HashMap<String,Boolean>();
      for(Source source:SOURCES)sources.put(source.id(),preferences.getBoolean(game.id()+".source."+source.id(),game.id().equals("forever")&&(source.id().equals("questie")||source.id().equals("native"))));
      if(!sources.get("native"))sources.put("wago",false);
      selectedSources.put(game.id(),sources);folders.put(game.id(),preferences.get(game.id()+".folder",""));cdn.put(game.id(),preferences.getBoolean(game.id()+".cdn",false));
      var check=new JCheckBox(game.name()+(game.planned()?"":" · beta"),preferences.getBoolean("game."+game.id(),game.id().equals("forever")));
      style(check);check.setToolTipText(game.planned()?"Import adapter planned; selection saves preferences only.":"Forever-specific beta data.");check.setName("setup-game-"+game.id());gameChecks.put(game.id(),check);check.addActionListener(e->refreshGames());
    }
    activeGame.setName("setup-active-game");style(activeGame);activeGame.addActionListener(e->{cancel();report.setText("");render();});
    refreshGames();
    String rememberedGame=preferences.get("active-game","forever");
    for(int i=0;i<activeGame.getItemCount();i++)if(activeGame.getItemAt(i).id().equals(rememberedGame))activeGame.setSelectedIndex(i);
    render();
  }
  private void refreshGames(){
    Game previous=(Game)activeGame.getSelectedItem();
    activeGame.removeAllItems();
    for(Game game:GAMES)if(gameChecks.get(game.id()).isSelected())activeGame.addItem(game);
    if(previous!=null)for(int i=0;i<activeGame.getItemCount();i++)if(activeGame.getItemAt(i).id().equals(previous.id()))activeGame.setSelectedIndex(i);
    if(activeGame.getSelectedItem()==null&&activeGame.getItemCount()>0)activeGame.setSelectedIndex(0);
  }
  private Game game(){return (Game)activeGame.getSelectedItem();}
  private void render(){
    body.removeAll();
    if(startupError!=null&&!startupError.isBlank())body.add(notice("Your previous database could not open: "+startupError));
    if(page.equals("setup")){
      body.add(title("Build your library",36));body.add(copy("Choose your game versions and sources. Each edition keeps its own settings and evidence."));
      var games=card("1 · Choose your games");for(JCheckBox check:gameChecks.values())games.add(check);games.add(copy("Classic, Retail and Anniversary import adapters are planned. Selecting them saves preferences; it does not install data."));body.add(games);
    }else body.add(title(page.equals("sources")?"Sources & updates":"Official game data",34));
    if(game()==null){body.add(notice("Choose at least one game version to edit its sources."));finish();return;}
    var context=card("Game version");context.add(activeGame);if(game().planned())context.add(notice("Preferences can be saved for this edition. Its import adapter is planned; there is no working catalog or import coverage yet."));body.add(context);
    if(!page.equals("data")){
      var sources=card(page.equals("setup")?"2 · Choose your sources":"Your selected sources");
      for(Source source:SOURCES){
        var check=new JCheckBox(source.name(),selectedSources.get(game().id()).get(source.id()));style(check);check.setName("setup-source-"+source.id());
        if(source.id().equals("wago"))check.setEnabled(selectedSources.get(game().id()).get("native"));
        check.addActionListener(e->{selectedSources.get(game().id()).put(source.id(),check.isSelected());if(source.id().equals("native")&&!check.isSelected()){selectedSources.get(game().id()).put("wago",false);}render();});
        sources.add(check);sources.add(copy("Adds: "+source.adds()));sources.add(copy("Needs: "+source.needs()));sources.add(copy("If not selected: "+source.omitted()));sources.add(gap());
      }
      body.add(sources);
      if(page.equals("sources"))body.add(notice("No successful import or update is recorded by this setup workspace. Source selections are preferences, not coverage claims. Update checking and activation are not available yet."));
    }
    if(page.equals("data")||page.equals("setup"))addDataTools();
    var summary=card(page.equals("setup")?"3 · Review & save":"Library preferences");
    summary.add(copy("Application data: "+UserDataDir.resolve()));
    summary.add(notice("Database generation is not ready in this app yet. Saving these choices does not download data or create a database. Build discovery and installation inspection are available in Game data."));
    var save=button("Save source preferences");save.setName("setup-save");save.addActionListener(e->save());summary.add(save);
    var build=button("Build library · not available yet");build.setEnabled(false);build.setToolTipText("The import job and safe database activation are still being implemented.");summary.add(build);
    summary.add(copy("Advanced: open a compatible WoW Forever SQLite database in place, read-only. Its edition/build is validated before the browser opens."));body.add(summary);finish();
  }
  private void addDataTools(){
    var nativeCard=card("Your WoW installation");
    nativeCard.add(copy("Find common installation folders first, or browse. This inspects metadata only; it does not extract assets or modify WoW."));
    var find=button("Find my installation");find.setName("setup-detect-local");find.addActionListener(e->runLocal(null));nativeCard.add(find);
    var browse=button("Browse for WoW folder…");browse.setName("setup-browse-local");browse.addActionListener(e->{var chooser=new JFileChooser();chooser.setDialogTitle("Choose your read-only WoW installation");chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)runLocal(chooser.getSelectedFile().toPath());});nativeCard.add(browse);
    nativeCard.add(copy(folders.get(game().id()).isBlank()?"No installation selected.":"Remembered folder: "+folders.get(game().id())+". Recheck before any future import."));
    var allow=new JCheckBox("Allow missing-file CDN downloads",cdn.get(game().id()));style(allow);allow.setName("setup-allow-cdn");
    allow.addActionListener(e->cdn.put(game().id(),allow.isSelected()));nativeCard.add(allow);
    nativeCard.add(copy("Optional Blizzard CDN permission for a future supported exact-build job only. Missing files stay gaps when off. All downloads go to Vibecraft storage, never the WoW folder."));body.add(nativeCard);
    var official=card("Official Blizzard builds");official.add(copy("Discover current and advertised preload pointers for "+game().channel()+". A published pointer is not proof of availability, extractability or a supported library."));
    var check=button("Check available builds");check.setName("setup-discover-builds");check.addActionListener(e->{String product=game().channel();run("Checking Blizzard build metadata…",cancelled->buildLookup.discover(product,cancelled));});official.add(check);
    official.add(copy("No game data is downloaded by this check. Product/build pins and locale must be validated before a future import."));body.add(official);body.add(report);
  }
  @FunctionalInterface private interface Work { String perform(BooleanSupplier cancelled)throws Exception; }
  private void runLocal(Path path){String gameId=game().id();run("Inspecting local installation metadata…",cancelled->{Inspection inspection=localLookup.inspect(path,cancelled);if(!cancelled.getAsBoolean()&&!inspection.selectedFolder().isBlank())SwingUtilities.invokeLater(()->{if(!cancelled.getAsBoolean())folders.put(gameId,inspection.selectedFolder());});return inspection.text();});}
  private void run(String message,Work work){
    if(worker!=null){status.setText("A check is already running. Cancel it before starting another.");return;}
    long token=++generation;status.setText(message);cancelWork.setEnabled(true);report.setText("");
    worker=new SwingWorker<>(){
      @Override protected String doInBackground()throws Exception{return work.perform(this::isCancelled);}
      @Override protected void done(){if(token!=generation)return;worker=null;cancelWork.setEnabled(false);try{report.setText(get());status.setText("Check complete. No import or database change was made.");}catch(CancellationException ignored){status.setText("Check cancelled.");}catch(InterruptedException e){Thread.currentThread().interrupt();status.setText("Check interrupted. Retry when ready.");}catch(ExecutionException e){status.setText("Check unavailable. Verify the connection or selected folder and retry.");}finish();}
    };worker.execute();
  }
  private void cancel(){++generation;if(worker!=null){worker.cancel(true);status.setText("Check cancelled. No import or database change was made.");}worker=null;cancelWork.setEnabled(false);}
  void save(){
    if(game()==null){status.setText("Choose at least one game version before saving.");return;}
    preferences.put("active-game",game().id());
    for(Game game:GAMES){preferences.putBoolean("game."+game.id(),gameChecks.get(game.id()).isSelected());for(Source source:SOURCES)preferences.putBoolean(game.id()+".source."+source.id(),selectedSources.get(game.id()).get(source.id()));preferences.put(game.id()+".folder",folders.get(game.id()));preferences.putBoolean(game.id()+".cdn",cdn.get(game.id()));}
    status.setText("Source preferences saved. No download, import or database creation started.");
  }
  private void chooseDatabase(){var chooser=new JFileChooser();chooser.setDialogTitle("Use an existing WoW Forever database");chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("SQLite database (*.sqlite, *.db)","sqlite","db"));if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION){cancel();openDatabase.accept(chooser.getSelectedFile().toPath());}}
  private void scrollTop(){SwingUtilities.invokeLater(()->{if(scroll!=null){scroll.getViewport().setViewPosition(new Point(0,0));scroll.getVerticalScrollBar().setValue(0);}});}
  private void finish(){body.revalidate();body.repaint();}
  @Override public void removeNotify(){cancel();super.removeNotify();}
  private static String discover(String product,BooleanSupplier cancelled)throws Exception{try(var client=new BlizzardBuildDiscovery()){var found=client.discover(product,cancelled,p->{});var result=new StringBuilder("Official build pointers · ").append(found.discoveredAt()).append('\n');for(var build:found.builds())result.append(build.product()).append(" · ").append(build.version()).append(" · ").append(build.region()).append(" · ").append(build.channel()).append('\n');return result.toString();}}
  private static Inspection inspect(Path folder,BooleanSupplier cancelled)throws Exception{var client=new LocalWowDiscovery();var found=folder==null?client.scan(List.of(),true,cancelled,p->{}):client.inspect(folder,cancelled,p->{});var result=new StringBuilder("Inspected ").append(found.inspectedCandidates()).append(" candidate locations.\n");for(var installation:found.installations())result.append(installation.directory()).append('\n').append(installation.product()).append(" · ").append(installation.version()).append(" · ").append(installation.status()).append('\n').append(installation.explanation()).append("\n\n");if(found.installations().isEmpty())result.append("No recognized installation found. Use Browse to choose another folder.\n");for(String note:found.notes())result.append(note).append('\n');return new Inspection(result.toString(),folder==null?"":folder.toAbsolutePath().normalize().toString());}
  private static JPanel stack(){var panel=new WidthPanel();panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));panel.setOpaque(false);return panel;}
  private static JPanel card(String title){var panel=stack();panel.setOpaque(true);panel.setBackground(Theme.current().bgSurface());panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.current().hairline()),new EmptyBorder(20,20,20,20)));panel.add(title(title,25));panel.add(gap());panel.setAlignmentX(LEFT_ALIGNMENT);return panel;}
  private static JComponent gap(){return (JComponent)Box.createVerticalStrut(14);}
  private static WrappingText copy(String text){var copy=new WrappingText(text,"Library setup information");((javax.swing.text.DefaultCaret)copy.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);copy.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,18));copy.setAlignmentX(LEFT_ALIGNMENT);return copy;}
  private static WrappingText notice(String text){var notice=copy(text);notice.setBorder(new EmptyBorder(12,12,12,12));notice.setOpaque(true);notice.setBackground(Theme.current().bgRaised());return notice;}
  private static JLabel title(String text,int size){var label=new JLabel(text);label.setFont(new Font(Font.SERIF,Font.PLAIN,size));label.setForeground(Theme.current().textPrimary());label.setAlignmentX(LEFT_ALIGNMENT);return label;}
  private static JButton button(String text){var button=new JButton(text);style(button);button.setAlignmentX(LEFT_ALIGNMENT);button.setMinimumSize(new Dimension(0,44));return button;}
  private static void style(JComponent component){component.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,18));component.setForeground(Theme.current().textPrimary());component.setBackground(Theme.current().bgSurface());component.setAlignmentX(LEFT_ALIGNMENT);}
  private static final class WidthPanel extends JPanel implements Scrollable {
    @Override public Dimension getPreferredScrollableViewportSize(){return new Dimension(800,650);}
    @Override public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 24;}
    @Override public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(24,r.height-24);}
    @Override public boolean getScrollableTracksViewportWidth(){return true;}
    @Override public boolean getScrollableTracksViewportHeight(){return false;}
  }
}
