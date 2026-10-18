package dev.wowforever.app.db;

import dev.wowforever.app.assets.AssetDownloadPermissions;
import dev.wowforever.importer.setup.LibraryCandidateValidator;
import dev.wowforever.importer.setup.SetupPlan;
import dev.wowforever.importer.setup.SetupStorage;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;

/** Atomic next-startup selection. Versioned databases are immutable; opening sessions never switch here. */
public final class LibraryRegistry {
  private static final int MAX_POINTER_BYTES = 65536;
  private static final List<String> TABLES = List.of("item", "npc", "quest", "zone", "game_object", "instance");
  public record Selection(Path database,String sha256,LibraryCandidateValidator.Validated validation,
      AssetDownloadPermissions.Consent assetPermissions) {
    public Selection {Objects.requireNonNull(assetPermissions);}
    public Selection(Path database,String sha256,LibraryCandidateValidator.Validated validation) {
      this(database,sha256,validation,AssetDownloadPermissions.CACHE_ONLY);
    }
  }
  @FunctionalInterface interface Publisher { void move(Path from, Path to) throws IOException; }
  final Path root;
  final SetupStorage storage;
  final List<Path> protectedGameRoots;
  private final SetupStorage readStorage;
  private final Publisher publisher;

  public LibraryRegistry(Path dataDir, List<Path> protectedGameRoots, Optional<Path> activeDatabase) throws IOException {
    this(dataDir, protectedGameRoots, activeDatabase,
        (from,to) -> Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING));
  }

  LibraryRegistry(Path dataDir, List<Path> gameRoots, Optional<Path> activeDatabase, Publisher publisher) throws IOException {
    Path requested = dataDir.toAbsolutePath().normalize().resolve("libraries");
    if (Files.isSymbolicLink(requested)) throw new IOException("Managed library root cannot be a symbolic link");
    var protectedRoots=new ArrayList<Path>();
    for(Path game:gameRoots)protectedRoots.add(AssetDownloadPermissions.canonicalProspective(game));
    protectedGameRoots=List.copyOf(protectedRoots);
    storage = new SetupStorage(requested, protectedGameRoots, activeDatabase);
    root = storage.verifyDestination(requested);
    readStorage = new SetupStorage(root,protectedGameRoots,Optional.empty());
    this.publisher = publisher;
  }

  /** Rehashes and checks compatibility off the EDT, falling back to the retained last good selection. */
  public Optional<Selection> resolve(BooleanSupplier cancelled) throws IOException {
    offEdt(); check(cancelled);
    Path pointer = checked(root.resolve("selection.properties"));
    if (!Files.exists(pointer, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
    if (!Files.isRegularFile(pointer) || Files.size(pointer) > MAX_POINTER_BYTES)
      throw new IOException("Managed library selection is not a bounded regular file");
    Properties properties = new Properties();
    try (var input = Files.newInputStream(pointer)) {
      byte[] bytes = input.readNBytes(MAX_POINTER_BYTES + 1);
      if (bytes.length > MAX_POINTER_BYTES) throw new IOException("Managed library selection is too large");
      properties.load(new ByteArrayInputStream(bytes));
    } catch (IllegalArgumentException failure) { throw new IOException("Managed library selection is malformed", failure); }
    if (!Set.of("1","2").contains(properties.getProperty("format",""))) throw new IOException("Unsupported managed library selection format");
    IOException rejected = null;
    for (String prefix : List.of("active.", "previous.")) {
      if (!properties.containsKey(prefix + "path")) continue;
      try { return Optional.of(read(properties,prefix,cancelled)); }
      catch (IOException | IllegalArgumentException failure) {
        if (rejected == null) rejected = new IOException("Managed library selection could not be validated", failure);
        else rejected.addSuppressed(failure);
      }
    }
    if (rejected != null) throw rejected;
    throw new IOException("Managed library selection has no active or previous entry");
  }

  private Selection read(Properties p, String prefix, BooleanSupplier cancelled) throws IOException {
    String relative = required(p,prefix+"path");
    if (!relative.matches("versions/[a-f0-9]{64}-[a-f0-9-]{36}/wowforever\\.sqlite"))
      throw new IOException("Managed selection is outside versioned library storage");
    Path file = checkedRead(root.resolve(relative));
    String expected = required(p,prefix+"sha256");
    var consent=readPermissions(p,prefix,expected);
    if(!new SetupStorage(root,consent.readOnlyGameRoots(),Optional.empty()).verifyDestination(file).equals(file))
      throw new IOException("Managed library aliases a protected game folder");
    if (!expected.matches("[a-f0-9]{64}") || !expected.equals(hash(file,cancelled)))
      throw new IOException("Managed library content hash does not match its selection receipt");
    DatabaseInstaller.validateExternal(file);
    String build = required(p,prefix+"build");
    var identity = new SetupPlan.NativeIdentity(required(p,prefix+"product"),required(p,prefix+"client-build"),required(p,prefix+"locale"));
    try (var db = Database.open(file)) {
      var meta = db.meta();
      if (!build.equals(meta.get("build_id")) || !identity.product().equals(meta.get("client_product"))
          || !identity.build().equals(meta.get("client_build"))
          || !identity.locale().equals(meta.getOrDefault("client_locale","enUS"))
          || !required(p,prefix+"ingestion").equals(meta.get("source_ingestion_id")))
        throw new IOException("Managed library identity differs from its activation receipt");
    } catch (java.sql.SQLException failure) { throw new IOException("Cannot validate managed library identity",failure); }
    Map<String,Long> counts = new LinkedHashMap<>();
    for (String table : TABLES) {
      long count = Long.parseLong(required(p,prefix+"count."+table));
      if (count < 0) throw new IOException("Invalid managed library entity count");
      counts.put(table,count);
    }
    int count = Integer.parseInt(required(p,prefix+"warning-count"));
    if (count < 0 || count > 16) throw new IOException("Invalid managed library warning count");
    List<String> warnings = new ArrayList<>();
    for (int i=0;i<count;i++) warnings.add(required(p,prefix+"warning."+i));
    check(cancelled);
    return new Selection(file,expected,new LibraryCandidateValidator.Validated(build,identity,required(p,prefix+"ingestion"),counts,warnings),consent);
  }

  /** Caller holds the registry lock and has validated the immutable copy. No non-atomic fallback. */
  void publish(Selection active, Optional<Selection> previous, BooleanSupplier cancelled) throws IOException {
    Properties p = new Properties(); p.setProperty("format","2");
    write(p,"active.",active);
    if (previous.isPresent()) write(p,"previous.",previous.orElseThrow());
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    p.store(bytes,"Vibecraft next-startup library selection; retained warnings are coverage limits");
    if (bytes.size() > MAX_POINTER_BYTES) throw new IOException("Managed library receipt exceeds its storage bound");
    Path temp = checked(root.resolve("selection-"+UUID.randomUUID()+".tmp"));
    try {
      check(cancelled);
      try (var channel = FileChannel.open(temp,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
        var buffer = java.nio.ByteBuffer.wrap(bytes.toByteArray());
        while(buffer.hasRemaining()) channel.write(buffer);
        channel.force(true);
      }
      check(cancelled);
      publisher.move(checked(temp),checked(root.resolve("selection.properties")));
    } finally { Files.deleteIfExists(checked(temp)); }
  }

  private void write(Properties p, String prefix, Selection selection) throws IOException {
    Path file = checkedRead(selection.database());
    String relative = root.relativize(file).toString().replace('\\','/');
    if (!relative.matches("versions/[a-f0-9]{64}-[a-f0-9-]{36}/wowforever\\.sqlite")) throw new IOException("Invalid managed version path");
    writePermissions(p,prefix,selection);
    var value = selection.validation();
    p.setProperty(prefix+"path",relative); p.setProperty(prefix+"sha256",selection.sha256());
    p.setProperty(prefix+"build",value.buildId()); p.setProperty(prefix+"product",value.nativeIdentity().product());
    p.setProperty(prefix+"client-build",value.nativeIdentity().build()); p.setProperty(prefix+"locale",value.nativeIdentity().locale());
    p.setProperty(prefix+"ingestion",value.ingestionId());
    for(String table:TABLES) p.setProperty(prefix+"count."+table,Long.toString(value.entityCounts().getOrDefault(table,0L)));
    if(value.warnings().size()>16) throw new IOException("Too many activation coverage warnings");
    p.setProperty(prefix+"warning-count",Integer.toString(value.warnings().size()));
    for(int i=0;i<value.warnings().size();i++) {
      String warning=value.warnings().get(i);
      if(warning.isBlank() || warning.length()>1024) throw new IOException("Invalid activation warning length");
      p.setProperty(prefix+"warning."+i,warning);
    }
  }

  private static AssetDownloadPermissions.Consent readPermissions(Properties p,String prefix,String databaseHash) throws IOException {
    if("1".equals(p.getProperty("format")))return AssetDownloadPermissions.CACHE_ONLY;
    if(!"1".equals(required(p,prefix+"assets.format"))
        || !databaseHash.equals(required(p,prefix+"assets.database-sha256")))
      throw new IOException("Asset permissions do not match the selected database");
    var mode=switch(required(p,prefix+"assets.mode")) {
      case "managed" -> AssetDownloadPermissions.Mode.MANAGED;
      case "cache-only" -> AssetDownloadPermissions.Mode.CACHE_ONLY;
      default -> throw new IOException("Invalid managed asset permission mode");
    };
    boolean cdn=permissionBoolean(p,prefix+"assets.blizzard-cdn"),wago=permissionBoolean(p,prefix+"assets.wago-export");
    int count=Integer.parseInt(required(p,prefix+"assets.game-root-count"));
    if(count<0 || count>16)throw new IOException("Invalid protected game-root count");
    List<Path> roots=new ArrayList<>();
    for(int i=0;i<count;i++) {
      Path path=Path.of(required(p,prefix+"assets.game-root."+i));
      if(!path.isAbsolute())throw new IOException("Protected game roots must be absolute");
      roots.add(path.normalize());
    }
    return new AssetDownloadPermissions.Consent(mode,cdn,wago,roots);
  }
  private static boolean permissionBoolean(Properties p,String key) throws IOException {
    return switch(required(p,key)) {case "true" -> true;case "false" -> false;default -> throw new IOException("Invalid asset permission flag");};
  }
  private static void writePermissions(Properties p,String prefix,Selection selection) throws IOException {
    var consent=selection.assetPermissions();
    if(consent.mode()==AssetDownloadPermissions.Mode.LEGACY)throw new IOException("Legacy asset access cannot be published as managed consent");
    p.setProperty(prefix+"assets.format","1");p.setProperty(prefix+"assets.database-sha256",selection.sha256());
    p.setProperty(prefix+"assets.mode",consent.mode()==AssetDownloadPermissions.Mode.MANAGED?"managed":"cache-only");
    p.setProperty(prefix+"assets.blizzard-cdn",Boolean.toString(consent.allowBlizzardCdn()));
    p.setProperty(prefix+"assets.wago-export",Boolean.toString(consent.allowWagoExport()));
    p.setProperty(prefix+"assets.game-root-count",Integer.toString(consent.readOnlyGameRoots().size()));
    for(int i=0;i<consent.readOnlyGameRoots().size();i++)p.setProperty(prefix+"assets.game-root."+i,consent.readOnlyGameRoots().get(i).toString());
  }

  private Path checkedRead(Path path) throws IOException {
    if (!readStorage.verifyDestination(path).equals(path)) throw new IOException("Managed library read path alias refused");
    return path;
  }
  Path checked(Path path) throws IOException {
    if (!storage.verifyDestination(path).equals(path)) throw new IOException("Managed library path alias refused");
    return path;
  }
  private static String required(Properties p,String key) throws IOException {
    String value=p.getProperty(key);
    if(value==null || value.isBlank() || value.length()>1024) throw new IOException("Missing or invalid managed receipt field: "+key);
    return value;
  }
  static String hash(Path file,BooleanSupplier cancelled) throws IOException {
    if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Library database is not a regular file");
    try {
      MessageDigest digest=MessageDigest.getInstance("SHA-256");
      try(var input=Files.newInputStream(file)) {
        byte[] buffer=new byte[1024*1024]; int count;
        while((count=input.read(buffer))!=-1) {check(cancelled);digest.update(buffer,0,count);}
      }
      check(cancelled); return HexFormat.of().formatHex(digest.digest());
    } catch(java.security.NoSuchAlgorithmException impossible) {throw new AssertionError(impossible);}
  }
  static void offEdt() {if(SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Library validation and activation must run off the EDT");}
  static void check(BooleanSupplier cancelled) {if(cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException("Library activation cancelled");}
}
